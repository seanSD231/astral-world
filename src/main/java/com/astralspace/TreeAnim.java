package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

/**
 * Client only: how the Mother Tree moves during each of her attacks. The server announces an attack (Net.TreeAnimPayload),
 * and from then on every animated part of the model (trunk, roots, twelve limbs, their blossoms or fruit, and the hanging
 * vines) gets its own transform each frame. Limbs that move shake leaves loose; the fruit attack drops the fruit and grows it back.
 * Attack ids are the server's pattern numbers (0 meteors, 1 rings, 2 summon, 3 slam, 4 purge, 5 temptation, 6 power, 7 curse,
 * 8 pollen, 9 mirror bloom, 10 fruit rain) and 20 for the ultimate meteor.
 */
final class TreeAnim {
    private static final Random R = new Random();
    private static int tick = 0;

    private TreeAnim() {}

    static void init() { NeoForge.EVENT_BUS.addListener(TreeAnim::onTick); }

    // ------------------------------------------------------------------ state

    static boolean on() {
        if (Net.ClientState.animAttack < 0) return false;
        long el = System.currentTimeMillis() - Net.ClientState.animStart;
        return el >= 0 && el < Net.ClientState.animTicks * 50L;
    }

    private static double seconds() { return (System.currentTimeMillis() - Net.ClientState.animStart) / 1000.0; }

    private static double smooth(double v) { double t = Math.max(0, Math.min(1, v)); return t * t * (3 - 2 * t); }

    private static double lerp(double a, double b, double t) { return a + (b - a) * Math.max(0, Math.min(1, t)); }

    private static void rot(Matrix4f m, double[] pv, float ax, float ay, float az, double angle) {
        if (Math.abs(angle) < 1e-6) return;
        m.translate((float) pv[0], (float) pv[1], (float) pv[2]).rotate((float) angle, ax, ay, az).translate((float) -pv[0], (float) -pv[1], (float) -pv[2]);
    }

    /** Is limb k one of the three the mirror bloom picked? */
    private static boolean picked(int k) { return k % 4 == Math.floorMod(Net.ClientState.animSeed, 4); }

    // ------------------------------------------------------------------ the motions

    /** How far limb k is raised (radians; negative droops) at s seconds into attack a. */
    private static double lift(int a, double s, double env, int k, double t) {
        double idle = 0.015 * Math.sin(t * 0.6 + k * 1.3);
        if (a < 0) return idle;
        double v;
        switch (a) {
            case 0 -> v = 0.38 * env + 0.05 * Math.sin(s * 8 + k) * env;
            case 1 -> v = 0.04 * Math.sin(s * 6 + k) * env;
            case 2 -> v = -0.14 * env + 0.05 * Math.sin(s * 5 + k * 1.7) * env;
            case 3 -> v = s < 1.9 ? 0.55 * smooth((s - 0.2) / 1.7) + 0.03 * Math.sin(s * 30 + k) * smooth(s / 1.5)
                    : s < 2.15 ? lerp(0.55, -0.45, (s - 1.9) / 0.25) : -0.45 * (1 - smooth((s - 2.15) / 1.2));
            case 4 -> v = s < 6.8 ? -0.28 * smooth(s / 6.6) + 0.03 * Math.sin(s * 18 + k) * smooth(s / 6.6)
                    : s < 7.1 ? lerp(-0.28, 0.5, (s - 6.8) / 0.3) : 0.5 * (1 - smooth((s - 7.1) / 1.0));
            case 5 -> v = 0.12 * Math.sin(s * 0.9 + k * 0.8) * env;
            case 6 -> v = s < 3.6 ? 0.3 * smooth(s / 3.4) + 0.06 * Math.sin(s * 24 + k) * smooth(s / 3.4)
                    : s < 3.9 ? lerp(0.3, -0.25, (s - 3.6) / 0.3) : -0.25 * (1 - smooth((s - 3.9) / 0.9));
            case 7 -> v = s < 3.0 ? -0.22 * smooth(s / 2.9) + 0.012 * Math.sin(s * 9 + k) * smooth(s / 2.9)   // the branches slowly draw in and tighten...
                    : s < 3.25 ? lerp(-0.22, 0.42, (s - 3.0) / 0.25) : 0.42 * (1 - smooth((s - 3.25) / 1.1));   // ...then snap out, and ease back
            case 8 -> v = 0.09 * Math.sin(s * 1.3 + k * 0.9) * env;
            case 9 -> v = picked(k) ? 0.25 * env + 0.04 * Math.sin(s * 10) * env : 0.03 * Math.sin(s * 2 + k) * env;
            case 10 -> v = 0.08 * Math.sin(s * 16 + k * 2.1) * Math.max(0, 1 - s / 3.6) * smooth(s / 0.3);
            case 20 -> v = 0.5 * env + 0.06 * Math.sin(s * 20 + k) * env;
            default -> v = 0;
        }
        return v + idle;
    }

