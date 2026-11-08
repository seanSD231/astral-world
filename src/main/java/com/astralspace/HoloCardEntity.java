package com.astralspace;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;

/**
 * A tarot card lying on the ground. Rare and better cards (Seq 5 and up) show a hologram above them, drawn by {@link HoloRenderer}.
 * Everything the renderer needs (pathway, sequence, shiny) is synced to every nearby player; right click picks the card up again.
 */
public class HoloCardEntity extends Entity {
    private static final EntityDataAccessor<Integer> PATH = SynchedEntityData.defineId(HoloCardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEQ = SynchedEntityData.defineId(HoloCardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SHINY = SynchedEntityData.defineId(HoloCardEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID owner;
    private String ownerName = "";

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, AstralMod.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<HoloCardEntity>> TYPE = ENTITIES.register("holo_card", () ->
        EntityType.Builder.<HoloCardEntity>of(HoloCardEntity::new, MobCategory.MISC).sized(0.6f, 0.08f).fireImmune().clientTrackingRange(10).updateInterval(1000).build("holo_card"));

    public static void register(IEventBus bus) { ENTITIES.register(bus); }

    public HoloCardEntity(EntityType<?> type, Level level) { super(type, level); this.noPhysics = true; }

    public void setCard(String pathway, int seq, boolean shiny) {
        int p = 0;
        for (int i = 0; i < Tarot.PATHWAYS.length; i++) if (Tarot.PATHWAYS[i].equals(pathway)) p = i;
        entityData.set(PATH, p); entityData.set(SEQ, seq); entityData.set(SHINY, shiny);
    }
    public void setOwner(Player p) { owner = p.getUUID(); ownerName = p.getGameProfile().getName(); }
    public String pathway() { return Tarot.PATHWAYS[Math.max(0, Math.min(Tarot.PATHWAYS.length - 1, entityData.get(PATH)))]; }
    public int pathIndex() { return Math.max(0, Math.min(Tarot.PATHWAYS.length - 1, entityData.get(PATH))); }
    public int seq() { return entityData.get(SEQ); }
    public boolean shiny() { return entityData.get(SHINY); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(PATH, 0); b.define(SEQ, 9); b.define(SHINY, false); }

    @Override protected void readAdditionalSaveData(CompoundTag t) {
        setCard(t.getString("pathway"), t.getInt("seq"), t.getBoolean("shiny"));
        owner = t.hasUUID("owner") ? t.getUUID("owner") : null; ownerName = t.getString("ownerName");
    }
    @Override protected void addAdditionalSaveData(CompoundTag t) {
        t.putString("pathway", pathway()); t.putInt("seq", seq()); t.putBoolean("shiny", shiny());
        if (owner != null) t.putUUID("owner", owner); t.putString("ownerName", ownerName);
    }

    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    /** The hologram reaches several blocks above the card, so the renderer must not be culled with the thin card box. */
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(48, 30, 48); }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 200.0 * 200.0; }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (owner != null && !owner.equals(player.getUUID()) && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.literal("This card belongs to " + (ownerName.isEmpty() ? "someone else" : ownerName) + "."), true);
            return InteractionResult.CONSUME;
        }
        ItemStack st = Tarot.makeCard(pathway(), seq(), shiny());
        if (!player.getInventory().add(st)) player.drop(st, false);
        level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.PLAYERS, .7f, 1.4f);
        discard();
        return InteractionResult.CONSUME;
    }

    @Override
    public void tick() {
        super.tick();
        // if the block under the card is broken, the card drops as an item instead of floating
        if (!level().isClientSide && tickCount % 10 == 0 && level().getBlockState(blockPosition().below()).isAir() && level().getBlockState(blockPosition()).isAir()) {
            level().addFreshEntity(new ItemEntity(level(), getX(), getY() + .2, getZ(), Tarot.makeCard(pathway(), seq(), shiny())));
            discard();
        }
    }
}
