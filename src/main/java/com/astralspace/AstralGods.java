package com.astralspace;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Client only: what the sky of the Astral World shows. Several separate swirls that drift and turn on their own, and, for
 * every pathway that has a Seq 0 (a "god"), that pathway's own sign: lightning for a Tyrant, the sun for a Sun god, the moon
 * for a Moon god, an eye for a Visionary ... Without a god of a pathway its sign is not there.
 */
public final class AstralGods {
    private AstralGods() {}

    private static final float R = 94f;

    // ------------------------------------------------------------------ geometry on the sky sphere

    /** Direction from longitude / latitude. */
    private static float[] dir(double lon, double lat) {
        double cl = Math.cos(lat);
        return new float[]{(float) (Math.cos(lon) * cl), (float) Math.sin(lat), (float) (Math.sin(lon) * cl)};
    }

    /** Two tangent axes at direction d: u sideways, v roughly up. Returns {ux,uy,uz, vx,vy,vz}. */
    private static float[] basis(float[] d) {
        float ux, uy, uz;
        if (Math.abs(d[1]) > 0.98f) { ux = 1; uy = 0; uz = 0; }
        else { ux = d[2]; uy = 0; uz = -d[0]; } // up x d
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        float vx = d[1] * uz - d[2] * uy, vy = d[2] * ux - d[0] * uz, vz = d[0] * uy - d[1] * ux;
        if (vy < 0) { vx = -vx; vy = -vy; vz = -vz; ux = -ux; uy = -uy; uz = -uz; }
        return new float[]{ux, uy, uz, vx, vy, vz};
    }

    /** A point on the sky sphere at (a, b) (radians-ish) away from the centre direction d, along its axes. */
    private static void vertex(BufferBuilder q, Matrix4f m, float[] d, float[] ax, float a, float b, int r, int g, int bl, float alpha) {
        float x = d[0] + ax[0] * a + ax[3] * b, y = d[1] + ax[1] * a + ax[4] * b, z = d[2] + ax[2] * a + ax[5] * b;
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        q.addVertex(m, x / l * R, y / l * R, z / l * R).setColor(r, g, bl, (int) (Math.max(0f, Math.min(1f, alpha)) * 255));
    }

    private static float smooth(float x) { x = Math.max(0f, Math.min(1f, x)); return x * x * (3 - 2 * x); }

    private static int mix(int a, int b, float t) { return (int) (a + (b - a) * t); }

