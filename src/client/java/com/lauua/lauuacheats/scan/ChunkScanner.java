package com.lauua.lauuacheats.scan;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Incremental client-thread scanner: 256 block states/tick, <= 128 classified chunks. */
public final class ChunkScanner {
    public record Result(int x,int z,String classification,double clusterScore,int spawners,int containers) { }
    private final Map<String, LinkedHashMap<Long,Result>> worlds=new HashMap<>();
    private final Map<String,Double> weights=new HashMap<>();
    private LevelChunk current;
    private String worldKey="";
    private int section, index, spawners, containers, unnatural;
    private double score;
    private int probe;
    public ChunkScanner() {
        weights.put("minecraft:chest",6.0); weights.put("minecraft:barrel",4.0);
        weights.put("minecraft:spawner",8.0); weights.put("minecraft:crafting_table",3.0);
        weights.put("minecraft:shulker_box",10.0); weights.put("minecraft:obsidian",1.0);
    }
    public void weight(String block,double value) { if(weights.size()<128 && Double.isFinite(value) && Math.abs(value)<=100) weights.put(block,value); }
    public Map<String,Double> weights() { return Collections.unmodifiableMap(weights); }
    public void reset() { current=null; section=index=probe=0; }
    public List<Result> results(String key) { return worlds.getOrDefault(key,new LinkedHashMap<>()).values().stream().sorted(Comparator.comparingDouble(Result::clusterScore).reversed()).limit(128).toList(); }
    public void tick(Minecraft mc,String key) {
        if(mc.level==null || mc.player==null) { reset(); return; }
        if(!worldKey.equals(key)) { reset(); worldKey=key; }
        LinkedHashMap<Long,Result> results=worlds.computeIfAbsent(key,k->new LinkedHashMap<>());
        if(worlds.size()>16) worlds.remove(worlds.keySet().iterator().next());
        if(current==null) {
            int centerX=mc.player.blockPosition().getX()>>4, centerZ=mc.player.blockPosition().getZ()>>4;
            for(int attempts=0;attempts<25;attempts++,probe=(probe+1)%25) {
                int x=centerX+probe%5-2,z=centerZ+probe/5-2;
                LevelChunk candidate=mc.level.getChunkSource().getChunk(x,z,false);
                if(candidate!=null && !results.containsKey(posKey(x,z))) {
                    current=candidate; section=index=spawners=containers=unnatural=0; score=0; break;
                }
            }
            if(current==null) return;
        }
        int x=current.getPos().x(), z=current.getPos().z();
        if(mc.level.getChunkSource().getChunk(x,z,false)!=current) { reset(); return; } // unloaded/replaced mid-scan
        LevelChunkSection[] sections=current.getSections(); int budget=256;
        while(budget>0 && section<sections.length) {
            if(index==4096) { section++; index=0; continue; }
            int i=index++, lx=i&15, lz=(i>>4)&15, ly=(i>>8)&15;
            BlockState state=sections[section].getBlockState(lx,ly,lz);
            String id=state.getBlock().builtInRegistryHolder().key().identifier().toString();
            int y=mc.level.getMinY()+section*16+ly;
            double weight=weights.getOrDefault(id,0.0);
            if(state.is(Blocks.SPAWNER)) spawners++;
            if(state.is(Blocks.CHEST) || state.is(Blocks.BARREL) || state.is(Blocks.ENDER_CHEST)) containers++;
            if(weight!=0) {
                // Cluster score: editable rare-block weight minus a height expectation (ore/obsidian
                // become more common underground) and biome background, if known. This is evidence,
                // not proof of player activity; scores are ranked, never used as a definitive flag.
                double expected=(y<32 && (state.is(Blocks.OBSIDIAN)))?0.8:0.0;
                score+=weight-expected; unnatural++;
            }
            budget--;
        }
        if(section>=sections.length) {
            // Client receives palette-compressed sections, but no server seed / generator settings.
            // A *real* fresh-vs-modified comparison must reconstruct vanilla worldgen's ordered
            // palette AND packed bit-storage with the same seed, version and biome, then compare
            // both arrays byte-for-byte. Counting rare blocks alone cannot identify new chunks.
            // Thus UNKNOWN or LIKELY_MODIFIED is honest; NEVER label a chunk freshly generated.
            String classification=unnatural>3?"LIKELY_MODIFIED":"UNKNOWN";
            results.put(posKey(x,z),new Result(x,z,classification,score,spawners,containers));
            if(results.size()>128) results.remove(results.keySet().iterator().next());
            reset();
        }
    }
    private static long posKey(int x,int z) { return ((long)x<<32) | (z&0xffffffffL); }
}
