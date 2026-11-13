package com.astralspace;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;

/**
 * A Uniqueness item for one LOTM pathway. These are sealed artifacts: they can be wielded but never taken in, so
 * holding one no longer makes anyone a Beyonder of that pathway (it used to, on a five second sneak and hold).
 * What they cost instead is corruption, in {@link UniqPower}: a little for carrying one, more for every use.
 */
public class Uniqueness extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int HOLD_TICKS = 100;
    private final String pathId, title;
    public String pathId() { return pathId; }
    private final boolean model3d;

    public Uniqueness(String pathId, String title) { this(pathId, title, false); }

    public Uniqueness(String pathId, String title, boolean model3d) { this(pathId, title, model3d, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)); }

    protected Uniqueness(String pathId, String title, boolean model3d, Item.Properties props) {
        super(props);
        this.pathId = pathId; this.title = title; this.model3d = model3d;
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        if (model3d) consumer.accept(UniqClient.EXT);
    }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return HOLD_TICKS; }   // for the few that still hold (the Rapier's lunge); UniqClient animates moving parts against it
    @Override public boolean isFoil(ItemStack stack) { return false; }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
        tip.add(Component.literal(title + " Uniqueness").withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal("A sealed artifact. It cannot be taken in.").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.literal("Carrying it corrupts you a little, and using it more.").withStyle(ChatFormatting.DARK_RED));
    }

}
