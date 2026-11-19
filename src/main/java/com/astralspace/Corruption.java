package com.astralspace;

import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How far the moon has got into someone.
 *
 * <p>A count rather than an effect, because an effect would tick down on its own and the whole
 * point is that it does not: it is shed by getting out of the light and nothing else. It is kept
 * on the server only and sent to the player's screen as a number, so there is no state to lose.</p>
 */
public final class Corruption {
    private Corruption() { }

    private static final Map<UUID, int[]> C = new HashMap<>();   // {count, ticks since last gained}

    public static int get(Player p) { int[] v = C.get(p.getUUID()); return v == null ? 0 : v[0]; }

    public static int add(Player p, int n) {
        int[] v = C.computeIfAbsent(p.getUUID(), k -> new int[2]);
        v[0] = Math.min(20, v[0] + n);
        v[1] = 0;
        if (p instanceof net.minecraft.server.level.ServerPlayer sp)
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new DoorNet.Corrupt(v[0]));
        return v[0];
    }

    public static void clear(Player p) {
        C.remove(p.getUUID());
        if (p instanceof net.minecraft.server.level.ServerPlayer sp)
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new DoorNet.Corrupt(0));
    }

    /** Out of the light for three seconds and it starts coming off, a point at a time. */
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        if (C.isEmpty() || e.getServer().getTickCount() % 20 != 0) return;
        for (var it = C.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            int[] v = en.getValue();
            if (++v[1] < 3) continue;
            v[0]--;
            v[1] = 2;
            var p = e.getServer().getPlayerList().getPlayer(en.getKey());
            if (p != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new DoorNet.Corrupt(Math.max(0, v[0])));
            if (v[0] <= 0) it.remove();
        }
    }

    // ---- the client's copy, for the bar on screen ----
    private static int shown;
    public static void setClient(int v) { shown = v; }
    public static int client() { return shown; }
}
