package com.astralspace;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A bracket, run from an altar an administrator keeps.
 *
 * <p>Nothing like the ladder: this is not standing open. Somebody opens signups, everybody who
 * wants in pays the same coin into one pot, and when the operator says so the names are drawn into
 * branches. You fight whoever you were drawn against. Lose and you are out, with nothing. The last
 * one standing takes the pot, their own stake included.</p>
 *
 * <p>One tournament at a time on a server. Bouts borrow the ladder's arenas and are run outside the
 * ladder, so a bracket never moves anybody's points.</p>
 */
public final class Tournament {
    private Tournament() { }

    public enum Phase { IDLE, SIGNUP, RUNNING }

    /** A drawn pairing. {@code b} null means a bye. */
    private static final class Pair {
        final UUID a, b;
        UUID winner;
        boolean running, done;
        Pair(UUID a, UUID b) { this.a = a; this.b = b; if (b == null) { winner = a; done = true; } }
    }

    private static Phase phase = Phase.IDLE;
    private static Arena.Mode mode = Arena.Mode.SEQ5;
    private static int fee = 100;
    private static int pot;
    private static UUID host;

    /** Entrants in the order they paid in; the draw follows it. */
    private static final Map<UUID, String> ENTRANTS = new LinkedHashMap<>();
    private static final List<List<Pair>> ROUNDS = new ArrayList<>();
    private static final Map<UUID, String> NAMES = new LinkedHashMap<>();
    private static UUID champion;

    public static Phase phase() { return phase; }
    public static Arena.Mode mode() { return mode; }
    public static int fee() { return fee; }
    public static int pot() { return pot; }
    public static boolean entered(UUID id) { return ENTRANTS.containsKey(id); }
    public static int entrants() { return ENTRANTS.size(); }

    // ---------------------------------------------------------------- the operator's side

    public static boolean op(ServerPlayer p) {
        return p.hasPermissions(2);
    }

    public static void setMode(ServerPlayer p, Arena.Mode m) {
        if (!op(p) || phase == Phase.RUNNING) return;
        mode = m;
        broadcast(p.server);
    }

    public static void setFee(ServerPlayer p, int amount) {
        if (!op(p) || phase != Phase.IDLE) return;       // nobody's stake changes under them
        fee = Math.max(0, Math.min(100000, amount));
        broadcast(p.server);
    }

    public static void open(ServerPlayer p) {
        if (!op(p) || phase != Phase.IDLE) return;
        phase = Phase.SIGNUP;
        host = p.getUUID();
        pot = 0;
        champion = null;
        ENTRANTS.clear();
        NAMES.clear();
        ROUNDS.clear();
        shout(p.server, Component.literal("A tournament is taking names — " + mode.title
                + ", " + fee + " to enter.").withStyle(s -> s.withColor(0xFFD37A)));
        broadcast(p.server);
    }

    public static void start(ServerPlayer p) {
        if (!op(p) || phase != Phase.SIGNUP) return;
        if (ENTRANTS.size() < 2) {
            p.displayClientMessage(Component.literal("Two entrants at least.").withStyle(s -> s.withColor(0xC86464)), true);
            return;
        }
        phase = Phase.RUNNING;
        NAMES.putAll(ENTRANTS);
        draw(new ArrayList<>(ENTRANTS.keySet()));
        shout(p.server, Component.literal("The bracket is drawn. " + ENTRANTS.size()
                + " in, " + pot + " on the table.").withStyle(s -> s.withColor(0xFFD37A)));
        broadcast(p.server);
    }

    public static void cancel(ServerPlayer p) {
        if (!op(p) || phase == Phase.IDLE) return;
        for (UUID id : ENTRANTS.keySet()) {                 // stakes go back
            ServerPlayer e = p.server.getPlayerList().getPlayer(id);
            if (e != null) {
                Loot.pay(e, fee);
                e.displayClientMessage(Component.literal("The tournament was called off. Your "
                        + fee + " is back.").withStyle(s -> s.withColor(0xBFD4E6)), false);
            }
        }
        reset();
        shout(p.server, Component.literal("The tournament was called off.").withStyle(s -> s.withColor(0x9AA4B0)));
        broadcast(p.server);
    }

    // ---------------------------------------------------------------- the entrants' side

    public static void join(ServerPlayer p) {
        if (phase != Phase.SIGNUP) return;
        if (ENTRANTS.containsKey(p.getUUID())) return;
        if (fee > 0 && !Loot.take(p, fee)) {
            p.displayClientMessage(Component.literal("You are short of the " + fee
                    + " to enter.").withStyle(s -> s.withColor(0xC86464)), true);
            return;
        }
        ENTRANTS.put(p.getUUID(), p.getGameProfile().getName());
        pot += fee;
        shout(p.server, Component.literal(p.getGameProfile().getName() + " is in. Pot: "
                + pot + ".").withStyle(s -> s.withColor(0xBFD4E6)));
        broadcast(p.server);
    }

    public static void withdraw(ServerPlayer p) {
        if (phase != Phase.SIGNUP) return;
        if (ENTRANTS.remove(p.getUUID()) == null) return;
        pot -= fee;
        Loot.pay(p, fee);
        broadcast(p.server);
    }

    // ---------------------------------------------------------------- the bracket