    /** A turn about the vertical, for the attacks where the limbs reach or writhe sideways. */
    private static double twist(int a, double s, double env, int k) {
        return switch (a) {
            case 2 -> 0.06 * Math.sin(s * 3 + k) * env;
            case 5 -> 0.1 * Math.sin(s * 0.6 + k) * env;
            case 7 -> s < 3.0 ? 0.05 * Math.sin(s * 1.1 + k * 0.5) * smooth(s / 2.0) : 0.05 * Math.sin(s * 1.1 + k * 0.5) * (1 - smooth((s - 3.25) / 1.1));
            case 9 -> picked(k) ? 0.1 * Math.sin(s * 2) * env : 0;
            default -> 0;
        };
    }

    private static void trunk(Matrix4f m, int a, double s, double env, double t) {
        double[] o = {0, 0, 0};
        double tiltZ = 0.004 * Math.sin(t * 0.5), tiltX = 0, sy = 1;
        switch (a) {
            case 1 -> sy = 1 + 0.012 * Math.sin(s * 7) * env;
            case 3 -> sy = 1 - 0.045 * Math.exp(-Math.pow((s - 2.1) / 0.18, 2)) + 0.02 * smooth(s / 1.8) * (s < 1.9 ? 1 : 0);
            case 4 -> sy = s < 6.8 ? 1 - 0.02 * smooth(s / 6.6) : 1 + 0.03 * Math.exp(-Math.pow((s - 6.95) / 0.3, 2));
            case 5 -> tiltZ += 0.025 * Math.sin(s * 0.8) * env;
            case 6, 20 -> { tiltX = 0.006 * Math.sin(s * 25) * env; tiltZ += 0.006 * Math.cos(s * 23) * env; }
            case 7 -> { sy = s < 3.0 ? 1 - 0.03 * smooth(s / 2.9) : 1 + 0.035 * Math.exp(-Math.pow((s - 3.3) / 0.3, 2)) - 0.03 * (1 - smooth((s - 3.0) / 0.4)) * (s < 3.4 ? 1 : 0); }
            default -> { }
        }
        rot(m, o, 0, 0, 1, tiltZ);
        rot(m, o, 1, 0, 0, tiltX);
        if (sy != 1) m.scale(1f, (float) sy, 1f);
    }

    private static void roots(Matrix4f m, int a, double s, double env) {
        double heave = 0, sc = 1;
        switch (a) {
            case 1 -> heave = 1.4 * Math.max(0, Math.sin(s * 6.5)) * env;
            case 2 -> heave = 2.2 * env * (0.5 + 0.5 * Math.sin(s * 5));
            case 3 -> heave = -1.5 * Math.exp(-Math.pow((s - 2.1) / 0.2, 2));
            case 4 -> heave = s < 6.8 ? 0.4 * Math.sin(s * 9) * env : 1.8 * Math.exp(-Math.pow((s - 7.0) / 0.3, 2));
            case 6 -> heave = 0.35 * Math.sin(s * 28) * env;
            case 7 -> heave = s < 3.0 ? -0.3 * smooth(s / 2.9) : 1.2 * Math.exp(-Math.pow((s - 3.2) / 0.35, 2));
            case 20 -> { sc = 1 - 0.03 * env; heave = 0.4 * Math.sin(s * 22) * env; }
            default -> { }
        }
        if (heave != 0) m.translate(0f, (float) heave, 0f);
        if (sc != 1) m.scale((float) sc, (float) sc, (float) sc);
    }

