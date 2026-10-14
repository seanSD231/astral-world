package com.astralspace;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Client side only: draws the Astral Barrier bar and the teleport coordinates in the bottom-right corner. */
public final class AstralClient {
    private AstralClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(AstralClient::registerLayers);
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "hud"), AstralClient::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "stage_text"), AstralClient::renderStageText);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (!Net.ClientState.show) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        Font font = mc.font;

        int hp = Net.ClientState.hp, max = Math.max(1, Net.ClientState.max);
        String coords = Net.ClientState.coords;
        String label = "Astral Barrier  " + hp + " / " + Net.ClientState.max;

        int width = Math.max(130, Math.max(font.width(coords), font.width(label)) + 10);
        int margin = 6;
        int right = g.guiWidth() - margin;
        int left = right - width;
        int barBottom = g.guiHeight() - margin;
        int barTop = barBottom - 9;

        // bar frame, background, fill
        g.fill(left - 1, barTop - 1, right + 1, barBottom + 1, 0xFF1B0F2E);
        g.fill(left, barTop, right, barBottom, 0xFF3A2A55);
        int fill = (int) Math.round((double) width * Math.min(hp, max) / max);
        if (fill > 0) g.fill(left, barTop, left + fill, barBottom, 0xFFB45CFF);

        // text above the bar: the barrier label, then the destination coordinates above that
        g.drawString(font, label, right - font.width(label), barTop - 11, 0xFFE6C8FF, true);
        g.drawString(font, coords, right - font.width(coords), barTop - 22, 0xFF8FD8FF, true);
    }

    /**
     * The story message: a black-to-crimson gradient where every letter bobs up and down on its own,
     * fading in and out. Black letters get a dim crimson outline so they can be read on dark backgrounds.
     */
    private static void renderStageText(GuiGraphics g, DeltaTracker delta) {
        String text = Net.ClientState.stageText;
        if (text == null || text.isEmpty()) return;
        final long life = 9000, fadeIn = 900, fadeOut = 2500;
        long age = System.currentTimeMillis() - Net.ClientState.stageStart;
        if (age < 0 || age > life) return;
        float a = age < fadeIn ? age / (float) fadeIn : (age > life - fadeOut ? (life - age) / (float) fadeOut : 1f);
        int alpha = (int) (a * 255);
        if (alpha < 12) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        Font font = mc.font;
        float scale = 2.0f;
        int n = text.length();
        int total = font.width(text);
        double time = age / 1000.0;

        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0f);
        float x = (g.guiWidth() / scale - total) / 2f;
        float baseY = g.guiHeight() * 0.28f / scale;
        int glowAlpha = Math.max(12, alpha / 2);
        for (int i = 0; i < n; i++) {
            String s = String.valueOf(text.charAt(i));
            float t = n > 1 ? i / (float) (n - 1) : 0f;
            t = Math.max(0f, Math.min(1f, t + 0.10f * (float) Math.sin(time * 2.0 - i * 0.5)));
            int r = (int) (220 * t), gr = (int) (20 * t), b = (int) (60 * t);
            int color = (alpha << 24) | (r << 16) | (gr << 8) | b;
            int glow = (glowAlpha << 24) | 0x7A0A1E;
            int px = Math.round(x);
            int py = Math.round(baseY + (float) Math.sin(time * 3.0 + i * 0.6) * 2.0f);
            g.drawString(font, s, px - 1, py, glow, false);
            g.drawString(font, s, px + 1, py, glow, false);
            g.drawString(font, s, px, py - 1, glow, false);
            g.drawString(font, s, px, py + 1, glow, false);
            g.drawString(font, s, px, py, color, false);
            x += font.width(s);
        }
        g.pose().popPose();
    }
}
