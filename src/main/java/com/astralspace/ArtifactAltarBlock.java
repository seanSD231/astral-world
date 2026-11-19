package com.astralspace;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The altar an artifact is levelled on. Right-click it with nothing in hand. */
public final class ArtifactAltarBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public ArtifactAltarBlock(Properties p) { super(p); }

    @Override
    protected VoxelShape getShape(BlockState s, net.minecraft.world.level.BlockGetter l, BlockPos pos, CollisionContext c) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp)
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new DoorNet.AltarOpen());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
