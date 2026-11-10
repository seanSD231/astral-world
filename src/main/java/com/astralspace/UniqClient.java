package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * Client side of the 3D Uniqueness items. Each item is a model made of coloured quads with an animated palette texture
 * (flicker, twinkle, lightning), plus separate moving parts (a spinning wheel, a spinning sun, a bow string that pulls back,
 * a rifle bolt that cycles) that this renderer moves. GUI and item frames draw the flat icon instead.
 */
public final class UniqClient extends BlockEntityWithoutLevelRenderer {
    private static UniqClient instance;

    public static final IClientItemExtensions EXT = new IClientItemExtensions() {
        @Override public HumanoidModel.ArmPose getArmPose(LivingEntity e, InteractionHand h, ItemStack s) {
            String p = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
            if (p.equals("starry_sniper") || p.equals("snake_rifle")) return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            if (p.equals("chained_bow")) return HumanoidModel.ArmPose.BOW_AND_ARROW;
            return null;
        }
        @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (instance == null) { Minecraft mc = Minecraft.getInstance(); instance = new UniqClient(mc); }
            return instance;
        }
    };

    private UniqClient(Minecraft mc) { super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels()); }

    private static ModelResourceLocation mrl(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "item/" + path));
    }

    /** Mod bus: make sure every model we draw by hand is loaded. */
    public static void registerModels(ModelEvent.RegisterAdditional e) {
        for (var en : UniqAnim.PARTS.entrySet()) {
            String n = en.getKey();
            e.register(mrl(n + "_3d")); e.register(mrl(n + "_icon"));
            for (UniqAnim.Part p : en.getValue()) e.register(mrl(n + "_part_" + p.name()));
        }
    }

    private static BakedModel model(String path) { return Minecraft.getInstance().getModelManager().getModel(mrl(path)); }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        UniqAnim.Part[] parts = UniqAnim.PARTS.get(name);
        if (parts == null) return;
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer ir = mc.getItemRenderer();
        VertexConsumer vc = ItemRenderer.getFoilBufferDirect(buf, ItemBlockRenderTypes.getRenderType(stack, true), true, stack.hasFoil());
        boolean flat = ctx == ItemDisplayContext.GUI || ctx == ItemDisplayContext.FIXED;
        if (flat) { ir.renderModelLists(model(name + "_icon"), stack, light, overlay, pose, vc); return; }
        ir.renderModelLists(model(name + "_3d"), stack, light, overlay, pose, vc);
        float t = (Util.getMillis() % 3_600_000L) / 1000f;
        float use = 0f;
        LocalPlayer p = mc.player;
        if (p != null && p.isUsingItem() && p.getUseItem().getItem() == stack.getItem()) use = Math.min(1f, p.getTicksUsingItem() / (float) Uniqueness.HOLD_TICKS);
        for (UniqAnim.Part part : parts) {
            pose.pushPose();
            switch (part.type()) {
                case "spin" -> {
                    float a = part.speed() * t * (1f + 3f * use);
                    float[] v = part.v();
                    pose.translate(v[0], v[1], v[2]); pose.mulPose(Axis.ZP.rotation(a)); pose.translate(-v[0], -v[1], -v[2]);
                }
                case "slide" -> {
                    float f = use > 0f ? (float) (0.5 - 0.5 * Math.cos(use * 5.0 * 2.0 * Math.PI)) : 0f;
                    float[] v = part.v(); pose.translate(v[0] * f, v[1] * f, v[2] * f);
                }
                case "pull" -> {
                    float[] v = part.v(); float f = use; pose.translate(v[0] * f, v[1] * f, v[2] * f);
                }
                default -> { }
            }
            ir.renderModelLists(model(name + "_part_" + part.name()), stack, light, overlay, pose, vc);
            pose.popPose();
        }
    }
}
