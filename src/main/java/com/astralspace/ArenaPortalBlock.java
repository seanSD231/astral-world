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
    public static final class Render implements BlockEntityRenderer<Tile> {
        private static final int EDGE = 0x2E6BFF, CORE = 0x9FD0FF;

        public Render(BlockEntityRendererProvider.Context ctx) { }

        private static void quad(VertexConsumer vc, Matrix4f m, float hw, float y0, float y1,
                                 int rgb, float a, float uy) {
            int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
            int al = (int) (Math.max(0, Math.min(1, a)) * 255);
            vc.addVertex(m, -hw, y0, 0).setColor(r, g, b, al).setUv(0, uy).setLight(0x00F000F0).setNormal(0, 0, 1);
            vc.addVertex(m,  hw, y0, 0).setColor(r, g, b, al).setUv(1, uy).setLight(0x00F000F0).setNormal(0, 0, 1);
            vc.addVertex(m,  hw, y1, 0).setColor(r, g, b, al).setUv(1, uy + .5f).setLight(0x00F000F0).setNormal(0, 0, 1);
            vc.addVertex(m, -hw, y1, 0).setColor(r, g, b, al).setUv(0, uy + .5f).setLight(0x00F000F0).setNormal(0, 0, 1);
        }

        @Override
        public void render(Tile tile, float pt, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
            if (tile.getLevel() == null) return;
            float t = (tile.getLevel().getGameTime() + pt) * .05f;
            var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();

            pose.pushPose();
            pose.translate(.5, .62, .5);
            // turned to the viewer, so it reads as an opening from wherever you stand
            double dx = cam.getPosition().x - (tile.getBlockPos().getX() + .5);
            double dz = cam.getPosition().z - (tile.getBlockPos().getZ() + .5);
            pose.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.atan2(dx, dz)));

            VertexConsumer vc = buf.getBuffer(RenderType.lightning());
            Matrix4f m = pose.last().pose();
            float sway = (float) Math.sin(t * 1.3f) * .03f;
            // three sheets, narrowing and brightening inward, so it has depth rather than being one flat pane
            quad(vc, m, .62f + sway, .02f, 2.18f, EDGE, .42f, 0);
            quad(vc, m, .46f - sway, .10f, 2.06f, EDGE, .46f, 0);
            quad(vc, m, .27f + sway * .5f, .22f, 1.90f, CORE, .55f, 0);
            pose.popPose();

            // motes rising through the opening
            pose.pushPose();
            pose.translate(.5, 0, .5);
            pose.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.atan2(dx, dz)));
            Matrix4f mm = pose.last().pose();
            for (int i = 0; i < 7; i++) {
                float q = (float) (((t * .35f) + i / 7f) % 1.0);
                float mx = (float) Math.sin(i * 2.4f + t) * .34f;
                float y = .08f + q * 2.0f, s = .035f * (1f - q * .4f);
                quad(vc, mm, s, y, y + s * 2f, CORE, .75f * (1f - q), 0);
                pose.pushPose();
                pose.translate(mx, 0, 0);
                pose.popPose();
            }
            pose.popPose();
        }

        @Override public boolean shouldRenderOffScreen(Tile t) { return false; }
        @Override public int getViewDistance() { return 96; }
    }
}
