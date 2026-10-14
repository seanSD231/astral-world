package com.astralspace;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Client only: Lord of the Mysteries pathway symbols (the mod's own textures) that slowly fade in and out in the sky of the
 * Astral World, each time somewhere else. The sky itself (stars, the LOTM moon) is the normal vanilla sky, so shader packs
 * and LOTM draw it exactly as they always did. /astralgfx symbols on|off turns these off if a shader pack draws them badly.
 */
public final class AstralSymbols {
    private static final String[] PATHWAYS = {
            "seer", "marauder", "apprentice", "spectator", "bard", "sailor", "reader", "secrets_supplicant", "sleepless",
            "corpse_collector", "warrior", "assassin", "hunter", "mystery_pryer", "savant", "monster", "planter",
            "apothecary", "criminal", "prisoner", "lawyer", "arbiter"
    };
    private static final int SYMBOLS = 14;
    private static final ResourceLocation ASTRAL = ResourceLocation.fromNamespaceAndPath("astral_world", "astral_world");

    public static volatile boolean enabled = true;
    private static final float[][] symbols = new float[SYMBOLS][]; // {size, phase, speed}

    static {
        Random r = new Random(0xA57A15CAL);
        for (int i = 0; i < SYMBOLS; i++) symbols[i] = new float[]{7f + r.nextFloat() * 6f, r.nextFloat() * 6.283f, 0.12f + r.nextFloat() * 0.2f};
    }

    private AstralSymbols() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(AstralSymbols::onRender);
    }

    private static float[] direction(Random r, float minY) {
        while (true) {
            float x = r.nextFloat() * 2 - 1, y = r.nextFloat() * 2 - 1, z = r.nextFloat() * 2 - 1;
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len < 0.2f || len > 1f || y / len < minY) continue;
            return new float[]{x / len, y / len, z / len};
        }
    }

    private static void onRender(RenderLevelStageEvent event) {
        if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().equals(ASTRAL)) return;
        Matrix4f modelView = event.getModelViewMatrix();

        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        double t = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < SYMBOLS; i++) {
            float[] s = symbols[i];
            double angle = t * s[2] + s[1];
            long cycle = (long) Math.floor(angle / (Math.PI * 2));
            float wave = (float) (0.5 - 0.5 * Math.cos(angle)); // 0 at the start and end of a cycle: invisible, so it can move unseen
            float alpha = wave * wave * 0.8f;
            if (alpha < 0.02f) continue;
            Random pr = new Random(i * 7919L + cycle * 104729L + 17L);
            float[] d = direction(pr, -0.6f); // anywhere, also low and below
            String path = PATHWAYS[(int) ((i * 5 + cycle * 3) % PATHWAYS.length)];
            RenderSystem.setShaderTexture(0, ResourceLocation.fromNamespaceAndPath("lotm", "textures/pathway/" + path + ".png"));

            // two axes across the sky at that direction
            float ax = Math.abs(d[1]) > 0.9f ? 1f : 0f, ay = Math.abs(d[1]) > 0.9f ? 0f : 1f;
            float ux = ay * d[2], uy = -ax * d[2], uz = ax * d[1] - ay * d[0];
            float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
            ux /= ul; uy /= ul; uz /= ul;
            float vx = d[1] * uz - d[2] * uy, vy = d[2] * ux - d[0] * uz, vz = d[0] * uy - d[1] * ux;

            BufferBuilder q = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float cx = d[0] * 100f, cy = d[1] * 100f, cz = d[2] * 100f, size = s[0];
            float[][] c = {{-1, -1, 0, 0}, {-1, 1, 0, 1}, {1, 1, 1, 1}, {1, -1, 1, 0}};
            for (float[] k : c) {
                q.addVertex(modelView, cx + (ux * k[0] + vx * k[1]) * size, cy + (uy * k[0] + vy * k[1]) * size,
                        cz + (uz * k[0] + vz * k[1]) * size).setUv(k[2], k[3]).setColor(215, 190, 255, (int) (alpha * 255));
            }
            BufferUploader.drawWithShader(q.buildOrThrow());
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
