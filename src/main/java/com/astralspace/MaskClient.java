package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client side of the masks: who is behind one, and the hooded robe drawn over them.
 *
 * <p>Wearing one puts you in a default skin &mdash; Steve or Alex, whichever you picked &mdash; rather than a
 * drawn-on suit and a blank face. The whole vanilla player model is drawn again over you in that texture, a
 * fraction outside your own so no stripe of your skin shows at any seam, overlay layers (hat, jacket, sleeves)
 * included. Behind a mask you are not meant to be placeable, and the two faces everybody already knows say
 * that better than an invented one.</p>
 */
public final class MaskClient {
    private MaskClient() { }

    private static final Map<UUID, Integer> COLOUR = new HashMap<>();
    private static final Map<UUID, String> NAME = new HashMap<>();

    private static final java.util.Set<UUID> SLIM = new java.util.HashSet<>();

    public static void set(UUID id, int colour, String name, boolean slim) {
        if (colour < 0) { COLOUR.remove(id); NAME.remove(id); SLIM.remove(id); }
        else {
            COLOUR.put(id, colour); NAME.put(id, name);
            if (slim) SLIM.add(id); else SLIM.remove(id);
        }
    }

    /** Alex rather than Steve. */
    static boolean slim(UUID id) { return SLIM.contains(id); }

    public static boolean masked(UUID id) { return COLOUR.containsKey(id); }

    private static int cloth(UUID id) {
        Integer c = COLOUR.get(id);
        return Masks.CLOTH[Math.max(0, Math.min(Masks.CLOTH.length - 1, c == null ? 0 : c))];
    }

    private static int trim(UUID id) {
        Integer c = COLOUR.get(id);
        return Masks.TRIM[Math.max(0, Math.min(Masks.TRIM.length - 1, c == null ? 0 : c))];
    }

    /** The name over the head is the mask's, not the account's. */
    public static void nameTag(RenderNameTagEvent e) {
        if (!(e.getEntity() instanceof AbstractClientPlayer p)) return;
        String n = NAME.get(p.getUUID());
        if (n == null) return;
        e.setContent(Component.literal(n).withColor(trim(p.getUUID()) & 0xFFFFFF));
    }

