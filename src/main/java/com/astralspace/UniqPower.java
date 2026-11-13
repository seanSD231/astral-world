package com.astralspace;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * What every Uniqueness ability shares: its spirituality cost, the Seq 1 / Seq 0 form it takes for someone who is on that item's own
 * pathway, and what it costs in corruption to carry and to use one (they are sealed artifacts: nobody takes one in any more).
 * LOTM is reached by reflection; without it abilities are free, never empowered, and the corruption is simply not applied.
 */
public final class UniqPower {
    private UniqPower() {}

    /** Spirituality per use, by item id. */
    private static final Map<String, Integer> COST = Map.of("quill_of_alzuhod", 500, "chained_bow", 3000, "snake_rifle", 1500, "shadow_sword", 3000, "starry_sniper", 4000,
        "spear_of_sun", 3000, "bone_greatsword", 4000, "twilight_longsword", 2500, "storm_katana", 3000);

    public static int cost(ItemStack st) { return COST.getOrDefault(BuiltInRegistries.ITEM.getKey(st.getItem()).getPath(), 0); }

    /** True for a Seq 1 or Seq 0 of this pathway: the Uniqueness answers them fully. */
    public static boolean emp(LivingEntity e, String pathId) {
        if (!(e instanceof ServerPlayer sp)) return false;
        try { int s = Lotm.sequence(sp); return s >= 0 && s <= 1 && pathId.equals(Lotm.pathwayId(sp)); } catch (Throwable t) { return false; }
    }

    private static Class<?> spirit() throws ClassNotFoundException { return Class.forName("net.swimmingtuna.lotm.beyonder.Spirituality"); }

    /** Takes the spirituality; false (and nothing taken) if there is not enough. Creative players and worlds without LOTM pay nothing. */
    public static boolean pay(ServerPlayer sp, int cost) {
        if (cost <= 0 || sp.isCreative()) return true;
        try {
            Class<?> c = spirit();
            int have = (int) c.getMethod("getSpirituality", LivingEntity.class).invoke(null, sp);
            if (have < cost) return false;
            c.getMethod("useSpirituality", LivingEntity.class, int.class).invoke(null, sp, cost);
            return true;
        } catch (ClassNotFoundException e) { return true;
        } catch (Throwable t) { return true; }
    }

    /**
     * The price of using a Uniqueness. They are sealed artifacts now, so nobody accommodates one and nobody is exempt:
     * every use costs {@link Settings#uniqUseCorruption} percent of the corruption limit on the item's own pathway.
     * That used to be half the limit, and only for someone who had not taken the thing in.
     */
    public static void toll(ServerPlayer sp, String pathId) {
        int pc = Settings.uniqUseCorruption;
        if (pc <= 0 || sp.isCreative()) return;
        try { Lotm.addCorruptionPercent(sp, Lotm.pathwayById(pathId), pc); } catch (Throwable ignored) { return; }
        sp.displayClientMessage(net.minecraft.network.chat.Component.literal("The seal gives, and the corruption takes its share.")
            .withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
    }

    // ---- what simply carrying a sealed artifact costs

    private static final ResourceLocation BURDEN = ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "uniqueness_burden");
    private static final java.util.Set<java.util.UUID> BURDENED = new java.util.HashSet<>();

