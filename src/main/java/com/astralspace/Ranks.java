package com.astralspace;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Reads a player's FTB Ranks permission nodes. Everything is by reflection, so the mod still runs without FTB Ranks:
 * then nobody has any node and every rank perk is simply off.
 *
 * The nodes this mod looks at:
 *   astral_world.weekly_packs[.rainbow|.shiny]  the weekly tarot packs (see {@link WeeklyPacks})
 *   astral_world.cmd.enchanting / .anvil / .enderchest   may use that command anywhere
 *   astral_world.perks.credit_card / .bank_terminal      given the item once, when the rank is first seen
 *   astral_world.perks.title                             a title id, granted through the title mod
 *   astral_world.fun_items                               days between fun-item picks (7, 3 or 1); absent = none
 */
public final class Ranks {
    private Ranks() {}

    private static Object value(ServerPlayer p, String node) {
        try {
            Class<?> api = Class.forName("dev.ftb.mods.ftbranks.api.FTBRanksAPI");
            Method m = api.getMethod("getPermissionValue", ServerPlayer.class, String.class);
            return m.invoke(null, p, node);
        } catch (Throwable t) { return null; }
    }

    private static Optional<?> read(Object v, String getter) {
        if (v == null) return Optional.empty();
        try {
            Class<?> pv = Class.forName("dev.ftb.mods.ftbranks.api.PermissionValue");
            Object r = pv.getMethod(getter).invoke(v);
            return r instanceof Optional<?> o ? o : Optional.empty();
        } catch (Throwable t) { return Optional.empty(); }
    }

    /** A whole-number node, or def when the player has no such node. */
    public static int integer(ServerPlayer p, String node, int def) {
        Optional<?> o = read(value(p, node), "asInteger");
        return o.isPresent() ? (Integer) o.get() : def;
    }

    /** A yes/no node. A number counts as yes when it is above zero, so "true" and "1" both work. */
    public static boolean flag(ServerPlayer p, String node) {
        Object v = value(p, node);
        Optional<?> b = read(v, "asBoolean");
        if (b.isPresent()) return Boolean.TRUE.equals(b.get());
        Optional<?> i = read(v, "asInteger");
        return i.isPresent() && (Integer) i.get() > 0;
    }

    /** True when FTB Ranks is installed at all. Without it every perk stays off and ops keep their own access. */
    public static boolean present() {
        try { Class.forName("dev.ftb.mods.ftbranks.api.FTBRanksAPI"); return true; } catch (Throwable t) { return false; }
    }
}
