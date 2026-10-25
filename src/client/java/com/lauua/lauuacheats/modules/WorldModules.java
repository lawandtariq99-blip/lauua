package com.lauua.lauuacheats.modules;

import com.lauua.lauuacheats.core.*;
import com.lauua.lauuacheats.hud.HudRegistry;
import com.lauua.lauuacheats.scan.ChunkScanner;
import com.lauua.lauuacheats.settings.Setting.*;
import net.minecraft.client.Minecraft;
import java.util.*;

/** Scanner results are leads, not X-ray rendering or proof of player modification. */
public final class WorldModules {
    private WorldModules() { }
    public static void register(ModuleManager manager,ChunkScanner scanner,HudRegistry hud,java.util.function.Supplier<String> world) {
        manager.register(new Module("NewChunks","Unknown/likely-modified chunk leads",Module.Category.WORLD,-1,false,Module.Tier.C,"Cannot know vanilla worldgen without seed") {});
        manager.register(new Module("ClusterFinder","Rank unusual blocks with editable weights",Module.Category.WORLD,-1,false,Module.Tier.C,"Use .lauua weight id score") {});
        manager.register(new Module("SpawnerESP","Nearby scanned spawner indicator",Module.Category.WORLD,-1,false,Module.Tier.B,"HUD indicator only") {});
        manager.register(new Module("StorageESP","Nearby scanned container indicator",Module.Category.WORLD,-1,false,Module.Tier.B,"HUD indicator only") {});
        manager.register(new Module("CoordLogger","Manual chat/file coordinate log",Module.Category.WORLD,-1,false,Module.Tier.A,"Press Insert to log") {});
        manager.register(new Module("WaypointExport","Xaero, CSV and JSON exporter",Module.Category.WORLD,-1,false,Module.Tier.A,"Use .lauua export") {});
        hud.put("World Leads",5,47,()-> {
            if(manager.find("SpawnerESP").map(Module::enabled).orElse(false) || manager.find("StorageESP").map(Module::enabled).orElse(false)) {
                var results=scanner.results(world.get());
                int spawners=results.stream().mapToInt(ChunkScanner.Result::spawners).sum();
                int storage=results.stream().mapToInt(ChunkScanner.Result::containers).sum();
                return "Nearby scanned: " + spawners + " spawners | " + storage + " storage";
            }
            return "";
        });
    }
    public static boolean scanning(ModuleManager manager) {
        return List.of("NewChunks","ClusterFinder","SpawnerESP","StorageESP").stream().anyMatch(n->manager.find(n).map(Module::enabled).orElse(false));
    }
}
