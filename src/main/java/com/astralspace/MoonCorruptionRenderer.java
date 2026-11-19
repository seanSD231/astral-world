package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

/**
 * Drawing the Moon's Corruption, at the size it is supposed to be: something you stand under.
 *
 * <p>The reading order of the silhouette is petals, then gown, then the figure. The petals are an
 * enormous fan thrown back and out behind it, pale at their tips and deep at the root, and they are
 * most of what is on screen. The gown falls out of them in long panels that never reach a floor.
 * The figure itself is slight, dark and high up in the middle of all that, arms raised, with a
 * wheel of fine spines over its head. The blood moon stands behind the lot with bare branches
 * across the face of it.</p>
 *
 * <p>Everything is in sixteenths of a block, and the whole arrangement runs about sixteen blocks
 * from the hem of the gown to the crown.</p>
 */
public final class MoonCorruptionRenderer extends EntityRenderer<MoonCorruption> {
    private static final ResourceLocation TEX = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private static final int PETAL_TIP = 0xFFF6CBCE, PETAL_MID = 0xFFE88E98, PETAL = 0xFFD4606E,
                             PETAL_DEEP = 0xFF9E1828,
                             GOWN = 0xFF8E1222, GOWN_DEEP = 0xFF46080F,
                             BODY = 0xFF1E1218, SKIN = 0xFFE2C6C8,
                             CROWN = 0xFFCBA070, MOONC = 0xFFC01E2A, MOON_PALE = 0xFFEE7C80,
                             THREAD = 0xFF5A2830, BARK = 0xFF1A1014;

    /** Where the figure's waist sits above the entity's feet, in sixteenths. */
    private static final float WAIST = 150f;

    public MoonCorruptionRenderer(EntityRendererProvider.Context ctx) { super(ctx); shadowRadius = 2.6f; }

    @Override
    public ResourceLocation getTextureLocation(MoonCorruption e) { return TEX; }

