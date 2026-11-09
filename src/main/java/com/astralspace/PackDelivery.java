package com.astralspace;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The cards of an opened pack are held here and handed over one at a time as the player flips them, so the inventory (and the Great Old One
 * advancement) never gives away what is still face down. Anything not yet handed over is given when the screen closes, when another pack is
 * opened, when the player leaves, when the server stops, or after three minutes.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class PackDelivery {
    private PackDelivery() {}

    private static final class Hold { final ItemStack[] cards; final long at; Hold(ItemStack[] c, long at) { cards = c; this.at = at; } }
    private static final Map<UUID, Hold> HOLD = new HashMap<>();

    /** Keep these cards for the player until they are flipped. Anything still held from an earlier pack is given first. */
    static void hold(ServerPlayer sp, ItemStack[] cards) {
        reveal(sp, -1);
        HOLD.put(sp.getUUID(), new Hold(cards, sp.level().getGameTime()));
    }

    /** Give card i (or, for -1, every card still held). */
    static void reveal(ServerPlayer sp, int i) {
        Hold h = HOLD.get(sp.getUUID());
        if (h == null) return;
        for (int k = 0; k < h.cards.length; k++) if ((i < 0 || i == k) && h.cards[k] != null) give(sp, h, k);
        boolean left = false;
        for (ItemStack s : h.cards) if (s != null) left = true;
        if (!left) HOLD.remove(sp.getUUID());
    }

    private static void give(ServerPlayer sp, Hold h, int k) {
        ItemStack c = h.cards[k]; h.cards[k] = null;
        var data = c.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        boolean goo = data != null && data.copyTag().getString("pathway").startsWith("goo_");
        if (!sp.getInventory().add(c)) sp.drop(c, false);
        if (goo) {   // advancement: pulled a Great Old One from a pack
            var adv = sp.server.getAdvancements().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "great_old_one"));
            if (adv != null) sp.getAdvancements().award(adv, "got");
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer sp) reveal(sp, -1); }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) { for (ServerPlayer sp : e.getServer().getPlayerList().getPlayers()) reveal(sp, -1); }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (HOLD.isEmpty() || e.getServer().getTickCount() % 100 != 0) return;
        for (ServerPlayer sp : e.getServer().getPlayerList().getPlayers()) {
            Hold h = HOLD.get(sp.getUUID());
            if (h != null && sp.level().getGameTime() - h.at > 3600) reveal(sp, -1);
        }
    }
}
