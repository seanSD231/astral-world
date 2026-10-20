package com.astralspace;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The story stage of the world. 0 = nothing yet.
 *   1  starts when the first player ever reaches Seq 0 (AstralServer).
 *   2  starts when two Seq 0 players are online at the same time.
 *   3  starts stage3_days (default 7) real days after stage 2 began. The barrier takes 50 damage, then slow damage (10 per period).
 *   4  starts stage4_days (default 30) real days after stage 3 began: the barrier takes damage much faster and the gods
 *      lay siege to it (all of them online = a near-fatal assault).
 *   5  starts the moment the barrier breaks, in any stage: "The end is here", 10 s of nothing, silence, chaos (EndChaos).
 * Every stage can have a message every player sees once (online players right away, offline players on their next
 * login, in order), plus a follow-up message for some players (e.g. "Protect the barrier" for Seq 0s).
 * Other code asks Stage.atLeast(server, n).
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class Stage {
    private static final int GAP_TICKS = 9 * 20;       // delay before a follow-up message
    private static final int NEXT_TICKS = 11 * 20;     // delay before the next stage's message

    static final String PLANET = "The planet is theirs, give up";
    static final String END = "The end is here";
    static final String RUN = "RUN";
    static final String HELP = "Help people leave";

    /** Message colors {from, to}: each message is coloured to fit what it says. */
    private static final Map<String, int[]> THEMES = new HashMap<>();
    static {
        THEMES.put("You feel a strange gaze from above.", new int[]{0x000000, 0xC8D8FF});   // black to a cold, watching pale blue
        THEMES.put("It's coming", new int[]{0x1A0000, 0xFF6A00});                           // black to ember orange: something rising
        THEMES.put("Protect the barrier", new int[]{0x3A0A80, 0xD9A8FF});                    // the barrier's own purple
        THEMES.put("They're watching, they're waiting", new int[]{0x000000, 0x6BFF8A});     // black to sickly eerie green
        THEMES.put("It's too late", new int[]{0x000000, 0xFF1010});                         // black to blood red
        THEMES.put("The barrier is at risk", new int[]{0x7A3CFF, 0xFF3050});                // barrier purple turning to danger red
        THEMES.put(PLANET, new int[]{0x000000, 0x8A0A5A});                                  // black to a bruised, hopeless crimson-violet
        THEMES.put(END, new int[]{0xFFFFFF, 0x300000});                                     // white fading into dried blood and dark
        THEMES.put(RUN, new int[]{0xFFFFFF, 0xFF0000});
        THEMES.put(HELP, new int[]{0x8A0A00, 0xFFD060});
        THEMES.put("The gods strike the barrier!", new int[]{0x7A3CFF, 0xFF1010});
    }
    private static final int[] DEFAULT_THEME = {0x000000, 0xDC143C};

    private static int[] theme(String message) { return THEMES.getOrDefault(message, DEFAULT_THEME); }

    private record Queued(String text, long due) {}
    private static final Map<UUID, ArrayDeque<Queued>> queues = new HashMap<>();

    private Stage() {}

    public static int current(MinecraftServer server) { return AstralData.get(server).stage; }

    public static boolean atLeast(MinecraftServer server, int stage) { return current(server) >= stage; }

    // ------------------------------------------------------------------ messages

    /** The message every player sees for a stage, or null if the stage has none. */
    private static String globalMessage(AstralData d, int stage) {
        return switch (stage) {
            case 1 -> "You feel a strange gaze from above.";
            case 2 -> "It's coming";
            case 3 -> d.stage3Text.isEmpty() ? "They're watching, they're waiting" : d.stage3Text;
            case 4 -> PLANET;
            case 5 -> d.endPhase.equals("evac") ? RUN : END;
            default -> null;
        };
    }

    /** A second message that only some players get, after the global one. Null if this player gets none. */
    private static String followUp(AstralData d, int stage, ServerPlayer p) {
        int seq = Lotm.sequence(p);
        if (stage == 5 && d.endPhase.equals("evac") && Lotm.isDoor(p) && seq >= 0 && seq <= 2) return HELP;
        if (stage == 2 && seq == 0) return "Protect the barrier";
        if (stage == 3 && seq >= 0 && seq <= 1) return "The barrier is at risk"; // Seq 1 and stronger
        return null;
    }

    // ------------------------------------------------------------------ changing stage

    /** Moves the world to a stage (up or down) and applies that stage's one-time effects. */
    public static void set(MinecraftServer server, int stage) {
        AstralData d = AstralData.get(server);
        int old = d.stage;
        d.stage = Math.max(0, stage);
        d.stageSince = System.currentTimeMillis();
        d.damageAccum = 0.0;
        siegeEnd = 0; nextSiege = 0;
        d.stage5Seen = false;
        if (d.stage != 5) EndChaos.unmuteAll(server);
        // lowering the stage lets players see the later messages again when the stage comes back
        d.seenStage.replaceAll((k, v) -> Math.min(v, d.stage));
        d.setDirty();
        EndGame.stageChanged(server, old, d.stage);
        if (d.stage == 3) {
            // the message depends on how healthy the barrier is right now, then the barrier takes 50 damage
            d.stage3Text = AstralBarrier.get(server) > 50 ? "They're watching, they're waiting" : "It's too late";
            d.setDirty();
            AstralBarrier.damage(server, 50);
        }
    }

    /** Called when the barrier drops to 0 HP: the end begins at once, whatever the stage. */
    public static void onBarrierBroken(MinecraftServer server) {
        if (current(server) != 5) set(server, 5);
    }

    /**
     * Once a second. seq0Online = how many Seq 0 players are online right now (any dimension, any game mode).
     */
    public static void tick(MinecraftServer server, int seq0Online) {
        AstralData d = AstralData.get(server);
        long nowMs = System.currentTimeMillis();
        if (d.stage == 1) {
            if (seq0Online >= 2) set(server, 2);
        } else if (d.stage == 2) {
            if (nowMs - d.stageSince >= (long) (Settings.stage3Days * 86_400_000.0)) set(server, 3);
        } else if (d.stage == 3 && nowMs - d.stageSince >= (long) (Settings.stage4Days * 86_400_000.0)) {
            set(server, 4);
        } else if (d.stage == 4) {
            stage4(server, d, seq0Online, nowMs);
        } else if (d.stage == 3) {
            // 1 damage per period; halved with no Seq 0 online; doubled for every Seq 0 online beyond the first
            double perHour = 60.0 / Settings.barrierDamageMinutes;
            double multiplier = seq0Online == 0 ? 0.5 : Math.pow(2.0, seq0Online - 1);
            d.damageAccum += perHour * multiplier / 3600.0; // this runs once a second
            d.setDirty();
            while (d.damageAccum >= 1.0 && d.stage == 3) {
                d.damageAccum -= 1.0;
                AstralBarrier.damage(server, 10); // reaching 0 moves the world to stage 5
            }
        }
    }

    // ------------------------------------------------------------------ stage 4: the barrier under siege

    private static long siegeEnd = 0, nextSiege = 0;
    private static double siegeRemaining = 0, siegePerSecond = 0, siegeAccum = 0;

    private static void stage4(MinecraftServer server, AstralData d, int online, long nowMs) {
        // constant pressure: the stage 3 damage, several times faster
        double perHour = 60.0 / Settings.barrierDamageMinutes;
        double multiplier = online == 0 ? 0.5 : Math.pow(2.0, online - 1);
        d.damageAccum += perHour * multiplier * Settings.stage4DamageMultiplier / 3600.0;
        d.setDirty();
        while (d.damageAccum >= 1.0 && d.stage == 4) {
            d.damageAccum -= 1.0;
            AstralBarrier.damage(server, 10);
        }
        if (d.stage != 4) return;

        // sieges: the gods that are online strike the barrier together. Everyone online = a near-fatal assault.
        if (siegeEnd > 0) {
            int defenders = 0;
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (!p.isSpectator() && Lotm.sequence(p) == 0 && p.level().dimension().equals(AstralServer.ASTRAL)) defenders++;
            double rate = siegePerSecond * Math.max(0.2, 1.0 - 0.3 * defenders); // Seq 0s in the Astral World hold the barrier up
            double hit = Math.min(siegeRemaining, rate);
            siegeRemaining -= hit;
            siegeAccum += hit;
            int whole = (int) siegeAccum;
            if (whole > 0) { siegeAccum -= whole; AstralBarrier.damage(server, whole); }
            if (nowMs >= siegeEnd) { siegeEnd = 0; nextSiege = nowMs + (4 + (long) (Math.random() * 5)) * 60_000L; }
        } else if (online >= 1) {
            if (nextSiege == 0) nextSiege = nowMs + (3 + (long) (Math.random() * 4)) * 60_000L;
            if (nowMs >= nextSiege) {
                int total = Math.max(1, d.seq0.size());
                double ratio = Math.min(1.0, online / (double) total);
                double amount = ratio * ratio * 0.55 * AstralBarrier.getMax(server); // all gods online: more than half the barrier
                siegeEnd = nowMs + 60_000L;
                siegeRemaining = amount;
                siegePerSecond = amount / 60.0;
                siegeAccum = 0;
                for (ServerPlayer p : server.getPlayerList().getPlayers())
                    if (Lotm.sequence(p) >= 0 && Lotm.sequence(p) <= 1) show(p, "The gods strike the barrier!");
            }
        }
    }

    // ------------------------------------------------------------------ showing messages

    /** Once a second: queue the messages a player has not seen, and show the ones that are due. */
    public static void notifyPass(MinecraftServer server) {
        AstralData d = AstralData.get(server);
        long now = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            int seen = d.seenStage.getOrDefault(id, 0);
            if (seen < d.stage) {
                ArrayDeque<Queued> q = queues.computeIfAbsent(id, k -> new ArrayDeque<>());
                long t = now;
                int seq = Lotm.sequence(p);
                for (int st = seen + 1; st <= d.stage; st++) {
                    String m = globalMessage(d, st);
                    if (m == null) continue;
                    if (st == 4 && d.stage >= 5) continue; // the end came first: skip the stage 4 message
                    q.add(new Queued(m, t));
                    String f = followUp(d, st, p);
                    if (f != null) { t += GAP_TICKS; q.add(new Queued(f, t)); }
                    t += NEXT_TICKS;
                }
                d.seenStage.put(id, d.stage);
                d.setDirty();
            }
            ArrayDeque<Queued> q = queues.get(id);
            while (q != null && !q.isEmpty() && q.peek().due() <= now) show(p, q.poll().text());
        }
    }

    private static void show(ServerPlayer p, String message) {
        int[] th = theme(message);
        PacketDistributor.sendToPlayer(p, new Net.StagePayload(message, th[0], th[1], ""));
        p.sendSystemMessage(gradient(message, th[0], th[1])); // a copy in chat, so it can be re-read
        BuiltInRegistries.SOUND_EVENT.getHolder(ResourceLocation.withDefaultNamespace("entity.warden.heartbeat")).ifPresent(h ->
                p.connection.send(new ClientboundSoundPacket(h, SoundSource.MASTER, p.getX(), p.getY(), p.getZ(), 1.0f, 0.6f, p.getRandom().nextLong())));
    }

    /** A gradient in chat in the message's colours (very dark colours are lifted, black would be unreadable on the chat background). */
    private static MutableComponent gradient(String text, int from, int to) {
        MutableComponent out = Component.empty();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            float t = n > 1 ? i / (float) (n - 1) : 0f;
            int rgb = 0;
            for (int shift = 16; shift >= 0; shift -= 8) {
                int a = (from >> shift) & 255, b = (to >> shift) & 255;
                rgb |= (int) (a + (b - a) * t) << shift;
            }
            int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
            int lift = Math.max(0, 90 - Math.max(r, Math.max(g, b))); // keep at least some brightness
            rgb = (Math.min(255, r + lift) << 16) | (Math.min(255, g + lift / 3) << 8) | Math.min(255, b + lift / 2);
            out.append(Component.literal(String.valueOf(text.charAt(i))).withStyle(Style.EMPTY.withColor(rgb)));
        }
        return out;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("astralstage")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("get").executes(ctx -> {
                    MinecraftServer s = ctx.getSource().getServer();
                    AstralData d = AstralData.get(s);
                    double days = (System.currentTimeMillis() - d.stageSince) / 86_400_000.0;
                    String msg = "Astral World stage: " + d.stage + " (for " + String.format("%.2f", days) + " days, barrier "
                            + AstralBarrier.get(s) + "/" + AstralBarrier.getMax(s) + ")";
                    ctx.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.LIGHT_PURPLE), false);
                    return 1;
                }))
                .then(Commands.literal("set").then(Commands.argument("stage", IntegerArgumentType.integer(0)).executes(ctx -> {
                    int s = IntegerArgumentType.getInteger(ctx, "stage");
                    set(ctx.getSource().getServer(), s);
                    ctx.getSource().sendSuccess(() -> Component.literal("Astral World stage set to " + s), true);
                    return 1;
                }))));
    }
}