    /** The Uniqueness this player is carrying anywhere at all: hand, Curios slot or pack. Empty if there is none. */
    private static ItemStack carried(ServerPlayer sp) {
        ItemStack worn = WornUniq.findWorn(sp);
        if (!worn.isEmpty()) return worn;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) { ItemStack st = inv.getItem(i); if (st.getItem() instanceof Uniqueness) return st; }
        return ItemStack.EMPTY;
    }

    /**
     * Carrying one of these is its own price, wielded or not: two hearts off your maximum health, slow hands, and a
     * drip of corruption on the artifact's own pathway every ten seconds. It all lifts the moment the thing leaves you.
     */
    public static void carryTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 20 != 0) return;
        boolean drip = e.getServer().getTickCount() % 200 == 0 && Settings.uniqCarryCorruption > 0;
        for (ServerPlayer sp : e.getServer().getPlayerList().getPlayers()) {
            ItemStack st = sp.isCreative() || sp.isSpectator() ? ItemStack.EMPTY : carried(sp);
            var mh = sp.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (st.isEmpty()) {
                if (BURDENED.remove(sp.getUUID()) && mh != null) {
                    mh.removeModifier(BURDEN);
                    if (sp.getHealth() > sp.getMaxHealth()) sp.setHealth(sp.getMaxHealth());
                }
                continue;
            }
            if (BURDENED.add(sp.getUUID()) && mh != null) {
                mh.removeModifier(BURDEN);
                mh.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(BURDEN, -4, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
                if (sp.getHealth() > sp.getMaxHealth()) sp.setHealth(sp.getMaxHealth());
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal("The sealed artifact weighs on you.").withStyle(net.minecraft.ChatFormatting.DARK_GRAY), true);
            }
            sp.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, 60, 0, true, false, false));
            if (drip && st.getItem() instanceof Uniqueness u)
                try { Lotm.addCorruptionPercent(sp, Lotm.pathwayById(u.pathId()), Settings.uniqCarryCorruption); } catch (Throwable ignored) { }
        }
    }

    /** Dropped on logout, so the burden never outlives the carrying. */
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            BURDENED.remove(sp.getUUID());
            var mh = sp.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (mh != null) mh.removeModifier(BURDEN);
        }
    }

    /**
     * Keeps players inside a circle: anyone at its edge is shoved back toward the middle, and anyone already outside is set back inside.
     * Uses the ServerPlayer teleport so the client follows (a plain Entity teleport leaves them rubber-banding back out).
     */
    public static void contain(net.minecraft.server.level.ServerLevel lv, net.minecraft.world.phys.Vec3 c, double r, ServerPlayer except) {
        for (ServerPlayer pl : lv.players()) {
            if (pl == except || pl.isSpectator() || pl.isCreative()) continue;
            double dx = pl.getX() - c.x, dz = pl.getZ() - c.z, d = Math.sqrt(dx * dx + dz * dz);
            if (d > r + 24 || Math.abs(pl.getY() - c.y) > r + 12) continue;                      // far outside: it was never holding them
            if (d < r - 1.5) continue;                                                           // comfortably inside
            double ix = -dx / Math.max(d, 1e-3), iz = -dz / Math.max(d, 1e-3);
            pl.setDeltaMovement(ix * .9, Math.max(pl.getDeltaMovement().y, .05), iz * .9);
            pl.hurtMarked = true;
            if (d > r) pl.teleportTo(lv, c.x + dx / d * (r - 2), pl.getY(), c.z + dz / d * (r - 2), pl.getYRot(), pl.getXRot());
        }
    }

    public static void refund(ServerPlayer sp, int cost) {
        if (cost <= 0 || sp.isCreative()) return;
        try { spirit().getMethod("addSpirituality", LivingEntity.class, int.class).invoke(null, sp, cost); } catch (Throwable ignored) { }
    }

    /** LOTM's stun, with a plain root as the stand-in when LOTM is missing. */
    public static void stun(LivingEntity e, int ticks) {
        if (Lotm.util("applyStun", e, ticks) == null && !lotm()) {
            e.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false));
            e.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.JUMP, ticks, 128, false, false));
        }
    }

    public static void bleed(LivingEntity e, int ticks) {
        if (Lotm.util("applyBleeding", e, ticks, 1) == null && !lotm()) e.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, ticks, 0));
    }

    /** One of LOTM's own mob effects ("twilight" ...); nothing if it does not exist. */
    public static void effect(LivingEntity e, String name, int ticks, int amp) {
        BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.fromNamespaceAndPath("lotm", name)).ifPresent(h -> e.addEffect(new MobEffectInstance(h, ticks, amp)));
    }

    /** Ages someone by this share of their own lifespan (LOTM's ageing). */
    public static void age(Entity e, float share) {
        try {
            Class<?> c = Class.forName("net.swimmingtuna.lotm.beyonder.Age");
            int max = e instanceof LivingEntity le ? (int) c.getMethod("getMaxAge", LivingEntity.class).invoke(null, le) : 0;
            if (max > 0) c.getMethod("addAge", Entity.class, int.class).invoke(null, e, Math.round(max * share));
        } catch (Throwable ignored) { }
    }

    private static Boolean lotm;
    private static boolean lotm() {
        if (lotm == null) { try { spirit(); lotm = true; } catch (Throwable t) { lotm = false; } }
        return lotm;
    }
}
