package com.astralspace;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Boss raids: the same queue the ladder uses, pointed at something that fights back.
 *
 * <p>A raid is a party of two to four put into a sealed room with the Moon's Corruption and
 * fourteen minutes to finish it. The boss is scaled by the depth asked for and by how many went in,
 * so a pair at depth one and a four at depth five are both a fight rather than a formality -- and
 * it is scaled by party size rather than divided by it, so bringing a friend is help and not a tax
 * on the reward.</p>
 *
 * <p>Nobody is dropped from a raid for dying. The dead sit it out and are carried home with
 * everybody else, and they still take their share if the thing goes down, because a raid where
 * one death ends your evening is a raid people stop queueing for.</p>
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class Raid {
    private Raid() { }

    private static final int R = 26, HIGH = 22, Y = 200;
    private static final int TIMEOUT = 20 * 60 * 14;
    private static final int PARTY_MAX = 4;
    /** How long two will wait for a third before going in as a pair. */
    private static final long PATIENCE_MS = 25_000;

    private static final class Party {
        final int depth;
        final List<UUID> members = new ArrayList<>();
        final Map<UUID, Vec3> home = new HashMap<>();
        final Map<UUID, ResourceKey<Level>> homeDim = new HashMap<>();
        final BlockPos origin;
        UUID boss;
        int age;
        Party(int depth, BlockPos origin) { this.depth = depth; this.origin = origin; }
    }

    private static final Map<UUID, Integer> QUEUE = new LinkedHashMap<>();   // who is waiting, at what depth
    private static final Map<UUID, Long> SINCE = new HashMap<>();
    private static final Map<UUID, Party> IN = new HashMap<>();
    private static final List<Party> PARTIES = new ArrayList<>();

    public static boolean raiding(ServerPlayer p) { return IN.containsKey(p.getUUID()); }

    // ---- the queue ----

    private static void join(ServerPlayer p, int depth) {
        if (raiding(p)) { tell(p, "You are already in one.", ChatFormatting.GRAY); return; }
        if (Duel.dueling(p)) { tell(p, "Finish your duel first.", ChatFormatting.RED); return; }
        QUEUE.put(p.getUUID(), Math.max(1, Math.min(5, depth)));
        SINCE.put(p.getUUID(), System.currentTimeMillis());
        int n = 0;
        for (int d : QUEUE.values()) if (d == depth) n++;
        p.sendSystemMessage(Component.literal("Queued for a raid at depth " + depth)
                .withColor(0xE0384A)
                .append(Component.literal("   (" + n + " waiting)").withStyle(ChatFormatting.DARK_GRAY)));
    }

    private static void leave(ServerPlayer p) {
        if (QUEUE.remove(p.getUUID()) == null) tell(p, "You are not in the queue.", ChatFormatting.GRAY);
        else { SINCE.remove(p.getUUID()); tell(p, "Out of the raid queue.", ChatFormatting.GRAY); }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        if (server.getTickCount() % 20 == 0) formParties(server);
        if (server.getTickCount() % 10 == 0) watch(server);
    }

    /** Fills a party at each depth: four at once, or two once they have waited long enough. */
    private static void formParties(MinecraftServer server) {
        if (QUEUE.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (int depth = 1; depth <= 5; depth++) {
            List<ServerPlayer> waiting = new ArrayList<>();
            long oldest = now;
            for (var en : new ArrayList<>(QUEUE.entrySet())) {
                if (en.getValue() != depth) continue;
                ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
                if (p == null || raiding(p) || Duel.dueling(p)) { QUEUE.remove(en.getKey()); continue; }
                waiting.add(p);
                oldest = Math.min(oldest, SINCE.getOrDefault(en.getKey(), now));
            }
            boolean full = waiting.size() >= PARTY_MAX;
            boolean patient = waiting.size() >= 2 && now - oldest >= PATIENCE_MS;
            if (!full && !patient) continue;
            start(server, waiting.subList(0, Math.min(PARTY_MAX, waiting.size())), depth);
        }
    }

    // ---- the room ----

    private static BlockPos freeOrigin() {
        for (int slot = 0; slot < 64; slot++) {
            BlockPos at = new BlockPos(60000 + slot * 256, Y, 60000);
            boolean taken = false;
            for (Party q : PARTIES) if (q.origin.equals(at)) taken = true;
            if (!taken) return at;
        }
        return new BlockPos(60000, Y, 60000);
    }

    private static void build(ServerLevel lv, BlockPos o, boolean up) {
        BlockState floor = up ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        BlockState rim = up ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
        BlockState wall = up ? Blocks.BARRIER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -R - 1; x <= R + 1; x++) for (int z = -R - 1; z <= R + 1; z++) {
            double d = Math.hypot(x, z);
            if (d <= R) lv.setBlock(m.set(o.getX() + x, o.getY(), o.getZ() + z), d >= R - 1.6 ? rim : floor, 2);
            if (d > R && d <= R + 1.6)
                for (int y = 1; y <= HIGH; y++) lv.setBlock(m.set(o.getX() + x, o.getY() + y, o.getZ() + z), wall, 2);
            if (d <= R + 1.6) lv.setBlock(m.set(o.getX() + x, o.getY() + HIGH + 1, o.getZ() + z), wall, 2);
        }
    }

    private static void start(MinecraftServer server, List<ServerPlayer> party, int depth) {
        if (party.isEmpty()) return;
        ServerLevel lv = party.get(0).serverLevel();
        Party q = new Party(depth, freeOrigin());
        build(lv, q.origin, true);

        for (ServerPlayer p : party) {
            QUEUE.remove(p.getUUID());
            SINCE.remove(p.getUUID());
            q.members.add(p.getUUID());
            q.home.put(p.getUUID(), p.position());
            q.homeDim.put(p.getUUID(), p.level().dimension());
            IN.put(p.getUUID(), q);
            double a = q.members.size() * Math.PI * 2 / Math.max(1, party.size());
            p.teleportTo(lv, q.origin.getX() + .5 + Math.cos(a) * (R - 6), q.origin.getY() + 1,
                         q.origin.getZ() + .5 + Math.sin(a) * (R - 6), java.util.Set.of(), p.getYRot(), p.getXRot());
            p.setDeltaMovement(Vec3.ZERO);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 4, false, false));
        }

        var boss = MoonCorruption.TYPE.get().create(lv);
        if (boss != null) {
            boss.moveTo(q.origin.getX() + .5, q.origin.getY() + 9, q.origin.getZ() + .5, 0, 0);
            // Scaled by depth and by how many went in -- multiplied by party size rather than
            // divided, so bringing a friend is help and not a tax on what you each take home.
            double mult = (.9 + depth * .5) * (.75 + party.size() * .32);
            var hp = boss.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) {
                hp.addPermanentModifier(new AttributeModifier(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "raid_hp"),
                        mult - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                boss.setHealth(boss.getMaxHealth());
            }
            var dmg = boss.getAttribute(Attributes.ATTACK_DAMAGE);
            if (dmg != null) dmg.addPermanentModifier(new AttributeModifier(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "raid_dmg"),
                    (mult - 1) * .45, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            lv.addFreshEntity(boss);
            q.boss = boss.getUUID();
        }
        PARTIES.add(q);

        Component line = Component.literal("The Moon's Corruption · depth " + depth + " · "
                + party.size() + (party.size() == 1 ? " raider" : " raiders")).withColor(0xE0384A);
        for (ServerPlayer p : party) {
            p.sendSystemMessage(line);
            p.sendSystemMessage(Component.literal("Fourteen minutes. /raid forfeit to go home.")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        lv.playSound(null, q.origin, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 2.4f, .6f);
    }

    // ---- watching one ----

    private static void watch(MinecraftServer server) {
        for (Party q : new ArrayList<>(PARTIES)) {
            q.age += 10;
            ServerLevel lv = null;
            boolean anyone = false;
            for (UUID id : q.members) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) continue;
                anyone = true;
                lv = p.serverLevel();
                // anybody who ends up outside the room goes back into the middle of it
                if (!p.isDeadOrDying() && p.distanceToSqr(q.origin.getX() + .5, q.origin.getY() + 1, q.origin.getZ() + .5) > (R + 10) * (R + 10))
                    p.teleportTo(lv, q.origin.getX() + .5, q.origin.getY() + 1, q.origin.getZ() + .5,
                                 java.util.Set.of(), p.getYRot(), p.getXRot());
            }
            if (!anyone) { finish(server, q, false); continue; }
            if (q.age > TIMEOUT) { finish(server, q, false); continue; }
            if (q.boss != null && lv != null) {
                Entity b = lv.getEntity(q.boss);
                if (b == null || !b.isAlive()) finish(server, q, true);
            }
        }
    }

    /** The boss going down is the only thing that wins a raid. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof MoonCorruption)) return;
        for (Party q : new ArrayList<>(PARTIES))
            if (e.getEntity().getUUID().equals(q.boss)) {
                MinecraftServer server = e.getEntity().getServer();
                if (server != null) finish(server, q, true);
            }
    }

    private static void finish(MinecraftServer server, Party q, boolean won) {
        if (!PARTIES.remove(q)) return;
        ServerLevel room = null;
        for (UUID id : q.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            IN.remove(id);
            if (p == null) continue;
            if (room == null) room = p.serverLevel();
            ServerLevel back = server.getLevel(q.homeDim.get(id));
            Vec3 home = q.home.get(id);
            if (back != null && home != null && !p.isDeadOrDying())
                p.teleportTo(back, home.x, home.y, home.z, java.util.Set.of(), p.getYRot(), p.getXRot());
            if (!won) {
                p.sendSystemMessage(Component.literal("The raid ends. Nothing earned.").withStyle(ChatFormatting.GRAY));
                continue;
            }
            reward(p, q.depth);
        }
        if (room != null) {
            for (Entity x : room.getEntitiesOfClass(LivingEntity.class, new AABB(q.origin).inflate(R + 4, HIGH + 4, R + 4)))
                if (!(x instanceof ServerPlayer)) x.discard();
            build(room, q.origin, false);
        }
    }

    /**
     * Everybody who went in takes their own share. A raid is not a race to a pile and the dead
     * are paid too: sitting out the last minute of a fight you helped win is punishment enough.
     */
    private static void reward(ServerPlayer p, int depth) {
        int coins = 40 + depth * 55 + p.getRandom().nextInt(30);
        Loot.pay(p, coins);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, .8f, 1.2f);
        p.sendSystemMessage(Component.literal("Cleared \u00b7 " + coins + " coins").withColor(0xE0384A));
    }

    // ---- commands ----

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("raid")
            .requires(c -> c.getEntity() instanceof ServerPlayer)
            .executes(c -> status(c.getSource().getPlayerOrException()))
            .then(Commands.literal("queue")
                .executes(c -> { join(c.getSource().getPlayerOrException(), 2); return 1; })
                .then(Commands.argument("depth", IntegerArgumentType.integer(1, 5))
                    .executes(c -> { join(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "depth")); return 1; })))
            .then(Commands.literal("leave").executes(c -> { leave(c.getSource().getPlayerOrException()); return 1; }))
            .then(Commands.literal("forfeit").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                Party q = IN.get(p.getUUID());
                if (q == null) { tell(p, "You are not in a raid.", ChatFormatting.GRAY); return 0; }
                finish(p.server, q, false);
                return 1;
            }))
            .then(Commands.literal("status").executes(c -> status(c.getSource().getPlayerOrException()))));
    }

    private static int status(ServerPlayer p) {
        Party q = IN.get(p.getUUID());
        if (q != null) {
            p.sendSystemMessage(Component.literal("Depth " + q.depth + " · " + q.members.size()
                    + " raiders · " + ((TIMEOUT - q.age) / 1200) + " minutes left").withColor(0xE0384A));
            return 1;
        }
        Map<Integer, Integer> by = new java.util.TreeMap<>();
        for (int d : QUEUE.values()) by.merge(d, 1, Integer::sum);
        if (by.isEmpty()) p.sendSystemMessage(Component.literal("Nobody is queued. /raid queue <1-5>")
                .withStyle(ChatFormatting.DARK_GRAY));
        else for (var en : by.entrySet())
            p.sendSystemMessage(Component.literal("Depth " + en.getKey() + ": " + en.getValue() + " waiting")
                    .withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static void tell(ServerPlayer p, String s, ChatFormatting f) {
        p.sendSystemMessage(Component.literal(s).withStyle(f));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        QUEUE.remove(e.getEntity().getUUID());
        SINCE.remove(e.getEntity().getUUID());
        Party q = IN.remove(e.getEntity().getUUID());
        if (q != null && e.getEntity() instanceof ServerPlayer sp) {
            boolean anyLeft = false;
            for (UUID id : q.members) if (sp.server.getPlayerList().getPlayer(id) != null) anyLeft = true;
            if (!anyLeft) finish(sp.server, q, false);
        }
    }
}
