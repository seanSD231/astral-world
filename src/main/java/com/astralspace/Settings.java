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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Server settings, in config/astral_world/settings.properties (created on first start, /astralsettings reload).
 * Keys that are missing from an older file are added automatically with their default value.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class Settings {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** key -> {default, comment} (in file order) */
    private static final Map<String, String[]> KEYS = new LinkedHashMap<>();
    static {
        KEYS.put("surface_top", new String[]{"320", "Height band of the REAL overworld. When a player leaves the Astral World the ground is searched from\n# surface_top DOWN to surface_bottom, so with a stacked dimension (OneDimension) the Nether/End layers are never\n# mistaken for the overworld's ground."});
        KEYS.put("surface_bottom", new String[]{"-64", null});
        KEYS.put("astral_barrier_y", new String[]{"200", "The Y level of the Astral Barrier in the sky of the Astral World (players only see it there). The Astral World is 0-255 high."});
        KEYS.put("stage3_days", new String[]{"7", "Real-life days from the start of stage 2 until stage 3 begins (decimals allowed, e.g. 0.01 to test)."});
        KEYS.put("barrier_damage_minutes", new String[]{"60", "Stage 3: one barrier damage every this many minutes, before the online-Seq-0 multiplier (halved with no Seq 0\n# online, doubled for every Seq 0 online beyond the first)."});
        KEYS.put("nearby_radius", new String[]{"32", "Stage 2+: how close (blocks) players must be to someone who names an Outer God to be affected. Doubled in stage 3."});
    }

    public static volatile int surfaceTop = 320;
    public static volatile int surfaceBottom = -64;
    public static volatile int barrierY = 200;
    public static volatile double stage3Days = 7.0;
    public static volatile double barrierDamageMinutes = 60.0;
    public static volatile int nearbyRadius = 32;

    private Settings() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("astral_world").resolve("settings.properties");
    }

    public static void load() {
        Path file = file();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, "# Astral World settings. Use /astralsettings reload after editing.\n");
            }
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(file)) { p.load(r); }

            // add any keys an older file does not have yet
            StringBuilder add = new StringBuilder();
            for (Map.Entry<String, String[]> e : KEYS.entrySet()) {
                if (p.getProperty(e.getKey()) != null) continue;
                add.append("\n");
                if (e.getValue()[1] != null) add.append("# ").append(e.getValue()[1]).append("\n");
                add.append(e.getKey()).append("=").append(e.getValue()[0]).append("\n");
                p.setProperty(e.getKey(), e.getValue()[0]);
            }
            if (add.length() > 0) Files.writeString(file, add.toString(), java.nio.file.StandardOpenOption.APPEND);

            surfaceTop = Integer.parseInt(p.getProperty("surface_top").trim());
            surfaceBottom = Integer.parseInt(p.getProperty("surface_bottom").trim());
            barrierY = Integer.parseInt(p.getProperty("astral_barrier_y").trim());
            stage3Days = Double.parseDouble(p.getProperty("stage3_days").trim());
            barrierDamageMinutes = Math.max(0.01, Double.parseDouble(p.getProperty("barrier_damage_minutes").trim()));
            nearbyRadius = Integer.parseInt(p.getProperty("nearby_radius").trim());
            LOGGER.info("[astral] settings: surface {} down to {}, astral_barrier_y {}, stage3_days {}, damage every {} min, nearby_radius {}",
                    surfaceTop, surfaceBottom, barrierY, stage3Days, barrierDamageMinutes, nearbyRadius);
        } catch (IOException | NumberFormatException e) {
            LOGGER.warn("[astral] could not read settings.properties, using defaults: {}", e.toString());
        }
    }

    public static String summary() {
        return "surface " + surfaceTop + " down to " + surfaceBottom + ", astral_barrier_y " + barrierY + ", stage3_days " + stage3Days
                + ", barrier damage every " + barrierDamageMinutes + " min, nearby_radius " + nearbyRadius;
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) { load(); }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("astralsettings")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("reload").executes(ctx -> {
                    load();
                    ctx.getSource().sendSuccess(() -> Component.literal("Reloaded: " + summary()), true);
                    return 1;
                }))
                .then(Commands.literal("info").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(summary()), false);
                    return 1;
                })));
    }
}
