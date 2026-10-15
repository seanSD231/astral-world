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

    /** Colours that every arm of a swirl runs through, one after the other (looping). */
    private static final int[][] ARM_COLORS = {
            {255, 70, 110}, {255, 130, 60}, {255, 215, 80}, {170, 255, 90}, {60, 255, 170}, {50, 215, 255},
            {70, 120, 255}, {140, 90, 255}, {215, 80, 255}, {255, 90, 200}, {255, 120, 150}, {90, 240, 230}
    };

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

    private static final int VORTICES = 5, STREAKS = 90, STEPS = 12;
    private static final float R_MIN = 0.08f, R_MAX = 1.25f; // radius in flat sky units (1.25 is about 51 degrees from the centre)
    private static final float[][] VORTEX = new float[VORTICES][];  // {lon, lat, lonSpeed, latAmp, latSpeed, pitch (+ or -), rotation speed, hue}
    private static final float[][][] STREAK = new float[VORTICES][STREAKS][]; // {phase, angle, widthFactor, hue, speed}

    static {
        Random r = new Random(0xB0771CE5L);
        float[][] centres = {{0.6f, 1.1f}, {2.2f, 0.25f}, {3.6f, 0.75f}, {4.9f, 0.1f}, {5.7f, -0.8f}};
        for (int i = 0; i < VORTICES; i++) {
            VORTEX[i] = new float[]{centres[i][0], centres[i][1], 0.006f + r.nextFloat() * 0.01f, 0.1f + r.nextFloat() * 0.12f,
                    0.02f + r.nextFloat() * 0.03f, (i % 2 == 0 ? 1f : -1f) * (1.2f + r.nextFloat() * 0.8f), (i % 2 == 0 ? 1f : -1f) * (0.03f + r.nextFloat() * 0.03f), r.nextFloat()};
            for (int j = 0; j < STREAKS; j++)
                STREAK[i][j] = new float[]{r.nextFloat(), r.nextFloat() * 6.2832f, 0.5f + r.nextFloat() * 1.4f, r.nextFloat(), 0.7f + r.nextFloat() * 0.8f};
        }
    }

    /** The filled-in body of a vortex: a disc of mixed colours that follow the spiral and fade out toward the rim. */
    private static void fillDisc(BufferBuilder q, Matrix4f m, float[] d, float[] ax, float[] v, double t) {
        final int rings = 12, segs = 40;
        float rad = R_MAX * 0.95f;
        float logRange = (float) Math.log(rad / R_MIN);
        for (int k = 0; k < rings; k++) {
            float r0 = (float) (R_MIN * Math.exp(logRange * k / rings)), r1 = (float) (R_MIN * Math.exp(logRange * (k + 1) / rings));
            for (int j = 0; j < segs; j++) {
                double t0 = Math.PI * 2 * j / segs, t1 = Math.PI * 2 * (j + 1) / segs;
                double[][] p = {{r0, t0}, {r0, t1}, {r1, t1}, {r1, t0}};
                for (double[] c : p) {
                    float r = (float) c[0];
                    double th = c[1] + v[5] * Math.log(r / R_MIN) + t * v[6];
                    float arm = 0.5f + 0.5f * (float) Math.sin(5 * th);
                    float hue = v[7] + (float) (c[1] / (Math.PI * 2)) * 0.9f + 0.25f * (float) Math.log(r / R_MIN) + (float) t * 0.006f + 0.25f * arm;
                    int[] col = armColor(hue);
                    float edge = (float) Math.pow(Math.max(0, 1f - (r / rad) * (r / rad)), 1.1);
                    float al = 0.5f * edge * (0.35f + 0.65f * arm);
                    vertex(q, m, d, ax, (float) Math.cos(c[1]) * r, (float) Math.sin(c[1]) * r, col[0], col[1], col[2], al);
                }
            }
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
                al[i] = 0.75f * env * tip;
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
        // a glowing heart where everything is dragged to
        int[] c = armColor(v[7] + (float) t * 0.01f);
        disc(q, m, d, 0.3f, 0.3f, 0f, c, new int[]{40, 10, 80}, 0.35f, 1.6f, 0, 0, 0);
        disc(q, m, d, 0.06f, 0.06f, 0f, new int[]{255, 250, 235}, c, 0.9f, 1.0f, 0, 0, 0);
    }

    private static void swirls(Matrix4f m, double t) {
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

    private static void glitch(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) { // Error: glitching blocks
        long n = (long) Math.floor(t * 6);
        Random r = new Random(n * 77L);
        for (int i = 0; i < 9; i++) {
            float a = (r.nextFloat() - 0.5f) * 0.36f, b = (r.nextFloat() - 0.5f) * 0.36f, w = 0.01f + r.nextFloat() * 0.05f, h = 0.008f + r.nextFloat() * 0.02f;
            int[] c = r.nextInt(3) == 0 ? new int[]{255, 70, 120} : new int[]{70, 255, 220};
            fill(q, m, d, ax, new float[][]{{a, b}, {a + w, b}, {a + w, b + h}, {a, b + h}}, c, 0.5f + 0.4f * r.nextFloat());
        }
    }

    private static void door(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) { // Door: a doorway, opening and closing
        float open = 0.3f + 0.7f * (0.5f + 0.5f * (float) Math.sin(t * 0.6));
        fill(q, m, d, ax, new float[][]{{-0.065f, -0.13f}, {0.065f, -0.13f}, {0.065f, 0.13f}, {-0.065f, 0.13f}}, new int[]{40, 70, 130}, 0.8f); // frame
        fill(q, m, d, ax, new float[][]{{-0.05f, -0.12f}, {0.05f, -0.12f}, {0.05f, 0.11f}, {-0.05f, 0.11f}}, new int[]{235, 250, 255}, 0.35f + 0.55f * open); // light inside
        fill(q, m, d, ax, new float[][]{{-0.05f, -0.12f}, {-0.05f + 0.1f * (1f - open) * 0.5f, -0.12f}, {-0.05f + 0.1f * (1f - open) * 0.5f, 0.11f}, {-0.05f, 0.11f}}, new int[]{20, 40, 80}, 0.9f); // the door leaf
    }

    private static void tower(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) { // White Tower: a tall white tower
        float p = 0.7f + 0.3f * (float) Math.sin(t * 0.7);
        fill(q, m, d, ax, new float[][]{{-0.035f, -0.17f}, {0.035f, -0.17f}, {0.014f, 0.13f}, {-0.014f, 0.13f}}, new int[]{235, 238, 255}, 0.85f);
        fill(q, m, d, ax, new float[][]{{-0.02f, 0.13f}, {0.02f, 0.13f}, {0.0f, 0.18f}, {0.0f, 0.18f}}, new int[]{255, 255, 255}, 0.9f);
        disc(q, m, d, 0.06f, 0.06f, 0f, new int[]{255, 255, 255}, new int[]{150, 170, 255}, 0.8f * p, 1.2f, 0, 0, 0);
    }

    private static void eclipse(BufferBuilder q, Matrix4f m, float[] d, double t) { // Darkness: a black sun with a thin corona
        float p = 0.6f + 0.4f * (float) Math.sin(t * 0.5);
        disc(q, m, d, 0.2f, 0.2f, 0f, new int[]{150, 150, 190}, new int[]{20, 20, 40}, 0.35f * p, 1.6f, 0, 0, 0);
        disc(q, m, d, 0.1f, 0.1f, 0f, BLACK, BLACK, 0.95f, 0.15f, 0, 0, 0);
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


    // ------------------------------------------------------------------ engraved gold-on-black art (Hanged Man, Visionary, Fool, Death, Wheel)

    private static final int[] GOLD = {255, 205, 90}, GOLD_LIGHT = {255, 238, 160};

    /** A ribbon along points {a, b, width}: every point gets a left and a right edge, one quad between neighbours. */
    private static void strip(BufferBuilder q, Matrix4f m, float[] d, float[] ax, float[][] p, int[] c, float al, float extra) {
        int n = p.length;
        float[] lx = new float[n], ly = new float[n], rx = new float[n], ry = new float[n];
        for (int i = 0; i < n; i++) {
            float[] a = p[Math.max(0, i - 1)], b = p[Math.min(n - 1, i + 1)];
            float dx = b[0] - a[0], dy = b[1] - a[1], l = (float) Math.sqrt(dx * dx + dy * dy);
            if (l < 1e-6f) { dx = 1; dy = 0; l = 1; }
            float h = Math.max(0.0005f, p[i][2] + extra) * 0.5f, nx = -dy / l * h, ny = dx / l * h;
            lx[i] = p[i][0] + nx; ly[i] = p[i][1] + ny; rx[i] = p[i][0] - nx; ry[i] = p[i][1] - ny;
        }
        for (int i = 0; i + 1 < n; i++) {
            vertex(q, m, d, ax, lx[i], ly[i], c[0], c[1], c[2], al);
            vertex(q, m, d, ax, lx[i + 1], ly[i + 1], c[0], c[1], c[2], al);
            vertex(q, m, d, ax, rx[i + 1], ry[i + 1], c[0], c[1], c[2], al);
            vertex(q, m, d, ax, rx[i], ry[i], c[0], c[1], c[2], al);
        }
    }

    /** A black shape with a thin gold outline, like the engraved tarot art. */
    private static void engraved(BufferBuilder q, Matrix4f m, float[] d, float[] ax, float[][] p) {
        strip(q, m, d, ax, p, GOLD, 1f, 0.02f);
        strip(q, m, d, ax, p, BLACK, 1f, 0f);
    }

    /** A black polygon (3 or 4 corners) with a gold outline: the outline is the same shape pushed outward. */
    private static void engravedPoly(BufferBuilder q, Matrix4f m, float[] d, float[] ax, float[][] pts) {
        float ca = 0, cb = 0;
        for (float[] p : pts) { ca += p[0]; cb += p[1]; }
        ca /= pts.length; cb /= pts.length;
        float[][] out = new float[pts.length][];
        for (int i = 0; i < pts.length; i++) {
            float dx = pts[i][0] - ca, dy = pts[i][1] - cb, l = Math.max(1e-6f, (float) Math.sqrt(dx * dx + dy * dy));
            out[i] = new float[]{pts[i][0] + dx / l * 0.011f, pts[i][1] + dy / l * 0.011f};
        }
        fill(q, m, d, ax, out, GOLD, 1f);
        fill(q, m, d, ax, pts, BLACK, 1f);
    }

    /** A warm glow behind a sign, so its black shapes stand out against the sky. */
    private static void backdrop(BufferBuilder q, Matrix4f m, float[] d, float rx, float ry, int[] inner, int[] outer, float alpha) {
        disc(q, m, d, rx, ry, 0f, inner, outer, alpha, 0.7f, 0, 0, 0);
    }

    private static float[] pt(float a, float b, float w) { return new float[]{a, b, w}; }

    /** The Hanged Man: a chained figure hanging upside down under a column of chains, in golden rings and beams of light. */
    private static void hanged(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) {
        float bob = 0.012f * (float) Math.sin(t * 0.6);
        backdrop(q, m, d, 0.42f, 0.38f, new int[]{255, 215, 120}, new int[]{150, 70, 20}, 0.75f); // golden sky behind the figure
        for (int i = -1; i <= 1; i += 2) backdrop(q, m, dirOffset(d, i * 0.26f, 0.1f), 0.2f, 0.12f, new int[]{90, 50, 20}, new int[]{20, 10, 5}, 0.8f); // dark clouds
        // beams of golden light falling straight down
        for (int i = 0; i < 11; i++) {
            float x = -0.3f + i * 0.06f;
            float al = 0.3f + 0.3f * (0.5f + 0.5f * (float) Math.sin(t * 1.4 + i * 1.3));
            line(q, m, d, ax, x, 0.34f, x + 0.004f, -0.32f, 0.02f, GOLD_LIGHT, al * 0.25f);
            line(q, m, d, ax, x, 0.34f, x + 0.004f, -0.32f, 0.004f, GOLD_LIGHT, al);
        }
        // golden rings (seen at an angle) around the figure
        float[][] rings = {{0.30f, 0.05f, 0.22f}, {0.20f, 0.08f, 0.02f}, {0.17f, 0.06f, -0.24f}};
        for (float[] r : rings) {
            float[][] p = new float[41][];
            for (int i = 0; i <= 40; i++) { double a = Math.PI * 2 * i / 40; p[i] = pt((float) Math.cos(a) * r[0], r[2] + (float) Math.sin(a) * r[1], 0.007f); }
            strip(q, m, d, ax, p, GOLD, 0.8f, 0f);
        }
        // the chain column above the figure
        for (int i = 0; i < 6; i++) {
            float b = 0.09f + bob + i * 0.036f, wd = (i % 2 == 0) ? 0.035f : 0.022f;
            engravedPoly(q, m, d, ax, new float[][]{{-wd, b}, {wd, b}, {wd, b + 0.026f}, {-wd, b + 0.026f}});
        }
        // arms stretched out to both sides, hung with chains
        for (int s = -1; s <= 1; s += 2) {
            float[][] arm = new float[8][];
            for (int i = 0; i < 8; i++) { float u = i / 7f; arm[i] = pt(s * (0.02f + 0.24f * u), -0.02f + bob - 0.025f * u * u, 0.045f * (1f - 0.55f * u)); }
            engraved(q, m, d, ax, arm);
            for (int i = 1; i < 6; i++) { // chain links along the arm
                float u = i / 7f;
                ring(q, m, dirOffset(d, s * (0.02f + 0.24f * u), -0.02f + bob - 0.025f * u * u - 0.03f), 0.006f, 0.014f, GOLD, 0.9f, 0, 0);
            }
            line(q, m, d, ax, s * 0.24f, -0.03f + bob, s * 0.25f, -0.15f + bob, 0.008f, GOLD, 0.9f); // a chain hanging from each hand
        }
        // torso
        engraved(q, m, d, ax, new float[][]{pt(0f, 0.095f + bob, 0.06f), pt(0f, 0.03f + bob, 0.085f), pt(0f, -0.04f + bob, 0.07f)});
        line(q, m, d, ax, -0.04f, 0.07f + bob, 0.04f, -0.03f + bob, 0.006f, GOLD, 0.9f); // chains across the chest
        line(q, m, d, ax, 0.04f, 0.07f + bob, -0.04f, -0.03f + bob, 0.006f, GOLD, 0.9f);
        // head at the bottom, with long hair falling below it
        disc(q, m, dirOffset(d, 0f, -0.075f + bob), 0.034f, 0.034f, 0f, GOLD, GOLD, 0.95f, 0.1f, 0, 0, 0);
        disc(q, m, dirOffset(d, 0f, -0.075f + bob), 0.03f, 0.03f, 0f, BLACK, BLACK, 0.97f, 0.1f, 0, 0, 0);
        for (int i = -2; i <= 2; i++) {
            float sw = 0.012f * (float) Math.sin(t * 1.1 + i);
            float[][] hair = new float[8][];
            for (int k = 0; k < 8; k++) { float u = k / 7f; hair[k] = pt(i * 0.014f * (1f + u) + sw * u, -0.1f + bob - 0.17f * u, 0.016f * (1f - u * 0.85f)); }
            engraved(q, m, d, ax, hair);
        }
    }

    /** The Visionary: a coiling dragon with horns, a spined back and a burning eye. */
    private static void dragon(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) {
        backdrop(q, m, d, 0.34f, 0.36f, new int[]{255, 200, 90}, new int[]{70, 40, 10}, 0.5f);
        final int n = 40;
        float phase = (float) (t * 0.5);
        float[][] body = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            float s = i / (float) n;
            float a = 0.17f * (float) Math.sin(Math.PI * 2 * 1.25 * s + phase) * (0.5f + 0.5f * s);
            float b = -0.26f + 0.5f * s + 0.03f * (float) Math.sin(Math.PI * 2 * 2.5 * s - phase * 1.3);
            float w = 0.012f + 0.05f * (float) Math.sin(Math.min(1f, s * 1.05f) * Math.PI * 0.5);
            body[i] = pt(a, b, w);
        }
        // spines along the back, then the body on top of them
        for (int i = 4; i < n; i += 3) {
            float[] p = body[i], pn = body[i + 1];
            float dx = pn[0] - p[0], dy = pn[1] - p[1], l = Math.max(1e-6f, (float) Math.sqrt(dx * dx + dy * dy));
            float nx = -dy / l, ny = dx / l, h = p[2] * 0.5f;
            engravedPoly(q, m, d, ax, new float[][]{{p[0] + nx * h, p[1] + ny * h}, {pn[0] + nx * h, pn[1] + ny * h}, {p[0] + nx * (h + 0.035f) + dx / l * 0.015f, p[1] + ny * (h + 0.035f) + dy / l * 0.015f}});
        }
        engraved(q, m, d, ax, body);
        for (int i = 6; i < n - 1; i += 4) { // belly scales
            float[] p = body[i], pn = body[i + 1];
            float dx = pn[0] - p[0], dy = pn[1] - p[1], l = Math.max(1e-6f, (float) Math.sqrt(dx * dx + dy * dy));
            float nx = -dy / l * p[2] * 0.5f, ny = dx / l * p[2] * 0.5f;
            line(q, m, d, ax, p[0] - nx, p[1] - ny, p[0] + nx, p[1] + ny, 0.004f, GOLD, 0.85f);
        }
        // head: a wedge pointing along the last bit of the body, two horns, an open jaw and an eye
        float[] h = body[n], hp = body[n - 3];
        float hx = h[0] - hp[0], hy = h[1] - hp[1], hl = Math.max(1e-6f, (float) Math.sqrt(hx * hx + hy * hy));
        hx /= hl; hy /= hl;
        float px = -hy, py = hx;
        float sx = h[0], sy = h[1];
        engravedPoly(q, m, d, ax, new float[][]{{sx + px * 0.035f, sy + py * 0.035f}, {sx + hx * 0.1f + px * 0.012f, sy + hy * 0.1f + py * 0.012f}, {sx + hx * 0.1f - px * 0.004f, sy + hy * 0.1f - py * 0.004f}, {sx - px * 0.03f, sy - py * 0.03f}});
        engravedPoly(q, m, d, ax, new float[][]{{sx - px * 0.02f, sy - py * 0.02f}, {sx + hx * 0.08f - px * 0.02f, sy + hy * 0.08f - py * 0.02f}, {sx + hx * 0.02f - px * 0.05f, sy + hy * 0.02f - py * 0.05f}}); // lower jaw
        for (int s = -1; s <= 1; s += 2) {
            engravedPoly(q, m, d, ax, new float[][]{{sx - hx * 0.01f + px * s * 0.02f, sy - hy * 0.01f + py * s * 0.02f}, {sx - hx * 0.09f + px * s * 0.07f, sy - hy * 0.09f + py * s * 0.07f}, {sx - hx * 0.02f + px * s * 0.03f, sy - hy * 0.02f + py * s * 0.03f}}); // horns
        }
        float glow = 0.7f + 0.3f * (float) Math.sin(t * 2.0);
        disc(q, m, dirOffset(d, sx + hx * 0.045f + px * 0.016f, sy + hy * 0.045f + py * 0.016f), 0.014f, 0.014f, 0f, new int[]{255, 250, 210}, new int[]{255, 120, 30}, glow, 0.6f, 0, 0, 0);
    }

    /** The Fool: great black tendrils with gold edges, ending in tight curls, and drifting gold smoke. */
    private static void tendrils(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) {
        backdrop(q, m, d, 0.36f, 0.36f, new int[]{255, 200, 90}, new int[]{70, 40, 10}, 0.5f);
        for (int i = -3; i <= 3; i++) { // pinstripes behind
            float x = i * 0.075f;
            line(q, m, d, ax, x, -0.3f, x, 0.3f, 0.003f, GOLD, 0.35f);
        }
        float[][] cfg = {{-0.22f, -0.3f, -0.26f, 0.12f, 1f}, {0.04f, -0.3f, 0.2f, 0.18f, -1f}, {0.24f, -0.3f, 0.3f, -0.02f, 1f}};
        for (int k = 0; k < 3; k++) {
            float[] c = cfg[k];
            float sway = 0.02f * (float) Math.sin(t * 0.6 + k * 2.0);
            float x0 = c[0], y0 = c[1], x1 = c[2] + sway, y1 = c[3];
            float cx = (x0 + x1) * 0.5f + c[4] * 0.16f, cy = (y0 + y1) * 0.5f - 0.05f;
            int body = 26, curl = 18;
            float[][] p = new float[body + curl + 1][];
            for (int i = 0; i <= body; i++) {
                float u = i / (float) body;
                float a = (1 - u) * (1 - u) * x0 + 2 * (1 - u) * u * cx + u * u * x1, b = (1 - u) * (1 - u) * y0 + 2 * (1 - u) * u * cy + u * u * y1;
                p[i] = pt(a, b, 0.055f * (1f - 0.55f * u));
            }
            // the tip curls into a spiral
            double ang0 = Math.atan2(y1 - cy, x1 - cx) - c[4] * Math.PI / 2;
            for (int i = 1; i <= curl; i++) {
                float u = i / (float) curl;
                double ang = ang0 + c[4] * u * Math.PI * 3.2;
                float r = 0.07f * (1f - 0.85f * u);
                p[body + i] = pt(x1 + (float) (Math.cos(ang) - Math.cos(ang0)) * r, y1 + (float) (Math.sin(ang) - Math.sin(ang0)) * r, 0.025f * (1f - 0.7f * u));
            }
            engraved(q, m, d, ax, p);
        }
        // gold smoke curls
        for (int k = 0; k < 3; k++) {
            float[][] p = new float[30][];
            float ph = (float) (t * 0.4 + k * 2.1);
            for (int i = 0; i < 30; i++) { float u = i / 29f; p[i] = pt(-0.28f + 0.56f * u, -0.1f + k * 0.1f + 0.05f * (float) Math.sin(u * 7 + ph), 0.005f * (float) Math.sin(u * Math.PI)); }
            strip(q, m, d, ax, p, GOLD, 0.8f, 0f);
        }
    }

    /** Death: a feathered serpent, coiled, with great wings of black feathers edged in gold. */
    private static void feathered(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) {
        float flap = 0.06f * (float) Math.sin(t * 0.9);
        backdrop(q, m, d, 0.38f, 0.34f, new int[]{255, 200, 90}, new int[]{70, 40, 10}, 0.5f);
        // the wings first, so the coiled body is in front
        for (int s = -1; s <= 1; s += 2) {
            for (int k = 0; k < 7; k++) {
                double ang = Math.toRadians(95 - k * 12) + flap * (1f + 0.15f * k);
                float len = 0.3f - 0.02f * k, w = 0.045f;
                float bx = s * 0.03f, by = 0.0f;
                float ax2 = (float) Math.cos(ang) * s, ay = (float) Math.sin(ang);
                float tx = bx + ax2 * len, ty = by + ay * len, mx = bx + ax2 * len * 0.45f, my = by + ay * len * 0.45f;
                float nx = -ay * s, ny = ax2 * s;
                engravedPoly(q, m, d, ax, new float[][]{{bx, by}, {mx + nx * w, my + ny * w}, {tx, ty}, {mx - nx * w, my - ny * w}});
                line(q, m, d, ax, bx, by, tx, ty, 0.004f, GOLD, 0.8f); // the quill
            }
        }
        // the coiled body
        final int n = 44;
        float[][] body = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n;
            double ang = Math.PI * 2 * 1.35 * u + t * 0.25;
            float r = 0.17f - 0.05f * u;
            body[i] = pt((float) Math.cos(ang) * r, -0.06f + (float) Math.sin(ang) * r * 0.75f + 0.1f * u - 0.1f, 0.012f + 0.05f * (float) Math.sin(Math.min(1f, u * 1.05f) * Math.PI * 0.5));
        }
        engraved(q, m, d, ax, body);
        for (int i = 4; i < n; i += 3) { // scale marks
            float[] p = body[i], pn = body[i + 1];
            float dx = pn[0] - p[0], dy = pn[1] - p[1], l = Math.max(1e-6f, (float) Math.sqrt(dx * dx + dy * dy));
            line(q, m, d, ax, p[0] - dy / l * p[2] * 0.4f, p[1] + dx / l * p[2] * 0.4f, p[0] + dy / l * p[2] * 0.4f, p[1] - dx / l * p[2] * 0.4f, 0.004f, GOLD, 0.8f);
        }
        float[] h = body[n], hp = body[n - 3];
        float hx = h[0] - hp[0], hy = h[1] - hp[1], hl = Math.max(1e-6f, (float) Math.sqrt(hx * hx + hy * hy));
        hx /= hl; hy /= hl;
        float px = -hy, py = hx;
        engravedPoly(q, m, d, ax, new float[][]{{h[0] + px * 0.032f, h[1] + py * 0.032f}, {h[0] + hx * 0.09f + px * 0.01f, h[1] + hy * 0.09f + py * 0.01f}, {h[0] + hx * 0.09f - px * 0.005f, h[1] + hy * 0.09f - py * 0.005f}, {h[0] - px * 0.03f, h[1] - py * 0.03f}});
        disc(q, m, dirOffset(d, h[0] + hx * 0.04f + px * 0.014f, h[1] + hy * 0.04f + py * 0.014f), 0.011f, 0.011f, 0f, new int[]{235, 245, 255}, new int[]{130, 190, 255}, 0.95f, 0.6f, 0, 0, 0);
    }

    /** Wheel of Fortune: a snake eating its own tail in a turning ring, with four serpents for spokes. */
    private static void serpentWheel(BufferBuilder q, Matrix4f m, float[] d, float[] ax, double t) {
        float rot = (float) (t * 0.2);
        float R0 = 0.17f;
        backdrop(q, m, d, 0.3f, 0.3f, new int[]{255, 200, 90}, new int[]{70, 40, 10}, 0.5f);
        // four wavy serpents as spokes, turning the other way
        for (int k = 0; k < 4; k++) {
            double base = -rot * 1.5 + k * Math.PI / 2;
            float[][] p = new float[22][];
            for (int i = 0; i < 22; i++) {
                float u = i / 21f, r = 0.02f + u * (R0 - 0.02f), off = 0.025f * (float) Math.sin(u * 9 + t * 1.5 + k);
                double ang = base + off / Math.max(0.04f, r);
                p[i] = pt((float) Math.cos(ang) * r, (float) Math.sin(ang) * r, 0.022f * (1f - 0.75f * u) + 0.006f);
            }
            engraved(q, m, d, ax, p);
        }
        // the snake ring: thin tail, thick body near the head, a small gap between head and tail
        final int n = 64;
        float gap = 0.35f;
        float[][] ringPts = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n;
            double ang = rot + gap * 0.5 + (Math.PI * 2 - gap) * u;
            float w = 0.012f + 0.05f * (float) Math.pow(u, 1.3);
            ringPts[i] = pt((float) Math.cos(ang) * R0, (float) Math.sin(ang) * R0, w);
        }
        engraved(q, m, d, ax, ringPts);
        for (int i = 3; i < n; i += 3) { // scales
            float[] p = ringPts[i];
            double ang = rot + gap * 0.5 + (Math.PI * 2 - gap) * (i / (float) n);
            float nx = (float) Math.cos(ang), ny = (float) Math.sin(ang);
            line(q, m, d, ax, p[0] - nx * p[2] * 0.4f, p[1] - ny * p[2] * 0.4f, p[0] + nx * p[2] * 0.4f, p[1] + ny * p[2] * 0.4f, 0.004f, GOLD, 0.85f);
        }
        // head: a wedge ahead of the thick end, facing the tail, and an eye
        double ha = rot + gap * 0.5 + (Math.PI * 2 - gap);
        float hx = (float) Math.cos(ha) * R0, hy = (float) Math.sin(ha) * R0;
        float tx = (float) -Math.sin(ha), ty = (float) Math.cos(ha); // along the ring, forward
        float nx = (float) Math.cos(ha), ny = (float) Math.sin(ha);
        engravedPoly(q, m, d, ax, new float[][]{{hx - nx * 0.04f, hy - ny * 0.04f}, {hx + tx * 0.07f - nx * 0.012f, hy + ty * 0.07f - ny * 0.012f}, {hx + tx * 0.07f + nx * 0.012f, hy + ty * 0.07f + ny * 0.012f}, {hx + nx * 0.04f, hy + ny * 0.04f}});
        disc(q, m, dirOffset(d, hx + tx * 0.025f + nx * 0.015f, hy + ty * 0.025f + ny * 0.015f), 0.01f, 0.01f, 0f, new int[]{255, 250, 200}, new int[]{255, 160, 40}, 0.95f, 0.6f, 0, 0, 0);
    }

    private static void sign(BufferBuilder q, Matrix4f m, String id, double t) {
        float[] d = anchor(id);
        float[] ax = basis(d);
        switch (id) {
            case "sailor" -> lightning(q, m, d, ax, t);
            case "seer" -> tendrils(q, m, d, ax, t);
            case "marauder" -> glitch(q, m, d, ax, t);
            case "apprentice" -> door(q, m, d, ax, t);
            case "spectator" -> dragon(q, m, d, ax, t);
            case "reader" -> tower(q, m, d, ax, t);
            case "secrets_supplicant" -> hanged(q, m, d, ax, t);
            case "sleepless" -> eclipse(q, m, d, t);
            case "corpse_collector" -> feathered(q, m, d, ax, t);
            case "warrior" -> giant(q, m, d, ax, t);
            case "assassin" -> lily(q, m, d, t);
            case "hunter" -> flames(q, m, d, t);
            case "mystery_pryer" -> hexagram(q, m, d, ax, t);
            case "savant" -> cogs(q, m, d, t);
            case "monster" -> serpentWheel(q, m, d, ax, t);
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
