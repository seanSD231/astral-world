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
            if (lotmFall) return;   // with LOTM's own meteor the fall belongs to it; this class only adds the impact
            double pr = t / fall, e = pr * pr, H = 85 * (1 - e);
            double hx = x + DX * H, hy = y + H + 0.5, hz = z + DZ * H;
            float in = clamp01(t / 3.0);
            double nl = Math.sqrt(DX * DX + 1 + DZ * DZ), tx = DX / nl, ty = 1 / nl, tz = DZ / nl, L = 18 + 40 * pr;
            if (!add) {
                // a long smoke trail and the rock itself: a tumbling cluster of dark chunks
                p.col(0x2A2522);
                tube(p, hx, hy, hz, r * 1.5, 0.45f * in, hx + tx * L * 2.4, hy + ty * L * 2.4, hz + tz * L * 2.4, 0.3, 0f, 10);
                p.col(0x3A3029);
                sphere(p, hx, hy, hz, r * 0.95, in, 6, 9);
                for (int i = 0; i < 10; i++) {
                    double ang = t * 0.16 * (i % 2 == 0 ? 1 : -1) + i * 0.63, el = hash(i * 5.1) * 2 - 1, rr = r * (0.7 + 0.35 * hash(i * 2.7));
                    p.col(i % 2 == 0 ? 0x4A3C32 : 0x2B2420);
                    box(p, hx + Math.cos(ang) * rr * Math.sqrt(1 - el * el), hy + el * rr, hz + Math.sin(ang) * rr * Math.sqrt(1 - el * el), r * (0.4 + 0.4 * hash(i * 1.9)), in);
                }
                return;
            }
            p.col(0xFF3A10);
            tube(p, hx, hy, hz, r * 1.5, 0.5f * in, hx + tx * L * 1.4, hy + ty * L * 1.4, hz + tz * L * 1.4, 0.05, 0f, 10);
            p.col(0xFF8A1E);
            tube(p, hx, hy, hz, r * 1.1, 0.7f * in, hx + tx * L, hy + ty * L, hz + tz * L, 0.04, 0f, 10);
            p.col(0xFFE08A);
            tube(p, hx, hy, hz, r * 0.6, 0.85f * in, hx + tx * L * 0.7, hy + ty * L * 0.7, hz + tz * L * 0.7, 0.03, 0f, 8);
            p.col(0xFF9A2E);   // lava glowing through the cracks of the rock
            for (int i = 0; i < 9; i++) {
                double az = hash(i * 3.3) * Math.PI * 2 + t * 0.1, el = hash(i * 8.8) * 2 - 1, k = Math.sqrt(1 - el * el);
                box(p, hx + Math.cos(az) * k * r, hy + el * r, hz + Math.sin(az) * k * r, r * 0.26, 0.95f * in);
            }
            p.col(0xFFB347);
            sphere(p, hx, hy, hz, r * 1.4, 0.3f * in, 6, 9);
            p.col(0xFFD27A);   // sparks thrown off the tail
            for (int i = 0; i < 16; i++) {
                double along = (hash(i * 4.4 + Math.floor(t * 0.5)) * 0.9) * L, ox = (hash(i * 6.1) - 0.5) * r * 2.4, oz = (hash(i * 9.7) - 0.5) * r * 2.4, oy = (hash(i * 2.2) - 0.5) * r * 2.4;
                double sx = hx + tx * along + ox, sy = hy + ty * along + oy, sz = hz + tz * along + oz;
                tube(p, sx, sy, sz, 0.09, 0.9f * in, sx + tx * 4, sy + ty * 4, sz + tz * 4, 0.02, 0f, 4);
            }
            return;
        }
        double span = Math.max(8, life - fall), u = Math.min(1, (t - fall) / span), fade = (1 - u) * (1 - u);
        if (!add) {
            p.col(0x1A1210);   // scorched ground
            flat(p, x, y + 0.12, z, 0, rad * (0.9 + 0.3 * u), (float) (0.55 * (1 - u * u)));
            for (int i = 0; i < 16; i++) {   // rock thrown out of the crater, falling back down
                double az = hash(i * 7.7) * Math.PI * 2, sp = rad * (0.7 + 1.3 * hash(i * 3.1)), hh = rad * (0.8 + 1.4 * hash(i * 5.9));
                p.col(i % 2 == 0 ? 0x4A3C32 : 0x2B2420);
                box(p, x + Math.cos(az) * sp * Math.min(1, u * 1.6), y + 0.3 + hh * 4 * u * (1 - u), z + Math.sin(az) * sp * Math.min(1, u * 1.6), 0.5 + 0.5 * hash(i * 1.3), clamp01(1.2 * (1 - u)));
            }
            return;
        }
        p.col(0xFFE6B0);
        sphere(p, x, y + 0.3, z, rad * (0.35 + 1.1 * Math.sqrt(u)), (float) (0.75 * fade), 6, 12);
        p.col(col);
        flat(p, x, y + 0.15, z, Math.max(0, rad * (0.3 + 1.7 * u) - rad * 0.3), rad * (0.3 + 1.7 * u), clamp01(0.9 * (1 - u)));
        wall(p, x, y, z, rad * (0.5 + 1.9 * u), 2.5 + rad * 0.35 * (1 - u), clamp01(0.85 * (1 - u)), 0f);
        wall(p, x, y, z, rad * (0.2 + 3.2 * u), 1.5 + rad * 0.2 * (1 - u), clamp01(0.5 * (1 - u)), 0f);   // a second, faster shockwave
        double ch = rad * 3 * (0.4 + u);
        tube(p, x, y, z, rad * 0.35 * (1 - u) + 0.1, clamp01(0.55 * (1 - u)), x, y + ch, z, rad * 0.12 * (1 - u) + 0.05, 0f, 10);
        p.col(0xFF5A14);   // the crater glows red-hot
        flat(p, x, y + 0.2, z, 0, rad * 0.8, clamp01(0.7 * (1 - u)));
    }

    /** A leaf or petal lying at an angle: a diamond with its tip along `ang`, tilted up by `tilt`. */
    private static void leaf(Pen p, double x, double y, double z, double ang, double size, double tilt, float a) {
        double ca = Math.cos(ang), sa = Math.sin(ang), w = size * 0.4;
        double tx = x + ca * size, tz = z + sa * size, ty = y + Math.sin(tilt) * size;
        double mx = x + ca * size * 0.45, mz = z + sa * size * 0.45, my = y + Math.sin(tilt) * size * 0.45;
        double px = -sa * w, pz = ca * w;
        p.quad(x, y, z, a, mx + px, my, mz + pz, a, tx, ty, tz, a * 0.4f, mx - px, my, mz - pz, a);
    }

    /**
     * The slam: a bud falls onto the circle and bursts open into a flower, throwing petals and leaves.
     * Before the change (The World Tree) it is a blossom of pink and white petals with gold and green leaves.
     * After it (the Mother Tree of Desire) it is a dark flower of crimson and violet petals, thorns and rotten leaves with red veins in the ground.
     */
    private static void slam(Pen p, boolean add, double t, double x, double y, double z, double drop, double rad, double life, int col, boolean fleshy) {
        int petalA = fleshy ? 0xC0103A : 0xFFB7D5, petalB = fleshy ? 0x7A1FA0 : 0xFFFFFF, core = fleshy ? 0xFF4060 : 0xFFD060;
        int leafC = fleshy ? 0x8A3A20 : 0x7CFC5A, litter = fleshy ? 0x2A0A18 : 0x2E6B1E;
        double hold = 8;
        if (t < drop + hold) {
            if (!add) return;
            double pr = Math.min(1, t / drop), tip = y + 48 * (1 - pr * pr);
            float al = clamp01(t < drop ? 1 : 1 - (t - drop) / hold);
            for (int k = 0; k < 8; k++) {   // the bud: eight petals folded over its tip
                double ang = Math.PI * 2 * k / 8;
                p.col(k % 2 == 0 ? petalA : petalB);
                tube(p, x, tip, z, 0.0, 0.0f, x + Math.cos(ang) * rad * 0.2, tip + 10, z + Math.sin(ang) * rad * 0.2, rad * 0.07, 0.9f * al, 4);
                tube(p, x + Math.cos(ang) * rad * 0.2, tip + 10, z + Math.sin(ang) * rad * 0.2, rad * 0.07, 0.9f * al, x, tip + 20, z, 0.0, 0.2f * al, 4);
            }
            p.col(core);
            tube(p, x, tip, z, 0.0, 0.9f * al, x, tip + 8, z, rad * 0.05, 0.6f * al, 6);
            p.col(leafC);
            for (int j = 0; j < 14; j++) {   // leaves spiralling in the air above it
                double ang = j * 0.9 + t * 0.25, hh = tip + 6 + j * 2.6, rr = rad * (0.18 + 0.02 * j);
                leaf(p, x + Math.cos(ang) * rr, hh, z + Math.sin(ang) * rr, ang + 1.2, 1.4, 0.5, 0.8f * al);
            }
        }
        if (t < drop) return;
        double span = Math.max(10, life - drop), u = Math.min(1, (t - drop) / span);
        if (add) {
            double open = Math.min(1, u * 2.5), L = rad * (0.3 + 1.0 * u);
            float al = clamp01(0.85 * (1 - u));
            for (int k = 0; k < 14; k++) {   // the bloom: fourteen petals, alternating colours
                double ang = Math.PI * 2 * k / 14 + 0.2 * u, lift = 2.5 * Math.sin(Math.PI * Math.min(1, u * 1.6)) * (0.5 + 0.5 * hash(k + x));
                p.col(k % 2 == 0 ? petalA : petalB);
                double mx = x + Math.cos(ang) * L * 0.5, mz = z + Math.sin(ang) * L * 0.5;
                tube(p, x, y + 0.4, z, 1.1 * (1 - open * 0.5), al, mx, y + 0.4 + lift, mz, rad * 0.06, al, 4);
                tube(p, mx, y + 0.4 + lift, mz, rad * 0.06, al, x + Math.cos(ang) * L, y + 0.3, z + Math.sin(ang) * L, 0.0, al * 0.3f, 4);
            }
            p.col(core);
            flat(p, x, y + 0.12, z, L - 0.5, L + 0.5, clamp01(0.7 * (1 - u)));
            if (open < 1) sphere(p, x, y + 0.6, z, rad * 0.55 * open, clamp01(0.55 * (1 - open)), 5, 9);
            if (fleshy) {   // thorns along the rim
                p.col(0x400018);
                for (int k = 0; k < 18; k++) {
                    double ang = Math.PI * 2 * k / 18 + hash(k * 2.3 + z), th = (1.0 + 2.2 * hash(k * 5.1)) * Math.sin(Math.PI * Math.min(1, u * 1.4));
                    tube(p, x + Math.cos(ang) * L, y, z + Math.sin(ang) * L, 0.3, al, x + Math.cos(ang) * (L + 0.6), y + th, z + Math.sin(ang) * (L + 0.6), 0.0, 0f, 4);
                }
            }
            p.col(leafC);
            for (int k = 0; k < 30; k++) {   // leaves thrown up and out, spinning as they fall
                double ang = Math.PI * 2 * hash(k * 3.7 + x), sp = rad * (0.5 + 1.1 * hash(k * 1.3 + z)) * u;
                double hh = (6 + 10 * hash(k * 9.1)) * (4 * u * (1 - u)) + 0.3;
                leaf(p, x + Math.cos(ang) * sp, y + hh, z + Math.sin(ang) * sp, ang + u * 9 * (hash(k) + 0.5), 1.1 + hash(k * 4.4), 0.7, clamp01(0.9 * (1 - u)));
            }
        } else {
            p.col(litter);
            float al = clamp01(0.9 * (1 - u * u));
            double reach = rad * (0.35 + 0.85 * Math.min(1, u * 4));
            for (int k = 0; k < 34; k++) {   // petals and leaves left lying on the ground
                double ang = Math.PI * 2 * hash(k * 2.9 + x), dist = reach * Math.sqrt(hash(k * 6.7 + z));
                leaf(p, x + Math.cos(ang) * dist, y + 0.08, z + Math.sin(ang) * dist, hash(k * 11.3) * 6.28, 0.9 + hash(k * 1.9), 0.0, al);
            }
            if (fleshy) {   // red veins in the ground, running out from the circle's heart
                p.col(0x500010);
                for (int k = 0; k < 11; k++) {
                    double ang = Math.PI * 2 * k / 11 + hash(k * 1.7 + x) * 0.5, px = x, pz = z, step = reach / 5;
                    for (int s2 = 0; s2 < 5; s2++) {
                        double na = ang + (hash(k * 13 + s2 * 5 + z) - 0.5) * 0.7, nx = px + Math.cos(na) * step, nz = pz + Math.sin(na) * step;
                        double w = 0.4 * (1 - s2 / 6.0), wx = -Math.sin(na) * w, wz = Math.cos(na) * w;
                        p.quad(px - wx, y + 0.1, pz - wz, al, px + wx, y + 0.1, pz + wz, al, nx + wx * 0.7, y + 0.1, nz + wz * 0.7, al, nx - wx * 0.7, y + 0.1, nz - wz * 0.7, al);
                        px = nx; pz = nz; ang = na;
                    }
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

    /** A flat disc that fades smoothly from its middle to nothing at the rim, so many of them overlap into mist with no visible edge. */
    private static void softDisc(Pen p, double cx, double y, double cz, double r, float a) {
        if (r < 0.5 || a <= 0.003) return;
        final int rings = 4, seg = 20;
        for (int k = 0; k < rings; k++) {
            double r0 = r * k / rings, r1 = r * (k + 1) / rings;
            float a0 = (float) (a * Math.pow(1 - (double) k / rings, 1.6)), a1 = (float) (a * Math.pow(1 - (double) (k + 1) / rings, 1.6));
            for (int i = 0; i < seg; i++) {
                double t0 = Math.PI * 2 * i / seg, t1 = Math.PI * 2 * (i + 1) / seg;
                p.quad(cx + Math.cos(t0) * r0, y, cz + Math.sin(t0) * r0, a0, cx + Math.cos(t0) * r1, y, cz + Math.sin(t0) * r1, a1,
                        cx + Math.cos(t1) * r1, y, cz + Math.sin(t1) * r1, a1, cx + Math.cos(t1) * r0, y, cz + Math.sin(t1) * r0, a0);
            }
        }
    }

    /** Fog: a huge bank of layered, drifting mist. No circle, no edge: it just thins out. */
    private static void cloud(Pen p, boolean add, double t, double x0, double y, double z0, double vx, double vz, double rad, double life, int col) {
        double env = Math.min(1, Math.min(t / 25.0, (life - t) / 25.0));
        if (env <= 0) return;
        float e = clamp01(env);
        double x = x0 + vx * t, z = z0 + vz * t;
        p.col(col);
        if (!add) {
            for (int i = 0; i < 6; i++) {   // the body: stacked layers, each turning a little differently
                double ang = t * 0.006 * (i % 2 == 0 ? 1 : -1) + i * 1.1, off = rad * 0.1;
                softDisc(p, x + Math.cos(ang) * off, y + 0.15 + i * 0.75, z + Math.sin(ang) * off, rad * (1.1 - 0.06 * i), 0.17f * e);
            }
            for (int j = 0; j < 11; j++) {   // thick patches drifting round inside it
                double ang = j * 0.571 + t * 0.005 * (j % 2 == 0 ? 1 : -1), rr = rad * (0.15 + 0.65 * hash(j * 3.3));
                softDisc(p, x + Math.cos(ang) * rr, y + 0.3 + hash(j * 7.7) * 3.4, z + Math.sin(ang) * rr, rad * (0.3 + 0.25 * hash(j * 1.7)), 0.14f * e);
            }
        } else {
            p.col(0xD9B8F0);
            for (int j = 0; j < 8; j++) {   // paler wisps on top, rolling the other way
                double ang = j * 0.785 - t * 0.007, rr = rad * (0.2 + 0.6 * hash(j * 5.3));
                softDisc(p, x + Math.cos(ang) * rr, y + 0.8 + hash(j * 2.9) * 3.0, z + Math.sin(ang) * rr, rad * (0.28 + 0.2 * hash(j * 8.1)), 0.07f * e);
            }
            p.col(col);
            for (int j = 0; j < 16; j++) {   // tall thin streamers rising out of the mist
                double ang = hash(j * 4.1) * Math.PI * 2 + t * 0.004, rr = rad * (0.1 + 0.8 * hash(j * 6.7)), h = 4 + 4 * hash(j * 9.3), w = 2.5 + 2 * hash(j * 1.1);
                double px = x + Math.cos(ang) * rr, pz = z + Math.sin(ang) * rr, dx = -Math.sin(ang) * w, dz = Math.cos(ang) * w;
                p.quad(px - dx, y, pz - dz, 0f, px + dx, y, pz + dz, 0f, px + dx * 0.4, y + h, pz + dz * 0.4, 0f, px - dx * 0.4, y + h, pz - dz * 0.4, 0f);
                p.quad(px - dx, y, pz - dz, 0.1f * e, px + dx, y, pz + dz, 0.1f * e, px + dx * 0.4, y + h, pz + dz * 0.4, 0f, px - dx * 0.4, y + h, pz - dz * 0.4, 0f);
            }
        }
    }

    /** A fruit hanging in the air over the spot: an apple with a stem and a leaf, and a beam of light so it can be found from far away. */
    private static void fruit(Pen p, boolean add, double t, double x, double y, double z, boolean rotten, boolean red, double life) {
        double env = Math.min(1, Math.min(t / 4.0, (life - t) / 8.0));
        if (env <= 0) return;
        float e = clamp01(env);
        double fallT = 12, cy;
        if (t < fallT) { double u = t / fallT; cy = y + 1.5 + 30 * (1 - u * u); } else cy = y + 1.5 + Math.sin((t - fallT) * 0.12) * 0.18;
        double spin = t * 0.09;
        if (!add) {
            p.col(rotten ? 0x4F3A30 : 0xD92B2B);
            sphere(p, x, cy, z, 0.52, e, 7, 10);
            for (int s = -1; s <= 1; s += 2) sphere(p, x + Math.cos(spin) * 0.2 * s, cy + 0.04, z + Math.sin(spin) * 0.2 * s, 0.46, e, 6, 9);   // the two lobes of an apple
            if (rotten) {
                p.col(0x6D8A3A);   // mould
                for (int i = 0; i < 5; i++) {
                    double az = hash(i * 3.7) * Math.PI * 2 + spin, el = hash(i * 6.1) * 1.4 - 0.7;
                    sphere(p, x + Math.cos(az) * 0.58 * Math.cos(el), cy + Math.sin(el) * 0.55, z + Math.sin(az) * 0.58 * Math.cos(el), 0.14, e, 4, 6);
                }
            }
            p.col(0x5A3A1E);
            tube(p, x, cy + 0.45, z, 0.06, e, x + 0.08, cy + 0.9, z, 0.04, e, 5);
            p.col(rotten ? 0x7A7A32 : 0x3FAE3A);
            leaf(p, x + 0.06, cy + 0.82, z, spin, 0.55, 0.45, e);
            leaf(p, x + 0.06, cy + 0.82, z, spin + Math.PI, 0.4, 0.45, e);
            return;
        }
        // the glow, the shine and the beam
        p.col(rotten ? 0x9A5AC8 : 0xFFD54A);
        sphere(p, x, cy, z, 1.0, 0.16f * e, 6, 9);
        if (!rotten) { p.col(0xFFF3C0); sphere(p, x - 0.2, cy + 0.25, z - 0.15, 0.12, 0.8f * e, 4, 6); }
        double flick = 0.8 + 0.2 * Math.sin(t * 0.35);
        p.col(red ? 0xFF2A2A : 0xFFD54A);
        tube(p, x, y, z, 0.38, (float) (0.5 * flick) * e, x, y + 70, z, 0.2, 0f, 8);
        p.col(red ? 0xFF9A8A : 0xFFF3C0);
        tube(p, x, y, z, 0.12, (float) (0.8 * flick) * e, x, y + 70, z, 0.07, 0f, 5);
        p.col(red ? 0xFF2A2A : 0xFFD54A);
        flat(p, x, y + 0.12, z, 0, 0.9, 0.25f * e);   // a small pool of light where the beam stands
    }

    /** The Purge: the tree gathers a ball of light above itself (rays pour in), then it bursts across the whole arena. */
    private static void purge(Pen p, boolean add, double t, double x, double y, double z, double windup, double maxR, double life, int col) {
        if (!add) return;
        double cy = y + 42;
        if (t < windup) {
            double f = t / windup, pulse = 1 + 0.12 * Math.sin(t * 0.9);
            float in = clamp01(t / 10.0);
            p.col(col);
            sphere(p, x, cy, z, (3 + 13 * f * f) * pulse, (0.25f + 0.35f * (float) f) * in, 8, 14);
            sphere(p, x, cy, z, (6 + 22 * f * f) * pulse, 0.1f * in, 8, 14);
            p.col(0xFFFFFF);
            sphere(p, x, cy, z, (1.5 + 7 * f * f) * pulse, (0.5f + 0.4f * (float) f) * in, 8, 12);
            p.col(col);
            for (int j = 0; j < 28; j++) {   // light streaming in from all round
                double az = hash(j * 3.9) * Math.PI * 2, el = (hash(j * 7.1) - 0.3) * 1.2, d = 70 * (1 - ((f * 2.2 + hash(j * 1.7)) % 1.0)) + 6;
                double ux = Math.cos(az) * Math.cos(el), uy = Math.sin(el), uz = Math.sin(az) * Math.cos(el);
                tube(p, x + ux * d, cy + uy * d, z + uz * d, 0.28, 0.7f * in, x + ux * d * 0.8, cy + uy * d * 0.8, z + uz * d * 0.8, 0.05, 0f, 4);
            }
            flat(p, x, y + 0.15, z, 0, 60 * (1 - f) + 10, 0.1f * (float) f);   // the ground trembles in towards the trunk
            return;
        }
        double span = Math.max(10, life - windup), u = Math.min(1, (t - windup) / span), inv = 1 - u;
        p.col(0xFFFFFF);
        sphere(p, x, cy, z, 16 + 40 * Math.sqrt(u), clamp01(0.9 * inv * inv * inv), 10, 16);
        p.col(col);
        sphere(p, x, cy, z, 18 + maxR * 0.8 * Math.sqrt(u), clamp01(0.45 * inv * inv), 12, 20);
        double r = maxR * (1 - inv * inv);
        wall(p, x, y, z, r, 8 + 40 * inv, clamp01(0.9 * inv), 0f);
        wall(p, x, y, z, r * 0.75, 4 + 20 * inv, clamp01(0.6 * inv), 0f);
        flat(p, x, y + 0.2, z, Math.max(0, r - 14), r, clamp01(0.85 * inv));
        tube(p, x, y, z, 14 * inv + 1, clamp01(0.7 * inv), x, y + 160, z, 4 * inv + 0.5, 0f, 12);
    }

    /** The ground ring: burning roots surge up out of the earth along the ring as it rolls out, and keep burning behind it. */
    private static void rootRing(Pen p, boolean add, double t, double x, double y, double z, double speed, double r0, double life, int col) {
        double r = r0 + speed * t, env = Math.min(1, Math.min(t / 4.0, (life - t) / 10.0));
        if (env <= 0 || r < 1) return;
        float e = clamp01(env);
        int n = (int) Math.min(110, r * 1.1 + 16);
        if (!add) {
            p.col(0x1A0F08);
            flat(p, x, y + 0.1, z, Math.max(0, r - 5), r + 2.5, 0.45f * e);
        }
        for (int k = 0; k < 3; k++) {            // the front and two trails left burning behind it
            double rr = r - k * 4.5;
            if (rr < 1) continue;
            double hk = 1 - 0.28 * k;
            for (int i = 0; i < n; i += (k == 0 ? 1 : 2)) {
                double ang = Math.PI * 2 * i / n + hash(i * 1.7) * 0.05, ux = Math.cos(ang), uz = Math.sin(ang);
                double surge = 0.55 + 0.45 * Math.sin(t * 0.45 + i * 0.9 + k), h = (4.5 + 5.5 * hash(i * 3.1 + k * 11)) * hk * surge;
                double lean = 0.5 + 1.4 * hash(i * 5.3), side = (hash(i * 7.9) - 0.5) * 1.6;
                double bx = x + ux * rr, bz = z + uz * rr;
                double mx = bx + ux * lean, my = y + h * 0.55, mz = bz + uz * lean;
                double tx = mx + ux * 1.6 - uz * side, ty = y + h, tz = mz + uz * 1.6 + ux * side;
                if (!add) {
                    p.col(i % 2 == 0 ? 0x4A2A14 : 0x2E1A0C);
                    tube(p, bx, y, bz, 0.6, e, mx, my, mz, 0.4, e, 5);
                    tube(p, mx, my, mz, 0.4, e, tx, ty, tz, 0.08, e, 5);
                } else {
                    float fl = (float) (0.7 + 0.3 * Math.sin(t * 0.9 + i));
                    p.col(0xFF7A1A);
                    tube(p, tx, ty, tz, 0.55, 0.85f * e * fl, tx - uz * 0.2, ty + 2.2 + hash(i * 2.3 + Math.floor(t * 0.5)) * 1.6, tz + ux * 0.2, 0.02, 0f, 4);
                    p.col(0xFFD060);
                    tube(p, tx, ty, tz, 0.28, 0.9f * e * fl, tx, ty + 1.5, tz, 0.02, 0f, 4);
                    p.col(0xFF5A10);
                    tube(p, mx, my, mz, 0.5, 0.5f * e * fl, mx, my + 1.3, mz, 0.05, 0f, 4);
                }
            }
        }
        if (add) {
            p.col(0xFF5A10);
            flat(p, x, y + 0.15, z, Math.max(0, r - 1.8), r + 1.8, 0.55f * e);
            p.col(0xFFD060);
            flat(p, x, y + 0.18, z, Math.max(0, r - 0.5), r + 0.5, 0.7f * e);
        }
    }

    /** A ring in the air: a wave of lightning and swirling leaves, 14 blocks tall so it is plain how far you must move to be out of it. */
    private static void skyWave(Pen p, boolean add, double t, double x, double y, double z, double speed, double r0, double life) {
        double r = r0 + speed * t, env = Math.min(1, Math.min(t / 4.0, (life - t) / 10.0)), H = 7;
        if (env <= 0 || r < 1) return;
        float e = clamp01(env);
        int m = (int) Math.max(28, Math.min(130, r * 0.55));
        if (!add) {
            int cols[] = {0x58C24A, 0xE0B84A, 0xFF9AC8, 0x7FD66A};
            int leaves = (int) Math.max(40, Math.min(160, r * 0.9));
            for (int i = 0; i < leaves; i++) {
                double a = Math.PI * 2 * i / leaves + t * 0.03 + hash(i * 1.3), rr = r + Math.sin(t * 0.2 + i) * 2.2;
                double ly = y + (hash(i * 5.7) * 2 - 1) * H + Math.sin(t * 0.3 + i) * 0.8;
                p.col(cols[i % cols.length]);
                leaf(p, x + Math.cos(a) * rr, ly, z + Math.sin(a) * rr, t * 0.2 + i, 0.95, 0.4, e);
            }
            return;
        }
        p.col(0x7FA8FF);
        wall(p, x, y, z, r, H, 0.4f * e, 0f);
        wall(p, x, y, z, r, -H, 0.4f * e, 0f);
        long flick = (long) Math.floor(t * 0.7);
        double[] px = new double[m], pz = new double[m];
        for (int i = 0; i < m; i++) {
            double a = Math.PI * 2 * i / m;
            px[i] = x + Math.cos(a) * r; pz[i] = z + Math.sin(a) * r;
            double lx = px[i], lz = pz[i], ly = y - H;
            p.col(0xD8EEFF);
            for (int j = 1; j <= 4; j++) {   // a jagged bolt from the bottom of the ring to the top
                double j2 = j / 4.0, nx = px[i] + (hash(i * 13.0 + j * 7 + flick) - 0.5) * 2.2 * (j < 4 ? 1 : 0), nz = pz[i] + (hash(i * 17.0 + j * 5 + flick) - 0.5) * 2.2 * (j < 4 ? 1 : 0), ny = y - H + 2 * H * j2;
                tube(p, lx, ly, lz, 0.17, 0.95f * e, nx, ny, nz, 0.17, 0.95f * e, 4);
                lx = nx; lz = nz; ly = ny;
            }
        }
        p.col(0xFFFFFF);
        for (int i = 0; i < m; i++) {   // arcs jumping from bolt to bolt
            int j = (i + 1) % m;
            double yy = y + (hash(i * 3.3 + flick) - 0.5) * H * 1.4;
            tube(p, px[i], yy, pz[i], 0.1, 0.8f * e, px[j], yy + (hash(i * 9.1 + flick) - 0.5) * 2.5, pz[j], 0.1, 0.8f * e, 4);
        }
    }

    /** A flower: stem, leaves and two rings of petals around a heart, with a beam of light standing on it. Pink and white before the tree changes, crimson and violet after. */
    private static void flower(Pen p, boolean add, double t, double x, double y, double z, boolean fleshy, double life) {
        double env = Math.min(1, Math.min(t / 8.0, (life - t) / 8.0));
        if (env <= 0) return;
        float e = clamp01(env);
        double open = ease(t / 14.0), spin = t * 0.025, hy = y + 2.8;
        int petal = fleshy ? 0xB0102C : 0xFF9AC8, inner = fleshy ? 0x6A1E8C : 0xFFF0F8, heart = fleshy ? 0x1A0008 : 0xFFD54A;
        if (!add) {
            p.col(0x3E7A32);
            tube(p, x, y, z, 0.14, e, x + 0.1, hy, z, 0.1, e, 5);
            leaf(p, x, y + 0.9, z, spin, 1.1, 0.5, e);
            leaf(p, x, y + 1.5, z, spin + 2.6, 0.9, 0.5, e);
            for (int i = 0; i < 9; i++) { p.col(petal); leaf(p, x + 0.1, hy, z, spin + Math.PI * 2 * i / 9, 2.3 * open, 0.3 + 0.1 * Math.sin(t * 0.1 + i), e); }
            for (int i = 0; i < 7; i++) { p.col(inner); leaf(p, x + 0.1, hy + 0.1, z, spin * 1.4 + Math.PI * 2 * i / 7 + 0.3, 1.5 * open, 0.85, e); }
            p.col(heart);
            sphere(p, x + 0.1, hy + 0.1, z, 0.4 * open, e, 5, 8);
            return;
        }
        double flick = 0.8 + 0.2 * Math.sin(t * 0.3);
        p.col(petal);
        sphere(p, x, hy, z, 2.6, 0.12f * e, 6, 9);
        tube(p, x, y, z, 0.45, (float) (0.5 * flick) * e, x, y + 90, z, 0.25, 0f, 8);
        p.col(fleshy ? 0xFF9AA8 : 0xFFFFFF);
        tube(p, x, y, z, 0.14, (float) (0.8 * flick) * e, x, y + 90, z, 0.08, 0f, 5);
        p.col(petal);
        flat(p, x, y + 0.12, z, 0, 1.5, 0.25f * e);
    }

    /** Lightning falling all over the arena at once: `count` tall bolts at places taken from the seed. */
    private static void skyBolts(Pen p, boolean add, double t, double x, double y, double z, double seed, double radius, double count, double life) {
        if (!add) return;
        double u = Math.min(1, t / Math.max(4, life)), fl = (1 - u) * (1 - u);
        float a = clamp01(fl);
        if (a <= 0.01f) return;
        long flick = (long) Math.floor(t * 0.5);
        for (int i = 0; i < (int) count; i++) {
            double ang = hash(seed + i * 1.7) * Math.PI * 2, rr = radius * Math.sqrt(hash(seed + i * 3.1));
            double bx = x + Math.cos(ang) * rr, bz = z + Math.sin(ang) * rr, lx = bx, lz = bz, ly = y + 120;
            p.col(0xC8E0FF);
            for (int j = 1; j <= 5; j++) {
                double nx = bx + (hash(i * 13.0 + j * 7 + flick) - 0.5) * 7 * (j < 5 ? 1 : 0), nz = bz + (hash(i * 17.0 + j * 5 + flick) - 0.5) * 7 * (j < 5 ? 1 : 0), ny = y + 120 - 120 * j / 5.0;
                tube(p, lx, ly, lz, 0.35, a, nx, ny, nz, 0.35, a, 4);
                lx = nx; lz = nz; ly = ny;
            }
            p.col(0xFFFFFF);
            flat(p, bx, y + 0.15, bz, 0, 3.5, 0.6f * a);
        }
    }

    private static final java.util.Map<Long, java.util.List<Bolts.Seg>> WEBS = new java.util.HashMap<>();

    private static java.util.List<Bolts.Seg> webFor(long seed, double cx, double cz, double reach, int arms) {
        if (WEBS.size() > 40) WEBS.clear();
        return WEBS.computeIfAbsent(seed * 100 + arms, k -> Bolts.web(k / 100, cx, cz, reach, (int) (k % 100)));
    }

    /** A flat ribbon on the ground from (x0, z0) to (x1, z1), w wide. */
    private static void strip(Pen p, double x0, double z0, double x1, double z1, double y, double w, float a) {
        double dx = x1 - x0, dz = z1 - z0, l = Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-3) return;
        double nx = -dz / l * w / 2, nz = dx / l * w / 2;
        p.quad(x0 - nx, y, z0 - nz, a, x0 + nx, y, z0 + nz, a, x1 + nx, y, z1 + nz, a, x1 - nx, y, z1 - nz, a);
    }

    /** The Meteor Storm: red lines show where the lightning will branch out of each impact; it strikes right after the meteors land. */
    private static void storm(Pen p, boolean add, double t, double x, double y, double z, double seed, double reach, double warn, double life, int arms) {
        if (!add) return;
        java.util.List<Bolts.Seg> web = webFor((long) seed, x, z, reach, arms);
        if (t < warn) {
            float in = clamp01(t / 8.0);
            double pulse = 0.65 + 0.35 * Math.sin(t * 0.55);
            p.col(0xFF1A1A);
            for (Bolts.Seg s : web) {
                strip(p, s.x0(), s.z0(), s.x1(), s.z1(), y + 0.12, 3.4, 0.16f * in);
                strip(p, s.x0(), s.z0(), s.x1(), s.z1(), y + 0.14, s.depth() == 0 ? 0.9 : 0.6, (float) (0.85 * pulse) * in);
            }
            return;
        }
        double u = Math.min(1, (t - warn) / Math.max(4, life - warn)), flash = (1 - u) * (1 - u);
        float fl = clamp01(flash);
        p.col(0xB8D8FF);
        for (Bolts.Seg s : web) {
            double h = s.depth() == 0 ? 26 : 18;
            strip(p, s.x0(), s.z0(), s.x1(), s.z1(), y + 0.15, 3.0, 0.5f * fl);
            p.quad(s.x0(), y, s.z0(), 0.55f * fl, s.x1(), y, s.z1(), 0.55f * fl, s.x1(), y + h, s.z1(), 0f, s.x0(), y + h, s.z0(), 0f);
        }
        p.col(0xFFFFFF);
        for (Bolts.Seg s : web) strip(p, s.x0(), s.z0(), s.x1(), s.z1(), y + 0.18, 0.8, fl);
        p.col(0xE8F0FF);
        tube(p, x, y, z, 2.2 * (1 - u) + 0.3, fl, x, y + 130, z, 0.6, 0f, 8);
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

    private static void ult(Pen p, boolean add, double t, double x, double y, double z, double height, double fall, double life, int col, double sc) {
        if (!add) return;
        if (t < fall) {
            double f = t / fall, hy = y + height - height * (0.6 * f + 0.4 * f * f), R = (3 + f * 6) * sc;
            float in = clamp01(t / 20.0);
            p.col(col);
            tube(p, x, hy, z, R * 1.5, 0.3f * in, x, hy + 110 * sc, z, 0.2, 0f, 12);
            sphere(p, x, hy, z, R * 2.6, 0.14f * in, 7, 12);
            sphere(p, x, hy, z, R * 1.6, 0.3f * in, 7, 12);
            p.col(0xFFB347);
            tube(p, x, hy, z, R * 0.85, 0.55f * in, x, hy + 70 * sc, z, 0.2, 0f, 10);
            p.col(0xFFF1CC);
            sphere(p, x, hy, z, R, 0.95f * in, 8, 14);
            return;
        }
        double span = Math.max(20, life - fall), u = Math.min(1, (t - fall) / span), inv = 1 - u, k = Math.sqrt(sc);
        p.col(0xFFE6B0);
        sphere(p, x, y + 4, z, (14 + 90 * Math.sqrt(u)) * k, clamp01(0.6 * inv * inv), 8, 14);
        p.col(col);
        wall(p, x, y, z, 110 * k * (1 - (1 - u) * (1 - u)), (4 + 24 * inv) * k, clamp01(0.9 * inv), 0f);
        flat(p, x, y + 0.2, z, Math.max(0, 110 * k * u - 6), 110 * k * u + 6, clamp01(0.7 * inv));
        tube(p, x, y, z, (26 * inv + 1) * k, clamp01(0.6 * inv), x, y + 140 * k * (0.5 + u), z, (6 * inv + 0.5) * k, 0f, 12);
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
                case 1 -> slam(pen, add, t, x, y, z, a, b, life, col, c > 0.5);
                case 2 -> wave(pen, add, t, x, y, z, a, b, life, col);
                case 3 -> cloud(pen, add, t, x, y, z, a, b, c, life, col);
                case 10 -> fruit(pen, add, t, x, y, z, a > 0.5, b > 0.5, life);
                case 13 -> storm(pen, add, t, x, y, z, a, b, c, life, col);
                case 14 -> purge(pen, add, t, x, y, z, a, b, life, col);
                case 15 -> rootRing(pen, add, t, x, y, z, a, b, life, col);
                case 16 -> skyWave(pen, add, t, x, y, z, a, b, life);
                case 17 -> flower(pen, add, t, x, y, z, a > 0.5, life);
                case 19 -> skyBolts(pen, add, t, x, y, z, a, b, c, life);
                case 4 -> blast(pen, add, t, x, y, z, a, life, col);
                case 5 -> vortex(pen, add, t, x, y, z, c, life, col);
                case 6 -> ult(pen, add, t, x, y, z, a, b, life, col, c > 0 ? c : 1);
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
