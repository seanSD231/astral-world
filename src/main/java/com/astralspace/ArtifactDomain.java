package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Domains: a sealed room you are put into for three waves, and what you walk out with.
 *
 * <p>A domain is cut for one set, so going in is how you decide what you are farming rather than
 * hoping the world hands it to you. The room is built high above the world on an empty column of
 * sky, walled in barrier so it is invisible from outside and cannot be walked out of, and taken
 * down again the moment the run ends however it ends.</p>
 *
 * <p>Each player gets their own column, picked from where they stand so two people running at once
 * never land in each other's room. The run holds where they came from and puts them back there,
 * and a run that is left hanging -- a logout, a death, a player who simply waits -- is ended by the
 * same path as one that is cleared, so a room is never left standing in the sky.</p>
 */
public final class ArtifactDomain {
    private ArtifactDomain() { }

    private static final int R = 15;          // half the floor
    private static final int HIGH = 14;       // how tall inside
    private static final int Y = 220;         // where the rooms sit
    private static final int TIMEOUT = 20 * 60 * 6;

    /** One run in progress. */
    private static final class Run {
        final String set;
        final int difficulty;
        final BlockPos origin;                // the middle of the floor
        final Vec3 back;                      // where they came from
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> backDim;
        final List<UUID> alive = new ArrayList<>();
        int wave, age, settle;
        Run(String set, int d, BlockPos o, Vec3 back, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> bd) {
            this.set = set; this.difficulty = d; this.origin = o; this.back = back; this.backDim = bd;
        }
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();

    public static boolean inside(ServerPlayer p) { return RUNS.containsKey(p.getUUID()); }

    // ---- going in ----

    public static void enter(ServerPlayer p, String setId, int difficulty) {
        if (RUNS.containsKey(p.getUUID())) {
            p.displayClientMessage(Component.literal("You are already in one.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ArtifactSets.Set set = ArtifactSets.get(setId);
        if (set == null) return;
        difficulty = Math.max(1, Math.min(5, difficulty));
        ServerLevel lv = p.serverLevel();

        // their own column, taken from where they stand so two runs never share a room
        int gx = (p.getBlockX() >> 7) * 128 + 2048, gz = (p.getBlockZ() >> 7) * 128 + 2048;
        BlockPos origin = new BlockPos(gx, Y, gz);

        Run run = new Run(setId, difficulty, origin, p.position(), lv.dimension());
        RUNS.put(p.getUUID(), run);
        build(lv, origin, true);
        p.teleportTo(lv, origin.getX() + .5, origin.getY() + 1, origin.getZ() + .5, java.util.Set.of(), p.getYRot(), p.getXRot());
        p.setDeltaMovement(Vec3.ZERO);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false));
        lv.playSound(null, origin, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, .5f, 1.4f);
        p.displayClientMessage(Component.literal(set.title()).withColor(set.colour())
                .append(Component.literal("  ·  three waves").withStyle(ChatFormatting.GRAY)), false);
        spawnWave(p, run, lv);
    }

    // ---- the room ----

    private static void build(ServerLevel lv, BlockPos o, boolean up) {
        BlockState floor = up ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        BlockState rim = up ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
        BlockState wall = up ? Blocks.BARRIER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
            boolean edge = Math.abs(x) >= R - 1 || Math.abs(z) >= R - 1;
            lv.setBlock(m.set(o.getX() + x, o.getY(), o.getZ() + z), edge ? rim : floor, 2);
            // walls and a lid of barrier: nothing to see from outside, and nowhere to step off
            if (Math.abs(x) == R || Math.abs(z) == R)
                for (int y = 1; y <= HIGH; y++) lv.setBlock(m.set(o.getX() + x, o.getY() + y, o.getZ() + z), wall, 2);
            lv.setBlock(m.set(o.getX() + x, o.getY() + HIGH + 1, o.getZ() + z), wall, 2);
        }
    }

    // ---- the waves ----

    private static final EntityType<?>[] EASY = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER};
    private static final EntityType<?>[] HARD = {EntityType.VINDICATOR, EntityType.WITCH, EntityType.PILLAGER, EntityType.STRAY};
    private static final EntityType<?>[] WORST = {EntityType.RAVAGER, EntityType.EVOKER, EntityType.BLAZE};

