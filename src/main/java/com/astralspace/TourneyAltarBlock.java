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
 * The tournament altar. No recipe: an administrator places it where the tournament is meant to be
 * held, and from it they take names, set the stake and start the draw. Everyone else right-clicks
 * it to pay in and to watch the branches.
 */
public final class TourneyAltarBlock extends Block {
    public TourneyAltarBlock(Properties p) { super(p); }

    @Override
    protected InteractionResult useWithoutItem(BlockState st, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) TourneyNet.sendBoard(sp);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
