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
        {0xFFD84A, 0xFF9A20, 0xFFF4B0}, {0x9CE0A0, 0x2E7A48, 0xE8FFE8}, {0xE04030, 0x6A1410, 0xFF9080}, {0xFF6A38, 0xB02818, 0xFFC060},
        {0xE0E6F2, 0x6A7890, 0x20242E}, {0xD8DCE0, 0x7A3CC8, 0xFFFFFF}, {0xDCE6EC, 0x9AA8B0, 0x58A878}, {0xB890F0, 0x5A3AA0, 0xFFE8A0},
        {0x7AD890, 0x2E8A50, 0xFF9CC4}, {0xC8C4EC, 0x6A5CA8, 0x8890A0}, {0x90C0FF, 0x4A78D8, 0xFFFFFF}, {0x4CC0FF, 0x1A5ED0, 0xE8FAFF},
        {0xFFB860, 0xC87020, 0xFFE8B0}, {0xFF6A5C, 0x8A1818, 0xFFC0B8}, {0xC0B8F0, 0x6A5CB0, 0xFFFFFF}, {0x88B0F0, 0x2C3C90, 0xE8F0FF},
        {0xE0F0FF, 0x70A0D0, 0x60E8FF}, {0xC87038, 0x6A2810, 0xFFB070}};

    /** Simple look for Rare and Epic: symbol shape, prop shape. */
    private record Cfg(String core, String prop) {}
    private static final Cfg[] CFG = {
        new Cfg("crescent", "leaf"), new Cfg("door", "key"), new Cfg("scales", "sword"), new Cfg("shard", "petal"), new Cfg("note", "note"),
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
        int al = Math.max(0, Math.min(255, (int) (Math.min(1f, a * 1.7f) * 255)));
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

    // ================= solid glowing quads (symbols, panels) and LOTM models =================
    private void eq(ResourceLocation rl, float cx, float cy, float cz, float ax, float ay, float az, float bx, float by, float bz, int argb, float u0, float v0, float u1, float v1) {
        int a = (argb >> 24) & 255, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        if (a <= 2) return;
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(rl));
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
    private ResourceLocation symRL() { return ResourceLocation.fromNamespaceAndPath("astral_world", "textures/gui/tarot/sym_" + Tarot.PATHWAYS[curPath] + ".png"); }
    /** The pathway symbol: one solid, bright plane, turned by yaw. */
    private void symPlane(int path, float x, float y, float z, float size, float yaw, float alpha) {
        float c = (float) Math.cos(yaw) * size / 2, s = (float) Math.sin(yaw) * size / 2;
        int tint = gold ? 0xFFDD66 : 0xFFFFFF;
        eq(ResourceLocation.fromNamespaceAndPath("astral_world", "textures/gui/tarot/sym_" + Tarot.PATHWAYS[path] + ".png"), x, y, z, c, 0, s, 0, size / 2, 0,
           (Math.max(0, Math.min(255, (int) (alpha * 255))) << 24) | tint, 0, 0, 1, 1);
    }
    /** Solid glowing panel using one of the holo textures. */
    private void panel(String name, float x, float y, float z, float w, float hgt, float yaw, int argb) {
        float c = (float) Math.cos(yaw) * w / 2, s = (float) Math.sin(yaw) * w / 2;
        eq(tex(name), x, y, z, c, 0, s, 0, hgt / 2, 0, argb, 0, 0, 1, 1);
    }

    /** A model out of the LOTM mod (Bedrock .geo.json), loaded from its resources. Missing mod or model: draws nothing. */
    private static final class Geo {
        final java.util.List<float[]> q = new java.util.ArrayList<>();
        float minX = 1e9f, maxX = -1e9f, minY = 1e9f, maxY = -1e9f, minZ = 1e9f, maxZ = -1e9f;
    }
    private static final Map<String, Geo> GEOS = new HashMap<>();
    private static Geo geo(String name) {
        if (GEOS.containsKey(name)) return GEOS.get(name);
        Geo g = null;
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("lotm", "geo/" + name + ".geo.json"));
            if (res.isPresent()) {
                try (java.io.Reader r = new java.io.InputStreamReader(res.get().open(), java.nio.charset.StandardCharsets.UTF_8)) {
                    g = parseGeo(com.google.gson.JsonParser.parseReader(r).getAsJsonObject());
                }
            }
        } catch (Throwable ex) { g = null; }
        GEOS.put(name, g);
        return g;
    }
    private static float[] arr(com.google.gson.JsonObject o, String k, float d) {
        float[] r = {d, d, d};
        if (o.has(k) && o.get(k).isJsonArray()) { var a = o.getAsJsonArray(k); for (int i = 0; i < 3 && i < a.size(); i++) r[i] = a.get(i).getAsFloat(); }
        return r;
    }
    private static Matrix4f boneM(String n, Map<String, com.google.gson.JsonObject> bm, Map<String, Matrix4f> cache) {
        if (cache.containsKey(n)) return cache.get(n);
        com.google.gson.JsonObject b = bm.get(n);
        Matrix4f m = new Matrix4f();
        if (b.has("parent") && bm.containsKey(b.get("parent").getAsString())) m.set(boneM(b.get("parent").getAsString(), bm, cache));
        float[] pv = arr(b, "pivot", 0), ro = arr(b, "rotation", 0);
        m.translate(-pv[0], pv[1], pv[2]).rotateZ((float) Math.toRadians(ro[2])).rotateY((float) Math.toRadians(-ro[1])).rotateX((float) Math.toRadians(-ro[0])).translate(pv[0], -pv[1], -pv[2]);
        cache.put(n, m);
        return m;
    }
    private static Geo parseGeo(com.google.gson.JsonObject root) {
        com.google.gson.JsonObject go = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        com.google.gson.JsonObject desc = go.getAsJsonObject("description");
        float tw = desc.get("texture_width").getAsFloat(), th = desc.get("texture_height").getAsFloat();
        Map<String, com.google.gson.JsonObject> bm = new HashMap<>();
        for (var e : go.getAsJsonArray("bones")) bm.put(e.getAsJsonObject().get("name").getAsString(), e.getAsJsonObject());
        Map<String, Matrix4f> cache = new HashMap<>();
        Geo g = new Geo(); Vector3f tmp = new Vector3f();
        for (var be : bm.entrySet()) {
            com.google.gson.JsonObject b = be.getValue();
            if (!b.has("cubes")) continue;
            Matrix4f bmx = boneM(be.getKey(), bm, cache);
            for (var ce : b.getAsJsonArray("cubes")) {
                com.google.gson.JsonObject c = ce.getAsJsonObject();
                if (!c.has("uv") || !c.has("origin") || !c.has("size")) continue;
                float[] o = arr(c, "origin", 0), sz = arr(c, "size", 0), cp = arr(c, "pivot", 0), cr = arr(c, "rotation", 0);
                float inf = c.has("inflate") ? c.get("inflate").getAsFloat() : 0;
                Matrix4f m = new Matrix4f(bmx);
                m.translate(-cp[0], cp[1], cp[2]).rotateZ((float) Math.toRadians(cr[2])).rotateY((float) Math.toRadians(-cr[1])).rotateX((float) Math.toRadians(-cr[0])).translate(cp[0], -cp[1], -cp[2]);
                float x0 = -(o[0] + sz[0]) - inf, x1 = -o[0] + inf, y0 = o[1] - inf, y1 = o[1] + sz[1] + inf, z0 = o[2] - inf, z1 = o[2] + sz[2] + inf;
                float sx = sz[0], sy = sz[1], sze = sz[2];
                String[] names = {"north", "south", "east", "west", "up", "down"};
                float[][][] corners = {
                    {{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}}, {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
                    {{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}}, {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}},
                    {{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}}, {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}};
                for (int f = 0; f < 6; f++) {
                    float u, v, w, hh;
                    if (c.get("uv").isJsonObject()) {
                        com.google.gson.JsonObject uo = c.getAsJsonObject("uv");
                        if (!uo.has(names[f])) continue;
                        com.google.gson.JsonObject fo = uo.getAsJsonObject(names[f]);
                        u = fo.getAsJsonArray("uv").get(0).getAsFloat(); v = fo.getAsJsonArray("uv").get(1).getAsFloat();
                        if (fo.has("uv_size")) { w = fo.getAsJsonArray("uv_size").get(0).getAsFloat(); hh = fo.getAsJsonArray("uv_size").get(1).getAsFloat(); } else { w = 0; hh = 0; }
                    } else {
                        float bu = c.getAsJsonArray("uv").get(0).getAsFloat(), bv = c.getAsJsonArray("uv").get(1).getAsFloat();
                        switch (f) {
                            case 0 -> { u = bu + sze; v = bv + sze; w = sx; hh = sy; }
                            case 1 -> { u = bu + 2 * sze + sx; v = bv + sze; w = sx; hh = sy; }
                            case 2 -> { u = bu; v = bv + sze; w = sze; hh = sy; }
                            case 3 -> { u = bu + sze + sx; v = bv + sze; w = sze; hh = sy; }
                            case 4 -> { u = bu + sze; v = bv; w = sx; hh = sze; }
                            default -> { u = bu + sze + sx; v = bv; w = sx; hh = sze; }
                        }
                    }
                    if (w == 0 && hh == 0) continue;
                    float[] uvs = {u, v + hh, u + w, v + hh, u + w, v, u, v};
                    float[] qd = new float[20];
                    for (int k = 0; k < 4; k++) {
                        m.transformPosition(corners[f][k][0], corners[f][k][1], corners[f][k][2], tmp);
                        qd[k * 5] = tmp.x / 16f; qd[k * 5 + 1] = tmp.y / 16f; qd[k * 5 + 2] = tmp.z / 16f;
                        qd[k * 5 + 3] = uvs[k * 2] / tw; qd[k * 5 + 4] = uvs[k * 2 + 1] / th;
                        g.minX = Math.min(g.minX, qd[k * 5]); g.maxX = Math.max(g.maxX, qd[k * 5]); g.minY = Math.min(g.minY, qd[k * 5 + 1]); g.maxY = Math.max(g.maxY, qd[k * 5 + 1]);
                        g.minZ = Math.min(g.minZ, qd[k * 5 + 2]); g.maxZ = Math.max(g.maxZ, qd[k * 5 + 2]);
                    }
                    g.q.add(qd);
                }
            }
        }
        return g.q.isEmpty() ? null : g;
    }
    /** Draw a LOTM model with its base at the origin, scaled so it is targetH blocks tall. Returns false when the model is not available. */
    private boolean drawGeo(String name, String ns, String texPath, float targetH) {
        Geo g = geo(name);
        if (g == null) return false;
        float sc = targetH / Math.max(.001f, g.maxY - g.minY), cx = (g.minX + g.maxX) / 2, cz = (g.minZ + g.maxZ) / 2;
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(ResourceLocation.fromNamespaceAndPath(ns, texPath)));
        for (float[] q : g.q) {
            float ex = q[5] - q[0], ey = q[6] - q[1], ez = q[7] - q[2], fx2 = q[15] - q[0], fy2 = q[16] - q[1], fz2 = q[17] - q[2];
            float nx = ey * fz2 - ez * fy2, ny = ez * fx2 - ex * fz2, nz = ex * fy2 - ey * fx2, l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-9f) { nx = 0; ny = 1; nz = 0; } else { nx /= l; ny /= l; nz /= l; }
            cur.transformDirection(nx, ny, nz, tn);
            float tnx = tn.x, tny = tn.y, tnz = tn.z;
            for (int k = 0; k < 4; k++) {
                cur.transformPosition((q[k * 5] - cx) * sc, (q[k * 5 + 1] - g.minY) * sc, (q[k * 5 + 2] - cz) * sc, tp);
                vc.addVertex(mat, tp.x, tp.y, tp.z).setColor(255, 255, 255, 255).setUv(q[k * 5 + 3], q[k * 5 + 4])
                  .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(tnx, tny, tnz);
            }
        }
        return true;
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
            torus(0, .73f * L, 0, .15f * L * s, .0f, .0f, 1, 3, 0, 0, rgb, 0);
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
        torus(0, 0, 0, r, r, .03f * r, 1, 3, 0, 0, rgb, 0);
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
        int shade = mix(bone, 0x504838, .35f), dark = 0x060606;
        sphere(0, .12f * r, -.05f * r, .92f * r, .88f * r, 1.0f * r, 10, 18, bone, 255);
        box(0, .28f * r, .78f * r, .66f * r, .09f * r, .17f * r, bone, 255);                       // brow ridge
        sphere(-.62f * r, -.22f * r, .62f * r, .2f * r, .17f * r, .17f * r, 5, 8, bone, 255);      // cheekbones
        sphere(.62f * r, -.22f * r, .62f * r, .2f * r, .17f * r, .17f * r, 5, 8, bone, 255);
        push(); tr(0, 0, .5f * r); taper(-.62f * r, -.12f * r, .48f * r, .3f * r, .54f * r, .3f * r, bone, 255); pop();
        sphere(-.37f * r, .02f * r, .83f * r, .26f * r, .29f * r, .17f * r, 6, 10, dark, 255);       // eye sockets
        sphere(.37f * r, .02f * r, .83f * r, .26f * r, .29f * r, .17f * r, 6, 10, dark, 255);
        sphere(-.37f * r, .02f * r, .9f * r, .1f * r, .1f * r, .06f * r, 4, 6, glow, 255);
        sphere(.37f * r, .02f * r, .9f * r, .1f * r, .1f * r, .06f * r, 4, 6, glow, 255);
        push(); tr(0, -.28f * r, .96f * r); taper(-.2f * r, .2f * r, .02f * r, .02f * r, .13f * r, .04f * r, dark, 255); pop();   // nose
        for (int i = -3; i <= 3; i++) box(i * .13f * r, -.64f * r, .78f * r, .055f * r, .09f * r, .03f * r, mix(bone, 0xFFFFFF, .5f), 255);   // upper teeth
        box(0, -.97f * r, .56f * r, .48f * r, .1f * r, .34f * r, shade, 255);                       // lower jaw
        box(-.52f * r, -.78f * r, .26f * r, .08f * r, .24f * r, .14f * r, shade, 255); box(.52f * r, -.78f * r, .26f * r, .08f * r, .24f * r, .14f * r, shade, 255);
        for (int i = -3; i <= 3; i++) box(i * .13f * r, -.84f * r, .82f * r, .05f * r, .08f * r, .03f * r, mix(bone, 0xFFFFFF, .5f), 255);   // lower teeth
        for (int i = 0; i < 3; i++) box(-.02f * r + i * .03f * r, .9f * r - i * .08f * r, .3f * r, .012f * r, .15f * r, .012f * r, shade, 255);  // cracks
    }
    private void mirrorModel(float s, int frame, int glass) {
        torus(0, 0, 0, .55f * s, .8f * s, .07f * s, 20, 5, 0, TAU, frame, 255);
        for (int i = 0; i < 12; i++) { float a = i * TAU / 12; sphere((float) Math.cos(a) * .6f * s, (float) Math.sin(a) * .87f * s, 0, .05f * s, .05f * s, .05f * s, 3, 5, mix(frame, 0xFFFFFF, .4f), 255); }
        solid(true); push(); rX(PI / 2); frustum(-.02f * s, .02f * s, .55f * s, .55f * s, 20, 1, 1.45f, glass, 120, true); pop(); solid(false);
        frustum(-1.7f * s, -.8f * s, .06f * s, .05f * s, 8, 1, 1, frame, 255, true); sphere(0, -1.75f * s, 0, .09f * s, .09f * s, .09f * s, 4, 6, frame, 255);
    }
    private void flask(float r, int glass, int liquid) {
        solid(true); sphere(0, 0, 0, r, r, r, 8, 12, glass, 150); frustum(r * .8f, r * 1.7f, r * .3f, r * .25f, 10, 1, 1, glass, 150, false); solid(false);
        sphere(0, -r * .15f, 0, r * .8f, r * .65f, r * .8f, 6, 10, liquid, 255);
        frustum(r * 1.7f, r * 2.0f, r * .3f, r * .33f, 10, 1, 1, 0x7A5A38, 255, true);
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
        taper(Hh, Hh, 0, 0, 0, 0, leaf, 0);
        torus(.5f * W, Hh, 0, .5f * W, .5f * W, .035f * W, 1, 3, 0, 0, leaf, 0);
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
        for (int i = 0; i < 3; i++) { float hw = .85f * (1.5f - .22f * (i + 1)) * s; int sc2 = mix(dark, trim, .12f * i + .08f);
            box(0, i == 0 ? -.04f * s : (i + .5f) * .22f * s, 0, hw, i == 0 ? .26f * s : .11f * s, hw, sc2, 255);
            box(0, (i + 1) * .22f * s - .012f * s, 0, hw + .02f * s, .014f * s, hw + .02f * s, mix(trim, 0xFFFFFF, .1f), 255); }
        box(0, .95f * s, 0, .55f * s, .16f * s, .5f * s, dark, 255);
        push(); tr(0, 0, -.4f * s);
        taper(.85f * s, 3.2f * s, .6f * s, .1f * s, .42f * s, .08f * s, dark, 255);
        taper(3.2f * s, 3.8f * s, .42f * s, .08f * s, 0, 0, dark, 255);
        for (int i = -2; i <= 2; i++) { push(); tr(i * .27f * s, 3.0f * s - Math.abs(i) * .12f * s, 0); rZ(-i * .18f); taper(0, (.9f - Math.abs(i) * .12f) * s, .07f * s, .05f * s, 0, 0, dark, 255); pop(); }
        pop();
        for (int sd = -1; sd <= 1; sd += 2) { box(sd * .62f * s, 1.25f * s, 0, .09f * s, .3f * s, .46f * s, dark, 255); box(sd * .62f * s, 1.58f * s, .3f * s, .1f * s, .03f * s, .22f * s, trim, 255); }
        box(0, 1.2f * s, .46f * s, .3f * s, .02f * s, .02f * s, trim, 255);
    }
    private void harp(float L, int wood, int string) {
        frustum(0, L, .05f * L, .035f * L, 8, 1, 1, wood, 255, true);
        torus(.32f * L, L, 0, .32f * L, .3f * L, .04f * L, 12, 5, PI / 2, PI, wood, 255);
        box(.16f * L, .08f * L, 0, .18f * L, .05f * L, .04f * L, wood, 255);
        for (int i = 0; i < 9; i++) { float x = (.06f + i * .05f) * L; frustum(.1f * L, (.95f - i * .012f) * L, .006f * L, .006f * L, 4, 1, 1, string, 255, false); push(); tr(x, 0, 0); pop(); }
    }


    // ================= round three: extra models =================
    /** One real petal: a flat, cupped, leaf-shaped sheet growing along +Y, width along Z, curling toward +X. */
    private void petalShape(float L, float W, float curl, int rgb) {
        float[] wp = {0f, .55f, .92f, 1f, .82f, .45f, 0f}; int n = wp.length - 1;
        float[] hint = v3(-6f, L * .5f, 0);
        for (int i = 0; i < n; i++) {
            float u0 = i / (float) n, u1 = (i + 1) / (float) n;
            float x0 = curl * u0 * u0 * L, x1 = curl * u1 * u1 * L;
            float w0 = wp[i] * W * .5f, w1 = wp[i + 1] * W * .5f, r0 = -wp[i] * W * .07f, r1 = -wp[i + 1] * W * .07f;
            int c0 = mix(rgb, 0xFFFFFF, u0 * .3f);
            sq(v3(x0, u0 * L, -w0), v3(x0 + r0, u0 * L, 0), v3(x1 + r1, u1 * L, 0), v3(x1, u1 * L, -w1), hint, c0, 255);
            sq(v3(x0 + r0, u0 * L, 0), v3(x0, u0 * L, w0), v3(x1, u1 * L, w1), v3(x1 + r1, u1 * L, 0), hint, c0, 255);
        }
    }
    private void leafShape(float L, float W, int rgb) { petalShape(L, W, .16f, rgb); }
    /** A wide open flower with real petals in rings, a stem and real leaves. */
    private void flowerModel(float L, int petal, int petal2, int heart, int leaf) {
        float st = .4f * L;
        frustum(0, st, .03f * L, .024f * L, 8, 1, 1, leaf, 255, true);
        for (int k = 0; k < 4; k++) { push(); tr(0, st * (.2f + .2f * k), 0); rY(k * PI / 2 + .6f); rZ(1.0f + .08f * k); leafShape((.36f - .04f * k) * L, .15f * L, leaf); pop(); }
        push(); tr(0, st, 0);
        float[] tilt = {1.45f, 1.15f, .85f, .55f, .3f}, len = {.5f, .47f, .42f, .34f, .26f}, wid = {.3f, .29f, .26f, .22f, .18f}; int[] cnt = {12, 10, 8, 6, 5};
        for (int layer = 0; layer < 5; layer++) for (int i = 0; i < cnt[layer]; i++) {
            push(); rY(i * TAU / cnt[layer] + layer * .3f); rZ(tilt[layer]); tr(0, .015f * L, 0);
            petalShape(len[layer] * L, wid[layer] * L, .38f, (layer & 1) == 0 ? petal : petal2); pop();
        }
        sphere(0, .03f * L, 0, .09f * L, .06f * L, .09f * L, 6, 10, heart, 255);
        for (int i = 0; i < 12; i++) { push(); rY(i * TAU / 12); rZ(.5f); frustum(.05f * L, .18f * L, .004f * L, .004f * L, 4, 1, 1, heart, 255, false); tr(0, .18f * L, 0); sphere(0, 0, 0, .013f * L, .013f * L, .013f * L, 3, 4, heart, 255); pop(); }
        pop();
    }
    /** A rose: thorny stem, real leaves and a cupped, layered bloom of wide petals. */
    private void rose(float L, int petal, int petal2, int leaf) {
        float st = .55f * L;
        frustum(0, st, .03f * L, .025f * L, 8, 1, 1, leaf, 255, true);
        for (int i = 0; i < 6; i++) { push(); tr(0, (.1f + i * .075f) * L, 0); rY(i * 2.1f); rZ(-1.2f); taper(0, .05f * L, .01f * L, .01f * L, 0, 0, 0x2A2A2A, 255); pop(); }
        for (int s = 0; s < 2; s++) { push(); tr(0, st * (.35f + .25f * s), 0); rY(s * PI + .5f); rZ(1.0f); leafShape(.3f * L, .15f * L, leaf); pop(); }
        push(); tr(0, st, 0);
        float[] tilt = {.95f, .78f, .6f, .42f, .26f, .12f}, len = {.46f, .42f, .37f, .32f, .26f, .2f}; int[] cnt = {5, 5, 4, 4, 3, 2};
        for (int layer = 0; layer < 6; layer++) for (int i = 0; i < cnt[layer]; i++) {
            push(); rY(i * TAU / cnt[layer] + layer * 1.1f); tr(.05f * L * (1f - layer * .12f), 0, 0); rZ(tilt[layer]);
            petalShape(len[layer] * L, len[layer] * L * .95f, .55f, (layer & 1) == 0 ? petal : petal2); pop();
        }
        pop();
    }
    private void eyeball(float r, int iris) {
        sphere(0, 0, 0, r, r, r, 8, 14, 0xF4F0EC, 255);
        sphere(0, 0, .82f * r, .52f * r, .52f * r, .2f * r, 6, 12, iris, 255);
        sphere(0, 0, .94f * r, .27f * r, .27f * r, .12f * r, 5, 10, 0x050505, 255);
        sphere(-.09f * r, .1f * r, 1.0f * r, .07f * r, .07f * r, .035f * r, 3, 5, 0xFFFFFF, 255);
        for (int i = 0; i < 4; i++) { push(); rZ(i * 1.7f + .4f); rY(-.8f - .15f * i); frustum(.2f * r, .75f * r, .012f * r, .006f * r, 4, 1, 1, 0xC03030, 255, false); pop(); }
    }
    /** A real die: white cube with proper pips. */
    private void die(float s) {
        box(0, 0, 0, s, s, s, 0xF0ECE0, 255);
        float[][][] pip = {{{0, 0}}, {{-1, -1}, {1, 1}}, {{-1, -1}, {0, 0}, {1, 1}}, {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}, {{-1, -1}, {1, -1}, {0, 0}, {-1, 1}, {1, 1}}, {{-1, -1}, {-1, 0}, {-1, 1}, {1, -1}, {1, 0}, {1, 1}}};
        int[][] ax = {{0, 1, 2}, {0, 1, 2}, {1, 0, 2}, {1, 0, 2}, {2, 0, 1}, {2, 0, 1}}; float[] sg = {1, -1, 1, -1, 1, -1}; int[] val = {0, 5, 1, 4, 2, 3};
        for (int f = 0; f < 6; f++) for (float[] pp : pip[val[f]]) {
            float[] c = new float[3], hs = new float[3];
            c[ax[f][0]] = sg[f] * s * 1.0f; c[ax[f][1]] = pp[0] * s * .5f; c[ax[f][2]] = pp[1] * s * .5f;
            hs[ax[f][0]] = .018f * s; hs[ax[f][1]] = .14f * s; hs[ax[f][2]] = .14f * s;
            box(c[0], c[1], c[2], hs[0], hs[1], hs[2], 0x181818, 255);
        }
    }
    private void scalesModel(float L, int rgb, int pan) {
        frustum(0, L, .03f * L, .02f * L, 6, 1, 1, rgb, 255, true); box(0, 0, 0, .18f * L, .03f * L, .18f * L, rgb, 255);
        float tl = (float) Math.sin(t * 1.2f) * .12f;
        push(); tr(0, L, 0); rZ(tl); box(0, 0, 0, .5f * L, .02f * L, .02f * L, rgb, 255);
        for (int s = -1; s <= 1; s += 2) { push(); tr(s * .5f * L, 0, 0); rZ(-tl);
            for (int k = 0; k < 3; k++) { push(); rY(k * TAU / 3); rZ(.46f); frustum(-.43f * L, 0, .004f * L, .004f * L, 4, 1, 1, rgb, 255, false); pop(); }
            frustum(-.48f * L, -.4f * L, .1f * L, .22f * L, 12, 1, 1, pan, 255, true); pop(); }
        pop();
    }
    private void lockModel(float s, int body, int shackle) {
        box(0, 0, 0, .5f * s, .4f * s, .2f * s, body, 255);
        torus(0, .4f * s, 0, .32f * s, .42f * s, .07f * s, 12, 4, 0, PI, shackle, 255);
        sphere(0, .05f * s, .21f * s, .07f * s, .07f * s, .04f * s, 3, 5, 0x101010, 255);
    }
    private void chairModel(float s, int bronze, int trim, float backH) {
        box(0, .5f * s, 0, .4f * s, .04f * s, .4f * s, bronze, 255);
        for (int i = 0; i < 4; i++) box(((i & 1) * 2 - 1) * .34f * s, .25f * s, ((i >> 1) * 2 - 1) * .34f * s, .03f * s, .25f * s, .03f * s, bronze, 255);
        box(0, .5f * s + backH * .5f, -.37f * s, .4f * s, backH * .5f, .035f * s, bronze, 255);
        box(0, .5f * s + backH, -.37f * s, .44f * s, .03f * s, .05f * s, trim, 255);
        for (int i = -1; i <= 1; i++) box(i * .22f * s, .5f * s + backH * .55f, -.34f * s, .02f * s, backH * .4f, .015f * s, trim, 255);
    }
    private void tableModel(float hl, float hw, float h, int bronze, int trim) {
        box(0, h, 0, hl, .06f * h, hw, bronze, 255); box(0, h - .12f * h, 0, hl * .96f, .06f * h, hw * .92f, mix(bronze, 0x000000, .25f), 255);
        box(0, h + .065f * h, 0, hl * 1.01f, .012f * h, hw * 1.01f, trim, 255);
        for (int i = 0; i < 4; i++) box(((i & 1) * 2 - 1) * (hl - .12f * hw), h * .46f, ((i >> 1) * 2 - 1) * (hw - .12f * hw), .1f * hw, h * .46f, .1f * hw, bronze, 255);
    }
    /** The Bearing of Sins: a black silhouette bound in iron chains, a red heart glowing in it. Feet at the origin. */
    private void bearer(float H) {
        int sil = 0x0B0B12, iron = 0x6A6A78;
        box(-.075f * H, .23f * H, 0, .045f * H, .23f * H, .05f * H, sil, 255); box(.075f * H, .23f * H, 0, .045f * H, .23f * H, .05f * H, sil, 255);
        box(0, .62f * H, 0, .14f * H, .17f * H, .075f * H, sil, 255);
        sphere(0, .87f * H, 0, .08f * H, .09f * H, .08f * H, 6, 10, sil, 255);
        for (int sd = -1; sd <= 1; sd += 2) { push(); tr(sd * .17f * H, .76f * H, 0); rZ(sd * .5f); box(0, -.16f * H, 0, .035f * H, .16f * H, .04f * H, sil, 255); pop(); }
        for (int i = 0; i < 3; i++) { push(); tr(0, (.5f + .1f * i) * H, 0); rX(PI / 2); torus(0, 0, 0, .17f * H, .1f * H, .012f * H, 14, 4, 0, TAU, iron, 255); pop(); }
        sphere(0, .68f * H, .08f * H, .035f * H, .035f * H, .02f * H, 4, 6, 0xE02020, 255);
    }
    /** A tube following points, for tentacles. */
    private void tube(float[][] pts, float r0, float r1, int rgb) {
        int n = pts.length - 1;
        for (int i = 0; i < n; i++) {
            float dx = pts[i + 1][0] - pts[i][0], dy = pts[i + 1][1] - pts[i][1], dz = pts[i + 1][2] - pts[i][2], l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (l < 1e-4f) continue;
            float ra = r0 + (r1 - r0) * i / n, rb = r0 + (r1 - r0) * (i + 1) / n;
            push(); tr(pts[i][0], pts[i][1], pts[i][2]); aim(dx, dy, dz); frustum(0, l, ra, rb, 8, 1, 1, rgb, 255, false); pop();
            sphere(pts[i + 1][0], pts[i + 1][1], pts[i + 1][2], rb * 1.02f, rb * 1.02f, rb * 1.02f, 4, 8, rgb, 255);
        }
    }
    /** A tentacle that rises from the ground and spirals around an orb centred at (cx,cy,cz). */
    private void wrapTentacle(float cx, float cy, float cz, float orbR, float startR, float startAng, float dir, int seed, int rgb, float thick) {
        int n = 30; float[][] pts = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n, k = Math.min(1f, u * 2.2f);
            float ang = startAng + dir * (u * 5.2f + (float) Math.sin(t * .6f + seed) * .3f);
            float rr = startR + (orbR * 1.3f - startR) * k + (float) Math.sin(t * 1.4f + u * 8 + seed) * .08f * orbR;
            float yy = (cy - orbR * 1.1f) * k + Math.max(0f, u - .45f) * (orbR * 2.4f / .55f) + (float) Math.sin(t * 1.5f + u * 9 + seed) * .1f * orbR;
            pts[i] = v3(cx + (float) Math.cos(ang) * rr, yy, cz + (float) Math.sin(ang) * rr);
        }
        tube(pts, thick, thick * .2f, rgb);
    }
    /** A sun that flares: solid glowing ball, soft halo and prominences that flare out and fall back. Looks the same from every side. */
    private void flareSun(float x, float y, float z, float rad, int c1, int c2) {
        sphere(x, y, z, rad, rad, rad, 10, 16, mix(c1, 0xFFFFFF, .5f), 255);
        bb("disc", x, y, z, rad * 2.15f, rad * 2.15f, 0, rgba(mix(c1, 0xFFFFFF, .6f), 1f));
        bb("corona", x, y, z, rad * 5f, rad * 5f, t * .2f, rgba(c1, .9f));
        bb("rays", x, y, z, rad * 8f, rad * 8f, -t * .15f, rgba(c2, .7f));
        for (int i = 0; i < 9; i++) {
            float a = i * TAU / 9 + t * .1f, pul = .5f + .5f * (float) Math.sin(t * 1.9f + i * 2.3f), len = rad * (.8f + 1.5f * pul);
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a), d = rad * .95f + len * .5f;
            bb("flame", x + (rx * ca + ux * sa) * d, y + (ry * ca + uy * sa) * d, z + (rz * ca + uz * sa) * d, len * .55f, len, a - PI / 2, rgba(mix(c1, c2, pul), .95f));
        }
    }
    /** Minecraft-style sky stars: tiny square points that twinkle. */
    private void mcStars(int n, float rad, int c1, int c2, float size) {
        for (int i = 0; i < n; i++) {
            float a = h(i, 101) * TAU, el = .12f + h(i, 102) * 1.35f, ce = (float) Math.cos(el);
            float tw = .4f + .6f * Math.abs((float) Math.sin(t * (.5f + h(i, 104)) + i * 1.7f)), s2 = size * (.6f + h(i, 103) * .9f);
            bb("solid", (float) Math.cos(a) * rad * ce, (float) Math.sin(el) * rad + 1f, (float) Math.sin(a) * rad * ce, s2, s2, 0, rgba(h(i, 105) < .5f ? c1 : c2, tw));
        }
    }
    /** Solid alpha-blended camera-facing quad (can be dark, unlike the glowing ones). */
    private void ebb(String name, float x, float y, float z, float w, float hgt, float rot, int argb) {
        float c = (float) Math.cos(rot), s = (float) Math.sin(rot), hx = w / 2, hy = hgt / 2;
        eq(tex(name), x, y, z, (rx * c + ux * s) * hx, (ry * c + uy * s) * hx, (rz * c + uz * s) * hx, (-rx * s + ux * c) * hy, (-ry * s + uy * c) * hy, (-rz * s + uz * c) * hy, argb, 0, 0, 1, 1);
    }
    private ResourceLocation symRL(int p) { return ResourceLocation.fromNamespaceAndPath("astral_world", "textures/gui/tarot/sym_" + Tarot.PATHWAYS[p] + ".png"); }
    /** The pathway symbol cut into slices that jump sideways; now and then it flashes as other pathways' symbols. */
    private void glitchSym(float cx, float cy, float cz, float size, float yw) {
        int slices = 14; float c = (float) Math.cos(yw), s = (float) Math.sin(yw), tt = (float) Math.floor(t * 4f) / 4f;
        boolean burst = fract(tt * .17f) < .08f;
        for (int k = 0; k < slices; k++) {
            boolean glitch = fract(tt * .6f + h(k, 3)) > .6f;
            float dx = glitch ? (h((int) (tt * 7) + k, 77) - .5f) * .32f * size : 0, v0 = k / (float) slices, v1 = (k + 1) / (float) slices, yc = cy + (.5f - (k + .5f) / slices) * size;
            int pk = burst && h((int) (tt * 7) + k, 9) > .35f ? (int) (h(k * 3 + (int) (tt * 5), 12) * Tarot.PATHWAYS.length) % Tarot.PATHWAYS.length : curPath;
            int tint = glitch ? mix(symTint(), 0xD0A0FF, .35f) : symTint();
            eq(symRL(pk), cx + c * dx, yc, cz + s * dx, c * size / 2, 0, s * size / 2, 0, size / slices / 2, 0, rgba(tint, 1f), 0, v0, 1, v1);
            if (glitch) eq(symRL(pk), cx - c * dx * 1.7f, yc, cz - s * dx * 1.7f, c * size / 2, 0, s * size / 2, 0, size / slices / 2, 0, rgba(0xA040FF, .55f), 0, v0, 1, v1);
        }
    }
    private double ex, ey, ez;
    /** Top surface of the ground in the block column at x,z (searching near y), so flat things lie on the real terrain. */
    private double groundTop(int x, int y, int z) {
        net.minecraft.client.multiplayer.ClientLevel lv = Minecraft.getInstance().level;
        if (lv == null) return y;
        for (int yy = y + 2; yy >= y - 4; yy--) {
            net.minecraft.core.BlockPos bp = new net.minecraft.core.BlockPos(x, yy, z);
            net.minecraft.world.phys.shapes.VoxelShape sh = lv.getBlockState(bp).getCollisionShape(lv, bp);
            if (!sh.isEmpty()) return yy + sh.max(net.minecraft.core.Direction.Axis.Y);
        }
        return y;
    }
    /** World offset of a point (x,z) given in throne-local space. */
    private float[] thW(float x, float z) {
        double a = thQ * Math.PI / 2; float c = Math.round(Math.cos(a)), s = Math.round(Math.sin(a));
        return new float[]{x * c + z * s, -x * s + z * c};
    }
    private void waveRing(float rad, float hh, int n, float dir, int rgb) {
        for (int i = 0; i < n; i++) {
            float a = dir * t * .25f + i * TAU / n, bob = (float) Math.sin(t * 1.8f + i * 1.3f) * .12f * hh, ca = (float) Math.cos(a), sa = (float) Math.sin(a), w = TAU * rad / n * 1.2f;
            float hv = hh * (.75f + .25f * (float) Math.sin(t * 1.1f + i * 2.1f));
            quad("wave", ca * rad, hv * .5f + bob, sa * rad, -sa * w / 2, 0, ca * w / 2, 0, hv * .5f, 0, rgba(rgb, .95f));
        }
    }
    private void paleEmperor(float s) {
        int robe = mix(P2, 0x0A140E, .35f), trim = P1, hood = 0x070A08;
        push(); tr(0, 1.11f * s, .02f * s);
        frustum(0, .12f * s, .62f * s, .5f * s, 10, 1, .85f, mix(robe, trim, .3f), 255, true);
        frustum(.12f * s, 1.55f * s, .5f * s, .3f * s, 10, 1, .8f, robe, 255, false);
        frustum(1.55f * s, 1.95f * s, .3f * s, .24f * s, 10, 1, .8f, mix(robe, trim, .15f), 255, true);
        box(0, 1.9f * s, 0, .4f * s, .07f * s, .2f * s, trim, 255);
        sphere(0, 2.2f * s, .02f * s, .22f * s, .26f * s, .22f * s, 8, 12, hood, 255);
        sphere(-.09f * s, 2.22f * s, .2f * s, .045f * s, .03f * s, .03f * s, 3, 5, P3, 255); sphere(.09f * s, 2.22f * s, .2f * s, .045f * s, .03f * s, .03f * s, 3, 5, P3, 255);
        push(); tr(0, 2.42f * s, 0); crown(.3f * s, metal(.7f), P3); pop();
        for (int sd = -1; sd <= 1; sd += 2) {
            push(); tr(sd * .5f * s, 1.15f * s, .1f * s); rX(-.3f); rZ(sd * .1f); box(0, 0, 0, .09f * s, .7f * s, .09f * s, robe, 255); pop();
            sphere(sd * .6f * s, .5f * s, .35f * s, .08f * s, .08f * s, .08f * s, 4, 6, mix(0xE8E8D0, P1, .3f), 255);
        }
        push(); tr(.78f * s, 0, .3f * s); frustum(-1.11f * s, 2.5f * s, .035f * s, .03f * s, 6, 1, 1, mix(robe, 0xFFFFFF, .2f), 255, true);
        sphere(0, 2.55f * s, 0, .11f * s, .11f * s, .11f * s, 5, 8, P1, 255); pop();
        pop();
    }
    private void epicProps(Cfg c, int seq, float cy) {
        boolean two = seq == 2; int props = two ? 4 : 2;
        switch (curPath) {
            case 2 -> { for (int i = 0; i < props; i++) { float a = t * .7f + i * TAU / props; push(); tr((float) Math.cos(a) * 1.05f, cy - .55f + (float) Math.sin(t * 1.5f + i) * .15f, (float) Math.sin(a) * 1.05f); rY(-a + PI / 2); scalesModel(.62f, metal(.75f), P1); pop(); } }
            case 6 -> {
                float vy = .55f; sphere(0, vy, 0, .4f, .4f, .4f, 8, 12, 0x050205, 255); flat("whirl", 0, vy, 0, 2.4f, t * 1.2f, rgba(P1, .9f));
                for (int i = 0; i < 3; i++) wrapTentacle(0, vy, 0, .4f, 1.7f, i * TAU / 3, i % 2 == 0 ? 1 : -1, i, mix(P2, 0x1A0830, .5f), .08f);
            }
            case 7 -> { for (int i = 0; i < props + 1; i++) { float a = t * .6f + i * TAU / (props + 1); flameAt((float) Math.cos(a) * 1.05f, .1f, (float) Math.sin(a) * 1.05f, .9f, i, P2, P1, P3, .95f); } }
            case 8 -> {
                pushThrone(); throneModel(.22f, mix(P3, 0x000000, .2f), metal(.6f)); pop();
                for (int i = 0; i < props; i++) { float a = t * .8f + i * TAU / props; push(); tr((float) Math.cos(a) * 1.1f, cy - .6f + (float) Math.sin(t * 1.4f + i) * .12f, (float) Math.sin(a) * 1.1f); rY(t + i); crown(.2f, metal(.7f), P1); pop(); }
            }
            case 10 -> {
                push(); tr(fx * 1.2f, .95f, fz * 1.2f); rY(face); rZ(t * .5f); wheelModel(.62f, metal(.7f), metal(.5f), P1); pop();
                for (int i = 0; i < props; i++) { float a = t * .8f + i * TAU / props; push(); tr((float) Math.cos(a) * 1.1f, .6f + (float) Math.sin(t * 1.6f + i) * .2f, (float) Math.sin(a) * 1.1f); rY(t * 1.3f + i); rX(t * 1.7f + i); die(.14f); pop(); }
            }
            case 11 -> { for (int i = 0; i < props + 1; i++) { float a = t * .6f + i * TAU / (props + 1); push(); tr((float) Math.cos(a) * 1.1f, cy - .3f + (float) Math.sin(t * 1.4f + i) * .2f, (float) Math.sin(a) * 1.1f); rY(face + (float) Math.sin(t * .8f + i) * .6f); eyeball(.2f, P1); pop(); } }
            case 15 -> { push(); tr(0, .08f, 0); ocean(1.8f, .13f); pop(); waveRing(2.2f, .55f, 12, 1f, P1); flat("whirl", 0, .06f, 0, 3.2f, -t * .5f, rgba(P2, .9f)); }
            case 17 -> { for (int i = 0; i < props; i++) { float a = t * .6f + i * TAU / props; push(); tr((float) Math.cos(a) * 1.15f, cy + .3f + (float) Math.sin(t * 1.3f + i) * .1f, (float) Math.sin(a) * 1.15f); rY(-a); rZ(PI); bearer(.7f); pop(); } }
            default -> {
                for (int i = 0; i < props; i++) {
                    float a = t * .9f + i * TAU / props, px = (float) Math.cos(a) * 1.0f, py = cy + (float) Math.sin(t * 1.5f + i) * .3f - .5f, pz = (float) Math.sin(a) * 1.0f;
                    bb(c.prop, px, py + .5f, pz, .4f, .4f, a, rgba(mix(P1, P3, .3f), 1f));
                }
            }
        }
    }

    // ================= render =================
    private float face, camDist;
    private int symTint() { return gold ? 0xFFDD66 : 0xFFFFFF; }
    private String symTex() { return "sym_" + Tarot.PATHWAYS[curPath]; }

    @Override
    public void render(HoloCardEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource bufs, int light) {
        super.render(e, yaw, pt, ps, bufs, light);
        int seq = e.seq(), pi = e.pathIndex(); thQ = e.throneQuarter(); ex = e.getX(); ey = e.getY(); ez = e.getZ();
        boolean shiny = e.shiny();
        this.buf = bufs; this.mat = ps.last().pose();
        this.t = (e.tickCount + pt) / 20f;
        clear(); strans = false;
        boolean symOnly = e.symbolOnly();
        if (!symOnly) drawCard(e, bufs, light, pi, seq, shiny);
        if (seq >= 6 && !symOnly) return;

        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        camDist = (float) cam.getPosition().distanceTo(e.getPosition(pt));
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        rx = -left.x; ry = -left.y; rz = -left.z; ux = up.x; uy = up.y; uz = up.z;
        fyaw = (float) Math.toRadians(e.getYRot());
        face = PI - fyaw; fx = -(float) Math.sin(fyaw); fz = (float) Math.cos(fyaw);
        sx0 = (float) Math.cos(face); sz0 = -(float) Math.sin(face);
        curPath = Math.min(pi, PAL.length - 1); gold = shiny;
        int[] pal = PAL[curPath];
        P1 = gold ? 0xFFD040 : pal[0]; P2 = gold ? 0xB87810 : pal[1]; P3 = gold ? 0xFFF4B0 : pal[2];
        if (symOnly) { symbolOnly(); return; }
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

    /** Crouch-placed: just the pathway symbol, floating and glowing, no card and no scene. */
    private void symbolOnly() {
        float bob = (float) Math.sin(t * 1.6f) * .06f;
        flat("ring", 0, .05f, 0, .9f, t * .5f, rgba(P2, .9f));
        bb("glow", 0, .85f + bob, 0, 1.7f, 1.7f, 0, rgba(P1, .3f));
        symPlane(curPath, 0, .85f + bob, 0, 1.1f, -face + (float) Math.sin(t * .5f) * .5f, 1f);
        for (int i = 0; i < 4; i++) { float p = fract(t * .3f + i * .25f); bb("mote", (float) Math.cos(i * 1.6f + t) * .5f, p * 1.4f, (float) Math.sin(i * 1.6f + t) * .5f, .1f, .1f, 0, rgba(P1, fd(p))); }
    }

    // ================= Rare and Epic =================
    private void rare(Cfg c, int seq) {
        float pulse = .8f + .2f * (float) Math.sin(t * 3);
        flat("ring", 0, .05f, 0, 1.0f, t * .5f, rgba(P2, .9f));
        column("beam", 0, .05f, 1.5f, 0, .35f, rgba(P1, .4f));
        float bob = (float) Math.sin(t * 2) * .06f;
        if (curPath == 3) {   // Demoness: a rose
            push(); tr(0, .35f + bob, 0); rY(t * .8f); rose(.95f, P1, P2, mix(P2, 0x103018, .3f)); pop();
            sprites("petal", 8, FALLM, .6f, 0, 1.8f, .22f, P1, 1f, .3f, 3, true);
            return;
        }
        if (curPath == 9) { glitchSym(0, 1.1f + bob, 0, .75f, -face); return; }
        if (curPath == 7) {   // Hunter: a real flickering flame
            flameAt(0, .25f, 0, 1.3f, 2, P2, P1, P3, 1f);
            return;
        }
        if (curPath == 11) {   // Mystery Pryer: an eyeball that looks around
            push(); tr(0, 1.1f + bob, 0); rY(face + (float) Math.sin(t * .8f) * .7f); rX((float) Math.sin(t * .6f) * .25f); eyeball(.42f, P1); pop();
            return;
        }
        stand(c.core, 0, 1.1f + bob, 0, seq == 4 ? .6f : .46f, seq == 4 ? .6f : .46f, t * 1.8f, rgba(mix(P1, 0xFFFFFF, .4f), pulse));
        int motes = seq == 4 ? 5 : 3;
        for (int i = 0; i < motes; i++) {
            float a = t * 1.1f + i * TAU / motes;
            bb("mote", (float) Math.cos(a) * .5f, .6f + .5f * fract(t * .4f + i * .31f), (float) Math.sin(a) * .5f, .16f, .16f, 0, rgba(P1, 1f));
        }
    }

    /** The pathway symbol: a single solid bright plane that gently swings so it can be read from most sides. */
    private void sym(float y, float size, float spin) {
        symPlane(curPath, 0, y, 0, size, -face + (float) Math.sin(t * .5f) * .8f, 1f);
    }

    private void epic(Cfg c, int seq) {
        boolean two = seq == 2;
        float tp2 = two ? 3.8f : 3.2f;
        column("beam", 0, 0.05f, tp2, 0, two ? .7f : .5f, rgba(P1, .6f));
        column("beam", 0, 0.05f, tp2, 0, .2f, rgba(P3, .6f));
        flat("ring_dash", 0, .05f, 0, 2.4f, -t * .4f, rgba(P1, .9f));
        flat("ring", 0, .06f, 0, 1.3f, t * .6f, rgba(P2, .9f));
        flat("ring_thick", 0, .07f, 0, 3f, t * .15f, rgba(P2, .6f));
        float cy = 1.9f + (float) Math.sin(t * 1.6) * .08f;
        if (curPath == 9) glitchSym(0, cy, 0, 1.9f, -face); else symPlane(curPath, 0, cy, 0, 1.9f, -face + (float) Math.sin(t * .5f) * .8f, 1f);
        int med = two ? 8 : 6;
        for (int i = 0; i < med; i++) {
            float a = -t * .5f + i * TAU / med, r = 1.5f, y = .75f + (float) Math.sin(t * 2 + i) * .08f;
            symPlane(curPath, (float) Math.cos(a) * r, y, (float) Math.sin(a) * r, .5f, a + PI / 2, .95f);
        }
        epicProps(c, seq, cy);
        for (int i = 0; i < 12; i++) {
            float p = fract(t * .3f + h(i, 1));
            bb("mote", (h(i, 2) - .5f) * 2f, p * tp2, (h(i, 3) - .5f) * 2f, .16f, .16f, 0, rgba(P1, fd(p)));
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
    /** A real fire: many flame tongues that rise, sway, shrink and cool from white-yellow to red, plus embers. */
    private void flameAt(float x, float y, float z, float size, int seed, int outer, int mid, int core, float alpha) {
        int n = Math.max(8, (int) (10 + size * 2.2f));
        for (int i = 0; i < n; i++) {
            float life = .9f + .5f * h(i, seed + 3), p = fract(t * life + h(i, seed + 1));
            float ang = h(i, seed + 2) * TAU, rr = (float) Math.sqrt(h(i, seed + 4)) * size * .22f * (1f - p * .6f);
            float sway = (float) Math.sin(t * 3f + i * 1.7f + p * 5f) * size * .14f * p, sway2 = (float) Math.cos(t * 2.6f + i * 2.3f + p * 4f) * size * .14f * p;
            float px = x + (float) Math.cos(ang) * rr + sway, py = y + p * size * (.8f + .35f * h(i, seed + 5)), pz = z + (float) Math.sin(ang) * rr + sway2;
            float sz = size * (.55f + .4f * h(i, seed + 6)) * (1f - p * .6f);
            int col = p < .25f ? mix(core, mid, p * 4f) : p < .65f ? mix(mid, outer, (p - .25f) / .4f) : outer;
            float a = alpha * Math.min(1f, p * 7f) * (float) Math.pow(1f - p, .55);
            bb("flame", px, py + sz * .3f, pz, sz * .75f, sz, (float) Math.sin(t * 4 + i) * .3f * p + sway * .3f, rgba(col, a));
        }
        for (int i = 0; i < Math.max(3, n / 4); i++) {
            float p = fract(t * .7f + h(i, seed + 9));
            bb("spark", x + (h(i, seed + 10) - .5f) * size * .6f + (float) Math.sin(t * 2 + i) * size * .2f * p, y + p * size * 1.8f, z + (h(i, seed + 11) - .5f) * size * .6f, size * .09f, size * .09f, t + i, rgba(mid, (1 - p)));
        }
        bb("glow", x, y + size * .15f, z, size * .8f, size * .35f, 0, rgba(outer, .35f * alpha));
    }
    /** A dark sky dome plus a shaded ground disc. Seen from outside it is nearly clear so you can see what is inside. */
    private void dome(float rad, int rgb, int alpha) {
        boolean inside = camDist < rad * .97f;
        int a1 = inside ? alpha : Math.max(0, (int) (alpha * .10f)), a2 = inside ? (int) (alpha * .92f) : (int) (alpha * .06f);
        solid(true);
        int lat = 10, lon = 28; float[] hint = v3(0, 0, 0);
        for (int i = 0; i < lat; i++) {
            float l0 = PI / 2 * i / lat, l1 = PI / 2 * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float g0 = TAU * j / lon, g1 = TAU * (j + 1) / lon;
                sq(sp(0, 0, 0, rad, rad, rad, l0, g0), sp(0, 0, 0, rad, rad, rad, l0, g1), sp(0, 0, 0, rad, rad, rad, l1, g1), sp(0, 0, 0, rad, rad, rad, l1, g0), hint, rgb, a1);
            }
        }
        for (int j = 0; j < lon; j++) {   // shade the ground so it is night down here too
            float g0 = TAU * j / lon, g1 = TAU * (j + 1) / lon, rr = rad * .98f;
            sq(v3(0, .04f, 0), v3((float) Math.cos(g0) * rr, .04f, (float) Math.sin(g0) * rr), v3((float) Math.cos(g1) * rr, .04f, (float) Math.sin(g1) * rr), v3(0, .04f, 0), v3(0, -9, 0), rgb, a2);
        }
        solid(false);
    }
    private void pushFace() { push(); rY(face); }
    private int thQ;
    private void pushThrone() { push(); rY(thQ * PI / 2); }
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
            case 0 -> moon(N); case 1 -> door(N, m); case 2 -> justiciar(N); case 3 -> demoness(N, m); case 4 -> sun(N);
            case 5 -> death(N); case 6 -> abyss(N, m); case 7 -> redPriest(N); case 8 -> blackEmperor(N, m); case 9 -> error(N);
            case 10 -> wheel(N); case 11 -> hermit(N); case 12 -> mother(N); case 13 -> chained(N, m); case 14 -> whiteTower(N);
            case 15 -> sailor(N, m); case 16 -> paragon(N); case 17 -> hangedMan(m); case 18 -> fool(N, m); case 19 -> darkness(N, m);
            case 20 -> visionary(N); default -> twilight(N);
        }
    }
    /** The symbol floating in front of the hologram (toward the person who placed the card). */
    private void symFront(float y, float size, float dist) {
        symPlane(curPath, -fx * dist, y, -fz * dist, size, -face + (float) Math.sin(t * .5f) * .8f, 1f);
    }
    private float sideX(float k) { return sx0 * k; }
    private float sideZ(float k) { return sz0 * k; }

    private void moon(int N) {
        boolean m = N == 2;
        dome(R * 4f, 0x05071C, m ? 228 : 205);
        sigil(R); float my = top * .92f, mx = sideX(R * .62f), mz = sideZ(R * .62f);
        if (m) {
            float mr = 3.2f * S;
            sphere(mx, my, mz, mr, mr, mr, 12, 20, 0xDCE4F4, 255);
            bb("disc", mx, my, mz, mr * 2.15f, mr * 2.15f, 0, rgba(0xEAF0FF, 1f));
            bb("corona", mx, my, mz, mr * 6f, mr * 6f, t * .05f, rgba(P1, .8f));
        } else {
            facing("crescent", mx, my, mz, 9 * S, 9 * S, 0.5f, rgba(mix(P1, 0xFFFFFF, .6f), 1f));
            bb("corona", mx, my, mz, 12 * S, 12 * S, 0, rgba(P1, .5f));
        }
        column("beam", mx, 0, my - 3 * S, mz, 1.6f * S, rgba(P1, .35f));
        sym(CY, 3.8f * S, 0);
        for (int i = 0; i < 5 * N; i++) {
            float a = t * .5f + i * TAU / (5 * N), r = R * .6f, y = CY - 1.2f * S + (float) Math.sin(a * 2 + i) * 1.1f * S;
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(t + i); rZ((float) Math.sin(t + i) * .3f);
            flask(.5f * S, mix(P3, 0xFFFFFF, .3f), i % 2 == 0 ? P1 : mix(P1, P2, .5f)); pop();
            glint((float) Math.cos(a) * r, y + .4f * S, (float) Math.sin(a) * r, 1.0f * S, P3, 1f);
        }
        mcStars(40, R * 3.2f, 0xFFFFFF, P1, .5f * S);
        sprites("petal", 40 * N, SPIRALM, R * .8f, 0, top * .8f, .7f * S, P1, 1f, .14f, 40, true);
        sprites("leaf", 24 * N, FALLM, R, 0, top, .8f * S, P2, 1f, .12f, 20, true);
        clouds(5 * N, R, top * .55f, P2, .35f, R);
    }
    private void door(int N, boolean m) {
        sigil(R);
        float H = m ? 3.6f : 2.3f;
        pushFace(); rY(PI);
        boolean ok = m ? drawGeo("high_sequence_door", "lotm", "textures/entity/high_sequence_door.png", H) : drawGeo("mid_sequence_door", "lotm", "textures/entity/mid_sequence_door.png", H);
        pop();
        if (!ok) { pushFace(); doorModel(H * .7f, H, mix(P2, 0x182030, .3f), mix(P2, 0x0A1428, .2f), P3, -1.15f); pop(); }
        facing("rays", 0, H * .55f, 0, H * 4.5f, H * 4.5f, t * .08f, rgba(P1, .5f));
        column("beam", 0, 0, H * 1.3f, 0, H * .5f, rgba(P1, .3f));
        sym(H + 3.4f * S, 3.6f * S, 0);
        for (int i = 0; i < 6 * N; i++) {
            float a = t * .6f + i * TAU / (6 * N), r = H * 1.9f, y = H * .6f + (float) Math.sin(a * 2 + i) * .9f * S;
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(-a); rX(t * 2 + i); tr(0, -.5f * S, 0); key(1.1f * S, metal(.6f)); pop();
            glint((float) Math.cos(a) * r, y, (float) Math.sin(a) * r, 1.1f * S, P3, 1f);
        }
        sprites("spark", 60 * N, RADIALM, R * 1.2f, H * .55f, 0, .5f * S, P3, 1f, .3f, 5, true);
        sprites("mote", 50 * N, RISEM, R * .6f, 0, top, .35f * S, P1, 1f, .25f, 6, false);
    }
    private void justiciar(int N) {
        sigil(R);
        for (int i = 0; i < 8 * N; i++) { float a = i * TAU / (8 * N); column("beam", (float) Math.cos(a) * R * .8f, 0, top * .85f, (float) Math.sin(a) * R * .8f, 1.2f * S, rgba(P1, .35f)); column("line", (float) Math.cos(a) * R * .8f, 0, top * .9f, (float) Math.sin(a) * R * .8f, .45f * S, rgba(P3, .8f)); }
        float L = 3.8f * S, tilt = (float) Math.sin(t * .6f) * .12f;
        pushFace(); tr(0, .5f * S, 0);
        frustum(0, L, .04f * L, .028f * L, 10, 1, 1, metal(.5f), 255, true); frustum(0, .07f * L, .2f * L, .15f * L, 12, 1, 1, metal(.3f), 255, true);
        sphere(0, L, 0, .045f * L, .045f * L, .045f * L, 5, 8, P3, 255);
        push(); tr(0, L, 0); rZ(tilt); box(0, 0, 0, .56f * L, .018f * L, .02f * L, metal(.6f), 255);
        for (int s = -1; s <= 1; s += 2) { push(); tr(s * .54f * L, 0, 0); rZ(-tilt);
            for (int k = 0; k < 3; k++) { push(); rY(k * TAU / 3); frustum(-.46f * L, 0, .006f * L, .006f * L, 4, 1, 1, metal(.4f), 255, false); pop(); }
            tr(0, -.48f * L, 0); frustum(-.06f * L, 0, .09f * L, .24f * L, 14, 1, 1, P1, 255, true); pop(); }
        pop(); pop();
        sym(CY + L * .55f + 1.5f * S, 3.4f * S, 0);
        for (int i = 0; i < 6 * N; i++) { float a = -t * .1f + i * TAU / (6 * N), r = R * .62f; float y = top * .5f + (float) Math.sin(t * 1.2f + i) * .4f;
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rZ(PI); rY(a);
            if (!drawGeo("sword_of_dawn", "lotm", "textures/item/sword_of_dawn.png", 3.6f * S)) sword(3.4f * S, metal(.85f), P1, P3); pop();
            column("line", (float) Math.cos(a) * r, y - 3.6f * S, y, (float) Math.sin(a) * r, .3f * S, rgba(P3, .4f)); }
        sprites("spark", 70 * N, FALLM, R, 0, top, .4f * S, P3, 1f, .15f, 6, true);
    }
    private void demoness(int N, boolean m) {
        sigil(R);
        flat("star", 0, .1f, 0, R * 1.3f, t * .15f, rgba(P1, .9f)); flat("ring_dash", 0, .11f, 0, R * 1.0f, -t * .2f, rgba(P3, .8f));
        facing("disc", sideX(-R * .6f), top * .85f, sideZ(-R * .6f), 6 * S, 6 * S, 0, rgba(mix(P2, 0x500018, .2f), 1f));
        facing("corona", sideX(-R * .6f), top * .85f, sideZ(-R * .6f), 10 * S, 10 * S, t * .1f, rgba(P2, .7f));
        for (int s = -1; s <= 1; s += 2) {
            facing("wing", s * sx0 * 5.4f * S, CY + .2f * S, s * sz0 * 5.4f * S, s * 6f * S, 5.4f * S, 0, rgba(P1, .9f));
            facing("wing", s * sx0 * 6.8f * S, CY - 1.2f * S, s * sz0 * 6.8f * S, s * 3.8f * S, 3.6f * S, 0, rgba(P2, .85f));
        }
        push(); tr(0, 0, 0); rY(t * .1f); rose(3.4f * S, P1, mix(P1, P2, .6f), mix(P2, 0x103018, .3f)); pop();
        sym(CY + 2.2f * S, 3.4f * S, 0);
        for (int i = 0; i < 6 * N; i++) { float a = t * .35f + i * TAU / (6 * N), r = R * .62f, y = 1.6f * S + (float) Math.sin(a * 2 + i) * 1.1f * S;
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(-a + PI / 2 + (float) Math.sin(t + i) * .3f); rZ((float) Math.sin(t * .9f + i) * .15f); mirrorModel(.5f * S, metal(.5f), mix(P1, 0xFFFFFF, .4f)); pop();
            glint((float) Math.cos(a) * r, y, (float) Math.sin(a) * r, .9f * S, P3, 1f); }
        sprites("petal", 120 * N, FALLM, R, 0, top, .7f * S, P1, 1f, .16f, 7, true);
        fogRing(10, R * .6f, P2, .5f);
        if (m) strikes(2, R, top, P1);
    }
    private void sun(int N) {
        sigil(R); float sy = top * .9f;
        flareSun(0, sy, 0, 2.6f * S, P1, P2);
        column("beam", 0, .05f, sy, 0, 2f * S, rgba(P1, .35f));
        sym(CY, 3.4f * S, 0);
        for (int i = 0; i < 10 * N; i++) { float a = t * .3f + i * TAU / (10 * N); column("line", (float) Math.cos(a) * R * .6f, 0, 5f * S, (float) Math.sin(a) * R * .6f, .3f * S, rgba(P1, .9f)); }
        sprites("note", 40 * N, RADIALM, R * 1.1f, CY, 0, 1.1f * S, P1, 1f, .22f, 9, true);
        sprites("spark", 70 * N, RISEM, R, 0, top, .35f * S, 0xFFFFFF, 1f, .3f, 11, true);
    }
    private void death(int N) {
        sigil(R); float s = .95f * S;
        pushThrone(); throneModel(s, mix(P1, 0x203024, .6f), mix(P1, 0xFFFFFF, .2f)); paleEmperor(s); pop();
        float[] w = thW(.78f * s, .32f * s);
        flameAt(w[0], 3.75f * s, w[1], 1.6f * S, 3, P2, P1, P3, .95f);
        sym(4.8f * s + 1.8f * S, 3.2f * S, 0);
        for (int i = 0; i < 8 * N; i++) { float a = i * TAU / (8 * N) + .2f, rr = R * .9f;
            push(); tr((float) Math.cos(a) * rr, 0, (float) Math.sin(a) * rr); rY(PI - a + PI / 2);
            box(0, 1.0f * S, 0, .65f * S, 1.0f * S, .18f * S, 0x707878, 255); sphere(0, 2.0f * S, 0, .65f * S, .5f * S, .18f * S, 5, 10, 0x707878, 255);
            box(0, 1.5f * S, .19f * S, .08f * S, .42f * S, .01f * S, P1, 255); box(0, 1.7f * S, .19f * S, .28f * S, .07f * S, .01f * S, P1, 255); pop(); }
        for (int i = 0; i < 6 * N; i++) { float a = -t * .15f + i * TAU / (6 * N), rr = R * .6f; flameAt((float) Math.cos(a) * rr, 0, (float) Math.sin(a) * rr, 2.2f * S, i, P2, P1, P3, .9f); }
        sprites("mote", 70 * N, RISEM, R, 0, top, .45f * S, P1, 1f, .25f, 14, false);
        sprites("smoke", 24 * N, RISEM, R, 0, top * .8f, 2.4f * S, P2, .6f, .12f, 13, true);
        fogRing(10, R * .6f, P1, .4f);
    }
    private void abyss(int N, boolean m) {
        dome(R * 3.6f, 0x0A0014, m ? 235 : 215);
        flat("whirl", 0, .06f, 0, 2.2f * R, -t * .5f, rgba(0x7A22B0, .9f)); flat("whirl", 0, .08f, 0, 1.4f * R, t * .8f, rgba(0xA01830, .9f));
        sigil(R);
        for (int i = 0; i < 8; i++) { float pw = fract(t * .25f + i / 8f); flat("ring", 0, .1f + i * .02f, 0, (1 - pw) * 2.2f * R, 0, rgba(P1, fd(pw))); }
        float vr = 2.2f * S, vy = CY + 1.4f * S;
        flat("whirl", 0, vy, 0, 9 * S, t * 1.2f, rgba(0xB02040, .95f)); flat("whirl", 0, vy, 0, 6 * S, -t * 1.8f, rgba(0x9A50E0, .9f));
        sphere(0, vy, 0, vr, vr, vr, 10, 16, 0x050205, 255);
        sym(vy + vr * 1.3f + 3.4f * S, 3.4f * S, 0);
        int dark = mix(P2, 0x1A0830, .5f);
        for (int i = 0; i < 6; i++) wrapTentacle(0, vy, 0, vr, R * .65f, i * TAU / 6 + t * .03f, i % 2 == 0 ? 1f : -1f, i, dark, .6f * S);
        mcStars(70, R * 2.8f, 0xFF3050, 0xB060FF, .55f * S);
        for (int i = 0; i < 12 * N; i++) { float a = i * TAU / (12 * N) + t * .08f, r = R * .85f;
            column("crack", (float) Math.cos(a) * r, 0, top * (.5f + .4f * h(i, 3)), (float) Math.sin(a) * r, 1.5f * S, rgba(P1, .9f)); }
        sprites("mote", 90 * N, SPIRALM, R, 0, top, .4f * S, P1, 1f, .2f, 16, false);
        sprites("smoke", 24 * N, DRIFTM, R, .5f, 6f, 4f * S, P2, .6f, .1f, 15, true);
        strikes(2 * N, R, top, P1);
    }
    /** A tentacle that grows out of the ground along +Y and curls toward local +X, wriggling. */
    private void tentacle(float len, float r0, float bend, int seed, int rgb) {
        int segs = 14; float seg = len / segs;
        push();
        for (int i = 0; i < segs; i++) {
            float ra = r0 * (1f - .85f * i / segs), rb = r0 * (1f - .85f * (i + 1) / segs);
            frustum(0, seg, ra, rb, 8, 1, 1, rgb, 255, i == segs - 1); tr(0, seg, 0);
            rZ(-bend / segs + (float) Math.sin(t * 1.6f + i * .5f + seed) * .09f); rX((float) Math.cos(t * 1.3f + i * .45f + seed) * .08f);
            if (i % 3 == 1) { push(); rY(PI / 2); tr(ra * .5f, 0, 0); sphere(0, 0, 0, ra * .5f, ra * .5f, ra * .5f, 3, 5, mix(rgb, P1, .5f), 255); pop(); }
        }
        pop();
    }

    private void redPriest(int N) {
        sigil(R);
        facing("rays", 0, CY, 0, 12 * S, 12 * S, t * .15f, rgba(P1, .4f));
        pushFace();
        int ir = mix(P2, 0x201008, .5f);
        frustum(0, 1.2f * S, .5f * S, .35f * S, 12, 1, 1, ir, 255, true); frustum(1.2f * S, 1.7f * S, .35f * S, 1.1f * S, 14, 1, 1, ir, 255, true);
        for (int i = 0; i < 3; i++) { push(); rY(i * TAU / 3); rZ(.25f); frustum(0, 1.3f * S, .12f * S, .08f * S, 6, 1, 1, ir, 255, false); pop(); }
        for (int s = -1; s <= 1; s += 2) { push(); tr(s * 2.2f * S, 0, -1.8f * S); rZ(-s * .35f);
            if (!drawGeo("spear_of_dawn", "lotm", "textures/entity/spear_of_dawn.png", 9f * S)) sword(8f * S, metal(.8f), ir, P1); pop(); }
        pop();
        flameAt(0, 1.7f * S, 0, 6f * S, 1, P2, P1, P3, 1f);
        sym(CY + 5.5f * S, 3.4f * S, 0);
        for (int i = 0; i < 8 * N; i++) { float a = i * TAU / (8 * N), rr = R * .75f;
            push(); tr((float) Math.cos(a) * rr, 0, (float) Math.sin(a) * rr); frustum(0, 2.8f * S, .12f * S, .1f * S, 6, 1, 1, ir, 255, true); frustum(2.8f * S, 3.1f * S, .1f * S, .26f * S, 8, 1, 1, ir, 255, true); pop();
            flameAt((float) Math.cos(a) * rr, 3.1f * S, (float) Math.sin(a) * rr, 2.2f * S, i + 5, P2, P1, P3, .95f); }
        sprites("spark", 90 * N, RISEM, R, 0, top * 1.1f, .3f * S, P3, 1f, .5f, 18, true);
        sprites("smoke", 16 * N, RISEM, R * .5f, top * .3f, top, 3f * S, mix(P2, 0x000000, .6f), .5f, .1f, 19, true);
    }
    private void blackEmperor(int N, boolean m) {
        dome(R * 4f, 0x000000, m ? 215 : 150);
        sigil(R);
        for (int i = 0; i < 6 * N; i++) { float a = i * TAU / (6 * N) + .3f; column("beam", (float) Math.cos(a) * R * .5f, 0, top, (float) Math.sin(a) * R * .5f, 1.4f * S, rgba(P1, .35f)); }
        pushThrone(); throneModel(.95f * S, mix(P3, 0x000000, .2f), metal(.6f)); pop();
        pushFace(); tr(0, 4.6f * S, 0); rY(t * .4f); crown(1.3f * S, metal(.7f), P1); pop();
        sym(CY + 5.6f * S, 3.2f * S, 0);
        for (int i = 0; i < 6 * N; i++) { float a = i * TAU / (6 * N) + .1f, r = R * .55f * (.7f + .3f * (i % 2)); push(); tr((float) Math.cos(a) * r, top + 1f, (float) Math.sin(a) * r); chain(top + 1f - 4f * S, .8f * S, mix(0x50545C, P1, .1f)); pop(); }
        for (int i = 0; i < 14 * N; i++) { float a = i * TAU / (14 * N), r = R * .85f; push(); tr((float) Math.cos(a) * r, 0, (float) Math.sin(a) * r); taper(0, (3f + 3f * h(i, 5)) * S, .35f * S, .35f * S, 0, 0, mix(P3, 0x000000, .2f), 255); pop(); }
        sprites("spark", 70 * N, FALLM, R, 0, top, .45f * S, 0xFFFFFF, 1f, .15f, 20, true);
        fogRing(8, R * .6f, P2, .4f);
    }

    /** Marauder: a server error. Stepped, choppy time; glitching symbol; error windows; binary rain; blocks that glitch onto the real ground. */
    private void error(int N) {
        float t0 = t; t = (float) Math.floor(t * 4f) / 4f;
        int PUR = 0xA050FF;
        sigil(R);
        glitchSym(0, CY, 0, 5.6f * S, -face);
        int nw = 3 * N;
        for (int i = 0; i < nw; i++) {   // error windows that jump around, each in its own sector so they do not pile up
            int slot = (int) Math.floor(t / 1.4f + h(i, 5) * 3);
            float a = (i + .15f + .7f * h(slot * 5 + i, 71)) * TAU / nw, r = R * (.75f + .3f * h(slot * 3 + i, 72)), y = 1.5f * S + h(slot * 7 + i, 73) * top * .55f;
            if (fract(t * .7f + h(i, 8)) < .85f) panel("errwin", (float) Math.cos(a) * r, y, (float) Math.sin(a) * r, 3.0f * S, 1.9f * S, a + PI / 2, rgba(0xFFFFFF, 1f));
        }
        String[] words = {"errtext", "nulltext", "deadtext", "t404"};
        for (int i = 0; i < 2; i++) { if (fract(t * .5f + h(i, 31)) < .6f) { int slot = (int) Math.floor(t * .5f + h(i, 31));
            float a = (i * .5f + .1f + .3f * h(slot + i, 81)) * TAU, r = R * (.5f + .3f * h(slot + i, 82)); bb(words[(slot + i) & 3], (float) Math.cos(a) * r, 1f * S + top * (.45f + .4f * h(slot + i, 83)), (float) Math.sin(a) * r, 4f * S, 1f * S, 0, rgba(0xFFFFFF, 1f)); } }
        for (int c = 0; c < 5 * N; c++) {   // binary rain
            float a = c * TAU / (5 * N) + .3f, r = R * (.35f + .35f * h(c, 4));
            for (int k = 0; k < 8; k++) { float yy = top - fract(t * .35f * (.6f + h(c, 5)) + k / 8f + h(c, 6)) * top; bb(((c + k + (int) (t * 2)) & 1) == 0 ? "bit0" : "bit1", (float) Math.cos(a) * r, yy, (float) Math.sin(a) * r, .7f * S, .7f * S, 0, rgba(P3, 1f)); }
        }
        for (int k = 0; k < 3; k++) {   // 3x3 patches of glitched ground that lie on the real blocks and last a while
            float q = t / 3.4f + k / 3f; int slot = (int) Math.floor(q); float ph = fract(q), fa = Math.min(1f, Math.min(ph, 1f - ph) * 5f);
            int bx = (int) Math.floor(ex + (h(slot * 3 + k, 91) - .5f) * 2 * R * .8f), bz = (int) Math.floor(ez + (h(slot * 5 + k, 92) - .5f) * 2 * R * .8f);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                double tp2 = groundTop(bx + dx, (int) Math.floor(ey), bz + dz); boolean w = ((dx + dz) & 1) == 0;
                quad("solid", (float) (bx + dx + .5 - ex), (float) (tp2 - ey) + .03f, (float) (bz + dz + .5 - ez), .5f, 0, 0, 0, 0, .5f, rgba(w ? 0xFFFFFF : PUR, fa * (w ? .7f : .85f)));
            }
        }
        for (int k = 0; k < 2; k++) { if (fract(t * 1.7f + h(k, 9)) < .35f) { float yy = h((int) (t * 6) + k, 41) * top; float dx2 = (h((int) (t * 6) + k, 42) - .5f) * 4 * S;
            quad("solid", dx2, yy, 0, R * .9f, 0, 0, 0, .12f * S, 0, rgba(P3, .5f)); quad("solid", dx2, yy, 0, 0, 0, R * .9f, 0, .12f * S, 0, rgba(PUR, .5f)); } }
        for (int k = 0; k < 6; k++) { float a = k * TAU / 6 + .3f; if (fract(t * 2f + h(k, 5)) > .3f) column("line", (float) Math.cos(a) * R * .85f, 0, top * (.5f + .5f * h(k, 6)), (float) Math.sin(a) * R * .85f, .5f * S, rgba(k % 2 == 0 ? P1 : PUR, .8f)); }
        sprites("mote", 60 * N, DRIFTM, R, 0, top, .4f * S, PUR, 1f, .6f, 21, false);
        t = t0;
    }
    private void wheel(int N) {
        sigil(R);
        flat("fortune", 0, .1f, 0, 1.9f * R, t * .2f, rgba(P2, .45f));
        float wr = 3.6f * S, wy = Math.max(top * .55f, wr + .5f), yaw = -face + (float) Math.sin(t * .35f) * .6f, c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
        float roll = t * .35f, cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
        eq(tex("fortune"), 0, wy, 0, c * cr * wr, sr * wr, s * cr * wr, -c * sr * wr, cr * wr, -s * sr * wr, rgba(mix(0xFFFFFF, P3, .1f), 1f), 0, 0, 1, 1);
        push(); tr(0, wy, 0); rY(-yaw); rZ(roll); ring(wr * .99f, .06f * wr, metal(.7f), 255); pop();
        symPlane(curPath, 0, wy, 0, wr * .5f, yaw, 1f);
        rays(wy, 11 * S, P1, .4f, .12f);
        for (int i = 0; i < 4 * N; i++) {
            float a = t * .4f + i * TAU / (4 * N), r = R * .85f, y = 1.4f * S + (float) Math.sin(a * 2 + i) * .8f * S;
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(t * 1.3f + i); rX(t * 1.7f + i); die(.65f * S); pop();
        }
        sprites("spark", 70 * N, RADIALM, R * 1.2f, wy, 0, .5f * S, 0xFFFFFF, 1f, .3f, 22, true);
        sprites("diamond", 18 * N, RISEM, R, 0, top, .5f * S, P1, 1f, .2f, 23, true);
    }
    private void hermit(int N) {
        sigil(R);
        float ly = top * .75f;
        column("beam", 0, 0, ly, 0, 2f * S, rgba(P3, .28f));
        push(); tr(0, top + 2f, 0); chain(top + 2f - ly - 3.6f * S, .9f * S, metal(.3f)); pop();
        push(); tr(0, ly - 3.6f * S, 0); rY(t * .2f); lanternModel(4.4f * S, metal(.35f), mix(P3, 0xFFFFFF, .3f)); pop();
        flameAt(0, ly - 3.0f * S, 0, 2.0f * S, 4, P3, mix(P3, 0xFFFFFF, .4f), 0xFFFFFF, 1f);
        bb("glow", 0, ly - 2.4f * S, 0, 7f * S, 7f * S, 0, rgba(P3, .85f));
        sym(CY - .2f * S, 3.4f * S, 0);
        for (int i = 0; i < 7 * N; i++) { float a = t * .15f + i * TAU / (7 * N), r = R * .7f, y = 2f * S + 1.8f * S * (i % 3);
            push(); tr((float) Math.cos(a) * r, y + 2.4f * S, (float) Math.sin(a) * r); chain(2.2f * S, .45f * S, metal(.3f)); pop();
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(a); lanternModel(1.5f * S, metal(.35f), mix(P3, 0xFFFFFF, .3f)); pop();
            flameAt((float) Math.cos(a) * r, y + .3f * S, (float) Math.sin(a) * r, .7f * S, i, P3, mix(P3, 0xFFFFFF, .4f), 0xFFFFFF, 1f);
            bb("glow", (float) Math.cos(a) * r, y + .6f * S, (float) Math.sin(a) * r, 3f * S, 3f * S, 0, rgba(P3, .8f)); }
        for (int i = 0; i < 9 * N; i++) {   // eyes opening in the dark
            float a = t * .1f + i * TAU / (9 * N), r = R * (.55f + .35f * h(i, 2)), y = 1.5f * S + h(i, 3) * top * .75f, bl = Math.max(.12f, Math.abs((float) Math.cos(t * .5f + i * 1.9f)));
            bb("eye", (float) Math.cos(a) * r, y, (float) Math.sin(a) * r, 2.4f * S, 1.2f * S * bl, 0, rgba(P1, 1f)); }
        sprites("mote", 40 * N, DRIFTM, R, .5f, top, .4f * S, 0xFFFFFF, 1f, .2f, 24, false);
        fogRing(10, R * .6f, P2, .5f);
    }
    private void mother(int N) {
        sigil(R);
        facing("rays", 0, CY, 0, 12 * S, 12 * S, t * .1f, rgba(P1, .35f));
        push(); rY(t * .1f); flowerModel(3.6f * S, mix(P1, 0xFFFFFF, .15f), mix(P1, P3, .5f), 0xFFD860, mix(P2, 0x103018, .2f)); pop();
        sym(CY + 4.4f * S, 3.4f * S, 0);
        for (int i = 0; i < 6 * N; i++) { float a = i * TAU / (6 * N) + t * .08f, rr = R * .75f;
            push(); tr((float) Math.cos(a) * rr, 0, (float) Math.sin(a) * rr); rY(a); rZ((float) Math.sin(t + i) * .06f); flowerModel(1.6f * S, mix(P3, 0xFFFFFF, .1f), P1, 0xFFD860, mix(P2, 0x103018, .2f)); pop(); }
        sprites("petal", 100 * N, SPIRALM, R, 0, top, .7f * S, P3, 1f, .14f, 25, true);
        sprites("leaf", 30 * N, FALLM, R, 0, top, 1f * S, P2, 1f, .12f, 26, true);
        sprites("mote", 80 * N, RISEM, R, 0, top, .3f * S, 0xFFF0A0, 1f, .25f, 27, false);
    }
    private void chained(int N, boolean m) {
        sigil(R); int iron = mix(0x707890, P1, .15f);
        column("beam", 0, 0, top, 0, 2.6f * S, rgba(P1, .22f));
        sym(CY, 3.4f * S, 0);
        float cr = R * .34f, chh = top * .78f; int bars = 14;   // an iron cage around the symbol
        for (int i = 0; i < bars; i++) { float a = i * TAU / bars; push(); tr((float) Math.cos(a) * cr, 0, (float) Math.sin(a) * cr); frustum(0, chh, .09f * S, .09f * S, 6, 1, 1, iron, 255, true); pop(); }
        for (int k = 0; k < 3; k++) { push(); tr(0, chh * (k == 0 ? .02f : k == 1 ? .5f : 1f), 0); rX(PI / 2); torus(0, 0, 0, cr, cr, .12f * S, 24, 5, 0, TAU, mix(iron, 0xFFFFFF, .15f), 255); pop(); }
        for (int i = 0; i < 4 * N; i++) { float a = t * .3f + i * TAU / (4 * N), r = cr * 1.5f;
            push(); tr((float) Math.cos(a) * r, chh * .7f + (float) Math.sin(t + i) * .5f * S, (float) Math.sin(a) * r); rY(-a); lockModel(.9f * S, mix(iron, 0xFFFFFF, .1f), metal(.7f)); pop(); }
        for (int i = 0; i < 7 * N; i++) { float a = i * TAU / (7 * N), r = R * (.6f + .3f * h(i, 2)); push(); tr((float) Math.cos(a) * r + (float) Math.sin(t * .8f + i) * .15f * S, top + 1.5f, (float) Math.sin(a) * r); chain(top + 1.5f - 1.4f * S * (1 + h(i, 3)), .9f * S, iron); pop(); }
        if (m) for (int i = 0; i < 28; i++) { float a = -t * .12f + i * TAU / 28, rr = R * .55f; push(); tr((float) Math.cos(a) * rr, CY + 2.8f * S, (float) Math.sin(a) * rr); rY(-a); if ((i & 1) == 1) rX(PI / 2);
            torus(0, 0, 0, .28f * S, .5f * S, .1f * S, 8, 4, 0, TAU, iron, 255); pop(); }
        for (int i = 0; i < 3 * N; i++) { float a = i * TAU / (3 * N) + .6f, r = R * .75f;
            push(); tr((float) Math.cos(a) * r, 0, (float) Math.sin(a) * r); rY(-a + PI / 2); torus(0, .55f * S, 0, .55f * S, .6f * S, .12f * S, 14, 4, -.4f, PI + .4f, iron, 255); box(0, -.1f * S, 0, .6f * S, .16f * S, .18f * S, mix(iron, 0x000000, .3f), 255); pop();
            push(); tr((float) Math.cos(a) * r, .6f * S, (float) Math.sin(a) * r); chain(3.2f * S * (1f), .5f * S, iron); pop(); }
        sprites("chain", 14 * N, FALLM, R, 0, top, .9f * S, mix(iron, 0xFFFFFF, .3f), 1f, .2f, 28, true);
        sprites("spark", 40 * N, RISEM, R, 0, top, .35f * S, P1, 1f, .3f, 29, true);
        fogRing(8, R * .6f, P2, .45f);
    }
    private void whiteTower(int N) {
        sigil(R); float H = top * .92f;
        pushFace(); towerModel(H, 1.6f * S, mix(P1, 0x3A4A70, .55f), mix(P1, 0x6078A0, .4f), P3); pop();
        for (int i = 0; i < 2; i++) { float a = t * .5f * (i == 0 ? 1 : -1) + i * PI; float len = 8 * S;
            quad("beam", (float) Math.cos(a) * len / 2, H * .93f, (float) Math.sin(a) * len / 2, 0, .3f * S, 0, (float) Math.cos(a) * len / 2, 0, (float) Math.sin(a) * len / 2, rgba(P3, .35f)); }
        for (int i = 0; i < 7 * N; i++) { float yy = (i + 1) * H / (7 * N + 1); flat("ring_dash", 0, yy, 0, (4f + 1.4f * (float) Math.sin(i * .9f)) * S, t * (i % 2 == 0 ? .7f : -.7f), rgba(i % 2 == 0 ? P1 : P2, .8f)); }
        symFront(CY + .8f * S, 3.2f * S, 3.4f * S);
        sprites("page", 24 * N, SPIRALM, R * .9f, 0, top, 1.3f * S, mix(P1, 0xFFFFFF, .3f), .9f, .13f, 30, true);
        clouds(7 * N, R * 1.2f, top * .3f, mix(P1, 0xFFFFFF, .3f), .22f, R * 1.1f);
        sprites("mote", 60 * N, RISEM, R * .6f, 0, top, .35f * S, P1, 1f, .3f, 31, false);
    }

    private void paragon(int N) {
        sigil(R);
        push(); tr(0, CY, 0); armillary(4.3f * S, mix(P1, 0xFFFFFF, .2f), P3); pop();
        symFront(CY + .4f * S, 3.4f * S, 5.4f * S);
        pushFace(); tr(-6f * S, CY + 1f * S, -1.2f * S); rZ(t * .4f); gear(2.2f * S, 14, .5f * S, metal(.55f), P1); pop();
        pushFace(); tr(-3f * S, CY + 3.8f * S, -1.2f * S); rZ(-t * .6f + .2f); gear(1.4f * S, 10, .45f * S, mix(P1, 0x402008, .3f), P3); pop();
        pushFace(); tr(6f * S, CY - 1.2f * S, -1.2f * S); rZ(t * .48f); gear(1.9f * S, 12, .45f * S, metal(.45f), P1); pop();
        push(); tr(0, .15f, 0); rX(-PI / 2); rZ(t * .12f); gear(R * .5f, 18, .3f * S, mix(P2, 0x402008, .4f), P1); pop();
        for (int k = 0; k < 4; k++) flat("ring_dash", 0, (1 + k * 2.2f) * S, 0, R * 1.6f - k * 1.2f * S, t * (k % 2 == 0 ? .3f : -.4f), rgba(k % 2 == 0 ? P1 : P2, .8f));
        for (int k = 0; k < 6 * N; k++) { float a = t * .5f + k * TAU / (6 * N); bb("clock", (float) Math.cos(a) * R * .8f, 1.5f * S + (k % 4) * 1.5f * S, (float) Math.sin(a) * R * .8f, 1.3f * S, 1.3f * S, 0, rgba(0xFFFFFF, 1f)); }
        sprites("spark", 70 * N, SPIRALM, R, 0, top, .4f * S, P1, 1f, .2f, 32, true);
    }
    private void hangedMan(boolean m) {
        dome(R * 4f, 0x000000, m ? 240 : 225);
        flat("whirl", 0, .06f, 0, 2f * R, t * .3f, rgba(P2, .9f));
        sigil(R);
        float ey = top * 1.25f, er = 3.2f * S, bh = 4.2f * S, headY = 1.5f * S, feetY = headY + bh;
        bb("corona", 0, ey, 0, 13 * S, 13 * S, t * .1f, rgba(P1, .95f));
        sphere(0, ey, 0, er, er, er, 12, 20, 0x000000, 255);
        int iron = mix(0x707078, P1, .15f);
        push(); tr(0, ey - er, 0); chain(ey - er - feetY, .8f * S, iron); pop();
        push(); tr(0, feetY, 0); rY(t * .15f); rZ(PI); bearer(bh); pop();
        bb("glow", 0, feetY - .68f * bh, 0, 2.6f * S, 2.6f * S, 0, rgba(0xFF2020, .7f));
        for (int i = 0; i < 4; i++) { float a = i * TAU / 4 + .4f, r = R * .5f; push(); tr((float) Math.cos(a) * r, 0, (float) Math.sin(a) * r); rY(-a + PI / 2); torus(0, .5f * S, 0, .5f * S, .55f * S, .1f * S, 12, 4, -.3f, PI + .3f, iron, 255); pop();
            column("line", (float) Math.cos(a) * r, .5f * S, feetY - .55f * bh, (float) Math.sin(a) * r * .1f, .25f * S, rgba(P1, .35f)); }
        symFront(CY + .6f * S, 3.4f * S, 3.8f * S);
        sprites("drop", 70, FALLM, R * .9f, 0, top, .7f * S, 0xC01820, 1f, .45f, 34, false);
        for (int i = 0; i < 6; i++) { float pw = fract(t * .5f + i / 6f); flat("ring", (h(i, 61) - .5f) * R, .1f, (h(i, 62) - .5f) * R, pw * 3f * S, 0, rgba(P1, fd(pw))); }
    }
    private void fool(int N, boolean m) {
        sigil(R);
        int fogc = 0x34343E;
        for (int i = 0; i < 16; i++) { float a = t * .07f + i * TAU / 16, rr = R * (.35f + .45f * h(i, 6)); ebb("fog", (float) Math.cos(a) * rr, (.5f + 1.2f * h(i, 7)) * S, (float) Math.sin(a) * rr, 8 * S, 3 * S, 0, rgba(fogc, .8f)); }
        for (int i = 0; i < 10; i++) { float a = -t * .05f + i * TAU / 10; ebb("fog", (float) Math.cos(a) * R * .9f, (1.5f + 3f * h(i, 8)) * S, (float) Math.sin(a) * R * .9f, 9 * S, 3.4f * S, 0, rgba(fogc, .65f)); }
        for (int i = 0; i < 18; i++) {   // small coloured lights drifting in the fog
            float a = t * (.06f + .05f * h(i, 11)) + h(i, 12) * TAU, rr = R * (.2f + .7f * h(i, 13)), y = (.6f + 4f * h(i, 14)) * S + (float) Math.sin(t * .8f + i) * .4f * S, tw = .6f + .4f * (float) Math.sin(t * 1.7f + i * 2f);
            bb("glow", (float) Math.cos(a) * rr, y, (float) Math.sin(a) * rr, .9f * S, .9f * S, 0, rgba(hsv(h(i, 15), .75f), tw));
            bb("mote", (float) Math.cos(a) * rr, y, (float) Math.sin(a) * rr, .22f * S, .22f * S, 0, rgba(0xFFFFFF, tw)); }
        if (m) {   // a bronze table and chairs, like the castle above the fog
            float th = 1.25f * S, hl = 2.7f * S, hw = .85f * S; int bronze = 0xA8702C, trim = 0xD8A050;
            pushFace(); tableModel(hl, hw, th, bronze, trim);
            for (int sd = -1; sd <= 1; sd += 2) for (int k = -1; k <= 1; k++) { push(); tr(k * 1.7f * S, 0, sd * (hw + .75f * S)); rY(sd > 0 ? PI : 0); chairModel(1.5f * S, bronze, trim, 1.3f * S); pop(); }
            push(); tr(hl + .9f * S, 0, 0); rY(-PI / 2); chairModel(1.7f * S, bronze, trim, 2.4f * S); pop();
            pop();
        }
        float ey = CY + (m ? 2.4f : .4f) * S, w = 6.4f * S, yw = -face + (float) Math.sin(t * .35f) * .5f;
        stand("eye", 0, ey, 0, w, w * .52f, yw, rgba(mix(P1, 0xFFFFFF, .3f), .85f));
        stand("ring_thick", 0, ey, 0, w * .34f, w * .34f, yw + .02f, rgba(P3, .7f));
        sym(ey + 4.4f * S, 3.2f * S, 0);
        for (int i = 0; i < 4; i++) column("beam", (i - 1.5f) * 1.2f * S, 0, ey - 1.2f * S, 0, 1.2f * S, rgba(P1, .22f));
        sprites("mote", 50 * N, SPIRALM, R, 0, top, .35f * S, P3, .8f, .18f, 37, false);
    }
    private void darkness(int N, boolean m) {
        dome(R * 4f, 0x000004, m ? 252 : 245);
        sigil(R);
        mcStars(90 * N, R * 3.2f, 0xFFFFFF, P1, .6f * S);
        facing("crescent", sideX(R * 1.2f), top * .9f, sideZ(R * 1.2f), 9 * S, 9 * S, -.4f, rgba(mix(P1, 0xFFFFFF, .5f), .85f));
        sym(CY, 3.6f * S, 0);
        clouds(6 * N, R * 1.2f, top * .4f, P2, .3f, R * 1.3f);
        fogRing(10, R * .7f, P2, .35f);
        sprites("mote", 50 * N, DRIFTM, R * 1.2f, .5f, top, .45f * S, P1, .8f, .2f, 35, false);
    }
    /** Spectator (Visionary): a mind palace. A few vision frames showing other pathways, thought beads, brain waves. */
    private void visionary(int N) {
        sigil(R);
        boolean m = N == 2;
        flat("whirl", 0, .06f, 0, 2f * R, t * .4f, rgba(P2, .9f));
        if (m) flat("eye", 0, .12f, 0, 1.7f * R, face, rgba(P1, .8f));
        sym(CY + .6f * S, 4.2f * S, 0);
        int frames = m ? 6 : 4;
        for (int i = 0; i < frames; i++) {
            float a = t * .1f + i * TAU / frames, r = R * .85f, y = 2.2f * S + (float) Math.sin(t * .7f + i) * .4f * S;
            float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r, yw = a + PI / 2, w = 2.8f * S;
            panel("vframe", x, y, z, w, w, yw, rgba(0xFFFFFF, 1f));
            int shown = (i * 5 + (int) Math.floor(t * .5f + i * .7f)) % Tarot.PATHWAYS.length;
            float fl = fract(t * .5f + i * .7f); float aa = Math.min(1f, Math.min(fl, 1f - fl) * 6f);
            symPlane(shown, x, y, z, w * .6f, yw, aa);
        }
        if (m) for (int i = 0; i < 10; i++) {   // eyes all turned toward the centre
            float a = -t * .15f + i * TAU / 10, r = R * .62f, y = 3.2f * S + (float) Math.sin(t + i * 1.3f) * .6f * S;
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(-a - PI / 2); eyeball(.7f * S, P1); pop(); }
        for (int ribbon = 0; ribbon < 3; ribbon++) for (int k = 0; k < 40; k++) {
            float u = k / 40f, a = t * (.35f + ribbon * .1f) + u * TAU * 1.5f + ribbon * 2.1f, r = R * (.35f + .2f * (float) Math.sin(u * 9 + t));
            bb("mote", (float) Math.cos(a) * r, CY - 1.4f * S + (float) Math.sin(u * TAU * 2 + t * 1.5f + ribbon) * 1.6f * S, (float) Math.sin(a) * r, .4f * S, .4f * S, 0, rgba(ribbon == 1 ? P3 : P1, 1f));
        }
        for (int i = 0; i < 16 * N; i++) { float p = fract(t * .2f + h(i, 3)), a = h(i, 2) * TAU, r = R * .3f * (1 + h(i, 4));
            bb("ring", (float) Math.cos(a) * r, p * top * .9f, (float) Math.sin(a) * r, (.4f + .8f * p) * S, (.4f + .8f * p) * S, 0, rgba(P3, fd(p))); }
        for (int i = 0; i < 4; i++) { float pw = fract(t * .3f + i / 4f); flat("ring", 0, .12f, 0, pw * 2.2f * R, 0, rgba(P3, fd(pw))); }
        sprites("mote", 70 * N, SPIRALM, R, 0, top, .35f * S, P3, 1f, .18f, 37, false);
    }
    private void twilight(int N) {
        sigil(R); boolean m = N == 2;
        facing("rays", 0, 1f * S, 0, R * 2.6f, R * 2.6f, 0, rgba(P1, .3f));
        float L = (m ? 8.5f : 6.8f) * S;
        push(); tr(0, L * .95f, 0); rZ(PI); rY(face);
        boolean ok = m ? drawGeo("sword_of_twilight", "lotm", "textures/item/sword_of_twilight.png", L) : drawGeo("silver_sword", "lotm", "textures/entity/silver_sword_entity.png", L);
        if (!ok) sword(L, metal(.35f), mix(P2, 0x302010, .3f), P1);
        pop();
        symFront(CY + .8f * S, 3.4f * S, 3.6f * S);
        for (int i = 0; i < 12 * N; i++) { float a = i * TAU / (12 * N) + .1f; flat("crack", (float) Math.cos(a) * R * .55f, .1f, (float) Math.sin(a) * R * .55f, 3.2f * S, a, rgba(P1, .8f)); }
        for (int i = 0; i < 6 * N; i++) { float a = -t * .1f + i * TAU / (6 * N), r = R * .85f; flameAt((float) Math.cos(a) * r, 0, (float) Math.sin(a) * r, 2.2f * S, i, P2, P1, P3, .75f); }
        sprites("spark", 50 * N, RISEM, R * 1.1f, 0, top * 1.1f, .3f * S, P3, .9f, .4f, 41, true);
        sprites("smoke", 12 * N, RISEM, R * .7f, 1f, top * .8f, 3f * S, mix(P2, 0x000000, .5f), .45f, .1f, 42, true);
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
        int na = 72; float[] hint = v3(0, -60, 0);
        for (int i = 0; i < na; i++) {
            float a0 = dir * t * .22f + TAU * i / na, a1 = dir * t * .22f + TAU * (i + 1) / na;
            float hh0 = H0 * Math.max(.3f, .6f + .4f * (float) Math.sin(3 * a0 - dir * t * 1.1f)), hh1 = H0 * Math.max(.3f, .6f + .4f * (float) Math.sin(3 * a1 - dir * t * 1.1f));
            for (int k = 0; k < prof.length - 1; k++) {
                float[] q0 = wp(radius, a0, prof[k], hh0), q1 = wp(radius, a1, prof[k], hh1), q2 = wp(radius, a1, prof[k + 1], hh1), q3 = wp(radius, a0, prof[k + 1], hh0);
                float s = (k + .5f) / (prof.length - 1);
                int col = mix(mix(P2, P1, s), 0xFFFFFF, k >= 5 ? .55f : Math.max(0, s - .5f) * .5f);
                sq(q0, q1, q2, q3, hint, col, 215);
            }
        }
        solid(false);
    }
    private float[] wp(float radius, float a, float[] pr, float H) {
        float rr = radius + pr[0] * H * .6f;
        return v3((float) Math.cos(a) * rr, pr[1] * H, (float) Math.sin(a) * rr);
    }

    private void sailor(int N, boolean m) {
        dims(m);
        sigil(R);
        flat("whirl", 0, .06f, 0, 2.2f * R, t * .4f, rgba(P2, .9f)); flat("whirl", 0, .08f, 0, 1.5f * R, -t * .6f, rgba(P1, .9f));
        for (int i = 0; i < 5; i++) { float pw = fract(t * .22f + i / 5f); flat("ring", 0, .1f, 0, pw * 2.2f * R, 0, rgba(P3, fd(pw))); }
        waveRing(R * .75f, 2.8f * S, 20 * N, 1f, P1); waveRing(R * .5f, 2.0f * S, 16 * N, -1f, mix(P1, P2, .4f));
        if (m) waveRing(R * .95f, 3.6f * S, 28, 1f, mix(P1, 0xFFFFFF, .2f));
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
        sym(CY + th * .78f, 3.2f * S, 0);
        fogRing(10, R * .55f, P3, .35f);
    }
}
