package com.lauua.lauuacheats.scan;

import com.lauua.lauuacheats.text.mixin.SectionStatesAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import java.util.*;

/** Event-driven bounded base-finding index. All chunk reads occur on the client tick, never in render callbacks. */
public final class ChunkScanner {
    public enum Kind { STORAGE, SPAWNER, UTILITY, ARTIFICIAL }
    public record Highlight(BlockPos pos, Kind kind) { }
    public record Result(int x, int z, String classification, double clusterScore,
                         int spawners, int containers, List<Highlight> highlights, long updatedTick) { }
    public record Stats(int indexed, int queued, int visible, int containers, int spawners, int strongest) { }
    private static final int MAX_WORLDS=16, MAX_CHUNKS=512, MAX_QUEUE=128, MAX_PER_CHUNK=64, MAX_RENDER=192;
    private static final List<int[]> SWEEP = makeSweep(7);
    private final LinkedHashMap<String, WorldIndex> worlds = new LinkedHashMap<>(16,0.75f,true);
    private final Deque<LevelChunk> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private final Map<String,Double> weights = new HashMap<>();
    private volatile List<Highlight> visible = List.of();
    private LevelChunk current;
    private Iterator<Map.Entry<BlockPos,BlockEntity>> blockEntities=Collections.emptyIterator();
    private String worldKey="";
    private int section, index, sweepIndex, sweepX=Integer.MIN_VALUE, sweepZ=Integer.MIN_VALUE;
    private int containers, spawners, utility, artificial;
    private double customBoost;
    private final List<Highlight> found = new ArrayList<>();
    private final Set<Long> foundPositions = new HashSet<>();
    private long tick;
    public int blocksPerTick=2048, highlightRange=96;

