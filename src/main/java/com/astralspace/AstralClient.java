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
        modBus.addListener(AstralClient::registerSky);
        NeoForge.EVENT_BUS.addListener(AstralClient::onPlaySound);
        NeoForge.EVENT_BUS.addListener(AstralClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(AstralClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(AstralClient::registerCommands);
        NeoForge.EVENT_BUS.addListener(AstralClient::registerGfxCommand);
    }

    private static boolean wasMuted = false;

    /** Stage 5: no sound at all. */
    private static void onPlaySound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event) {
        if (Net.ClientState.muted) event.setSound(null);
    }

    private static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        boolean m = Net.ClientState.muted;
        if (m && !wasMuted) Minecraft.getInstance().getSoundManager().stop(); // cut everything that is playing right now
        wasMuted = m;
    }

    private static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        Net.ClientState.muted = false; // the sound comes back for the next world / server
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
                .then(Commands.literal("sky").then(Commands.argument("mode", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("auto", "custom", "vanilla"), b))
                        .executes(ctx -> {
                            String m = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "mode");
                            AstralSky.skyMode = m.equals("custom") ? 1 : m.equals("vanilla") ? 2 : 0;
                            BarrierRenderer.saveGfx();
                            ctx.getSource().sendSuccess(() -> Component.literal("Astral sky: " + (AstralSky.skyMode == 1 ? "custom (always)"
                                    : AstralSky.skyMode == 2 ? "vanilla / shader pack" : "auto (custom unless a shader pack is on)")), false);
                            return 1;
                        })))
                .then(Commands.literal("swirl").then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(ctx -> {
                    AstralSymbols.swirl = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on");
                    ctx.getSource().sendSuccess(() -> Component.literal("Astral sky swirl: " + (AstralSymbols.swirl ? "on" : "off")), false);
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

    private static void registerSky(net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath("astral_world", "astral_world"), new AstralSky());
        event.register(ResourceLocation.fromNamespaceAndPath("astral_world", "cosmos"), new CosmosSky());
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

        // a rounded, animated bar: dark rim, dark track, flowing gradient fill with a sheen sweeping across it
        double time = System.currentTimeMillis() / 1000.0;
        final int h = barBottom - barTop;
        final float rad = h / 2f;
        pill(g, left - 1, barTop - 1, right + 1, barBottom + 1, h / 2f + 1f, x -> 0xFF160B28);
        final int w = right - left;
        pill(g, left, barTop, right, barBottom, rad, x -> {
            float u = x / (float) w;
            return argb(255, 40 + (int) (14 * u), 24 + (int) (8 * u), 70 + (int) (20 * u)); // dark violet track
        });
        int fill = (int) Math.round((double) w * Math.min(hp, max) / max);
        if (fill > 0) {
            final int fw = fill;
            pill(g, left, barTop, left + fill, barBottom, rad, x -> {
                // the colours flow along the bar: violet -> magenta -> cyan -> violet
                float u = (float) ((x / (float) w) * 0.9 - time * 0.12);
                u -= (float) Math.floor(u);
                int[] c = palette(u);
                // a bright sheen sweeping across
                float sx = (float) ((time * 0.35) % 1.6) * w - 0.3f * w;
                float d = Math.abs(x - sx) / (0.12f * w);
                float sheen = d < 1f ? (1f - d) * 0.45f : 0f;
                int r = Math.min(255, c[0] + (int) ((255 - c[0]) * sheen));
                int gr = Math.min(255, c[1] + (int) ((255 - c[1]) * sheen));
                int b = Math.min(255, c[2] + (int) ((255 - c[2]) * sheen));
                return argb(255, r, gr, b);
            });
            // glossy highlight on the upper half
            pillShade(g, left, barTop, left + fw, barTop + h / 2, rad);
        }

        // text above the bar: the barrier label, then the destination coordinates above that
        g.drawString(font, label, right - font.width(label), barTop - 11, 0xFFE6C8FF, true);
        g.drawString(font, coords, right - font.width(coords), barTop - 22, 0xFF8FD8FF, true);
        g.pose().popPose();
    }


    private static int argb(int a, int r, int g, int b) { return (a << 24) | (r << 16) | (g << 8) | b; }

    private static final int[][] BAR_PALETTE = {{150, 60, 240}, {230, 90, 210}, {70, 190, 255}, {150, 60, 240}};

    /** A colour on the looping violet / magenta / cyan palette, u = 0..1. */
    private static int[] palette(float u) {
        float f = u * (BAR_PALETTE.length - 1);
        int i = Math.min(BAR_PALETTE.length - 2, (int) f);
        float t = f - i;
        t = t * t * (3 - 2 * t);
        int[] a = BAR_PALETTE[i], b = BAR_PALETTE[i + 1];
        return new int[]{(int) (a[0] + (b[0] - a[0]) * t), (int) (a[1] + (b[1] - a[1]) * t), (int) (a[2] + (b[2] - a[2]) * t)};
    }

    /** Vertical inset (pixels) of a column at x of a pill that is w wide, so the ends are round. */
    private static int inset(int x, int w, float rad) {
        float d = Math.min(x + 0.5f, w - x - 0.5f);
        if (d >= rad) return 0;
        float dy = (float) Math.sqrt(Math.max(0f, rad * rad - (rad - d) * (rad - d)));
        return Math.max(0, Math.round(rad - dy));
    }

    /** A rounded bar drawn column by column; `color` gives the colour of each column (x is pixels from the left end). */
    private static void pill(GuiGraphics g, int x0, int y0, int x1, int y1, float rad, java.util.function.IntUnaryOperator color) {
        int w = x1 - x0;
        for (int x = 0; x < w; x++) {
            int in = inset(x, w, rad);
            if (y0 + in >= y1 - in) continue;
            g.fill(x0 + x, y0 + in, x0 + x + 1, y1 - in, color.applyAsInt(x));
        }
    }

    /** A faint white highlight over the top part of a rounded bar. */
    private static void pillShade(GuiGraphics g, int x0, int y0, int x1, int y1, float rad) {
        int w = x1 - x0;
        for (int x = 0; x < w; x++) {
            int in = Math.max(inset(x, w, rad), 1);
            if (y0 + in >= y1) continue;
            g.fill(x0 + x, y0 + in, x0 + x + 1, y1, 0x30FFFFFF);
        }
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
        double time = age / 1000.0;
        drawMovingGradient(g, font, text, 2.0f, g.guiHeight() * 0.28f, alpha, time, Net.ClientState.stageFrom, Net.ClientState.stageTo, true);
        String sub = Net.ClientState.stageSub;
        if (sub != null && !sub.isEmpty())
            drawMovingGradient(g, font, sub, 1.0f, g.guiHeight() * 0.28f + 26f, alpha, time, 0x707080, 0xE0E0F0, false);
    }

    /**
     * Draws `text` centred, each letter coloured from a gradient between `from` and `to` that keeps moving along the line
     * (back and forth), with a soft outline; `bob` also makes the letters bob up and down.
     */
    private static void drawMovingGradient(GuiGraphics g, Font font, String text, float scale, float y, int alpha, double time,
                                           int f, int e, boolean bob) {
        int n = text.length();
        int total = font.width(text);
        int glowAlpha = Math.max(12, alpha / 2);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0f);
        float x = (g.guiWidth() / scale - total) / 2f;
        float baseY = y / scale;
        for (int i = 0; i < n; i++) {
            String s = String.valueOf(text.charAt(i));
            float pos = n > 1 ? i / (float) (n - 1) : 0f;
            // the gradient slides along the text: a triangle wave of (position - time)
            double ph = pos * 1.0 - time * 0.35;
            ph -= Math.floor(ph);
            float t = (float) (ph < 0.5 ? ph * 2 : (1 - ph) * 2);
            t = t * t * (3 - 2 * t);
            int r = (int) (((f >> 16) & 255) + (((e >> 16) & 255) - ((f >> 16) & 255)) * t);
            int gr = (int) (((f >> 8) & 255) + (((e >> 8) & 255) - ((f >> 8) & 255)) * t);
            int b = (int) ((f & 255) + ((e & 255) - (f & 255)) * t);
            int color = (alpha << 24) | (r << 16) | (gr << 8) | b;
            int glow = (glowAlpha << 24) | (Math.max(r, ((e >> 16) & 255)) / 2 << 16) | (Math.max(gr, ((e >> 8) & 255)) / 2 << 8) | (Math.max(b, (e & 255)) / 2);
            int px = Math.round(x);
            int py = Math.round(baseY + (bob ? (float) Math.sin(time * 3.0 + i * 0.6) * 2.0f : 0f));
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
