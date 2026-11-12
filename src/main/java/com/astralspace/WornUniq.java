package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A Uniqueness you wear in the Uniqueness curio slot (or hold). It shows on your body (see {@link WornClient}) and the Uniqueness key (G)
 * fires its ability. Taking it in (sneak and hold right click) still works like every other Uniqueness.
 */
public class WornUniq extends Uniqueness {
    public interface Ability { void use(ServerPlayer sp, ItemStack stack); }

    /** The Quill's ability, kept as one object so press() can recognise it. */
    public static final Ability QUILL = UniqAbilities::quill;
    private final int cooldown;
    private final String blurb;
    private final Ability ability;

    public WornUniq(String pathId, String title, int cooldown, String blurb, Ability ability) {
        this(pathId, title, cooldown, blurb, ability, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    public WornUniq(String pathId, String title, int cooldown, String blurb, Ability ability, Item.Properties props) {
        super(pathId, title, false, props);
        this.cooldown = cooldown; this.blurb = blurb; this.ability = ability;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tip, flag);
        tip.add(Component.literal("Wear it in the Uniqueness curio slot.").withStyle(ChatFormatting.AQUA));
        tip.add(Component.literal("Press the Uniqueness key (G): " + blurb).withStyle(ChatFormatting.AQUA));
        tip.add(Component.literal("Costs " + UniqPower.cost(stack) + " spirituality. A Seq 1 or 0 of its pathway draws out its full power.").withStyle(ChatFormatting.DARK_AQUA));
        tip.add(Component.literal("Use it without taking it in and it costs half your corruption limit as well.").withStyle(ChatFormatting.DARK_RED));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        if (p.isShiftKeyDown()) return super.use(level, p, hand);
        return InteractionResultHolder.pass(p.getItemInHand(hand));
    }

    /** The first worn Uniqueness of any kind: in a hand or in the Curios Uniqueness slot (Curios by reflection, so it stays optional). */
    public static ItemStack findWorn(LivingEntity p) {
        if (p.getMainHandItem().getItem() instanceof WornUniq || p.getMainHandItem().getItem() instanceof DoorEyes) return p.getMainHandItem();
        if (p.getOffhandItem().getItem() instanceof WornUniq || p.getOffhandItem().getItem() instanceof DoorEyes) return p.getOffhandItem();
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object opt = api.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, p);
            if (!(opt instanceof java.util.Optional<?> o) || o.isEmpty()) return ItemStack.EMPTY;
            Object h = o.get();
            Object eq = h.getClass().getMethod("getEquippedCurios").invoke(h);
            if (eq instanceof net.neoforged.neoforge.items.IItemHandlerModifiable m)
                for (int i = 0; i < m.getSlots(); i++) { Item it = m.getStackInSlot(i).getItem(); if (it instanceof WornUniq || it instanceof DoorEyes) return m.getStackInSlot(i); }
        } catch (Throwable ignored) { }
        return ItemStack.EMPTY;
    }

    /** The Uniqueness key was pressed. */
    public static void press(ServerPlayer sp) {
        ItemStack st = findWorn(sp);
        if (st.isEmpty()) return;
        if (st.getItem() instanceof DoorEyes) { DoorEyes.trigger(sp); return; }
        WornUniq w = (WornUniq) st.getItem();
        if (sp.getCooldowns().isOnCooldown(w)) { sp.displayClientMessage(Component.literal("Still recovering...").withStyle(ChatFormatting.GRAY), true); return; }
        boolean quillEmp = w.ability == (Ability) QUILL && UniqPower.emp(sp, w.pathId());   // the Quill at Seq 1/0: half the cost, half the wait
        int cost = UniqPower.cost(st) / (quillEmp ? 2 : 1);
        if (!UniqPower.pay(sp, cost)) { sp.displayClientMessage(Component.literal("Not enough spirituality (" + cost + ").").withStyle(ChatFormatting.GRAY), true); return; }
        UniqAbilities.refund = false;
        UniqPower.toll(sp, w.pathId());   // wielding a Uniqueness you never took in costs corruption
        try { w.ability.use(sp, st); } catch (Throwable t) { UniqPower.refund(sp, cost); com.mojang.logging.LogUtils.getLogger().warn("[astral] uniqueness ability failed: {}", t.toString()); return; }
        if (UniqAbilities.refund) { UniqPower.refund(sp, cost); return; }
        if (w.cooldown > 0) sp.getCooldowns().addCooldown(w, quillEmp ? w.cooldown / 2 : w.cooldown);
    }
}
