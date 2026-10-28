package com.astralspace;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Client only: the visual side of the Mother Tree's attacks. The server sends ONE message per attack
 * ({@link Net.FxPayload}) and everything below is animated here, from the moment it arrives.
 *
 * kinds: 0 meteor (falls with a fiery trail, then explodes)   1 slam (a thorn spike crashes down, the ground cracks)
 *        2 shock wave (a glowing wall rolling out)            3 pollen cloud (drifting, swirling)
 *        4 blast (one big expanding wave and flash)           5 curse vortex (dark sparks spiralling up a player)
 *        6 the 15% meteor (follows the server's slow fall, then a huge blast)
 */
public final class AttackFx {
    private static VertexBuffer vb;

    private AttackFx() {}

    public static void init() { NeoForge.EVENT_BUS.addListener(AttackFx::onRender); }

    // ------------------------------------------------------------------ geometry (all coordinates are made camera-relative)

    private static final class Pen {
        BufferBuilder bb;
        Vec3 cam;
        int quads;
        int r, g, b;
        void col(int c) { r = c >> 16 & 255; g = c >> 8 & 255; b = c & 255; }
        void v(double x, double y, double z, float a) {
            bb.addVertex((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(r, g, b, (int) (Math.max(0f, Math.min(1f, a)) * 255));
        }
        void quad(double x1, double y1, double z1, float a1, double x2, double y2, double z2, float a2,
                  double x3, double y3, double z3, float a3, double x4, double y4, double z4, float a4) {
            v(x1, y1, z1, a1); v(x2, y2, z2, a2); v(x3, y3, z3, a3); v(x4, y4, z4, a4);
            quads++;
        }
    }

    /** A vertical wall ring: alpha aBot at the bottom, aTop at the top. */
    private static void wall(Pen p, double cx, double y, double cz, double r, double h, float aBot, float aTop) {
        if (r < 0.2 || aBot <= 0.003 && aTop <= 0.003) return;
        int n = (int) Math.max(24, Math.min(160, r * 5));
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            double x0 = cx + Math.cos(a0) * r, z0 = cz + Math.sin(a0) * r, x1 = cx + Math.cos(a1) * r, z1 = cz + Math.sin(a1) * r;
            p.quad(x0, y, z0, aBot, x1, y, z1, aBot, x1, y + h, z1, aTop, x0, y + h, z0, aTop);
        }
    }

    /** A flat ring on the ground (rin 0 = a disc). */
    private static void flat(Pen p, double cx, double y, double cz, double rin, double rout, float a) {
        if (rout < 0.1 || a <= 0.003) return;
        int n = (int) Math.max(24, Math.min(140, rout * 5));
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            p.quad(cx + c0 * rin, y, cz + s0 * rin, a, cx + c0 * rout, y, cz + s0 * rout, a, cx + c1 * rout, y, cz + s1 * rout, a, cx + c1 * rin, y, cz + s1 * rin, a);
        }
    }

    private static void sphere(Pen p, double cx, double cy, double cz, double r, float a, int lat, int lon) {
        if (r < 0.05 || a <= 0.003) return;
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat - Math.PI / 2, t1 = Math.PI * (i + 1) / lat - Math.PI / 2;
            double y0 = Math.sin(t0) * r, y1 = Math.sin(t1) * r, r0 = Math.cos(t0) * r, r1 = Math.cos(t1) * r;
            for (int j = 0; j < lon; j++) {
                double a0 = Math.PI * 2 * j / lon, a1 = Math.PI * 2 * (j + 1) / lon;
                p.quad(cx + Math.cos(a0) * r0, cy + y0, cz + Math.sin(a0) * r0, a, cx + Math.cos(a1) * r0, cy + y0, cz + Math.sin(a1) * r0, a,
                        cx + Math.cos(a1) * r1, cy + y1, cz + Math.sin(a1) * r1, a, cx + Math.cos(a0) * r1, cy + y1, cz + Math.sin(a0) * r1, a);
            }
        }
    }

    private static void box(Pen p, double x, double y, double z, double s, float a) {
        double h = s / 2;
        p.quad(x - h, y - h, z - h, a, x + h, y - h, z - h, a, x + h, y + h, z - h, a, x - h, y + h, z - h, a);
        p.quad(x - h, y - h, z + h, a, x + h, y - h, z + h, a, x + h, y + h, z + h, a, x - h, y + h, z + h, a);
        p.quad(x - h, y - h, z - h, a, x - h, y - h, z + h, a, x - h, y + h, z + h, a, x - h, y + h, z - h, a);
        p.quad(x + h, y - h, z - h, a, x + h, y - h, z + h, a, x + h, y + h, z + h, a, x + h, y + h, z - h, a);
        p.quad(x - h, y + h, z - h, a, x + h, y + h, z - h, a, x + h, y + h, z + h, a, x - h, y + h, z + h, a);
    }

    /** A tapering tube from p0 (radius r0, alpha a0) to p1 (radius r1, alpha a1). r1 = 0 makes a spike. */
    private static void tube(Pen p, double x0, double y0, double z0, double r0, float a0, double x1, double y1, double z1, double r1, float a1, int sides) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-3 || a0 <= 0.003 && a1 <= 0.003) return;
        dx /= len; dy /= len; dz /= len;
        double ux, uy, uz;               // u = d x up (or d x east when d is vertical)
        if (Math.abs(dy) > 0.98) { ux = 1; uy = 0; uz = 0; }
        else { ux = dz; uy = 0; uz = -dx; double l = Math.sqrt(ux * ux + uz * uz); ux /= l; uz /= l; }
        double vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
        for (int i = 0; i < sides; i++) {
            double a = Math.PI * 2 * i / sides, b = Math.PI * 2 * (i + 1) / sides;
            double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
            p.quad(x0 + (ux * ca + vx * sa) * r0, y0 + (uy * ca + vy * sa) * r0, z0 + (uz * ca + vz * sa) * r0, a0,
                    x0 + (ux * cb + vx * sb) * r0, y0 + (uy * cb + vy * sb) * r0, z0 + (uz * cb + vz * sb) * r0, a0,
                    x1 + (ux * cb + vx * sb) * r1, y1 + (uy * cb + vy * sb) * r1, z1 + (uz * cb + vz * sb) * r1, a1,
                    x1 + (ux * ca + vx * sa) * r1, y1 + (uy * ca + vy * sa) * r1, z1 + (uz * ca + vz * sa) * r1, a1);
        }
    }

    private static double hash(double s) { double v = Math.sin(s * 127.1 + 311.7) * 43758.5453; return v - Math.floor(v); }

    private static float clamp01(double v) { return (float) Math.max(0, Math.min(1, v)); }

    // ------------------------------------------------------------------ one effect at a time. "add" is the glowing layer, the other layer is for dark parts.

    private static final double DX = 0.32, DZ = 0.18;   // a meteor comes in slightly sideways

    private static void meteor(Pen p, boolean add, double t, double x, double y, double z, double fall, double rad, double life, int col, boolean lotmFall) {
        double r = Math.max(1.0, rad * 0.3);
        if (t < fall) {
            if (!add || lotmFall) return;   // with LOTM's own meteor the fall belongs to it; this class only adds the impact
            double pr = t / fall, e = pr * pr, H = 85 * (1 - e);
            double hx = x + DX * H, hy = y + H + 0.5, hz = z + DZ * H;
            float in = clamp01(t / 3.0);
            double nl = Math.sqrt(DX * DX + 1 + DZ * DZ), tx = DX / nl, ty = 1 / nl, tz = DZ / nl, L = 14 + 34 * pr;
            p.col(col);
            tube(p, hx, hy, hz, r * 1.35, 0.38f * in, hx + tx * L * 1.1, hy + ty * L * 1.1, hz + tz * L * 1.1, 0.05, 0f, 10);
            sphere(p, hx, hy, hz, r * 1.5, 0.28f * in, 6, 10);
            p.col(0xFFB347);
            tube(p, hx, hy, hz, r * 0.8, 0.65f * in, hx + tx * L * 0.8, hy + ty * L * 0.8, hz + tz * L * 0.8, 0.04, 0f, 8);
            p.col(0xFFF1CC);
            sphere(p, hx, hy, hz, r * 0.8, 0.95f * in, 6, 10);
            return;
        }
        double span = Math.max(8, life - fall), u = Math.min(1, (t - fall) / span), fade = (1 - u) * (1 - u);
        if (!add) return;
        p.col(0xFFE6B0);
        sphere(p, x, y + 0.3, z, rad * (0.35 + 1.1 * Math.sqrt(u)), (float) (0.75 * fade), 6, 12);
        p.col(col);
        flat(p, x, y + 0.15, z, Math.max(0, rad * (0.3 + 1.7 * u) - rad * 0.3), rad * (0.3 + 1.7 * u), clamp01(0.9 * (1 - u)));
        wall(p, x, y, z, rad * (0.5 + 1.9 * u), 2.5 + rad * 0.35 * (1 - u), clamp01(0.85 * (1 - u)), 0f);
        double ch = rad * 3 * (0.4 + u);
        tube(p, x, y, z, rad * 0.35 * (1 - u) + 0.1, clamp01(0.55 * (1 - u)), x, y + ch, z, rad * 0.12 * (1 - u) + 0.05, 0f, 10);
    }

    private static void slam(Pen p, boolean add, double t, double x, double y, double z, double drop, double rad, double life, int col) {
        double hold = 8;
        if (t < drop + hold) {
            if (!add) return;
            double pr = Math.min(1, t / drop), tip = y + 48 * (1 - pr * pr);
            float al = clamp01(t < drop ? 1 : 1 - (t - drop) / hold);
            p.col(col);
            tube(p, x, tip, z, 0.0, 0.0f, x, tip + 36, z, rad * 0.4, 0.0f, 10);
            tube(p, x, tip, z, 0.0, 0.8f * al, x, tip + 22, z, rad * 0.3, 0.55f * al, 10);
            p.col(0xFFF0C8);
            tube(p, x, tip, z, 0.0, 0.9f * al, x, tip + 10, z, rad * 0.12, 0.7f * al, 8);
        }
        if (t < drop) return;
        double span = Math.max(10, life - drop), u = Math.min(1, (t - drop) / span);
        if (add) {
            p.col(col);
            wall(p, x, y, z, rad * (0.35 + 1.0 * u), 1.6 + 1.2 * (1 - u), clamp01(0.8 * (1 - u)), 0f);
            flat(p, x, y + 0.12, z, rad * (0.35 + 1.0 * u) - 0.6, rad * (0.35 + 1.0 * u) + 0.6, clamp01(0.7 * (1 - u)));
            double rise = Math.min(1, u * 3);   // shards of earth thrown up at the edge of the circle
            if (u < 0.6) for (int k = 0; k < 14; k++) {
                double ang = Math.PI * 2 * k / 14 + hash(k + x), rr = rad * (0.8 + 0.25 * hash(k * 7 + z)), hh = (2.2 + 2.6 * hash(k * 3.1)) * Math.sin(Math.PI * Math.min(1, u / 0.6));
                tube(p, x + Math.cos(ang) * rr, y, z + Math.sin(ang) * rr, 0.35, clamp01(0.7 * (1 - u)), x + Math.cos(ang) * rr, y + hh, z + Math.sin(ang) * rr, 0.0, 0f, 5);
            }
            if (rise < 1) { p.col(0xFFE0A0); sphere(p, x, y + 0.5, z, rad * 0.6 * rise, clamp01(0.6 * (1 - rise)), 5, 9); }
        } else {
            p.col(0x1A0505);
            float al = clamp01(0.9 * (1 - u * u));
            double reach = rad * (0.35 + 0.85 * Math.min(1, u * 4));
            for (int k = 0; k < 11; k++) {   // jagged cracks, each in five bends
                double ang = Math.PI * 2 * k / 11 + hash(k * 1.7 + x) * 0.5, px = x, pz = z, step = reach / 5;
                for (int s = 0; s < 5; s++) {
                    double na = ang + (hash(k * 13 + s * 5 + z) - 0.5) * 0.7, nx = px + Math.cos(na) * step, nz = pz + Math.sin(na) * step;
                    double w = 0.5 * (1 - s / 6.0), wx = -Math.sin(na) * w, wz = Math.cos(na) * w;
                    p.quad(px - wx, y + 0.1, pz - wz, al, px + wx, y + 0.1, pz + wz, al, nx + wx * 0.7, y + 0.1, nz + wz * 0.7, al, nx - wx * 0.7, y + 0.1, nz - wz * 0.7, al);
                    px = nx; pz = nz; ang = na;
                }
            }
        }
    }

    private static void wave(Pen p, boolean add, double t, double x, double y, double z, double r0, double speed, double life, int col) {
        if (!add) return;
        double r = r0 + speed * t, env = Math.min(1, Math.min(t / 3.0, (life - t) / 8.0));
        if (env <= 0) return;
        float e = clamp01(env);
        p.col(col);
        wall(p, x, y, z, r, 2.0, 0.75f * e, 0f);
        flat(p, x, y + 0.1, z, Math.max(0, r - 0.7), r + 0.7, 0.55f * e);
        p.col(0xFFFFFF);
        wall(p, x, y, z, Math.max(0.2, r - 0.5), 0.9, 0.55f * e, 0f);
    }

    private static void cloud(Pen p, boolean add, double t, double x0, double y, double z0, double vx, double vz, double rad, double life, int col) {
        double env = Math.min(1, Math.min(t / 12.0, (life - t) / 12.0));
        if (env <= 0) return;
        float e = clamp01(env);
        double x = x0 + vx * t, z = z0 + vz * t;
        p.col(col);
        if (!add) {
            double[] hs = {0.35, 1.4, 2.6, 3.8}, rs = {1.0, 0.92, 0.75, 0.5};
            for (int i = 0; i < 4; i++) flat(p, x, y + hs[i], z, 0, rad * rs[i], 0.17f * e);
        } else {
            for (int i = 0; i < 8; i++) {
                double ang = t * 0.035 * (i % 2 == 0 ? 1 : -1) + i * 0.785, rr = rad * (0.25 + 0.55 * hash(i * 3.3));
                sphere(p, x + Math.cos(ang) * rr, y + 0.8 + 2.6 * hash(i * 9.1) + Math.sin(t * 0.07 + i) * 0.3, z + Math.sin(ang) * rr, rad * 0.2, 0.2f * e, 4, 7);
            }
            wall(p, x, y, z, rad, 0.6, 0.35f * e, 0f);
        }
    }

    private static void blast(Pen p, boolean add, double t, double x, double y, double z, double maxR, double life, int col) {
        if (!add) return;
        double pr = Math.min(1, t / Math.max(1, life)), r = maxR * (1 - (1 - pr) * (1 - pr)), inv = 1 - pr;
        p.col(col);
        wall(p, x, y, z, r, 3 + 7 * inv, clamp01(0.9 * inv), 0f);
        flat(p, x, y + 0.15, z, Math.max(0, r - 2), r + 2, clamp01(0.7 * inv));
        p.col(0xFFFFFF);
        sphere(p, x, y + 2, z, 5 + maxR * 0.2 * pr, clamp01(0.7 * inv * inv * inv), 6, 10);
    }

    private static void vortex(Pen p, boolean add, double t, double x, double y, double z, double rad, double life, int col) {
        if (!add) return;
        double env = Math.min(1, Math.min(t / 6.0, (life - t) / 10.0));
        if (env <= 0) return;
        float e = clamp01(env);
        p.col(col);
        for (int s = 0; s < 3; s++) {
            for (int i = 0; i < 9; i++) {
                double h = i * 0.32 + ((t * 0.045 + s / 3.0) % 1.0) * 0.3, ang = t * 0.28 + s * 2.094 + h * 2.4, rr = rad * (1 - h / 3.6);
                box(p, x + Math.cos(ang) * rr, y + h, z + Math.sin(ang) * rr, 0.16 + 0.1 * (1 - i / 9.0), 0.75f * e);
            }
        }
        flat(p, x, y + 0.1, z, rad * 0.7, rad * 1.1, 0.35f * e);
    }

    private static void ult(Pen p, boolean add, double t, double x, double y, double z, double height, double fall, double life, int col) {
        if (!add) return;
        if (t < fall) {
            double f = t / fall, hy = y + height - height * (0.6 * f + 0.4 * f * f), R = 3 + f * 6;
            float in = clamp01(t / 20.0);
            p.col(col);
            tube(p, x, hy, z, R * 1.5, 0.3f * in, x, hy + 110, z, 0.2, 0f, 12);
            sphere(p, x, hy, z, R * 2.6, 0.14f * in, 7, 12);
            sphere(p, x, hy, z, R * 1.6, 0.3f * in, 7, 12);
            p.col(0xFFB347);
            tube(p, x, hy, z, R * 0.85, 0.55f * in, x, hy + 70, z, 0.2, 0f, 10);
            p.col(0xFFF1CC);
            sphere(p, x, hy, z, R, 0.95f * in, 8, 14);
            return;
        }
        double span = Math.max(20, life - fall), u = Math.min(1, (t - fall) / span), inv = 1 - u;
        p.col(0xFFE6B0);
        sphere(p, x, y + 4, z, 14 + 90 * Math.sqrt(u), clamp01(0.6 * inv * inv), 8, 14);
        p.col(col);
        wall(p, x, y, z, 110 * (1 - (1 - u) * (1 - u)), 4 + 24 * inv, clamp01(0.9 * inv), 0f);
        flat(p, x, y + 0.2, z, Math.max(0, 110 * u - 6), 110 * u + 6, clamp01(0.7 * inv));
        tube(p, x, y, z, 26 * inv + 1, clamp01(0.6 * inv), x, y + 140 * (0.5 + u), z, 6 * inv + 0.5, 0f, 12);
    }

    // ------------------------------------------------------------------ rendering

    private static void pass(Pen pen, boolean add, long now, List<double[]> list) {
        for (double[] f : list) {
            int kind = (int) f[0];
            double x = f[1], y = f[2], z = f[3], a = f[4], b = f[5], c = f[6];
            int col = (int) f[7];
            double t = (now - f[8]) / 50.0, life = f[9];
            switch (kind) {
                case 0 -> meteor(pen, add, t, x, y, z, a, b, life, col, c > 0.5);
                case 1 -> slam(pen, add, t, x, y, z, a, b, life, col);
                case 2 -> wave(pen, add, t, x, y, z, a, b, life, col);
                case 3 -> cloud(pen, add, t, x, y, z, a, b, c, life, col);
                case 4 -> blast(pen, add, t, x, y, z, a, life, col);
                case 5 -> vortex(pen, add, t, x, y, z, c, life, col);
                case 6 -> ult(pen, add, t, x, y, z, a, b, life, col);
                default -> { }
            }
        }
    }

    private static void draw(RenderLevelStageEvent event, List<double[]> list, boolean add, long now) {
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Pen pen = new Pen();
        pen.bb = bb;
        pen.cam = event.getCamera().getPosition();
        pass(pen, add, now, list);
        if (pen.quads == 0) { try { bb.build(); } catch (Throwable ignored) { } return; }
        MeshData md = bb.build();
        if (md == null) return;
        if (vb == null) vb = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        vb.bind();
        vb.upload(md);
        if (add) RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        vb.drawWithShader(new Matrix4f(event.getModelViewMatrix()), event.getProjectionMatrix(), GameRenderer.getPositionColorShader());
        VertexBuffer.unbind();
    }

    private static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (Net.ClientState.fx.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = System.currentTimeMillis();
        List<double[]> list;
        synchronized (Net.ClientState.fx) {
            Net.ClientState.fx.removeIf(f -> (now - f[8]) / 50.0 > f[9]);
            list = new ArrayList<>(Net.ClientState.fx);
        }
        if (list.isEmpty()) return;
        float fs = RenderSystem.getShaderFogStart(), fe = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(1.0E7f);
        RenderSystem.setShaderFogEnd(2.0E7f);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        draw(event, list, false, now);
        draw(event, list, true, now);
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderFogStart(fs);
        RenderSystem.setShaderFogEnd(fe);
    }
}
