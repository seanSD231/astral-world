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

    public static final int SERPENT = 1, SPIKES = 2, DOLL = 3, STORM = 4, FXPOINT = 5, FXPATH = 6, SLASH = 7, DOLLTP = 8;
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
        if (kind == DOLL && dur < 0) { LIVE.add(new Fx(DOLLTP, id, id2, x, y, z, x2, y2, z2, -dur, mc.level.getGameTime())); return; }   // a Seq 1/0 doll: it does not walk, it appears
        Fx f = new Fx(kind, id, id2, x, y, z, x2, y2, z2, dur, mc.level.getGameTime());
        if (kind == FXPOINT || kind == FXPATH || (kind == STORM && LotmFx.available())) { photon(f, mc); return; }
        LIVE.add(f);
    }

    /** The effects that are Photon: started here, killed when they are over. For the sword, id2 is its look (bit 8 = unsealed: black and red) and, on a path, its tilt in the low bits. */
    private static void photon(Fx f, Minecraft mc) {
        long now = mc.level.getGameTime();
        boolean dark = (f.id2 & 8) != 0;
        if (f.kind == FXPOINT) {
            float sc = (float) f.x2;
            if (f.id == 4) { LIVE.add(new Fx(SLASH, 4, f.id2, f.x, f.y, f.z, sc, 0, 0, f.dur, f.start)); return; }
            if (f.id == 0) LIVE.add(new Fx(SLASH, 0, f.id2, f.x, f.y, f.z, sc, 0, 0, 12, f.start));
            if (f.id == 3) LIVE.add(new Fx(SLASH, 3, f.id2, f.x, f.y, f.z, sc, 0, 0, f.dur, f.start));
            if (f.id == 6) LIVE.add(new Fx(SLASH, 6, f.id2, f.x, f.y, f.z, sc, 0, 0, 14, f.start));
            Object h = LotmFx.start(f.id == 2 ? "astral_world:storm_ball" : dark ? "astral_world:twilight_hit_dark" : "astral_world:twilight_hit", f.x, f.y, f.z, f.id == 3 ? Math.max(2.4f, sc * .45f) : f.id == 6 ? sc * 1.4f : sc);
            later(now + Math.min(f.dur, 40), () -> LotmFx.kill(h));
        } else if (f.kind == FXPATH) {
            LIVE.add(new Fx(SLASH, f.id == 5 ? 5 : 1, f.id2, f.x, f.y, f.z, f.x2, f.y2, f.z2, f.dur, f.start));
            Object h = LotmFx.carry(dark ? "astral_world:twilight_wave_dark" : "astral_world:twilight_wave", pt -> lerpPos(f, Mth.clamp((Minecraft.getInstance().level.getGameTime() - f.start + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)) / Math.max(1f, f.dur), 0f, 1f)), f.id == 5 ? .4f : 1f);
            later(now + f.dur, () -> LotmFx.kill(h));
        } else {   // the storm: a ball of lightning flies, then it bursts into the great storm
            int fly = f.id; float R = f.id2 / 10f;
            Object ball = LotmFx.carry("astral_world:storm_ball", pt -> lerpPos(f, Mth.clamp((Minecraft.getInstance().level.getGameTime() - f.start + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)) / Math.max(1f, fly), 0f, 1f)), .8f);
            later(now + fly, () -> {
                LotmFx.kill(ball);
                Object flash = LotmFx.start("astral_world:storm_burst", f.x2, f.y2, f.z2, R);
                later(mc.level.getGameTime() + 30, () -> LotmFx.kill(flash));
                final Object[] big = {LotmFx.start("astral_world:storm_sphere", f.x2, f.y2, f.z2, R * .3f)};
                for (int i = 1; i <= 8; i++) { final int k = i; later(mc.level.getGameTime() + i * 2, () -> { LotmFx.kill(big[0]); big[0] = LotmFx.start("astral_world:storm_sphere", f.x2, f.y2, f.z2, R * (.3f + .7f * k / 8f)); }); }   // it swells
                later(now + f.dur, () -> LotmFx.kill(big[0]));
            });
        }
    }

    /** True while this player's doll is out walking (so it is not also drawn on the shoulder). */
    public static boolean dollOut(int playerId) {
        for (Fx f : LIVE) if ((f.kind == DOLL || f.kind == DOLLTP) && f.id == playerId) return true;
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
            if (t < 0) return false;
            if (t >= f.dur) return true;
            try {
                switch (f.kind) {
                    case SERPENT -> serpent(ps, buf, f, t, cam, mc, pt);
                    case SPIKES -> spikes(ps, buf, f, t, cam);
                    case DOLL -> doll(ps, buf, f, t, cam, mc, pt);
                    case DOLLTP -> dollTp(ps, buf, f, t, cam, mc, pt);
                    case STORM -> storm(ps, buf, f, t, cam);
                    case SLASH -> slash(ps, buf, f, t, cam);
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

    /** The shadow cage on one victim: a pool of shadow opens under them, black spears cross through them and tentacles rise round the rim. id2 = 1 is the Seq 1/0 form, half again as big. */
    private static void spikes(PoseStack ps, MultiBufferSource buf, Fx f, float t, Vec3 cam) {
        float g = Math.min(ease(t / 5f), ease((f.dur - t) / 8f)), sz = (f.id2 == 1 || f.id2 == 3) ? 1.5f : 1f;   // 2 and 3 are the surge that drags someone down out of the air
        if (g <= .01f) return;
        ps.pushPose(); ps.translate(f.x - cam.x, f.y - cam.y + .03, f.z - cam.z); ps.scale(sz, sz, sz);
        UniqGfx.pool(ps, buf, 26f * ease(t / 3f) * Math.min(1f, (f.dur - t) / 6f), t, f.id, .95f);
        for (int k = 0; k < 7; k++) {
            float a = k * (float) Math.PI * 2 / 7 + .4f, tilt = .5f + .12f * UniqGfx.rnd(k + f.id), len = (52f + 14f * UniqGfx.rnd(k + 9)) * g;
            float bx = (float) Math.cos(a) * 15, bz = (float) Math.sin(a) * 15;
            float dx = (float) (-Math.cos(a) * Math.sin(tilt)), dy = (float) Math.cos(tilt), dz = (float) (-Math.sin(a) * Math.sin(tilt));
            float px = bx, py = 0, pz = bz;
            for (int j = 0; j < 6; j++) {   // a black spear, faceted, tapering to a violet point, with a barb near the tip
                float l = len / 6, nx = px + dx * l, ny = py + dy * l, nz = pz + dz * l;
                UniqGfx.seg(ps, buf, px, py, pz, nx, ny, nz, 5.2f - j * .85f, j == 5 ? 0xFFFF6070 : j == 4 ? 0xFF901018 : j % 2 == 0 ? 0xFF100204 : 0xFF200408);
                if (j == 3) UniqGfx.seg(ps, buf, nx, ny, nz, nx - dx * 5f + dz * 4f, ny - dy * 5f - 2f, nz - dz * 5f - dx * 4f, 1.4f, 0xFF0A0410);
                px = nx; py = ny; pz = nz;
            }
        }
        for (int k = 0; k < 5; k++) {
            float a = k * (float) Math.PI * 2 / 5 + t * .02f;
            ps.pushPose(); UniqGfx.tr(ps, (float) Math.cos(a) * 22f, 0, (float) Math.sin(a) * 22f);
            UniqGfx.tentacle(ps, buf, t, k + f.id, 30f * g, 3f, 0xFF100204, 0xFFC01820);
            ps.popPose();
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

    /** A Seq 1/0 doll: it stands by its owner for a second, folds away, and unfolds beside its target, binding it. */
    private static void dollTp(PoseStack ps, MultiBufferSource buf, Fx f, float t, Vec3 cam, Minecraft mc, float pt) {
        Entity src = mc.level.getEntity(f.id), dst = mc.level.getEntity(f.id2);
        Vec3 s = src != null ? pos(src, pt, .1) : new Vec3(f.x, f.y, f.z), d = dst != null ? pos(dst, pt, 0) : new Vec3(f.x2, f.y2, f.z2);
        double a = f.id2 * 2.39996;
        boolean there = t >= 20f;
        Vec3 p = there ? d.add(Math.cos(a) * .9, 0, Math.sin(a) * .9) : s.add(Math.cos(a) * 1.3, 0, Math.sin(a) * 1.3);
        float g = there ? Math.min(ease((t - 20f) / 4f), ease((f.dur - t) / 6f)) : Math.min(ease(t / 4f), ease((20f - t) / 3f));
        if (g <= .02f) return;
        Vec3 look = there ? d.subtract(p) : d.subtract(s);
        ps.pushPose(); ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z); ps.mulPose(Axis.YP.rotation((float) Math.atan2(look.x, look.z))); ps.scale(1.7f * g, 1.7f * (2f - g), 1.7f * g);
        UniqGfx.doll(ps, buf, t, 0f, false, there);
        ps.popPose();
    }

    private static final int[][] TW = {{0xFFC83C, 0xFFE9A0, 0xFFFFFF}, {0x7A0010, 0xE01020, 0x0C0608}};   // the sword's light: {aura, edge, core}, sealed and unsealed

    /** The strokes of the Sword of Twilight: 0 a crescent through its target, 1 a crescent in flight, 3 a ring of crescents bursting out, 4 the colossal sword, 5 a small sword in flight, 6 a cross of light. */
    private static void slash(PoseStack ps, MultiBufferSource buf, Fx f, float t, Vec3 cam) {
        float k = t / f.dur; boolean dark = (f.id2 & 8) != 0; int[] c = TW[dark ? 1 : 0];
        if (f.id == 4) { blade(ps, buf, f, t, cam, dark); return; }
        if (f.id == 5) {
            Vec3 p = lerpPos(f, k), u = new Vec3(f.x - f.x2, f.y - f.y2, f.z - f.z2).normalize();
            ps.pushPose(); ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            UniqGfx.seg(ps, buf, 0, 0, 0, (float) u.x * 60f, (float) u.y * 60f, (float) u.z * 60f, 1.6f, UniqGfx.rgba(c[1], .45f));   // its trail
            ps.mulPose(Axis.YP.rotation((float) Math.atan2(u.x, u.z))); ps.mulPose(Axis.XP.rotation((float) Math.acos(Mth.clamp(u.y, -1, 1)))); ps.mulPose(Axis.YP.rotation(t * .9f));
            ps.scale(.14f, .14f, .14f); UniqGfx.twilightSword(ps, buf, Mth.clamp(t / 2f, 0f, 1f), dark);
            ps.popPose();
            return;
        }
        if (f.id == 6) {   // a cross of light bursting on the one struck
            float sc = (float) f.x2, g = ease(k * 2f), al = (1f - k) * (1f - k), yaw = (float) Math.atan2(cam.x - f.x, cam.z - f.z);
            ps.pushPose(); ps.translate(f.x - cam.x, f.y - cam.y, f.z - cam.z); ps.mulPose(Axis.YP.rotation(yaw)); ps.scale(sc, sc, sc);
            for (int j = 0; j < 2; j++) {
                ps.pushPose(); ps.mulPose(Axis.ZP.rotationDegrees(j == 0 ? 45 : -45));
                UniqGfx.box(ps, buf, 96f * g, 9f * (1f - k), 1f, UniqGfx.rgba(c[0], .5f * al)); UniqGfx.box(ps, buf, 84f * g, 4.5f * (1f - k), 1.4f, UniqGfx.rgba(c[1], .9f * al)); UniqGfx.box(ps, buf, 70f * g, 1.6f * (1f - k), 1.8f, UniqGfx.rgba(c[2], al));
                ps.popPose();
            }
            for (int j = 0; j < 2; j++) { ps.pushPose(); ps.mulPose(Axis.ZP.rotationDegrees(j * 90 + k * 60)); UniqGfx.crescent(ps, buf, 26f * g, 3f, UniqGfx.rgba(c[1], .7f * al)); ps.popPose(); }
            ps.popPose();
            return;
        }
        if (f.id == 3) {   // the light detonating: a ring of upright crescents flung outward, a flat ring of light under them
            float sc = Math.max(1f, (float) f.x2), g = ease(k * 1.4f), al = (1f - k) * (1f - k), cs = Mth.clamp(sc / 6f, .5f, 2.4f);
            for (int j = 0; j < 8; j++) {
                float a = j * (float) Math.PI / 4 + k * .6f, r = sc * .7f * g;
                ps.pushPose(); ps.translate(f.x + Math.cos(a) * r - cam.x, f.y - cam.y, f.z + Math.sin(a) * r - cam.z);
                ps.mulPose(Axis.YP.rotation((float) (Math.PI / 2 - a))); ps.mulPose(Axis.ZP.rotationDegrees(j % 2 == 0 ? -20 : 20)); ps.scale(cs, cs, cs);
                UniqGfx.crescent(ps, buf, 22f * (.5f + g), 7f, UniqGfx.rgba(c[0], .5f * al));
                ps.translate(0, 0, .01); UniqGfx.crescent(ps, buf, 22f * (.5f + g), 4.5f, UniqGfx.rgba(c[1], .9f * al));
                ps.translate(0, 0, .01); UniqGfx.crescent(ps, buf, 22f * (.5f + g), 2f, UniqGfx.rgba(c[2], al));
                ps.popPose();
            }
            ps.pushPose(); ps.translate(f.x - cam.x, f.y - cam.y - .6, f.z - cam.z);
            UniqGfx.box(ps, buf, 16f * 1.4f * sc * g + 4f, .4f, 16f * 1.4f * sc * g + 4f, UniqGfx.rgba(c[0], .25f * al));
            ps.popPose();
            return;
        }
        Vec3 p = f.id == 1 ? lerpPos(f, k) : new Vec3(f.x, f.y, f.z);
        float grow = f.id == 1 ? .75f + .25f * ease(k * 4f) : .45f + .75f * ease(k * 2.5f);
        float al = f.id == 1 ? Mth.clamp((1f - k) * 4f, 0f, 1f) : (1f - k) * (1f - k);
        if (al <= .01f) return;
        float ssc = f.id == 0 ? Math.max(.5f, (float) f.x2) : 1f, R = 30f * grow * ssc, W = (f.id == 1 ? 9f : 10f) * grow * ssc;
        int tv = f.id2 & 3; float tilt = f.id == 1 ? (tv == 0 ? -8f : tv == 1 ? -58f : 58f) : -48f;
        float yaw = (float) Math.atan2(cam.x - p.x, cam.z - p.z);
        ps.pushPose(); ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z); ps.mulPose(Axis.YP.rotation(yaw)); ps.mulPose(Axis.ZP.rotationDegrees(tilt));
        UniqGfx.crescent(ps, buf, R * 1.08f, W * 1.7f, UniqGfx.rgba(c[0], .42f * al));
        ps.translate(0, 0, .01); UniqGfx.crescent(ps, buf, R, W * 1.1f, UniqGfx.rgba(c[1], .85f * al));
        ps.translate(0, 0, .01); UniqGfx.crescent(ps, buf, R, W * .5f, UniqGfx.rgba(c[2], al));
        ps.popPose();
    }

    /** Twilight Verdict: the Sword of Twilight itself, colossal, drops point first out of the sky, buries its point and stands there shining before it fades. x2 is its scale. */
    private static final float BLADE_FALL = 9f;
    private static void blade(PoseStack ps, MultiBufferSource buf, Fx f, float t, Vec3 cam, boolean dark) {
        float sc = Math.max(.5f, (float) f.x2);
        float kf = Mth.clamp(t / BLADE_FALL, 0f, 1f), drop = 30f * sc * (1f - kf * kf), al = Mth.clamp((f.dur - t) / 10f, 0f, 1f) * Mth.clamp(t / 2f, 0f, 1f);
        if (al <= .01f) return;
        int[] c = TW[dark ? 1 : 0];
        float yaw = (float) Math.atan2(cam.x - f.x, cam.z - f.z), sh = t > BLADE_FALL && t < BLADE_FALL + 6f ? (float) Math.sin(t * 5f) * .04f * (1f - (t - BLADE_FALL) / 6f) : 0f;
        ps.pushPose(); ps.translate(f.x - cam.x, f.y - cam.y - 1.2 * sc + drop, f.z - cam.z); ps.mulPose(Axis.YP.rotation(yaw)); ps.mulPose(Axis.ZP.rotation(sh)); ps.scale(sc, sc, sc);
        UniqGfx.twilightSword(ps, buf, al, dark);
        ps.popPose();
        if (t > BLADE_FALL) {   // the ground breaks into light where it lands
            float g = ease((t - BLADE_FALL) / 10f), fa = al * (1f - .6f * g);
            ps.pushPose(); ps.translate(f.x - cam.x, f.y - cam.y + .05, f.z - cam.z);
            for (int j = 0; j < 12; j++) {
                float a = j * (float) Math.PI / 6 + .3f, len = 96f * sc * g * (.6f + .4f * UniqGfx.rnd(j + 3));
                UniqGfx.seg(ps, buf, 0, 1, 0, (float) Math.cos(a) * len, 1, (float) Math.sin(a) * len, 3f * sc, UniqGfx.rgba(j % 2 == 0 ? c[2] : c[1], .8f * fa));
            }
            for (int j = 0; j < 3; j++) {   // rings of light running out across the ground
                float rr = 96f * sc * g * (1f - j * .28f);
                for (int q = 0; q < 28; q++) { float a0 = q * 6.2832f / 28, a1 = (q + 1) * 6.2832f / 28; UniqGfx.seg(ps, buf, (float) Math.cos(a0) * rr, .6f, (float) Math.sin(a0) * rr, (float) Math.cos(a1) * rr, .6f, (float) Math.sin(a1) * rr, 2.2f * sc, UniqGfx.rgba(c[j == 0 ? 0 : 1], .7f * fa)); }
            }
            ps.popPose();
        }
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
        UniqGfx.stormBall(ps, buf, t, R * 16f * g, R > 20 ? 40 : 16, R > 20 ? 60 : 26);
        ps.popPose();
    }
}
