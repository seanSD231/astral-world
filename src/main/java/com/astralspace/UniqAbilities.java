package com.astralspace;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** What the G key does for each worn Uniqueness. LOTM is reached by reflection, like everywhere else. */
public final class UniqAbilities {
    private UniqAbilities() {}

    /** Death: the feathered serpent strikes everything nearby for 25, you and your allies included. */
    public static void serpent(ServerPlayer sp, ItemStack st) {
        ServerLevel lv = sp.serverLevel();
        List<LivingEntity> hit = lv.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(8), e -> e.isAlive());
        lv.playSound(null, sp.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2f, 1.4f);
        for (LivingEntity e : hit) {
            world(lv, new DoorNet.World(1, sp.getId(), e.getId(), sp.getX(), sp.getY(), sp.getZ(), e.getX(), e.getY(), e.getZ(), 18));
            later(8, () -> {
                if (!e.isAlive()) return;
                e.hurt(lv.damageSources().magic(), 25f);
                lv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, e.getX(), e.getY() + e.getBbHeight() * .5, e.getZ(), 14, .3, .4, .3, .05);
                lv.playSound(null, e.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1f, .6f);
            });
        }
        fx(sp, 1);
    }

    /** A Photon effect at a spot (0 twilight hit, 2 storm ball), played for this many ticks. */
    public static void fxPoint(ServerLevel lv, int idx, double x, double y, double z, float scale, int life) {
        world(lv, new DoorNet.World(5, idx, 0, x, y, z, scale, 0, 0, life));
    }

    /** A Photon effect carried along a path (1 twilight wave). */
    public static void fxPath(ServerLevel lv, int idx, Vec3 a, Vec3 b, int ticks) {
        world(lv, new DoorNet.World(6, idx, 0, a.x, a.y, a.z, b.x, b.y, b.z, ticks));
    }

    private static void world(ServerLevel lv, DoorNet.World w) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(lv, null, w.x(), w.y(), w.z(), 192.0, w);
    }

    private static final List<Holder<MobEffect>> GOOD = List.of(MobEffects.DAMAGE_BOOST, MobEffects.REGENERATION, MobEffects.MOVEMENT_SPEED,
        MobEffects.DAMAGE_RESISTANCE, MobEffects.ABSORPTION, MobEffects.LUCK, MobEffects.JUMP, MobEffects.FIRE_RESISTANCE);
    private static final List<Holder<MobEffect>> BAD = List.of(MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.POISON,
        MobEffects.DIG_SLOWDOWN, MobEffects.UNLUCK, MobEffects.CONFUSION, MobEffects.BLINDNESS, MobEffects.HUNGER);

    /** Monster: the wheel spins (3 seconds) and only then lands on a boon or a bane. */
    public static void wheel(ServerPlayer sp, ItemStack st) {
        fx(sp, 2);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, .8f);
        int roll = sp.getRandom().nextInt(GOOD.size() + BAD.size());
        boolean good = roll < GOOD.size();
        Holder<MobEffect> eff = good ? GOOD.get(roll) : BAD.get(roll - GOOD.size());
        later(60, () -> {
            if (!sp.isAlive()) return;
            sp.addEffect(new MobEffectInstance(eff, good ? 20 * 45 : 20 * 20, good ? 1 : 0));
            sp.level().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, good ? 1.4f : 0.5f);
            sp.displayClientMessage(Component.literal("The wheel lands on " + eff.value().getDisplayName().getString()).withStyle(good ? ChatFormatting.GOLD : ChatFormatting.DARK_RED), true);
        });
    }

    /** Sun: three flaring suns, one ahead and one to either side of it. */
    public static void suns(ServerPlayer sp, ItemStack st) {
        Vec3 look = sp.getLookAngle(); Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        Vec3 mid = sp.position().add(0, 3.0, 0).add(flat.scale(15));
        Vec3[] at = { mid, mid.add(side.scale(10)).subtract(flat.scale(1)), mid.subtract(side.scale(10)).subtract(flat.scale(1)) };
        try {
            Class<?> c = Class.forName("net.swimmingtuna.lotm.pathway.bard.abilities.FlaringSun");
            var m = c.getMethod("spawnMiniatureSun", net.minecraft.world.level.Level.class, Vec3.class, LivingEntity.class, float.class, float.class);
            for (Vec3 p : at) m.invoke(null, sp.level(), p, sp, 40f, 9f);
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().warn("[astral] flaring sun failed: {}", t.toString());
            sp.displayClientMessage(Component.literal("The suns will not rise.").withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Spectator: the Quill opens the Storytelling screen (the Seq 1 prophecy ability), through LOTM's own packet. */
    public static void quill(ServerPlayer sp, ItemStack st) {
        try {
            Class<?> story = Class.forName("net.swimmingtuna.lotm.pathway.spectator.story.Storytelling");
            Class<?> pk = Class.forName("net.swimmingtuna.lotm.network.spectator.OpenStorytellingScreenS2C");
            story.getMethod("setPendingTarget", net.minecraft.world.entity.player.Player.class, LivingEntity.class).invoke(null, sp, sp);
            Object ctx = story.getMethod("contextFor", ServerPlayer.class).invoke(null, sp);
            Class<?> cc = ctx.getClass();
            Object waiting = story.getMethod("waitingFor", ServerPlayer.class).invoke(null, sp);
            int spirit = (int) Class.forName("net.swimmingtuna.lotm.beyonder.Spirituality").getMethod("getSpirituality", LivingEntity.class).invoke(null, sp);
            Object packet = null;
            for (var k : pk.getConstructors()) if (k.getParameterCount() == 7)
                packet = k.newInstance(cc.getMethod("sequence").invoke(ctx), spirit, cc.getMethod("targetName").invoke(ctx), cc.getMethod("playerNames").invoke(ctx),
                    cc.getMethod("tellerName").invoke(ctx), cc.getMethod("nearby").invoke(ctx), waiting);
            Class.forName("net.swimmingtuna.lotm.networking.LOTMNetworkHandler").getMethod("sendToPlayer",
                net.minecraft.network.protocol.common.custom.CustomPacketPayload.class, ServerPlayer.class).invoke(null, packet, sp);
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().warn("[astral] storytelling failed: {}", t.toString());
            sp.displayClientMessage(Component.literal("The quill stays still.").withStyle(ChatFormatting.GRAY), true);
        }
    }

    // ---- stage 2 ----
    private static final java.util.List<Object[]> LATER = new java.util.ArrayList<>();   // {ticksLeft, Runnable}

    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        for (int i = LATER.size() - 1; i >= 0; i--) {
            Object[] o = LATER.get(i);
            int left = (int) o[0] - 1; o[0] = left;
            if (left <= 0) { LATER.remove(i); try { ((Runnable) o[1]).run(); } catch (Throwable t) { com.mojang.logging.LogUtils.getLogger().warn("[astral] delayed uniqueness effect failed: {}", t.toString()); } }
        }
    }

    /** An ability sets this when it did nothing, so the key press costs no cooldown. */
    public static boolean refund;

    private static void later(int ticks, Runnable r) { LATER.add(new Object[]{ticks, r}); }

    /** Holds someone in place: no walking, no jumping, for this long. */
    private static void pin(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false));
        e.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 128, false, false));
        e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
    }

    private static boolean foe(ServerPlayer sp, LivingEntity e) {
        if (e == sp || !e.isAlive() || e.isAlliedTo(sp)) return false;
        return !(e instanceof net.minecraft.world.entity.TamableAnimal t && t.isOwnedBy(sp));
    }

    /** Hanged Man: the shadow spreads 20 blocks and a cage of shadow closes on everyone in it, spikes of shadow impaling them. */
    public static void cage(ServerPlayer sp, ItemStack st) {
        ServerLevel lv = sp.serverLevel();
        fx(sp, 3);
        lv.playSound(null, sp.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 1.5f, 0.5f);
        for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(20, 8, 20), x -> foe(sp, x) && x.distanceToSqr(sp) <= 400)) {
            int delay = 8 + (int) (e.distanceTo(sp) * .6);   // the shadow reaches the near ones first
            later(delay, () -> {
                if (!e.isAlive()) return;
                pin(e, 20 * 12);
                world(lv, new DoorNet.World(2, e.getId(), 0, e.getX(), e.getY(), e.getZ(), 0, 0, 0, 60));
                lv.playSound(null, e.blockPosition(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, .6f, 1.6f);
                e.hurt(lv.damageSources().magic(), 14f);
            });
        }
    }

    /** Chained: the doll climbs down, walks to the nearest opponent and binds them where they stand. */
    public static void doll(ServerPlayer sp, ItemStack st) {
        ServerLevel lv = sp.serverLevel();
        LivingEntity tgt = lv.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(24), e -> foe(sp, e))
            .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(sp))).orElse(null);
        if (tgt == null) { sp.displayClientMessage(Component.literal("The doll finds nobody.").withStyle(ChatFormatting.GRAY), true); refund = true; return; }
        int walk = Math.max(14, (int) (sp.distanceTo(tgt) / .35));
        int total = walk + 16;
        world(lv, new DoorNet.World(3, sp.getId(), tgt.getId(), sp.getX(), sp.getY(), sp.getZ(), tgt.getX(), tgt.getY(), tgt.getZ(), total));
        later(walk, () -> {
            if (!tgt.isAlive()) return;
            pin(tgt, 20 * 10);
            lv.playSound(null, tgt.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.5f, 0.7f);
            for (int j = 0; j < 20; j++) {
                double a = j * Math.PI / 10;
                for (int h = 0; h < 4; h++) lv.sendParticles(ParticleTypes.ENCHANTED_HIT, tgt.getX() + Math.cos(a + h) * 0.7, tgt.getY() + h * 0.45, tgt.getZ() + Math.sin(a + h) * 0.7, 1, 0, 0, 0, 0);
            }
        });
    }

    /** Tyrant: a ball of lightning is thrown, then swells into a huge sphere of storm and chaos. */
    public static void storm(ServerPlayer sp, ItemStack st) {
        ServerLevel lv = sp.serverLevel();
        Vec3 eye = sp.getEyePosition(), dir = sp.getLookAngle();
        net.minecraft.world.phys.BlockHitResult hit = lv.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(dir.scale(40)),
            net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, sp));
        Vec3 end = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? eye.add(dir.scale(40)) : hit.getLocation().subtract(dir.scale(1.2));
        Vec3 start = eye.add(dir.scale(.8)).subtract(0, .4, 0);
        int fly = Math.max(4, (int) (eye.distanceTo(end) / 2.5));
        final double R = 14;
        world(lv, new DoorNet.World(4, fly, (int) (R * 10), start.x, start.y, start.z, end.x, end.y, end.z, fly + 22 + 70 + 16));
        lv.playSound(null, sp.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1f, 1.6f);
        later(fly, () -> {
            lv.playSound(null, net.minecraft.core.BlockPos.containing(end), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6f, 0.5f);
            for (int t = 6; t < 90; t += 2) {
                final int tt = t;
                later(t, () -> {
                    double r = Math.min(R, 2 + R * tt / 22.0);
                    if (tt % 10 == 6) {
                        for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(end, end).inflate(r), x -> x != sp && x.isAlive() && x.position().add(0, x.getBbHeight() / 2, 0).distanceTo(end) <= r)) {
                            e.hurt(lv.damageSources().lightningBolt(), 14f);
                            e.setDeltaMovement(e.getDeltaMovement().add((lv.random.nextDouble() - .5) * 1.2, 0.35, (lv.random.nextDouble() - .5) * 1.2));
                            e.hurtMarked = true;
                        }
                        net.minecraft.world.entity.LightningBolt b = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(lv);
                        if (b != null) { b.setVisualOnly(true); b.moveTo(end.x + (lv.random.nextDouble() - .5) * 2 * r * 0.8, end.y - r * 0.3, end.z + (lv.random.nextDouble() - .5) * 2 * r * 0.8); lv.addFreshEntity(b); }
                    }
                });
            }
        });
    }

    /** Tyrant's no-shield rule: while the lightning ball is carried, a shield blocks nothing. */
    public static void shield(net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack w = WornUniq.findWorn(sp);
        if (!w.isEmpty() && BuiltInRegistries.ITEM.getKey(w.getItem()).getPath().equals("storm_katana")) {
            e.setBlocked(false);
            sp.displayClientMessage(Component.literal("The lightning ball will not let you hold a shield.").withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Tells everyone who can see this player to play a worn-item effect (1 serpent strike, 2 wheel spin). */
    public static void fx(ServerPlayer sp, int kind) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new DoorNet.Fx(sp.getId(), kind));
    }
}
