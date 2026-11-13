package com.astralspace;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Reads a player's FTB Ranks permission nodes. Everything is by reflection, so the mod still runs without FTB Ranks:
 * then nobody has any node and every rank perk is simply off.
 *
 * FTB Ranks has moved its API about between versions, so nothing here is looked up by an exact signature:
 * getPermissionValue is found by name among the static and instance methods, and a value is read through whichever
 * of asInteger / asBoolean / asDouble / asString answers. asInteger returns an OptionalInt rather than an
 * Optional&lt;Integer&gt;, which is what made every numeric node (packs, fun items, title) read as missing before.
 *
 * The nodes this mod looks at:
 *   astral_world.weekly_packs[.rainbow|.shiny]  the weekly tarot packs (see {@link WeeklyPacks})
 *   astral_world.cmd.enchanting / .anvil / .enderchest   may use that command anywhere
 *   astral_world.perks.credit_card / .bank_terminal      given the item once, when the rank is first seen
 *   astral_world.perks.title                             a title id, granted through the title mod
 *   astral_world.fun_items                               days between fun-item picks (7, 3 or 1); absent = none
 */
public final class Ranks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private Ranks() {}

    private static boolean looked;
    private static Method getValue;
    private static Object apiInstance;      // null when getPermissionValue is static

    private static synchronized void look() {
        if (looked) return;
        looked = true;
        Class<?> api;
        try { api = Class.forName("dev.ftb.mods.ftbranks.api.FTBRanksAPI"); }
        catch (Throwable t) { LOGGER.info("[astral] FTB Ranks is not installed: every rank perk is off"); return; }
        for (Method m : api.getMethods()) {
            if (!m.getName().equals("getPermissionValue") || m.getParameterCount() != 2) continue;
            if (!m.getParameterTypes()[0].isAssignableFrom(ServerPlayer.class) || m.getParameterTypes()[1] != String.class) continue;
            if (Modifier.isStatic(m.getModifiers())) { getValue = m; apiInstance = null; break; }
            Object inst = instance(api);
            if (inst != null) { getValue = m; apiInstance = inst; break; }
        }
        if (getValue == null) LOGGER.warn("[astral] FTB Ranks is here but getPermissionValue could not be found: rank perks stay off");
        else LOGGER.info("[astral] FTB Ranks found, reading nodes through {}{}", getValue, apiInstance == null ? " (static)" : "");
    }

    /** The API object, for the versions where getPermissionValue is an instance method. */
    private static Object instance(Class<?> api) {
        for (String n : new String[]{"api", "getInstance", "manager"}) {
            try {
                Method m = api.getMethod(n);
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0) return m.invoke(null);
            } catch (Throwable ignored) { }
        }
        try { return api.getField("INSTANCE").get(null); } catch (Throwable ignored) { }
        return null;
    }

    /** The raw PermissionValue for a node, or null. */
    private static Object value(ServerPlayer p, String node) {
        look();
        if (getValue == null) return null;
        try { return getValue.invoke(apiInstance, p, node); }
        catch (Throwable t) { return null; }
    }

    /** Calls one of PermissionValue's readers and unwraps whatever shape of Optional (or bare value) it hands back. */
    private static Object read(Object v, String getter) {
        if (v == null) return null;
        Object r;
        try { r = v.getClass().getMethod(getter).invoke(v); }
        catch (Throwable t) { return null; }
        if (r instanceof OptionalInt o) return o.isPresent() ? o.getAsInt() : null;
        if (r instanceof OptionalLong o) return o.isPresent() ? o.getAsLong() : null;
        if (r instanceof OptionalDouble o) return o.isPresent() ? o.getAsDouble() : null;
        if (r instanceof Optional<?> o) return o.orElse(null);
        return r;
    }

    /** A whole-number node, or def when the player has no such node. Text and decimals are accepted too. */
    public static int integer(ServerPlayer p, String node, int def) {
        Object v = value(p, node);
        for (String g : new String[]{"asInteger", "asDouble", "asString"}) {
            Object r = read(v, g);
            if (r instanceof Number n) return n.intValue();
            if (r instanceof String s) { try { return (int) Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) { } }
        }
        Object b = read(v, "asBoolean");
        if (b instanceof Boolean bb) return bb ? 1 : 0;
        return def;
    }

    /** A yes/no node. A number counts as yes when it is above zero, so "true" and "1" both work. */
    public static boolean flag(ServerPlayer p, String node) {
        Object v = value(p, node);
        Object b = read(v, "asBoolean");
        if (b instanceof Boolean bb) return bb;
        Object i = read(v, "asInteger");
        if (i instanceof Number n) return n.intValue() > 0;
        Object s = read(v, "asString");
        return s instanceof String str && (str.equalsIgnoreCase("true") || str.equals("1"));
    }

    /**
     * A cap node: a number means that many, true means as many as `total`, anything else none.
     * Used for the rainbow and shiny shares of the weekly packs.
     */
    public static int cap(ServerPlayer p, String node, int total) {
        Object v = value(p, node);
        Object i = read(v, "asInteger");
        if (i instanceof Number n) return Math.min(total, Math.max(0, n.intValue()));
        Object b = read(v, "asBoolean");
        if (b instanceof Boolean bb) return bb ? total : 0;
        return 0;
    }

    /** What a node reads as right now, in words, for the /astralranks report. */
    public static String describe(ServerPlayer p, String node) {
        Object v = value(p, node);
        if (v == null) return "not set";
        StringBuilder s = new StringBuilder();
        for (String g : new String[]{"asBoolean", "asInteger", "asDouble", "asString"}) {
            Object r = read(v, g);
            if (r != null) s.append(s.length() > 0 ? ", " : "").append(g.substring(2).toLowerCase()).append('=').append(r);
        }
        return s.length() > 0 ? s.toString() : "set, but empty";
    }

    /** True when FTB Ranks is installed at all. Without it every perk stays off and ops keep their own access. */
    public static boolean present() {
        look();
        return getValue != null;
    }
}
