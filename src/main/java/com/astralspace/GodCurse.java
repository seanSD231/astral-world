package com.astralspace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What an Outer God does to someone who names it (the speaker, and from stage 2 everyone nearby) BEFORE the
 * corruption finishes the speaker. One short, brutal curse per god. Length 6 / 8 / 10 seconds in stages 1 / 2 / 3
 * (never more than 12); intensity grows a little with the stage. Uses LOTM's own effects (stun, paralysis, frenzy ...)
 * where they fit; they are looked up by id, so this compiles and runs without LOTM.
 * Seq 1 and Seq 0 can never be killed by a curse (damage leaves them at half a heart); everyone else can die.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class GodCurse {
    public enum Kind {
        SUPERNOVA, DEPRAVITY, DESIRE, DEVILS, BLATHERER, CHAOS, MIST, BRIDGE, HUNGER, CIRCLE, RAVINGS, DECAY, OVERSEER, FATE, UNKNOWN
    }

    private static final class Curse {
        final UUID id; final Kind kind; final int stage; final long end; final Runnable onEnd;
        long nextAct; int step;
        final List<Mob> summoned = new ArrayList<>();
        final ArrayDeque<Vec3> history = new ArrayDeque<>();
        Curse(UUID id, Kind kind, int stage, long end, Runnable onEnd) {
            this.id = id; this.kind = kind; this.stage = stage; this.end = end; this.onEnd = onEnd;
        }
    }

    private static final List<Curse> active = new ArrayList<>();

    private GodCurse() {}

    // ------------------------------------------------------------------ helpers

    private static Holder<MobEffect> lotm(String id, Holder<MobEffect> fallback) {
        return BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.fromNamespaceAndPath("lotm", id)).<Holder<MobEffect>>map(h -> h).orElse(fallback);
    }

    private static void fx(ServerPlayer p, Holder<MobEffect> e, int ticks, int amp) {
        p.addEffect(new MobEffectInstance(e, Math.max(5, ticks), Math.max(0, amp), false, true));
    }

    /** Seq 1 and Seq 0 are never killed by a curse. */
    private static boolean unkillable(ServerPlayer p) {
        int seq = Lotm.sequence(p);
        return seq >= 0 && seq <= 1;
    }

    /** Damage that cannot kill an unkillable player. Armor-ignoring when `magic` is true. */
    private static void hurt(ServerPlayer p, float amount, boolean magic) {
        if (unkillable(p)) amount = Math.min(amount, p.getHealth() - 1.0f);
        if (amount <= 0) return;
        DamageSource src = magic ? p.level().damageSources().magic() : p.level().damageSources().explosion(null, null);
        p.hurt(src, amount);
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
    }

    /** A random nearby standable spot at distance min..max (same height +-), or null. */
    private static Vec3 nearbySpot(ServerPlayer p, double min, double max) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        ServerLevel level = p.serverLevel();
        for (int attempt = 0; attempt < 8; attempt++) {
            double ang = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * (max - min);
            int x = (int) Math.floor(p.getX() + Math.cos(ang) * d), z = (int) Math.floor(p.getZ() + Math.sin(ang) * d);
            level.getChunk(x >> 4, z >> 4);
            for (int dy = 8; dy >= -12; dy--) {
                BlockPos feet = new BlockPos(x, (int) p.getY() + dy, z);
                if (standable(level, feet)) return new Vec3(x + 0.5, feet.getY(), z + 0.5);
            }
        }
        return null;
    }

    private static void jump(ServerPlayer p, Vec3 to) {
        p.teleportTo(p.serverLevel(), to.x, to.y, to.z, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
    }

    /** Anywhere inside the world border, on the ground of the overworld band. */
    private static void supernovaJump(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        var border = level.getWorldBorder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 6; attempt++) {
            int x = (int) (border.getMinX() + 1 + r.nextDouble() * (border.getMaxX() - border.getMinX() - 2));
            int z = (int) (border.getMinZ() + 1 + r.nextDouble() * (border.getMaxZ() - border.getMinZ() - 2));
            int y = Surface.find(level, x, z, true);
            if (y < 0) continue;
            jump(p, new Vec3(x + 0.5, y, z + 0.5));
            return;
        }
    }

    private static void burst(ServerPlayer p, Kind kind) {
        p.serverLevel().sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
        OuterGods.playAt(p, "entity.generic.explode", p.getX(), p.getY(), p.getZ(), 1.5f, 0.7f);
    }

    private static void spawn(ServerPlayer p, Curse c, EntityType<? extends Mob> type, int count) {
        ServerLevel level = p.serverLevel();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Mob m = type.create(level);
            if (m == null) continue;
            double ang = r.nextDouble() * Math.PI * 2;
            m.moveTo(p.getX() + Math.cos(ang) * 1.5, p.getY(), p.getZ() + Math.sin(ang) * 1.5, r.nextFloat() * 360f, 0f);
            m.setTarget(p);
            level.addFreshEntity(m);
            c.summoned.add(m);
        }
    }

    /** Durability of everything the player carries is pushed down toward 10% remaining, never to breaking. */
    private static void wearDown(ServerPlayer p, double fraction) {
        Inventory inv = p.getInventory();
        List<ItemStack> all = new ArrayList<>(inv.items);
        all.addAll(inv.armor);
        all.addAll(inv.offhand);
        for (ItemStack s : all) {
            if (s.isEmpty() || !s.isDamageableItem()) continue;
            int max = s.getMaxDamage();
            int floor = (int) Math.floor(max * 0.90);          // 10% left
            int next = s.getDamageValue() + (int) Math.ceil(max * fraction);
            if (s.getDamageValue() < floor) s.setDamageValue(Math.min(floor, next));
        }
        p.containerMenu.broadcastChanges();
    }

    // ------------------------------------------------------------------ starting a curse

    /** stage 1-3 (and later stages count as 3). onEnd runs when the curse is over (the speaker's corruption death). */
    public static void start(MinecraftServer server, ServerPlayer p, Kind kind, int stage, Runnable onEnd) {
        int s = Math.max(1, Math.min(3, stage));
        int seconds = Math.min(12, 4 + 2 * s); // 6 / 8 / 10 seconds
        long now = server.getTickCount();
        Curse c = new Curse(p.getUUID(), kind, s, now + seconds * 20L, onEnd);
        c.nextAct = now;
        active.add(c);
        begin(p, c, seconds * 20);
    }

    /** One-time start of each curse: the lasting potion effects. */
    private static void begin(ServerPlayer p, Curse c, int ticks) {
        int a = c.stage >= 3 ? 1 : 0;
        switch (c.kind) {
            case SUPERNOVA -> fx(p, lotm("flash", MobEffects.BLINDNESS), 40, 0);
            case DEPRAVITY -> {
                fx(p, MobEffects.WITHER, ticks, 1 + a);
                fx(p, MobEffects.WEAKNESS, ticks, 2);
                fx(p, MobEffects.CONFUSION, ticks, 0);
                fx(p, MobEffects.HUNGER, ticks, 4);
                fx(p, lotm("mentalplague", MobEffects.POISON), ticks, 0);
                spawn(p, c, EntityType.ENDERMITE, 4 + 2 * c.stage);
                spawn(p, c, EntityType.SILVERFISH, 4 + 2 * c.stage);
            }
            case DESIRE -> {
                fx(p, lotm("paralysis", MobEffects.MOVEMENT_SLOWDOWN), ticks, 0);
                fx(p, MobEffects.MOVEMENT_SLOWDOWN, ticks, 6);
                fx(p, MobEffects.WITHER, ticks, 1 + a);
                fx(p, MobEffects.HUNGER, ticks, 4);
            }
            case DEVILS -> {
                fx(p, MobEffects.BLINDNESS, ticks, 0);
                fx(p, MobEffects.WITHER, ticks, 1 + a);
                fx(p, lotm("frenzy", MobEffects.WEAKNESS), ticks, 0);
                p.setRemainingFireTicks(ticks);
                spawn(p, c, EntityType.VEX, 3 + 2 * c.stage);
            }
            case BLATHERER -> {
                Lotm.loseSanityPercent(p, 50);
                fx(p, MobEffects.CONFUSION, ticks, 2);
                fx(p, MobEffects.BLINDNESS, ticks, 0);
                fx(p, lotm("mentalplague", MobEffects.POISON), ticks, 0);
                fx(p, lotm("deafness", MobEffects.DARKNESS), ticks, 0);
                fx(p, lotm("screen_shake", MobEffects.CONFUSION), ticks, 0);
            }
            case CHAOS -> fx(p, lotm("screen_shake", MobEffects.CONFUSION), ticks, 0);
            case MIST -> {
                fx(p, MobEffects.BLINDNESS, ticks, 0);
                fx(p, MobEffects.DARKNESS, ticks, 0);
                fx(p, MobEffects.MOVEMENT_SLOWDOWN, ticks, 2 + a);
            }
            case BRIDGE -> { }
            case HUNGER -> {
                fx(p, MobEffects.HUNGER, ticks, 9);
                fx(p, MobEffects.WITHER, ticks, 1 + a);
                p.getFoodData().setFoodLevel(0);
                p.getFoodData().setSaturation(0f);
            }
            case CIRCLE -> {
                fx(p, MobEffects.MOVEMENT_SLOWDOWN, ticks, 3);
                fx(p, lotm("hobbled", MobEffects.MOVEMENT_SLOWDOWN), ticks, 0);
            }
            case RAVINGS -> {
                Lotm.loseSanityPercent(p, 50);
                fx(p, MobEffects.CONFUSION, ticks, 1);
                fx(p, MobEffects.DIG_SLOWDOWN, ticks, 2);
                fx(p, lotm("frenzy", MobEffects.WEAKNESS), ticks, 0);
            }
            case DECAY -> {
                fx(p, MobEffects.WITHER, ticks, 2 + a);
                fx(p, MobEffects.WEAKNESS, ticks, 1);
                fx(p, lotm("bleeding", MobEffects.POISON), ticks, 0);
                fx(p, lotm("armor_weakness", MobEffects.WEAKNESS), ticks, 0);
            }
            case OVERSEER -> {
                int lev = (3 + c.stage) * 20; // 4 / 5 / 6 seconds of rising, roughly 40-60 blocks
                fx(p, MobEffects.LEVITATION, lev, 9);
                fx(p, MobEffects.GLOWING, ticks, 0);
                fx(p, MobEffects.BLINDNESS, ticks, 0);
                fx(p, lotm("awe", MobEffects.WEAKNESS), ticks, 0);
            }
            case FATE -> {
                fx(p, MobEffects.UNLUCK, ticks, 9);
                fx(p, MobEffects.WEAKNESS, ticks, 1);
                fx(p, lotm("noregeneration", MobEffects.WITHER), ticks, 0);
                fx(p, lotm("beneficialeffectsblocker", MobEffects.WEAKNESS), ticks, 0);
            }
            default -> { // a name that is not one of the known gods
                fx(p, MobEffects.DARKNESS, ticks, 0);
                fx(p, MobEffects.MOVEMENT_SLOWDOWN, ticks, 2);
            }
        }
    }

    // ------------------------------------------------------------------ the recurring part

    private static Holder<MobEffect> randomChaosEffect(ThreadLocalRandom r) {
        @SuppressWarnings("unchecked")
        Holder<MobEffect>[] pool = (Holder<MobEffect>[]) new Holder[]{
                MobEffects.POISON, MobEffects.WITHER, MobEffects.BLINDNESS, MobEffects.CONFUSION, MobEffects.WEAKNESS,
                MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN, MobEffects.HUNGER, MobEffects.DARKNESS, MobEffects.LEVITATION,
                lotm("stun", MobEffects.BLINDNESS), lotm("frenzy", MobEffects.WEAKNESS), lotm("paralysis", MobEffects.MOVEMENT_SLOWDOWN),
                lotm("bleeding", MobEffects.POISON), lotm("mentalplague", MobEffects.POISON), lotm("hobbled", MobEffects.MOVEMENT_SLOWDOWN),
                lotm("tumble", MobEffects.CONFUSION)
        };
        return pool[r.nextInt(pool.length)];
    }

    private static void act(MinecraftServer server, ServerPlayer p, Curse c, long now) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float k = 1.0f + 0.5f * (c.stage - 1); // 1.0 / 1.5 / 2.0
        switch (c.kind) {
            case SUPERNOVA -> {
                supernovaJump(p);
                burst(p, c.kind);
                hurt(p, 6f * k, false);
                p.setRemainingFireTicks(100);
                c.nextAct = now + 20;
            }
            case DEPRAVITY -> c.nextAct = now + 40;
            case DESIRE -> {
                p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
                p.hurtMarked = true;
                hurt(p, 2f * k, true);
                p.serverLevel().sendParticles(ParticleTypes.COMPOSTER, p.getX(), p.getY() + 0.5, p.getZ(), 12, 0.4, 0.6, 0.4, 0.0);
                c.nextAct = now + 20;
            }
            case DEVILS -> {
                p.setRemainingFireTicks(60);
                for (Mob m : c.summoned) if (m.isAlive()) m.setTarget(p);
                c.nextAct = now + 20;
            }
            case BLATHERER -> {
                OuterGods.playAt(p, r.nextBoolean() ? "entity.ghast.scream" : "entity.elder_guardian.curse",
                        p.getX() + r.nextInt(7) - 3, p.getY(), p.getZ() + r.nextInt(7) - 3, 1.6f, 0.5f);
                hurt(p, 1.5f * k, true);
                c.nextAct = now + 20;
            }
            case CHAOS -> {
                fx(p, randomChaosEffect(r), 60, 1 + (c.stage >= 3 ? 1 : 0));
                p.setDeltaMovement((r.nextDouble() - 0.5) * 2.2, 0.3 + r.nextDouble() * 0.5, (r.nextDouble() - 0.5) * 2.2);
                p.hurtMarked = true;
                if (c.step++ % 2 == 0) { // once a second: shuffle the whole inventory
                    Inventory inv = p.getInventory();
                    Collections.shuffle(inv.items, r);
                    p.containerMenu.broadcastChanges();
                    p.inventoryMenu.broadcastChanges();
                }
                c.nextAct = now + 10;
            }
            case MIST -> {
                Vec3 to = nearbySpot(p, 20, 60);
                if (to != null) jump(p, to);
                hurt(p, 1.5f * k, true);
                c.nextAct = now + 20;
            }
            case BRIDGE -> {
                boolean light = (c.step++ % 2) == 0;
                p.removeEffect(MobEffects.BLINDNESS);
                p.removeEffect(MobEffects.GLOWING);
                fx(p, light ? lotm("flash", MobEffects.GLOWING) : MobEffects.BLINDNESS, 40, 0);
                if (light) fx(p, MobEffects.GLOWING, 40, 0);
                hurt(p, 2f * k, true);
                p.serverLevel().sendParticles(light ? ParticleTypes.END_ROD : ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1, p.getZ(), 20, 0.5, 0.8, 0.5, 0.05);
                c.nextAct = now + 20;
            }
            case HUNGER -> {
                p.getFoodData().setFoodLevel(0);
                p.getFoodData().setSaturation(0f);
                wearDown(p, 0.25);
                c.nextAct = now + 20;
            }
            case CIRCLE -> {
                // snap back to where the player stood 3 seconds ago
                if (c.history.size() >= 4) {
                    Vec3 back = c.history.peekFirst();
                    if (back != null && back.distanceToSqr(p.position()) > 1.0) {
                        jump(p, back);
                        hurt(p, 2f * k, true);
                        OuterGods.playAt(p, "block.portal.travel", p.getX(), p.getY(), p.getZ(), 0.8f, 0.5f);
                    }
                }
                c.nextAct = now + 20;
            }
            case RAVINGS -> {
                p.setRemainingFireTicks(80); // water cannot put this out: it is lit again every second
                hurt(p, 1.5f * k, false);
                c.nextAct = now + 10;
            }
            case DECAY -> {
                wearDown(p, 0.25);
                Inventory inv = p.getInventory();
                for (int i = 0; i < inv.items.size(); i++) {
                    ItemStack s = inv.items.get(i);
                    if (!s.isEmpty() && s.get(DataComponents.FOOD) != null && !s.is(Items.ROTTEN_FLESH))
                        inv.items.set(i, new ItemStack(Items.ROTTEN_FLESH, s.getCount()));
                }
                p.containerMenu.broadcastChanges();
                c.nextAct = now + 20;
            }
            case OVERSEER -> {
                p.fallDistance = Math.max(p.fallDistance, 0f);
                c.nextAct = now + 20;
            }
            case FATE -> {
                hurt(p, 4f * k, true);
                c.nextAct = now + 20;
            }
            default -> c.nextAct = now + 40;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (active.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % 5 != 0) return;
        Iterator<Curse> it = active.iterator();
        while (it.hasNext()) {
            Curse c = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(c.id);
            if (p == null) continue;               // logged out: the curse waits for them
            if (!p.isAlive()) { cleanup(c); it.remove(); continue; }
            if (c.kind == Kind.CIRCLE) { // remember where the player was, every quarter second, for 3 seconds
                c.history.addLast(p.position());
                while (c.history.size() > 13) c.history.pollFirst();
            }
            if (now >= c.end) {
                cleanup(c);
                it.remove();
                if (c.onEnd != null) c.onEnd.run();
                continue;
            }
            if (now >= c.nextAct) act(server, p, c, now);
        }
    }

    private static void cleanup(Curse c) {
        for (Mob m : c.summoned) if (m.isAlive()) m.discard();
        c.summoned.clear();
    }
}
