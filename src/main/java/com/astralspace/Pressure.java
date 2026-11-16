package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What Crush Depth does to a Beyonder, and how hard the bends bite.
 *
 * Crush Depth has no hooks for either, and this mod has no mixins, so both are done from the outside:
 *
 *  - Pressure damage arrives as the {@code crush_depth:pressure} damage type, which an ordinary damage listener can
 *    turn down or refuse. A Sequence 1 or 0 is simply not crushed by water; the Sailor pathway, whose whole business
 *    is the sea, takes a fraction of it; everyone else is unchanged.
 *
 *  - The bends are three effects - nausea, weakness and slowness - applied together in one tick with the same
 *    duration, up to ten minutes of it. They are caught as they land and cut down: a short first dose that grows if
 *    you keep surfacing too fast, and a ceiling far below ten minutes. A Sequence 1 or 0 does not get them at all.
 *
 * And because the first anyone knows about pressure is usually dying of it, a warning is given on the way down.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class Pressure {
    private Pressure() {}

    private static final String PRESSURE = "crush_depth:pressure";

    /** Per player: how many times the bends have bitten recently, and when the last one was. */
    private record Bite(int count, long at) { }
    private static final Map<UUID, Bite> BITES = new HashMap<>();
    /** The three effects of one dose, caught in the tick they land so they can be shortened together. */
    private static final Map<UUID, long[]> LANDING = new HashMap<>();
    /** The depth each player was last warned about, so a warning is given once on the way down and not every tick. */
    private static final Map<UUID, Integer> WARNED = new HashMap<>();

    /** How much of the pressure this player feels: 0 for the untouchable, less for a Sailor, 1 for everyone else. */
    private static float factor(ServerPlayer p) {
        int seq = Lotm.sequence(p);
        if (seq >= 0 && seq <= Settings.pressureImmuneSequence) return 0f;
        String path = Lotm.pathwayId(p);
        if (path != null && path.equals("sailor")) return Math.max(0f, Settings.pressureSailorPercent / 100f);
        return 1f;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (!e.getSource().typeHolder().getRegisteredName().equals(PRESSURE)) return;
        float f = factor(p);
        if (f <= 0f) {
            e.setCanceled(true);
            if (p.tickCount % 60 == 0) p.displayClientMessage(Component.literal("The weight of the water means nothing to you.").withStyle(ChatFormatting.AQUA), true);
            return;
        }
        if (f < 1f) e.setAmount(e.getAmount() * f);
    }

    /**
     * The bends, caught as they land. Crush Depth gives all three at once with the same duration, so the first of
     * them is recorded and the lot are shortened a tick later, once they are all on.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffect(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added e) {
        if (!Settings.bendsTrim || !(e.getEntity() instanceof ServerPlayer p)) return;
        var eff = e.getEffectInstance();
        if (!eff.getEffect().equals(MobEffects.CONFUSION) || eff.getAmplifier() != 0) return;
        int dur = eff.getDuration();
        if (dur < 20 * 20 || dur > 20 * 601) return;                 // not the shape of a bends dose
        if (!p.isInWater() && !p.isUnderWater()) return;             // and not where one happens

        long[] mark = {p.getServer().getTickCount(), dur};
        LANDING.put(p.getUUID(), mark);
        UniqAbilities.later(1, () -> trim(p, dur));
    }

    /** Cuts one dose down to size, or removes it outright for someone the sea cannot touch. */
    private static void trim(ServerPlayer p, int was) {
        long[] mark = LANDING.remove(p.getUUID());
        if (mark == null || p.getServer() == null) return;

        if (factor(p) <= 0f) {
            p.removeEffect(MobEffects.CONFUSION); p.removeEffect(MobEffects.WEAKNESS); p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            p.displayClientMessage(Component.literal("Your blood does not care how fast you rose.").withStyle(ChatFormatting.AQUA), true);
            return;
        }

        long now = System.currentTimeMillis();
        Bite b = BITES.get(p.getUUID());
        int count = b != null && now - b.at() < Settings.bendsForgetMinutes * 60_000L ? b.count() + 1 : 1;
        BITES.put(p.getUUID(), new Bite(count, now));

        // a short first dose, growing with each one you take before the last has been forgotten, up to the ceiling
        int secs = Math.min(Settings.bendsMaxSeconds, Settings.bendsFirstSeconds * count);
        if (factor(p) < 1f) secs = Math.max(5, Math.round(secs * factor(p)));
        int ticks = secs * 20;
        if (ticks >= was) return;                                     // their own dose was already shorter

        for (var holder : new Object[]{MobEffects.CONFUSION, MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN}) {
            @SuppressWarnings("unchecked") var h = (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>) holder;
            var cur = p.getEffect(h);
            if (cur == null || cur.getDuration() <= ticks) continue;
            p.removeEffect(h);
            p.addEffect(new MobEffectInstance(h, ticks, cur.getAmplifier(), false, true, true));
        }
        p.displayClientMessage(Component.literal("The bends: " + secs + "s" + (count > 1 ? "  (x" + count + ", you keep doing this)" : "")).withStyle(ChatFormatting.RED), true);
    }

    /**
     * A word before it hurts. Crush Depth starts crushing past a rated depth and the first sign of it is damage, so
     * a line is given on the way down at the depth where it is about to begin and again once it has.
     */
    @SubscribeEvent
    public static void onTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        if (!Settings.pressureWarnings || e.getServer().getTickCount() % 20 != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            if (!p.isInWater()) { WARNED.remove(p.getUUID()); continue; }
            int depth = (int) (p.level().getSeaLevel() - p.getY());
            if (depth < Settings.pressureWarnDepth) { if (depth < Settings.pressureWarnDepth - 12) WARNED.remove(p.getUUID()); continue; }
            if (factor(p) <= 0f) continue;
            int step = depth / 16;
            Integer told = WARNED.get(p.getUUID());
            if (told != null && told >= step) continue;
            WARNED.put(p.getUUID(), step);
            p.displayClientMessage(Component.literal(depth + " blocks down. The water is starting to press.").withStyle(ChatFormatting.DARK_AQUA), true);
            p.playNotifySound(net.minecraft.sounds.SoundEvents.AMBIENT_UNDERWATER_ENTER, net.minecraft.sounds.SoundSource.AMBIENT, 0.5f, 0.6f);
        }
    }

    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        LANDING.remove(e.getEntity().getUUID());
        WARNED.remove(e.getEntity().getUUID());
    }
}
