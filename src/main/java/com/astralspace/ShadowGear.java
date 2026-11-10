package com.astralspace;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** While the Ball of Lightning is worn the off hand is taken up by it: whatever was there (a shield, say) goes back to the inventory and nothing else fits. */
public final class ShadowGear {
    private ShadowGear() {}

    public static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % 3 != 0) return;
        ItemStack w = WornUniq.findWorn(p);
        boolean has = !w.isEmpty() && BuiltInRegistries.ITEM.getKey(w.getItem()).getPath().equals("storm_katana");
        ItemStack off = p.getOffhandItem();
        if (has && off.getItem() != w.getItem()) {
            if (!off.is(AstralFloor.LIGHTNING_HOLD.get())) {
                if (!off.isEmpty() && !p.getInventory().add(off.copy())) p.drop(off.copy(), false);
                p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AstralFloor.LIGHTNING_HOLD.get()));
            }
        } else if (off.is(AstralFloor.LIGHTNING_HOLD.get())) {
            p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.items.size(); i++) if (inv.items.get(i).is(AstralFloor.LIGHTNING_HOLD.get())) inv.items.set(i, ItemStack.EMPTY);
    }

    /** A hand-filler dropped or left behind just vanishes. */
    public static void drop(net.neoforged.neoforge.event.entity.item.ItemTossEvent e) {
        if (e.getEntity().getItem().is(AstralFloor.LIGHTNING_HOLD.get())) e.getEntity().discard();
    }
}
