package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Client only: the Door supernova drawn as real geometry, not particles. A blinding dome of light (white core, gold halo) that blooms in a
 * few ticks, coloured shock spheres racing outward, and long rays of light shooting into the sky and along the ground. Additive and depth
 * tested, so the ground cuts it off like the Sun manifestation does. Only runs while a nova is alive.
 */
public final class NovaRender {
    private NovaRender() {}

    private static final int DUR = 90;
    private static final List<Nova> LIVE = new ArrayList<>();

    private static final class Nova {
        final double x, y, z; final float scale; final long start; final float[][] rays;   // per ray: dx, dy, dz, length, width, hue
        Nova(double x, double y, double z, float scale, long start) {
            this.x = x; this.y = y; this.z = z; this.scale = scale; this.start = start;
            Random r = new Random(start * 31 + (long) x * 7 + (long) z * 13);
            rays = new float[52][6];
            for (int i = 0; i < rays.length; i++) {
                double th = r.nextDouble() * 6.2832, yy = i < 36 ? .08 + r.nextDouble() * .92 : r.nextDouble() * .12 - .03, rr = Math.sqrt(1 - yy * yy);
                rays[i] = new float[]{(float) (Math.cos(th) * rr), (float) yy, (float) (Math.sin(th) * rr), (90 + r.nextFloat() * 170) * scale, (1.2f + r.nextFloat() * 4.5f) * scale, r.nextFloat()};
            }
        }
    }

    /** Called when the server says a supernova went off here. */
    public static void add(double x, double y, double z, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) LIVE.add(new Nova(x, y, z, scale, mc.level.getGameTime()));
    }

    // unit sphere, built once: 14 rings x 22 segments
    private static final int LAT = 14, LON = 22;
    private static final float[][] SPH = new float[(LAT + 1) * (LON + 1)][3];
    static {
        for (int i = 0; i <= LAT; i++) for (int j = 0; j <= LON; j++) {
            double a = Math.PI * i / LAT - Math.PI / 2, b = 2 * Math.PI * j / LON;
            SPH[i * (LON + 1) + j] = new float[]{(float) (Math.cos(a) * Math.cos(b)), (float) Math.sin(a), (float) (Math.cos(a) * Math.sin(b))};
        }
    }

    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIVE.clear(); return; }
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        PoseStack ps = e.getPoseStack();
        LIVE.removeIf(n -> {
            float t = (mc.level.getGameTime() - n.start) + pt;
            if (t >= DUR) return true;
            ps.pushPose();
            ps.translate(n.x - cam.x, n.y - cam.y, n.z - cam.z);
            draw(ps.last().pose(), vc, n, t, cam);
            ps.popPose();
            return false;
        });
        buf.endBatch(RenderType.lightning());
    }

    private static float ease(float p) { p = Mth.clamp(p, 0f, 1f); return 1f - (1f - p) * (1f - p) * (1f - p); }

    private static void draw(Matrix4f m, VertexConsumer vc, Nova n, float t, Vec3 cam) {
        float R = 30f * n.scale;
        float bloom = ease(t / 9f);                                   // the dome bursts open in about half a second
        float hold = t < 34 ? 1f : Mth.clamp(1f - (t - 34f) / 50f, 0f, 1f);   // then burns, then fades
        float grow = bloom * (1f + .3f * t / DUR);
        // inside out, so each outer layer passes the depth test in front of the one before it
        sphere(m, vc, R * .5f * grow, 1f, 1f, 1f, hold);
        sphere(m, vc, R * .8f * grow, 1f, .98f, .8f, .8f * hold);
        sphere(m, vc, R * 1.1f * grow, 1f, .9f, .55f, .5f * hold);
        sphere(m, vc, R * 1.5f * grow, 1f, .78f, .4f, .22f * hold);
        // coloured shock spheres racing out
        shell(m, vc, R, t / 52f, 1f, .5f, .2f);
        shell(m, vc, R, (t - 5f) / 58f, 1f, .3f, .8f);
        shell(m, vc, R, (t - 10f) / 66f, .4f, .6f, 1f);
        shell(m, vc, R, (t - 16f) / 72f, .5f, 1f, .8f);
        // rays of light
        float rayIn = ease(t / 7f), rayOut = t < 28 ? 1f : Mth.clamp(1f - (t - 28f) / 55f, 0f, 1f);
        float cx = (float) (n.x - cam.x), cy = (float) (n.y - cam.y), cz = (float) (n.z - cam.z);   // vector from camera to the centre
        for (float[] r : n.rays) {
            float len = r[3] * rayIn * (1f + .25f * t / DUR), w = r[4] * (1f - .35f * t / DUR);
            // ribbon faces the camera: sideways = ray x (centre - camera)
            float sx = r[1] * cz - r[2] * cy, sy = r[2] * cx - r[0] * cz, sz = r[0] * cy - r[1] * cx;
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz); if (sl < 1e-4f) continue;
            sx = sx / sl * w; sy = sy / sl * w; sz = sz / sl * w;
            float rr = 1f, gg = .93f + .07f * r[5], bb = .7f + .3f * r[5] * r[5];     // warm white, a few pale blue-whites
            if (r[5] > .86f) { rr = .75f; gg = .85f; bb = 1f; }
            float a = .75f * rayOut;
            float ex = r[0] * len, ey = r[1] * len, ez = r[2] * len;
            vc.addVertex(m, -sx, -sy, -sz).setColor(rr, gg, bb, a);
            vc.addVertex(m, sx, sy, sz).setColor(rr, gg, bb, a);
            vc.addVertex(m, ex, ey, ez).setColor(rr, gg, bb, 0f);
            vc.addVertex(m, ex, ey, ez).setColor(rr, gg, bb, 0f);
        }
    }

    private static void shell(Matrix4f m, VertexConsumer vc, float R, float p, float r, float g, float b) {
        if (p <= 0f || p >= 1f) return;
        sphere(m, vc, R * (.3f + 3.6f * ease(p)), r, g, b, .3f * (1f - p) * (1f - p));
    }

    /** A UV sphere, both sides drawn so it also reads from inside. */
    private static void sphere(Matrix4f m, VertexConsumer vc, float rad, float r, float g, float b, float a) {
        if (rad <= .01f || a <= .002f) return;
        for (int i = 0; i < LAT; i++) for (int j = 0; j < LON; j++) {
            float[] p0 = SPH[i * (LON + 1) + j], p1 = SPH[i * (LON + 1) + j + 1], p2 = SPH[(i + 1) * (LON + 1) + j + 1], p3 = SPH[(i + 1) * (LON + 1) + j];
            quad(m, vc, p0, p1, p2, p3, rad, r, g, b, a);
            quad(m, vc, p3, p2, p1, p0, rad, r, g, b, a);
        }
    }

    private static void quad(Matrix4f m, VertexConsumer vc, float[] a, float[] b, float[] c, float[] d, float rad, float r, float g, float bl, float al) {
        vc.addVertex(m, a[0] * rad, a[1] * rad, a[2] * rad).setColor(r, g, bl, al);
        vc.addVertex(m, b[0] * rad, b[1] * rad, b[2] * rad).setColor(r, g, bl, al);
        vc.addVertex(m, c[0] * rad, c[1] * rad, c[2] * rad).setColor(r, g, bl, al);
        vc.addVertex(m, d[0] * rad, d[1] * rad, d[2] * rad).setColor(r, g, bl, al);
    }
}
