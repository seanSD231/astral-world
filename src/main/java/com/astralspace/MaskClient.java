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
 * <p>The skin is not swapped (that would need the player's own texture rebuilt); instead an opaque robe is drawn a
 * fraction outside every part of the body, so nothing of the skin underneath shows through, overlay layers
 * (jacket, sleeves, hat) included.</p>
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

    /** A hooded robe over the whole body, in the mask's colour, with the mask itself on the face. */
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

            // The hood, and the mask on the face. Head space: the cube runs y -8..0, z -4..4, with the face at -z.
            pose.pushPose();
            m.head.translateAndRotate(pose);
            pose.pushPose(); UniqGfx.tr(pose, 0, -4f, .4f); UniqGfx.box(pose, buf, 9.8f, 9.8f, 10.4f, cloth); pose.popPose();
            pose.pushPose(); UniqGfx.tr(pose, 0, -8.4f, .6f); UniqGfx.box(pose, buf, 10.4f, 1.6f, 10.8f, shade); pose.popPose();   // the crown of the hood
            pose.pushPose(); UniqGfx.tr(pose, 0, -1.4f, .6f); UniqGfx.box(pose, buf, 10.6f, 2.4f, 11f, shade); pose.popPose();      // the brim, standing out over the face
            masquerade(pose, buf, trim);
            pose.popPose();

            // The robe: body, mantle over the shoulders, sleeves and skirts.
            pose.pushPose();
            m.body.translateAndRotate(pose);
            pose.pushPose(); UniqGfx.tr(pose, 0, 6f, 0); UniqGfx.box(pose, buf, 9.4f, 13.2f, 5.4f, cloth); pose.popPose();
            pose.pushPose(); UniqGfx.tr(pose, 0, 1.6f, 0); UniqGfx.box(pose, buf, 11.6f, 3.4f, 7f, shade); pose.popPose();          // the mantle
            pose.pushPose(); UniqGfx.tr(pose, 0, 2.2f, -2.9f); UniqGfx.box(pose, buf, 2.2f, 10f, .6f, trim); pose.popPose();        // the band down the front
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

        private void sleeve(PoseStack pose, MultiBufferSource buf, net.minecraft.client.model.geom.ModelPart arm, float side, int cloth, int shade) {
            pose.pushPose();
            arm.translateAndRotate(pose);
            pose.pushPose(); UniqGfx.tr(pose, side, 4f, 0); UniqGfx.box(pose, buf, 5.4f, 13f, 5.4f, cloth); pose.popPose();
            pose.pushPose(); UniqGfx.tr(pose, side, 8.6f, 0); UniqGfx.box(pose, buf, 6.4f, 3.4f, 6.4f, shade); pose.popPose();      // the cuff
            pose.popPose();
        }

        private void skirt(PoseStack pose, MultiBufferSource buf, net.minecraft.client.model.geom.ModelPart leg, int cloth, int shade) {
            pose.pushPose();
            leg.translateAndRotate(pose);
            pose.pushPose(); UniqGfx.tr(pose, 0, 5.4f, 0); UniqGfx.box(pose, buf, 5.4f, 11.6f, 5.4f, cloth); pose.popPose();
            pose.pushPose(); UniqGfx.tr(pose, 0, 11.4f, 0); UniqGfx.box(pose, buf, 6f, 2.4f, 6f, shade); pose.popPose();            // the hem
            pose.popPose();
        }

        private static int shade(int argb, float k) {
            int a = argb >>> 24, r = (int) (((argb >> 16) & 0xFF) * k), g = (int) (((argb >> 8) & 0xFF) * k), b = (int) ((argb & 0xFF) * k);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
}
