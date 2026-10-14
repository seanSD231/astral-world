package com.astralspace;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/** Finds the ground inside the configured overworld band (see Settings). */
public final class Surface {
    private Surface() {}

    /**
     * The Y a player should stand at (the first block above the ground) at x/z, scanning down from Settings.surfaceTop.
     * Leaves are skipped. Returns -1 if the band has no ground there, or (loadChunk=false) the chunk is not loaded.
     */
    public static int find(ServerLevel level, int x, int z, boolean loadChunk) {
        int cx = x >> 4, cz = z >> 4;
        if (!loadChunk && !level.hasChunk(cx, cz)) return -1;
        if (loadChunk) level.getChunk(cx, cz); // loads (or generates) the chunk
        int top = Math.min(Settings.surfaceTop, level.getMaxBuildHeight() - 1);
        int bottom = Math.max(Settings.surfaceBottom, level.getMinBuildHeight());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = top; y >= bottom; y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (!state.isAir() && !state.is(BlockTags.LEAVES)) return y + 1;
        }
        return -1;
    }
}
