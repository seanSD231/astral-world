package com.astralspace;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/** An arcade cabinet: using it opens the osu menu on your screen. */
public final class OsuCabinetBlock extends Block {
    public OsuCabinetBlock(Properties p) { super(p); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, new OsuNet.OpenCabinet(pos));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
