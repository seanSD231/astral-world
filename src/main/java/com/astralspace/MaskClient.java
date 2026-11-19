package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
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
 * <p>The skin is not swapped (that would need the player's own texture rebuilt); instead an opaque suit is drawn a
 * fraction outside every part of the body, so nothing of the skin underneath shows through, overlay layers
 * (jacket, sleeves, hat) included. The face is the same blank one on everybody, which is the point: behind a
 * mask you are not meant to be placeable, and a stranger's own face showing under it would give them away.</p>
 */
public final class MaskClient {
    private MaskClient() { }

    private static final Map<UUID, Integer> COLOUR = new HashMap<>();
    private static final Map<UUID, String> NAME = new HashMap<>();

    public static void set(UUID id, int colour, String name) {
        if (colour < 0) { COLOUR.remove(id); NAME.remove(id); }
        else { COLOUR.put(id, colour); NAME.put(id, name); }
    }

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

    /** A business suit over the whole body, a face nobody can place, and the mask itself over its eyes. */
    public static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buf, int light, AbstractClientPlayer p, float limb, float limbAmt, float pt, float age, float headYaw, float headPitch) {
            if (!COLOUR.containsKey(p.getUUID()) || p.isInvisible() || p.isSpectator()) return;
            int cloth = cloth(p.getUUID()), trim = trim(p.getUUID());
            int shade = shade(cloth, .74f), deep = shade(cloth, .52f);
            int was = UniqGfx.light;
            UniqGfx.light = light;
            PlayerModel<AbstractClientPlayer> m = getParentModel();

            // The face and the mask on it. Head space: the cube runs y -8..0, z -4..4, face at -z.
            pose.pushPose();
            m.head.translateAndRotate(pose);
            pose.pushPose(); UniqGfx.tr(pose, 0, -4f, 0); UniqGfx.box(pose, buf, 9.1f, 9.1f, 9.1f, SKIN); pose.popPose();
            pose.pushPose(); UniqGfx.tr(pose, 0, -7.4f, .2f); UniqGfx.box(pose, buf, 9.4f, 2.6f, 9.4f, HAIR); pose.popPose();        // hair: a cap,
            pose.pushPose(); UniqGfx.tr(pose, 0, -4.4f, 3.7f); UniqGfx.box(pose, buf, 9.4f, 7.6f, 2.2f, HAIR); pose.popPose();       // the back of the head,
            for (int sd = -1; sd <= 1; sd += 2) { pose.pushPose(); UniqGfx.tr(pose, sd * 4.3f, -5.2f, 1.2f); UniqGfx.box(pose, buf, 1.2f, 6f, 6.6f, HAIR); pose.popPose(); }
            pose.pushPose(); UniqGfx.tr(pose, 0, -2.2f, -4.7f); UniqGfx.box(pose, buf, 2.4f, .55f, .45f, MOUTH); pose.popPose();     // and a mouth, which is all the face it gets
            masquerade(pose, buf, trim);
            pose.popPose();

            // The suit: jacket, shirt and tie.
            pose.pushPose();
            m.body.translateAndRotate(pose);
            pose.pushPose(); UniqGfx.tr(pose, 0, 6f, 0); UniqGfx.box(pose, buf, 9.3f, 13.2f, 5.3f, cloth); pose.popPose();
            pose.pushPose(); UniqGfx.tr(pose, 0, 1.3f, 0); UniqGfx.box(pose, buf, 10.1f, 2.4f, 6.1f, shade); pose.popPose();         // the shoulders
            pose.pushPose(); UniqGfx.tr(pose, 0, 4.4f, -2.8f); UniqGfx.box(pose, buf, 3.2f, 7.4f, .5f, SHIRT); pose.popPose();       // the shirt, open between the lapels
            for (int sd = -1; sd <= 1; sd += 2) {   // the lapels, narrowing to the button
                for (int k = 0; k < 5; k++) {
                    float u = k / 4f;
                    pose.pushPose();
                    UniqGfx.tr(pose, sd * (2.9f - u * 1.0f), 1.9f + u * 5.4f, -2.85f);
                    UniqGfx.box(pose, buf, 2.4f - u * .7f, 1.7f, .55f, shade);
                    pose.popPose();
                }
            }
            pose.pushPose(); UniqGfx.tr(pose, 0, 1.4f, -2.7f); UniqGfx.box(pose, buf, 5.2f, 1.5f, 1.2f, SHIRT); pose.popPose();      // the collar
            pose.pushPose(); UniqGfx.tr(pose, 0, 2.5f, -3.15f); UniqGfx.box(pose, buf, 1.8f, 1.6f, .5f, trim); pose.popPose();       // the knot
            pose.pushPose(); UniqGfx.tr(pose, 0, 6.2f, -3.1f); UniqGfx.box(pose, buf, 1.3f, 5.8f, .45f, trim); pose.popPose();       // and the tie
            pose.pushPose(); UniqGfx.tr(pose, 0, 9.4f, -3.05f); UniqGfx.box(pose, buf, .9f, 1.4f, .4f, shade(trim, .8f)); pose.popPose();
            pose.pushPose(); UniqGfx.tr(pose, -1.1f, 8.2f, -2.9f); UniqGfx.box(pose, buf, .7f, .7f, .5f, shade(trim, .9f)); pose.popPose();   // the button
            pose.popPose();

            sleeve(pose, buf, m.rightArm, -1f, cloth, shade);
            sleeve(pose, buf, m.leftArm, 1f, cloth, shade);
            skirt(pose, buf, m.rightLeg, cloth, shade);
            skirt(pose, buf, m.leftLeg, cloth, shade);

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

        /** A jacket sleeve with an inch of shirt cuff showing at the wrist, as a suit does. */
        private void sleeve(PoseStack pose, MultiBufferSource buf, net.minecraft.client.model.geom.ModelPart arm, float side, int cloth, int shade) {
            pose.pushPose();
            arm.translateAndRotate(pose);
            pose.pushPose(); UniqGfx.tr(pose, side * .2f, 3.4f, 0); UniqGfx.box(pose, buf, 4.9f, 11.6f, 4.9f, cloth); pose.popPose();
            pose.pushPose(); UniqGfx.tr(pose, side * .2f, 1f, 0); UniqGfx.box(pose, buf, 5.3f, 2.6f, 5.3f, shade); pose.popPose();   // the shoulder of it
            pose.pushPose(); UniqGfx.tr(pose, side * .2f, 9.6f, 0); UniqGfx.box(pose, buf, 4.7f, 1.5f, 4.7f, SHIRT); pose.popPose(); // the cuff
            pose.pushPose(); UniqGfx.tr(pose, side * .2f, 11.2f, 0); UniqGfx.box(pose, buf, 4.4f, 2.4f, 4.4f, SKIN); pose.popPose(); // and the hand
            pose.popPose();
        }

        /** A trouser leg with a turn-up, and a polished shoe under it. */
        private void skirt(PoseStack pose, MultiBufferSource buf, net.minecraft.client.model.geom.ModelPart leg, int cloth, int shade) {
            pose.pushPose();
            leg.translateAndRotate(pose);
            pose.pushPose(); UniqGfx.tr(pose, 0, 5f, 0); UniqGfx.box(pose, buf, 5f, 10.6f, 5f, cloth); pose.popPose();
            pose.pushPose(); UniqGfx.tr(pose, 0, 10f, 0); UniqGfx.box(pose, buf, 5.2f, 1.4f, 5.2f, shade); pose.popPose();          // the turn-up
            pose.pushPose(); UniqGfx.tr(pose, 0, 11.4f, -.9f); UniqGfx.box(pose, buf, 5.1f, 2.1f, 6.6f, SHOE); pose.popPose();      // the shoe
            pose.popPose();
        }

        private static final int SKIN = 0xFFC9A086, HAIR = 0xFF2B2521, MOUTH = 0xFF7A5247,
                                 SHIRT = 0xFFF2F2F2, SHOE = 0xFF141318;

        private static int shade(int argb, float k) {
            int a = argb >>> 24, r = (int) (((argb >> 16) & 0xFF) * k), g = (int) (((argb >> 8) & 0xFF) * k), b = (int) ((argb & 0xFF) * k);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
}
