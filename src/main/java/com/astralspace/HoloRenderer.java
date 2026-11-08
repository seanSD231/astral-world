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

import java.util.HashMap;
import java.util.Map;

/**
 * Client only: draws a placed tarot card and its hologram. Everything is hand-built glowing quads (additive), no vanilla particles.
 * Seq 9-6: just the card. Seq 5-4 Rare: small spinning symbol. Seq 3-2 Epic: beam, rune ring, orbiting props.
 * Seq 1 Legendary and Seq 0 Mythic: a scene built from the pathway's own recipe (symbol, props, ambient motion, extras).
 * Shiny cards use a gold palette.
 */
public class HoloRenderer extends EntityRenderer<HoloCardEntity> {
    private static final int LIGHT = 15728880;
    private static final Map<String, ResourceLocation> TEX = new HashMap<>();
    private static final float TAU = (float) (Math.PI * 2);

    // extras
    private static final int WINGS = 1, CHAINS = 2, LIGHTNING = 4, RAYS = 8, CROWN = 16, CLOUDS = 32;
    // ambient motions
    private static final int FALL = 0, RISE = 1, ORBIT = 2, SPIRAL = 3, RADIAL = 4, DRIFT = 5;

    private record Cfg(String core, String prop, String amb, int c1, int c2, int motion, int extras) {}

    /** One recipe per pathway, in Tarot.PATHWAYS order. */
    private static final Cfg[] CFG = {
        new Cfg("crescent", "leaf", "mote", 0xB8E8FF, 0x6090FF, SPIRAL, RAYS | CLOUDS),            // apothecary  (Moon)
        new Cfg("door", "key", "spark", 0xFFC860, 0xFFF0C0, ORBIT, RAYS),                          // apprentice  (Door)
        new Cfg("scales", "sword", "line", 0xE8E8FF, 0x8090FF, FALL, LIGHTNING | CROWN),           // arbiter     (Justiciar)
        new Cfg("shard", "petal", "smoke", 0xFF3080, 0x701040, SPIRAL, WINGS),                     // assassin    (Demoness)
        new Cfg("note", "feather", "spark", 0xFFD050, 0xFFF2A0, RADIAL, RAYS | WINGS | CROWN),     // bard        (Sun)
        new Cfg("skull", "skull", "smoke", 0x70FFB0, 0x1A5040, RISE, CLOUDS),                      // corpse      (Death)
        new Cfg("eye", "crack", "fog", 0x8040FF, 0x301060, DRIFT, CLOUDS | LIGHTNING),             // criminal    (Abyss)
        new Cfg("flame", "sword", "flame", 0xFF4020, 0xFFA040, RISE, RAYS),                        // hunter      (Red Priest)
        new Cfg("crown", "scales", "diamond", 0xFFD060, 0xA050FF, ORBIT, CROWN | CHAINS),          // lawyer      (Black Emperor)
        new Cfg("cog", "crack", "shard", 0x40FF90, 0xFF40A0, DRIFT, LIGHTNING),                    // marauder    (Error)
        new Cfg("wheel", "diamond", "spark", 0xC0C8D0, 0x60FF80, ORBIT, RAYS),                     // monster     (Wheel of Fortune)
        new Cfg("lantern", "star", "mote", 0xFFE080, 0x8060FF, SPIRAL, CLOUDS),                    // mystery     (Hermit)
        new Cfg("petal", "leaf", "petal", 0xFF90C0, 0x60E070, RISE, RAYS),                         // planter     (Mother)
        new Cfg("chain", "crack", "ring", 0xB0B8C8, 0xFF6040, FALL, CHAINS),                       // prisoner    (Chained)
        new Cfg("tower", "page", "mote", 0xFFFFFF, 0x80B0FF, RISE, RAYS | CLOUDS),                 // reader      (White Tower)
        new Cfg("spiral", "drop", "drop", 0x40B8FF, 0xFFE860, FALL, CLOUDS | LIGHTNING),           // sailor      (Tyrant)
        new Cfg("cog", "clock", "spark", 0xFFB040, 0x60D0FF, ORBIT, RAYS),                         // savant      (Paragon)
        new Cfg("cross", "chain", "drop", 0xE02030, 0x501018, FALL, CHAINS | CLOUDS),              // secrets     (Hanged Man)
        new Cfg("star", "page", "spark", 0xE0A0FF, 0x60FFE0, RADIAL, CROWN | RAYS),                // seer        (Fool)
        new Cfg("crescent", "star", "mote", 0x6070FF, 0x202860, DRIFT, CLOUDS),                    // sleepless   (Darkness)
        new Cfg("eye", "ring", "mote", 0x60FFF0, 0xFF80FF, SPIRAL, RAYS),                          // spectator   (Visionary)
        new Cfg("sword", "shard", "flame", 0xFFA030, 0x602010, RISE, WINGS | RAYS),                // warrior     (Twilight Giant)
    };

    public HoloRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override public ResourceLocation getTextureLocation(HoloCardEntity e) { return tex("glow"); }

    private static ResourceLocation tex(String n) {
        return TEX.computeIfAbsent(n, k -> ResourceLocation.fromNamespaceAndPath("astral_world", "textures/holo/" + k + ".png"));
    }

    // ---- state for one draw call ----
    private MultiBufferSource buf;
    private Matrix4f mat;
    private float rx, ry, rz, ux, uy, uz;   // camera right and up (world space)
    private int col1, col2;
    private float t;
    private int curPath;
    private boolean e_shiny;

    private static float h(int i, int salt) {
        double v = Math.sin(i * 12.9898 + salt * 78.233) * 43758.5453;
        return (float) (v - Math.floor(v));
    }
    private static float fract(float v) { return v - (float) Math.floor(v); }
    private static int rgba(int rgb, float a) {
        int al = Math.max(0, Math.min(255, (int) (a * 255)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }
    private static int mix(int a, int b, float f) {
        int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
        return (r << 16) | (g << 8) | bl;
    }

    /** One glowing quad centred at (cx,cy,cz), half-extents (ax,ay,az) and (bx,by,bz). Drawn from both sides. */
    private void quad(String tex, float cx, float cy, float cz, float ax, float ay, float az, float bx, float by, float bz, int argb) {
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(tex(tex)));
        int a = (argb >> 24) & 255, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        if (a <= 2) return;
        r = r * a / 255; g = g * a / 255; b = b * a / 255;   // the glow blend ignores alpha, so fade by darkening
        float[][] p = {
            {cx - ax - bx, cy - ay - by, cz - az - bz}, {cx + ax - bx, cy + ay - by, cz + az - bz},
            {cx + ax + bx, cy + ay + by, cz + az + bz}, {cx - ax + bx, cy - ay + by, cz - az + bz}};
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int side = 0; side < 2; side++)
            for (int k = 0; k < 4; k++) {
                int i = side == 0 ? k : 3 - k;
                vc.addVertex(mat, p[i][0], p[i][1], p[i][2]).setColor(r, g, b, a).setUv(uv[i][0], uv[i][1])
                  .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(0, 1, 0);
            }
    }
    /** Camera-facing quad. Negative width mirrors it. rot is in radians. */
    private void bb(String tex, float x, float y, float z, float w, float hgt, float rot, int argb) {
        float c = (float) Math.cos(rot), s = (float) Math.sin(rot);
        float hx = w / 2, hy = hgt / 2;
        float ax = (rx * c + ux * s) * hx, ay = (ry * c + uy * s) * hx, az = (rz * c + uz * s) * hx;
        float bx = (-rx * s + ux * c) * hy, by = (-ry * s + uy * c) * hy, bz = (-rz * s + uz * c) * hy;
        quad(tex, x, y, z, ax, ay, az, bx, by, bz, argb);
    }
    /** Flat quad on a horizontal plane, spun by rot. */
    private void flat(String tex, float x, float y, float z, float size, float rot, int argb) {
        float c = (float) Math.cos(rot) * size / 2, s = (float) Math.sin(rot) * size / 2;
        quad(tex, x, y, z, c, 0, s, -s, 0, c, argb);
    }
    /** Upright quad turned around the Y axis by yaw (spins like a coin). */
    private void stand(String tex, float x, float y, float z, float w, float hgt, float yaw, int argb) {
        float c = (float) Math.cos(yaw) * w / 2, s = (float) Math.sin(yaw) * w / 2;
        quad(tex, x, y, z, c, 0, s, 0, hgt / 2, 0, argb);
    }
    /** Upright column that always faces the camera sideways (beams, chains, bolts). */
    private void column(String tex, float x, float y0, float y1, float z, float w, int argb) {
        float len = (float) Math.sqrt(rx * rx + rz * rz); if (len < 1e-4f) len = 1;
        float hx = rx / len * w / 2, hz = rz / len * w / 2;
        quad(tex, x, (y0 + y1) / 2, z, hx, 0, hz, 0, (y1 - y0) / 2, 0, argb);
    }