    private static void spawnWave(ServerPlayer p, Run run, ServerLevel lv) {
        run.wave++;
        run.alive.clear();
        int n = 3 + run.difficulty + run.wave * 2;
        double boost = 1 + (run.difficulty - 1) * .55 + (run.wave - 1) * .25;
        for (int i = 0; i < n; i++) {
            EntityType<?>[] pool = run.wave >= 3 && run.difficulty >= 4 ? WORST
                    : (run.wave >= 2 || run.difficulty >= 3 ? HARD : EASY);
            EntityType<?> type = pool[p.getRandom().nextInt(pool.length)];
            double a = i * Math.PI * 2 / n, rr = R * .55;
            Entity e = type.create(lv);
            if (!(e instanceof LivingEntity le)) continue;
            le.moveTo(run.origin.getX() + .5 + Math.cos(a) * rr, run.origin.getY() + 1,
                      run.origin.getZ() + .5 + Math.sin(a) * rr, (float) (a * 180 / Math.PI), 0);
            if (le instanceof net.minecraft.world.entity.Mob mob)
                mob.finalizeSpawn(lv, lv.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
            scale(le, boost);
            le.setPersistenceRequired();
            lv.addFreshEntity(le);
            run.alive.add(le.getUUID());
        }
        lv.playSound(null, run.origin, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, .7f, 1f);
        p.displayClientMessage(Component.literal("Wave " + run.wave + " of 3").withColor(0xFFD24A), true);
    }

    private static void scale(LivingEntity le, double boost) {
        var hp = le.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.addPermanentModifier(new AttributeModifier(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "domain_hp"),
                    boost - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            le.setHealth(le.getMaxHealth());
        }
        var dmg = le.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.addPermanentModifier(new AttributeModifier(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "domain_dmg"),
                (boost - 1) * .7, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    // ---- watching it ----

    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        if (RUNS.isEmpty() || e.getServer().getTickCount() % 10 != 0) return;
        for (UUID id : new ArrayList<>(RUNS.keySet())) {
            Run run = RUNS.get(id);
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(id);
            if (p == null) { tidy(e.getServer(), run); RUNS.remove(id); continue; }
            run.age += 10;
            ServerLevel lv = p.serverLevel();
            if (run.age > TIMEOUT || p.isDeadOrDying()) { leave(p, false); continue; }
            // a player who falls out of the room somehow is put back in the middle of it
            if (p.getY() < run.origin.getY() - 4 || p.distanceToSqr(run.origin.getX() + .5, run.origin.getY() + 1, run.origin.getZ() + .5) > (R + 8) * (R + 8))
                p.teleportTo(lv, run.origin.getX() + .5, run.origin.getY() + 1, run.origin.getZ() + .5, java.util.Set.of(), p.getYRot(), p.getXRot());

            run.alive.removeIf(u -> {
                Entity x = lv.getEntity(u);
                return x == null || !x.isAlive();
            });
            if (!run.alive.isEmpty()) continue;
            if (run.settle < 2) { run.settle++; continue; }
            run.settle = 0;
            if (run.wave >= 3) leave(p, true);
            else spawnWave(p, run, lv);
        }
    }

    /** Ends a run, hands over whatever was earned, and takes the room down behind them. */
    public static void leave(ServerPlayer p, boolean won) {
        Run run = RUNS.remove(p.getUUID());
        if (run == null) return;
        ServerLevel lv = p.server.getLevel(run.backDim);
        if (lv == null) lv = p.serverLevel();
        tidy(p.server, run);
        if (!p.isDeadOrDying())
            p.teleportTo(lv, run.back.x, run.back.y, run.back.z, java.util.Set.of(), p.getYRot(), p.getXRot());

        if (!won) {
            p.displayClientMessage(Component.literal("The domain closes. Nothing earned.")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }
        ArtifactSets.Set set = ArtifactSets.get(run.set);
        var r = p.getRandom();
        int count = 1 + (run.difficulty >= 3 ? 1 : 0) + (r.nextDouble() < run.difficulty * .14 ? 1 : 0);
        for (int i = 0; i < count; i++) {
            int rarity = Math.max(1, Math.min(5, 1 + run.difficulty / 2 + (r.nextDouble() < .35 ? 1 : 0)));
            var slot = Artifacts.Slot.values()[r.nextInt(Artifacts.Slot.values().length)];
            // the domain is cut for one set, but it does not pay out that set every single time
            String id = r.nextDouble() < .78 ? run.set : ArtifactSets.random(r).id();
            ItemStack st = Artifacts.roll(id, slot, rarity, r);
            if (!p.getInventory().add(st)) p.drop(st, false);
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, .8f, 1.3f);
        p.displayClientMessage(Component.literal("Cleared · " + (set == null ? run.set : set.title()))
                .withColor(set == null ? 0xFFD24A : set.colour())
                .append(Component.literal("  ·  " + count + " piece" + (count == 1 ? "" : "s"))
                        .withStyle(ChatFormatting.GRAY)), false);
    }

    /** Takes the room down and clears out anything still standing in it. */
    private static void tidy(net.minecraft.server.MinecraftServer server, Run run) {
        for (ServerLevel lv : server.getAllLevels()) {
            if (!lv.hasChunkAt(run.origin)) continue;
            if (!lv.getBlockState(run.origin).is(Blocks.POLISHED_BLACKSTONE)
                    && !lv.getBlockState(run.origin.above(HIGH + 1)).is(Blocks.BARRIER)) continue;
            for (Entity x : lv.getEntitiesOfClass(LivingEntity.class, new AABB(run.origin).inflate(R + 2, HIGH + 2, R + 2)))
                if (!(x instanceof ServerPlayer)) x.discard();
            build(lv, run.origin, false);
        }
    }

    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && RUNS.containsKey(sp.getUUID())) {
            Run run = RUNS.remove(sp.getUUID());
            tidy(sp.server, run);
        }
    }
}
