package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/** Client only: the Uniqueness abilities that happen out in the world (the serpent strikes, shadow spikes, the walking doll, the ball of storms). */
public final class UniqWorld {
    private UniqWorld() {}

    public static final int SERPENT = 1, SPIKES = 2, DOLL = 3, STORM = 4, FXPOINT = 5, FXPATH = 6;
    private static final String[] FXN = {"astral_world:twilight_hit", "astral_world:twilight_wave", "astral_world:storm_ball"};
    private static final List<Object[]> PENDING = new ArrayList<>();   // {game tick, Runnable}

    public static void init() { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(UniqWorld::tick); }

    private static void later(long at, Runnable r) { PENDING.add(new Object[]{at, r}); }

    private static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { PENDING.clear(); return; }
        long now = mc.level.getGameTime();
        for (int i = PENDING.size() - 1; i >= 0; i--) { Object[] o = PENDING.get(i); if ((long) o[0] <= now) { PENDING.remove(i); try { ((Runnable) o[1]).run(); } catch (Throwable ignored) { } } }
    }

    private static Vec3 lerpPos(Fx f, float k) { return new Vec3(Mth.lerp(k, f.x, f.x2), Mth.lerp(k, f.y, f.y2), Mth.lerp(k, f.z, f.z2)); }
    private record Fx(int kind, int id, int id2, double x, double y, double z, double x2, double y2, double z2, int dur, long start) {}
    private static final List<Fx> LIVE = new ArrayList<>();

    public static void add(int kind, int id, int id2, double x, double y, double z, double x2, double y2, double z2, int dur) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Fx f = new Fx(kind, id, id2, x, y, z, x2, y2, z2, dur, mc.level.getGameTime());
        if (kind == FXPOINT || kind == FXPATH || (kind == STORM && LotmFx.available())) { photon(f, mc); return; }
        LIVE.add(f);
    }

    /** The effects that are Photon: started here, killed when they are over. */
    private static void photon(Fx f, Minecraft mc) {
        long now = mc.level.getGameTime();
        if (f.kind == FXPOINT) {
            Object h = LotmFx.start(FXN[f.id], f.x, f.y, f.z, (float) f.x2);
            later(now + f.dur, () -> LotmFx.kill(h));
        } else if (f.kind == FXPATH) {
            Object h = LotmFx.carry(FXN[f.id], pt -> lerpPos(f, Mth.clamp((Minecraft.getInstance().level.getGameTime() - f.start + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)) / Math.max(1f, f.dur), 0f, 1f)), 1f);
            later(now + f.dur, () -> LotmFx.kill(h));
        } else {   // the storm: a small ball flies, then it swells into the great sphere
            int fly = f.id; float R = f.id2 / 10f;
            Object ball = LotmFx.carry(FXN[2], pt -> lerpPos(f, Mth.clamp((Minecraft.getInstance().level.getGameTime() - f.start + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)) / Math.max(1f, fly), 0f, 1f)), .7f);
            later(now + fly, () -> {
                LotmFx.kill(ball);
                final Object[] big = {LotmFx.start(FXN[2], f.x2, f.y2, f.z2, R * .3f)};
                for (int i = 1; i <= 10; i++) { final int k = i; later(mc.level.getGameTime() + i * 2, () -> { LotmFx.kill(big[0]); big[0] = LotmFx.start(FXN[2], f.x2, f.y2, f.z2, R * (.3f + .7f * Math.min(1f, k / 8f))); }); }   // it swells
                later(now + f.dur, () -> LotmFx.kill(big[0]));
            });
        }
    }

    /** True while this player's doll is out walking (so it is not also drawn on the shoulder). */
    public static boolean dollOut(int playerId) {
        for (Fx f : LIVE) if (f.kind == DOLL && f.id == playerId) return true;
        return false;
    }

    private static float ease(float p) { p = Mth.clamp(p, 0f, 1f); return 1f - (1f - p) * (1f - p) * (1f - p); }

    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIVE.clear(); return; }
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        PoseStack ps = e.getPoseStack();
        UniqGfx.light = 0xF000F0;
        LIVE.removeIf(f -> {
            float t = mc.level.getGameTime() - f.start + pt;
            if (t >= f.dur) return true;
            try {
                switch (f.kind) {
                    case SERPENT -> serpent(ps, buf, f, t, cam, mc, pt);
                    case SPIKES -> spikes(ps, buf, f, t, cam);
                    case DOLL -> doll(ps, buf, f, t, cam, mc, pt);
                    case STORM -> storm(ps, buf, f, t, cam);
                    default -> { }
                }
            } catch (Throwable ignored) { }
            return false;
        });
        buf.endBatch(RenderType.entityTranslucent(UniqGfx.WHITE));
    }

    private static Vec3 pos(Entity en, float pt, double up) { return new Vec3(Mth.lerp(pt, en.xo, en.getX()), Mth.lerp(pt, en.yo, en.getY()) + up, Mth.lerp(pt, en.zo, en.getZ())); }

    private static void serpent(PoseStack ps, MultiBufferSource buf, Fx f, float t, Vec3 cam, Minecraft mc, float pt) {
        Entity dst = mc.level.getEntity(f.id2);
        Vec3 g = dst != null ? pos(dst, pt, 0) : new Vec3(f.x2, f.y2, f.z2);
        double hc = dst != null ? dst.getBbHeight() * .55 : 1.0;
        float out = f.dur * .42f;
        float tau = t < out ? ease(t / out) : 1f - ease((t - f.dur * .58f) / (f.dur * .42f));
        if (tau <= .01f) return;
        double sc = 2.6, a0 = f.id2 * 2.1; Vec3 off = new Vec3(Math.cos(a0) * 2.4, 0, Math.sin(a0) * 2.4), side = new Vec3(-off.z, 0, off.x).normalize();
        int n = 26; float[][] pts = new float[n][3];
        for (int i = 0; i < n; i++) {   // it comes up out of the ground beside them and strikes upward through them
            float ti = Math.max(0f, tau - (n - 1 - i) * .032f);
            double hor = 1.0 - ti * ti, ver = Math.sqrt(ti);
            Vec3 pp = g.add(off.scale(hor)).add(0, -3.2 + (hc + 3.2) * ver, 0).add(side.scale(Math.sin(ti * 12 + t * .3) * (1 - ti) * .7));
            Vec3 rel = pp.subtract(g).scale(16 / sc);
            pts[i] = new float[]{(float) rel.x, (float) rel.y, (float) rel.z};
        }
        ps.pushPose(); ps.translate(g.x - cam.x, g.y - cam.y, g.z - cam.z); ps.scale((float) sc, (float) sc, (float) sc);
        UniqGfx.serpent(ps, buf, pts, tau > .96f ? 4f : 40f, t);
        ps.popPose();
    }

    private static void spikes(PoseStack ps, MultiBufferSource buf, Fx f, float t, Vec3 cam) {
        float g = Math.min(ease(t / 5f), ease((f.dur - t) / 8f));
        if (g <= .01f) return;
        ps.pushPose(); ps.translate(f.x - cam.x, f.y - cam.y + .03, f.z - cam.z);
        UniqGfx.box(ps, buf, 36, .3f, 36, 0xC8100618);
        for (int k = 0; k < 7; k++) {
            float a = k * (float) Math.PI * 2 / 7 + .4f, tilt = .5f + .12f * UniqGfx.rnd(k + f.id), len = (52f + 14f * UniqGfx.rnd(k + 9)) * g;
            float bx = (float) Math.cos(a) * 15, bz = (float) Math.sin(a) * 15;
            float dx = (float) (-Math.cos(a) * Math.sin(tilt)), dy = (float) Math.cos(tilt), dz = (float) (-Math.sin(a) * Math.sin(tilt));
            float px = bx, py = 0, pz = bz;
            for (int j = 0; j < 4; j++) {   // a black spear tapering to a violet point
                float l = len / 4, nx = px + dx * l, ny = py + dy * l, nz = pz + dz * l;
                UniqGfx.seg(ps, buf, px, py, pz, nx, ny, nz, 5f - j * 1.2f, j == 3 ? 0xFF7A38B8 : 0xFF0A0410); px = nx; py = ny; pz = nz;
            }
        }
        ps.popPose();
    }

    private static void doll(PoseStack ps, MultiBufferSource buf, Fx f, float t, Vec3 cam, Minecraft mc, float pt) {
        Entity src = mc.level.getEntity(f.id), dst = mc.level.getEntity(f.id2);
        Vec3 s = src != null ? pos(src, pt, .1) : new Vec3(f.x, f.y, f.z), d = dst != null ? pos(dst, pt, 0) : new Vec3(f.x2, f.y2, f.z2);
        float walk = Math.max(1f, f.dur - 16f), tau = Mth.clamp(t / walk, 0f, 1f);
        Vec3 dir = d.subtract(s), p = s.add(dir.scale(tau));
        float yaw = (float) Math.atan2(dir.x, dir.z);
        ps.pushPose(); ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z); ps.mulPose(Axis.YP.rotation(yaw)); ps.scale(1.7f, 1.7f, 1.7f);
        UniqGfx.doll(ps, buf, t, tau < 1f ? 1f : 0f, false, tau >= 1f);
        ps.popPose();
    }

    private static void storm(PoseStack ps, MultiBufferSource buf, Fx f, float t, Vec3 cam) {
        float fly = f.id, R = f.id2 / 10f;
        if (t < fly) {
            float k = t / fly; Vec3 p = new Vec3(Mth.lerp(k, f.x, f.x2), Mth.lerp(k, f.y, f.y2), Mth.lerp(k, f.z, f.z2));
            ps.pushPose(); ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            UniqGfx.stormBall(ps, buf, t, 9f, 4, 6);
            ps.popPose();
            return;
        }
        float u = t - fly, g = ease(u / 22f) * Mth.clamp((f.dur - fly - u) / 16f, 0f, 1f);
        if (g <= .02f) return;
        ps.pushPose(); ps.translate(f.x2 - cam.x, f.y2 - cam.y, f.z2 - cam.z);
        UniqGfx.stormBall(ps, buf, t, R * 16f * g, 16, 26);
        ps.popPose();
    }
}
