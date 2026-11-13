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
    private static final EntityDataAccessor<String> OWNERN = SynchedEntityData.defineId(HoloCardEntity.class, EntityDataSerializers.STRING);   // the one who put it down, so the scene can wear their face
    private UUID owner;
    private String realPath = ""; private int realSeq; private boolean realShiny;   // a disguised card (Marauder / Lord of Mysteries): what to give back
    public void setReal(String p, int sq, boolean sh) { realPath = p; realSeq = sq; realShiny = sh; }
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
    public void setOwner(Player p) { owner = p.getUUID(); ownerName = p.getGameProfile().getName(); entityData.set(OWNERN, ownerName); }
    /** The name of whoever put this card down, on the client too ("" if it is not known). */
    public String ownerName() { return entityData.get(OWNERN); }
    /** Index of the Great Old One this card is (0..8), or -1 for an ordinary card. */
    public int goo() { return entityData.get(GOO); }
    public String pathway() { if (goo() >= 0) return "goo_" + Tarot.GOO_IDS[goo()]; return Tarot.PATHWAYS[Math.max(0, Math.min(Tarot.PATHWAYS.length - 1, entityData.get(PATH)))]; }
    public int pathIndex() { return Math.max(0, Math.min(Tarot.PATHWAYS.length - 1, entityData.get(PATH))); }
    public int seq() { return entityData.get(SEQ); }
    public boolean shiny() { return entityData.get(SHINY); }
    /** Placed while crouching: only the pathway symbol is shown, no card and no hologram scene. */
    public boolean symbolOnly() { return entityData.get(SYMBOL); }
    public void setSymbolOnly(boolean b) { entityData.set(SYMBOL, b); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(PATH, 0); b.define(SEQ, 9); b.define(SHINY, false); b.define(SYMBOL, false); b.define(GOO, -1); b.define(OWNERN, ""); }

    @Override protected void readAdditionalSaveData(CompoundTag t) {
        setCard(t.getString("pathway"), t.getInt("seq"), t.getBoolean("shiny"));
        setSymbolOnly(t.getBoolean("symbolOnly"));
        owner = t.hasUUID("owner") ? t.getUUID("owner") : null; ownerName = t.getString("ownerName"); entityData.set(OWNERN, ownerName);
        realPath = t.getString("realPath"); realSeq = t.getInt("realSeq"); realShiny = t.getBoolean("realShiny");
    }
    @Override protected void addAdditionalSaveData(CompoundTag t) {
        t.putBoolean("symbolOnly", symbolOnly()); t.putString("pathway", pathway()); t.putInt("seq", seq()); t.putBoolean("shiny", shiny());
        if (owner != null) t.putUUID("owner", owner); t.putString("ownerName", ownerName);
        t.putString("realPath", realPath); t.putInt("realSeq", realSeq); t.putBoolean("realShiny", realShiny);
    }

    @Override public boolean isPickable() { return true; }
    @Override public float getPickRadius() { return 0.3f; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }
    @Override public void kill() { }                                          // not even an ability that kills outright takes a card
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
        ItemStack st = realPath.isEmpty() ? Tarot.makeCard(pathway(), seq(), shiny()) : Tarot.makeCard(realPath, realSeq, realShiny);
        if (!player.getInventory().add(st)) player.drop(st, false);
        level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.PLAYERS, .7f, 1.4f);
        discard();
        return InteractionResult.CONSUME;
    }

    /** Throne holograms (Death and Black Emperor, Seq 1 and 0) turn in quarter turns so their solid parts line up with the model. */
    public int throneQuarter() { return (int) Math.round((Math.PI - Math.toRadians(getYRot())) / (Math.PI / 2)); }
    public boolean hasThrone() { return !symbolOnly() && (goo() == 0 || goo() == 2 || goo() < 0 && seq() <= 1 && (pathIndex() == 5 || pathIndex() == 8 || pathIndex() == 18)); }

    /** Real, solid boxes matching HoloRenderer.throneModel (s = .95 * scale): three steps, seat, armrests and the back. */
    /** The Fool's hall: twenty-two seats you can sit in and the long table under the card, matching HoloRenderer.foolHall (local x right, z toward you). */
    /** How much bigger than a person the Fool's hall is; HoloRenderer.foolHall draws it with the same number. */
    public static final float HALL_K = 1.2f;
    /** Lord of Mysteries: seven real seats round the table (scale of one chair in S units, ring radius in S units, first angle) and the Fool at Seq 1: eight chairs round a round table. HoloRenderer draws from the same numbers. */
    public static final float LOM_CHAIR = .9f, LOM_RING = 4.35f, LOM_PHASE = .2f, LOM_S = 2.35f;
    public static final float F1_R = 9f, F1_S = 1.7f;
    /** Lord of Mysteries' long table, in blocks, in the quarter-turn frame (x right, the table running away along -z from the card): half width, half length, centre z, the side seats' x and spacing, the head seat's z. */
    public static final float LOM_TW = 1.9f, LOM_TL = 7.6f, LOM_TZC = -9.9f, LOM_SX = 3.3f, LOM_SZ = 4.7f, LOM_HEAD = 2.1f;
    /** Door Seq 0: six doors in a ring (radius as a share of the scene's 11 blocks, first angle); walking into one brings you out of another. */
    public static final float DOOR_RING = .72f, DOOR_PHASE = .26f;
    private final java.util.Map<java.util.UUID, Long> doorCd = new java.util.HashMap<>();
    /** The hall's layout in hall units (times HALL_K for blocks): table centre z and half length, half width, the side chairs' x and pitch. The renderer draws from the same numbers. */
    public static final float HALL_ZC = -13.75f, HALL_HL = 13.0f, HALL_W = 1.2f, HALL_SX = 2.0f, HALL_PITCH = 2.4f, HALL_Z0 = -3.25f, HALL_CHAIR = .9f, HALL_HEAD = .85f;
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
    /** The River of Eternal Darkness is a ring round the scene: its centre line's radius and its half width at an angle (the renderer draws the same river),
     *  the rowboat's angle at a game time (it sails the whole loop, on both sides alike), the rocks in it and the stone bridge across it at angle 0 (along +x). */
    public static final float RIV_R = 34f, RIV_HW = 5.5f, BRIDGE_ROW = 1.5f;
    public static float rivR(double th) { return RIV_R + 1.6f * (float) Math.sin(3 * th + .7); }
    public static float rivHW(double th) { return RIV_HW * (1f + .12f * (float) Math.sin(5 * th + 1)); }
    public static double boatTheta(double time) { return time * .0035; }
    private static float rnd(int k, int s) { double v = Math.sin(k * 127.1 + s * 311.7) * 43758.5453; return (float) (v - Math.floor(v)); }
    /** {angle, radius, size} */
    public static java.util.List<float[]> riverRocks() {
        java.util.List<float[]> l = new java.util.ArrayList<>();
        for (int k = 0; k < 14; k++) { double th = .5 + k * (Math.PI * 2 - 1.0) / 14 + rnd(k, 1) * .2; float sd = k % 2 == 0 ? 1 : -1; l.add(new float[]{(float) th, rivR(th) + sd * rivHW(th) * (.45f + .35f * rnd(k, 2)), .9f + 1.1f * rnd(k, 3)}); }   // near the banks, so the boat's channel stays clear
        return l;
    }
    /** {x, z, bottom, top}: two files of blocks, a low arch with a hollow under its middle for the boat. */
    public static java.util.List<float[]> bridgeRows() {
        java.util.List<float[]> rows = new java.util.ArrayList<>();
        float rc = rivR(0), half = rivHW(0) * 1.3f; int n = (int) Math.ceil(2 * half / BRIDGE_ROW);
        for (int j = 0; j < n; j++) {
            float x = rc - n * BRIDGE_ROW / 2f + (j + .5f) * BRIDGE_ROW, u = (x - rc) / (n * BRIDGE_ROW / 2f), top = .3f + (1f - u * u) * 1.0f, bot = Math.abs(u) < .62f ? top - .5f : 0f;
            for (int c = -1; c <= 1; c += 2) rows.add(new float[]{x, c * BRIDGE_ROW / 2, bot, top});
        }
        return rows;
    }
    private void spawnBridge() {
        for (float[] q : bridgeRows()) {
            ThronePart tp = new ThronePart(ThronePart.TYPE.get(), level());
            tp.setup(getUUID(), BRIDGE_ROW, q[3] - q[2], false);
            tp.setPos(getX() + q[0], getY() + q[2], getZ() + q[1]);
            level().addFreshEntity(tp);
        }
        for (float[] r : riverRocks()) {   // the rocks are solid
            ThronePart tp = new ThronePart(ThronePart.TYPE.get(), level());
            tp.setup(getUUID(), r[2] * 1.5f, r[2] * .62f, false);
            tp.setPos(getX() + Math.cos(r[0]) * r[1], getY(), getZ() + Math.sin(r[0]) * r[1]);
            level().addFreshEntity(tp);
        }
        for (float along : new float[]{1.25f, 0f, -1.25f}) {                   // the rowboat's three thwarts: real seats that sail with it
            ThronePart seat = new ThronePart(ThronePart.TYPE.get(), level());
            seat.setup(getUUID(), .8f, .5f, true);
            seat.setBoat(getX(), getY(), getZ(), along);
            level().addFreshEntity(seat);
        }
    }
    private void spawnThrone() {
        if (goo() == 2) { spawnBridge(); return; }
        if (goo() == 0) { spawnHall(); return; }   // the Lord of Mysteries keeps court in the same hall as the Fool
        if (pathIndex() == 18 && seq() == 1) {   // the round table (HoloRenderer.foolTable: radius R * .42, chairs 1.7 k beyond it, k = S * .6) with its eight seats, and the table top itself to walk on and lay things on
            float k = F1_S * .6f, tr0 = F1_R * .42f, cell = tr0 * 2 / 6;
            spawnRing(8, tr0 + 1.7f * k, (float) Math.PI / 8, k);
            for (int ix = 0; ix < 6; ix++) for (int iz = 0; iz < 6; iz++) {
                float x = -tr0 + cell * (ix + .5f), z = -tr0 + cell * (iz + .5f);
                if (x * x + z * z > (tr0 + cell * .2f) * (tr0 + cell * .2f)) continue;
                if (x * x + z * z < 1.3 * 1.3) continue;                       // the middle stays open: the card sits there
                ThronePart tp = new ThronePart(ThronePart.TYPE.get(), level());
                tp.setup(getUUID(), cell, .84f * k, false); tp.setTable();
                tp.setPos(getX() + x, getY(), getZ() + z);
                level().addFreshEntity(tp);
            }
            return;
        }
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

    /** Door Seq 0: the six doors of the ring, as {x, z} around this card. */
    private java.util.List<double[]> doorRing() {
        java.util.List<double[]> l = new java.util.ArrayList<>();
        double rr = 11.0 * DOOR_RING;
        for (int i = 0; i < 6; i++) { double a = i * Math.PI / 3 + DOOR_PHASE; l.add(new double[]{Math.cos(a) * rr, Math.sin(a) * rr}); }
        return l;
    }

    /** Lord of Mysteries: the ten doors standing in the fog, in the same places the scene draws them. */
    private java.util.List<double[]> lomDoors() {
        java.util.List<double[]> l = new java.util.ArrayList<>();
        int k = throneQuarter(); double c = Math.round(Math.cos(k * Math.PI / 2)), sn = Math.round(Math.sin(k * Math.PI / 2));
        double rr = 12.0 * 1.55;
        for (int i = 0; i < 10; i++) {
            double ang = (i % 2 == 0 ? 1 : -1) * (.42 + .58 * (i / 2)), lx = rr * Math.sin(ang), lz = -rr * Math.cos(ang);
            l.add(new double[]{lx * c + lz * sn, -lx * sn + lz * c});
        }
        return l;
    }

    /**
     * Walking into one of these doors brings you out of another, picked at random. You step out on the side you came from,
     * facing away from the door, so you are never set down behind it.
     */
    private void doorways(java.util.List<double[]> doors, boolean ring) {
        long now = level().getGameTime();
        double reach = 1.0;
        for (Player pl : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(40, 6, 40))) {
            Long cd = doorCd.get(pl.getUUID());
            if (cd != null && now < cd) continue;
            for (int i = 0; i < doors.size(); i++) {
                double dx = getX() + doors.get(i)[0], dz = getZ() + doors.get(i)[1];
                if (Math.abs(pl.getY() - getY()) > 4 || (pl.getX() - dx) * (pl.getX() - dx) + (pl.getZ() - dz) * (pl.getZ() - dz) > reach * reach) continue;
                int j = (i + 1 + random.nextInt(doors.size() - 1)) % doors.size();
                double ox = doors.get(j)[0], oz = doors.get(j)[1], ol = Math.sqrt(ox * ox + oz * oz);
                if (ol < 1e-3) continue;
                double inward = (pl.getX() - getX()) * (pl.getX() - getX()) + (pl.getZ() - getZ()) * (pl.getZ() - getZ()) < (doors.get(i)[0] * doors.get(i)[0] + doors.get(i)[1] * doors.get(i)[1]) ? -1 : 1;
                double sx = ox / ol * inward, sz = oz / ol * inward;            // the side of the far door you came from
                double tx = getX() + ox + sx * 1.8, tz = getZ() + oz + sz * 1.8;
                float yaw = (float) Math.toDegrees(Math.atan2(-sx, sz));         // looking away from the door you just stepped out of
                if (pl instanceof net.minecraft.server.level.ServerPlayer sp) sp.teleportTo((net.minecraft.server.level.ServerLevel) level(), tx, getY(), tz, yaw, pl.getXRot());
                level().playSound(null, tx, getY(), tz, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS, .7f, 1.5f);
                doorCd.put(pl.getUUID(), now + 30);
                break;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount == 2 && hasThrone()) spawnThrone();
        if (!level().isClientSide && !symbolOnly()) {
            if (goo() < 0 && pathIndex() == 1 && seq() == 0) doorways(doorRing(), true);
            else if (goo() == 0) doorways(lomDoors(), false);
        }
        // if the block under the card is broken, the card drops as an item instead of floating
        if (!level().isClientSide && tickCount % 10 == 0 && level().getBlockState(blockPosition().below()).isAir() && level().getBlockState(blockPosition()).isAir()) {
            level().addFreshEntity(new ItemEntity(level(), getX(), getY() + .2, getZ(), realPath.isEmpty() ? Tarot.makeCard(pathway(), seq(), shiny()) : Tarot.makeCard(realPath, realSeq, realShiny)));
            discard();
        }
    }
}
