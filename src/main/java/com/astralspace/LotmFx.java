package com.astralspace;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Plays LOTM's own Photon effects (its .fx files) inside holograms. LOTM is only an optional dependency, so everything goes through
 * reflection on ClientFX.holdFXAt; if LOTM or the effect is missing, callers fall back to drawing their own version.
 * One effect per hologram; it is killed when the hologram stops being drawn.
 */
final class LotmFx {
    private LotmFx() {}

    private static final Map<String, Object[]> LIVE = new HashMap<>();   // entity id + effect -> {handle, last seen tick, x, y, z, scale}
    private static Method hold, kill;
    private static boolean tried, ok;

    static void init() { NeoForge.EVENT_BUS.addListener(LotmFx::onTick); }

    private static boolean prepare() {
        if (tried) return ok;
        tried = true;
        try {
            Class<?> c = Class.forName("net.swimmingtuna.lotm.client.ClientFX");
            hold = c.getMethod("holdFXAt", ResourceLocation.class, Vec3.class, Vec3.class, float.class, float.class, float.class);
            kill = Class.forName("net.swimmingtuna.lotm.client.ClientFX$FXHandle").getMethod("kill");
            ok = true;
        } catch (Throwable t) { ok = false; }
        return ok;
    }

    /** Keeps the effect (for example "bard/flaring_sun") alive at the given world spot for this hologram. Returns false if it could not be played. */
    static boolean hold(int id, String fx, double x, double y, double z, float scale) {
        if (!prepare()) return false;
        Level lv = Minecraft.getInstance().level;
        if (lv == null) return false;
        long now = lv.getGameTime();
        String key = id + ":" + fx;
        Object[] e = LIVE.get(key);
        if (e != null) {
            boolean moved = Math.abs((double) e[2] - x) + Math.abs((double) e[3] - y) + Math.abs((double) e[4] - z) > .02 || Math.abs((float) e[5] - scale) > .01f;
            if (!moved) { e[1] = now; return e[0] != null; }
            stop(e[0]); LIVE.remove(key);
        }
        Object h = null;
        try { h = hold.invoke(null, ResourceLocation.fromNamespaceAndPath("lotm", fx), new Vec3(x, y, z), new Vec3(0, 1, 0), scale, 1f, 1f); } catch (Throwable t) { h = null; }
        LIVE.put(key, new Object[] {h, now, x, y, z, scale});
        return h != null;
    }

    private static void stop(Object h) { if (h != null) try { kill.invoke(h); } catch (Throwable ignored) {} }

    private static void onTick(ClientTickEvent.Post ev) {
        if (LIVE.isEmpty()) return;
        Level lv = Minecraft.getInstance().level;
        long now = lv == null ? Long.MAX_VALUE / 2 : lv.getGameTime();
        for (Iterator<Map.Entry<String, Object[]>> it = LIVE.entrySet().iterator(); it.hasNext();) {
            Object[] e = it.next().getValue();
            if (lv == null || now - (long) e[1] > 8 || now < (long) e[1]) { stop(e[0]); it.remove(); }
        }
    }
}
