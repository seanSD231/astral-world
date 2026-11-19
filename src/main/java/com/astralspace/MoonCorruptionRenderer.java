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
 * <p>A thin figure hanging in the air at the heart of an enormous flower, arms up and open, with a
 * crown of fine spines over its head, a gown that runs away below it into nothing, and the blood
 * moon standing behind the whole arrangement. The petals are the thing that reads first, so they
 * are what moves: they close over it when it guards and throw themselves wide when it blooms, and
 * the rest of it is deliberately slight by comparison.</p>
 *
 * <p>Built from the mod's own primitives rather than a model file, which is what lets the corolla
 * open and shut and the threads hang at their own lengths.</p>
 */
public final class MoonCorruptionRenderer extends EntityRenderer<MoonCorruption> {
    private static final ResourceLocation TEX = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private static final int PETAL_TIP = 0xFFF2B9BC, PETAL = 0xFFE07A86, PETAL_DEEP = 0xFFA81E30,
                             GOWN = 0xFF8E1222, GOWN_DEEP = 0xFF4A0A14,
                             BODY = 0xFF2A1A22, SKIN = 0xFFD8BCC0,
                             CROWN = 0xFFC89A6A, MOONC = 0xFFC8202C, MOON_PALE = 0xFFF28A8A,
                             THREAD = 0xFF6A3038;

    public MoonCorruptionRenderer(EntityRendererProvider.Context ctx) { super(ctx); shadowRadius = 1.4f; }

    @Override
    public ResourceLocation getTextureLocation(MoonCorruption e) { return TEX; }

