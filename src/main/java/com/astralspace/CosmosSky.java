package com.astralspace;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Random;

/** The sky of the Cosmos (client only): pure black with thousands of stars all round, no sun and no moon. */
public final class CosmosSky extends DimensionSpecialEffects {
    private static final int[][] COLORS = {{255, 255, 255}, {255, 255, 255}, {255, 255, 255}, {175, 205, 255}, {255, 240, 190}, {255, 190, 140}, {255, 160, 170}};
    private final float[][] stars = new float[4000][];

    public CosmosSky() {
        super(Float.NaN, true, SkyType.NORMAL, false, false);
        Random r = new Random(77123L);
        for (int i = 0; i < stars.length; i++) {
            float x, y, z, len;
            do { x = r.nextFloat() * 2 - 1; y = r.nextFloat() * 2 - 1; z = r.nextFloat() * 2 - 1; len = (float) Math.sqrt(x * x + y * y + z * z); } while (len < 0.2f || len > 1f);
            int[] c = COLORS[r.nextInt(COLORS.length)];
            float big = r.nextInt(30) == 0 ? 2.2f : 1f;
            stars[i] = new float[]{x / len, y / len, z / len, (0.12f + r.nextFloat() * 0.22f) * big, 0.45f + r.nextFloat() * 0.55f, c[0], c[1], c[2]};
        }
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) { return fogColor; }

    @Override
    public boolean isFoggyAt(int x, int y) { return false; }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelView, Camera camera,
                             Matrix4f projection, boolean isFoggy, Runnable setupFog) {
        if (AstralSky.shaderPackOn()) return false; // a shader pack draws its own sky
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (float[] s : stars) {
            float dx = s[0], dy = s[1], dz = s[2];
            float ax = Math.abs(dy) > 0.9f ? 1f : 0f, ay = Math.abs(dy) > 0.9f ? 0f : 1f;
            float ux = ay * dz, uy = -ax * dz, uz = ax * dy - ay * dx;
            float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
            ux /= ul; uy /= ul; uz /= ul;
            float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
            float cx = dx * 100f, cy = dy * 100f, cz = dz * 100f, size = s[3] * 1.6f;
            float[][] k = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
            for (float[] q : k)
                b.addVertex(modelView, cx + (ux * q[0] + vx * q[1]) * size, cy + (uy * q[0] + vy * q[1]) * size, cz + (uz * q[0] + vz * q[1]) * size)
                        .setColor((int) s[5], (int) s[6], (int) s[7], (int) (s[4] * 255));
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        return true;
    }
}
