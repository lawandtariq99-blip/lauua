package com.lauua.lauuacheats.test;

import com.lauua.lauuacheats.LauuaClient;
import com.lauua.lauuacheats.scan.ChunkScanner;
import com.lauua.lauuacheats.text.mixin.GuiScreenAccessor;
import com.lauua.lauuacheats.ui.ClickGui;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

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
                    throw new AssertionError("chest marker missing: " + highlights);
                if(highlights.stream().noneMatch(h->h.pos().equals(new BlockPos(3,100,0)) && h.kind()==ChunkScanner.Kind.SPAWNER))
                    throw new AssertionError("spawner marker missing: " + highlights);
                mc.gui.setScreen(LauuaClient.INSTANCE.createGui());
                if(!(((GuiScreenAccessor)mc.gui).lauuacheats$screen() instanceof ClickGui))
                    throw new AssertionError("GUI did not open");
            });
            context.waitTicks(2); // causes HUD and ESP extraction, catching runtime mixin/render errors
            context.takeScreenshot("lauua-explorer-26_2");
        }
    }
}
