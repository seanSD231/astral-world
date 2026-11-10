package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.List;
import java.util.function.Consumer;

/** The shield that comes with the Sword of Shadow. It exists only while the sword is held (see {@link ShadowGear}). */
public class ShadowShield extends ShieldItem {
    public ShadowShield() { super(new Item.Properties().stacksTo(1).durability(2000).rarity(Rarity.EPIC)); }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
        tip.add(Component.literal("Made of shadow. It fades when the sword is put away.").withStyle(ChatFormatting.DARK_PURPLE));
    }

    @Override public boolean isFoil(ItemStack stack) { return false; }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) { consumer.accept(UniqClient.EXT); }
}
