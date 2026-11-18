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
            pose.pushPose(); UniqGfx.tr(pose, 0, -4.2f, -5.3f); UniqGfx.box(pose, buf, 7.4f, 7.6f, .7f, trim); pose.popPose();      // the mask
            pose.pushPose(); UniqGfx.tr(pose, 0, -4.2f, -5.75f); UniqGfx.box(pose, buf, 6.2f, 6.4f, .4f, shade(trim, .86f)); pose.popPose();
            pose.pushPose(); UniqGfx.tr(pose, -1.8f, -5.6f, -5.95f); UniqGfx.box(pose, buf, 2f, 1f, .3f, deep); pose.popPose();     // two slits for the eyes
            pose.pushPose(); UniqGfx.tr(pose, 1.8f, -5.6f, -5.95f); UniqGfx.box(pose, buf, 2f, 1f, .3f, deep); pose.popPose();
            pose.pushPose(); UniqGfx.tr(pose, 0, -2.6f, -5.95f); UniqGfx.box(pose, buf, .8f, 2.2f, .3f, deep); pose.popPose();      // and one down the mouth
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