    @Override
    public void render(HoloCardEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource bufs, int light) {
        super.render(e, yaw, pt, ps, bufs, light);
        int seq = e.seq(), pi = e.pathIndex();
        boolean shiny = e.shiny();
        this.buf = bufs; this.mat = ps.last().pose();
        this.t = (e.tickCount + pt) / 20f;

        drawCard(e, ps, bufs, light, pi, seq, shiny);
        if (seq >= 6) return;

        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        rx = -left.x; ry = -left.y; rz = -left.z; ux = up.x; uy = up.y; uz = up.z;

        curPath = pi; e_shiny = shiny;
        Cfg c = CFG[Math.min(pi, CFG.length - 1)];
        col1 = shiny ? 0xFFD040 : c.c1; col2 = shiny ? 0xFFF4B0 : c.c2;
        if (seq >= 4) rare(c, seq);
        else if (seq >= 2) epic(c, seq);
        else legendary(c, seq == 0);
    }

    private void drawCard(HoloCardEntity e, PoseStack ps, MultiBufferSource bufs, int light, int pi, int seq, boolean shiny) {
        String name = "textures/item/card_" + Tarot.PATHWAYS[pi] + "_" + seq + (shiny ? "_shiny" : "") + ".png";
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("astral_world", name);
        VertexConsumer vc = bufs.getBuffer(RenderType.entityCutoutNoCull(rl));
        float yawR = (float) Math.toRadians(e.getYRot());
        float fx = -(float) Math.sin(yawR), fz = (float) Math.cos(yawR);   // direction the placer faced = top of the card
        float sx = -fz, sz = fx;                                           // right of the card
        float hw = .3f, hl = .45f, y = .03f;
        float[][] p = {{-hw, -hl}, {hw, -hl}, {hw, hl}, {-hw, hl}};
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int k = 0; k < 4; k++)
            vc.addVertex(mat, sx * p[k][0] + fx * p[k][1], y, sz * p[k][0] + fz * p[k][1]).setColor(255, 255, 255, 255)
              .setUv(uv[k][0], uv[k][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0);
    }

    // ---------------- tiers ----------------

    private void rare(Cfg c, int seq) {
        float pulse = .75f + .25f * (float) Math.sin(t * 3);
        flat("ring", 0, .05f, 0, 1.0f, t * .5f, rgba(col2, .8f));
        bb("glow", 0, 1.1f, 0, 1.0f, 1.0f, 0, rgba(col1, .45f * pulse));
        float bob = (float) Math.sin(t * 2) * .06f;
        stand(c.core, 0, 1.1f + bob, 0, seq == 4 ? .55f : .42f, seq == 4 ? .55f : .42f, t * 1.8f, rgba(mix(col1, 0xFFFFFF, .35f), 1f));
        int motes = seq == 4 ? 5 : 3;
        for (int i = 0; i < motes; i++) {
            float a = t * 1.1f + i * TAU / motes;
            bb("mote", (float) Math.cos(a) * .5f, .6f + .5f * fract(t * .4f + i * .31f), (float) Math.sin(a) * .5f, .14f, .14f, 0, rgba(col1, .9f));
        }
    }

    private void epic(Cfg c, int seq) {
        boolean two = seq == 2;
        float top = two ? 3.6f : 3.0f;
        float pulse = .7f + .3f * (float) Math.sin(t * 2.5);
        // beam
        column("beam", 0, 0.05f, top, 0, two ? .75f : .6f, rgba(col1, .5f * pulse));
        column("beam", 0, 0.05f, top, 0, .25f, rgba(0xFFFFFF, .35f));
        // ground ring
        flat("ring_dash", 0, .05f, 0, 2.2f, -t * .4f, rgba(col1, .8f));
        flat("ring", 0, .06f, 0, 1.2f, t * .6f, rgba(col2, .7f));
        // core
        float cy = 1.7f + (float) Math.sin(t * 1.6) * .08f;
        bb("glow", 0, cy, 0, 1.6f, 1.6f, 0, rgba(col1, .5f * pulse));
        stand(c.core, 0, cy, 0, .75f, .75f, t * 1.5f, rgba(mix(col1, 0xFFFFFF, .4f), 1f));
        // ring of rune medallions
        int med = two ? 8 : 6;
        for (int i = 0; i < med; i++) {
            float a = -t * .5f + i * TAU / med;
            float x = (float) Math.cos(a) * 1.1f, z = (float) Math.sin(a) * 1.1f;
            float y = .85f + (float) Math.sin(t * 2 + i) * .08f;
            bb("disc", x, y, z, .42f, .42f, 0, rgba(col2, .75f));
            bb(i % 2 == 0 ? c.core : "star", x, y, z, .26f, .26f, t, rgba(0xFFFFFF, .9f));
        }
        // orbiting props
        int props = two ? 4 : 2;
        for (int i = 0; i < props; i++) {
            float a = t * .9f + i * TAU / props;
            float r = .75f;
            bb(c.prop, (float) Math.cos(a) * r, cy + (float) Math.sin(t * 1.5f + i) * .25f, (float) Math.sin(a) * r, .32f, .32f, a, rgba(mix(col1, col2, .3f), .95f));
        }
        // rising motes
        for (int i = 0; i < 10; i++) {
            float p = fract(t * .3f + h(i, 1));
            bb("mote", (h(i, 2) - .5f) * 1.6f, p * top, (h(i, 3) - .5f) * 1.6f, .12f, .12f, 0, rgba(col1, (float) Math.sin(p * Math.PI)));
        }
    }

    private void legendary(Cfg c, boolean myth) {
        if (curPath != 15) dims(myth);
        if (scene(curPath, myth)) return;
        float s = myth ? 2.2f : 1.5f;                  // overall scale
        float top = (myth ? 11f : 7.5f);
        float cy = (myth ? 4.5f : 3.2f) + (float) Math.sin(t * 1.3) * .1f * s;
        float pulse = .7f + .3f * (float) Math.sin(t * 2.2);
        int ex = c.extras;

        // ground sigil
        flat("ring_thick", 0, .05f, 0, 3.2f * s, t * .25f, rgba(col2, .8f));
        flat("ring_dash", 0, .06f, 0, 2.5f * s, -t * .5f, rgba(col1, .9f));
        flat("star", 0, .07f, 0, 1.8f * s, t * .8f, rgba(col1, .45f));
        if (myth) flat("ring_dash", 0, .08f, 0, 4.4f, t * .3f, rgba(col1, .6f));

        // beam
        column("beam", 0, .05f, top, 0, (myth ? 1.4f : .9f), rgba(col1, .45f * pulse));
        column("beam", 0, .05f, top, 0, .3f * s, rgba(0xFFFFFF, .4f));

        // extras
        if ((ex & CLOUDS) != 0) {
            for (int i = 0; i < (myth ? 7 : 4); i++) {
                float a = t * .08f + i * TAU / (myth ? 7 : 4);
                bb("cloud", (float) Math.cos(a) * 1.4f * s, top - .2f + (i % 2) * .3f, (float) Math.sin(a) * 1.4f * s, 3.6f * s, 2.0f * s, 0, rgba(mix(col2, 0x202030, .3f), .5f));
            }
        }
        if ((ex & RAYS) != 0) {
            bb("rays", 0, cy, 0, 5f * s, 5f * s, t * .25f, rgba(col1, .55f));
            if (myth) bb("rays", 0, cy, 0, 7.5f, 7.5f, -t * .15f, rgba(col2, .45f));
        }
        if ((ex & CHAINS) != 0) {
            int n = myth ? 7 : 4;
            for (int i = 0; i < n; i++) {
                float a = i * TAU / n + .4f, r = (1.0f + .15f * (float) Math.sin(t + i)) * s;
                float sway = (float) Math.sin(t * .8f + i) * .15f;
                column("chain", (float) Math.cos(a) * r + sway, cy - .4f, top + 1.5f, (float) Math.sin(a) * r, .38f * s, rgba(mix(col1, 0xFFFFFF, .2f), .85f));
            }
        }
        if ((ex & LIGHTNING) != 0) {
            for (int i = 0; i < (myth ? 4 : 2); i++) {
                float ph = fract(t * (.9f + i * .17f) + h(i, 9));
                if (ph < .14f) {
                    float x = (h((int) (t * 1.0f) + i, 4) - .5f) * 3.6f * s, z = (h((int) (t * 1.0f) + i, 5) - .5f) * 3.6f * s;
                    float f = 1f - ph / .14f;
                    column("line", x, .05f, top, z, .3f * s, rgba(0xFFFFFF, f));
                    column("line", x + .2f, .05f, top, z, .6f * s, rgba(col1, f * .6f));
                    bb("glow", x, .3f, z, 1.4f * s, 1.4f * s, 0, rgba(col1, f * .8f));
                }
            }
        }

        // core symbol, glow and halo
        bb("corona", 0, cy, 0, 3.2f * s, 3.2f * s, t * .4f, rgba(col1, .6f * pulse));
        bb("glow", 0, cy, 0, 2.4f * s, 2.4f * s, 0, rgba(col2, .55f));
        if ((ex & WINGS) != 0) {
            float flap = 1f + .06f * (float) Math.sin(t * 2.5);
            bb("wing", rx * .95f * s, cy + .1f, rz * .95f * s, 2.8f * s * flap, 2.6f * s, 0, rgba(mix(col1, 0xFFFFFF, .3f), .85f));
            bb("wing", -rx * .95f * s, cy + .1f, -rz * .95f * s, -2.8f * s * flap, 2.6f * s, 0, rgba(mix(col1, 0xFFFFFF, .3f), .85f));
        }
        stand(c.core, 0, cy, 0, 1.25f * s, 1.25f * s, t * 1.3f, rgba(mix(col1, 0xFFFFFF, .45f), 1f));
        if ((ex & CROWN) != 0) bb("crown", 0, cy + .95f * s, 0, .9f * s, .6f * s, 0, rgba(mix(col1, 0xFFFFFF, .3f), .95f));

        // props: two orbiting rings
        int pn = myth ? 10 : 6;
        for (int i = 0; i < pn; i++) {
            boolean inner = i % 2 == 0;
            float a = t * (inner ? .8f : -.55f) + i * TAU / pn;
            float r = (inner ? 1.5f : 2.2f) * s;
            float y = cy + (float) Math.sin(a * 1.5f + i) * .6f * s;
            bb(c.prop, (float) Math.cos(a) * r, y, (float) Math.sin(a) * r, .55f * s, .55f * s, inner ? a : -a, rgba(mix(col1, 0xFFFFFF, .25f), .95f));
        }

        // ambient motion, unique per pathway
        int n = myth ? 70 : 34;
        for (int i = 0; i < n; i++) {
            float sp = .12f + h(i, 7) * .12f;
            float p = fract(t * sp + h(i, 1));
            float fade = (float) Math.sin(p * Math.PI);
            float ang = h(i, 2) * TAU, rr = (.5f + h(i, 3) * 2.4f) * s;
            float x, y, z, sz = (.1f + h(i, 8) * .14f) * s;
            switch (c.motion) {
                case FALL -> { x = (float) Math.cos(ang) * rr; z = (float) Math.sin(ang) * rr; y = top - p * top; }
                case RISE -> { x = (float) Math.cos(ang) * rr * .8f + (float) Math.sin(t + i) * .2f; z = (float) Math.sin(ang) * rr * .8f; y = p * top; }
                case ORBIT -> { float a = t * (.6f + h(i, 4) * .4f) + ang; x = (float) Math.cos(a) * rr; z = (float) Math.sin(a) * rr; y = cy + (h(i, 5) - .5f) * 3f * s; fade = .7f + .3f * fade; }
                case SPIRAL -> { float a = ang + p * 9f; float r2 = rr * (1.2f - p * .8f); x = (float) Math.cos(a) * r2; z = (float) Math.sin(a) * r2; y = p * top; }
                case RADIAL -> { float d = p * 3.5f * s; float el = (h(i, 5) - .5f) * 2.4f; x = (float) Math.cos(ang) * d * (float) Math.cos(el); z = (float) Math.sin(ang) * d * (float) Math.cos(el); y = cy + (float) Math.sin(el) * d; }
                default -> { x = (float) Math.cos(ang) * rr + (float) Math.sin(t * .5f + i) * .8f; z = (float) Math.sin(ang) * rr + (float) Math.cos(t * .4f + i) * .8f; y = .3f + h(i, 6) * 3.2f * s; sz *= 2.2f; fade *= .6f; }
            }
            bb(c.amb, x, y, z, sz, sz, c.motion == FALL ? 0 : t + i, rgba(i % 3 == 0 ? col2 : col1, fade * .9f));
        }
        if (myth) {
            // mythic only: a second, much larger sigil in the sky and a flash that pulses outward from the symbol
            flat("ring_thick", 0, top - .5f, 0, 5f, -t * .2f, rgba(col1, .55f));
            float wv = fract(t * .35f);
            flat("ring", 0, .1f, 0, wv * 9f, 0, rgba(col1, (1 - wv) * .8f));
            bb("ring", 0, cy, 0, wv * 6f, wv * 6f, 0, rgba(0xFFFFFF, (1 - wv) * .5f));
        }
    }

