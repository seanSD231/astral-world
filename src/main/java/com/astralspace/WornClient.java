package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The Uniquenesses you wear show on your body: shadows at your feet, a sun over the shoulder, a wheel at your back... */
public final class WornClient {
    private WornClient() {}

    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "textures/entity/white.png");
    private static final Map<UUID, String> WORN = new HashMap<>();
    private static final Map<UUID, Long> SPIN = new HashMap<>();
    private static final Map<UUID, Long> STRIKE = new HashMap<>();
    private static final Map<UUID, Long> SHADOW = new HashMap<>();
    private static final Map<UUID, Long> DOLL = new HashMap<>();

    private static float since(UUID id, Map<UUID, Long> m, float pt) {
        Long t = m.get(id); Minecraft mc = Minecraft.getInstance();
        return t == null || mc.level == null ? 1e9f : mc.level.getGameTime() - t + pt;
    }

    /** Refresh who is wearing what (every 10 ticks; Curios is looked up by reflection). */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.player.tickCount % 10 != 0) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            ItemStack st = WornUniq.findWorn(p);
            if (st.isEmpty()) WORN.remove(p.getUUID()); else WORN.put(p.getUUID(), BuiltInRegistries.ITEM.getKey(st.getItem()).getPath());
        }
    }

    public static void fx(int entityId, int kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(entityId);
        if (e == null) return;
        (kind == 2 ? SPIN : kind == 3 ? SHADOW : kind == 4 ? DOLL : STRIKE).put(e.getUUID(), mc.level.getGameTime());
    }

    public static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buf, int light, AbstractClientPlayer p, float limb, float limbAmt, float pt, float age, float headYaw, float headPitch) {
            String id = WORN.get(p.getUUID());
            if (id == null || p.isInvisible()) return;
            float t = p.tickCount + pt;
            switch (id) {
                case "spear_of_sun" -> sun(pose, buf, t);
                case "bone_greatsword" -> serpent(pose, buf, p, t);
                case "snake_rifle" -> wheel(pose, buf, p, t, pt);
                case "chained_bow" -> { if (since(p.getUUID(), DOLL, pt) > 50) doll(pose, buf, t); }
                case "storm_katana" -> lightning(pose, buf, t);
                case "shadow_sword" -> shadows(pose, buf, t, since(p.getUUID(), SHADOW, pt));
                default -> { }
            }
        }

        private B glow(MultiBufferSource b) { return new B(b, RenderType.eyes(WHITE)); }
        private B solid(MultiBufferSource b) { return new B(b, RenderType.entityTranslucent(WHITE)); }

        /** The body-space frame (y down, feet at +24/16, back is +z, left is +x). */
        private void body(PoseStack pose) { getParentModel().body.translateAndRotate(pose); }

        private void sun(PoseStack pose, MultiBufferSource buf, float t) {
            pose.pushPose(); body(pose);
            pose.translate(-7f / 16, -9f / 16 + Math.sin(t * 0.1) * 0.03, 0);
            pose.mulPose(Axis.YP.rotationDegrees(t * 3));
            B g = glow(buf);
            box(pose, g, 3, 0xFFFFA020, 0xF000F0);
            pose.mulPose(Axis.YP.rotationDegrees(45)); pose.mulPose(Axis.XP.rotationDegrees(45));
            box(pose, g, 3, 0xFFFFD860, 0xF000F0);
            pose.mulPose(Axis.ZP.rotationDegrees(45));
            box(pose, g, 4.6f, 0x80FF9030, 0xF000F0);
            pose.popPose();
        }

        private void lightning(PoseStack pose, MultiBufferSource buf, float t) {
            pose.pushPose(); getParentModel().leftArm.translateAndRotate(pose);
            pose.translate(1f / 16, 11f / 16, 0);
            B g = glow(buf);
            float f = 1 + (float) Math.sin(t * 1.7) * 0.12f;
            pose.mulPose(Axis.YP.rotationDegrees(t * 9)); pose.mulPose(Axis.XP.rotationDegrees(t * 6));
            box(pose, g, 3.2f * f, 0xFFB8D8FF, 0xF000F0);
            pose.mulPose(Axis.ZP.rotationDegrees(45)); pose.mulPose(Axis.XP.rotationDegrees(45));
            box(pose, g, 4.4f * f, 0x908060FF, 0xF000F0);
            for (int i = 0; i < 4; i++) {   // little sparks off the ball
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(i * 90 + t * 20)); pose.mulPose(Axis.ZP.rotationDegrees(30 + 25 * i));
                pose.translate(3.2f / 16, 0, 0);
                box(pose, g, 1.1f, 0xFFFFFFFF, 0xF000F0);
                pose.popPose();
            }
            pose.popPose();
        }

        private void shadows(PoseStack pose, MultiBufferSource buf, float t, float spread) {
            pose.pushPose(); body(pose);
            pose.translate(0, 24f / 16 - 0.01f, 0);   // ground, under the feet
            B s = solid(buf);
            for (int i = 0; i < 9; i++) {
                float a = i * 40f + t * 4;
                float r = (5 + 2.5f * (float) Math.sin(t * 0.13 + i * 1.9)) / 16;
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(a));
                pose.translate(r, 0, 0);
                float h = 2.5f + 2.5f * (float) Math.sin(t * 0.2 + i * 2.3);
                for (int k = 0; k < 4; k++) {
                    pose.translate(0, -h / 4 / 16, 0);
                    pose.mulPose(Axis.ZP.rotationDegrees(14 * (float) Math.sin(t * 0.25 + i + k)));
                    box(pose, s, new float[]{1.6f - k * 0.3f, h / 4 + 0.3f, 1.6f - k * 0.3f}, 0xE0120418, 0xF000F0);
                }
                pose.popPose();
            }
            if (spread < 50) {   // the shadow floods out to 20 blocks, then draws back in
                float k = spread < 25 ? spread / 25f : 1 - (spread - 25) / 25f;
                float side = 20f * 2 * 16 * k;
                box(pose, s, new float[]{side, 0.1f, side}, 0x90050008, 0xF000F0);
                pose.mulPose(Axis.YP.rotationDegrees(45));
                box(pose, s, new float[]{side * 0.9f, 0.15f, side * 0.9f}, 0x70050008, 0xF000F0);
            }
            pose.mulPose(Axis.YP.rotationDegrees(t));
            box(pose, s, new float[]{15, 0.15f, 15}, 0xC0090210, 0xF000F0);
            pose.mulPose(Axis.YP.rotationDegrees(45));
            box(pose, s, new float[]{13, 0.2f, 13}, 0xC0090210, 0xF000F0);
            pose.popPose();
        }

        private void serpent(PoseStack pose, MultiBufferSource buf, AbstractClientPlayer p, float t) {
            pose.pushPose(); body(pose);
            pose.translate(0, 24f / 16 - 0.05f, 0);
            Long st = STRIKE.get(p.getUUID());
            float strike = 0;
            if (st != null && Minecraft.getInstance().level != null) strike = Math.max(0, 1 - (Minecraft.getInstance().level.getGameTime() - st) / 12f);
            B s = solid(buf), g = glow(buf);
            int n = 26;
            for (int i = 0; i < n; i++) {
                float u = i / (float) n;
                float a = (float) (u * Math.PI * 4 + t * 0.06);
                float r = (6.5f - 3f * u + strike * 12 * u) / 16;
                float y = -(u * u * 14 + strike * 8 * (1 - u)) / 16;
                float w = 2.3f - 1.1f * u;
                pose.pushPose();
                pose.translate(Math.cos(a) * r, y, Math.sin(a) * r);
                pose.mulPose(Axis.YP.rotationDegrees(-(float) Math.toDegrees(a)));
                box(pose, s, new float[]{1.6f, w, w + 0.4f}, i % 2 == 0 ? 0xFF1C8C5C : 0xFF16724A, 0xF000F0);
                if (i % 3 == 0) {   // feathers
                    pose.mulPose(Axis.ZP.rotationDegrees(25));
                    box(pose, g, new float[]{0.4f, 3.2f - 1.5f * u, 1.1f}, i % 6 == 0 ? 0xFFE04040 : 0xFF40C0FF, 0xF000F0);
                }
                pose.popPose();
            }
            pose.popPose();
        }

        private void wheel(PoseStack pose, MultiBufferSource buf, AbstractClientPlayer p, float t, float pt) {
            pose.pushPose(); body(pose);
            pose.translate(0, 4f / 16, 9f / 16);
            Long st = SPIN.get(p.getUUID());
            float ang = t * 0.8f;
            if (st != null && Minecraft.getInstance().level != null) {
                float d = Minecraft.getInstance().level.getGameTime() - st + pt;
                if (d < 60) ang += (60 - d) * (60 - d) * 0.25f;   // spins fast, then slows to a stop
            }
            pose.mulPose(Axis.ZP.rotationDegrees(ang));
            B s = solid(buf), g = glow(buf);
            int[] cols = {0xFFE8C040, 0xFFB02020, 0xFFE8C040, 0xFF2040B0};
            for (int i = 0; i < 12; i++) {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(i * 30));
                pose.translate(0, -12f / 16, 0);
                box(pose, g, new float[]{3.4f, 2.4f, 1.2f}, cols[i % 4], 0xF000F0);
                pose.popPose();
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(i * 30));
                pose.translate(0, -6f / 16, 0);
                box(pose, s, new float[]{0.7f, 9.5f, 0.7f}, 0xFFC89A30, 0xF000F0);
                pose.popPose();
            }
            box(pose, g, 3f, 0xFFFFE070, 0xF000F0);
            pose.popPose();
        }

        private void doll(PoseStack pose, MultiBufferSource buf, float t) {
            pose.pushPose(); body(pose);
            pose.translate(6.5f / 16, -4.2f / 16, 0);   // sitting on the left shoulder
            pose.mulPose(Axis.ZP.rotationDegrees(-6 + (float) Math.sin(t * 0.08) * 3));
            B s = solid(buf);
            box(pose, s, new float[]{3.2f, 4f, 2.2f}, 0xFFD8CBA4, 0xF000F0);                 // wrapped body
            pose.pushPose(); pose.translate(0, -3.6f / 16, 0);
            box(pose, s, new float[]{3.2f, 3.2f, 3.2f}, 0xFFE6DCB8, 0xF000F0);                // head
            pose.translate(0, 0, -1.65f / 16);
            box(pose, s, new float[]{0.7f, 0.7f, 0.2f}, 0xFF201010, 0xF000F0);               // button eye
            pose.popPose();
            for (int i = 0; i < 5; i++) {   // thorns
                pose.pushPose();
                pose.translate(0, (-1.5f + i * 0.8f) / 16, 0);
                pose.mulPose(Axis.YP.rotationDegrees(i * 72 + 20)); pose.mulPose(Axis.ZP.rotationDegrees(i % 2 == 0 ? 40 : -40));
                pose.translate(2f / 16, 0, 0);
                box(pose, s, new float[]{2.4f, 0.35f, 0.35f}, 0xFF2A4A22, 0xF000F0);
                pose.popPose();
            }
            pose.popPose();
        }
    }

    /** A render type to draw with; the buffer is fetched at each draw so interleaving two types never reuses a finished builder. */
    private record B(MultiBufferSource src, RenderType type) { VertexConsumer get() { return src.getBuffer(type); } }

    private static void box(PoseStack pose, B b, float size, int argb, int light) { box(pose, b, new float[]{size, size, size}, argb, light); }

    /** A cuboid centred on the origin; size in pixels (1/16 block). */
    private static void box(PoseStack pose, B buf, float[] sz, int argb, int light) {
        VertexConsumer vc = buf.get();   // fetched fresh for every box, so two render types can interleave
        PoseStack.Pose m = pose.last();
        float x = sz[0] / 32, y = sz[1] / 32, z = sz[2] / 32;
        int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        quad(vc, m, a, r, g, b, light, -x, -y, -z, x, -y, -z, x, y, -z, -x, y, -z, 0, 0, -1);
        quad(vc, m, a, r, g, b, light, x, -y, z, -x, -y, z, -x, y, z, x, y, z, 0, 0, 1);
        quad(vc, m, a, r, g, b, light, -x, -y, z, -x, -y, -z, -x, y, -z, -x, y, z, -1, 0, 0);
        quad(vc, m, a, r, g, b, light, x, -y, -z, x, -y, z, x, y, z, x, y, -z, 1, 0, 0);
        quad(vc, m, a, r, g, b, light, -x, -y, z, x, -y, z, x, -y, -z, -x, -y, -z, 0, -1, 0);
        quad(vc, m, a, r, g, b, light, -x, y, -z, x, y, -z, x, y, z, -x, y, z, 0, 1, 0);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose m, int a, int r, int g, int b, int light,
                             float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny, float nz) {
        v(vc, m, a, r, g, b, light, x0, y0, z0, 0, 0, nx, ny, nz); v(vc, m, a, r, g, b, light, x1, y1, z1, 1, 0, nx, ny, nz);
        v(vc, m, a, r, g, b, light, x2, y2, z2, 1, 1, nx, ny, nz); v(vc, m, a, r, g, b, light, x3, y3, z3, 0, 1, nx, ny, nz);
    }

    private static void v(VertexConsumer vc, PoseStack.Pose m, int a, int r, int g, int b, int light, float x, float y, float z, float u, float w, float nx, float ny, float nz) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, w).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(m, nx, ny, nz);
    }
}
