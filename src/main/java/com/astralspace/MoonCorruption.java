package com.astralspace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * The Moon's Corruption.
 *
 * <p>What the crimson moon makes of whatever it finds. It fights in three turns of its own moon:
 * waxing, where it is still mostly a thing with arms and hunts you down; crimson, where it pulls a
 * moon of its own up over the fight and the light off it falls on everything; and eclipse, where
 * what is left of it stops pretending and feeds.</p>
 *
 * <p>Its corruption is not a damage type but a count. Standing in its light adds to a player's
 * corruption and corruption is what hurts them -- slowly at first, and then not slowly -- and it
 * only comes off by getting out of the light. That makes the fight about where you stand rather
 * than how much health you have, which is the only kind of boss worth building twice.</p>
 */
public class MoonCorruption extends Monster {

    // ---- registry ----

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AstralMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<MoonCorruption>> TYPE =
            ENTITIES.register("moon_corruption", () -> EntityType.Builder
                    .of(MoonCorruption::new, MobCategory.MONSTER)
                    .sized(2.2f, 4.4f).fireImmune().clientTrackingRange(12).build("moon_corruption"));

    public static void register(IEventBus bus) { ENTITIES.register(bus); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 480)
                .add(Attributes.ATTACK_DAMAGE, 12)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.ARMOR_TOUGHNESS, 6)
                .add(Attributes.MOVEMENT_SPEED, .3)
                .add(Attributes.KNOCKBACK_RESISTANCE, .85)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    // ---- what the client needs to draw it ----

    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(MoonCorruption.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACT =
            SynchedEntityData.defineId(MoonCorruption.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> MOON =
            SynchedEntityData.defineId(MoonCorruption.class, EntityDataSerializers.FLOAT);

    /** 0 waxing, 1 crimson, 2 eclipse. */
    public int phase() { return entityData.get(PHASE); }
    /** Ticks left of whatever it is doing, for the renderer to lean on. */
    public int act() { return entityData.get(ACT); }
    /** How far its own moon has risen, nought to one. */
    public float moon() { return entityData.get(MOON); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(PHASE, 0);
        b.define(ACT, 0);
        b.define(MOON, 0f);
    }

    // ---- the fight ----

    private final ServerBossEvent bar = new ServerBossEvent(
            Component.literal("The Moon's Corruption").withColor(0xE0384A),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6);

    private int howl, beam, summon, feed;

    public MoonCorruption(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 250;
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, .8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 24f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    @Override
    public boolean canBeAffected(MobEffectInstance e) {
        return e.getEffect() != MobEffects.WITHER && e.getEffect() != MobEffects.POISON && super.canBeAffected(e);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); bar.addPlayer(p); }

    @Override
    public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); bar.removePlayer(p); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) { clientTick(); return; }

        bar.setProgress(getHealth() / getMaxHealth());
        int want = getHealth() > getMaxHealth() * .66f ? 0 : (getHealth() > getMaxHealth() * .33f ? 1 : 2);
        if (want != phase()) turn(want);

        float m = moon();
        float target = phase() == 0 ? 0f : (phase() == 1 ? .7f : 1f);
        if (m != target) entityData.set(MOON, m + Math.signum(target - m) * Math.min(.012f, Math.abs(target - m)));
        if (act() > 0) entityData.set(ACT, act() - 1);

        if (phase() >= 1) corrupt();
        if (--howl <= 0) howl();
        if (phase() >= 1 && --beam <= 0) beam();
        if (--summon <= 0) summon();
        if (phase() >= 2 && --feed <= 0) feed();
    }

    /** A turn of its moon: it stops, the light changes, and it comes back harder. */
    private void turn(int to) {
        entityData.set(PHASE, to);
        entityData.set(ACT, 40);
        ServerLevel lv = (ServerLevel) level();
        lv.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2f, to == 2 ? .6f : .8f);
        lv.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 2, getZ(), 120, 2, 2, 2, .3);
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 3, false, false));
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(.3 + to * .07);
        heal(getMaxHealth() * .04f);
        for (Player p : nearby(28))
            p.displayClientMessage(Component.literal(to == 1 ? "The moon comes up." : "It stops pretending.")
                    .withColor(0xE0384A), true);
    }

    /** Standing in its light corrupts you; the count is what does the damage, and only leaving sheds it. */
    private void corrupt() {
        if (tickCount % 20 != 0) return;
        double r = phase() == 2 ? 20 : 14;
        for (Player p : nearby(r)) {
            if (p.isCreative() || p.isSpectator()) continue;
            int c = Corruption.add(p, phase() == 2 ? 2 : 1);
            if (c >= 6) p.hurt(level().damageSources().magic(), 1f + (c - 6) * .8f);
            if (c >= 10) p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, false, true));
        }
    }

    /** The howl: knocks everyone back out of its reach and leaves them corrupted for it. */
    private void howl() {
        howl = 150 - phase() * 30;
        entityData.set(ACT, 25);
        ServerLevel lv = (ServerLevel) level();
        lv.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.2f, .55f);
        lv.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 2.6, getZ(), 2, .4, .4, .4, 0);
        for (Player p : nearby(11)) {
            Vec3 away = p.position().subtract(position()).normalize().scale(1.1).add(0, .45, 0);
            p.push(away.x, away.y, away.z);
            p.hurt(level().damageSources().mobAttack(this), 5f + phase() * 3f);
            Corruption.add(p, 2);
        }
    }

    /** A shaft of its moonlight, dropped where the target is standing a moment before it lands. */
    private void beam() {
        beam = 110 - phase() * 25;
        LivingEntity t = getTarget();
        if (t == null) return;
        entityData.set(ACT, 20);
        ServerLevel lv = (ServerLevel) level();
        BlockPos at = t.blockPosition();
        lv.sendParticles(ParticleTypes.END_ROD, at.getX() + .5, at.getY() + 6, at.getZ() + .5, 70, .4, 5, .4, .02);
        lv.playSound(null, at, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.4f, 1.5f);
        MoonBeam.drop(lv, at, this, 26);
    }

    /** Thralls: whatever the light has already got to. */
    private void summon() {
        summon = 240 - phase() * 55;
        ServerLevel lv = (ServerLevel) level();
        int n = 2 + phase();
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + random.nextDouble();
            EntityType<?> type = phase() >= 2 && random.nextBoolean() ? EntityType.RAVAGER
                    : (phase() >= 1 ? EntityType.HUSK : EntityType.ZOMBIE);
            Entity e = type.create(lv);
            if (!(e instanceof Mob mob)) continue;
            mob.moveTo(getX() + Math.cos(a) * 4, getY(), getZ() + Math.sin(a) * 4, (float) (a * 180 / Math.PI), 0);
            mob.finalizeSpawn(lv, lv.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            mob.setPersistenceRequired();
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 600, 1, false, false));
            if (getTarget() != null) mob.setTarget(getTarget());
            lv.addFreshEntity(mob);
        }
        lv.playSound(null, blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.HOSTILE, 2f, .6f);
    }

    /** Eclipse only: it takes back what the corruption has been storing up. */
    private void feed() {
        feed = 170;
        entityData.set(ACT, 30);
        ServerLevel lv = (ServerLevel) level();
        float got = 0;
        for (Player p : nearby(18)) {
            int c = Corruption.get(p);
            if (c <= 0) continue;
            p.hurt(level().damageSources().magic(), 2f + c * .7f);
            got += 4 + c * 1.6f;
            lv.sendParticles(ParticleTypes.DAMAGE_INDICATOR, p.getX(), p.getY() + 1, p.getZ(), 8, .3, .5, .3, .02);
        }
        if (got > 0) {
            heal(Math.min(got, getMaxHealth() * .06f));
            lv.playSound(null, blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.4f, .7f);
        }
    }

    private List<Player> nearby(double r) {
        return level().getEntitiesOfClass(Player.class, new AABB(blockPosition()).inflate(r),
                p -> p.isAlive() && !p.isSpectator());
    }

    private void clientTick() {
        if (phase() == 0 || !(level() instanceof Level lv)) return;
        for (int i = 0; i < (phase() == 2 ? 3 : 1); i++)
            lv.addParticle(ParticleTypes.DRIPPING_OBSIDIAN_TEAR,
                    getX() + (random.nextDouble() - .5) * 3, getY() + random.nextDouble() * 4,
                    getZ() + (random.nextDouble() - .5) * 3, 0, 0, 0);
    }

    // ---- what it leaves ----

    @Override
    public void die(DamageSource src) {
        super.die(src);
        if (level().isClientSide) return;
        bar.removeAllPlayers();
        ServerLevel lv = (ServerLevel) level();
        lv.playSound(null, blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 2f, .7f);
        lv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 2, getZ(), 200, 2, 2, 2, .25);

        // Everyone who was in the fight gets their own, so a boss is not a race to the drop pile.
        List<Player> share = nearby(42);
        for (Player p : share) {
            Corruption.clear(p);
            int n = 2 + (random.nextDouble() < .5 ? 1 : 0);
            for (int i = 0; i < n; i++) {
                // weighted to the Moon's own set, which is what it is: the Apothecary pathway
                String set = random.nextDouble() < .55 ? "apothecary" : ArtifactSets.random(random).id();
                var slot = Artifacts.Slot.values()[random.nextInt(Artifacts.Slot.values().length)];
                int rarity = random.nextDouble() < .3 ? 5 : 4;
                ItemStack st = Artifacts.roll(set, slot, rarity, random);
                if (!p.getInventory().add(st)) p.drop(st, false);
            }
            p.displayClientMessage(Component.literal("The light goes out.").withColor(0xE0384A), false);
        }
        if (share.isEmpty()) {
            for (int i = 0; i < 3; i++)
                spawnAtLocation(Artifacts.roll("apothecary",
                        Artifacts.Slot.values()[random.nextInt(Artifacts.Slot.values().length)], 4, random));
        }
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("phase", phase());
        if (hasCustomName()) bar.setName(getDisplayName());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag t) {
        super.readAdditionalSaveData(t);
        entityData.set(PHASE, t.getInt("phase"));
        if (hasCustomName()) bar.setName(getDisplayName());
    }

    @Override
    public void setCustomName(Component name) { super.setCustomName(name); bar.setName(getDisplayName()); }

    @Override
    public void remove(RemovalReason r) { bar.removeAllPlayers(); super.remove(r); }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.WARDEN_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.RAVAGER_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.RAVAGER_DEATH; }
}
