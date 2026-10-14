package com.astralspace;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Client only: the Astral Barrier in the sky of the Astral World (never drawn in the overworld).
 * A huge, very translucent flat purple square with a faint grid. One crack per missing HP, in 6 different looks (colour, width, jaggedness, branching),
 * every crack always looks the same for everyone. The dome follows the player and the cracks repeat every TILE blocks.
 */
public final class BarrierRenderer {
    private static final float HALF = 800f;     // how far around the player the barrier is drawn
    private static final float CURVE = 1600f;   // radius of the "planet": the barrier sags d*d/(2*CURVE) blocks at distance d
    private static final float CELL = 50f;      // size of one cell of the dome mesh
    private static final float TILE = 1024f;    // the crack pattern repeats every TILE blocks
    private static final float GRID = 128f;     // faint grid lines every GRID blocks
    private static final float PIECE = 24f;     // lines are cut into pieces this long so they follow the curve
    private static final int MAX_CRACKS = 120;
    private static final net.minecraft.resources.ResourceLocation ASTRAL =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("astral_space", "astral_space");

    // base alphas (very translucent)
    private static final int BASE_ALPHA = 30, GRID_ALPHA = 60, CRACK_ALPHA = 205;

    /** Six crack looks: {angleJitter, segLenMin, segLenMax, segMin, segMax, branchChance, width, r, g, b, glowAlpha, glowWidthMul} */
    private static final float[][] VARIANTS = {
            {0.45f, 30, 70, 8, 13, 0.35f, 3.2f, 255, 70, 90, 36, 3.2f},     // classic crimson
            {0.25f, 50, 110, 8, 12, 0.10f, 1.6f, 235, 215, 255, 28, 4.0f},  // long pale hairline
            {0.60f, 15, 35, 10, 16, 0.60f, 4.5f, 200, 20, 40, 40, 2.6f},    // thick dark red, many branches
            {0.80f, 20, 50, 8, 12, 0.40f, 2.8f, 255, 150, 40, 36, 3.0f},    // jagged ember orange
            {1.00f, 12, 30, 12, 18, 0.50f, 1.8f, 120, 200, 255, 40, 3.5f},  // electric blue, very jagged
            {0.30f, 40, 80, 4, 7, 0.20f, 6.5f, 255, 60, 200, 30, 3.5f},     // wide magenta, few segments
    };

    private static final class Crack {
        int variant; float widthMul;
        final List<float[]> segs = new ArrayList<>(); // {x0, z0, x1, z1, width}
    }
    private static final Map<Integer, Crack> CRACKS = new HashMap<>();

    private static float cx, cz;                    // the player's position this frame
    private static float baseY;

