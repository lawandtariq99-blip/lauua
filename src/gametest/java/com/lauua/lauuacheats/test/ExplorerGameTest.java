package com.lauua.lauuacheats.test;

import com.lauua.lauuacheats.LauuaClient;
import com.lauua.lauuacheats.scan.ChunkScanner;
import com.lauua.lauuacheats.text.mixin.GuiScreenAccessor;
import com.lauua.lauuacheats.ui.ClickGui;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

/** A virtual-display integration test: world join, basefinding, ESP index and GUI opening. */
public final class ExplorerGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try(TestSingleplayerContext world=context.worldBuilder().create()) {
            world.getServer().runCommand("/setblock 0 98 0 minecraft:stone");
            world.getServer().runCommand("/tp @a 0 99 0");
            world.getServer().runCommand("/setblock 1 100 0 minecraft:chest");
            world.getServer().runCommand("/setblock 2 100 0 minecraft:crafting_table");
            world.getServer().runCommand("/setblock 3 100 0 minecraft:spawner");
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
                mc.gui.setScreen(LauuaClient.INSTANCE.createGui());
                if(!(((GuiScreenAccessor)mc.gui).lauuacheats$screen() instanceof ClickGui))
                    throw new AssertionError("GUI did not open");
            });
            context.waitTicks(2); // causes HUD and ESP extraction, catching runtime mixin/render errors
            context.takeScreenshot("lauua-explorer-26_2");
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
        }
    }
}
