package com.astralspace;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stage 1 effects (the Outer Gods have noticed the world):
 *  - Sequence 2 and stronger (Seq 2, 1, 0): rarely hear audio hallucinations, about once every half hour,
 *    lasting 5-10 seconds. They are vanilla sounds played low, wrong and from the wrong direction.
 *  - Anyone weaker than Seq 2 (Seq 3-9) who names an Outer God in chat or in a messaging command is hit with
 *    deadly corruption (LOTM itself does the killing once corruption is maxed). The message is swallowed.
 *    Creative and spectator players are exempt, and so are players who are not Beyonders.
 * The names live in config/astral_world/outer_god_names.txt (one per line, edit it any time, /astralnames reload).
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class OuterGods {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int HALLUCINATE_AT_OR_STRONGER = 2; // Seq 2, 1, 0
    private static final int PUNISH_AT_OR_WEAKER = 3;        // Seq 3-9
    private static final int MIN_GAP_TICKS = 20 * 60 * 20;   // 20 min
    private static final int MAX_GAP_TICKS = 20 * 60 * 40;   // 40 min
    private static final int MIN_LEN_TICKS = 5 * 20;
    private static final int MAX_LEN_TICKS = 10 * 20;

    private static final String[] SOUNDS = {
            "ambient.cave", "entity.warden.heartbeat", "entity.enderman.stare", "entity.elder_guardian.curse",
            "entity.ghast.scream", "entity.wither.ambient", "entity.ender_dragon.growl", "block.sculk_shrieker.shriek",
            "ambient.soul_sand_valley.mood", "block.portal.ambient", "entity.warden.nearby_closest", "entity.phantom.ambient",
            "block.sculk_sensor.clicking", "entity.zombie_villager.converted"
    };

    private static final String DEFAULT_NAMES = String.join("\n",
            "# Names of the Outer Gods. One per line. Case, spaces and punctuation are ignored when matching.",
            "# Anyone weaker than Seq 2 who writes one of these (once stage 1 has started) is hit with deadly corruption.",
            "# From the Lord of the Mysteries wiki. Lines starting with # are ignored. Use /astralnames reload after editing.",
            "Mother Goddess of Depravity",
            "Brood Hive of Filth",
            "Mother Tree of Desire",
            "Father of Devils",
            "Lord of Deviants",
            "Perpetual Blatherer",
            "Son of Chaos",
            "Ever-Uncertain Mist",
            "Uncertain Mist",
            "Bridge of Light and Darkness",
            "Primordial Hunger",
            "Circle of Inevitability",
            "Supernova Dominator",
            "Inextinguishable Ravings",
            "Monarch of Decay",
            "High-Dimensional Overseer",
            "Goddess of Fate",
            "# Abbreviations: a line starting with @ is matched only as a whole word (so it never fires inside another word).",
            "# These are built in already (mgod = Mother Goddess of Depravity, mtod = Mother Tree of Desire, fod = Father of",
            "# Devils, hdo = High-Dimensional Overseer). Add more like this, or remove the # to use the extras below:",
            "#@SOCH",
            "#@SCHAOS",
            "#@PHUNGER",
            "# The collective names. Remove the # to punish these too:",
            "#Outer God",
            "#Outer Deity",
            "#Outer Deities",
            "#Great Old Ones",
            "");

    /** Abbreviations that are always active (matched as whole words, so "mgod" never triggers inside another word). */
    private static final String[] BUILTIN_ABBREVIATIONS = { "mgod", "mtod", "fod", "hdo" };

    private static final String DEFAULT_PATHWAYS = String.join("\n",
            "# Which LOTM pathway's corruption each Outer God inflicts. Format:  name = pathway",
            "# Pathway ids: seer(Fool) marauder(Error) apprentice(Door) spectator(Visionary) bard(Sun) sailor(Tyrant)",
            "# reader(White Tower) secrets_supplicant(Hanged Man) sleepless(Darkness) corpse_collector(Death)",
            "# warrior(Twilight Giant) assassin(Demoness) hunter(Red Priest) mystery_pryer(Hermit) savant(Paragon)",
            "# monster(Wheel of Fortune) planter(Mother) apothecary(Moon) criminal(Abyss) prisoner(Chained)",
            "# lawyer(Black Emperor) arbiter(Justiciar)",
            "# Any god (or abbreviation) not listed here inflicts the Mother pathway (planter).",
            "Mother Goddess of Depravity = assassin",
            "MGOD = assassin",
            "Father of Devils = criminal",
            "FOD = criminal",
            "Mother Tree of Desire = planter",
            "MTOD = planter",
            "");

    private static List<String> names = new ArrayList<>();
    private static Map<String, String> godPathways = new HashMap<>(); // normalized name -> pathway id
    private static java.util.Set<String> abbreviations = new java.util.HashSet<>();
    private static final Map<UUID, Long> nextHallucination = new HashMap<>();

    private static final class Hallucination {
        final UUID id; final long end; long nextSound;
        Hallucination(UUID id, long end, long nextSound) { this.id = id; this.end = end; this.nextSound = nextSound; }
    }
    private static final List<Hallucination> active = new ArrayList<>();

    private static final class Doom {
        final Object pathway; final long end;
        Doom(Object pathway, long end) { this.pathway = pathway; this.end = end; }
    }
    private static final Map<UUID, Doom> doomed = new HashMap<>();

    private OuterGods() {}

    // ------------------------------------------------------------------ names

    private static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static void loadNames() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("astral_world").resolve("outer_god_names.txt");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT_NAMES);
            }
            List<String> loaded = new ArrayList<>();
            java.util.Set<String> abbr = new java.util.HashSet<>(java.util.Arrays.asList(BUILTIN_ABBREVIATIONS));
            for (String line : Files.readAllLines(file)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                if (t.startsWith("@")) {
                    String a = normalize(t.substring(1));
                    if (a.length() >= 2) abbr.add(a);
                    continue;
                }
                String n = normalize(t);
                if (n.length() >= 4) loaded.add(n);
            }
            names = loaded;
            abbreviations = abbr;
            loadPathways();
            LOGGER.info("[astral] loaded {} Outer God names and {} abbreviations from {}", names.size(), abbreviations.size(), file);
        } catch (IOException e) {
            LOGGER.warn("[astral] could not read the Outer God names file: {}", e.toString());
        }
    }

    private static void loadPathways() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("astral_world").resolve("outer_god_pathways.txt");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT_PATHWAYS);
            }
            Map<String, String> map = new HashMap<>();
            for (String line : Files.readAllLines(file)) {
                String t = line.trim();
                int eq = t.indexOf('=');
                if (t.isEmpty() || t.startsWith("#") || eq < 1) continue;
                String key = normalize(t.substring(0, eq));
                String id = t.substring(eq + 1).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
                if (!key.isEmpty() && !id.isEmpty()) map.put(key, id);
            }
            godPathways = map;
        } catch (IOException e) {
            LOGGER.warn("[astral] could not read outer_god_pathways.txt: {}", e.toString());
        }
    }

    /** The pathway whose corruption this Outer God inflicts: the one listed for it, otherwise Mother (planter). */
    private static Object godPathway(String match) {
        Object p = Lotm.pathwayById(godPathways.getOrDefault(match, "planter"));
        if (p == null) p = Lotm.pathwayById("planter");
        return p;
    }

    /** The name or abbreviation that was found in the text, or null. */
    private static String findMention(String text) {
        String n = normalize(text);
        for (String name : names) if (n.contains(name)) return name;
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() < 2) continue;
            if (abbreviations.contains(token)) return token;
            // plural: "mgods" -> "mgod"
            if (token.endsWith("s") && abbreviations.contains(token.substring(0, token.length() - 1))) return token.substring(0, token.length() - 1);
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) { loadNames(); }

    // ------------------------------------------------------------------ stage 2+: the name echoes to people nearby

    /** An Outer God: the names and abbreviations (normalized) that belong to it, and the lasting effect it leaves. */
    private record God(String[] aliases, Holder<MobEffect> effect, int amplifier) {}

    private static final God[] GODS = {
            new God(new String[]{"mothergoddessofdepravity", "broodhiveoffilth", "mgod"}, MobEffects.WEAKNESS, 1),
            new God(new String[]{"mothertreeofdesire", "fatherofdevils", "lordofdeviants", "perpetualblatherer", "mtod", "fod"}, MobEffects.UNLUCK, 1),
            new God(new String[]{"sonofchaos", "everuncertainmist", "uncertainmist", "bridgeoflightanddarkness"}, MobEffects.CONFUSION, 0),
            new God(new String[]{"primordialhunger"}, MobEffects.HUNGER, 2),
            new God(new String[]{"circleofinevitability"}, MobEffects.MOVEMENT_SLOWDOWN, 1),
            new God(new String[]{"supernovadominator"}, MobEffects.DIG_SLOWDOWN, 1),
            new God(new String[]{"inextinguishableravings"}, MobEffects.DARKNESS, 0),
            new God(new String[]{"monarchofdecay"}, MobEffects.POISON, 0),
            new God(new String[]{"highdimensionaloverseer", "hdo"}, MobEffects.GLOWING, 0),
            new God(new String[]{"goddessoffate"}, MobEffects.UNLUCK, 2),
    };
    /** Used for names added to the names file that are not in the table above. */
    private static final God UNKNOWN_GOD = new God(new String[0], MobEffects.DARKNESS, 0);

    private static God godFor(String match) {
        for (God g : GODS) for (String a : g.aliases()) if (a.equals(match)) return g;
        return UNKNOWN_GOD;
    }

    /**
     * Stage 2: when someone names an Outer God, everyone nearby gets half the corruption and a lasting effect of that
     * god. Stage 3 makes it stronger: 75% corruption, twice the range, a longer and stronger effect.
     */
    private static void spillover(MinecraftServer server, ServerPlayer speaker, String match) {
        boolean strong = Stage.atLeast(server, 3);
        double radius = Settings.nearbyRadius * (strong ? 2.0 : 1.0);
        double percent = strong ? 75 : 50;
        God god = godFor(match);
        int duration = (strong ? 20 : 10) * 60 * 20; // 10 minutes, 20 in stage 3
        int amplifier = god.amplifier() + (strong ? 1 : 0);
        for (ServerPlayer q : server.getPlayerList().getPlayers()) {
            if (q == speaker || q.isCreative() || q.isSpectator()) continue;
            if (q.level() != speaker.level() || q.distanceToSqr(speaker) > radius * radius) continue;
            if (Lotm.sequence(q) >= 0) {
                Object pathway = godPathway(match); // the god's own pathway (Mother if it has none)
                if (pathway != null) Lotm.addCorruptionPercent(q, pathway, percent);
            }
            q.addEffect(new MobEffectInstance(god.effect(), duration, amplifier, false, true));
            q.displayClientMessage(Component.literal("A name was spoken nearby...").withStyle(ChatFormatting.DARK_RED), true);
        }
        LOGGER.info("[astral] {} named an Outer God ('{}'): nearby players were affected (radius {}, {}% corruption)",
                speaker.getGameProfile().getName(), match, radius, percent);
    }

    // ------------------------------------------------------------------ the punishment

    /** Stage 1: Seq 3-9 die. Each stage adds one more sequence (stage 2: Seq 2-9), but Seq 1 and Seq 0 are never killed. */
    private static int punishLine(MinecraftServer server) {
        return Math.max(2, PUNISH_AT_OR_WEAKER - (Math.max(1, Stage.current(server)) - 1));
    }

    /** Why this player is NOT punished for naming an Outer God, or null if they would be. */
    private static String exemptReason(MinecraftServer server, ServerPlayer p) {
        if (!Stage.atLeast(server, 1)) return "the stage is 0 (run /astralstage set 1)";
        if (p.isCreative() || p.isSpectator()) return "creative and spectator players are exempt (switch to survival)";
        int seq = Lotm.sequence(p);
        if (seq < 0) return "you are not a Beyonder (no LOTM sequence found)";
        int line = punishLine(server);
        if (seq < line) return "Seq " + seq + " is stronger than the current kill line (Seq " + line + " and weaker die in stage " + Stage.current(server) + "; Seq 1 and 0 are never killed)";
        return null;
    }

    /** Ops are told why nothing happened, so testing is not guesswork. */
    private static void tellOp(ServerPlayer p, String match, String reason) {
        if (p.hasPermissions(2)) {
            p.sendSystemMessage(Component.literal("[astral] Outer God name '" + match + "' noticed, but nothing happens: " + reason)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static void punish(MinecraftServer server, ServerPlayer p, String match) {
        Object pathway = godPathway(match); // the god's own pathway (Mother if it has none)
        if (pathway == null) pathway = Lotm.pathway(p);
        if (pathway == null) pathway = Lotm.randomPathway();
        if (pathway == null) {
            LOGGER.warn("[astral] {} named an Outer God but LOTM corruption could not be applied", p.getGameProfile().getName());
            return;
        }
        Lotm.addCorruptionPercent(p, pathway, 100); // straight to max; LOTM's own handler then kills them
        doomed.put(p.getUUID(), new Doom(pathway, server.getTickCount() + 20 * 60));
        p.displayClientMessage(Component.literal("Something heard you.").withStyle(ChatFormatting.DARK_RED), true);
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0, false, false));
        playAt(p, "entity.warden.heartbeat", p.getX(), p.getY(), p.getZ(), 1.0f, 0.5f);
        LOGGER.info("[astral] {} named an Outer God and was struck with deadly corruption", p.getGameProfile().getName());
    }

    private static boolean chatHookLogged = false;

    // Highest priority and receiveCanceled: a chat-formatting mod in this pack cancels the normal chat event and sends
    // the message itself, so a normal listener never saw the text.
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onChat(ServerChatEvent event) {
        ServerPlayer p = event.getPlayer();
        MinecraftServer server = p.getServer();
        if (server == null) return;
        if (!chatHookLogged) {
            chatHookLogged = true;
            LOGGER.info("[astral] chat hook is working (first chat message seen, already cancelled by another mod: {})", event.isCanceled());
        }
        String match = findMention(event.getRawText());
        if (match == null) return;
        String reason = exemptReason(server, p);
        if (Stage.atLeast(server, 2) && !p.isSpectator()) server.execute(() -> spillover(server, p, match)); // stage 2+: the name echoes
        if (reason != null) { tellOp(p, match, reason); return; }
        LOGGER.info("[astral] {} named an Outer God ('{}') in chat", p.getGameProfile().getName(), match);
        event.setCanceled(true); // the words never reach anyone (unless another mod already sent them)
        server.execute(() -> punish(server, p, match));
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        var source = event.getParseResults().getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer p)) return;
        String text = event.getParseResults().getReader().getString();
        String lower = text.startsWith("/") ? text.substring(1) : text;
        String first = lower.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!first.equals("msg") && !first.equals("tell") && !first.equals("w") && !first.equals("say")
                && !first.equals("me") && !first.equals("teammsg") && !first.equals("tm")) return;
        MinecraftServer server = p.getServer();
        if (server == null) return;
        String match = findMention(lower);
        if (match == null) return;
        String reason = exemptReason(server, p);
        if (Stage.atLeast(server, 2) && !p.isSpectator()) spillover(server, p, match);
        if (reason != null) { tellOp(p, match, reason); return; }
        event.setCanceled(true);
        punish(server, p, match);
    }

    // ------------------------------------------------------------------ hallucinations

    private static void playAt(ServerPlayer p, String soundId, double x, double y, double z, float volume, float pitch) {
        BuiltInRegistries.SOUND_EVENT.getHolder(ResourceLocation.withDefaultNamespace(soundId)).ifPresent(h ->
                p.connection.send(new ClientboundSoundPacket(h, SoundSource.AMBIENT, x, y, z, volume, pitch, p.getRandom().nextLong())));
    }

    private static void hallucinate(MinecraftServer server, ServerPlayer p) {
        boolean strong = Stage.atLeast(server, 3); // stage 3: closer, louder, more obvious
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String id = SOUNDS[r.nextInt(SOUNDS.length)];
        double angle = r.nextDouble() * Math.PI * 2;
        double dist = strong ? 2 + r.nextDouble() * 5 : 4 + r.nextDouble() * 8;
        playAt(p, id,
                p.getX() + Math.cos(angle) * dist,
                p.getY() + r.nextDouble() * 3 - 1,
                p.getZ() + Math.sin(angle) * dist,
                strong ? 1.4f + r.nextFloat() * 0.8f : 0.7f + r.nextFloat() * 0.5f,
                0.5f + r.nextFloat() * (strong ? 0.15f : 0.3f)); // 0.5 is the lowest pitch Minecraft allows
    }

    public static void startHallucination(MinecraftServer server, ServerPlayer p) {
        long now = server.getTickCount();
        boolean strong = Stage.atLeast(server, 3);
        int min = strong ? MIN_LEN_TICKS * 2 : MIN_LEN_TICKS, max = strong ? MAX_LEN_TICKS * 2 : MAX_LEN_TICKS; // 10-20 s in stage 3
        long len = ThreadLocalRandom.current().nextInt(min, max + 1);
        active.add(new Hallucination(p.getUUID(), now + len, now));
        // Stage 3: Seq 1 and Seq 2 lose 25% of their max sanity whenever a distortion plays on them (Seq 0 is spared)
        int seq = Lotm.sequence(p);
        if (strong && (seq == 1 || seq == 2)) {
            boolean ok = Lotm.loseSanityPercent(p, 25);
            LOGGER.info("[astral] {} (Seq {}) lost 25% max sanity to a distortion: {}", p.getGameProfile().getName(), seq, ok);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();

        // keep corrupting anyone who named an Outer God until LOTM has finished them (or a minute passes)
        if (now % 10 == 0 && !doomed.isEmpty()) {
            Iterator<Map.Entry<UUID, Doom>> it = doomed.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Doom> e = it.next();
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p == null || !p.isAlive() || now > e.getValue().end) { it.remove(); continue; }
                Lotm.addCorruptionPercent(p, e.getValue().pathway, 50);
            }
        }

        // the sounds of a running hallucination
        if (now % 5 == 0 && !active.isEmpty()) {
            Iterator<Hallucination> it = active.iterator();
            while (it.hasNext()) {
                Hallucination h = it.next();
                ServerPlayer p = server.getPlayerList().getPlayer(h.id);
                if (p == null || now > h.end) { it.remove(); continue; }
                if (now >= h.nextSound) {
                    hallucinate(server, p);
                    h.nextSound = now + (Stage.atLeast(server, 3) ? 5 + ThreadLocalRandom.current().nextInt(15) : 10 + ThreadLocalRandom.current().nextInt(30));
                }
            }
        }

        // schedule new hallucinations: once per player roughly every 20-40 minutes
        if (now % 20 == 0 && Stage.atLeast(server, 1)) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.isSpectator()) continue;
                int seq = Lotm.sequence(p);
                if (seq < 0 || seq > HALLUCINATE_AT_OR_STRONGER) continue;
                UUID id = p.getUUID();
                Long next = nextHallucination.get(id);
                boolean strong = Stage.atLeast(server, 3);
                int minGap = strong ? MIN_GAP_TICKS / 2 : MIN_GAP_TICKS; // stage 3: every 10-20 minutes
                int maxGap = strong ? MAX_GAP_TICKS / 2 : MAX_GAP_TICKS;
                if (next == null) {
                    nextHallucination.put(id, now + ThreadLocalRandom.current().nextInt(0, maxGap));
                } else if (now >= next) {
                    startHallucination(server, p);
                    nextHallucination.put(id, now + ThreadLocalRandom.current().nextInt(minGap, maxGap + 1));
                }
            }
        }
    }

    // ------------------------------------------------------------------ commands (ops)

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("astralnames")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("reload").executes(ctx -> {
                    loadNames();
                    int n = names.size(), a = abbreviations.size();
                    ctx.getSource().sendSuccess(() -> Component.literal("Loaded " + n + " Outer God names and " + a + " abbreviations."), true);
                    return 1;
                }))
                .then(Commands.literal("test").then(Commands.argument("text", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
                    String text = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "text");
                    String match = findMention(text);
                    String msg = match == null ? "No Outer God name found in that text."
                            : "Found '" + match + "'. Whether you would be punished: ";
                    var src = ctx.getSource();
                    if (match != null && src.getEntity() instanceof ServerPlayer p) {
                        String reason = exemptReason(src.getServer(), p);
                        msg += reason == null ? "YES" : "no (" + reason + ")";
                    }
                    String out = msg;
                    src.sendSuccess(() -> Component.literal(out), false);
                    return 1;
                }))));

        event.getDispatcher().register(Commands.literal("astralhallucinate")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    startHallucination(ctx.getSource().getServer(), p);
                    ctx.getSource().sendSuccess(() -> Component.literal("Hallucination started (5-10 seconds)."), false);
                    return 1;
                }));
    }
}
