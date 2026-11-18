package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A mask. Right-click it to put it on: your skin disappears under a hooded robe in the mask's colour, and the name
 * over your head and in chat becomes whatever the mask is called. Rename it on an anvil to choose that name.
 */
public class Mask extends Item {
    private final int colour;

    public Mask(int colour) {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
        this.colour = colour;
    }

    public int colour() { return colour; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (p instanceof ServerPlayer sp) Masks.toggle(sp, st, colour);
        p.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
        tip.add(Component.literal("Right click to wear it, and again to take it off.").withStyle(ChatFormatting.AQUA));
        tip.add(Component.literal("Your skin goes under the robe; the name over your head and in chat")
                .withStyle(ChatFormatting.DARK_AQUA));
        tip.add(Component.literal("becomes this mask's name. Rename it on an anvil to choose one.")
                .withStyle(ChatFormatting.DARK_AQUA));
        tip.add(Component.literal("Lose the mask and the name goes with it.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