    private static BufferBuilder begin() {
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    private static void end(BufferBuilder q) {
        com.mojang.blaze3d.vertex.MeshData mesh = q.build(); // null when nothing was added
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    /**
     * A soft disc (or ellipse, rx / ry) centred on d, turned by `rot`; colour goes from `in` at the middle to `out` at the rim,
     * alpha fades to nothing at the rim. arms > 0 cuts spiral arms into it (that is a swirl), spinning with `spin`.
     */
    private static void disc(BufferBuilder q, Matrix4f m, float[] d, float rx, float ry, float rot, int[] in, int[] out,
                             float alpha, float power, int arms, float twist, float spin) {
        float[] ax = basis(d);
        final int rings = 8, segs = 28;
        float cr = (float) Math.cos(rot), sr = (float) Math.sin(rot);
        for (int k = 0; k < rings; k++) {
            float r0 = k / (float) rings, r1 = (k + 1) / (float) rings;
            for (int j = 0; j < segs; j++) {
                float t0 = (float) (Math.PI * 2 * j / segs), t1 = (float) (Math.PI * 2 * (j + 1) / segs);
                float[][] p = {{r0, t0}, {r0, t1}, {r1, t1}, {r1, t0}};
                for (float[] v : p) {
                    float lx = (float) Math.cos(v[1]) * v[0] * rx, ly = (float) Math.sin(v[1]) * v[0] * ry;
                    float a = lx * cr - ly * sr, b = lx * sr + ly * cr;
                    float fall = (float) Math.pow(1f - v[0], power);
                    float arm = 1f;
                    if (arms > 0) arm = 0.2f + 0.8f * (0.5f + 0.5f * (float) Math.sin(arms * (v[1] + twist * v[0]) - spin));
                    vertex(q, m, d, ax, a, b, mix(in[0], out[0], v[0]), mix(in[1], out[1], v[0]), mix(in[2], out[2], v[0]), alpha * fall * arm);
                }
            }
        }
    }

    /** A ring (halo) of inner / outer radius around d, with `dashes` gaps that turn with `spin`. */
    private static void ring(BufferBuilder q, Matrix4f m, float[] d, float rIn, float rOut, int[] col, float alpha, int dashes, float spin) {
        float[] ax = basis(d);
        final int segs = 64;
        for (int j = 0; j < segs; j++) {
            float t0 = (float) (Math.PI * 2 * j / segs), t1 = (float) (Math.PI * 2 * (j + 1) / segs);
            float a0 = dashes > 0 ? 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(dashes * t0 - spin)) : 1f;
            float a1 = dashes > 0 ? 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(dashes * t1 - spin)) : 1f;
            vertex(q, m, d, ax, (float) Math.cos(t0) * rIn, (float) Math.sin(t0) * rIn, col[0], col[1], col[2], 0f);
            vertex(q, m, d, ax, (float) Math.cos(t1) * rIn, (float) Math.sin(t1) * rIn, col[0], col[1], col[2], 0f);
            vertex(q, m, d, ax, (float) Math.cos(t1) * (rIn + rOut) * 0.5f, (float) Math.sin(t1) * (rIn + rOut) * 0.5f, col[0], col[1], col[2], alpha * a1);
            vertex(q, m, d, ax, (float) Math.cos(t0) * (rIn + rOut) * 0.5f, (float) Math.sin(t0) * (rIn + rOut) * 0.5f, col[0], col[1], col[2], alpha * a0);
            vertex(q, m, d, ax, (float) Math.cos(t0) * (rIn + rOut) * 0.5f, (float) Math.sin(t0) * (rIn + rOut) * 0.5f, col[0], col[1], col[2], alpha * a0);
            vertex(q, m, d, ax, (float) Math.cos(t1) * (rIn + rOut) * 0.5f, (float) Math.sin(t1) * (rIn + rOut) * 0.5f, col[0], col[1], col[2], alpha * a1);
            vertex(q, m, d, ax, (float) Math.cos(t1) * rOut, (float) Math.sin(t1) * rOut, col[0], col[1], col[2], 0f);
            vertex(q, m, d, ax, (float) Math.cos(t0) * rOut, (float) Math.sin(t0) * rOut, col[0], col[1], col[2], 0f);
        }
    }

    // ------------------------------------------------------------------ the swirls

    private static final int SWIRLS = 11;
    private static final int[][][] PALETTES = {
            {{210, 170, 255}, {90, 30, 190}}, {{255, 200, 120}, {190, 40, 120}}, {{150, 235, 255}, {30, 70, 190}},
            {{255, 150, 170}, {110, 20, 100}}, {{170, 255, 220}, {20, 100, 140}}, {{255, 230, 160}, {120, 60, 170}}
    };
    private static final float[][] SWIRL = new float[SWIRLS][]; // {lon, lat, lonSpeed, latAmp, latSpeed, radius, arms, spin, twist, palette, alpha, dir}

    static {
        Random r = new Random(0x5111A1L);
        for (int i = 0; i < SWIRLS; i++) {
            boolean big = i < 5;
            SWIRL[i] = new float[]{
                    r.nextFloat() * 6.283f, -0.9f + r.nextFloat() * 2.1f, (0.012f + r.nextFloat() * 0.03f) * (r.nextBoolean() ? 1 : -1),
                    0.12f + r.nextFloat() * 0.25f, 0.03f + r.nextFloat() * 0.05f, big ? 0.35f + r.nextFloat() * 0.25f : 0.13f + r.nextFloat() * 0.12f,
                    2 + r.nextInt(3), (0.25f + r.nextFloat() * 0.5f) * (r.nextBoolean() ? 1 : -1), 3f + r.nextFloat() * 4f,
                    r.nextInt(PALETTES.length), big ? 0.75f : 0.6f, r.nextFloat() * 6.283f};
        }
    }

    private static void swirls(Matrix4f m, double t) {
        BufferBuilder q = begin();
        for (float[] s : SWIRL) {
            double lon = s[0] + t * s[2];
            double lat = s[1] + s[3] * Math.sin(t * s[4] + s[11]);
            lat = Math.max(-1.35, Math.min(1.35, lat));
            int[][] pal = PALETTES[(int) s[9]];
            disc(q, m, dir(lon, lat), s[5], s[5], 0f, pal[0], pal[1], s[10], 1.3f, (int) s[6], s[8], (float) (t * s[7]));
        }
        end(q);
    }

    // ------------------------------------------------------------------ the gods' signs

    private static final ResourceLocation SUN = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    private static final ResourceLocation MOON = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    private static final ResourceLocation CRIMSON_MOON = ResourceLocation.fromNamespaceAndPath("lotm", "textures/environment/crimson_moon_phases.png");

