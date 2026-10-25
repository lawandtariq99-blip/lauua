package com.lauua.lauuacheats.scan;

import com.lauua.lauuacheats.text.mixin.SectionStatesAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Strategy;
import java.util.*;

/** Exact section comparison utility; the caller must supply a TRUSTED vanilla baseline. */
public final class PaletteComparator {
    private PaletteComparator() { }
    private static final Strategy<BlockState> STRATEGY=Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
    public record Snapshot(List<BlockState> palette,int bits,long[] packedStorage) {
        public Snapshot { palette=List.copyOf(palette); packedStorage=packedStorage.clone(); }
        @Override public long[] packedStorage() { return packedStorage.clone(); }
    }
    public static Snapshot snapshot(LevelChunkSection section) {
        // pack() reconstructs a canonical ordered palette from the section's indexed states,
        // and returns the exact bit-packed storage for those indices. Compare BOTH: same
        // set of blocks in a different order changes the palette indices and bit array.
        // Bound callers to one section per tick; don't pack all sections on the render thread.
        var packed=((SectionStatesAccessor)section).lauuacheats$states().pack(STRATEGY);
        return new Snapshot(packed.paletteEntries(),packed.bitsPerEntry(),packed.storage().map(s->s.toArray()).orElseGet(()->new long[0]));
    }
    public static boolean matches(Snapshot observed,Snapshot vanilla) {
        return observed.bits()==vanilla.bits()
            && observed.palette().equals(vanilla.palette())
            && Arrays.equals(observed.packedStorage(),vanilla.packedStorage());
    }
}
