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
        int[] c = vivid(r, g, bl);
        q.addVertex(m, x / l * R, y / l * R, z / l * R).setColor(c[0], c[1], c[2], (int) (Math.max(0f, Math.min(1f, alpha * gain)) * 255));
    }

    /** Colourfulness of the swirls: 3 means 1000% more saturated; and lifted so no colour stays dark. */
    private static final float VIVID = 2.6f;

    private static int[] vivid(int r, int g, int b) {
        float l = 0.3f * r + 0.59f * g + 0.11f * b;
        float nr = Math.max(0f, l + (r - l) * VIVID), ng = Math.max(0f, l + (g - l) * VIVID), nb = Math.max(0f, l + (b - l) * VIVID);
        float mx = Math.max(1f, Math.max(nr, Math.max(ng, nb)));
        float k = Math.min(1f, 120f / mx) < 1f ? 1f : 120f / mx;   // colours that are nearly black are lifted a little, the rest keep their brightness
        return new int[]{clamp255(nr * k), clamp255(ng * k), clamp255(nb * k)};
    }

    private static int clamp255(float v) { return (int) Math.max(0f, Math.min(255f, v)); }

    /** Brightness of whatever is being drawn: the swirls are drawn at double. */
    private static float gain = 1f;

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

    /** Colours that every arm of a swirl runs through, one after the other (looping). */
    private static final int[][] ARM_COLORS = {
            {150, 40, 200}, {90, 70, 230}, {40, 150, 220}, {30, 200, 190}, {230, 180, 70}, {235, 90, 90}, {200, 40, 130}, {120, 60, 220}
    };

    private static float smoothstep(float x) { x = Math.max(0f, Math.min(1f, x)); return x * x * (3 - 2 * x); }

    /** The original swirling sky colours: indigo, violet, teal, gold and crimson flowing into each other. */
    private static float[] swirlColor(double lon, double lat, double t) {
        float f1 = (float) Math.sin(lon * 2 + lat * 3 + t * 0.12 + 1.8 * Math.sin(lat * 4 - t * 0.07));
        float f2 = (float) Math.sin(lon * 3 - lat * 2 - t * 0.09 + 1.5 * Math.sin(lon * 2 + t * 0.05));
        float f3 = (float) Math.sin(lon * 5 + lat * 5 + t * 0.15 + Math.sin(lon * 3 - t * 0.08));
        float r = 20, g = 8, b = 60;
        float w = smoothstep(f1 * 0.5f + 0.5f);       r += (170 - r) * w * 0.8f; g += (35 - g) * w * 0.8f; b += (200 - b) * w * 0.8f;
        w = smoothstep(f2 * 0.5f + 0.35f);            r += (15 - r) * w * 0.6f;  g += (160 - g) * w * 0.6f; b += (200 - b) * w * 0.6f;
        w = (float) Math.pow(Math.max(0, f3), 3);     r += (245 - r) * w * 0.6f; g += (180 - g) * w * 0.6f; b += (70 - b) * w * 0.6f;
        w = (float) Math.pow(Math.max(0, -f1 * f2), 2) * 0.6f; r += (230 - r) * w; g += (40 - g) * w; b += (90 - b) * w;
        float a = 0.55f + 0.25f * f1 * f2 + 0.12f * f3;
        return new float[]{r, g, b, Math.max(0.3f, Math.min(0.9f, a))};
    }

    private static int[] armColor(float u) {
        u -= (float) Math.floor(u);
        float f = u * ARM_COLORS.length;
        int i = (int) f % ARM_COLORS.length, j = (i + 1) % ARM_COLORS.length;
        float t = smooth(f - (int) f);
        return new int[]{mix(ARM_COLORS[i][0], ARM_COLORS[j][0], t), mix(ARM_COLORS[i][1], ARM_COLORS[j][1], t), mix(ARM_COLORS[i][2], ARM_COLORS[j][2], t)};
    }

    /** One swirl: a few curling arms, each a ribbon that changes colour along its length, turning slowly. */
    private static void swirlArms(BufferBuilder q, Matrix4f m, float[] d, float radius, int arms, float twist, float spin, float hue, float alpha) {
        float[] ax = basis(d);
        final int steps = 26;
        for (int k = 0; k < arms; k++) {
            double th0 = spin + k * Math.PI * 2 / arms;
            float[] pa = new float[steps + 1], pb = new float[steps + 1], w = new float[steps + 1], al = new float[steps + 1];
            int[][] col = new int[steps + 1][];
            for (int i = 0; i <= steps; i++) {
                float s = i / (float) steps;
                double th = th0 + twist * s;
                float r = radius * (0.04f + 0.96f * s);
                pa[i] = (float) Math.cos(th) * r; pb[i] = (float) Math.sin(th) * r;
                float body = (float) Math.pow(Math.sin(Math.PI * Math.min(1f, s * 0.92f + 0.04f)), 0.7);
                w[i] = radius * 0.16f * (0.25f + 0.75f * body) * (1f - 0.35f * s);
                al[i] = alpha * body;
                col[i] = armColor(hue + s * 0.8f + k * 0.17f);
            }
            for (int i = 0; i < steps; i++) {
                float[][] n = new float[2][];
                for (int e = 0; e < 2; e++) {
                    int p0 = Math.max(0, i + e - 1), p1 = Math.min(steps, i + e + 1);
                    float tx = pa[p1] - pa[p0], ty = pb[p1] - pb[p0], l = (float) Math.sqrt(tx * tx + ty * ty);
                    if (l < 1e-6f) l = 1f;
                    n[e] = new float[]{-ty / l, tx / l};
                }
                int i1 = i + 1;
                vertex(q, m, d, ax, pa[i] + n[0][0] * w[i], pb[i] + n[0][1] * w[i], col[i][0], col[i][1], col[i][2], al[i] * 0.15f);
                vertex(q, m, d, ax, pa[i1] + n[1][0] * w[i1], pb[i1] + n[1][1] * w[i1], col[i1][0], col[i1][1], col[i1][2], al[i1] * 0.15f);
                vertex(q, m, d, ax, pa[i1], pb[i1], col[i1][0], col[i1][1], col[i1][2], al[i1]);
                vertex(q, m, d, ax, pa[i], pb[i], col[i][0], col[i][1], col[i][2], al[i]);
                vertex(q, m, d, ax, pa[i], pb[i], col[i][0], col[i][1], col[i][2], al[i]);
                vertex(q, m, d, ax, pa[i1], pb[i1], col[i1][0], col[i1][1], col[i1][2], al[i1]);
                vertex(q, m, d, ax, pa[i1] - n[1][0] * w[i1], pb[i1] - n[1][1] * w[i1], col[i1][0], col[i1][1], col[i1][2], al[i1] * 0.15f);
                vertex(q, m, d, ax, pa[i] - n[0][0] * w[i], pb[i] - n[0][1] * w[i], col[i][0], col[i][1], col[i][2], al[i] * 0.15f);
            }
        }
    }

    // ---- the vortices: a few MASSIVE ones, each made of many thin streaks of different colours that flow inward and mix

    private static final int VORTICES = 5, STREAKS = 60, STEPS = 12;
    private static final float R_MIN = 0.08f, R_MAX = 0.7f; // radius in flat sky units (1.25 is about 51 degrees from the centre)
    private static final float[][] VORTEX = new float[VORTICES][];  // {lon, lat, lonSpeed, latAmp, latSpeed, pitch (+ or -), rotation speed, hue}
    private static final float[][][] STREAK = new float[VORTICES][STREAKS][]; // {phase, angle, widthFactor, hue, speed}

    static {
        Random r = new Random(0xB0771CE5L);
        float[][] centres = {{0.6f, 1.1f}, {2.2f, 0.25f}, {3.6f, 0.75f}, {4.9f, 0.1f}, {5.7f, -0.8f}};
        for (int i = 0; i < VORTICES; i++) {
            VORTEX[i] = new float[]{centres[i][0], centres[i][1], 0.008f, 0.03f + r.nextFloat() * 0.03f,
                    0.02f + r.nextFloat() * 0.03f, (i % 2 == 0 ? 1f : -1f) * (1.2f + r.nextFloat() * 0.8f), (i % 2 == 0 ? 1f : -1f) * (0.03f + r.nextFloat() * 0.03f), r.nextFloat()};
            for (int j = 0; j < STREAKS; j++)
                STREAK[i][j] = new float[]{r.nextFloat(), r.nextFloat() * 6.2832f, 0.5f + r.nextFloat() * 1.4f, r.nextFloat(), 0.7f + r.nextFloat() * 0.8f};
        }
    }

    /** The filled-in body of a vortex: a disc of mixed colours that follow the spiral and fade out toward the rim. */
    private static void fillDisc(BufferBuilder q, Matrix4f m, float[] d, float[] ax, float[] v, double t) {
        final int rings = 22, segs = 72;
        float rad = R_MAX * 0.97f;
        float logRange = (float) Math.log(rad / R_MIN);
        for (int k = 0; k < rings; k++) {
            float r0 = (float) (R_MIN * Math.exp(logRange * k / rings)), r1 = (float) (R_MIN * Math.exp(logRange * (k + 1) / rings));
            for (int j = 0; j < segs; j++) {
                double t0 = Math.PI * 2 * j / segs, t1 = Math.PI * 2 * (j + 1) / segs;
                double[][] p = {{r0, t0}, {r0, t1}, {r1, t1}, {r1, t0}};
                for (double[] c : p) {
                    float r = (float) c[0];
                    float x = (float) Math.log(r / R_MIN);
                    double th = c[1] + v[5] * x + t * v[6];
                    float[] col = swirlColor(th, x * 0.9 + v[7] * 6.0, t);
                    float edge = (float) Math.pow(Math.max(0, 1f - (r / rad) * (r / rad)), 0.9);
                    vertex(q, m, d, ax, (float) Math.cos(c[1]) * r, (float) Math.sin(c[1]) * r, (int) col[0], (int) col[1], (int) col[2], 0.9f * col[3] * edge);
                }
            }
        }
    }

    /** A soft-edged ribbon along the points (pa, pb) with width, alpha and colour per point. */
    private static void ribbon(BufferBuilder q, Matrix4f m, float[] d, float[] ax, float[] pa, float[] pb, float[] w, float[] al, int[][] col) {
        int n = pa.length - 1;
        for (int i = 0; i < n; i++) {
            int i1 = i + 1;
            float[][] nn = new float[2][];
            for (int k = 0; k < 2; k++) {
                int p0 = Math.max(0, i + k - 1), p1 = Math.min(n, i + k + 1);
                float tx = pa[p1] - pa[p0], ty = pb[p1] - pb[p0], l = (float) Math.sqrt(tx * tx + ty * ty);
                if (l < 1e-6f) l = 1f;
                nn[k] = new float[]{-ty / l, tx / l};
            }
            vertex(q, m, d, ax, pa[i] + nn[0][0] * w[i], pb[i] + nn[0][1] * w[i], col[i][0], col[i][1], col[i][2], 0f);
            vertex(q, m, d, ax, pa[i1] + nn[1][0] * w[i1], pb[i1] + nn[1][1] * w[i1], col[i1][0], col[i1][1], col[i1][2], 0f);
            vertex(q, m, d, ax, pa[i1], pb[i1], col[i1][0], col[i1][1], col[i1][2], al[i1]);
            vertex(q, m, d, ax, pa[i], pb[i], col[i][0], col[i][1], col[i][2], al[i]);
            vertex(q, m, d, ax, pa[i], pb[i], col[i][0], col[i][1], col[i][2], al[i]);
            vertex(q, m, d, ax, pa[i1], pb[i1], col[i1][0], col[i1][1], col[i1][2], al[i1]);
            vertex(q, m, d, ax, pa[i1] - nn[1][0] * w[i1], pb[i1] - nn[1][1] * w[i1], col[i1][0], col[i1][1], col[i1][2], 0f);
            vertex(q, m, d, ax, pa[i] - nn[0][0] * w[i], pb[i] - nn[0][1] * w[i], col[i][0], col[i][1], col[i][2], 0f);
        }
    }

    private static void vortex(BufferBuilder q, Matrix4f m, double t, int vi) {
        float[] v = VORTEX[vi];
        double lon = v[0] + t * v[2], lat = Math.max(-1.3, Math.min(1.3, v[1] + v[3] * Math.sin(t * v[4] + vi)));
        float[] d = dir(lon, lat);
        float[] ax = basis(d);
        fillDisc(q, m, d, ax, v, t);
        float logRange = (float) Math.log(R_MAX / R_MIN);
        for (int j = 0; j < STREAKS; j++) {
            float[] sp = STREAK[vi][j];
            double frac = (sp[0] - t * 0.018 * sp[4]) % 1.0;
            if (frac < 0) frac += 1.0;
            float rc = (float) (R_MIN * Math.exp(frac * logRange));          // the streak slides inward, being dragged into the middle
            float env = (float) Math.pow(Math.sin(Math.PI * frac), 0.8);       // and fades in far out, fades out near the middle
            if (env < 0.03f) continue;
            float[] pa = new float[STEPS + 1], pb = new float[STEPS + 1], w = new float[STEPS + 1], al = new float[STEPS + 1];
            int[][] col = new int[STEPS + 1][];
            for (int i = 0; i <= STEPS; i++) {
                float s = -1f + 2f * i / STEPS;
                float r = rc * (1f + 0.5f * s);
                double th = sp[1] + v[5] * Math.log(r / R_MIN) + t * v[6] + 0.2 * Math.sin(t * 0.3 + sp[0] * 9);
                pa[i] = (float) Math.cos(th) * r; pb[i] = (float) Math.sin(th) * r;
                float tip = 1f - (float) Math.pow(Math.abs(s), 1.6);
                w[i] = rc * (0.022f + 0.035f * sp[2]) * (0.2f + 0.8f * tip);
                al[i] = 0.45f * env * tip;
                col[i] = armColor(v[7] + sp[3] * 1.0f + 0.15f * s + (float) t * 0.004f);
            }
            for (int i = 0; i < STEPS; i++) {
                int i1 = i + 1;
                float[][] n = new float[2][];
                for (int k = 0; k < 2; k++) {
                    int p0 = Math.max(0, i + k - 1), p1 = Math.min(STEPS, i + k + 1);
                    float tx = pa[p1] - pa[p0], ty = pb[p1] - pb[p0], l = (float) Math.sqrt(tx * tx + ty * ty);
                    if (l < 1e-6f) l = 1f;
                    n[k] = new float[]{-ty / l, tx / l};
                }
                // a streak is a thin ribbon with soft edges (centre opaque, edges clear)
                vertex(q, m, d, ax, pa[i] + n[0][0] * w[i], pb[i] + n[0][1] * w[i], col[i][0], col[i][1], col[i][2], 0f);
                vertex(q, m, d, ax, pa[i1] + n[1][0] * w[i1], pb[i1] + n[1][1] * w[i1], col[i1][0], col[i1][1], col[i1][2], 0f);
                vertex(q, m, d, ax, pa[i1], pb[i1], col[i1][0], col[i1][1], col[i1][2], al[i1]);
                vertex(q, m, d, ax, pa[i], pb[i], col[i][0], col[i][1], col[i][2], al[i]);
                vertex(q, m, d, ax, pa[i], pb[i], col[i][0], col[i][1], col[i][2], al[i]);
                vertex(q, m, d, ax, pa[i1], pb[i1], col[i1][0], col[i1][1], col[i1][2], al[i1]);
                vertex(q, m, d, ax, pa[i1] - n[1][0] * w[i1], pb[i1] - n[1][1] * w[i1], col[i1][0], col[i1][1], col[i1][2], 0f);
                vertex(q, m, d, ax, pa[i] - n[0][0] * w[i], pb[i] - n[0][1] * w[i], col[i][0], col[i][1], col[i][2], 0f);
            }
        }
        // tendrils hanging down out of the vortex, stretching toward the player
        final int tendrils = 16, ts = 16;
        for (int k = 0; k < tendrils; k++) {
            float[] sp = STREAK[vi][k];
            float len = 1.1f + 0.9f * sp[0] + 0.15f * (float) Math.sin(t * 0.5 + k);
            double th0 = k * Math.PI * 2 / tendrils + t * 0.12 * (v[5] > 0 ? 1 : -1);
            float[] pa = new float[ts + 1], pb = new float[ts + 1], w = new float[ts + 1], al = new float[ts + 1];
            int[][] col = new int[ts + 1][];
            for (int i = 0; i <= ts; i++) {
                float s = i / (float) ts;
                float rho = 0.2f * (1f - 0.75f * s) * (0.5f + sp[2] * 0.5f);
                double th = th0 + s * 5.0 * (v[5] > 0 ? 1 : -1) + t * 0.5;
                pa[i] = (float) Math.cos(th) * rho * (1f + s);
                pb[i] = -s * len + (float) Math.sin(th) * rho * 0.3f;
                w[i] = 0.035f * (1f - 0.8f * s) + 0.004f;
                al[i] = 0.8f * (float) Math.sin(Math.PI * Math.min(1f, s * 6f) * 0.5) * (1f - s * s);
                col[i] = armColor(v[7] + sp[3] + s * 0.6f + (float) t * 0.02f);
            }
            ribbon(q, m, d, ax, pa, pb, w, al, col);
        }
        // a glowing heart where everything is dragged to
        int[] c = armColor(v[7] + (float) t * 0.01f);
        disc(q, m, d, 0.3f, 0.3f, 0f, c, new int[]{40, 10, 80}, 0.35f, 1.6f, 0, 0, 0);
        disc(q, m, d, 0.06f, 0.06f, 0f, new int[]{255, 250, 235}, c, 0.9f, 1.0f, 0, 0, 0);
    }

    /** A very faint, slow veil of the old swirling colours over the whole sky, behind the vortexes. */
    private static void veil(Matrix4f m, double t) {
        final int stacks = 18, slices = 36;
        final float radius = 98f;
        BufferBuilder q = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        double ts = t * 0.4;
        for (int i = 0; i < stacks; i++) {
            double lat0 = -Math.PI / 2 + Math.PI * i / stacks, lat1 = -Math.PI / 2 + Math.PI * (i + 1) / stacks;
            for (int j = 0; j < slices; j++) {
                double lon0 = Math.PI * 2 * j / slices, lon1 = Math.PI * 2 * (j + 1) / slices;
                double[][] p = {{lon0, lat0}, {lon1, lat0}, {lon1, lat1}, {lon0, lat1}};
                for (double[] v : p) {
                    float[] c = swirlColor(v[0], v[1], ts);
                    float cl = (float) Math.cos(v[1]);
                    float a = 0.7f * c[3] + 0.25f; // a fairly even veil over the whole sky
                    int[] vc = vivid((int) c[0], (int) c[1], (int) c[2]);
                    q.addVertex(m, (float) (Math.cos(v[0]) * cl) * radius, (float) Math.sin(v[1]) * radius, (float) (Math.sin(v[0]) * cl) * radius)
                            .setColor(vc[0], vc[1], vc[2], (int) (a * 255));
                }
            }
        }
        BufferUploader.drawWithShader(q.buildOrThrow());
    }

    private static void swirls(Matrix4f m, double t) {
        gain = 3f;
        try { swirlsInner(m, t); } finally { gain = 1f; }
    }

    private static void swirlsInner(Matrix4f m, double t) {
        veil(m, t);
        for (int vi = 0; vi < VORTICES; vi++) {
            BufferBuilder q = begin();
            vortex(q, m, t, vi);
            end(q);
        }
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

    /**
     * Where a pathway's sign sits in the sky. All 22 get their own place on an evenly spread spiral (same for everyone, always),
     * so two signs never share the same spot.
     */
    public static float[] anchor(String id) {
        int idx = 0;
        for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(id)) idx = i;
        double zLo = Math.sin(-0.30), zHi = 1.0;
        double z = zLo + (zHi - zLo) * (idx + 0.5) / ALL.length;
        return dir(idx * 2.39996323, Math.asin(z));
    }

    private static void quadTex(float[] d, float[] ax, float half, float u0, float v0, float u1, float v1, Matrix4f m) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float[][] c = {{-1, -1, u0, v1}, {-1, 1, u0, v0}, {1, 1, u1, v0}, {1, -1, u1, v1}};
        for (float[] k : c) {
            float x = d[0] + ax[0] * k[0] * half + ax[3] * k[1] * half, y = d[1] + ax[1] * k[0] * half + ax[4] * k[1] * half, z = d[2] + ax[2] * k[0] * half + ax[5] * k[1] * half;
            float l = (float) Math.sqrt(x * x + y * y + z * z);
            b.addVertex(m, x / l * 100f, y / l * 100f, z / l * 100f).setUv(k[2], k[3]);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void sunAndMoon(Matrix4f m, boolean sun, boolean moon, int moonPhase) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        // like vanilla: additive blending, so the black square around the sun / moon picture disappears
        RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        if (sun) {
            float[] d = anchor("bard");
            RenderSystem.setShaderTexture(0, SUN);
            quadTex(d, basis(d), 0.3f, 0f, 0f, 1f, 1f, m);
        }
        if (moon) {
            boolean crimson = crimsonMoon();
            float[] d = anchor("apothecary");
            RenderSystem.setShaderTexture(0, crimson ? CRIMSON_MOON : MOON);
            if (crimson) RenderSystem.setShaderColor(1f, 0.35f, 0.3f, 1f);
            float u0 = (moonPhase % 4) / 4f, u1 = u0 + 0.25f, v0 = ((moonPhase / 4) % 2) / 2f, v1 = v0 + 0.5f;
            quadTex(d, basis(d), 0.3f, u0, v0, u1, v1, m);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        RenderSystem.defaultBlendFunc();
    }

    // ---- little drawing helpers, in the flat (a = sideways, b = up) coordinates around a sign's centre

    private static void line(BufferBuilder q, Matrix4f m, float[] d, float[] ax, float a0, float b0, float a1, float b1, float w, int[] c, float al) {
        float dx = a1 - a0, dy = b1 - b0, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-6f) return;
        float nx = -dy / l * w * 0.5f, ny = dx / l * w * 0.5f;
        vertex(q, m, d, ax, a0 + nx, b0 + ny, c[0], c[1], c[2], al);
        vertex(q, m, d, ax, a1 + nx, b1 + ny, c[0], c[1], c[2], al);
        vertex(q, m, d, ax, a1 - nx, b1 - ny, c[0], c[1], c[2], al);
        vertex(q, m, d, ax, a0 - nx, b0 - ny, c[0], c[1], c[2], al);
    }

    private static void fill(BufferBuilder q, Matrix4f m, float[] d, float[] ax, float[][] pts, int[] c, float al) { // 3 or 4 corners
        for (int i = 0; i < 4; i++) {
            float[] p = pts[Math.min(i, pts.length - 1)];
            vertex(q, m, d, ax, p[0], p[1], c[0], c[1], c[2], al);
        }
    }

    private static final int[] BLACK = {0, 0, 0};

    // ------------------------------------------------------------------ one distinct sign per pathway (names from the LOTM wiki)

    private static void lightning(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) { // Tyrant: a storm
        disc(q, m, d, 0.2f, 0.11f, 0f, new int[]{70, 70, 110}, new int[]{20, 20, 50}, 0.55f, 0.8f, 0, 0, 0); // dark storm cloud
        for (int slot = 0; slot < 3; slot++) {
            double period = 1.6 + slot * 0.7;
            long n = (long) Math.floor((t + slot * 0.6) / period);
            double age = (t + slot * 0.6) - n * period;
            if (age > 0.45) continue;
            float flick = age < 0.08 ? 1f : (age < 0.16 ? 0.35f : (age < 0.24 ? 0.9f : 0.5f * (1f - (float) ((age - 0.24) / 0.21))));
            Random r = new Random(n * 9973L + slot * 131L);
            float a = (r.nextFloat() - 0.5f) * 0.3f, b = 0.02f;
            for (int i = 0; i < 7; i++) {
                float na = a + (r.nextFloat() - 0.5f) * 0.06f, nb = b - 0.032f;
                line(q, m, d, ax, a, b, na, nb, 0.016f, new int[]{120, 150, 255}, 0.55f * flick);
                line(q, m, d, ax, a, b, na, nb, 0.005f, new int[]{240, 245, 255}, flick);
                a = na; b = nb;
            }
        }
    }

    private static void strings(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) { // Fool: puppet strings
        for (int i = 0; i < 7; i++) {
            float x = (i - 3) * 0.045f + 0.012f * (float) Math.sin(t * 0.8 + i), y = -0.14f + 0.02f * (float) Math.sin(t * 1.1 + i * 1.7);
            line(q, m, d, ax, 0f, 0.18f, x, y, 0.004f, new int[]{255, 230, 150}, 0.85f);
            disc(q, m, d, 0.012f, 0.012f, 0f, new int[]{255, 235, 170}, new int[]{200, 150, 60}, 0.9f, 0.6f, 0, 0, 0);
        }
        disc(q, m, d, 0.03f, 0.03f, 0f, new int[]{255, 240, 190}, new int[]{200, 150, 60}, 0.9f, 0.7f, 0, 0, 0);
    }

    private static void glitch(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) { // Error: glitching blocks
        long n = (long) Math.floor(t * 6);
        Random r = new Random(n * 77L);
        for (int i = 0; i < 9; i++) {
            float a = (r.nextFloat() - 0.5f) * 0.36f, b = (r.nextFloat() - 0.5f) * 0.36f, w = 0.01f + r.nextFloat() * 0.05f, h = 0.008f + r.nextFloat() * 0.02f;
            int[] c = r.nextInt(3) == 0 ? new int[]{255, 70, 120} : new int[]{70, 255, 220};
            fill(q, m, d, ax, new float[][]{{a, b}, {a + w, b}, {a + w, b + h}, {a, b + h}}, c, 0.5f + 0.4f * r.nextFloat());
        }
    }

    // Door: the Door pathway's own model from the LOTM mod (its high-sequence door: arched frame with blue glyphs, a starry black doorway and a vortex),
    // seen from the front. Baked from the mod's model and texture into rectangles: row range, column range and colour, in a 64 x 128 grid.
    private static final String DOOR_RECTS = "0000<c00101=@10101AA20101BC10101DD30101EG10101IM10101NN40101OO50101PQ20101RU10101VV60101XX10101YY20101Z]10101^^20101_b10102HH20103WW20107<<00107cc00202=?10202@A20202BB50202CE10202FF20202GG50202IL10202MM20202NN10202OP50202QR20202SV10202XY50202Z\\10202]^20202__50202`b10303=>10303??20303@C50303FH20303II50303JJ20303MN20303OQ50303RR20303SU10303VV50303XX10303Z[10303\\\\20303]`50304DE10304KL10304ab10305YY50404=?10404@@50404AC20404JJ60404MM50404NN20404OR50404SS20404UU10404VV20404Z\\10404]]50404^`20405FG20405HH50405II20405TT70405WW50405XX20505>>80505?@10505AA50505BB20505CE10505JN10505OP20505QQ90505RS10505UV10505ZZ10505[[80505\\]10505^^50505__20505`a10505bb70506==10606>>70606??80606@D10606EE70606FG10606HH00606IM10606NN80606OO50606PQ20606R^10606__90606`b10707C\\00708=B10708]b108088<00808cg0080<D[1080>CC0080>\\\\00909<<30909==20909>@10909AA90909BB10909]_10909``80909ad10909ee8090:ff1090<9;1090?880090?gg00:0:<<20:0:=>50:0:@B10:0:]]10:0:^^80:0:_`10:0:aa20:0:bb50:0:cc20:0:dd10:0:ee00:0;??20;0;>>30;0;@@50;0;]^10;0;__20;0;``50;0;ab20;0;cc50;0;dd20;0<<=50;0<AB10;0<ef10<0<??50<0<@@20<0<]_10<0<``20<0<aa50<0<bd20<0=>>20=0=9<10=0===50=0=?B10=0=DV10=0=WW:0=0=X[10=0=]a10=0=bc20=0=df10>0>9;10>0><=90>0>>?10>0>@@90>0>AB10>0>D[10>0>]f10?0??C00?0?DE10?0?FF:0?0?G[10?0?\\`00?0?aa60?0?bb70?0?cc10?0?dd40?0?ef10?0@9>10@0@4800@0@af10@0@gk00@0E@_10@0F??00@0F``00A0A5680A0A7>10A0Aac10A0Add80A0Aej10A1d4400A1nkk00B0B6920B0B:>10B0Bae10B0Bfi20B0Bjj10B0C5510C0C6650C0C7920C0C::80C0C;>10C0Cad10C0Cee;0C0Cfj20D0D5610D0D7750D0D8920D0D:=10D0D>>40D0Dae10D0Dff90D0Dgg50D0Dhi20D0Ejj10E0E5510E0E6820E0E9950E0E:>10E0Eaa90E0Ebe10E0Efh20E0Eii50F0F7720F0F8850F0F9;10F0F<<80F0F==70F0F>>10F0F@Y10F0FZZ:0F0F[_10F0Faf10F0Fgg20F0Fhh50F0Fij10F0G5610G0G7700G0G8880G0G9:10G0G;?00G0G@_10G0G`d00G0Geh10G0Gii70G0Gjj10H0H5570H0H6610H0H7770H0H8:10H0I<c10H0Iej10H0c;;00H1ndd00I0I5810I0I9900I0I::10J0J5:10J0J<?10J0J@@:0J0JAc10J0Jeg10J0Jhh00J0Jij10K0K5710K0K8880K0K9:10K0Keh10K0Kii80K0L<c10K0Ljj10L0L5510L0L6920L0L::10L0Lfi20L0Qee10M0M5590M0M6650M0M7:20M0M<<:0M0M=c10M0Mff50M0Mgj20N0N6850N0N<W10N0NXX:0N0NYc10N0Nfh50N0O9:20N0Oij20N0P5510O0O6650O0O7720O0O8850O0Off50O0Ogg20O0Ohh50O0R<c10P0P6620P0P7950P0P::20P0Pff20P0Pgi50P0Pjj20Q0Qff70Q0Qgj10Q0T5:10R0Ree90R0Rfj10S0S<P10S0SQQ:0S0SRc10S0Uej10T0T<c10U0U5810U0U9970U0U::10U0U<Z10U0U[[:0U0U\\c10V0V5610V0V7820V0V9:10V0V<D10V0VEE:0V0VFc10V0Vef10V0Vgh20V0Vij10W0W6720W0W8850W0W9920W0Wfg20W0Whh50W0Wii20W0Wjj70W0X5510W0Xee10W0Y::10W0Z<c10X0X6820X0X9950X0Xfh20X0Xii50X0Yjj10Y0Y7750Y0Y8920Y0Ygg50Y0Yhi20Y0Z5610Y0Zef10Z0Z7720Z0Z8850Z0Zgg20Z0Zhh50Z0[9:10Z0\\ij10[0[5710[0[8820[0[<U10[0[VV:0[0[Wc10[0[hh20[0\\eg10\\0\\5510\\0\\6680\\0\\7:10\\0\\<c10\\0\\hh80]0]5:10]0]<P10]0]QQ:0]0]RY10]0]ZZ:0]0][[10]0]\\\\:0]0]]c10]0]ej10^0^5570^0^6:10^0^ef10^0^gg60^0^hj10^0_<c10_0_5:10_0_ej10`0`7790`0`8:10`0`<`10`0`aa:0`0`bc10`0`gg70`0`hh10`0`ii70`0`jj10`0a5610`0aef10a0a7720a0a8850a0a9:10a0a<a10a0abb:0a0acc10a0agg20a0ahh50a0aij10b0b6720b0b8950b0b::10b0b<]10b0b^^:0b0b_c10b0bfg20b0bhi50b0bjj10b0c5510b0cee10c0c6650c0c7720c0c8850c0c9:20c0c<@10c0cAA:0c0cBD10c0cEE:0c0cFK10c0cLL:0c0cMc10c0cff50c0cgg20c0chh50c0cij20d0d5610d0d7850d0d9920d0d:;00d0d<H10d0dII:0d0dJc10d0def10d0dgh50d0dii20d0djj10e0e5710e0e8820e0e9980e0e::10e0e<P10e0eQQ:0e0eRS10e0eTT:0e0eUc10e0eeg10e0ehh20e0eij10e1n;;00f0f5:10f0f<c10f0fej10g0g5610g0g7800g0g<@10g0gAA:0g0gBX10g0gYY:0g0gZc10g0gee70g0gfj10g0h9:10h0h5710h0h8800h0h<F10h0hGG:0h0hHc10h0jej10i0i5810i0i9960i0i::10i0j<c10j0j5:10k0k6950k0k::20k0kfi50k0kjj20k0l<<10k0l==:0k0l>c10k0m5510k0mee10l0l6720l0l8810l0l9950l0l::10l0lfg20l0lhh10l0lii50l0ljj10m0m6750m0m8:20m0m<F10m0mGG:0m0mHc10m0mfg50m0mhj20n0n5610n0n7720n0n8850n0n9:10n0nef10n0ngg20n0nhh50n0nij10n10<c10o0o5:10o0oej11010571101088810109991010::11010ee91010fj111115:11111<?11111@@:1111Ac11113ej1121255712126:11212<c113135:11313<X11313YY:1313Zc11414778141488114149921414eg11414hh21414ij114155611415::11415<c1151577515158921515gg51515hi21515jj11516ef116165551616661161678516169:21616<\\11616]]:1616^c11616gh51616ij217177951717::21717<<:1717=A11717BB:1717Cc11717ee51717ff21717gi51717jj2171856218187:51818<]11818^^:1818_c11818ef21818gj51919561191978219199981919::11919ee21919ff11919ij1191:gh2191;<c11:1:6721:1:8851:1:9911:1:::81:1:ef11:1:ii51:1:jj11:1;5511;1;6681;1;7:11;1;ee71;1;ff11;1;gg01;1;hj11<1<<Y11<1<ZZ:1<1<[c11<1<eh11<1<ii71<1<jj11<1=5:11=1=<c11=1?ej11>1>5511>1>6601>1>7711>1>8871>1>9:11>1><R11>1>SS:1>1>Tc11?1?5:11?1?<c11@1@5711@1@8821@1@<P11@1@QQ:1@1@Rc11@1@eg11@1@hh21@1@ii11@1@jj81@1A9:11A1A5531A1A6721A1A8851A1Afg21A1Ahh51A1Aij11A1D<c11A1Dee11B1B5511B1B6751B1B8:21B1Bfg51B1Bhj21C1C5571C1C6821C1C9951C1Cfh21C1Cii51C1D::11C1Djj11D1D5591D1D6921D1Dfi21E1E5:11E1E<b11E1Ecc:1E1Gej11F1F5511F1F6631F1F7911F1F::61F1F<c11G1G5:11G1G<^11G1G__:1G1G`c11H1H5571H1H6:11H1H<D11H1HEE:1H1HFW11H1HXX:1H1HYc11H1Hee81H1Hfj11I1I5:11I1I<E11I1IFF:1I1IGL11I1IMM:1I1IN_11I1I``:1I1Iac11I1Ieg11I1Ihh81I1Jij11J1J6661J1J7721J1J8851J1J9:11J1J<G11J1JHH:1J1JIc11J1Jef11J1Jgg21J1Jhh51J1L5511K1K6721K1K8951K1K::11K1K<S11K1KTT:1K1KUc11K1Kfg21K1Khi51K1Kjj11K1Lee11L1L6651L1L7721L1L8851L1L9:21L1L<O11L1LPP:1L1LQc11L1Lff51L1Lgg21L1Lhh51L1Lij21M1M5611M1M7851M1M9921M1M::11M1M<Q11M1MRR:1M1MSc11M1Mef11M1Mgh51M1Mii21M1Mjj11N1N5711N1N8821N1N9:11N1N<F11N1NGG:1N1NH_11N1N``:1N1Nac11N1Neg11N1Nhh21N1Nij11O1O5:11O1O<c11O1Oej11P1P5591P1P6:11P1P<F11P1PGG:1P1PHS11P1PTU:1P1PVc11P1Peh11P1Pii81P1Pjj11Q1Q<c11Q1R5:11Q1Sej11R1R<S11R1RTT:1R1RUW11R1RXX:1R1RYc11S1S5811S1S9981S1S::11S1S<Z11S1S[[:1S1S\\c11T1T7721T1T8851T1T9:11T1T<c11T1Tgg21T1Thh51T1Tij11T1U5611T1Uef11U1U7751U1U8921U1U<G11U1UHH:1U1UIc11U1Ugg51U1Uhi21U1V::11U1Vjj11V1V6721V1V8811V1V9951V1Vfg21V1Vhh11V1Vii51V1W5511V1Wee11V1Y<c11W1W6951W1W::21W1Wfi51W1Wjj21X1X5611X1X7851X1X9921X1X::11X1Xef11X1Xgh51X1Xii21X1Xjj11Y1Y5581Y1Y6:11Y1[ej11Z1Z5571Z1Z6911Z1Z::81Z1Z<<11Z1Z==:1Z1Z>E11Z1ZFF:1Z1ZGc11[1[5711[1[8801[1[9:11[1[<C11[1[DD:1[1[Ec11\\1\\5:11\\1\\ee11\\1\\ff91\\1\\gj11\\1]<c11]1]5811]1]9981]1]::11]1]ej11^1^6921^1^::71^1^<a11^1^bb:1^1^cc11^1^fi21^1^jj11^1b5511^1bee11_1_6651_1_7:21_1_ff51_1_gj21_1c<c11`1`6851`1`fh51`1a9:21`1aij21a1a6651a1a7721a1a8851a1aff51a1agg21a1ahh51b1b6621b1b7951b1b::21b1bff21b1bgi51b1bjj21c1d5:11c1eej11d1d<A11d1dBB:1d1dCN11d1dOO:1d1dPc11e1e4501e1e6:11e1e<O11e1ePQ:1e1eRc11f1f5911f1f::71f1f<c11f1feh11f1fii71f1fjj11f1n4401g1g5:11g1g<@11g1gAA:1g1gBM11g1gNN:1g1gOc11g1gej11h1h5711h1h8821h1h9:11h1h<<11h1h==:1h1h>c11h1heg11h1hhh21h1hij11i1i5611i1i7751i1i8921i1ief11i1igg51i1ihi21i1k::11i1kjj11i1m<c11j1j5511j1j6821j1j9951j1jee11j1jfh21j1jii51k1k5531k1k6721k1k8851k1k9981k1kef11k1kgg21k1khi51l1l5511l1l6951l1l::21l1lee11l1lfi51l1ljj21m1m5911m1m::71m1mei11m1mjj81n1n5511n1n6671n1n7:11n1n<<11n1n==:1n1n>c11n1neg11n1nhh01n1nij11o1o4;01o1o<L11o1oMM:1o1oNc11o1odk0";
    private static final int[] DOOR_PAL = {90,90,126,8,8,24,54,18,198,126,90,162,90,90,162,18,90,255,54,54,126,54,54,90,54,18,90,90,54,126,90,90,90,126,126,198};
    private static float[][] doorRects;

    private static void door(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) {
        if (doorRects == null) {
            int n = DOOR_RECTS.length() / 7;
            doorRects = new float[n][];
            for (int i = 0; i < n; i++) {
                int o = i * 7;
                doorRects[i] = new float[]{(DOOR_RECTS.charAt(o) - 48) * 64 + DOOR_RECTS.charAt(o + 1) - 48, (DOOR_RECTS.charAt(o + 2) - 48) * 64 + DOOR_RECTS.charAt(o + 3) - 48,
                        DOOR_RECTS.charAt(o + 4) - 48, DOOR_RECTS.charAt(o + 5) - 48, DOOR_RECTS.charAt(o + 6) - 48};
            }
        }
        final float px = 0.4f / 128f;   // the door is 0.4 tall
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 0.8);
        disc(q, m, d, 0.16f, 0.26f, 0f, new int[]{120, 150, 255}, new int[]{20, 30, 90}, 0.35f * pulse, 1.2f, 0, 0, 0);   // a glow round it
        for (float[] r : doorRects) {
            int ci = (int) r[4] * 3;
            int[] c = {DOOR_PAL[ci], DOOR_PAL[ci + 1], DOOR_PAL[ci + 2]};
            float x0 = (r[2] - 32f) * px, x1 = (r[3] + 1f - 32f) * px, y0 = (64f - r[1] - 1f) * px, y1 = (64f - r[0]) * px;
            fill(q, m, d, ax, new float[][]{{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}}, c, 0.95f);
        }
    }

    private static void eye(BufferBuilder q, Matrix4f m, float[] d, double t) { // Visionary: a watching eye
        float open = 0.25f + 0.75f * smooth((float) (0.5 + 0.5 * Math.sin(t * 0.35)) * 1.6f - 0.2f);
        disc(q, m, d, 0.2f, 0.2f * open, 0f, new int[]{240, 250, 255}, new int[]{20, 90, 70}, 0.7f, 0.9f, 0, 0, 0);
        disc(q, m, d, 0.08f, 0.08f * Math.min(1f, open * 1.2f), 0f, new int[]{150, 255, 200}, new int[]{70, 130, 100}, 0.95f, 0.5f, 0, 0, 0);
        disc(q, m, d, 0.032f, 0.032f * Math.min(1f, open * 1.2f), 0f, BLACK, BLACK, 1f, 0.3f, 0, 0, 0);
    }

    private static void tower(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) { // White Tower: a tall white tower
        float p = 0.7f + 0.3f * (float) Math.sin(t * 0.7);
        fill(q, m, d, ax, new float[][]{{-0.035f, -0.17f}, {0.035f, -0.17f}, {0.014f, 0.13f}, {-0.014f, 0.13f}}, new int[]{235, 238, 255}, 0.85f);
        fill(q, m, d, ax, new float[][]{{-0.02f, 0.13f}, {0.02f, 0.13f}, {0.0f, 0.18f}, {0.0f, 0.18f}}, new int[]{255, 255, 255}, 0.9f);
        disc(q, m, d, 0.06f, 0.06f, 0f, new int[]{255, 255, 255}, new int[]{150, 170, 255}, 0.8f * p, 1.2f, 0, 0, 0);
    }

    private static void hanged(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) { // Hanged Man: a figure swinging on a rope
        float sw = 0.05f * (float) Math.sin(t * 0.9);
        float topB = 0.2f;
        float hx = sw * 0.9f, hy = 0.02f; // head
        int[] rope = {190, 150, 120}, body = {60, 25, 60};
        line(q, m, d, ax, 0f, topB, hx, hy + 0.025f, 0.005f, rope, 0.9f);
        disc(q, m, d, 0.2f, 0.22f, 0f, new int[]{150, 60, 120}, new int[]{20, 0, 25}, 0.3f, 1.2f, 0, 0, 0); // sickly glow
        disc(q, m, dirOffset(d, hx, hy), 0.026f, 0.026f, 0f, body, new int[]{140, 60, 120}, 0.95f, 0.15f, 0, 0, 0);       // head
        fill(q, m, d, ax, new float[][]{{hx - 0.03f, hy - 0.03f}, {hx + 0.03f, hy - 0.03f}, {hx + sw * 0.5f + 0.022f, hy - 0.13f}, {hx + sw * 0.5f - 0.022f, hy - 0.13f}}, body, 0.95f); // body
        line(q, m, d, ax, hx + sw * 0.5f - 0.01f, hy - 0.13f, hx + sw * 0.8f - 0.015f, hy - 0.22f, 0.014f, body, 0.95f); // legs
        line(q, m, d, ax, hx + sw * 0.5f + 0.01f, hy - 0.13f, hx + sw * 0.8f + 0.015f, hy - 0.22f, 0.014f, body, 0.95f);
        line(q, m, d, ax, hx - 0.03f, hy - 0.04f, hx - 0.05f, hy - 0.11f, 0.012f, body, 0.9f); // arms
        line(q, m, d, ax, hx + 0.03f, hy - 0.04f, hx + 0.05f, hy - 0.11f, 0.012f, body, 0.9f);
    }

    private static void eclipse(BufferBuilder q, Matrix4f m, float[] d, double t) { // Darkness: a black sun with a thin corona
        float p = 0.6f + 0.4f * (float) Math.sin(t * 0.5);
        disc(q, m, d, 0.2f, 0.2f, 0f, new int[]{150, 150, 190}, new int[]{20, 20, 40}, 0.35f * p, 1.6f, 0, 0, 0);
        disc(q, m, d, 0.1f, 0.1f, 0f, BLACK, BLACK, 0.95f, 0.15f, 0, 0, 0);
    }

    private static void souls(BufferBuilder q, Matrix4f m, float[] d, double t) { // Death: pale souls rising
        for (int i = 0; i < 4; i++) {
            double f = (t * 0.05 + i * 0.25) % 1.0;
            float a = (i - 1.5f) * 0.08f + 0.02f * (float) Math.sin(t * 0.9 + i), b = -0.15f + (float) f * 0.35f;
            float al = (float) Math.sin(Math.PI * f) * 0.85f;
            float[] p = dirOffset(d, a, b);
            disc(q, m, p, 0.022f, 0.05f, 0f, new int[]{235, 245, 255}, new int[]{110, 160, 220}, al, 0.8f, 0, 0, 0);
        }
    }

    /** Where a god's own pathway symbol is drawn: just below its sign, so it never covers the sign. */
    public static float[] symbolSpot(String id) {
        return dirOffset(anchor(id), 0f, -0.3f);
    }

    /** Direction moved (a, b) from d in the flat coordinates (used to place small parts of a sign). */
    private static float[] dirOffset(float[] d, float a, float b) {
        float[] ax = basis(d);
        float x = d[0] + ax[0] * a + ax[3] * b, y = d[1] + ax[1] * a + ax[4] * b, z = d[2] + ax[2] * a + ax[5] * b;
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / l, y / l, z / l};
    }

    private static void giant(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) { // Twilight Giant: a giant against the dusk
        float p = 0.7f + 0.3f * (float) Math.sin(t * 0.4);
        disc(q, m, d, 0.24f, 0.2f, 0f, new int[]{255, 160, 70}, new int[]{150, 50, 110}, 0.7f * p, 1.0f, 0, 0, 0); // dusk glow behind
        int[] k = {10, 5, 15};
        fill(q, m, d, ax, new float[][]{{-0.04f, -0.05f}, {0.04f, -0.05f}, {0.03f, 0.08f}, {-0.03f, 0.08f}}, k, 0.92f);       // torso
        fill(q, m, d, ax, new float[][]{{-0.035f, -0.17f}, {-0.005f, -0.17f}, {-0.005f, -0.05f}, {-0.04f, -0.05f}}, k, 0.92f); // legs
        fill(q, m, d, ax, new float[][]{{0.005f, -0.17f}, {0.035f, -0.17f}, {0.04f, -0.05f}, {0.005f, -0.05f}}, k, 0.92f);
        fill(q, m, d, ax, new float[][]{{-0.07f, 0.0f}, {-0.04f, 0.07f}, {-0.03f, 0.04f}, {-0.06f, -0.04f}}, k, 0.92f);        // arms
        fill(q, m, d, ax, new float[][]{{0.07f, 0.0f}, {0.04f, 0.07f}, {0.03f, 0.04f}, {0.06f, -0.04f}}, k, 0.92f);
        disc(q, m, dirOffset(d, 0f, 0.115f), 0.028f, 0.028f, 0f, k, k, 0.95f, 0.15f, 0, 0, 0);                                // head
    }

    private static void lily(BufferBuilder q, Matrix4f m, float[] d, double t) { // Demoness: a red spider lily
        for (int i = 0; i < 9; i++) {
            double ang = i * Math.PI * 2 / 9 + t * 0.06;
            float[] p = dirOffset(d, (float) Math.cos(ang) * 0.09f, (float) Math.sin(ang) * 0.09f);
            disc(q, m, p, 0.012f, 0.1f, (float) (ang - Math.PI / 2), new int[]{255, 70, 120}, new int[]{150, 10, 60}, 0.85f, 0.7f, 0, 0, 0);
        }
        disc(q, m, d, 0.025f, 0.025f, 0f, new int[]{255, 200, 220}, new int[]{200, 40, 100}, 0.9f, 0.5f, 0, 0, 0);
    }

    private static void flames(BufferBuilder q, Matrix4f m, float[] d, double t) { // Red Priest: flames
        for (int i = 0; i < 4; i++) {
            float h = 0.07f + 0.05f * (0.5f + 0.5f * (float) Math.sin(t * (3 + i) + i));
            float[] p = dirOffset(d, (i - 1.5f) * 0.06f, -0.05f + h * 0.5f);
            disc(q, m, p, 0.03f, h, 0f, new int[]{255, 230, 120}, new int[]{230, 40, 10}, 0.85f, 0.8f, 0, 0, 0);
        }
        disc(q, m, d, 0.2f, 0.12f, 0f, new int[]{255, 90, 20}, new int[]{120, 0, 0}, 0.3f, 1.4f, 0, 0, 0);
    }

    private static void hexagram(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) { // Hermit: an occult seal
        float rot = (float) (t * 0.12);
        int[] c = {200, 170, 255};
        for (int tri = 0; tri < 2; tri++) {
            for (int i = 0; i < 3; i++) {
                double a0 = rot + tri * Math.PI / 3 * 1.0 + i * Math.PI * 2 / 3 + Math.PI / 2, a1 = a0 + Math.PI * 2 / 3;
                line(q, m, d, ax, (float) Math.cos(a0) * 0.17f, (float) Math.sin(a0) * 0.17f, (float) Math.cos(a1) * 0.17f, (float) Math.sin(a1) * 0.17f, 0.006f, c, 0.85f);
            }
        }
        ring(q, m, d, 0.18f, 0.2f, c, 0.6f, 0, 0);
    }

    private static void cogs(BufferBuilder q, Matrix4f m, float[] d, double t) { // Paragon: turning gears
        ring(q, m, d, 0.12f, 0.19f, new int[]{150, 255, 255}, 0.85f, 10, (float) t * 0.5f);
        ring(q, m, d, 0.04f, 0.09f, new int[]{255, 255, 255}, 0.9f, 6, (float) -t * 0.9f);
    }

    private static void wheel(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) { // Wheel of Fortune: a turning wheel
        float rot = (float) (t * 0.25);
        int[] c = {255, 220, 110};
        ring(q, m, d, 0.16f, 0.19f, c, 0.9f, 0, 0);
        for (int i = 0; i < 8; i++) {
            double a = rot + i * Math.PI / 4;
            line(q, m, d, ax, 0f, 0f, (float) Math.cos(a) * 0.17f, (float) Math.sin(a) * 0.17f, 0.007f, c, 0.85f);
        }
    }

    private static void tree(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) { // Mother: a growing tree
        float grow = 0.6f + 0.4f * (float) Math.sin(t * 0.4);
        int[] bark = {110, 220, 110};
        line(q, m, d, ax, 0f, -0.18f, 0f, -0.18f + 0.22f * grow, 0.012f, bark, 0.9f);
        for (int i = 0; i < 4; i++) {
            float by = -0.1f + i * 0.055f, side = (i % 2 == 0) ? 1 : -1, len = 0.09f * grow;
            line(q, m, d, ax, 0f, by * grow, side * len, by * grow + 0.05f * grow, 0.007f, bark, 0.85f);
            disc(q, m, dirOffset(d, side * len, by * grow + 0.05f * grow), 0.022f, 0.022f, 0f, new int[]{190, 255, 160}, new int[]{40, 160, 50}, 0.85f, 0.6f, 0, 0, 0);
        }
        disc(q, m, dirOffset(d, 0f, -0.18f + 0.22f * grow), 0.035f, 0.035f, 0f, new int[]{190, 255, 160}, new int[]{40, 160, 50}, 0.9f, 0.6f, 0, 0, 0);
    }

    private static void vortex(BufferBuilder q, Matrix4f m, float[] d, double t) { // Abyss: a dark red whirlpool
        disc(q, m, d, 0.2f, 0.2f, 0f, new int[]{60, 0, 10}, new int[]{0, 0, 0}, 0.9f, 0.9f, 5, 3f, (float) (t * 0.35));
        disc(q, m, d, 0.05f, 0.05f, 0f, BLACK, BLACK, 1f, 0.4f, 0, 0, 0);
    }

    private static void chains(BufferBuilder q, Matrix4f m, float[] d, double t) { // Chained: a hanging chain
        for (int i = 0; i < 6; i++) {
            float sway = 0.03f * (float) Math.sin(t * 0.8 - i * 0.35) * (i / 5f + 0.2f);
            float[] p = dirOffset(d, sway, 0.16f - i * 0.055f);
            ring(q, m, p, 0.012f, (i % 2 == 0) ? 0.03f : 0.022f, new int[]{185, 190, 205}, 0.95f, 0, 0);
        }
    }

    private static void crown(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) { // Black Emperor: a crown
        float p = 0.75f + 0.25f * (float) Math.sin(t * 0.6);
        int[] gold = {255, 205, 80};
        disc(q, m, d, 0.2f, 0.2f, 0f, new int[]{40, 30, 0}, new int[]{0, 0, 0}, 0.6f, 0.8f, 0, 0, 0);
        fill(q, m, d, ax, new float[][]{{-0.11f, -0.07f}, {0.11f, -0.07f}, {0.11f, -0.02f}, {-0.11f, -0.02f}}, gold, 0.9f * p);
        fill(q, m, d, ax, new float[][]{{-0.11f, -0.02f}, {-0.07f, -0.02f}, {-0.11f, 0.09f}, {-0.11f, 0.09f}}, gold, 0.9f * p);
        fill(q, m, d, ax, new float[][]{{-0.04f, -0.02f}, {0.04f, -0.02f}, {0f, 0.12f}, {0f, 0.12f}}, gold, 0.9f * p);
        fill(q, m, d, ax, new float[][]{{0.07f, -0.02f}, {0.11f, -0.02f}, {0.11f, 0.09f}, {0.11f, 0.09f}}, gold, 0.9f * p);
    }

    private static void scales(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) { // Justiciar: scales of justice
        float tilt = 0.025f * (float) Math.sin(t * 0.6);
        int[] gold = {255, 225, 130};
        line(q, m, d, ax, 0f, -0.17f, 0f, 0.14f, 0.01f, gold, 0.9f);
        line(q, m, d, ax, -0.14f, 0.14f + tilt, 0.14f, 0.14f - tilt, 0.008f, gold, 0.9f);
        for (int s = -1; s <= 1; s += 2) {
            float ex = s * 0.14f, ey = 0.14f + s * -1 * tilt * -1f;
            ey = 0.14f - s * tilt;
            line(q, m, d, ax, ex, ey, ex - 0.05f, ey - 0.09f, 0.004f, gold, 0.8f);
            line(q, m, d, ax, ex, ey, ex + 0.05f, ey - 0.09f, 0.004f, gold, 0.8f);
            disc(q, m, dirOffset(d, ex, ey - 0.1f), 0.06f, 0.016f, 0f, gold, new int[]{200, 150, 60}, 0.9f, 0.6f, 0, 0, 0);
        }
    }

    private static void sign(BufferBuilder q, Matrix4f m, String id, double t) {
        float[] d = anchor(id);
        float[] ax = basis(d);
        switch (id) {
            case "sailor" -> lightning(q, m, d, ax, t);
            case "seer" -> strings(q, m, d, ax, t);
            case "marauder" -> glitch(q, m, d, ax, t);
            case "apprentice" -> door(q, m, d, ax, t);
            case "spectator" -> eye(q, m, d, t);
            case "reader" -> tower(q, m, d, ax, t);
            case "secrets_supplicant" -> hanged(q, m, d, ax, t);
            case "sleepless" -> eclipse(q, m, d, t);
            case "corpse_collector" -> souls(q, m, d, t);
            case "warrior" -> giant(q, m, d, ax, t);
            case "assassin" -> lily(q, m, d, t);
            case "hunter" -> flames(q, m, d, t);
            case "mystery_pryer" -> hexagram(q, m, d, ax, t);
            case "savant" -> cogs(q, m, d, t);
            case "monster" -> wheel(q, m, d, ax, t);
            case "planter" -> tree(q, m, d, ax, t);
            case "criminal" -> vortex(q, m, d, t);
            case "prisoner" -> chains(q, m, d, t);
            case "lawyer" -> crown(q, m, d, ax, t);
            case "arbiter" -> scales(q, m, d, ax, t);
            default -> { } // bard (the sun) and apothecary (the moon) are textures, drawn separately
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
        if (sun || moon) sunAndMoon(m, sun, moon, moonPhase);
    }
}