    // ---------------- Sailor / Tyrant: a hand-built storm ----------------
    private void sailor(boolean myth) {
        float R = myth ? 11f : 7f, top = myth ? 16f : 10f, s = myth ? 2.1f : 1.4f;
        float cy = (myth ? 5.5f : 3.8f) + (float) Math.sin(t * 1.2) * .12f * s;
        int sea = 0x40B8FF, foam = 0xC8F0FF, gold = 0xFFE860;
        if (e_shiny) { sea = 0xFFC030; foam = 0xFFF0A0; gold = 0xFFFFFF; }
        // lightning schedule first, because the whole scene brightens with it
        int nb = myth ? 5 : 3;
        float flash = 0;
        float[] bi = new float[nb];
        for (int i = 0; i < nb; i++) {
            float period = 1.1f + .23f * i, q = t / period + h(i, 9);
            float ph = fract(q);
            float v = ph < .07f ? 1f : ph < .12f ? .25f : ph < .17f ? .9f : ph < .22f ? .4f * (1 - (ph - .17f) / .05f) : 0f;
            bi[i] = v; flash = Math.max(flash, v);
        }
        // whirlpool on the ground and a waterspout funnel
        flat("whirl", 0, .06f, 0, 2f * R, t * .6f, rgba(sea, .85f));
        flat("whirl", 0, .08f, 0, 1.3f * R, -t * 1.0f, rgba(foam, .6f));
        flat("ring_thick", 0, .09f, 0, 2f * R * .98f, t * .1f, rgba(foam, .6f));
        flat("ring_dash", 0, .10f, 0, 1.5f * R, -t * .3f, rgba(gold, .6f));
        for (int j = 0; j < 7; j++) {
            float y = .3f + j * (cy - .3f) / 7f;
            float size = R * (1.05f - j * .13f);
            flat("whirl", 0, y, 0, size, t * (.9f + j * .12f) * (j % 2 == 0 ? 1 : -1), rgba(sea, .3f));
        }
        float pw = fract(t * .3f);
        flat("ring", 0, .12f, 0, pw * 2.2f * R, 0, rgba(foam, (1 - pw) * .7f));
        // storm clouds that light up with every strike
        int nc = myth ? 18 : 11;
        for (int i = 0; i < nc; i++) {
            float a = t * .05f + i * TAU / nc, rr = R * (.35f + .45f * h(i, 11));
            float lit = .28f + flash * .5f;
            bb("cloud", (float) Math.cos(a) * rr, top + h(i, 12) * 1.5f, (float) Math.sin(a) * rr, R * (.9f + h(i, 13) * .6f), R * .55f, 0, rgba(mix(0x5A7AA8, 0xFFFFFF, flash * .6f), lit));
        }
        // rain sheets
        int nr = myth ? 320 : 170;
        for (int i = 0; i < nr; i++) {
            float p = fract(t * (.9f + h(i, 3) * .5f) + h(i, 1));
            float a = h(i, 2) * TAU, rr = (float) Math.sqrt(h(i, 4)) * R * .95f;
            float y = top * (1 - p);
            column("streak", (float) Math.cos(a) * rr, y - 1.3f * s, y, (float) Math.sin(a) * rr, .1f * s, rgba(foam, .55f));
        }
        // waves rising out of the whirlpool
        int nw = myth ? 14 : 9;
        for (int ring = 0; ring < (myth ? 2 : 1); ring++)
            for (int i = 0; i < nw; i++) {
                float a = (ring == 0 ? t * .32f : -t * .22f) + i * TAU / nw + ring * .3f;
                float rad = R * (ring == 0 ? .6f : .88f);
                float hgt = (1.9f + .6f * (float) Math.sin(t * 1.4f + i * 1.7f)) * s * (ring == 0 ? 1f : 1.35f);
                stand("wave", (float) Math.cos(a) * rad, hgt / 2, (float) Math.sin(a) * rad, hgt * 2f, hgt, a + 1.5708f, rgba(mix(sea, foam, .25f), .75f));
            }
        // lightning strikes
        for (int i = 0; i < nb; i++) {
            float v = bi[i]; if (v <= 0) continue;
            int k = (int) Math.floor(t / (1.1f + .23f * i) + h(i, 9));
            float a = h(k * 7 + i, 21) * TAU, rr = (.1f + .75f * h(k * 5 + i, 22)) * R;
            float x = (float) Math.cos(a) * rr, z = (float) Math.sin(a) * rr;
            boolean mir = (k & 1) == 0;
            float bw = top * .16f * (mir ? -1 : 1);
            column(i % 2 == 0 ? "bolt" : "bolt2", x, .05f, top + .5f, z, bw, rgba(0xFFFFFF, v));
            column(i % 2 == 0 ? "bolt" : "bolt2", x, .05f, top + .5f, z, bw * 1.8f, rgba(sea, v * .45f));
            bb("glow", x, .4f, z, 5f * s, 5f * s, 0, rgba(0xFFFFFF, v * .9f));
            flat("ring", x, .14f, z, (1.2f - v * .4f + (1 - v) * 3f) * s, 0, rgba(foam, v));
        }
        // the trident
        float th = (myth ? 8.5f : 5.2f);
        bb("whirl", 0, cy, 0, th * 1.5f, th * 1.5f, t * .8f, rgba(sea, .55f));
        bb("corona", 0, cy, 0, th * 1.4f, th * 1.4f, -t * .3f, rgba(gold, .6f + flash * .3f));
        bb("glow", 0, cy, 0, th * 1.1f, th * 1.1f, 0, rgba(sea, .5f));
        stand("trident", 0, cy, 0, th * .5f, th, t * .8f, rgba(mix(gold, 0xFFFFFF, .5f), 1f));
        column("beam", 0, .05f, cy - th / 2f, 0, .7f * s, rgba(sea, .55f));
        for (int i = 0; i < 3; i++) {   // arcs from the prongs up into the clouds
            float ph = fract(t * 2.3f + i * .37f);
            if (ph < .35f) column(i % 2 == 0 ? "bolt2" : "bolt", (i - 1) * .55f * s, cy + th * .45f, top, 0, top * .1f * (i % 2 == 0 ? 1 : -1), rgba(gold, (1 - ph / .35f) * .8f));
        }
        // droplets spiralling up the funnel
        int nd = myth ? 60 : 36;
        for (int i = 0; i < nd; i++) {
            float p = fract(t * .22f + h(i, 5));
            float a = h(i, 6) * TAU + p * 8f, rr = R * (.55f - .45f * p) * (.6f + .4f * h(i, 7));
            float sz = (.3f + .3f * h(i, 8)) * s;
            bb("drop", (float) Math.cos(a) * rr, p * cy * 1.4f, (float) Math.sin(a) * rr, sz * .6f, sz, 0, rgba(foam, (float) Math.sin(p * Math.PI) * .9f));
        }
        // mist hugging the water
        for (int i = 0; i < 10; i++) {
            float a = t * .1f + i * TAU / 10;
            bb("fog", (float) Math.cos(a) * R * .55f, .8f * s, (float) Math.sin(a) * R * .55f, 4f * s, 2f * s, 0, rgba(foam, .3f));
        }
    }

