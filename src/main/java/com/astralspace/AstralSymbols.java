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
    private static final int SYMBOLS = 12;
    private static final ResourceLocation ASTRAL = ResourceLocation.fromNamespaceAndPath("astral_world", "astral_world");

    public static volatile boolean enabled = true;
    public static volatile boolean swirl = true;
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

    /** True if a direction is close to the sign of a pathway that has a god (those signs must stay clear). */
    private static boolean nearGod(float[] d) {
        String gods = Net.ClientState.gods == null ? "," : Net.ClientState.gods;
        for (String id : AstralGods.ALL) {
            if (!gods.contains("," + id + ",")) continue;
            float[] a = AstralGods.anchor(id);
            if (d[0] * a[0] + d[1] * a[1] + d[2] * a[2] > 0.88f) return true; // within about 28 degrees
        }
        return false;
    }

    private static void onRender(RenderLevelStageEvent event) {
        if ((!enabled && !swirl) || event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().equals(ASTRAL)) return;
        Matrix4f modelView = event.getModelViewMatrix();

        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        double t = System.currentTimeMillis() / 1000.0;
        AstralGods.draw(modelView, t, swirl, mc.level.getMoonPhase());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        for (int i = 0; enabled && i < SYMBOLS; i++) {
            float[] s = symbols[i];
            double angle = t * s[2] + s[1];
            long cycle = (long) Math.floor(angle / (Math.PI * 2));
            float wave = (float) (0.5 - 0.5 * Math.cos(angle)); // 0 at the start and end of a cycle: invisible, so it can move unseen
            float alpha = wave * wave * 0.8f;
            if (alpha < 0.02f) continue;
            Random pr = new Random(i * 7919L + cycle * 104729L + 17L);
            float[] d = direction(pr, -0.6f); // anywhere, also low and below, but never on top of a god's sign
            for (int tries = 0; tries < 12 && nearGod(d); tries++) d = direction(pr, -0.6f);
            String path = PATHWAYS[(int) ((i * 5 + cycle * 3) % PATHWAYS.length)];
            RenderSystem.setShaderTexture(0, ResourceLocation.fromNamespaceAndPath("lotm", "textures/pathway/" + path + ".png"));

            // two axes across the sky at that direction
            float ax = Math.abs(d[1]) > 0.9f ? 1f : 0f, ay = Math.abs(d[1]) > 0.9f ? 0f : 1f;
            float ux = ay * d[2], uy = -ax * d[2], uz = ax * d[1] - ay * d[0];
            float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
            ux /= ul; uy /= ul; uz /= ul;
            float vx = d[1] * uz - d[2] * uy, vy = d[2] * ux - d[0] * uz, vz = d[0] * uy - d[1] * ux;
            if (vy < 0) { vx = -vx; vy = -vy; vz = -vz; } // "up" on the symbol points up in the world
            ux = d[1] * vz - d[2] * vy; uy = d[2] * vx - d[0] * vz; uz = d[0] * vy - d[1] * vx; // right-hand side, so it is not mirrored

            BufferBuilder q = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float cx = d[0] * 100f, cy = d[1] * 100f, cz = d[2] * 100f, size = s[0];
            float[][] c = {{-1, -1, 0, 1}, {-1, 1, 0, 0}, {1, 1, 1, 0}, {1, -1, 1, 1}};
            for (float[] k : c) {
                q.addVertex(modelView, cx + (ux * k[0] + vx * k[1]) * size, cy + (uy * k[0] + vy * k[1]) * size,
                        cz + (uz * k[0] + vz * k[1]) * size).setUv(k[2], k[3]).setColor(215, 190, 255, (int) (alpha * 255));
            }
            BufferUploader.drawWithShader(q.buildOrThrow());
        }

        // every god has its own pathway symbol, bigger and brighter, at its own place in the sky
        String gods = Net.ClientState.gods == null ? "," : Net.ClientState.gods;
        for (int gi = 0; enabled && gi < AstralGods.ALL.length; gi++) {
            String id = AstralGods.ALL[gi];
            if (!gods.contains("," + id + ",")) continue;
            float[] d = AstralGods.symbolSpot(id);
            float alpha = 0.55f + 0.35f * (float) Math.sin(t * 0.5 + gi);
            RenderSystem.setShaderTexture(0, ResourceLocation.fromNamespaceAndPath("lotm", "textures/pathway/" + id + ".png"));
            drawSymbol(modelView, d, 0.0f, 7f, alpha, 255, 235, 190);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawSymbol(Matrix4f modelView, float[] d, float unused, float size, float alpha, int cr, int cg, int cb) {
        float ax = Math.abs(d[1]) > 0.9f ? 1f : 0f, ay = Math.abs(d[1]) > 0.9f ? 0f : 1f;
        float ux = ay * d[2], uy = -ax * d[2], uz = ax * d[1] - ay * d[0];
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        float vx = d[1] * uz - d[2] * uy, vy = d[2] * ux - d[0] * uz, vz = d[0] * uy - d[1] * ux;
        if (vy < 0) { vx = -vx; vy = -vy; vz = -vz; }
        ux = d[1] * vz - d[2] * vy; uy = d[2] * vx - d[0] * vz; uz = d[0] * vy - d[1] * vx;
        BufferBuilder q = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float cx = d[0] * 100f, cy = d[1] * 100f, cz = d[2] * 100f;
        float[][] c = {{-1, -1, 0, 1}, {-1, 1, 0, 0}, {1, 1, 1, 0}, {1, -1, 1, 1}};
        for (float[] k : c) {
            q.addVertex(modelView, cx + (ux * k[0] + vx * k[1]) * size, cy + (uy * k[0] + vy * k[1]) * size,
                    cz + (uz * k[0] + vz * k[1]) * size).setUv(k[2], k[3]).setColor(cr, cg, cb, (int) (Math.max(0f, Math.min(1f, alpha)) * 255));
        }
        BufferUploader.drawWithShader(q.buildOrThrow());
    }
}
