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
    private static final Map<UUID, String> SUNKIND = new HashMap<>();
    private static final Map<UUID, Float> SHADOWR = new HashMap<>();

    private static float since(UUID id, Map<UUID, Long> m, float pt) {
        Long t = m.get(id); Minecraft mc = Minecraft.getInstance();
        return t == null || mc.level == null ? 1e9f : mc.level.getGameTime() - t + pt;
    }

    /** Refresh who is wearing what (every 10 ticks; Curios is looked up by reflection) and keep a Photon mini sun on every Mini Sun wearer's shoulder. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (mc.player.tickCount % 10 == 0) for (net.minecraft.client.player.AbstractClientPlayer p : mc.level.players()) {
            ItemStack st = WornUniq.findShown(p);
            if (st.isEmpty()) WORN.remove(p.getUUID()); else WORN.put(p.getUUID(), BuiltInRegistries.ITEM.getKey(st.getItem()).getPath());
        }
        for (net.minecraft.client.player.AbstractClientPlayer p : mc.level.players()) {
            String worn = WORN.get(p.getUUID());
            boolean sun = "spear_of_sun".equals(worn) && !p.isInvisible(), ball = "storm_katana".equals(worn) && !p.isInvisible();
            String want = sun ? "sun" : ball ? "ball" : null;
            Object h = SUNFX.get(p.getUUID());
            String have = SUNKIND.get(p.getUUID());
            if (h != null && !java.util.Objects.equals(want, have)) { if (!"none".equals(h)) LotmFx.kill(h); SUNFX.remove(p.getUUID()); SUNKIND.remove(p.getUUID()); h = null; }
            if (want != null && h == null) {
                UUID id = p.getUUID();
                Object nh = LotmFx.carry(sun ? "bard/flaring_sun" : "astral_world:storm_ball", pt -> {
                    Entity e = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getPlayerByUUID(id);
                    return e == null ? Vec3.ZERO : sun ? shoulder(e, pt) : hand(e, pt);
                }, sun ? .1f : .035f);
                SUNFX.put(p.getUUID(), nh == null ? "none" : nh); SUNKIND.put(p.getUUID(), want);
            }
        }
        SUNFX.keySet().removeIf(id -> {   // players who left
            if (mc.level.getPlayerByUUID(id) != null) return false;
            Object h = SUNFX.get(id); if (h != null && !"none".equals(h)) LotmFx.kill(h); return true;
        });
    }

    /** About where the left hand is, for the ball of storms. */
    private static Vec3 hand(Entity e, float pt) {
        float yaw = (float) Math.toRadians(e instanceof net.minecraft.world.entity.LivingEntity le ? le.yBodyRot : e.getYRot());
        double x = net.minecraft.util.Mth.lerp(pt, e.xo, e.getX()), y = net.minecraft.util.Mth.lerp(pt, e.yo, e.getY()), z = net.minecraft.util.Mth.lerp(pt, e.zo, e.getZ());
        return new Vec3(x + Math.cos(yaw) * .42 - Math.sin(yaw) * .3, y + e.getBbHeight() * .5, z + Math.sin(yaw) * .42 + Math.cos(yaw) * .3);
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
        (kind == 2 ? SPIN : kind == 3 || kind == 5 ? SHADOW : STRIKE).put(e.getUUID(), mc.level.getGameTime());
        if (kind == 3 || kind == 5) SHADOWR.put(e.getUUID(), kind == 5 ? 30f : 20f);   // 5: the Seq 1/0 flood, half again as far
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
                    float s = since(p.getUUID(), STRIKE, pt); float k = s < 8 ? s / 8f : s < 100 ? 1f : s < 112 ? 1 - (s - 100) / 12f : 0;   // open for the five seconds the hands last
                    pose.pushPose(); body(pose); UniqGfx.tr(pose, 0, 24f, 0); pose.mulPose(Axis.XP.rotationDegrees(180));
                    UniqGfx.tr(pose, -11f, 17f, 9f); pose.scale(.62f, .62f, .62f);   // hanging in the air by the right hand, a little ahead
                    UniqGfx.serpentBook(pose, buf, t, k); pose.popPose();
                }
                case "snake_rifle" -> wheel(pose, buf, p, t, pt);
                case "chained_bow" -> {
                    if (UniqWorld.dollOut(p.getId())) break;
                    pose.pushPose(); body(pose); UniqGfx.tr(pose, 6.4f, .6f, 0); pose.mulPose(Axis.XP.rotationDegrees(180)); pose.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(t * .08) * 3));
                    UniqGfx.doll(pose, buf, t, 0, true, false); pose.popPose();
                }
                case "storm_katana" -> {
                    if (!"none".equals(SUNFX.get(p.getUUID()))) break;
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

        /** Tentacles of shadow rising out of the ordinary shadow under your feet: they follow it (gone when you leave the ground or the light), reach farther and come thicker at night. */
        private void shadows(PoseStack pose, MultiBufferSource buf, int light, AbstractClientPlayer p, float t, float pt) {
            Minecraft mc = Minecraft.getInstance();
            float lt = Math.max(LightTexture.block(light), LightTexture.sky(light));
            if (p.isSpectator() || mc.level == null || lt < 1f) return;
            double gy = groundBelow(mc.level, p);
            float h = (float) (p.getY() - gy);
            float k = Math.min(1f, lt / 5f) * Math.max(0f, 1f - h / 1.2f);   // the ordinary shadow fades out about a block up
            if (k <= .04f) return;
            long dt = mc.level.getDayTime() % 24000L;
            float night = dt < 12000 ? 0f : dt < 13500 ? (dt - 12000) / 1500f : dt < 22500 ? 1f : dt < 24000 ? 1f - (dt - 22500) / 1500f : 0f;
            int n = 7 + Math.round(8 * night);
            pose.pushPose(); body(pose); UniqGfx.tr(pose, 0, 24f + h * 16f, 0); pose.mulPose(Axis.XP.rotationDegrees(180));
            UniqGfx.tr(pose, 0, .2f, 0);
            UniqGfx.pool(pose, buf, (9f + 12f * night) * k, t, 3, .9f * k);   // the pool they rise from
            for (int i = 0; i < n; i++) {
                float a = i * (360f / n) + t * 2.5f, r = (5f + 9f * night) + (2.5f + 3f * night) * (float) Math.sin(t * .13 + i * 1.9);
                float hh = (6f + 5f * night + 4f * (float) Math.pow(Math.sin(t * .07 + i * 2.1), 2)) * k;
                pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(a)); UniqGfx.tr(pose, r, 0, 0);
                UniqGfx.tentacle(pose, buf, t, i, hh * (i % 3 == 0 ? 1.5f : 1f), i % 3 == 0 ? 3.1f : 2.2f, 0xFF100204, 0xFFC01820);
                pose.popPose();
            }
            float sp = since(p.getUUID(), SHADOW, pt);
            if (sp < 100) {   // the shadow floods out (20 blocks, 30 at Seq 1/0), holds, then draws back: a round creeping pool with tentacles groping up all over it
                float g = sp < 25 ? ease3(sp / 25f) : sp < 85 ? 1f : 1 - (sp - 85) / 15f, rad = SHADOWR.getOrDefault(p.getUUID(), 20f) * 16 * g;
                UniqGfx.pool(pose, buf, rad, t, 11, .8f);
                int m = rad > 400 ? 40 : 26;
                for (int i = 0; i < m; i++) {
                    float a = i * 2.39996f, rr = rad * (float) Math.sqrt((i + .5f) / m) * .95f;
                    pose.pushPose(); UniqGfx.tr(pose, (float) Math.cos(a) * rr, 0, (float) Math.sin(a) * rr);
                    UniqGfx.tentacle(pose, buf, t, i + 30, (22f + 16f * UniqGfx.rnd(i + 5)) * g, 3.4f, 0xFF100204, 0xFFC01820);
                    pose.popPose();
                }
            }
            pose.popPose();
        }

        private static float ease3(float x) { x = Math.max(0f, Math.min(1f, x)); return 1f - (1f - x) * (1f - x) * (1f - x); }

        /** The top of the floor under the player, up to four blocks down (far below if there is none). */
        private static double groundBelow(net.minecraft.world.level.Level lv, AbstractClientPlayer p) {
            net.minecraft.core.BlockPos.MutableBlockPos bp = new net.minecraft.core.BlockPos.MutableBlockPos(p.getX(), p.getY() - 1e-4, p.getZ());
            for (int i = 0; i < 6; i++) {
                net.minecraft.world.level.block.state.BlockState st = lv.getBlockState(bp);
                if (!st.getCollisionShape(lv, bp).isEmpty()) return bp.getY() + st.getCollisionShape(lv, bp).max(net.minecraft.core.Direction.Axis.Y);
                bp.move(0, -1, 0);
            }
            return p.getY() - 99;
        }
    }
}
