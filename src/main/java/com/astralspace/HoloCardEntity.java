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
    private static final EntityDataAccessor<Integer> GOO = SynchedEntityData.defineId(HoloCardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SYMBOL = SynchedEntityData.defineId(HoloCardEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID owner;
    private String ownerName = "";

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, AstralMod.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<HoloCardEntity>> TYPE = ENTITIES.register("holo_card", () ->
        EntityType.Builder.<HoloCardEntity>of(HoloCardEntity::new, MobCategory.MISC).sized(0.8f, 0.3f).fireImmune().clientTrackingRange(10).updateInterval(1000).build("holo_card"));

    public static void register(IEventBus bus) { ENTITIES.register(bus); ThronePart.register(bus); }

    public HoloCardEntity(EntityType<?> type, Level level) { super(type, level); this.noPhysics = true; }

    public void setCard(String pathway, int seq, boolean shiny) {
        int gi = Tarot.gooIndex(pathway);
        entityData.set(GOO, gi);
        if (gi >= 0) { entityData.set(PATH, 0); entityData.set(SEQ, 0); entityData.set(SHINY, false); return; }
        int p = 0;
        for (int i = 0; i < Tarot.PATHWAYS.length; i++) if (Tarot.PATHWAYS[i].equals(pathway)) p = i;
        entityData.set(PATH, p); entityData.set(SEQ, seq); entityData.set(SHINY, shiny);
    }
    public void setOwner(Player p) { owner = p.getUUID(); ownerName = p.getGameProfile().getName(); }
    /** Index of the Great Old One this card is (0..8), or -1 for an ordinary card. */
    public int goo() { return entityData.get(GOO); }
    public String pathway() { if (goo() >= 0) return "goo_" + Tarot.GOO_IDS[goo()]; return Tarot.PATHWAYS[Math.max(0, Math.min(Tarot.PATHWAYS.length - 1, entityData.get(PATH)))]; }
    public int pathIndex() { return Math.max(0, Math.min(Tarot.PATHWAYS.length - 1, entityData.get(PATH))); }
    public int seq() { return entityData.get(SEQ); }
    public boolean shiny() { return entityData.get(SHINY); }
    /** Placed while crouching: only the pathway symbol is shown, no card and no hologram scene. */
    public boolean symbolOnly() { return entityData.get(SYMBOL); }
    public void setSymbolOnly(boolean b) { entityData.set(SYMBOL, b); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(PATH, 0); b.define(SEQ, 9); b.define(SHINY, false); b.define(SYMBOL, false); b.define(GOO, -1); }

    @Override protected void readAdditionalSaveData(CompoundTag t) {
        setCard(t.getString("pathway"), t.getInt("seq"), t.getBoolean("shiny"));
        setSymbolOnly(t.getBoolean("symbolOnly"));
        owner = t.hasUUID("owner") ? t.getUUID("owner") : null; ownerName = t.getString("ownerName");
    }
    @Override protected void addAdditionalSaveData(CompoundTag t) {
        t.putBoolean("symbolOnly", symbolOnly()); t.putString("pathway", pathway()); t.putInt("seq", seq()); t.putBoolean("shiny", shiny());
        if (owner != null) t.putUUID("owner", owner); t.putString("ownerName", ownerName);
    }

    @Override public boolean isPickable() { return true; }
    @Override public float getPickRadius() { return 0.3f; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    /** The hologram reaches several blocks above the card, so the renderer must not be culled with the thin card box. */
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(goo() >= 0 || pathIndex() == 18 && seq() == 0 ? 300 : 48, goo() >= 0 || pathIndex() == 18 && seq() == 0 ? 220 : 30, goo() >= 0 || pathIndex() == 18 && seq() == 0 ? 300 : 48); }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 420.0 * 420.0; }

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

    /** Throne holograms (Death and Black Emperor, Seq 1 and 0) turn in quarter turns so their solid parts line up with the model. */
    public int throneQuarter() { return (int) Math.round((Math.PI - Math.toRadians(getYRot())) / (Math.PI / 2)); }
    public boolean hasThrone() { return !symbolOnly() && (goo() == 0 || goo() < 0 && seq() <= 1 && (pathIndex() == 5 || pathIndex() == 8 || pathIndex() == 18)); }

    /** Real, solid boxes matching HoloRenderer.throneModel (s = .95 * scale): three steps, seat, armrests and the back. */
    /** The Fool's hall: twenty-two seats you can sit in and the long table under the card, matching HoloRenderer.foolHall (local x right, z toward you). */
    /** How much bigger than a person the Fool's hall is; HoloRenderer.foolHall draws it with the same number. */
    public static final float HALL_K = 2.4f;
    /** Lord of Mysteries: seven real seats round the table (scale of one chair in S units, ring radius in S units, first angle) and the Fool at Seq 1: eight chairs round a round table. HoloRenderer draws from the same numbers. */
    public static final float LOM_CHAIR = .9f, LOM_RING = 4.35f, LOM_PHASE = .2f, LOM_S = 2.35f;
    public static final float F1_R = 9f, F1_S = 1.7f;
    /** The hall's layout in hall units (times HALL_K for blocks): table centre z and half length, half width, the side chairs' x and pitch. The renderer draws from the same numbers. */
    public static final float HALL_ZC = -12.4f, HALL_HL = 13.0f, HALL_W = 1.2f, HALL_SX = 2.0f, HALL_PITCH = 2.4f, HALL_Z0 = -1.9f, HALL_CHAIR = .9f, HALL_HEAD = 2.0f;
    private void spawnHall() {
        int k = throneQuarter(); float K = HALL_K;
        double c = Math.round(Math.cos(k * Math.PI / 2)), sn = Math.round(Math.sin(k * Math.PI / 2));
        java.util.List<float[]> parts = new java.util.ArrayList<>();   // {local x, bottom y, local z, side, height, kind (0 solid, 1 seat, 2 table)}, in hall units
        float zc = HALL_ZC, hl = HALL_HL, side = HALL_W * 2 + .04f;
        for (float z = zc + hl - side / 2; z > zc - hl - side / 2; z -= side) parts.add(new float[]{0, 0, z, side, .8f, 2});     // the table, solid, and a surface for items
        for (int sd = -1; sd <= 1; sd += 2) for (int i = 0; i < 10; i++) parts.add(new float[]{sd * HALL_SX, 0, HALL_Z0 - i * HALL_PITCH, .8f * HALL_CHAIR, .5f * HALL_CHAIR, 1});
        parts.add(new float[]{0, 0, HALL_HEAD, .8f * HALL_CHAIR, .5f * HALL_CHAIR, 1}); parts.add(new float[]{0, 0, zc - hl - 1.3f, .8f * HALL_CHAIR, .5f * HALL_CHAIR, 1});
        for (float[] q : parts) {
            double x = q[0] * K, z = q[2] * K, wx = x * c + z * sn, wz = -x * sn + z * c;
            ThronePart tp = new ThronePart(ThronePart.TYPE.get(), level());
            tp.setup(getUUID(), q[3] * K, q[4] * K, q[5] == 1);
            if (q[5] == 2) tp.setTable();
            tp.setPos(getX() + wx, getY() + q[1] * K, getZ() + wz);
            level().addFreshEntity(tp);
        }
    }

    /** Lord of Mysteries: the marble stair up to the door of light, as solid boxes matching HoloRenderer.lightDoor (z0 9.5, 14 steps of .42 rise, .9 run, half width 5.8). */
    private void spawnStairs() {
        int k = throneQuarter(); float K = HALL_K;
        double c = Math.round(Math.cos(k * Math.PI / 2)), sn = Math.round(Math.sin(k * Math.PI / 2));
        float side = 2.34f, z0 = 9.5f, run = .9f, rise = .42f;
        for (int i = 0; i <= 14; i++) for (int j = 0; j < 5; j++) {
            double x = (-4.68f + j * side) * K, z = (z0 + i * run + side / 2) * K, wx = x * c + z * sn, wz = -x * sn + z * c;
            ThronePart tp = new ThronePart(ThronePart.TYPE.get(), level());
            tp.setup(getUUID(), side * K, Math.min(14, i + 1) * rise * K, false);
            tp.setPos(getX() + wx, getY(), getZ() + wz);
            level().addFreshEntity(tp);
        }
    }

    private void spawnRing(int n, float ring, float phase, float scale) {   // n seats on a circle, each a real seat box the size of the drawn chair
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + phase;
            ThronePart tp = new ThronePart(ThronePart.TYPE.get(), level());
            tp.setup(getUUID(), .8f * scale, .5f * scale, true);
            tp.setPos(getX() + Math.cos(a) * ring, getY(), getZ() + Math.sin(a) * ring);
            level().addFreshEntity(tp);
        }
    }
    private void spawnThrone() {
        if (goo() == 0) { spawnRing(7, LOM_RING * LOM_S, LOM_PHASE, LOM_CHAIR * LOM_S); return; }
        if (pathIndex() == 18 && seq() == 1) { float k = F1_S * .55f; spawnRing(8, F1_R * .34f + 1.7f * k, (float) Math.PI / 8, k); return; }
        if (pathIndex() == 18) { spawnHall(); return; }
        float s = .95f * (seq() == 0 ? 2.1f : 1.15f);
        int k = throneQuarter();
        double c = Math.round(Math.cos(k * Math.PI / 2)), sn = Math.round(Math.sin(k * Math.PI / 2));
        // entity boxes are always square: {local x, bottom y, local z, side, height, seat?}
        java.util.List<float[]> parts = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) parts.add(new float[]{0, .22f * i * s, 0, (1.5f - .22f * (i + 1)) * s * 1.7f, .22f * s, 0});
        parts.add(new float[]{0, .66f * s, 0, 1.0f * s, .45f * s, 1});
        for (int sd = -1; sd <= 1; sd += 2) for (int j = -1; j <= 1; j += 2) parts.add(new float[]{sd * .62f * s, .66f * s, j * .23f * s, .46f * s, .9f * s, 0});
        for (int j = -1; j <= 1; j++) parts.add(new float[]{j * .37f * s, .85f * s, -.4f * s, .37f * s, 2.35f * s, 0});
        for (float[] q : parts) {
            double wx = q[0] * c + q[2] * sn, wz = -q[0] * sn + q[2] * c;
            ThronePart tp = new ThronePart(ThronePart.TYPE.get(), level());
            tp.setup(getUUID(), q[3], q[4], q[5] > 0);
            tp.setPos(getX() + wx, getY() + q[1], getZ() + wz);
            level().addFreshEntity(tp);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount == 2 && hasThrone()) spawnThrone();
        // if the block under the card is broken, the card drops as an item instead of floating
        if (!level().isClientSide && tickCount % 10 == 0 && level().getBlockState(blockPosition().below()).isAir() && level().getBlockState(blockPosition()).isAir()) {
            level().addFreshEntity(new ItemEntity(level(), getX(), getY() + .2, getZ(), Tarot.makeCard(pathway(), seq(), shiny())));
            discard();
        }
    }
}
