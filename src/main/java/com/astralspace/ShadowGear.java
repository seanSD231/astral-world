package com.astralspace;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Holding the Sword of Shadow puts the Shield of Shadow in the off hand; putting the sword away takes it back. */
public final class ShadowGear {
    private ShadowGear() {}

    public static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % 2 != 0) return;
        boolean sword = p.getMainHandItem().is(AstralFloor.SHADOW_SWORD_ITEM.get());
        ItemStack off = p.getOffhandItem();
        if (sword) {
            if (off.isEmpty()) p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AstralFloor.SHADOW_SHIELD.get()));
        } else if (off.is(AstralFloor.SHADOW_SHIELD.get())) {
            p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.items.size(); i++) if (inv.items.get(i).is(AstralFloor.SHADOW_SHIELD.get())) inv.items.set(i, ItemStack.EMPTY);
    }

    /** A shield dropped or left behind just vanishes. */
    public static void drop(net.neoforged.neoforge.event.entity.item.ItemTossEvent e) {
        if (e.getEntity().getItem().is(AstralFloor.SHADOW_SHIELD.get())) e.getEntity().discard();
    }
}
