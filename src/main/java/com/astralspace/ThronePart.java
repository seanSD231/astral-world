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
import net.minecraft.world.item.ItemStack;
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
    private static final EntityDataAccessor<Boolean> TBL = SynchedEntityData.defineId(ThronePart.class, EntityDataSerializers.BOOLEAN);
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

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(W, 1f); b.define(H, 1f); b.define(SEAT, false); b.define(TBL, false); b.define(OCC, -1); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (key == W || key == H) refreshDimensions();
        super.onSyncedDataUpdated(key);
    }
    @Override public EntityDimensions getDimensions(Pose pose) { return EntityDimensions.fixed(entityData.get(W), entityData.get(H)); }

    /** Marks this box as part of the long table: a surface that holds items like an item frame (right click with an item to put it down, empty hand or sneak to take it back). */
    public void setTable() { entityData.set(TBL, true); }
    public boolean isTable() { return entityData.get(TBL); }
    public boolean isSeat() { return entityData.get(SEAT); }
    /** Index in Tarot.PATHWAYS of the pathway of whoever sits here, or -1 (empty seat, or not a Beyonder). */
    public int occupant() { return entityData.get(OCC); }

    @Override protected void readAdditionalSaveData(CompoundTag t) {}
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
    @Override public boolean shouldBeSaved() { return false; }

    @Override public boolean canBeCollidedWith() { return true; }
    @Override public boolean isPickable() { return entityData.get(SEAT) || entityData.get(TBL); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override protected boolean canAddPassenger(Entity e) { return entityData.get(SEAT) && getPassengers().isEmpty(); }

    private java.util.List<net.minecraft.world.entity.Display.ItemDisplay> shown(double x, double y, double z, double r) {
        return level().getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class, new net.minecraft.world.phys.AABB(x - r, y - .3, z - r, x + r, y + .5, z + r), d -> d.getTags().contains("astral_table"));
    }
    private static net.minecraft.nbt.ListTag nums(double... v) { var l = new net.minecraft.nbt.ListTag(); for (double d : v) l.add(net.minecraft.nbt.DoubleTag.valueOf(d)); return l; }
    private static net.minecraft.nbt.ListTag fl(float... v) { var l = new net.minecraft.nbt.ListTag(); for (float d : v) l.add(net.minecraft.nbt.FloatTag.valueOf(d)); return l; }

    @Override
    public InteractionResult interactAt(Player player, net.minecraft.world.phys.Vec3 vec, InteractionHand hand) {
        if (!entityData.get(TBL)) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        double x = getX() + vec.x, z = getZ() + vec.z, y = getY() + getBbHeight() + .01;
        ItemStack held = player.getItemInHand(hand);
        var near = shown(x, y, z, .55);
        if (held.isEmpty() || player.isSecondaryUseActive()) {   // take back the closest one
            net.minecraft.world.entity.Display.ItemDisplay best = null; double bd = 1e9;
            for (var d : near) { double dd = d.distanceToSqr(x, d.getY(), z); if (dd < bd) { bd = dd; best = d; } }
            if (best == null) return InteractionResult.PASS;
            ItemStack back = best.getSlot(0).get().copy(); best.discard();
            if (!player.getInventory().add(back)) player.drop(back, false);
            return InteractionResult.SUCCESS;
        }
        ItemStack one = held.copyWithCount(1);
        boolean block = held.getItem() instanceof net.minecraft.world.item.BlockItem;
        var d = net.minecraft.world.entity.EntityType.ITEM_DISPLAY.create(level());
        if (d == null) return InteractionResult.PASS;
        var n = new CompoundTag();
        n.put("Pos", nums(x, y + (block ? .02 : .01), z));
        n.put("Rotation", fl(player.getYRot() + 180f, 0f));
        n.put("Tags", new net.minecraft.nbt.ListTag());
        ((net.minecraft.nbt.ListTag) n.get("Tags")).add(net.minecraft.nbt.StringTag.valueOf("astral_table"));
        n.put("item", one.save(level().registryAccess()));
        n.putString("item_display", block ? "ground" : "fixed");
        var tf = new CompoundTag();
        tf.put("translation", fl(0f, block ? .2f : .0f, 0f));
        tf.put("left_rotation", block ? fl(0f, 0f, 0f, 1f) : fl(.70710677f, 0f, 0f, .70710677f));   // flat items lie face up
        tf.put("scale", block ? fl(1.6f, 1.6f, 1.6f) : fl(.55f, .55f, .55f));
        tf.put("right_rotation", fl(0f, 0f, 0f, 1f));
        n.put("transformation", tf);
        d.load(n);
        level().addFreshEntity(d);
        if (!player.getAbilities().instabuild) held.shrink(1);
        return InteractionResult.SUCCESS;
    }

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
        if (!level().isClientSide && entityData.get(TBL) && tickCount % 20 == 0 && (parent == null || !(((net.minecraft.server.level.ServerLevel) level()).getEntity(parent) instanceof HoloCardEntity)))
            for (var d : shown(getX(), getY() + getBbHeight(), getZ(), getBbWidth() / 2 + .1)) { var it = d.getSlot(0).get().copy(); d.discard(); level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level(), getX(), getY() + getBbHeight() + .3, getZ(), it)); }
        if (!level().isClientSide && (parent == null || tickCount % 20 == 0 && !(((net.minecraft.server.level.ServerLevel) level()).getEntity(parent) instanceof HoloCardEntity))) discard();
    }
}
