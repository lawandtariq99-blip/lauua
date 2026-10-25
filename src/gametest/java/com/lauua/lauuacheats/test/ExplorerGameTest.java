package com.lauua.lauuacheats.test;

import com.lauua.lauuacheats.LauuaClient;
import com.lauua.lauuacheats.scan.ChunkScanner;
import com.lauua.lauuacheats.core.Module;
import com.lauua.lauuacheats.settings.Setting;
import com.lauua.lauuacheats.render.WorldEsp;
import com.lauua.lauuacheats.text.mixin.GuiScreenAccessor;
import com.lauua.lauuacheats.ui.ClickGui;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import org.lwjgl.glfw.GLFW;

/** A virtual-display integration test: world join, basefinding, ESP index and GUI opening. */
public final class ExplorerGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1600,900); // Typical desktop size; default gametest window is tiny.
        try(TestSingleplayerContext world=context.worldBuilder().create()) {
            world.getServer().runCommand("/setblock 0 98 2 minecraft:stone");
            world.getServer().runCommand("/tp @a 0 99 2 180 0");
            world.getServer().runCommand("/setblock 1 100 0 minecraft:chest");
            world.getServer().runCommand("/setblock 2 100 0 minecraft:crafting_table");
            world.getServer().runCommand("/setblock 3 100 0 minecraft:spawner");
            world.getServer().runCommand("/setblock 5 100 0 minecraft:diamond_ore");
            world.getServer().runCommand("/setblock 6 100 0 minecraft:end_portal_frame");
            world.getConnection().waitForChunksRender();
            context.waitTicks(160);
            context.runOnClient(mc -> {
                if(LauuaClient.INSTANCE==null) throw new AssertionError("client entrypoint missing");
                if(!LauuaClient.INSTANCE.modules().find("StorageESP").orElseThrow().enabled())
                    throw new AssertionError("StorageESP should be on by default");
                var highlights=LauuaClient.INSTANCE.scanner().visible();
                if(highlights.stream().noneMatch(h->h.pos().equals(new BlockPos(1,100,0)) && h.kind()==ChunkScanner.Kind.STORAGE))
                    throw new AssertionError("chest marker missing: " + highlights + " stats="
                        + LauuaClient.INSTANCE.scanner().stats("singleplayer/"+mc.level.dimension().identifier())
                        + " current="+mc.level.getBlockState(new BlockPos(1,100,0)) + " player="+mc.player.blockPosition());
                if(highlights.stream().noneMatch(h->h.pos().equals(new BlockPos(3,100,0)) && h.kind()==ChunkScanner.Kind.SPAWNER))
                    throw new AssertionError("spawner marker missing: " + highlights);
                if(highlights.stream().noneMatch(h->h.pos().equals(new BlockPos(1,100,0)) && h.storageType()==ChunkScanner.StorageType.CHEST))
                    throw new AssertionError("chest storage subtype missing: " + highlights);
                if(highlights.stream().noneMatch(h->h.pos().equals(new BlockPos(5,100,0)) && h.kind()==ChunkScanner.Kind.ORE))
                    throw new AssertionError("ore marker missing: " + highlights);
                if(highlights.stream().noneMatch(h->h.pos().equals(new BlockPos(6,100,0)) && h.kind()==ChunkScanner.Kind.PORTAL))
                    throw new AssertionError("portal marker missing: " + highlights);
                if(WorldEsp.storageRgb(ChunkScanner.StorageType.BARREL,1,2,3)!=2)
                    throw new AssertionError("storage palette routing failed");
                String key="singleplayer/"+mc.level.dimension().identifier();
                if(LauuaClient.INSTANCE.scanner().results(key).stream()
                    .noneMatch(r->r.x()==0 && r.z()==0 && r.clusterScore()>=12))
                    throw new AssertionError("base cluster score did not include chest + crafting table");
            });
            Path espShot=context.takeScreenshot("lauua-live-esp");
            try {
                Files.createDirectories(Path.of("build/libs"));
                Files.copy(espShot,Path.of("build/libs/lauua-live-esp.png"),StandardCopyOption.REPLACE_EXISTING);
            }
            catch(IOException e) { throw new AssertionError("Unable to save world ESP screenshot",e); }
            context.runOnClient(mc -> {
                mc.gui.setScreen(LauuaClient.INSTANCE.createGui());
                if(!(((GuiScreenAccessor)mc.gui).lauuacheats$screen() instanceof ClickGui))
                    throw new AssertionError("GUI did not open");
            });
            context.waitTicks(2); // causes HUD and ESP extraction, catching runtime mixin/render errors
            Path screenshot=context.takeScreenshot("lauua-explorer-26_2");
            try {
                Files.createDirectories(Path.of("build/libs"));
                Files.copy(screenshot,Path.of("build/libs/lauua-gui-preview.png"),StandardCopyOption.REPLACE_EXISTING);
            } catch(IOException e) { throw new AssertionError("Unable to save tested GUI screenshot: " + screenshot,e); }
            int[] scrollAt=new int[2];
            context.runOnClient(mc -> {
                int gw=mc.getWindow().getGuiScaledWidth(),gh=mc.getWindow().getGuiScaledHeight();
                scrollAt[0]=(gw/2-60)*mc.getWindow().getScreenWidth()/gw;
                scrollAt[1]=(gh/2)*mc.getWindow().getScreenHeight()/gh;
            });
            context.getInput().setCursorPos(scrollAt[0],scrollAt[1]);
            context.getInput().scroll(-2);
            context.waitTicks(2);
            int[] pixel=new int[2];
            context.runOnClient(mc -> {
                var gui=(ClickGui)((GuiScreenAccessor)mc.gui).lauuacheats$screen();
                int[] pt=gui.moduleTogglePoint("SpawnerESP");
                pixel[0]=pt[0]*mc.getWindow().getScreenWidth()/mc.getWindow().getGuiScaledWidth();
                pixel[1]=pt[1]*mc.getWindow().getScreenHeight()/mc.getWindow().getGuiScaledHeight();
            });
            context.getInput().setCursorPos(pixel[0],pixel[1]);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            context.runOnClient(mc -> {
                if(LauuaClient.INSTANCE.modules().find("SpawnerESP").orElseThrow().enabled())
                    throw new AssertionError("GUI click failed to toggle SpawnerESP");
            });
            // Exercise actual navigation, card-inspection and a live filter setting at compact scale.
            clickGui(context,"category",Module.Category.RENDER.name());
            clickGui(context,"inspect","StorageESP");
            Path settingsShot=context.takeScreenshot("lauua-storage-settings");
            try { Files.copy(settingsShot,Path.of("build/libs/lauua-storage-settings.png"),StandardCopyOption.REPLACE_EXISTING); }
            catch(IOException e) { throw new AssertionError("Unable to save settings screenshot",e); }
            clickGui(context,"setting","Barrels");
            context.runOnClient(mc -> {
                Module storage=LauuaClient.INSTANCE.modules().find("StorageESP").orElseThrow();
                Setting<?> barrels=storage.settings().stream().filter(s->s.name.equals("Barrels")).findFirst().orElseThrow();
                if(!Boolean.FALSE.equals(barrels.get())) throw new AssertionError("Barrels filter setting not changed by GUI");
                if(WorldEsp.storageEnabled(storage,ChunkScanner.StorageType.BARREL))
                    throw new AssertionError("Barrel visibility filter not applied to renderer/radar");
                storage.setEnabled(false);
                if(!com.lauua.lauuacheats.modules.WorldModules.scanning(LauuaClient.INSTANCE.modules()))
                    throw new AssertionError("scanner stopped while other scanners are enabled");
                storage.setEnabled(true);
                mc.gui.setScreen(null);
            });
            // Live packet changes after initial scan must update markers without reconnecting.
            world.getServer().runCommand("/setblock 1 100 0 minecraft:air");
            world.getServer().runCommand("/setblock 4 100 0 minecraft:barrel");
            context.waitTicks(140);
            context.runOnClient(mc -> {
                var live=LauuaClient.INSTANCE.scanner().visible();
                if(live.stream().anyMatch(h->h.pos().equals(new BlockPos(1,100,0))))
                    throw new AssertionError("stale chest marker after removal: " + live);
                if(live.stream().noneMatch(h->h.pos().equals(new BlockPos(4,100,0))
                    && h.storageType()==ChunkScanner.StorageType.BARREL))
                    throw new AssertionError("new typed barrel missing after live update: " + live);
                var chunk=mc.level.getChunkSource().getChunk(0,0,false);
                LauuaClient.INSTANCE.scanner().unloaded(mc.level,chunk);
                if(LauuaClient.INSTANCE.scanner().visible().stream().anyMatch(h->(h.pos().getX()>>4)==0 && (h.pos().getZ()>>4)==0))
                    throw new AssertionError("unloaded chunk leaked live ESP markers");
            });
        }
    }
    private static void clickGui(ClientGameTestContext context,String action,String name) {
        int[] pixel=new int[2];
        context.runOnClient(mc -> {
            ClickGui gui=(ClickGui)((GuiScreenAccessor)mc.gui).lauuacheats$screen();
            int[] point=switch(action) {
                case "category" -> gui.categoryPoint(Module.Category.valueOf(name));
                case "inspect" -> gui.moduleInspectPoint(name);
                case "setting" -> gui.settingPoint(name);
                default -> throw new IllegalArgumentException(action);
            };
            pixel[0]=point[0]*mc.getWindow().getScreenWidth()/mc.getWindow().getGuiScaledWidth();
            pixel[1]=point[1]*mc.getWindow().getScreenHeight()/mc.getWindow().getGuiScaledHeight();
        });
        context.getInput().setCursorPos(pixel[0],pixel[1]);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }
}
