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
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import java.io.DataInputStream;
import java.util.HashMap;
import java.util.Map;
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
            if (p.equals("snake_rifle")) return HumanoidModel.ArmPose.CROSSBOW_HOLD;
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

    /** Mod bus: the flat inventory icons are ordinary models; the 3D parts are smooth meshes loaded by hand below. */
    public static void registerModels(ModelEvent.RegisterAdditional e) {
        for (String n : UniqAnim.PARTS.keySet()) e.register(mrl(n + "_icon"));
    }

    private static BakedModel model(String path) { return Minecraft.getInstance().getModelManager().getModel(mrl(path)); }

    // ---- smooth meshes ----
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "textures/item/mesh_white.png");
    private static final Map<String, Mesh[]> MESHES = new HashMap<>();
    private static final float[][][] FLAME = {{{.12f, .9f, .25f}, {.55f, 1f, .55f}}, {{.2f, .85f, .3f}, {.85f, 1f, .8f}}, {{.05f, .45f, .15f}, {.2f, .8f, .3f}}};

    private static final class Mesh {
        float[] pos, nrm; int[] col; int[] kind, phase, tint; int[] tri;
    }

    private static Mesh[] meshes(String name) {
        Mesh[] m = MESHES.get(name);
        if (m != null) return m;
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "meshes/" + name + ".bin"));
            if (res.isEmpty()) { m = new Mesh[0]; MESHES.put(name, m); return m; }
            try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(res.get().open()))) {
                int np = in.readInt(); m = new Mesh[np];
                for (int p = 0; p < np; p++) {
                    int n = in.readInt(), t = in.readInt(); Mesh x = new Mesh();
                    x.pos = new float[n * 3]; x.nrm = new float[n * 3]; x.col = new int[n * 3];
                    x.kind = new int[n]; x.phase = new int[n]; x.tint = new int[n]; x.tri = new int[t * 3];
                    for (int i = 0; i < n; i++) {
                        for (int k = 0; k < 3; k++) x.pos[i * 3 + k] = in.readFloat();
                        for (int k = 0; k < 3; k++) x.nrm[i * 3 + k] = in.readFloat();
                        for (int k = 0; k < 3; k++) x.col[i * 3 + k] = in.readUnsignedByte();
                        x.kind[i] = in.readUnsignedByte(); x.phase[i] = in.readUnsignedByte(); x.tint[i] = in.readUnsignedByte();
                    }
                    for (int i = 0; i < t * 3; i++) x.tri[i] = in.readInt();
                    m[p] = x;
                }
            }
        } catch (Exception ex) { m = new Mesh[0]; }
        MESHES.put(name, m);
        return m;
    }

    private static int clamp(float v) { return v < 0 ? 0 : (v > 255 ? 255 : (int) v); }

    private static void draw(Mesh m, PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, float t) {
        int n = m.kind.length;
        int[] r = new int[n], g = new int[n], b = new int[n], lt = new int[n];
        for (int i = 0; i < n; i++) {
            float cr = m.col[i * 3], cg = m.col[i * 3 + 1], cb = m.col[i * 3 + 2];
            int k = m.kind[i];
            lt[i] = light;
            if (k != 0) {
                lt[i] = LightTexture.FULL_BRIGHT;
                float ph = m.phase[i] / 256f;
                float s = (float) Math.sin(2 * Math.PI * (t / 1.2f + ph));
                switch (k) {
                    case 1 -> { float f = .22f + (float) Math.pow(Math.max(0, s), 3); float q = Math.max(0, s) * 255f;
                        cr = cr * f + .1f * q; cg = cg * f + .12f * q; cb = cb * f + .2f * q; }
                    case 2 -> { float f = .72f + .4f * s; cr *= f; cg *= f; cb *= f; }
                    case 3 -> { int fr = (int) (t / .15f) % 8; float f = ((fr + 2 * (m.phase[i] % 4)) % 8) < 2 ? 1.25f : .35f; cr *= f; cg *= f; cb *= f; }
                    case 4 -> { float w = .5f + .5f * (float) Math.sin(2 * Math.PI * (t / .8f + ph)); float[][] fl = FLAME[m.tint[i] % 3];
                        cr = (fl[0][0] * (1 - w) + fl[1][0] * w) * 255f; cg = (fl[0][1] * (1 - w) + fl[1][1] * w) * 255f; cb = (fl[0][2] * (1 - w) + fl[1][2] * w) * 255f; }
                    default -> { }
                }
            }
            r[i] = clamp(cr); g[i] = clamp(cg); b[i] = clamp(cb);
        }
        for (int f = 0; f < m.tri.length; f += 3) {
            for (int c = 0; c < 4; c++) {
                int i = m.tri[f + (c == 3 ? 2 : c)];
                vc.addVertex(pose, m.pos[i * 3], m.pos[i * 3 + 1], m.pos[i * 3 + 2]).setColor(r[i], g[i], b[i], 255).setUv(.5f, .5f)
                  .setOverlay(overlay).setLight(lt[i]).setNormal(pose, 0f, 1f, 0f);
            }
        }
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        UniqAnim.Part[] parts = UniqAnim.PARTS.get(name);
        if (parts == null) return;
        Minecraft mc = Minecraft.getInstance();
        boolean orb = name.equals("storm_katana");   // the ball of lightning has no hand model: the Photon ball at the hand is the whole visual
        if (orb && ctx != ItemDisplayContext.GUI && ctx != ItemDisplayContext.FIXED && ctx != ItemDisplayContext.GROUND) return;
        boolean flat = ctx == ItemDisplayContext.GUI || ctx == ItemDisplayContext.FIXED || orb;
        if (flat) {
            VertexConsumer fv = ItemRenderer.getFoilBufferDirect(buf, ItemBlockRenderTypes.getRenderType(stack, true), true, false);
            mc.getItemRenderer().renderModelLists(model(name + "_icon"), stack, light, overlay, pose, fv);
            return;
        }
        Mesh[] ms = meshes(name);
        if (ms.length == 0) return;
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        float t = (Util.getMillis() % 3_600_000L) / 1000f;
        draw(ms[0], pose.last(), vc, light, overlay, t);
        float use = 0f;
        LocalPlayer p = mc.player;
        if (p != null && p.isUsingItem() && p.getUseItem().getItem() == stack.getItem()) use = Math.min(1f, p.getTicksUsingItem() / (float) Uniqueness.HOLD_TICKS);
        for (int pi = 0; pi < parts.length && pi + 1 < ms.length; pi++) {
            UniqAnim.Part part = parts[pi];
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
            draw(ms[pi + 1], pose.last(), vc, light, overlay, t);
            pose.popPose();
        }
    }
}
