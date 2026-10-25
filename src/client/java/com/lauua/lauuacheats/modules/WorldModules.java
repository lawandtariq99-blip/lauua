package com.lauua.lauuacheats.modules;

import com.lauua.lauuacheats.core.Module;
import com.lauua.lauuacheats.core.ModuleManager;
import com.lauua.lauuacheats.hud.HudRegistry;
import com.lauua.lauuacheats.scan.ChunkScanner;
import com.lauua.lauuacheats.settings.Setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.*;

/** Basefinding defaults ON: scan loaded chunks immediately; ESP and radar show actual block positions. */
public final class WorldModules {
    private WorldModules() { }
    public static void register(ModuleManager manager,ChunkScanner scanner,HudRegistry hud,java.util.function.Supplier<String> world) {
        manager.register(new Module("NewChunks","Heuristic leads; no unprovable 'fresh' claims",Module.Category.WORLD,-1,false,Module.Tier.C,"Remote seed required for exact worldgen comparison") {});
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
        manager.register(new Module("StorageESP","3D boxes + radar for loaded storage",Module.Category.WORLD,-1,true,Module.Tier.B,"Enabled by default; villages also contain chests") {});
        manager.register(new Module("CoordLogger","Manual coordinate journal",Module.Category.WORLD,-1,false,Module.Tier.A,"Insert or .lauua waypoint") {});
        manager.register(new Module("WaypointExport","CSV/JSON/Xaero export",Module.Category.WORLD,-1,false,Module.Tier.A,"Use .lauua export") {});
        hud.put("World Leads",5,47,()-> {
            var s=scanner.stats(world.get());
            return scanning(manager)?"SCAN " + s.indexed()+" chunks | " + s.containers()+" storage | " + s.spawners()+" spawners | " + s.strongest()+" top":"";
        });
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("lauuacheats","radar"),
            (graphics,delta)->drawRadar(graphics,manager,scanner));
    }
    public static boolean scanning(ModuleManager manager) {
        return List.of("NewChunks","ClusterFinder","SpawnerESP","StorageESP").stream()
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
            };
            if(mark.kind()==ChunkScanner.Kind.SPAWNER && !modules.find("SpawnerESP").map(Module::enabled).orElse(false)) continue;
            if(mark.kind()==ChunkScanner.Kind.STORAGE && !modules.find("StorageESP").map(Module::enabled).orElse(false)) continue;
            g.fill(mx-1,mz-1,mx+2,mz+2,color);
        }
        g.fill(x+49,y+57,x+54,y+62,0xFFFFFFFF);
        g.text(mc.font,"100 blocks / 2px",x+3,y+103,0xFF8BA8BA);
    }
}
