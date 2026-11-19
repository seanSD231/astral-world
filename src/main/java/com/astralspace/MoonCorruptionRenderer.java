package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

/**
 * Drawing the Moon's Corruption.
 *
 * <p>Built out of the same primitives the rest of the mod draws with rather than a model file, so
 * it can change shape as the fight turns: it goes from something with a back and arms to something
 * hunched and open, and the moon over its shoulder comes up with it. Nothing is skinned -- it is
 * all boxes and tapers with the seams hidden in shadow, which at this size reads better than a
 * low-poly mesh would.</p>
 */
public final class MoonCorruptionRenderer extends EntityRenderer<MoonCorruption> {
    private static final ResourceLocation TEX = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private static final int HIDE = 0xFF1B1016, HIDE2 = 0xFF2A1620, SINEW = 0xFF5E1A24,
                             GLOW = 0xFFE0384A, PALE = 0xFFF7C6CE, BONE = 0xFFD8CFC4;

    public MoonCorruptionRenderer(EntityRendererProvider.Context ctx) { super(ctx); shadowRadius = 1.6f; }

    @Override
    public ResourceLocation getTextureLocation(MoonCorruption e) { return TEX; }

    @Override
    public void render(MoonCorruption e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        int was = UniqGfx.light;
        UniqGfx.light = light;
        float t = e.tickCount + pt;
        int ph = e.phase();
        float moon = e.moon();
        float act = e.act() / 40f;

        pose.pushPose();
        pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(180 - yaw)));

        // as its moon rises it stops standing up straight
        float hunch = .35f * moon, lift = 1f - .12f * moon;
        float breathe = (float) Math.sin(t * .12) * .04f + act * .12f;

        pose.pushPose();
        pose.scale(1f, lift, 1f);
        tr(pose, 0, 1.55f + breathe, 0);
        pose.mulPose(new Quaternionf().rotateX(hunch));

        body(pose, buf, ph, t, act);
        head(pose, buf, ph, t, act, moon);
        for (int sd = -1; sd <= 1; sd += 2) {
            arm(pose, buf, sd, ph, t, act);
            leg(pose, buf, sd, t);
        }
        pose.popPose();

        if (moon > .01f) halo(pose, buf, t, moon, ph);

        pose.popPose();
        UniqGfx.light = was;
        super.render(e, yaw, pt, pose, buf, light);
    }

    // ---- the thing itself ----

    private void body(PoseStack p, MultiBufferSource b, int ph, float t, float act) {
        p.pushPose(); tr(p, 0, 0, 0);
        UniqGfx.box(p, b, 17f, 20f, 12f, HIDE);
        p.popPose();
        p.pushPose(); tr(p, 0, -9f, 0);
        UniqGfx.box(p, b, 20f, 6f, 13f, HIDE2);                        // the shoulders
        p.popPose();
        p.pushPose(); tr(p, 0, 7f, -1f);
        UniqGfx.box(p, b, 13f, 8f, 10f, HIDE2);                        // and the waist
        p.popPose();
        // ribs that have come through, and the light behind them
        for (int i = 0; i < 4; i++) {
            float y = -5f + i * 4.2f, w = 15f - i * 1.4f;
            p.pushPose(); tr(p, 0, y, -6.2f);
            UniqGfx.box(p, b, w, 1.5f, .9f, BONE);
            p.popPose();
        }
        float pulse = .5f + .5f * (float) Math.sin(t * .18);
        p.pushPose(); tr(p, 0, 1f, -6.6f);
        UniqGfx.box(p, b, 5.5f + pulse + act * 3f, 7f + pulse, .7f, ph >= 2 ? GLOW : SINEW);
        p.popPose();
        p.pushPose(); tr(p, 0, 1f, -6.9f);
        UniqGfx.box(p, b, 2.6f + pulse, 3.4f + pulse, .5f, PALE);
        p.popPose();
        // the spine, which goes on growing
        for (int i = 0; i < 6; i++) {
            float y = -8f + i * 3.4f, s = (ph >= 1 ? 2.6f : 1.6f) * (1f - i * .09f);
            p.pushPose();
            tr(p, 0, y, 6.4f);
            p.mulPose(new Quaternionf().rotateX(-.5f - i * .08f + (float) Math.sin(t * .09 + i) * .05f));
            UniqGfx.box(p, b, 1.2f, s * 2.6f, s, i % 2 == 0 ? BONE : HIDE2);
            p.popPose();
        }
    }

    private void head(PoseStack p, MultiBufferSource b, int ph, float t, float act, float moon) {
        p.pushPose();
        tr(p, 0, -13.5f, -1.5f);
        p.mulPose(new Quaternionf().rotateX(.25f + act * .5f));         // it holds its head low
        UniqGfx.box(p, b, 10f, 8f, 11f, HIDE2);
        p.pushPose(); tr(p, 0, 1.5f, -6.5f);
        UniqGfx.box(p, b, 7f, 5f, 4f, HIDE);                            // the muzzle
        p.popPose();
        // it opens when it is about to do something
        float gape = act * 3.2f;
        p.pushPose(); tr(p, 0, 3.6f + gape, -6.6f);
        UniqGfx.box(p, b, 6.4f, 1.2f + gape, 3.6f, GLOW);
        p.popPose();
        for (int sd = -1; sd <= 1; sd += 2) {
            p.pushPose(); tr(p, sd * 2.6f, -1.2f, -5.2f);
            UniqGfx.box(p, b, 2.4f, 1.4f, .6f, ph >= 2 ? PALE : GLOW);  // the eyes
            p.popPose();
            // horns, which come up with the moon
            p.pushPose();
            tr(p, sd * 3.6f, -4.2f, 1.5f);
            p.mulPose(new Quaternionf().rotateZ(sd * (.5f + moon * .35f)).rotateX(-.35f));
            UniqGfx.box(p, b, 1.6f, 8f + moon * 5f, 1.6f, BONE);
            p.pushPose(); tr(p, 0, 5f + moon * 3f, 0);
            p.mulPose(new Quaternionf().rotateZ(sd * .5f));
            UniqGfx.box(p, b, 1.1f, 5f + moon * 4f, 1.1f, BONE);
            p.popPose();
            p.popPose();
            // teeth
            for (int k = 0; k < 4; k++) {
                p.pushPose(); tr(p, sd * (.9f + k * .9f), 2.6f, -7.4f);
                UniqGfx.box(p, b, .7f, 1.8f - k * .25f, .6f, BONE);
                p.popPose();
            }
        }
        p.popPose();
    }

    private void arm(PoseStack p, MultiBufferSource b, int sd, int ph, float t, float act) {
        float swing = (float) Math.sin(t * .09 + (sd > 0 ? 0 : Math.PI)) * .22f;
        p.pushPose();
        tr(p, sd * 10.5f, -8f, 0);
        p.mulPose(new Quaternionf().rotateX(swing - act * .9f).rotateZ(sd * (.22f + act * .3f)));
        UniqGfx.box(p, b, 5.5f, 15f, 5.5f, HIDE);
        p.pushPose(); tr(p, 0, 8f, 0);
        p.mulPose(new Quaternionf().rotateX(.45f + act * .6f));
        UniqGfx.box(p, b, 4.6f, 14f, 4.6f, HIDE2);                      // forearm
        p.pushPose(); tr(p, 0, 8f, 0);
        UniqGfx.box(p, b, 5.2f, 4f, 5.2f, HIDE);                        // the hand
        for (int k = -1; k <= 1; k++) {                                 // and what it has instead of fingers
            p.pushPose();
            tr(p, k * 1.8f, 3.4f, -1f);
            p.mulPose(new Quaternionf().rotateX(.5f + k * .12f));
            UniqGfx.box(p, b, 1.1f, 6.5f + (ph >= 1 ? 2.5f : 0), 1.1f, BONE);
            p.popPose();
        }
        p.popPose();
        p.popPose();
        p.popPose();
    }

    private void leg(PoseStack p, MultiBufferSource b, int sd, float t) {
        float stride = (float) Math.sin(t * .11 + (sd > 0 ? Math.PI : 0)) * .18f;
        p.pushPose();
        tr(p, sd * 4.6f, 10f, 0);
        p.mulPose(new Quaternionf().rotateX(stride));
        UniqGfx.box(p, b, 6.5f, 13f, 6.5f, HIDE);
        p.pushPose(); tr(p, 0, 7.5f, 1.5f);
        p.mulPose(new Quaternionf().rotateX(-.55f));                    // it is digitigrade, like the thing it was not
        UniqGfx.box(p, b, 5.2f, 13f, 5.2f, HIDE2);
        p.pushPose(); tr(p, 0, 7.5f, -1.5f);
        p.mulPose(new Quaternionf().rotateX(.55f));
        UniqGfx.box(p, b, 5f, 5f, 9f, HIDE);
        p.popPose();
        p.popPose();
        p.popPose();
    }

    /** Its own moon, hanging over its shoulder and turning the air under it. */
    private void halo(PoseStack p, MultiBufferSource b, float t, float moon, int ph) {
        p.pushPose();
        tr(p, 0, -46f * moon - 14f, 16f);
        float r = 11f + 7f * moon;
        p.mulPose(new Quaternionf().rotateY(t * .01f));
        UniqGfx.sphere(p, b, r, 10, 14, ph >= 2 ? GLOW : 0xFFB02838);
        p.pushPose(); tr(p, 0, 0, -1.5f);
        UniqGfx.sphere(p, b, r * .62f, 8, 12, PALE);                    // what is left of the face of it
        p.popPose();
        for (int i = 0; i < 14; i++) {                                  // light coming off it, unevenly
            float a = i * (float) Math.PI * 2 / 14 + t * .02f;
            float len = r * (1.5f + .5f * (float) Math.sin(t * .2 + i * 1.7));
            p.pushPose();
            tr(p, (float) Math.cos(a) * r * 1.15f, (float) Math.sin(a) * r * 1.15f, 0);
            p.mulPose(new Quaternionf().rotateZ(a + (float) Math.PI / 2));
            UniqGfx.box(p, b, .9f, len, .9f, i % 3 == 0 ? PALE : GLOW);
            p.popPose();
        }
        p.popPose();
    }

    private static void tr(PoseStack p, float x, float y, float z) { p.translate(x / 16f, y / 16f, z / 16f); }
}
