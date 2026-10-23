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
                    case "chooseRandomPathway" -> { if (m.getParameterCount() == 0 && getRandomWorkingPathway == null) getRandomWorkingPathway = m; }   // the newer LOTM's name for it
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

    private static final String[] PATH_IDS = {"seer", "marauder", "apprentice", "spectator", "bard", "sailor", "reader", "secrets_supplicant",
            "sleepless", "corpse_collector", "warrior", "assassin", "hunter", "mystery_pryer", "savant", "monster", "planter",
            "apothecary", "criminal", "prisoner", "lawyer", "arbiter"};

    /** The LOTM pathway id of a player ("sailor" = Tyrant, "bard" = Sun, "apothecary" = Moon ...), or "" if unknown. */
    public static String pathwayId(ServerPlayer player) {
        String n = pathwayName(player).toLowerCase().replace("_", "");
        for (String id : PATH_IDS) if (n.contains(id.replace("_", ""))) return id;
        return "";
    }

    /** Door is the Apprentice pathway in LOTM. */
    public static boolean isDoor(ServerPlayer player) {
        Object p = pathway(player);
        return p != null && p.getClass().getSimpleName().toLowerCase().contains("apprentice");
    }

    /** A LOTM pathway by its id ("planter" = Mother, "assassin" = Demoness, "criminal" = Abyss ...), or null. */
    public static Object pathwayById(String id) {
        try {
            Class<?> init = Class.forName("net.swimmingtuna.lotm.init.BeyonderClassInit");
            Object opt = init.getMethod("getBeyonderClassByName", String.class).invoke(null, id);
            return ((java.util.Optional<?>) opt).orElse(null);
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not look up pathway '{}': {}", id, t.toString());
            return null;
        }
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
        if (pathway == null) pathway = pathway(player);   // no random pathway to be had: the player's own takes it
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
                if (m.getName().equals("setSanity") && m.getParameterCount() == 2) setSanityM = m;
                if (m.getName().equals("getSanity") && m.getParameterCount() == 1) getSanityM = m;
            }
            LOGGER.info("[astral] LOTM sanity found. getMaxSanity={} decreaseSanityUnresisted={}", getMaxSanity != null, decreaseSanityUnresisted != null);
        } catch (Throwable t) {
            LOGGER.warn("[astral] LOTM sanity not readable, sanity loss is off: {}", t.toString());
        }
    }

    private static Method setSanityM, getSanityM;
    private static Method holderOf, holderSetSequence;
    private static boolean holderFailed;

    /** Current sanity as a percent of the maximum, or -1 if LOTM could not be called. */
    public static double sanityPercent(ServerPlayer player) {
        initSanity();
        if (getMaxSanity == null || getSanityM == null) return -1;
        try {
            int max = (Integer) getMaxSanity.invoke(null, player);
            return max <= 0 ? -1 : 100.0 * (Integer) getSanityM.invoke(null, player) / max;
        } catch (Throwable t) { return -1; }
    }

    /** Puts a player's sequence back (LOTM's own loss-of-control and sanity collapse regress it). Returns false if LOTM could not be called. */
    public static boolean setSequence(ServerPlayer player, int seq) {
        if (holderFailed) return false;
        try {
            if (holderOf == null) {
                Class<?> att = Class.forName("net.swimmingtuna.lotm.caps.BeyonderHolderAttacher");
                Class<?> hold = Class.forName("net.swimmingtuna.lotm.caps.BeyonderHolder");
                holderOf = att.getMethod("getHolderUnwrap", net.minecraft.world.entity.player.Player.class);
                holderSetSequence = hold.getMethod("setSequence", int.class);
            }
            holderSetSequence.invoke(holderOf.invoke(null, player), seq);
            return true;
        } catch (ReflectiveOperationException e) {
            holderFailed = true;
            LOGGER.warn("[astral] could not find LOTM's sequence setter, regression is not undone: {}", e.toString());
            return false;
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not set the sequence: {}", t.toString());
            return false;
        }
    }


    /** Sets sanity to `percent` of the player's maximum. Returns false if LOTM could not be called. */
    public static boolean setSanityPercent(ServerPlayer player, double percent) {
        initSanity();
        if (getMaxSanity == null || setSanityM == null) return false;
        try {
            int max = (Integer) getMaxSanity.invoke(null, player);
            setSanityM.invoke(null, player, (int) Math.max(1, Math.round(max * percent / 100.0)));
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not set sanity: {}", t.toString());
            return false;
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

    /** Adds LOTM "misfortune" to a player (the same stat Misfortune Bestowal raises; Bestowal itself adds 200). */
    public static boolean addMisfortune(ServerPlayer player, double amount) {
        try {
            Class<?> h = Class.forName("net.swimmingtuna.lotm.util.CorruptionAndLuckHandler");
            net.minecraft.nbt.CompoundTag tag = player.getPersistentData();
            double cur = (Double) h.getMethod("getMisfortune", CompoundTag.class).invoke(null, tag);
            h.getMethod("setMisfortune", net.minecraft.world.entity.LivingEntity.class, CompoundTag.class, double.class)
                    .invoke(null, player, tag, cur + amount);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not add misfortune: {}", t.toString());
            return false;
        }
    }

    /** True while LOTM's combat timer is running for this player (the same check LOTM uses). */
    public static boolean inCombat(ServerPlayer player) {
        try {
            Class<?> util = Class.forName("net.swimmingtuna.lotm.util.BeyonderUtil");
            return (Boolean) util.getMethod("inCombat", net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
        } catch (Throwable t) {
            return player.getPersistentData().getInt("LOTMinCombat") > 0; // what LOTM stores it in
        }
    }

    // ------------------------------------------------------------------ LOTM abilities (all by reflection, all optional)

    private static Class<?> util;
    private static boolean utilFailed;

    private static Class<?> beyonderUtil() {
        if (util == null && !utilFailed) {
            try { util = Class.forName("net.swimmingtuna.lotm.util.BeyonderUtil"); }
            catch (Throwable t) { utilFailed = true; }
        }
        return util;
    }

    private static boolean accepts(Class<?> param, Object arg) {
        if (arg == null) return !param.isPrimitive();
        if (param.isInstance(arg)) return true;
        return (param == int.class && arg instanceof Integer) || (param == float.class && arg instanceof Float)
                || (param == double.class && arg instanceof Double) || (param == boolean.class && arg instanceof Boolean)
                || (param == long.class && arg instanceof Long);
    }

    /** Calls a static LOTM BeyonderUtil method by name (e.g. "applyStun", victim, 20). Returns its result, or null when missing/failed. */
    public static Object util(String name, Object... args) {
        Class<?> c = beyonderUtil();
        if (c == null) return null;
        try {
            for (Method m : c.getMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                Class<?>[] types = m.getParameterTypes();
                boolean ok = true;
                for (int i = 0; i < types.length; i++) if (!accepts(types[i], args[i])) { ok = false; break; }
                if (ok) return m.invoke(null, args);
            }
        } catch (Throwable t) {
            LOGGER.warn("[astral] LOTM ability {} failed: {}", name, String.valueOf(t.getCause() != null ? t.getCause() : t));
        }
        return null;
    }

    /** LOTM's own damage source of a kind ("magic", "explosion", "lava", "holyFire", "lightning", "crush", "fall", "freeze", "generic"), or null. */
    public static net.minecraft.world.damagesource.DamageSource source(String kind, ServerPlayer victim) {
        Object o = util(kind + "Source", null, victim);
        return o instanceof net.minecraft.world.damagesource.DamageSource d ? d : null;
    }

    /** The corrupted whispers LOTM sends to players who are losing their mind. */
    public static void whisper(ServerPlayer p) {
        try {
            Class<?> c = Class.forName("net.swimmingtuna.lotm.util.SpamClass");
            c.getMethod("sendMonsterMessage", net.minecraft.world.entity.player.Player.class).invoke(null, p);
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ LOTM's ally system

    private static Object allyData(net.minecraft.server.MinecraftServer server) throws Exception {
        Class<?> c = Class.forName("net.swimmingtuna.lotm.util.AllyInformation.PlayerAllyData");
        return c.getMethod("get", net.minecraft.world.level.LevelAccessor.class).invoke(null, server.overworld());
    }

    /** Makes two players LOTM allies. Returns true only if they were not allies before (so the caller knows to undo it later). */
    public static boolean addAlly(net.minecraft.server.MinecraftServer server, java.util.UUID a, java.util.UUID b) {
        try {
            Object data = allyData(server);
            Class<?> c = data.getClass();
            if ((Boolean) c.getMethod("areAllies", java.util.UUID.class, java.util.UUID.class).invoke(data, a, b)) return false;
            c.getMethod("addAlly", java.util.UUID.class, java.util.UUID.class).invoke(data, a, b);
            c.getMethod("addAlly", java.util.UUID.class, java.util.UUID.class).invoke(data, b, a);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not add LOTM ally: {}", String.valueOf(t.getCause() != null ? t.getCause() : t));
            return false;
        }
    }

    public static void removeAlly(net.minecraft.server.MinecraftServer server, java.util.UUID a, java.util.UUID b) {
        try {
            Object data = allyData(server);
            Class<?> c = data.getClass();
            c.getMethod("removeAlly", java.util.UUID.class, java.util.UUID.class).invoke(data, a, b);
            c.getMethod("removeAlly", java.util.UUID.class, java.util.UUID.class).invoke(data, b, a);
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not remove LOTM ally: {}", String.valueOf(t.getCause() != null ? t.getCause() : t));
        }
    }

    /** Spawns one of LOTM's entities (like /summon would), with a short life. Returns it, or null. */
    public static net.minecraft.world.entity.Entity spawn(net.minecraft.server.level.ServerLevel level, String id,
                                                         double x, double y, double z, net.minecraft.world.phys.Vec3 motion, int life,
                                                         net.minecraft.world.entity.Entity owner, float scale) {
        try {
            var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("lotm", id)).orElse(null);
            if (type == null) return null;
            net.minecraft.world.entity.Entity e = type.create(level);
            if (e == null) return null;
            e.setPos(x, y, z);
            if (owner != null) {
                for (Method m : e.getClass().getMethods())
                    if (m.getName().equals("setOwner") && m.getParameterCount() == 1) { m.invoke(e, owner); break; }
            }
            util("setScale", e, scale);
            if (motion != null) e.setDeltaMovement(motion);
            for (Method m : e.getClass().getMethods()) {
                if (m.getName().equals("setMaxLife") && m.getParameterCount() == 1) m.invoke(e, life);
                if (m.getName().equals("setDestroyBlocks") && m.getParameterCount() == 1) m.invoke(e, false);
            }
            level.addFreshEntity(e);
            return e;
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not spawn lotm:{}: {}", id, String.valueOf(t.getCause() != null ? t.getCause() : t));
            return null;
        }
    }
}