    @Override
    public void render(MoonCorruption e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        int was = UniqGfx.light;
        UniqGfx.light = light;
        float t = e.tickCount + pt;
        int ph = e.phase();
        float moon = e.moon();
        float open = e.open();
        float act = e.act() / 40f;

        pose.pushPose();
        pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(180 - yaw)));
        pose.translate(0, .55 + Math.sin(t * .04) * .22, 0);       // it hangs, and it is never quite still

        moonBehind(pose, buf, t, moon, ph);
        threads(pose, buf, t, ph);
        gown(pose, buf, t, ph, open);
        corolla(pose, buf, t, open, ph, act);
        figure(pose, buf, t, ph, act, moon);

        pose.popPose();
        UniqGfx.light = was;
        super.render(e, yaw, pt, pose, buf, light);
    }

    // ---- the flower, which is most of it ----

    private void corolla(PoseStack p, MultiBufferSource b, float t, float open, int ph, float act) {
        int n = 9;
        for (int i = 0; i < n; i++) {
            // spread across the back rather than evenly round: it is a fan, not a daisy
            float spread = (i / (float) (n - 1) - .5f) * 2f;
            float a = spread * 2.5f;
            float sway = (float) Math.sin(t * .05 + i * 1.1) * .10f;
            float pitch = (1f - open) * -1.35f + open * (.30f + Math.abs(spread) * .30f) + sway + act * .18f;
            float len = 150f + 34f * open - Math.abs(spread) * 26f + (float) Math.sin(t * .04 + i) * 6f;
            p.pushPose();
            UniqGfx.tr(p, 0, WAIST - 16f, 10f);
            p.mulPose(new Quaternionf().rotateY(a).rotateX(pitch).rotateZ(spread * .22f));
            petal(p, b, len, 46f + 16f * open, i);
            p.popPose();
        }
        // a shorter ring in front, which never opens as far
        for (int i = 0; i < 5; i++) {
            float spread = (i / 4f - .5f) * 2f;
            p.pushPose();
            UniqGfx.tr(p, 0, WAIST - 12f, -6f);
            p.mulPose(new Quaternionf().rotateY(spread * 1.5f + (float) Math.PI)
                                       .rotateX(-.9f + open * .75f));
            petal(p, b, 76f + 18f * open, 26f, i + 20);
            p.popPose();
        }
    }

    /** One petal: widens, comes back to a point, pale at the tip, and curls along its length. */
    private void petal(PoseStack p, MultiBufferSource b, float len, float wide, int seed) {
        int steps = 11;
        for (int k = 0; k < steps; k++) {
            float u = (k + .5f) / steps;
            float w = wide * (float) Math.sin(Math.pow(u, .72) * Math.PI) + 2.5f;
            float h = len / steps + 1.2f;
            int c = u < .18f ? PETAL_DEEP : (u < .45f ? PETAL : (u < .74f ? PETAL_MID : PETAL_TIP));
            p.pushPose();
            UniqGfx.tr(p, (float) Math.sin(u * 3.1 + seed) * wide * .09f, len * u,
                          (float) Math.sin(u * 2.3) * len * .09f);
            p.mulPose(new Quaternionf().rotateX(u * .55f).rotateY((float) Math.sin(u * 2.6 + seed) * .12f));
            UniqGfx.box(p, b, w, h, 1.6f, c);
            p.popPose();
        }
        p.pushPose(); UniqGfx.tr(p, 0, len * .46f, 1.4f);
        UniqGfx.box(p, b, 2.2f, len * .9f, 1.0f, PETAL_DEEP);          // the vein up the middle
        p.popPose();
    }

    // ---- what is at the heart of it ----

    private void figure(PoseStack p, MultiBufferSource b, float t, int ph, float act, float moon) {
        p.pushPose();
        UniqGfx.tr(p, 0, WAIST, 0);

        UniqGfx.box(p, b, 13f, 40f, 9f, BODY);                          // the torso, long and slight
        p.pushPose(); UniqGfx.tr(p, 0, -4f, -5f);
        UniqGfx.box(p, b, 10f, 26f, 1.6f, GOWN); p.popPose();           // the bodice down the front
        p.pushPose(); UniqGfx.tr(p, 0, 21f, 0);
        UniqGfx.box(p, b, 11f, 9f, 8f, GOWN_DEEP); p.popPose();         // the waist it narrows into
        p.pushPose(); UniqGfx.tr(p, 0, -21f, 0);
        UniqGfx.box(p, b, 16f, 6f, 10f, GOWN_DEEP); p.popPose();        // the collar

        // the head, tipped back
        p.pushPose();
        UniqGfx.tr(p, 0, -29f, 0);
        p.mulPose(new Quaternionf().rotateX(-.34f - act * .22f));
        UniqGfx.box(p, b, 11f, 12f, 11f, SKIN);
        p.pushPose(); UniqGfx.tr(p, 0, -3f, 4.5f);
        UniqGfx.box(p, b, 12f, 12f, 6f, BODY); p.popPose();             // hair off the back of it
        // the crown: a wheel of fine spines standing out all round the head
        for (int i = 0; i < 18; i++) {
            float a = i * (float) Math.PI * 2 / 18;
            float len = (i % 2 == 0 ? 30f : 17f) + moon * 10f;
            p.pushPose();
            p.mulPose(new Quaternionf().rotateZ(a).rotateX(-.1f));
            UniqGfx.feather(p, b, 0, 7f, 0, 0, 1, (float) Math.sin(a) * .18f, len, 2.6f,
                            i % 2 == 0 ? CROWN : UniqGfx.lighten(CROWN));
            p.popPose();
        }
        p.popPose();

        // the arms, up and open, which is how it hangs
        for (int sd = -1; sd <= 1; sd += 2) {
            float lift = 1.05f + act * .35f + (float) Math.sin(t * .04 + sd) * .07f;
            p.pushPose();
            UniqGfx.tr(p, sd * 7.5f, -17f, 0);
            p.mulPose(new Quaternionf().rotateZ(sd * lift).rotateX(-.22f));
            UniqGfx.box(p, b, 6f, 34f, 6f, SKIN);
            p.pushPose(); UniqGfx.tr(p, 0, 20f, 0);
            p.mulPose(new Quaternionf().rotateZ(sd * .34f));
            UniqGfx.box(p, b, 5f, 32f, 5f, SKIN);
            p.pushPose(); UniqGfx.tr(p, 0, 18f, 0);
            UniqGfx.box(p, b, 5.4f, 7f, 5.4f, SKIN);
            for (int k = -1; k <= 1; k++) {                             // long open fingers
                p.pushPose();
                UniqGfx.tr(p, k * 2.1f, 7f, 0);
                p.mulPose(new Quaternionf().rotateZ(k * .24f));
                UniqGfx.box(p, b, 1.5f, 11f, 1.5f, SKIN);
                p.popPose();
            }
            p.popPose(); p.popPose(); p.popPose();
        }
        p.popPose();
    }

    /** The gown, which does not end anywhere: it runs off below into the water. */
    private void gown(PoseStack p, MultiBufferSource b, float t, int ph, float open) {
        for (int i = 0; i < 14; i++) {
            float a = i * (float) Math.PI * 2 / 14 + t * .004f;
            float drift = (float) Math.sin(t * .045 + i * .8) * .14f;
            p.pushPose();
            UniqGfx.tr(p, 0, WAIST + 22f, 0);
            p.mulPose(new Quaternionf().rotateY(a).rotateX(2.9f + drift - open * .12f));
            UniqGfx.feather(p, b, 0, 0, 0, 0, 1, 0, 95f + (i % 4) * 22f, 20f,
                            i % 2 == 0 ? GOWN : GOWN_DEEP);
            p.popPose();
        }
        p.pushPose(); UniqGfx.tr(p, 0, WAIST + 52f, 0);
        UniqGfx.box(p, b, 22f, 70f, 22f, GOWN_DEEP); p.popPose();
        p.pushPose(); UniqGfx.tr(p, 0, WAIST + 104f, 0);
        UniqGfx.box(p, b, 11f, 48f, 11f, GOWN_DEEP); p.popPose();       // and it tapers away
    }

    /** The threads it hangs from, out of a sky that is not there. */
    private void threads(PoseStack p, MultiBufferSource b, float t, int ph) {
        int n = ph >= 1 ? 22 : 14;
        for (int i = 0; i < n; i++) {
            float a = UniqGfx.rnd(i * 7 + 3) * (float) Math.PI * 2;
            float r = 24f + UniqGfx.rnd(i * 7 + 5) * 110f;
            float len = 150f + UniqGfx.rnd(i * 7 + 9) * 260f;
            float sway = (float) Math.sin(t * .025 + i) * 5f;
            p.pushPose();
            UniqGfx.tr(p, (float) Math.cos(a) * r + sway, WAIST - 60f - len * .5f, (float) Math.sin(a) * r);
            UniqGfx.box(p, b, 1.1f, len, 1.1f, THREAD);
            p.popPose();
        }
    }

    /** The moon it stands in front of, and the dead tree across it. */
    private void moonBehind(PoseStack p, MultiBufferSource b, float t, float moon, int ph) {
        if (moon < .01f) return;
        p.pushPose();
        UniqGfx.tr(p, 0, WAIST - 40f, 190f);
        float r = 130f + 55f * moon;
        UniqGfx.sphere(p, b, r, 14, 22, ph >= 2 ? UniqGfx.lighten(MOONC) : MOONC);
        p.pushPose(); UniqGfx.tr(p, 0, 0, -6f);
        UniqGfx.sphere(p, b, r * .74f, 12, 18, MOON_PALE);
        p.popPose();
        for (int i = 0; i < 11; i++) {                                  // bare branches across it
            float a = (float) Math.PI + i * (float) Math.PI / 10f;
            p.pushPose();
            UniqGfx.tr(p, 0, -r * .5f, -14f);
            p.mulPose(new Quaternionf().rotateZ(a));
            branch(p, b, r * .78f, i, 2);
            p.popPose();
        }
        p.popPose();
    }

    private void branch(PoseStack p, MultiBufferSource b, float len, int seed, int depth) {
        UniqGfx.box(p, b, 2.4f + depth * 1.6f, len, 2.4f + depth * 1.6f, BARK);
        if (depth <= 0) return;
        for (int sd = -1; sd <= 1; sd += 2) {
            p.pushPose();
            UniqGfx.tr(p, 0, len * .44f, 0);
            p.mulPose(new Quaternionf().rotateZ(sd * (.42f + UniqGfx.rnd(seed * 13 + depth) * .4f)));
            branch(p, b, len * .54f, seed * 3 + sd, depth - 1);
            p.popPose();
        }
    }
}
