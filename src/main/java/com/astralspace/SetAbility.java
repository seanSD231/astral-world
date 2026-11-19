package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The thing five fragments hand you: an ability you carry and use, the way a Scribe carries one.
 *
 * <p>One item does for all of them, with the set written into its data, because eighteen registered
 * items would be eighteen models and eighteen textures for what is the same object wearing a
 * different name. It is not craftable, cannot be dropped out of the world by hand and is given and
 * taken away by {@link ArtifactSlots} as the fifth fragment goes on and comes off, so it can never
 * be carried by somebody who has not earned it.</p>
 */
public final class SetAbility extends Item {
    private static final String TAG = "astral_set_ability";

    public SetAbility() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    public static ItemStack of(String set) {
        ItemStack st = new ItemStack(AstralFloor.SET_ABILITY.get());
        CustomData.update(DataComponents.CUSTOM_DATA, st, t -> t.putString(TAG, set));
        return st;
    }

    /** Which set this belongs to, or empty if it is not one of ours. */
    public static String setOf(ItemStack st) {
        if (!(st.getItem() instanceof SetAbility)) return "";
        CustomData cd = st.get(DataComponents.CUSTOM_DATA);
        return cd == null ? "" : cd.copyTag().getString(TAG);
    }

    @Override
    public Component getName(ItemStack st) {
        ArtifactSets.Set s = ArtifactSets.get(setOf(st));
        if (s == null) return super.getName(st);
        return Component.literal(name(s)).withColor(s.colour());
    }

    /** The ability's own name: the first clause of its line, which is what it is called. */
    static String name(ArtifactSets.Set s) {
        String line = s.fiveLine();
        int dot = line.indexOf('.');
        return dot > 0 ? line.substring(0, dot) : s.title();
    }

    @Override
    public void appendHoverText(ItemStack st, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        ArtifactSets.Set s = ArtifactSets.get(setOf(st));
        if (s == null) return;
        tip.add(Component.literal(s.title() + "  ·  all five").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.empty());
        String line = s.fiveLine();
        int dot = line.indexOf('.');
        tip.add(Component.literal(dot > 0 ? line.substring(dot + 1).trim() : line).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("– " + s.fiveCost()).withColor(0xC07070));
        tip.add(Component.empty());
        tip.add(Component.literal("Right click to spend it.").withStyle(ChatFormatting.AQUA));
        tip.add(Component.literal("It is yours only while all five fragments are on you.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp) SetAbilities.cast(sp, setOf(st));
        p.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }
}
