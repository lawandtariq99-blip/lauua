package com.lauua.lauuacheats.text.mixin;

import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelChunkSection.class)
public interface SectionStatesAccessor {
    @Accessor("states") PalettedContainer<BlockState> lauuacheats$states();
}
