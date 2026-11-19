package com.astralspace;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who is wearing a mask, and under what name.
 *
 * <p>The server is the only authority: it holds the map, writes it into the player's own data so it survives a
 * relog, and tells every client about every change. The clients use it to draw the robe over the body (see
 * {@link MaskClient}) and to put the chosen name over the head in place of the real one; chat is rewritten here.</p>
 *
 * <p>The name is whatever the mask itself is called, so it is set the ordinary way: rename the mask on an anvil.
 * An unnamed mask shows simply "Masked".</p>
 */
public final class Masks {
    private Masks() { }

    public static final String[] COLOURS = {"black", "white", "crimson", "azure", "verdant", "gilded"};
    /** Robe colour per mask, and the colour its name is written in. */
    public static final int[] CLOTH = {0xFF17161C, 0xFFDDDDE4, 0xFF7A1420, 0xFF16305E, 0xFF14432A, 0xFF5C4612};
    public static final int[] TRIM = {0xFF3A3844, 0xFFFFFFFF, 0xFFD24054, 0xFF4A86D8, 0xFF46C078, 0xFFE0C060};

    private static final String TAG = "astral_mask";
    private static final Map<UUID, int[]> COLOUR = new HashMap<>();     // uuid -> {colour}
    private static final Map<UUID, String> NAME = new HashMap<>();

    public static boolean masked(UUID id) { return COLOUR.containsKey(id); }

    public static int colourOf(UUID id) { int[] c = COLOUR.get(id); return c == null ? -1 : c[0]; }

    public static String nameOf(UUID id) { return NAME.getOrDefault(id, "Masked"); }

    /** Takes a mask off, or -- if none is on -- asks them what they want to be called behind this one. */
    public static void toggle(ServerPlayer sp, ItemStack st, int colour) {
        if (COLOUR.containsKey(sp.getUUID())) {
            clear(sp);
            sp.displayClientMessage(Component.literal("You take the mask off.").withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return;
        }
        PacketDistributor.sendToPlayer(sp, new DoorNet.MaskAsk(colour));
    }

    /**
     * Puts the mask on under the name they chose, and tells everyone. The client asked for this, so
     * the name is theirs to pick -- but it is checked here, and so is the mask: nothing stops a
     * client sending this without one in hand.
     */
    public static void wear(ServerPlayer sp, int colour, String name) {
        if (COLOUR.containsKey(sp.getUUID())) return;
        if (colour < 0 || colour >= COLOURS.length) return;
        boolean has = false;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize() && !has; i++)
            if (inv.getItem(i).getItem() instanceof Mask m && m.colour() == colour) has = true;
        if (!has) return;
        name = name == null ? "" : name.trim();
        if (name.length() > 24) name = name.substring(0, 24);
        if (name.isEmpty()) name = "Masked";
        COLOUR.put(sp.getUUID(), new int[]{colour});
        NAME.put(sp.getUUID(), name);
        save(sp);
        broadcast(sp);
        sp.displayClientMessage(Component.literal("You are " + name + " now.").withStyle(net.minecraft.ChatFormatting.AQUA), true);
    }

    private static void clear(ServerPlayer sp) {
        COLOUR.remove(sp.getUUID());
        NAME.remove(sp.getUUID());
        save(sp);
        broadcast(sp);
    }

    private static void save(ServerPlayer sp) {
        CompoundTag root = sp.getPersistentData();
        if (COLOUR.containsKey(sp.getUUID())) {
            CompoundTag t = new CompoundTag();
            t.putInt("c", colourOf(sp.getUUID()));
            t.putString("n", nameOf(sp.getUUID()));
            root.put(TAG, t);
        } else {
            root.remove(TAG);
        }
    }

    private static void broadcast(ServerPlayer sp) {
        var p = new DoorNet.Mask(sp.getUUID(), colourOf(sp.getUUID()), nameOf(sp.getUUID()));
        for (ServerPlayer other : sp.server.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(other, p);
    }

    /** On the way in: remember what this player had on, and catch them up on everyone else. */
    public static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        CompoundTag root = sp.getPersistentData();
        if (root.contains(TAG)) {
            CompoundTag t = root.getCompound(TAG);
            COLOUR.put(sp.getUUID(), new int[]{t.getInt("c")});
            NAME.put(sp.getUUID(), t.getString("n"));
        }
        for (UUID id : COLOUR.keySet()) PacketDistributor.sendToPlayer(sp, new DoorNet.Mask(id, colourOf(id), nameOf(id)));
        if (COLOUR.containsKey(sp.getUUID())) broadcast(sp);
    }

    /** A mask only works while you still have it: lose it and the mask comes off. */
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 20 != 0 || COLOUR.isEmpty()) return;
        for (ServerPlayer sp : e.getServer().getPlayerList().getPlayers()) {
            if (!COLOUR.containsKey(sp.getUUID())) continue;
            boolean has = false;
            var inv = sp.getInventory();
            for (int i = 0; i < inv.getContainerSize() && !has; i++) if (inv.getItem(i).getItem() instanceof Mask) has = true;
            if (!has) {
                clear(sp);
                sp.displayClientMessage(Component.literal("The mask is gone, and so is the name.").withStyle(net.minecraft.ChatFormatting.GRAY), true);
            }
        }
    }

    /** Speaking from behind a mask: the line goes out under the chosen name, and never the account's. */
    public static void chat(net.neoforged.neoforge.event.ServerChatEvent e) {
        ServerPlayer sp = e.getPlayer();
        if (!COLOUR.containsKey(sp.getUUID())) return;
        int c = Math.max(0, Math.min(TRIM.length - 1, colourOf(sp.getUUID())));
        e.setCanceled(true);
        Component line = Component.empty()
                .append(Component.literal("<" + nameOf(sp.getUUID()) + "> ").withColor(TRIM[c] & 0xFFFFFF))
                .append(Component.literal(e.getRawText()).withStyle(net.minecraft.ChatFormatting.WHITE));
        sp.server.getPlayerList().broadcastSystemMessage(line, false);
    }

    /** Gone is gone: the map lets go, and the saved tag brings the mask back next time they log in. */
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) { COLOUR.remove(sp.getUUID()); NAME.remove(sp.getUUID()); }
    }
}
