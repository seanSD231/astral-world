package com.astralspace;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Paying a player in money, and counting what they have.
 *
 * <p>One place for it, because several systems hand out coins and each of them guessing at what
 * this server's currency is would be several places to get it wrong. Numismatics if that mod is
 * here, emeralds if it is not.</p>
 *
 * <p>Numismatics money is not one item. It is six, each worth a fixed number of the smallest, and
 * a player with a pocket full of cogs is carrying 64 spurs apiece and not a single spur. Counting
 * spurs alone read that player as penniless, which is why they could not pay into a tournament
 * while plainly holding the money for it. Everything here works in spur value and breaks coins
 * when it has to, handing back the change.</p>
 */
public final class Loot {
    private Loot() { }

    /** A coin and what it is worth in the smallest unit, smallest first. */
    private record Coin(Item item, int value) { }

    /** Numismatics' denominations, smallest first. */
    private static final String[][] NUMIS = {
        {"numismatics:spur", "1"}, {"numismatics:bevel", "8"}, {"numismatics:sprocket", "16"},
        {"numismatics:cog", "64"}, {"numismatics:crown", "512"}, {"numismatics:sun", "4096"},
    };

    private static List<Coin> coins;

    /** The denominations this server actually has, smallest first. */
    private static List<Coin> coins() {
        if (coins != null) return coins;
        List<Coin> found = new ArrayList<>();
        for (String[] row : NUMIS)
            BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(row[0]))
                    .ifPresent(i -> found.add(new Coin(i, Integer.parseInt(row[1]))));
        if (found.isEmpty())
            BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("minecraft:emerald"))
                    .ifPresent(i -> found.add(new Coin(i, 1)));
        coins = found;
        return coins;
    }

    /** The smallest denomination: what an amount is quoted in. */
    public static Item coin() {
        List<Coin> cs = coins();
        return cs.isEmpty() ? null : cs.get(0).item();
    }

    /** What they are carrying, in the smallest unit, counting every denomination. */
    public static int count(Player p) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            for (Coin c : coins()) if (st.is(c.item())) { n += st.getCount() * c.value(); break; }
        }
        return n;
    }

    /** Hands over an amount, in the largest coins that fit, dropping what will not go in. */
    public static void pay(Player p, int amount) {
        List<Coin> cs = coins();
        if (cs.isEmpty() || amount <= 0) return;
        int left = amount;
        for (int d = cs.size() - 1; d >= 0 && left > 0; d--) {
            Coin c = cs.get(d);
            int many = left / c.value();
            left -= many * c.value();
            while (many > 0) {
                int n = Math.min(c.item().getDefaultMaxStackSize(), many);
                ItemStack st = new ItemStack(c.item(), n);
                if (!p.getInventory().add(st)) p.drop(st, false);
                many -= n;
            }
        }
    }

    /**
     * Takes an amount if they have it, breaking a larger coin and handing back the change when
     * their small change does not cover it. Says whether it did.
     */
    public static boolean take(Player p, int amount) {
        if (amount <= 0) return true;
        List<Coin> cs = coins();
        if (cs.isEmpty() || count(p) < amount) return false;
        var inv = p.getInventory();
        int need = amount;

        // smallest first, so the fewest coins get broken
        for (Coin c : cs) {
            if (need <= 0) break;
            for (int i = 0; i < inv.getContainerSize() && need >= c.value(); i++) {
                ItemStack st = inv.getItem(i);
                if (!st.is(c.item())) continue;
                int use = Math.min(st.getCount(), need / c.value());
                st.shrink(use);
                need -= use * c.value();
            }
        }
        // what is left is smaller than any coin they still hold: break one and take change back
        while (need > 0) {
            Coin broke = null;
            int slot = -1;
            for (Coin c : cs) {                       // the smallest coin that covers the rest
                if (c.value() < need) continue;
                for (int i = 0; i < inv.getContainerSize(); i++)
                    if (inv.getItem(i).is(c.item())) { broke = c; slot = i; break; }
                if (broke != null) break;
            }
            if (broke == null) return false;          // cannot happen: count() said they had it
            inv.getItem(slot).shrink(1);
            int change = broke.value() - need;
            need = 0;
            if (change > 0) pay(p, change);
        }
        return true;
    }
}