    private static final class WorldIndex {
        final LinkedHashMap<Long,Result> results=new LinkedHashMap<>(128,0.75f,true);
        List<Result> ranked=List.of();
    }
    public ChunkScanner() {
        weights.put("minecraft:crafting_table",3.0);
        weights.put("minecraft:beacon",9.0);
        weights.put("minecraft:respawn_anchor",5.0);
        weights.put("minecraft:obsidian",0.8);
    }
    public void weight(String id,double value) {
        if(!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !Double.isFinite(value) || value < -20 || value>20) throw new IllegalArgumentException("invalid block or weight");
        if(weights.size()>=128 && !weights.containsKey(id)) throw new IllegalArgumentException("weight limit reached");
        weights.put(id,value);
    }
    public Map<String,Double> weights() { return Map.copyOf(weights); }
    public void reset() { current=null; blockEntities=Collections.emptyIterator(); queue.clear(); queued.clear(); visible=List.of(); worldKey=""; sweepIndex=0; }
    public List<Result> results(String key) { WorldIndex w=worlds.get(key); return w==null?List.of():w.ranked; }
    public List<Highlight> visible() { return visible; }
    public Stats stats(String key) {
        WorldIndex w=worlds.get(key); if(w==null) return new Stats(0,queue.size(),0,0,0,0);
        int chest=0, mob=0; for(Result r:w.results.values()) { chest+=r.containers(); mob+=r.spawners(); }
        return new Stats(w.results.size(),queue.size()+(current==null?0:1),visible.size(),chest,mob,
            w.ranked.isEmpty()?0:(int)Math.round(w.ranked.getFirst().clusterScore()));
    }
    public void loaded(ClientLevel level,LevelChunk chunk) {
        if(level==Minecraft.getInstance().level && !worldKey.isEmpty()) enqueue(chunk);
    }
    public void unloaded(ClientLevel level,LevelChunk chunk) {
        if(level!=Minecraft.getInstance().level) return;
        long id=key(chunk.getPos().x(),chunk.getPos().z());
        if(current==chunk) { current=null; blockEntities=Collections.emptyIterator(); }
        queue.removeIf(c->c==chunk); queued.remove(id);
        // Historical ranked leads survive unloading; only live ESP markers are discarded.
        rebuildVisible(Minecraft.getInstance());
    }
    public void tick(Minecraft mc,String key) {
        if(mc.player==null || mc.level==null) { reset(); return; }
        tick++;
        if(!worldKey.equals(key)) { reset(); worldKey=key; }
        WorldIndex w=worlds.computeIfAbsent(key,k->new WorldIndex());
        while(worlds.size()>MAX_WORLDS) worlds.remove(worlds.keySet().iterator().next());
        int px=mc.player.blockPosition().getX()>>4, pz=mc.player.blockPosition().getZ()>>4;
        if(px!=sweepX || pz!=sweepZ) {
            sweepX=px; sweepZ=pz; sweepIndex=0;
            if(current!=null && (Math.abs(current.getPos().x()-px)>4 || Math.abs(current.getPos().z()-pz)>4)) current=null;
        }
        // Chunk packets can deliver block-entity data AFTER the first chunk load. Revisit loaded
        // chunks instead of permanently marking them done; refresh the player's chunk first.
        if(tick%40==0) sweepIndex=0;
        LevelChunk here=mc.level.getChunkSource().getChunk(px,pz,false);
        if(here!=null && here!=current) {
            Result previous=w.results.get(key(px,pz));
            if(previous==null || tick-previous.updatedTick()>80) {
                queue.removeIf(c->c==here);
                queued.add(key(px,pz));
                queue.addFirst(here);
            }
        }
        // Bootstrap chunks already loaded before a module was toggled, then sweep around movement.
        for(int n=0;n<6 && sweepIndex<SWEEP.size();n++) {
            int[] off=SWEEP.get(sweepIndex++);
            LevelChunk chunk=mc.level.getChunkSource().getChunk(px+off[0],pz+off[1],false);
            if(chunk==null) continue;
            Result old=w.results.get(key(chunk.getPos().x(),chunk.getPos().z()));
            if(old==null || tick-old.updatedTick()>240) enqueue(chunk);
        }
        if(current==null) {
            current=queue.pollFirst();
            if(current==null) { if(tick%10==0) rebuildVisible(mc); return; }
            queued.remove(key(current.getPos().x(),current.getPos().z()));
            section=index=containers=spawners=utility=artificial=0; customBoost=0;
            found.clear(); foundPositions.clear();
            // Block entities reveal storage/spawners without scanning every block in a section.
            // Keep the iterator only while this chunk is current; abort if packets mutate it.
            blockEntities=current.getBlockEntities().entrySet().iterator();
        }
        int cx=current.getPos().x(), cz=current.getPos().z();
        if(mc.level.getChunkSource().getChunk(cx,cz,false)!=current) { current=null; blockEntities=Collections.emptyIterator(); return; }
        long deadline=System.nanoTime()+1_500_000;
        try {
            for(int processed=0;processed<96 && blockEntities.hasNext() && System.nanoTime()<deadline;processed++) {
                var entry=blockEntities.next(); BlockState state=entry.getValue().getBlockState();
                if(state.is(Blocks.SPAWNER)) { spawners++; mark(entry.getKey(),Kind.SPAWNER); }
                else if(isStorage(state)) { containers++; mark(entry.getKey(),Kind.STORAGE); customBoost+=weights.getOrDefault("minecraft:chest",1.5)-1.5; }
            }
        } catch(ConcurrentModificationException changed) { current=null; blockEntities=Collections.emptyIterator(); return; }
        if(blockEntities.hasNext()) return; // never process an unbounded BE map on one tick
        LevelChunkSection[] sections=current.getSections();
        int remaining=Math.clamp(blocksPerTick,256,8192); // target ~1.5ms/tick maximum on normal hardware
        while(section<sections.length && remaining>0 && System.nanoTime()<deadline) {
            LevelChunkSection s=sections[section];
            if(index==0 && (s.hasOnlyAir() || !((SectionStatesAccessor)s).lauuacheats$states().maybeHas(ChunkScanner::interesting))) { section++; continue; }
            int i=index++, x=i&15, z=(i>>4)&15, y=(i>>8)&15;
            BlockState state=s.getBlockState(x,y,z);
            if(interesting(state)) {
                BlockPos pos=new BlockPos((cx<<4)+x,mc.level.getMinY()+(section<<4)+y,(cz<<4)+z);
                if(state.is(Blocks.SPAWNER)) { if(foundPositions.add(pos.asLong())) { spawners++; mark(pos,Kind.SPAWNER); } }
                else if(isStorage(state)) { if(foundPositions.add(pos.asLong())) { containers++; mark(pos,Kind.STORAGE); customBoost+=weights.getOrDefault("minecraft:chest",1.5)-1.5; } }
                else if(isUtility(state)) { utility++; mark(pos,Kind.UTILITY); boost(state); }
                else if(isArtificial(state)) { artificial++; mark(pos,Kind.ARTIFICIAL); boost(state); }
            }
            remaining--;
            if(index==4096) { index=0; section++; }
        }
        if(section>=sections.length) {
            int y=mc.player.blockPosition().getY();
            boolean nether=mc.level.dimension()==Level.NETHER;
            boolean natural=spawners>0 && utility==0 || containers>0 && utility==0 && artificial==0;
            double score=ScoreModel.score(new ScoreModel.Evidence(containers,spawners,utility,artificial,natural,nether,y))+customBoost;
            score=Math.max(0,score);
            Result r=new Result(cx,cz,ScoreModel.classification(score),score,spawners,containers,List.copyOf(found),tick);
            w.results.put(key(cx,cz),r);
            if(w.results.size()>MAX_CHUNKS) w.results.remove(w.results.keySet().iterator().next());
            w.ranked=w.results.values().stream().sorted(Comparator.comparingDouble(Result::clusterScore).reversed()).toList();
            current=null; blockEntities=Collections.emptyIterator(); rebuildVisible(mc);
        } else if(tick%10==0) rebuildVisible(mc);
    }
    private void enqueue(LevelChunk chunk) {
        long id=key(chunk.getPos().x(),chunk.getPos().z());
        if(chunk==current || queue.size()>=MAX_QUEUE || !queued.add(id)) return;
        queue.addLast(chunk);
    }
    private void mark(BlockPos pos,Kind kind) {
        foundPositions.add(pos.asLong());
        if(found.size()<MAX_PER_CHUNK) found.add(new Highlight(pos,kind));
    }
    private void boost(BlockState state) {
        // Avoid allocating registry names for the common stone/dirt/air path.
        String id=state.getBlock().builtInRegistryHolder().key().identifier().toString();
        double baseline=state.is(Blocks.CRAFTING_TABLE)?3.0:state.is(Blocks.OBSIDIAN)?0.8:0.0;
        customBoost+=weights.getOrDefault(id,baseline)-baseline;
    }
    private void rebuildVisible(Minecraft mc) {
        if(mc.player==null || mc.level==null) { visible=List.of(); return; }
        WorldIndex w=worlds.get(worldKey); if(w==null) { visible=List.of(); return; }
        int px=mc.player.blockPosition().getX(), pz=mc.player.blockPosition().getZ(), r=Math.clamp(highlightRange,16,192);
        List<Highlight> list=new ArrayList<>(MAX_RENDER);
        // Iterate only indexed nearby chunks; no block reads, sorting or allocation on render thread.
        for(Result result:w.results.values()) {
            if(Math.abs((result.x()<<4)-px)>r+16 || Math.abs((result.z()<<4)-pz)>r+16) continue;
            if(mc.level.getChunkSource().getChunk(result.x(),result.z(),false)==null) continue;
            for(Highlight h:result.highlights()) {
                if(Math.abs(h.pos().getX()-px)<=r && Math.abs(h.pos().getZ()-pz)<=r && list.size()<MAX_RENDER) list.add(h);
            }
        }
        visible=List.copyOf(list);
    }
    private static boolean isStorage(BlockState s) { return s.is(Blocks.CHEST) || s.is(Blocks.TRAPPED_CHEST) || s.is(Blocks.BARREL)
        || s.is(Blocks.ENDER_CHEST) || s.is(Blocks.HOPPER) || s.is(Blocks.DISPENSER) || s.is(Blocks.DROPPER)
        || s.is(Blocks.FURNACE) || s.is(Blocks.BLAST_FURNACE) || s.is(Blocks.SMOKER) || s.getBlock() instanceof ShulkerBoxBlock; }
    private static boolean isUtility(BlockState s) { return s.is(Blocks.CRAFTING_TABLE) || s.is(Blocks.BEACON)
        || s.is(Blocks.ENCHANTING_TABLE) || s.is(Blocks.RESPAWN_ANCHOR) || s.is(Blocks.ANVIL)
        || s.is(Blocks.BREWING_STAND); }
    private static boolean isArtificial(BlockState s) { return s.is(Blocks.OBSIDIAN) || s.is(Blocks.NETHER_PORTAL)
        || s.is(Blocks.GLOWSTONE) || s.is(Blocks.LADDER) || s.is(Blocks.COBBLESTONE)
        || s.is(Blocks.OAK_PLANKS) || s.is(Blocks.GLASS); }
    private static boolean interesting(BlockState s) { return s.is(Blocks.SPAWNER) || isStorage(s) || isUtility(s) || isArtificial(s); }
    private static long key(int x,int z) { return ((long)x<<32)|(z&0xffffffffL); }
    private static List<int[]> makeSweep(int radius) {
        List<int[]> offsets=new ArrayList<>();
        for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) offsets.add(new int[]{x,z});
        offsets.sort(Comparator.comparingInt(a->Math.max(Math.abs(a[0]),Math.abs(a[1]))));
        return List.copyOf(offsets);
    }
}