    // ================= shared building blocks for the hand-built scenes =================
    private float R, top, S, CY;
    private void dims(boolean myth) {
        R = myth ? 11f : 7f; top = myth ? 16f : 10f; S = myth ? 2.1f : 1.4f;
        CY = (myth ? 5.5f : 3.8f) + (float) Math.sin(t * 1.2) * .12f * S;
    }
    private static int hsv(float hue, float sat) {
        float hh = (hue - (float) Math.floor(hue)) * 6f; int i = (int) hh; float f = hh - i;
        float p = 1 - sat, q = 1 - sat * f, u = 1 - sat * (1 - f);
        float r, g, b;
        switch (i) { case 0 -> { r = 1; g = u; b = p; } case 1 -> { r = q; g = 1; b = p; } case 2 -> { r = p; g = 1; b = u; }
            case 3 -> { r = p; g = q; b = 1; } case 4 -> { r = u; g = p; b = 1; } default -> { r = 1; g = p; b = q; } }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
    private static final int FALLM = 0, RISEM = 1, SPIRALM = 2, ORBITM = 3, RADIALM = 4, DRIFTM = 5;
    /** n sprites of one texture moving in a pattern. y0..y1 is the height range (RADIAL: y0 is the centre height). */
    private void sprites(String tx, int n, int mode, float rad, float y0, float y1, float size, int rgb, float alpha, float speed, int seed, boolean spin) {
        for (int i = 0; i < n; i++) {
            float p = fract(t * speed * (.7f + .6f * h(i, seed + 3)) + h(i, seed + 1));
            float ang = h(i, seed + 2) * TAU, rr = (float) Math.sqrt(h(i, seed + 4)) * rad;
            float fade = (float) Math.sin(p * Math.PI), sz = size * (.6f + .8f * h(i, seed + 8));
            float x, y, z;
            switch (mode) {
                case FALLM -> { x = (float) Math.cos(ang) * rr; z = (float) Math.sin(ang) * rr; y = y1 - p * (y1 - y0); }
                case RISEM -> { x = (float) Math.cos(ang) * rr + (float) Math.sin(t + i) * .25f; z = (float) Math.sin(ang) * rr; y = y0 + p * (y1 - y0); }
                case SPIRALM -> { float a = ang + p * 8f, r2 = rad * (1.1f - p * .8f) * (.5f + .5f * h(i, seed + 4)); x = (float) Math.cos(a) * r2; z = (float) Math.sin(a) * r2; y = y0 + p * (y1 - y0); }
                case ORBITM -> { float a = t * (.4f + .5f * h(i, seed + 5)) + ang; x = (float) Math.cos(a) * rr; z = (float) Math.sin(a) * rr; y = y0 + h(i, seed + 6) * (y1 - y0); fade = .6f + .4f * fade; }
                case RADIALM -> { float el = (h(i, seed + 5) - .5f) * 2.6f, d = p * rad; x = (float) Math.cos(ang) * d * (float) Math.cos(el); z = (float) Math.sin(ang) * d * (float) Math.cos(el); y = y0 + (float) Math.sin(el) * d; }
                default -> { x = (float) Math.cos(ang) * rr + (float) Math.sin(t * .5f + i) * 1.2f; z = (float) Math.sin(ang) * rr + (float) Math.cos(t * .4f + i) * 1.2f; y = y0 + h(i, seed + 6) * (y1 - y0); fade = (.4f + .6f * (float) Math.sin(t + i * 1.3f)) * .8f; }
            }
            bb(tx, x, y, z, sz, sz, spin ? t * (1 + h(i, seed + 7)) + i : 0, rgba(rgb, Math.max(0, fade) * alpha));
        }
    }
    private void pillars(String tx, int n, float rad, float y0, float height, float w, int rgb, float alpha, float phase) {
        for (int i = 0; i < n; i++) {
            float a = phase + i * TAU / n;
            column(tx, (float) Math.cos(a) * rad, y0, y0 + height * (.85f + .15f * (float) Math.sin(t * 1.5f + i)), (float) Math.sin(a) * rad, w, rgba(rgb, alpha));
        }
    }
    private void hang(String tx, int n, float rad, float yTop, float yBot, float w, int rgb, float alpha, float phase) {
        for (int i = 0; i < n; i++) {
            float a = phase + i * TAU / n, r = rad * (.8f + .35f * h(i, 31));
            float sw = (float) Math.sin(t * .8f + i * 1.9f) * .2f * S;
            column(tx, (float) Math.cos(a) * r + sw, yBot + h(i, 32) * 2f, yTop, (float) Math.sin(a) * r, w, rgba(rgb, alpha));
        }
    }
    private void clouds(int n, float rad, float y, int rgb, float alpha, float size) {
        for (int i = 0; i < n; i++) {
            float a = t * .05f + i * TAU / n, rr = rad * (.3f + .5f * h(i, 11));
            bb("cloud", (float) Math.cos(a) * rr, y + h(i, 12) * 1.5f, (float) Math.sin(a) * rr, size * (.9f + h(i, 13) * .6f), size * .55f, 0, rgba(rgb, alpha));
        }
    }
    private void sigil(float rad, boolean spiky) {
        flat("ring_thick", 0, .06f, 0, 2f * rad, t * .12f, rgba(col2, .8f));
        flat("ring_dash", 0, .08f, 0, 1.5f * rad, -t * .3f, rgba(col1, .9f));
        flat(spiky ? "star" : "ring", 0, .09f, 0, 1.1f * rad, t * .5f, rgba(col1, .5f));
        float pw = fract(t * .3f);
        flat("ring", 0, .12f, 0, pw * 2.2f * rad, 0, rgba(col1, (1 - pw) * .7f));
    }
    private void strikes(int n, float rad, float y, int rgb, String a, String b) {
        for (int i = 0; i < n; i++) {
            float period = 1.2f + .27f * i, q = t / period + h(i, 19);
            float ph = fract(q);
            float v = ph < .07f ? 1f : ph < .12f ? .25f : ph < .17f ? .9f : ph < .22f ? .4f * (1 - (ph - .17f) / .05f) : 0f;
            if (v <= 0) continue;
            int k = (int) Math.floor(q);
            float an = h(k * 7 + i, 21) * TAU, rr = (.1f + .75f * h(k * 5 + i, 22)) * rad;
            float x = (float) Math.cos(an) * rr, z = (float) Math.sin(an) * rr, bw = y * .16f * ((k & 1) == 0 ? -1 : 1);
            column(i % 2 == 0 ? a : b, x, .05f, y + .5f, z, bw, rgba(0xFFFFFF, v));
            column(i % 2 == 0 ? a : b, x, .05f, y + .5f, z, bw * 1.8f, rgba(rgb, v * .45f));
            bb("glow", x, .4f, z, 5f * S, 5f * S, 0, rgba(0xFFFFFF, v * .8f));
        }
    }
    /** The big spinning symbol with glow behind it. */
    private void emblem(String tx, float y, float size, float spin, float yawSpeed, boolean flatFace) {
        float pulse = .75f + .25f * (float) Math.sin(t * 2.2);
        bb("corona", 0, y, 0, size * 2.2f, size * 2.2f, t * .4f, rgba(col1, .6f * pulse));
        bb("glow", 0, y, 0, size * 1.8f, size * 1.8f, 0, rgba(col2, .55f));
        int c = mix(col1, 0xFFFFFF, .5f);
        if (flatFace) bb(tx, 0, y, 0, size, size, spin, rgba(c, 1f)); else stand(tx, 0, y, 0, size, size, t * yawSpeed, rgba(c, 1f));
    }
    private void beam(float y1, float w) {
        column("beam", 0, .05f, y1, 0, w, rgba(col1, .45f));
        column("beam", 0, .05f, y1, 0, w * .3f, rgba(0xFFFFFF, .4f));
    }
    private void mythExtra(boolean myth) {
        if (!myth) return;
        flat("ring_thick", 0, top - .5f, 0, 5f * S, -t * .2f, rgba(col1, .5f));
        float wv = fract(t * .35f);
        flat("ring", 0, .1f, 0, wv * 9f * S, 0, rgba(col1, (1 - wv) * .7f));
        bb("ring", 0, CY, 0, wv * 8f * S, wv * 8f * S, 0, rgba(0xFFFFFF, (1 - wv) * .4f));
    }
    private void orbiters(String tx, int n, float rad, float y0, float amp, float size, float speed, boolean spinSelf, int rgb, float alpha) {
        for (int i = 0; i < n; i++) {
            float a = t * speed + i * TAU / n;
            bb(tx, (float) Math.cos(a) * rad, y0 + (float) Math.sin(a * 1.7f + i) * amp, (float) Math.sin(a) * rad, size, size, spinSelf ? a * 2 : 0, rgba(rgb, alpha));
        }
    }

    private boolean scene(int p, boolean m) {
        float myth = m ? 1f : 0f; int N = m ? 2 : 1;
        switch (p) {
            case 0 -> { // Moon
                sigil(R, true); beam(top * .8f, 2f * S);
                bb("glow", 0, top * .8f, 0, 14 * S, 14 * S, 0, rgba(col2, .35f));
                bb("crescent", 0, top * .8f, 0, 9 * S, 9 * S, .5f, rgba(mix(col1, 0xFFFFFF, .5f), 1f));
                bb("crescent", 0, top * .8f, 0, 11 * S, 11 * S, .5f, rgba(col2, .35f));
                sprites("star", 40 * N, DRIFTM, R * 1.3f, 1, top, .5f * S, 0xFFFFFF, .8f, .3f, 4, true);
                clouds(8 * N, R, top * .6f, 0xB0C8FF, .22f, R * 1.1f);
                emblem("crescent", CY, 3f * S, 0, 1.1f, false);
                sprites("leaf", 30 * N, SPIRALM, R * .8f, 0, top * .8f, .6f * S, col2, .9f, .15f, 20, true);
                sprites("drop", 26 * N, RISEM, R * .7f, 0, top * .6f, .5f * S, 0xE0F4FF, .9f, .2f, 30, false);
                sprites("petal", 30 * N, SPIRALM, R * .6f, 0, top * .9f, .4f * S, 0xFFFFFF, .8f, .12f, 40, true);
                orbiters("diamond", 6 * N, R * .45f, CY, 1.2f * S, .9f * S, .5f, true, col1, .9f);
            }
            case 1 -> { // Door
                sigil(R, true);
                bb("rays", 0, CY, 0, 16 * S, 16 * S, t * .15f, rgba(col1, .6f));
                if (m) bb("rays", 0, CY, 0, 24 * S, 24 * S, -t * .1f, rgba(col2, .45f));
                bb("glow", 0, CY, 0, 9 * S, 9 * S, 0, rgba(col1, .7f));
                bb("door", 0, CY, 0, 4.5f * S, 6.5f * S, 0, rgba(mix(col1, 0xFFFFFF, .6f), 1f));
                column("beam", 0, .05f, CY - 3 * S, 0, 3f * S, rgba(col1, .5f));
                for (int i = 0; i < 6 * N; i++) { float a = t * .2f + i * TAU / (6 * N); float op = .5f + .5f * (float) Math.sin(t * 1.3f + i * 1.7f);
                    float x = (float) Math.cos(a) * R * .8f, z = (float) Math.sin(a) * R * .8f;
                    bb("glow", x, 1.6f * S, z, 3.5f * S * op, 3.5f * S * op, 0, rgba(col1, .6f)); bb("door", x, 1.6f * S, z, 1.8f * S, 2.8f * S, 0, rgba(col2, .9f)); }
                orbiters("key", 8 * N, R * .5f, CY, 1.8f * S, 1f * S, .7f, true, col1, 1f);
                sprites("spark", 70 * N, RADIALM, R * 1.3f, CY, 0, .5f * S, 0xFFFFFF, .9f, .35f, 5, true);
            }
            case 2 -> { // Justiciar
                sigil(R, false);
                pillars("beam", 8 * N, R * .75f, 0, top * .85f, 1.4f * S, col1, .4f, 0);
                pillars("line", 8 * N, R * .75f, 0, top * .9f, .6f * S, 0xFFFFFF, .5f, 0);
                emblem("scales", CY, 4.5f * S, 0, .6f, false);
                for (int i = 0; i < 8 * N; i++) { float a = -t * .1f + i * TAU / (8 * N), r = R * (.35f + .3f * (i % 2));
                    bb("sword", (float) Math.cos(a) * r, top * .55f + (float) Math.sin(t * 1.2f + i) * .5f, (float) Math.sin(a) * r, 1.4f * S, 3.4f * S, 3.14159f, rgba(mix(col1, 0xFFFFFF, .4f), .95f)); }
                sprites("line", 90 * N, FALLM, R, 0, top, .4f * S, 0xFFFFFF, .6f, .9f, 6, false);
                clouds(6 * N, R, top, 0xC8D0FF, .25f, R);
                strikes(2 * N, R, top, col1, "bolt", "bolt2");
            }
            case 3 -> { // Demoness
                sigil(R, true);
                bb("wing", rx * 3.2f * S, CY + S, rz * 3.2f * S, 7.5f * S, 7f * S, 0, rgba(col1, .75f));
                bb("wing", -rx * 3.2f * S, CY + S, -rz * 3.2f * S, -7.5f * S, 7f * S, 0, rgba(col1, .75f));
                bb("wing", rx * 4.8f * S, CY - .5f * S, rz * 4.8f * S, 5f * S, 5f * S, 0, rgba(col2, .6f));
                bb("wing", -rx * 4.8f * S, CY - .5f * S, -rz * 4.8f * S, -5f * S, 5f * S, 0, rgba(col2, .6f));
                emblem("shard", CY, 3.2f * S, 0, 1.4f, false);
                sprites("petal", 100 * N, SPIRALM, R * .9f, 0, top, .7f * S, col1, .95f, .2f, 7, true);
                sprites("smoke", 30 * N, RISEM, R * .6f, 0, top * .7f, 2f * S, col2, .4f, .15f, 8, true);
                orbiters("shard", 10 * N, R * .45f, CY, 1.5f * S, 1.1f * S, 1.6f, true, 0xFFFFFF, .9f);
                bb("crescent", 0, top * .85f, 0, 7 * S, 7 * S, 0, rgba(col1, .7f));
            }
            case 4 -> { // Sun
                sigil(R, true);
                bb("rays", 0, top * .6f, 0, 20 * S, 20 * S, t * .2f, rgba(col1, .6f));
                bb("rays", 0, top * .6f, 0, 14 * S, 14 * S, -t * .3f, rgba(col2, .55f));
                bb("corona", 0, top * .6f, 0, 12 * S, 12 * S, t * .3f, rgba(col1, .9f));
                bb("disc", 0, top * .6f, 0, 6 * S, 6 * S, 0, rgba(0xFFFFFF, 1f));
                column("beam", 0, .05f, top * .6f, 0, 2.5f * S, rgba(col1, .5f));
                bb("wing", rx * 2.2f * S, CY, rz * 2.2f * S, 5f * S, 4.6f * S, 0, rgba(0xFFFFFF, .85f));
                bb("wing", -rx * 2.2f * S, CY, -rz * 2.2f * S, -5f * S, 4.6f * S, 0, rgba(0xFFFFFF, .85f));
                emblem("note", CY, 3f * S, 0, 1.0f, false);
                pillars("line", 12 * N, R * .6f, 0, 6f * S, .35f * S, col1, .8f, t * .2f);   // harp strings
                sprites("note", 50 * N, RADIALM, R * 1.1f, CY, 0, 1f * S, col1, 1f, .22f, 9, true);
                sprites("feather", 40 * N, FALLM, R, 0, top, .9f * S, 0xFFFFFF, .9f, .12f, 10, true);
                sprites("spark", 80 * N, RISEM, R, 0, top, .35f * S, 0xFFFFFF, .9f, .3f, 11, true);
            }
            case 5 -> { // Death
                sigil(R, false);
                clouds(10 * N, R, top * .5f, col2, .5f, R * 1.2f);
                emblem("skull", CY, 4.5f * S, 0, .5f, false);
                sprites("skull", 16 * N, SPIRALM, R * .8f, 0, top * .8f, 1.5f * S, col1, .85f, .1f, 12, false);
                pillars("flame", 10 * N, R * .7f, 0, 4.5f * S, 1.8f * S, col1, .85f, 0);
                for (int i = 0; i < 8 * N; i++) { float a = i * TAU / (8 * N) + .2f; float rr = R * .95f; stand("cross", (float) Math.cos(a) * rr, 1.5f * S, (float) Math.sin(a) * rr, 2f * S, 3f * S, a + 1.57f, rgba(col2, .9f)); }
                sprites("smoke", 40 * N, RISEM, R, 0, top * .8f, 2.2f * S, col1, .35f, .12f, 13, true);
                sprites("mote", 70 * N, RISEM, R, 0, top, .4f * S, col1, .9f, .25f, 14, false);
                for (int i = 0; i < 10; i++) bb("fog", (float) Math.cos(i * .63f + t * .08f) * R * .6f, .8f * S, (float) Math.sin(i * .63f + t * .08f) * R * .6f, 5 * S, 2 * S, 0, rgba(col1, .22f));
            }
            case 6 -> { // Abyss
                flat("whirl", 0, .06f, 0, 2.2f * R, -t * .5f, rgba(col1, .8f)); flat("whirl", 0, .08f, 0, 1.4f * R, t * .8f, rgba(col2, .8f));
                sigil(R, true);
                for (int i = 0; i < 8; i++) { float pw = fract(t * .25f + i / 8f); flat("ring", 0, .1f + i * .02f, 0, (1 - pw) * 2.2f * R, 0, rgba(col1, pw * .7f)); }
                bb("whirl", 0, CY, 0, 11 * S, 11 * S, t * .6f, rgba(col2, .7f));
                bb("glow", 0, CY, 0, 8 * S, 8 * S, 0, rgba(col1, .5f));
                bb("eye", 0, CY, 0, 6.5f * S, 3.4f * S * Math.max(.15f, Math.abs((float) Math.cos(t * .5f))), 0, rgba(mix(col1, 0xFFFFFF, .5f), 1f));
                bb("orb", 0, CY, 0, 1.6f * S, 1.6f * S, 0, rgba(0xFFFFFF, 1f));
                for (int i = 0; i < 12 * N; i++) { float a = i * TAU / (12 * N) + t * .1f, r = R * .85f; float sw = (float) Math.sin(t + i) * .5f;
                    column("crack", (float) Math.cos(a) * r, 0, top * (.5f + .4f * h(i, 3)) + sw, (float) Math.sin(a) * r, 1.4f * S, rgba(col1, .8f)); }
                clouds(10 * N, R, top * .7f, col2, .45f, R * 1.2f);
                sprites("fog", 20 * N, DRIFTM, R, .5f, 5f, 5f * S, col2, .35f, .1f, 15, true);
                sprites("mote", 80 * N, SPIRALM, R, 0, top, .35f * S, col1, .9f, .2f, 16, false);
                for (int i = 0; i < 9 * N; i++) { float a = t * .15f + i * TAU / (9 * N); float bl = Math.max(.1f, (float) Math.abs(Math.cos(t * .6f + i)));
                    bb("eye", (float) Math.cos(a) * R * .65f, 1.2f * S + (i % 3) * 1.2f * S, (float) Math.sin(a) * R * .65f, 1.8f * S, 1f * S * bl, 0, rgba(col1, .85f)); }
                strikes(2 * N, R, top, col1, "bolt", "bolt2");
            }
            case 7 -> { // Red Priest
                sigil(R, true);
                for (int k = 0; k < 3; k++) bb("rays", 0, CY, 0, (14 + k * 4) * S, (14 + k * 4) * S, t * (k % 2 == 0 ? .2f : -.15f), rgba(k == 1 ? col2 : col1, .45f));
                for (int k = 0; k < 4; k++) { float sc = 1f - k * .18f; bb("flame", (float) Math.sin(t * 3 + k) * .3f, k * 1.3f * S + 3 * S, 0, 5.5f * S * sc, 7f * S * sc, 0, rgba(k % 2 == 0 ? col1 : col2, .8f)); }
                emblem("flame", CY + S, 3.5f * S, 0, 0, true);
                pillars("flame", 12 * N, R * .75f, 0, 5f * S, 2.2f * S, col1, .85f, 0);
                for (int i = 0; i < 4; i++) { float a = i * TAU / 4 + t * .2f; bb("sword", (float) Math.cos(a) * R * .35f, CY, (float) Math.sin(a) * R * .35f, 1.4f * S, 4.2f * S, a, rgba(0xFFFFFF, .9f)); }
                sprites("flame", 60 * N, RISEM, R * .8f, 0, top, 1.1f * S, col1, .85f, .25f, 17, false);
                sprites("spark", 120 * N, RISEM, R, 0, top * 1.2f, .3f * S, col2, 1f, .5f, 18, true);
                sprites("smoke", 20 * N, RISEM, R * .5f, top * .3f, top, 3f * S, 0x602010, .3f, .1f, 19, true);
            }
            case 8 -> { // Black Emperor
                sigil(R, true);
                pillars("tower", 8 * N, R * .8f, 0, top * .8f, 2.2f * S, col2, .7f, 0);
                pillars("beam", 8 * N, R * .8f, 0, top * .9f, 1.2f * S, col1, .35f, 0);
                bb("rays", 0, CY, 0, 18 * S, 18 * S, t * .12f, rgba(col1, .5f));
                bb("crown", 0, CY + 3 * S, 0, 5.5f * S, 3.6f * S, 0, rgba(mix(col1, 0xFFFFFF, .4f), 1f));
                emblem("scales", CY - .4f * S, 3.6f * S, 0, .7f, false);
                hang("chain", 8 * N, R * .6f, top + 2, CY, .7f * S, col1, .85f, 0);
                orbiters("diamond", 8 * N, R * .5f, CY, 1.5f * S, 1f * S, .5f, true, col2, .95f);
                sprites("diamond", 60 * N, FALLM, R, 0, top, .6f * S, col1, .9f, .15f, 20, true);
                clouds(8 * N, R, top, col2, .35f, R);
            }
            case 9 -> { // Error
                sigil(R, true);
                for (int k = 0; k < 7 * N; k++) { float a = t * (k % 2 == 0 ? .4f : -.5f) + k * 1.1f; float r = R * (.25f + .08f * k); float sz = (3f + (k % 3) * 1.6f) * S;
                    bb("cog", (float) Math.cos(a) * r, 1f * S + (k % 4) * 1.8f * S, (float) Math.sin(a) * r, sz, sz, (k % 2 == 0 ? t : -t) * (.6f + .2f * k), rgba(k % 2 == 0 ? col1 : col2, .85f)); }
                emblem("cog", CY, 4.5f * S, t * .5f, 0, true);
                for (int k = 0; k < 14 * N; k++) { float seg = (float) Math.floor(t * 3.2f + k * .37f); float vis = fract(t * 3.2f + k * .37f);
                    float a = h((int) seg + k, 41) * TAU, rr = h((int) seg + k, 42) * R; float yy = h((int) seg + k, 43) * top * .8f;
                    if (vis < .7f) bb(k % 2 == 0 ? "crack" : "shard", (float) Math.cos(a) * rr, yy, (float) Math.sin(a) * rr, 2.6f * S, 2.6f * S, h((int) seg + k, 44) * TAU, rgba(k % 3 == 0 ? col2 : col1, 1f - vis)); }
                for (int k = 0; k < 3; k++) { float jx = h((int) (t * 8) + k, 45) - .5f; bb("glow", jx * R, CY + (h((int) (t * 8) + k, 46) - .5f) * 6 * S, 0, 3f * S, 3f * S, 0, rgba(k == 0 ? col2 : col1, .5f)); }
                sprites("shard", 60 * N, DRIFTM, R, 0, top, .6f * S, col1, .8f, .5f, 21, true);
                strikes(3 * N, R, top, col2, "bolt", "bolt2");
            }
            case 10 -> { // Wheel of Fortune
                sigil(R, true);
                flat("wheel", 0, .1f, 0, 1.9f * R, t * .3f, rgba(col2, .55f));
                bb("rays", 0, top * .5f, 0, 18 * S, 18 * S, t * .15f, rgba(col1, .55f));
                bb("glow", 0, top * .5f, 0, 14 * S, 14 * S, 0, rgba(col2, .4f));
                bb("wheel", 0, top * .5f, 0, 12 * S, 12 * S, t * .4f, rgba(mix(col1, 0xFFFFFF, .4f), 1f));
                bb("wheel", 0, top * .5f, 0, 8 * S, 8 * S, -t * .6f, rgba(col2, .9f));
                emblem("diamond", top * .5f, 2.4f * S, t, 0, true);
                for (int i = 0; i < 12 * N; i++) { float a = t * .4f + i * TAU / (12 * N); float r = 5.5f * S; bb(i % 2 == 0 ? "clock" : "diamond", (float) Math.cos(a) * r, top * .5f + (float) Math.sin(a) * r, 0, 1.5f * S, 1.5f * S, 0, rgba(i % 2 == 0 ? col1 : col2, .95f)); }
                column("beam", 0, .05f, top * .5f - 6 * S, 0, 2f * S, rgba(col1, .4f));
                sprites("spark", 100 * N, RADIALM, R * 1.2f, top * .5f, 0, .5f * S, 0xFFFFFF, 1f, .3f, 22, true);
                orbiters("diamond", 8 * N, R * .6f, 1.5f * S, .6f * S, 1f * S, .6f, true, col1, .9f);
            }
            case 11 -> { // Hermit
                sigil(R, true);
                clouds(10 * N, R, top * .7f, 0xA090FF, .3f, R * 1.1f);
                for (int i = 0; i < 3; i++) column("beam", 0, 0.05f, top * .9f, 0, (6 - i * 1.6f) * S, rgba(i == 2 ? 0xFFFFFF : col1, .22f + i * .1f));
                bb("glow", 0, top * .8f, 0, 12 * S, 12 * S, 0, rgba(col1, .6f));
                emblem("lantern", top * .8f, 5f * S, 0, .4f, false);
                bb("lantern", 0, CY, 0, 3f * S, 4f * S, 0, rgba(col1, .9f));
                for (int i = 0; i < 8 * N; i++) { float a = t * .2f + i * TAU / (8 * N), r = R * .7f; float yy = 2f * S + 1.2f * S * (i % 3); bb("glow", (float) Math.cos(a) * r, yy, (float) Math.sin(a) * r, 2.6f * S, 2.6f * S, 0, rgba(col1, .6f)); bb("lantern", (float) Math.cos(a) * r, yy, (float) Math.sin(a) * r, 1f * S, 1.4f * S, 0, rgba(0xFFFFFF, .95f)); }
                sprites("star", 70 * N, SPIRALM, R, 0, top, .6f * S, col1, .95f, .15f, 23, true);
                sprites("mote", 60 * N, DRIFTM, R, .5f, top, .4f * S, 0xFFFFFF, .9f, .2f, 24, false);
                for (int i = 0; i < 10; i++) bb("fog", (float) Math.cos(i * .63f + t * .06f) * R * .6f, .8f * S, (float) Math.sin(i * .63f + t * .06f) * R * .6f, 5 * S, 2 * S, 0, rgba(col2, .3f));
            }
            case 12 -> { // Mother
                sigil(R, false);
                bb("rays", 0, CY, 0, 16 * S, 16 * S, t * .1f, rgba(col1, .45f));
                bb("glow", 0, CY, 0, 10 * S, 10 * S, 0, rgba(col1, .5f));
                for (int layer = 0; layer < 3; layer++) { int pn = 10 + layer * 2; for (int i = 0; i < pn; i++) { float a = i * TAU / pn + layer * .3f + t * (layer % 2 == 0 ? .15f : -.1f); float r = (1.2f + layer * 1.1f) * S;
                    bb("petal", (float) Math.cos(a) * r, CY + layer * .2f * S, (float) Math.sin(a) * r, 2.4f * S, 3.2f * S, a + 1.57f, rgba(layer == 1 ? col2 : col1, .8f)); } }
                bb("orb", 0, CY, 0, 2f * S, 2f * S, 0, rgba(0xFFF0A0, 1f));
                bb("wing", rx * 4f * S, CY + S, rz * 4f * S, 6f * S, 5f * S, 0, rgba(col2, .6f));
                bb("wing", -rx * 4f * S, CY + S, -rz * 4f * S, -6f * S, 5f * S, 0, rgba(col2, .6f));
                for (int i = 0; i < 6 * N; i++) { float a = i * TAU / (6 * N) + t * .1f, r = R * .75f; for (int k = 0; k < 4; k++) bb("leaf", (float) Math.cos(a) * r + (float) Math.sin(t + k) * .3f, k * 1.4f * S + .6f, (float) Math.sin(a) * r, 1.6f * S, 1.6f * S, k + a, rgba(col2, .85f)); }
                sprites("petal", 120 * N, SPIRALM, R, 0, top, .7f * S, col1, .9f, .14f, 25, true);
                sprites("leaf", 40 * N, FALLM, R, 0, top, 1f * S, col2, .85f, .12f, 26, true);
                sprites("mote", 100 * N, RISEM, R, 0, top, .3f * S, 0xFFF0A0, 1f, .25f, 27, false);
            }
            case 13 -> { // Chained
                sigil(R, false);
                hang("chain", 14 * N, R * .75f, top + 3, 0, .9f * S, col1, .9f, 0);
                hang("chain", 8 * N, R * .35f, top + 3, CY, 1.1f * S, 0xFFFFFF, .9f, .3f);
                for (int i = 0; i < 4; i++) { float yy = (1 + i * 1.8f) * S; flat("ring_thick", 0, yy, 0, (2.2f - i * .25f) * S * 2, t * (i % 2 == 0 ? .5f : -.5f), rgba(i % 2 == 0 ? col1 : col2, .6f)); }
                pillars("line", 16 * N, R * .6f, 0, 7f * S, .3f * S, col1, .7f, 0);
                emblem("chain", CY, 4.5f * S, 0, .6f, false);
                for (int i = 0; i < 6 * N; i++) { float a = t * .3f + i * TAU / (6 * N); bb("ring_thick", (float) Math.cos(a) * R * .45f, CY + (float) Math.sin(a * 2) * S, (float) Math.sin(a) * R * .45f, 1.8f * S, 1.8f * S, a, rgba(0xFFFFFF, .9f)); }
                for (int i = 0; i < 8 * N; i++) { float a = i * TAU / (8 * N); flat("crack", (float) Math.cos(a) * R * .7f, .1f, (float) Math.sin(a) * R * .7f, 3f * S, a * 2, rgba(col2, .8f)); }
                sprites("spark", 80 * N, RISEM, R, 0, top, .35f * S, col2, 1f, .35f, 28, true);
                sprites("smoke", 20 * N, RISEM, R * .6f, 0, top * .6f, 2.5f * S, col2, .25f, .1f, 29, true);
            }
            case 14 -> { // White Tower
                sigil(R, false);
                column("tower", 0, 0.05f, top * 1.1f, 0, 6f * S, rgba(0xFFFFFF, .95f));
                column("beam", 0, .05f, top * 1.4f, 0, 8f * S, rgba(col2, .35f));
                for (int i = 0; i < 9 * N; i++) { float yy = (i + 1) * top / (9 * N + 1); flat("ring_dash", 0, yy, 0, (3.6f + 2.4f * (float) Math.sin(i * .9f)) * S * (m ? 1.3f : 1f), t * (i % 2 == 0 ? .7f : -.7f), rgba(i % 2 == 0 ? col1 : col2, .8f)); }
                bb("rays", 0, top * .9f, 0, 22 * S, 22 * S, t * .1f, rgba(0xFFFFFF, .5f));
                bb("glow", 0, top * 1.0f, 0, 10 * S, 10 * S, 0, rgba(col1, .7f));
                sprites("page", 40 * N, SPIRALM, R * .9f, 0, top, 1.5f * S, 0xFFFFFF, .95f, .13f, 30, true);
                clouds(10 * N, R * 1.2f, top * .35f, 0xFFFFFF, .3f, R * 1.1f);
                clouds(8 * N, R, top * 1.05f, 0xFFFFFF, .35f, R);
                sprites("mote", 100 * N, RISEM, R * .6f, 0, top, .35f * S, col1, 1f, .3f, 31, false);
                orbiters("page", 6 * N, R * .35f, CY, 2f * S, 1.3f * S, .5f, true, 0xFFFFFF, 1f);
            }
            case 16 -> { // Paragon
                sigil(R, true);
                for (int k = 0; k < 4; k++) flat("ring_dash", 0, (1 + k * 2.2f) * S, 0, (R * 1.6f - k * 1.2f * S), t * (k % 2 == 0 ? .3f : -.4f), rgba(k % 2 == 0 ? col1 : col2, .7f));
                bb("rays", 0, CY, 0, 16 * S, 16 * S, t * .2f, rgba(col1, .5f));
                emblem("cog", CY, 5f * S, t * .3f, 0, true);
                bb("cog", 0, CY, 0, 8f * S, 8f * S, -t * .2f, rgba(col2, .45f));
                for (int k = 0; k < 6 * N; k++) { float a = t * (k % 2 == 0 ? .35f : -.45f) + k * 1.3f; float r = R * (.3f + .09f * k); float sz = (2f + (k % 3)) * S;
                    bb("cog", (float) Math.cos(a) * r, CY + ((k % 3) - 1) * 1.8f * S, (float) Math.sin(a) * r, sz, sz, (k % 2 == 0 ? t : -t) * .8f, rgba(k % 2 == 0 ? col1 : col2, .85f)); }
                for (int k = 0; k < 10 * N; k++) { float a = t * .5f + k * TAU / (10 * N); bb("clock", (float) Math.cos(a) * R * .75f, 1.5f * S + (k % 4) * 1.5f * S, (float) Math.sin(a) * R * .75f, 1.3f * S, 1.3f * S, 0, rgba(0xFFFFFF, .9f)); }
                pillars("beam", 4, R * .5f, 0, top * .7f, 1.2f * S, col2, .35f, .4f);
                sprites("spark", 100 * N, SPIRALM, R, 0, top, .4f * S, col1, 1f, .2f, 32, true);
                sprites("diamond", 30 * N, RISEM, R, 0, top, .6f * S, col2, .9f, .2f, 33, true);
            }
            case 17 -> { // Hanged Man
                flat("whirl", 0, .06f, 0, 2f * R, t * .3f, rgba(col2, .8f));
                sigil(R, false);
                bb("corona", 0, top * .85f, 0, 15 * S, 15 * S, t * .1f, rgba(col1, .8f));
                bb("disc", 0, top * .85f, 0, 6.5f * S, 6.5f * S, 0, rgba(0xFF8080, .6f));
                bb("glow", 0, top * .85f, 0, 11 * S, 11 * S, 0, rgba(col1, .5f));
                clouds(10 * N, R, top, col2, .6f, R * 1.1f);
                hang("chain", 12 * N, R * .7f, top + 3, 0, .8f * S, mix(col1, 0xFFFFFF, .3f), .9f, 0);
                for (int i = 0; i < 8 * N; i++) { float a = i * TAU / (8 * N) + t * .08f, r = R * .6f * (.7f + .3f * (i % 2)); float yy = top * (.35f + .25f * h(i, 51)) + (float) Math.sin(t + i) * .4f;
                    bb("cross", (float) Math.cos(a) * r, yy, (float) Math.sin(a) * r, 2.2f * S, 3.4f * S, 3.14159f, rgba(col1, .95f)); }
                emblem("cross", CY, 4.5f * S, 0, .5f, false);
                bb("cross", 0, CY, 0, 4f * S, 6f * S, 3.14159f, rgba(0xFFD0D0, .4f));
                sprites("drop", 140 * N, FALLM, R, 0, top, .6f * S, col1, .95f, .45f, 34, false);
                for (int i = 0; i < 6; i++) { float pw = fract(t * .5f + i / 6f); flat("ring", (h(i, 61) - .5f) * R, .1f, (h(i, 62) - .5f) * R, pw * 3f * S, 0, rgba(col1, (1 - pw) * .8f)); }
                for (int i = 0; i < 10; i++) bb("fog", (float) Math.cos(i * .63f + t * .07f) * R * .6f, .8f * S, (float) Math.sin(i * .63f + t * .07f) * R * .6f, 5 * S, 2 * S, 0, rgba(col1, .22f));
            }
            case 18 -> { // Fool
                sigil(R, true);
                for (int k = 0; k < 3; k++) bb("rays", 0, CY, 0, (14 + k * 4) * S, (14 + k * 4) * S, t * .2f * (k % 2 == 0 ? 1 : -1), rgba(hsv(t * .05f + k * .3f, .6f), .5f));
                for (int k = 0; k < 6; k++) flat("ring_thick", 0, .1f + k * .02f, 0, (1.8f + k * .3f) * R * .6f, t * .2f * (k % 2 == 0 ? 1 : -1), rgba(hsv(t * .1f + k / 6f, .7f), .6f));
                bb("glow", 0, CY, 0, 10 * S, 10 * S, 0, rgba(0xFFFFFF, .5f));
                emblem("star", CY, 5f * S, t * .4f, 0, true);
                bb("crown", 0, CY + 3.6f * S, 0, 4f * S, 2.6f * S, 0, rgba(0xFFF0A0, 1f));
                for (int i = 0; i < 12 * N; i++) { float a = t * .3f + i * TAU / (12 * N), r = R * .7f; bb("page", (float) Math.cos(a) * r, CY + (float) Math.sin(a * 2 + i) * 2 * S, (float) Math.sin(a) * r, 1.8f * S, 2.6f * S, a * 2, rgba(hsv(i / 12f + t * .05f, .5f), .95f)); }
                for (int i = 0; i < 140 * N; i++) { float pq = fract(t * .25f + h(i, 3)); float ang = h(i, 2) * TAU, el = (h(i, 5) - .5f) * 2.6f, d = pq * R * 1.3f;
                    bb(i % 3 == 0 ? "star" : "spark", (float) Math.cos(ang) * d * (float) Math.cos(el), CY + (float) Math.sin(el) * d, (float) Math.sin(ang) * d * (float) Math.cos(el), .6f * S, .6f * S, t + i, rgba(hsv(h(i, 9) + t * .1f, .6f), (float) Math.sin(pq * Math.PI))); }
                column("beam", 0, .05f, top, 0, 3f * S, rgba(hsv(t * .1f, .5f), .4f));
            }
            case 19 -> { // Darkness
                sigil(R, false);
                for (int i = 0; i < 160 * N; i++) { float a = h(i, 1) * TAU, el = h(i, 2) * 1.3f + .15f; float d = R * 1.8f;
                    bb("star", (float) Math.cos(a) * d * (float) Math.cos(el), (float) Math.sin(el) * d * .9f + 1, (float) Math.sin(a) * d * (float) Math.cos(el), (.4f + h(i, 3)) * S, (.4f + h(i, 3)) * S, t * .3f + i, rgba(i % 4 == 0 ? col1 : 0xFFFFFF, .4f + .6f * Math.abs((float) Math.sin(t * (.6f + h(i, 4)) + i)))); }
                bb("glow", 0, top * .75f, 0, 16 * S, 16 * S, 0, rgba(col1, .35f));
                bb("crescent", 0, top * .75f, 0, 9 * S, 9 * S, -.4f, rgba(mix(col1, 0xFFFFFF, .5f), 1f));
                column("beam", 0, .05f, top * .7f, 0, 3f * S, rgba(col1, .3f));
                emblem("crescent", CY, 3.6f * S, 0, .7f, false);
                clouds(12 * N, R * 1.2f, top * .45f, col2, .55f, R * 1.3f);
                for (int i = 0; i < 12; i++) bb("fog", (float) Math.cos(i * .52f + t * .05f) * R * .7f, .8f * S, (float) Math.sin(i * .52f + t * .05f) * R * .7f, 6 * S, 2.4f * S, 0, rgba(col1, .3f));
                orbiters("orb", 8 * N, R * .5f, CY, 2f * S, 1.2f * S, .3f, false, col1, .8f);
                sprites("mote", 100 * N, DRIFTM, R * 1.2f, .5f, top, .45f * S, col1, .9f, .2f, 35, false);
                sprites("star", 30 * N, FALLM, R * 1.3f, 0, top * 1.4f, .8f * S, 0xFFFFFF, .9f, .35f, 36, true);
            }
            case 20 -> { // Visionary
                sigil(R, true);
                bb("whirl", 0, CY, 0, 14 * S, 14 * S, t * .5f, rgba(col2, .6f));
                bb("rays", 0, CY, 0, 18 * S, 18 * S, -t * .12f, rgba(col1, .45f));
                bb("glow", 0, CY, 0, 9 * S, 9 * S, 0, rgba(col1, .5f));
                bb("ring_thick", 0, CY, 0, 8 * S, 8 * S, t * .4f, rgba(col1, .9f)); bb("ring_dash", 0, CY, 0, 6.4f * S, 6.4f * S, -t * .6f, rgba(col2, .9f));
                bb("eye", 0, CY, 0, 6f * S, 3.2f * S * Math.max(.2f, Math.abs((float) Math.cos(t * .45f))), 0, rgba(0xFFFFFF, 1f));
                bb("orb", 0, CY, 0, 1.5f * S, 1.5f * S, 0, rgba(col2, 1f));
                for (int i = 0; i < 14 * N; i++) { float a = t * (i % 2 == 0 ? .25f : -.3f) + i * TAU / (14 * N); float r = R * (.45f + .3f * (i % 2)); float bl = Math.max(.12f, Math.abs((float) Math.cos(t * .7f + i * 1.3f)));
                    float yy = 1.5f * S + h(i, 7) * 7 * S; bb("glow", (float) Math.cos(a) * r, yy, (float) Math.sin(a) * r, 2.4f * S, 2.4f * S, 0, rgba(i % 2 == 0 ? col1 : col2, .5f)); bb("eye", (float) Math.cos(a) * r, yy, (float) Math.sin(a) * r, 2f * S, 1.1f * S * bl, 0, rgba(0xFFFFFF, .95f)); }
                column("beam", 0, .05f, CY - 2 * S, 0, 1.6f * S, rgba(col1, .5f));
                sprites("mote", 90 * N, SPIRALM, R, 0, top, .4f * S, col1, 1f, .18f, 37, false);
                sprites("ring", 20 * N, RISEM, R * .5f, 0, top, 1.6f * S, col2, .7f, .15f, 38, true);
            }
            case 21 -> { // Twilight Giant
                sigil(R, true);
                for (int k = 0; k < 3; k++) bb("rays", 0, CY, 0, (16 + k * 5) * S, (16 + k * 5) * S, t * .1f * (k % 2 == 0 ? 1 : -1), rgba(k == 1 ? col2 : col1, .5f));
                bb("glow", 0, top * .35f, 0, 16 * S, 16 * S, 0, rgba(col1, .45f));
                bb("wing", rx * 4.2f * S, CY + 2 * S, rz * 4.2f * S, 8f * S, 7f * S, 0, rgba(col1, .7f));
                bb("wing", -rx * 4.2f * S, CY + 2 * S, -rz * 4.2f * S, -8f * S, 7f * S, 0, rgba(col1, .7f));
                bb("glow", 0, CY, 0, 10 * S, 10 * S, 0, rgba(col2, .5f));
                bb("sword", 0, CY, 0, 3.6f * S, 11f * S, 0, rgba(mix(col1, 0xFFFFFF, .5f), 1f));
                bb("sword", 0, CY, 0, 5f * S, 14f * S, 0, rgba(col1, .25f));
                for (int i = 0; i < 16 * N; i++) { float a = i * TAU / (16 * N) + .1f; flat("crack", (float) Math.cos(a) * R * .55f, .1f, (float) Math.sin(a) * R * .55f, 3.5f * S, a, rgba(col1, .8f)); }
                pillars("flame", 10 * N, R * .8f, 0, 6f * S, 2.4f * S, col1, .85f, 0);
                orbiters("shard", 10 * N, R * .5f, CY, 2f * S, 1.4f * S, .7f, true, col2, .9f);
                sprites("flame", 60 * N, RISEM, R, 0, top, 1.2f * S, col1, .85f, .25f, 39, false);
                sprites("shard", 60 * N, RISEM, R, 0, top, .7f * S, col2, .9f, .3f, 40, true);
                sprites("spark", 120 * N, RISEM, R * 1.1f, 0, top * 1.2f, .3f * S, 0xFFFFFF, 1f, .5f, 41, true);
            }
            case 15 -> { sailor(m); return true; }
            default -> { return false; }
        }
        mythExtra(m);
        return true;
    }
}
