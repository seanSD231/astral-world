package com.astralspace;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Paying a player in money.
 *
 * <p>One place for it, because three systems now hand out coins and each of them guessing at what
 * this server's currency is would be three places to get it wrong. Numismatics spurs if that mod
 * is here, emeralds if it is not.
 */
public final class Loot {
    private Loot() { }

    private static Item coin;
    private static boolean looked;

    public static Item coin() {
        if (!looked) {
            looked = true;
            coin = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("numismatics:spur"))
                    .orElse(BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("minecraft:emerald")).orElse(null));
        }
        return coin;
    }

    /** Hands over an amount, in as many stacks as it takes, dropping what will not fit. */
    public static void pay(Player p, int amount) {
        Item c = coin();
        if (c == null || amount <= 0) return;
        for (int left = amount; left > 0; ) {
            int n = Math.min(c.getDefaultMaxStackSize(), left);
            ItemStack st = new ItemStack(c, n);
            if (!p.getInventory().add(st)) p.drop(st, false);
            left -= n;
        }
    }

    /** Takes an amount if they have it, and says whether it did. */
    public static boolean take(Player p, int amount) {
        Item c = coin();
        if (c == null) return false;
        if (count(p) < amount) return false;
        int left = amount;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.is(c)) continue;
            int take = Math.min(left, st.getCount());
            st.shrink(take);
            left -= take;
        }
        return true;
    }

    public static int count(Player p) {
        Item c = coin();
        if (c == null) return 0;
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(c)) n += inv.getItem(i).getCount();
        return n;
    }
}
