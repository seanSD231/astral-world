package com.astralspace;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.Level;
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
 * Client only: the Astral Barrier in the overworld sky. A huge, mostly transparent purple plane at barrier_y with a
 * faint grid, and one crimson crack for every missing HP (healing removes cracks again).
 * The plane follows the player and the crack pattern repeats every TILE blocks, so cracks are visible wherever you are.
 * Cracks are identical for everyone: crack number i always looks the same.
 */
public final class BarrierRenderer {
    private static final float HALF = 750f;     // how far around the player the barrier is drawn
    private static final float TILE = 1024f;    // the crack pattern repeats every TILE blocks
    private static final float GRID = 128f;     // faint grid lines every GRID blocks
    private static final int MAX_CRACKS = 120;

    /** crack index -> segments {x0, z0, x1, z1, width} inside one tile */
    private static final Map<Integer, List<float[]>> CRACKS = new HashMap<>();

    private BarrierRenderer() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(BarrierRenderer::onRender);
    }

    private static List<float[]> crack(int index) {
        return CRACKS.computeIfAbsent(index, i -> {
            Random r = new Random(0x9E3779B97F4A7C15L * (i + 1) ^ 0xA57A1L);
            List<float[]> segs = new ArrayList<>();
            float x = r.nextFloat() * TILE, z = r.nextFloat() * TILE;
            double angle = r.nextDouble() * Math.PI * 2;
            int n = 8 + r.nextInt(6);
            for (int k = 0; k < n; k++) {
                float len = 30 + r.nextFloat() * 40;
                angle += r.nextGaussian() * 0.45;
                float nx = x + (float) Math.cos(angle) * len, nz = z + (float) Math.sin(angle) * len;
                segs.add(new float[]{x, z, nx, nz, 3.2f});
                if (r.nextFloat() < 0.35f) { // a short branch
                    float bx = nx, bz = nz;
                    double ba = angle + (r.nextBoolean() ? 1 : -1) * (0.6 + r.nextDouble() * 0.6);
                    for (int b = 0; b < 3; b++) {
                        float bl = 18 + r.nextFloat() * 22;
                        ba += r.nextGaussian() * 0.35;
                        float ex = bx + (float) Math.cos(ba) * bl, ez = bz + (float) Math.sin(ba) * bl;
                        segs.add(new float[]{bx, bz, ex, ez, 2.0f});
                        bx = ex; bz = ez;
                    }
                }
                x = nx; z = nz;
            }
            return segs;
        });
    }

    /** One flat quad, drawn from both sides (the render type culls back faces). */
    private static void quad(VertexConsumer vc, Matrix4f m, float y,
                             float x0, float z0, float x1, float z1, float x2, float z2, float x3, float z3,
                             int r, int g, int b, int a) {
        vc.addVertex(m, x0, y, z0).setColor(r, g, b, a);
        vc.addVertex(m, x1, y, z1).setColor(r, g, b, a);
        vc.addVertex(m, x2, y, z2).setColor(r, g, b, a);
        vc.addVertex(m, x3, y, z3).setColor(r, g, b, a);
        vc.addVertex(m, x3, y, z3).setColor(r, g, b, a);
        vc.addVertex(m, x2, y, z2).setColor(r, g, b, a);
        vc.addVertex(m, x1, y, z1).setColor(r, g, b, a);
        vc.addVertex(m, x0, y, z0).setColor(r, g, b, a);
    }

    /** A line of the given width between two points, as a quad lying in the barrier plane. */
    private static void segment(VertexConsumer vc, Matrix4f m, float y, float x0, float z0, float x1, float z1,
                                float width, int r, int g, int b, int a) {
        float dx = x1 - x0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001f) return;
        float px = -dz / len * width * 0.5f, pz = dx / len * width * 0.5f;
        quad(vc, m, y, x0 + px, z0 + pz, x1 + px, z1 + pz, x1 - px, z1 - pz, x0 - px, z0 - pz, r, g, b, a);
    }

    private static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!Net.ClientState.barrierShow) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.level.dimension().equals(Level.OVERWORLD)) return;

        int missing = Math.max(0, Net.ClientState.barrierMax - Net.ClientState.barrierHp);
        int cracks = Math.min(missing, MAX_CRACKS);
        float y = Net.ClientState.barrierY;

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

        float cx = (float) cam.x, cz = (float) cam.z;
        float minX = cx - HALF, maxX = cx + HALF, minZ = cz - HALF, maxZ = cz + HALF;

        // the barrier itself: mostly transparent purple
        quad(vc, m, y, minX, minZ, maxX, minZ, maxX, maxZ, minX, maxZ, 150, 60, 255, 20);

        // faint grid so it reads as a surface
        for (float gx = (float) Math.floor(minX / GRID) * GRID; gx <= maxX; gx += GRID)
            segment(vc, m, y, gx, minZ, gx, maxZ, 1.6f, 190, 120, 255, 38);
        for (float gz = (float) Math.floor(minZ / GRID) * GRID; gz <= maxZ; gz += GRID)
            segment(vc, m, y, minX, gz, maxX, gz, 1.6f, 190, 120, 255, 38);

        // cracks: one per missing HP, repeated in every tile around the player
        if (cracks > 0) {
            int tx0 = (int) Math.floor(minX / TILE), tx1 = (int) Math.floor(maxX / TILE);
            int tz0 = (int) Math.floor(minZ / TILE), tz1 = (int) Math.floor(maxZ / TILE);
            for (int i = 0; i < cracks; i++) {
                for (float[] s : crack(i)) {
                    for (int tx = tx0; tx <= tx1; tx++) {
                        for (int tz = tz0; tz <= tz1; tz++) {
                            float ox = tx * TILE, oz = tz * TILE;
                            float x0 = s[0] + ox, z0 = s[1] + oz, x1 = s[2] + ox, z1 = s[3] + oz;
                            if (x0 < minX - 100 || x0 > maxX + 100 || z0 < minZ - 100 || z0 > maxZ + 100) continue;
                            segment(vc, m, y, x0, z0, x1, z1, s[4] * 3.2f, 220, 20, 60, 36); // soft glow
                            segment(vc, m, y, x0, z0, x1, z1, s[4], 255, 70, 90, 235);        // the crack
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
