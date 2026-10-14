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
}
