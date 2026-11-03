package com.astralspace;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/** An arcade cabinet: using it opens the osu menu on your screen. */
public final class OsuCabinetBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public OsuCabinetBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH)); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) { return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()); }

    @Override
    protected BlockState rotate(BlockState s, net.minecraft.world.level.block.Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }

    @Override
    protected BlockState mirror(BlockState s, net.minecraft.world.level.block.Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            boolean jukebox = false;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) if (level.getBlockState(pos.relative(d)).is(net.minecraft.world.level.block.Blocks.JUKEBOX)) jukebox = true;
            if (!jukebox) sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("No jukebox is next to this cabinet: levels will play without music. Put a jukebox against it to hear a level's music disc.").withStyle(net.minecraft.ChatFormatting.YELLOW));
            PacketDistributor.sendToPlayer(sp, new OsuNet.OpenCabinet(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
