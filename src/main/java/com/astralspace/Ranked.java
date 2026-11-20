package com.astralspace;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ranked duelling: a queue, a rating, and a way to turn what you have won into money.
 *
 * <p>The rating is Elo, which is the only sensible choice here: it needs no seasons, no decay and
 * no table of brackets to be fair, it settles quickly, and beating somebody far below you is worth
 * almost nothing while beating somebody far above you is worth a great deal. Everybody starts at a
 * thousand. Nobody can fall below a hundred, so a bad night cannot put a player somewhere they can
 * never climb out of.</p>
 *
 * <p>The queue matches on how close two ratings are and widens as people wait, so a quiet server
 * still gets games and a busy one still gets fair ones. Ranked matches are always safe duels --
 * putting a player's sequence and inventory on a ladder they are meant to climb would stop anybody
 * queueing twice.</p>
 *
 * <p>Ratings live in the player's own persistent data, so they survive a relog and travel with the
 * player rather than needing a file of their own.</p>
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class Ranked {
    private Ranked() { }

    private static final String TAG = "astral_ranked";
    private static final int START = 1000, FLOOR = 100;
    /** How far apart two ratings may be at first, and how much further every second of waiting. */
    private static final int WINDOW = 60, WIDEN = 14;

    /** A rating and the record behind it. */
    private record Record(int rating, int wins, int losses, int points) {
        Record with(int r, boolean won, int pts) {
            return new Record(r, wins + (won ? 1 : 0), losses + (won ? 0 : 1), points + pts);
        }
    }

    private static final Map<UUID, Record> BOOK = new HashMap<>();
    private static final Map<UUID, Long> QUEUE = new java.util.LinkedHashMap<>();   // who is waiting, since when

    // ---- the ladder ----

    /** What a rating is called. The bands are wide at the bottom and narrow at the top, as they should be. */
    public static String rank(int r) {
        if (r < 700) return "Unranked";
        if (r < 900) return "Sequence 9";
        if (r < 1050) return "Sequence 8";
        if (r < 1200) return "Sequence 7";
        if (r < 1350) return "Sequence 6";
        if (r < 1500) return "Sequence 5";
        if (r < 1650) return "Sequence 4";
        if (r < 1800) return "Sequence 3";
        if (r < 2000) return "Sequence 2";
        if (r < 2250) return "Sequence 1";
        return "Sequence 0";
    }

    public static int colour(int r) {
        if (r < 900) return 0xA0A0A8;
        if (r < 1200) return 0x8AC88A;
        if (r < 1500) return 0x6FA2FF;
        if (r < 1800) return 0xC566E6;
        if (r < 2250) return 0xFFAA00;
        return 0xFFD24A;
    }

    // ---- the book ----

    private static Record read(ServerPlayer p) {
        Record r = BOOK.get(p.getUUID());
        if (r != null) return r;
        CompoundTag root = p.getPersistentData();
        if (root.contains(TAG)) {
            CompoundTag t = root.getCompound(TAG);
            r = new Record(t.getInt("r"), t.getInt("w"), t.getInt("l"), t.getInt("p"));
        } else r = new Record(START, 0, 0, 0);
        BOOK.put(p.getUUID(), r);
        return r;
    }

    private static void write(ServerPlayer p, Record r) {
        BOOK.put(p.getUUID(), r);
        CompoundTag t = new CompoundTag();
        t.putInt("r", r.rating()); t.putInt("w", r.wins()); t.putInt("l", r.losses()); t.putInt("p", r.points());
        p.getPersistentData().put(TAG, t);
    }

    public static int ratingOf(ServerPlayer p) { return read(p).rating(); }

    // ---- the queue ----

    private static void join(ServerPlayer p) {
        if (Duel.dueling(p)) { tell(p, "Finish the duel you are in first.", ChatFormatting.RED); return; }
        if (HungerGames.fighting(p)) { tell(p, "Not during the hunger games.", ChatFormatting.RED); return; }
        if (QUEUE.containsKey(p.getUUID())) { tell(p, "You are already in the queue.", ChatFormatting.GRAY); return; }
        QUEUE.put(p.getUUID(), System.currentTimeMillis());
        Record r = read(p);
        p.sendSystemMessage(Component.literal("Queued at " + r.rating() + " · " + rank(r.rating()))
                .withColor(colour(r.rating()))
                .append(Component.literal("   (" + QUEUE.size() + " waiting)").withStyle(ChatFormatting.DARK_GRAY)));
    }

    private static void leave(ServerPlayer p) {
        if (QUEUE.remove(p.getUUID()) == null) tell(p, "You are not in the queue.", ChatFormatting.GRAY);
        else tell(p, "Out of the queue.", ChatFormatting.GRAY);
    }

    /**
     * Pairs whoever is closest in rating, every second. The window opens as people wait, so a
     * server with two players online still gets a game out of them eventually and a busy one
     * still pairs by skill.
     */
    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 20 != 0 || QUEUE.size() < 2) return;
        MinecraftServer server = e.getServer();
        long now = System.currentTimeMillis();

        List<UUID> ids = new ArrayList<>(QUEUE.keySet());
        ids.removeIf(id -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || Duel.dueling(p)) { QUEUE.remove(id); return true; }
            return false;
        });
        ids.sort(Comparator.comparingInt(id -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            return p == null ? 0 : ratingOf(p);
        }));

        for (int i = 0; i + 1 < ids.size(); i++) {
            ServerPlayer a = server.getPlayerList().getPlayer(ids.get(i));
            ServerPlayer b = server.getPlayerList().getPlayer(ids.get(i + 1));
            if (a == null || b == null) continue;
            long waited = (now - Math.min(QUEUE.getOrDefault(a.getUUID(), now), QUEUE.getOrDefault(b.getUUID(), now))) / 1000;
            int window = (int) (WINDOW + WIDEN * waited);
            if (Math.abs(ratingOf(a) - ratingOf(b)) > window) continue;
            QUEUE.remove(a.getUUID());
            QUEUE.remove(b.getUUID());
            if (!Duel.begin(a, b, true)) continue;
            RANKED.add(a.getUUID());
            RANKED.add(b.getUUID());
            Component line = Component.literal("Ranked · ")
                    .append(Component.literal(a.getGameProfile().getName() + " (" + ratingOf(a) + ")").withColor(colour(ratingOf(a))))
                    .append(Component.literal(" vs ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(b.getGameProfile().getName() + " (" + ratingOf(b) + ")").withColor(colour(ratingOf(b))));
            a.sendSystemMessage(line);
            b.sendSystemMessage(line);
            i++;
        }
    }

    // ---- the result ----

    /**
     * Elo, with a K that falls as a player settles: a newcomer moves fast and somebody with a
     * hundred games behind them moves slowly, so the top of the ladder does not swing on one night.
     */
    private static void settle(UUID winner, UUID loser) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerPlayer w = server.getPlayerList().getPlayer(winner), l = server.getPlayerList().getPlayer(loser);
        if (w == null || l == null) return;
        boolean wasRanked = RANKED.remove(winner);
        wasRanked = RANKED.remove(loser) || wasRanked;
        if (!wasRanked) return;                       // a friendly duel: there is nothing to settle

        Record rw = read(w), rl = read(l);
        double expect = 1.0 / (1.0 + Math.pow(10, (rl.rating() - rw.rating()) / 400.0));
        int kw = k(rw), kl = k(rl);
        int gain = (int) Math.round(kw * (1 - expect));
        int lose = (int) Math.round(kl * (1 - expect));
        int nw = rw.rating() + gain;
        int nl = Math.max(FLOOR, rl.rating() - lose);

        // points are not the rating: they are what the rating earned you, and they never go down
        int pts = 10 + Math.max(0, gain) * 2;
        write(w, rw.with(nw, true, pts));
        write(l, rl.with(nl, false, 3));

        w.sendSystemMessage(Component.literal("+" + gain + " → " + nw + " · " + rank(nw))
                .withColor(colour(nw))
                .append(Component.literal("   +" + pts + " RP").withStyle(ChatFormatting.DARK_GRAY)));
        l.sendSystemMessage(Component.literal("-" + (rl.rating() - nl) + " → " + nl + " · " + rank(nl))
                .withColor(colour(nl))
                .append(Component.literal("   +3 RP").withStyle(ChatFormatting.DARK_GRAY)));
        if (!rank(nw).equals(rank(rw.rating())))
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    w.getGameProfile().getName() + " has reached " + rank(nw)).withColor(colour(nw)), false);
    }

    private static int k(Record r) {
        int games = r.wins() + r.losses();
        return games < 10 ? 48 : (games < 40 ? 32 : 20);
    }

    /** Who is in a ranked match rather than a friendly one. */
    private static final java.util.Set<UUID> RANKED = new java.util.HashSet<>();

    // ---- turning it into money ----

    /** A rating point is worth nothing; a ranked point buys a spur. Twenty of them to a coin. */
    private static final int PER_COIN = 20;

    private static void cash(ServerPlayer p, int points) {
        Record r = read(p);
        if (points <= 0 || points > r.points()) {
            tell(p, "You have " + r.points() + " RP.", ChatFormatting.GRAY);
            return;
        }
        int coins = points / PER_COIN;
        if (coins <= 0) { tell(p, PER_COIN + " RP to a coin.", ChatFormatting.GRAY); return; }
        int spent = coins * PER_COIN;
        if (Loot.coin() == null) { tell(p, "There is no money on this server to pay you in.", ChatFormatting.RED); return; }
        write(p, new Record(r.rating(), r.wins(), r.losses(), r.points() - spent));
        Loot.pay(p, coins);
        p.sendSystemMessage(Component.literal("Cashed " + spent + " RP for " + coins + " coins.")
                .withStyle(ChatFormatting.GOLD));
    }

    // ---- commands ----

    /** Hung once, when the server comes up, so a duel ending can never find nobody listening. */
    @SubscribeEvent
    public static void onServerStart(net.neoforged.neoforge.event.server.ServerStartingEvent e) {
        if (!watching) { watching = true; Duel.watch(Ranked::settle); }
    }

    private static boolean watching;

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("ranked")
            .requires(c -> c.getEntity() instanceof ServerPlayer)
            .executes(c -> stats(c.getSource().getPlayerOrException()))
            .then(Commands.literal("queue").executes(c -> { join(c.getSource().getPlayerOrException()); return 1; }))
            .then(Commands.literal("leave").executes(c -> { leave(c.getSource().getPlayerOrException()); return 1; }))
            .then(Commands.literal("stats").executes(c -> stats(c.getSource().getPlayerOrException())))
            .then(Commands.literal("top").executes(c -> top(c.getSource().getPlayerOrException())))
            .then(Commands.literal("cash")
                .executes(c -> { ServerPlayer p = c.getSource().getPlayerOrException(); cash(p, read(p).points()); return 1; })
                .then(Commands.argument("rp", IntegerArgumentType.integer(1))
                    .executes(c -> { cash(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "rp")); return 1; }))));
    }

    private static int stats(ServerPlayer p) {
        Record r = read(p);
        int games = r.wins() + r.losses();
        p.sendSystemMessage(Component.literal(rank(r.rating()) + "  " + r.rating()).withColor(colour(r.rating())));
        p.sendSystemMessage(Component.literal(r.wins() + "W " + r.losses() + "L"
                + (games == 0 ? "" : "  (" + Math.round(100f * r.wins() / games) + "%)")
                + "   ·   " + r.points() + " RP").withStyle(ChatFormatting.GRAY));
        p.sendSystemMessage(Component.literal("/ranked queue to find a match · /ranked cash to turn RP into coins")
                .withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    private static int top(ServerPlayer p) {
        List<ServerPlayer> all = new ArrayList<>(p.server.getPlayerList().getPlayers());
        all.sort(Comparator.comparingInt(Ranked::ratingOf).reversed());
        p.sendSystemMessage(Component.literal("Ranked · online").withStyle(ChatFormatting.GOLD));
        int n = 0;
        for (ServerPlayer x : all) {
            if (n++ >= 10) break;
            int r = ratingOf(x);
            p.sendSystemMessage(Component.literal(" " + n + ". " + x.getGameProfile().getName() + "  " + r
                    + "  " + rank(r)).withColor(colour(r)));
        }
        return 1;
    }

    private static void tell(ServerPlayer p, String s, ChatFormatting f) {
        p.sendSystemMessage(Component.literal(s).withStyle(f));
    }

    // ---- housekeeping ----

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        QUEUE.remove(e.getEntity().getUUID());
        RANKED.remove(e.getEntity().getUUID());
        BOOK.remove(e.getEntity().getUUID());        // already written into their own data
    }
}
