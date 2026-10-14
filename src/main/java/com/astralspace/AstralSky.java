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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * The sky of the Astral World (client only): black, with stars, the moon straight overhead, and no sun.
 * Pathway symbols from the Lord of the Mysteries mod fade in and out slowly among the stars.
 * The moon uses the normal moon texture, so a resource pack that recolours the moon still applies.
 */
public final class AstralSky extends DimensionSpecialEffects {
    private static final String[] PATHWAYS = {
            "seer", "marauder", "apprentice", "spectator", "bard", "sailor", "reader", "secrets_supplicant", "sleepless",
            "corpse_collector", "warrior", "assassin", "hunter", "mystery_pryer", "savant", "monster", "planter",
            "apothecary", "criminal", "prisoner", "lawyer", "arbiter"
    };
    private static final int SYMBOLS = 14;       // how many symbols hang in the sky
    private static final ResourceLocation MOON = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");

    /** Star colours, like real stars: mostly white, some blue, yellow, orange and red ones. {r, g, b, weight} */
    private static final int[][] STAR_COLORS = {
            {255, 255, 255, 55}, {170, 200, 255, 15}, {255, 240, 180, 12}, {255, 190, 130, 10}, {255, 160, 170, 8}
    };

    private final float[][] stars = new float[1600][];      // {x, y, z, size, alpha, r, g, b}
    private final float[][] symbols = new float[SYMBOLS][]; // {size, phase, speed}

    public AstralSky() {
        super(Float.NaN, true, SkyType.NORMAL, false, false);
        Random r = new Random(0xA57A15CAL);
        for (int i = 0; i < stars.length; i++) {
            float[] d = direction(r, -1.0f); // the whole sky, under the player too
            int pick = r.nextInt(100), acc = 0, ci = 0;
            for (int k = 0; k < STAR_COLORS.length; k++) { acc += STAR_COLORS[k][3]; if (pick < acc) { ci = k; break; } }
            stars[i] = new float[]{d[0], d[1], d[2], 0.15f + r.nextFloat() * 0.12f, 0.5f + r.nextFloat() * 0.5f,
                    STAR_COLORS[ci][0], STAR_COLORS[ci][1], STAR_COLORS[ci][2]};
        }
        for (int i = 0; i < SYMBOLS; i++) {
            symbols[i] = new float[]{7f + r.nextFloat() * 6f, r.nextFloat() * 6.283f, 0.12f + r.nextFloat() * 0.2f};
        }
    }

    /** A random direction on the sky with a height of at least minY (1 = straight up). */
    private static float[] direction(Random r, float minY) {
        while (true) {
            float x = r.nextFloat() * 2 - 1, y = r.nextFloat() * 2 - 1, z = r.nextFloat() * 2 - 1;
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len < 0.2f || len > 1f) continue;
            if (y / len < minY) continue;
            return new float[]{x / len, y / len, z / len};
        }
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor.multiply(brightness * 0.94f + 0.06f, brightness * 0.94f + 0.06f, brightness * 0.91f + 0.09f);
    }

    @Override
    public boolean isFoggyAt(int x, int y) { return false; }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelView, Camera camera,
                             Matrix4f projection, boolean isFoggy, Runnable setupFog) {
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);

        // stars
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (float[] s : stars) quadFacing(b, modelView, s[0], s[1], s[2], s[3], (int) s[5], (int) s[6], (int) s[7], (int) (s[4] * 255), 0);
        BufferUploader.drawWithShader(b.buildOrThrow());

        // the moon, straight overhead (phase 0 = the top-left tile of the moon texture)
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, MOON);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        BufferBuilder m = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float mr = 20f;
        m.addVertex(modelView, -mr, 100f, -mr).setUv(0.0f, 0.0f);
        m.addVertex(modelView, -mr, 100f, mr).setUv(0.0f, 0.5f);
        m.addVertex(modelView, mr, 100f, mr).setUv(0.25f, 0.5f);
        m.addVertex(modelView, mr, 100f, -mr).setUv(0.25f, 0.0f);
        BufferUploader.drawWithShader(m.buildOrThrow());

        // pathway symbols fading in and out
        double t = System.currentTimeMillis() / 1000.0;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        for (int i = 0; i < SYMBOLS; i++) {
            float[] s = symbols[i];
            double angle = t * s[2] + s[1];
            long cycle = (long) Math.floor(angle / (Math.PI * 2));
            float wave = (float) (0.5 - 0.5 * Math.cos(angle));        // 0 at the start and end of a cycle: invisible
            float alpha = wave * wave * 0.8f;
            if (alpha < 0.02f) continue;
            // every time a symbol fades in again it is somewhere else, and may be another pathway
            Random pr = new Random(i * 7919L + cycle * 104729L + 17L);
            float[] d = direction(pr, -0.6f);
            String path = PATHWAYS[(int) ((i * 5 + cycle * 3 + (cycle < 0 ? 22 * 1000L : 0)) % PATHWAYS.length)];
            RenderSystem.setShaderTexture(0, ResourceLocation.fromNamespaceAndPath("lotm", "textures/pathway/" + path + ".png"));
            BufferBuilder q = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            texturedFacing(q, modelView, d[0], d[1], d[2], s[0], 215, 190, 255, (int) (alpha * 255));
            BufferUploader.drawWithShader(q.buildOrThrow());
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        return true; // vanilla sky (with the sun) is skipped
    }

    /** Two axes perpendicular to the direction (dx, dy, dz). Returns {ux,uy,uz, vx,vy,vz}. */
    private static float[] axes(float dx, float dy, float dz) {
        float ax = Math.abs(dy) > 0.9f ? 1f : 0f, ay = Math.abs(dy) > 0.9f ? 0f : 1f;
        float ux = ay * dz - 0f * dy, uy = 0f * dx - ax * dz, uz = ax * dy - ay * dx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
        return new float[]{ux, uy, uz, vx, vy, vz};
    }

    private static void quadFacing(BufferBuilder b, Matrix4f m, float dx, float dy, float dz, float size,
                                   int r, int g, int bl, int a, int unused) {
        float[] ax = axes(dx, dy, dz);
        float cx = dx * 100f, cy = dy * 100f, cz = dz * 100f;
        float[][] c = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
        for (float[] k : c) {
            b.addVertex(m, cx + (ax[0] * k[0] + ax[3] * k[1]) * size, cy + (ax[1] * k[0] + ax[4] * k[1]) * size,
                    cz + (ax[2] * k[0] + ax[5] * k[1]) * size).setColor(r, g, bl, a);
        }
    }

    private static void texturedFacing(BufferBuilder b, Matrix4f m, float dx, float dy, float dz, float size,
                                       int r, int g, int bl, int a) {
        float[] ax = axes(dx, dy, dz);
        float cx = dx * 100f, cy = dy * 100f, cz = dz * 100f;
        float[][] c = {{-1, -1, 0, 0}, {-1, 1, 0, 1}, {1, 1, 1, 1}, {1, -1, 1, 0}};
        for (float[] k : c) {
            b.addVertex(m, cx + (ax[0] * k[0] + ax[3] * k[1]) * size, cy + (ax[1] * k[0] + ax[4] * k[1]) * size,
                    cz + (ax[2] * k[0] + ax[5] * k[1]) * size).setUv(k[2], k[3]).setColor(r, g, bl, a);
        }
    }
}
