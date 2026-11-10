package com.astralspace;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.UUID;

/**
 * Test item: a monocle. Sneak + right click puts it on your head. Hold right click for 5 seconds and it is
 * accommodated as the Marauder Uniqueness (a sequence 0 characteristic), through LOTM's own accommodation, so
 * LOTM's rules still decide whether it takes. LOTM is called by reflection like everything else in {@link Lotm}.
 */
public class Monocle extends Item implements Equipable {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int HOLD_TICKS = 100;

    public Monocle() { super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)); }

    @Override public EquipmentSlot getEquipmentSlot() { return EquipmentSlot.HEAD; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.SPYGLASS; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return HOLD_TICKS; }
    @Override public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) return Equipable.swapWithEquipmentSlot(this, level, player, hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity who, ItemStack stack, int remaining) {
        if (level.isClientSide || !(who instanceof ServerPlayer sp)) return;
        int done = HOLD_TICKS - remaining;
        if (done % 20 == 0 && done > 0) sp.displayClientMessage(Component.literal("The monocle clouds over... " + (HOLD_TICKS - done) / 20 + "s").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), true);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity who) {
        if (!level.isClientSide && who instanceof ServerPlayer sp) accommodate(sp);
        return stack;
    }

    private static void say(ServerPlayer p, String s, net.minecraft.ChatFormatting c) { p.sendSystemMessage(Component.literal(s).withStyle(c)); }

    /** Builds a LOTM Marauder sequence 0 characteristic and takes it in through LOTM's own item. */
    static void accommodate(ServerPlayer p) {
        try {
            Object marauder = Lotm.pathwayById("marauder");
            if (marauder == null) { say(p, "The monocle can't find the Marauder pathway (is LOTM loaded?).", net.minecraft.ChatFormatting.RED); return; }
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("lotm:beyondercharacteristics"));
            if (item == null || item == net.minecraft.world.item.Items.AIR) { say(p, "LOTM's characteristic item was not found.", net.minecraft.ChatFormatting.RED); return; }
            ItemStack st = new ItemStack(item);
            Class<?> bc = Class.forName("net.swimmingtuna.lotm.item.BeyonderPotions.BeyonderCharacteristic");
            Class<?> bcl = Class.forName("net.swimmingtuna.lotm.beyonder.api.BeyonderClass");
            bc.getMethod("setData", ItemStack.class, bcl, int.class, UUID.class, String.class).invoke(null, st, marauder, 0, UUID.randomUUID(), "the Monocle");
            Object res = bc.getMethod("takeIn", Player.class, ItemStack.class, boolean.class).invoke(item, p, st, true);
            boolean ok = res instanceof InteractionResultHolder<?> h && h.getResult().consumesAction();
            LOGGER.info("[astral] monocle accommodation for {} -> {}", p.getGameProfile().getName(), res instanceof InteractionResultHolder<?> h2 ? h2.getResult() : res);
            if (!ok) say(p, "The Marauder Uniqueness was refused (LOTM decides who can take it in).", net.minecraft.ChatFormatting.GRAY);
        } catch (Throwable t) {
            LOGGER.warn("[astral] monocle accommodation failed: {}", t.toString());
            say(p, "The monocle failed: " + t.getClass().getSimpleName(), net.minecraft.ChatFormatting.RED);
        }
    }
}
