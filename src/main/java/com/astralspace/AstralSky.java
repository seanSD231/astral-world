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
 * The sky of the Astral World (client only): black, with Minecraft's stars recoloured, the Lord of the Mysteries moon
 * (same size, and the crimson texture whenever LOTM's crimson moon is up) and no sun.
 * Shader packs (Photon) draw the sky themselves and ignore custom skies, so in mode "auto" this sky is only used when no
 * shader pack is active. /astralgfx sky custom|vanilla|auto changes that.
 */
public final class AstralSky extends DimensionSpecialEffects {
    /** 0 = auto (custom sky unless a shader pack is on), 1 = always custom, 2 = always vanilla/pack sky. */
    public static volatile int skyMode = 0;

    private static final ResourceLocation MOON = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    private static final ResourceLocation CRIMSON_MOON = ResourceLocation.fromNamespaceAndPath("lotm", "textures/environment/crimson_moon_phases.png");

    private static boolean shaderPackActive() {
        try {
            Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object api = c.getMethod("getInstance").invoke(null);
            return (Boolean) c.getMethod("isShaderPackInUse").invoke(api);
        } catch (Throwable t) { return false; }
    }

    private static boolean crimsonMoon() {
        try {
            return (Boolean) Class.forName("net.swimmingtuna.lotm.util.ClientData.ClientCrimsonMoonData").getMethod("isCrimsonMoon").invoke(null);
        } catch (Throwable t) { return false; }
    }

    /** Star colours, like real stars: mostly white, some blue, yellow, orange and red ones. {r, g, b, weight} */
    private static final int[][] STAR_COLORS = {
            {255, 255, 255, 55}, {170, 200, 255, 15}, {255, 240, 180, 12}, {255, 190, 130, 10}, {255, 160, 170, 8}
    };

    private final float[][] stars = new float[1500][];      // {x, y, z, size, alpha, r, g, b}

    public AstralSky() {
        super(Float.NaN, true, SkyType.NORMAL, false, false);
        Random r = new Random(10842L); // the seed Minecraft uses for its stars
        for (int i = 0; i < stars.length; i++) {
            float[] d = direction(r, -1.0f); // the whole sky, under the player too
            int pick = r.nextInt(100), acc = 0, ci = 0;
            for (int k = 0; k < STAR_COLORS.length; k++) { acc += STAR_COLORS[k][3]; if (pick < acc) { ci = k; break; } }
            stars[i] = new float[]{d[0], d[1], d[2], 0.15f + r.nextFloat() * 0.1f, 0.5f + r.nextFloat() * 0.5f,
                    STAR_COLORS[ci][0], STAR_COLORS[ci][1], STAR_COLORS[ci][2]};
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
        boolean custom = skyMode == 1 || (skyMode == 0 && !shaderPackActive());
        if (!custom) return false; // vanilla / shader pack sky
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);

        // no stars: the swirling colours and symbols of the Astral World are drawn by AstralSymbols

        // no moon and no sun here: they only appear once a Moon god / Sun god exists (see AstralGods)

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
