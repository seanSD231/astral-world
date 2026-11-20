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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
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
 * The Moon's Corruption: a thin figure hanging in the air inside an enormous flower, under the
 * blood moon, over a congregation that will not stop kneeling to it.
 *
 * <p>Everything it does comes off how it is built. The corolla is the fight: wide open it is
 * attacking and can be hurt, folded over itself it is guarded and winding up, and the moment it
 * throws itself open again everything standing near goes with it. The lance is the thin red line
 * it holds. The threads it hangs from come down and pin you to the floor. The worshippers below
 * get up when it calls them and give it back what they have.</p>
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
                    .sized(5.0f, 16.0f).fireImmune().clientTrackingRange(24).build("moon_corruption"));

    public static void register(IEventBus bus) { ENTITIES.register(bus); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 900)
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
    private static final EntityDataAccessor<Float> OPEN =
            SynchedEntityData.defineId(MoonCorruption.class, EntityDataSerializers.FLOAT);

    /** 0 waxing, 1 crimson, 2 eclipse. */
    public int phase() { return entityData.get(PHASE); }
    /** Ticks left of whatever it is doing, for the renderer to lean on. */
    public int act() { return entityData.get(ACT); }
    /** How far its own moon has risen, nought to one. */
    public float moon() { return entityData.get(MOON); }
    /** How wide the corolla is standing: nought folded shut over it, one thrown fully open. */
    public float open() { return entityData.get(OPEN); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(PHASE, 0);
        b.define(ACT, 0);
        b.define(MOON, 0f);
        b.define(OPEN, 1f);
    }

    // ---- the fight ----

    private final ServerBossEvent bar = new ServerBossEvent(
            Component.literal("The Moon's Corruption").withColor(0xE0384A),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6);

    private int lance, bloom, threads, call, feed, fold;

    public MoonCorruption(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 250;
        setPersistenceRequired();
        setNoGravity(true);          // it hangs; nothing holds it up that anyone can see
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 32f));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    @Override
    public boolean causeFallDamage(float a, float b, DamageSource s) { return false; }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState st, BlockPos pos) { }

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
        float target = phase() == 0 ? .35f : (phase() == 1 ? .75f : 1f);
        if (m != target) entityData.set(MOON, m + Math.signum(target - m) * Math.min(.012f, Math.abs(target - m)));
        if (act() > 0) entityData.set(ACT, act() - 1);

        drift();
        corrupt();
        if (fold > 0) { fold--; petals(0f); return; }    // shut, it does nothing but wait
        petals(1f);

        if (--lance <= 0) lance();
        if (--bloom <= 0) bloom();
        if (phase() >= 1 && --threads <= 0) threads();
        if (--call <= 0) call();
        if (phase() >= 2 && --feed <= 0) feed();
    }

    /** A turn of its moon: it folds over itself, the light changes, and it opens again harder. */
    private void turn(int to) {
        entityData.set(PHASE, to);
        entityData.set(ACT, 40);
        fold = 60;
        ServerLevel lv = (ServerLevel) level();
        lv.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2f, to == 2 ? .6f : .8f);
        lv.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1, getZ(), 140, 2, 2, 2, .3);
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 3, false, false));
        heal(getMaxHealth() * .04f);
        for (Player p : nearby(30))
            p.displayClientMessage(Component.literal(to == 1
                    ? "The moon comes up behind it." : "The petals will not close again.").withColor(0xE0384A), true);
    }

    /** Standing in its light corrupts you; the count is what does the damage, and only leaving sheds it. */
    private void corrupt() {
        if (tickCount % 20 != 0) return;
        double r = phase() == 2 ? 30 : (phase() == 1 ? 22 : 15);
        for (Player p : nearby(r)) {
            if (p.isCreative() || p.isSpectator()) continue;
            int c = Corruption.add(p, phase() + 1);
            if (c >= 6) p.hurt(level().damageSources().magic(), 1f + (c - 6) * .8f);
            if (c >= 10) p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1, false, true));
        }
    }


    /** It drifts rather than walks: it keeps its distance and keeps its height. */
    private void drift() {
        LivingEntity t = getTarget();
        double want = 7.5 + phase() * .8;                 // how high it rides
        double floor = level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                getBlockX(), getBlockZ());
        double lift = (floor + want - getY()) * .06;
        Vec3 v = getDeltaMovement().scale(.86).add(0, Math.max(-.25, Math.min(.25, lift)), 0);
        if (t != null) {
            Vec3 to = t.position().subtract(position());
            double d = Math.sqrt(to.x * to.x + to.z * to.z);
            double pull = d > 20 ? .035 : (d < 11 ? -.026 : 0);  // close in, but never crowd
            if (d > .1) v = v.add(to.x / d * pull, 0, to.z / d * pull);
            setYRot((float) (Math.atan2(to.z, to.x) * 180 / Math.PI) - 90);
            yBodyRot = getYRot();
        }
        setDeltaMovement(v);
    }

    /** The corolla settles toward wherever it ought to be standing. */
    private void petals(float want) {
        float o = open();
        if (Math.abs(o - want) < .02f) { entityData.set(OPEN, want); return; }
        entityData.set(OPEN, o + Math.signum(want - o) * .055f);
    }

    /** Folded shut it takes far less; that is the whole reason to make it open. */
    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (open() < .35f) amount *= .25f;
        return super.hurt(src, amount);
    }

    /** The lance: the thin red line it carries, laid out on the floor and then run down. */
    private void lance() {
        lance = 120 - phase() * 22;
        LivingEntity t = getTarget();
        if (t == null) return;
        entityData.set(ACT, 26);
        ServerLevel lv = (ServerLevel) level();
        Vec3 to = t.position().subtract(position());
        MoonBeam.lance(lv, blockPosition().below(6), this, 9f + phase() * 4f, to.x, to.z);
        lv.playSound(null, blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.HOSTILE, 1.8f, .7f);
    }

    /**
     * The bloom: it shuts itself, holds, and throws itself open. While it is shut it can hardly be
     * hurt, which is the trade -- you are given a window and then told to leave.
     */
    private void bloom() {
        bloom = 230 - phase() * 40;
        fold = 50;
        entityData.set(ACT, 40);
        ServerLevel lv = (ServerLevel) level();
        lv.playSound(null, blockPosition(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.HOSTILE, 2f, .5f);
        MoonBeam.bloom(lv, blockPosition().below(7), this, 10f + phase() * 5f);
    }

    /** The threads it hangs from, come down for somebody else. */
    private void threads() {
        threads = 180 - phase() * 30;
        entityData.set(ACT, 20);
        ServerLevel lv = (ServerLevel) level();
        lv.playSound(null, blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.4f, .6f);
        for (Player p : nearby(26)) {
            if (p.isCreative() || p.isSpectator()) continue;
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 5, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.JUMP, 70, 200, false, false));   // pinned to the floor
            Corruption.add(p, 2);
            for (int k = 0; k < 16; k++)
                lv.sendParticles(ParticleTypes.DRIPPING_OBSIDIAN_TEAR,
                        p.getX(), p.getY() + 2.4 + k * .4, p.getZ(), 1, .25, .1, .25, 0);
        }
    }

    /** The congregation: what is already down there, called up. */
    private void call() {
        call = 260 - phase() * 55;
        ServerLevel lv = (ServerLevel) level();
        int n = 3 + phase();
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + random.nextDouble();
            double rr = 12 + random.nextDouble() * 8;
            EntityType<?> type = phase() >= 2 && random.nextBoolean() ? EntityType.HUSK : EntityType.DROWNED;
            Entity e = type.create(lv);
            if (!(e instanceof Mob mob)) continue;
            mob.moveTo(getX() + Math.cos(a) * rr, getY() - 8, getZ() + Math.sin(a) * rr, (float) (a * 180 / Math.PI), 0);
            mob.finalizeSpawn(lv, lv.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            mob.setPersistenceRequired();
            mob.setCustomName(Component.literal("Worshipper").withColor(0xB0384A));
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 600, 1, false, false));
            if (getTarget() != null) mob.setTarget(getTarget());
            lv.addFreshEntity(mob);
            lv.sendParticles(ParticleTypes.SCULK_SOUL, mob.getX(), mob.getY() + 1, mob.getZ(), 30, .4, .6, .4, .05);
        }
        lv.playSound(null, blockPosition(), SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.HOSTILE, 2f, .5f);
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
        // and what the congregation has, which is why they were called up
        for (Mob m : level().getEntitiesOfClass(Mob.class, new AABB(blockPosition()).inflate(20),
                x -> x.hasCustomName() && !(x instanceof MoonCorruption))) {
            got += m.getHealth() * .5f;
            m.discard();
            lv.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 1, m.getZ(), 20, .3, .5, .3, .06);
        }
        if (got > 0) {
            heal(Math.min(got, getMaxHealth() * .07f));
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

        // Everyone who was in the fight is paid their own, so a boss is not a race to a drop pile.
        for (Player p : nearby(42)) {
            Corruption.clear(p);
            Loot.pay(p, 90 + random.nextInt(60));
            p.displayClientMessage(Component.literal("The light goes out.").withColor(0xE0384A), false);
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
