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

    private static Method corrMax, corrAdd, corrGet, corrSet, corrClear;
    private static Method pwSetSequence, pwGetSequence, fateMisfortune, fateSetMisfortune, combatIn;

    private static Class<?> cls(String n) {
        try { return Class.forName(n); } catch (Throwable t) { return null; }
    }

    private static Method find(Class<?> c, String name, int params) {
        if (c == null) return null;
        for (Method m : c.getMethods()) if (m.getName().equals(name) && m.getParameterCount() == params) return m;
        return null;
    }

    /** Finds LOTM's methods. Newer LOTM (the acting line) has Pathways/Corruption/Fate/Combat; older LOTM had BeyonderUtil. */
    private static synchronized void init() {
        if (loaded) return;
        loaded = true;
        try {
            Class<?> pathways = cls("net.swimmingtuna.lotm.beyonder.Pathways");
            Class<?> corruption = cls("net.swimmingtuna.lotm.beyonder.Corruption");
            Class<?> fate = cls("net.swimmingtuna.lotm.beyonder.Fate");
            Class<?> combat = cls("net.swimmingtuna.lotm.entity.Combat");
            Class<?> old = cls("net.swimmingtuna.lotm.util.BeyonderUtil");
            Class<?> classInit = cls("net.swimmingtuna.lotm.init.BeyonderClassInit");

            getPathway = find(pathways, "getPathway", 1);
            pwSetSequence = find(pathways, "setSequence", 2);
            pwGetSequence = find(pathways, "getSequence", 1);
            corrMax = find(corruption, "max", 1);
            corrAdd = find(corruption, "add", 3);
            corrGet = find(corruption, "get", 2);
            corrSet = find(corruption, "set", 3);
            corrClear = find(corruption, "clear", 1);
            fateMisfortune = find(fate, "misfortune", 1);
            fateSetMisfortune = find(fate, "setMisfortune", 2);
            combatIn = find(combat, "inCombat", 1);
            getRandomWorkingPathway = find(classInit, "getRandomWorkingPathway", 0);
            if (getRandomWorkingPathway == null) getRandomWorkingPathway = find(pathways, "chooseRandomPathway", 0);

            // the older LOTM, for whatever is still missing
            if (old != null) {
                if (getPathway == null) getPathway = find(old, "getPathway", 1);
                if (addCorruption == null) addCorruption = find(old, "addCorruption", 3);
                if (getMaxCorruption == null) getMaxCorruption = find(old, "getMaxCorruption", 1);
                if (getRandomWorkingPathway == null) getRandomWorkingPathway = find(old, "getRandomWorkingPathway", 0);
            }
            LOGGER.info("[astral] LOTM found. getPathway={} corruption(add/max/get/set/clear)={}/{}/{}/{}/{} misfortune={} combat={} sequenceSetter={} randomPathway={}",
                    getPathway != null, corrAdd != null, corrMax != null, corrGet != null, corrSet != null, corrClear != null,
                    fateSetMisfortune != null, combatIn != null, pwSetSequence != null, getRandomWorkingPathway != null);
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
        Method add = corrAdd != null ? corrAdd : addCorruption;
        Method max = corrMax != null ? corrMax : getMaxCorruption;
        if (add == null || max == null || pathway == null) return false;
        try {
            int mx = (Integer) max.invoke(null, player);
            int amount = (int) Math.max(1, Math.round(mx * percent / 100.0));
            add.invoke(null, player, pathway, amount);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not add corruption: {}", String.valueOf(t.getCause() != null ? t.getCause() : t));
            return false;
        }
    }

    /**
     * Doubles the corruption the player already has on every pathway (capped at the maximum). With none at all, adds 1% on a random pathway instead.
     * Returns false if LOTM could not be called.
     */
    public static boolean doubleCorruption(ServerPlayer player) {
        init();
        try {
            Class<?> ci = Class.forName("net.swimmingtuna.lotm.init.BeyonderClassInit");
            Class<?> bc = Class.forName("net.swimmingtuna.lotm.beyonder.api.BeyonderClass");
            Method all = ci.getMethod("getAllPathways");
            Method get = corrGet, set = corrSet, max = corrMax;
            if (get == null || set == null || max == null) {   // the older LOTM kept these in BeyonderUtil
                Class<?> u = Class.forName("net.swimmingtuna.lotm.util.BeyonderUtil");
                get = u.getMethod("getCorruption", net.minecraft.world.entity.Entity.class, bc);
                set = u.getMethod("setCorruption", net.minecraft.world.entity.Entity.class, bc, int.class);
                max = u.getMethod("getMaxCorruption", net.minecraft.world.entity.Entity.class);
            }
            int cap = (Integer) max.invoke(null, player);
            boolean any = false;
            for (Object pw : (java.util.List<?>) all.invoke(null)) {
                int cur = (Integer) get.invoke(null, player, pw);
                if (cur <= 0) continue;
                any = true;
                int add = cap > 0 ? Math.min(cur, Math.max(1, cap / 10)) : cur;   // doubles, but a single hit adds at most 10% of the cap per pathway
                set.invoke(null, player, pw, Math.min(cap > 0 ? cap : Integer.MAX_VALUE, cur + add));
            }
            if (!any) return addCorruptionPercent(player, randomPathway(), 1);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not double corruption: {}", String.valueOf(t.getCause() != null ? t.getCause() : t));
            return false;
        }
    }

    /** Clears all corruption on every pathway. */
    public static void clearCorruption(ServerPlayer player) {
        init();
        if (corrClear != null) {
            try { corrClear.invoke(null, player); return; }
            catch (Throwable t) { LOGGER.warn("[astral] could not clear corruption: {}", String.valueOf(t.getCause() != null ? t.getCause() : t)); }
        }
        util("clearCorruption", player);
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
        init();
        if (pwSetSequence != null) {   // newer LOTM: Pathways.setSequence(entity, seq)
            try { pwSetSequence.invoke(null, player, seq); return true; }
            catch (Throwable t) { LOGGER.warn("[astral] could not set the sequence: {}", String.valueOf(t.getCause() != null ? t.getCause() : t)); return false; }
        }
        if (holderFailed) return false;
        try {
            if (holderOf == null) {   // older LOTM
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
        init();
        try {
            if (fateMisfortune != null && fateSetMisfortune != null) {   // newer LOTM: beyonder.Fate
                double cur = ((Number) fateMisfortune.invoke(null, player)).doubleValue();
                fateSetMisfortune.invoke(null, player, cur + amount);
                return true;
            }
            Class<?> h = Class.forName("net.swimmingtuna.lotm.util.CorruptionAndLuckHandler");   // older LOTM
            net.minecraft.nbt.CompoundTag tag = player.getPersistentData();
            double cur = (Double) h.getMethod("getMisfortune", CompoundTag.class).invoke(null, tag);
            h.getMethod("setMisfortune", net.minecraft.world.entity.LivingEntity.class, CompoundTag.class, double.class)
                    .invoke(null, player, tag, cur + amount);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not add misfortune: {}", String.valueOf(t.getCause() != null ? t.getCause() : t));
            return false;
        }
    }

    /** True while LOTM's combat timer is running for this player (the same check LOTM uses). */
    public static boolean inCombat(ServerPlayer player) {
        init();
        try {
            if (combatIn != null) return (Boolean) combatIn.invoke(null, player);
            Class<?> util = Class.forName("net.swimmingtuna.lotm.util.BeyonderUtil");
            return (Boolean) util.getMethod("inCombat", net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
        } catch (Throwable t) {
            return player.getPersistentData().getInt("LOTMinCombat") > 0; // what the older LOTM stored it in
        }
    }

    // ------------------------------------------------------------------ LOTM abilities (all by reflection, all optional)

    private static Class<?>[] utilClasses;

    /** The classes LOTM keeps its helpers in (the newer split first, the older BeyonderUtil last). */
    private static Class<?>[] utilClasses() {
        if (utilClasses == null) {
            java.util.List<Class<?>> l = new java.util.ArrayList<>();
            for (String n : new String[]{"ability.trait.Afflictions", "entity.Scale", "entity.Combat", "beyonder.Corruption", "beyonder.Fate",
                    "beyonder.Pathways", "capabilities.sanity_data.SanityUtil", "util.BeyonderUtil"}) {
                Class<?> c = cls("net.swimmingtuna.lotm." + n);
                if (c != null) l.add(c);
            }
            utilClasses = l.toArray(new Class<?>[0]);
        }
        return utilClasses;
    }

    private static boolean accepts(Class<?> param, Object arg) {
        if (arg == null) return !param.isPrimitive();
        if (param.isInstance(arg)) return true;
        return (param == int.class && arg instanceof Integer) || (param == float.class && arg instanceof Float)
                || (param == double.class && arg instanceof Double) || (param == boolean.class && arg instanceof Boolean)
                || (param == long.class && arg instanceof Long);
    }

    /** Calls a static LOTM helper by name (e.g. "applyStun", victim, 20). Returns its result, or null when missing/failed. */
    public static Object util(String name, Object... args) {
        if (name.equals("applyMentalPlague") && args.length == 2) {   // the newer LOTM wants a cast snapshot too; we have none
            Object r = utilExact(name, args[0], args[1], null);
            if (r != null || plagueWorked) return r;
            return utilExact("applyFrenzy", args[0], args[1]);
        }
        return utilExact(name, args);
    }

    private static boolean plagueWorked;

    private static Object utilExact(String name, Object... args) {
        for (Class<?> c : utilClasses()) {
            try {
                for (Method m : c.getMethods()) {
                    if (!m.getName().equals(name) || m.getParameterCount() != args.length || !java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                    Class<?>[] types = m.getParameterTypes();
                    boolean ok = true;
                    for (int i = 0; i < types.length; i++) if (!accepts(types[i], args[i])) { ok = false; break; }
                    if (!ok) continue;
                    Object r = m.invoke(null, args);
                    if (name.equals("applyMentalPlague")) plagueWorked = true;
                    return r;
                }
            } catch (Throwable t) {
                LOGGER.warn("[astral] LOTM ability {} failed: {}", name, String.valueOf(t.getCause() != null ? t.getCause() : t));
                return null;
            }
        }
        return null;
    }

    /** LOTM's own damage source of a kind ("magic", "explosion", "lava", "holyFire", "lightning", "crush", "fall", "freeze", "generic"), or null. */
    public static net.minecraft.world.damagesource.DamageSource source(String kind, ServerPlayer victim) {
        Object o = utilExact(kind + "Source", null, victim);   // the older LOTM: BeyonderUtil.<kind>Source(null, victim)
        if (o == null) {
            try {   // the newer LOTM keeps a few in ability.damage.Sources (magic, generic ...)
                Class<?> c = Class.forName("net.swimmingtuna.lotm.ability.damage.Sources");
                for (Method m : c.getMethods())
                    if (m.getName().equals(kind + "Source") && m.getParameterCount() == 2 && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                        o = m.invoke(null, null, victim);
                        break;
                    }
            } catch (Throwable ignored) { }
        }
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
            util("setScale", e, scale);   // Scale.setScale in the newer LOTM, BeyonderUtil.setScale in the older
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

    private static Object protectMark; private static Method protectSet; private static boolean protectTried;

    /** LOTM's 'inside a protective structure' mark (the World of Pallor and other area effects skip anything that has it). The acting line keeps it in memory and
     *  it runs out, so it has to be renewed; the old NBT flag does nothing there. Quietly does nothing if LOTM has no such mark. */
    public static void markProtected(net.minecraft.world.entity.Entity e, int ticks) {
        if (!protectTried) {
            protectTried = true;
            try {
                Class<?> c = Class.forName("net.swimmingtuna.lotm.util.TimedMarks");
                protectMark = c.getField("IN_PROTECTIVE_STRUCTURE").get(null);
                protectSet = c.getMethod("set", net.minecraft.world.entity.Entity.class, int.class);
            } catch (Throwable t) { protectMark = null; }
        }
        if (protectMark == null || protectSet == null) return;
        try { protectSet.invoke(protectMark, e, ticks); } catch (Throwable ignored) { }
    }

    private static Method recipeGetAll;
    private static boolean recipeTried;
    private static java.util.Map<Object, String> pathwayNames;   // BeyonderClass -> its id, built once

    /**
     * Which potion recipes this player has unlocked, as "pathway:sequence" (for instance "sailor:7"), read out of LOTM's own
     * potion-recipe capability. That capability is never sent to the client, so the server has to hand the list over itself
     * ({@link PotionLock}) before JEI can hide what is still locked. An empty set means LOTM could not be asked.
     */
    public static java.util.Set<String> unlockedRecipes(ServerPlayer p) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (!recipeTried) {
            recipeTried = true;
            try {
                recipeGetAll = Class.forName("net.swimmingtuna.lotm.capabilities.potion_recipes_data.PotionRecipesUtil")
                        .getMethod("getRecipes", net.minecraft.world.entity.LivingEntity.class);
            } catch (Throwable t) {
                LOGGER.info("[astral] LOTM has no potion-recipe capability here, so JEI hides nothing: {}", t.toString());
            }
            pathwayNames = new java.util.IdentityHashMap<>();
            for (String id : PATH_IDS) { Object bc = pathwayById(id); if (bc != null) pathwayNames.put(bc, id); }
        }
        if (recipeGetAll == null) return out;
        try {
            Object map = recipeGetAll.invoke(null, p);
            if (!(map instanceof java.util.Map<?, ?> m)) return out;
            for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
                String id = pathwayNames.get(e.getKey());
                if (id == null || !(e.getValue() instanceof Iterable<?> seqs)) continue;
                for (Object s : seqs) out.add(id + ":" + s);
            }
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not read {}'s potion recipes: {}", p.getGameProfile().getName(), t.toString());
        }
        return out;
    }

    /** The pathway and sequence written on a LOTM potion, as "pathway:sequence", or "" if the stack is not one. */
    public static String potionKey(net.minecraft.world.item.ItemStack st) {
        if (st.isEmpty()) return "";
        CompoundTag t = itemTag(st);
        if (t == null) return "";
        String path = t.getString("pathway");
        return path.isEmpty() || !t.contains("sequence") ? "" : path + ":" + t.getInt("sequence");
    }

    private static Method nbtGetTag, nbtHasTag;
    private static boolean nbtTried;

    /** LOTM keeps its own compound on a stack; this reads it the way LOTM does, whatever component it lives in. */
    private static CompoundTag itemTag(net.minecraft.world.item.ItemStack st) {
        if (!nbtTried) {
            nbtTried = true;
            try {
                Class<?> c = Class.forName("net.swimmingtuna.lotm.util.ItemNbt");
                nbtHasTag = c.getMethod("hasTag", net.minecraft.world.item.ItemStack.class);
                nbtGetTag = c.getMethod("getTag", net.minecraft.world.item.ItemStack.class);
            } catch (Throwable t) { nbtGetTag = null; }
        }
        if (nbtGetTag == null || nbtHasTag == null) return null;
        try {
            if (!((Boolean) nbtHasTag.invoke(null, st))) return null;
            return (CompoundTag) nbtGetTag.invoke(null, st);
        } catch (Throwable t) { return null; }
    }

    // ---- what a Beyonder carries that is not an item: grazed souls, scribed abilities, enslaved spirits

    /** {the field on LOTM's AttachmentInit, the class that holds the data}. */
    private static final String[][] CARRIED = {
        {"GRAZED_ABILITIES", "net.swimmingtuna.lotm.capabilities.grazed_abilities.GrazedAbilitiesCapability"},
        {"SCRIBED_ABILITIES", "net.swimmingtuna.lotm.capabilities.scribed_abilities.ScribedAbilitiesCapability"},
        {"SPIRIT_HOLDER", "net.swimmingtuna.lotm.capabilities.spirit_data.SpiritHolderCapability"}};

    /** One of the three, off this player: {the data object, its attachment id}, or null when LOTM (or the data) is not there. */
    private static Object[] carried(ServerPlayer p, String field) {
        try {
            Object scoped = Class.forName("net.swimmingtuna.lotm.init.AttachmentInit").getField(field).get(null);
            Object opt = scoped.getClass().getMethod("getFrom", Object.class).invoke(scoped, p);
            if (!(opt instanceof java.util.Optional<?> o) || o.isEmpty()) return null;
            Object type = scoped.getClass().getMethod("getType").invoke(scoped);
            var key = net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES.getKey((net.neoforged.neoforge.attachment.AttachmentType<?>) type);
            return new Object[]{o.get(), key == null ? "" : key.toString()};
        } catch (Throwable t) { return null; }
    }

    /** Empties one of them by copying a brand new one over it, and then, if `tag` is given, loads that into it. */
    private static boolean refill(ServerPlayer p, Object cap, String impl, CompoundTag tag) {
        try {
            Object fresh = Class.forName(impl).getDeclaredConstructor().newInstance();
            for (Method m : cap.getClass().getMethods()) if (m.getName().equals("copyFrom") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(fresh)) { m.invoke(cap, fresh); break; }
            Method de = cap.getClass().getMethod("deserializeNBT", net.minecraft.core.HolderLookup.Provider.class, CompoundTag.class);
            if (tag == null) tag = (CompoundTag) fresh.getClass().getMethod("serializeNBT", net.minecraft.core.HolderLookup.Provider.class).invoke(fresh, p.registryAccess());
            de.invoke(cap, p.registryAccess(), tag);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not reset {} on {}: {}", impl.substring(impl.lastIndexOf('.') + 1), p.getGameProfile().getName(), t.toString());
            return false;
        }
    }

    /** Takes a player's grazed souls, scribed abilities and enslaved spirits away (the hunger games: nothing is carried in). Returns how many of the three were emptied. */
    public static int stripCarried(ServerPlayer p) {
        int n = 0;
        for (String[] c : CARRIED) { Object[] got = carried(p, c[0]); if (got != null && refill(p, got[0], c[1], null)) n++; }
        return n;
    }

    /**
     * Puts those three back exactly as they are in a saved copy of the player's attachments (what {@code neoforge:attachments}
     * held before the games). Whatever was picked up since is gone, because each is emptied first; one that is not in
     * the saved copy at all is simply left empty.
     */
    public static void restoreCarried(ServerPlayer p, CompoundTag attachments) {
        for (String[] c : CARRIED) {
            Object[] got = carried(p, c[0]);
            if (got == null) continue;
            String key = (String) got[1];
            CompoundTag saved = attachments != null && !key.isEmpty() && attachments.contains(key, 10) ? attachments.getCompound(key) : null;
            refill(p, got[0], c[1], saved);
        }
    }
}
