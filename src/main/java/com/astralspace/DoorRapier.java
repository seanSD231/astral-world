package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Door Uniqueness as a rapier: quick, long reach, light hits. Three hits in a row inside 1.5s build a combo
 * (x1.0, x1.25, x1.7). Right click on an entity opens doors around it, fills the space with stars, then bursts (8s cooldown). Sneak + hold right click still
 * takes the Uniqueness in. Test build: numbers are guesses and need playing with.
 */
public final class DoorRapier extends Uniqueness {
    private static final ResourceLocation REACH_ID = ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "door_rapier_reach");
    public static final SoundEvent THRUST = SoundEvents.PLAYER_ATTACK_SWEEP, LUNGE = SoundEvents.ENDERMAN_TELEPORT, HIT = SoundEvents.AMETHYST_BLOCK_CHIME, COMBO = SoundEvents.AMETHYST_BLOCK_RESONATE;
    private static final int COMBO_WINDOW = 30, LUNGE_COOLDOWN = 50;
    private static final Map<UUID, long[]> COMBOS = new ConcurrentHashMap<>();   // uuid -> {last hit tick, hits so far}
    private static boolean inLunge;

    private static Item.Properties props() {
        return new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).attributes(ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 2.5, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -0.8, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_ID, 0.9, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .build());
    }

    public DoorRapier() { super("apprentice", "Door", false, props()); }

    @Override
    public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(DoorClient.EXT);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tip, flag);
        tip.add(Component.literal("Fast, long reach, light hits. Three quick hits build a combo.").withStyle(ChatFormatting.AQUA));
        tip.add(Component.literal("Right click an enemy: doors open around it, stars flood in, then burst.").withStyle(ChatFormatting.AQUA));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        if (p.isShiftKeyDown()) return super.use(level, p, hand);
        return InteractionResultHolder.pass(p.getItemInHand(hand));
    }

    /** Right click an entity: doors open around it, stars pour out and fill the space, then burst. */
    @Override
    public net.minecraft.world.InteractionResult interactLivingEntity(ItemStack st, Player p, LivingEntity target, InteractionHand hand) {
        if (p.isShiftKeyDown() || target == p) return net.minecraft.world.InteractionResult.PASS;
        if (p.getCooldowns().isOnCooldown(this)) return net.minecraft.world.InteractionResult.FAIL;
        p.swing(hand);
        if (!p.level().isClientSide && p instanceof ServerPlayer sp) {
            BURSTS.add(new Burst(sp.serverLevel(), sp.getUUID(), target));
            sp.getCooldowns().addCooldown(this, BURST_COOLDOWN);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(p.level().isClientSide);
    }

    private static final int BURST_COOLDOWN = 160, OPEN = 12, FILL = 42, DOORS = 7;
    private static final double RING = 3.0, AREA = 3.6;
    private static final float BURST_DAMAGE = 14f;
    private static final List<Burst> BURSTS = new java.util.ArrayList<>();

    private static final class Burst {
        final ServerLevel lv; final UUID owner; final LivingEntity target; final double[] ang = new double[DOORS], lift = new double[DOORS];
        Vec3 c; int age; final List<net.minecraft.world.entity.Entity> doors = new java.util.ArrayList<>();
        Burst(ServerLevel lv, UUID owner, LivingEntity t) {
            this.lv = lv; this.owner = owner; this.target = t; c = t.position().add(0, t.getBbHeight() * .5, 0);
            double off = lv.random.nextDouble() * 6.28;
            for (int i = 0; i < DOORS; i++) { ang[i] = off + i * 6.2832 / DOORS; lift[i] = (i % 3 == 0 ? -0.3 : i % 3 == 1 ? 0.0 : 0.6) + lv.random.nextDouble() * .3; }
            lv.playSound(null, c.x, c.y, c.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 0.6f);
            ServerPlayer sp = lv.getServer().getPlayerList().getPlayer(owner);
            if (sp != null) for (int i = 0; i < DOORS; i++) {   // real LOTM doors, if LOTM is installed
                double x = c.x + Math.cos(ang[i]) * RING, y = t.getY() + lift[i], z = c.z + Math.sin(ang[i]) * RING;
                net.minecraft.world.entity.Entity d = spawnDoor(lv, sp, x, y, z, (float) Math.toDegrees(Math.atan2(Math.cos(ang[i]), -Math.sin(ang[i]))));
                if (d != null) doors.add(d);
            }
        }
        void clearDoors() { for (var d : doors) d.discard(); doors.clear(); }
        /** returns true when finished */
        boolean tick() {
            if (target.isAlive() && target.level() == lv) c = target.position().add(0, target.getBbHeight() * .5, 0);
            age++;
            double grow = Math.min(1.0, age / (double) OPEN);
            boolean redraw = age % 3 == 1;
            for (int i = 0; i < DOORS; i++) {
                double a = ang[i];
                Vec3 pos = c.add(Math.cos(a) * RING, -c.y + target.getY() + lift[i], Math.sin(a) * RING).add(0, 0, 0);
                pos = new Vec3(c.x + Math.cos(a) * RING, target.getY() + lift[i], c.z + Math.sin(a) * RING);
                Vec3 n = new Vec3(-Math.cos(a), 0, -Math.sin(a)), r = new Vec3(-n.z, 0, n.x);
                if (redraw && doors.isEmpty()) {
                    int pts = 18;
                    for (int k = 0; k <= pts; k++) {   // arched doorway outline, drawn in as it grows
                        double t = Math.PI * k / pts, u = .5 * Math.cos(t) * grow, v = (1.5 + .5 * Math.sin(t)) * grow;
                        door(pos, r, u, v);
                    }
                    for (int k = 0; k <= 8; k++) { double v = 1.5 * grow * k / 8; door(pos, r, -.5 * grow, v); door(pos, r, .5 * grow, v); }
                    lv.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + grow, pos.z, 2, .25, .6, .25, .02);
                }
                if (age > OPEN - 4 && age < FILL) {   // stars pour out of the door towards the middle
                    Vec3 d = c.subtract(pos.add(0, 1, 0)).normalize();
                    for (int k = 0; k < 2; k++) {
                        double u = (lv.random.nextDouble() - .5) * .8, v = .2 + lv.random.nextDouble() * 1.6;
                        Vec3 q = pos.add(r.scale(u)).add(0, v, 0).add(n.scale(.15));
                        lv.sendParticles(PartyItems.STAR.get(), q.x, q.y, q.z, 0, d.x, d.y, d.z, .26 + lv.random.nextDouble() * .08);
                    }
                }
            }
            if (age > OPEN && age < FILL) {   // the space fills with stars
                int n = 3 + (age - OPEN) / 4;
                for (int k = 0; k < n; k++) {
                    double dx, dy, dz; do { dx = lv.random.nextDouble() * 2 - 1; dy = lv.random.nextDouble() * 2 - 1; dz = lv.random.nextDouble() * 2 - 1; } while (dx * dx + dy * dy + dz * dz > 1);
                    lv.sendParticles(PartyItems.STAR.get(), c.x + dx * AREA, c.y + dy * AREA * .6, c.z + dz * AREA, 1, 0, 0, 0, 0.004);
                }
                if (age % 6 == 0) lv.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 0.8f + (age - OPEN) * .02f);
            }
            if (age >= FILL - 8 && age < FILL) {   // everything is pulled in just before the bang
                for (int i = 0; i < 14; i++) {
                    double y = lv.random.nextDouble() * 2 - 1, rr = Math.sqrt(1 - y * y), th = lv.random.nextDouble() * 6.2832;
                    Vec3 d = new Vec3(Math.cos(th) * rr, y, Math.sin(th) * rr);
                    lv.sendParticles(PartyItems.STAR.get(), c.x + d.x * 3.2, c.y + d.y * 3.2, c.z + d.z * 3.2, 0, -d.x, -d.y, -d.z, .3);
                }
            }
            if (age == FILL) { explode(); clearDoors(); }
            if (age > FILL) aftershock();
            return age > FILL + 8;
        }
        void door(Vec3 pos, Vec3 r, double u, double v) {
            lv.sendParticles(PartyItems.STAR.get(), pos.x + r.x * u, pos.y + v, pos.z + r.z * u, 1, 0, 0, 0, 0);
        }
        void sphere(int n, double speed, double r0) {   // evenly spread directions, so it reads as a clean shell
            for (int i = 0; i < n; i++) {
                double y = 1 - 2 * (i + .5) / n, rr = Math.sqrt(1 - y * y), th = i * 2.399963;
                lv.sendParticles(PartyItems.STAR.get(), c.x + Math.cos(th) * rr * r0, c.y + y * r0, c.z + Math.sin(th) * rr * r0, 0, Math.cos(th) * rr, y, Math.sin(th) * rr, speed);
            }
        }
        void ring(int n, double speed, double tilt) {
            for (int i = 0; i < n; i++) {
                double th = i * 6.2832 / n, dx = Math.cos(th), dz = Math.sin(th);
                lv.sendParticles(PartyItems.STAR.get(), c.x, c.y + .1, c.z, 0, dx * Math.cos(tilt), Math.sin(tilt) * (i % 2 == 0 ? 1 : -1), dz * Math.cos(tilt), speed);
            }
        }
        void explode() {
            ServerPlayer sp = lv.getServer().getPlayerList().getPlayer(owner);
            lv.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.5f, 0.6f);
            lv.playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.2f, 0.8f);
            lv.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.6f);
            lv.sendParticles(PartyItems.STAR.get(), c.x, c.y, c.z, 60, .15, .15, .15, .03);   // dense bright core
            sphere(110, .5, 0.2);
            sphere(60, .3, 0.2);
            sphere(30, .14, 0.2);
            inLunge = true;
            try {
                for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(AREA), x -> x.isAlive() && (sp == null || x != sp))) {
                    if (e.position().add(0, e.getBbHeight() * .5, 0).distanceTo(c) > AREA) continue;
                    if (e.hurt(sp != null ? lv.damageSources().playerAttack(sp) : lv.damageSources().magic(), BURST_DAMAGE)) {
                        Vec3 away = e.position().subtract(c.x, e.getY(), c.z); if (away.lengthSqr() < 1e-4) away = new Vec3(0, 0, 1);
                        away = away.normalize(); e.knockback(0.9, -away.x, -away.z);
                    }
                }
            } finally { inLunge = false; }
        }
        /** Shockwave rings that keep rolling out for a few ticks after the bang. */
        void aftershock() {
            int k = age - FILL;
            if (k >= 1 && k <= 7) {   // pinwheel arms spinning out of the middle
                for (int a = 0; a < 5; a++) {
                    double th = a * 1.2566 + k * .55;
                    lv.sendParticles(PartyItems.STAR.get(), c.x, c.y, c.z, 0, Math.cos(th), .12 * Math.sin(k + a), Math.sin(th), .33 + .04 * k);
                }
            }
            if (k == 3) for (int i = 0; i < 24; i++) lv.sendParticles(PartyItems.STAR.get(), c.x, c.y, c.z, 0, (lv.random.nextDouble() - .5) * .5, 1, (lv.random.nextDouble() - .5) * .5, .25 + lv.random.nextDouble() * .25);
            if (k == 2) ring(48, .45, 0);
            if (k == 4) { ring(40, .36, .5); sphere(50, .15, 0.2); lv.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.9f); }
            if (k == 6) ring(32, .28, -.5);
        }
    }

    private static Class<?> doorCls;
    private static boolean doorTried;
    /** Spawns LOTM's own ApprenticeDoorEntity by reflection (same 7-arg constructor in every LOTM build I have); null if LOTM is missing. */
    private static net.minecraft.world.entity.Entity spawnDoor(ServerLevel lv, ServerPlayer owner, double x, double y, double z, float yaw) {
        try {
            if (!doorTried) {
                doorTried = true;
                for (String n : new String[]{"net.swimmingtuna.lotm.pathway.apprentice.entities.ApprenticeDoorEntity", "net.swimmingtuna.lotm.entity.ApprenticeDoorEntity"}) {
                    try { doorCls = Class.forName(n); break; } catch (Throwable ignored) { }
                }
            }
            if (doorCls == null) return null;
            // (Level, LivingEntity creator, int sequence, float yaw, float destX, destY, destZ): destination is the door itself
            var e = (net.minecraft.world.entity.Entity) doorCls.getConstructor(Level.class, LivingEntity.class, int.class, float.class, float.class, float.class, float.class)
                .newInstance(lv, owner, 9, yaw, (float) x, (float) y, (float) z);
            try {   // the door must not teleport anyone: clear its "free to use" flag
                var f = doorCls.getDeclaredField("FREE_TO_USE"); f.setAccessible(true);
                @SuppressWarnings("unchecked") var acc = (net.minecraft.network.syncher.EntityDataAccessor<Boolean>) f.get(null);
                e.getEntityData().set(acc, false);
            } catch (Throwable ignored) { }
            try { doorCls.getMethod("keepBack", UUID.class).invoke(e, owner.getUUID()); } catch (Throwable ignored) { }
            e.moveTo(x, y, z, yaw, 0);
            lv.addFreshEntity(e);
            return e;
        } catch (Throwable t) { doorCls = null; return null; }
    }

    public static void tickBursts(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        if (BURSTS.isEmpty()) return;
        BURSTS.removeIf(Burst::tick);
    }

    /** Combo: three hits inside the window each hit harder; the third finishes with a chime and a burst. */
    public static void onHurt(LivingIncomingDamageEvent e) {
        if (inLunge || !(e.getSource().getEntity() instanceof ServerPlayer sp) || e.getSource().getDirectEntity() != sp) return;
        if (!(sp.getMainHandItem().getItem() instanceof DoorRapier)) return;
        ServerLevel lv = sp.serverLevel(); long now = lv.getGameTime();
        long[] st = COMBOS.computeIfAbsent(sp.getUUID(), k -> new long[]{-999, 0});
        st[1] = (now - st[0] <= COMBO_WINDOW) ? st[1] + 1 : 1; st[0] = now;
        int hit = (int) Math.min(3, st[1]);
        float mult = hit == 1 ? 1.0f : hit == 2 ? 1.25f : 1.7f;
        e.setAmount(e.getAmount() * mult);
        LivingEntity t = e.getEntity(); Vec3 c = t.position().add(0, t.getBbHeight() * .6, 0);
        lv.playSound(null, c.x, c.y, c.z, hit == 3 ? COMBO : HIT, SoundSource.PLAYERS, hit == 3 ? 1f : 0.7f, hit == 3 ? 1.8f : 1.2f + 0.2f * hit);
        lv.sendParticles(PartyItems.STAR.get(), c.x, c.y, c.z, hit == 3 ? 16 : 6, .3, .3, .3, hit == 3 ? .07 : .04);
        sp.displayClientMessage(Component.literal("✦".repeat(hit)).withStyle(hit == 3 ? ChatFormatting.AQUA : ChatFormatting.BLUE), true);
        if (hit == 3) st[1] = 0;
    }
}