    /** Applies the motion of one part of the tree to `m` (already holding the camera and the tree's place). */
    static void apply(TreeModel meta, String name, double[] pv, Matrix4f m, double t) {
        boolean live = on();
        int a = live ? Net.ClientState.animAttack : -1;
        double s = live ? seconds() : 0, dur = live ? Net.ClientState.animTicks / 20.0 : 0;
        double env = live ? Math.min(smooth(s / 0.7), smooth((dur - s) / 0.9)) : 0;
        if (name.equals("roots")) { roots(m, a, s, env); return; }
        trunk(m, a, s, env, t);
        if (name.equals("trunk")) return;
        int k = Integer.parseInt(name.replaceAll("^[a-z]+(\\d+).*$", "$1"));
        rot(m, pv, (float) -Math.sin(meta.limbAngle[k]), 0f, (float) Math.cos(meta.limbAngle[k]), lift(a, s, env, k, t));
        rot(m, pv, 0f, 1f, 0f, twist(a, s, env, k));
        if (name.startsWith("vine")) rot(m, pv, 1f, 0f, 0.4f, 0.03 * Math.sin(t * 1.1 + k) * (1 + 2 * env));
        if (name.startsWith("leaf")) rot(m, pv, 1f, 0f, 0.4f, 0.015 * Math.sin(t * 2.3 + k) * (1 + 3 * env));
        if (name.startsWith("fruit")) {
            int g = name.charAt(name.length() - 1) - '0', idx = k * 3 + g;
            double sc = 1, fall = 0;
            if (a == 10) {
                double f0 = 0.8 + 0.06 * idx, r0 = 5.4 + 0.05 * idx;
                if (s >= f0 && s < f0 + 1.4) { double u = (s - f0) / 1.4; fall = 30 * u * u; sc = 1 - smooth((u - 0.5) / 0.5); }
                else if (s >= f0 + 1.4 && s < r0) { sc = 0; fall = 30; }
                else if (s >= r0) sc = smooth((s - r0) / 2.0);
            } else if (a == 8) sc = 1 + 0.2 * Math.sin(s * 3.4 + idx) * env;
            else if (a == 9) sc = 1 + (picked(k) ? 0.5 : 0.1) * Math.sin(s * 5 + g) * env;
            else if (a == 5) sc = 1 + 0.12 * Math.sin(s * 2 + idx) * env;
            double[] c = meta.centroids.getOrDefault(name, new double[]{0, 0, 0});
            if (fall != 0) m.translate(0f, (float) -fall, 0f);
            if (sc != 1) m.translate((float) c[0], (float) c[1], (float) c[2]).scale((float) Math.max(0.001, sc)).translate((float) -c[0], (float) -c[1], (float) -c[2]);
        }
    }

    /** A colour the whole tree is washed with during the attack (1,1,1 = none). */
    static float[] tint() {
        if (!on()) return new float[]{1f, 1f, 1f};
        double s = seconds(), dur = Net.ClientState.animTicks / 20.0, env = Math.min(smooth(s / 0.7), smooth((dur - s) / 0.9));
        float[] c = switch (Net.ClientState.animAttack) {
            case 7 -> new float[]{0.85f, 0.55f, 1.15f};
            case 6 -> new float[]{1.2f, 1.1f, 1.35f};
            case 20 -> new float[]{1.35f, 0.75f, 0.65f};
            case 9 -> new float[]{1.1f, 1.1f, 1.25f};
            default -> new float[]{1f, 1f, 1f};
        };
        float k = (float) (env * 0.6);
        return new float[]{1 + (c[0] - 1) * k, 1 + (c[1] - 1) * k, 1 + (c[2] - 1) * k};
    }

    // ------------------------------------------------------------------ falling leaves and dust

    private static double leafRate(int a, double s, double env) {
        return switch (a) {
            case 0 -> 5 * env;
            case 1 -> 1;
            case 2 -> 3 * env;
            case 3 -> s > 1.9 && s < 2.5 ? 28 : 2 * smooth(s / 1.5);
            case 4 -> s > 6.8 && s < 7.4 ? 28 : 3;
            case 5 -> 3 * env;
            case 6 -> 6 * env;
            case 7 -> 4 * env;
            case 8 -> 3 * env;
            case 9 -> 2 * env;
            case 10 -> 9 * Math.max(0, 1 - s / 3.6);
            case 20 -> 8 * env;
            default -> 0;
        };
    }

