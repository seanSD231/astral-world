package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Client only: draws a placed tarot card and its hologram. Glowing sprites (additive quads) plus small lit 3D models built from
 * boxes, cones, spheres and tori. Nothing here follows the camera except tiny particles: every model and every large effect is
 * fixed in the world (the hologram "faces" the direction the card was placed).
 * Seq 9-6: just the card. Seq 5-4 Rare: small spinning symbol. Seq 3-2 Epic: the pathway symbol, beam, rune ring, props.
 * Seq 1 Legendary and Seq 0 Mythic: a hand-built scene per pathway. Shiny cards use a gold palette.
 */
public class HoloRenderer extends EntityRenderer<HoloCardEntity> {
    private static final int LIGHT = 15728880;
    private static final Map<String, ResourceLocation> TEX = new HashMap<>();
    private static final float TAU = (float) (Math.PI * 2), PI = (float) Math.PI;
    private static final ResourceLocation SOLID = ResourceLocation.fromNamespaceAndPath("astral_world", "textures/holo/solid.png");

    /** Palette per pathway (Tarot.PATHWAYS order): main colour, deep colour, accent. Taken from each pathway's own symbol. */
    private static final int[][] PAL = {
        {0xF2A0B0, 0xC04860, 0xFFE0E6}, {0x5AA8FF, 0x2A5CC8, 0xB8E0FF}, {0xF4EFE8, 0x9AA4B8, 0xFFE8A0}, {0xFF5FA8, 0x8A1850, 0xFFC0DC},
        {0xFFD84A, 0xFF9A20, 0xFFF4B0}, {0xCFEFC0, 0x4E8A5A, 0xFFFFFF}, {0xE04030, 0x6A1410, 0xFF9080}, {0xFF6A38, 0xB02818, 0xFFC060},
        {0xE0E6F2, 0x6A7890, 0x20242E}, {0xD8DCE0, 0x808890, 0xFFFFFF}, {0xDCE6EC, 0x9AA8B0, 0x58A878}, {0xB890F0, 0x5A3AA0, 0xFFE8A0},
        {0x7AD890, 0x2E8A50, 0xFF9CC4}, {0xC8C4EC, 0x6A5CA8, 0x8890A0}, {0x90C0FF, 0x4A78D8, 0xFFFFFF}, {0x4CC0FF, 0x1A5ED0, 0xE8FAFF},
        {0xFFB860, 0xC87020, 0xFFE8B0}, {0xFF6A5C, 0x8A1818, 0xFFC0B8}, {0xC0B8F0, 0x6A5CB0, 0xFFFFFF}, {0x88B0F0, 0x2C3C90, 0xE8F0FF},
        {0xE0F0FF, 0x70A0D0, 0x60E8FF}, {0xC87038, 0x6A2810, 0xFFB070}};

    /** Simple look for Rare and Epic: symbol shape, prop shape. */
    private record Cfg(String core, String prop) {}
    private static final Cfg[] CFG = {
        new Cfg("crescent", "leaf"), new Cfg("door", "key"), new Cfg("scales", "sword"), new Cfg("shard", "petal"), new Cfg("note", "feather"),
        new Cfg("skull", "skull"), new Cfg("crack", "drop"), new Cfg("flame", "sword"), new Cfg("crown", "diamond"), new Cfg("shard", "crack"),
        new Cfg("wheel", "diamond"), new Cfg("lantern", "star"), new Cfg("petal", "leaf"), new Cfg("chain", "ring"), new Cfg("tower", "page"),
        new Cfg("spiral", "drop"), new Cfg("cog", "clock"), new Cfg("cross", "chain"), new Cfg("eye", "page"), new Cfg("crescent", "star"),
        new Cfg("ring", "mote"), new Cfg("sword", "shard")};

    public HoloRenderer(EntityRendererProvider.Context ctx) { super(ctx); }
    @Override public ResourceLocation getTextureLocation(HoloCardEntity e) { return tex("glow"); }
    private static ResourceLocation tex(String n) {
        return TEX.computeIfAbsent(n, k -> ResourceLocation.fromNamespaceAndPath("astral_world", "textures/holo/" + k + ".png"));
    }

    // ---- state for one draw call ----
    private MultiBufferSource buf;
    private Matrix4f mat;
    private float rx, ry, rz, ux, uy, uz;   // camera right and up (only for tiny particles)
    private float fyaw, fx, fz, sx0, sz0;   // direction the hologram faces, and its right
    private int P1, P2, P3;
    private boolean gold;
    private float t, R, top, S, CY;
    private int curPath;

    private static float h(int i, int salt) {
        double v = Math.sin(i * 12.9898 + salt * 78.233) * 43758.5453;
        return (float) (v - Math.floor(v));
    }
    private static float fract(float v) { return v - (float) Math.floor(v); }
    private static float fd(float p) { return Math.min(1f, 2.2f * (float) Math.sin(p * Math.PI)); }   // fade in and out, mostly fully visible
    private static int rgba(int rgb, float a) {
        int al = Math.max(0, Math.min(255, (int) (Math.min(1f, a * 1.25f) * 255)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }
    private static int mix(int a, int b, float f) {
        int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
        return (r << 16) | (g << 8) | bl;
    }
    private static int hsv(float hue, float sat) {
        float hh = (hue - (float) Math.floor(hue)) * 6f; int i = (int) hh; float f = hh - i;
        float p = 1 - sat, q = 1 - sat * f, u = 1 - sat * (1 - f);
        float r, g, b;
        switch (i) { case 0 -> { r = 1; g = u; b = p; } case 1 -> { r = q; g = 1; b = p; } case 2 -> { r = p; g = 1; b = u; }
            case 3 -> { r = p; g = q; b = 1; } case 4 -> { r = u; g = p; b = 1; } default -> { r = 1; g = p; b = q; } }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
    /** Steel for models; gold on shiny cards. */
    private int metal(float f) { return gold ? mix(0xB87810, 0xFFE890, f) : mix(0x6A7488, 0xF4F8FF, f); }

    // ================= additive sprites =================
    private void quad(String tex, float cx, float cy, float cz, float ax, float ay, float az, float bx, float by, float bz, int argb) {
        quadUV(tex, cx, cy, cz, ax, ay, az, bx, by, bz, argb, 0, 0, 1, 1);
    }
    private void quadUV(String tex, float cx, float cy, float cz, float ax, float ay, float az, float bx, float by, float bz, int argb, float u0, float v0, float u1, float v1) {
        int a = (argb >> 24) & 255, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        if (a <= 2) return;
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(tex(tex)));
        r = r * a / 255; g = g * a / 255; b = b * a / 255;   // the glow blend ignores alpha, so fade by darkening
        float[][] p = {
            {cx - ax - bx, cy - ay - by, cz - az - bz}, {cx + ax - bx, cy + ay - by, cz + az - bz},
            {cx + ax + bx, cy + ay + by, cz + az + bz}, {cx - ax + bx, cy - ay + by, cz - az + bz}};
        float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        for (int side = 0; side < 2; side++)
            for (int k = 0; k < 4; k++) {
                int i = side == 0 ? k : 3 - k;
                vc.addVertex(mat, p[i][0], p[i][1], p[i][2]).setColor(r, g, b, a).setUv(uv[i][0], uv[i][1])
                  .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(0, 1, 0);
            }
    }
    /** Camera-facing quad, for small particles only. Negative width mirrors. */
    private void bb(String tex, float x, float y, float z, float w, float hgt, float rot, int argb) {
        float c = (float) Math.cos(rot), s = (float) Math.sin(rot);
        float hx = w / 2, hy = hgt / 2;
        quad(tex, x, y, z, (rx * c + ux * s) * hx, (ry * c + uy * s) * hx, (rz * c + uz * s) * hx,
             (-rx * s + ux * c) * hy, (-ry * s + uy * c) * hy, (-rz * s + uz * c) * hy, argb);
    }
    private void flat(String tex, float x, float y, float z, float size, float rot, int argb) {
        float c = (float) Math.cos(rot) * size / 2, s = (float) Math.sin(rot) * size / 2;
        quad(tex, x, y, z, c, 0, s, -s, 0, c, argb);
    }
    /** Upright quad turned around Y by yaw. */
    private void stand(String tex, float x, float y, float z, float w, float hgt, float yaw, int argb) {
        float c = (float) Math.cos(yaw) * w / 2, s = (float) Math.sin(yaw) * w / 2;
        quad(tex, x, y, z, c, 0, s, 0, hgt / 2, 0, argb);
    }
    /** Two upright quads crossed at 90 degrees, so it reads from every side. */
    private void stand2(String tex, float x, float y, float z, float w, float hgt, float yaw, int argb) {
        stand(tex, x, y, z, w, hgt, yaw, argb); stand(tex, x, y, z, w, hgt, yaw + PI / 2, argb);
    }
    /** Upright quad facing the direction the hologram faces. Negative width mirrors. */
    private void facing(String tex, float x, float y, float z, float w, float hgt, float roll, int argb) {
        float c = (float) Math.cos(roll), s = (float) Math.sin(roll), hw = w / 2, hh = hgt / 2;
        quad(tex, x, y, z, sx0 * c * hw, s * hw, sz0 * c * hw, -sx0 * s * hh, c * hh, -sz0 * s * hh, argb);
    }
    /** Beam, chain light, bolt: crossed so it never turns to the camera. */
    private void column(String tex, float x, float y0, float y1, float z, float w, int argb) {
        float hy = (y1 - y0) / 2, cy = (y0 + y1) / 2;
        quad(tex, x, cy, z, w / 2, 0, 0, 0, hy, 0, argb);
        quad(tex, x, cy, z, 0, 0, w / 2, 0, hy, 0, argb);
    }

    // ================= lit 3D models =================
    private final ArrayDeque<Matrix4f> mstack = new ArrayDeque<>();
    private Matrix4f cur = new Matrix4f();
    private boolean strans;
    private final Vector3f tp = new Vector3f(), tn = new Vector3f();
    private void push() { mstack.push(new Matrix4f(cur)); }
    private void pop() { cur = mstack.pop(); }
    private void tr(float x, float y, float z) { cur.translate(x, y, z); }
    private void rY(float a) { cur.rotateY(a); }
    private void rX(float a) { cur.rotateX(a); }
    private void rZ(float a) { cur.rotateZ(a); }
    private void clear() { cur = new Matrix4f(); mstack.clear(); }
    private void solid(boolean translucent) { strans = translucent; }

    private void sv(float[] p, float[] n, int rgb, int a, VertexConsumer vc) {
        cur.transformPosition(p[0], p[1], p[2], tp);
        cur.transformDirection(n[0], n[1], n[2], tn);
        vc.addVertex(mat, tp.x, tp.y, tp.z).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a).setUv(.5f, .5f)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(tn.x, tn.y, tn.z);
    }
    /** Lit quad in the current local frame; hint is a point inside the shape so the normal points outward. */
    private void sq(float[] a, float[] b, float[] c, float[] d, float[] hint, int rgb, int alpha) {
        float ex = b[0] - a[0], ey = b[1] - a[1], ez = b[2] - a[2], fx2 = d[0] - a[0], fy2 = d[1] - a[1], fz2 = d[2] - a[2];
        float nx = ey * fz2 - ez * fy2, ny = ez * fx2 - ex * fz2, nz = ex * fy2 - ey * fx2;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-9f) { nx = 0; ny = 1; nz = 0; } else { nx /= l; ny /= l; nz /= l; }
        float mx = (a[0] + b[0] + c[0] + d[0]) / 4 - hint[0], my = (a[1] + b[1] + c[1] + d[1]) / 4 - hint[1], mz = (a[2] + b[2] + c[2] + d[2]) / 4 - hint[2];
        if (nx * mx + ny * my + nz * mz < 0) { nx = -nx; ny = -ny; nz = -nz; }
        float[] n = {nx, ny, nz};
        VertexConsumer vc = buf.getBuffer(strans ? RenderType.entityTranslucent(SOLID) : RenderType.entityCutoutNoCull(SOLID));
        sv(a, n, rgb, alpha, vc); sv(b, n, rgb, alpha, vc); sv(c, n, rgb, alpha, vc); sv(d, n, rgb, alpha, vc);
    }
    private static float[] v3(float x, float y, float z) { return new float[]{x, y, z}; }

