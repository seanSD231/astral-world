package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The Uniquenesses you wear show on your body: shadow tentacles at your feet, a sun over the shoulder, a wheel at your back... */
public final class WornClient {
    private WornClient() {}

    private static final Map<UUID, String> WORN = new HashMap<>();
    private static final Map<UUID, Long> SPIN = new HashMap<>();
    private static final Map<UUID, Long> STRIKE = new HashMap<>();
    private static final Map<UUID, Long> SHADOW = new HashMap<>();
    private static final Map<UUID, Object> SUNFX = new HashMap<>();

    private static float since(UUID id, Map<UUID, Long> m, float pt) {
        Long t = m.get(id); Minecraft mc = Minecraft.getInstance();
        return t == null || mc.level == null ? 1e9f : mc.level.getGameTime() - t + pt;
    }

    /** Refresh who is wearing what (every 10 ticks; Curios is looked up by reflection) and keep a Photon mini sun on every Mini Sun wearer's shoulder. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (mc.player.tickCount % 10 == 0) for (net.minecraft.client.player.AbstractClientPlayer p : mc.level.players()) {
            ItemStack st = WornUniq.findWorn(p);
            if (st.isEmpty()) WORN.remove(p.getUUID()); else WORN.put(p.getUUID(), BuiltInRegistries.ITEM.getKey(st.getItem()).getPath());
        }
        for (net.minecraft.client.player.AbstractClientPlayer p : mc.level.players()) {
            boolean sun = "spear_of_sun".equals(WORN.get(p.getUUID())) && !p.isInvisible();
            Object h = SUNFX.get(p.getUUID());
            if (sun && h == null) {
                UUID id = p.getUUID();
                Object nh = LotmFx.carry("bard/flaring_sun", pt -> {
                    Entity e = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getPlayerByUUID(id);
                    return e == null ? Vec3.ZERO : shoulder(e, pt);
                }, .1f);
                SUNFX.put(p.getUUID(), nh == null ? "none" : nh);
            } else if (!sun && h != null) { if (!"none".equals(h)) LotmFx.kill(h); SUNFX.remove(p.getUUID()); }
        }
        SUNFX.keySet().removeIf(id -> {   // players who left
            if (mc.level.getPlayerByUUID(id) != null) return false;
            Object h = SUNFX.get(id); if (h != null && !"none".equals(h)) LotmFx.kill(h); return true;
        });
    }

    private static Vec3 shoulder(Entity e, float pt) {
        float yaw = (float) Math.toRadians(e instanceof net.minecraft.world.entity.LivingEntity le ? le.yBodyRot : e.getYRot());
        double x = net.minecraft.util.Mth.lerp(pt, e.xo, e.getX()), y = net.minecraft.util.Mth.lerp(pt, e.yo, e.getY()), z = net.minecraft.util.Mth.lerp(pt, e.zo, e.getZ());
        return new Vec3(x - Math.cos(yaw) * .5, y + e.getBbHeight() + .22 + Math.sin(e.tickCount * .1) * .03, z - Math.sin(yaw) * .5);
    }

    public static void fx(int entityId, int kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(entityId);
        if (e == null) return;
        (kind == 2 ? SPIN : kind == 3 ? SHADOW : STRIKE).put(e.getUUID(), mc.level.getGameTime());
    }

    public static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buf, int light, AbstractClientPlayer p, float limb, float limbAmt, float pt, float age, float headYaw, float headPitch) {
            String id = WORN.get(p.getUUID());
            if (id == null || p.isInvisible()) return;
            UniqGfx.light = 0xF000F0;
            float t = p.tickCount + pt;
            switch (id) {
                case "spear_of_sun" -> { if ("none".equals(SUNFX.get(p.getUUID()))) sunFallback(pose, buf, t); }
                case "bone_greatsword" -> {
                    float s = since(p.getUUID(), STRIKE, pt); float k = s < 12 ? 1 - s / 12f : 0;
                    pose.pushPose(); body(pose); UniqGfx.tr(pose, 0, 24f, 0); pose.mulPose(Axis.XP.rotationDegrees(180));
                    UniqGfx.serpentCoil(pose, buf, t, k); pose.popPose();
                }
                case "snake_rifle" -> wheel(pose, buf, p, t, pt);
                case "chained_bow" -> {
                    if (UniqWorld.dollOut(p.getId())) break;
                    pose.pushPose(); body(pose); UniqGfx.tr(pose, 6.4f, .6f, 0); pose.mulPose(Axis.XP.rotationDegrees(180)); pose.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(t * .08) * 3));
                    UniqGfx.doll(pose, buf, t, 0, true, false); pose.popPose();
                }
                case "storm_katana" -> {
                    pose.pushPose(); getParentModel().leftArm.translateAndRotate(pose); UniqGfx.tr(pose, 1f, 11f, 0);
                    UniqGfx.stormBall(pose, buf, t, 3.6f, 4, 5); pose.popPose();
                }
                case "shadow_sword" -> shadows(pose, buf, light, p, t, pt);
                default -> { }
            }
        }

        /** The body-space frame: origin at the neck, y down, the back toward +z, the left side toward +x. */
        private void body(PoseStack pose) { getParentModel().body.translateAndRotate(pose); }

