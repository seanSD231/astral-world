package com.astralspace;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Server settings, in config/astral_world/settings.properties (created on first start, /astralsettings reload).
 *
 * surface_top / surface_bottom: the height band of the REAL overworld. With the OneDimension mod the overworld,
 * Nether and End share one tall dimension, so "the ground at X/Z" must only be searched inside the overworld's
 * band, scanning down from surface_top. Without this the Astral World pointed at the top of the whole stack.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class Settings {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static volatile int surfaceTop = 320;
    public static volatile int surfaceBottom = -64;

    private Settings() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("astral_world").resolve("settings.properties");
    }

    public static void load() {
        Path file = file();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, String.join("\n",
                        "# Astral World settings. Use /astralsettings reload after editing.",
                        "#",
                        "# The height band of the real overworld. When a player leaves the Astral World the ground is searched",
                        "# from surface_top DOWN to surface_bottom, so with a stacked dimension (OneDimension) the Nether and",
                        "# End layers are never mistaken for the overworld's ground.",
                        "surface_top=320",
                        "surface_bottom=-64",
                        ""));
            }
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(file)) { p.load(r); }
            surfaceTop = Integer.parseInt(p.getProperty("surface_top", "320").trim());
            surfaceBottom = Integer.parseInt(p.getProperty("surface_bottom", "-64").trim());
            LOGGER.info("[astral] surface search band: {} down to {}", surfaceTop, surfaceBottom);
        } catch (IOException | NumberFormatException e) {
            LOGGER.warn("[astral] could not read settings.properties, using defaults: {}", e.toString());
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) { load(); }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("astralsettings")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("reload").executes(ctx -> {
                    load();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Surface search band: " + surfaceTop + " down to " + surfaceBottom), true);
                    return 1;
                }))
                .then(Commands.literal("info").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Surface search band: " + surfaceTop + " down to " + surfaceBottom), false);
                    return 1;
                })));
    }
}
