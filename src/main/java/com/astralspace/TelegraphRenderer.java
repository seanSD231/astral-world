package com.astralspace;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

/**
 * Client only: draws the raid's danger markers (rings, beams, root and seed beacons) on top of everything, so the huge
 * tree model can never hide them. The server sends one short-lived marker per frame of the telegraph.
 */
public final class TelegraphRenderer {
    private static VertexBuffer vb;

    private TelegraphRenderer() {}

    public static void init() { NeoForge.EVENT_BUS.addListener(TelegraphRenderer::onRender); }

    private static void quad(BufferBuilder bb, Vec3 c, double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4, int r, int g, int b, int a) {
        bb.addVertex((float) (x1 - c.x), (float) (y1 - c.y), (float) (z1 - c.z)).setColor(r, g, b, a);
        bb.addVertex((float) (x2 - c.x), (float) (y2 - c.y), (float) (z2 - c.z)).setColor(r, g, b, a);
        bb.addVertex((float) (x3 - c.x), (float) (y3 - c.y), (float) (z3 - c.z)).setColor(r, g, b, a);
        bb.addVertex((float) (x4 - c.x), (float) (y4 - c.y), (float) (z4 - c.z)).setColor(r, g, b, a);
    }

    private static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (Net.ClientState.telegraphs.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        long now = System.currentTimeMillis();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int count = 0;
        synchronized (Net.ClientState.telegraphs) {
            Net.ClientState.telegraphs.removeIf(t -> t[7] < now);
            for (double[] t : Net.ClientState.telegraphs) {
                int kind = (int) t[0], col = (int) t[6];
                double x = t[1], y = t[2], z = t[3], a = t[4], b = t[5];
                int r = col >> 16 & 255, g = col >> 8 & 255, bl = col & 255;
                if (kind == 0 || kind == 1) {
                    int n = (int) Math.max(24, Math.min(120, a * 8));
                    double in = Math.max(0.1, a - 0.4), out = a + 0.4, yy = y + 0.12;
                    for (int i = 0; i < n; i++) {
                        double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                        double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
                        quad(bb, cam, x + c0 * in, yy, z + s0 * in, x + c0 * out, yy, z + s0 * out, x + c1 * out, yy, z + s1 * out, x + c1 * in, yy, z + s1 * in, r, g, bl, 235);
                        if (kind == 0) quad(bb, cam, x, yy - 0.02, z, x + c0 * in, yy - 0.02, z + s0 * in, x + c1 * in, yy - 0.02, z + s1 * in, x, yy - 0.02, z, r, g, bl, 70);
                        count += kind == 0 ? 2 : 1;
                    }
                } else if (kind == 2) { // a beam lying on the ground: start (x,z), angle a, length b
                    double dx = Math.cos(a), dz = Math.sin(a), px = -dz * 1.3, pz = dx * 1.3, yy = y + 0.14;
                    quad(bb, cam, x + px, yy, z + pz, x + dx * b + px, yy, z + dz * b + pz, x + dx * b - px, yy, z + dz * b - pz, x - px, yy, z - pz, r, g, bl, 150);
                    count++;
                } else if (kind == 3) { // a tall beacon: two crossed panels, height b
                    quad(bb, cam, x - 0.6, y, z, x + 0.6, y, z, x + 0.6, y + b, z, x - 0.6, y + b, z, r, g, bl, 170);
                    quad(bb, cam, x, y, z - 0.6, x, y, z + 0.6, x, y + b, z + 0.6, x, y + b, z - 0.6, r, g, bl, 170);
                    count += 2;
                }
            }
        }
        if (count == 0) return;
        MeshData md = bb.build();
        if (md == null) return;
        if (vb == null) vb = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        vb.bind();
        vb.upload(md);
        float fs = RenderSystem.getShaderFogStart(), fe = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(1.0E7f);
        RenderSystem.setShaderFogEnd(2.0E7f);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        vb.drawWithShader(new Matrix4f(event.getModelViewMatrix()), event.getProjectionMatrix(), GameRenderer.getPositionColorShader());
        VertexBuffer.unbind();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderFogStart(fs);
        RenderSystem.setShaderFogEnd(fe);
    }
}
