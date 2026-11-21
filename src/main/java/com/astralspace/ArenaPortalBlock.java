package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.joml.Matrix4f;

/**
 * The way into the ladder: a stone with a blue portal standing out of it.
 *
 * <p>You right-click the portal, not the stone. The block's interaction shape is the whole of the
 * opening rather than the cube at its foot, so what you look at and what you click are the same
 * thing; its collision shape is still only the stone, so the portal is walked through, as a
 * portal should be.</p>
 *
 * <p>Craftable, and dear enough to be a project, so a settlement can have its own rather than
 * everybody walking to Tingen to find a fight.</p>
 */
public final class ArenaPortalBlock extends Block implements EntityBlock {

    /** The stone itself. */
    private static final VoxelShape STONE = Block.box(0, 0, 0, 16, 10, 16);
    /** The stone and the opening above it: what a click has to land on. */
    private static final VoxelShape WHOLE = Block.box(1, 0, 1, 15, 46, 15);

    public ArenaPortalBlock(Properties p) { super(p); }

    @Override
    protected VoxelShape getShape(BlockState st, BlockGetter lv, BlockPos pos, CollisionContext ctx) { return WHOLE; }

    @Override
    protected VoxelShape getCollisionShape(BlockState st, BlockGetter lv, BlockPos pos, CollisionContext ctx) { return STONE; }

    @Override
    protected VoxelShape getInteractionShape(BlockState st, BlockGetter lv, BlockPos pos) { return WHOLE; }

    @Override
    protected InteractionResult useWithoutItem(BlockState st, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // The stone is just the foot of it. Clicking the stone does nothing; the opening above it
        // is what you use.
        if (hit.getLocation().y - pos.getY() < 0.64) {
            if (!level.isClientSide && player instanceof ServerPlayer sp)
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal("Use the opening, not the stone."), true);
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) ArenaNet.sendBoard(sp);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new Tile(pos, st); }

    // ---------------------------------------------------------------- the block entity

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AstralMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Tile>> TYPE =
            TYPES.register("arena_portal", () ->
                    BlockEntityType.Builder.of(Tile::new, AstralFloor.ARENA_PORTAL.get()).build(null));

    public static void register(IEventBus bus) { TYPES.register(bus); }

    /** Holds nothing: it exists so the portal has something to draw itself from. */
    public static final class Tile extends BlockEntity {
        public Tile(BlockPos pos, BlockState st) { super(TYPE.get(), pos, st); }
    }

    // ---------------------------------------------------------------- drawing it

    /**
     * The opening: a sheet of blue standing out of the stone, turned to face whoever is looking,
     * with a brighter core and a run of motes rising through it.
     */
    /**
     * The opening: an oval of blue standing out of the stone, turned to face whoever is looking.
     *
     * <p>Drawn as one sheet of horizontal strips rather than a stack of nested panes. The panes
     * were three quads and a run of motes all sharing the same space, which fought each other for
     * depth and read as a flickering glass box. A single run of strips cannot fight itself.</p>
     *
     * <p>{@code RenderType.lightning()} takes position and colour and nothing else; the old code
     * also fed it texture coordinates, light and normals, which is the other half of why it looked
     * the way it did.</p>
     */
    public static final class Render implements BlockEntityRenderer<Tile> {
        private static final int ROWS = 26;
        private static final float HW = .62f, Y0 = .05f, Y1 = 2.25f;

        public Render(BlockEntityRendererProvider.Context ctx) { }

        private static void strip(VertexConsumer vc, Matrix4f m,
                                  float wl, float yl, float wu, float yu, int rgb, float a) {
            int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
            int al = (int) (Math.max(0, Math.min(1, a)) * 255);
            if (al <= 2) return;
            vc.addVertex(m, -wl, yl, 0).setColor(r, g, b, al);
            vc.addVertex(m,  wl, yl, 0).setColor(r, g, b, al);
            vc.addVertex(m,  wu, yu, 0).setColor(r, g, b, al);
            vc.addVertex(m, -wu, yu, 0).setColor(r, g, b, al);
        }

        @Override
        public void render(Tile tile, float pt, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
            if (tile.getLevel() == null) return;
            float t = (tile.getLevel().getGameTime() + pt) * .05f;
            var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
            double dx = cam.getPosition().x - (tile.getBlockPos().getX() + .5);
            double dz = cam.getPosition().z - (tile.getBlockPos().getZ() + .5);

            pose.pushPose();
            pose.translate(.5, 0, .5);
            pose.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.atan2(dx, dz)));
            VertexConsumer vc = buf.getBuffer(RenderType.lightning());
            Matrix4f m = pose.last().pose();

            float cy = (Y0 + Y1) * .5f, hy = (Y1 - Y0) * .5f;
            float prevW = 0, prevY = Y0;
            for (int i = 0; i <= ROWS; i++) {
                float u = i / (float) ROWS;
                float y = Y0 + u * (Y1 - Y0);
                float k = (y - cy) / hy;
                float w = HW * (float) Math.sqrt(Math.max(0, 1 - k * k));
                // a slow ripple across the face of it, so the opening is never a flat shape
                w *= 1f + .05f * (float) Math.sin(t * 2.1f + u * 7.5f);
                if (i > 0) {
                    float mid = (u + (i - 1) / (float) ROWS) * .5f;
                    float edge = Math.abs(mid - .5f) * 2f;                 // 0 in the middle, 1 at the ends
                    float shimmer = .5f + .5f * (float) Math.sin(t * 3f - mid * 9f);
                    strip(vc, m, prevW, prevY, w, y, EDGE, .30f + .22f * edge);
                    strip(vc, m, prevW * .55f, prevY, w * .55f, y, CORE, .16f + .26f * shimmer);
                }
                prevW = w; prevY = y;
            }
            // a bright seam up the middle
            for (int i = 0; i < ROWS; i++) {
                float u0 = i / (float) ROWS, u1 = (i + 1) / (float) ROWS;
                float y0 = Y0 + u0 * (Y1 - Y0), y1 = Y0 + u1 * (Y1 - Y0);
                float f = (float) Math.sin(Math.PI * u0);
                float w = .055f * f * (1f + .3f * (float) Math.sin(t * 4f + u0 * 11f));
                strip(vc, m, w, y0, w, y1, 0xDCEEFF, .50f * f);
            }
            pose.popPose();
        }

        private static final int EDGE = 0x2E6BFF, CORE = 0x7FC2FF;

        @Override public boolean shouldRenderOffScreen(Tile t) { return false; }
        @Override public int getViewDistance() { return 96; }
    }
}