    /** Draws the opening round, giving byes out to whoever is left over. */
    private static void draw(List<UUID> seeds) {
        java.util.Collections.shuffle(seeds);
        int size = 1;
        while (size < seeds.size()) size <<= 1;
        int byes = size - seeds.size();

        List<Pair> first = new ArrayList<>();
        int i = 0;
        while (byes-- > 0 && i < seeds.size()) first.add(new Pair(seeds.get(i++), null));
        for (; i + 1 < seeds.size(); i += 2) first.add(new Pair(seeds.get(i), seeds.get(i + 1)));
        if (i < seeds.size()) first.add(new Pair(seeds.get(i), null));

        ROUNDS.add(first);
    }

    /** Called every few ticks: pushes resolved rounds along and feeds free arenas. */
    public static void onTick(MinecraftServer server) {
        if (phase != Phase.RUNNING || ROUNDS.isEmpty()) return;
        List<Pair> round = ROUNDS.get(ROUNDS.size() - 1);

        for (Pair pr : round) {
            if (pr.done || pr.running) continue;
            ServerPlayer a = server.getPlayerList().getPlayer(pr.a);
            ServerPlayer b = server.getPlayerList().getPlayer(pr.b);
            if (a == null && b == null) continue;                    // both away; wait for one
            if (a == null) { settle(server, pr, pr.b); continue; }   // walkover
            if (b == null) { settle(server, pr, pr.a); continue; }
            if (Arena.freeSlots() == 0) break;                       // every arena busy; next tick
            final Pair held = pr;
            if (Arena.exhibition(mode, a, b, (winner, loser) ->
                    settle(server, held, winner != null ? winner : held.a))) {
                pr.running = true;
            } else {
                break;
            }
        }

        boolean all = true;
        for (Pair pr : round) if (!pr.done) { all = false; break; }
        if (!all) return;

        List<UUID> through = new ArrayList<>();
        for (Pair pr : round) if (pr.winner != null) through.add(pr.winner);

        if (through.size() <= 1) {
            champion = through.isEmpty() ? null : through.get(0);
            finish(server);
            return;
        }
        List<Pair> next = new ArrayList<>();
        for (int i = 0; i + 1 < through.size(); i += 2) next.add(new Pair(through.get(i), through.get(i + 1)));
        if (through.size() % 2 == 1) next.add(new Pair(through.get(through.size() - 1), null));
        ROUNDS.add(next);
        shout(server, Component.literal("Round " + ROUNDS.size() + ": " + through.size()
                + " left.").withStyle(s -> s.withColor(0xFFD37A)));
        broadcast(server);
    }

    private static void settle(MinecraftServer server, Pair pr, UUID winner) {
        if (pr.done) return;
        pr.running = false;
        pr.done = true;
        pr.winner = winner;
        UUID out = winner != null && winner.equals(pr.a) ? pr.b : pr.a;
        shout(server, Component.literal(name(winner) + " puts " + name(out)
                + " out.").withStyle(s -> s.withColor(0xBFD4E6)));
        broadcast(server);
    }

    private static void finish(MinecraftServer server) {
        if (champion != null) {
            ServerPlayer w = server.getPlayerList().getPlayer(champion);
            if (w != null) {
                Loot.pay(w, pot);
                w.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.2f);
                w.displayClientMessage(Component.literal("The whole pot — " + pot
                        + ".").withStyle(s -> s.withColor(0xFFD37A)), false);
            }
            shout(server, Component.literal(name(champion) + " takes the tournament, and "
                    + pot + " with it.").withStyle(s -> s.withColor(0xFFD37A)));
        }
        reset();
        broadcast(server);
    }

    private static void reset() {
        phase = Phase.IDLE;
        pot = 0;
        host = null;
        ENTRANTS.clear();
        ROUNDS.clear();
    }

    public static void onLogout(ServerPlayer p) {
        if (phase == Phase.SIGNUP) withdraw(p);
    }

    // ---------------------------------------------------------------- reading it out

    private static String name(UUID id) {
        if (id == null) return "nobody";
        String n = NAMES.get(id);
        return n == null ? "someone" : n;
    }

    /**
     * The whole board as lines, which is all the screen needs and far less than a record tree.
     * First line is the state; then one {@code E} line per entrant, then one {@code R} line per
     * pairing.
     */
    public static String blob(ServerPlayer viewer) {
        StringBuilder sb = new StringBuilder();
        sb.append(phase.name()).append('\t').append(mode.id).append('\t').append(fee)
                .append('\t').append(pot).append('\t').append(entered(viewer.getUUID()) ? 1 : 0)
                .append('\t').append(op(viewer) ? 1 : 0).append('\t').append(Loot.count(viewer));
        if (phase == Phase.SIGNUP) {
            for (String n : ENTRANTS.values()) sb.append('\n').append("E\t").append(n);
        }
        for (int r = 0; r < ROUNDS.size(); r++) {
            for (Pair pr : ROUNDS.get(r)) {
                sb.append('\n').append("R\t").append(r + 1).append('\t').append(name(pr.a))
                        .append('\t').append(pr.b == null ? "—" : name(pr.b)).append('\t')
                        .append(pr.done ? (pr.b == null ? "bye" : name(pr.winner))
                                : pr.running ? "fighting" : "waiting");
            }
        }
        return sb.toString();
    }

    private static void broadcast(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (TourneyNet.watching(p)) TourneyNet.sendBoard(p);
        }
    }

    private static void shout(MinecraftServer server, Component msg) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(msg);
    }
}