        private void sunFallback(PoseStack pose, MultiBufferSource buf, float t) {
            pose.pushPose(); body(pose); UniqGfx.tr(pose, -7f, -9f, 0);
            UniqGfx.sphere(pose, buf, 2.4f, 7, 12, 0xFFFFC040); UniqGfx.sphere(pose, buf, 3.4f, 7, 12, UniqGfx.rgba(0xFF9030, .5f));
            pose.popPose();
        }

        private void wheel(PoseStack pose, MultiBufferSource buf, AbstractClientPlayer p, float t, float pt) {
            pose.pushPose(); body(pose); UniqGfx.tr(pose, 0, 5f, 5.4f); pose.mulPose(Axis.XP.rotationDegrees(180));
            float ang = t * .8f, d = since(p.getUUID(), SPIN, pt);
            if (d < 60) ang += (60 - d) * (60 - d) * .25f;   // spins fast, then slows to a stop
            pose.pushPose(); pose.mulPose(Axis.ZP.rotationDegrees(ang)); UniqGfx.wheel(pose, buf, 12f); pose.popPose();
            UniqGfx.pointer(pose, buf, 12f);
            pose.popPose();
        }

        /** Tentacles of shadow rising from your own shadow; when the shadow goes (darkness, invisibility) they go with it. */
        private void shadows(PoseStack pose, MultiBufferSource buf, int light, AbstractClientPlayer p, float t, float pt) {
            float lt = Math.max(LightTexture.block(light), LightTexture.sky(light));
            float k = Math.min(1f, lt / 5f);
            if (k <= .05f || p.isSpectator()) return;
            pose.pushPose(); body(pose); UniqGfx.tr(pose, 0, 24f, 0); pose.mulPose(Axis.XP.rotationDegrees(180));
            UniqGfx.tr(pose, 0, .15f, 0);
            for (int i = 0; i < 9; i++) {
                float a = i * 40f + t * 3f, r = 5f + 2.5f * (float) Math.sin(t * .13 + i * 1.9);
                pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(a)); UniqGfx.tr(pose, r, 0, 0);
                UniqGfx.tentacle(pose, buf, t, i, (22f + 16f * (float) Math.sin(t * .07 + i * 2.1) * (float) Math.sin(t * .07 + i * 2.1) + 8f) * k, 2.6f, 0xFF0A0410, 0xFF6A30A8);
                pose.popPose();
            }
            float sp = since(p.getUUID(), SHADOW, pt);
            UniqGfx.box(pose, buf, 15f * k, .3f, 15f * k, UniqGfx.rgba(0x060210, .8f * k));
            pose.mulPose(Axis.YP.rotationDegrees(45)); UniqGfx.box(pose, buf, 13f * k, .35f, 13f * k, UniqGfx.rgba(0x060210, .8f * k));
            if (sp < 100) {   // the shadow floods out to 20 blocks, holds, then draws back
                float g = sp < 25 ? sp / 25f : sp < 85 ? 1f : 1 - (sp - 85) / 15f, side = 20f * 2 * 16 * g;
                pose.mulPose(Axis.YP.rotationDegrees(-45)); UniqGfx.tr(pose, 0, .2f, 0);
                UniqGfx.box(pose, buf, side, .25f, side, UniqGfx.rgba(0x060210, .55f)); pose.mulPose(Axis.YP.rotationDegrees(45)); UniqGfx.box(pose, buf, side * .92f, .3f, side * .92f, UniqGfx.rgba(0x060210, .5f));
            }
            pose.popPose();
        }
    }
}
