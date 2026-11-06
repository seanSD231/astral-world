package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /duel <player> <safe|normal>, then /duel accept | decline | forfeit.
 * Safe: nobody can die or regress; a killing blow just ends the duel. Normal: death and regression happen as they always do.
 * In both modes a duelist can only be hurt or affected by their opponent, and can only hurt or affect their opponent.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class Duel {
    private Duel() {}

    private static final long ASK_MS = 60_000;

    private record Ask(UUID from, boolean safe, long until) {}
    private static final class Match {
        final UUID a, b; final boolean safe; final int seqA, seqB; final int slot;
        Match(UUID a, UUID b, boolean safe, int seqA, int seqB, int slot) { this.a = a; this.b = b; this.safe = safe; this.seqA = seqA; this.seqB = seqB; this.slot = slot; }
        UUID other(UUID x) { return x.equals(a) ? b : a; }
        int seqOf(UUID x) { return x.equals(a) ? seqA : seqB; }
    }

    private static final Map<UUID, Ask> asks = new HashMap<>();      // target -> challenge
    private static final Map<UUID, Match> matches = new HashMap<>(); // both duelists -> their match

    public static boolean dueling(Player p) { return matches.containsKey(p.getUUID()); }

    // ------------------------------------------------------------------ the duel arena
    // Duels do not happen where the challenge was made (an ambush, a prepared spot, someone else's spell or mob would decide them): both
    // duelists are carried to a platform of their own, far from anything, and carried back to exactly where they were afterwards.

    private static final int ARENA_X = 1_000_000, ARENA_Z = 1_000_000, ARENA_Y = 250, ARENA_R = 20, SLOT_GAP = 80;
    private static final String BACK = "astral_duel_back";
    private static final Map<UUID, Long> returns = new HashMap<>();   // player -> server tick at which they go back

    private static BlockPos centre(int slot) { return new BlockPos(ARENA_X + slot * SLOT_GAP, ARENA_Y, ARENA_Z); }

    private static int freeSlot() {
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Match m : matches.values()) used.add(m.slot);
        int i = 0; while (used.contains(i)) i++;
        return i;
    }

    /** The platform: a round stone floor with lit panels, and invisible walls and a lid so nobody leaves it. Built once. */
    private static void buildArena(ServerLevel lv, int slot) {
        BlockPos c = centre(slot);
        BlockState mark = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        if (lv.getBlockState(c).is(mark.getBlock())) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -ARENA_R - 3; dx <= ARENA_R + 3; dx++)
            for (int dz = -ARENA_R - 3; dz <= ARENA_R + 3; dz++) {
                double r = Math.hypot(dx, dz);
                if (r <= ARENA_R) {
                    BlockState f = (dx == 0 && dz == 0) ? mark
                        : r >= ARENA_R - 1.5 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                        : ((dx & 3) == 0 && (dz & 3) == 0) ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
                    lv.setBlock(pos.set(c.getX() + dx, c.getY(), c.getZ() + dz), f, 2);
                    lv.setBlock(pos.set(c.getX() + dx, c.getY() + 31, c.getZ() + dz), Blocks.BARRIER.defaultBlockState(), 2);
                } else if (r <= ARENA_R + 2.5) {
                    for (int y = 1; y <= 30; y++) lv.setBlock(pos.set(c.getX() + dx, c.getY() + y, c.getZ() + dz), Blocks.BARRIER.defaultBlockState(), 2);
                }
            }
    }

    /** Where a duelist stands in the arena: opposite ends, facing each other. */
    private static double[] spot(int slot, boolean first) {
        BlockPos c = centre(slot);
        return new double[]{c.getX() + 0.5 + (first ? -14 : 14), c.getY() + 1, c.getZ() + 0.5, first ? -90 : 90};
    }

    private static void carryToArena(ServerPlayer p, int slot, boolean first) {
        CompoundTag keep = p.getPersistentData();
        if (!keep.contains(BACK)) {
            CompoundTag t = new CompoundTag();
            t.putString("dim", p.level().dimension().location().toString());
            t.putDouble("x", p.getX()); t.putDouble("y", p.getY()); t.putDouble("z", p.getZ());
            t.putFloat("yaw", p.getYRot()); t.putFloat("pitch", p.getXRot());
            keep.put(BACK, t);
        }
        ServerLevel lv = p.server.overworld();
        double[] s = spot(slot, first);
        p.teleportTo(lv, s[0], s[1], s[2], (float) s[3], 0f);
        p.setDeltaMovement(0, 0, 0); p.fallDistance = 0;
    }

    private static void carryBack(ServerPlayer p) {
        CompoundTag keep = p.getPersistentData();
        if (!keep.contains(BACK)) return;
        CompoundTag t = keep.getCompound(BACK);
        keep.remove(BACK);
        ServerLevel to = null;
        try { to = p.server.getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dim")))); } catch (Exception ignored) { }
        if (to == null) to = p.server.overworld();
        p.teleportTo(to, t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("yaw"), t.getFloat("pitch"));
        p.setDeltaMovement(0, 0, 0); p.fallDistance = 0;
        p.sendSystemMessage(Component.literal("You are back where you were.").withStyle(ChatFormatting.GREEN));
    }

    private static void sendBackIn(ServerPlayer p, int seconds) {
        returns.put(p.getUUID(), (long) p.server.getTickCount() + seconds * 20L);
        say(p, "You are carried back in " + seconds + " seconds.", ChatFormatting.GRAY);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("duel").requires(c -> c.getEntity() instanceof ServerPlayer)
            .then(Commands.literal("accept").executes(Duel::accept))
            .then(Commands.literal("decline").executes(Duel::decline))
            .then(Commands.literal("forfeit").executes(Duel::forfeit))
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.literal("safe").executes(c -> challenge(c, true)))
                .then(Commands.literal("normal").executes(c -> challenge(c, false)))));
    }

    private static void say(ServerPlayer p, String s, ChatFormatting f) { p.sendSystemMessage(Component.literal(s).withStyle(f)); }

    private static int challenge(CommandContext<CommandSourceStack> ctx, boolean safe) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException(), them = EntityArgument.getPlayer(ctx, "player");
        if (me == them) { say(me, "You can't duel yourself.", ChatFormatting.RED); return 0; }
        if (dueling(me)) { say(me, "You are already in a duel. /duel forfeit to leave it.", ChatFormatting.RED); return 0; }
        if (dueling(them)) { say(me, them.getGameProfile().getName() + " is already in a duel.", ChatFormatting.RED); return 0; }
        if (HungerGames.fighting(me) || HungerGames.fighting(them)) { say(me, "Not during the hunger games.", ChatFormatting.RED); return 0; }
        if (me.level().dimension().equals(Cosmos.COSMOS) || them.level().dimension().equals(Cosmos.COSMOS)) { say(me, "Not in the arena dimension.", ChatFormatting.RED); return 0; }
        asks.put(them.getUUID(), new Ask(me.getUUID(), safe, System.currentTimeMillis() + ASK_MS));
        String mode = safe ? "SAFE (nobody loses items or sequence)" : "NORMAL (the loser really dies: items and sequence are at stake)";
        say(me, "Challenge sent to " + them.getGameProfile().getName() + " (" + (safe ? "safe" : "normal") + "). It expires in 60 seconds.", ChatFormatting.GOLD);
        them.sendSystemMessage(Component.literal(me.getGameProfile().getName() + " challenges you to a " + mode + " duel. ").withStyle(safe ? ChatFormatting.GOLD : ChatFormatting.RED)
            .append(Component.literal("[Accept]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel accept")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Accept the duel")))))
            .append(Component.literal(" "))
            .append(Component.literal("[Decline]").withStyle(s -> s.withColor(ChatFormatting.GRAY).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel decline")))));
        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        Ask ask = asks.remove(me.getUUID());
        if (ask == null || ask.until() < System.currentTimeMillis()) { say(me, "You have no pending duel challenge.", ChatFormatting.RED); return 0; }
        ServerPlayer from = me.server.getPlayerList().getPlayer(ask.from());
        if (from == null) { say(me, "Your challenger is no longer online.", ChatFormatting.RED); return 0; }
        if (dueling(me) || dueling(from)) { say(me, "One of you is already in a duel.", ChatFormatting.RED); return 0; }
        if (HungerGames.fighting(me) || HungerGames.fighting(from)) { say(me, "Not during the hunger games.", ChatFormatting.RED); return 0; }
        if (me.level().dimension().equals(Cosmos.COSMOS) || from.level().dimension().equals(Cosmos.COSMOS)) { say(me, "Not in the arena dimension.", ChatFormatting.RED); return 0; }
        returns.remove(me.getUUID()); returns.remove(from.getUUID());
        Match m = new Match(from.getUUID(), me.getUUID(), ask.safe(), Lotm.sequence(from), Lotm.sequence(me), freeSlot());
        matches.put(m.a, m); matches.put(m.b, m);
        asks.remove(from.getUUID());
        buildArena(me.server.overworld(), m.slot);
        carryToArena(from, m.slot, true); carryToArena(me, m.slot, false);
        String msg = "The duel begins in the duel arena! (" + (ask.safe() ? "safe" : "normal") + ") Only your opponent can hurt or affect you. /duel forfeit to yield. You are carried back to where you were afterwards.";
        say(from, msg, ChatFormatting.GREEN); say(me, msg, ChatFormatting.GREEN);
        return 1;
    }

    private static int decline(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        Ask ask = asks.remove(me.getUUID());
        if (ask == null) { say(me, "You have no pending duel challenge.", ChatFormatting.RED); return 0; }
        ServerPlayer from = me.server.getPlayerList().getPlayer(ask.from());
        say(me, "Challenge declined.", ChatFormatting.GRAY);
        if (from != null) say(from, me.getGameProfile().getName() + " declined your duel.", ChatFormatting.GRAY);
        return 1;
    }

    private static int forfeit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        if (!dueling(me)) { say(me, "You are not in a duel.", ChatFormatting.RED); return 0; }
        end(me.server, matches.get(me.getUUID()), me.getUUID(), "forfeited");
        return 1;
    }

    /** Ends a match; `loser` lost. Nothing else happens to anyone here: a normal duel's death is the ordinary death. */
    private static void end(MinecraftServer server, Match m, UUID loser, String how) {
        if (m == null) return;
        matches.remove(m.a); matches.remove(m.b);
        ServerPlayer l = server.getPlayerList().getPlayer(loser), w = server.getPlayerList().getPlayer(m.other(loser));
        String ln = l != null ? l.getGameProfile().getName() : "?", wn = w != null ? w.getGameProfile().getName() : "?";
        Component msg = Component.literal("Duel over: " + wn + " defeated " + ln + " (" + how + ").").withStyle(ChatFormatting.GOLD);
        if (l != null) l.sendSystemMessage(msg);
        if (w != null) w.sendSystemMessage(msg);
        if (how.equals("killed")) {   // the loser really died: they respawn as usual (nothing to carry back); the winner may collect the drops first
            if (l != null) l.getPersistentData().remove(BACK);
            if (w != null) sendBackIn(w, 20);
        } else {
            if (l != null) sendBackIn(l, 3);
            if (w != null) sendBackIn(w, 3);
        }
    }

    /** The player behind a damage source or an effect, or null. */
    private static Player playerBehind(Entity e) { return e instanceof Player p ? p : null; }

    /** Is a player-versus-player interaction allowed? Only between the two members of one duel. */
    private static boolean allowed(Player attacker, Player victim) {
        Match ma = matches.get(attacker.getUUID()), mv = matches.get(victim.getUUID());
        if (ma == null && mv == null) return true;           // nobody is dueling: ordinary rules
        return ma != null && ma == mv;                       // a duelist and an outsider, or two other duelists: never
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || matches.isEmpty()) return;
        Player by = playerBehind(event.getSource().getEntity());
        if (by != null && by != victim && !allowed(by, victim)) event.setCanceled(true);
    }

    /** Safe duels: a blow that would kill is cancelled (after armour) and ends the duel. Healed to full so the loser walks away. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Match m = matches.get(p.getUUID());
        if (m == null || !m.safe) return;
        if (event.getNewDamage() >= p.getHealth() + p.getAbsorptionAmount()) {
            event.setNewDamage(0);
            p.setHealth(p.getMaxHealth());
            p.clearFire();
            end(p.server, m, p.getUUID(), "knocked out");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Match m = matches.get(p.getUUID());
        if (m == null) return;
        if (m.safe) {   // a death that skipped the damage check (a command, a mod): nobody dies in a safe duel
            event.setCanceled(true);
            p.setHealth(p.getMaxHealth());
            p.clearFire();
            end(p.server, m, p.getUUID(), "knocked out");
        } else {
            end(p.server, m, p.getUUID(), "killed");
        }
    }

    /** Nobody outside a duel can put an effect on a duelist, and a duelist's effects only reach their opponent. The effect is taken straight back off (the old one restored). */
    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Added event) {
        if (matches.isEmpty() || !(event.getEntity() instanceof Player victim)) return;
        Player by = playerBehind(event.getEffectSource());
        if (by == null || by == victim || allowed(by, victim)) return;
        var old = event.getOldEffectInstance();
        victim.removeEffect(event.getEffectInstance().getEffect());
        if (old != null) victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(old));
    }

    /** Back from a duel that ended while they were away (they left the game, or the server stopped). */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || matches.containsKey(p.getUUID())) return;
        if (p.getPersistentData().contains(BACK)) returns.put(p.getUUID(), (long) p.server.getTickCount() + 20);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !matches.containsKey(p.getUUID())) p.getPersistentData().remove(BACK);
    }

    /** The arena is not for building or digging. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() != null && matches.containsKey(event.getPlayer().getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player p && matches.containsKey(p.getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        asks.remove(p.getUUID());
        asks.values().removeIf(a -> a.from().equals(p.getUUID()));
        Match m = matches.get(p.getUUID());
        if (m != null) end(p.server, m, p.getUUID(), "left the game");
    }

    /** Once a second: expire challenges, and keep safe duelists from regressing (LOTM's protection timers, and a dropped sequence is put back). */
    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        long tickNow = server.getTickCount();
        for (var it = returns.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue() > tickNow) continue;
            it.remove();
            ServerPlayer back = server.getPlayerList().getPlayer(e.getKey());
            if (back != null && !matches.containsKey(back.getUUID())) carryBack(back);
        }
        for (Match m : new java.util.HashSet<>(matches.values())) {   // nobody leaves the platform
            for (UUID id : new UUID[]{m.a, m.b}) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) continue;
                BlockPos c = centre(m.slot);
                boolean in = p.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                        && Math.hypot(p.getX() - c.getX() - 0.5, p.getZ() - c.getZ() - 0.5) <= ARENA_R + 1 && p.getY() > c.getY() - 3;
                if (!in) { double[] s = spot(m.slot, id.equals(m.a)); p.teleportTo(server.overworld(), s[0], s[1], s[2], (float) s[3], 0f); p.fallDistance = 0; }
            }
        }
        long now = System.currentTimeMillis();
        asks.values().removeIf(a -> a.until() < now);
        for (Match m : new java.util.HashSet<>(matches.values())) {
            if (!m.safe) continue;
            for (UUID id : new UUID[]{m.a, m.b}) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) continue;
                var tag = p.getPersistentData();
                tag.putInt("sequenceRegressionProtectionTimer", 6000);
                tag.putInt("sequenceImmunityTimer", 6000);
                int want = m.seqOf(id), sq = Lotm.sequence(p);
                if (want >= 0 && sq > want) Lotm.setSequence(p, want);
            }
        }
    }
}