    /** The default skin over the whole body, and the mask itself over its eyes. */
    public static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private static final ResourceLocation STEVE =
                ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
        private static final ResourceLocation ALEX =
                ResourceLocation.withDefaultNamespace("textures/entity/player/slim/alex.png");
        /** How far outside the player's own model the borrowed one is drawn. */
        private static final float OUT = 1.04f;

        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet set) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buf, int light, AbstractClientPlayer p, float limb, float limbAmt, float pt, float age, float headYaw, float headPitch) {
            if (!COLOUR.containsKey(p.getUUID()) || p.isInvisible() || p.isSpectator()) return;
            int trim = trim(p.getUUID());
            int was = UniqGfx.light;
            UniqGfx.light = light;
            PlayerModel<AbstractClientPlayer> m = getParentModel();

            // Their skin becomes the default one.
            //
            // Drawn on the player's OWN model rather than a second one baked here: a separately
            // baked model never appeared in game, and the parent is already posed for this frame,
            // so re-rendering it in the default texture a fraction larger is both simpler and
            // certain to line up. It carries the hat and jacket layers with it, so the result is
            // the full 3D skin and not a flat repaint.
            boolean sl = slim(p.getUUID());
            m.setAllVisible(true);
            pose.pushPose();
            pose.scale(OUT, OUT, OUT);
            pose.translate(0, -1.5f * (OUT - 1f) / OUT, 0);   // the scale is about the neck; put the feet back down
            m.renderToBuffer(pose, buf.getBuffer(RenderType.entityTranslucent(sl ? ALEX : STEVE)),
                             light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            pose.popPose();

            // and the mask itself, over the eyes of it
            pose.pushPose();
            m.head.translateAndRotate(pose);
            masquerade(pose, buf, trim);
            pose.popPose();

            UniqGfx.light = was;
        }

        /**
         * The mask itself: a Venetian masquerade mask in cut lace, not a plate with slits in it.
         * Eyes only, as those are worn -- two scrolled lobes joined over the bridge, a fleur
         * standing off the brow, points swept back past the temples, and stones set along the top.
         * Head space: the cube runs y -8..0 and z -4..4, with the face at -z, so up is -y.
         */
        private static void masquerade(PoseStack p, MultiBufferSource b, int trim) {
            final int lace = 0xFF0C0B11, gem = 0xFFEEF2FF;
            final float zL = -5.35f, zA = -5.52f, zG = -5.66f;   // lace, glitter, stones
            for (int sd = -1; sd <= 1; sd += 2) {
                float ex = sd * 2.0f, ey = -5.55f;
                arc(p, b, ex, ey, zL, 2.15f, 1.6f, 0, TAU, 14, .78f, lace);     // round the eye
                arc(p, b, ex, ey, zA, 1.7f, 1.2f, 0, TAU, 12, .34f, trim);
                // the lobe swept out over the temple, and the scrolls on it
                arc(p, b, ex * .55f, -5.3f, zL, sd * 4.6f, 2.5f, -.35f, 1.5f, 10, .72f, lace);
                curl(p, b, sd * 5.0f, -6.5f, zL, 1.5f, sd > 0 ? 2.1f : 1.0f, .62f, sd, .6f, lace);
                curl(p, b, sd * 5.3f, -4.3f, zL, 1.25f, sd > 0 ? 3.6f : -.5f, .55f, -sd, .5f, trim);
                curl(p, b, sd * 3.3f, -3.6f, zL, 1.15f, sd > 0 ? 3.2f : -.1f, .5f, sd, .46f, lace);
                p.pushPose(); UniqGfx.tr(p, sd * 6.4f, -5.6f, zL); UniqGfx.box(p, b, 1.5f, .5f, .5f, lace); p.popPose();
            }
            // the brow, and the stones set along it
            arc(p, b, 0, -3.6f, zL, 5.5f, 3.3f, PI * 1.07f, PI * 1.93f, 16, .72f, lace);
            for (int i = 0; i < 13; i++) {
                float a = PI * 1.1f + (PI * .8f) * i / 12f;
                if (Math.abs(i - 6) < 2) continue;                 // the fleur stands where these would be
                p.pushPose();
                UniqGfx.tr(p, (float) Math.cos(a) * 5.5f, -3.6f + (float) Math.sin(a) * 3.3f, zG);
                UniqGfx.box(p, b, .62f, .62f, .5f, gem);
                p.popPose();
            }
            // the bridge
            UniqGfx.seg(p, b, 0, -6.3f, zL, 0, -4.1f, zL, .62f, lace);
            p.pushPose(); UniqGfx.tr(p, 0, -5.2f, zA); UniqGfx.box(p, b, 1.1f, 1.1f, .45f, trim); p.popPose();
            // the fleur off the brow
            arc(p, b, 0, -8.5f, zL, 1.05f, 1.45f, 0, TAU, 12, .6f, lace);
            p.pushPose(); UniqGfx.tr(p, 0, -8.5f, zA); UniqGfx.box(p, b, 1.1f, 1.7f, .4f, trim); p.popPose();
            UniqGfx.seg(p, b, 0, -9.9f, zL, 0, -11.1f, zL, .5f, lace);
            for (int sd = -1; sd <= 1; sd += 2)
                curl(p, b, sd * 1.7f, -8.2f, zL, 1.05f, sd > 0 ? 2.6f : .5f, .55f, sd, .46f, trim);
        }

        /** A run of lace following an ellipse. */
        private static void arc(PoseStack p, MultiBufferSource b, float cx, float cy, float z,
                                float rx, float ry, float a0, float a1, int n, float w, int argb) {
            float px = 0, py = 0;
            for (int i = 0; i <= n; i++) {
                float a = a0 + (a1 - a0) * i / n;
                float x = cx + (float) Math.cos(a) * rx, y = cy + (float) Math.sin(a) * ry;
                if (i > 0) UniqGfx.seg(p, b, px, py, z, x, y, z, w, argb);
                px = x; py = y;
            }
        }

        /** A scroll: lace winding inward on itself, thinning as it goes, the way cut lace curls. */
        private static void curl(PoseStack p, MultiBufferSource b, float cx, float cy, float z,
                                 float r0, float a0, float turns, float dir, float w, int argb) {
            int n = 12; float px = 0, py = 0;
            for (int i = 0; i <= n; i++) {
                float u = i / (float) n, a = a0 + dir * u * turns * TAU, r = r0 * (1f - .76f * u);
                float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
                if (i > 0) UniqGfx.seg(p, b, px, py, z, x, y, z, Math.max(.2f, w * (1f - .5f * u)), argb);
                px = x; py = y;
            }
        }

        private static final float PI = (float) Math.PI, TAU = (float) (Math.PI * 2);


        private static final int SKIN = 0xFFC9A086, HAIR = 0xFF2B2521, MOUTH = 0xFF7A5247,
                                 SHIRT = 0xFFF2F2F2, SHOE = 0xFF141318,
                                 BROW = 0xFF241E1A, EYE_W = 0xFFEDE6DE, EYE = 0xFF3A2A22;

        private static int shade(int argb, float k) {
            int a = argb >>> 24, r = (int) (((argb >> 16) & 0xFF) * k), g = (int) (((argb >> 8) & 0xFF) * k), b = (int) ((argb & 0xFF) * k);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
}
