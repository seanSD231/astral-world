package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

/**
 * Client only: little hand-built models for the worn Uniquenesses and their abilities (shadow tentacles, the feathered serpent, the wheel, the doll,
 * the ball of storms). Everything is drawn with one depth-tested translucent type, full bright, so none of it shows through blocks. The pose is in
 * blocks; every size and position handed to the helpers is in pixels (1/16 block).
 */
final class UniqGfx {
    private UniqGfx() {}

    static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "textures/entity/white.png");
    static int light = 0xF000F0;

    static VertexConsumer vc(MultiBufferSource b) { return b.getBuffer(RenderType.entityTranslucent(WHITE)); }
    static void tr(PoseStack p, float x, float y, float z) { p.translate(x / 16f, y / 16f, z / 16f); }
    static float rnd(int seed) { int x = seed * 374761393 + 668265263; x = (x ^ (x >>> 13)) * 1274126177; return ((x ^ (x >>> 16)) & 0xFFFF) / 65535f; }

    // ---- primitives ----
    static void box(PoseStack pose, MultiBufferSource buf, float sx, float sy, float sz, int argb) {
        VertexConsumer vc = vc(buf);
        PoseStack.Pose m = pose.last();
        float x = sx / 32, y = sy / 32, z = sz / 32;
        int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        quad(vc, m, a, r, g, b, -x, -y, -z, x, -y, -z, x, y, -z, -x, y, -z, 0, 0, -1);
        quad(vc, m, a, r, g, b, x, -y, z, -x, -y, z, -x, y, z, x, y, z, 0, 0, 1);
        quad(vc, m, a, r, g, b, -x, -y, z, -x, -y, -z, -x, y, -z, -x, y, z, -1, 0, 0);
        quad(vc, m, a, r, g, b, x, -y, -z, x, -y, z, x, y, z, x, y, -z, 1, 0, 0);
        quad(vc, m, a, r, g, b, -x, -y, z, x, -y, z, x, -y, -z, -x, -y, -z, 0, -1, 0);
        quad(vc, m, a, r, g, b, -x, y, -z, x, y, -z, x, y, z, -x, y, z, 0, 1, 0);
    }

    static void quad(VertexConsumer vc, PoseStack.Pose m, int a, int r, int g, int b,
                     float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny, float nz) {
        v(vc, m, a, r, g, b, x0, y0, z0, 0, 0, nx, ny, nz); v(vc, m, a, r, g, b, x1, y1, z1, 1, 0, nx, ny, nz);
        v(vc, m, a, r, g, b, x2, y2, z2, 1, 1, nx, ny, nz); v(vc, m, a, r, g, b, x3, y3, z3, 0, 1, nx, ny, nz);
    }

    static void v(VertexConsumer vc, PoseStack.Pose m, int a, int r, int g, int b, float x, float y, float z, float u, float w, float nx, float ny, float nz) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, w).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(m, nx, ny, nz);
    }

    /** A thick segment between two points. */
    static void seg(PoseStack p, MultiBufferSource b, float ax, float ay, float az, float bx, float by, float bz, float w, int argb) {
        float dx = bx - ax, dy = by - ay, dz = bz - az, l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-4f) return;
        p.pushPose(); tr(p, (ax + bx) / 2, (ay + by) / 2, (az + bz) / 2);
        p.mulPose(new Quaternionf().rotationTo(0, 1, 0, dx / l, dy / l, dz / l));
        box(p, b, w, l, w, argb); p.popPose();
    }

    /** A flat blade of a feather: from a point along a direction. */
    static void feather(PoseStack p, MultiBufferSource b, float ox, float oy, float oz, float dx, float dy, float dz, float len, float wid, int argb) {
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz); if (l < 1e-4f) return;
        dx /= l; dy /= l; dz /= l;
        p.pushPose(); tr(p, ox + dx * len / 2, oy + dy * len / 2, oz + dz * len / 2);
        p.mulPose(new Quaternionf().rotationTo(0, 1, 0, dx, dy, dz));
        box(p, b, .5f, len, wid, argb);
        box(p, b, .35f, len * .9f, .5f, lighten(argb));
        p.popPose();
    }

    static int lighten(int argb) {
        int a = argb >>> 24, r = Math.min(255, (argb >> 16 & 255) + 60), g = Math.min(255, (argb >> 8 & 255) + 60), b = Math.min(255, (argb & 255) + 60);
        return a << 24 | r << 16 | g << 8 | b;
    }

    static int rgba(int rgb, float a) { return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (rgb & 0xFFFFFF); }

    private static final java.util.Map<Integer, float[][]> UNITS = new java.util.HashMap<>();
    static void sphere(PoseStack pose, MultiBufferSource buf, float r, int lat, int lon, int argb) {
        float[][] UNIT = UNITS.computeIfAbsent(lat * 1000 + lon, key -> {
            float[][] u = new float[(lat + 1) * (lon + 1)][3];
            for (int i = 0; i <= lat; i++) for (int j = 0; j <= lon; j++) {
                double a = Math.PI * i / lat - Math.PI / 2, c = 2 * Math.PI * j / lon;
                u[i * (lon + 1) + j] = new float[]{(float) (Math.cos(a) * Math.cos(c)), (float) Math.sin(a), (float) (Math.cos(a) * Math.sin(c))};
            }
            return u;
        });
        VertexConsumer vc = vc(buf); PoseStack.Pose m = pose.last();
        int a = argb >>> 24, rr = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255; float k = r / 16f;
        for (int i = 0; i < lat; i++) for (int j = 0; j < lon; j++) {
            float[] p0 = UNIT[i * (lon + 1) + j], p1 = UNIT[i * (lon + 1) + j + 1], p2 = UNIT[(i + 1) * (lon + 1) + j + 1], p3 = UNIT[(i + 1) * (lon + 1) + j];
            v(vc, m, a, rr, g, b, p0[0] * k, p0[1] * k, p0[2] * k, 0, 0, p0[0], p0[1], p0[2]); v(vc, m, a, rr, g, b, p1[0] * k, p1[1] * k, p1[2] * k, 1, 0, p1[0], p1[1], p1[2]);
            v(vc, m, a, rr, g, b, p2[0] * k, p2[1] * k, p2[2] * k, 1, 1, p2[0], p2[1], p2[2]); v(vc, m, a, rr, g, b, p3[0] * k, p3[1] * k, p3[2] * k, 0, 1, p3[0], p3[1], p3[2]);
        }
    }

    /** A flat triangle (as a degenerate quad), both faces, in the XY plane at depth z. */
    static void tri(PoseStack pose, MultiBufferSource buf, float x0, float y0, float x1, float y1, float x2, float y2, float z, int argb) {
        VertexConsumer vc = vc(buf); PoseStack.Pose m = pose.last();
        int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255; z /= 16f;
        quad(vc, m, a, r, g, b, x0 / 16, y0 / 16, z, x1 / 16, y1 / 16, z, x2 / 16, y2 / 16, z, x2 / 16, y2 / 16, z, 0, 0, 1);
    }

    /** A crescent moon in the XY plane, open toward -x, R px from its centre to the tip of the curve, W px thick at the belly. Both faces. */
    static void crescent(PoseStack p, MultiBufferSource b, float R, float W, int argb) {
        VertexConsumer vc = vc(b); PoseStack.Pose m = p.last();
        int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, bl = argb & 255;
        int n = 16; float sh = -R * .75f;
        for (int i = 0; i < n; i++) {
            float u0 = -1f + 2f * i / n, u1 = -1f + 2f * (i + 1) / n, t0 = u0 * 1.25f, t1 = u1 * 1.25f;
            float ox0 = (R * (float) Math.cos(t0) + sh) / 16, oy0 = R * (float) Math.sin(t0) / 16, ox1 = (R * (float) Math.cos(t1) + sh) / 16, oy1 = R * (float) Math.sin(t1) / 16;
            float ix0 = ox0 - W * (1 - u0 * u0) / 16, ix1 = ox1 - W * (1 - u1 * u1) / 16;
            quad(vc, m, a, r, g, bl, ix0, oy0, 0, ox0, oy0, 0, ox1, oy1, 0, ix1, oy1, 0, 0, 0, 1);
            quad(vc, m, a, r, g, bl, ix1, oy1, 0, ox1, oy1, 0, ox0, oy0, 0, ix0, oy0, 0, 0, 0, -1);
        }
    }

    // ---- models ----

    /** A tentacle of shadow standing up from the origin: a thick root, bulges that travel up it, a row of dim violet suckers and a tip that curls over. */
    static void tentacle(PoseStack p, MultiBufferSource b, float t, int seed, float height, float base, int argb, int tip) {
        int n = 18; float sg = height / n, x = 0, y = 0, z = 0, ax = 0, az = 0;
        float ox = (float) Math.cos(seed * 2.4f), oz = (float) Math.sin(seed * 2.4f);
        int dark = rgba(0x05020A, (argb >>> 24) / 255f), glow = rgba(0x8A4AD8, .9f * (argb >>> 24) / 255f), aura = rgba(0x2A1048, .16f * (argb >>> 24) / 255f);
        for (int i = 0; i < n; i++) {
            float u = i / (float) n;
            ax += (float) Math.sin(t * .11f + seed * 1.7f + i * .45f) * .11f + u * u * u * .2f;
            az += (float) Math.cos(t * .09f + seed * 2.3f + i * .4f) * .11f;
            float dx = (float) (Math.sin(ax) * Math.cos(az)), dy = (float) (Math.cos(ax) * Math.cos(az)), dz = (float) Math.sin(az);
            float nx = x + dx * sg, ny = y + dy * sg, nz = z + dz * sg;
            float w = Math.max(.45f, base * (float) Math.pow(1f - u, .8f) * (1f + .14f * (float) Math.sin(i * 1.6f - t * .25f)));
            seg(p, b, x, y, z, nx, ny, nz, w, i >= n - 3 ? tip : i % 2 == 0 ? argb : dark);
            if (i % 3 == 0 && i < n - 3) seg(p, b, x, y, z, nx, ny, nz, w * 1.7f, aura);
            if (i % 2 == 1 && u < .8f) { p.pushPose(); tr(p, (x + nx) / 2 + ox * w * .5f, (y + ny) / 2, (z + nz) / 2 + oz * w * .5f); box(p, b, w * .42f, w * .42f, w * .42f, glow); p.popPose(); }
            x = nx; y = ny; z = nz;
        }
    }

    /** A round pool of shadow lying on the ground (y up), r px in radius: three layers, darker to the middle, the rim creeping in and out. */
    static void pool(PoseStack p, MultiBufferSource b, float r, float t, int seed, float alpha) {
        p.pushPose(); p.mulPose(Axis.XP.rotationDegrees(90));
        int n = 30;
        for (int layer = 0; layer < 3; layer++) {
            float rr = r * (1f - layer * .24f), zz = -(.25f + layer * .12f); int col = rgba(layer == 2 ? 0x020006 : 0x0A0418, alpha * (.4f + .22f * layer));
            for (int k = 0; k < n; k++) {
                float a0 = k * 6.2832f / n, a1 = (k + 1) * 6.2832f / n;
                float e0 = rr * (1f + .07f * (float) Math.sin(a0 * 5 + t * .07f + seed + layer) + .05f * (float) Math.sin(a0 * 9 - t * .11f));
                float e1 = rr * (1f + .07f * (float) Math.sin(a1 * 5 + t * .07f + seed + layer) + .05f * (float) Math.sin(a1 * 9 - t * .11f));
                tri(p, b, 0, 0, (float) Math.cos(a0) * e0, (float) Math.sin(a0) * e0, (float) Math.cos(a1) * e1, (float) Math.sin(a1) * e1, zz, col);
            }
        }
        p.popPose();
    }

    /** The Sword of Twilight, point down at the origin and 340 px tall: a slender pale blade edged in light, a crescent guard that bows toward the blade, a wrapped grip and a small pommel.
     *  dark is its unsealed form: a black blade with a red line and rings down it, black furniture, red light. */
    static void twilightSword(PoseStack p, MultiBufferSource b, float al, boolean dark) {
        int blade = rgba(dark ? 0x0C0C10 : 0xF6F6F2, al), flat = rgba(dark ? 0x1A1A20 : 0xE4E6EA, al), glow = dark ? 0xE01020 : 0xFFC83C, line = rgba(dark ? 0xFF2030 : 0xFFE9A0, al);
        int guard = rgba(dark ? 0x101014 : 0xE8C860, al), guardLt = rgba(dark ? 0x26262E : 0xFFF0B0, al), inlay = rgba(dark ? 0xE01020 : 0x27305A, al), grip = rgba(dark ? 0x0A0A0E : 0x263C96, al), band = rgba(dark ? 0x3A0A10 : 0xE8C860, al);
        for (int j = 0; j < 6; j++) { p.pushPose(); tr(p, 0, 4 + j * 8, 0); box(p, b, 4 + j * 4.2f, 8, 4, blade); p.popPose(); }        // the point
        p.pushPose(); tr(p, 0, 140, 0); box(p, b, 27, 186, 4, blade); box(p, b, 17, 186, 5.4f, flat); box(p, b, dark ? 2.4f : 1.6f, 180, 6.2f, line); p.popPose();
        if (dark) for (int j = 0; j < 5; j++) { p.pushPose(); tr(p, 0, 150 + j * 17, 0); box(p, b, 9, 9, 6.4f, line); box(p, b, 5, 5, 6.8f, blade); p.popPose(); }   // rings down the fuller
        p.pushPose(); tr(p, 0, 124, -.5f); box(p, b, 40, 236, 1, rgba(glow, .34f * al)); box(p, b, 58, 252, .6f, rgba(glow, .15f * al)); p.popPose();     // the light on its edges
        for (int j = 0; j < 4; j++) { p.pushPose(); tr(p, 0, 216 + j * 6, 0); box(p, b, 19 - j * 4.5f, 6, 6.6f, j == 0 ? guard : inlay); p.popPose(); }           // the ricasso inlay, pointing down the blade
        p.pushPose(); tr(p, 0, 238, 0); box(p, b, 30, 13, 11, guard); box(p, b, 12, 7, 12, inlay); p.popPose();
        for (int sd = -1; sd <= 1; sd += 2) for (int j = 1; j <= 6; j++) {   // the crescent guard, its horns bowing toward the blade
            p.pushPose(); tr(p, sd * (10 + j * 7.5f), 238 - j * j * .55f, 0); p.mulPose(Axis.ZP.rotationDegrees(sd * j * 3.2f)); box(p, b, 9, 11.5f - j * 1.1f, 10 - j * .9f, j % 2 == 0 ? guard : guardLt);
            if (j < 5) box(p, b, 9.2f, 2.2f, 10.4f - j * .9f, inlay); p.popPose();
        }
        p.pushPose(); tr(p, 0, 284, 0); box(p, b, 8.5f, 78, 8.5f, grip); p.popPose();
        for (int j = 0; j < 4; j++) { p.pushPose(); tr(p, 0, 252 + j * 21, 0); box(p, b, 10, 3, 10, band); p.popPose(); }
        p.pushPose(); tr(p, 0, 328, 0); box(p, b, 14, 10, 12, guard); tr(p, 0, 7, 0); box(p, b, 8, 5, 8, guardLt); p.popPose();
    }

    static final int[] WHITES = {0xFFFFFFFF, 0xFFF1F4FA, 0xFFE3EAF4, 0xFFFAFAFF};
    static final int[] PLUME = {0xFF14A07A, 0xFF22C89C, 0xFF7EF0D0, 0xFFEFFFF8};

    private static void wing(PoseStack p, MultiBufferSource b, float[] root, float dx, float dy, float dz, float sx, float sz, int sd, float flap, float size) {
        float ex = root[0] + sx * sd * size * .55f + dx * size * .1f, ey = root[1] + size * (.35f + flap * .35f), ez = root[2] + sz * sd * size * .55f + dz * size * .1f;
        float tx = ex + sx * sd * size * .75f - dx * size * .25f, ty = ey + size * (.05f + flap * .55f), tz = ez + sz * sd * size * .75f - dz * size * .25f;
        seg(p, b, root[0], root[1], root[2], ex, ey, ez, 1.5f, 0xFF0E5A46); seg(p, b, ex, ey, ez, tx, ty, tz, 1.1f, 0xFF0E5A46);
        for (int k = 0; k < 13; k++) {   // coverts on the arm, long primaries on the hand, every one tipped white
            float f = k / 12f; boolean hand = k >= 5; float g = hand ? (k - 5) / 7f : k / 5f;
            float ox = hand ? ex + (tx - ex) * g : root[0] + (ex - root[0]) * g, oy = hand ? ey + (ty - ey) * g : root[1] + (ey - root[1]) * g, oz = hand ? ez + (tz - ez) * g : root[2] + (ez - root[2]) * g;
            float len = size * (hand ? .75f + .45f * g : .45f + .2f * g), sweep = .25f + f * .9f;
            float fx2 = -dx * (1.1f - sweep * .5f) + sx * sd * sweep * .8f, fy2 = -.75f + flap * .3f - (hand ? 0 : .2f), fz2 = -dz * (1.1f - sweep * .5f) + sz * sd * sweep * .8f;
            feather(p, b, ox, oy, oz, fx2, fy2, fz2, len * .72f, 2.6f, PLUME[k % 2]);
            float l = (float) Math.sqrt(fx2 * fx2 + fy2 * fy2 + fz2 * fz2);
            feather(p, b, ox + fx2 / l * len * .66f, oy + fy2 / l * len * .66f, oz + fz2 / l * len * .66f, fx2, fy2, fz2, len * .34f, 2.2f, k % 3 == 0 ? 0xFFE04848 : PLUME[3]);
        }
    }

    /** The feathered serpent: points tail to head in pixels. A long emerald body, banded on the back and gold under the belly, a crest of quetzal plumes down its spine, a pair of
     *  great feathered wings behind the head that beat slowly, three long tail streamers, and a horned dragon's head with a tall fan of plumes, a beard and long whiskers. */
    static void serpent(PoseStack p, MultiBufferSource b, float[][] raw, float jaw, float t) {
        int m = raw.length, n = (m - 1) * 2 + 1; float[][] pts = new float[n][3];
        for (int i = 0; i < m - 1; i++) {   // smooth the spine: one more point between every two
            float[] p0 = raw[Math.max(i - 1, 0)], p1 = raw[i], p2 = raw[i + 1], p3 = raw[Math.min(i + 2, m - 1)];
            pts[i * 2] = p1;
            for (int k = 0; k < 3; k++) pts[i * 2 + 1][k] = (-p0[k] + 9 * p1[k] + 9 * p2[k] - p3[k]) / 16f;
        }
        pts[n - 1] = raw[m - 1];
        int iw = (int) (n * .74f);
        for (int i = 0; i < n - 1; i++) {
            float u = i / (float) (n - 1), w = 1.3f + 3.7f * (float) Math.sin(Math.PI * Math.min(1f, .1f + u * .9f));
            float[] a = pts[i], c = pts[i + 1];
            float dx = c[0] - a[0], dy = c[1] - a[1], dz = c[2] - a[2], l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz); if (l < 1e-4f) continue;
            dx /= l; dy /= l; dz /= l;
            float sx = -dz, sz = dx, sl = (float) Math.sqrt(sx * sx + sz * sz); if (sl < 1e-3f) { sx = 1; sz = 0; sl = 1; }
            sx /= sl; sz /= sl;
            seg(p, b, a[0], a[1], a[2], c[0], c[1], c[2], w, i % 4 < 2 ? 0xFF0E5A46 : 0xFF14785C);                                               // scales
            seg(p, b, a[0], a[1] + w * .2f, a[2], c[0], c[1] + w * .2f, c[2], w * .7f, i % 2 == 0 ? 0xFF1FA67C : 0xFF178A68);                 // a brighter stripe down the back
            seg(p, b, a[0], a[1] - w * .3f, a[2], c[0], c[1] - w * .3f, c[2], w * .62f, i % 2 == 0 ? 0xFFE8C860 : 0xFFC9A440);                // gold belly plates
            float crest = 2.4f + 5.5f * u * u + (float) Math.sin(t * .2f + i * .6f) * .5f;
            feather(p, b, a[0], a[1] + w * .55f, a[2], -dx * .7f, -dy * .7f + 1f, -dz * .7f, crest, 1.7f, PLUME[i % 3]);                         // the crest down the spine
            if (i % 2 == 0) for (int sd = -1; sd <= 1; sd += 2) for (int k = 0; k < 2; k++) {                                                  // plumes swept back along the flanks
                float fl = (6.5f - 2.8f * u + (float) Math.sin(t * .25f + i) * .6f) * (1f - k * .15f);
                feather(p, b, a[0] + sx * sd * w * .4f, a[1] + w * (.05f + k * .22f), a[2] + sz * sd * w * .4f,
                    -dx * .85f + sx * sd * (.35f + k * .3f), -dy * .85f + k * .4f, -dz * .85f + sz * sd * (.35f + k * .3f), fl, 2.4f, u > .82f && k == 0 ? 0xFFD03838 : PLUME[(i / 2 + k + 1) % 4]);
            }
            if (i == iw) for (int sd = -1; sd <= 1; sd += 2) wing(p, b, new float[]{a[0], a[1] + w * .4f, a[2]}, dx, dy, dz, sx, sz, sd, (float) Math.sin(t * .16f) * .5f + .25f, 17f);
        }
        // three long tail streamers, each a chain of plumes that trails and sways
        float[] t0 = pts[0], t1 = pts[1]; float tx = t0[0] - t1[0], ty = t0[1] - t1[1], tz = t0[2] - t1[2], tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz) + 1e-4f; tx /= tl; ty /= tl; tz /= tl;
        for (int k = -1; k <= 1; k++) {
            float x = t0[0], y = t0[1], z = t0[2];
            for (int j = 0; j < 4; j++) {
                float sw = (float) Math.sin(t * .15f + j * .9f + k) * .35f, ddx = tx + k * .28f + sw * tz, ddy = ty + .1f - j * .08f, ddz = tz + k * .2f - sw * tx, len = 9f - j * 1.2f;
                feather(p, b, x, y, z, ddx, ddy, ddz, len, 2.8f - j * .4f, j == 3 ? 0xFFD03838 : PLUME[(j + k + 1) % 3]);
                float dl = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz); x += ddx / dl * len * .9f; y += ddy / dl * len * .9f; z += ddz / dl * len * .9f;
            }
        }
        // the head
        float[] h0 = pts[n - 2], h1 = pts[n - 1]; float hx = h1[0] - h0[0], hy = h1[1] - h0[1], hz = h1[2] - h0[2], hl = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        if (hl < 1e-4f) return;
        p.pushPose(); tr(p, h1[0], h1[1], h1[2]); p.mulPose(new Quaternionf().rotationTo(0, 0, 1, hx / hl, hy / hl, hz / hl));
        box(p, b, 6.4f, 5.2f, 7.5f, 0xFF14785C);                         // skull
        p.pushPose(); tr(p, 0, 2.2f, -.5f); box(p, b, 5f, 1.2f, 6.5f, 0xFF1FA67C); p.popPose();   // crown plate
        p.pushPose(); tr(p, 0, 1.5f, 3.2f); box(p, b, 6.8f, .9f, 2.2f, 0xFF0E5A46); p.popPose();     // brow ridge
        for (int sd = -1; sd <= 1; sd += 2) {
            p.pushPose(); tr(p, sd * 2.9f, .9f, 2.2f); box(p, b, 1.1f, 1.5f, 1.9f, 0xFFFFD040); tr(p, sd * .1f, 0, .4f); box(p, b, .45f, 1.5f, .5f, 0xFF140808); p.popPose();   // slit eyes
            seg(p, b, sd * 2.2f, 2.6f, -1f, sd * 3.6f, 5.2f, -5f, 1.2f, 0xFFE8C860); seg(p, b, sd * 3.6f, 5.2f, -5f, sd * 3.2f, 8.4f, -9f, .7f, 0xFFFFF0B0);                 // gold horns swept back
            seg(p, b, sd * 2.2f, -.4f, 7.5f, sd * 5f, 0f + (float) Math.sin(t * .2f + sd) * 1.2f, 13f, .4f, 0xFFE8C860);                                                         // long whiskers
            seg(p, b, sd * 5f, (float) Math.sin(t * .2f + sd) * 1.2f, 13f, sd * 7.5f, -2f + (float) Math.sin(t * .2f + sd + 1) * 1.5f, 19f, .3f, 0xFFFFF0B0);
            for (int k = 0; k < 3; k++) feather(p, b, sd * 3.1f, -.6f + k * 1.1f, -1.5f, sd * 1f, (k - 1) * .35f, -1f, 6.5f - k, 2.2f, PLUME[k + 1]);                            // cheek plumes
        }
        p.pushPose(); tr(p, 0, .1f, 5f);
        p.pushPose(); tr(p, 0, .9f, 0); p.mulPose(Axis.XP.rotationDegrees(-jaw)); tr(p, 0, 0, 2.6f); box(p, b, 4.4f, 1.5f, 6.4f, 0xFF178A68);
        p.pushPose(); tr(p, 0, .9f, 2.2f); box(p, b, 2.6f, .6f, 1.4f, 0xFF0E5A46); p.popPose();   // nostrils
        for (int k = -1; k <= 1; k += 2) { tr(p, k * 1.6f, -1.1f, 1.8f); box(p, b, .55f, 1.8f, .55f, 0xFFFFFFFF); tr(p, 0, .2f, -2.2f); box(p, b, .4f, 1.1f, .4f, 0xFFFFFFFF); tr(p, -k * 1.6f, .9f, .4f); }   // fangs
        p.popPose();
        p.pushPose(); tr(p, 0, -1.1f, 0); p.mulPose(Axis.XP.rotationDegrees(jaw * .8f)); tr(p, 0, 0, 2.2f); box(p, b, 3.9f, 1.1f, 5.4f, 0xFFE8C860);
        seg(p, b, 0, .6f, 1.5f, 0, .9f, 6.5f + jaw * .1f, .5f, 0xFFC02828); seg(p, b, 0, .9f, 6.5f + jaw * .1f, -.8f, 1f, 8.4f + jaw * .1f, .35f, 0xFFC02828); seg(p, b, 0, .9f, 6.5f + jaw * .1f, .8f, 1f, 8.4f + jaw * .1f, .35f, 0xFFC02828);   // fork
        for (int k = -1; k <= 1; k++) feather(p, b, k * 1.2f, -.6f, -1f, k * .3f, -1f, -.5f, 4.5f - Math.abs(k), 1.6f, PLUME[3]);   // beard
        p.popPose();
        p.popPose();
        for (int k = 0; k < 9; k++) {   // a tall fan of plumes rising from the back of the skull, red at the tips
            float ang = (k - 4) * .3f, cx = (float) Math.sin(ang), cy = (float) Math.cos(ang), len = 13f - Math.abs(k - 4) * 1.6f + (float) Math.sin(t * .2f + k) * .6f;
            feather(p, b, cx * 2.2f, 2.4f, -3f, cx * .75f, cy * .8f, -.75f, len * .7f, 2.8f, PLUME[k % 3]);
            float fl = (float) Math.sqrt(cx * cx * .5625f + cy * cy * .64f + .5625f);
            feather(p, b, cx * 2.2f + cx * .75f / fl * len * .64f, 2.4f + cy * .8f / fl * len * .64f, -3f - .75f / fl * len * .64f, cx * .75f, cy * .8f, -.75f, len * .32f, 2.3f, k % 2 == 0 ? 0xFFD03838 : PLUME[3]);
        }
        p.popPose();
    }

    /** The coiled serpent at someone's feet, y up. */
    static void serpentCoil(PoseStack p, MultiBufferSource b, float t, float strike) {
        int n = 26; float[][] pts = new float[n][3];
        for (int i = 0; i < n; i++) {
            float u = i / (float) (n - 1), a = (float) (u * Math.PI * 4 + t * .06f), r = 20f - 6f * u + strike * 14 * u, y = 1.5f + u * u * 14f + strike * 8 * (1 - u);
            pts[i] = new float[]{(float) Math.cos(a) * r, y, (float) Math.sin(a) * r};
        }
        serpent(p, b, pts, 14f + (float) Math.sin(t * .2f) * 6f, t);
    }

    /** A wheel of fortune in the XY plane facing +z, r pixels across: a gilded double rim with studs and gems, a deep blue band of coloured wedges, a golden star and eight carved spokes. Spin it by turning the pose about z first. */
    static void wheel(PoseStack p, MultiBufferSource b, float r) {
        int gold = 0xFFD9A63A, dgold = 0xFF9A6E1E, navy = 0xFF14204A;
        int[] gem = {0xFFE02830, 0xFF3070F0, 0xFF30C860, 0xFFFFFFFF, 0xFFB040E0, 0xFFF0A020, 0xFF30D0E0, 0xFFFF70B0};
        int n = 24;
        for (float z : new float[]{-1.1f, 1.1f}) {
            for (int k = 0; k < n; k++) {   // the band: deep blue behind, bright wedges on it
                double a0 = Math.PI * 2 * k / n, a1 = Math.PI * 2 * (k + 1) / n;
                tri(p, b, 0, 0, (float) Math.cos(a0) * r, (float) Math.sin(a0) * r, (float) Math.cos(a1) * r, (float) Math.sin(a1) * r, z, navy);
                float r0 = r * .56f, r1 = r * .9f; int col = gem[(k / 3) % gem.length];
                if (k % 3 != 2) { tri(p, b, (float) Math.cos(a0) * r0, (float) Math.sin(a0) * r0, (float) Math.cos(a0) * r1, (float) Math.sin(a0) * r1, (float) Math.cos(a1) * r1, (float) Math.sin(a1) * r1, z * 1.3f, col);
                                  tri(p, b, (float) Math.cos(a0) * r0, (float) Math.sin(a0) * r0, (float) Math.cos(a1) * r1, (float) Math.sin(a1) * r1, (float) Math.cos(a1) * r0, (float) Math.sin(a1) * r0, z * 1.3f, col); }
            }
            for (int k = 0; k < 8; k++) {   // a golden eight-pointed star in the middle
                double a0 = Math.PI * 2 * k / 8, a1 = a0 + Math.PI / 8, a2 = a0 + Math.PI / 4;
                tri(p, b, (float) Math.cos(a0) * r * .55f, (float) Math.sin(a0) * r * .55f, (float) Math.cos(a1) * r * .22f, (float) Math.sin(a1) * r * .22f, (float) Math.cos(a2) * r * .55f, (float) Math.sin(a2) * r * .55f, z * 1.6f, k % 2 == 0 ? gold : dgold);
            }
        }
        for (int ring = 0; ring < 2; ring++) {   // the double gilded rim, thick and round
            float rr = r + .8f + ring * 1.9f;
            for (int k = 0; k < 40; k++) {
                double a0 = Math.PI * 2 * k / 40, a1 = Math.PI * 2 * (k + 1) / 40;
                seg(p, b, (float) Math.cos(a0) * rr, (float) Math.sin(a0) * rr, 0, (float) Math.cos(a1) * rr, (float) Math.sin(a1) * rr, 0, 2.6f - ring * .4f, ring == 0 ? gold : dgold);
            }
        }
        for (int k = 0; k < 16; k++) {   // studs and gems around the rim
            double a = Math.PI * 2 * k / 16; float rr = r + 3.6f;
            p.pushPose(); tr(p, (float) Math.cos(a) * rr, (float) Math.sin(a) * rr, 0); sphere(p, b, 1.5f, 5, 8, k % 2 == 0 ? gem[(k / 2) % gem.length] : 0xFFFFF0B0); p.popPose();
        }
        for (int k = 0; k < 8; k++) {   // spokes: a carved gold bar with a gem on it
            double a = Math.PI * 2 * k / 8; float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            seg(p, b, ca * 3f, sa * 3f, 0, ca * (r + .5f), sa * (r + .5f), 0, 2f, gold);
            seg(p, b, ca * 3f, sa * 3f, 1.3f, ca * (r * .55f), sa * (r * .55f), 1.3f, .9f, dgold);
            p.pushPose(); tr(p, ca * r * .72f, sa * r * .72f, 2f); sphere(p, b, 1.1f, 5, 8, gem[k]); p.popPose();
        }
        p.pushPose(); tr(p, 0, 0, 1.6f); sphere(p, b, 3.6f, 8, 14, gold); tr(p, 0, 0, 2f); sphere(p, b, 2f, 6, 10, 0xFFE02830); p.popPose();
    }

    static void pointer(PoseStack p, MultiBufferSource b, float r) {
        tri(p, b, -2.2f, r + 5.5f, 2.2f, r + 5.5f, 0, r - .5f, 1.6f, 0xFFD02020);
        tri(p, b, -2.2f, r + 5.5f, 2.2f, r + 5.5f, 0, r - .5f, -1.6f, 0xFFD02020);
    }

    /** A bandaged, thorn-wound rag doll about 12 px tall facing +z, feet at the origin. walk 0 is still; sit folds the legs forward. */
    static void doll(PoseStack p, MultiBufferSource b, float t, float walk, boolean sit, boolean binding) {
        int cloth = 0xFFD8CBA4, dark = 0xFFB09C70, wrap = 0xFFF0E8CC, thorn = 0xFF1E3A18, spike = 0xFF4A8A30, blood = 0xFF7A1018;
        float sw = (float) Math.sin(t * .9f) * 38f * walk, bob = Math.abs((float) Math.sin(t * .9f)) * .8f * walk;
        p.pushPose(); tr(p, 0, bob, 0);
        for (int sd = -1; sd <= 1; sd += 2) {   // legs
            p.pushPose(); tr(p, sd * 1.3f, 4.2f, 0); p.mulPose(Axis.XP.rotationDegrees(sit ? -75f : sd * sw));
            tr(p, 0, -2f, 0); box(p, b, 1.7f, 4.2f, 1.7f, cloth); box(p, b, 1.95f, .7f, 1.95f, wrap);
            tr(p, 0, -1.3f, .2f); box(p, b, 1.8f, .7f, 2.1f, 0xFF3A2A22);   // a little shoe
            p.popPose();
        }
        tr(p, 0, 6.6f, 0); box(p, b, 4.4f, 5.2f, 2.8f, dark);                                   // the dress
        for (int k = 0; k < 3; k++) { p.pushPose(); tr(p, 0, -1.6f + k * 1.5f, 0); p.mulPose(Axis.ZP.rotationDegrees(k % 2 == 0 ? 16 : -16)); box(p, b, 4.9f, .7f, 3.2f, wrap); p.popPose(); }
        for (int k = 0; k < 11; k++) {   // a thorn vine wound round the body, thorns out
            float a = k * .95f + .4f, yy = -2.4f + k * .5f, cx = (float) Math.cos(a) * 2.5f, cz = (float) Math.sin(a) * 1.8f;
            p.pushPose(); tr(p, cx, yy, cz); box(p, b, .7f, .7f, .7f, thorn); p.popPose();
            seg(p, b, cx, yy, cz, cx * 1.45f, yy + .5f, cz * 1.6f, .45f, spike);
        }
        p.pushPose(); tr(p, .8f, .6f, 1.5f); box(p, b, .9f, .9f, .1f, blood); p.popPose();
        for (int sd = -1; sd <= 1; sd += 2) {   // arms: bandaged and thorned
            p.pushPose(); tr(p, sd * 3.1f, 2.1f, 0);
            float sa = binding ? 150f + (float) Math.sin(t * .8f) * 12f : -sd * sw * .9f + (sit ? 10f : 0f);
            p.mulPose(Axis.XP.rotationDegrees(sa)); p.mulPose(Axis.ZP.rotationDegrees(sd * 6f));
            tr(p, 0, -2f, 0); box(p, b, 1.5f, 4.2f, 1.5f, cloth);
            for (int k = 0; k < 3; k++) { p.pushPose(); tr(p, 0, -1.4f + k * 1.4f, 0); p.mulPose(Axis.YP.rotationDegrees(k * 30)); box(p, b, 1.85f, .55f, 1.85f, wrap); p.popPose(); }
            seg(p, b, -.8f, 1.6f, .8f, .8f, -1.8f, -.8f, .4f, thorn); seg(p, b, .8f, .6f, -.8f, 1.4f, .6f, -1.6f, .35f, spike);
            p.popPose();
        }
        tr(p, 0, 5.4f, 0);   // the head
        box(p, b, 4.8f, 4.6f, 4.4f, 0xFFE6DCB8);
        p.pushPose(); tr(p, 0, .9f, 0); p.mulPose(Axis.ZP.rotationDegrees(-8)); box(p, b, 5.2f, .9f, 4.8f, wrap); p.popPose();   // a band over the brow
        p.pushPose(); tr(p, -.9f, -.4f, 0); p.mulPose(Axis.ZP.rotationDegrees(40)); box(p, b, 5.2f, .8f, 4.8f, wrap); p.popPose();
        for (int sd = -1; sd <= 1; sd += 2) { p.pushPose(); tr(p, sd * 1.15f, .3f, 2.25f); box(p, b, 1.2f, 1.2f, .2f, 0xFFFFFFFF); tr(p, 0, 0, .15f); box(p, b, .8f, .8f, .2f, 0xFF140808); p.popPose(); }   // button eyes
        for (int k = -2; k <= 2; k++) { p.pushPose(); tr(p, k * .6f, -1.4f + (Math.abs(k) == 2 ? .35f : 0), 2.25f); box(p, b, .22f, .7f, .15f, 0xFF301010); p.popPose(); }   // a stitched mouth
        for (int k = 0; k < 6; k++) seg(p, b, (k - 2.5f) * .8f, 2.2f, 0, (k - 2.5f) * 1.5f, 3.6f + (k % 2), (k % 3 - 1) * .8f, .5f, 0xFFA02828);   // yarn hair
        seg(p, b, -2.2f, 1.6f, 1f, -3f, 2.6f, 1.2f, .45f, thorn); seg(p, b, 2.2f, -1f, -1.6f, 3f, -.4f, -2f, .4f, spike);
        p.popPose();
    }

    /** A ball of storms, rPx across: a white-hot core wrapped in dark rolling cloud with lightning arcing out of it. */
    static void stormBall(PoseStack p, MultiBufferSource b, float t, float r, int bolts, int blobs) {
        sphere(p, b, r * .38f, 9, 14, 0xFFEAF4FF);
        sphere(p, b, r * .62f, 10, 16, rgba(0x6A90FF, .55f));
        for (int i = 0; i < blobs; i++) {   // rolling cloud
            float a = t * (.05f + .03f * rnd(i)) * (i % 2 == 0 ? 1 : -1) + i * 2.4f, el = (rnd(i + 40) - .5f) * 2.4f, d = r * (.5f + .3f * rnd(i + 90)), cr = r * (.16f + .12f * rnd(i + 7));
            p.pushPose(); tr(p, (float) Math.cos(a) * (float) Math.cos(el) * d, (float) Math.sin(el) * d, (float) Math.sin(a) * (float) Math.cos(el) * d);
            sphere(p, b, cr, 6, 9, i % 3 == 0 ? rgba(0x4A5270, .85f) : rgba(0x2A2F44, .85f)); p.popPose();
        }
        sphere(p, b, r, 10, 18, rgba(0x2A3050, .22f));
        int s = (int) Math.floor(t * 1.4f);
        for (int k = 0; k < bolts; k++) {   // jagged arcs, a new shape every few ticks
            float az = rnd(s * 31 + k) * 6.283f, el = (rnd(s * 17 + k * 5) - .5f) * 2.6f, len = r * (.9f + .5f * rnd(k + s));
            float dx = (float) (Math.cos(az) * Math.cos(el)), dy = (float) Math.sin(el), dz = (float) (Math.sin(az) * Math.cos(el));
            float x = 0, y = 0, z = 0;
            for (int j = 1; j <= 6; j++) {
                float f = j / 6f, nx = dx * len * f + (rnd(s * 7 + k * 13 + j) - .5f) * r * .35f, ny = dy * len * f + (rnd(s * 3 + k * 11 + j) - .5f) * r * .35f, nz = dz * len * f + (rnd(s * 5 + k * 7 + j) - .5f) * r * .35f;
                seg(p, b, x, y, z, nx, ny, nz, Math.max(.3f, r * .016f * (1.2f - f)), 0xFFE8F2FF); x = nx; y = ny; z = nz;
            }
        }
    }
}
