package com.astralspace;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The hunger games leaderboard: a monument an operator sets down anywhere with /hungergames board. It is one entity,
 * drawn by {@link HgBoardRenderer} as a dark stone slab in a gold frame between two burning braziers, with the last
 * game's champion (and their face), the places behind them and the champions of earlier games cut into it.
 *
 * What it shows is one synced string, taken from {@link HungerGames#boardText()} every couple of seconds, so every
 * board in the world follows the games without being tracked. It cannot be hurt, pushed or walked into; an operator
 * takes it away with /hungergames board remove.
 */
public class HgBoard extends Entity {
    private static final EntityDataAccessor<String> TEXT = SynchedEntityData.defineId(HgBoard.class, EntityDataSerializers.STRING);

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, AstralMod.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<HgBoard>> TYPE = ENTITIES.register("hg_board", () ->
        EntityType.Builder.<HgBoard>of(HgBoard::new, MobCategory.MISC).sized(1f, 1f).fireImmune().clientTrackingRange(10).updateInterval(40).build("hg_board"));

    public static void register(IEventBus bus) { ENTITIES.register(bus); }

    public HgBoard(EntityType<?> type, Level level) { super(type, level); this.noPhysics = true; }

    /** The lines to show; see {@link HungerGames#boardText()} for their shape. */
    public String text() { return entityData.get(TEXT); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(TEXT, ""); }
    @Override protected void readAdditionalSaveData(CompoundTag t) { }
    @Override protected void addAdditionalSaveData(CompoundTag t) { }

    @Override public boolean isInvulnerableTo(DamageSource s) { return true; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(6, 8, 6); }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 128.0 * 128.0; }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (tickCount % 40 == 1) { String now = HungerGames.boardText(); if (!now.equals(text())) entityData.set(TEXT, now); }
            return;
        }
        // the braziers on the two pillars burn, and embers drift up off them
        double q = Math.toRadians(180 - getYRot()), c = Math.cos(q), s = Math.sin(q);
        for (int sd = -1; sd <= 1; sd += 2) {
            double lx = sd * 3.35, x = getX() + c * lx, z = getZ() - s * lx, y = getY() + 5.55;
            if (tickCount % 2 == 0) level().addParticle(ParticleTypes.FLAME, x + (random.nextDouble() - .5) * .3, y, z + (random.nextDouble() - .5) * .3, 0, .03 + random.nextDouble() * .03, 0);
            if (tickCount % 7 == 0) level().addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
            if (tickCount % 5 == 0) level().addParticle(ParticleTypes.SMOKE, x, y + .5, z, 0, .04, 0);
        }
    }
}
