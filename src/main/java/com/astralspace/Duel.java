package com.astralspace;

import net.minecraft.ChatFormatting;
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
        final UUID a, b; final boolean safe; final int seqA, seqB;
        Match(UUID a, UUID b, boolean safe, int seqA, int seqB) { this.a = a; this.b = b; this.safe = safe; this.seqA = seqA; this.seqB = seqB; }
        UUID other(UUID x) { return x.equals(a) ? b : a; }
        int seqOf(UUID x) { return x.equals(a) ? seqA : seqB; }
    }

    private static final Map<UUID, Ask> asks = new HashMap<>();      // target -> challenge
    private static final Map<UUID, Match> matches = new HashMap<>(); // both duelists -> their match

    public static boolean dueling(Player p) { return matches.containsKey(p.getUUID()); }

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
        Match m = new Match(from.getUUID(), me.getUUID(), ask.safe(), Lotm.sequence(from), Lotm.sequence(me));
        matches.put(m.a, m); matches.put(m.b, m);
        asks.remove(from.getUUID());
        String msg = "The duel begins! (" + (ask.safe() ? "safe" : "normal") + ") Only your opponent can hurt or affect you. /duel forfeit to yield.";
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