    private BarrierRenderer() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(BarrierRenderer::onRender);
    }

    private static Crack crack(int index) {
        return CRACKS.computeIfAbsent(index, i -> {
            Random r = new Random(0x9E3779B97F4A7C15L * (i + 1) ^ 0xA57A1L);
            Crack c = new Crack();
            c.variant = r.nextInt(VARIANTS.length);
            c.widthMul = 0.75f + r.nextFloat() * 0.6f;             // size varies a bit within a look
            float[] v = VARIANTS[c.variant];
            float scale = 0.8f + r.nextFloat() * 0.5f;
            float x = r.nextFloat() * TILE, z = r.nextFloat() * TILE;
            double angle = r.nextDouble() * Math.PI * 2;
            int n = (int) v[3] + r.nextInt((int) (v[4] - v[3]) + 1);
            for (int k = 0; k < n; k++) {
                float len = (v[1] + r.nextFloat() * (v[2] - v[1])) * scale;
                angle += r.nextGaussian() * v[0];
                float nx = x + (float) Math.cos(angle) * len, nz = z + (float) Math.sin(angle) * len;
                c.segs.add(new float[]{x, z, nx, nz, v[6] * c.widthMul});
                if (r.nextFloat() < v[5]) { // a branch
                    float bx = nx, bz = nz;
                    double ba = angle + (r.nextBoolean() ? 1 : -1) * (0.6 + r.nextDouble() * 0.6);
                    int bn = 2 + r.nextInt(3);
                    for (int b = 0; b < bn; b++) {
                        float bl = (v[1] * 0.6f + r.nextFloat() * v[2] * 0.4f) * scale;
                        ba += r.nextGaussian() * v[0] * 0.8;
                        float ex = bx + (float) Math.cos(ba) * bl, ez = bz + (float) Math.sin(ba) * bl;
                        c.segs.add(new float[]{bx, bz, ex, ez, v[6] * c.widthMul * 0.6f});
                        bx = ex; bz = ez;
                    }
                }
                x = nx; z = nz;
            }
            return c;
        });
    }

    /** Height of the barrier at x/z: it curves down away from the player like a planet's surface. */
    private static float yAt(float x, float z) {
        float dx = x - cx, dz = z - cz;
        return baseY; // flat again (CURVE kept so a curved version is a one-line change)
    }

    /** Fades to nothing toward the edge of the drawn area (also hides where the mesh ends). */
    private static float fade(float x, float z) {
        float dx = x - cx, dz = z - cz;
        float d = (float) Math.sqrt(dx * dx + dz * dz) / HALF;
        return 1f; // a plain square: no fading toward the edge
    }

    private static void vert(VertexConsumer vc, Matrix4f m, float x, float z, int r, int g, int b, int a) {
        int alpha = Math.round(a * fade(x, z));
        vc.addVertex(m, x, yAt(x, z), z).setColor(r, g, b, alpha);
    }

    /** One quad, drawn from both sides (the render type culls back faces). */
    private static void quad(VertexConsumer vc, Matrix4f m,
                             float x0, float z0, float x1, float z1, float x2, float z2, float x3, float z3,
                             int r, int g, int b, int a) {
        vert(vc, m, x0, z0, r, g, b, a); vert(vc, m, x1, z1, r, g, b, a); vert(vc, m, x2, z2, r, g, b, a); vert(vc, m, x3, z3, r, g, b, a);
        vert(vc, m, x3, z3, r, g, b, a); vert(vc, m, x2, z2, r, g, b, a); vert(vc, m, x1, z1, r, g, b, a); vert(vc, m, x0, z0, r, g, b, a);
    }

    /** A line of the given width between two points, lying on the curved barrier (cut into short pieces). */
    private static void segment(VertexConsumer vc, Matrix4f m, float x0, float z0, float x1, float z1,
                                float width, int r, int g, int b, int a) {
        float dx = x1 - x0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001f) return;
        float px = -dz / len * width * 0.5f, pz = dx / len * width * 0.5f;
        int pieces = Math.max(1, (int) Math.ceil(len / PIECE));
        for (int k = 0; k < pieces; k++) {
            float t0 = k / (float) pieces, t1 = (k + 1) / (float) pieces;
            float ax = x0 + dx * t0, az = z0 + dz * t0, bx = x0 + dx * t1, bz = z0 + dz * t1;
            quad(vc, m, ax + px, az + pz, bx + px, bz + pz, bx - px, bz - pz, ax - px, az - pz, r, g, b, a);
        }
    }

    private static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!Net.ClientState.barrierShow) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.level.dimension().location().equals(ASTRAL)) return;

        int missing = Math.max(0, Net.ClientState.barrierMax - Net.ClientState.barrierHp);
        int cracks = Math.min(missing, MAX_CRACKS);
        baseY = Net.ClientState.barrierY;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());

        // The barrier is hundreds of blocks away, past the normal fog: switch fog off while drawing it.
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(1.0E7f);
        RenderSystem.setShaderFogEnd(2.0E7f);

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();

        cx = (float) cam.x; cz = (float) cam.z;
        float minX = cx - HALF, maxX = cx + HALF, minZ = cz - HALF, maxZ = cz + HALF;

        // the dome itself: a mesh of cells so it can curve
        for (float x = minX; x < maxX; x += CELL)
            for (float z = minZ; z < maxZ; z += CELL)
                quad(vc, m, x, z, x + CELL, z, x + CELL, z + CELL, x, z + CELL, 170, 90, 255, BASE_ALPHA);

        // faint grid so it reads as a surface
        for (float gx = (float) Math.floor(minX / GRID) * GRID; gx <= maxX; gx += GRID)
            segment(vc, m, gx, minZ, gx, maxZ, 1.6f, 210, 150, 255, GRID_ALPHA);
        for (float gz = (float) Math.floor(minZ / GRID) * GRID; gz <= maxZ; gz += GRID)
            segment(vc, m, minX, gz, maxX, gz, 1.6f, 210, 150, 255, GRID_ALPHA);

        // cracks: one per missing HP, repeated in every tile around the player
        if (cracks > 0) {
            int tx0 = (int) Math.floor(minX / TILE), tx1 = (int) Math.floor(maxX / TILE);
            int tz0 = (int) Math.floor(minZ / TILE), tz1 = (int) Math.floor(maxZ / TILE);
            for (int i = 0; i < cracks; i++) {
                Crack c = crack(i);
                float[] v = VARIANTS[c.variant];
                int cr = (int) v[7], cg = (int) v[8], cb = (int) v[9], glowA = (int) v[10];
                for (float[] s : c.segs) {
                    for (int tx = tx0; tx <= tx1; tx++) {
                        for (int tz = tz0; tz <= tz1; tz++) {
                            float ox = tx * TILE, oz = tz * TILE;
                            float x0 = s[0] + ox, z0 = s[1] + oz, x1 = s[2] + ox, z1 = s[3] + oz;
                            if (x0 < minX - 100 || x0 > maxX + 100 || z0 < minZ - 100 || z0 > maxZ + 100) continue;
                            segment(vc, m, x0, z0, x1, z1, s[4] * v[11], cr * 3 / 4, cg / 3, cb * 3 / 4, glowA); // soft glow
                            segment(vc, m, x0, z0, x1, z1, s[4], cr, cg, cb, CRACK_ALPHA);                      // the crack
                        }
                    }
                }
            }
        }

        ps.popPose();
        buffers.endBatch(RenderType.lightning());
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
    }
}
