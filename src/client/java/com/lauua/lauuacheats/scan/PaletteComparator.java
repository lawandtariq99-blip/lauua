package com.lauua.lauuacheats.scan;

import com.lauua.lauuacheats.text.mixin.SectionStatesAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Strategy;
import java.util.*;

/** Canonical packed-section comparison utility; callers must supply a trusted vanilla baseline. */
public final class PaletteComparator {
    private PaletteComparator() { }
    private static final Strategy<BlockState> STRATEGY=Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
    public record Snapshot(List<BlockState> palette,int bits,long[] packedStorage) {
        public Snapshot { palette=List.copyOf(palette); packedStorage=packedStorage.clone(); }
        @Override public long[] packedStorage() { return packedStorage.clone(); }
    }
    public static Snapshot snapshot(LevelChunkSection section) {
        // pack() REENCODES states into a canonical ordered palette and packed bit-storage.
        // Compare BOTH the palette and its indices: equal block sets can have different
        // layouts. This is NOT a dump of the section's original in-memory/raw palette order;
        // original ordering is lost when pack() repalettizes. It cannot identify vanilla
        // worldgen without a trusted same-seed baseline. Bound use to one section per tick.
        var packed=((SectionStatesAccessor)section).lauuacheats$states().pack(STRATEGY);
        return new Snapshot(packed.paletteEntries(),packed.bitsPerEntry(),packed.storage().map(s->s.toArray()).orElseGet(()->new long[0]));
    }
    public static boolean matches(Snapshot observed,Snapshot vanilla) {
        return observed.bits()==vanilla.bits()
            && observed.palette().equals(vanilla.palette())
            && Arrays.equals(observed.packedStorage(),vanilla.packedStorage());
    }
}
