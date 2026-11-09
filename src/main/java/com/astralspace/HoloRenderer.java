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
        return TEX.computeIfAbsent(n, k -> k.startsWith("lotm:") ? ResourceLocation.fromNamespaceAndPath("lotm", "textures/particle/fx/" + k.substring(5) + ".png")
            : ResourceLocation.fromNamespaceAndPath("astral_world", "textures/holo/" + k + ".png"));
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
        frustum(0, .78f * L, .02f * L, .02f * L, 8, 1, 1, rgb, 255, true);                       // the shaft
        for (int k = 0; k < 4; k++) frustum(.12f * L + k * .17f * L, .14f * L + k * .17f * L, .03f * L, .03f * L, 8, 1, 1, mix(rgb, gem, .4f), 255, true);   // grip rings
        box(0, .78f * L, 0, .17f * L, .018f * L, .02f * L, rgb, 255);                             // the crossbar the three prongs rise from
        taper(.78f * L, .93f * L, .028f * L, .02f * L, .03f * L, .02f * L, rgb, 255); taper(.93f * L, L, .03f * L, .02f * L, 0, 0, rgb, 255);   // middle prong, a spear
        for (int sd = -1; sd <= 1; sd += 2) {
            push(); tr(sd * .16f * L, .78f * L, 0); taper(0, .14f * L, .02f * L, .016f * L, .022f * L, .016f * L, rgb, 255); taper(.14f * L, .22f * L, .022f * L, .016f * L, 0, 0, rgb, 255);
            push(); tr(0, .08f * L, 0); rZ(sd * 2.4f); taper(0, .06f * L, .014f * L, .012f * L, 0, 0, rgb, 255); pop(); pop();   // a barb on each outer prong
            push(); tr(sd * .045f * L, .93f * L, 0); rZ(sd * 2.4f); taper(0, .05f * L, .012f * L, .012f * L, 0, 0, rgb, 255); pop();
        }
        sphere(0, .78f * L, 0, .035f * L, .035f * L, .035f * L, 5, 8, gem, 255);
        sphere(0, 0, 0, .03f * L, .03f * L, .03f * L, 5, 8, rgb, 255);
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
    /** A tall arched standing mirror: ornate frame, pale glass that glints, little feet. Origin at the base, facing +Z. */
    private void standMirror(float H, int frame, int glass) {
        float w = .34f * H, th = .035f * H;
        box(-w, H * .5f, 0, th, H * .5f, th, frame, 255); box(w, H * .5f, 0, th, H * .5f, th, frame, 255); box(0, th, 0, w + th, th, th, frame, 255);
        torus(0, H, 0, w, w * 1.0f, th, 16, 4, 0, PI, frame, 255);
        for (int i = 0; i <= 6; i++) { float a = PI * i / 6; sphere((float) Math.cos(a) * (w + th), H + (float) Math.sin(a) * (w + th), 0, th * .9f, th * .9f, th * .9f, 3, 5, mix(frame, 0xFFFFFF, .4f), 255); }
        // an opaque silvered pane: a sky-to-ground gradient of pale reflections, then a glint that sweeps across it
        int sky = mix(0xD8ECFF, glass, .25f), gnd = mix(0x8890A0, glass, .2f);
        for (int k = 0; k < 8; k++) { float y0 = H * k / 8f; box(0, y0 + H * .0625f + th, 0, w - th, H * .0625f, .008f * H, mix(gnd, sky, k / 7f), 255); }
        push(); tr(0, H, 0); frustum(-.008f * H, .008f * H, w - th, w - th, 16, 1, 1, sky, 255, true); pop();
        float g = fract(t * .25f); push(); tr(-w + g * 2f * w, H * .5f, .012f * H); rZ(.5f); box(0, 0, 0, .05f * H, H * .42f, .004f * H, 0xFFFFFF, 255); pop();
        box(-w, th, 0, th * 1.4f, th, .12f * H, frame, 255); box(w, th, 0, th * 1.4f, th, .12f * H, frame, 255);
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
    /** A bat: dark body, red eyes, wings that flap. Faces +Z. */
    private void bat(float L, float flap) {
        int dk = 0x16101E; float f = (float) Math.sin(flap) * .7f;
        sphere(0, 0, 0, .12f * L, .1f * L, .2f * L, 4, 8, dk, 255); sphere(0, .08f * L, .2f * L, .08f * L, .08f * L, .08f * L, 4, 6, dk, 255);
        box(-.04f * L, .15f * L, .24f * L, .015f * L, .04f * L, .015f * L, dk, 255); box(.04f * L, .15f * L, .24f * L, .015f * L, .04f * L, .015f * L, dk, 255);
        sphere(-.03f * L, .09f * L, .27f * L, .015f * L, .015f * L, .015f * L, 3, 4, 0xFF2030, 255); sphere(.03f * L, .09f * L, .27f * L, .015f * L, .015f * L, .015f * L, 3, 4, 0xFF2030, 255);
        for (int sd = -1; sd <= 1; sd += 2) { push(); tr(sd * .1f * L, 0, 0); rZ(sd * f); box(sd * .35f * L, 0, 0, .35f * L, .012f * L, .13f * L, dk, 255); box(sd * .65f * L, 0, -.07f * L, .12f * L, .012f * L, .09f * L, dk, 255); pop(); }
    }
    private void cauldron(float s, int iron, int liquid) {
        frustum(0, .9f * s, .55f * s, .85f * s, 14, 1, 1, iron, 255, false); torus(0, .9f * s, 0, .85f * s, .85f * s, .06f * s, 14, 4, 0, TAU, mix(iron, 0xFFFFFF, .15f), 255);
        frustum(.84f * s, .86f * s, .8f * s, .8f * s, 14, 1, 1, liquid, 255, true);
        for (int i = 0; i < 3; i++) { float a = i * TAU / 3 + .5f; box((float) Math.cos(a) * .5f * s, .08f * s, (float) Math.sin(a) * .5f * s, .08f * s, .08f * s, .08f * s, iron, 255); }
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
    private void lanternModel(float L, int frame, int glass) {   // a six-sided hanging lantern: base plate, glass, six posts, a cap with a hanging ring, a bright flame inside
        frustum(0, .05f * L, .28f * L, .3f * L, 6, 1, 1, frame, 255, true);
        solid(true); frustum(.05f * L, .62f * L, .27f * L, .34f * L, 6, 1, 1, glass, 110, false); solid(false);
        for (int i = 0; i < 6; i++) { float a = i * TAU / 6; push(); tr((float) Math.cos(a) * .3f * L, 0, (float) Math.sin(a) * .3f * L); frustum(0, .62f * L, .016f * L, .016f * L, 5, 1, 1, frame, 255, false); pop(); }
        frustum(.32f * L, .34f * L, .31f * L, .31f * L, 6, 1, 1, frame, 255, false);
        frustum(.62f * L, .66f * L, .38f * L, .36f * L, 6, 1, 1, frame, 255, true); frustum(.66f * L, .84f * L, .34f * L, .07f * L, 6, 1, 1, frame, 255, true);
        push(); tr(0, .92f * L, 0); torus(0, 0, 0, .07f * L, .09f * L, .014f * L, 8, 4, 0, TAU, frame, 255); pop();
        sphere(0, .3f * L, 0, .1f * L, .14f * L, .1f * L, 5, 8, mix(glass, 0xFFFFFF, .85f), 255);
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
        box(0, .725f * s, 0, .5f * s, .1f * s, .46f * s, mix(dark, trim, .1f), 255);   // the seat sits on a solid block, nothing floats
        for (int sd = -1; sd <= 1; sd += 2) box(sd * .62f * s, .805f * s, 0, .09f * s, .15f * s, .44f * s, dark, 255);
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
    /** The scales of justice, detailed: stepped plinth, fluted column with collars, scrolled beam, three chains a pan, dished pans with rims and offerings. */
    private void scalesModel(float L, int rgb, int pan) {
        int gold = mix(0xD8A030, rgb, .25f), dk = mix(gold, 0x000000, .3f), lt = mix(gold, 0xFFFFFF, .35f); rgb = gold;
        int blue = mix(gold, 0x000000, .25f), red = blue, ivory = 0xF0E8D0;   // one gold for the base and both pans
        frustum(0, .035f * L, .26f * L, .24f * L, 8, 1, 1, mix(blue, 0x000000, .3f), 255, true); frustum(.035f * L, .07f * L, .2f * L, .18f * L, 8, 1, 1, ivory, 255, true); frustum(.07f * L, .1f * L, .14f * L, .1f * L, 8, 1, 1, lt, 255, true);
        frustum(.1f * L, L * .96f, .042f * L, .03f * L, 10, 1, 1, rgb, 255, true);
        for (int k = 0; k < 4; k++) { float yy = (.2f + .2f * k) * L; frustum(yy, yy + .018f * L, .06f * L, .06f * L, 10, 1, 1, lt, 255, true); }
        for (int k = 0; k < 6; k++) { float a = k * TAU / 6; box((float) Math.cos(a) * .036f * L, .5f * L, (float) Math.sin(a) * .036f * L, .006f * L, .38f * L, .006f * L, blue, 255); }
        sphere(0, L * .98f, 0, .06f * L, .06f * L, .06f * L, 6, 10, rgb, 255); sphere(0, L * 1.03f, 0, .03f * L, .03f * L, .03f * L, 5, 8, pan, 255);
        float tl = (float) Math.sin(t * 1.2f) * .1f;
        push(); tr(0, L, 0); rZ(tl);
        box(0, 0, 0, .56f * L, .016f * L, .022f * L, rgb, 255); box(0, .022f * L, 0, .5f * L, .007f * L, .012f * L, lt, 255);
        for (int s = -1; s <= 1; s += 2) {
            sphere(s * .56f * L, 0, 0, .035f * L, .035f * L, .035f * L, 5, 8, lt, 255); torus(s * .62f * L, 0, 0, .03f * L, .03f * L, .008f * L, 8, 4, 0, TAU, rgb, 255);
            for (int k = 1; k < 4; k++) box(s * k * .15f * L, .045f * L, 0, .012f * L, .018f * L, .006f * L, pan, 255);
            push(); tr(s * .54f * L, 0, 0); rZ(-tl);
            for (int k = 0; k < 3; k++) { push(); rY(k * TAU / 3 + .5f); rZ(.5f); frustum(-.46f * L, 0, .0045f * L, .0045f * L, 4, 1, 1, lt, 255, false);
                for (int j = 1; j < 6; j++) torus(0, -.07f * L * j, 0, .008f * L, .014f * L, .003f * L, 5, 3, 0, TAU, rgb, 255); pop(); }
            tr(0, -.5f * L, 0);
            frustum(-.07f * L, 0, .06f * L, .26f * L, 16, 1, 1, s < 0 ? blue : red, 255, true); frustum(-.085f * L, -.07f * L, .05f * L, .06f * L, 16, 1, 1, ivory, 255, true);
            torus(0, 0, 0, .26f * L, .26f * L, .014f * L, 16, 4, 0, TAU, lt, 255);
            if (s < 0) { sphere(0, .09f * L, 0, .08f * L, .08f * L, .08f * L, 6, 10, pan, 255); bb("glow", 0, .09f * L, 0, .4f * L, .4f * L, 0, rgba(pan, .5f)); }   // an orb of law
            else { push(); tr(0, .03f * L, 0); rZ(.4f); frustum(-.1f * L, .12f * L, .008f * L, .004f * L, 4, 1, 1, 0xF4F4F0, 255, false); box(.02f * L, .03f * L, 0, .014f * L, .08f * L, .002f * L, 0xF4F4F0, 255); pop(); }   // a feather
            pop(); }
        pop();
    }
    private void lockModel(float s, int body, int shackle) {   // round and link-like, so it matches the chain; the shackle top is at 1.0 s, where the chain hangs from
        push(); tr(0, .62f * s, 0); torus(0, 0, 0, .21f * s, .36f * s, .065f * s, 14, 5, 0, TAU, shackle, 255); pop();
        sphere(0, .24f * s, 0, .4f * s, .3f * s, .2f * s, 8, 14, body, 255);
        torus(0, .24f * s, 0, .4f * s, .3f * s, .025f * s, 14, 4, 0, TAU, shackle, 255);
        sphere(0, .24f * s, .17f * s, .3f * s, .22f * s, .05f * s, 6, 10, mix(body, shackle, .5f), 255);
        sphere(0, .29f * s, .21f * s, .05f * s, .05f * s, .02f * s, 4, 6, 0x101010, 255); taper(.12f * s, .27f * s, .02f * s, .02f * s, .035f * s, .02f * s, 0x101010, 255);
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
    /** The Bearing of Sins as LOTM shows it: a thick translucent red cross with a bright small cross burning at its heart, the Hanged Man's
     *  flower symbol faint behind it, red glow and drifting petals. Centre (x,y,z), height size, swaying by yaw. */
    private void bearerSym(float x, float y, float z, float size, float yaw) {
        float pulse = .85f + .15f * (float) Math.sin(t * 2.2f), w = .1f * size, th = .05f * size;
        boolean fxGlow = false, fxFlare = false;   // drawn, fixed in space: the Photon version turned to face the camera and flickered
        if (!fxGlow) bb("glow", x, y, z, size * 1.9f, size * 1.9f, 0, rgba(0xFF2030, .4f * pulse));
        float c = (float) Math.cos(yaw) * size * .5f, s2 = (float) Math.sin(yaw) * size * .5f, nx = -(float) Math.sin(yaw) * .02f * size, nz = (float) Math.cos(yaw) * .02f * size;
        eq(ResourceLocation.fromNamespaceAndPath("lotm", "textures/effect/hanged_man_symbol.png"), x - nx, y, z - nz, c, 0, s2, 0, size * .5f, 0, rgba(0xFFFFFF, 1f), 0, 0, 1, 1);
        eq(ResourceLocation.fromNamespaceAndPath("lotm", "textures/effect/hanged_man_symbol.png"), x + nx, y, z + nz, c, 0, s2, 0, size * .5f, 0, rgba(0xFFFFFF, 1f), 0, 0, 1, 1);
        push(); tr(x, y, z); rY(-yaw); solid(true);
        box(0, size * .05f, 0, w, size * .5f, th, 0xE02030, 150); box(0, 0, 0, size * .45f, w, th, 0xE02030, 150);
        solid(false); pop();
        if (!fxFlare) bb("glow", x, y, z, size * .4f, size * .4f, 0, rgba(0xFFFFFF, .8f * pulse));
        for (int i = 0; i < 6; i++) { float p = fract(t * .3f + i / 6f), a = h(i, 91) * TAU; bb("petal", x + (float) Math.cos(a) * size * .5f, y + size * .5f - p * size * 1.1f, z + (float) Math.sin(a) * size * .3f, size * .08f, size * .08f, t + i, rgba(0xFF7090, fd(p))); }
    }
    /** The Bearing of Sins (old model fallback): a black silhouette bound in iron chains, a red heart glowing in it. Feet at the origin. */
    private void bearer(float H) {
        if (drawGeo("hanged_man_mythical_creature", "lotm", "textures/entity/hanged_man_mythical_creature.png", H)) return;
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
    /** LOTM's flaring_sun fx rebuilt emitter by emitter with LOTM's own particle textures (the real effect is a Photon fx, which this mod cannot
     *  start): body and heart circles, ray_fade rays, wisp flames, streak prominences and kindling, ember_hd sparks. Looks the same from every side. */
    private void flareSun(float x, float y, float z, float rad, int c1, int c2) {
        float U = rad * 2f, pulse = 1f + .04f * (float) Math.sin(t * 2.1f);
        int GOLD = 0xFFC23A, YEL = 0xFFE16E, CREAM = 0xFFF0C8;
        bb("glow", x, y, z, U * 2.6f, U * 2.6f, 0, rgba(0xFF961E, .25f));
        bb("lotm:sunburst", x, y, z, U * 1.9f, U * 1.9f, t * .1f, rgba(0xFFE15A, .45f));
        bb("disc", x, y, z, U * pulse, U * pulse, 0, rgba(0xFF961E, .32f));       // body: size 1.0, orange, ~30% opaque
        bb("disc", x, y, z, U * .6f * pulse, U * .6f * pulse, 0, rgba(YEL, .45f)); // heart: size 0.6, yellow
        bb("glow", x, y, z, U * .95f * pulse, U * .95f * pulse, 0, rgba(YEL, .5f));
        bb("lotm:flare", x, y, z, U * .9f, U * .9f, 0, rgba(0xFFFFFF, .9f));
        for (int i = 0; i < 64; i++) {   // rays: ray_fade, 0.08-0.2 across, standing round the body
            float a = i * TAU / 64 + (h(i, 5) - .5f) * .08f, ln = U * (.18f + .2f * h(i, 6)) * (.85f + .15f * (float) Math.sin(t * 1.6f + i)), d = U * .5f + ln * .5f;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            bb("lotm:ray_fade", x + (rx * ca + ux * sa) * d, y + (ry * ca + uy * sa) * d, z + (rz * ca + uz * sa) * d, U * (.04f + .07f * h(i, 7)), ln, a - PI / 2, rgba(0xFFE15A, .7f));
        }
        for (int i = 0; i < 16; i++) {   // sacred flames: wisps off a 0.95 sphere
            float k = fract(t / (10f + 6f * h(i, 21)) + h(i, 22)), a = h(i, 23) * TAU, rr = U * (.42f + .3f * k), ca = (float) Math.cos(a), sa = (float) Math.sin(a), wz = U * (.16f + .1f * h(i, 24));
            bb("lotm:wisp", x + (rx * ca + ux * sa) * rr, y + (ry * ca + uy * sa) * rr, z + (rz * ca + uz * sa) * rr, wz * .7f, wz * 1.3f, a - PI / 2, rgba(GOLD, .55f * fd(k)));
        }
        for (int i = 0; i < 10; i++) {   // prominences: streaks shooting out and falling back
            float per = 2.4f + 1.6f * h(i, 31), k = fract(t / per + h(i, 32)), a = h(i, 33) * TAU, out = (float) Math.sin(k * PI);
            float d = U * (.5f + .25f * out), ca = (float) Math.cos(a), sa = (float) Math.sin(a), ln = U * (.08f + .22f * out);
            bb("lotm:streak", x + (rx * ca + ux * sa) * d, y + (ry * ca + uy * sa) * d, z + (rz * ca + uz * sa) * d, ln * .35f, ln, a - PI / 2, rgba(h(i, 34) < .5f ? GOLD : CREAM, .9f * out));
        }
        for (int i = 0; i < 16; i++) {   // kindling: streaks drawn in from a wide shell
            float k = fract(t / (3f + 2f * h(i, 41)) + h(i, 42)), a = h(i, 43) * TAU, d = U * (1.4f - .85f * k), ca = (float) Math.cos(a), sa = (float) Math.sin(a), ln = U * .12f;
            bb("lotm:streak", x + (rx * ca + ux * sa) * d, y + (ry * ca + uy * sa) * d, z + (rz * ca + uz * sa) * d, ln * .25f, ln, a + PI / 2, rgba(h(i, 44) < .5f ? YEL : CREAM, fd(k)));
        }
        for (int i = 0; i < 30; i++) {   // embers: little sparkles drifting away
            float k = fract(t / (14f + 10f * h(i, 51)) + h(i, 52)), a = h(i, 53) * TAU, el = (h(i, 54) - .5f) * PI, d = U * (.55f + 1.1f * k), ce = (float) Math.cos(el);
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a), sz = U * (.07f + .06f * h(i, 55));
            bb("lotm:ember_hd", x + (rx * ca + ux * sa) * d * ce, y + (ry * ca + uy * sa) * d * ce + (float) Math.sin(el) * d, z + (rz * ca + uz * sa) * d * ce, sz, sz, 0, rgba(GOLD, fd(k)));
        }
    }
    /** Sky stars: our own Photon effect of round, many-coloured twinkling dots; if it can't load, round dots drawn by hand. */
    private void mcStars(int n, float rad, int c1, int c2, float size) {
        if (LotmFx.hold(curId, "astral_world:star_dots", ex, ey, ez, rad)) return;
        dotStars(n, rad, c1, c2, size);
    }
    /** Round dots drawn by hand. */
    private void dotStars(int n, float rad, int c1, int c2, float size) {
        for (int i = 0; i < n; i++) {
            float a = h(i, 101) * TAU, el = .12f + h(i, 102) * 1.35f, ce = (float) Math.cos(el);
            float tw = .4f + .6f * Math.abs((float) Math.sin(t * (.5f + h(i, 104)) + i * 1.7f)), s2 = size * .9f * (.6f + h(i, 103) * .9f);
            int col = mix(h(i, 105) < .5f ? c1 : c2, hsv(h(i, 106), .6f), .55f);
            float x = (float) Math.cos(a) * rad * ce, y = (float) Math.sin(el) * rad + 1f, z = (float) Math.sin(a) * rad * ce;
            bb("glow", x, y, z, s2 * 2.2f, s2 * 2.2f, 0, rgba(col, tw * .5f)); bb("mote", x, y, z, s2, s2, 0, rgba(0xFFFFFF, tw));
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
        // every few seconds it turns, slice by slice, into one other pathway's symbol, stays that one for a while, then glitches back
        int slot = (int) Math.floor(t / 7f); float ph = (t - slot * 7f) / 7f;
        int alt = (curPath + 1 + (int) (h(slot, 12) * (Tarot.PATHWAYS.length - 1))) % Tarot.PATHWAYS.length;
        float amt = Math.max(0f, Math.min(1f, (ph - .45f) / .06f)) - Math.max(0f, Math.min(1f, (ph - .8f) / .06f));
        for (int k = 0; k < slices; k++) {
            boolean showAlt = amt > h(k, 3) * .98f && amt > 0f;
            boolean moving = amt > 0f && amt < 1f;
            boolean glitch = moving || fract(tt * .6f + h(k, 3)) > .8f;
            float dx = glitch ? (h((int) (tt * 7) + k, 77) - .5f) * .3f * size : 0, v0 = k / (float) slices, v1 = (k + 1) / (float) slices, yc = cy + (.5f - (k + .5f) / slices) * size;
            int pk = showAlt ? alt : curPath;
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
            case 7 -> { for (int i = 0; i < props + 1; i++) { float a = t * .6f + i * TAU / (props + 1); flameAt((float) Math.cos(a) * 1.05f, two ? cy - .6f : .1f, (float) Math.sin(a) * 1.05f, .9f, i, P2, P1, P3, .95f); } }
            case 10 -> {   // Monster: dice tumbling round the symbol (the wheel was clipping through it)
                for (int i = 0; i < props + 1; i++) { float a = t * .6f + i * TAU / (props + 1); push(); tr((float) Math.cos(a) * 1.05f, cy - .55f + (float) Math.sin(t * 1.5f + i) * .15f, (float) Math.sin(a) * 1.05f); rY(t * 1.1f + i); rX(t * 1.5f + i * 2f); die(.1f); pop(); }
            }
            case 11 -> { for (int i = 0; i < props + 1; i++) { float a = t * .6f + i * TAU / (props + 1); float bl = Math.max(.15f, Math.abs((float) Math.cos(t * .6f + i * 1.7f))), ex2 = (float) Math.cos(a) * 1.1f, ey2 = cy - .3f + (float) Math.sin(t * 1.4f + i) * .2f, ez2 = (float) Math.sin(a) * 1.1f;
                bb("glow", ex2, ey2, ez2, .7f, .7f, 0, rgba(P1, .3f)); bb("eye", ex2, ey2, ez2, .55f, .55f * .5f * bl, 0, rgba(mix(P1, 0xFFFFFF, .3f), 1f)); } }
            case 15 -> {
                if (!two) { waveRing(2.2f, .55f, 12, 1f, P1); flat("whirl", 0, .06f, 0, 3.2f, -t * .5f, rgba(P2, .9f)); }
                else for (int i = 0; i < 6; i++) { float a = t * .9f + i * TAU / 6; bb("drop", (float) Math.cos(a) * 1.1f, cy - .5f + (float) Math.sin(t * 1.6f + i) * .2f, (float) Math.sin(a) * 1.1f, .3f, .4f, 0, rgba(P1, 1f)); }
            }
            case 17 -> {
                push(); tr(gx(0, .9f), 3.6f, gz(0, .9f)); chain(3.6f - (cy + .1f) - .5f, .12f, metal(.3f)); pop();
                bearerSym(gx(0, .9f), cy + .1f, gz(0, .9f), 1.0f, -face);
                for (int i = 0; i < props; i++) { float a = i * TAU / props + .6f; push(); tr((float) Math.cos(a) * 1.3f, 0, (float) Math.sin(a) * 1.3f); rY(-a + PI / 2); torus(0, .2f, 0, .2f, .24f, .045f, 10, 4, -.3f, PI + .3f, metal(.3f), 255); pop(); }
                for (int i = 0; i < 5; i++) { float p = fract(t * .35f + i * .2f); bb("drop", (h(i, 7) - .5f) * 1.6f, 3f - p * 3f, (h(i, 8) - .5f) * 1.6f, .12f, .16f, 0, rgba(0xC01820, fd(p))); }
            }
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
        int seq = e.seq(), pi = e.pathIndex(); thQ = e.throneQuarter(); curId = e.getId(); ex = e.getX(); ey = e.getY(); ez = e.getZ();
        boolean shiny = e.shiny();
        this.buf = bufs; this.mat = ps.last().pose();
        this.t = (e.tickCount + pt) / 20f;
        clear(); strans = false;
        boolean symOnly = e.symbolOnly();
        drawCard(e, bufs, light, pi, seq, shiny);
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
        if (e.goo() >= 0) { R = 12f; top = 19f; S = 2.35f; CY = 7.4f + (float) Math.sin(t * 1.2) * .3f; int[] gp = GOOPAL[e.goo()]; curPath = pidx(Tarot.GOO_PATHS[e.goo()][0]); P1 = gp[0]; P2 = gp[1]; P3 = gp[2]; if (!symOnly) { gooScene(e.goo()); return; } }
        if (symOnly) { if (e.goo() >= 0) gooSymbolOnly(e.goo()); else symbolOnly(); return; }
        Cfg c = CFG[curPath];
        if (seq >= 4) rare(c, seq);
        else if (seq >= 2) epic(c, seq);
        else { boolean m = seq == 0; dims(m); scene(curPath, m); if (m) mythExtra(); }
    }

    private void drawCard(HoloCardEntity e, MultiBufferSource bufs, int light, int pi, int seq, boolean shiny) {
        String name = e.goo() >= 0 ? "textures/item/card_goo_" + Tarot.GOO_IDS[e.goo()] + "_0.png" : "textures/item/card_" + Tarot.PATHWAYS[pi] + "_" + seq + (shiny ? "_shiny" : "") + ".png";
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("astral_world", name);
        VertexConsumer vc = bufs.getBuffer(RenderType.entityCutoutNoCull(rl));
        float yawR = (float) Math.toRadians(e.getYRot());
        float ffx = -(float) Math.sin(yawR), ffz = (float) Math.cos(yawR);
        float ssx = -ffz, ssz = ffx;
        float hw = .3f, hl = .45f, y = (e.goo() < 0 && pi == 18 && seq == 0 && !e.symbolOnly()) ? .83f * HK : .03f;
        float[][] p = {{-hw, -hl}, {hw, -hl}, {hw, hl}, {-hw, hl}};
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int k = 0; k < 4; k++)
            vc.addVertex(mat, ssx * p[k][0] + ffx * p[k][1], y, ssz * p[k][0] + ffz * p[k][1]).setColor(255, 255, 255, 255)
              .setUv(uv[k][0], uv[k][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0);
    }

    // ================= the nine Great Old Ones =================
    private static final int[][] GOOPAL = {
        {0x9A78F0, 0x4A2A9A, 0xFFE8A0}, {0xFFC446, 0xC87810, 0xFFF4C0}, {0xB0B070, 0x3A3E20, 0xFF4030}, {0xFF4838, 0x8A1010, 0xFFC060}, {0x8478F0, 0x2C2C90, 0xE0E8FF},
        {0x5AC8FF, 0x1060B8, 0xFFFFFF}, {0xFF7846, 0xA03018, 0xFFE0A0}, {0xE63CC8, 0x601060, 0xFF7090}, {0x46BEC8, 0x145060, 0xFFE070}};
    private static int pidx(String n) { for (int i = 0; i < Tarot.PATHWAYS.length; i++) if (Tarot.PATHWAYS[i].equals(n)) return i; return 0; }
    /** The group's pathway symbols in an arc high in front of the scene, all readable. */
    private void gooSymbols(int g, float y, float dist, float size) {
        String[] ps = Tarot.GOO_PATHS[g]; int n = ps.length;
        for (int k = 0; k < n; k++) {
            float off = (k - (n - 1) / 2f) * size * 1.15f, px = sx0 * off - fx * dist * .55f, pz = sz0 * off - fz * dist * .55f;
            symPlane(pidx(ps[k]), px, y, pz, size, -face, 1f);
            bb("glow", px, y, pz, size * 1.5f, size * 1.5f, 0, rgba(P1, .3f));
        }
    }
    private void gooBase(int g, int domeRgb, int domeA) {
        gooDomeRgb = domeRgb;
        sigil(R * .6f);   // just a small circle under the model, like the legendaries
    }
    private int gooDomeRgb;
    private void gooScene(int g) {
        gooBase(g, g == 1 ? 0x100800 : g == 5 ? 0x020A18 : g == 6 ? 0x180804 : g == 3 ? 0x1A0204 : g == 7 ? 0x0C0012 : g == 8 ? 0x020A0C : g == 0 ? 0x08041A : g == 4 ? 0x04041A : 0x010204, 248);
        switch (g) {
            case 0 -> gooLord(); case 1 -> gooAlmighty(); case 2 -> gooDarkness(); case 3 -> gooCalamity(); case 4 -> gooKnowledge();
            case 5 -> gooKey(); case 6 -> gooGoddess(); case 7 -> gooDevils(); default -> gooAnarchy();
        }
        sprites("mote", 40, DRIFTM, R * .9f, 0, top, .5f * S, P1, 1f, .2f, 51, false);
    }
    private static float gc(float a) { return (float) Math.cos(a); }
    private static float gs(float a) { return (float) Math.sin(a); }
    /** World offset of a spot given in the hologram's own frame: lx to its right, lz toward the person who placed it. */
    private float gx(float lx, float lz) { return sx0 * lx - fx * lz; }
    private float gz(float lx, float lz) { return sz0 * lx - fz * lz; }

    /** Lord of Mysteries: grey fog and drifting colour, a huge door of light at the far end of the Fool's round bronze table, the other doors of the mysteries around it, every pathway circling overhead and the Fool's symbol glitching above the table. */
    private void gooLord() {
        int cp = curPath; curPath = 18;
        float t0 = t, ph = t % 14f; boolean gl = ph < .35f || (ph > 6.5f && ph < 6.65f); int fb = (int) (t * 25);
        if (gl) t = t0 - .3f * h(fb, 3);
        boolean pf = LotmFx.hold(curId, "astral_world:fool_fog", ex, ey, ez, R * 1.5f) & LotmFx.hold(curId, "astral_world:fool_hall_fog", ex, ey, ez, R * 1.3f);
        if (!pf) for (int i = 0; i < 22; i++) { float a = t * .05f + i * TAU / 22, rr = R * (.1f + .8f * h(i, 6)); ebb("fog", (float) Math.cos(a) * rr, (.3f + 3f * h(i, 7)) * S, (float) Math.sin(a) * rr, 8 * S, 3f * S, h(i, 9) * 3f, rgba(0x3A4044, .5f)); }
        for (int i = 0; i < 26; i++) {
            float a = t * (.05f + .05f * h(i, 11)) + h(i, 12) * TAU, rr = R * (.15f + .8f * h(i, 13)), y = (.6f + 7f * h(i, 14)) * S + (float) Math.sin(t * .8f + i) * .4f * S, tw = .6f + .4f * (float) Math.sin(t * 1.7f + i * 2f);
            int c = hsv(h(i, 15), .75f);
            bb("glow", (float) Math.cos(a) * rr, y, (float) Math.sin(a) * rr, 1.5f * S, 1.5f * S, 0, rgba(c, tw * .7f)); bb("mote", (float) Math.cos(a) * rr, y, (float) Math.sin(a) * rr, .3f * S, .3f * S, 0, rgba(0xFFFFFF, tw));
        }
        // the Fool's round bronze table with its seven chairs
        frustum(0, .75f * S, 3.5f * S, 3.5f * S, 28, 1, 1, 0x6A4A22, 255, true); frustum(.75f * S, .9f * S, 3.6f * S, 3.6f * S, 28, 1, 1, 0xA67A34, 255, true);
        for (int i = 0; i < 7; i++) {
            float a = i * TAU / 7 + .2f; push(); tr(gc(a) * 4.7f * S, 0, gs(a) * 4.7f * S); rY(-a);
            box(0, .5f * S, 0, .5f * S, .5f * S, .5f * S, 0x2A1A10, 255); box(.46f * S, 1.5f * S, 0, .09f * S, 1.0f * S, .46f * S, 0x4A2E18, 255); box(.46f * S, 2.55f * S, 0, .11f * S, .1f * S, .3f * S, 0xA67A34, 255); pop();
        }
        bb("glow", 0, 1.2f * S, 0, 9f * S, 4f * S, 0, rgba(0xFFE0A0, .35f));
        // the great door of light at the far end, on a round dais, with a column of white light rising from it
        float gzl = -R * .62f, gwx = gx(0, gzl), gwz = gz(0, gzl);
        for (int k = 0; k < 3; k++) { push(); tr(gwx, 0, gwz); frustum(k * .3f * S, (k + 1) * .3f * S, (4.4f - k * .8f) * S, (4.1f - k * .8f) * S, 24, 1, 1, mix(0x8A8478, 0xFFFFFF, k * .25f), 255, true); pop(); }
        lomDoor(0, gzl, 5.2f * S, 6.8f * S, gl ? hsv(h(fb, 40), .9f) : 0xFFFFFF, gl ? (h(fb, 41) - .5f) * 1.5f * S : 0f);
        column("beam", gwx, 0, top * 1.4f, gwz, 3.6f * S, rgba(0xFFFFFF, .3f)); bb("glow", gwx, 3.4f * S, gwz, 16f * S, 18f * S, 0, rgba(0xFFFFFF, .35f));
        // the other doors of the mysteries stand around the fog, each its own colour
        for (int i = 0; i < 6; i++) {
            float ang = (i < 3 ? 1f : -1f) * (1.0f + .5f * (i % 3)), lx = R * .95f * gs(ang), lz = -R * .95f * gc(ang);
            int c = gl ? hsv(h(fb, 50 + i), .9f) : mix(hsv(h(i, 23), .55f), 0xFFFFFF, .5f);
            lomDoor(lx, lz, 2.6f * S, (4.4f + 1.4f * h(i, 22)) * S, c, gl ? (h(fb, 60 + i) - .5f) * 1.2f * S : 0f);
        }
        // every pathway circling overhead: the Fool holds them all
        float ry = CY + 4.4f * S;
        for (int i = 0; i < pathCount(); i++) {
            float a = t * .06f + i * TAU / pathCount(), px = gc(a) * R * .55f, pz = gs(a) * R * .55f, py = ry + gs(t * .7f + i) * .3f * S;
            symPlane(i, px, py, pz, 1.35f * S, -a + PI / 2, .9f); bb("glow", px, py, pz, 2.6f * S, 2.6f * S, 0, rgba(hsv(i / (float) pathCount(), .5f), .25f));
        }
        glitchSym(0, CY + 2.6f * S, 0, 3.6f * S, -face);
        gooSymbols(0, 1.2f * S, R * .3f, 1.7f * S);
        if (gl) {
            int[] cols = {0x40FFFF, 0xFF40C0, 0xFFFFFF, 0x60FF80};
            for (int i = 0; i < 10; i++) { float a = h(fb, 10 + i) * TAU, rr = R * (.3f + 1.0f * h(fb, 30 + i)); bb("solid", (float) Math.cos(a) * rr, h(fb, 50 + i) * 12f * S, (float) Math.sin(a) * rr, (4f + 24f * h(fb, 70 + i)) * S, (.05f + .3f * h(fb, 90 + i)) * S, 0, rgba(cols[i % 4], .25f + .3f * h(fb, 110 + i))); }
        }
        t = t0; curPath = cp;
    }
    private int pathCount() { return Tarot.PATHWAYS.length; }
    private void lomDoor(float lx, float lz, float w, float dh, int c, float jit) {
        float wx = gx(lx, lz), wz = gz(lx, lz), a = (float) Math.atan2(wz, wx);
        bb("glow", wx + jit, dh * .5f, wz, w * 1.9f, dh * 1.15f, 0, rgba(c, .4f));
        stand("door", wx + jit, dh * .5f, wz, w, dh, -a + PI / 2, rgba(c, 1f));
        for (int k = 0; k < 3; k++) { float p = fract(t * .3f + k / 3f + lx * .13f); bb("lotm:ember_hd", wx, p * dh, wz, .3f * S, .3f * S, 0, rgba(c, fd(p))); }
    }

    /** God Almighty: a stepped golden dais with four pillars, a vast armillary around a blazing sun, wings of light spread behind it, rays flaring and the five symbols circling. */
    private void gooAlmighty() {
        for (int k = 0; k < 5; k++) frustum(k * .5f * S, (k + 1) * .5f * S, (4.6f - k * .7f) * S, (4.4f - k * .7f) * S, 28, 1, 1, mix(0x9A6818, 0xFFE070, k / 4f), 255, true);
        for (int i = 0; i < 4; i++) {
            float a = i * PI / 2 + PI / 4; push(); tr(gc(a) * 3.3f * S, 2.5f * S, gs(a) * 3.3f * S);
            frustum(0, 5.4f * S, .4f * S, .32f * S, 12, 1, 1, metal(.6f), 255, true); box(0, 5.5f * S, 0, .6f * S, .12f * S, .6f * S, metal(.9f), 255); box(0, -.05f * S, 0, .6f * S, .15f * S, .6f * S, metal(.8f), 255); pop();
            bb("glow", gc(a) * 3.3f * S, 8.2f * S, gs(a) * 3.3f * S, 2.2f * S, 2.2f * S, 0, rgba(P3, .6f));
        }
        float ay = CY + 2.4f * S, ar = 3.6f * S;
        push(); tr(0, ay, 0);
        for (int i = 0; i < 5; i++) { push(); rY(i * PI / 5 + t * (.12f + .03f * i)); rX(PI / 2); ring(ar * (1f - i * .08f), .2f * S, i % 2 == 0 ? metal(.75f) : mix(P1, 0xFFFFFF, .3f), 255); pop(); }
        push(); rY(t * .1f); rZ(.4f); ring(ar * 1.22f, .12f * S, mix(P3, 0xFFFFFF, .3f), 255); pop();
        push(); rY(-t * .08f); rZ(-.5f); ring(ar * 1.34f, .09f * S, mix(P3, 0xFFFFFF, .4f), 255); pop();
        sphere(0, 0, 0, 1.3f * S, 1.3f * S, 1.3f * S, 10, 16, mix(P3, 0xFFFFFF, .7f), 255); pop();
        // wings of gold and light
        pushFace(); tr(0, ay, 0);
        for (int sd = -1; sd <= 1; sd += 2) for (int f = 0; f < 8; f++) {
            push(); tr(sd * 1.2f * S, 0, -.5f * S); rZ(-sd * (.25f + f * .27f)); float fl = (4.2f + 1.0f * (float) Math.sin(f * .55f + .4f)) * S;
            frustum(.5f * S, fl, .36f * S, 0, 4, 1, .3f, f % 2 == 0 ? mix(P3, 0xFFFFFF, .6f) : mix(P1, 0xFFFFFF, .4f), 255, false); pop();
        }
        for (int i = 0; i < 16; i++) { push(); rZ(i * TAU / 16 + t * .05f); frustum(1.5f * S, (2.9f + (i % 2) * 1.6f) * S, .2f * S, 0, 4, 1, 1, metal(.95f), 255, false); pop(); }
        pop();
        bb("glow", 0, ay, 0, 16f * S, 16f * S, 0, rgba(P1, .5f)); bb("lotm:sunburst", 0, ay, 0, 12f * S, 12f * S, t * .05f, rgba(P3, .55f));
        column("beam", 0, 0, top * 1.4f, 0, 3.2f * S, rgba(P1, .3f)); column("beam", 0, 0, top * 1.4f, 0, 1.0f * S, rgba(P3, .75f));
        String[] ps = Tarot.GOO_PATHS[1];
        for (int k = 0; k < 5; k++) {
            float a = t * .12f + k * TAU / 5, px = gc(a) * ar * 1.9f, pz = gs(a) * ar * 1.9f, py = ay + gs(t * .8f + k) * .6f * S;
            symPlane(pidx(ps[k]), px, py, pz, 2.6f * S, -a + PI / 2, 1f); bb("glow", px, py, pz, 5f * S, 5f * S, 0, rgba(P1, .35f));
        }
        for (int i = 0; i < 2; i++) { float pw = fract(t * .2f + i / 2f); flat("ring", 0, .12f, 0, pw * R * 1.6f, 0, rgba(P1, fd(pw))); }
        sprites("feather", 30, FALLM, R * .8f, 0, top, 1.1f * S, 0xFFFFFF, 1f, .12f, 54, true); sprites("spark", 60, RISEM, R * .7f, 0, top, .35f * S, 0xFFFFFF, 1f, .3f, 55, true);
    }

    private float riverZ(float x) { return R * .22f * gs(x / (R * 1.1f) * 2f + 1f) + R * .06f * gs(x / (R * .45f)); }
    private float riverW(float x) { return 4.6f * S * (.85f + .2f * gs(x * .3f + 2f)); }
    /** A branching tree: tubes forking recursively, ending in leaf clusters (leaf 0 = bare). */
    private void treeBranch(float x, float y, float z, float ang, float len, float rise, float r0, int depth, int seed, int bark, int leaf, float leafSize) {
        int n = 7; float[][] pts = new float[n][]; float dx = gc(ang), dz = gs(ang);
        for (int k = 0; k < n; k++) { float u = k / (n - 1f), sw = gs(t * .5f + seed) * .05f * len * u; pts[k] = v3(x + dx * len * u + sw, y + rise * u, z + dz * len * u); }
        tube(pts, r0, r0 * .5f, bark);
        float[] e = pts[n - 1];
        if (depth > 0) { for (int s = 0; s < 2; s++) treeBranch(e[0], e[1], e[2], ang + (s == 0 ? .75f : -.75f) + (h(seed, s) - .5f) * .5f, len * .68f, rise * .7f + .3f * len * h(seed, s + 4), r0 * .5f, depth - 1, seed * 3 + s + 1, bark, leaf, leafSize); }
        else if (leaf != 0) {
            solid(true); sphere(e[0], e[1], e[2], leafSize, leafSize * .8f, leafSize, 6, 10, leaf, 150); solid(false);
            bb("glow", e[0], e[1], e[2], leafSize * 3.2f, leafSize * 3.2f, 0, rgba(leaf, .22f));
        }
    }
    /** Eternal Darkness: a wide black river winding out of the dark where the dead rest, lit by a pale moon: glimmers sliding down the water, pale banks lined with leaning gravestones, bare dead trees and black reeds. */
    private void gooDarkness() {
        float len = R * 2.8f; int seg = 40;
        for (int i = 0; i < seg; i++) {
            float x0 = (i / (float) seg - .5f) * len, x1 = ((i + 1) / (float) seg - .5f) * len, z0 = riverZ(x0), z1 = riverZ(x1), dx = x1 - x0, dz = z1 - z0, l = (float) Math.sqrt(dx * dx + dz * dz), w = riverW((x0 + x1) / 2) / 2;
            float ux2 = dx / l, uz2 = dz / l, px = -uz2, pz = ux2, mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
            float wax = gx(ux2 * l * .52f, uz2 * l * .52f) - gx(0, 0), waz = gz(ux2 * l * .52f, uz2 * l * .52f) - gz(0, 0), wbx = gx(px * w, pz * w) - gx(0, 0), wbz = gz(px * w, pz * w) - gz(0, 0);
            // pale banks either side, then the black water
            for (int sd = -1; sd <= 1; sd += 2) eq(tex("solid"), gx(mx, mz) + sd * wbx * 1.55f, .05f, gz(mx, mz) + sd * wbz * 1.55f, wax, 0, waz, wbx * .55f, 0, wbz * .55f, rgba(0x363C34, .95f), 0, 0, 1, 1);
            eq(tex("solid"), gx(mx, mz), .07f, gz(mx, mz), wax, 0, waz, wbx, 0, wbz, rgba(0x02060C, .98f), 0, 0, 1, 1);
            float fe = .5f + .5f * gs(t * .8f + i * .7f);
            quad("solid", gx(mx, mz) + wbx * .97f, .09f, gz(mx, mz) + wbz * .97f, wax, 0, waz, wbx * .06f, 0, wbz * .06f, rgba(mix(P1, 0xFFFFFF, .3f), .6f * (.5f + .5f * fe)));
            quad("solid", gx(mx, mz) - wbx * .97f, .09f, gz(mx, mz) - wbz * .97f, wax, 0, waz, wbx * .06f, 0, wbz * .06f, rgba(mix(P1, 0xFFFFFF, .3f), .6f * (1.0f - .5f * fe)));
        }
        // moonlight glimmers and slow ripples on the water
        for (int k = 0; k < 20; k++) {
            float u = fract(t * .03f + h(k, 3)), x = (u - .5f) * len, z = riverZ(x) + (h(k, 4) - .5f) * riverW(x) * .8f, ph2 = fract(t * .25f + h(k, 5));
            flat("ring", gx(x, z), .1f, gz(x, z), (.6f + 1.8f * ph2) * S, 0, rgba(mix(P1, 0xFFFFFF, .4f), .6f * fd(ph2)));
        }
        bb("glow", gx(0, -R * .9f), top * 1.15f, gz(0, -R * .9f), 13f * S, 13f * S, 0, rgba(0xC8D0A0, .4f)); bb("glow", gx(0, -R * .9f), top * 1.15f, gz(0, -R * .9f), 5f * S, 5f * S, 0, rgba(0xF0F4D0, .7f));
        // gravestones and the dead's markers on both banks
        for (int k = 0; k < 18; k++) {
            float x = (h(k, 11) - .5f) * len * .9f, side = k % 2 == 0 ? -1f : 1f, z = riverZ(x) + side * (riverW(x) * .5f + .9f * S + h(k, 13) * 2.4f * S), hh = (.8f + .9f * h(k, 14)) * S;
            push(); tr(gx(x, z), 0, gz(x, z)); rY(h(k, 15) * TAU); rZ((h(k, 16) - .5f) * .35f); box(0, hh, 0, .5f * S, hh, .16f * S, 0x5C625A, 255); box(0, hh * 2f + .1f * S, 0, .6f * S, .1f * S, .22f * S, 0x4A5048, 255); pop();
        }
        // bare dead trees
        for (int k = 0; k < 7; k++) {
            float x = (h(k, 21) - .5f) * len * .8f, side = k % 2 == 0 ? -1f : 1f, z = riverZ(x) + side * (riverW(x) * .5f + 3f * S + h(k, 22) * 2f * S), wx = gx(x, z), wz = gz(x, z);
            treeBranch(wx, 0, wz, h(k, 23) * TAU, .1f, 4.2f * S, .3f * S, 0, 100 + k, 0x0C0C10, 0, 0f);
            for (int b = 0; b < 3; b++) treeBranch(wx, 3.2f * S, wz, b * 2.1f + h(k, 24), 2.4f * S, 1.2f * S, .14f * S, 1, 120 + k * 3 + b, 0x0C0C10, 0, 0f);
        }
        for (int k = 0; k < 24; k++) { float x = (h(k, 31) - .5f) * len, side = h(k, 32) < .5f ? -1f : 1f, z = riverZ(x) + side * (riverW(x) * .5f + .3f * S + h(k, 33) * 1.2f * S);
            push(); tr(gx(x, z), 0, gz(x, z)); taper(0, (1.4f + 1.8f * h(k, 34)) * S, .09f * S, .09f * S, 0, 0, 0x08080A, 255); pop(); }
        if (!LotmFx.hold(curId, "astral_world:dark_mist", ex, ey, ez, R * 1.3f)) for (int k = 0; k < 5; k++) fogRing(10, R * (.5f + .2f * k), P2, .22f);
        gooSymbols(2, CY + 3.4f * S, R * .5f, 3.0f * S);
    }
    /** Calamity of Destruction: a vast cracked molten core in burning shells, spikes erupting from the ground, meteors falling and a storm of shards and shockwaves. */
    private void gooCalamity() {
        float cy = CY + 1.6f * S, br = 2.8f * S * (1f + .03f * gs(t * 2f));
        sphere(0, cy, 0, br, br, br, 12, 18, mix(P1, 0xFFFFFF, .5f), 255);
        solid(true); sphere(0, cy, 0, br * 1.25f, br * 1.25f, br * 1.25f, 10, 16, P1, 120); sphere(0, cy, 0, br * 1.6f, br * 1.6f, br * 1.6f, 10, 16, P2, 60); sphere(0, cy, 0, br * 2.1f, br * 2.1f, br * 2.1f, 8, 14, P2, 30); solid(false);
        for (int i = 0; i < 10; i++) { push(); tr(0, cy, 0); rY(i * 1.3f); rX(i * 2.1f + .4f); torus(0, 0, 0, br * 1.03f, br * 1.03f, .05f * S, 20, 3, 0, TAU * (.3f + .05f * (i % 5)), 0x300808, 255); pop(); }
        bb("glow", 0, cy, 0, 24f * S, 24f * S, 0, rgba(P1, .5f));
        column("beam", 0, cy, top * 1.5f, 0, 3.4f * S, rgba(P1, .45f)); column("beam", 0, cy, top * 1.5f, 0, 1.2f * S, rgba(P3, .8f));
        for (int i = 0; i < 12; i++) {   // spikes breaking out of the ground
            float a = i * TAU / 12 + .15f, r = R * (.42f + .45f * h(i, 7)), hh = (3.5f + 5f * h(i, 8)) * S;
            push(); tr(gc(a) * r, 0, gs(a) * r); rY(-a); rZ(-.25f - .2f * h(i, 9)); frustum(0, hh, 1.0f * S, 0, 6, 1, 1, 0x1C0606, 255, false); frustum(hh * .55f, hh, .5f * S, 0, 6, 1, 1, mix(P1, 0xFFFFFF, .2f), 255, false); pop();
            bb("glow", gc(a) * r, .6f * S, gs(a) * r, 3.4f * S, 3.4f * S, 0, rgba(P1, .45f));
        }
        for (int i = 0; i < 9; i++) {   // meteors
            float p = fract(t * .16f + i / 9f), a = h(i, 21) * TAU, r = R * (.35f + .6f * h(i, 22)), y = (1f - p) * top * 1.6f + 1f * S, mx = gc(a) * r + (1f - p) * 7f, mz = gs(a) * r;
            float mr = (.8f + .8f * h(i, 23)) * S;
            push(); tr(mx, y, mz); rY(t * h(i, 24)); rX(t * h(i, 25) * 2f); sphere(0, 0, 0, mr, mr * .9f, mr, 6, 9, 0x2A0A08, 255); pop();
            bb("glow", mx, y, mz, mr * 4f, mr * 4f, 0, rgba(P1, .65f));
            for (int k = 1; k <= 4; k++) bb("glow", mx - (1f - p) * 0, y + k * mr * 1.3f, mz, mr * (3.6f - k * .6f), mr * (3.6f - k * .6f), 0, rgba(P2, .4f - k * .07f));
        }
        for (int i = 0; i < 30; i++) {
            float p = fract(t * .12f + i / 30f), a = h(i, 1) * TAU, e = (h(i, 2) - .35f) * 1.6f, r = 3.2f * S + p * R * .7f;
            push(); tr(gc(a) * gc(e) * r, cy + gs(e) * r * .8f, gs(a) * gc(e) * r); rY(h(i, 3) * TAU + t * h(i, 5)); rX(t * (h(i, 6) - .5f) * 3f);
            frustum(0, (1.4f + 1.8f * h(i, 10)) * S * (1f - p * .5f), .6f * S, 0, 4, 1, 1, mix(P2, 0x400808, h(i, 4)), 255, false); pop();
        }
        for (int i = 0; i < 3; i++) { float pw = fract(t * .22f + i / 3f); flat("ring_thick", 0, .1f, 0, pw * R * 1.8f, 0, rgba(P1, fd(pw))); }
        for (int i = 0; i < 10; i++) flat("crack", 0, .12f, 0, R * 1.3f, i * TAU / 10 + .3f, rgba(P1, .85f));
        if (!LotmFx.hold(curId, "astral_world:calamity_embers", ex, ey + .3f, ez, R * .8f)) sprites("spark", 70, RISEM, R * .7f, 0, top, .45f * S, P3, 1f, .5f, 58, true);
        gooSymbols(3, cy + 6.2f * S, R * .5f, 3.0f * S);
    }
    private static final int[] BOOKS = {0x8A2A2A, 0x2A4A8A, 0x2A6A3A, 0x7A5A1A, 0x5A2A7A, 0x2A6A6A};
    /** Demon of Knowledge: towers of books ringing a lectern with a great open tome, a vast horned eye above it, rune rings, books orbiting and a whirlwind of pages. */
    private void gooKnowledge() {
        float by = CY + .2f * S;
        for (int i = 0; i < 6; i++) {   // towers of stacked books
            float a = i * TAU / 6 + .3f, r = R * .72f; int nb = 9 + (int) (4 * h(i, 3));
            for (int j = 0; j < nb; j++) { push(); tr(gc(a) * r + (h(i * 31 + j, 5) - .5f) * .35f * S, j * .62f * S, gs(a) * r + (h(i * 31 + j, 6) - .5f) * .35f * S); rY(h(i * 31 + j, 7) * .6f - a); box(0, .3f * S, 0, 1.0f * S * (.8f + .2f * h(i * 31 + j, 8)), .28f * S, .7f * S, BOOKS[(i + j) % 6], 255); pop(); }
            bb("glow", gc(a) * r, nb * .62f * S + .8f * S, gs(a) * r, 3.4f * S, 3.4f * S, 0, rgba(P3, .55f));
        }
        frustum(0, 2.0f * S, .7f * S, .45f * S, 10, 1, 1, 0x3A2A18, 255, true); frustum(2.0f * S, 2.15f * S, 1.4f * S, 1.4f * S, 10, 1, 1, 0x6A4A22, 255, true);
        stand("page", sideX(-2.6f * S), by + 1.2f * S, sideZ(-2.6f * S), 5f * S, 6.4f * S, -face + .5f, rgba(0xF4ECD0, .95f)); stand("page", sideX(2.6f * S), by + 1.2f * S, sideZ(2.6f * S), 5f * S, 6.4f * S, -face - .5f, rgba(0xF4ECD0, .95f));
        column("beam", 0, by + 1f * S, top * 1.2f, 0, 2.4f * S, rgba(P3, .55f)); bb("glow", 0, by + 2f * S, 0, 12f * S, 12f * S, 0, rgba(P3, .5f));
        float ey2 = by + 6.2f * S, bl = Math.max(.2f, .55f + .45f * gs(t * .5f));   // the eye above, slowly blinking
        bb("glow", 0, ey2, 0, 11f * S, 8f * S, 0, rgba(P1, .35f)); bb("eye", 0, ey2, 0, 9f * S, 4.4f * S * bl, 0, rgba(P1, .95f));
        pushFace(); torus(3.4f * S, ey2 - .6f * S, 0, 2.4f * S, 4.2f * S, .35f * S, 24, 5, PI * .5f, PI, 0x1A1430, 255); torus(-3.4f * S, ey2 - .6f * S, 0, 2.4f * S, 4.2f * S, .35f * S, 24, 5, 0, PI * .5f, 0x1A1430, 255); pop();
        for (int i = 0; i < 3; i++) flat("ring", 0, by + (1.6f + 2.2f * i) * S, 0, R * (.5f + .12f * i), (i % 2 == 0 ? 1 : -1) * t * .4f, rgba(P1, .8f));
        for (int i = 0; i < 10; i++) { float a = t * .3f + i * TAU / 10, y = by + 3.2f * S + gs(t + i) * .8f * S; push(); tr(gc(a) * R * .42f, y, gs(a) * R * .42f); rY(-a); rZ(.3f); box(0, 0, 0, .55f * S, .75f * S, .22f * S, BOOKS[i % 6], 255); pop(); }
        sprites("page", 110, SPIRALM, R * .8f, 0, top, 1.5f * S, mix(P1, 0xFFFFFF, .3f), .9f, .16f, 59, true);
        gooSymbols(4, by + 8.2f * S, R * .5f, 3.4f * S);
    }
    /** Goddess of Origin: a giant mystical tree. A twisting trunk with a glowing heart, great branches forking into canopies of luminous blossom, roots across the ground, fruit of light hanging and petals drifting. */
    private void gooGoddess() {
        float U = S * .85f, bark = 0, hy = 3.2f * U;
        int bk = mix(0x3A2014, P2, .25f), lf = mix(P1, 0xFFC0A0, .3f);
        for (int v = 0; v < 4; v++) {   // trunk: four strands twisting round each other
            float[][] pts = new float[14][];
            for (int k = 0; k < 14; k++) { float u = k / 13f, a = v * TAU / 4 + u * 5.5f, rr = (1.5f * (1f - u * .55f) + .1f) * U; pts[k] = v3(gc(a) * rr, u * 5.4f * U, gs(a) * rr); }
            tube(pts, .85f * U * (v % 2 == 0 ? 1f : .8f), .5f * U, bk);
        }
        for (int v = 0; v < 9; v++) {   // roots flaring across the ground
            float[][] pts = new float[10][]; float a = v * TAU / 9 + .2f;
            for (int k = 0; k < 10; k++) { float u = k / 9f, aa = a + u * .5f * (v % 2 == 0 ? 1 : -1), r2 = (1.2f + u * 6.5f) * U; pts[k] = v3(gc(aa) * r2, (1f - u) * 1.8f * U * (1f - u * .3f) + .1f * U + gs(u * 9f + v) * .15f * U, gs(aa) * r2); }
            tube(pts, .7f * U, .1f * U, bk);
        }
        float cy2 = 5.2f * U;
        for (int b = 0; b < 8; b++) treeBranch(0, cy2, 0, b * TAU / 8 + .2f, 3.6f * U, 1.8f * U, .5f * U, 2, 10 + b * 7, bk, lf, 1.1f * U);
        for (int b = 0; b < 5; b++) treeBranch(0, cy2 + 1.6f * U, 0, b * TAU / 5 + .8f, 2.6f * U, 3.2f * U, .38f * U, 1, 90 + b * 5, bk, mix(P3, 0xFFFFFF, .3f), 1.3f * U);
        // glowing heart of the tree and a crown of light
        float hy2 = 2.8f * U;
        bb("glow", 0, hy2, 0, 6f * U, 6f * U, 0, rgba(mix(P3, 0xFFFFFF, .4f), .8f)); sphere(0, hy2, 0, .5f * U, .6f * U, .5f * U, 6, 10, mix(P3, 0xFFFFFF, .8f), 255);
        bb("glow", 0, cy2 + 3.5f * U, 0, 26f * U, 20f * U, 0, rgba(P1, .3f)); bb("corona", 0, cy2 + 3.2f * U, 0, 20f * U, 20f * U, t * .05f, rgba(P3, .3f));
        for (int i = 0; i < 12; i++) {   // fruit of light hanging from the canopy
            float a = h(i, 3) * TAU, r = (1.5f + 5f * h(i, 4)) * U, y = cy2 + (1.4f + 3f * h(i, 5)) * U + gs(t * .8f + i) * .15f * U;
            bb("glow", gc(a) * r, y, gs(a) * r, 1.3f * U, 1.3f * U, 0, rgba(0xFFE890, .85f)); bb("mote", gc(a) * r, y, gs(a) * r, .5f * U, .5f * U, 0, rgba(0xFFFFFF, 1f));
        }
        for (int i = 0; i < 3; i++) { float pw = fract(t * .2f + i / 3f); flat("ring", 0, .12f, 0, pw * 1.8f * R, 0, rgba(P1, fd(pw))); }
        sprites("petal", 120, FALLM, R * .85f, 0, top * 1.1f, 1f * S, P1, 1f, .1f, 62, true); if (!LotmFx.hold(curId, "astral_world:light_motes", ex, ey + 4f * S, ez, R * .8f)) sprites("mote", 70, RISEM, R * .7f, 0, top, .45f * S, 0xFFF0A0, 1f, .25f, 64, false);
        gooSymbols(6, CY + 7.5f * S, R * .5f, 3.4f * S);
    }
    /** Father of Devils: a vast horned skull wreathed in hellfire above a whirling pit, ringed by black spires and braziers, chains and eyes hanging in the dark. */
    private void gooDevils() {
        flat("whirl", 0, .06f, 0, 2.1f * R, -t * .4f, rgba(0x7A22B0, .9f)); flat("whirl", 0, .08f, 0, 1.5f * R, t * .6f, rgba(0xA01830, .9f)); flat("whirl", 0, .1f, 0, .8f * R, -t * 1.1f, rgba(0xB02040, .95f));
        int horn = mix(0x1C0C18, P1, .15f); float sy = 3.0f * S;
        for (int i = 0; i < 12; i++) {   // ring of black spires
            float a = i * TAU / 12 + .1f, r = R * (.8f + .1f * (i % 2)), hh = (3f + 4f * h(i, 3)) * S;
            push(); tr(gc(a) * r, 0, gs(a) * r); rY(-a); rZ(-.15f); frustum(0, hh, .8f * S, 0, 5, 1, 1, 0x160812, 255, false); pop();
            bb("glow", gc(a) * r, .8f * S, gs(a) * r, 3f * S, 3f * S, 0, rgba(P1, .35f));
        }
        pushFace(); tr(0, sy, 0); skull(2.6f * S, mix(0x2A1820, 0x9A8A80, .35f), P3); pop();
        pushFace();
        torus(5.8f * S, sy, 0, 3.2f * S, 6.4f * S, .6f * S, 30, 6, PI * .5f, PI, horn, 255); torus(-5.8f * S, sy, 0, 3.2f * S, 6.4f * S, .6f * S, 30, 6, 0, PI * .5f, horn, 255);
        torus(5.8f * S, sy, 0, 3.2f * S, 6.4f * S, .2f * S, 30, 4, PI * .5f, PI, mix(P1, 0xFFFFFF, .2f), 255); torus(-5.8f * S, sy, 0, 3.2f * S, 6.4f * S, .2f * S, 30, 4, 0, PI * .5f, mix(P1, 0xFFFFFF, .2f), 255);
        pop();
        bb("glow", 0, sy, 0, 16f * S, 16f * S, 0, rgba(P1, .45f));
        flameAt(gx(-1.5f * S, 0), sy + 2.4f * S, gz(-1.5f * S, 0), 4.2f * S, 11, P2, P1, P3, .85f); flameAt(gx(1.5f * S, 0), sy + 2.4f * S, gz(1.5f * S, 0), 4.2f * S, 12, P2, P1, P3, .85f);
        for (int i = 0; i < 6; i++) { float a = i * TAU / 6 + .2f, px = gc(a) * R * .6f, pz = gs(a) * R * .6f; push(); tr(px, 0, pz); box(0, 1.2f * S, 0, .6f * S, 1.2f * S, .6f * S, 0x1A0A18, 255); pop(); flameAt(px, 2.7f * S, pz, 4f * S, i + 5, P2, P1, P3, .9f); }
        for (int i = 0; i < 6; i++) {   // chains hanging out of the dark
            float a = i * TAU / 6 + .5f, r = R * (.35f + .3f * h(i, 6)); float[][] pts = new float[10][];
            for (int k = 0; k < 10; k++) pts[k] = v3(gc(a) * r + gs(k * .9f + i) * .25f * S, top * 1.2f - k * top * 1.2f / 11f * (.5f + .06f * k) , gs(a) * r);
            tube(pts, .12f * S, .1f * S, 0x2A2A30);
        }
        for (int i = 0; i < 10; i++) { float a = h(i, 8) * TAU, hh = 5f * S + 8f * S * h(i, 9), r = R * .95f; bb("eye", gc(a) * r, hh, gs(a) * r, 3.8f * S, 1.8f * S * Math.max(.1f, Math.abs(gs(t * .6f + i * 2f))), 0, rgba(P1, .85f)); }
        for (int i = 0; i < 2; i++) { float pw = fract(t * .22f + i / 2f); flat("ring_thick", 0, .12f, 0, pw * R * 1.7f, 0, rgba(P1, fd(pw))); }
        sprites("mote", 80, SPIRALM, R * .8f, 0, top, .5f * S, P1, 1f, .2f, 65, false);
        gooSymbols(7, CY + 3.2f * S, R * .5f, 3.4f * S);
    }
    /** The Anarchy: titanic scales of law tipped and half sunk, the Black Emperor's crown cracked and spinning loose above them, broken pillars, falling rubble, lightning and splitting law tablets. */
    private void gooAnarchy() {
        float S0 = S; S = S0 * 1.5f;
        pushFace(); tr(-1.0f * S, -1.1f * S, 0); rZ(.38f + .03f * gs(t * .6f)); scalesModel(7.5f * S, mix(P2, 0xFFFFFF, .3f), P1); pop();
        float cy = CY + 3.6f * S;
        push(); tr(gx(1.8f * S, 0), cy + gs(t * .8f) * .4f * S, gz(1.8f * S, 0)); rY(t * .5f); rX(.35f); rZ(.2f); crown(2.3f * S, metal(.6f), P1); pop();
        bb("glow", gx(1.8f * S, 0), cy, gz(1.8f * S, 0), 9f * S, 9f * S, 0, rgba(P1, .45f));
        for (int i = 0; i < 8; i++) { float a = i * TAU / 8 + .2f, rr = R * (.5f + .18f * (i % 2)), hh = (1.8f + 4.4f * h(i, 5)) * S; push(); tr(gc(a) * rr, 0, gs(a) * rr); rY(-a); box(0, hh / 2, 0, .55f * S, hh / 2, .55f * S, 0x1A2630, 255); push(); tr(.5f * S, hh * .4f, 0); rZ(-.6f); box(0, 0, 0, .4f * S, .12f * S, .4f * S, 0x1A2630, 255); pop(); pop(); }
        for (int i = 0; i < 14; i++) {
            float a = t * .1f * (i % 2 == 0 ? 1 : -1) + i * TAU / 14, r = R * (.3f + .3f * h(i, 4)), y = CY + (h(i, 5) - .3f) * 7f * S, sp = 1f + .3f * gs(t * .7f + i);
            push(); tr(gc(a) * r, y, gs(a) * r); rY(h(i, 6) * TAU); rX(t * (h(i, 7) - .5f) * 2f);
            for (int sd = -1; sd <= 1; sd += 2) { push(); tr(sd * .1f * S * sp, 0, 0); rZ(sd * .08f); box(0, 0, 0, .45f * S, .95f * S, .07f * S, mix(P1, 0xFFFFFF, .2f), 255); pop(); } pop();
        }
        for (int i = 0; i < 20; i++) {   // rubble thrown up and falling
            float p = fract(t * .14f + i / 20f), a = h(i, 21) * TAU, r = R * (.2f + .7f * h(i, 22)), y = gs(p * PI) * (5f + 6f * h(i, 23)) * S;
            push(); tr(gc(a) * r, y, gs(a) * r); rY(t * h(i, 24) * 2f); rX(t * h(i, 25) * 2f); box(0, 0, 0, (.3f + .4f * h(i, 26)) * S, (.25f + .3f * h(i, 27)) * S, (.3f + .3f * h(i, 28)) * S, 0x2A3640, 255); pop();
        }
        int bf = (int) (t * 7);
        for (int k = 0; k < 3; k++) if (h(bf, 200 + k) < .45f) {   // lightning flickering down
            float a = h(bf, 210 + k) * TAU, r = R * (.2f + .6f * h(bf, 220 + k)); float[][] pts = new float[9][];
            for (int s = 0; s < 9; s++) pts[s] = v3(gc(a) * r + (h(bf * 9 + s, 230 + k) - .5f) * 2.4f * S * (s == 8 ? 0 : 1), top * 1.4f * (1f - s / 8f), gs(a) * r + (h(bf * 9 + s, 240 + k) - .5f) * 2.4f * S * (s == 8 ? 0 : 1));
            tube(pts, .22f * S, .1f * S, mix(P3, 0xFFFFFF, .8f)); bb("glow", gc(a) * r, 1f * S, gs(a) * r, 5f * S, 5f * S, 0, rgba(P3, .7f));
        }
        for (int i = 0; i < 4; i++) { float pw = fract(t * .3f + i / 4f); flat("crack", 0, .12f, 0, pw * R * 1.8f, i * 2f, rgba(P1, fd(pw))); }
        if (!LotmFx.hold(curId, "astral_world:anarchy_sparks", ex, ey + CY, ez, R * .7f)) sprites("spark", 60, RISEM, R * .7f, 0, top, .4f * S, P3, 1f, .3f, 67, true);
        gooSymbols(8, CY + 8f * S, R * .5f, 3.4f * S);
        S = S0;
    }


    private static final int[] SPECTRUM = {0xFF4040, 0xFF9030, 0xFFE040, 0x50E060, 0x40C0FF, 0x6060FF, 0xC060FF};
    /** Key of Light: a golden key standing upright through a vertical gate ring, seven spectrum beams fanning from it and shards of colour circling. */
    private void gooKey() {
        float gy = CY + 2.4f * S, gr = 4f * S;
        pushFace(); tr(0, gy, -1f * S);
        ring(gr, .24f * S, metal(.7f), 255); ring(gr * .82f, .08f * S, mix(P1, 0xFFFFFF, .3f), 255);
        for (int i = 0; i < 24; i++) { float a = i * TAU / 24; box(gc(a) * gr * .91f, gs(a) * gr * .91f, 0, .05f * S, (i % 2 == 0 ? .4f : .22f) * S, .05f * S, mix(P1, 0xFFFFFF, .3f), 255); }
        pop();
        bb("glow", gx(0, -1f * S), gy, gz(0, -1f * S), 12f * S, 12f * S, 0, rgba(0xFFFFFF, .4f));
        float kl = 7f * S, sw = gs(t * .4f) * .08f;
        pushFace(); tr(0, .8f * S, 0); rZ(sw);
        frustum(0, kl, .18f * S, .18f * S, 12, 1, 1, metal(.8f), 255, true);
        for (int k = 0; k < 4; k++) frustum(kl * (.12f + .2f * k), kl * (.12f + .2f * k) + .1f * S, .28f * S, .28f * S, 12, 1, 1, mix(P1, 0xFFFFFF, .4f), 255, true);
        push(); tr(0, kl + 1.2f * S, 0); torus(0, 0, 0, 1.2f * S, 1.2f * S, .22f * S, 24, 6, 0, TAU, metal(.85f), 255); torus(0, 0, 0, .8f * S, .8f * S, .08f * S, 24, 4, 0, TAU, mix(P1, 0xFFFFFF, .5f), 255); pop();
        for (int k = 0; k < 3; k++) box(.7f * S, (.08f + .07f * k) * kl, 0, .7f * S, .18f * S, .13f * S, metal(.8f), 255);
        pop();
        float kx = gx(0, 0), kz = gz(0, 0);
        for (int k = 0; k < 7; k++) {
            float a = -PI * .5f + (k - 3) * .38f + gs(t * .3f + k) * .03f, dxl = gc(a) * R * .9f, dyl = gs(a) * R * .9f;
            quad("solid", kx + sx0 * dxl * .5f, gy + dyl * .5f, kz + sz0 * dxl * .5f, sx0 * dxl * .5f, dyl * .5f, sz0 * dxl * .5f, -sz0 * .25f * S, 0, sx0 * .25f * S, rgba(SPECTRUM[k], .45f));
        }
        for (int k = 0; k < 7; k++) { float a = t * .18f + k * TAU / 7, rr = R * .55f; push(); tr(gc(a) * rr, gy + gs(t * .6f + k) * 1.2f * S, gs(a) * rr); rY(t * .5f + k); rX(.4f); frustum(0, 1.1f * S, .4f * S, 0, 4, 1, 1, SPECTRUM[k], 220, false); pop(); }
        for (int i = 0; i < 3; i++) { float pw = fract(t * .2f + i / 3f); flat("ring", 0, .12f, 0, pw * 1.8f * R, 0, rgba(SPECTRUM[(i * 2) % 7], fd(pw))); }
        sprites("spark", 50, RADIALM, R * .9f, gy, 0, .45f * S, 0xFFFFFF, 1f, .35f, 60, true);
        gooSymbols(5, CY - 1f * S, R * .5f, 2.8f * S);
    }

    /** Crouch-placed: the card lies there with its pathway symbol floating above it, no scene. */
    /** The small form of a Great Old One card: every one of its pathway symbols, side by side. */
    private void gooSymbolOnly(int g) {
        String[] ps = Tarot.GOO_PATHS[g]; int n = ps.length; float bob = (float) Math.sin(t * 1.6f) * .06f, sz = n == 1 ? 1.1f : n == 2 ? .85f : n == 3 ? .7f : .55f;
        flat("ring", 0, .05f, 0, 1.4f + .2f * n, t * .5f, rgba(P2, .9f)); bb("glow", 0, .85f + bob, 0, 1.7f + .3f * n, 1.7f + .3f * n, 0, rgba(P1, .3f));
        for (int k = 0; k < n; k++) { float off = (k - (n - 1) / 2f) * sz * 1.1f; symPlane(pidx(ps[k]), sx0 * off, .85f + bob, sz0 * off, sz, -face, 1f); }
        for (int i = 0; i < 4; i++) { float p = fract(t * .3f + i * .25f); bb("mote", (float) Math.cos(i * 1.6f + t) * .5f, p * 1.4f, (float) Math.sin(i * 1.6f + t) * .5f, .1f, .1f, 0, rgba(P1, fd(p))); }
    }
    private void symbolOnly() {
        float bob = (float) Math.sin(t * 1.6f) * .06f;
        flat("ring", 0, .05f, 0, 1.4f, t * .5f, rgba(P2, .9f));
        bb("glow", 0, .85f + bob, 0, 1.7f, 1.7f, 0, rgba(P1, .3f));
        symPlane(curPath, 0, .85f + bob, 0, 1.1f, -face, 1f);
        for (int i = 0; i < 4; i++) { float p = fract(t * .3f + i * .25f); bb("mote", (float) Math.cos(i * 1.6f + t) * .5f, p * 1.4f, (float) Math.sin(i * 1.6f + t) * .5f, .1f, .1f, 0, rgba(P1, fd(p))); }
    }

    // ================= Rare and Epic =================
    /** A small bespoke model for every rare pathway, so none of them is just a spinning sprite. Returns false for paths with their own scene above. */
    private boolean rareModel(int seq, float bob) {
        float u = seq == 4 ? 1f : .82f; int gl = mix(P1, 0xFFFFFF, .4f);
        flat("glow", 0, .06f, 0, 1.5f, 0, rgba(P1, .4f));
        switch (curPath) {
            case 0 -> {   // Apothecary: a potion, tiny ones orbiting, bubbles, a bat
                bb("glow", 0, .78f + bob, 0, 1.5f * u, 1.5f * u, 0, rgba(P1, .35f));   // a moon, not a potion: pale, cratered, turning slowly
                push(); tr(0, .78f + bob, 0); rY(t * .25f); sphere(0, 0, 0, .34f * u, .34f * u, .34f * u, 10, 16, 0xE4E8F4, 255);
                for (int k = 0; k < 7; k++) { float ka = k * 2.4f, ke = (h(k, 3) - .5f) * 2f; sphere(gc(ka) * gc(ke) * .31f * u, gs(ke) * .31f * u, gs(ka) * gc(ke) * .31f * u, (.04f + .03f * h(k, 4)) * u, (.04f + .03f * h(k, 4)) * u, (.04f + .03f * h(k, 4)) * u, 4, 6, 0xB4BACC, 255); }
                pop();
                for (int i = 0; i < 4; i++) { float a = -t * .9f + i * TAU / 4; push(); tr(gc(a) * .75f, .6f + gs(t * 1.5f + i) * .12f, gs(a) * .75f); rY(t + i); flask(.12f * u, mix(P3, 0xFFFFFF, .3f), i % 2 == 0 ? P2 : hsv(h(i, 5), .8f)); pop(); }
                for (int i = 0; i < 5; i++) { float p = fract(t * .5f + i * .2f); bb("drop", gc(i * 2f) * .12f, .9f + p * .8f, gs(i * 2f) * .12f, .1f, .1f, 0, rgba(P1, fd(p))); }
                if (seq == 4) { float a = t * 1.2f; push(); tr(gc(a) * .95f, 1.5f + gs(t * 3) * .1f, gs(a) * .95f); rY(-a); bat(.4f, t * 9f); pop(); }
            }
            case 1 -> {   // Apprentice: a door of light, drawn as vfx
                bb("glow", 0, .75f, 0, 1.7f, 1.7f, 0, rgba(P3, .55f * (.6f + .4f * (float) Math.sin(t))));
                facing("door", 0, .75f * u + bob, 0, .85f * u, 1.45f * u, 0, rgba(mix(P1, 0xFFFFFF, .4f), 1f));
                for (int i = 0; i < 4; i++) { float p = fract(t * .4f + i * .25f); bb("mote", gx(.12f * (i - 1.5f), .2f), .3f + p * .9f, gz(.12f * (i - 1.5f), .2f), .1f, .1f, 0, rgba(P3, fd(p))); }
            }
            case 2 -> {   // Arbiter: a balance
                push(); tr(0, .05f, 0); rY(t * .5f); scalesModel(1.1f * u, metal(.75f), P1); pop();
                bb("glow", 0, 1.15f * u, 0, .6f, .6f, 0, rgba(P3, .5f));
            }
            case 5 -> {   // Corpse Collector: soul fire and a pale skull, drawn as a model so nothing shows as a flat rectangle
                flameAt(0, .3f, -.05f, 1.1f * u, 3, P2, P1, P3, .42f);
                bb("glow", 0, .95f + bob, 0, 1.1f, 1.1f, 0, rgba(P1, .3f));
                push(); tr(0, .95f + bob, 0); rY(face); skull(.26f * u, mix(P3, 0xFFFFFF, .6f), P1); pop();
                for (int i = 0; i < 4; i++) { float p = fract(t * .35f + i * .25f), a = i * 1.7f + t * .5f; bb("mote", gc(a) * .5f, .3f + p * 1.1f, gs(a) * .5f, .12f, .12f, 0, rgba(P3, .7f * fd(p))); }
            }
            case 6 -> {   // Abyss: a glowing void with a bright rim and a few thin tendrils, the same size and light as the others
                float vy = .95f + bob;
                bb("glow", 0, vy, 0, 1.6f, 1.6f, 0, rgba(P1, .5f * (.8f + .2f * (float) Math.sin(t * 2f))));
                sphere(0, vy, 0, .2f * u, .2f * u, .2f * u, 8, 12, 0x050205, 255);
                bb("ring", 0, vy, 0, .62f * u, .62f * u, 0, rgba(mix(P3, 0xFFFFFF, .3f), .9f));
                for (int i = 0; i < 3; i++) wrapTentacle(0, vy, 0, .2f * u, .5f, i * TAU / 3, i % 2 == 0 ? 1 : -1, i, mix(P1, P3, .4f), .03f);
                for (int i = 0; i < 4; i++) { float p = fract(t * .4f + i * .25f), a = i * 1.6f; bb("mote", gc(a) * .35f, vy - .3f + p * .9f, gs(a) * .35f, .1f, .1f, 0, rgba(P3, fd(p))); }
            }
            case 8 -> {   // Lawyer: a floating crown and a coin
                push(); tr(0, .95f + bob, 0); rY(face); rX(.2f); crown(.36f * u, metal(.7f), P1); pop();
                for (int i = 0; i < 3; i++) { float a = -t + i * TAU / 3; bb("diamond", gc(a) * .65f, .8f + gs(t * 2 + i) * .1f, gs(a) * .65f, .16f, .16f, 0, rgba(P3, 1f)); }
            }
            case 10 -> {   // Monster: a wheel of fortune with a ticker, coins and a die
                push(); tr(0, .95f + bob, 0); rY(face); rZ(t * 1.1f); wheelModel(.5f * u, metal(.7f), metal(.5f), P1); pop();
                push(); tr(gx(0, 0), 1.5f * u + bob, gz(0, 0)); rY(face); rZ(PI); frustum(0, .14f * u, .04f, 0, 5, 1, 1, 0xE03030, 255, true); pop();
                for (int i = 0; i < 3; i++) { float a = t * .9f + i * TAU / 3; bb("disc", gc(a) * .8f, .7f + gs(t * 1.6f + i) * .15f, gs(a) * .8f, .2f, .2f, 0, rgba(0xFFD040, 1f)); }
                push(); tr(.7f, .35f, .3f); rY(t); rX(t * 1.3f); die(.055f); pop();
            }
            case 13 -> {   // Prisoner: a padlock bound in chains
                push(); tr(0, 1.7f, 0); chain(.8f, .14f, metal(.3f)); pop();
                push(); tr(0, .75f + bob, 0); rY(face); box(0, 0, 0, .22f * u, .18f * u, .1f * u, metal(.55f), 255); torus(0, .2f * u, 0, .12f * u, .14f * u, .035f * u, 10, 5, 0, PI, metal(.8f), 255);
                box(0, 0, .105f * u, .03f * u, .06f * u, .01f * u, 0x101010, 255); pop();
                for (int i = 0; i < 3; i++) { float a = t * .5f + i * TAU / 3; push(); tr(gc(a) * .6f, .2f, gs(a) * .6f); rY(-a); torus(0, .1f, 0, .1f, .12f, .03f, 8, 4, 0, TAU, metal(.3f), 255); pop(); }
            }
            case 14 -> {   // Reader: a small white tower
                bb("glow", 0, .9f, 0, 1.4f, 1.4f, 0, rgba(P3, .4f));
                push(); rY(face); towerModel(1.5f * u, .55f * u, mix(0xF0F0F4, P1, .15f), metal(.6f), P3); pop();
                for (int i = 0; i < 3; i++) { float a = t * .8f + i * TAU / 3; bb("page", gc(a) * .6f, .8f + gs(t * 1.4f + i) * .2f, gs(a) * .6f, .22f, .26f, a, rgba(P1, 1f)); }
            }
            case 15 -> {   // Sailor: a whirlpool with spray
                push(); tr(0, .08f, 0); ocean(.9f, .07f); pop(); waveRing(1.0f, .22f, 10, 1f, P1); flat("whirl", 0, .1f, 0, 1.8f, -t * .8f, rgba(P2, .9f));
                for (int i = 0; i < 6; i++) { float p = fract(t * .6f + i / 6f), a = i * 1.1f + t; bb("drop", gc(a) * .5f * p, .1f + gs(p * PI) * .8f, gs(a) * .5f * p, .14f, .2f, 0, rgba(P3, fd(p))); }
            }
            case 16 -> {   // Savant: three gears that really mesh, each turning the opposite way to its neighbour, faces toward you
                float rA = .34f * u, th0 = t * .5f; int[] nn = {10, 6, 5}; float[] rr = {rA, rA * .6f, rA * .5f};
                float[] al = {0f, 0f, 2.356f};   // direction from the big gear to each small one
                push(); rY(face); tr(0, 1.0f + bob, 0); push(); rZ(th0); gear(rr[0], 10, .1f, metal(.6f), P1); pop();
                for (int k = 1; k <= 2; k++) {
                    float d = rr[0] + rr[k] + .012f, aDeg = (float) Math.toDegrees(al[k]), ph = (float) Math.toRadians(aDeg + 180f + (10f / nn[k]) * aDeg - 180f / nn[k]);
                    push(); tr((float) Math.cos(al[k]) * d, (float) Math.sin(al[k]) * d, .0f); rZ(ph - (10f / nn[k]) * th0); gear(rr[k], nn[k], .09f, metal(k == 1 ? .45f : .7f), k == 1 ? P3 : P1); pop();
                }
                pop();
            }
            case 17 -> bearerSym(0, 1.0f + bob, 0, 1.1f * u, -face);
            case 18 -> {   // Seer: an armillary sphere
                push(); tr(0, 1.0f + bob, 0); armillary(.5f * u, metal(.6f), P1); pop();
                for (int i = 0; i < 4; i++) { float a = t * .6f + i * TAU / 4; bb("star", gc(a) * .8f, 1.0f + gs(a * 2) * .2f, gs(a) * .8f, .16f, .16f, t + i, rgba(P3, 1f)); }
            }
            case 19 -> {   // Sleepless: a small night orb with stars and a crescent
                sphere(0, 1.0f + bob, 0, .32f * u, .32f * u, .32f * u, 8, 12, 0x02030A, 255);
                for (int i = 0; i < 9; i++) { float a = t * .5f + i * 2.4f, el = (h(i, 3) - .5f) * 2.2f; bb("star", gc(a) * gc(el) * .34f * u, 1.0f + bob + gs(el) * .34f * u, gs(a) * gc(el) * .34f * u, .07f, .07f, 0, rgba(0xFFFFFF, .5f + .5f * gs(t * 2 + i))); }
                bb("glow", 0, 1.0f + bob, 0, 1.0f, 1.0f, 0, rgba(P1, .3f));
                facing("crescent", gx(.55f, 0), 1.25f, gz(.55f, 0), .5f, .5f, .5f, rgba(mix(P1, 0xFFFFFF, .5f), 1f));
            }
            case 20 -> {   // Spectator: a crystal with thought rings
                push(); tr(0, 1.0f + bob, 0); rY(face); frustum(0, .5f * u, .26f * u, 0, 4, 1, 1, gl, 235, false); frustum(-.5f * u, 0, 0, .26f * u, 4, 1, 1, mix(P3, 0xFFFFFF, .3f), 235, false); pop();
                bb("glow", 0, 1.0f + bob, 0, 1.0f, 1.0f, 0, rgba(P3, .5f));
                for (int i = 0; i < 3; i++) { float p = fract(t * .4f + i / 3f); bb("ring", 0, 1.0f + bob, 0, (.4f + 1.0f * p), (.4f + 1.0f * p), 0, rgba(P3, fd(p))); }
            }
            case 21 -> {   // Warrior: three swords like its icon, points down, the outer two crossed behind
                plantedSwords(1.5f * u, .04f, mix(P1, 0xFFFFFF, .45f), P2, P3);
                for (int i = 0; i < 4; i++) { float p = fract(t * .5f + i * .25f); bb("spark", gc(i * 1.6f) * .35f, .2f + p * 1.5f, gs(i * 1.6f) * .35f, .12f, .12f, t, rgba(P3, fd(p))); }
            }
            default -> { return false; }
        }
        for (int i = 0; i < 3; i++) { float p = fract(t * .3f + i * .33f); bb("mote", gc(i * 2.1f) * .6f, p * 1.6f, gs(i * 2.1f) * .6f, .1f, .1f, 0, rgba(P1, fd(p))); }
        return true;
    }
    private void rare(Cfg c, int seq) {
        float pulse = .8f + .2f * (float) Math.sin(t * 3);
        flat("ring", 0, .05f, 0, 1.0f, t * .5f, rgba(P2, .9f));
        column("beam", 0, .05f, 1.5f, 0, .35f, rgba(P1, .4f));
        float bob = (float) Math.sin(t * 2) * .06f;
        if (curPath == 3) {   // Demoness: a rose
            for (int i = 0; i < 7; i++) { float a = i * TAU / 7 + .3f, r = i == 0 ? 0f : .5f; push(); tr((float) Math.cos(a) * r, .05f, (float) Math.sin(a) * r); rY(i); rose(.34f + .1f * (i % 3), i % 2 == 0 ? P1 : P3, P2, mix(P2, 0x103018, .3f)); pop(); }
            sprites("petal", 8, FALLM, .6f, 0, 1.8f, .22f, P1, 1f, .3f, 3, true);
            return;
        }
        if (curPath == 9) { glitchSym(0, 1.1f + bob, 0, .75f, -face); return; }
        if (curPath == 7) {   // Hunter: a real flickering flame
            flameAt(0, .25f, 0, 1.3f, 2, P2, P1, P3, 1f);
            return;
        }
        if (curPath == 11) {   // Mystery Pryer: an eyeball that looks around
            float bl = Math.max(.15f, Math.abs((float) Math.cos(t * .6f)));
            bb("glow", 0, 1.1f + bob, 0, 1.1f, 1.1f, 0, rgba(P1, .35f)); bb("eye", 0, 1.1f + bob, 0, .9f, .9f * .5f * bl, 0, rgba(mix(P1, 0xFFFFFF, .3f), 1f));
            return;
        }
        if (curPath == 12) {   // Planter: a few small flowers
            for (int i = 0; i < 7; i++) { float a = i * TAU / 7 + .3f, r = i == 0 ? 0f : .5f; push(); tr((float) Math.cos(a) * r, .05f, (float) Math.sin(a) * r); rY(i); flowerModel(.38f + .1f * (i % 3), P1, mix(P1, P3, .5f), 0xFFD860, mix(P2, 0x103018, .2f)); pop(); }
            sprites("petal", 8, FALLM, .6f, 0, 1.8f, .22f, P1, 1f, .3f, 3, true);
            return;
        }
        if (rareModel(seq, bob)) return;
        stand(c.core, 0, 1.1f + bob, 0, seq == 4 ? .6f : .46f, seq == 4 ? .6f : .46f, -face, rgba(mix(P1, 0xFFFFFF, .4f), pulse));
        int motes = seq == 4 ? 5 : 3;
        for (int i = 0; i < motes; i++) {
            float a = t * 1.1f + i * TAU / motes;
            bb("mote", (float) Math.cos(a) * .5f, .6f + .5f * fract(t * .4f + i * .31f), (float) Math.sin(a) * .5f, .16f, .16f, 0, rgba(P1, 1f));
        }
    }

    /** The pathway symbol: a single solid bright plane that gently swings so it can be read from most sides. */
    private void sym(float y, float size, float spin) {
        symPlane(curPath, 0, y, 0, size, -face, 1f);
    }

    private void epic(Cfg c, int seq) {
        boolean two = seq == 2;
        float tp2 = two ? 3.8f : 3.2f;
        column("beam", 0, 0.05f, tp2, 0, two ? .7f : .5f, rgba(P1, .6f));
        column("beam", 0, 0.05f, tp2, 0, .2f, rgba(P3, .6f));
        flat("ring_dash", 0, .05f, 0, 2.4f, -t * .4f, rgba(P1, .9f));
        flat("ring", 0, .06f, 0, 1.3f, t * .6f, rgba(P2, .9f)); flat("ring_thick", 0, .07f, 0, 3f, t * .15f, rgba(P2, .6f)); if (two) flat("ring_dash", 0, .08f, 0, 3.6f, t * .3f, rgba(P3, .8f));   // seq 2 has every ring seq 3 has, and one more
        float cy = 1.9f + (float) Math.sin(t * 1.6) * .08f;
        if (curPath == 9) glitchSym(0, cy, 0, 1.9f, -face); else symPlane(curPath, 0, cy, 0, 1.9f, -face, 1f);
        int med = two ? 8 : 6;
        for (int i = 0; i < med; i++) {
            float a = i * TAU / med, r = 1.5f, y = .75f + (float) Math.sin(t * 2 + i) * .08f;
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
        y = Math.max(y, top * 1.1f); alpha *= .7f;   // always up in the sky, never in front of a symbol
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
    private float glX, glZ;   // while Lord of Mysteries glitches, the whole hall is knocked sideways by this much
    private void pushThrone() { push(); if (glX != 0 || glZ != 0) tr(glX, 0, glZ); rY(thQ * PI / 2); }
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
        symPlane(curPath, -fx * dist, y, -fz * dist, size, -face, 1f);
    }
    private float sideX(float k) { return sx0 * k; }
    private float sideZ(float k) { return sz0 * k; }

    private void moon(int N) {
        boolean m = N == 2;
        dome(R * 4f, 0x02030C, m ? 252 : 246);
        sigil(R); float my = top * .92f, mx = sideX(R * .62f), mz = sideZ(R * .62f);
        if (m) {
            float mr = 1.9f * S; mx = sideX(R * 1.5f); mz = sideZ(R * 1.5f); my = top * 1.05f;   // a full moon, small and far away
            bb("disc", mx, my, mz, mr * 2f, mr * 2f, 0, rgba(0xEAF0FF, 1f));
            bb("corona", mx, my, mz, mr * 4.5f, mr * 4.5f, t * .05f, rgba(P1, .5f));
        } else {
            facing("crescent", mx, my, mz, 9 * S, 9 * S, 0.5f, rgba(mix(P1, 0xFFFFFF, .6f), 1f));
        }
        column("beam", mx, 0, my - 3 * S, mz, 1.6f * S, rgba(P1, .35f));
        sym(CY, m ? 2.8f * S : 3.8f * S, 0);
        int nf = m ? 10 : 6;
        for (int i = 0; i < nf; i++) {   // potions: the legendary apothecary is drowned in them, in rings at several heights, every colour
            int ring = m ? i % 3 : 0; float a = t * (.35f + .12f * ring) * (ring == 1 ? -1 : 1) + i * TAU / nf * 1.0f, r = R * (.34f + .14f * ring), y = CY - 1.2f * S + ring * 1.1f * S + (float) Math.sin(a * 2 + i) * .9f * S;
            int liq = m ? (i % 4 == 0 ? 0xC01020 : mix(hsv(h(i, 61), .8f), P1, .25f)) : (i % 2 == 0 ? P1 : mix(P1, P2, .5f));
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(t + i); rZ((float) Math.sin(t + i) * .3f);
            flask((m ? .26f + .07f * (i % 3) : .5f) * S, mix(P3, 0xFFFFFF, .3f), liq); pop();
            glint((float) Math.cos(a) * r, y + .4f * S, (float) Math.sin(a) * r, .9f * S, liq, 1f);
        }
        for (int i = 0; i < (m ? 12 : 8); i++) {   // a ring of moon phases standing round the edge
            int nph = m ? 12 : 8; float a = i * TAU / nph + t * .06f, r = R * .85f, py = 2.2f * S + (float) Math.sin(t * .8f + i) * .4f * S;
            stand(i % 4 == 0 ? "disc" : "crescent", (float) Math.cos(a) * r, py, (float) Math.sin(a) * r, 1.8f * S, 1.8f * S, -a + PI / 2, rgba(mix(P1, 0xFFFFFF, .5f), .9f));
            bb("glow", (float) Math.cos(a) * r, py, (float) Math.sin(a) * r, 3.2f * S, 3.2f * S, 0, rgba(P1, .25f));
        }
        if (!m) {
            float cs = .6f * S; push(); tr(gx(0, R * .42f), 0, gz(0, R * .42f)); cauldron(cs, mix(P3, 0x000000, .55f), P1); pop();
            for (int i = 0; i < 6; i++) { float p = fract(t * .4f + h(i, 71)); bb("drop", gx(0, R * .42f) + (h(i, 72) - .5f) * cs, cs * .85f + p * 2.4f * S, gz(0, R * .42f) + (h(i, 73) - .5f) * cs, .3f * S, .3f * S, 0, rgba(P1, fd(p))); }
            for (int i = 0; i < 5; i++) { float a = -t * (.5f + .2f * h(i, 81)) + i * TAU / 5, r = R * (.4f + .4f * h(i, 82)), y = top * (.35f + .4f * h(i, 83)); push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(-a); bat(.4f * S, t * 9f + i); pop(); }
            sprites("star", 20, FALLM, R, 0, top, .5f * S, 0xFFFFFF, 1f, .3f, 78, true);
        }
        if (m) {
            float cs = .85f * S; push(); tr(gx(0, R * .42f), 0, gz(0, R * .42f)); cauldron(cs, mix(P3, 0x000000, .55f), 0xC01020); pop();   // a bubbling blood cauldron
            for (int i = 0; i < 12; i++) { float p = fract(t * .4f + h(i, 71)); bb("drop", gx(0, R * .42f) + (h(i, 72) - .5f) * cs, cs * .85f + p * 3f * S, gz(0, R * .42f) + (h(i, 73) - .5f) * cs, .35f * S, .35f * S, 0, rgba(0xC01020, fd(p))); }
            bb("glow", gx(0, R * .42f), cs * .9f, gz(0, R * .42f), 2.4f * S, 2.4f * S, 0, rgba(0xC01020, .5f));
            for (int i = 0; i < 12; i++) {   // vampire bats circling the moon
                float a = -t * (.5f + .2f * h(i, 81)) + i * TAU / 12, r = R * (.5f + .5f * h(i, 82)), y = top * (.45f + .45f * h(i, 83)) + (float) Math.sin(t * 2 + i) * S;
                push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(-a); bat(.5f * S, t * 9f + i); pop(); }
            for (int i = 0; i < 3; i++) { float pw = fract(t * .22f + i / 3f); flat("ring", 0, .1f, 0, pw * 2.4f * R, 0, rgba(0xC01020, fd(pw))); }
            sprites("drop", 40, FALLM, R, 0, top, .5f * S, 0xB01020, 1f, .35f, 77, false);
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
        if (m) {   // a small galaxy turning above the door
            float gyy = top * .62f; bb("glow", 0, gyy, 0, 5f * S, 5f * S, 0, rgba(mix(P3, 0xFFFFFF, .5f), .8f)); bb("corona", 0, gyy, 0, 3f * S, 3f * S, t * .3f, rgba(0xFFFFFF, .9f));
            for (int arm = 0; arm < 3; arm++) for (int k = 0; k < 46; k++) { float u = k / 46f, an = arm * TAU / 3 + u * 3.4f + t * .22f, rad = (.4f + u * 5.2f) * S, jit = (h(k + arm * 50, 221) - .5f) * .6f * S;
                float px = (float) Math.cos(an) * (rad + jit), pz = (float) Math.sin(an) * (rad + jit); int gc2 = mix(mix(0xFFFFFF, P3, u), P1, u * u);
                bb("glow", px, gyy + pz * .32f + px * .1f, pz * .95f, (.5f + .3f * (1 - u)) * S, (.5f + .3f * (1 - u)) * S, 0, rgba(gc2, .55f)); bb("mote", px, gyy + pz * .32f + px * .1f, pz * .95f, .16f * S, .16f * S, 0, rgba(0xFFFFFF, .9f)); }
        }
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
        pushFace(); scalesModel(L, metal(.55f), P1); pop();
        sym(CY + L * .55f + 1.5f * S, 3.4f * S, 0);
        for (int i = 0; i < 6 * N; i++) { float a = -t * .1f + i * TAU / (6 * N), r = R * .62f; float y = top * .5f + (float) Math.sin(t * 1.2f + i) * .4f;
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rZ(PI); rY(a);
            if (!drawGeo("silver_sword", "lotm", "textures/entity/silver_sword_entity.png", 3.6f * S)) sword(3.4f * S, metal(.85f), P1, P3); pop();
            column("line", (float) Math.cos(a) * r, y - 3.6f * S, y, (float) Math.sin(a) * r, .3f * S, rgba(P3, .4f)); }
        sprites("spark", 70 * N, FALLM, R, 0, top, .4f * S, P3, 1f, .15f, 6, true);
    }
    private void demoness(int N, boolean m) {
        sigil(R);
        flat("ring_dash", 0, .11f, 0, R * 1.0f, -t * .2f, rgba(P3, .8f));
        for (int s = -1; s <= 1; s += 2) {
            facing("wing", s * sx0 * 5.4f * S, CY + .2f * S, s * sz0 * 5.4f * S, s * 6f * S, 5.4f * S, 0, rgba(P1, .9f));
            facing("wing", s * sx0 * 6.8f * S, CY - 1.2f * S, s * sz0 * 6.8f * S, s * 3.8f * S, 3.6f * S, 0, rgba(P2, .85f));
        }
        for (int i = 0; i < (m ? 170 : 70); i++) { float a = h(i, 41) * TAU, r = R * 1.05f * (float) Math.sqrt(h(i, 42)); push(); tr((float) Math.cos(a) * r, 0, (float) Math.sin(a) * r); rY(h(i, 43) * TAU); rZ((float) Math.sin(t * .8f + i) * .05f);
            rose((.55f + .5f * h(i, 44)) * S, i % 3 == 0 ? P3 : P1, mix(P1, P2, .6f), mix(P2, 0x103018, .3f)); pop(); }
        sym(CY + 2.2f * S, 3.4f * S, 0);
        int vine = mix(P2, 0x103018, .4f);
        for (int i = 0; i < 6 * N; i++) {   // thorned arches wound with roses
            float a = i * TAU / (6 * N) + .2f, r = R * .62f; push(); tr((float) Math.cos(a) * r, 0, (float) Math.sin(a) * r); rY(-a + PI / 2);
            torus(0, 0, 0, 1.5f * S, 2.8f * S, .12f * S, 16, 5, 0, PI, vine, 255);
            for (int k = 1; k < 7; k++) { float u = k * PI / 7; push(); tr((float) Math.cos(u) * 1.5f * S, (float) Math.sin(u) * 2.8f * S, 0); rZ(u - PI / 2); taper(0, .35f * S, .05f * S, .05f * S, 0, 0, vine, 255); pop(); }
            push(); tr(0, 2.8f * S, 0); rose(.7f * S, P3, mix(P1, P2, .6f), vine); pop(); pop();
            glint((float) Math.cos(a) * r, 3.2f * S, (float) Math.sin(a) * r, .9f * S, P3, 1f); }
        if (m) {   // chaos: roses tumbling through the air and thorned vines writhing up from the ground
            for (int i = 0; i < 26; i++) { float a = t * (.3f + .4f * h(i, 3)) + h(i, 4) * TAU, r = R * (.2f + .7f * h(i, 5)), y = 1.5f * S + h(i, 6) * top * .8f + (float) Math.sin(t * 1.3f + i) * S;
                push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rX(t * (h(i, 7) - .5f) * 4f); rZ(t * (h(i, 8) - .5f) * 4f); rose((.5f + .4f * h(i, 9)) * S, i % 2 == 0 ? P3 : P1, mix(P1, P2, .6f), vine); pop(); }
            for (int k = 0; k < 6; k++) { float a = k * TAU / 6 + .5f, r0 = R * (.3f + .5f * h(k, 12)); float[][] pts = new float[18][];
                for (int j = 0; j < 18; j++) { float u = j / 17f; pts[j] = v3((float) Math.cos(a + u * 1.4f + (float) Math.sin(t + k) * .3f) * (r0 + u * 2f * S), u * top * .6f, (float) Math.sin(a + u * 1.4f + (float) Math.sin(t + k) * .3f) * (r0 + u * 2f * S)); }
                tube(pts, .28f * S, .05f * S, vine); }
        }
        sprites("petal", 120 * N, FALLM, R, 0, top, .7f * S, P1, 1f, .16f, 7, true);
        fogRing(10, R * .6f, P2, .5f);
        if (m) strikes(2, R, top, P1);
    }
    private int curId;
    /** LOTM's real flaring sun (a Photon effect), if LOTM is present. */
    private boolean lotmSun(float lx, float ly, float lz, float scale) { return LotmFx.hold(curId, "bard/flaring_sun", ex + lx, ey + ly, ez + lz, scale); }
    private void sun(int N) {
        sigil(R); float sk = N == 1 ? .8f : 1f, sy = top * (N == 1 ? .9f : 1.3f);   // seq 1 a fifth smaller, seq 0 higher
        if (!lotmSun(0, sy, 0, S * 5f * sk)) flareSun(0, sy, 0, 6.8f * S * sk, P1, P2);
        column("beam", 0, .05f, sy, 0, 2f * S, rgba(P1, .35f));
        sym(CY, 3.4f * S, 0);
        for (int i = 0; i < 10 * N; i++) { float a = t * .3f + i * TAU / (10 * N); column("line", (float) Math.cos(a) * R * .6f, 0, 5f * S, (float) Math.sin(a) * R * .6f, .3f * S, rgba(P1, .9f)); }
        sprites("note", 40 * N, RADIALM, R * 1.1f, CY, 0, 1.1f * S, P1, 1f, .22f, 9, true);
        sprites("spark", 70 * N, RISEM, R, 0, top, .35f * S, 0xFFFFFF, 1f, .3f, 11, true);
    }
    private void death(int N) {
        sigil(R); float s = .95f * S;
        pushThrone(); throneModel(s, mix(P1, 0x203024, .6f), mix(P1, 0xFFFFFF, .2f)); pop();
        float[] w = thW(.78f * s, .32f * s);
        flameAt(w[0], 3.75f * s, w[1], 1.6f * S, 3, P2, P1, P3, .95f);
        sym(4.8f * s + 1.8f * S, 3.2f * S, 0);
        for (int i = 0; i < 4 * N; i++) { float a = i * TAU / (4 * N) + .2f, rr = R * .9f;
            push(); tr((float) Math.cos(a) * rr, 0, (float) Math.sin(a) * rr); rY(PI - a + PI / 2);
            box(0, 1.0f * S, 0, .65f * S, 1.0f * S, .18f * S, 0x707878, 255); sphere(0, 2.0f * S, 0, .65f * S, .5f * S, .18f * S, 5, 10, 0x707878, 255);
            box(0, 1.5f * S, .19f * S, .08f * S, .42f * S, .01f * S, P1, 255); box(0, 1.7f * S, .19f * S, .28f * S, .07f * S, .01f * S, P1, 255); pop(); }
        for (int i = 0; i < 2 * N; i++) { float a = -t * .15f + i * TAU / (2 * N), rr = R * .6f; flameAt((float) Math.cos(a) * rr, 0, (float) Math.sin(a) * rr, 2.2f * S, i, P2, P1, P3, .9f); }
        sprites("mote", 28 * N, RISEM, R, 0, top, .45f * S, P1, 1f, .25f, 14, false);
        sprites("smoke", 8 * N, RISEM, R, 0, top * .8f, 2.4f * S, P2, .6f, .12f, 13, true);
        fogRing(10, R * .6f, P1, .4f);
    }
    private void abyss(int N, boolean m) {
        dome(R * 3.6f, 0x050008, m ? 252 : 246);
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
        if (m) {   // seq 0: the whole sphere is the abyss: swirls and cracks across the floor out to the dome, and swirls turning in the sky
            for (int i = 0; i < 9; i++) { float a = i * TAU / 9 + .4f, rr = R * (.9f + 1.6f * h(i, 231)), sz = (7f + 9f * h(i, 232)) * S; flat("whirl", (float) Math.cos(a) * rr, .1f + i * .005f, (float) Math.sin(a) * rr, sz, t * (i % 2 == 0 ? .5f : -.6f) + i, rgba(i % 2 == 0 ? 0x9A50E0 : 0xB02040, .85f)); }
            flat("whirl", 0, .04f, 0, 6.5f * R, -t * .15f, rgba(0x4A1470, .9f)); flat("whirl", 0, .05f, 0, 4.6f * R, t * .2f, rgba(0x701028, .8f));
            for (int i = 0; i < 22; i++) flat("crack", 0, .13f, 0, R * (2.4f + 1.6f * h(i, 233)), i * TAU / 22 + h(i, 234) * .3f, rgba(i % 2 == 0 ? 0xFF3050 : 0xC060FF, .85f));
            for (int i = 0; i < 12; i++) { float a = i * TAU / 12 + t * .02f, el = .15f + .6f * h(i, 235); bb("whirl", (float) Math.cos(a) * R * 2.9f * (float) Math.cos(el), (float) Math.sin(el) * R * 2.9f, (float) Math.sin(a) * R * 2.9f * (float) Math.cos(el), (10f + 8f * h(i, 236)) * S, (10f + 8f * h(i, 236)) * S, t * .3f + i, rgba(i % 2 == 0 ? 0x9A50E0 : 0xB02040, .55f)); }
        }
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
        for (int s = -1; s <= 1; s += 2) { push(); tr(s * 2.2f * S, 8.4f * S, -1.8f * S); rZ(PI - s * .35f);   // point down, driven into the ground
            if (!drawGeo("spear_of_dawn", "lotm", "textures/entity/spear_of_dawn.png", 9f * S)) sword(8f * S, metal(.8f), ir, P1); pop(); }
        pop();
        flameAt(0, 1.7f * S, 0, 6f * S, 1, P2, P1, P3, 1f);
        sym(top * .9f, 3.6f * S, 0);
        for (int i = 0; i < 8 * N; i++) { float a = i * TAU / (8 * N), rr = R * .75f;
            push(); tr((float) Math.cos(a) * rr, 0, (float) Math.sin(a) * rr); frustum(0, 2.8f * S, .12f * S, .1f * S, 6, 1, 1, ir, 255, true); frustum(2.8f * S, 3.1f * S, .1f * S, .26f * S, 8, 1, 1, ir, 255, true); pop();
            flameAt((float) Math.cos(a) * rr, 3.1f * S, (float) Math.sin(a) * rr, 2.2f * S, i + 5, P2, P1, P3, .95f); }
        String[] sp = {"spear_of_dawn", "spear_of_light", "unshadowed_spear"}, spt = {"textures/entity/spear_of_dawn.png", "textures/entity/spear_of_light.png", "textures/entity/unshadowed_spear.png"};
        int ns = N == 2 ? 26 : 12;   // spears driven into the ground all around, leaning outward
        for (int i = 0; i < ns; i++) { float a = i * TAU / ns + (i % 2) * .12f, rr = R * (.4f + .45f * ((i * 7) % 5) / 4f), L = (4.5f + 3f * h(i, 5)) * S; int v = N == 2 ? i % 3 : 0;
            push(); tr((float) Math.cos(a) * rr, L * .9f, (float) Math.sin(a) * rr); rY(-a); rZ(PI + .1f + .12f * h(i, 6)); drawGeo(sp[v], "lotm", spt[v], L); pop(); }   // point down, in the ground
        sprites("spark", 90 * N, RISEM, R, 0, top * 1.1f, .3f * S, P3, 1f, .5f, 18, true);
        sprites("smoke", 16 * N, RISEM, R * .5f, top * .3f, top, 3f * S, mix(P2, 0x000000, .6f), .5f, .1f, 19, true);
    }
    private void blackEmperor(int N, boolean m) {
        dome(R * 4f, 0x000000, m ? 252 : 246);
        sigil(R);
        mcStars(90 * N, R * 3.2f, 0xFFFFFF, P1, .5f * S);
        for (int i = 0; i < 6 * N; i++) { float a = i * TAU / (6 * N) + .3f; column("beam", (float) Math.cos(a) * R * .5f, 0, top, (float) Math.sin(a) * R * .5f, 1.4f * S, rgba(P1, .35f)); }
        pushThrone(); throneModel(.95f * S, mix(P3, 0x000000, .2f), metal(.6f)); pop();
        pushFace(); tr(0, 4.6f * S, 0); rY(t * .4f); crown(1.3f * S, metal(.7f), P1); pop();
        sym(CY + 5.6f * S, 3.2f * S, 0);
        for (int i = 0; i < 6 * N; i++) { float a = i * TAU / (6 * N) + .1f, r = R * .55f * (.7f + .3f * (i % 2)); push(); tr((float) Math.cos(a) * r, top + 1f, (float) Math.sin(a) * r); chain(top + 1f - 4f * S, .8f * S, mix(0x50545C, P1, .1f)); pop(); }
        for (int i = 0; i < 14 * N; i++) { float a = i * TAU / (14 * N), r = R * .85f; push(); tr((float) Math.cos(a) * r, 0, (float) Math.sin(a) * r); taper(0, (3f + 3f * h(i, 5)) * S, .35f * S, .35f * S, 0, 0, mix(P3, 0x000000, .2f), 255); pop(); }
        if (m) {   // space bends round the throne: expanding warped rings, stepped tears across the sky and a jitter that comes and goes
            for (int i = 0; i < 4; i++) { float pw = fract(t * .18f + i / 4f), rr = pw * R * 1.6f; push(); tr(0, CY + 1f * S, 0); rX(PI / 2 + (float) Math.sin(t + i) * .25f); rZ(t * .2f + i); torus(0, 0, 0, rr, rr * (.85f + .15f * (float) Math.sin(t * 2f + i)), .08f * S, 28, 3, 0, TAU, mix(P1, 0xFFFFFF, .3f), (int) (200 * (1 - pw))); pop(); }
            float stp = (float) Math.floor(t * 5f); boolean tear = fract(t * .23f) < .3f;
            for (int i = 0; i < 12; i++) { if (!tear && i > 3) break; float yy = h((int) stp + i, 241) * top, ww = (4f + 20f * h((int) stp + i, 242)) * S, xo = (h((int) stp + i, 243) - .5f) * 6f * S;
                bb("solid", gx(xo, 0), yy, gz(xo, 0), ww, (.06f + .25f * h((int) stp + i, 244)) * S, 0, rgba(i % 2 == 0 ? P1 : 0xFFFFFF, .22f)); }
            for (int i = 0; i < 3; i++) { float a = i * TAU / 3 + t * .3f; bb("glow", (float) Math.cos(a) * R * .35f, CY + 2f * S + (float) Math.sin(t * 1.7f + i) * S, (float) Math.sin(a) * R * .35f, 5f * S, 2f * S, 0, rgba(0xFF2040, .18f)); }
        }
        sprites("spark", 70 * N, FALLM, R, 0, top, .45f * S, 0xFFFFFF, 1f, .15f, 20, true);
        fogRing(8, R * .6f, P2, .4f);
    }

    /** Marauder: a server error. Stepped, choppy time; glitching symbol; error windows; binary rain; blocks that glitch onto the real ground. */
    private void error(int N) {
        float t0 = t; t = (float) Math.floor(t * 4f) / 4f;
        int PUR = 0xA050FF;
        sigil(R);
        glitchSym(0, CY, 0, 4.2f * S, -face);
        float stp = (float) Math.floor(t * 3f);   // chromatic ghosts and dead pixels that jump with the stepped time
        for (int g = -1; g <= 1; g += 2) if (h((int) stp + g, 3) < .6f) symPlane(9, gx(g * (.3f + .5f * h((int) stp, 4)) * S, 0), CY + (h((int) stp + g, 5) - .5f) * .6f * S, gz(g * (.3f + .5f * h((int) stp, 4)) * S, 0), 4.2f * S, -face, .35f);
        for (int i = 0; i < 18; i++) { int sl = (int) Math.floor(t * 2f + h(i, 6) * 5); float px = (h(sl * 3 + i, 7) - .5f) * 2f * R * .8f, pz = (h(sl * 5 + i, 8) - .5f) * 2f * R * .8f, py = h(sl * 7 + i, 9) * top * .8f, sz2 = (.25f + .5f * h(i, 10)) * S;
            bb("solid", px, py, pz, sz2, sz2, 0, rgba(i % 3 == 0 ? 0xFFFFFF : i % 3 == 1 ? PUR : 0xFF2060, .9f)); }
        { float bw = 5f * S, by = CY - 3.6f * S, prog = (float) Math.min(1f, fract(t * .12f) * 1.3f); prog = Math.round(prog * 12f) / 12f;   // a stuttering loading bar
          quad("solid", gx(0, 0), by, gz(0, 0), sx0 * bw / 2, 0, sz0 * bw / 2, 0, .22f * S, 0, rgba(0x101018, .85f));
          quad("solid", gx(-bw / 2 + bw * prog / 2, 0), by, gz(-bw / 2 + bw * prog / 2, 0), sx0 * bw * prog / 2, 0, sz0 * bw * prog / 2, 0, .16f * S, 0, rgba(PUR, .95f)); }
        int nw = 2 * N;
        for (int i = 0; i < nw; i++) {   // error windows that jump around, each in its own sector so they do not pile up
            int slot = (int) Math.floor(t / 1.4f + h(i, 5) * 3);
            float a = (i + .15f + .7f * h(slot * 5 + i, 71)) * TAU / nw, r = R * (.75f + .3f * h(slot * 3 + i, 72)), y = 1.5f * S + h(slot * 7 + i, 73) * top * .55f;
            if (fract(t * .7f + h(i, 8)) < .85f) panel("errwin", (float) Math.cos(a) * r, y, (float) Math.sin(a) * r, 3.0f * S, 1.9f * S, a + PI / 2, rgba(0xFFFFFF, 1f));
        }
        String[] words = {"errtext", "nulltext", "deadtext", "t404"};
        for (int i = 0; i < 2; i++) { if (fract(t * .5f + h(i, 31)) < .6f) { int slot = (int) Math.floor(t * .5f + h(i, 31));
            float a = (i * .5f + .1f + .3f * h(slot + i, 81)) * TAU, r = R * (.5f + .3f * h(slot + i, 82)); bb(words[(slot + i) & 3], (float) Math.cos(a) * r, 1f * S + top * (.45f + .4f * h(slot + i, 83)), (float) Math.sin(a) * r, 4f * S, 1f * S, 0, rgba(0xFFFFFF, 1f)); } }
        for (int c = 0; c < 2 * N; c++) {   // binary rain
            float a = c * TAU / (2 * N) + .3f, r = R * (.35f + .35f * h(c, 4));
            for (int k = 0; k < 4; k++) { float yy = top - fract(t * .35f * (.6f + h(c, 5)) + k / 4f + h(c, 6)) * top; bb(((c + k + (int) (t * 2)) & 1) == 0 ? "bit0" : "bit1", (float) Math.cos(a) * r, yy, (float) Math.sin(a) * r, .55f * S, .55f * S, 0, rgba(P3, .6f)); }
        }
        for (int k = 0; k < 3; k++) {   // 3x3 patches of glitched ground on the real blocks, one to each third of the circle so they never overlap
            float q = t / 3.4f + k / 3f; int slot = (int) Math.floor(q); float ph = fract(q), fa = Math.min(1f, Math.min(ph, 1f - ph) * 5f);
            float pa = (k + .15f + .7f * h(slot * 5 + k, 92)) * TAU / 3, pr = R * (.4f + .35f * h(slot * 3 + k, 91));
            int bx = (int) Math.floor(ex + Math.cos(pa) * pr), bz = (int) Math.floor(ez + Math.sin(pa) * pr);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                double tp2 = groundTop(bx + dx, (int) Math.floor(ey), bz + dz); boolean w = ((dx + dz) & 1) == 0;
                int col = w ? 0x7A2CE0 : 0x07030C; float cxq = (float) (bx + dx + .5 - ex), cyq = (float) (tp2 - ey) + .03f, czq = (float) (bz + dz + .5 - ez);
                eq(tex("solid"), cxq, cyq, czq, .5f, 0, 0, 0, 0, .5f, rgba(col, fa * .95f), 0, 0, 1, 1); eq(tex("solid"), cxq, cyq, czq, -.5f, 0, 0, 0, 0, .5f, rgba(col, fa * .95f), 0, 0, 1, 1);
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
        float wr = 3.0f * S, wy = Math.max(top * .5f, wr + .5f), yaw = -face, c = (float) Math.cos(yaw), s2 = (float) Math.sin(yaw);
        // it spins up, slows and clicks onto a segment, then rests for a few seconds before the next spin
        float cyc = t / 9f, ph = fract(cyc); int turn = (int) Math.floor(cyc);
        float sp = Math.min(1f, ph / .4f), land = ((turn * 2.1f) % TAU), spin = (1f - (float) Math.pow(1f - sp, 3f)) * 3f * TAU;
        float roll = land + spin + (ph > .4f && ph < .5f ? (float) Math.sin((ph - .4f) * 120f) * .03f * (1f - (ph - .4f) * 10f) : 0f), cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
        // plane behind: pointed rays, soft glow
        bb("lotm:sunburst", 0, wy, 0, wr * 3.6f, wr * 3.6f, t * .05f, rgba(P1, .3f));
        bb("glow", 0, wy, 0, wr * 3.4f, wr * 3.4f, 0, rgba(P1, .3f));
        for (int sdn = -1; sdn <= 1; sdn += 2) eq(tex("fortune"), -s2 * sdn * .014f * wr, wy, c * sdn * .014f * wr, c * cr * wr, sr * wr, s2 * cr * wr, -c * sr * wr, cr * wr, -s2 * sr * wr, rgba(mix(0xFFFFFF, P3, .1f), 1f), 0, 0, 1, 1);
        push(); tr(0, wy, 0); rY(-yaw); rZ(roll);
        push(); rX(PI / 2); frustum(-.011f * wr, .011f * wr, wr * .99f, wr * .99f, 32, 1, 1, mix(0x101428, P2, .25f), 255, true); pop();   // solid backing so nothing shows through the wheel
        ring(wr * 1.0f, .075f * wr, metal(.7f), 255); ring(wr * .62f, .035f * wr, metal(.5f), 255); ring(wr * .22f, .03f * wr, metal(.8f), 255);
        for (int i = 0; i < 8; i++) { push(); rZ(i * TAU / 8); box(0, wr * .5f, .03f * wr, .03f * wr, wr * .5f, .03f * wr, metal(.55f), 255); box(0, wr * .5f, -.03f * wr, .03f * wr, wr * .5f, .03f * wr, metal(.55f), 255); pop(); }
        for (int i = 0; i < 24; i++) { float a = i * TAU / 24; boolean lit = ((i + (int) Math.floor(t * (ph < .4f ? 10f : 2f))) % 4) == 0;   // chasing bulbs round the rim
            sphere(gc(a) * wr * 1.03f, gs(a) * wr * 1.03f, 0, .05f * wr, .05f * wr, .05f * wr, 4, 6, lit ? 0xFFFFFF : mix(P1, 0x402010, .3f), 255); }
        for (int i = 0; i < 8; i++) { float a = (i + .5f) * TAU / 8; sphere(gc(a) * wr * .62f, gs(a) * wr * .62f, 0, .045f * wr, .045f * wr, .045f * wr, 4, 6, P3, 255); }
        sphere(0, 0, .02f * wr, .17f * wr, .17f * wr, .1f * wr, 6, 10, metal(.8f), 255);
        for (int i = 0; i < 8; i++) for (int sdz = -1; sdz <= 1; sdz += 2) {   // the prizes on the wheel, both faces: coin, clover, gem, card
            float a = (i + .5f) * TAU / 8; push(); tr(gc(a) * wr * .8f, gs(a) * wr * .8f, sdz * .05f * wr); rZ(a - PI / 2);
            switch (i % 4) {
                case 0 -> sphere(0, 0, 0, .1f * wr, .1f * wr, .025f * wr, 5, 8, 0xFFD040, 255);
                case 1 -> { for (int k = 0; k < 3; k++) { float b = k * TAU / 3; sphere(gc(b) * .055f * wr, gs(b) * .055f * wr + .03f * wr, 0, .055f * wr, .055f * wr, .02f * wr, 4, 6, 0x40D060, 255); } }
                case 2 -> sphere(0, 0, 0, .07f * wr, .1f * wr, .04f * wr, 4, 4, 0x40C0FF, 255);
                default -> { box(0, 0, 0, .07f * wr, .1f * wr, .012f * wr, 0xF4ECD0, 255); box(0, 0, sdz * .012f * wr, .04f * wr, .06f * wr, .004f * wr, P1, 255); }
            }
            pop();
        }
        pop();
        for (int i = 0; i < 24; i++) { float a = i * TAU / 24 + roll; if (((i + (int) Math.floor(t * (ph < .4f ? 10f : 2f))) % 4) != 0) continue;
            bb("glow", (c * gc(a)) * wr * 1.03f, wy + gs(a) * wr * 1.03f, (s2 * gc(a)) * wr * 1.03f, .5f * S, .5f * S, 0, rgba(0xFFF0B0, .85f)); }
        // the ticker at the top that the pegs flick
        float flick = Math.max(0f, (float) Math.sin(roll * 8f)) * .3f * (ph < .4f ? 1f - ph / .4f : 0f);
        push(); tr(0, wy + wr * 1.12f, 0); rY(-yaw); rZ(PI + flick); frustum(0, .3f * wr, .08f * wr, 0, 6, 1, 1, 0xE03030, 255, true); sphere(0, .3f * wr, 0, .06f * wr, .06f * wr, .06f * wr, 4, 6, metal(.8f), 255); pop();
        symPlane(curPath, -s2 * .13f * wr, wy, c * .13f * wr, wr * .8f, yaw, 1f); symPlane(curPath, s2 * .13f * wr, wy, -c * .13f * wr, wr * .8f, yaw + PI, 1f);   // the symbol on the wheel's hub, both faces, clear of the backing
        for (int i = 0; i < 6; i++) {   // fortune cards, coins and clovers that the wheel throws out
            float a = t * .35f + i * TAU / 6, r = R * .7f, y = wy + (float) Math.sin(t * 1.1f + i * 1.9f) * 2f * S;
            bb("disc", (float) Math.cos(a) * r, y, (float) Math.sin(a) * r, 1.2f * S, 1.2f * S, 0, rgba(0xFFD040, 1f));
            bb("ring", (float) Math.cos(a) * r, y, (float) Math.sin(a) * r, 1.2f * S, 1.2f * S, 0, rgba(0xFFF4B0, 1f)); }
        for (int i = 0; i < 4 * N; i++) {
            float a = t * .4f + i * TAU / (4 * N), r = R * .85f, y = 1.4f * S + (float) Math.sin(a * 2 + i) * .8f * S;
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(t * 1.3f + i); rX(t * 1.7f + i); die(.28f * S); pop();
        }
        sprites("disc", 40 * N, FALLM, R, 0, top, .6f * S, 0xFFD040, 1f, .25f, 25, true);
        sprites("leaf", 30 * N, SPIRALM, R, 0, top, .7f * S, 0x50D060, 1f, .18f, 26, true);
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
        bb("glow", 0, ly - 2.4f * S, 0, 3.4f * S, 3.4f * S, 0, rgba(P3, .4f));   // small, so it no longer washes out the symbol behind it
        symFront(CY + .6f * S, 3.4f * S, 4.5f * S);
        for (int i = 0; i < 7 * N; i++) { float a = t * .15f + i * TAU / (7 * N), r = R * .7f, y = 2f * S + 1.8f * S * (i % 3);
            push(); tr((float) Math.cos(a) * r, y + 2.4f * S, (float) Math.sin(a) * r); chain(2.2f * S, .45f * S, metal(.3f)); pop();
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(a); lanternModel(1.5f * S, metal(.35f), mix(P3, 0xFFFFFF, .3f)); pop();
            flameAt((float) Math.cos(a) * r, y + .3f * S, (float) Math.sin(a) * r, .7f * S, i, P3, mix(P3, 0xFFFFFF, .4f), 0xFFFFFF, 1f);
            bb("glow", (float) Math.cos(a) * r, y + .6f * S, (float) Math.sin(a) * r, 1.6f * S, 1.6f * S, 0, rgba(P3, .4f)); }
        for (int i = 0; i < 9 * N; i++) {   // eyes opening in the dark
            float a = t * .1f + i * TAU / (9 * N), r = R * (.55f + .35f * h(i, 2)), y = 1.5f * S + h(i, 3) * top * .75f, bl = Math.max(.12f, Math.abs((float) Math.cos(t * .5f + i * 1.9f)));
            bb("eye", (float) Math.cos(a) * r, y, (float) Math.sin(a) * r, 2.4f * S, 1.2f * S * bl, 0, rgba(P1, 1f)); }
        sprites("mote", 40 * N, DRIFTM, R, .5f, top, .4f * S, 0xFFFFFF, 1f, .2f, 24, false);
        fogRing(10, R * .6f, P2, .5f);
    }
    private void mother(int N) {
        sigil(R);
        facing("rays", 0, CY, 0, 12 * S, 12 * S, t * .1f, rgba(P1, .35f));
        for (int i = 0; i < 50; i++) { float a = h(i, 51) * TAU, r = R * 1.05f * (float) Math.sqrt(h(i, 52)); push(); tr((float) Math.cos(a) * r, 0, (float) Math.sin(a) * r); rY(h(i, 53) * TAU); rZ((float) Math.sin(t * .7f + i) * .06f);
            flowerModel((.7f + .6f * h(i, 54)) * S, mix(i % 2 == 0 ? P1 : P3, 0xFFFFFF, .15f), mix(P1, P3, .5f), 0xFFD860, mix(P2, 0x103018, .2f)); pop(); }
        sym(CY + 4.4f * S, 3.4f * S, 0);
        for (int i = 0; i < 6 * N; i++) { float a = i * TAU / (6 * N), rr = R * .75f;
            push(); tr((float) Math.cos(a) * rr, 0, (float) Math.sin(a) * rr); rY(a); rZ((float) Math.sin(t + i) * .06f); flowerModel(1.6f * S, mix(P3, 0xFFFFFF, .1f), P1, 0xFFD860, mix(P2, 0x103018, .2f)); pop(); }
        sprites("petal", 28 * N, SPIRALM, R, 0, top, .7f * S, P3, 1f, .14f, 25, true);
        sprites("leaf", 8 * N, FALLM, R, 0, top, 1f * S, P2, 1f, .12f, 26, true);
        sprites("mote", 22 * N, RISEM, R, 0, top, .3f * S, 0xFFF0A0, 1f, .25f, 27, false);
    }
    private void chained(int N, boolean m) {
        sigil(R); int iron = mix(0x707890, P1, .15f);
        column("beam", 0, 0, top, 0, 2.6f * S, rgba(P1, .22f));
        float cr = R * .34f, chh = top * .78f;
        for (int i = 0; i < 4 * N; i++) { float a = i * TAU / (4 * N) + .3f, r = cr * 1.6f, y = chh * (.4f + .22f * (i % 2)) + (float) Math.sin(t * .8f + i) * .2f * S;   // padlocks hanging on chains, facing you
            push(); tr((float) Math.cos(a) * r, top + 1.5f, (float) Math.sin(a) * r); chain(top + 1.5f - (y + 1.4f * S) + .3f * S, .5f * S, iron); pop();   // hangs right down onto the lock's shackle
            push(); tr((float) Math.cos(a) * r, y, (float) Math.sin(a) * r); rY(face); lockModel(1.4f * S, mix(iron, 0x303848, .3f), metal(.7f)); pop(); }
        for (int i = 0; i < 7 * N; i++) { float a = i * TAU / (7 * N), r = R * (.6f + .3f * h(i, 2)); push(); tr((float) Math.cos(a) * r + (float) Math.sin(t * .8f + i) * .15f * S, top + 1.5f, (float) Math.sin(a) * r); chain(top + 1.5f - 1.4f * S * (1 + h(i, 3)), .9f * S, iron); pop(); }
        if (m) for (int i = 0; i < 28; i++) { float a = -t * .12f + i * TAU / 28, rr = R * .55f; push(); tr((float) Math.cos(a) * rr, CY + 2.8f * S, (float) Math.sin(a) * rr); rY(-a); if ((i & 1) == 1) rX(PI / 2);
            torus(0, 0, 0, .28f * S, .5f * S, .1f * S, 8, 4, 0, TAU, iron, 255); pop(); }
        for (int i = 0; i < 3 * N; i++) { float a = i * TAU / (3 * N) + .6f, r = R * .75f;
            push(); tr((float) Math.cos(a) * r, 0, (float) Math.sin(a) * r); rY(-a + PI / 2); torus(0, .55f * S, 0, .55f * S, .6f * S, .12f * S, 14, 4, -.4f, PI + .4f, iron, 255); box(0, -.1f * S, 0, .6f * S, .16f * S, .18f * S, mix(iron, 0x000000, .3f), 255); pop();
            push(); tr((float) Math.cos(a) * r, .6f * S, (float) Math.sin(a) * r); chain(3.2f * S * (1f), .5f * S, iron); pop(); }
        symFront(CY, 3.8f * S, cr * .55f);
        sprites("chain", 14 * N, FALLM, R, 0, top, .9f * S, mix(iron, 0xFFFFFF, .3f), 1f, .2f, 28, true);
        sprites("spark", 40 * N, RISEM, R, 0, top, .35f * S, P1, 1f, .3f, 29, true);
        fogRing(8, R * .6f, P2, .45f);
    }
    private void whiteTower(int N) {
        sigil(R); float H = top * .92f, ox = gx(0, -R * .55f), oz = gz(0, -R * .55f);
        pushFace(); tr(0, 0, -R * .55f); towerModel(H, 1.6f * S, mix(P1, 0x3A4A70, .55f), mix(P1, 0x6078A0, .4f), P3); pop();
        for (int i = 0; i < 2; i++) { float a = t * .5f * (i == 0 ? 1 : -1) + i * PI; float len = 8 * S;
            quad("beam", ox + (float) Math.cos(a) * len / 2, H * .93f, oz + (float) Math.sin(a) * len / 2, 0, .3f * S, 0, (float) Math.cos(a) * len / 2, 0, (float) Math.sin(a) * len / 2, rgba(P3, .35f)); }
        for (int i = 0; i < 7 * N; i++) { float yy = (i + 1) * H / (7 * N + 1); flat("ring_dash", ox, yy, oz, (4f + 1.4f * (float) Math.sin(i * .9f)) * S, t * (i % 2 == 0 ? .7f : -.7f), rgba(i % 2 == 0 ? P1 : P2, .8f)); }
        symFront(CY + .8f * S, 3.2f * S, 3.4f * S);
        sprites("page", 24 * N, SPIRALM, R * .9f, 0, top, 1.3f * S, mix(P1, 0xFFFFFF, .3f), .9f, .13f, 30, true);
        clouds(7 * N, R * 1.2f, top * .3f, mix(P1, 0xFFFFFF, .3f), .22f, R * 1.1f);
        sprites("mote", 60 * N, RISEM, R * .6f, 0, top, .35f * S, P1, 1f, .3f, 31, false);
    }

    // ---- Savant: a steam train on a ring of track, and an airship ----
    private void trainWheels(float s, float x, float roll, int col, int brass) {
        for (int sd = -1; sd <= 1; sd += 2) { push(); tr(x, .45f * s, sd * .62f * s); rZ(-roll);
            push(); rX(PI / 2); frustum(-.04f * s, .04f * s, .45f * s, .45f * s, 14, 1, 1, col, 255, true); pop();
            for (int k = 0; k < 4; k++) { push(); rZ(k * PI / 4); box(0, 0, sd * .05f * s, .43f * s, .03f * s, .015f * s, brass, 255); pop(); }
            sphere(0, 0, sd * .05f * s, .09f * s, .09f * s, .06f * s, 4, 6, brass, 255); pop(); }
    }
    private void trainLoco(float s, float roll, int body, int trim) {   // forward is +x, up +y, the rails at y = 0
        int blk = 0x15171C, brass = 0xC8A040;
        push(); tr(.3f * s, 1.05f * s, 0); rZ(-PI / 2); frustum(-1.5f * s, 1.5f * s, .52f * s, .52f * s, 14, 1, 1, body, 255, true); pop();
        sphere(1.8f * s, 1.05f * s, 0, .3f * s, .52f * s, .52f * s, 6, 12, blk, 255);
        for (int k = 0; k < 3; k++) { push(); tr(-.9f * s + k * 1.1f * s, 1.05f * s, 0); rZ(-PI / 2); frustum(-.05f * s, .05f * s, .54f * s, .54f * s, 14, 1, 1, brass, 255, true); pop(); }
        push(); tr(1.3f * s, 1.55f * s, 0); frustum(0, .5f * s, .14f * s, .12f * s, 8, 1, 1, blk, 255, true); frustum(.5f * s, .72f * s, .12f * s, .25f * s, 8, 1, 1, blk, 255, true); pop();
        sphere(.2f * s, 1.58f * s, 0, .2f * s, .2f * s, .2f * s, 5, 8, brass, 255);
        box(-1.55f * s, 1.4f * s, 0, .55f * s, .75f * s, .55f * s, body, 255); box(-1.55f * s, 2.2f * s, 0, .72f * s, .06f * s, .62f * s, trim, 255);
        for (int sd = -1; sd <= 1; sd += 2) box(-1.55f * s, 1.55f * s, sd * .56f * s, .3f * s, .26f * s, .01f * s, 0xFFE9A0, 255);
        box(0, .62f * s, 0, 2.1f * s, .07f * s, .62f * s, blk, 255); box(2.15f * s, .55f * s, 0, .22f * s, .16f * s, .5f * s, blk, 255);
        sphere(2.12f * s, 1.05f * s, 0, .06f * s, .06f * s, .06f * s, 4, 6, 0xFFF4C0, 255);
        for (int k = 0; k < 3; k++) trainWheels(s, (-1.1f + k * 1.1f) * s, roll, blk, brass);
        for (int sd = -1; sd <= 1; sd += 2) box(.0f * s + .1f * s * (float) Math.cos(roll), .45f * s + .1f * s * (float) Math.sin(roll), sd * .7f * s, 1.15f * s, .03f * s, .02f * s, brass, 255);
    }
    private void trainCar(float s, float roll, int body, int trim, boolean tender) {
        int blk = 0x15171C, brass = 0xC8A040;
        if (tender) { box(0, .9f * s, 0, 1.2f * s, .45f * s, .56f * s, blk, 255); for (int k = 0; k < 9; k++) sphere((h(k, 31) - .5f) * 1.8f * s, 1.4f * s, (h(k, 32) - .5f) * .8f * s, .2f * s, .16f * s, .2f * s, 4, 6, 0x303038, 255); }
        else { box(0, 1.0f * s, 0, 1.5f * s, .6f * s, .58f * s, body, 255); box(0, 1.7f * s, 0, 1.58f * s, .07f * s, .64f * s, trim, 255);
            for (int k = -2; k <= 2; k++) for (int sd = -1; sd <= 1; sd += 2) box(k * .55f * s, 1.15f * s, sd * .59f * s, .2f * s, .22f * s, .01f * s, 0xFFE9A0, 255); }
        box(0, .5f * s, 0, 1.4f * s, .06f * s, .5f * s, blk, 255);
        trainWheels(s, -.8f * s, roll, blk, brass); trainWheels(s, .8f * s, roll, blk, brass);
    }
    private void airship(float s) {   // nose toward +x
        int env = 0xD8D2C0, rib = 0x8A6A3A, red = 0xB02830;
        sphere(0, 0, 0, 4f * s, 1.5f * s, 1.5f * s, 10, 20, env, 255);
        for (int k = -3; k <= 3; k++) { float x = k * .95f * s, r = 1.5f * s * (float) Math.sqrt(Math.max(.05f, 1f - (x / (4f * s)) * (x / (4f * s)))); push(); tr(x, 0, 0); rY(PI / 2); torus(0, 0, 0, r * 1.01f, r * 1.01f, .04f * s, 14, 3, 0, TAU, rib, 255); pop(); }
        box(0, -1.95f * s, 0, 1.3f * s, .35f * s, .45f * s, 0x7A5230, 255); box(0, -1.9f * s, .0f, 1.2f * s, .2f * s, .47f * s, 0xFFE9A0, 255);
        for (int sd = -1; sd <= 1; sd += 2) for (int k = -1; k <= 1; k += 2) box(k * .8f * s, -1.4f * s, sd * .3f * s, .03f * s, .5f * s, .03f * s, rib, 255);
        for (int k = 0; k < 4; k++) { push(); tr(-3.3f * s, 0, 0); rX(k * PI / 2); box(-.2f * s, 1.55f * s, 0, .6f * s, .55f * s, .04f * s, red, 255); pop(); }
        for (int sd = -1; sd <= 1; sd += 2) { push(); tr(-1.3f * s, -1.9f * s, sd * .75f * s); rX(t * 14f); box(0, 0, 0, .03f * s, .55f * s, .07f * s, 0x303038, 255); box(0, 0, 0, .03f * s, .07f * s, .55f * s, 0x303038, 255); pop(); }
        bb("glow", 0, -1.9f * s, 0, 1.6f * s, 1.6f * s, 0, rgba(0xFFD870, .5f));
    }
    private void paragon(int N) {
        sigil(R); boolean m = N == 2; float s = (m ? .7f : .5f) * S, rad = R * .72f;
        for (int sd = -1; sd <= 1; sd += 2) tube(ring(0, .14f * s, 0, rad + sd * .5f * s, 56), .045f * s, .045f * s, metal(.6f));   // the rails
        for (int k = 0; k < 72; k++) { float a = k * TAU / 72; push(); tr((float) Math.cos(a) * rad, .06f * s, (float) Math.sin(a) * rad); rY(-a); box(0, 0, 0, .68f * s, .05f * s, .1f * s, 0x5A4026, 255); pop(); }
        float v = 3.6f * S, th0 = t * v / rad, roll = t * v / (.45f * s);
        int body = mix(P2, 0x101828, .35f), trim = mix(P1, 0xFFFFFF, .1f);
        float[] gap = {0f, 3.3f, 5.8f, 9.4f}; int[] kind = {0, 1, 2, 2};
        for (int i = 0; i < 4; i++) {
            float th = th0 - gap[i] * s / rad; push(); tr((float) Math.cos(th) * rad, 0, (float) Math.sin(th) * rad); rY(-th - PI / 2);
            if (kind[i] == 0) trainLoco(s, roll, body, trim); else trainCar(s, roll, i == 1 ? 0x2A2A30 : mix(P1, 0x201020, .45f), trim, i == 1);
            pop();
        }
        float fx2 = (float) -Math.sin(th0), fz2 = (float) Math.cos(th0), cx = (float) Math.cos(th0) * rad + fx2 * 1.3f * s, cz = (float) Math.sin(th0) * rad + fz2 * 1.3f * s;
        for (int k = 0; k < 7; k++) { float p = fract(t * .7f + k / 7f); bb("smoke", cx - fx2 * p * 3f * s, 2.3f * s + p * 3.5f * s, cz - fz2 * p * 3f * s, (.5f + 1.3f * p) * s, (.5f + 1.3f * p) * s, t + k, rgba(0xC8C8D0, .55f * (1f - p))); }
        bb("glow", (float) Math.cos(th0) * rad + fx2 * 2.2f * s, 1.05f * s, (float) Math.sin(th0) * rad + fz2 * 2.2f * s, 1.6f * s, 1.6f * s, 0, rgba(0xFFF4C0, .6f));
        if (m) { float ta = -t * .1f, ar = R * .55f, ay = top * .74f + (float) Math.sin(t * .6f) * .5f * S; push(); tr((float) Math.cos(ta) * ar, ay, (float) Math.sin(ta) * ar); rY(PI / 2 - ta); rZ((float) Math.sin(t * .5f) * .04f); airship(.95f * S); pop(); }
        symFront(CY + 1.6f * S, 3.4f * S, 0f);
        for (int k = 0; k < 4; k++) flat("ring_dash", 0, (1 + k * 2.2f) * S, 0, R * 1.6f - k * 1.2f * S, t * (k % 2 == 0 ? .3f : -.4f), rgba(k % 2 == 0 ? P1 : P2, .8f));
        sprites("spark", 70 * N, SPIRALM, R, 0, top, .4f * S, P1, 1f, .2f, 32, true);
    }
    private void hangedMan(boolean m) {
        dome(R * 4f, 0x000000, m ? 252 : 246);
        mcStars(100, R * 3.2f, 0xFFFFFF, P1, .5f * S);
        flat("whirl", 0, .06f, 0, 2f * R, t * .3f, rgba(P2, .9f));
        sigil(R);
        float ey = top * 1.25f, er = 3.2f * S, bh = 4.2f * S, headY = 1.5f * S, feetY = headY + bh;
        bb("glow", 0, ey, 0, 11 * S, 11 * S, 0, rgba(P1, .6f));
        sphere(0, ey, 0, er, er, er, 12, 20, 0x000000, 255);
        for (int i = 0; i < 3; i++) { push(); tr(0, ey, 0); rY(i * PI / 3 + t * .1f); rX(i * .5f); torus(0, 0, 0, er * 1.5f, er * 1.5f, .1f * S, 28, 4, 0, TAU, mix(P1, 0xFFFFFF, .2f), 255); pop(); }
        int iron = mix(0x707078, P1, .15f);
        push(); tr(0, ey - er, 0); chain(ey - er - feetY, .8f * S, iron); pop();
        if (!m) bearerSym(0, feetY - bh * .5f, 0, bh * 1.5f, -face);
        if (m) {   // seq 0: the great bound figure from the reference: a black pillar of a body with arms stretched along a beam, wound in chains, gold and red bands, black tentacles at its feet, a hanging block above, and a red grid over the sky
            int blk = 0x0C0C12, gold = 0xC8A040, redc = 0x8A1020, rune = 0xFF3040;
            box(0, 3.2f * S, 0, .55f * S, 3.2f * S, .42f * S, blk, 255);
            for (int k = 0; k < 5; k++) box(0, 3.4f * S + k * .45f * S, 0, .6f * S, .13f * S, .46f * S, k % 2 == 0 ? gold : redc, 255);
            box(0, 5.2f * S, 0, 4.6f * S, .42f * S, .42f * S, 0x1A1A20, 255);
            for (int sd = -1; sd <= 1; sd += 2) { box(sd * 4.7f * S, 5.2f * S, 0, .6f * S, .56f * S, .56f * S, 0x2A2A30, 255);
                for (int k = 0; k < 6; k++) { push(); tr(sd * (1.0f * S + k * .6f * S), 5.2f * S, 0); rY(PI / 2); torus(0, 0, 0, .5f * S, .5f * S, .07f * S, 10, 4, 0, TAU, iron, 255); pop(); }
                for (int k = 0; k < 4; k++) bb("glow", sd * (1.6f * S + k * .9f * S), 5.45f * S, .0f, .5f * S, .5f * S, 0, rgba(rune, .7f)); }
            box(0, 6.6f * S, 0, .46f * S, .5f * S, .46f * S, 0x1A1A24, 255); box(0, 6.5f * S, .47f * S, .34f * S, .12f * S, .01f * S, 0xE8D0C0, 255); box(0, 7.3f * S, 0, .6f * S, .22f * S, .6f * S, 0x14141A, 255);
            box(0, top * .98f, 0, 1.2f * S, .8f * S, 1.2f * S, 0x2A2A30, 255); box(0, top * .98f + .1f * S, 1.21f * S, .8f * S, .3f * S, .01f * S, rune, 255);
            push(); tr(0, top * .98f - .8f * S, 0); chain(top * .98f - .8f * S - 7.6f * S, .8f * S, iron); pop();
            for (int i = 0; i < 4; i++) { float a = i * TAU / 4 + .5f; push(); tr((float) Math.cos(a) * 1.6f * S, 0, (float) Math.sin(a) * 1.6f * S); rY(-a); tentacle(4.2f * S, .5f * S, 1.6f, i, 0x050507); pop(); }
            for (int k = 1; k <= 4; k++) { float el = k * .27f; tube(ring(0, (float) Math.sin(el) * R * 3.5f, 0, (float) Math.cos(el) * R * 3.5f, 40), .06f * S, .06f * S, 0xC01830); }
            for (int mm = 0; mm < 12; mm++) { float a = mm * TAU / 12; float[][] pts = new float[12][]; for (int j = 0; j < 12; j++) { float el = j * 1.2f / 11f; pts[j] = v3((float) Math.cos(a) * (float) Math.cos(el) * R * 3.5f, (float) Math.sin(el) * R * 3.5f, (float) Math.sin(a) * (float) Math.cos(el) * R * 3.5f); } tube(pts, .06f * S, .06f * S, 0xC01830); }
        }
        for (int i = 0; i < 4; i++) { float a = i * TAU / 4 + .4f, r = R * .5f; push(); tr((float) Math.cos(a) * r, 0, (float) Math.sin(a) * r); rY(-a + PI / 2); torus(0, .5f * S, 0, .5f * S, .55f * S, .1f * S, 12, 4, -.3f, PI + .3f, iron, 255); pop();
            column("line", (float) Math.cos(a) * r, .5f * S, feetY - .55f * bh, (float) Math.sin(a) * r * .1f, .25f * S, rgba(P1, .35f)); }
        symFront(CY + .6f * S, 3.4f * S, 3.8f * S);
        sprites("drop", 70, FALLM, R * .9f, 0, top, .7f * S, 0xC01820, 1f, .45f, 34, false);
        for (int i = 0; i < 6; i++) { float pw = fract(t * .5f + i / 6f); flat("ring", (h(i, 61) - .5f) * R, .1f, (h(i, 62) - .5f) * R, pw * 3f * S, 0, rgba(P1, fd(pw))); }
    }
    /** Sefirah Castle as the books describe it: stone pillars round a vast dome where the grey fog gathers, an ancient mottled bronze long table
     *  with ten high backed chairs a side and one at each end (the Fool's at the head), every chair back glowing faint crimson with a constellation. */
    private float hx(float lx, float lz) { float q = thQ * PI / 2; return (float) Math.cos(q) * lx + (float) Math.sin(q) * lz; }
    private float hz(float lx, float lz) { float q = thQ * PI / 2; return -(float) Math.sin(q) * lx + (float) Math.cos(q) * lz; }
    private static final float HK = HoloCardEntity.HALL_K;   // how much bigger than a person the hall is built (the real seats and table use the same number)
    private float hxk(float lx, float lz) { return hx(lx * HK, lz * HK); }
    private float hzk(float lx, float lz) { return hz(lx * HK, lz * HK); }
    private static int light(int rgb, float k) { return mix(rgb, 0xFFFFFF, k); }
    /** A tall carved seat in the style of the picture: dark stone, a high back with a gabled crown and a pale teal edge, thick arms. Origin at the floor
     *  under the seat, facing +z, back at -z. */
    private void hallChair(int stone, int dark, int edge, boolean big) {
        float bh = big ? 3.0f : 1.9f, bw = big ? .6f : .44f;
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) { box(sx * .34f, .22f, sz * .34f, .045f, .22f, .045f, dark, 255); box(sx * .34f, .05f, sz * .34f, .06f, .05f, .06f, stone, 255); }
        box(0, .2f, .34f, .34f, .02f, .02f, dark, 255); box(0, .2f, -.34f, .34f, .02f, .02f, dark, 255);
        box(0, .46f, 0, .42f, .04f, .42f, stone, 255); box(0, .505f, .01f, .36f, .02f, .36f, mix(dark, 0x2A5058, .35f), 255);
        box(0, .47f, .425f, .43f, .012f, .006f, edge, 255);
        box(0, .5f + bh / 2, -.4f, bw, bh / 2, .05f, stone, 255);                            // the high back
        box(0, .5f + bh * .46f, -.35f, bw * .78f, bh * .38f, .018f, dark, 255);            // an inset panel
        for (int k = -2; k <= 2; k++) box(k * bw * .3f, .5f + bh * .46f, -.33f, .012f, bh * .36f, .012f, mix(stone, edge, .35f), 255);
        for (int sd = -1; sd <= 1; sd += 2) { box(sd * (bw + .03f), .5f + bh / 2, -.4f, .03f, bh / 2, .07f, dark, 255); box(sd * (bw + .03f), .5f + bh, -.4f, .036f, .02f, .076f, edge, 255);
            box(sd * .46f, .8f, -.02f, .045f, .045f, .4f, stone, 255); box(sd * .46f, .62f, .3f, .04f, .17f, .04f, dark, 255); box(sd * .46f, .85f, -.02f, .02f, .008f, .38f, edge, 255);   // arm with a teal rim
            push(); tr(sd * (bw + .03f), .5f + bh, -.4f); taper(.0f, big ? .5f : .3f, .045f, .075f, 0, 0, dark, 255); pop(); }
        push(); tr(0, .5f + bh, -.4f); taper(0, big ? .9f : .55f, bw, .055f, .03f, .02f, stone, 255); pop();        // the gabled crown
        box(0, .5f + bh + .015f, -.4f, bw * .96f, .015f, .06f, edge, 255);
        push(); tr(0, .5f + bh + (big ? 1.0f : .62f), -.4f); taper(0, .25f, .025f, .02f, 0, 0, edge, 255); pop();
        if (big) for (int sd = -1; sd <= 1; sd += 2) { push(); tr(sd * bw * .6f, .5f + bh, -.4f); taper(0, 1.4f, .08f, .06f, 0, 0, stone, 255); pop(); }
        bb("glow", 0, .5f + bh + .1f, -.35f, bw * 2.4f, .5f, 0, rgba(edge, .35f));
    }
    /** A fluted column like the pictures: plinth, ringed shaft with grooves, a carved collar and a flared capital with horned corners. Base at the origin. */
    private void column2(float H, float r, boolean detail, int stone, int dark, int edge) {
        box(0, .12f, 0, r * 1.7f, .12f, r * 1.7f, dark, 255); box(0, .3f, 0, r * 1.45f, .06f, r * 1.45f, stone, 255);
        frustum(.36f, H - 1.3f * r * 1.8f, r * 1.2f, r * .92f, 12, 1, 1, stone, 255, false);
        float top = H - 1.3f * r * 1.8f;
        for (int k = 1; k <= 3; k++) { float y = .36f + (top - .36f) * k / 4f; frustum(y, y + .1f * r * 2f, r * 1.12f, r * 1.12f, 12, 1, 1, dark, 255, true); }
        if (detail) for (int g = 0; g < 10; g++) { push(); rY(g * TAU / 10); box(0, (.36f + top) / 2, r * 1.04f, r * .08f, (top - .36f) / 2 - .05f, r * .03f, dark, 255); pop(); }
        frustum(top, top + .3f * r * 1.8f, r * .95f, r * 1.3f, 12, 1, 1, dark, 255, true);
        if (detail) for (int g = 0; g < 12; g++) { push(); rY(g * TAU / 12); tr(0, top + .15f * r * 1.8f, r * 1.2f); box(0, 0, 0, r * .12f, r * .2f, r * .04f, mix(edge, dark, .4f), 255); pop(); }
        frustum(top + .3f * r * 1.8f, H - .22f * r * 1.8f, r * 1.3f, r * 2.1f, 12, 1, 1, stone, 255, true);
        box(0, H - .11f * r * 1.8f, 0, r * 2.2f, .11f * r * 1.8f, r * 2.2f, dark, 255);
        for (int c = 0; c < 4; c++) { push(); tr((c & 1) == 0 ? -r * 2.1f : r * 2.1f, H, (c & 2) == 0 ? -r * 2.1f : r * 2.1f); taper(0, r * 1.2f, r * .35f, r * .35f, 0, 0, stone, 255); pop(); }
        if (detail) bb("glow", 0, H + r * .3f, 0, r * 3f, r * 3f, 0, rgba(0xFF3040, .5f));
    }
    /** A ribbed dome over the table: a dark stone shell with an open eye at the top, ribs and rings, standing on a ring of columns joined by pointed arches. */
    private void canopy(float Hb, float zc, float Rc, float Ry, int n, float cr, float tk, int stone, int dark, int edge) {
        float[] px = new float[n], pz = new float[n];
        for (int i = 0; i < n; i++) { float a = i * TAU / n + TAU / (2 * n); px[i] = Rc * (float) Math.cos(a); pz[i] = zc + Rc * (float) Math.sin(a); }
        for (int i = 0; i < n; i++) { push(); tr(px[i], 0, pz[i]); column2(Hb, cr, true, stone, dark, edge); pop(); }
        for (int i = 0; i < n; i++) {   // pointed arches between neighbouring columns
            int j = (i + 1) % n; float dx = px[j] - px[i], dz = pz[j] - pz[i], W = (float) Math.sqrt(dx * dx + dz * dz) - cr * 3.4f, y0 = Hb - cr * 3.2f;
            push(); tr((px[i] + px[j]) / 2, 0, (pz[i] + pz[j]) / 2); rY((float) Math.atan2(-dz, dx));
            torus(W / 2, y0, 0, W, W, .15f * tk, 12, 5, 2.094f, PI, stone, 255); torus(-W / 2, y0, 0, W, W, .15f * tk, 12, 5, 0, 1.047f, stone, 255);
            torus(W / 2, y0, .02f * tk, W - .12f * tk, W - .12f * tk, .035f * tk, 12, 4, 2.094f, PI, edge, 255); torus(-W / 2, y0, .02f * tk, W - .12f * tk, W - .12f * tk, .035f * tk, 12, 4, 0, 1.047f, edge, 255);
            box(0, Hb - cr * .95f, 0, W / 2 + cr * .5f, .1f * tk, cr * .3f, dark, 255); pop();
        }
        solid(true);   // the shell, left open at the top
        int lat = 8, lon = 32; float[] hint = v3(0, Hb - 5, zc); float top = PI / 2 * .9f;
        for (int a = 0; a < lat; a++) {
            float l0 = top * a / lat, l1 = top * (a + 1) / lat;
            for (int g = 0; g < lon; g++) { float g0 = TAU * g / lon, g1 = TAU * (g + 1) / lon;
                sq(sp(0, Hb, zc, Rc, Ry, Rc, l0, g0), sp(0, Hb, zc, Rc, Ry, Rc, l0, g1), sp(0, Hb, zc, Rc, Ry, Rc, l1, g1), sp(0, Hb, zc, Rc, Ry, Rc, l1, g0), hint, mix(dark, 0x0C1214, (a % 2) * .3f), 245); }
        }
        solid(false);
        for (int k = 0; k < n; k++) {   // ribs, running down to the columns
            float g = k * TAU / n + TAU / (2 * n); float[][] pts = new float[14][];
            for (int a = 0; a < 14; a++) { float l = top * a / 13f; pts[a] = v3(Rc * (float) Math.cos(l) * (float) Math.cos(g), Hb + Ry * (float) Math.sin(l) + .05f, zc + Rc * (float) Math.cos(l) * (float) Math.sin(g)); }
            tube(pts, .17f * tk, .1f * tk, stone);
        }
        for (int k = 1; k <= 4; k++) { float l = top * k / 5f; float[][] pts = new float[49][];
            for (int g = 0; g <= 48; g++) { float a = TAU * g / 48; pts[g] = v3(Rc * (float) Math.cos(l) * (float) Math.cos(a), Hb + Ry * (float) Math.sin(l) + .06f, zc + Rc * (float) Math.cos(l) * (float) Math.sin(a)); }
            tube(pts, .1f * tk, .1f * tk, k == 2 ? mix(stone, edge, .3f) : stone); }
        { float[][] pts = new float[49][];   // the lip of the eye
            for (int g = 0; g <= 48; g++) { float a = TAU * g / 48; pts[g] = v3(Rc * (float) Math.cos(top) * (float) Math.cos(a), Hb + Ry * (float) Math.sin(top), zc + Rc * (float) Math.cos(top) * (float) Math.sin(a)); }
            tube(pts, .2f * tk, .2f * tk, dark); }
    }
    private static float[][] ring(float cx, float y, float cz, float r, int n) {
        float[][] p = new float[n + 1][]; for (int i = 0; i <= n; i++) { float a = TAU * i / n; p[i] = v3(cx + r * (float) Math.cos(a), y, cz + r * (float) Math.sin(a)); } return p;
    }
    /** The hall as in the pictures, at HK times a person: a very long bronze table, tall carved chairs well apart down both sides, the Fool's seat at the head,
     *  a round dark floor under a great ribbed dome on a ring of columns, and far taller pillars standing off in the fog. Local x is right and z points to whoever
     *  placed it. Every seat has the symbol of whoever is sitting in it, to the left of the chair. */
    private void foolHall() {
        int stone = 0x232F33, dark = 0x131B1E, edge = 0x78D0D4;
        int bronze = 0x7A6038, bronzeD = 0x3A2E1C, patina = 0x3E7262;
        float zc = HoloCardEntity.HALL_ZC, hl = HoloCardEntity.HALL_HL, W = HoloCardEntity.HALL_W, L = zc - hl, cs = HoloCardEntity.HALL_CHAIR;
        float Rc = 20f, Hb = 24f, Ry = 18f;
        pushThrone(); cur.scale(HK, HK, HK);
        push(); tr(0, 0, zc); frustum(-.12f, 0f, Rc + 1.2f, Rc + 1.2f, 56, 1, 1, mix(dark, 0x000000, .3f), 255, true); pop();   // the round dark floor
        tube(ring(0, .04f, zc, Rc - .4f, 40), .09f, .09f, edge); tube(ring(0, .035f, zc, Rc - 2.2f, 40), .05f, .05f, mix(edge, dark, .5f)); tube(ring(0, .03f, zc, Rc * .55f, 32), .05f, .05f, mix(edge, dark, .55f));
        for (int k = 0; k < 12; k++) { float a = k * TAU / 12; float[][] ln = {v3((float) Math.cos(a) * 4f, .03f, zc + (float) Math.sin(a) * 4f), v3((float) Math.cos(a) * (Rc - .4f), .03f, zc + (float) Math.sin(a) * (Rc - .4f))}; tube(ln, .03f, .03f, mix(edge, dark, .6f)); }
        canopy(Hb, zc, Rc, Ry, 12, .95f, 2f, stone, dark, edge);
        for (int i = 0; i < 22; i++) {   // columns standing off in the fog, all round, far taller than the dome
            float a = h(i, 1) * TAU, rho = 24f + 30f * h(i, 2), H = i % 7 == 0 ? 14f + 10f * h(i, 3) : 36f + 44f * h(i, 4), r = .9f + .9f * h(i, 5);
            float x = rho * (float) Math.cos(a), z = zc + rho * (float) Math.sin(a);
            if (z > 6f && Math.abs(x) < 10f) continue;   // keep the way to the door clear
            push(); tr(x, 0, z); column2(H, r, false, stone, dark, edge); pop();
        }
        // the table: aged bronze, mottled green
        box(0, .74f, zc, W, .06f, hl, bronze, 255);
        box(0, .806f, zc, W - .1f, .01f, hl - .15f, mix(bronze, patina, .3f), 255);
        for (int k = 0; k < 22; k++) box((h(k, 61) - .5f) * 2f * (W - .25f), .818f, zc + (h(k, 62) - .5f) * 2f * (hl - .4f), .1f + .3f * h(k, 63), .004f, .2f + .8f * h(k, 64), mix(patina, bronze, h(k, 65) * .7f), 255);
        for (int sd = -1; sd <= 1; sd += 2) { box(sd * (W - .02f), .79f, zc, .035f, .03f, hl + .02f, mix(bronze, 0xE8C070, .3f), 255); box(sd * (W - .12f), .82f, zc, .008f, .004f, hl - .3f, mix(bronze, 0xE8C070, .45f), 255); }
        box(0, .5f, zc, W - .1f, .19f, hl - .06f, bronzeD, 255);
        for (int k = 0; k < 26; k++) { float z = zc + hl - .3f - k * (2 * hl - .6f) / 25f; for (int sd = -1; sd <= 1; sd += 2) box(sd * (W - .08f), .5f, z, .022f, .17f, .06f, mix(bronze, bronzeD, .4f), 255); }
        box(0, .2f, zc, W + .05f, .1f, hl + .1f, bronzeD, 255); box(0, .07f, zc, W + .16f, .07f, hl + .22f, stone, 255);
        for (int en = -1; en <= 1; en += 2) { float ez0 = en > 0 ? zc + hl : zc - hl; box(0, .76f, ez0 + en * .05f, W * .9f, .08f, .08f, mix(bronze, 0xE8C070, .25f), 255); }
        for (int sd = -1; sd <= 1; sd += 2) for (int k = 0; k < 10; k++) { push(); tr(sd * HoloCardEntity.HALL_SX, 0, HoloCardEntity.HALL_Z0 - k * HoloCardEntity.HALL_PITCH); rY(sd < 0 ? PI / 2 : -PI / 2); cur.scale(cs, cs, cs); hallChair(stone, dark, edge, false); pop(); }
        push(); tr(0, 0, HoloCardEntity.HALL_HEAD); rY(PI); cur.scale(cs * 1.1f, cs * 1.1f, cs * 1.1f); hallChair(stone, dark, edge, true); pop();       // the Fool's seat, at the head
        push(); tr(0, 0, L - 1.3f); cur.scale(cs, cs, cs); hallChair(stone, dark, edge, false); pop();           // the foot, clear of the table
        pop();
        // the eye at the top of the dome: a bright white-teal rosette inside a blue ring of ticks and arrowheads, seen from below
        float es = 2.9f, oy = (Hb + Ry * .985f) * HK, ox = hxk(0, zc), oz = hzk(0, zc);
        flat("glow", ox, oy, oz, 9f * es * HK, 0, rgba(0x3F7CFF, .5f));
        flat("disc", ox, oy + .02f, oz, 2.4f * es * HK, 0, rgba(0xD8FFFF, 1f)); flat("ring", ox, oy + .03f, oz, 2.0f * es * HK, 0, rgba(0x40B8C8, .9f)); flat("ring_thick", ox, oy + .03f, oz, 1.4f * es * HK, 0, rgba(0x80E0E8, .9f));
        flat("web", ox, oy + .04f, oz, 2.3f * es * HK, PI / 8, rgba(0x60C8D0, .8f)); flat("star", ox, oy + .05f, oz, 1.2f * es * HK, 0, rgba(0xFFFFFF, .9f));
        flat("ring_thick", ox, oy + .01f, oz, 4.2f * es * HK, 0, rgba(0x2F6BFF, 1f)); flat("ring_dash", ox, oy + .015f, oz, 4.8f * es * HK, 0, rgba(0x6A9CFF, .95f)); flat("ring_dash", ox, oy + .012f, oz, 3.6f * es * HK, .2f, rgba(0x4F86FF, .9f));
        for (int k = 0; k < 8; k++) { float an = k * TAU / 8 + PI / 8; flat("diamond", ox + (float) Math.cos(an) * 2.05f * es * HK, oy + .02f, oz + (float) Math.sin(an) * 2.05f * es * HK, .5f * es * HK, an, rgba(0x4F86FF, 1f)); }
        // the symbol of whoever is sitting in each chair, from the real seats (their pathway is synced from the server), to the left of the chair; an empty chair is just a faint glow
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(ex - 120, ey - 3, ez - 120, ex + 120, ey + 12, ez + 120);
        var seats = net.minecraft.client.Minecraft.getInstance().level == null ? new java.util.ArrayList<ThronePart>()
            : net.minecraft.client.Minecraft.getInstance().level.getEntitiesOfClass(ThronePart.class, area, ThronePart::isSeat);
        float q = thQ * PI / 2;
        for (int seat = 0; seat < 21; seat++) {
            float sx, sz, fxl, fzl, yaw;   // the chair's place and which way the sitter faces, in hall units
            if (seat < 20) { int sd = seat < 10 ? -1 : 1, k = seat % 10; sx = sd * HoloCardEntity.HALL_SX; sz = HoloCardEntity.HALL_Z0 - k * HoloCardEntity.HALL_PITCH; fxl = -sd; fzl = 0; yaw = PI / 2 - q; }
            else { sx = 0; sz = L - 1.3f; fxl = 0; fzl = 1; yaw = -q; }
            float lx = sx + fzl * .95f, lz = sz - fxl * .95f, y = 1.9f;   // left of the sitter is up x facing = (fz, -fx)
            int who = -1; double best = 2.6;
            double cx = ex + hxk(sx, sz), cz = ez + hzk(sx, sz);
            for (ThronePart p : seats) { double d = Math.abs(p.getX() - cx) + Math.abs(p.getZ() - cz); if (d < best) { best = d; who = p.occupant(); } }
            float gx = hxk(lx, lz), gz = hzk(lx, lz);
            bb("glow", gx, y * HK, gz, 2.2f * HK, 2.2f * HK, 0, rgba(0x78D0D4, who >= 0 ? .4f : .1f));
            if (who >= 0) symPlane(who, gx, y * HK, gz, 1.1f * HK, yaw, 1f);
        }
        float hz0 = HoloCardEntity.HALL_HEAD + 1.4f;   // over the head seat: the Fool's symbol, and in Lord of Mysteries all three
        int[] top3 = lomHall ? new int[]{pidx("apprentice"), 18, pidx("marauder")} : new int[]{18};
        for (int k = 0; k < top3.length; k++) {
            float lx = (k - (top3.length - 1) / 2f) * 2.7f;
            symPlane(top3[k], hxk(lx, hz0), 5.6f * HK, hzk(lx, hz0), 2.2f * HK, -q, 1f);
            bb("glow", hxk(lx, hz0), 5.6f * HK, hzk(lx, hz0), 4.8f * HK, 4.8f * HK, 0, rgba(0x78D0D4, .4f));
        }
    }
    private boolean lomHall;
    /** A great gothic door of white light at the top of a long marble stair, behind the head seat (hall frame: local z toward the head of the table): dark carved
     *  columns and nested pointed arches, an opening filled with huge white whorls of light, and black tentacles curling round the frame and down the steps. */
    private void lightDoor() {
        int stone = 0x1B2326, dark = 0x0E1315, marble = 0x8C9298, marbleD = 0x6A7076, white = 0xFFFFFF, black = 0x040607;
        float z0 = 9.5f, steps = 14, rise = .42f, run = .9f, sw = 5.8f, top0 = steps * rise, zd = z0 + steps * run + 1.6f, dw = 3.4f, jh = 7f, Wo = dw * 2, apex = Wo * .866f;
        pushThrone(); cur.scale(HK, HK, HK);
        for (int i = 0; i < steps; i++) { float y = (i + 1) * rise, z = z0 + i * run + run / 2;
            box(0, y / 2, z, sw, y / 2, run / 2, i % 2 == 0 ? marble : marbleD, 255);
            box(0, y + .004f, z - run / 2 + .02f, sw, .006f, .02f, white, 255);
            for (int sd = -1; sd <= 1; sd += 2) { box(sd * (sw + .45f), y / 2 + .5f, z, .45f, y / 2 + .5f, run / 2, dark, 255); box(sd * (sw + .45f), y + 1.02f, z, .5f, .03f, run / 2, mix(white, 0x78D0D4, .5f), 255); } }
        box(0, top0 / 2, zd, sw + .9f, top0 / 2, 1.7f, marble, 255); box(0, top0 + .01f, zd - 1.68f, sw + .9f, .01f, .02f, white, 255);
        for (int sd = -1; sd <= 1; sd += 2) {   // carved columns each side, with buttresses and panels
            float x0 = sd * (dw + 1.0f);
            push(); tr(x0, top0, zd); column2(jh + 2.4f, .6f, true, stone, dark, edge2()); pop();
            box(sd * (dw + 2.1f), top0 + (jh + 5f) / 2, zd, .55f, (jh + 5f) / 2, .8f, stone, 255);
            box(sd * (dw + 2.1f), top0 + (jh + 5f) / 2, zd - .82f, .38f, (jh + 4f) / 2, .02f, dark, 255);
            for (int k = 0; k < 6; k++) box(sd * (dw + 2.1f), top0 + 1f + k * 1.5f, zd - .85f, .3f, .05f, .02f, mix(stone, 0x78D0D4, .3f), 255);
            push(); tr(sd * (dw + 2.1f), top0 + jh + 5f, zd); taper(0, 2.4f, .55f, .8f, 0, 0, stone, 255); pop(); }
        for (int l = 0; l < 3; l++) {   // nested pointed arches
            float Wl = Wo + 2.4f - l * .7f, th = .42f - l * .09f; int col = l == 0 ? stone : l == 1 ? dark : mix(stone, white, .35f);
            push(); tr(0, top0 + jh, zd - l * .06f); torus(Wl / 2, 0, 0, Wl, Wl, th, 22, 6, 2.094f, PI, col, 255); torus(-Wl / 2, 0, 0, Wl, Wl, th, 22, 6, 0, 1.047f, col, 255); pop();
        }
        push(); tr(0, top0 + jh + apex + .6f, zd); taper(0, 2.6f, .5f, .5f, 0, 0, stone, 255); pop(); box(0, top0 + jh + apex + .4f, zd, .45f, .45f, .45f, dark, 255);
        // tentacles curling round the frame and onto the steps
        for (int k = 0; k < 10; k++) {
            int sd = k % 2 == 0 ? -1 : 1; float ph = h(k, 41) * TAU + (float) Math.sin(t * .5f + k) * .35f, rr = .3f + .12f * h(k, 42), H0 = jh + 3f + 3f * h(k, 43);
            float[][] pts = new float[28][];
            for (int i = 0; i < 28; i++) { float u = i / 27f; pts[i] = v3(sd * (dw + 1.6f + .9f * (float) Math.sin(u * 9f + ph) + 1.2f * u * (k < 4 ? -1f : 0f)), top0 + .2f + u * H0, zd - .9f + .8f * (float) Math.cos(u * 9f + ph)); }
            tube(pts, rr, .05f, black);
        }
        for (int k = 0; k < 7; k++) {
            float x0 = (h(k, 44) - .5f) * 5f, ph = h(k, 45) * TAU + (float) Math.sin(t * .6f + k * 2f) * .4f; float[][] pts = new float[22][];
            for (int i = 0; i < 22; i++) { float u = i / 21f, z = zd - 1.2f - u * 7f; pts[i] = v3(x0 + 1.4f * (float) Math.sin(u * 6f + ph) * u, Math.max(.05f, top0 - (zd - 1.2f - z) * rise / run) + .25f + .15f * (float) Math.sin(u * 8f + ph), z); }
            tube(pts, .26f, .05f, black);
        }
        pop();
        // the light itself: a pointed-arch fill built from strips, then big white whorls over it
        float cx = hxk(0, zd + .15f), cz = hzk(0, zd + .15f), HT = jh + apex; int ns = 30;
        for (int i = 0; i < ns; i++) {
            float yc = (i + .5f) / ns * HT, hw = yc < jh ? dw : (float) Math.sqrt(Math.max(0f, Wo * Wo - (yc - jh) * (yc - jh))) - dw;
            if (hw < .05f) continue;
            int c = mix(0xB4C6D8, white, Math.max(0f, 1f - Math.abs(yc - HT * .45f) / (HT * .55f)));
            eq(tex("solid"), cx, (top0 + yc) * HK, cz, hx(hw * HK, 0), 0, hz(hw * HK, 0), 0, HT / ns / 2f * HK * 1.06f, 0, rgba(c, .92f), 0, 0, 1, 1);
            eq(tex("solid"), cx, (top0 + yc) * HK, cz, -hx(hw * HK, 0), 0, -hz(hw * HK, 0), 0, HT / ns / 2f * HK * 1.06f, 0, rgba(c, .92f), 0, 0, 1, 1);
        }
        String[] wt = {"whirl", "spiral", "whirl", "spiral"}; float[] wy = {jh * .55f, jh * .95f, jh + apex * .25f, jh * .2f}, wr = {dw * .98f, dw * .85f, dw * .62f, dw * .7f};
        for (int k = 0; k < 4; k++) {
            float th = t * (k % 2 == 0 ? .25f : -.35f) + k * 1.3f, c = (float) Math.cos(th), s = (float) Math.sin(th), r = wr[k] * HK, yy = (top0 + wy[k]) * HK;
            float ax = hx(r, 0) * c, ay = r * s, az = hz(r, 0) * c, bx = -hx(r, 0) * s, by2 = r * c, bz = -hz(r, 0) * s;
            for (int sdd = -1; sdd <= 1; sdd += 2) eq(tex(wt[k]), cx, yy, cz, sdd * ax, sdd * ay, sdd * az, bx, by2, bz, rgba(white, .9f), 0, 0, 1, 1);
        }
        float by = (top0 + HT * .5f) * HK;
        bb("glow", cx, by, cz, Wo * 2.6f * HK, HT * 1.5f * HK, 0, rgba(white, .5f)); bb("lotm:sunburst", cx, by, cz, HT * 1.6f * HK, HT * 1.6f * HK, 0, rgba(white, .35f));
        column("beam", cx, top0 * HK, top0 * HK + 160f, cz, 3f * HK, rgba(white, .28f));
        LotmFx.hold(curId, "astral_world:door_light", cx, by, cz, HT * .55f * HK);
        for (int k = 0; k < 5; k++) { float p = fract(t * .25f + k / 5f), sx2 = (h(k, 31) - .5f) * Wo * .9f; bb("lotm:ember_hd", hxk(sx2, zd - .8f - p * 6f), (top0 + .6f + p * 5f) * HK, hzk(sx2, zd - .8f - p * 6f), .9f * HK, .9f * HK, 0, rgba(white, fd(p))); }
    }
    private static int edge2() { return 0x78D0D4; }
    private void foolTable() {   // Fool 1: a round bronze table with eight tall carved chairs and fluted columns, in the hall's colours
        int stone = 0x2E3C40, dark = 0x182226, edge = 0x78D0D4, bronze = 0x7A6038, bronzeD = 0x3A2E1C, patina = 0x3E7262;
        float tr0 = R * .34f, k = S * .55f;
        frustum(0, .7f * k, tr0 * .8f, tr0, 24, 1, 1, bronzeD, 255, true); frustum(.7f * k, .82f * k, tr0 + .1f * k, tr0 + .1f * k, 24, 1, 1, bronze, 255, true); frustum(.82f * k, .84f * k, tr0 * .92f, tr0 * .92f, 24, 1, 1, mix(bronze, patina, .35f), 255, true);
        tube(ring(0, .86f * k, 0, tr0 * 1.02f, 28), .04f * k, .04f * k, mix(bronze, 0xE8C070, .4f));
        for (int i = 0; i < 8; i++) { float a = i * TAU / 8 + PI / 8, rc = tr0 + 1.7f * k; push(); tr((float) Math.cos(a) * rc, 0, (float) Math.sin(a) * rc); rY((float) Math.atan2(-Math.cos(a), -Math.sin(a))); cur.scale(k, k, k); hallChair(stone, dark, edge, i == 0); pop(); }
        for (int i = 0; i < 6; i++) { float a = i * TAU / 6 + .3f; push(); tr((float) Math.cos(a) * R * .95f, 0, (float) Math.sin(a) * R * .95f); column2(top * 1.5f, .24f * S, false, stone, dark, edge); pop(); }
    }
    private void fool(int N, boolean m) {
        if (!m) { R = 9f; top = 13f; S = 1.7f; CY = 4.6f + (float) Math.sin(t * 1.2) * .2f; }   // seq 1 is bigger and fuller than before
        if (!m) sigil(R);
        if (!m) foolTable();
        float SKY = 190f;
        int fogc = 0x2C4650;
        boolean photonFog = m ? LotmFx.hold(curId, "astral_world:fool_fog", ex, ey, ez, SKY * .62f) & LotmFx.hold(curId, "astral_world:fool_hall_fog", ex + hxk(0, HoloCardEntity.HALL_ZC), ey, ez + hzk(0, HoloCardEntity.HALL_ZC), 44f)
                : LotmFx.hold(curId, "astral_world:seer_fog", ex, ey, ez, R * .95f);   // our own Photon fog: one filling the whole sphere, one thick around the hall
        if (m) { dome(SKY, 0x2C3A6C, 232);
            int[] nc = {0x3A6CFF, 0xC040C8, 0x30D0E8, 0x6A50FF};   // far nebula clouds, so the sky is bright even where the particles are thin
            for (int i = 0; i < 12; i++) { float a = i * TAU / 12 + h(i, 8), el = .12f + .5f * h(i, 9), d = SKY * .8f; bb("cloud", (float) Math.cos(a) * d * (float) Math.cos(el), (float) Math.sin(el) * d, (float) Math.sin(a) * d * (float) Math.cos(el), SKY * (.4f + .35f * h(i, 10)), SKY * (.2f + .2f * h(i, 11)), 0, rgba(nc[i % 4], .55f)); }
            for (int i = 0; i < 6; i++) { float a = t * .01f + i * TAU / 6, d = SKY * .5f; bb("cloud", (float) Math.cos(a) * d, SKY * (.3f + .3f * h(i, 12)), (float) Math.sin(a) * d, SKY * .5f, SKY * .25f, 0, rgba(mix(nc[i % 4], 0xFFFFFF, .25f), .3f)); } }
        int fn = m ? 2 : 1;
        if (!photonFog) for (int i = 0; i < 26 * fn; i++) { float a = t * .05f + i * TAU / (26 * fn), rr = R * (.15f + .95f * h(i, 6)); ebb("fog", (float) Math.cos(a) * rr, (.2f + 1.0f * h(i, 7)) * S, (float) Math.sin(a) * rr, 9 * S, 2.6f * S, h(i, 9) * 3f, rgba(fogc, .55f)); }
        for (int i = 0; i < 14 * fn; i++) { float a = t * (.06f + .05f * h(i, 11)) + h(i, 12) * TAU, rr = R * (.2f + .7f * h(i, 13)), y = (.6f + 4f * h(i, 14)) * S + (float) Math.sin(t * .8f + i) * .4f * S, tw = .6f + .4f * (float) Math.sin(t * 1.7f + i * 2f);
            bb("glow", (float) Math.cos(a) * rr, y, (float) Math.sin(a) * rr, .8f * S, .8f * S, 0, rgba(hsv(h(i, 15), .6f), tw * .8f));
            bb("mote", (float) Math.cos(a) * rr, y, (float) Math.sin(a) * rr, .2f * S, .2f * S, 0, rgba(0xFFFFFF, tw)); }
        for (int i = 0; i < 70 * fn; i++) {   // many faint, far-off lights out in the dark
            float a = h(i, 21) * TAU + t * .01f, rr = R * (1.1f + 1.8f * h(i, 22)), y = (.5f + 9f * h(i, 23)) * S, tw = .35f + .35f * (float) Math.sin(t * (.6f + h(i, 24)) + i * 2f);
            bb("glow", (float) Math.cos(a) * rr, y, (float) Math.sin(a) * rr, .6f * S, .6f * S, 0, rgba(hsv(h(i, 25), .5f), tw * .5f)); bb("mote", (float) Math.cos(a) * rr, y, (float) Math.sin(a) * rr, .12f * S, .12f * S, 0, rgba(0xFFFFFF, tw * .7f)); }
        if (m) { mcStars(320, SKY * .95f, 0xFFFFFF, 0xA8D8FF, .6f * S); foolHall(); }
        float ey = CY + (m ? 2.4f : .4f) * S, yw = -face + (float) Math.sin(t * .35f) * .5f;
        if (!m) { float w = 6.4f * S; stand("eye", 0, ey, 0, w, w * .52f, yw, rgba(mix(P1, 0xFFFFFF, .3f), .85f)); stand("ring_thick", 0, ey, 0, w * .34f, w * .34f, yw + .02f, rgba(P3, .7f)); }
        if (!m) sym(ey + 4.4f * S, 3.2f * S, 0);
        if (!m) for (int i = 0; i < 4; i++) column("beam", (i - 1.5f) * 1.2f * S, 0, ey - 1.2f * S, 0, 1.2f * S, rgba(P1, .22f));
        sprites("mote", 50 * N, SPIRALM, R, 0, top, .35f * S, P3, .8f, .18f, 37, false);
    }
    private void darkness(int N, boolean m) {
        dome(R * 4f, 0x000002, m ? 253 : 250);
        sigil(R);
        mcStars(90 * N, R * 3.2f, 0xFFFFFF, P1, .6f * S); dotStars(220 * N, R * 3.0f, 0xFFFFFF, 0xB0C8FF, .7f * S);   // many more stars
        facing("crescent", sideX(R * 1.2f), top * .9f, sideZ(R * 1.2f), 9 * S, 9 * S, -.4f, rgba(mix(P1, 0xFFFFFF, .5f), .85f));
        sym(CY, 3.6f * S, 0);
        clouds(12 * N, R * 2.8f, top * .35f, P2, .3f, R * 1.8f);
        if (!LotmFx.hold(curId, "astral_world:sleepless_fog", ex, ey + top * .55f, ez, R * 2.2f)) for (int k = 0; k < 3; k++) fogRing(14, R * (.8f + 1.1f * k), P2, .35f);
        sprites("mote", 50 * N, DRIFTM, R * 1.2f, .5f, top, .45f * S, P1, .8f, .2f, 35, false);
    }
    /** Spectator (Visionary): a mind palace. A few vision frames showing other pathways, thought beads, brain waves. */
    private void quillModel(float L, int col) {   // the tip at the origin, the shaft up and a feather vane
        frustum(0, L, .03f * L, .012f * L, 6, 1, 1, 0xF0E8D8, 255, true);
        push(); tr(0, .3f * L, 0); taper(0, .7f * L, .13f * L, .012f * L, .02f * L, .01f * L, col, 255); pop();
        push(); tr(0, .3f * L, 0); rY(PI); taper(0, .7f * L, .13f * L, .012f * L, .02f * L, .01f * L, mix(col, 0xFFFFFF, .3f), 255); pop();
    }
    /** A card with lines of ink appearing on it (prog 0 to 1), a coloured seal, both faces. */
    private void quillCard(float x, float y, float z, float w, float hh, float yaw, float prog, float alpha, int seed) {
        float c = (float) Math.cos(yaw), s2 = (float) Math.sin(yaw);
        for (int sd = -1; sd <= 1; sd += 2) {
            float ox = -s2 * .012f * w * sd, oz = c * .012f * w * sd;
            eq(tex("solid"), x, y, z, sd * c * w / 2, 0, sd * s2 * w / 2, 0, hh / 2, 0, rgba(0xF4ECD0, alpha), 0, 0, 1, 1);
            eq(tex("solid"), x + ox, y, z + oz, sd * c * w * .46f, 0, sd * s2 * w * .46f, 0, hh * .47f, 0, rgba(0xC8B890, alpha), 0, 0, 1, 1);
            eq(tex("solid"), x + ox * 2f, y, z + oz * 2f, sd * c * w * .43f, 0, sd * s2 * w * .43f, 0, hh * .44f, 0, rgba(0xF4ECD0, alpha), 0, 0, 1, 1);
            for (int k = 0; k < 6; k++) {
                float len = w * .7f * Math.max(0f, Math.min(1f, prog * 6f - k)) * (.75f + .25f * h(seed * 7 + k, 211)), lx = -.35f * w + len / 2, ly = hh * (.3f - k * .1f);
                if (len < .01f) continue;
                eq(tex("solid"), x + c * lx + ox * 3f, y + ly, z + s2 * lx + oz * 3f, sd * c * len / 2, 0, sd * s2 * len / 2, 0, hh * .016f, 0, rgba(0x302040, alpha * .9f), 0, 0, 1, 1);
            }
            eq(tex("solid"), x + ox * 3f, y - hh * .36f, z + oz * 3f, sd * c * w * .07f, 0, sd * s2 * w * .07f, 0, w * .07f, 0, rgba(hsv(h(seed, 212), .8f), alpha * prog), 0, 0, 1, 1);
        }
    }
    private void visionary(int N) {
        sigil(R);
        boolean m = N == 2;
        flat("whirl", 0, .06f, 0, 2f * R, t * .4f, rgba(P2, .9f));
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
        if (m) {   // a quill writing on a card; each finished card flies off and floats round the area, a few at a time
            float Tc = 4.5f, cw = 2.3f * S, chh = 3.3f * S; int cn = (int) Math.floor(t / Tc); float prog = Math.min(1f, fract(t / Tc) * 1.1f);
            float wx = gx(-R * .34f, -R * .22f), wz = gz(-R * .34f, -R * .22f), wy = CY + 1.2f * S;
            quillCard(wx, wy, wz, cw, chh, -face, prog, 1f, cn);
            float lp = prog * 6f; int ln = Math.min(5, (int) lp); float lf = lp - ln, qx = -.35f * cw + .7f * cw * lf, qy = chh * (.3f - ln * .1f) + (float) Math.sin(t * 9f) * .05f * S;
            if (prog < 1f) { push(); tr(wx + sx0 * qx, wy + qy, wz + sz0 * qx); rY(face); rZ(-.9f); quillModel(2.4f * S, P3); pop(); }
            for (int j = Math.max(0, cn - 8); j < cn; j++) {
                float age = t - (j + 1) * Tc, fly = Math.min(1f, age / 1.4f), e = fly * fly * (3f - 2f * fly), fade = Math.min(1f, (8f * Tc - age) / Tc);
                if (fade <= 0f) continue;
                float oa = h(j, 201) * TAU + t * .04f, orr = R * (.45f + .4f * h(j, 202)), oy = (2.5f + 8f * h(j, 203)) * S + (float) Math.sin(t * .7f + j) * .4f * S;
                float x = wx + ((float) Math.cos(oa) * orr - wx) * e, y = wy + (oy - wy) * e, z = wz + ((float) Math.sin(oa) * orr - wz) * e, yw = -face + ((-oa + PI / 2) - (-face)) * e;
                quillCard(x, y, z, cw * .8f, chh * .8f, yw, 1f, fade, j);
            }
        }
        for (int ribbon = 0; ribbon < 3; ribbon++) for (int k = 0; k < 40; k++) {
            float u = k / 40f, a = t * (.35f + ribbon * .1f) + u * TAU * 1.5f + ribbon * 2.1f, r = R * (.35f + .2f * (float) Math.sin(u * 9 + t));
            bb("mote", (float) Math.cos(a) * r, CY - 1.4f * S + (float) Math.sin(u * TAU * 2 + t * 1.5f + ribbon) * 1.6f * S, (float) Math.sin(a) * r, .4f * S, .4f * S, 0, rgba(ribbon == 1 ? P3 : P1, 1f));
        }
        for (int i = 0; i < 16 * N; i++) { float p = fract(t * .2f + h(i, 3)), a = h(i, 2) * TAU, r = R * .3f * (1 + h(i, 4));
            bb("ring", (float) Math.cos(a) * r, p * top * .9f, (float) Math.sin(a) * r, (.4f + .8f * p) * S, (.4f + .8f * p) * S, 0, rgba(P3, fd(p))); }
        for (int i = 0; i < 4; i++) { float pw = fract(t * .3f + i / 4f); flat("ring", 0, .12f, 0, pw * 2.2f * R, 0, rgba(P3, fd(pw))); }
        sprites("mote", 70 * N, SPIRALM, R, 0, top, .35f * S, P3, 1f, .18f, 37, false);
    }
    /** Three swords like the Warrior icon: the big one upright with its point at the ground and its pommel up, and two behind it crossing in an X, pommels
     *  out and high, points meeting low near the middle. sword() has its pommel at the origin and its point along +y, so each is turned over. */
    private void plantedSwords(float L, float back, int blade, int guard, int gem) { plantedSwords(L, back, blade, guard, gem, false, .7f); }
    private void swordAt(boolean geo, float L, int blade, int guard, int gem) { if (!(geo && drawGeo("sword_of_twilight", "lotm", "textures/item/sword_of_twilight.png", L))) sword(L, blade, guard, gem); }
    private void plantedSwords(float L, float back, int blade, int guard, int gem, boolean geo, float th) {
        float La = .85f * L, tipX = .2f * L * (th / .7f);
        for (int sd = -1; sd <= 1; sd += 2) {
            push(); rY(face); tr(-sd * tipX + sd * (float) Math.sin(th) * La, .08f + (float) Math.cos(th) * La, -back); rZ(PI - sd * th);   // turn to face first, so the offsets are in that frame too
            swordAt(geo, La, blade, guard, gem); pop();
        }
        push(); tr(0, .08f + L, 0); rY(face); rZ(PI); swordAt(geo, L, blade, guard, gem); pop();
    }
    private void twilight(int N) {
        sigil(R); boolean m = N == 2;
        dome(R * 4f, 0x2A1240, 225);   // the whole area is a twilight: dusk sky, a low sun at the rim, glowing horizon
        for (int i = 0; i < 14; i++) { float a = i * TAU / 14; bb("glow", (float) Math.cos(a) * R * 3.3f, 1.2f * S, (float) Math.sin(a) * R * 3.3f, 16f * S, 7f * S, 0, rgba(i % 2 == 0 ? 0xFF7A28 : 0xE0408A, .5f)); }
        bb("glow", gx(0, -R * 3.1f), 3f * S, gz(0, -R * 3.1f), 24f * S, 24f * S, 0, rgba(0xFF6A20, .5f)); bb("disc", gx(0, -R * 3.1f), 3f * S, gz(0, -R * 3.1f), 9f * S, 9f * S, 0, rgba(0xFF8A30, .95f));
        mcStars(40, R * 3.2f, 0xFFE0C0, 0xFFA0C0, .5f * S);
        facing("rays", 0, 1f * S, 0, R * 2.6f, R * 2.6f, 0, rgba(P1, .3f));
        float L = (m ? 8.5f : 6.8f) * S;
        plantedSwords(L, .15f * S, mix(P1, 0xFFFFFF, .45f), P2, P3, true, .36f);   // the Sword of Twilight, the side two close in behind the upright one
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
