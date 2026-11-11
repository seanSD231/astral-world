package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Client only: anyone standing inside a Demoness Seq 1 or Seq 0 hologram takes on LOTM's Witch body (the model an Assassin gets at Sequence 7),
 * by switching on LOTM's own flag for them while they are in it. LOTM by reflection; without it nothing happens.
 */
public final class DemonessLook {
    private DemonessLook() {}
    private static final Set<UUID> OURS = new HashSet<>();   // those we switched on (never anyone LOTM itself had already made a witch)
    private static Method set, isActive; private static boolean tried;

    private static boolean ready() {
        if (!tried) {
            tried = true;
            try {
                Class<?> c = Class.forName("net.swimmingtuna.lotm.client.pathway.assassin.ClientAssassinWitchData");
                set = c.getMethod("set", UUID.class, boolean.class); isActive = c.getMethod("isActive", UUID.class);
            } catch (Throwable t) { set = null; }
        }
        return set != null;
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.player.tickCount % 5 != 0 || !ready()) return;
        try {
            Set<UUID> in = new HashSet<>();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof HoloCardEntity h) || h.goo() >= 0 || h.pathIndex() != 3 || h.seq() > 1 || h.symbolOnly()) continue;
                double r = h.seq() == 0 ? 11.5 : 6.5;
                for (Player p : mc.level.players()) if (Math.abs(p.getY() - h.getY()) < 14 && (p.getX() - h.getX()) * (p.getX() - h.getX()) + (p.getZ() - h.getZ()) * (p.getZ() - h.getZ()) < r * r) in.add(p.getUUID());
            }
            for (UUID id : in) if (!OURS.contains(id) && !(boolean) isActive.invoke(null, id)) { set.invoke(null, id, true); OURS.add(id); }
            for (var it = OURS.iterator(); it.hasNext(); ) { UUID id = it.next(); if (!in.contains(id)) { set.invoke(null, id, false); it.remove(); } }
        } catch (Throwable ignored) { }
    }
}
