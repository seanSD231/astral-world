package com.astralspace;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Sol's Relic System (modid {@code solsrelicsystem}) gives every player two extra hands: a phantom mainhand and a
 * phantom offhand, kept in a NeoForge attachment of its own rather than in the inventory. The hunger games empty the
 * inventory and the Curios slots on the way in and put them back on the way out, and knew nothing about these two, so
 * a contestant could carry their own gear in and walk an arena weapon out in a hand nobody was looking at.
 *
 * <p>Everything here is reflection, so the mod stays optional: with Sol's Relic System absent, every call does nothing
 * and says so once in the log.</p>
 */
public final class PhantomSlots {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PhantomSlots() { }

    private static boolean tried, ok;
    private static Method getExisting, getSlots, setMain, setOff, sync;

    private static void init() {
        if (tried) return;
        tried = true;
        try {
            Class<?> handler = Class.forName("com.solsrelic.handler.PhantomSlotHandler");
            Class<?> iface = Class.forName("com.solsrelic.core.IPhantomSlots");
            getSlots = handler.getMethod("getSlots", net.minecraft.world.entity.player.Player.class);
            setMain = iface.getMethod("setPhantomMainhand", ItemStack.class);
            setOff = iface.getMethod("setPhantomOffhand", ItemStack.class);
            try { sync = handler.getMethod("syncToClient", ServerPlayer.class); } catch (NoSuchMethodException ignored) { }
            try {
                getExisting = Class.forName("com.solsrelic.core.PhantomSlotProvider")
                        .getMethod("getExisting", net.minecraft.world.entity.player.Player.class);
            } catch (ReflectiveOperationException ignored) { }   // older builds: fall back to getSlots
            ok = true;
            LOGGER.info("[astral] Sol's Relic System found: the phantom hands are emptied with the inventory");
        } catch (Throwable t) {
            ok = false;
            LOGGER.info("[astral] Sol's Relic System not present ({}): nothing to empty", String.valueOf(t));
        }
    }

    /** The player's phantom slots, or null when the mod is absent or the player has none yet. */
    private static Object of(ServerPlayer p) throws ReflectiveOperationException {
        if (getExisting != null) {
            Object got = getExisting.invoke(null, p);
            return got instanceof Optional<?> o ? o.orElse(null) : got;
        }
        return getSlots.invoke(null, p);
    }

    /**
     * Empties both phantom hands. Whatever was in them is gone, not dropped: on the way into the games the player's
     * own gear is already in the snapshot and comes back with their attachments, and on the way out what is in there
     * belongs to the arena.
     */
    public static void clear(ServerPlayer p) {
        init();
        if (!ok) return;
        try {
            Object slots = of(p);
            if (slots == null) return;
            setMain.invoke(slots, ItemStack.EMPTY);
            setOff.invoke(slots, ItemStack.EMPTY);
            push(p);
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not empty {}'s phantom hands: {}", p.getGameProfile().getName(), String.valueOf(t));
        }
    }

    /** Tells the client what its phantom hands hold now, so the overlay is not left drawing an item that has gone. */
    public static void push(ServerPlayer p) {
        init();
        if (!ok || sync == null) return;
        try { sync.invoke(null, p); }
        catch (Throwable t) { LOGGER.warn("[astral] could not send {}'s phantom hands to their client: {}", p.getGameProfile().getName(), String.valueOf(t)); }
    }
}
