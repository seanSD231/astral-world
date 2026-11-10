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
 * A Uniqueness item for one LOTM pathway. Hold right click for 5 seconds and it is accommodated as that pathway's
 * sequence 0 characteristic, through LOTM's own accommodation, so LOTM's rules still decide whether it takes.
 * LOTM is called by reflection like everything else in {@link Lotm}.
 */
public class Uniqueness extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int HOLD_TICKS = 100;
    private final String pathId, title;
    private final boolean model3d;

    public Uniqueness(String pathId, String title) { this(pathId, title, false); }

    public Uniqueness(String pathId, String title, boolean model3d) {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
        this.pathId = pathId; this.title = title; this.model3d = model3d;
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        if (model3d) consumer.accept(UniqClient.EXT);
    }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return HOLD_TICKS; }
    @Override public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
        tip.add(Component.literal(title + " Uniqueness").withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal("Hold right click for 5 seconds to take it in.").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity who, ItemStack stack, int remaining) {
        if (level.isClientSide || !(who instanceof ServerPlayer sp)) return;
        int done = HOLD_TICKS - remaining;
        if (done % 20 == 0 && done > 0) sp.displayClientMessage(Component.literal("Taking in the Uniqueness... " + (HOLD_TICKS - done) / 20 + "s").withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity who) {
        if (!level.isClientSide && who instanceof ServerPlayer sp) accommodate(sp);
        return stack;
    }

    private static void say(ServerPlayer p, String s, ChatFormatting c) { p.sendSystemMessage(Component.literal(s).withStyle(c)); }

    private void accommodate(ServerPlayer p) {
        try {
            Object path = Lotm.pathwayById(pathId);
            if (path == null) { say(p, "Can't find the " + title + " pathway (is LOTM loaded?).", ChatFormatting.RED); return; }
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("lotm:beyondercharacteristics"));
            if (item == null || item == net.minecraft.world.item.Items.AIR) { say(p, "LOTM's characteristic item was not found.", ChatFormatting.RED); return; }
            ItemStack st = new ItemStack(item);
            Class<?> bc = Class.forName("net.swimmingtuna.lotm.item.BeyonderPotions.BeyonderCharacteristic");
            Class<?> bcl = Class.forName("net.swimmingtuna.lotm.beyonder.api.BeyonderClass");
            bc.getMethod("setData", ItemStack.class, bcl, int.class, UUID.class, String.class).invoke(null, st, path, 0, UUID.randomUUID(), title);
            Object res = bc.getMethod("takeIn", Player.class, ItemStack.class, boolean.class).invoke(item, p, st, true);
            boolean ok = res instanceof InteractionResultHolder<?> h && h.getResult().consumesAction();
            LOGGER.info("[astral] {} uniqueness for {} -> {}", pathId, p.getGameProfile().getName(), res instanceof InteractionResultHolder<?> h2 ? h2.getResult() : res);
            if (!ok) say(p, "The " + title + " Uniqueness was refused (LOTM decides who can take it in).", ChatFormatting.GRAY);
        } catch (Throwable t) {
            LOGGER.warn("[astral] uniqueness accommodation failed: {}", t.toString());
            say(p, "It failed: " + t.getClass().getSimpleName(), ChatFormatting.RED);
        }
    }
}
