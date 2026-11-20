package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * The standing board: a block that shows the top ten of one game, floating over it.
 *
 * <p>Right-click cycles which game it shows. The lines are worked out on the server and sent with
 * the block, rather than the client asking for them, because a board in a town square is read far
 * more often than it changes and nobody should have to open anything to look at one.</p>
 */
public final class LeaderBoard {
    private LeaderBoard() { }

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AstralMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Tile>> TYPE =
            TYPES.register("leader_board", () -> BlockEntityType.Builder
                    .of(Tile::new, AstralFloor.LEADER_BOARD.get()).build(null));

    public static void register(IEventBus bus) { TYPES.register(bus); }

    /** The block itself. */
    public static final class Stand extends Block implements net.minecraft.world.level.block.EntityBlock {
        public Stand(Properties p) { super(p); }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new Tile(pos, st); }

        @Override
        protected InteractionResult useWithoutItem(BlockState st, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof Tile t && player instanceof ServerPlayer sp) {
                t.cycle();
                sp.displayClientMessage(Component.literal(t.mode().title).withStyle(net.minecraft.ChatFormatting.AQUA), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    /** What the board holds: which game, and the ten lines as they last stood. */
    public static final class Tile extends BlockEntity {
        private Arena.Mode mode = Arena.Mode.OPEN;
        private List<String> lines = new ArrayList<>();
        private int tick;

        public Tile(BlockPos pos, BlockState st) { super(TYPE.get(), pos, st); }

        public Arena.Mode mode() { return mode; }
        public List<String> lines() { return lines; }

        void cycle() {
            mode = Arena.Mode.values()[(mode.ordinal() + 1) % Arena.Mode.values().length];
            refresh();
        }

        /** Reads the standings and sends them out with the block. */
        void refresh() {
            if (level == null || level.isClientSide || level.getServer() == null) return;
            Ladder l = Ladder.of(level.getServer());
            List<String> out = new ArrayList<>();
            for (Ladder.Row r : l.standings(mode)) {
                if (out.size() >= 10) break;
                out.add(r.name() + "\u0000" + r.points());
            }
            lines = out;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }

        /** Every ten seconds is often enough for something people walk past. */
        public static void tick(Level level, BlockPos pos, BlockState st, Tile t) {
            if (level.isClientSide) return;
            if (++t.tick % 200 != 0) return;
            t.refresh();
        }

        @Override
        protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider reg) {
            super.saveAdditional(tag, reg);
            tag.putString("mode", mode.id());
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (String s : lines) l.add(net.minecraft.nbt.StringTag.valueOf(s));
            tag.put("lines", l);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider reg) {
            super.loadAdditional(tag, reg);
            mode = Arena.Mode.of(tag.getString("mode"));
            lines = new ArrayList<>();
            net.minecraft.nbt.ListTag l = tag.getList("lines", 8);
            for (int i = 0; i < l.size(); i++) lines.add(l.getString(i));
        }

        @Override
        public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider reg) {
            CompoundTag t = new CompoundTag();
            saveAdditional(t, reg);
            return t;
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }
    }

    /** Draws the ten lines in the air over the block. */
    public static final class Render implements net.minecraft.client.renderer.blockentity.BlockEntityRenderer<Tile> {
        private final net.minecraft.client.gui.Font font;

        public Render(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context ctx) {
            this.font = ctx.getFont();
        }

        @Override
        public void render(Tile t, float pt, PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buf,
                           int light, int overlay) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || mc.player.distanceToSqr(
                    t.getBlockPos().getX() + .5, t.getBlockPos().getY(), t.getBlockPos().getZ() + .5) > 36 * 36) return;
            int accent = t.mode() == Arena.Mode.OPEN ? 0xFFD24A : (t.mode() == Arena.Mode.SEQ5 ? 0x6FA2FF : 0xC566E6);

            pose.pushPose();
            pose.translate(.5, 1.45 + t.lines().size() * .14, .5);
            pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            pose.scale(-.022f, -.022f, .022f);
            line(t, pose, buf, light, t.mode().title, accent, 0);
            int row = 1;
            for (String s : t.lines()) {
                int cut = s.indexOf('\u0000');
                String name = cut < 0 ? s : s.substring(0, cut);
                String pts = cut < 0 ? "" : s.substring(cut + 1);
                line(t, pose, buf, light, row + ". " + name + "   " + pts, row <= 3 ? accent : 0xC8C8D2, row);
                row++;
            }
            pose.popPose();
        }

        private void line(Tile t, PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buf,
                          int light, String s, int colour, int row) {
            float x = -font.width(s) / 2f;
            font.drawInBatch(s, x, row * 10f, colour, false, pose.last().pose(), buf,
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0x40000000, light);
        }

        @Override
        public boolean shouldRenderOffScreen(Tile t) { return false; }
    }
}
