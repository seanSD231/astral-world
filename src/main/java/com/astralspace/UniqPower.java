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
 * What every Uniqueness ability shares: its spirituality cost, and the Seq 1 / Seq 0 form it takes for someone who is on that item's own pathway.
 * LOTM is reached by reflection; without it abilities are free and never empowered.
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
     * The price of using a Uniqueness you have not taken in: half your corruption limit, on the item's own pathway.
     * Someone who has accommodated it (a Seq 1/0 of that pathway, or anyone LOTM says holds it) pays nothing.
     */
    public static void toll(ServerPlayer sp, String pathId) {
        if (sp.isCreative() || accommodated(sp, pathId)) return;
        try { Lotm.addCorruptionPercent(sp, Lotm.pathwayById(pathId), 50); } catch (Throwable ignored) { return; }
        sp.displayClientMessage(net.minecraft.network.chat.Component.literal("It is not yours to wield. The corruption takes its half.")
            .withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
    }

    /** Has this player taken this Uniqueness in? True when LOTM puts them on its pathway at all (the accommodation sets it). */
    public static boolean accommodated(ServerPlayer sp, String pathId) {
        try { return pathId.equals(Lotm.pathwayId(sp)); } catch (Throwable t) { return true; }   // without LOTM nothing is owed
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
