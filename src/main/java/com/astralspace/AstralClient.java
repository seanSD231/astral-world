package com.astralspace;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Client side only: draws the Astral Barrier bar and the teleport coordinates in the bottom-right corner. */
public final class AstralClient {
    private AstralClient() {}

    // HUD size and position (client side, per player). Saved in config/astral_world_hud.properties
    private static float hudScale = 0.7f;
    private static int hudOffsetX = 0; // pixels the HUD is moved left from the corner (can be negative)
    private static int hudOffsetY = 0; // pixels the HUD is moved up from the corner (can be negative)

    private static Path hudFile() { return FMLPaths.CONFIGDIR.get().resolve("astral_world_hud.properties"); }

    private static void loadHud() {
        try {
            Path f = hudFile();
            if (!Files.exists(f)) return;
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(f)) { p.load(r); }
            hudScale = Math.max(0.3f, Math.min(2.0f, Float.parseFloat(p.getProperty("scale", "0.7"))));
            hudOffsetX = Integer.parseInt(p.getProperty("offset_x", "0"));
            hudOffsetY = Integer.parseInt(p.getProperty("offset_y", "0"));
        } catch (IOException | NumberFormatException ignored) { }
    }

    private static void saveHud() {
        try {
            Properties p = new Properties();
            p.setProperty("scale", String.valueOf(hudScale));
            p.setProperty("offset_x", String.valueOf(hudOffsetX));
            p.setProperty("offset_y", String.valueOf(hudOffsetY));
            try (Writer w = Files.newBufferedWriter(hudFile())) { p.store(w, "Astral World HUD (change with /astralhud)"); }
        } catch (IOException ignored) { }
    }

    public static void init(IEventBus modBus) {
        loadHud();
        BarrierRenderer.init();
        AstralSymbols.init();
        modBus.addListener(AstralClient::registerLayers);
        NeoForge.EVENT_BUS.addListener(AstralClient::registerCommands);
        NeoForge.EVENT_BUS.addListener(AstralClient::registerGfxCommand);
    }

    private static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("astralhud")
                .then(Commands.literal("scale").then(Commands.argument("size", FloatArgumentType.floatArg(0.3f, 2.0f)).executes(ctx -> {
                    hudScale = FloatArgumentType.getFloat(ctx, "size");
                    saveHud();
                    ctx.getSource().sendSuccess(() -> Component.literal("Astral HUD scale: " + hudScale + " (1.0 = full size)"), false);
                    return 1;
                })))
                .then(Commands.literal("move")
                        .then(Commands.argument("left", IntegerArgumentType.integer(-2000, 2000))
                                .then(Commands.argument("up", IntegerArgumentType.integer(-2000, 2000)).executes(ctx -> {
                                    hudOffsetX = IntegerArgumentType.getInteger(ctx, "left");
                                    hudOffsetY = IntegerArgumentType.getInteger(ctx, "up");
                                    saveHud();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Astral HUD moved " + hudOffsetX + " left, " + hudOffsetY + " up"), false);
                                    return 1;
                                }))))
                .then(Commands.literal("reset").executes(ctx -> {
                    hudScale = 0.7f; hudOffsetX = 0; hudOffsetY = 0;
                    saveHud();
                    ctx.getSource().sendSuccess(() -> Component.literal("Astral HUD reset"), false);
                    return 1;
                }))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Astral HUD: scale " + hudScale + ", offset " + hudOffsetX + " left / " + hudOffsetY
                                    + " up.  /astralhud scale <0.3-2.0>   /astralhud move <left> <up>   /astralhud reset"), false);
                    return 1;
                }));
    }

    private static void registerGfxCommand(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("astralgfx")
                .then(Commands.literal("cracks").then(Commands.argument("mode", IntegerArgumentType.integer(0, 3)).executes(ctx -> {
                    BarrierRenderer.crackMode = IntegerArgumentType.getInteger(ctx, "mode");
                    BarrierRenderer.saveGfx();
                    ctx.getSource().sendSuccess(() -> Component.literal("Astral barrier: " + BarrierRenderer.describe()), false);
                    return 1;
                })))
                .then(Commands.literal("plane").then(Commands.argument("mode", IntegerArgumentType.integer(0, 3)).executes(ctx -> {
                    BarrierRenderer.planeMode = IntegerArgumentType.getInteger(ctx, "mode");
                    BarrierRenderer.saveGfx();
                    ctx.getSource().sendSuccess(() -> Component.literal("Astral barrier: " + BarrierRenderer.describe()), false);
                    return 1;
                })))
                .then(Commands.literal("symbols").then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(ctx -> {
                    AstralSymbols.enabled = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on");
                    ctx.getSource().sendSuccess(() -> Component.literal("Astral sky symbols: " + (AstralSymbols.enabled ? "on" : "off")), false);
                    return 1;
                })))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Astral barrier: " + BarrierRenderer.describe()
                            + "   (0 flat, 1 lit like an entity, 2 gui, 3 additive glow)   /astralgfx cracks <0-3>   /astralgfx plane <0-3>"), false);
                    return 1;
                }));
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "hud"), AstralClient::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "stage_text"), AstralClient::renderStageText);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "flash"), AstralClient::renderFlash);
    }

    /** A white flash over the whole screen that fades out (the explosions of the Outer Gods; at most ~50% white). */
    private static void renderFlash(GuiGraphics g, DeltaTracker delta) {
        long age = System.currentTimeMillis() - Net.ClientState.flashStart;
        int ms = Net.ClientState.flashMs;
        if (age < 0 || age >= ms) return;
        float t = 1f - age / (float) ms;
        int alpha = (int) (Net.ClientState.flashAlpha * t * t);
        if (alpha < 2) return;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | 0xFFFFFF);
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
        float scale = hudScale;
        int margin = 4;
        // work in scaled coordinates, anchored to the bottom-right corner
        int right = Math.round((g.guiWidth() - margin - hudOffsetX) / scale);
        int barBottom = Math.round((g.guiHeight() - margin - hudOffsetY) / scale);
        int left = right - width;
        int barTop = barBottom - 9;

        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0f);

        // bar frame, background, fill
        g.fill(left - 1, barTop - 1, right + 1, barBottom + 1, 0xFF1B0F2E);
        g.fill(left, barTop, right, barBottom, 0xFF3A2A55);
        int fill = (int) Math.round((double) width * Math.min(hp, max) / max);
        if (fill > 0) g.fill(left, barTop, left + fill, barBottom, 0xFFB45CFF);

        // text above the bar: the barrier label, then the destination coordinates above that
        g.drawString(font, label, right - font.width(label), barTop - 11, 0xFFE6C8FF, true);
        g.drawString(font, coords, right - font.width(coords), barTop - 22, 0xFF8FD8FF, true);
        g.pose().popPose();
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
            int f = Net.ClientState.stageFrom, e = Net.ClientState.stageTo;
            int r = (int) (((f >> 16) & 255) + (((e >> 16) & 255) - ((f >> 16) & 255)) * t);
            int gr = (int) (((f >> 8) & 255) + (((e >> 8) & 255) - ((f >> 8) & 255)) * t);
            int b = (int) ((f & 255) + ((e & 255) - (f & 255)) * t);
            int color = (alpha << 24) | (r << 16) | (gr << 8) | b;
            int glow = (glowAlpha << 24) | (((e >> 16) & 255) / 2 << 16) | (((e >> 8) & 255) / 2 << 8) | ((e & 255) / 2);
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
