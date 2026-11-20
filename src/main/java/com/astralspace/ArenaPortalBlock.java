package com.astralspace;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The way into the ladder. Right-click it to pick a mode and queue.
 *
 * <p>Craftable, and dear enough to be a project, so a settlement can have its own rather than
 * everybody walking to Tingen to find a fight.</p>
 */
public final class ArenaPortalBlock extends Block {
    public ArenaPortalBlock(Properties p) { super(p); }

    @Override
    protected InteractionResult useWithoutItem(BlockState st, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) ArenaNet.sendBoard(sp);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
