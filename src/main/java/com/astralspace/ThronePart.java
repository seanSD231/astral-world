package com.astralspace;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;

/**
 * An invisible solid box that makes part of a hologram throne real: you can walk onto it, stand on it, and (for the seat) right click to sit.
 * Spawned by {@link HoloCardEntity} and removed with it; never saved.
 */
public class ThronePart extends Entity {
    private static final EntityDataAccessor<Float> W = SynchedEntityData.defineId(ThronePart.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> H = SynchedEntityData.defineId(ThronePart.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> SEAT = SynchedEntityData.defineId(ThronePart.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> OCC = SynchedEntityData.defineId(ThronePart.class, EntityDataSerializers.INT);
    private UUID parent;

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, AstralMod.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<ThronePart>> TYPE = ENTITIES.register("throne_part", () ->
        EntityType.Builder.<ThronePart>of(ThronePart::new, MobCategory.MISC).sized(1f, 1f).fireImmune().clientTrackingRange(10).updateInterval(1000).build("throne_part"));

    public static void register(IEventBus bus) { ENTITIES.register(bus); }

    public ThronePart(EntityType<?> type, Level level) { super(type, level); this.noPhysics = true; }

    public void setup(UUID parent, float width, float height, boolean seat) {
        this.parent = parent;
        entityData.set(W, width); entityData.set(H, height); entityData.set(SEAT, seat);
        refreshDimensions();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(W, 1f); b.define(H, 1f); b.define(SEAT, false); b.define(OCC, -1); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (key == W || key == H) refreshDimensions();
        super.onSyncedDataUpdated(key);
    }
    @Override public EntityDimensions getDimensions(Pose pose) { return EntityDimensions.fixed(entityData.get(W), entityData.get(H)); }

    public boolean isSeat() { return entityData.get(SEAT); }
    /** Index in Tarot.PATHWAYS of the pathway of whoever sits here, or -1 (empty seat, or not a Beyonder). */
    public int occupant() { return entityData.get(OCC); }

    @Override protected void readAdditionalSaveData(CompoundTag t) {}
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
    @Override public boolean shouldBeSaved() { return false; }

    @Override public boolean canBeCollidedWith() { return true; }
    @Override public boolean isPickable() { return entityData.get(SEAT); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override protected boolean canAddPassenger(Entity e) { return entityData.get(SEAT) && getPassengers().isEmpty(); }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!entityData.get(SEAT) || player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!level().isClientSide) player.startRiding(this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && entityData.get(SEAT) && tickCount % 5 == 0) {
            int v = -1;
            if (getFirstPassenger() instanceof net.minecraft.server.level.ServerPlayer sp) v = java.util.Arrays.asList(Tarot.PATHWAYS).indexOf(Lotm.pathwayId(sp));
            if (v != entityData.get(OCC)) entityData.set(OCC, v);
        }
        if (!level().isClientSide && (parent == null || tickCount % 20 == 0 && !(((net.minecraft.server.level.ServerLevel) level()).getEntity(parent) instanceof HoloCardEntity))) discard();
    }
}
