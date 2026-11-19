package com.astralspace;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The way in. Right-click it with nothing in hand to pick a domain. */
public final class DomainGateBlock extends Block {
    public DomainGateBlock(Properties p) { super(p); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (ArtifactDomain.inside(sp)) ArtifactDomain.leave(sp, false);
            else net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new DoorNet.DomainOpen());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
