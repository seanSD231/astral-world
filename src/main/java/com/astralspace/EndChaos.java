package com.astralspace;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stage 5, the end. Time since the stage began (real time, so it survives a restart):
 *   0 s    "The end is here" (Stage shows it)
 *   10 s   all sound is switched off on every client
 *   12 s   chaos: meteors, lightning, explosions, LOTM disasters; anything that touches a player kills them at once,
 *          and every Beyonder is corrupted rapidly
 * A player who dies is kicked ("Earth has fallen to unknown forces"). When everyone is gone the world goes back to
 * stage 0 (barrier full again) and the server stops.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class EndChaos {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MUTE_AFTER_MS = 10_000L, CHAOS_AFTER_MS = 12_000L;
    private static final String KICK = "Earth has fallen to unknown forces";

    /** Something that kills a player who comes within `radius` of it. Follows an entity, or stays at a spot. */
    private static final class Hazard {
        final ServerLevel level; final Entity entity; Vec3 pos; final double radius; final long until;
        Hazard(ServerLevel level, Entity entity, Vec3 pos, double radius, long until) {
            this.level = level; this.entity = entity; this.pos = pos; this.radius = radius; this.until = until;
        }
    }

    private static final List<Hazard> hazards = new ArrayList<>();
    private static final Set<UUID> fallen = new HashSet<>();
    private static final Set<UUID> mutedSent = new HashSet<>();
    private static String meteorId = null;
    private static boolean meteorChecked = false;
    private static int second = 0;

    private EndChaos() {}

    // ------------------------------------------------------------------ sound

    /** Gives every client its sound back (the stage is no longer 5). */
    static void unmuteAll(MinecraftServer server) {
        hazards.clear();
        fallen.clear();
        if (mutedSent.isEmpty()) return;
        mutedSent.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(p, new Net.MutePayload(false));
    }

    private static boolean chaosOn(AstralData d) { return d.stage == 5 && System.currentTimeMillis() - d.stageSince >= CHAOS_AFTER_MS; }

    // ------------------------------------------------------------------ players dying, joining

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        MinecraftServer server = p.getServer();
        if (server == null || !EndGame.classic(AstralData.get(server)) || EndGame.isOp(p)) return;
        fallen.add(p.getUUID());
        server.execute(() -> kick(p));
    }

    private static void kick(ServerPlayer p) {
        if (p.connection != null) p.connection.disconnect(Component.literal(KICK));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        MinecraftServer server = p.getServer();
        if (server == null || !EndGame.classic(AstralData.get(server)) || EndGame.isOp(p)) return;
        if (fallen.contains(p.getUUID())) server.execute(() -> kick(p)); // no coming back in while the end is still going
    }

    // ------------------------------------------------------------------ the end

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        AstralData d = AstralData.get(server);
        if (!EndGame.chaosPhase(d)) {
            if (!mutedSent.isEmpty()) unmuteAll(server);
            return;
        }
        boolean classic = EndGame.classic(d);
        long el = System.currentTimeMillis() - d.stageSince;
        long tick = server.getTickCount();

        if (el >= MUTE_AFTER_MS && tick % 10 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                boolean want = EndGame.inDanger(p, d); // on another planet there is sound again
                if (want && mutedSent.add(p.getUUID())) PacketDistributor.sendToPlayer(p, new Net.MutePayload(true));
                else if (!want && mutedSent.remove(p.getUUID())) PacketDistributor.sendToPlayer(p, new Net.MutePayload(false));
            }
        }
        if (el < CHAOS_AFTER_MS) return;

        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        boolean anyNonOp = false;
        for (ServerPlayer p : players) if (!EndGame.isOp(p)) anyNonOp = true;
        if (anyNonOp && !d.stage5Seen) { d.stage5Seen = true; d.setDirty(); }

        // anything touching a player kills them
        checkHazards(server, players, tick);

        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (ServerPlayer p : players) {
            if (p.isRemoved() || !p.isAlive() || p.isSpectator() || !EndGame.inDanger(p, d)) continue;
            ServerLevel lv = p.serverLevel();
            if (tick % 3 == 0) lightning(lv, p, r);
            if (tick % 10 == 0) meteor(lv, p, r);
            if (tick % 30 == 0) explosion(lv, p, r);
            if (tick % 80 == (p.getId() % 80)) disaster(lv, p, r);
            if (tick % 20 == 0) {
                if (Lotm.sequence(p) >= 0) Lotm.addCorruptionPercent(p, Lotm.randomPathway(), 12); // rapid corruption
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
                p.setRemainingFireTicks(Math.max(p.getRemainingFireTicks(), 60));
            }
        }

        // once a second: has everyone gone?
        if (tick % 20 == 0) {
            if (classic && !anyNonOp && d.stage5Seen) finish(server, d);
        }
    }

    private static void finish(MinecraftServer server, AstralData d) {
        LOGGER.info("[astral] the end is over: resetting to stage 0; the server closes once the operators leave");
        d.hp = AstralBarrier.getMax(server); // a new world starts with a whole barrier
        Stage.set(server, 0);
        d.setDirty();
        server.overworld().getDataStorage().save();
        EndGame.beginClose(server, 0, "Earth has fallen to unknown forces");
    }

    // ------------------------------------------------------------------ killing

    private static void checkHazards(MinecraftServer server, List<ServerPlayer> players, long tick) {
        Iterator<Hazard> it = hazards.iterator();
        while (it.hasNext()) {
            Hazard h = it.next();
            boolean gone = h.entity != null && h.entity.isRemoved();
            if (h.entity != null && !gone) h.pos = h.entity.position();
            for (ServerPlayer p : players) {
                if (!p.isAlive() || p.level() != h.level) continue;
                if (p.position().add(0, p.getBbHeight() / 2, 0).distanceToSqr(h.pos) <= h.radius * h.radius) kill(p);
            }
            if (gone || tick >= h.until) it.remove();
        }
    }

    static void kill(ServerPlayer p) {
        p.hurt(p.serverLevel().damageSources().genericKill(), Float.MAX_VALUE);
        if (p.isAlive()) p.kill();
    }

    // ------------------------------------------------------------------ the disasters

    private static Vec3 around(ServerPlayer p, double spread, ThreadLocalRandom r) {
        return new Vec3(p.getX() + (r.nextDouble() * 2 - 1) * spread, p.getY(), p.getZ() + (r.nextDouble() * 2 - 1) * spread);
    }

    private static void lightning(ServerLevel lv, ServerPlayer p, ThreadLocalRandom r) {
        Vec3 at = r.nextInt(6) == 0 ? p.position() : around(p, 28, r); // now and then straight onto the player
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(lv);
        if (bolt == null) return;
        bolt.moveTo(at.x, lv.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) at.x, (int) at.z), at.z);
        lv.addFreshEntity(bolt);
        hazards.add(new Hazard(lv, null, new Vec3(bolt.getX(), bolt.getY() + 1, bolt.getZ()), 3.5, 0));
    }

    private static void meteor(ServerLevel lv, ServerPlayer p, ThreadLocalRandom r) {
        Vec3 at = around(p, 34, r);
        double y = p.getY() + 70;
        Vec3 motion = new Vec3((r.nextDouble() - 0.5) * 0.6, -1.6, (r.nextDouble() - 0.5) * 0.6);
        Entity e = null;
        String id = meteorId();
        if (id != null) e = Lotm.spawn(lv, id, at.x, y, at.z, motion, 200, p, 2.0f);
        if (e == null) { // LOTM's meteor not found: a ghast fireball does the same job
            LargeFireball f = new LargeFireball(lv, p, motion, 4);
            f.setPos(at.x, y, at.z);
            f.setDeltaMovement(motion);
            lv.addFreshEntity(f);
            e = f;
        }
        hazards.add(new Hazard(lv, e, new Vec3(at.x, y, at.z), 6.0, (long) lv.getServer().getTickCount() + 220));
    }

    private static String meteorId() {
        if (!meteorChecked) {
            meteorChecked = true;
            for (String id : new String[]{"meteor", "meteor_entity"})
                if (BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath("lotm", id)).isPresent()) { meteorId = id; break; }
            LOGGER.info("[astral] end chaos: LOTM meteor entity = {}", meteorId);
        }
        return meteorId;
    }

    private static void explosion(ServerLevel lv, ServerPlayer p, ThreadLocalRandom r) {
        Vec3 at = r.nextInt(5) == 0 ? p.position() : around(p, 22, r);
        Level.ExplosionInteraction mode = Settings.chaosDestroysBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
        lv.explode(null, at.x, at.y + 1, at.z, 7.0f, true, mode);
        hazards.add(new Hazard(lv, null, new Vec3(at.x, at.y + 1, at.z), 7.0, 0));
    }

    /** LOTM's big ones: black holes, space rifts, tornados, lightning pillars. */
    private static void disaster(ServerLevel lv, ServerPlayer p, ThreadLocalRandom r) {
        String[] ids = {"black_hole_entity", "space_rift_entity", "tornado", "lightning_pillar"};
        String id = ids[r.nextInt(ids.length)];
        Vec3 at = r.nextInt(3) == 0 ? p.position() : around(p, 20, r);
        Entity e = Lotm.spawn(lv, id, at.x, at.y + 1, at.z, null, 160, p, 2.0f);
        if (e != null) hazards.add(new Hazard(lv, e, at, 5.0, (long) lv.getServer().getTickCount() + 180));
    }
}
