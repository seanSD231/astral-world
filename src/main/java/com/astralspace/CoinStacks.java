package com.astralspace;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/** Create: Numismatics coins stack to 9999, like the LOTM coins they are swapped with, instead of 64. Does nothing if Numismatics is not installed. */
public final class CoinStacks {
    public static final int MAX = 9999;
    private static final String[] COINS = {"spur", "bevel", "sprocket", "cog", "crown", "sun"};

    private CoinStacks() {}

    public static void modify(ModifyDefaultComponentsEvent event) {
        for (String id : COINS) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("numismatics", id));
            if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
            event.modify(item, b -> b.set(DataComponents.MAX_STACK_SIZE, MAX));
        }
    }

    /**
     * Where the 99 came from: the inventory, slots and saving all cap at 99, and LOTM lifts that cap (its own mixins, saving the extra count in NBT)
     * only for the items on a private list in {@code LOTM.MAX_STACK_ITEM_IDS}. That list is an immutable Set.of(...), so Numismatics coins were never on it.
     * This swaps in a bigger set that also holds the six Numismatics coins, so they get the same 9999 handling as the LOTM coins.
     */
    public static void patchLotmList() {
        try {
            Class<?> lotm = Class.forName("net.swimmingtuna.lotm.LOTM");
            java.lang.reflect.Field f = lotm.getDeclaredField("MAX_STACK_ITEM_IDS");
            f.setAccessible(true);
            java.util.Set<ResourceLocation> now = new java.util.HashSet<>();
            Object cur = f.get(null);
            if (cur instanceof java.util.Collection<?> c) for (Object o : c) if (o instanceof ResourceLocation r) now.add(r);
            for (String id : COINS) now.add(ResourceLocation.fromNamespaceAndPath("numismatics", id));
            java.lang.reflect.Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            uf.setAccessible(true);
            sun.misc.Unsafe u = (sun.misc.Unsafe) uf.get(null);
            u.putObjectVolatile(u.staticFieldBase(f), u.staticFieldOffset(f), java.util.Set.copyOf(now));
            com.mojang.logging.LogUtils.getLogger().info("[astral] LOTM 9999-stack list now holds {} items (Numismatics coins added)", now.size());
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().warn("[astral] could not add Numismatics coins to LOTM's 9999-stack list: {}", t.toString());
        }
    }
}