    /** Her death: blood-red drops fall off the sagging flesh, then ash and smoke lift off the drying puddle. */
    private static void meltDust(Minecraft mc) {
        double ms = TreeRenderer.meltSeconds(), T = Net.ClientState.meltTicks / 20.0;
        if (ms < 0 || ms > T || Double.isNaN(TreeRenderer.lastY)) return;
        double bx = TreeRenderer.lastX, by = TreeRenderer.lastY, bz = TreeRenderer.lastZ, sc = Net.ClientState.treeScale;
        double e = Math.min(1, ms / 9.0); e = e * e * (3 - 2 * e);
        double rad = 80 * sc * e, top = Math.max(2, 150 * sc * (1 - 0.97 * Math.pow(Math.min(1, ms / 10.0), 0.85)));
        if (ms < 10) for (int i = 0; i < 10; i++) {
            double a = R.nextDouble() * Math.PI * 2, r = R.nextDouble() * (14 + 40 * Math.min(1, ms / 8.0)) * sc;
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.5f, 0.05f, 0.09f), 3.0f + R.nextFloat() * 2), bx + Math.cos(a) * r, by + R.nextDouble() * top, bz + Math.sin(a) * r, 0, -0.4, 0);
        }
        if (ms > 6) for (int i = 0; i < 30; i++) {
            double a = R.nextDouble() * Math.PI * 2, r = Math.sqrt(R.nextDouble()) * Math.max(8, rad);
            mc.level.addParticle(R.nextInt(3) == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : R.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH, bx + Math.cos(a) * r, by + 0.5, bz + Math.sin(a) * r, R.nextGaussian() * 0.03, 0.06 + R.nextDouble() * 0.08, R.nextGaussian() * 0.03);
        }
        if (mc.player != null && tick % 8 == 0) mc.level.playLocalSound(bx, by + 5, bz, SoundEvents.SLIME_BLOCK_STEP, SoundSource.HOSTILE, 6f, 0.5f, false);
    }

    private static void onTick(ClientTickEvent.Post ev) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && Net.ClientState.meltStart != 0) { tick++; meltDust(mc); }
        if (mc.level == null || mc.player == null || !on()) return;
        TreeModel meta = TreeRenderer.metaNow();
        if (meta == null || meta.limbTips.isEmpty()) return;
        tick++;
        int a = Net.ClientState.animAttack;
        double s = seconds(), dur = Net.ClientState.animTicks / 20.0, env = Math.min(smooth(s / 0.7), smooth((dur - s) / 0.9));
        double bx = TreeRenderer.lastX, by = TreeRenderer.lastY, bz = TreeRenderer.lastZ;
        if (Double.isNaN(by)) return;
        if (mc.player.distanceToSqr(bx, by + 60, bz) > 260.0 * 260.0) return;
        boolean rotten = TreeRenderer.lastFleshy;
        double rate = leafRate(a, s, env);
        int n = (int) rate + (R.nextDouble() < rate - (int) rate ? 1 : 0);
        for (int i = 0; i < n; i++) {
            int k = R.nextInt(12);
            List<double[]> tips = meta.limbTips.get(k);
            if (tips.isEmpty()) continue;
            double[] p = tips.get(R.nextInt(tips.size()));
            double[] q = moved(meta, k, p, a, s, env);
            double x = bx + q[0] + R.nextGaussian() * 3, y = by + q[1] + R.nextGaussian() * 2, z = bz + q[2] + R.nextGaussian() * 3;
            if (a == 8) mc.level.addParticle(ParticleTypes.WITCH, x, y, z, R.nextGaussian() * 0.05, -0.02, R.nextGaussian() * 0.05);
            else if (a == 9 && picked(k)) mc.level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, -0.05, 0);
            else if (rotten) mc.level.addParticle(new DustParticleOptions(new Vector3f(0.55f, 0.45f, 0.15f), 2.2f), x, y, z, R.nextGaussian() * 0.04, -0.2, R.nextGaussian() * 0.04);
            else mc.level.addParticle(ParticleTypes.CHERRY_LEAVES, x, y, z, R.nextGaussian() * 0.05, -0.1, R.nextGaussian() * 0.05);
        }
        // dust thrown up round the foot for the attacks that hit the ground
        boolean slam = a == 3 && Math.abs(s - 2.1) < 0.15, purge = a == 4 && Math.abs(s - 7.0) < 0.2, ring = a == 1 && tick % 8 == 0, up = a == 2 && tick % 4 == 0;
        if (slam || purge || ring || up) {
            int c = slam || purge ? 36 : 12;
            for (int i = 0; i < c; i++) {
                double ang = R.nextDouble() * Math.PI * 2, r = 16 + R.nextDouble() * 70;
                mc.level.addParticle(ParticleTypes.POOF, bx + Math.cos(ang) * r, by + 1, bz + Math.sin(ang) * r, Math.cos(ang) * 0.15, 0.1, Math.sin(ang) * 0.15);
            }
        }
    }

    /** Where a point on limb k has moved to (the same raise and turn the model gets). */
    private static double[] moved(TreeModel meta, int k, double[] p, int a, double s, double env) {
        double t = (System.nanoTime() / 1_000_000L) / 1000.0;
        double[] pv = meta.limbPivot[k];
        double ang = lift(a, s, env, k, t), tw = twist(a, s, env, k), la = meta.limbAngle[k];
        double[] w = {-Math.sin(la), 0, Math.cos(la)};
        double[] r = {p[0] - pv[0], p[1] - pv[1], p[2] - pv[2]};
        r = rodrigues(r, w, ang);
        r = rodrigues(r, new double[]{0, 1, 0}, tw);
        return new double[]{pv[0] + r[0], pv[1] + r[1], pv[2] + r[2]};
    }

    private static double[] rodrigues(double[] v, double[] k, double th) {
        double c = Math.cos(th), s = Math.sin(th), d = k[0] * v[0] + k[1] * v[1] + k[2] * v[2];
        double[] x = {k[1] * v[2] - k[2] * v[1], k[2] * v[0] - k[0] * v[2], k[0] * v[1] - k[1] * v[0]};
        return new double[]{v[0] * c + x[0] * s + k[0] * d * (1 - c), v[1] * c + x[1] * s + k[1] * d * (1 - c), v[2] * c + x[2] * s + k[2] * d * (1 - c)};
    }
}
