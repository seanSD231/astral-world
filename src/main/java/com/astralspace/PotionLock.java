package com.astralspace;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps each client told which LOTM potion recipes its player has unlocked, so JEI can hide the rest ({@link AstralJei}).
 *
 * LOTM's potion-recipe capability lives only on the server (its attachment is not synced), and the potion recipes
 * themselves are ordinary data-pack recipes, which every client does have. So without this the recipe list in JEI
 * shows every potion in the game, formula or no formula. The server reads the capability a few times a minute and
 * sends the unlocked keys over when they change; nothing is sent while they stay the same.
 *
 * A key is "pathway:sequence", for instance "sailor:7".
 */
public final class PotionLock {
    private PotionLock() {}

    private static final int EVERY = 60;   // three seconds between looks
    private static final Map<UUID, Set<String>> sent = new HashMap<>();

    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        var server = e.getServer();
        if (server.getTickCount() % EVERY != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) push(p, false);
    }

    public static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) UniqAbilities.later(40, () -> push(p, true));
    }

    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        sent.remove(e.getEntity().getUUID());
    }

    /** Sends this player their unlocked keys if they have changed (or always, when `force`). */
    private static void push(ServerPlayer p, boolean force) {
        if (!p.isAlive() && !force) return;
        Set<String> now = Lotm.unlockedRecipes(p);
        if (!force && now.equals(sent.get(p.getUUID()))) return;
        sent.put(p.getUUID(), new HashSet<>(now));
        PacketDistributor.sendToPlayer(p, new Net.RecipesPayload(new java.util.ArrayList<>(now)));
    }

    // ------------------------------------------------------------------ client side

    private static final Set<String> UNLOCKED = new HashSet<>();
    private static int version = 0;

    /** The list arrived from the server. */
    static void arrived(java.util.List<String> keys) {
        UNLOCKED.clear();
        UNLOCKED.addAll(keys);
        version++;
    }

    /** Changes whenever the list changes, so JEI knows to look again. */
    static int version() { return version; }

    /** True once the server has told us anything at all: before that, nothing is hidden. */
    static boolean known() { return version > 0; }

    static boolean unlocked(String key) { return UNLOCKED.contains(key); }
}