    /** Cone or cylinder along +Y from y0 to y1, elliptical section scaled by (sx, sz). */
    private void frustum(float y0, float y1, float r0, float r1, int n, float sx, float sz, int rgb, int a, boolean caps) {
        float[] hint = v3(0, (y0 + y1) / 2, 0);
        for (int i = 0; i < n; i++) {
            float a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
            float[] p0 = v3((float) Math.cos(a0) * r0 * sx, y0, (float) Math.sin(a0) * r0 * sz), p1 = v3((float) Math.cos(a1) * r0 * sx, y0, (float) Math.sin(a1) * r0 * sz);
            float[] p2 = v3((float) Math.cos(a1) * r1 * sx, y1, (float) Math.sin(a1) * r1 * sz), p3 = v3((float) Math.cos(a0) * r1 * sx, y1, (float) Math.sin(a0) * r1 * sz);
            sq(p0, p1, p2, p3, hint, rgb, a);
            if (caps) {
                sq(p3, p2, v3(0, y1, 0), v3(0, y1, 0), v3(0, y1 - 1, 0), rgb, a);
                sq(p0, p1, v3(0, y0, 0), v3(0, y0, 0), v3(0, y0 + 1, 0), rgb, a);
            }
        }
    }
    private void box(float cx, float cy, float cz, float hx, float hy, float hz, int rgb, int a) {
        float[] hint = v3(cx, cy, cz);
        float[][] c = new float[8][];
        for (int i = 0; i < 8; i++) c[i] = v3(cx + ((i & 1) == 0 ? -hx : hx), cy + ((i & 2) == 0 ? -hy : hy), cz + ((i & 4) == 0 ? -hz : hz));
        sq(c[0], c[1], c[3], c[2], hint, rgb, a); sq(c[4], c[5], c[7], c[6], hint, rgb, a);
        sq(c[0], c[1], c[5], c[4], hint, rgb, a); sq(c[2], c[3], c[7], c[6], hint, rgb, a);
        sq(c[0], c[2], c[6], c[4], hint, rgb, a); sq(c[1], c[3], c[7], c[5], hint, rgb, a);
    }
    /** Rectangular frustum along +Y: half-width w (X) and half-depth d (Z) at y0 and at y1. */
    private void taper(float y0, float y1, float w0, float d0, float w1, float d1, int rgb, int a) {
        float[] hint = v3(0, (y0 + y1) / 2, 0);
        float[] b0 = v3(-w0, y0, -d0), b1 = v3(w0, y0, -d0), b2 = v3(w0, y0, d0), b3 = v3(-w0, y0, d0);
        float[] t0 = v3(-w1, y1, -d1), t1 = v3(w1, y1, -d1), t2 = v3(w1, y1, d1), t3 = v3(-w1, y1, d1);
        sq(b0, b1, t1, t0, hint, rgb, a); sq(b1, b2, t2, t1, hint, rgb, a); sq(b2, b3, t3, t2, hint, rgb, a); sq(b3, b0, t0, t3, hint, rgb, a);
        sq(t0, t1, t2, t3, v3(0, y1 - 1, 0), rgb, a); sq(b0, b1, b2, b3, v3(0, y0 + 1, 0), rgb, a);
    }
    private void sphere(float cx, float cy, float cz, float rx2, float ry2, float rz2, int lat, int lon, int rgb, int a) {
        float[] hint = v3(cx, cy, cz);
        for (int i = 0; i < lat; i++) {
            float l0 = -PI / 2 + PI * i / lat, l1 = -PI / 2 + PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float g0 = TAU * j / lon, g1 = TAU * (j + 1) / lon;
                sq(sp(cx, cy, cz, rx2, ry2, rz2, l0, g0), sp(cx, cy, cz, rx2, ry2, rz2, l0, g1), sp(cx, cy, cz, rx2, ry2, rz2, l1, g1), sp(cx, cy, cz, rx2, ry2, rz2, l1, g0), hint, rgb, a);
            }
        }
    }
    private static float[] sp(float cx, float cy, float cz, float rx2, float ry2, float rz2, float lat, float lon) {
        return v3(cx + (float) (Math.cos(lat) * Math.cos(lon)) * rx2, cy + (float) Math.sin(lat) * ry2, cz + (float) (Math.cos(lat) * Math.sin(lon)) * rz2);
    }
    /** Ring in the local XY plane (elliptical, radii rxx and ryy), tube radius r, over the angle range th0..th1. */
    private void torus(float cx, float cy, float cz, float rxx, float ryy, float r, int segs, int sides, float th0, float th1, int rgb, int a) {
        for (int i = 0; i < segs; i++) {
            float t0 = th0 + (th1 - th0) * i / segs, t1 = th0 + (th1 - th0) * (i + 1) / segs;
            float[] c0 = v3(cx + (float) Math.cos(t0) * rxx, cy + (float) Math.sin(t0) * ryy, cz), c1 = v3(cx + (float) Math.cos(t1) * rxx, cy + (float) Math.sin(t1) * ryy, cz);
            for (int j = 0; j < sides; j++) {
                float p0 = TAU * j / sides, p1 = TAU * (j + 1) / sides;
                sq(tt(c0, t0, r, p0), tt(c0, t0, r, p1), tt(c1, t1, r, p1), tt(c1, t1, r, p0), c0, rgb, a);
            }
        }
    }
    private static float[] tt(float[] c, float th, float r, float ph) {
        float cp = (float) Math.cos(ph) * r;
        return v3(c[0] + (float) Math.cos(th) * cp, c[1] + (float) Math.sin(th) * cp, c[2] + (float) Math.sin(ph) * r);
    }
    private void ring(float rr, float r, int rgb, int a) { torus(0, 0, 0, rr, rr, r, 16, 5, 0, TAU, rgb, a); }

    // ---- models (local frame: +Y is "up" along the object, size given by L) ----
    private void sword(float L, int blade, int guard, int gem) {
        taper(.2f * L, .86f * L, .055f * L, .011f * L, .046f * L, .010f * L, blade, 255);
        taper(.86f * L, L, .046f * L, .010f * L, 0, 0, blade, 255);
        taper(.24f * L, .8f * L, .014f * L, .0125f * L, .011f * L, .0125f * L, mix(blade, 0xFFFFFF, .6f), 255);
        box(0, .19f * L, 0, .17f * L, .022f * L, .03f * L, guard, 255);
        sphere(-.17f * L, .19f * L, 0, .03f * L, .03f * L, .03f * L, 4, 6, guard, 255); sphere(.17f * L, .19f * L, 0, .03f * L, .03f * L, .03f * L, 4, 6, guard, 255);
        sphere(0, .19f * L, 0, .045f * L, .045f * L, .04f * L, 4, 8, gem, 255);
        frustum(.02f * L, .17f * L, .022f * L, .02f * L, 8, 1, 1, 0x3A2A24, 255, true);
        for (int i = 0; i < 4; i++) torus(0, .05f * L + i * .035f * L, 0, .024f * L, .024f * L, .004f * L, 6, 3, 0, TAU, guard, 255);
        sphere(0, 0, 0, .04f * L, .04f * L, .04f * L, 4, 8, gem, 255);
    }
    private void dagger(float L, int blade, int guard, int gem) {
        taper(.22f * L, .56f * L, .06f * L, .011f * L, .105f * L, .013f * L, blade, 255);
        taper(.56f * L, L, .105f * L, .013f * L, 0, 0, blade, 255);
        taper(.24f * L, .9f * L, .012f * L, .014f * L, .004f * L, .014f * L, mix(blade, 0xFFFFFF, .6f), 255);
        box(0, .2f * L, 0, .13f * L, .02f * L, .028f * L, guard, 255);
        sphere(-.13f * L, .2f * L, 0, .028f * L, .035f * L, .028f * L, 4, 6, guard, 255); sphere(.13f * L, .2f * L, 0, .028f * L, .035f * L, .028f * L, 4, 6, guard, 255);
        frustum(.06f * L, .18f * L, .03f * L, .025f * L, 8, 1, 1, 0x2A1E1E, 255, true);
        torus(0, .035f * L, 0, .04f * L, .04f * L, .012f * L, 8, 4, 0, TAU, gem, 255);
    }
    /** A chain hanging down from the current origin. */
    private void chain(float length, float link, int rgb) {
        float step = link * .78f; int n = Math.max(1, (int) (length / step));
        for (int k = 0; k < n; k++) {
            push(); tr(0, -k * step, 0); if ((k & 1) == 1) rY(PI / 2);
            torus(0, 0, 0, link * .26f, link * .5f, link * .095f, 8, 4, 0, TAU, rgb, 255);
            pop();
        }
    }
    private void trident(float L, int rgb, int gem) {
        frustum(0, .8f * L, .018f * L, .018f * L, 8, 1, 1, rgb, 255, true);
        taper(.8f * L, L, .035f * L, .012f * L, 0, 0, rgb, 255); taper(.74f * L, .8f * L, .03f * L, .012f * L, .035f * L, .012f * L, rgb, 255);
        for (int s = -1; s <= 1; s += 2) {
            box(s * .075f * L, .72f * L, 0, .078f * L, .014f * L, .014f * L, rgb, 255);
            frustum(.72f * L, .9f * L, .014f * L, .014f * L, 6, 1, 1, rgb, 255, false);
            push(); tr(s * .15f * L, .9f * L, 0); taper(0, .1f * L, .03f * L, .012f * L, 0, 0, rgb, 255); pop();
            push(); tr(s * .15f * L, .78f * L, 0); rZ(-s * 2.2f); taper(0, .05f * L, .012f * L, .008f * L, 0, 0, rgb, 255); pop();
        }
        sphere(0, .74f * L, 0, .035f * L, .035f * L, .035f * L, 4, 8, gem, 255);
        taper(-.04f * L, 0, 0, 0, .02f * L, .02f * L, rgb, 255);
    }
    private void crown(float r, int rgb, int gem) {
        frustum(0, .28f * r, r, r * 1.04f, 16, 1, 1, rgb, 255, false);
        for (int i = 0; i < 8; i++) { float a = i * TAU / 8; push(); tr((float) Math.cos(a) * r, .28f * r, (float) Math.sin(a) * r); taper(0, .5f * r, .09f * r, .09f * r, 0, 0, rgb, 255); tr(0, .52f * r, 0); sphere(0, 0, 0, .06f * r, .06f * r, .06f * r, 3, 5, gem, 255); pop(); }
        for (int i = 0; i < 8; i++) { float a = (i + .5f) * TAU / 8; sphere((float) Math.cos(a) * r * 1.03f, .14f * r, (float) Math.sin(a) * r * 1.03f, .05f * r, .05f * r, .05f * r, 3, 5, gem, 255); }
    }
    private void gear(float r, int teeth, float thick, int rgb, int hub) {
        push(); rX(PI / 2);
        frustum(-thick / 2, thick / 2, r, r, teeth * 2, 1, 1, rgb, 255, true);
        frustum(-thick * .6f, thick * .6f, r * .3f, r * .3f, 10, 1, 1, hub, 255, true);
        for (int i = 0; i < teeth; i++) { float a = i * TAU / teeth; push(); rY(-a); tr(r * 1.06f, 0, 0); box(0, 0, 0, r * .12f, thick * .5f, r * .09f, rgb, 255); pop(); }
        pop();
    }
    private void key(float L, int rgb) {
        frustum(0, L, .035f * L, .035f * L, 8, 1, 1, rgb, 255, true);
        push(); tr(0, L + .13f * L, 0); torus(0, 0, 0, .12f * L, .12f * L, .035f * L, 12, 5, 0, TAU, rgb, 255); pop();
        box(.07f * L, .12f * L, 0, .07f * L, .035f * L, .03f * L, rgb, 255); box(.06f * L, .25f * L, 0, .06f * L, .035f * L, .03f * L, rgb, 255);
    }
    private void skull(float r, int bone, int glow) {
        sphere(0, 0, 0, r, r * .92f, r * .95f, 8, 14, bone, 255);
        box(0, -r * .62f, r * .1f, r * .55f, r * .22f, r * .6f, bone, 255);
        for (int i = -2; i <= 2; i++) box(i * r * .2f, -r * .7f, r * .68f, r * .07f, r * .1f, r * .02f, mix(bone, 0xFFFFFF, .5f), 255);
        sphere(-r * .36f, -r * .05f, r * .8f, r * .22f, r * .25f, r * .2f, 5, 8, 0x080808, 255);
        sphere(r * .36f, -r * .05f, r * .8f, r * .22f, r * .25f, r * .2f, 5, 8, 0x080808, 255);
        box(0, -r * .3f, r * .93f, r * .07f, r * .12f, r * .03f, 0x080808, 255);
        sphere(-r * .36f, -r * .05f, r * .86f, r * .09f, r * .09f, r * .05f, 4, 6, glow, 255); sphere(r * .36f, -r * .05f, r * .86f, r * .09f, r * .09f, r * .05f, 4, 6, glow, 255);
    }
    private void flask(float r, int glass, int liquid) {
        solid(true); sphere(0, 0, 0, r, r, r, 8, 12, glass, 150); frustum(r * .8f, r * 1.7f, r * .3f, r * .25f, 10, 1, 1, glass, 150, false); solid(false);
        sphere(0, -r * .15f, 0, r * .8f, r * .65f, r * .8f, 6, 10, liquid, 255);
        frustum(r * 1.7f, r * 2.0f, r * .3f, r * .33f, 10, 1, 1, 0x7A5A38, 255, true);
    }
    private void flowerModel(float L, int petal, int petal2, int heart, int leaf) {
        frustum(0, L, .035f * L, .028f * L, 8, 1, 1, leaf, 255, true);
        for (int s = -1; s <= 1; s += 2) {
            push(); tr(0, .3f * L, 0); rY(s > 0 ? 0 : PI); rZ(-1.0f); sphere(.18f * L, 0, 0, .2f * L, .015f * L, .075f * L, 4, 8, leaf, 255); pop();
            push(); tr(0, .55f * L, 0); rY(s > 0 ? PI / 2 : -PI / 2); rZ(-1.15f); sphere(.14f * L, 0, 0, .15f * L, .012f * L, .06f * L, 4, 8, leaf, 255); pop();
        }
        push(); tr(0, L, 0);
        for (int layer = 0; layer < 3; layer++) {
            int n = 9 + layer * 3; float tilt = 1.25f - layer * .4f, len = (.34f + layer * .0f) * L * (layer == 0 ? 1.0f : layer == 1 ? .9f : .7f);
            for (int i = 0; i < n; i++) {
                push(); rY(i * TAU / n + layer * .35f); rZ(tilt); tr(0, .03f * L, 0);
                taper(0, len * .5f, .055f * L, .004f * L, .1f * L, .004f * L, layer == 1 ? petal2 : petal, 255);
                taper(len * .5f, len, .1f * L, .004f * L, .005f * L, .002f * L, layer == 1 ? petal2 : petal, 255);
                pop();
            }
        }
        sphere(0, .02f * L, 0, .09f * L, .06f * L, .09f * L, 6, 10, heart, 255);
        for (int i = 0; i < 10; i++) { push(); rY(i * TAU / 10); rZ(.5f); frustum(.05f * L, .22f * L, .004f * L, .004f * L, 4, 1, 1, heart, 255, false); tr(0, .22f * L, 0); sphere(0, 0, 0, .014f * L, .014f * L, .014f * L, 3, 4, heart, 255); pop(); }
        pop();
    }
    private void towerModel(float H, float W, int stone, int trim, int glow) {
        frustum(0, .1f * H, .66f * W, .52f * W, 20, 1, 1, trim, 255, true);
        frustum(.1f * H, .52f * H, .5f * W, .4f * W, 20, 1, 1, stone, 255, false);
        frustum(.52f * H, .545f * H, .44f * W, .44f * W, 20, 1, 1, trim, 255, true);
        frustum(.545f * H, .82f * H, .4f * W, .32f * W, 20, 1, 1, stone, 255, false);
        frustum(.82f * H, .86f * H, .45f * W, .45f * W, 20, 1, 1, trim, 255, true);
        for (int i = 0; i < 14; i++) { float a = i * TAU / 14; box((float) Math.cos(a) * .44f * W, .89f * H, (float) Math.sin(a) * .44f * W, .05f * W, .035f * H, .05f * W, trim, 255); }
        frustum(.86f * H, .95f * H, .28f * W, .28f * W, 14, 1, 1, mix(stone, glow, .35f), 255, false);
        frustum(.95f * H, 1.1f * H, .32f * W, 0, 14, 1, 1, trim, 255, false);
        for (int k = 0; k < 6; k++) { float yy = (.2f + k * .11f) * H; float rr = (.5f - (yy / H - .1f) * .24f) * W; for (int i = 0; i < 4; i++) {
            float a = i * TAU / 4 + k * 1.1f; push(); tr((float) Math.cos(a) * rr * 1.005f, yy, (float) Math.sin(a) * rr * 1.005f); rY(-a); box(0, 0, 0, .004f * W, .04f * H, .045f * W, glow, 255); pop(); } }
    }
    private void doorModel(float W, float Hh, int frame, int leaf, int knob, float open) {
        box(-.58f * W, Hh / 2, 0, .09f * W, Hh / 2, .12f * W, frame, 255); box(.58f * W, Hh / 2, 0, .09f * W, Hh / 2, .12f * W, frame, 255);
        torus(0, Hh, 0, .58f * W, .5f * W, .09f * W, 14, 5, 0, PI, frame, 255);
        box(0, 0, 0, .7f * W, .05f * W, .16f * W, mix(frame, 0x000000, .2f), 255);
        push(); tr(-.5f * W, 0, 0); rY(open);
        box(.5f * W, Hh / 2, 0, .5f * W, Hh / 2, .035f * W, leaf, 255);
        box(.5f * W, Hh * .72f, 0, .36f * W, Hh * .2f, .045f * W, mix(leaf, 0xFFFFFF, .12f), 255);
        box(.5f * W, Hh * .28f, 0, .36f * W, Hh * .2f, .045f * W, mix(leaf, 0xFFFFFF, .12f), 255);
        sphere(.88f * W, Hh * .5f, .06f * W, .05f * W, .05f * W, .05f * W, 4, 6, knob, 255);
        pop();
    }
    private void wheelModel(float r, int rim, int spoke, int hub) {
        torus(0, 0, 0, r, r, .07f * r, 28, 6, 0, TAU, rim, 255);
        torus(0, 0, 0, r * .62f, r * .62f, .035f * r, 20, 4, 0, TAU, spoke, 255);
        for (int i = 0; i < 8; i++) { push(); rZ(i * TAU / 8); box(0, r * .5f, 0, .03f * r, r * .5f, .035f * r, spoke, 255); pop(); }
        for (int i = 0; i < 8; i++) { float a = i * TAU / 8; sphere((float) Math.cos(a) * r, (float) Math.sin(a) * r, 0, .08f * r, .08f * r, .08f * r, 4, 6, hub, 255); }
        sphere(0, 0, 0, .14f * r, .14f * r, .14f * r, 6, 10, hub, 255);
    }
    private void lanternModel(float L, int frame, int glass) {
        frustum(0, .06f * L, .22f * L, .24f * L, 4, 1, 1, frame, 255, true);
        solid(true); frustum(.06f * L, .6f * L, .24f * L, .3f * L, 4, 1, 1, glass, 140, false); solid(false);
        for (int i = 0; i < 4; i++) { float a = i * TAU / 4 + PI / 4; push(); tr((float) Math.cos(a) * .3f * L * 1.0f, 0, (float) Math.sin(a) * .3f * L); frustum(0, .6f * L, .02f * L, .02f * L, 5, 1, 1, frame, 255, false); pop(); }
        frustum(.6f * L, .78f * L, .34f * L, .06f * L, 4, 1, 1, frame, 255, true);
        push(); tr(0, .86f * L, 0); torus(0, 0, 0, .08f * L, .08f * L, .015f * L, 8, 4, 0, TAU, frame, 255); pop();
        sphere(0, .3f * L, 0, .09f * L, .12f * L, .09f * L, 4, 8, mix(glass, 0xFFFFFF, .7f), 255);
    }
    private void armillary(float r, int rgb, int core) {
        for (int i = 0; i < 3; i++) { push(); rY(t * (.5f + i * .2f) + i * 1.0f); rX(i * 1.05f + t * .3f); ring(r * (1f - i * .12f), r * .03f, i == 1 ? mix(rgb, 0xFFFFFF, .4f) : rgb, 255); pop(); }
        sphere(0, 0, 0, r * .18f, r * .18f, r * .18f, 6, 10, core, 255);
    }
    private void shardModel(float L, int rgb) {
        frustum(0, L, .16f * L, .16f * L, 6, 1, .65f, rgb, 255, false); frustum(L, 1.45f * L, .16f * L, 0, 6, 1, .65f, mix(rgb, 0xFFFFFF, .3f), 255, false);
        frustum(0, -.35f * L, .16f * L, 0, 6, 1, .65f, mix(rgb, 0x000000, .2f), 255, false);
    }
    private void throneModel(float s, int dark, int trim) {
        for (int i = 0; i < 3; i++) frustum(i * .22f * s, (i + 1) * .22f * s, (1.5f - i * .22f) * s, (1.5f - (i + 1) * .22f) * s, 6, 1, 1, mix(dark, trim, .15f * i), 255, true);
        box(0, .95f * s, 0, .55f * s, .16f * s, .5f * s, dark, 255);
        taper(.85f * s, 3.2f * s, .6f * s, .1f * s, .42f * s, .08f * s, dark, 255);
        taper(3.2f * s, 3.8f * s, .42f * s, .08f * s, 0, 0, dark, 255);
        for (int i = -2; i <= 2; i++) { push(); tr(i * .27f * s, 3.0f * s - Math.abs(i) * .12f * s, 0); rZ(-i * .18f); taper(0, (.9f - Math.abs(i) * .12f) * s, .07f * s, .05f * s, 0, 0, dark, 255); pop(); }
        for (int sd = -1; sd <= 1; sd += 2) { box(sd * .62f * s, 1.25f * s, 0, .09f * s, .3f * s, .46f * s, dark, 255); box(sd * .62f * s, 1.58f * s, .3f * s, .1f * s, .03f * s, .22f * s, trim, 255); }
        box(0, 1.2f * s, .46f * s, .3f * s, .02f * s, .02f * s, trim, 255);
    }
    private void harp(float L, int wood, int string) {
        frustum(0, L, .05f * L, .035f * L, 8, 1, 1, wood, 255, true);
        torus(.32f * L, L, 0, .32f * L, .3f * L, .04f * L, 12, 5, PI / 2, PI, wood, 255);
        box(.16f * L, .08f * L, 0, .18f * L, .05f * L, .04f * L, wood, 255);
        for (int i = 0; i < 9; i++) { float x = (.06f + i * .05f) * L; push(); tr(x, 0, 0); frustum(.1f * L, (.95f - i * .012f) * L, .006f * L, .006f * L, 4, 1, 1, string, 255, false); pop(); }
    }

    // ================= render =================
    private float face;
    private int symTint() { return gold ? 0xFFDD66 : 0xFFFFFF; }
    private String symTex() { return "sym_" + Tarot.PATHWAYS[curPath]; }

    @Override
    public void render(HoloCardEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource bufs, int light) {
        super.render(e, yaw, pt, ps, bufs, light);
        int seq = e.seq(), pi = e.pathIndex();
        boolean shiny = e.shiny();
        this.buf = bufs; this.mat = ps.last().pose();
        this.t = (e.tickCount + pt) / 20f;
        clear(); strans = false;
        drawCard(e, bufs, light, pi, seq, shiny);
        if (seq >= 6) return;

        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        rx = -left.x; ry = -left.y; rz = -left.z; ux = up.x; uy = up.y; uz = up.z;
        fyaw = (float) Math.toRadians(e.getYRot());
        face = PI - fyaw; fx = -(float) Math.sin(fyaw); fz = (float) Math.cos(fyaw);
        sx0 = (float) Math.cos(face); sz0 = -(float) Math.sin(face);
        curPath = Math.min(pi, PAL.length - 1); gold = shiny;
        int[] pal = PAL[curPath];
        P1 = gold ? 0xFFD040 : pal[0]; P2 = gold ? 0xB87810 : pal[1]; P3 = gold ? 0xFFF4B0 : pal[2];
        Cfg c = CFG[curPath];
        if (seq >= 4) rare(c, seq);
        else if (seq >= 2) epic(c, seq);
        else { boolean m = seq == 0; dims(m); scene(curPath, m); if (m) mythExtra(); }
    }

    private void drawCard(HoloCardEntity e, MultiBufferSource bufs, int light, int pi, int seq, boolean shiny) {
        String name = "textures/item/card_" + Tarot.PATHWAYS[pi] + "_" + seq + (shiny ? "_shiny" : "") + ".png";
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("astral_world", name);
        VertexConsumer vc = bufs.getBuffer(RenderType.entityCutoutNoCull(rl));
        float yawR = (float) Math.toRadians(e.getYRot());
        float ffx = -(float) Math.sin(yawR), ffz = (float) Math.cos(yawR);
        float ssx = -ffz, ssz = ffx;
        float hw = .3f, hl = .45f, y = .03f;
        float[][] p = {{-hw, -hl}, {hw, -hl}, {hw, hl}, {-hw, hl}};
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int k = 0; k < 4; k++)
            vc.addVertex(mat, ssx * p[k][0] + ffx * p[k][1], y, ssz * p[k][0] + ffz * p[k][1]).setColor(255, 255, 255, 255)
              .setUv(uv[k][0], uv[k][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0);
    }

    // ================= Rare and Epic =================
    private void rare(Cfg c, int seq) {
        float pulse = .8f + .2f * (float) Math.sin(t * 3);
        flat("ring", 0, .05f, 0, 1.0f, t * .5f, rgba(P2, .9f));
        column("beam", 0, .05f, 1.5f, 0, .35f, rgba(P1, .4f));
        float bob = (float) Math.sin(t * 2) * .06f;
        stand(c.core, 0, 1.1f + bob, 0, seq == 4 ? .6f : .46f, seq == 4 ? .6f : .46f, t * 1.8f, rgba(mix(P1, 0xFFFFFF, .4f), pulse));
        int motes = seq == 4 ? 5 : 3;
        for (int i = 0; i < motes; i++) {
            float a = t * 1.1f + i * TAU / motes;
            bb("mote", (float) Math.cos(a) * .5f, .6f + .5f * fract(t * .4f + i * .31f), (float) Math.sin(a) * .5f, .16f, .16f, 0, rgba(P1, 1f));
        }
    }

    private void sym(float y, float size, float spin) {
        stand2(symTex(), 0, y, 0, size, size, t * spin, rgba(symTint(), 1f));
    }

    private void epic(Cfg c, int seq) {
        boolean two = seq == 2;
        float tp2 = two ? 3.8f : 3.2f;
        column("beam", 0, 0.05f, tp2, 0, two ? .8f : .6f, rgba(P1, .6f));
        column("beam", 0, 0.05f, tp2, 0, .22f, rgba(P3, .6f));
        flat("ring_dash", 0, .05f, 0, 2.4f, -t * .4f, rgba(P1, .9f));
        flat("ring", 0, .06f, 0, 1.3f, t * .6f, rgba(P2, .9f));
        flat("ring_thick", 0, .07f, 0, 3f, t * .15f, rgba(P2, .6f));
        float cy = 1.8f + (float) Math.sin(t * 1.6) * .08f;
        stand2(symTex(), 0, cy, 0, 1.5f, 1.5f, t * 1.0f, rgba(symTint(), 1f));
        int med = two ? 8 : 6;
        for (int i = 0; i < med; i++) {
            float a = -t * .5f + i * TAU / med, r = 1.4f, y = .9f + (float) Math.sin(t * 2 + i) * .08f;
            stand2(symTex(), (float) Math.cos(a) * r, y, (float) Math.sin(a) * r, .5f, .5f, t * 2 + i, rgba(symTint(), .95f));
        }
        int props = two ? 4 : 2;
        for (int i = 0; i < props; i++) {
            float a = t * .9f + i * TAU / props;
            bb(c.prop, (float) Math.cos(a) * .9f, cy + (float) Math.sin(t * 1.5f + i) * .3f, (float) Math.sin(a) * .9f, .38f, .38f, a, rgba(mix(P1, P3, .3f), 1f));
        }
        for (int i = 0; i < 12; i++) {
            float p = fract(t * .3f + h(i, 1));
            bb("mote", (h(i, 2) - .5f) * 2f, p * tp2, (h(i, 3) - .5f) * 2f, .14f, .14f, 0, rgba(P1, fd(p)));
        }
    }

    // ================= shared scene pieces =================
    private void dims(boolean m) {
        R = m ? 11f : 6f; top = m ? 16f : 8.5f; S = m ? 2.1f : 1.15f;
        CY = (m ? 5.5f : 3.2f) + (float) Math.sin(t * 1.2) * .12f * S;
    }
    private static final int FALLM = 0, RISEM = 1, SPIRALM = 2, ORBITM = 3, RADIALM = 4, DRIFTM = 5;
    private void sprites(String tx, int n, int mode, float rad, float y0, float y1, float size, int rgb, float alpha, float speed, int seed, boolean spin) {
        for (int i = 0; i < n; i++) {
            float p = fract(t * speed * (.7f + .6f * h(i, seed + 3)) + h(i, seed + 1));
            float ang = h(i, seed + 2) * TAU, rr = (float) Math.sqrt(h(i, seed + 4)) * rad;
            float fade = fd(p), sz = size * (.6f + .8f * h(i, seed + 8));
            float x, y, z;
            switch (mode) {
                case FALLM -> { x = (float) Math.cos(ang) * rr; z = (float) Math.sin(ang) * rr; y = y1 - p * (y1 - y0); }
                case RISEM -> { x = (float) Math.cos(ang) * rr + (float) Math.sin(t + i) * .25f; z = (float) Math.sin(ang) * rr; y = y0 + p * (y1 - y0); }
                case SPIRALM -> { float a = ang + p * 8f, r2 = rad * (1.1f - p * .8f) * (.5f + .5f * h(i, seed + 4)); x = (float) Math.cos(a) * r2; z = (float) Math.sin(a) * r2; y = y0 + p * (y1 - y0); }
                case ORBITM -> { float a = t * (.4f + .5f * h(i, seed + 5)) + ang; x = (float) Math.cos(a) * rr; z = (float) Math.sin(a) * rr; y = y0 + h(i, seed + 6) * (y1 - y0); fade = .8f + .2f * fade; }
                case RADIALM -> { float el = (h(i, seed + 5) - .5f) * 2.6f, d = p * rad; x = (float) Math.cos(ang) * d * (float) Math.cos(el); z = (float) Math.sin(ang) * d * (float) Math.cos(el); y = y0 + (float) Math.sin(el) * d; }
                default -> { x = (float) Math.cos(ang) * rr + (float) Math.sin(t * .5f + i) * 1.2f; z = (float) Math.sin(ang) * rr + (float) Math.cos(t * .4f + i) * 1.2f; y = y0 + h(i, seed + 6) * (y1 - y0); fade = (.55f + .45f * (float) Math.sin(t + i * 1.3f)); }
            }
            bb(tx, x, y, z, sz, sz, spin ? t * (1 + h(i, seed + 7)) + i : 0, rgba(rgb, Math.max(0, fade) * alpha));
        }
    }
    private void clouds(int n, float rad, float y, int rgb, float alpha, float size) {
        for (int i = 0; i < n; i++) {
            float a = t * .04f + i * TAU / n, rr = rad * (.3f + .5f * h(i, 11));
            bb("cloud", (float) Math.cos(a) * rr, y + h(i, 12) * 1.5f, (float) Math.sin(a) * rr, size * (.9f + h(i, 13) * .6f), size * .55f, 0, rgba(rgb, alpha));
        }
    }
    private void sigil(float rad) {
        flat("ring_thick", 0, .06f, 0, 2f * rad, t * .12f, rgba(P2, .9f));
        flat("ring_dash", 0, .08f, 0, 1.5f * rad, -t * .3f, rgba(P1, 1f));
        flat("ring", 0, .09f, 0, 1.0f * rad, t * .5f, rgba(mix(P1, P2, .5f), .8f));
        float pw = fract(t * .3f);
        flat("ring", 0, .12f, 0, pw * 2.2f * rad, 0, rgba(P1, (1 - pw) * .8f));
    }
    private void rays(float y, float size, int rgb, float a, float spd) { stand2("rays", 0, y, 0, size, size, t * spd, rgba(rgb, a)); }
    private void mythExtra() {
        flat("ring_thick", 0, top - .5f, 0, 5f * S, -t * .2f, rgba(P1, .6f));
        float wv = fract(t * .35f);
        flat("ring", 0, .1f, 0, wv * 9f * S, 0, rgba(P1, (1 - wv) * .8f));
    }
    private void strikes(int n, float rad, float y, int rgb) {
        for (int i = 0; i < n; i++) {
            float period = 1.2f + .27f * i, q = t / period + h(i, 19), ph = fract(q);
            float v = ph < .07f ? 1f : ph < .12f ? .25f : ph < .17f ? .9f : ph < .22f ? .4f * (1 - (ph - .17f) / .05f) : 0f;
            if (v <= 0) continue;
            int k = (int) Math.floor(q);
            float an = h(k * 7 + i, 21) * TAU, rr = (.1f + .75f * h(k * 5 + i, 22)) * rad;
            float x = (float) Math.cos(an) * rr, z = (float) Math.sin(an) * rr, bw = y * .16f * ((k & 1) == 0 ? -1 : 1);
            String b = i % 2 == 0 ? "bolt" : "bolt2";
            column(b, x, .05f, y + .5f, z, bw, rgba(0xFFFFFF, v));
            column(b, x, .05f, y + .5f, z, bw * 1.8f, rgba(rgb, v * .5f));
            flat("ring", x, .14f, z, (1f + (1 - v) * 3f) * S, 0, rgba(rgb, v));
        }
    }
    /** Three layered fixed flames (outer, middle, hot core) that flicker. */
    private void flameAt(float x, float y, float z, float size, int seed, int outer, int mid, int core, float alpha) {
        float fl = .92f + .12f * (float) Math.sin(t * 9 + seed * 5), wob = (float) Math.sin(t * 5 + seed) * .06f * size;
        stand2("flame", x + wob, y + size * .5f * fl, z, size * .75f, size * fl, t * .5f + seed, rgba(outer, alpha * .9f));
        stand2("flame", x + wob * 1.3f, y + size * .42f * fl, z, size * .55f, size * .8f * fl, t * .6f + seed + 1, rgba(mid, alpha));
        stand2("flame", x + wob * 1.6f, y + size * .3f * fl, z, size * .34f, size * .55f * fl, t * .7f + seed + 2, rgba(core, alpha));
    }
    private void dome(float rad, int rgb, int alpha) {
        solid(true);
        int lat = 8, lon = 22; float[] hint = v3(0, 0, 0);
        for (int i = 0; i < lat; i++) {
            float l0 = PI / 2 * i / lat, l1 = PI / 2 * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float g0 = TAU * j / lon, g1 = TAU * (j + 1) / lon;
                sq(sp(0, 0, 0, rad, rad, rad, l0, g0), sp(0, 0, 0, rad, rad, rad, l0, g1), sp(0, 0, 0, rad, rad, rad, l1, g1), sp(0, 0, 0, rad, rad, rad, l1, g0), hint, rgb, alpha);
            }
        }
        solid(false);
    }
    private void pushFace() { push(); rY(face); }
    /** Point the model's +Y axis along (dx, dy, dz). */
    private void aim(float dx, float dy, float dz) {
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz); if (l < 1e-6f) return;
        rY((float) Math.atan2(dx, dz)); rX((float) Math.acos(Math.max(-1, Math.min(1, dy / l))));
    }
    private void glint(float x, float y, float z, float size, int rgb, float a) { bb("spark", x, y, z, size, size, t, rgba(rgb, a)); }
    private void fogRing(int n, float rad, int rgb, float a) {
        for (int i = 0; i < n; i++) { float ang = t * .06f + i * TAU / n; bb("fog", (float) Math.cos(ang) * rad, .8f * S, (float) Math.sin(ang) * rad, 5 * S, 2f * S, 0, rgba(rgb, a)); }
    }

    // ================= the 22 scenes =================
    private void scene(int p, boolean m) {
        int N = m ? 2 : 1;
        switch (p) {
            case 0 -> moon(N); case 1 -> door(N); case 2 -> justiciar(N); case 3 -> demoness(N, m); case 4 -> sun(N);
            case 5 -> death(N); case 6 -> abyss(N, m); case 7 -> redPriest(N); case 8 -> blackEmperor(N, m); case 9 -> error(N);
            case 10 -> wheel(N); case 11 -> hermit(N); case 12 -> mother(N); case 13 -> chained(N, m); case 14 -> whiteTower(N);
            case 15 -> sailor(N, m); case 16 -> paragon(N); case 17 -> hangedMan(m); case 18 -> fool(N); case 19 -> darkness(N, m);
            case 20 -> visionary(N); default -> twilight(N);
        }
    }

    private void moon(int N) {
        sigil(R); float my = top * .85f;
        stand2("crescent", 0, my, 0, 10 * S, 10 * S, t * .15f, rgba(P3, .55f));
        stand2("crescent", 0, my, 0, 8 * S, 8 * S, t * .15f, rgba(mix(P1, 0xFFFFFF, .55f), 1f));
        column("beam", 0, 0, my - 3 * S, 0, 2.4f * S, rgba(P1, .4f));
        sym(CY, 3.4f * S, .6f);
        for (int i = 0; i < 5 * N; i++) {
            float a = t * .5f + i * TAU / (5 * N), r = R * .55f, y = CY - .6f * S + (float) Math.sin(a * 2 + i) * 1.2f * S;
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(t + i); rZ((float) Math.sin(t + i) * .3f);
            flask(.5f * S, mix(P3, 0xFFFFFF, .3f), i % 2 == 0 ? P1 : mix(P1, P2, .5f)); pop();
            glint((float) Math.cos(a) * r, y, (float) Math.sin(a) * r, 1.2f * S, P3, .8f);
        }
        sprites("petal", 40 * N, SPIRALM, R * .8f, 0, top * .8f, .7f * S, P1, 1f, .14f, 40, true);
        sprites("leaf", 24 * N, FALLM, R, 0, top, .8f * S, P2, 1f, .12f, 20, true);
        sprites("star", 36 * N, DRIFTM, R * 1.2f, 2, top, .5f * S, 0xFFFFFF, 1f, .3f, 4, true);
        clouds(6 * N, R, top * .55f, P2, .3f, R);
    }

    private void door(int N) {
        sigil(R); float W = 3.2f * S, H = 5.4f * S;
        pushFace(); doorModel(W, H, mix(P2, 0x182030, .3f), mix(P2, 0x0A1428, .2f), P3, -1.15f); pop();
        // light pouring out of the doorway
        facing("solid", 0, H * .5f, 0, W * 1.0f, H * .96f, 0, rgba(mix(P1, 0xFFFFFF, .7f), .55f));
        facing("rays", 0, H * .5f, 0, 14 * S, 14 * S, t * .08f, rgba(P1, .65f));
        column("beam", 0, 0, H * 1.2f, 0, W * .9f, rgba(P1, .35f));
        sym(H + 1.8f * S, 3f * S, .7f);
        for (int i = 0; i < 6 * N; i++) {
            float a = t * .6f + i * TAU / (6 * N), r = R * .7f, y = H * .5f + (float) Math.sin(a * 2 + i) * 1.4f * S;
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(-a); rX(t * 2 + i); tr(0, -.6f * S, 0); key(1.3f * S, metal(.6f)); pop();
            glint((float) Math.cos(a) * r, y, (float) Math.sin(a) * r, 1.3f * S, P3, .8f);
        }
        sprites("spark", 70 * N, RADIALM, R * 1.3f, H * .5f, 0, .5f * S, P3, 1f, .3f, 5, true);
        sprites("mote", 60 * N, RISEM, R * .7f, 0, top, .35f * S, P1, 1f, .25f, 6, false);
    }

    private void justiciar(int N) {
        sigil(R);
        for (int i = 0; i < 8 * N; i++) { float a = i * TAU / (8 * N); column("beam", (float) Math.cos(a) * R * .8f, 0, top * .85f, (float) Math.sin(a) * R * .8f, 1.3f * S, rgba(P1, .35f)); column("line", (float) Math.cos(a) * R * .8f, 0, top * .9f, (float) Math.sin(a) * R * .8f, .5f * S, rgba(0xFFFFFF, .7f)); }
        // giant scales
        float L = 4.4f * S, tilt = (float) Math.sin(t * .6f) * .12f;
        pushFace(); tr(0, CY - L * .5f, 0);
        frustum(0, L, .04f * L, .028f * L, 10, 1, 1, metal(.5f), 255, true); frustum(0, .07f * L, .2f * L, .15f * L, 12, 1, 1, metal(.3f), 255, true);
        sphere(0, L, 0, .045f * L, .045f * L, .045f * L, 5, 8, P3, 255);
        push(); tr(0, L, 0); rZ(tilt); box(0, 0, 0, .56f * L, .018f * L, .02f * L, metal(.6f), 255);
        for (int s = -1; s <= 1; s += 2) { push(); tr(s * .54f * L, 0, 0); rZ(-tilt);
            for (int k = 0; k < 3; k++) { push(); rY(k * TAU / 3); frustum(-.46f * L, 0, .006f * L, .006f * L, 4, 1, 1, metal(.4f), 255, false); pop(); }
            tr(0, -.48f * L, 0); frustum(-.06f * L, 0, .09f * L, .24f * L, 14, 1, 1, metal(.55f), 255, true); pop(); }
        pop(); pop();
        sym(CY + L * .62f, 2.6f * S, .5f);
        for (int i = 0; i < 8 * N; i++) { float a = -t * .1f + i * TAU / (8 * N), r = R * (.35f + .3f * (i % 2)); float y = top * .55f + (float) Math.sin(t * 1.2f + i) * .5f;
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rZ(PI); rY(a); sword(3.6f * S, metal(.8f), metal(.45f), P3); pop();
            column("line", (float) Math.cos(a) * r, y - 3.6f * S, y, (float) Math.sin(a) * r, .3f * S, rgba(0xFFFFFF, .3f)); }
        sprites("line", 90 * N, FALLM, R, 0, top, .4f * S, 0xFFFFFF, .7f, .9f, 6, false);
    }

    private void demoness(int N, boolean m) {
        sigil(R);
        flat("star", 0, .1f, 0, R * 1.2f, t * .2f, rgba(P1, .8f));
        stand2("disc", 0, top * .72f, 0, 9 * S, 9 * S, t * .06f, rgba(mix(P2, 0x300008, .2f), .95f));
        stand2("corona", 0, top * .72f, 0, 14 * S, 14 * S, t * .1f, rgba(P2, .6f));
        for (int s = -1; s <= 1; s += 2) {
            facing("wing", s * sx0 * 3.4f * S, CY + .6f * S, s * sz0 * 3.4f * S, s * 7f * S, 6.4f * S, 0, rgba(P1, .85f));
            facing("wing", s * sx0 * 5.2f * S, CY - .7f * S, s * sz0 * 5.2f * S, s * 4.6f * S, 4.4f * S, 0, rgba(P2, .8f));
        }
        sym(CY, 3.2f * S, .7f);
        for (int ring = 0; ring < N; ring++) for (int i = 0; i < 8; i++) {
            float a = (ring == 0 ? t * .7f : -t * .5f) + i * TAU / 8, r = R * (ring == 0 ? .5f : .75f), y = CY + (float) Math.sin(a * 2) * 1.2f * S;
            float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r;
            push(); tr(x, y, z); aim(-x, -(y - CY) * .6f, -z); dagger(2.6f * S, metal(.7f), mix(P2, 0x200010, .3f), P1); pop();
        }
        push(); tr(0, CY + 6.2f * S, 0); rZ(PI); dagger(5.6f * S, metal(.85f), mix(P2, 0x200010, .3f), P1); pop();
        column("beam", 0, CY + .8f * S, CY + 3.6f * S, 0, .8f * S, rgba(P1, .6f));
        sprites("petal", 120 * N, FALLM, R, 0, top, .7f * S, P1, 1f, .16f, 7, true);
        fogRing(10, R * .6f, P2, .4f);
        if (m) strikes(2, R, top, P1);
    }

    private void sun(int N) {
        sigil(R); float sy = top * .66f;
        rays(sy, 22 * S, P1, .65f, .2f); rays(sy, 15 * S, P2, .6f, -.3f);
        stand2("corona", 0, sy, 0, 13 * S, 13 * S, t * .3f, rgba(P1, .95f));
        stand2("disc", 0, sy, 0, 6 * S, 6 * S, 0, rgba(0xFFFFFF, 1f));
        column("beam", 0, .05f, sy, 0, 2.6f * S, rgba(P1, .5f));
        pushFace(); tr(0, CY - 2.2f * S, 0); harp(4.4f * S, mix(P2, 0x402010, .3f), P3); pop();
        for (int s = -1; s <= 1; s += 2) facing("wing", s * sx0 * 3.2f * S, CY, s * sz0 * 3.2f * S, s * 5.2f * S, 4.8f * S, 0, rgba(0xFFFFFF, .9f));
        sym(CY + 3.2f * S, 2.6f * S, .6f);
        sprites("note", 50 * N, RADIALM, R * 1.1f, CY, 0, 1.1f * S, P1, 1f, .22f, 9, true);
        sprites("feather", 40 * N, FALLM, R, 0, top, .9f * S, 0xFFFFFF, 1f, .12f, 10, true);
        sprites("spark", 80 * N, RISEM, R, 0, top, .35f * S, 0xFFFFFF, 1f, .3f, 11, true);
    }

    private void death(int N) {
        sigil(R);
        pushFace(); tr(0, CY, 0); rY((float) Math.sin(t * .4f) * .4f); skull(2.3f * S, mix(P1, 0xFFFFFF, .5f), P1); pop();
        sym(CY + 3.8f * S, 2.6f * S, .5f);
        stand2("rays", 0, CY, 0, 12 * S, 12 * S, t * .1f, rgba(P1, .35f));
        for (int i = 0; i < 8 * N; i++) { float a = i * TAU / (8 * N) + .2f, rr = R * .9f;
            push(); tr((float) Math.cos(a) * rr, 0, (float) Math.sin(a) * rr); rY(PI - a + PI / 2);
            box(0, 1.1f * S, 0, .7f * S, 1.1f * S, .18f * S, 0x707878, 255); sphere(0, 2.2f * S, 0, .7f * S, .55f * S, .18f * S, 5, 10, 0x707878, 255);
            box(0, 1.6f * S, .19f * S, .08f * S, .45f * S, .01f * S, P1, 255); box(0, 1.8f * S, .19f * S, .3f * S, .07f * S, .01f * S, P1, 255); pop(); }
        for (int i = 0; i < 10 * N; i++) { float a = -t * .15f + i * TAU / (10 * N), rr = R * .65f; flameAt((float) Math.cos(a) * rr, 0, (float) Math.sin(a) * rr, 3f * S, i, P2, P1, P3, .9f); }
        sprites("mote", 80 * N, RISEM, R, 0, top, .45f * S, P1, 1f, .25f, 14, false);
        sprites("smoke", 30 * N, RISEM, R, 0, top * .8f, 2.4f * S, P2, .5f, .12f, 13, true);
        fogRing(10, R * .6f, P1, .3f);
    }

    private void abyss(int N, boolean m) {
        flat("whirl", 0, .06f, 0, 2.2f * R, -t * .5f, rgba(P1, .9f)); flat("whirl", 0, .08f, 0, 1.4f * R, t * .8f, rgba(P2, .9f));
        sigil(R);
        for (int i = 0; i < 8; i++) { float pw = fract(t * .25f + i / 8f); flat("ring", 0, .1f + i * .02f, 0, (1 - pw) * 2.2f * R, 0, rgba(P1, fd(pw))); }
        float vr = 2.2f * S;
        flat("whirl", 0, CY, 0, 11 * S, t * 1.2f, rgba(P1, .95f)); flat("whirl", 0, CY, 0, 7 * S, -t * 1.8f, rgba(mix(P1, 0xFFFFFF, .4f), .9f));
        stand2("ring_thick", 0, CY, 0, 8 * S, 8 * S, t * .5f, rgba(P1, .8f));
        sphere(0, CY, 0, vr, vr, vr, 10, 16, 0x050205, 255);
        sym(CY + 4.4f * S, 2.8f * S, .5f);
        for (int i = 0; i < 6; i++) { float a = i * TAU / 6 + t * .1f, r = 3.5f * S;
            push(); tr((float) Math.cos(a) * r, CY - 1f * S, (float) Math.sin(a) * r); rY(-a + PI / 2); rZ(-.2f); horn(5f * S, .5f * S, 1.9f, mix(P2, 0x120505, .45f)); pop(); }
        for (int i = 0; i < 12 * N; i++) { float a = i * TAU / (12 * N) + t * .08f, r = R * .85f;
            column("crack", (float) Math.cos(a) * r, 0, top * (.5f + .4f * h(i, 3)), (float) Math.sin(a) * r, 1.5f * S, rgba(P1, .9f)); }
        sprites("mote", 90 * N, SPIRALM, R, 0, top, .4f * S, P1, 1f, .2f, 16, false);
        sprites("smoke", 24 * N, DRIFTM, R, .5f, 6f, 4f * S, P2, .5f, .1f, 15, true);
        strikes(2 * N, R, top, P1);
        if (m) dome(R * 1.5f, 0x020002, 190);
    }
    /** A curved horn starting along +Y. */
    private void horn(float len, float r0, float bend, int rgb) {
        int segs = 8; float seg = len / segs;
        push();
        for (int i = 0; i < segs; i++) {
            float ra = r0 * (1f - i / (float) segs), rb = r0 * (1f - (i + 1) / (float) segs);
            frustum(0, seg, ra, rb, 8, 1, 1, rgb, 255, i == segs - 1); tr(0, seg, 0); rZ(-bend / segs);
        }
        pop();
    }

    private void redPriest(int N) {
        sigil(R);
        rays(CY, 14 * S, P1, .5f, .15f);
        pushFace(); tr(0, 0, 0);
        frustum(0, 1.4f * S, .5f * S, .35f * S, 12, 1, 1, mix(P2, 0x201008, .5f), 255, true); frustum(1.4f * S, 2f * S, .35f * S, 1.2f * S, 14, 1, 1, mix(P2, 0x201008, .5f), 255, true);
        for (int i = 0; i < 3; i++) { push(); rY(i * TAU / 3); rZ(.25f); frustum(0, 1.5f * S, .12f * S, .08f * S, 6, 1, 1, mix(P2, 0x201008, .5f), 255, false); pop(); }
        for (int s = -1; s <= 1; s += 2) { push(); tr(0, 6.3f * S, -2.6f * S); rZ(s * .55f); rX(0); sword(10.5f * S, metal(.8f), mix(P2, 0x302010, .4f), P1); pop(); }
        pop();
        flameAt(0, 1.8f * S, 0, 7f * S, 1, P2, P1, P3, 1f); flameAt(.8f * S, 1.8f * S, .4f * S, 5f * S, 2, P2, P1, P3, 1f); flameAt(-.7f * S, 1.8f * S, -.3f * S, 5.5f * S, 3, P2, P1, P3, 1f);
        sym(CY + 5f * S, 2.8f * S, .5f);
        for (int i = 0; i < 12 * N; i++) { float a = i * TAU / (12 * N), rr = R * .78f;
            push(); tr((float) Math.cos(a) * rr, 0, (float) Math.sin(a) * rr); frustum(0, 3.2f * S, .12f * S, .1f * S, 6, 1, 1, mix(P2, 0x201008, .5f), 255, true); frustum(3.2f * S, 3.5f * S, .1f * S, .26f * S, 8, 1, 1, mix(P2, 0x201008, .5f), 255, true); pop();
            flameAt((float) Math.cos(a) * rr, 3.5f * S, (float) Math.sin(a) * rr, 2.2f * S, i + 5, P2, P1, P3, .95f); }
        sprites("spark", 120 * N, RISEM, R, 0, top * 1.1f, .3f * S, P3, 1f, .5f, 18, true);
        sprites("smoke", 20 * N, RISEM, R * .5f, top * .3f, top, 3f * S, mix(P2, 0x000000, .6f), .5f, .1f, 19, true);
    }

    private void blackEmperor(int N, boolean m) {
        dome(R * 1.7f, 0x000000, m ? 215 : 140);
        sigil(R);
        for (int i = 0; i < 6 * N; i++) { float a = i * TAU / (6 * N) + .3f; column("beam", (float) Math.cos(a) * R * .5f, 0, top, (float) Math.sin(a) * R * .5f, 1.6f * S, rgba(P1, .35f)); }
        pushFace(); throneModel(1.3f * S, mix(P3, 0x000000, .2f), metal(.6f)); pop();
        pushFace(); tr(0, CY + 4.2f * S, 0); rY(t * .4f); crown(1.7f * S, metal(.7f), P1); pop();
        stand2("rays", 0, CY + 4.5f * S, 0, 9 * S, 9 * S, t * .15f, rgba(P1, .5f));
        sym(CY + 7f * S, 2.6f * S, .5f);
        for (int i = 0; i < 6 * N; i++) { float a = i * TAU / (6 * N) + .1f, r = R * .6f * (.7f + .3f * (i % 2)); push(); tr((float) Math.cos(a) * r, top + 1f, (float) Math.sin(a) * r); chain(top + 1f - CY * .7f, .8f * S, mix(P2, 0x000000, .45f)); pop(); }
        for (int i = 0; i < 14 * N; i++) { float a = i * TAU / (14 * N), r = R * .85f; push(); tr((float) Math.cos(a) * r, 0, (float) Math.sin(a) * r); taper(0, (3f + 3f * h(i, 5)) * S, .35f * S, .35f * S, 0, 0, mix(P3, 0x000000, .2f), 255); pop(); }
        sprites("spark", 70 * N, FALLM, R, 0, top, .45f * S, 0xFFFFFF, 1f, .15f, 20, true);
        fogRing(8, R * .6f, P2, .3f);
    }

    private void error(int N) {
        sigil(R);
        int slices = 14; float size = 7f * S;
        for (int k = 0; k < slices; k++) {
            int tick = (int) (t * 7);
            boolean glitch = fract(t * .6f + h(k, 3)) > .55f;
            float dx = glitch ? (h(tick + k, 77) - .5f) * 1.6f * S : 0;
            float v0 = k / (float) slices, v1 = (k + 1) / (float) slices;
            float yc = CY + (.5f - (k + .5f) / slices) * size;
            quadUV(symTex(), sx0 * dx, yc, sz0 * dx, sx0 * size / 2, 0, sz0 * size / 2, 0, size / slices / 2, 0, rgba(symTint(), glitch ? .9f : 1f), 0, v0, 1, v1);
            if (glitch) quadUV(symTex(), sx0 * (dx * -1.6f), yc, sz0 * (dx * -1.6f), sx0 * size / 2, 0, sz0 * size / 2, 0, size / slices / 2, 0, rgba(P2, .6f), 0, v0, 1, v1);
        }
        for (int i = 0; i < 12 * N; i++) {
            float a = t * (i % 2 == 0 ? .4f : -.35f) + i * TAU / (12 * N), r = R * (.4f + .45f * h(i, 4)), y = 1f + h(i, 5) * top * .7f;
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(t * (.6f + h(i, 6))); rX(t * (.5f + h(i, 7)) + i); rZ(i); shardModel(1.3f * S * (.6f + h(i, 8)), metal(.5f + .4f * h(i, 9))); pop();
        }
        for (int k = 0; k < 14 * N; k++) { int seg = (int) Math.floor(t * 3.2f + k * .37f); float vis = fract(t * 3.2f + k * .37f);
            float a = h(seg + k, 41) * TAU, rr = h(seg + k, 42) * R, yy = h(seg + k, 43) * top * .8f;
            if (vis < .75f) bb("crack", (float) Math.cos(a) * rr, yy, (float) Math.sin(a) * rr, 2.8f * S, 2.8f * S, h(seg + k, 44) * TAU, rgba(k % 3 == 0 ? 0xFFFFFF : P1, 1f - vis * .6f)); }
        for (int k = 0; k < 9; k++) { float a = k * TAU / 9; if (fract(t * 2f + h(k, 5)) > .3f) column("line", (float) Math.cos(a) * R * .8f, 0, top * (.5f + .5f * h(k, 6)), (float) Math.sin(a) * R * .8f, .5f * S, rgba(P1, .7f)); }
        sprites("mote", 100 * N, DRIFTM, R, 0, top, .4f * S, 0xFFFFFF, 1f, .6f, 21, false);
        strikes(2 * N, R, top, P1);
    }

    private void wheel(int N) {
        sigil(R);
        stand2("wheel", 0, .1f, 0, 1.9f * R, 1.9f * R, 0, 0); flat("wheel", 0, .1f, 0, 1.9f * R, t * .3f, rgba(P2, .6f));
        float wy = top * .5f;
        rays(wy, 18 * S, P1, .55f, .15f);
        pushFace(); tr(0, wy, 0); rZ(t * .4f); wheelModel(5.6f * S, metal(.55f), metal(.35f), P3); pop();
        pushFace(); tr(0, wy, -.2f * S); rZ(-t * .6f); wheelModel(3f * S, mix(P2, 0xFFFFFF, .2f), metal(.4f), metal(.8f)); pop();
        sym(wy, 2.4f * S, .8f);
        for (int i = 0; i < 8 * N; i++) {
            float a = t * .5f + i * TAU / (8 * N), r = R * .65f, y = 1.6f * S + (float) Math.sin(a * 2 + i) * 1.2f * S;
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(t * 1.3f + i); rX(t * 1.7f + i); die(.9f * S); pop();
        }
        sprites("spark", 90 * N, RADIALM, R * 1.2f, wy, 0, .5f * S, 0xFFFFFF, 1f, .3f, 22, true);
        sprites("diamond", 24 * N, RISEM, R, 0, top, .6f * S, P2, 1f, .2f, 23, true);
    }
    private void die(float s) {
        box(0, 0, 0, s, s, s, metal(.7f), 255);
        float[][] face = {{0, 1.02f, 0}, {0, -1.02f, 0}, {1.02f, 0, 0}, {-1.02f, 0, 0}, {0, 0, 1.02f}, {0, 0, -1.02f}};
        for (float[] f : face) sphere(f[0] * s, f[1] * s, f[2] * s, .22f * s, .22f * s, .22f * s, 3, 6, P3, 255);
    }

    private void hermit(int N) {
        sigil(R);
        float ly = top * .78f;
        solid(true); push(); tr(0, 0, 0); frustum(0, ly - .6f * S, R * .55f, .25f * S, 18, 1, 1, P3, 38, false); pop(); solid(false);
        column("beam", 0, 0, ly, 0, 3f * S, rgba(P3, .4f));
        push(); tr(0, top + 2f, 0); chain(top + 2f - ly - 4.4f * S, .9f * S, metal(.3f)); pop();
        push(); tr(0, ly - 4.4f * S, 0); rY(t * .2f); lanternModel(5.2f * S, metal(.35f), P3); pop();
        flameAt(0, ly - 3.5f * S, 0, 2.6f * S, 4, P3, mix(P3, 0xFFFFFF, .4f), 0xFFFFFF, 1f);
        sym(CY - .4f * S, 2.8f * S, .5f);
        for (int i = 0; i < 7 * N; i++) { float a = t * .15f + i * TAU / (7 * N), r = R * .7f, y = 2f * S + 1.8f * S * (i % 3);
            push(); tr((float) Math.cos(a) * r, y + 2.4f * S, (float) Math.sin(a) * r); chain(2.2f * S, .45f * S, metal(.3f)); pop();
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(a); lanternModel(1.7f * S, metal(.35f), P3); pop();
            flameAt((float) Math.cos(a) * r, y + .4f * S, (float) Math.sin(a) * r, .9f * S, i, P3, mix(P3, 0xFFFFFF, .4f), 0xFFFFFF, 1f); }
        sprites("star", 60 * N, SPIRALM, R, 0, top, .6f * S, P1, 1f, .15f, 23, true);
        sprites("mote", 60 * N, DRIFTM, R, .5f, top, .4f * S, 0xFFFFFF, 1f, .2f, 24, false);
        fogRing(10, R * .6f, P2, .4f);
    }

    private void mother(int N) {
        sigil(R);
        rays(CY, 16 * S, P1, .4f, .1f);
        pushFace(); rY(t * .1f); flowerModel(6.5f * S, mix(P1, 0xFFFFFF, .15f), mix(P1, P3, .5f), 0xFFD860, mix(P2, 0x103018, .2f)); pop();
        sym(CY + 5f * S, 2.8f * S, .5f);
        for (int s = -1; s <= 1; s += 2) facing("wing", s * sx0 * 3.8f * S, CY + 1f * S, s * sz0 * 3.8f * S, s * 6f * S, 5f * S, 0, rgba(P2, .6f));
        for (int i = 0; i < 6 * N; i++) { float a = i * TAU / (6 * N) + t * .08f, rr = R * .75f;
            push(); tr((float) Math.cos(a) * rr, 0, (float) Math.sin(a) * rr); rY(a); rZ((float) Math.sin(t + i) * .06f); flowerModel(1.8f * S, mix(P3, 0xFFFFFF, .1f), P1, 0xFFD860, mix(P2, 0x103018, .2f)); pop(); }
        sprites("petal", 120 * N, SPIRALM, R, 0, top, .7f * S, P3, 1f, .14f, 25, true);
        sprites("leaf", 36 * N, FALLM, R, 0, top, 1f * S, P2, 1f, .12f, 26, true);
        sprites("mote", 100 * N, RISEM, R, 0, top, .3f * S, 0xFFF0A0, 1f, .25f, 27, false);
    }

    private void chained(int N, boolean m) {
        sigil(R); int iron = mix(0x707890, P1, .15f);
        stand2("rays", 0, CY, 0, 12 * S, 12 * S, t * .1f, rgba(P1, .3f));
        column("beam", 0, 0, top, 0, 3.2f * S, rgba(P1, .25f));
        sym(CY, 3.4f * S, .5f);
        for (int i = 0; i < 12 * N; i++) { float a = i * TAU / (12 * N), r = R * (.6f + .35f * h(i, 2)); push(); tr((float) Math.cos(a) * r + (float) Math.sin(t * .8f + i) * .15f * S, top + 1.5f, (float) Math.sin(a) * r); chain(top + 1.5f - 1.2f * S * h(i, 3), .9f * S, iron); pop(); }
        for (int ring = 0; ring < N; ring++) {
            int links = 30; float rr = R * (ring == 0 ? .5f : .8f), yy = CY + ring * 1.4f * S, dir = ring == 0 ? 1 : -1;
            for (int i = 0; i < links; i++) { float a = dir * t * .12f + i * TAU / links;
                push(); tr((float) Math.cos(a) * rr, yy, (float) Math.sin(a) * rr); rY(-a); if ((i & 1) == 1) rX(PI / 2);
                torus(0, 0, 0, .28f * S, .5f * S, .1f * S, 8, 4, 0, TAU, iron, 255); pop(); }
        }
        for (int i = 0; i < 4 * N; i++) { float a = i * TAU / (4 * N) + .6f, r = R * .35f;   // shackles
            push(); tr((float) Math.cos(a) * r, CY - 2.2f * S, (float) Math.sin(a) * r); rY(-a); torus(0, 0, 0, .55f * S, .6f * S, .12f * S, 14, 4, -.4f, PI + .4f, iron, 255); box(0, -.65f * S, 0, .6f * S, .16f * S, .18f * S, mix(iron, 0x000000, .3f), 255); pop(); }
        for (int i = 0; i < 14; i++) { float a = i * TAU / 14, r = R * .33f; push(); tr((float) Math.cos(a) * r, 0, (float) Math.sin(a) * r); frustum(0, 5.5f * S, .1f * S, .1f * S, 6, 1, 1, iron, 255, true); pop(); }
        push(); tr(0, 5.5f * S, 0); rX(PI / 2); ring(R * .33f, .1f * S, iron, 255); pop();
        sprites("spark", 60 * N, RISEM, R, 0, top, .35f * S, P1, 1f, .3f, 28, true);
        fogRing(10, R * .6f, P2, .35f);
    }

    private void whiteTower(int N) {
        sigil(R); float H = top * .95f;
        pushFace(); towerModel(H, 1.7f * S, mix(P1, 0xFFFFFF, .75f), mix(P1, 0xFFFFFF, .45f), P3); pop();
        for (int i = 0; i < 4; i++) { float a = t * .5f * (i % 2 == 0 ? 1 : -1) + i * PI / 2; float len = 9 * S;
            quad("beam", 0, H * .93f, 0, 0, .3f * S, 0, (float) Math.cos(a) * len / 2, 0, (float) Math.sin(a) * len / 2, rgba(P3, .5f));
            quad("beam", (float) Math.cos(a) * len / 2, H * .93f, (float) Math.sin(a) * len / 2, 0, .3f * S, 0, (float) Math.cos(a) * len / 2, 0, (float) Math.sin(a) * len / 2, rgba(P3, .55f)); }
        for (int i = 0; i < 8 * N; i++) { float yy = (i + 1) * H / (8 * N + 1); flat("ring_dash", 0, yy, 0, (4f + 1.6f * (float) Math.sin(i * .9f)) * S, t * (i % 2 == 0 ? .7f : -.7f), rgba(i % 2 == 0 ? P1 : P2, .9f)); }
        sym(H * 1.25f, 3f * S, .5f);
        sprites("page", 40 * N, SPIRALM, R * .9f, 0, top, 1.5f * S, 0xFFFFFF, 1f, .13f, 30, true);
        clouds(9 * N, R * 1.2f, top * .3f, 0xFFFFFF, .4f, R * 1.1f);
        clouds(6 * N, R, top * 1.05f, 0xFFFFFF, .4f, R);
        sprites("mote", 90 * N, RISEM, R * .6f, 0, top, .35f * S, P1, 1f, .3f, 31, false);
    }

    private void paragon(int N) {
        sigil(R);
        pushFace(); tr(0, CY, 0); armillary(3.4f * S, mix(P1, 0xFFFFFF, .2f), P3); pop();
        sym(CY, 2.4f * S, .6f);
        pushFace(); tr(-4.4f * S, CY + 1f * S, -1.4f * S); rZ(t * .4f); gear(2.4f * S, 14, .5f * S, metal(.55f), P1); pop();
        pushFace(); tr(1.6f * S, CY + 3.2f * S, -1.4f * S); rZ(-t * .4f * 1.5f + .2f); gear(1.6f * S, 10, .45f * S, mix(P1, 0x402008, .3f), P3); pop();
        pushFace(); tr(4.6f * S, CY - 1.4f * S, -1.4f * S); rZ(t * .4f * 1.2f); gear(2f * S, 12, .45f * S, metal(.45f), P1); pop();
        push(); tr(0, .15f, 0); rX(-PI / 2); rZ(t * .12f); gear(R * .5f, 18, .3f * S, mix(P2, 0x402008, .4f), P1); pop();
        stand2("rays", 0, CY, 0, 16 * S, 16 * S, t * .15f, rgba(P1, .5f));
        for (int k = 0; k < 4; k++) flat("ring_dash", 0, (1 + k * 2.2f) * S, 0, R * 1.6f - k * 1.2f * S, t * (k % 2 == 0 ? .3f : -.4f), rgba(k % 2 == 0 ? P1 : P2, .8f));
        for (int k = 0; k < 8 * N; k++) { float a = t * .5f + k * TAU / (8 * N); bb("clock", (float) Math.cos(a) * R * .75f, 1.5f * S + (k % 4) * 1.5f * S, (float) Math.sin(a) * R * .75f, 1.4f * S, 1.4f * S, 0, rgba(0xFFFFFF, 1f)); }
        sprites("spark", 100 * N, SPIRALM, R, 0, top, .4f * S, P1, 1f, .2f, 32, true);
    }

    private void hangedMan(boolean m) {
        dome(R * 1.8f, 0x000000, m ? 235 : 150);
        flat("whirl", 0, .06f, 0, 2f * R, t * .3f, rgba(P2, .9f));
        sigil(R);
        float ey = top * .85f;
        stand2("corona", 0, ey, 0, 15 * S, 15 * S, t * .1f, rgba(P1, .95f));
        stand2("rays", 0, ey, 0, 20 * S, 20 * S, t * .06f, rgba(P2, .6f));
        sphere(0, ey, 0, 2.6f * S, 2.6f * S, 2.6f * S, 10, 16, 0x000000, 255);
        int dark = mix(P2, 0x100008, .4f);
        for (int i = 0; i < 5; i++) { float a = i * TAU / 5, r = 2.4f * S; push(); tr((float) Math.cos(a) * r, top + 1.5f, (float) Math.sin(a) * r); chain(top + 1.5f - (CY + 3.4f * S), .8f * S, mix(0x707078, P1, .15f)); pop(); }
        pushFace(); tr(0, CY, 0); rY(t * .15f); rZ(PI);
        box(0, 0, 0, .5f * S, 3.2f * S, .35f * S, dark, 255); box(0, 1.6f * S, 0, 2f * S, .5f * S, .35f * S, dark, 255);
        box(0, 0, .36f * S, .12f * S, 3f * S, .02f * S, P1, 255); box(0, 1.6f * S, .36f * S, 1.8f * S, .1f * S, .02f * S, P1, 255);
        pop();
        sym(CY - 4.6f * S, 2.2f * S, .4f);
        sprites("drop", 90, FALLM, R * .9f, 0, top, .7f * S, P1, 1f, .45f, 34, false);
        for (int i = 0; i < 6; i++) { float pw = fract(t * .5f + i / 6f); flat("ring", (h(i, 61) - .5f) * R, .1f, (h(i, 62) - .5f) * R, pw * 3f * S, 0, rgba(P1, fd(pw))); }
    }

    private void fool(int N) {
        sigil(R);
        for (int i = 0; i < 12; i++) { float a = t * .08f + i * TAU / 12; bb("fog", (float) Math.cos(a) * R * .6f, .9f * S, (float) Math.sin(a) * R * .6f, 7 * S, 2.6f * S, 0, rgba(P2, .55f)); }
        float ey = CY + .6f * S, w = 9f * S;
        stand("eye", 0, ey, 0, w, w * .52f, -face + (float) Math.sin(t * .35f) * .5f, rgba(mix(P1, 0xFFFFFF, .5f), 1f));
        stand("eye", 0, ey, 0, w * 1.25f, w * .65f, -face + (float) Math.sin(t * .35f) * .5f, rgba(P1, .4f));
        stand("ring_thick", 0, ey, 0, w * .36f, w * .36f, -face + (float) Math.sin(t * .35f) * .5f + .02f, rgba(P3, .9f));
        for (int i = 0; i < 9 * N; i++) {   // fan of tarot cards behind the eye
            float a = (i / (9f * N - 1) - .5f) * 2.6f, r = 5.8f * S;
            float x = sx0 * (float) Math.sin(a) * r - fx * 1.2f * S, z = sz0 * (float) Math.sin(a) * r - fz * 1.2f * S, y = ey + (float) Math.cos(a) * r * .55f - r * .1f + (float) Math.sin(t + i) * .15f * S;
            facing("page", x, y, z, 1.6f * S, 2.6f * S, -a * .8f, rgba(mix(P1, 0xFFFFFF, .3f), .95f));
        }
        sym(ey + 5.4f * S, 2.6f * S, .5f);
        for (int i = 0; i < 4; i++) column("beam", (i - 1.5f) * 1.2f * S, 0, ey - 1.2f * S, 0, 1.4f * S, rgba(P1, .25f));
        sprites("star", 80 * N, DRIFTM, R * 1.2f, 1, top, .6f * S, 0xFFFFFF, 1f, .3f, 36, true);
        sprites("mote", 70 * N, SPIRALM, R, 0, top, .4f * S, P3, 1f, .18f, 37, false);
    }

    private void darkness(int N, boolean m) {
        dome(R * 1.9f, 0x02030C, m ? 215 : 170);
        sigil(R);
        for (int i = 0; i < 150 * N; i++) { float a = h(i, 1) * TAU, el = h(i, 2) * 1.3f + .15f, d = R * 1.7f;
            bb("star", (float) Math.cos(a) * d * (float) Math.cos(el), (float) Math.sin(el) * d + 1, (float) Math.sin(a) * d * (float) Math.cos(el), (.5f + h(i, 3)) * S, (.5f + h(i, 3)) * S, t * .3f + i, rgba(i % 4 == 0 ? P1 : 0xFFFFFF, .45f + .55f * Math.abs((float) Math.sin(t * (.6f + h(i, 4)) + i)))); }
        stand2("crescent", 0, top * .75f, 0, 12 * S, 12 * S, t * .1f, rgba(P2, .6f));
        stand2("crescent", 0, top * .75f, 0, 9 * S, 9 * S, t * .1f, rgba(mix(P1, 0xFFFFFF, .5f), 1f));
        column("beam", 0, 0, top * .7f, 0, 3f * S, rgba(P1, .3f));
        sym(CY, 3.4f * S, .5f);
        clouds(10 * N, R * 1.2f, top * .4f, P2, .5f, R * 1.3f);
        fogRing(12, R * .7f, P2, .45f);
        sprites("mote", 100 * N, DRIFTM, R * 1.2f, .5f, top, .45f * S, P1, 1f, .2f, 35, false);
        sprites("star", 24 * N, FALLM, R * 1.3f, 0, top * 1.4f, .9f * S, 0xFFFFFF, 1f, .35f, 36, true);
    }

    private void visionary(int N) {
        sigil(R);
        flat("whirl", 0, .06f, 0, 2f * R, t * .4f, rgba(P2, .9f));
        float ly = CY + .8f * S;
        pushFace(); tr(0, ly, 0); ring(3.6f * S, .22f * S, mix(P3, 0xFFFFFF, .3f), 255); ring(4.1f * S, .08f * S, mix(P1, 0xFFFFFF, .5f), 255);
        solid(true); push(); rX(PI / 2); frustum(-.15f * S, .15f * S, 3.5f * S, 3.5f * S, 24, 1, 1, P1, 70, true); pop(); solid(false); pop();
        for (int i = 0; i < 3 * N; i++) { float a = i * TAU / (3 * N) + t * .2f, r = R * 1.0f; int col = i % 3 == 0 ? P3 : i % 3 == 1 ? 0xFF80FF : P1;
            float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r;
            quad("beam", x / 2, ly, z / 2, 0, .5f * S, 0, x / 2, 0, z / 2, rgba(col, .7f)); }
        column("beam", 0, 0, ly + 8 * S, 0, 1.4f * S, rgba(0xFFFFFF, .5f));
        sym(ly, 3f * S, .8f);
        for (int i = 0; i < 6 * N; i++) { float a = t * .3f + i * TAU / (6 * N), r = R * .6f, y = 1.4f * S + h(i, 7) * 5 * S;
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(t * .8f + i); rX(t * .6f + i);
            solid(true); box(0, 0, 0, 1.1f * S, 1.5f * S, .05f * S, mix(P1, 0xFFFFFF, .6f), 90); solid(false); box(0, 0, 0, 1.15f * S, 1.55f * S, .03f * S, P3, 70); pop(); }
        for (int i = 0; i < 4; i++) { float pw = fract(t * .3f + i / 4f); flat("ring", 0, .12f, 0, pw * 2.2f * R, 0, rgba(P3, fd(pw))); }
        sprites("mote", 90 * N, SPIRALM, R, 0, top, .4f * S, P3, 1f, .18f, 37, false);
        sprites("shard", 20 * N, FALLM, R, 0, top, .8f * S, mix(P1, 0xFFFFFF, .5f), 1f, .15f, 38, true);
    }

    private void twilight(int N) {
        sigil(R);
        facing("rays", 0, 1f * S, 0, R * 3f, R * 3f, 0, rgba(P1, .4f));
        float L = 9f * S;
        pushFace(); tr(0, L * .88f, 0); rZ(PI); sword(L, metal(.35f), mix(P2, 0x302010, .3f), P1); pop();
        for (int s = -1; s <= 1; s += 2) facing("wing", s * sx0 * 4.8f * S, CY + 1.2f * S, s * sz0 * 4.8f * S, s * 7f * S, 6f * S, 0, rgba(P2, .55f));
        sym(CY + L * .55f, 2.6f * S, .5f);
        for (int i = 0; i < 14 * N; i++) { float a = i * TAU / (14 * N) + .1f; flat("crack", (float) Math.cos(a) * R * .55f, .1f, (float) Math.sin(a) * R * .55f, 3.4f * S, a, rgba(P1, .7f)); }
        for (int i = 0; i < 8 * N; i++) { float a = -t * .1f + i * TAU / (8 * N), r = R * .85f; flameAt((float) Math.cos(a) * r, 0, (float) Math.sin(a) * r, 2.6f * S, i, P2, P1, P3, .7f); }
        sprites("spark", 70 * N, RISEM, R * 1.1f, 0, top * 1.1f, .3f * S, P3, .9f, .4f, 41, true);
        sprites("smoke", 16 * N, RISEM, R * .7f, 1f, top * .8f, 3f * S, mix(P2, 0x000000, .5f), .4f, .1f, 42, true);
    }

    // ================= Sailor: a storm over a heaving sea =================
    private float seaH(float r, float th, float amp, float rad) {
        float k = Math.min(1f, r / rad);
        float swell = (float) (Math.sin(4 * th + r * .9f - t * 1.7f) * .6 + Math.sin(7 * th - r * .6f + t * 1.2f) * .4);
        float dip = -amp * 2.2f * (float) Math.exp(-(r * r) / (rad * rad * .06f));
        return amp * (.35f + .65f * k) * swell + dip;
    }
    private void ocean(float rad, float amp) {
        solid(true);
        int nr = 14, na = 40; float[] hint = v3(0, -60, 0);
        for (int i = 0; i < nr; i++) for (int j = 0; j < na; j++) {
            float r0 = rad * i / nr, r1 = rad * (i + 1) / nr, a0 = TAU * j / na, a1 = TAU * (j + 1) / na;
            float[] p0 = v3((float) Math.cos(a0) * r0, seaH(r0, a0, amp, rad), (float) Math.sin(a0) * r0), p1 = v3((float) Math.cos(a1) * r0, seaH(r0, a1, amp, rad), (float) Math.sin(a1) * r0);
            float[] p2 = v3((float) Math.cos(a1) * r1, seaH(r1, a1, amp, rad), (float) Math.sin(a1) * r1), p3 = v3((float) Math.cos(a0) * r1, seaH(r1, a0, amp, rad), (float) Math.sin(a0) * r1);
            float hh = (p0[1] + p1[1] + p2[1] + p3[1]) / 4 / amp;
            int col = hh > .55f ? mix(P1, 0xFFFFFF, Math.min(1f, (hh - .55f) * 2.2f)) : mix(P2, P1, Math.max(0f, Math.min(1f, .5f + hh * .6f)));
            sq(p0, p1, p2, p3, hint, col, 225);
        }
        solid(false);
    }
    /** A ring of breaking waves. */
    private void waveWall(float radius, float H0, float dir) {
        solid(true);
        float[][] prof = {{-1.5f, 0f}, {-1.1f, .16f}, {-.7f, .42f}, {-.3f, .76f}, {.1f, 1f}, {.5f, 1f}, {.82f, .84f}, {.92f, .62f}};
        int na = 56; float[] hint = v3(0, -60, 0);
        for (int i = 0; i < na; i++) {
            float a0 = dir * t * .22f + TAU * i / na, a1 = dir * t * .22f + TAU * (i + 1) / na;
            float hh0 = H0 * Math.max(.3f, .6f + .4f * (float) Math.sin(3 * a0 - dir * t * 1.1f) + .15f * (float) Math.sin(9 * a0 + t)), hh1 = H0 * Math.max(.3f, .6f + .4f * (float) Math.sin(3 * a1 - dir * t * 1.1f) + .15f * (float) Math.sin(9 * a1 + t));
            for (int k = 0; k < prof.length - 1; k++) {
                float[] q0 = wp(radius, a0, prof[k], hh0), q1 = wp(radius, a1, prof[k], hh1), q2 = wp(radius, a1, prof[k + 1], hh1), q3 = wp(radius, a0, prof[k + 1], hh0);
                float s = (k + .5f) / (prof.length - 1);
                int col = mix(mix(P2, P1, s), 0xFFFFFF, k >= 5 ? .55f : Math.max(0, s - .5f) * .5f);
                sq(q0, q1, q2, q3, hint, col, 215);
            }
        }
        solid(false);
        for (int i = 0; i < 18; i++) { float a = dir * t * .22f + TAU * i / 18 + .1f; float hh = H0 * Math.max(.3f, .6f + .4f * (float) Math.sin(3 * a - dir * t * 1.1f));
            bb("spark", (float) Math.cos(a) * (radius + .5f * S), hh * 1.05f, (float) Math.sin(a) * (radius + .5f * S), .9f * S, .9f * S, t + i, rgba(0xFFFFFF, .8f + .2f * (float) Math.sin(t * 4 + i))); }
    }
    private float[] wp(float radius, float a, float[] pr, float H) {
        float rr = radius + pr[0] * H * .6f;
        return v3((float) Math.cos(a) * rr, pr[1] * H, (float) Math.sin(a) * rr);
    }

    private void sailor(int N, boolean m) {
        dims(m);
        sigil(R);
        float amp = (m ? .9f : .6f) * S;
        ocean(R, amp);
        waveWall(R * .62f, 2.2f * S, 1f);
        if (m) waveWall(R * .9f, 3.4f * S, -1f);
        clouds(10 * N, R * 1.1f, top, mix(P2, 0xC0D0E8, .5f), .6f, R * 1.1f);
        clouds(6 * N, R * .8f, top + 1.5f, mix(P2, 0xFFFFFF, .4f), .4f, R * .9f);
        for (int i = 0; i < 160 * N; i++) {
            float p = fract(t * (.9f + h(i, 3) * .5f) + h(i, 1)); float a = h(i, 2) * TAU, rr = (float) Math.sqrt(h(i, 4)) * R * .95f, y = top * (1 - p);
            column("streak", (float) Math.cos(a) * rr, y - 1.4f * S, y, (float) Math.sin(a) * rr, .1f * S, rgba(P3, .8f));
        }
        strikes(3 * N, R, top, P1);
        float th = 5.2f * S;
        push(); tr(0, CY - th * .45f, 0); rY(t * .5f); trident(th, metal(.8f), P1); pop();
        column("beam", 0, 0, CY - th * .45f, 0, .9f * S, rgba(P1, .45f));
        for (int i = 0; i < 3; i++) { float ph = fract(t * 2.3f + i * .37f); if (ph < .35f) column(i % 2 == 0 ? "bolt2" : "bolt", (i - 1) * .5f * S, CY + th * .5f, top, 0, top * .1f * (i % 2 == 0 ? 1 : -1), rgba(P1, (1 - ph / .35f))); }
        sym(CY + th * .78f, 2.4f * S, .6f);
        sprites("drop", 36 * N, SPIRALM, R * .6f, 0, CY * 1.6f, .8f * S, P3, 1f, .22f, 8, false);
        fogRing(10, R * .55f, P3, .35f);
    }
}