    @Override
    public void render(MoonCorruption e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        int was = UniqGfx.light;
        UniqGfx.light = light;
        float t = e.tickCount + pt;
        int ph = e.phase();
        float moon = e.moon();
        float open = e.open();                 // how wide the corolla is standing
        float act = e.act() / 40f;

        pose.pushPose();
        pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(180 - yaw)));
        // it does not stand on anything
        pose.translate(0, .3 + Math.sin(t * .045) * .10, 0);

        moonBehind(pose, buf, t, moon, ph);
        threads(pose, buf, t, ph);
        corolla(pose, buf, t, open, ph, act);
        figure(pose, buf, t, ph, act, moon);
        gown(pose, buf, t, ph);

        pose.popPose();
        UniqGfx.light = was;
        super.render(e, yaw, pt, pose, buf, light);
    }

    // ---- the flower ----

    /** Eight petals round it, long and tapering, standing wide open or folded over it. */
    private void corolla(PoseStack p, MultiBufferSource b, float t, float open, int ph, float act) {
        int n = 8;
        for (int i = 0; i < n; i++) {
            float a = i * (float) Math.PI * 2 / n + t * .004f;
            float sway = (float) Math.sin(t * .07 + i * 1.3) * .09f;
            // open: thrown back and out. closed: folded up over it.
            float pitch = (1f - open) * -1.25f + open * .34f + sway + act * .22f;
            float len = 30f + 7f * open + (float) Math.sin(t * .05 + i) * 1.4f;
            p.pushPose();
            UniqGfx.tr(p, 0, -22f, 0);
            p.mulPose(new Quaternionf().rotateY(a).rotateX(pitch));
            petal(p, b, len, 13f + 4f * open, i % 2 == 0);
            p.popPose();
        }
        // the small inner ring that never opens
        for (int i = 0; i < 6; i++) {
            float a = i * (float) Math.PI * 2 / 6 - t * .009f;
            p.pushPose();
            UniqGfx.tr(p, 0, -20f, 0);
            p.mulPose(new Quaternionf().rotateY(a).rotateX(-.55f + open * .5f));
            petal(p, b, 15f, 6f, false);
            p.popPose();
        }
    }

    /** One petal: a blade that widens, then comes back to a point, pale at the tip and deep at the root. */
    private void petal(PoseStack p, MultiBufferSource b, float len, float wide, boolean alt) {
        int steps = 7;
        for (int k = 0; k < steps; k++) {
            float u = k / (float) steps, u2 = (k + 1) / (float) steps;
            float w = wide * (float) Math.sin(Math.pow(u, .8) * Math.PI) + 1.2f;
            float y = len * u, h = len * (u2 - u) + .6f;
            int c = u < .25f ? PETAL_DEEP : (u < .62f ? PETAL : PETAL_TIP);
            if (alt && u > .4f) c = UniqGfx.lighten(c);
            p.pushPose();
            UniqGfx.tr(p, 0, y, (float) Math.sin(u * 2.1) * 2.4f);
            p.mulPose(new Quaternionf().rotateX(.1f * u));
            UniqGfx.box(p, b, w, h, .55f, c);
            p.popPose();
        }
        // the vein up the middle
        p.pushPose(); UniqGfx.tr(p, 0, len * .5f, .35f);
        UniqGfx.box(p, b, .7f, len * .95f, .3f, PETAL_DEEP);
        p.popPose();
    }

    // ---- what is at the heart of it ----

    private void figure(PoseStack p, MultiBufferSource b, float t, int ph, float act, float moon) {
        p.pushPose();
        UniqGfx.tr(p, 0, -22f, 0);

        UniqGfx.box(p, b, 4.6f, 13f, 3.4f, BODY);                       // the torso, slight
        p.pushPose(); UniqGfx.tr(p, 0, -1.5f, -1.8f);
        UniqGfx.box(p, b, 3.4f, 8f, .6f, GOWN); p.popPose();            // the bodice
        p.pushPose(); UniqGfx.tr(p, 0, 7f, 0);
        UniqGfx.box(p, b, 4f, 3f, 3f, GOWN_DEEP); p.popPose();          // the waist it narrows to

        // the head, tipped back
        p.pushPose();
        UniqGfx.tr(p, 0, -9.5f, 0);
        p.mulPose(new Quaternionf().rotateX(-.3f - act * .25f));
        UniqGfx.box(p, b, 4.2f, 4.6f, 4.2f, SKIN);
        p.pushPose(); UniqGfx.tr(p, 0, -1.5f, 1.6f);
        UniqGfx.box(p, b, 4.6f, 4.4f, 2.6f, BODY); p.popPose();         // hair off the back of it
        // a crown of fine spines, a wheel of them standing out round the head
        for (int i = 0; i < 14; i++) {
            float a = i * (float) Math.PI * 2 / 14;
            float len = (i % 2 == 0 ? 9f : 5.5f) + moon * 3f;
            p.pushPose();
            p.mulPose(new Quaternionf().rotateZ(a).rotateX(-.12f));
            UniqGfx.feather(p, b, 0, 2.6f, 0, 0, 1, (float) Math.sin(a) * .2f, len, .9f,
                            i % 2 == 0 ? CROWN : UniqGfx.lighten(CROWN));
            p.popPose();
        }
        p.popPose();

        // the arms, up and out, which is how it hangs
        for (int sd = -1; sd <= 1; sd += 2) {
            float lift = 1.15f + act * .35f + (float) Math.sin(t * .05 + sd) * .06f;
            p.pushPose();
            UniqGfx.tr(p, sd * 2.8f, -5.5f, 0);
            p.mulPose(new Quaternionf().rotateZ(sd * lift).rotateX(-.18f));
            UniqGfx.box(p, b, 2.1f, 11f, 2.1f, SKIN);
            p.pushPose(); UniqGfx.tr(p, 0, 6.5f, 0);
            p.mulPose(new Quaternionf().rotateZ(sd * .3f));
            UniqGfx.box(p, b, 1.8f, 10f, 1.8f, SKIN);
            p.pushPose(); UniqGfx.tr(p, 0, 5.6f, 0);
            UniqGfx.box(p, b, 1.9f, 2.4f, 1.9f, SKIN);
            for (int k = -1; k <= 1; k++) {                             // long fingers, open
                p.pushPose();
                UniqGfx.tr(p, k * .7f, 2.2f, 0);
                p.mulPose(new Quaternionf().rotateZ(k * .22f));
                UniqGfx.box(p, b, .5f, 3.4f, .5f, SKIN);
                p.popPose();
            }
            p.popPose(); p.popPose(); p.popPose();
        }
        p.popPose();
    }

    /** The gown, which does not end anywhere: it runs off below into the water. */
    private void gown(PoseStack p, MultiBufferSource b, float t, int ph) {
        for (int i = 0; i < 10; i++) {
            float a = i * (float) Math.PI * 2 / 10 + t * .005f;
            float drift = (float) Math.sin(t * .06 + i * .9) * .16f;
            p.pushPose();
            UniqGfx.tr(p, 0, -14f, 0);
            p.mulPose(new Quaternionf().rotateY(a).rotateX(2.55f + drift));
            UniqGfx.feather(p, b, 0, 0, 0, 0, 1, 0, 26f + (i % 3) * 5f, 5.5f,
                            i % 2 == 0 ? GOWN : GOWN_DEEP);
            p.popPose();
        }
        p.pushPose(); UniqGfx.tr(p, 0, -5f, 0);
        UniqGfx.box(p, b, 7f, 16f, 7f, GOWN_DEEP); p.popPose();
    }

    /** The threads that hold it up, hanging out of a sky that is not there. */
    private void threads(PoseStack p, MultiBufferSource b, float t, int ph) {
        int n = ph >= 1 ? 16 : 10;
        for (int i = 0; i < n; i++) {
            float a = UniqGfx.rnd(i * 7 + 3) * (float) Math.PI * 2;
            float r = 10f + UniqGfx.rnd(i * 7 + 5) * 34f;
            float len = 40f + UniqGfx.rnd(i * 7 + 9) * 70f;
            float sway = (float) Math.sin(t * .03 + i) * 1.6f;
            p.pushPose();
            UniqGfx.tr(p, (float) Math.cos(a) * r + sway, -56f, (float) Math.sin(a) * r);
            UniqGfx.box(p, b, .35f, len, .35f, THREAD);
            p.popPose();
        }
    }

    /** The moon it stands in front of, and the dead tree across it. */
    private void moonBehind(PoseStack p, MultiBufferSource b, float t, float moon, int ph) {
        if (moon < .01f) return;
        p.pushPose();
        UniqGfx.tr(p, 0, -34f, 46f);
        float r = 34f + 16f * moon;
        UniqGfx.sphere(p, b, r, 12, 18, ph >= 2 ? UniqGfx.lighten(MOONC) : MOONC);
        p.pushPose(); UniqGfx.tr(p, 0, 0, -2f);
        UniqGfx.sphere(p, b, r * .72f, 10, 14, MOON_PALE);
        p.popPose();
        // bare branches across the face of it
        for (int i = 0; i < 9; i++) {
            float a = (float) Math.PI + i * (float) Math.PI / 8f;
            p.pushPose();
            p.mulPose(new Quaternionf().rotateZ(a));
            branch(p, b, r * .95f, 0, 2);
            p.popPose();
        }
        p.popPose();
    }

    /** One bare branch, which forks twice and thins as it goes. */
    private void branch(PoseStack p, MultiBufferSource b, float len, int seed, int depth) {
        UniqGfx.box(p, b, .9f + depth * .5f, len, .9f + depth * .5f, 0xFF201418);
        if (depth <= 0) return;
        for (int sd = -1; sd <= 1; sd += 2) {
            p.pushPose();
            UniqGfx.tr(p, 0, len * .42f, 0);
            p.mulPose(new Quaternionf().rotateZ(sd * (.45f + UniqGfx.rnd(seed * 13 + depth) * .35f)));
            branch(p, b, len * .55f, seed * 3 + sd, depth - 1);
            p.popPose();
        }
    }
}
