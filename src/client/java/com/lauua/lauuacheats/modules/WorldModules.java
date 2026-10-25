package com.lauua.lauuacheats.modules;

import com.lauua.lauuacheats.core.Module;
import com.lauua.lauuacheats.core.ModuleManager;
import com.lauua.lauuacheats.hud.HudRegistry;
import com.lauua.lauuacheats.scan.ChunkScanner;
import com.lauua.lauuacheats.scan.CoordLogger;
import com.lauua.lauuacheats.render.WorldEsp;
import net.minecraft.core.BlockPos;
import java.io.IOException;
import com.lauua.lauuacheats.settings.Setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.*;

/** Basefinding defaults ON: scan loaded chunks immediately; ESP and radar show actual block positions. */
public final class WorldModules {
    public enum EspStyle { FILL, OUTLINE, BOTH }
    private WorldModules() { }
    public static void register(ModuleManager manager,ChunkScanner scanner,HudRegistry hud,CoordLogger logger,java.util.function.Supplier<String> world) {
        // Do not expose a toggle that falsely claims it can identify newly generated chunks.
        manager.register(new Module("ClusterFinder","Rank storage + utility clusters",Module.Category.WORLD,-1,true,Module.Tier.B,"Enabled by default; not proof of a base") {
            final IntSlider budget=add(new IntSlider("Blocks per tick",2048,256,8192));
            final IntSlider range=add(new IntSlider("ESP range",96,16,192));
            final BooleanSetting radar=add(new BooleanSetting("Radar",true));
            final DoubleSlider storageWeight=add(new DoubleSlider("Storage weight",1.5,0,10));
            final DoubleSlider utilityWeight=add(new DoubleSlider("Crafting weight",3.0,0,12));
            final DoubleSlider obsidianWeight=add(new DoubleSlider("Obsidian weight",0.8,0,6));
            double oldStorage=Double.NaN, oldUtility=Double.NaN, oldObsidian=Double.NaN;
            @Override public void tick() {
                scanner.blocksPerTick=budget.get(); scanner.highlightRange=range.get();
                if(oldStorage!=storageWeight.get()) { scanner.weight("minecraft:chest",storageWeight.get()); oldStorage=storageWeight.get(); }
                if(oldUtility!=utilityWeight.get()) { scanner.weight("minecraft:crafting_table",utilityWeight.get()); oldUtility=utilityWeight.get(); }
                if(oldObsidian!=obsidianWeight.get()) { scanner.weight("minecraft:obsidian",obsidianWeight.get()); oldObsidian=obsidianWeight.get(); }
            }
        });
        manager.register(new Module("SpawnerESP","3D boxes + radar for loaded spawners",Module.Category.WORLD,-1,true,Module.Tier.B,"Enabled by default; natural spawners aren't bases") {});
        manager.register(new Module("StorageESP","Colored 3D storage markers",Module.Category.RENDER,-1,true,Module.Tier.B,"Loaded chunks only; not x-ray for unseen chunks") {
            final BooleanSetting chests=add(new BooleanSetting("Chests",true));
            final BooleanSetting barrels=add(new BooleanSetting("Barrels",true));
            final BooleanSetting shulkers=add(new BooleanSetting("Shulkers",true));
            final BooleanSetting machines=add(new BooleanSetting("Hoppers & furnaces",true));
            final IntSlider range=add(new IntSlider("Range",96,16,192));
            final IntSlider opacity=add(new IntSlider("Opacity",100,12,220));
            final EnumSetting<EspStyle> style=add(new EnumSetting<>("Style",EspStyle.BOTH,EspStyle.class));
            final ColorSetting chestColor=add(new ColorSetting("Chest color",0xFF71E3CF));
            final ColorSetting barrelColor=add(new ColorSetting("Barrel color",0xFFFFBD73));
            final ColorSetting otherColor=add(new ColorSetting("Other color",0xFFB698FA));
        });
        manager.register(new Module("OreESP","Rare ore boxes in loaded chunks",Module.Category.RENDER,-1,false,Module.Tier.B,"Diamond, emerald & ancient debris; bounded loaded-chunk index") {
            final IntSlider range=add(new IntSlider("Range",64,16,192));
            final ColorSetting color=add(new ColorSetting("Color",0xFF76B7FF));
        });
        manager.register(new Module("PortalESP","Mark loaded portal blocks",Module.Category.RENDER,-1,false,Module.Tier.B,"Nether portals and End portal frames") {});
        manager.register(new Module("UtilityESP","Crafting & utility block markers",Module.Category.RENDER,-1,false,Module.Tier.B,"Loaded workstations only") {});
        manager.register(new Module("CoordLogger","Journal your route automatically",Module.Category.WORLD,-1,false,Module.Tier.B,"Logs only after both time and distance thresholds; .lauua export for CSV/JSON/Xaero") {
            final IntSlider distance=add(new IntSlider("Minimum distance",128,16,1024));
            final IntSlider interval=add(new IntSlider("Interval seconds",60,5,600));
            BlockPos previous;
            long lastLogged;
            @Override protected void onEnable() { previous=null; lastLogged=System.currentTimeMillis(); }
            @Override public void worldChanged() { previous=null; lastLogged=System.currentTimeMillis(); }
            @Override public void tick() {
                Minecraft mc=Minecraft.getInstance();
                if(mc.player==null || mc.level==null) return;
                BlockPos position=mc.player.blockPosition();
                if(previous==null) { previous=position; return; }
                long now=System.currentTimeMillis();
                if(now-lastLogged<interval.get()*1000L || previous.distSqr(position)<(long)distance.get()*distance.get()) return;
                try {
                    logger.log(mc,world.get(),"Route");
                    previous=position; lastLogged=now;
                } catch(IOException e) { System.err.println("[Lauua] Auto waypoint failed: "+e); setEnabled(false); }
            }
        });
        hud.put("World Leads",5,47,()-> {
            var s=scanner.stats(world.get());
            return scanning(manager)?"SCAN " + s.indexed()+" chunks | " + s.containers()+" storage | " + s.spawners()+" spawners | " + s.strongest()+" top":"";
        });
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("lauuacheats","radar"),
            (graphics,delta)->drawRadar(graphics,manager,scanner));
    }
    public static boolean scanning(ModuleManager manager) {
        return List.of("ClusterFinder","SpawnerESP","StorageESP","OreESP","PortalESP","UtilityESP").stream()
            .anyMatch(n->manager.find(n).map(Module::enabled).orElse(false));
    }
    private static void drawRadar(GuiGraphicsExtractor g,ModuleManager modules,ChunkScanner scanner) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.gui.hud.isHidden() || !scanning(modules)) return;
        var cluster=modules.find("ClusterFinder");
        if(cluster.isPresent() && cluster.get().settings().stream().anyMatch(s->s.name.equals("Radar") && Boolean.FALSE.equals(s.get()))) return;
        int x=g.guiWidth()-116,y=16;
        g.fill(x-3,y-3,x+105,y+114,0xD90E1420);
        g.fill(x-2,y-2,x+104,y+13,0xFF182738);
        g.text(mc.font,"LAUUA / RADAR",x+4,y+1,0xFF70DACC);
        g.fill(x,y+17,x+102,y+102,0xCC0B1520);
        for(int grid=0;grid<=4;grid++) {
            g.fill(x+grid*25,y+17,x+grid*25+1,y+102,0x223F6C72);
            g.fill(x,y+17+grid*21,x+102,y+18+grid*21,0x223F6C72);
        }
        int px=mc.player.blockPosition().getX(),pz=mc.player.blockPosition().getZ();
        for(var mark:scanner.visible()) {
            int dx=(mark.pos().getX()-px)/2,dz=(mark.pos().getZ()-pz)/2;
            int mx=x+51+dx,mz=y+59+dz;
            if(mx<x+2 || mx>x+100 || mz<y+19 || mz>y+99) continue;
            int color=switch(mark.kind()) {
                case SPAWNER -> 0xFFDD80F9;
                case STORAGE -> 0xFF65DFD4;
                case UTILITY -> 0xFFFFBF71;
                case ARTIFICIAL -> 0xFFE18F64;
                case ORE -> 0xFF76B7FF;
                case PORTAL -> 0xFFAD85F6;
            };
            if(mark.kind()==ChunkScanner.Kind.SPAWNER && !modules.find("SpawnerESP").map(Module::enabled).orElse(false)) continue;
            if(mark.kind()==ChunkScanner.Kind.STORAGE && ( !modules.find("StorageESP").map(Module::enabled).orElse(false)
                || !WorldEsp.storageEnabled(modules.find("StorageESP").orElseThrow(),mark.storageType()))) continue;
            if(mark.kind()==ChunkScanner.Kind.ORE && !modules.find("OreESP").map(Module::enabled).orElse(false)) continue;
            if(mark.kind()==ChunkScanner.Kind.PORTAL && !modules.find("PortalESP").map(Module::enabled).orElse(false)) continue;
            if(mark.kind()==ChunkScanner.Kind.UTILITY && !modules.find("UtilityESP").map(Module::enabled).orElse(false)
                && !modules.find("ClusterFinder").map(Module::enabled).orElse(false)) continue;
            g.fill(mx-1,mz-1,mx+2,mz+2,color);
        }
        g.fill(x+49,y+57,x+54,y+62,0xFFFFFFFF);
        g.text(mc.font,"100 blocks / 2px",x+3,y+103,0xFF8BA8BA);
    }
}
