package com.astralspace;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
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
        long nextAct; int step; long startTick;
        final List<net.minecraft.world.entity.Entity> summoned = new ArrayList<>();
        final List<int[]> pending = new ArrayList<>(); // supernova: far spots whose chunks are still being generated {x, z}
        ServerLevel pendingLevel;
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

    /** Damage with one of LOTM's own damage sources ("magic", "explosion", "lava", "holyFire", "crush" ...); vanilla magic if LOTM has none. */
    private static void hurt(ServerPlayer p, float amount, String kind) {
        if (unkillable(p)) amount = Math.min(amount, p.getHealth() - 1.0f);
        if (amount <= 0) return;
        DamageSource src = Lotm.source(kind, p);
        if (src == null) src = kind.equals("explosion") ? p.level().damageSources().explosion(null, null) : p.level().damageSources().magic();
        p.hurt(src, amount);
    }

    private static void util(String name, ServerPlayer p, Object... extra) {
        Object[] args = new Object[extra.length + 1];
        args[0] = p;
        System.arraycopy(extra, 0, args, 1, extra.length);
        Lotm.util(name, args);
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

    private static final double SUPERNOVA_MIN = 500;

    /**
     * Picks a random spot inside the world border at least 500 blocks away (less only if the border is tiny) and starts
     * loading its chunk in the BACKGROUND (a forced chunk loads and generates without blocking the server). The player is
     * only teleported once the chunk is ready, so the server never freezes waiting for world generation.
     */
    private static void requestTarget(ServerPlayer p, Curse c) {
        ServerLevel level = p.serverLevel();
        var border = level.getWorldBorder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double minX = border.getMinX() + 2, maxX = border.getMaxX() - 2, minZ = border.getMinZ() + 2, maxZ = border.getMaxZ() - 2;
        double need = Math.min(SUPERNOVA_MIN, Math.min(maxX - minX, maxZ - minZ) * 0.4);
        for (int attempt = 0; attempt < 40; attempt++) {
            int x = (int) (minX + r.nextDouble() * (maxX - minX));
            int z = (int) (minZ + r.nextDouble() * (maxZ - minZ));
            double dx = x - p.getX(), dz = z - p.getZ();
            if (dx * dx + dz * dz < need * need) continue;
            c.pendingLevel = level;
            level.setChunkForced(x >> 4, z >> 4, true);
            c.pending.add(new int[]{x, z});
            return;
        }
    }

    /** Teleports to the first requested spot whose chunk is ready. Returns false if none is ready yet. */
    private static boolean supernovaJump(ServerPlayer p, Curse c) {
        ServerLevel level = c.pendingLevel;
        if (level == null || level != p.serverLevel()) { releaseTargets(c); requestTarget(p, c); return false; }
        for (int[] t : c.pending) {
            if (level.getChunkSource().getChunkNow(t[0] >> 4, t[1] >> 4) == null) continue; // still generating
            c.pending.remove(t);
            int y = Surface.find(level, t[0], t[1], false);
            level.setChunkForced(t[0] >> 4, t[1] >> 4, false); // the player's own presence keeps it loaded from now on
            if (y >= 0) {
                double dx = t[0] - p.getX(), dz = t[1] - p.getZ();
                jump(p, new Vec3(t[0] + 0.5, y, t[1] + 0.5));
                OuterGods.log("[astral] supernova: {} teleported {} blocks to {}, {}", p.getGameProfile().getName(),
                        (int) Math.sqrt(dx * dx + dz * dz), t[0], t[1]);
                requestTarget(p, c);
                return true;
            }
            requestTarget(p, c); // no ground there: try another spot
            return false;
        }
        return false;
    }

    private static void releaseTargets(Curse c) {
        if (c.pendingLevel != null) for (int[] t : c.pending) c.pendingLevel.setChunkForced(t[0] >> 4, t[1] >> 4, false);
        c.pending.clear();
    }

    /** Opens a LOTM black hole and a spatial tear beside the player, drags the player around and burns them. Removed with the curse. */
    private static void collapse(ServerPlayer p, Curse c) {
        ServerLevel lv = p.serverLevel();
        Vec3 at = p.position();
        var hole = Lotm.spawn(lv, "black_hole_entity", at.x + 5, at.y + 3, at.z, null, 120, p, 1.0f);
        if (hole != null) c.summoned.add(hole);
        Vec3 from = new Vec3(at.x - 9, at.y + 1.5, at.z);
        var tear = Lotm.spawn(lv, "space_rift_entity", from.x, from.y, from.z, at.subtract(from).normalize().scale(0.6), 100, p, 1.0f);
        if (tear != null) c.summoned.add(tear);
        lv.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x + 5, at.y + 3, at.z, 80, 1.5, 1.5, 1.5, 0.3);
        lv.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 60, 2.0, 2.0, 2.0, 0.2);
        // the pull toward the hole, in case the entity does nothing on its own
        Vec3 pull = new Vec3(at.x + 5, at.y + 3, at.z).subtract(at).normalize().scale(0.7);
        p.setDeltaMovement(p.getDeltaMovement().add(pull));
        p.hurtMarked = true;
    }

    /** Vexes made far stronger than normal ones: lots of health, hard-hitting, fast, armed and burning. */
    private static void spawnVexes(ServerPlayer p, Curse c, int count) {
        ServerLevel level = p.serverLevel();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            net.minecraft.world.entity.monster.Vex v = EntityType.VEX.create(level);
            if (v == null) continue;
            double ang = r.nextDouble() * Math.PI * 2;
            v.moveTo(p.getX() + Math.cos(ang) * 2, p.getY() + 1, p.getZ() + Math.sin(ang) * 2, r.nextFloat() * 360f, 0f);
            try {
                v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(80);
                v.setHealth(80f);
                v.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(14 + 3 * c.stage);
                var fly = v.getAttribute(Attributes.FLYING_SPEED);
                if (fly != null) fly.setBaseValue(1.2);
                v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.9);
                v.getAttribute(Attributes.ARMOR).setBaseValue(12);
            } catch (Throwable ignored) { }
            v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
            v.setBoundOrigin(p.blockPosition());
            v.setTarget(p);
            v.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0, false, false));
            v.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 1, false, false));
            level.addFreshEntity(v);
            c.summoned.add(v);
        }
    }

    /** A white screen flash, half strength by default (so it never whites the screen out completely). */
    private static void flash(ServerPlayer p, int alpha, int ms) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new Net.FlashPayload(alpha, ms));
    }

    private static void burst(ServerPlayer p, Kind kind) {
        p.serverLevel().sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1, p.getZ(), 40, 1.2, 1.0, 1.2, 0.05);
        p.serverLevel().sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 40, 1.2, 1.0, 1.2, 0.15);
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
        int s = Math.max(1, Math.min(4, stage)); // stage 4 (and the end) is stronger again: 12 seconds, intensity x2.5
        int seconds = Math.min(12, 4 + 2 * s); // 6 / 8 / 10 seconds
        if (kind == Kind.CIRCLE) seconds = Math.min(24, seconds * 2); // the Circle lasts twice as long
        if (kind == Kind.SUPERNOVA) seconds = Math.min(16, seconds + 4); // time for the far chunks to be ready
        long now = server.getTickCount();
        Curse c = new Curse(p.getUUID(), kind, s, now + seconds * 20L, onEnd);
        c.nextAct = now;
        c.startTick = now;
        active.add(c);
        begin(p, c, seconds * 20);
    }

    /** One-time start of each curse: the lasting potion effects. */
    private static void begin(ServerPlayer p, Curse c, int ticks) {
        int a = c.stage >= 3 ? 1 : 0;
        switch (c.kind) {
            case SUPERNOVA -> {
                for (int i = 0; i < 2; i++) requestTarget(p, c); // far chunks start generating now, in the background
                fx(p, MobEffects.GLOWING, ticks, 0);
                collapse(p, c); // a black hole and a spatial tear open right away, whether or not a far spot is ready yet
            }
            case DEPRAVITY -> {
                fx(p, MobEffects.WITHER, ticks, 2 + a);
                fx(p, MobEffects.WEAKNESS, ticks, 3);
                fx(p, MobEffects.CONFUSION, ticks, 0);
                fx(p, MobEffects.HUNGER, ticks, 4);
                fx(p, lotm("mentalplague", MobEffects.POISON), ticks, 0);
                util("applyFrenzy", p, ticks); util("applyMentalPlague", p, ticks); // Demoness: depravity eats the mind
                spawn(p, c, EntityType.ENDERMITE, 4 + 2 * c.stage);
                spawn(p, c, EntityType.SILVERFISH, 4 + 2 * c.stage);
            }
            case DESIRE -> {
                fx(p, lotm("paralysis", MobEffects.MOVEMENT_SLOWDOWN), ticks, 0);
                fx(p, MobEffects.MOVEMENT_SLOWDOWN, ticks, 6);
                fx(p, MobEffects.WITHER, ticks, 1 + a);
                fx(p, MobEffects.HUNGER, ticks, 4);
                fx(p, MobEffects.POISON, ticks, 1);
                fx(p, MobEffects.WEAKNESS, ticks, 2);
                fx(p, MobEffects.CONFUSION, ticks, 0);
                fx(p, MobEffects.DIG_SLOWDOWN, ticks, 3);
                fx(p, lotm("mentalplague", MobEffects.POISON), ticks, 0);
                util("applyMentalPlague", p, ticks);
                util("applyParalysis", p, ticks); // Mother: roots hold the body
            }
            case DEVILS -> {
                fx(p, MobEffects.BLINDNESS, ticks, 0);
                fx(p, MobEffects.WITHER, ticks, 1 + a);
                fx(p, lotm("frenzy", MobEffects.WEAKNESS), ticks, 0);
                p.setRemainingFireTicks(ticks);
                util("applyBleeding", p, ticks, 1); // Abyss: devils tear at you
                spawnVexes(p, c, 4 + 2 * c.stage);
            }
            case BLATHERER -> {
                fx(p, MobEffects.CONFUSION, ticks, 2);
                fx(p, MobEffects.BLINDNESS, ticks, 0);
                fx(p, lotm("mentalplague", MobEffects.POISON), ticks, 0);
                fx(p, lotm("deafness", MobEffects.DARKNESS), ticks, 0);
                fx(p, lotm("screen_shake", MobEffects.CONFUSION), ticks, 0);
            }
            case CHAOS -> {
                fx(p, lotm("screen_shake", MobEffects.CONFUSION), ticks, 0);
                Lotm.addMisfortune(p, 150.0 * c.stage); // Wheel of Fortune: LOTM's own misfortune calamities
            }
            case MIST -> {
                util("applyAwe", p, 60);
                fx(p, MobEffects.DARKNESS, ticks, 0); // no blindness, so you can watch it happen
                fx(p, MobEffects.MOVEMENT_SLOWDOWN, ticks, 2 + a);
            }
            case BRIDGE -> {
                fx(p, MobEffects.MOVEMENT_SLOWDOWN, ticks, 2);
                fx(p, MobEffects.WEAKNESS, ticks, 2);
                fx(p, MobEffects.WITHER, ticks, 1 + a);
                fx(p, MobEffects.CONFUSION, ticks, 0);
                fx(p, MobEffects.HUNGER, ticks, 4);
                fx(p, lotm("mentalplague", MobEffects.POISON), ticks, 0);
                fx(p, lotm("bleeding", MobEffects.POISON), ticks, 1);
                util("applyBleeding", p, ticks, 1);
                util("applyFrenzy", p, ticks);
                util("applyNoRegeneration", p, ticks);
            }
            case HUNGER -> {
                fx(p, MobEffects.HUNGER, ticks, 9);
                fx(p, MobEffects.WITHER, ticks, 1 + a);
                p.getFoodData().setFoodLevel(0);
                p.getFoodData().setSaturation(0f);
                util("applyNoRegeneration", p, ticks);
            }
            case CIRCLE -> {
                util("applyNoRegeneration", p, ticks);
                fx(p, MobEffects.MOVEMENT_SLOWDOWN, ticks, 3);
                fx(p, lotm("hobbled", MobEffects.MOVEMENT_SLOWDOWN), ticks, 0);
            }
            case RAVINGS -> {
                util("applyFrenzy", p, ticks); // the ravings drive you mad
                Lotm.loseSanityPercent(p, Lotm.sequence(p) == 0 ? 20 : 50);   // a Seq 0 keeps more of their mind
                fx(p, MobEffects.CONFUSION, ticks, 1);
                fx(p, MobEffects.DIG_SLOWDOWN, ticks, 2);
                fx(p, lotm("frenzy", MobEffects.WEAKNESS), ticks, 0);
            }
            case DECAY -> {
                fx(p, MobEffects.WITHER, ticks, 1);
                fx(p, MobEffects.WEAKNESS, ticks, 1);
                fx(p, lotm("bleeding", MobEffects.POISON), ticks, 0);
                fx(p, lotm("handsofdeath", MobEffects.WITHER), 60, 0); // Death: the hands of death, only for 3 seconds
            }
            case OVERSEER -> {
                int lev = (3 + c.stage) * 20; // 4 / 5 / 6 seconds of rising, roughly 40-60 blocks
                fx(p, MobEffects.LEVITATION, lev, 9);
                if (Lotm.sequence(p) == 0) fx(p, MobEffects.SLOW_FALLING, lev + 600, 0);   // the fall cannot finish a Seq 0 (a Seq 1 and below can still die to it)
                fx(p, MobEffects.GLOWING, ticks, 0);
                fx(p, MobEffects.BLINDNESS, ticks, 0);
                fx(p, lotm("awe", MobEffects.WEAKNESS), ticks, 0);
            }
            case FATE -> {
                fx(p, MobEffects.UNLUCK, ticks, 9);
                Lotm.addMisfortune(p, 200.0 * c.stage); // LOTM's own misfortune (what Misfortune Bestowal gives)
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
                if (supernovaJump(p, c)) {
                    burst(p, c.kind);
                    hurt(p, 8f * k, "explosion");
                    p.setRemainingFireTicks(100);
                    util("applyStun", p, 10);
                    collapse(p, c);
                    c.nextAct = now + 20;
                } else {
                    p.setRemainingFireTicks(40);
                    hurt(p, 2f * k, "explosion"); // still burning while the next far spot is being made
                    if (c.step++ % 4 == 0)
                        OuterGods.log("[astral] supernova: waiting for {} far chunk(s) to be ready for {}", c.pending.size(), p.getGameProfile().getName());
                    c.nextAct = now + 10;
                }
            }
            case DEPRAVITY -> {
                Lotm.whisper(p);
                hurt(p, 3f * k, "magic");
                c.nextAct = now + 20;
            }
            case DESIRE -> {
                p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
                p.hurtMarked = true;
                hurt(p, 3f * k, "crush");
                util("applyBleeding", p, 60, 2); // thorns cut
                util("applyNoRegeneration", p, 40);
                p.serverLevel().sendParticles(ParticleTypes.COMPOSTER, p.getX(), p.getY() + 0.5, p.getZ(), 12, 0.4, 0.6, 0.4, 0.0);
                c.nextAct = now + 20;
            }
            case DEVILS -> {
                p.setRemainingFireTicks(80);
                hurt(p, 3f * k, "holyFire");
                for (net.minecraft.world.entity.Entity m : c.summoned) if (m instanceof Mob mob && mob.isAlive()) mob.setTarget(p);
                c.nextAct = now + 20;
            }
            case BLATHERER -> {
                OuterGods.playAt(p, r.nextBoolean() ? "entity.ghast.scream" : "entity.elder_guardian.curse",
                        p.getX() + r.nextInt(7) - 3, p.getY(), p.getZ() + r.nextInt(7) - 3, 1.6f, 0.5f);
                Lotm.whisper(p); // the babble: LOTM's own corrupted whispers
                // 95% of max sanity in total, spread evenly over the curse (one hit every half second)
                Lotm.loseSanityPercent(p, 95.0 / Math.max(1.0, (c.end - c.startTick) / 10.0));
                hurt(p, 6f * k, "magic");
                c.nextAct = now + 10;
            }
            case CHAOS -> {
                fx(p, randomChaosEffect(r), 60, 1 + (c.stage >= 3 ? 1 : 0));
                switch (r.nextInt(5)) {
                    case 0 -> util("applyStun", p, 20);
                    case 1 -> util("applyParalysis", p, 20);
                    case 2 -> util("applyFrenzy", p, 60);
                    case 3 -> util("applyBleeding", p, 60, 1);
                    default -> util("applyMentalPlague", p, 60);
                }
                p.setDeltaMovement((r.nextDouble() - 0.5) * 2.2, 0.3 + r.nextDouble() * 0.5, (r.nextDouble() - 0.5) * 2.2);
                p.hurtMarked = true;
                hurt(p, 5f * k, r.nextBoolean() ? "lightning" : "generic"); // raw damage
                if (c.step++ % 2 == 0) { // once a second: shuffle the whole inventory
                    Inventory inv = p.getInventory();
                    Collections.shuffle(inv.items, r);
                    p.containerMenu.broadcastChanges();
                    p.inventoryMenu.broadcastChanges();
                }
                c.nextAct = now + 10;
            }
            case MIST -> {
                Vec3 to = nearbySpot(p, 20, 70);
                if (to != null) jump(p, to);
                util("applyAwe", p, 20);
                hurt(p, 5f * k, "magic");
                p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 30, 0.6, 0.9, 0.6, 0.05);
                c.nextAct = now + 8; // teleports about 2.5 times a second
            }
            case BRIDGE -> {
                boolean light = (c.step++ % 2) == 0;
                p.removeEffect(MobEffects.BLINDNESS);
                p.removeEffect(MobEffects.GLOWING);
                if (light) fx(p, MobEffects.GLOWING, 40, 0);
                else fx(p, MobEffects.BLINDNESS, 40, 0);
                hurt(p, 6f * k, light ? "holyFire" : "magic"); // light burns with holy fire, dark with plain magic
                if (light) p.setRemainingFireTicks(60);
                else util("applyStun", p, 8);
                p.serverLevel().sendParticles(light ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1, p.getZ(), 20, 0.5, 0.8, 0.5, 0.05);
                c.nextAct = now + 10;
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
                        hurt(p, 4f * k, true);
                        OuterGods.playAt(p, "block.portal.travel", p.getX(), p.getY(), p.getZ(), 0.8f, 0.5f);
                    }
                }
                c.nextAct = now + 20;
            }
            case RAVINGS -> {
                boolean seq0 = Lotm.sequence(p) == 0;   // Seq 0 survives the ravings; Seq 1 and below can be killed by them
                p.setRemainingFireTicks(80); // water cannot put this out: it is lit again every second
                if (seq0) hurt(p, 4f * k, "lava");   // never kills
                else {
                    DamageSource rsrc = Lotm.source("lava", p);
                    p.hurt(rsrc != null ? rsrc : p.level().damageSources().lava(), 4f * k);   // can kill
                }
                // lots of random corruption, each time into a different random pathway (a third of it for a Seq 0)
                Lotm.addCorruptionPercent(p, Lotm.randomPathway(), seq0 ? 1 + r.nextInt(3) * k / 2 : 4 + r.nextInt(7) * k);
                if (r.nextInt(3) == 0) Lotm.whisper(p);
                c.nextAct = now + 10;
            }
            case DECAY -> {
                wearDown(p, 0.12);
                util("applyBleeding", p, 40, 1);
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
                boolean seq0 = Lotm.sequence(p) == 0;   // Seq 0 survives the Overseer; Seq 1 and below can be killed by it
                double sanity = Lotm.sanityPercent(p);
                if (!seq0 || sanity < 0 || sanity > 15) Lotm.loseSanityPercent(p, 12); // seeing too much at once (a Seq 0's mind is never emptied)
                if (seq0) hurt(p, 5f * k, "magic");   // never kills
                else {
                    DamageSource osrc = Lotm.source("magic", p);
                    p.hurt(osrc != null ? osrc : p.level().damageSources().magic(), 5f * k);   // can kill
                }
                util("applyAwe", p, 30);
                Lotm.whisper(p);
                c.nextAct = now + 10;
            }
            case FATE -> {
                boolean seq0 = Lotm.sequence(p) == 0;   // Seq 0 survives fate; Seq 1 and below can be killed by it
                if (seq0) {
                    hurt(p, 8f * k, true);   // never kills
                    if (p.getHealth() > 6f) {   // the stacking calamities and bleeding stop while a Seq 0 is close to death
                        Lotm.addMisfortune(p, 20.0 * c.stage);
                        util("applyBleeding", p, 40, 1);
                    }
                    var ce = randomChaosEffect(r);
                    if (ce != MobEffects.WITHER && ce != MobEffects.POISON && !ce.is(ResourceLocation.fromNamespaceAndPath("lotm", "bleeding"))) fx(p, ce, 60, 1);
                } else {
                    p.hurt(p.level().damageSources().magic(), 8f * k);   // can kill
                    Lotm.addMisfortune(p, 60.0 * c.stage); // the calamities keep stacking
                    fx(p, randomChaosEffect(r), 60, 1);
                    util("applyBleeding", p, 40, 1);
                }
                c.nextAct = now + 10;
            }
            default -> c.nextAct = now + 40;
        }
    }

    /** Never leave forced chunks behind when the server stops mid-curse. */
    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (Curse c : active) releaseTargets(c);
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
                finish(c, p);
                cleanup(c);
                it.remove();
                if (c.onEnd != null) c.onEnd.run();
                continue;
            }
            if (now >= c.nextAct) act(server, p, c, now);
        }
    }

    /** What a curse leaves behind on the player is taken off again (the hands of death go away). */
    private static void finish(Curse c, ServerPlayer p) {
        if (c.kind == Kind.DECAY) {
            BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.fromNamespaceAndPath("lotm", "handsofdeath")).ifPresent(h -> p.removeEffect(h));
            var tag = p.getPersistentData();
            tag.remove("handsOfDeathDuration"); tag.remove("handsOfDeathCeiling"); tag.remove("handsOfDeathCaster");
        }
    }

    private static void cleanup(Curse c) {
        releaseTargets(c);
        for (net.minecraft.world.entity.Entity m : c.summoned) if (m.isAlive()) m.discard();
        c.summoned.clear();
    }

    // ------------------------------------------------------------------ test command (ops)

    private static int test(CommandSourceStack src, String god, int stage) {
        ServerPlayer p;
        try { p = src.getPlayerOrException(); } catch (Exception e) { src.sendFailure(Component.literal("Run this as a player.")); return 0; }
        if (god.equalsIgnoreCase("stop")) {
            active.removeIf(c -> { if (c.id.equals(p.getUUID())) { cleanup(c); return true; } return false; });
            p.removeAllEffects();
            src.sendSuccess(() -> Component.literal("Your curse was stopped."), false);
            return 1;
        }
        Kind kind;
        try { kind = Kind.valueOf(god.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) { src.sendFailure(Component.literal("Unknown curse. Use tab to see the list.")); return 0; }
        start(src.getServer(), p, kind, stage, null); // no corruption death afterwards: this is only a test
        src.sendSuccess(() -> Component.literal("Curse " + kind.name().toLowerCase(java.util.Locale.ROOT) + " (stage " + stage
                + ") on you. Seq 1 and 0 cannot die from it, anyone weaker can. /astralcurse stop ends it."), false);
        return 1;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        List<String> names = new ArrayList<>();
        for (Kind k : Kind.values()) names.add(k.name().toLowerCase(java.util.Locale.ROOT));
        names.add("stop");
        event.getDispatcher().register(Commands.literal("astralcurse")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("god", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(names, b))
                        .executes(ctx -> test(ctx.getSource(), StringArgumentType.getString(ctx, "god"), 2))
                        .then(Commands.argument("stage", IntegerArgumentType.integer(1, 4))
                                .executes(ctx -> test(ctx.getSource(), StringArgumentType.getString(ctx, "god"),
                                        IntegerArgumentType.getInteger(ctx, "stage"))))));
    }
}
