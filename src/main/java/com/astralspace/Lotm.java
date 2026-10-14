package com.astralspace;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Everything that touches the Lord of the Mysteries mod. LOTM is called by reflection, so this mod compiles
 * and loads without LOTM, and a changed LOTM method only disables that one feature (with a log message).
 */
public final class Lotm {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean loaded;
    private static Method getPathway, addCorruption, getMaxCorruption, getRandomWorkingPathway;
    private static boolean sanityLoaded;
    private static Method getMaxSanity, decreaseSanityUnresisted;

    private Lotm() {}

    private static synchronized void init() {
        if (loaded) return;
        loaded = true;
        try {
            Class<?> util = Class.forName("net.swimmingtuna.lotm.util.BeyonderUtil");
            for (Method m : util.getMethods()) {
                switch (m.getName()) {
                    case "getPathway" -> { if (m.getParameterCount() == 1) getPathway = m; }
                    case "addCorruption" -> { if (m.getParameterCount() == 3) addCorruption = m; }
                    case "getMaxCorruption" -> { if (m.getParameterCount() == 1) getMaxCorruption = m; }
                    case "getRandomWorkingPathway" -> { if (m.getParameterCount() == 0) getRandomWorkingPathway = m; }
                    default -> { }
                }
            }
            LOGGER.info("[astral] LOTM found. getPathway={} addCorruption={} getMaxCorruption={} randomPathway={}",
                    getPathway != null, addCorruption != null, getMaxCorruption != null, getRandomWorkingPathway != null);
        } catch (Throwable t) {
            LOGGER.warn("[astral] LOTM not found or not readable, LOTM features are off: {}", t.toString());
        }
    }

    /** Sequence 0-9 (0 = strongest), or -1 if the player is not a Beyonder. Read from LOTM's saved attachment. */
    public static int sequence(ServerPlayer player) {
        CompoundTag nbt = player.saveWithoutId(new CompoundTag());
        if (!nbt.contains("neoforge:attachments", 10)) return -1;
        CompoundTag attachments = nbt.getCompound("neoforge:attachments");
        if (!attachments.contains("lotm:beyonder_class", 10)) return -1;
        CompoundTag beyonder = attachments.getCompound("lotm:beyonder_class");
        if (!beyonder.contains("currentSequence")) return -1;
        return beyonder.getInt("currentSequence");
    }

    /** The player's pathway object, or null. */
    public static Object pathway(ServerPlayer player) {
        init();
        if (getPathway == null) return null;
        try {
            return getPathway.invoke(null, player);
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not read the pathway of {}: {}", player.getGameProfile().getName(), t.toString());
            return null;
        }
    }

    public static String pathwayName(ServerPlayer player) {
        Object p = pathway(player);
        return p == null ? "unknown" : p.getClass().getSimpleName();
    }

    /** Door is the Apprentice pathway in LOTM. */
    public static boolean isDoor(ServerPlayer player) {
        Object p = pathway(player);
        return p != null && p.getClass().getSimpleName().toLowerCase().contains("apprentice");
    }

    public static Object randomPathway() {
        init();
        if (getRandomWorkingPathway == null) return null;
        try {
            return getRandomWorkingPathway.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Adds `percent` of the player's max corruption to one pathway. Returns false if LOTM could not be called. */
    public static boolean addCorruptionPercent(ServerPlayer player, Object pathway, double percent) {
        init();
        if (addCorruption == null || getMaxCorruption == null || pathway == null) return false;
        try {
            int max = (Integer) getMaxCorruption.invoke(null, player);
            int amount = (int) Math.max(1, Math.round(max * percent / 100.0));
            addCorruption.invoke(null, player, pathway, amount);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not add corruption: {}", t.toString());
            return false;
        }
    }

    private static synchronized void initSanity() {
        if (sanityLoaded) return;
        sanityLoaded = true;
        try {
            Class<?> util = Class.forName("net.swimmingtuna.lotm.capabilities.sanity_data.SanityUtil");
            for (Method m : util.getMethods()) {
                if (m.getName().equals("getMaxSanity") && m.getParameterCount() == 1) getMaxSanity = m;
                if (m.getName().equals("decreaseSanityUnresisted") && m.getParameterCount() == 2) decreaseSanityUnresisted = m;
            }
            LOGGER.info("[astral] LOTM sanity found. getMaxSanity={} decreaseSanityUnresisted={}", getMaxSanity != null, decreaseSanityUnresisted != null);
        } catch (Throwable t) {
            LOGGER.warn("[astral] LOTM sanity not readable, sanity loss is off: {}", t.toString());
        }
    }

    /** Takes `percent` of the player's MAX sanity away (ignoring their sanity-loss resistance). Returns false if LOTM could not be called. */
    public static boolean loseSanityPercent(ServerPlayer player, double percent) {
        initSanity();
        if (getMaxSanity == null || decreaseSanityUnresisted == null) return false;
        try {
            int max = (Integer) getMaxSanity.invoke(null, player);
            int amount = (int) Math.max(1, Math.round(max * percent / 100.0));
            decreaseSanityUnresisted.invoke(null, player, amount);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not lower sanity: {}", t.toString());
            return false;
        }
    }
}
