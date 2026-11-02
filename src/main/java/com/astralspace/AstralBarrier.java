package com.astralspace;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Astral Barrier's health. This is the API for the rest of the project:
 *   AstralBarrier.get(server) / getMax(server) / set / heal / damage / isBroken
 *   AstralBarrier.onBreak(server -> ...)   runs when HP drops from above 0 to 0
 * HP never changes by itself; AstralServer regenerates it while a Seq 0 is in the Astral World.
 */
public final class AstralBarrier {
    public static final int HP_PER_SEQ0 = 100;
    private static final List<Consumer<MinecraftServer>> BREAK_LISTENERS = new ArrayList<>();

    private AstralBarrier() {}

    public static int get(MinecraftServer server) { return AstralData.get(server).hp; }

    /** Every Seq 0 adds 100 to the barrier's maximum; a Door (Apprentice) Seq 0 counts as two, as it does for healing it. */
    public static int getMax(MinecraftServer server) {
        AstralData d = AstralData.get(server);
        int n = 0;
        for (java.util.UUID id : d.seq0.keySet()) n += "apprentice".equals(d.seq0Paths.get(id)) ? 2 : 1;
        return n * HP_PER_SEQ0;
    }

    public static boolean isBroken(MinecraftServer server) { return get(server) <= 0; }

    /** Sets the HP (kept between 0 and the max). Returns the new HP. */
    public static int set(MinecraftServer server, int value) {
        AstralData d = AstralData.get(server);
        int before = d.hp;
        d.hp = Math.max(0, Math.min(getMax(server), value));
        d.setDirty();
        if (before > 0 && d.hp == 0) {
            for (Consumer<MinecraftServer> l : BREAK_LISTENERS) {
                try { l.accept(server); } catch (Throwable ignored) { }
            }
        }
        return d.hp;
    }

    public static int heal(MinecraftServer server, int amount) { return set(server, get(server) + amount); }

    /** Testing: while true the barrier takes no damage from the stages, the sieges or the gods. Kept in memory only, so a restart turns it off. */
    public static volatile boolean frozen = false;

    public static int damage(MinecraftServer server, int amount) { return frozen ? get(server) : set(server, get(server) - amount); }

    public static void onBreak(Consumer<MinecraftServer> listener) { BREAK_LISTENERS.add(listener); }
}
