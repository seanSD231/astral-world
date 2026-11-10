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
}
