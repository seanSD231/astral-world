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
        KEYS.put("astral_barrier_level", new String[]{"170", "The height (Y) of the Astral Barrier in the sky of the Astral World. Lower number = closer. It is never drawn higher than the game\n# can still render it (about 2.4 x the player's render distance above them)."});
        KEYS.put("overworld_barrier_y", new String[]{"1000", "The same for the overworld sky. It is always drawn at least as high as the game allows, never below this height unless the render distance is too small."});
        KEYS.put("stage3_days", new String[]{"7", "Real-life days from the start of stage 2 until stage 3 begins (decimals allowed, e.g. 0.01 to test)."});
        KEYS.put("barrier_damage_minutes", new String[]{"60", "Stage 3: one barrier damage every this many minutes, before the online-Seq-0 multiplier (halved with no Seq 0\n# online, doubled for every Seq 0 online beyond the first)."});
        KEYS.put("stage4_days", new String[]{"30", "Real-life days from the start of stage 3 until stage 4 begins (decimals allowed, e.g. 0.01 to test)."});
        KEYS.put("stage4_damage_multiplier", new String[]{"5", "Stage 4: how many times faster the barrier takes its normal damage."});
        KEYS.put("evacuation_minutes", new String[]{"10", "Stage 5 with a living Door (Seq 2 or stronger): real minutes the players have to get 75% of everyone onto the other planets."});
        KEYS.put("boss_days", new String[]{"3", "After the evacuation: real days to defeat the planet bosses (each boss defeated adds this many days again)."});
        KEYS.put("recovery_days", new String[]{"10", "After the raid boss is beaten: real days the world stays at stage 0 before it returns to its earlier stage."});
        KEYS.put("chaos_destroys_blocks", new String[]{"false", "Stage 5 (the end): true = meteors and explosions also destroy blocks. The world is kept when the server resets,\n# so this is off by default."});
        KEYS.put("weekly_packs_default", new String[]{"1", "Weekly tarot packs (/weeklypacks): how many packs a player without an FTB Ranks node may pick each week. Ranks set their own number\n# with the permission node astral_world.weekly_packs, and allow Rainbow / Shiny packs with astral_world.weekly_packs.rainbow and .shiny\n# (a number = that many of the packs, true = any of them). Use 0 to give nothing to players without a rank."});
        KEYS.put("weekly_reset_day", new String[]{"1", "Weekly tarot packs: the day the week resets, 1 = Monday ... 7 = Sunday (server time)."});
        KEYS.put("raid_full_damage_players", new String[]{"3", "The Mother Tree raid: up to this many fighters hurt her at full strength. Past it, every hit is scaled down so that any\n# number of fighters together do what this many would: the fight stays as long with thirty as it is with three."});
        KEYS.put("uniqueness_use_corruption", new String[]{"12", "Uniquenesses are sealed artifacts: nobody can take one in. Percent of the corruption limit each use costs, on that\n# item's own pathway (it used to be 50, and only for someone who had not accommodated it)."});
        KEYS.put("uniqueness_carry_corruption", new String[]{"1", "Percent of the corruption limit that simply carrying a Uniqueness costs, every ten seconds. While one is on you,\n# you also lose two hearts of maximum health and mine slowly. 0 turns the carrying price off."});
        KEYS.put("hide_title_messages", new String[]{"true", "Keep the title mod's \"you unlocked a title\" lines out of chat (titles are still granted)."});
        KEYS.put("nearby_radius", new String[]{"32", "Stage 2+: how close (blocks) players must be to someone who names an Outer God to be affected. Doubled in stage 3."});
    }

    public static volatile int surfaceTop = 320;
    public static volatile int surfaceBottom = -64;
    public static volatile int barrierY = 170;
    public static volatile int barrierYOverworld = 1000;
    public static volatile double stage3Days = 7.0;
    public static volatile double barrierDamageMinutes = 60.0;
    public static volatile int nearbyRadius = 32;
    public static volatile double stage4Days = 30.0;
    public static volatile double stage4DamageMultiplier = 5.0;
    public static volatile boolean chaosDestroysBlocks = false;
    public static volatile double evacuationMinutes = 10.0;
    public static volatile double bossDays = 3.0;
    public static volatile double recoveryDays = 10.0;
    public static volatile int weeklyPacksDefault = 1;
    public static volatile int weeklyResetDay = 1;
    public static volatile boolean hideTitleMessages = true;
    public static volatile int raidFullDamagePlayers = 3;
    public static volatile int uniqUseCorruption = 12;
    public static volatile int uniqCarryCorruption = 1;

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

            // the old default (320) is moved to the new default (170); any other value you set yourself is kept
            if ("320".equals(p.getProperty("astral_barrier_level").trim())) {
                Files.writeString(file, Files.readString(file).replaceAll("(?m)^astral_barrier_level\\s*=\\s*320\\s*$", "astral_barrier_level=170"));
                p.setProperty("astral_barrier_level", "170");
            }
            surfaceTop = Integer.parseInt(p.getProperty("surface_top").trim());
            surfaceBottom = Integer.parseInt(p.getProperty("surface_bottom").trim());
            barrierY = Integer.parseInt(p.getProperty("astral_barrier_level").trim());
            barrierYOverworld = Integer.parseInt(p.getProperty("overworld_barrier_y").trim());
            stage3Days = Double.parseDouble(p.getProperty("stage3_days").trim());
            barrierDamageMinutes = Math.max(0.01, Double.parseDouble(p.getProperty("barrier_damage_minutes").trim()));
            nearbyRadius = Integer.parseInt(p.getProperty("nearby_radius").trim());
            stage4Days = Double.parseDouble(p.getProperty("stage4_days").trim());
            stage4DamageMultiplier = Math.max(1.0, Double.parseDouble(p.getProperty("stage4_damage_multiplier").trim()));
            chaosDestroysBlocks = Boolean.parseBoolean(p.getProperty("chaos_destroys_blocks").trim());
            evacuationMinutes = Double.parseDouble(p.getProperty("evacuation_minutes").trim());
            bossDays = Double.parseDouble(p.getProperty("boss_days").trim());
            recoveryDays = Double.parseDouble(p.getProperty("recovery_days").trim());
            weeklyPacksDefault = Math.max(0, Integer.parseInt(p.getProperty("weekly_packs_default").trim()));
            weeklyResetDay = Math.max(1, Math.min(7, Integer.parseInt(p.getProperty("weekly_reset_day").trim())));
            hideTitleMessages = Boolean.parseBoolean(p.getProperty("hide_title_messages").trim());
            raidFullDamagePlayers = Math.max(1, Integer.parseInt(p.getProperty("raid_full_damage_players").trim()));
            uniqUseCorruption = Math.max(0, Integer.parseInt(p.getProperty("uniqueness_use_corruption").trim()));
            uniqCarryCorruption = Math.max(0, Integer.parseInt(p.getProperty("uniqueness_carry_corruption").trim()));
            LOGGER.info("[astral] settings: surface {} down to {}, astral_barrier_level {}, stage3_days {}, damage every {} min, nearby_radius {}",
                    surfaceTop, surfaceBottom, barrierY, stage3Days, barrierDamageMinutes, nearbyRadius);
        } catch (IOException | NumberFormatException e) {
            LOGGER.warn("[astral] could not read settings.properties, using defaults: {}", e.toString());
        }
    }

    public static String summary() {
        return "surface " + surfaceTop + " down to " + surfaceBottom + ", astral_barrier_level " + barrierY + ", stage3_days " + stage3Days
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