    private static boolean crimsonMoon() {
        try {
            return (Boolean) Class.forName("net.swimmingtuna.lotm.util.ClientData.ClientCrimsonMoonData").getMethod("isCrimsonMoon").invoke(null);
        } catch (Throwable t) { return false; }
    }

    /** Where a pathway's sign sits in the sky (the same for everyone, always). */
    public static float[] anchor(String id) {
        Random r = new Random(id.hashCode() * 7919L + 31);
        return dir(r.nextDouble() * Math.PI * 2, -0.35 + r.nextDouble() * 1.2);
    }

    private static void sunAndMoon(Matrix4f m, double t, boolean sun, boolean moon, int moonPhase) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        if (sun) {
            float[] d = dir(0.8 + t * 0.004, 0.75);
            float[] ax = basis(d);
            RenderSystem.setShaderTexture(0, SUN);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float s = 0.32f; // vanilla's sun is about this big at this distance
            float[][] c = {{-1, -1, 0, 0}, {-1, 1, 0, 1}, {1, 1, 1, 1}, {1, -1, 1, 0}};
            for (float[] k : c) {
                float x = d[0] + ax[0] * k[0] * s + ax[3] * k[1] * s, y = d[1] + ax[1] * k[0] * s + ax[4] * k[1] * s, z = d[2] + ax[2] * k[0] * s + ax[5] * k[1] * s;
                float l = (float) Math.sqrt(x * x + y * y + z * z);
                b.addVertex(m, x / l * 100f, y / l * 100f, z / l * 100f).setUv(k[2], k[3]);
            }
            BufferUploader.drawWithShader(b.buildOrThrow());
        }
        if (moon) {
            boolean crimson = crimsonMoon();
            float[] d = dir(3.9 - t * 0.004, 0.95);
            float[] ax = basis(d);
            RenderSystem.setShaderTexture(0, crimson ? CRIMSON_MOON : MOON);
            if (crimson) RenderSystem.setShaderColor(1f, 0.35f, 0.3f, 1f); else RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            float u0 = (moonPhase % 4) / 4f, u1 = u0 + 0.25f, v0 = ((moonPhase / 4) % 2) / 2f, v1 = v0 + 0.5f;
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float s = 0.5f; // LOTM's moon is 2.5 x vanilla
            float[][] c = {{-1, -1, u0, v0}, {-1, 1, u0, v1}, {1, 1, u1, v1}, {1, -1, u1, v0}};
            for (float[] k : c) {
                float x = d[0] + ax[0] * k[0] * s + ax[3] * k[1] * s, y = d[1] + ax[1] * k[0] * s + ax[4] * k[1] * s, z = d[2] + ax[2] * k[0] * s + ax[5] * k[1] * s;
                float l = (float) Math.sqrt(x * x + y * y + z * z);
                b.addVertex(m, x / l * 100f, y / l * 100f, z / l * 100f).setUv(k[2], k[3]);
            }
            BufferUploader.drawWithShader(b.buildOrThrow());
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    /** Jagged lightning bolts that flash at random places (a Tyrant). */
    private static void lightning(BufferBuilder q, Matrix4f m, double t) {
        for (int slot = 0; slot < 3; slot++) {
            double period = 2.2 + slot * 0.9;
            long n = (long) Math.floor((t + slot * 0.7) / period);
            double age = (t + slot * 0.7) - n * period;
            if (age > 0.45) continue;
            float flick = age < 0.08 ? 1f : (age < 0.16 ? 0.35f : (age < 0.24 ? 0.9f : 0.5f * (1f - (float) ((age - 0.24) / 0.21))));
            Random r = new Random(n * 9973L + slot * 131L);
            double lon = r.nextDouble() * Math.PI * 2, lat = -0.1 + r.nextDouble() * 1.2;
            int steps = 8 + r.nextInt(4);
            double[][] pts = new double[steps + 1][];
            double lo = lon, la = lat;
            for (int i = 0; i <= steps; i++) {
                pts[i] = new double[]{lo, la};
                lo += (r.nextDouble() - 0.5) * 0.06;
                la -= 0.05 + r.nextDouble() * 0.03;
            }
            for (int pass = 0; pass < 2; pass++) { // a wide blue glow, then a thin white core
                float w = pass == 0 ? 0.018f : 0.006f;
                int[] col = pass == 0 ? new int[]{120, 150, 255} : new int[]{235, 240, 255};
                float al = (pass == 0 ? 0.55f : 1f) * flick;
                for (int i = 0; i < steps; i++) {
                    float[] d0 = dir(pts[i][0], pts[i][1]), d1 = dir(pts[i + 1][0], pts[i + 1][1]);
                    float[] ax0 = basis(d0), ax1 = basis(d1);
                    vertex(q, m, d0, ax0, -w, 0, col[0], col[1], col[2], al);
                    vertex(q, m, d0, ax0, w, 0, col[0], col[1], col[2], al);
                    vertex(q, m, d1, ax1, w, 0, col[0], col[1], col[2], al);
                    vertex(q, m, d1, ax1, -w, 0, col[0], col[1], col[2], al);
                }
            }
        }
    }

    /** Small lights drifting up or down in a column (death, mother, demoness, red priest ...). */
    private static void motes(BufferBuilder q, Matrix4f m, String id, double t, int[] col, boolean up, int count) {
        float[] a = anchor(id);
        double lon0 = Math.atan2(a[2], a[0]), lat0 = Math.asin(a[1]);
        Random r = new Random(id.hashCode() * 31L + 7);
        for (int i = 0; i < count; i++) {
            double ph = r.nextDouble(), sp = 0.04 + r.nextDouble() * 0.05;
            double off = (r.nextDouble() - 0.5) * 0.5, sway = r.nextDouble() * 6.28;
            double f = (t * sp + ph) % 1.0;
            double lat = lat0 + (up ? (f - 0.5) : (0.5 - f)) * 0.9;
            double lon = lon0 + off + 0.04 * Math.sin(t * 0.7 + sway);
            float al = (float) Math.sin(Math.PI * f) * 0.9f;
            float[] d = dir(lon, lat);
            disc(q, m, d, 0.018f, 0.018f, 0f, col, col, al, 1.0f, 0, 0, 0);
        }
    }

    private static void sign(BufferBuilder q, Matrix4f m, String id, double t) {
        float[] a = anchor(id);
        float pulse = 0.65f + 0.35f * (float) Math.sin(t * 0.8 + id.hashCode());
        switch (id) {
            case "sailor" -> lightning(q, m, t);
            case "spectator" -> eye(q, m, a, t, new int[]{150, 255, 200}, new int[]{20, 90, 70});
            case "mystery_pryer" -> eye(q, m, a, t, new int[]{200, 170, 255}, new int[]{50, 20, 110});
            case "seer" -> ring(q, m, a, 0.10f, 0.14f, new int[]{235, 200, 120}, 0.8f * pulse, 6, (float) t * 0.5f);
            case "arbiter" -> ring(q, m, a, 0.10f, 0.13f, new int[]{255, 225, 130}, 0.9f * pulse, 0, 0);
            case "prisoner" -> ring(q, m, a, 0.09f, 0.13f, new int[]{170, 175, 190}, 0.85f, 14, (float) t * 0.3f);
            case "reader" -> ring(q, m, a, 0.09f, 0.12f, new int[]{240, 240, 255}, 0.8f * pulse, 3, (float) -t * 0.4f);
            case "apprentice" -> rift(q, m, a, t, new int[]{210, 245, 255}, new int[]{60, 130, 200});
            case "marauder" -> rift(q, m, a, t, new int[]{150, 255, 235}, new int[]{20, 110, 100});
            case "secrets_supplicant" -> rift(q, m, a, t, new int[]{190, 190, 255}, new int[]{50, 50, 140});
            case "sleepless" -> disc(q, m, a, 0.30f, 0.22f, (float) (t * 0.02), new int[]{0, 0, 0}, new int[]{5, 0, 15}, 0.7f, 0.8f, 0, 0, 0);
            case "criminal" -> disc(q, m, a, 0.28f, 0.28f, 0f, new int[]{40, 0, 5}, new int[]{0, 0, 0}, 0.75f * pulse, 0.8f, 5, 2f, (float) (t * 0.3));
            case "corpse_collector" -> motes(q, m, id, t, new int[]{190, 225, 255}, true, 16);
            case "planter" -> motes(q, m, id, t, new int[]{120, 255, 130}, true, 16);
            case "assassin" -> motes(q, m, id, t, new int[]{255, 140, 190}, false, 16);
            case "hunter" -> motes(q, m, id, t, new int[]{255, 140, 50}, true, 16);
            case "monster" -> motes(q, m, id, t, new int[]{255, 225, 110}, true, 14);
            case "savant" -> motes(q, m, id, t, new int[]{140, 255, 255}, false, 14);
            case "lawyer" -> motes(q, m, id, t, new int[]{255, 215, 120}, true, 14);
            case "warrior" -> band(q, m, t);
            default -> { }
        }
    }

    private static void eye(BufferBuilder q, Matrix4f m, float[] a, double t, int[] iris, int[] rim) {
        float open = 0.25f + 0.75f * smooth((float) (0.5 + 0.5 * Math.sin(t * 0.35)) * 1.6f - 0.2f);
        disc(q, m, a, 0.22f, 0.22f * open, 0f, new int[]{240, 250, 255}, rim, 0.7f, 0.9f, 0, 0, 0);
        disc(q, m, a, 0.09f, 0.09f * Math.min(1f, open * 1.2f), 0f, iris, new int[]{iris[0] / 2, iris[1] / 2, iris[2] / 2}, 0.95f, 0.5f, 0, 0, 0);
        disc(q, m, a, 0.035f, 0.035f * Math.min(1f, open * 1.2f), 0f, new int[]{0, 0, 0}, new int[]{0, 0, 0}, 1f, 0.3f, 0, 0, 0);
    }

    private static void rift(BufferBuilder q, Matrix4f m, float[] a, double t, int[] core, int[] edge) {
        float pulse = 0.6f + 0.4f * (float) Math.sin(t * 0.9 + a[0] * 5);
        disc(q, m, a, 0.025f, 0.30f, 0.15f, core, edge, 1f * pulse, 0.7f, 0, 0, 0);
        disc(q, m, a, 0.08f, 0.34f, 0.15f, edge, edge, 0.35f * pulse, 1.6f, 0, 0, 0);
    }

    /** A band of twilight low on the horizon all the way round (a Twilight Giant). */
    private static void band(BufferBuilder q, Matrix4f m, double t) {
        final int segs = 48;
        for (int j = 0; j < segs; j++) {
            double l0 = Math.PI * 2 * j / segs, l1 = Math.PI * 2 * (j + 1) / segs;
            float w0 = 0.55f + 0.45f * (float) Math.sin(l0 * 3 + t * 0.2), w1 = 0.55f + 0.45f * (float) Math.sin(l1 * 3 + t * 0.2);
            float[][] c = {{255, 150, 60}, {255, 90, 120}, {120, 60, 170}};
            double[] lats = {-0.05, 0.06, 0.22};
            for (int k = 0; k < 2; k++) {
                float[] d00 = dir(l0, lats[k]), d01 = dir(l1, lats[k]), d11 = dir(l1, lats[k + 1]), d10 = dir(l0, lats[k + 1]);
                float a0 = k == 0 ? 0.65f : 0.4f, a1 = k == 0 ? 0.4f : 0f;
                q.addVertex(m, d00[0] * R, d00[1] * R, d00[2] * R).setColor((int) c[k][0], (int) c[k][1], (int) c[k][2], (int) (a0 * w0 * 255));
                q.addVertex(m, d01[0] * R, d01[1] * R, d01[2] * R).setColor((int) c[k][0], (int) c[k][1], (int) c[k][2], (int) (a0 * w1 * 255));
                q.addVertex(m, d11[0] * R, d11[1] * R, d11[2] * R).setColor((int) c[k + 1][0], (int) c[k + 1][1], (int) c[k + 1][2], (int) (a1 * w1 * 255));
                q.addVertex(m, d10[0] * R, d10[1] * R, d10[2] * R).setColor((int) c[k + 1][0], (int) c[k + 1][1], (int) c[k + 1][2], (int) (a1 * w0 * 255));
            }
        }
    }

    public static final String[] ALL = {"seer", "marauder", "apprentice", "spectator", "bard", "sailor", "reader", "secrets_supplicant",
            "sleepless", "corpse_collector", "warrior", "assassin", "hunter", "mystery_pryer", "savant", "monster", "planter",
            "apothecary", "criminal", "prisoner", "lawyer", "arbiter"};

    /** Draws everything: the swirls and the signs of the gods that exist. Called after the sky, depth off, blending on. */
    public static void draw(Matrix4f m, double t, boolean swirl, int moonPhase) {
        String gods = Net.ClientState.gods == null ? "," : Net.ClientState.gods;
        if (swirl) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            swirls(m, t);
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder q = begin();
        for (String id : ALL) if (gods.contains("," + id + ",")) sign(q, m, id, t);
        end(q);
        boolean sun = gods.contains(",bard,"), moon = gods.contains(",apothecary,");
        if (sun || moon) sunAndMoon(m, t, sun, moon, moonPhase);
    }
}
