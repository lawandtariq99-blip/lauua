package com.lauua.lauuacheats.text.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 26.2 moved current screen from Minecraft into Gui's private field. */
@Mixin(Gui.class)
public interface GuiScreenAccessor {
    @Accessor("screen") Screen lauuacheats$screen();
}
