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
        if (curPath == 15) { sailor(myth); return; }
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
}
