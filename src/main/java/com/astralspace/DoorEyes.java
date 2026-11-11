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
 * The Door Uniqueness as a pair of eyes. It goes in the Uniqueness curios slot (or can simply be held); a keybind picks the
 * creature you are looking at and opens doors around it, fills the space with stars that swell into real stars, and they go supernova.
 */
public final class DoorEyes extends Uniqueness {   // (not a WornUniq itself: it has its own trigger, but WornUniq.press knows it)
    private static boolean inLunge;

    public DoorEyes() { super("apprentice", "Door", false, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)); }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tip, flag);
        tip.add(Component.literal("Wear it in the Uniqueness curio slot.").withStyle(ChatFormatting.AQUA));
        tip.add(Component.literal("Press the Door key while looking at a creature: doors open around it, stars flood in, swell, and go supernova.").withStyle(ChatFormatting.AQUA));
        tip.add(Component.literal("Costs " + UniqPower.cost(stack) + " spirituality. Seq 1/0 Door: you step 20 blocks clear first (sneak to step to the target instead), and the last blast lights a second, greater round of supernovas.").withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        if (p.isShiftKeyDown()) return super.use(level, p, hand);
        return InteractionResultHolder.pass(p.getItemInHand(hand));
    }

    // ------------------------------------------------------------------ equipped, aim, trigger

    /** The eyes if the player has them in a hand or in the Curios Uniqueness slot (Curios by reflection, so it stays optional). */
    public static ItemStack findEyes(LivingEntity p) {
        if (p.getMainHandItem().getItem() instanceof DoorEyes) return p.getMainHandItem();
        if (p.getOffhandItem().getItem() instanceof DoorEyes) return p.getOffhandItem();
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object opt = api.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, p);
            if (!(opt instanceof java.util.Optional<?> o) || o.isEmpty()) return ItemStack.EMPTY;
            Object h = o.get();
            Object eq = h.getClass().getMethod("getEquippedCurios").invoke(h);
            if (eq instanceof net.neoforged.neoforge.items.IItemHandlerModifiable m)
                for (int i = 0; i < m.getSlots(); i++) if (m.getStackInSlot(i).getItem() instanceof DoorEyes) return m.getStackInSlot(i);
        } catch (Throwable ignored) { }
        return ItemStack.EMPTY;
    }

    /** Keybind pressed: the creature in the crosshair gets the supernova. */
    public static void trigger(ServerPlayer sp) {
        ItemStack st = findEyes(sp);
        if (st.isEmpty()) return;
        if (sp.getCooldowns().isOnCooldown(st.getItem())) { sp.displayClientMessage(Component.literal("The Door is still closing...").withStyle(ChatFormatting.GRAY), true); return; }
        Vec3 eye = sp.getEyePosition(), end = eye.add(sp.getLookAngle().scale(48));
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(sp, eye, end, new net.minecraft.world.phys.AABB(eye, end).inflate(1.5),
            x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator() && x != sp, 48 * 48);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) { sp.displayClientMessage(Component.literal("No one in sight.").withStyle(ChatFormatting.GRAY), true); return; }
        int cost = UniqPower.cost(st);
        if (!UniqPower.pay(sp, cost)) { sp.displayClientMessage(Component.literal("Not enough spirituality (" + cost + ").").withStyle(ChatFormatting.GRAY), true); return; }
        boolean emp = UniqPower.emp(sp, "apprentice");
        if (emp) {   // a Seq 1/0 of the Door steps through one first: sneaking, to the target's side; otherwise to twenty blocks clear of it
            Vec3 away = sp.position().subtract(target.position()); away = new Vec3(away.x, 0, away.z);
            away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
            Vec3 to = target.position().add(away.scale(sp.isShiftKeyDown() ? 2.0 : 20.0));
            ServerLevel lv = sp.serverLevel();
            int top = lv.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(to.x), (int) Math.floor(to.z));
            double ty = Math.abs(top - target.getY()) <= 12 ? top : target.getY();
            lv.sendParticles(PartyItems.STAR.get(), sp.getX(), sp.getY() + 1, sp.getZ(), 40, .3, .6, .3, .05);
            sp.teleportTo(to.x, ty, to.z);
            sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            lv.sendParticles(PartyItems.STAR.get(), to.x, ty + 1, to.z, 40, .3, .6, .3, .05);
            lv.playSound(null, to.x, ty, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.3f);
        }
        Burst b = new Burst(sp.serverLevel(), sp.getUUID(), target); b.emp = emp;
        BURSTS.add(b);
        sp.getCooldowns().addCooldown(st.getItem(), BURST_COOLDOWN);
    }

    private static final int BURST_COOLDOWN = 300, OPEN = 16, FILL = 80, CHAIN = 24, FINAL = FILL + CHAIN + 2, DOORS = 11, AFTER = 34, NODES = 170;
    private static final double RING = 9.0, AREA = 16.0, DMG_RADIUS = 12.0;
    private static final float BURST_DAMAGE = 320f;
    private static final List<Burst> BURSTS = new java.util.ArrayList<>();

    private static final class Burst {
        final ServerLevel lv; final UUID owner; final LivingEntity target; final double[] ang = new double[DOORS], lift = new double[DOORS];
        boolean emp, second; double area = AREA, dmgR = DMG_RADIUS, boost = 1;
        Vec3 c; int age; final List<net.minecraft.world.entity.Entity> doors = new java.util.ArrayList<>();
        Burst(ServerLevel lv, UUID owner, LivingEntity t) {
            this.lv = lv; this.owner = owner; this.target = t; c = t.position().add(0, t.getBbHeight() * .5, 0);
            double off = lv.random.nextDouble() * 6.28;
            for (int i = 0; i < DOORS; i++) { ang[i] = off + i * 6.2832 / DOORS; lift[i] = (i % 3 == 0 ? -0.3 : i % 3 == 1 ? 0.0 : 0.8) + lv.random.nextDouble() * .3; }
            lv.playSound(null, c.x, c.y, c.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9f, 0.5f);
            lv.playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.6f, 1.4f);
            ServerPlayer sp = lv.getServer().getPlayerList().getPlayer(owner);
            if (sp != null) for (int i = 0; i < DOORS; i++) {   // real LOTM doors, if LOTM is installed
                double x = c.x + Math.cos(ang[i]) * RING, y = t.getY() + lift[i], z = c.z + Math.sin(ang[i]) * RING;
                net.minecraft.world.entity.Entity d = spawnDoor(lv, sp, x, y, z, (float) (Math.toDegrees(Math.atan2(Math.cos(ang[i]), Math.sin(ang[i]))) - 180.0));
                if (d != null) doors.add(d);
            }
        }
        void closeDoors() { for (var d : doors) try { d.getClass().getMethod("setClosing").invoke(d); } catch (Throwable ignored) { } }
        void clearDoors() { for (var d : doors) d.discard(); doors.clear(); }
        final List<Vec3> nodes = new java.util.ArrayList<>(); int[] fuse;
        static final int SEED = FILL - 28;
        /** Real stars bloom across the whole area and swell; each then goes supernova, one after another, rippling out from the middle. */
        void seedStars() {
            fuse = new int[NODES]; nodes.clear();
            for (int i = 0; i < NODES; i++) {
                double dx, dy, dz; do { dx = lv.random.nextDouble() * 2 - 1; dy = lv.random.nextDouble() * 2 - 1; dz = lv.random.nextDouble() * 2 - 1; } while (dx * dx + dy * dy + dz * dz > 1);
                Vec3 q = c.add(dx * area * .9, dy * area * .55 + .5, dz * area * .9); nodes.add(q);
                fuse[i] = Math.max(1, Math.min(CHAIN, (int) Math.round(q.distanceTo(c) / (area * 1.05) * CHAIN + lv.random.nextDouble() * 3)));
                double sz = 4 + lv.random.nextDouble() * 4;
                lv.sendParticles(PartyItems.STAR_CORE.get(), q.x, q.y, q.z, 0, sz, -1, (FILL - SEED) + fuse[i], 1);   // swells for exactly as long as its fuse
            }
            lv.sendParticles(PartyItems.STAR_CORE.get(), c.x, c.y, c.z, 0, 24, 0, (FINAL - SEED) - 1, 1);   // the big one in the middle
            if (target.isAlive()) target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 120, 6, false, false));
            lv.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, .6f);
        }
        void chain(int k) {
            int pops = 0;
            for (int i = 0; i < NODES; i++) if (fuse[i] == k) { supernova(nodes.get(i), .8 + lv.random.nextDouble() * .7); pops++; }
            if (pops > 0) lv.playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.6f, .6f + k * .03f);
            if (pops > 2) lv.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 1.2f, .5f);
        }
        /** One star going supernova: a spiky flash, the expanding shock shell, hot ejecta, and gas clouds left drifting behind. */
        void supernova(Vec3 q, double size) {
            lv.sendParticles(PartyItems.STAR_CORE.get(), q.x, q.y, q.z, 0, 18 * size, 4, -12, 1);                    // the nova flash, spikes and all
            lv.sendParticles(PartyItems.FLASH.get(), q.x, q.y, q.z, 0, 12 * size, 4, 9, 1);
            lv.sendParticles(PartyItems.RING.get(), q.x, q.y, q.z, 0, 24 * size, 2, 30, 1);                         // shock shell, hot orange
            lv.sendParticles(PartyItems.RING.get(), q.x, q.y, q.z, 0, 16 * size, 1, 22, 1);                       // inner shell, magenta
            lv.sendParticles(PartyItems.STAR.get(), q.x, q.y, q.z, 60, .2, .2, .2, .35);
            lv.sendParticles(PartyItems.STAR_BLUE.get(), q.x, q.y, q.z, 20, .15, .15, .15, .2);
            for (int i = 0; i < 2; i++) {   // the nebula it leaves
                lv.sendParticles(PartyItems.NEBULA.get(), q.x, q.y, q.z, 0, (lv.random.nextDouble() - .5) * .12, (lv.random.nextDouble() - .5) * .08, (lv.random.nextDouble() - .5) * .12, 1);
            }
        }
        double sc = 1;
        void star(double x, double y, double z, double dx, double dy, double dz, double speed) { lv.sendParticles(PartyItems.STAR.get(), x, y, z, 0, dx, dy, dz, speed * sc); }
        void blob(int n, double spread, double speed) { lv.sendParticles(PartyItems.STAR.get(), c.x, c.y, c.z, n, spread * sc, spread * sc, spread * sc, speed * sc); }

        /** returns true when finished */
        boolean tick() {
            if (age < FILL && target.isAlive() && target.level() == lv) c = target.position().add(0, target.getBbHeight() * .5, 0);
            age++;
            double grow = Math.min(1.0, age / (double) OPEN);
            for (int i = 0; i < DOORS && age < FILL; i++) {
                double a = ang[i];
                Vec3 pos = new Vec3(c.x + Math.cos(a) * RING, target.getY() + lift[i], c.z + Math.sin(a) * RING);
                Vec3 n = new Vec3(-Math.cos(a), 0, -Math.sin(a)), r = new Vec3(-n.z, 0, n.x);
                if (age % 3 == 1 && doors.isEmpty() && !second) {   // fallback doorway outline when LOTM is missing
                    for (int k = 0; k <= 18; k++) { double t = Math.PI * k / 18; fb(pos, r, .5 * Math.cos(t) * grow, (1.5 + .5 * Math.sin(t)) * grow); }
                    for (int k = 0; k <= 8; k++) { double v = 1.5 * grow * k / 8; fb(pos, r, -.5 * grow, v); fb(pos, r, .5 * grow, v); }
                }
                if (age > OPEN - 4 && !second) {   // a heavy stream of stars out of every door, towards the middle
                    Vec3 d = c.subtract(pos.add(0, 1, 0)).normalize();
                    for (int k = 0; k < 6; k++) {
                        double u = (lv.random.nextDouble() - .5) * .9, v = .1 + lv.random.nextDouble() * 1.9;
                        Vec3 q = pos.add(r.scale(u)).add(0, v, 0).add(n.scale(.15));
                        star(q.x, q.y, q.z, d.x, d.y, d.z, RING * .085 + lv.random.nextDouble() * RING * .02);
                    }
                }
            }
            if (age > OPEN && age < FILL) {   // the whole area fills up and thickens
                int cnt = 12 + (age - OPEN) * 2;
                lv.sendParticles(PartyItems.STAR.get(), c.x, c.y + 1, c.z, cnt, area * .55, area * .35, area * .55, 0.004);
                if (age % 5 == 0) lv.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 0.7f + (age - OPEN) * .012f);
            }
            if (age == SEED) seedStars();
            if (age > FILL && age <= FILL + CHAIN) chain(age - FILL);
            if (age >= FINAL - 7 && age < FINAL) implode(FINAL - age);
            if (age >= FINAL) sc = 2.2 * boost;
            if (age == FINAL) { explode(); closeDoors(); }
            if (age > FINAL) aftershock();
            if (age == FINAL + 10) clearDoors();
            if (age > FINAL + AFTER && emp && !second) {   // the last blast lights a second round: more room, bigger stars, a greater end
                second = true; area = AREA * 1.6; dmgR = DMG_RADIUS * 1.6; boost = 1.5; sc = 1; age = SEED - 1;
                lv.playSound(null, c.x, c.y, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.2f, .7f);
                return false;
            }
            return age > FINAL + AFTER;
        }
        void fb(Vec3 pos, Vec3 r, double u, double v) { lv.sendParticles(PartyItems.STAR.get(), pos.x + r.x * u, pos.y + v, pos.z + r.z * u, 1, 0, 0, 0, 0); }

        void sphere(int n, double speed) {   // evenly spread directions: a clean shell
            for (int i = 0; i < n; i++) {
                double y = 1 - 2 * (i + .5) / n, rr = Math.sqrt(1 - y * y), th = i * 2.399963;
                star(c.x, c.y, c.z, Math.cos(th) * rr, y, Math.sin(th) * rr, speed);
            }
        }
        void ring(int n, double speed, double tilt, double spin) {   // a flat ring of stars, tilted and turned
            for (int i = 0; i < n; i++) {
                double th = i * 6.2832 / n + spin, dx = Math.cos(th), dz = Math.sin(th);
                star(c.x, c.y, c.z, dx, -dz * Math.sin(tilt), dz * Math.cos(tilt), speed);
            }
        }
        /** The breath before the blast: everything is drawn in to a point and the light goes with it. */
        void implode(int left) {
            double r = area * (.25 + .12 * left);
            for (int i = 0; i < 46; i++) {
                double y = 1 - 2 * (i + .5) / 46, rr = Math.sqrt(1 - y * y), th = i * 2.399963 + left;
                lv.sendParticles(PartyItems.STAR.get(), c.x + Math.cos(th) * rr * r, c.y + y * r * .7, c.z + Math.sin(th) * rr * r, 0, -Math.cos(th) * rr, -y * .7, -Math.sin(th) * rr, r * .16);
            }
            if (left == 7) lv.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2f, 1.6f);
        }
        /** A pillar of stars through the heart of it, and the disc of the blast across the ground. */
        void pillarAndDisc() {
            for (int i = 0; i < 120; i++) {
                double up = (lv.random.nextDouble() * 2 - 1), j = .05;
                star(c.x + (lv.random.nextDouble() - .5) * .8, c.y, c.z + (lv.random.nextDouble() - .5) * .8, (lv.random.nextDouble() - .5) * j, Math.signum(up), (lv.random.nextDouble() - .5) * j, .5 + Math.abs(up) * 1.4);
            }
            ring(180, 1.25, 0, 0); ring(140, .95, 0, .4); ring(100, .7, 0, .8);
            lv.sendParticles(PartyItems.RING.get(), c.x, c.y, c.z, 0, 230 * boost, 0, 56, 1);
            lv.sendParticles(PartyItems.FLASH.get(), c.x, c.y, c.z, 0, 110 * boost, 4, 12, 1);
        }
        void explode() {
            pillarAndDisc();
            ServerPlayer sp = lv.getServer().getPlayerList().getPlayer(owner);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(lv, null, c.x, c.y, c.z, 96.0, new Net.FlashPayload(255, 3500));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(lv, null, c.x, c.y, c.z, 400.0, new DoorNet.Nova(c.x, c.y - 1.0, c.z, (float) boost));
            lv.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2f, 0.7f);
            lv.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2f, 1.5f);
            lv.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 2f, 0.5f);
            lv.playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 2f, 0.6f);
            lv.sendParticles(PartyItems.RING.get(), c.x, c.y, c.z, 0, 70, 4, 24, 1);           // shock shells: blue-white, orange, magenta, teal, bigger and slower
            lv.sendParticles(PartyItems.RING.get(), c.x, c.y, c.z, 0, 105, 2, 32, 1);
            lv.sendParticles(PartyItems.RING.get(), c.x, c.y, c.z, 0, 140, 1, 40, 1);
            lv.sendParticles(PartyItems.RING.get(), c.x, c.y, c.z, 0, 180, 5, 48, 1);
            for (int i = 0; i < 280; i++) {   // the remnant: a nebula of coloured gas thrown across the whole area
                double y = lv.random.nextDouble() * 2 - 1, rr = Math.sqrt(1 - y * y), th = lv.random.nextDouble() * 6.2832, sp2 = .15 + lv.random.nextDouble() * .5;
                lv.sendParticles(PartyItems.NEBULA.get(), c.x, c.y, c.z, 0, Math.cos(th) * rr * sp2, y * sp2 * .8, Math.sin(th) * rr * sp2, 1);
            }
            blob(260, .2, .05);          // blinding core
            blob(500, .3, .55);          // thick cloud of stars
            sphere(160, .9); sphere(110, .6); sphere(70, .35); sphere(40, .16);
            for (int i = 0; i < 5; i++) ring(70, .5 + i * .12, (i - 2) * .55, i * 1.3);   // five crossed shockwaves
            inLunge = true;
            try {
                for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(dmgR), x -> x.isAlive() && (sp == null || x != sp))) {
                    if (e.position().add(0, e.getBbHeight() * .5, 0).distanceTo(c) > dmgR) continue;
                    if (e.hurt(sp != null ? lv.damageSources().playerAttack(sp) : lv.damageSources().magic(), BURST_DAMAGE * (second ? 1.5f : 1f))) {
                        Vec3 away = e.position().subtract(c.x, e.getY(), c.z); if (away.lengthSqr() < 1e-4) away = new Vec3(0, 0, 1);
                        away = away.normalize(); e.knockback(1.8, -away.x, -away.z); e.setDeltaMovement(e.getDeltaMovement().add(0, .5, 0)); e.hurtMarked = true;
                    }
                }
            } finally { inLunge = false; }
        }
        /** The explosion keeps going: a pillar of stars, pinwheels, rolling shockwaves and a second, bigger blast. */
        void aftershock() {
            int k = age - FINAL;
            if (k <= 16) {   // two jets of stars blasting out of the poles, like a real supernova
                Vec3 ax = new Vec3(.25, 1, .15).normalize();
                for (int i = 0; i < 14; i++) {
                    double sp3 = .6 + lv.random.nextDouble() * .7, j = (lv.random.nextDouble() - .5) * .08;
                    star(c.x, c.y, c.z, ax.x + j, ax.y, ax.z + j, sp3);
                    star(c.x, c.y, c.z, -ax.x + j, -ax.y, -ax.z + j, sp3);
                }
            }
            if (k >= 1 && k <= 16) {   // eight spinning pinwheel arms
                for (int a = 0; a < 8; a++) {
                    double th = a * .7854 + k * .4;
                    star(c.x, c.y, c.z, Math.cos(th), .15 * Math.sin(k + a), Math.sin(th), .4 + .03 * k);
                }
            }
            if (k % 3 == 2 && k <= 20) { ring(80, .4 + k * .03, ((k / 3) % 3 - 1) * .5, k); blob(150, .3, .3); }
            if (k == 8) { lv.sendParticles(PartyItems.FLASH.get(), c.x, c.y, c.z, 0, 60, -1, 16, 1); lv.sendParticles(PartyItems.RING.get(), c.x, c.y, c.z, 0, 90, 3, 28, 1); lv.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2f, 1.9f); sphere(120, .7); blob(300, .3, .45); }
            if (k == 14) { lv.sendParticles(PartyItems.FLASH.get(), c.x, c.y, c.z, 0, 80, 6, 18, 1); lv.sendParticles(PartyItems.RING.get(), c.x, c.y, c.z, 0, 130, 0, 36, 1); lv.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5f, 1.2f); sphere(140, 1.0); blob(400, .4, .65); }
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
            try {   // TELEPORT_ONLY does nothing unless someone sneaks into it (the default mirage mode teleports its creator)
                for (var m : doorCls.getMethods()) if (m.getName().equals("setDoorMode") && m.getParameterCount() == 1) {
                    for (Object k : m.getParameterTypes()[0].getEnumConstants()) if (k.toString().equals("TELEPORT_ONLY")) { m.invoke(e, k); break; }
                }
            } catch (Throwable ignored) { }
            try {   // the door must not teleport anyone: clear its "free to use" flag
                var f = doorCls.getDeclaredField("FREE_TO_USE"); f.setAccessible(true);
                @SuppressWarnings("unchecked") var acc = (net.minecraft.network.syncher.EntityDataAccessor<Boolean>) f.get(null);
                e.getEntityData().set(acc, false);
            } catch (Throwable ignored) { }
            try { doorCls.getMethod("keepBack", UUID.class).invoke(e, owner.getUUID()); } catch (Throwable ignored) { }
            try {   // long life, upright
                for (String f : new String[]{"LIFE", "FULL_LIFE"}) {
                    var fl = doorCls.getDeclaredField(f); fl.setAccessible(true);
                    @SuppressWarnings("unchecked") var ac = (net.minecraft.network.syncher.EntityDataAccessor<Integer>) fl.get(null);
                    e.getEntityData().set(ac, 600);
                }
                doorCls.getMethod("setPitch", float.class).invoke(e, 0f);
                doorCls.getMethod("setYaw", float.class).invoke(e, yaw);
            } catch (Throwable ignored) { }
            e.moveTo(x, y, z, 0, 0);
            lv.addFreshEntity(e);
            return e;
        } catch (Throwable t) { doorCls = null; return null; }
    }

    public static void tickBursts(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        if (BURSTS.isEmpty()) return;
        BURSTS.removeIf(Burst::tick);
    }
}
