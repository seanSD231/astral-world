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

    // ---- models ----

    /** A tentacle of shadow standing up from the origin, swaying and tapering to a curled tip. */
    static void tentacle(PoseStack p, MultiBufferSource b, float t, int seed, float height, float base, int argb, int tip) {
        int n = 10; float sg = height / n, x = 0, y = 0, z = 0, ax = 0, az = 0;
        for (int i = 0; i < n; i++) {
            float u = i / (float) n;
            ax += (float) Math.sin(t * .11f + seed * 1.7f + i * .7f) * .17f + .06f * u * u * 4f;
            az += (float) Math.cos(t * .09f + seed * 2.3f + i * .6f) * .17f;
            float dx = (float) (Math.sin(ax) * Math.cos(az)), dy = (float) (Math.cos(ax) * Math.cos(az)), dz = (float) Math.sin(az);
            float nx = x + dx * sg, ny = y + dy * sg, nz = z + dz * sg;
            seg(p, b, x, y, z, nx, ny, nz, Math.max(.5f, base * (1f - u * .9f)), i >= n - 2 ? tip : argb);
            x = nx; y = ny; z = nz;
        }
    }

    static final int[] RAINBOW = {0xFFE03A3A, 0xFFF08A30, 0xFFF2D040, 0xFF30C8A0, 0xFF3090E0, 0xFF6050D0};

    /** The feathered serpent: points tail to head in pixels. Its body is scaled green, and every part of it is laid with long plumes in quetzal colours. */
    static void serpent(PoseStack p, MultiBufferSource b, float[][] pts, float jaw, float t) {
        int n = pts.length;
        for (int i = 0; i < n - 1; i++) {
            float u = i / (float) (n - 1), w = 1.4f + 3.0f * (float) Math.sin(Math.PI * Math.min(1f, .12f + u * .88f));
            float[] a = pts[i], c = pts[i + 1];
            seg(p, b, a[0], a[1], a[2], c[0], c[1], c[2], w, i % 2 == 0 ? 0xFF1F9E6A : 0xFF168058);
            seg(p, b, a[0], a[1] - w * .28f, a[2], c[0], c[1] - w * .28f, c[2], w * .55f, 0xFFE8D070);   // pale belly scales
            float dx = c[0] - a[0], dy = c[1] - a[1], dz = c[2] - a[2], l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz); if (l < 1e-4f) continue;
            dx /= l; dy /= l; dz /= l;
            float sx = dy * 0 - dz * 1, sy = dz * 0 - dx * 0, sz = dx * 1 - dy * 0;   // d x up(0,1,0)
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz); if (sl < 1e-3f) { sx = 1; sy = 0; sz = 0; sl = 1; }
            sx /= sl; sy /= sl; sz /= sl;
            float fl = 5.6f - 2.6f * u + (float) Math.sin(t * .25f + i) * .6f;
            for (int sd = -1; sd <= 1; sd += 2) for (int k = 0; k < 2; k++) {
                float spread = .55f + k * .35f, up = k == 0 ? .2f : .55f;
                feather(p, b, a[0] + sx * sd * w * .35f, a[1] + w * .2f, a[2] + sz * sd * w * .35f,
                    -dx * .75f + sx * sd * spread, -dy * .75f + up, -dz * .75f + sz * sd * spread, fl * (k == 0 ? 1f : .8f), 2.1f, RAINBOW[(i + k * 3 + (sd + 1)) % RAINBOW.length]);
            }
        }
        // a fan of long plumes off the tail
        float[] t0 = pts[0], t1 = pts[1]; float tx = t0[0] - t1[0], ty = t0[1] - t1[1], tz = t0[2] - t1[2];
        for (int k = 0; k < 5; k++) feather(p, b, t0[0], t0[1], t0[2], tx + (k - 2) * .55f, ty + (k % 2) * .4f, tz + (k - 2) * .3f, 9f - Math.abs(k - 2), 2.4f, RAINBOW[k % RAINBOW.length]);
        // the head
        float[] h0 = pts[n - 2], h1 = pts[n - 1]; float hx = h1[0] - h0[0], hy = h1[1] - h0[1], hz = h1[2] - h0[2], hl = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        if (hl < 1e-4f) return;
        p.pushPose(); tr(p, h1[0], h1[1], h1[2]); p.mulPose(new Quaternionf().rotationTo(0, 0, 1, hx / hl, hy / hl, hz / hl));
        box(p, b, 4.6f, 3.8f, 6f, 0xFF1F9E6A);
        box(p, b, 3.4f, 2.4f, 3.2f, 0xFF28B07A);                                  // snout
        tr(p, 0, .3f, 5.4f);
        p.pushPose(); tr(p, 0, .6f, 0); p.mulPose(Axis.XP.rotationDegrees(-jaw)); tr(p, 0, 0, 1.4f); box(p, b, 2.8f, 1f, 3.6f, 0xFFF0D060); box(p, b, .5f, .9f, .5f, 0xFFFFFFFF); p.popPose();
        p.pushPose(); tr(p, 0, -.6f, 0); p.mulPose(Axis.XP.rotationDegrees(jaw)); tr(p, 0, 0, 1.2f); box(p, b, 2.6f, 1f, 3.2f, 0xFFD8B850); p.popPose();
        p.popPose();
        p.pushPose(); tr(p, h1[0], h1[1], h1[2]); p.mulPose(new Quaternionf().rotationTo(0, 0, 1, hx / hl, hy / hl, hz / hl));
        for (int sd = -1; sd <= 1; sd += 2) { tr(p, sd * 2.4f, .9f, 1.2f); box(p, b, .8f, 1.2f, 1.2f, 0xFFFFE050); tr(p, -sd * 2.4f, -.9f, -1.2f); }
        for (int k = 0; k < 7; k++) {   // the crest, swept back
            float s2 = (k - 3) * .5f;
            feather(p, b, s2 * .5f, 1.9f, -1f, s2 * .5f, .45f, -1f, 8f - Math.abs(k - 3) * 1.2f, 2.2f, RAINBOW[(k + 2) % RAINBOW.length]);
        }
        p.popPose();
    }

    /** The coiled serpent at someone's feet, y up. */
    static void serpentCoil(PoseStack p, MultiBufferSource b, float t, float strike) {
        int n = 26; float[][] pts = new float[n][3];
        for (int i = 0; i < n; i++) {
            float u = i / (float) (n - 1), a = (float) (u * Math.PI * 4 + t * .06f), r = 7f - 3.4f * u + strike * 14 * u, y = 1.5f + u * u * 14f + strike * 8 * (1 - u);
            pts[i] = new float[]{(float) Math.cos(a) * r, y, (float) Math.sin(a) * r};
        }
        serpent(p, b, pts, 14f + (float) Math.sin(t * .2f) * 6f, t);
    }

    /** A wheel of fortune in the XY plane facing +z, r pixels across. Spin it by turning the pose about z first. */
    static void wheel(PoseStack p, MultiBufferSource b, float r) {
        int[] cols = {0xFFC8282C, 0xFFF0C440, 0xFF2850C0, 0xFFF0C440, 0xFF28A050, 0xFFF0C440, 0xFF7A30B0, 0xFFF0C440};
        int n = 16;
        for (int k = 0; k < n; k++) {   // wedges, the back and the front
            double a0 = Math.PI * 2 * k / n, a1 = Math.PI * 2 * (k + 1) / n;
            for (float z : new float[]{-.7f, .7f}) {
                tri(p, b, 0, 0, (float) Math.cos(a0) * r, (float) Math.sin(a0) * r, (float) Math.cos(a1) * r, (float) Math.sin(a1) * r, z, cols[k % cols.length]);
                tri(p, b, 0, 0, (float) Math.cos(a0) * r * .55f, (float) Math.sin(a0) * r * .55f, (float) Math.cos(a1) * r * .55f, (float) Math.sin(a1) * r * .55f, z * 1.5f, k % 2 == 0 ? 0xFF2A1A10 : 0xFF3A2A18);
            }
        }
        for (int k = 0; k < 32; k++) {   // the gilded rim
            double a0 = Math.PI * 2 * k / 32, a1 = Math.PI * 2 * (k + 1) / 32;
            seg(p, b, (float) Math.cos(a0) * (r + .6f), (float) Math.sin(a0) * (r + .6f), 0, (float) Math.cos(a1) * (r + .6f), (float) Math.sin(a1) * (r + .6f), 0, 2.4f, 0xFFD9A63A);
        }
        for (int k = 0; k < 8; k++) {   // spokes and pegs
            double a = Math.PI * 2 * k / 8;
            seg(p, b, 0, 0, 1.6f, (float) Math.cos(a) * r, (float) Math.sin(a) * r, 1.6f, 1.1f, 0xFFB88A2A);
            p.pushPose(); tr(p, (float) Math.cos(a) * (r + 2.2f), (float) Math.sin(a) * (r + 2.2f), 0); sphere(p, b, 1.4f, 5, 8, 0xFFFFF0A0); p.popPose();
        }
        p.pushPose(); tr(p, 0, 0, 1.8f); sphere(p, b, 3.2f, 7, 12, 0xFFE8B840); tr(p, 0, 0, 1.5f); sphere(p, b, 1.5f, 5, 8, 0xFFFFF4C0); p.popPose();
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
                seg(p, b, x, y, z, nx, ny, nz, Math.max(.5f, r * .035f * (1.2f - f)), 0xFFE8F2FF); x = nx; y = ny; z = nz;
            }
        }
    }
}
