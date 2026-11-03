package com.astralspace;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * What happens after the barrier breaks (stage 5), the part that can be survived. d.endPhase says where we are:
 *   ""        nothing is going on
 *   "classic" the plain end (EndChaos: everyone dies and is kicked, the world resets, the server stops)
 *   "evac"    a Door of Seq 2 or stronger was online, so the message is RUN and a countdown runs. Chaos only strikes
 *             where people are not on another planet. When 75% of the online players stand on another planet
 *             before it ends, Earth is destroyed. Otherwise the plain end follows.
 *   "bosses"  Earth is gone and the overworld is locked. A boss (Seq 0, 10,000 HP) waits on every other planet.
 *             Defeat them all before the time runs out (each kill adds days) or phase 5 happens again, with no countdown.
 *   "raid"    all bosses fell: stage 0 and an Outer God with 1,000,000 HP. Everyone is pulled into the arena with 5 lives.
 *             Losing = phase 5 again with no countdown. Winning = Earth returns and stage 0 lasts recovery_days.
 *   "recover" stage 0 for recovery_days, then the stage from before the first break returns.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class EndGame {
    private static final Logger LOGGER = LogUtils.getLogger();

    static final String TAG_BOSS = "astral_boss", TAG_RAID = "astral_raid", TAG_MINION = "astral_minion";
    private static final String TAG_PLANET = "astral_boss_of:";
    private static final float BOSS_HP = 1000f;        // the sovereigns: the vanilla cap is 1024, so they keep shrinking damage (BOSS_DAMAGE)
    static final float RAID_HP = 1_000_000f;       // the Mother Tree has real health: AstralMod lifts the vanilla max health cap
    private static final float BOSS_DAMAGE = 0.1f;     // 1000 HP / 0.1  = 10,000 effective
    private static final float RAID_DAMAGE = 1f;       // no shrinking: her 1,000,000 HP is real
    private static final int LIVES = 3;
    private static final long DAY = 86_400_000L;

    /** The boss of each planet: a Seq 0 of a pathway that is finished in LOTM. */
    private record BossDef(String planet, String pathway, String title) {}
    private static final List<BossDef> BOSSES = List.of();   // the planets and their sovereigns are gone: only the Mother Tree remains
    private static final String RAID_PATHWAY = "prisoner";
    private static final String RAID_NAME = "Mother Tree of Desire";

    /** Set while this class changes the stage itself (Stage.set must then not reset the end). */
    private static boolean internal = false;
    private static final Map<String, ServerBossEvent> bars = new HashMap<>();
    private static ServerBossEvent raidBar;
    private static UUID traitor = null, offered = null;   // the one fighter who took Desire's offer; the one it is waiting on
    private static boolean offerDone = false;
    private static long offerUntil = 0;
    private static final Map<UUID, Long> noHeal = new HashMap<>();       // player -> tick until which nothing heals them
    private static final Map<UUID, long[]> desire = new HashMap<>();     // player -> {stacks, tick of the last stack}
    private static final Map<UUID, Long> thrallHitAt = new HashMap<>();
    /** What everyone calls her right now: the World Tree until the change at 50%, the Mother Tree of Desire after it. */
    static final String EARLY_TREE = "The World Tree";
    private static String treeName() { return treeFleshy ? RAID_NAME : EARLY_TREE; }
    private static boolean treeFleshy = false;   // after the 50% change she is the Mother Tree of Desire; before it, the World Tree
    private static int lineBucket = 20;          // the last 5% step of her health she has spoken at

    /** The boss bar name: "The World Tree" before the change, the Mother Tree of Desire after it. The clients tell the two forms apart by this name. */
    private static Component barName(String suffix, ChatFormatting c) {
        return Component.literal((treeFleshy ? RAID_NAME : "The World Tree") + suffix).withStyle(c);
    }

    private static final String[] WORLD_TREE_LINES = {
            "Kill me and the Astral World forgets how to hold the sky.",
            "Every root you burn remembers your name.",
            "There is no Earth to return to without me.",
            "You are cutting the branch you stand on.",
            "Kill the tree and the seeds of tomorrow go with it.",
            "I am the reason the ground is solid. Think before you strike.",
            "Your victory is a world with no roots.",
            "When I fall, everything that grew from me falls too.",
            "Killing me is the worst thing you will ever do.",
            "Spare the tree. The wood remembers who was kind.",
            "Do you know what rots when a world tree dies?",
            "Strike again and learn what the sky does without its pillar."};

    private EndGame() {}

    // ------------------------------------------------------------------ small helpers

    static boolean isOp(ServerPlayer p) { return p.getServer() != null && p.getServer().getPlayerList().isOp(p.getGameProfile()); }

    private static boolean tracking(AstralData d) { return d.endPhase.equals("bosses") || d.endPhase.equals("raid"); }

    private static AstralData.Stat stat(AstralData d, ServerPlayer p) {
        AstralData.Stat s = d.stats.computeIfAbsent(p.getUUID(), k -> new AstralData.Stat());
        s.name = p.getGameProfile().getName();
        d.setDirty();
        return s;
    }

    /** The top 20 by damage, shown to everyone. */
    private static void leaderboard(MinecraftServer server, AstralData d) {
        List<Map.Entry<UUID, AstralData.Stat>> l = new ArrayList<>(d.stats.entrySet());
        l.sort(Comparator.comparingDouble((Map.Entry<UUID, AstralData.Stat> e) -> e.getValue().damage).reversed());
        tell(server, "===== Leaderboard =====", ChatFormatting.GOLD);
        for (int i = 0; i < Math.min(20, l.size()); i++) {
            AstralData.Stat s = l.get(i).getValue();
            ChatFormatting c = i == 0 ? ChatFormatting.GOLD : i < 3 ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
            if (l.get(i).getKey().equals(traitor)) tell(server, String.format("#%d  %s   TRAITOR - damage to other players %,.0f   deaths %d", i + 1, s.name, s.damage, s.deaths), ChatFormatting.DARK_PURPLE);
            else tell(server, String.format("#%d  %s   damage %,.0f   healing %,.0f   deaths %d", i + 1, s.name, s.damage, s.heal, s.deaths), c);
        }
        if (l.isEmpty()) tell(server, "Nobody left a mark.", ChatFormatting.GRAY);
    }

    // ------------------------------------------------------------------ closing the server (operators may stay)

    private static long closeAt = -1;
    private static String closeMessage = "";

    /** After `seconds`, everyone who is not an operator is kicked; the server stops once nobody is left. */
    static void beginClose(MinecraftServer server, int seconds, String message) {
        closeAt = server.getTickCount() + seconds * 20L;
        closeMessage = message;
    }

    private static void closeTick(MinecraftServer server) {
        if (closeAt < 0 || server.getTickCount() < closeAt || server.getTickCount() % 20 != 0) return;
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers()))
            if (!isOp(p)) p.connection.disconnect(Component.literal(closeMessage));
        if (server.getPlayerList().getPlayers().isEmpty()) {
            LOGGER.info("[astral] the end game is over: stopping the server");
            server.overworld().getDataStorage().save();
            closeAt = -1;
            server.halt(false);
        }
    }

    private static boolean isEarthDim(ResourceKey<Level> k) { return k.equals(Cosmos.key("earth")); }

    /** On a planet that is not Earth. */
    static boolean onOtherPlanet(ServerPlayer p) {
        ResourceKey<Level> k = p.level().dimension();
        return Cosmos.isPlanetDim(k) && !isEarthDim(k);
    }

    /** Is chaos allowed to strike this player right now? */
    static boolean inDanger(ServerPlayer p, AstralData d) {
        if (d.endPhase.equals("evac")) return !onOtherPlanet(p) && !p.level().dimension().equals(Cosmos.COSMOS);
        return true;
    }

    static boolean chaosPhase(AstralData d) {
        return d.stage == 5 && (d.endPhase.isEmpty() || d.endPhase.equals("classic") || d.endPhase.equals("evac"));
    }

    static boolean classic(AstralData d) { return d.stage == 5 && (d.endPhase.isEmpty() || d.endPhase.equals("classic")); }

    private static void tell(MinecraftServer server, String msg, ChatFormatting color) {
        Component c = Component.literal(msg).withStyle(color);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(c);
    }

    private static ServerLevel dim(MinecraftServer server, String id) { return server.getLevel(Cosmos.key(id.equals("raid") ? "cosmos" : id)); }   // the arena lives in the Cosmos dimension

    private static void force(ServerLevel lv, int r, boolean on) {
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) lv.setChunkForced(x, z, on);
    }

    private static final Map<UUID, GameType> priorMode = new HashMap<>();

    /** Survival is only forced on spectators (the fallen): creative players stay creative. A spectator goes back to the mode they had before. */
    private static void setGameMode(ServerPlayer p, GameType t) {
        GameType cur = p.gameMode.getGameModeForPlayer();
        if (t == GameType.SPECTATOR) {
            if (cur != GameType.SPECTATOR) { priorMode.put(p.getUUID(), cur); p.setGameMode(t); }
        } else if (cur == GameType.SPECTATOR) {
            GameType back = priorMode.remove(p.getUUID());
            p.setGameMode(back == null || back == GameType.SPECTATOR ? t : back);
        }   // anything else (creative, adventure, survival) is left as it is
    }

    // ------------------------------------------------------------------ beginning (called by Stage.set)

    /** The stage was just set to `stage` (not by this class). `old` is the stage it was before. */
    static void stageChanged(MinecraftServer server, int old, int stage) {
        if (internal) return;
        if (stage == 5) begin(server, old); else resetAll(server);
    }

    private static void begin(MinecraftServer server, int old) {
        AstralData d = AstralData.get(server);
        String ph = d.endPhase;
        if (ph.equals("bosses") || ph.equals("raid") || ph.equals("call")) {
            // phase 5 again: no countdown, the plain end
            callStart = 0; callStarted = false;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) setGameMode(p, GameType.SURVIVAL);
            d.endPhase = "classic";
            d.endUntil = 0;
            clearRaidState(server, ph.equals("raid"));   // a lost raid sends everyone home from the arena
            releaseAllies(server, d);
            d.setDirty();
            LOGGER.info("[astral] phase 5 again (was {}): the plain end", ph);
            return;
        }
        if (!ph.isEmpty()) resetAll(server);
        d.resumeStage = old >= 1 && old < 5 ? old : Math.max(1, d.resumeStage);
        d.endPhase = "call";   // the Fool is called, then everyone is taken to the arena
        d.endUntil = 0;
        callStart = server.getTickCount() + 160;   // a few seconds for "The end is here" to be read
        callStarted = false;
        spoken = 0;
        d.setDirty();
        LOGGER.info("[astral] the end begins: the call");
    }

    // ------------------------------------------------------------------ the call

    private static long callStart = 0;
    private static boolean callStarted = false;
    private static int spoken = 0;
    private static final String[] PRAYER = {
            "The Fool that does not belong to this era.",
            "You are the mysterious ruler above the gray fog.",
            "You are the King of Yellow and Black who wields good luck.",
            "I pray to you,",
            "Praying for the strength that stems from mystery,",
            "And praying for the bestowment that stems from good luck.",
            "Please grant us the power to protect our home.",
            "Something heard you",
            "Something is watching you",
            "Something is angry"};
    // ticks after the fog begins; the client (CallScene) draws the same lines at the same moments. Lines 7-9 are the red ones.
    private static final int[] PRAYER_AT = {40, 130, 220, 310, 400, 490, 580, 720, 800, 880};
    private static final int CALL_TICKS = 1020;   // the scene ends with the error; the last line comes after, as a title

    /** The sky turns to grey fog, a voice prays, something answers, and then everyone is taken to the arena. */
    private static void callTick(MinecraftServer server, AstralData d) {
        long now = server.getTickCount();
        if (callStart == 0) { callStart = now + 40; callStarted = false; spoken = 0; }   // after a restart
        long t = now - callStart;
        if (t < 0) return;
        if (!callStarted) {
            callStarted = true;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(p, new Net.CutscenePayload(20, CALL_TICKS + 20));
        }
        while (spoken < PRAYER.length && t >= PRAYER_AT[spoken]) {
            boolean red = spoken >= 7 && spoken <= 9;   // the words are on the screen only, not in chat
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (red) p.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.MASTER, 2.0f, 0.6f);
                else p.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.MASTER, 1.5f, 0.5f);
            }
            spoken++;
        }
        if (t >= CALL_TICKS) {
            callStart = 0; callStarted = false;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                PacketDistributor.sendToPlayer(p, new Net.CutscenePayload(21, 0));
                PacketDistributor.sendToPlayer(p, new Net.FlashPayload(255, 3000));
            }
            startRaid(server, d);
            // the last words are not part of the scene: a title, golden fading to black
            for (ServerPlayer p : server.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(p, new Net.StagePayload("I've heard your prayers.", 0x0A0600, 0xFFD24A, ""));
        }
    }

    // ------------------------------------------------------------------ the Fool's blessing

    private static final String[] BLESSINGS = {"hp", "attack", "armor"};
    private static final ResourceLocation B_HP = ResourceLocation.fromNamespaceAndPath("astral", "blessing_hp"),
            B_ATK = ResourceLocation.fromNamespaceAndPath("astral", "blessing_attack"),
            B_ARM = ResourceLocation.fromNamespaceAndPath("astral", "blessing_armor"),
            B_TGH = ResourceLocation.fromNamespaceAndPath("astral", "blessing_toughness");
    private static final Set<UUID> blessed = new HashSet<>();

    private static void removeBlessing(ServerPlayer p) {
        for (var e : new Object[][]{{Attributes.MAX_HEALTH, B_HP}, {Attributes.ATTACK_DAMAGE, B_ATK}, {Attributes.ARMOR, B_ARM}, {Attributes.ARMOR_TOUGHNESS, B_TGH}}) {
            @SuppressWarnings("unchecked") var inst = p.getAttribute((net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>) e[0]);
            if (inst != null) inst.removeModifier((ResourceLocation) e[1]);
        }
        blessed.remove(p.getUUID());
    }

    private static void addBlessingMod(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, ResourceLocation id, double amount) {
        var inst = p.getAttribute(attr);
        if (inst != null && inst.getModifier(id) == null) inst.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, amount, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
    }

    /** Everyone arriving in the arena: healed, Sequence 4 at least, told of the Fool's blessing, and offered a choice. */
    private static void blessPlayer(ServerPlayer p) {
        p.setHealth(p.getMaxHealth());
        int sq = Lotm.sequence(p);
        if (sq > 4 && Lotm.setSequence(p, 4)) {
            raidSeq.put(p.getUUID(), 4);
            chat(p, Component.literal("The Fool lifts you to Sequence 4.").withStyle(ChatFormatting.GOLD));
        }
        if (introQueue) introChat.add(() -> PacketDistributor.sendToPlayer(p, new Net.StagePayload("The Fool's Blessing", 0x3A2A00, 0xFFD870, "You are not alone")));
        else PacketDistributor.sendToPlayer(p, new Net.StagePayload("The Fool's Blessing", 0x3A2A00, 0xFFD870, "You are not alone"));
        giftDue.put(p.getUUID(), gt + (introQueue ? INTRO_TICKS + CHAT_AFTER_EXTRA : 0) + 1200);   // 60 seconds to choose
        chat(p, Component.literal("You have received the Fool's blessing.").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        offerBlessing(p);
    }

    private static void offerBlessing(ServerPlayer p) {
        chat(p, Component.literal("Choose one gift (click):").withStyle(ChatFormatting.YELLOW));
        chat(p, blessingButton("[+50 max HP]", "hp").append(Component.literal("  ")).append(blessingButton("[+5 attack damage]", "attack"))
                .append(Component.literal("  ")).append(blessingButton("[+8 armor, +6 armor toughness]", "armor")));
    }

    private static net.minecraft.network.chat.MutableComponent blessingButton(String label, String id) {
        return Component.literal(label).withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/astralblessing " + id)));
    }

    // ------------------------------------------------------------------ god mode (testing)

    private static final Set<UUID> god = new HashSet<>();

    /** /astralgod: ops only. Toggles immunity for testing the tree's attacks and cutscenes: no damage, no lost lives, no being pulled back. */
    @SubscribeEvent
    public static void onGodCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("astralgod").requires(s -> s.hasPermission(2))
                .executes(ctx -> toggleGod(ctx.getSource().getPlayerOrException(), null))
                .then(Commands.literal("on").executes(ctx -> toggleGod(ctx.getSource().getPlayerOrException(), true)))
                .then(Commands.literal("off").executes(ctx -> toggleGod(ctx.getSource().getPlayerOrException(), false))));
    }

    private static int toggleGod(ServerPlayer p, Boolean want) {
        boolean on = want != null ? want : !god.contains(p.getUUID());
        if (on) god.add(p.getUUID()); else god.remove(p.getUUID());
        p.sendSystemMessage(Component.literal("Astral god mode: " + (on ? "ON. Nothing in the tree fight can hurt you or cost you a life." : "off.")).withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        return 1;
    }

    @SubscribeEvent
    public static void onBlessingCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("astralblessing")
                .executes(ctx -> { offerBlessing(ctx.getSource().getPlayerOrException()); return 1; })
                .then(Commands.argument("gift", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    AstralData d = AstralData.get(ctx.getSource().getServer());
                    String g = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "gift");
                    if (!d.endPhase.equals("raid") || d.raidLives.getOrDefault(p.getUUID(), 0) <= 0) { ctx.getSource().sendFailure(Component.literal("There is no blessing to take now.")); return 0; }
                    if (!blessed.add(p.getUUID())) { ctx.getSource().sendFailure(Component.literal("You have already chosen.")); return 0; }
                    giftDue.remove(p.getUUID());
                    switch (g) {
                        case "hp" -> { addBlessingMod(p, Attributes.MAX_HEALTH, B_HP, 50); p.heal(50); }
                        case "attack" -> addBlessingMod(p, Attributes.ATTACK_DAMAGE, B_ATK, 5);
                        case "armor" -> { addBlessingMod(p, Attributes.ARMOR, B_ARM, 8); addBlessingMod(p, Attributes.ARMOR_TOUGHNESS, B_TGH, 6); }
                        default -> { blessed.remove(p.getUUID()); ctx.getSource().sendFailure(Component.literal("hp, attack or armor")); return 0; }
                    }
                    p.sendSystemMessage(Component.literal("The Fool's gift is yours.").withStyle(ChatFormatting.GOLD));
                    return 1;
                })));
    }

    @SubscribeEvent
    public static void onBlessingLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.getServer() != null && !AstralData.get(p.getServer()).endPhase.equals("raid")) removeBlessing(p);   // a blessing never outlasts the fight
    }

    /** Puts everything back as it was: Earth, the overworld, the bosses. Does not touch the stage. */
    static void resetAll(MinecraftServer server) {
        woken.clear();
        if (callStarted) for (ServerPlayer p : server.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(p, new Net.CutscenePayload(21, 0));
        callStart = 0; callStarted = false;
        AstralData d = AstralData.get(server);
        boolean was = !d.endPhase.isEmpty() || d.earthGone || !d.bossesDead.isEmpty();
        if (!was) return;
        clearRaidState(server, true);
        releaseAllies(server, d);
        if (d.earthGone) {
            ServerLevel cosmos = server.getLevel(Cosmos.COSMOS);
            if (cosmos != null) CosmosGenerator.setPlanet(cosmos, CosmosGenerator.PLANETS.get(2), true);
        }
        d.earthGone = false;
        CosmosGenerator.earthGone = false;
        d.endPhase = "";
        d.endUntil = 0;
        d.evacOffered = false;
        d.bossesDead.clear();
        d.stats.clear();
        d.raidLives.clear();
        d.raidBoss = "";
        d.setDirty();
    }

    /** Removes the bosses and the raid; people in the arena go home when `sendHome` is set. */
    private static void clearRaidState(MinecraftServer server, boolean sendHome) {
        ArenaReset.start(server);   // broken and placed blocks, roots, chests ...: back to how the world generated it
        gathering = false; deathAnimStarted = false; raidOver = false;
        AstralData d = AstralData.get(server);
        clearRootBoxes(dim(server, "raid"));
        for (ServerPlayer bp : server.getPlayerList().getPlayers()) removeBlessing(bp);
        for (ServerBossEvent b : bars.values()) b.removeAllPlayers();
        bars.clear();
        if (raidBar != null) { raidBar.removeAllPlayers(); raidBar = null; }
        timed.clear();
        nextBossAttack.clear();
        for (BossDef b : BOSSES) {
            ServerLevel lv = dim(server, b.planet());
            if (lv == null) continue;
            for (Entity e : lv.getAllEntities()) if ((e.getTags().contains(TAG_BOSS) || e.getTags().contains(TAG_MINION))) e.discard();
            force(lv, 1, false);
        }
        ServerLevel raid = dim(server, "raid");
        if (raid != null) {
            removeRoots(raid);
            removeSeeds(raid);
            rootsActive = false;
            for (Entity e : raid.getAllEntities()) if (e.getTags().contains(TAG_RAID) || e.getTags().contains(TAG_MINION)) e.discard();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(p, new Net.CutscenePayload(9, 0));   // clients forget where the tree stood
            if (sendHome) {
                ServerLevel ow = server.overworld();
                var s = ow.getSharedSpawnPos();
                for (ServerPlayer p : new ArrayList<>(raid.players())) {
                    setGameMode(p, GameType.SURVIVAL);
                    p.teleportTo(ow, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, p.getYRot(), 0f);
                }
            }
            force(raid, 3, false);
        }
        if (sendHome) for (ServerPlayer p : server.getPlayerList().getPlayers()) setGameMode(p, p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR ? GameType.SURVIVAL : p.gameMode.getGameModeForPlayer());
        d.setDirty();
    }

    // ------------------------------------------------------------------ allies

    private static final Set<String> allyChecked = new HashSet<>();

    /** Everyone in the boss fights is a LOTM ally of everyone else, so nobody hurts or targets each other. */
    private static void ensureAllies(MinecraftServer server, AstralData d) {
        List<ServerPlayer> ps = server.getPlayerList().getPlayers();
        for (int i = 0; i < ps.size(); i++)
            for (int j = i + 1; j < ps.size(); j++) {
                String a = ps.get(i).getUUID().toString(), b = ps.get(j).getUUID().toString();
                String key = a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
                if (!allyChecked.add(key)) continue;
                if (Lotm.addAlly(server, ps.get(i).getUUID(), ps.get(j).getUUID())) { d.allyPairs.add(key); d.setDirty(); }
            }
    }

    /** Undoes only the alliances this fight made. */
    private static void releaseAllies(MinecraftServer server, AstralData d) {
        for (String key : d.allyPairs) {
            String[] p = key.split("\\|");
            if (p.length == 2) Lotm.removeAlly(server, UUID.fromString(p[0]), UUID.fromString(p[1]));
        }
        d.allyPairs.clear();
        allyChecked.clear();
        d.setDirty();
    }

    // ------------------------------------------------------------------ the countdown

    private static void evacuated(MinecraftServer server, int[] out) {
        int on = 0, total = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) { total++; if (onOtherPlanet(p)) on++; }
        out[0] = on; out[1] = total;
    }

    private static void secondEvac(MinecraftServer server, AstralData d) {
        int[] c = new int[2];
        evacuated(server, c);
        long left = Math.max(0, d.endUntil - System.currentTimeMillis());
        if (c[1] > 0 && c[0] * 4 >= c[1] * 3) { destroyEarth(server, d); return; }
        if (left <= 0) {
            d.endPhase = "classic";
            d.endUntil = 0;
            d.setDirty();
            tell(server, "Not enough of you made it.", ChatFormatting.DARK_RED);
            return;
        }
        String t = String.format("%d:%02d", left / 60000, (left / 1000) % 60);
        Component bar = Component.literal("RUN  " + t + "   on another planet: " + c[0] + "/" + c[1] + " (75% needed)")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.displayClientMessage(bar, true);
    }

    private static void destroyEarth(MinecraftServer server, AstralData d) {
        LOGGER.info("[astral] evacuation succeeded: Earth is destroyed");
        d.earthGone = true;
        CosmosGenerator.earthGone = true;
        d.endPhase = "bosses";
        d.endUntil = System.currentTimeMillis() + (long) (Settings.bossDays * DAY);
        d.setDirty();
        ServerLevel cosmos = server.getLevel(Cosmos.COSMOS);
        if (cosmos != null) CosmosGenerator.setPlanet(cosmos, CosmosGenerator.PLANETS.get(2), false);
        EndChaos.unmuteAll(server);
        tell(server, "Earth is gone.", ChatFormatting.DARK_RED);
        // whoever stayed behind goes down with it
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
            ResourceKey<Level> k = p.level().dimension();
            if (k.equals(Level.OVERWORLD) || isEarthDim(k)) EndChaos.kill(p);
        }
        tell(server, "Defeat the sovereign on every planet within " + (int) Settings.bossDays + " days. Each one you defeat gives you " + (int) Settings.bossDays + " more.", ChatFormatting.GOLD);
        tell(server, "Anyone may use /cosmos to travel now: /cosmos to <planet> (mercury, venus, mars, jupiter, saturn, uranus, neptune).", ChatFormatting.AQUA);
    }

    // ------------------------------------------------------------------ the planet bosses

    /** Sequence of each planet boss when it is not 0. */
    private static final Map<String, Integer> BOSS_SEQ = Map.of("neptune", 1);

    private static Mob makeMob(ServerLevel lv, String pathId, String name, double x, double y, double z) { return makeMob(lv, pathId, name, x, y, z, 0); }

    private static Mob makeMob(ServerLevel lv, String pathId, String name, double x, double y, double z, int seq) {
        Mob m = null;
        try {
            Object path = Lotm.pathwayById(pathId);
            if (path != null) {
                Class<?> c = Class.forName("net.swimmingtuna.lotm.entity.Mobs.PlayerMobEntity");
                Class<?> bc = Class.forName("net.swimmingtuna.lotm.beyonder.api.BeyonderClass");
                m = (Mob) c.getConstructor(Level.class, bc, int.class).newInstance(lv, path, seq); // Seq 0 unless told otherwise, built directly so LOTM's Seq 0 limit never sees it
            }
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not build a LOTM Beyonder for '{}': {}", pathId, String.valueOf(t.getCause() != null ? t.getCause() : t));
        }
        if (m == null) m = EntityType.WARDEN.create(lv); // LOTM missing: something big all the same
        if (m == null) return null;
        m.setPos(x, y, z);
        m.setPersistenceRequired();
        var hp = m.getAttribute(Attributes.MAX_HEALTH);
        float startHp = name.equals(RAID_NAME) ? RAID_HP : BOSS_HP;
        if (hp != null) hp.setBaseValue(startHp);
        m.setHealth(startHp);
        m.setCustomName(Component.literal(name.equals(RAID_NAME) ? EARLY_TREE : name).withStyle(ChatFormatting.DARK_RED));   // the real name only after the reveal (Jade and the like show this name)
        m.setCustomNameVisible(!name.equals(RAID_NAME));
        if (name.equals(RAID_NAME)) limitTreeAbilities(m);
        else if (name.startsWith("Sovereign of ")) silence(m, name.substring(13).toLowerCase(java.util.Locale.ROOT));
        return m;
    }

    // ------------------------------------------------------------------ the tree's own Beyonder abilities

    /** The only LOTM abilities the Mother Tree may cast, and how rarely. */
    private static final String[] TREE_ABILITIES = {"wraith_shriek", "cursing_aura", "bane"};
    private static long nextTreeCast = 0;

    /** Stops the LOTM mob casting on its own and limits it to a short list (it still casts, but only when treeCast says so). */
    private static void limitTreeAbilities(Mob m) {
        try {
            Class<?> c = m.getClass();
            c.getMethod("setAttackChance", int.class).invoke(m, 0);
            java.util.List<net.minecraft.world.item.Item> items = new ArrayList<>();
            for (String id : TREE_ABILITIES) {
                net.minecraft.world.item.Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("lotm", id));
                if (it != net.minecraft.world.item.Items.AIR) items.add(it);
            }
            if (!items.isEmpty()) c.getMethod("setCustomAbilities", java.util.List.class).invoke(m, items);
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not limit the tree's abilities: {}", String.valueOf(t));
        }
    }

    /** The sovereigns' fights are scripted by this mod; their own LOTM abilities (which could hurt or hide them) never fire. */
    private static void silence(Mob m, String planet) {
        try {
            m.getClass().getMethod("setAttackChance", int.class).invoke(m, 0);
            java.util.List<net.minecraft.world.item.Item> items = new ArrayList<>();
            for (String id : POWERS.getOrDefault(planet, new String[0])) {
                net.minecraft.world.item.Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("lotm", id));
                if (it != net.minecraft.world.item.Items.AIR) items.add(it);
            }
            if (!items.isEmpty()) m.getClass().getMethod("setCustomAbilities", java.util.List.class).invoke(m, items);
        } catch (Throwable t) { LOGGER.warn("[astral] could not set up a sovereign's powers: {}", String.valueOf(t)); }
    }

    /** The two LOTM abilities each sovereign may cast, and only inside its attack cycle (the POWER attack, every third attack). */
    private static final Map<String, String[]> POWERS = Map.of(
            "mercury", new String[]{"starfall", "doorgammarayburst"}, "venus", new String[]{"dragonbreath", "mindstorm"},
            "mars", new String[]{"beamoftwilight", "globeoftwilight"}, "jupiter", new String[]{"sailorlightningstorm", "sonicboom"},
            "saturn", new String[]{"deathgraveyard"}, "uranus", new String[]{"psychestorm", "misfortuneimplosion"},
            "neptune", new String[]{"purewhiteray", "spearoflight"});
    private static final Map<String, Integer> cycleCount = new HashMap<>();

    /** The POWER attack: a ring charges at the boss for 2.5 seconds, then it casts one of its abilities at a random fighter. */
    private static void power(ServerLevel lv) {
        Mob boss = null;
        for (Entity e : lv.getAllEntities()) if (e instanceof Mob m && m.isAlive() && m.getTags().contains(TAG_BOSS)) { boss = m; break; }
        if (boss == null) return;
        final Mob b = boss;
        for (int t = 0; t < 50; t += 5) {
            final int tt = t;
            at(t, l -> circle(l, b.getX(), b.getZ(), 4 + tt / 6.0, 0.7f, 0.3f, 1f));
        }
        at(50, l -> {
            List<ServerPlayer> f = fighters(l);
            if (f.isEmpty() || !b.isAlive()) return;
            ServerPlayer target = f.get(ThreadLocalRandom.current().nextInt(f.size()));
            try {
                b.getClass().getMethod("setSpirituality", int.class).invoke(b, b.getClass().getMethod("getMaxSpirituality").invoke(b));
                b.setTarget(target);
                b.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
                b.getClass().getMethod("selectAndUseAbility").invoke(b);
            } catch (Throwable t) { LOGGER.warn("[astral] a sovereign could not cast: {}", String.valueOf(t)); }
        });
    }

    /** Every 3 to 5 minutes the tree casts one of its few abilities at someone in the arena. */
    private static void treeCast(ServerLevel lv) {
        if (nextTreeCast == 0 || nextTreeCast > rt + 6000) nextTreeCast = rt + 1800;
        if (rt < nextTreeCast || rootsActive) return;
        Mob boss = findBoss(lv, TAG_RAID);
        List<ServerPlayer> f = fighters(lv);
        if (boss == null || f.isEmpty()) return;
        nextTreeCast = rt + 3600 + ThreadLocalRandom.current().nextInt(2400);
        try {
            boss.setTarget(f.get(ThreadLocalRandom.current().nextInt(f.size())));
            boss.getClass().getMethod("selectAndUseAbility").invoke(boss);
        } catch (Throwable t) {
            LOGGER.warn("[astral] the tree could not cast: {}", String.valueOf(t));
        }
    }

    /** Whoever has not chosen a gift 60 seconds after it was offered gets a random one. */
    private static void giftSecond(ServerLevel lv) {
        if (giftDue.isEmpty()) return;
        for (var it = giftDue.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (gt < en.getValue() || introQueue) continue;
            it.remove();
            ServerPlayer p = lv.getServer().getPlayerList().getPlayer(en.getKey());
            if (p == null || blessed.contains(p.getUUID())) continue;
            String g = BLESSINGS[ThreadLocalRandom.current().nextInt(BLESSINGS.length)];
            p.sendSystemMessage(Component.literal("You hesitated. The Fool chooses for you.").withStyle(ChatFormatting.GOLD));
            lv.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withSuppressedOutput(), "astralblessing " + g);
        }
    }

    // ---- the Cursing Aura (a Prisoner ability the tree can cast): "your legs stop agreeing with you"

    private static final String CURSING_AURA = "net.swimmingtuna.lotm.item.BeyonderAbilities.Prisoner.CursingAura";
    private static final String[] DISHARMONY = {"disharmony", "disharmonyUntil", "disharmonyReverseMode", "disharmonyReverseTicks"};
    private static final long AURA_TICKS = 300;   // the aura is switched off 15 seconds after it starts
    private static long auraSince = -1;

    private static boolean auraActive(Mob boss) {
        try { return (Boolean) Class.forName(CURSING_AURA).getMethod("isActive", net.minecraft.world.entity.LivingEntity.class).invoke(null, boss); }
        catch (Throwable t) { return false; }
    }

    private static void cleanse(ServerPlayer p) {
        var tag = p.getPersistentData();
        for (String k : DISHARMONY) tag.remove(k);
    }

    /** Is the aura on, or does anyone still carry its disharmony? */
    private static boolean disharmony(ServerLevel lv) {
        Mob boss = findBoss(lv, TAG_RAID);
        if (boss != null && auraActive(boss)) return true;
        for (ServerPlayer p : fighters(lv)) if (p.getPersistentData().getInt("disharmony") > 0) return true;
        return false;
    }

    /** Every second: the aura never lasts longer than AURA_TICKS, and the Astral god (testing) never feels it. */
    private static void auraSecond(ServerLevel lv) {
        for (ServerPlayer p : lv.players()) if (god.contains(p.getUUID())) cleanse(p);
        Mob boss = findBoss(lv, TAG_RAID);
        if (boss == null || !auraActive(boss)) { auraSince = -1; return; }
        if (auraSince < 0) { auraSince = rt; return; }
        if (rt - auraSince < AURA_TICKS) return;
        auraSince = -1;
        try { Class.forName(CURSING_AURA).getMethod("deactivate", net.minecraft.world.entity.LivingEntity.class).invoke(null, boss); }
        catch (Throwable t) { LOGGER.warn("[astral] could not switch the Cursing Aura off: {}", String.valueOf(t)); }
        for (ServerPlayer p : lv.players()) cleanse(p);   // everybody's legs agree with them again
    }

    private static Mob findBoss(ServerLevel lv, String tag) {
        for (Entity e : lv.getAllEntities()) if (e instanceof Mob m && m.isAlive() && e.getTags().contains(tag)) return m;
        return null;
    }

    private static void secondBosses(MinecraftServer server, AstralData d) {
        long now = System.currentTimeMillis();
        int alive = 0;
        for (BossDef b : BOSSES) {
            if (d.bossesDead.contains(b.planet())) continue;
            alive++;
            ServerLevel lv = dim(server, b.planet());
            if (lv == null) continue;
            lv.getChunk(0, 0);
            force(lv, 1, true);
            String tag = TAG_PLANET + b.planet();
            Mob m = findBoss(lv, tag);
            if (m == null) {
                int gy = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, 14, 14);
                m = makeMob(lv, b.pathway(), b.title(), 14.5, gy, 14.5, BOSS_SEQ.getOrDefault(b.planet(), 0));
                if (m == null) continue;
                m.addTag(TAG_BOSS);
                m.addTag(tag);
                m.setNoGravity(true);
                m.setNoAi(true); // it stays where it is: the model is far bigger than the mob, so walking up to you would swallow you
                lv.addFreshEntity(m);
            }
            bossIds.put(b.planet(), m.getUUID());
            ServerBossEvent bar = bars.computeIfAbsent(b.planet(), k ->
                    new ServerBossEvent(Component.literal(b.title()), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10));
            bar.setProgress(Math.max(0f, Math.min(1f, m.getHealth() / BOSS_HP)));
            boolean near = false;
            for (ServerPlayer p : lv.players()) if (!p.isSpectator() && p.distanceToSqr(m) < 60 * 60) near = true;
            if (!aggro(b.planet(), lv, m)) near = false;
            if (near) {
                Long nx = nextBossAttack.get(b.planet());
                if (nx == null) nextBossAttack.put(b.planet(), gt + 100);
                else if (gt >= nx) {
                    int[] set = PLANET_ATTACKS.get(b.planet());
                    int n = cycleCount.merge(b.planet(), 1, Integer::sum);
                    int which = n % 3 == 0 ? 6 : set[ThreadLocalRandom.current().nextInt(set.length)];   // every third attack is the POWER
                    runPattern(new Arena(lv, m.getX(), m.getZ(), lv.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) m.getX(), (int) m.getZ()), false), which);
                    nextBossAttack.put(b.planet(), gt + DURATION[which] + 100 + ThreadLocalRandom.current().nextInt(100));
                }
            }
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                boolean here = p.level() == lv;
                if (here) bar.addPlayer(p); else bar.removePlayer(p);
            }
        }
        if (alive == 0) { startRaid(server, d); return; }
        if (now >= d.endUntil) {
            tell(server, "Time has run out.", ChatFormatting.DARK_RED);
            Stage.set(server, 5); // phase 5 again, no countdown
            return;
        }
        long left = d.endUntil - now;
        if (server.getTickCount() % 600 == 0) {
            String t = (left / DAY) + "d " + ((left / 3_600_000L) % 24) + "h";
            Component c = Component.literal("Planets left: " + alive + "   Time left: " + t).withStyle(ChatFormatting.GOLD);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) p.displayClientMessage(c, true);
        }
    }

    private static void bossDied(MinecraftServer server, AstralData d, String planet) {
        woken.remove(planet);
        if (!d.bossesDead.add(planet)) return;
        d.endUntil += (long) (Settings.bossDays * DAY);
        d.setDirty();
        ServerBossEvent bar = bars.remove(planet);
        if (bar != null) bar.removeAllPlayers();
        int left = BOSSES.size() - d.bossesDead.size();
        tell(server, "The sovereign of " + planet.substring(0, 1).toUpperCase() + planet.substring(1) + " has fallen. +" + (int) Settings.bossDays + " days. "
                + (left == 0 ? "That was the last." : left + " left."), ChatFormatting.GOLD);
    }

    // ------------------------------------------------------------------ the raid

    /** Where an attack happens: the raid arena, or around a planet boss. */
    private record Arena(ServerLevel lv, double cx, double cz, double gy, boolean raid) {}
    private record Timed(long at, Arena a, Consumer<ServerLevel> run) {}
    private static Arena cur = null;
    private static long gt = 0;
    private static final Map<String, Long> nextBossAttack = new HashMap<>();
    private static final Map<String, int[]> PLANET_ATTACKS = Map.of("mercury", new int[]{3}, "venus", new int[]{3, 0}, "mars", new int[]{0, 1},
            "jupiter", new int[]{1, 2}, "saturn", new int[]{2, 3}, "uranus", new int[]{4, 1}, "neptune", new int[]{5, 0});
    private static final List<Timed> timed = new ArrayList<>();
    private static long rt = 0, nextPattern = 0;
    private static final int INTRO_TICKS = 200, GRACE_TICKS = 600;   // the camera scene, then 30 seconds of peace
    private static final double WAKE_R = 45;                         // after the grace the tree wakes when someone is this close, or hits it
    private static boolean aggro = false;                            // false: the tree has not noticed anyone yet and does not attack
    private static long graceUntil = 0;

    private static boolean raidPhase(Entity e) { return e.getServer() != null && "raid".equals(AstralData.get(e.getServer()).endPhase); }

    private static void wake(ServerLevel lv, String why) {
        if (aggro) return;
        aggro = true;
        nextPattern = rt + 40;
        nextTreeCast = rt + 600;
        LOGGER.info("[astral] the tree wakes: {}", why);
        tellLevel(lv, treeName() + " wakes.", ChatFormatting.DARK_RED);
        sfx(lv, SoundEvents.WARDEN_ROAR, 3f, 0.5f);
    }
    private static double groundY = 4;
    private static final Map<UUID, Long> lastHit = new HashMap<>();
    /** After each attack only some others may follow (meteors only after a slam or rings), and which one is chosen is random. 0 meteors, 1 rings, 2 beam, 3 slam, 4 safe circles, 5 temptation. */
    // two followers each (index = attack): 0 meteors, 1 rings, 2 summon, 3 slam, 4 purge, 5 temptation, 6 (power, unused), 7 curse, 8 pollen, 9 mirror bloom, 10 fruit, 11 storm (lightning web)
    private static final int[][] NEXT = {{3, 8, 16}, {11, 4, 16}, {3, 5, 15}, {1, 7, 15}, {5, 10, 15}, {8, 1, 15}, {0, 1}, {9, 0, 16}, {4, 9, 15}, {10, 0, 16}, {8, 0, 15}, {0, 3, 16}, {3, 5, 15}, {5, 9}, {3, 0}, {3, 5}, {0, 8}};   // 15 mind assault (osu), 16 parry
    private static final int[] DURATION = {170, 330, 110, 90, 190, 200, 90, 130, 200, 570, 560, 130, 100, 190, 190, 260, 150};
    private static int lastPattern = 3;
    private static long nextTempest = 0;

    /** Chat held back while the opening camera scene plays. */
    private static final List<Runnable> introChat = new ArrayList<>();
    private static boolean introQueue = false;
    private static final int CHAT_AFTER_EXTRA = 80;   // the client starts the scene a few seconds after the server (loading): chat waits for that
    private static final Map<UUID, Long> giftDue = new HashMap<>();   // player -> tick when the Fool picks their gift for them
    private static void chat(ServerPlayer p, Component c) {
        if (introQueue) introChat.add(() -> p.sendSystemMessage(c)); else p.sendSystemMessage(c);
    }

    private static void startRaid(MinecraftServer server, AstralData d) {
        ServerLevel lv = dim(server, "raid");
        if (lv == null) { LOGGER.warn("[astral] raid dimension missing"); Stage.set(server, 5); return; }
        LOGGER.info("[astral] the call is answered: the raid begins");
        clearRaidState(server, false);
        Map<UUID, Integer> seen = new HashMap<>(d.seenStage);   // lowering the stage must not make everyone read the old messages again if the raid is lost
        internal = true;
        try { Stage.set(server, 0); } finally { internal = false; }
        d.seenStage.putAll(seen);
        d.endPhase = "raid";
        d.endUntil = 0;
        d.raidLives.clear();
        d.raidBoss = RAID_NAME;
        d.hp = AstralBarrier.getMax(server);
        d.setDirty();
        EndChaos.unmuteAll(server);
        lv.getChunk(0, 0);
        force(lv, 3, true);
        groundY = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        clearRuin(lv);
        timed.clear();
        raidSeq.clear();
        introChat.clear(); introQueue = true;
        rt = 0; aggro = false; graceUntil = INTRO_TICKS + GRACE_TICKS; cutscene = false; nextTreeCast = 0; nextPattern = 100; lastPattern = 3; nextTempest = 0; lastHit.clear(); rootsActive = false; rootStage = 0; roots.clear(); seeds.clear(); activeKind = 0; ultimateDone = false; nextUltimate = 0; ultCutsceneUntil = 0; clouds.clear(); fruits.clear(); summonSeals.clear(); summonMobs.clear(); globes.clear(); pendingUse.clear(); dazedUntil = 0; temptUntil = 0; bloomTrue = null; kingdomDone = false; kingdomEnd = 0; ultFallUntil = 0; ultImmuneUntil = 0; treeFleshy = false; lineBucket = 20;
        traitor = null; offered = null; offerDone = false; echo = null; resetGrowth(); noHeal.clear(); bloomNoHeal.clear(); sanityLock.clear(); lifeDamage.clear(); desire.clear(); thrallHitAt.clear();
        Mob boss = makeMob(lv, RAID_PATHWAY, RAID_NAME, 0.5, groundY + 2, 0.5);
        if (boss != null) {
            boss.addTag(TAG_BOSS);
            boss.addTag(TAG_RAID);
            boss.setNoAi(true);        // the fight is scripted: the attacks below are all it does
            boss.setNoGravity(true);
            lv.addFreshEntity(boss);
        }
        List<ServerPlayer> all = new ArrayList<>(server.getPlayerList().getPlayers());
        int i = 0;
        for (ServerPlayer p : all) {
            d.raidLives.put(p.getUUID(), LIVES);
            setGameMode(p, GameType.SURVIVAL);
            double x = ((i % 9) - 4) * 7.0, z = 380 + (i / 9) * 7.0;   // outside the church wall, on the field south of its gate, facing it
            i++;
            lv.getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
            p.teleportTo(lv, 0.5 + x, lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z)), z, 180f, 0f);
            PacketDistributor.sendToPlayer(p, new Net.TreeScalePayload(1f));
        }
        treeScale = 1f;
        gathering = true;
        introAt = -1;
        gatherEnd = gt + 1200;
        var wb = lv.getWorldBorder();
        if (wb.getSize() < ARENA_R * 2 + 40) LOGGER.warn("[astral] the world border ({} blocks wide) is smaller than the arena ({}): players will be stopped by it", (int) wb.getSize(), (int) (ARENA_R * 2));
        for (ServerPlayer p : all) PacketDistributor.sendToPlayer(p, new Net.StagePayload("Go inside the church", 0x10080A, 0xFFB070, "in 60 seconds the rest are carried in"));
        tellLevel(lv, "A ruined church stands before you. Go inside, everyone. In 60 seconds anyone still outside is carried in.", ChatFormatting.GOLD);
        // nothing in chat until the camera scene is over: everything said now is queued and sent when the scene ends
        introChat.add(() -> tellLevel(lv, "The World Tree has come. You have " + LIVES + " lives. Do not be where it strikes.", ChatFormatting.DARK_PURPLE));
    }

    private static boolean gathering = false;
    private static long gatherEnd = 0;

    /** Until everyone is inside the church (or 60 seconds have passed and those outside are carried in), nothing else happens. Then the camera scene starts. */
    private static long introAt = -1;

    private static void gatherTick(ServerLevel lv) {
        if (introAt >= 0) {   // everyone is in: a few seconds for the world to load around them, then the camera scene
            if (gt < introAt) return;
            introAt = -1;
            gathering = false;
            for (ServerPlayer p : lv.players()) {
                if (p.isSpectator() || !p.isAlive()) continue;
                removeBlessing(p);
                blessPlayer(p);
                PacketDistributor.sendToPlayer(p, new Net.CutscenePayload(23, INTRO_TICKS));
            }
            return;
        }
        if (gt % 20 != 0 && gatherEnd - gt > 0) return;
        long left = Math.max(0, gatherEnd - gt) / 20;
        List<ServerPlayer> ps = new ArrayList<>();
        for (ServerPlayer p : lv.players()) if (!p.isSpectator() && p.isAlive()) ps.add(p);
        List<ServerPlayer> outside = new ArrayList<>();
        for (ServerPlayer p : ps) if (!PlanetGenerator.churchInside(p.getX(), p.getZ(), 2)) outside.add(p);
        for (ServerPlayer p : outside) p.displayClientMessage(Component.literal("Go inside the church: " + left + " s").withStyle(ChatFormatting.GOLD), true);
        if (!outside.isEmpty() && gatherEnd - gt > 0) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (ServerPlayer p : outside) {   // carried in, just inside the door
            p.teleportTo(lv, 0.5 + rnd.nextDouble(-35, 35), groundY, 295 + rnd.nextDouble(0, 30), 180f, 0f);   // just inside the south gate
            p.sendSystemMessage(Component.literal("You were carried inside.").withStyle(ChatFormatting.GRAY));
        }
        introAt = gt + 120;   // six seconds, so chunks and the tree have loaded on every screen
        for (ServerPlayer p : ps) p.displayClientMessage(Component.literal("Everyone is inside. Look to the tree...").withStyle(ChatFormatting.GOLD), true);
    }

    /** Older worlds still have the ruin that used to stand under the tree (dais, columns, wall, rubble): turn it back into grass. */
    private static void clearRuin(ServerLevel lv) {
        for (int cx = -4; cx <= 3; cx++) for (int cz = -4; cz <= 3; cz++) lv.getChunk(cx, cz);
        Set<net.minecraft.world.level.block.Block> ruin = Set.of(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS,
                Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_STAIRS);
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        int base = PlanetGenerator.BASE, n = 0;
        for (int x = -50; x <= 50; x++) for (int z = -50; z <= 50; z++) {
            if (Math.hypot(x, z) > 50) continue;
            if (ruin.contains(lv.getBlockState(q.set(x, base, z)).getBlock())) { lv.setBlock(q, Blocks.GRASS_BLOCK.defaultBlockState(), 2); n++; }
            for (int y = base + 1; y <= base + 18; y++) if (ruin.contains(lv.getBlockState(q.set(x, y, z)).getBlock())) { lv.setBlock(q, Blocks.AIR.defaultBlockState(), 2); n++; }
        }
        LOGGER.info("[astral] cleared {} ruin blocks under the tree", n);
    }

    private static List<ServerPlayer> fighters(ServerLevel lv) {
        List<ServerPlayer> l = new ArrayList<>();
        AstralData d = AstralData.get(lv.getServer());
        if (cur != null && !cur.raid()) { for (ServerPlayer p : lv.players()) if (!p.isSpectator() && p.isAlive()) l.add(p); return l; }
        for (ServerPlayer p : lv.players()) if (!p.isSpectator() && p.isAlive() && d.raidLives.getOrDefault(p.getUUID(), 0) > 0 && !p.getUUID().equals(traitor)) l.add(p);   // the tree's new ally is never a target
        return l;
    }

    // ---- anti-heal, sanity lock, damage credit

    private static boolean treeHealing = false;   // set while a heal comes from one of the tree's own attacks: it ignores anti-heal
    private static final Map<UUID, Long> bloomNoHeal = new HashMap<>();      // the Mirror bloom's own no-heal: blocks every heal
    private static final Map<UUID, double[]> sanityLock = new HashMap<>();   // player -> {until tick, sanity percent they cannot drop below}
    private static final Map<UUID, float[]> lifeDamage = new HashMap<>();    // player -> {damage from the traitor, damage from everything else} since their last lost life
    private static final long HIT_ANTIHEAL = 1200, SANITY_LOCK = 600, HIT_GRACE = 20;

    /** Anti-heal for the next `ticks`, never shorter than what they already have. */
    private static void antiHeal(ServerPlayer p, long ticks) {
        noHeal.merge(p.getUUID(), gt + ticks, Math::max);
    }

    /** A heal from the tree's own attacks (fruit, the true bloom, holy clouds): anti-heal does not stop it. */
    static void treeHeal(ServerPlayer p, float amount) {
        treeHealing = true;
        try { p.heal(amount); } finally { treeHealing = false; }
    }

    private static void lockSanity(ServerPlayer p) {
        double now = Lotm.sanityPercent(p);
        if (now < 0) return;
        double[] cur = sanityLock.get(p.getUUID());
        sanityLock.put(p.getUUID(), new double[]{gt + SANITY_LOCK, cur != null && cur[0] > gt ? Math.min(cur[1], now) : now});
    }

    /** Every tick for locked players: sanity cannot go down, but it can rise. */
    private static void sanityLockTick(ServerLevel lv) {
        if (sanityLock.isEmpty()) return;
        for (ServerPlayer p : lv.players()) {
            double[] l = sanityLock.get(p.getUUID());
            if (l == null) continue;
            if (gt >= l[0]) { sanityLock.remove(p.getUUID()); continue; }
            double now = Lotm.sanityPercent(p);
            if (now < 0) continue;
            if (now < l[1] - 0.05) Lotm.setSanityPercent(p, l[1]);
            else if (now > l[1]) l[1] = now;
        }
    }

    /** A hit from the tree: doubles their corruption, takes 15% of max health and blocks all healing for 60 seconds. At 0 health it takes a life. */
    private static void loseLife(ServerLevel lv, ServerPlayer p) {
        if (cutscene || god.contains(p.getUUID())) return;
        long now = lv.getServer().getTickCount();
        Long last = lastHit.get(p.getUUID());
        if (last != null && now - last < HIT_GRACE) return; // just hit: a moment of grace
        lastHit.put(p.getUUID(), now);
        Lotm.doubleCorruption(p);
        antiHeal(p, HIT_ANTIHEAL);
        lockSanity(p);
        float dmg = p.getMaxHealth() * 0.15f;
        lifeDamage.computeIfAbsent(p.getUUID(), k -> new float[2])[1] += Math.min(dmg, p.getHealth());
        if (p.getHealth() > dmg) {
            p.setHealth(p.getHealth() - dmg);
            lv.broadcastEntityEvent(p, (byte) 2);
            lv.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1f, 1.2f);
            p.displayClientMessage(Component.literal("Nothing heals you for a minute.").withStyle(ChatFormatting.DARK_RED), true);
            return;
        }
        takeLife(lv, p);
    }

    /** Takes a life outright: a killing blow, a burst seed, a failed minigame. Totems and revival abilities never get the chance to answer it. */
    private static void takeLife(ServerLevel lv, ServerPlayer p) {
        if (cutscene || god.contains(p.getUUID())) return;
        AstralData d = AstralData.get(lv.getServer());
        long now = lv.getServer().getTickCount();
        lastHit.put(p.getUUID(), now);
        stat(d, p).deaths++;
        int lives = d.raidLives.merge(p.getUUID(), -1, Integer::sum);
        d.setDirty();
        lv.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1f, 1.2f);
        if (kingdomEnd != 0) kingdomLivesLeft--;
        Lotm.clearCorruption(p);   // dying washes the corruption away
        noHeal.remove(p.getUUID());
        sanityLock.remove(p.getUUID());
        float[] ld = lifeDamage.remove(p.getUUID());
        if (traitor != null && ld != null && !p.getUUID().equals(traitor) && ld[0] > 0f && ld[0] >= 0.8f * (ld[0] + ld[1])) traitorKill(lv);
        if (lives <= 0) {
            d.raidLives.put(p.getUUID(), 0);
            setGameMode(p, GameType.SPECTATOR);
            p.sendSystemMessage(Component.literal("You have fallen. You can only watch now.").withStyle(ChatFormatting.DARK_RED));
        } else {
            double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            p.teleportTo(lv, 0.5 + Math.cos(a) * SPAWN_R, groundY, 0.5 + Math.sin(a) * SPAWN_R, p.getYRot(), 0f);
            p.setHealth(p.getMaxHealth());
            p.sendSystemMessage(Component.literal("Lives left: " + lives).withStyle(ChatFormatting.RED));
        }
    }

    /** Forces a life to be lost whatever their health is. */
    private static void killLife(ServerLevel lv, ServerPlayer p) {
        lastHit.remove(p.getUUID());
        takeLife(lv, p);
    }

    private static final Map<UUID, long[]> airborne = new HashMap<>();   // player -> {last tick counted, ticks since leaving the ground}

    /** True only for a player who has just jumped (low and airborne for under about a second): flying or hovering gets no cover from rings and beams. */
    private static boolean jumping(ServerPlayer p) {
        long[] a = airborne.computeIfAbsent(p.getUUID(), k -> new long[]{-1, 0});
        if (p.onGround() || p.isInWater()) { a[0] = gt; a[1] = 0; return false; }
        if (a[0] != gt) { a[1] += Math.max(1, Math.min(5, gt - a[0])); a[0] = gt; }
        double up = p.getY() - cur.gy();
        return a[1] <= 24 && up < 3.2 && !p.isFallFlying() && !p.getAbilities().flying;
    }

    /** A hit: in the raid it costs a life, around a planet boss it is a hard but survivable blow (practice for the raid). */
    /** Corruption from the tree's attacks on a random pathway. The Astral god (testing) never takes any. */
    private static boolean corrupt(ServerPlayer p, int percent) {
        if (god.contains(p.getUUID())) return true;
        return Lotm.addCorruptionPercent(p, Lotm.randomPathway(), percent);
    }

    private static void hit(ServerLevel lv, ServerPlayer p) {
        if (cur != null && cur.raid()) { loseLife(lv, p); return; }
        long now = lv.getServer().getTickCount();
        Long last = lastHit.get(p.getUUID());
        if (last != null && now - last < 40) return;
        lastHit.put(p.getUUID(), now);
        Lotm.addCorruptionPercent(p, Lotm.randomPathway(), 30);
        p.hurt(lv.damageSources().magic(), p.getMaxHealth() * 0.5f);
        lv.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1f, 1.2f);
    }

    private static void runPattern(Arena a, int which) { cur = a; pattern(a.lv(), which); }

    /** Anyone inside the area (and low enough to be hit) loses a life. */
    private static void strike(ServerLevel lv, double x, double z, double r, double maxFeetAbove) {
        for (ServerPlayer p : fighters(lv)) {
            double dx = p.getX() - x, dz = p.getZ() - z;
            if (dx * dx + dz * dz <= r * r && p.getY() < cur.gy() + maxFeetAbove) hit(lv, p);
        }
    }

    private static void at(long delay, Consumer<ServerLevel> run) { timed.add(new Timed(gt + delay, cur, run)); }

    private static void dust(ServerLevel lv, double x, double y, double z, float r, float g, float b) {
        lv.sendParticles(new DustParticleOptions(new org.joml.Vector3f(r, g, b), 1.6f), x, y, z, 1, 0, 0, 0, 0);
    }

    private static int rgb(float r, float g, float b) { return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255); }

    /** A danger marker the clients draw on top of everything (the tree model is far too big to leave this to world particles). */
    private static void tele(ServerLevel lv, int kind, double x, double y, double z, float a, float b, float cr, float cg, float cb, int life) {
        teleBuf.computeIfAbsent(lv, k -> new ArrayList<>()).add(new Net.TelegraphPayload(kind, (float) x, (float) y, (float) z, a, b, rgb(cr, cg, cb), life));
    }

    private static final Map<ServerLevel, List<Net.TelegraphPayload>> teleBuf = new java.util.IdentityHashMap<>();

    /** Once a tick: everything drawn this tick goes out as one packet per player (in chunks), not one packet per marker. */
    private static void flushTele() {
        if (teleBuf.isEmpty()) return;
        for (var e : teleBuf.entrySet()) {
            List<Net.TelegraphPayload> all = e.getValue();
            for (int from = 0; from < all.size(); from += 400) {
                Net.TelegraphBatch pk = new Net.TelegraphBatch(new ArrayList<>(all.subList(from, Math.min(all.size(), from + 400))));
                for (ServerPlayer p : e.getKey().players()) PacketDistributor.sendToPlayer(p, pk);
            }
        }
        teleBuf.clear();
    }

    /** One attack effect for every player in the level; the clients animate it by themselves (see AttackFx for the kinds). */
    private static void fx(ServerLevel lv, int kind, double x, double y, double z, double a, double b, double c, int color, int life) {
        Net.FxPayload pk = new Net.FxPayload(kind, (float) x, (float) y, (float) z, (float) a, (float) b, (float) c, color, life);
        for (ServerPlayer p : lv.players()) PacketDistributor.sendToPlayer(p, pk);
    }

    private static boolean lightningy(net.minecraft.world.damagesource.DamageSource src) {
        try {
            String id = src.getMsgId() == null ? "" : src.getMsgId().toLowerCase();
            Entity d = src.getDirectEntity(), b = src.getEntity();
            String dn = d == null ? "" : d.getClass().getName().toLowerCase(), bn = b == null ? "" : b.getClass().getName().toLowerCase();
            return src.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING) || id.contains("lightning") || id.contains("thunder") || id.contains("electric")
                || dn.contains("lightning") || dn.contains("thunder") || bn.contains("lightning") || bn.contains("thunder");
        } catch (Throwable t) { return false; }
    }

    /** The player behind a hit: the attacker, or the owner of the bolt, projectile or summon that did it (by reflection, LOTM's entities all keep one somewhere). */
    private static ServerPlayer ownerOf(net.minecraft.world.damagesource.DamageSource src) {
        for (Entity en : new Entity[]{src.getEntity(), src.getDirectEntity()}) {
            if (en == null) continue;
            if (en instanceof ServerPlayer sp) return sp;
            if (en instanceof net.minecraft.world.entity.projectile.Projectile pr && pr.getOwner() instanceof ServerPlayer sp) return sp;
            if (en instanceof net.minecraft.world.entity.LightningBolt lb && lb.getCause() instanceof ServerPlayer sp) return sp;
            if (en instanceof net.minecraft.world.entity.OwnableEntity oe && oe.getOwner() instanceof ServerPlayer sp) return sp;
            for (String m : new String[]{"getOwner", "getCause", "getCaster", "getSummoner"}) {
                try {
                    var mm = en.getClass().getMethod(m);
                    if (mm.getParameterCount() == 0 && mm.invoke(en) instanceof ServerPlayer sp) return sp;
                } catch (Throwable ignored) { }
            }
        }
        return null;
    }

    /** A LOTM lightning that was aimed at something (Star of Lightning homes onto a target); the Ocean's rain is not. */
    private static boolean starBolt(Entity d) {
        if (d == null || !d.getClass().getName().contains("LightningEntity")) return false;
        try { return d.getClass().getMethod("getTargetEntity").invoke(d) != null; } catch (Throwable t) { return false; }
    }

    /** A point between `lo` and `hi` blocks from (px, pz) that is inside the arena (within `limit` of the centre). */
    private static double[] reachable(double cx, double cz, double px, double pz, double lo, double hi, double limit, ThreadLocalRandom rnd) {
        double[] best = {px, pz};
        for (int i = 0; i < 12; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = lo + rnd.nextDouble() * (hi - lo);
            double x = px + Math.cos(a) * d, z = pz + Math.sin(a) * d;
            if (Math.hypot(x - cx, z - cz) <= limit) return new double[]{x, z};
        }
        double dx = cx - px, dz = cz - pz, dd = Math.max(1, Math.hypot(dx, dz));   // at the edge: step inwards
        return new double[]{px + dx / dd * Math.max(lo, 1), pz + dz / dd * Math.max(lo, 1)};
    }

    /** The crack of a whole web of lightning going off: layered thunder, and real (harmless) bolts along the lines for the sound and the flash. */
    private static void stormSounds(ServerLevel l, List<List<Bolts.Seg>> webs) {
        sfx(l, SoundEvents.LIGHTNING_BOLT_THUNDER, 6f, 0.7f);
        sfx(l, SoundEvents.LIGHTNING_BOLT_IMPACT, 6f, 1.0f);
        sfx(l, SoundEvents.TRIDENT_THUNDER.value(), 6f, 0.8f);
        int shown = 0;
        for (List<Bolts.Seg> web : webs) {
            for (int k = 0; k < web.size() && shown < 40; k += Math.max(2, web.size() / 6)) {
                Bolts.Seg sg = web.get(k);
                LightningBolt lb = EntityType.LIGHTNING_BOLT.create(l);
                if (lb != null) { lb.moveTo(sg.x1(), cur.gy(), sg.z1()); lb.setVisualOnly(true); l.addFreshEntity(lb); shown++; }
            }
        }
    }

    /** True when solid blocks stand between the blast and the player (the church walls make cover). */
    private static boolean blastShielded(ServerLevel l, net.minecraft.world.phys.Vec3 from, ServerPlayer p) {
        net.minecraft.world.phys.Vec3 to = p.getEyePosition();
        var hit = l.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getLocation().distanceTo(to) > 1.0;
    }

    private static boolean lotmMeteorFailed = false;

    /**
     * One of LOTM's own meteors (lotm:meteor_no_hurt: it only hurts what it lands on, never breaks blocks) falls onto (x, z) and arrives after
     * `ticks`. Players in the arena cannot be damaged by it (all damage there is blocked), the life is taken by the circle's strike instead.
     * Returns false when LOTM's meteor is not there (the client then draws its own).
     */
    private static boolean lotmMeteor(ServerLevel l, double x, double z, double radius, int ticks) {
        if (lotmMeteorFailed) return false;
        try {
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath("lotm", "meteor_no_hurt"));
            if (type.isEmpty()) { lotmMeteorFailed = true; return false; }
            Entity e = type.get().create(l);
            if (!(e instanceof net.minecraft.world.entity.projectile.Projectile pr)) { lotmMeteorFailed = true; return false; }
            double H = 85, sx = x + 0.32 * H, sz = z + 0.18 * H;
            e.setPos(sx, cur.gy() + H, sz);
            Mob boss = findBoss(l, TAG_RAID);
            if (boss != null) pr.setOwner(boss);   // so its blow can never hurt the tree itself
            e.setDeltaMovement((x - sx) / ticks, -H / ticks, (z - sz) / ticks);
            try {
                Class.forName("net.swimmingtuna.lotm.util.BeyonderUtil").getMethod("setScale", Entity.class, float.class).invoke(null, e, (float) Math.max(1.0, radius / 3.5));
            } catch (Throwable t) { LOGGER.warn("[astral] could not scale the LOTM meteor: {}", String.valueOf(t)); }
            l.addFreshEntity(e);
            at(ticks + 25, ll -> { if (e.isAlive()) e.discard(); });   // never leave one behind
            return true;
        } catch (Throwable t) {
            lotmMeteorFailed = true;
            LOGGER.warn("[astral] LOTM's meteor could not be spawned, using the mod's own: {}", String.valueOf(t));
            return false;
        }
    }

    private static void ringOnly(ServerLevel lv, double x, double z, double r, float cr, float cg, float cb) {
        tele(lv, 1, x, cur.gy(), z, (float) r, 0f, cr, cg, cb, 4);
    }

    private static void circle(ServerLevel lv, double x, double z, double r, float cr, float cg, float cb) {
        tele(lv, 0, x, cur.gy(), z, (float) r, 0f, cr, cg, cb, 8);
    }

    private static final double FLY_H = 5;   // above this a fighter counts as flying: the ground rings pass under them
    private static boolean crowd = false;   // many fighters: fewer bolts and sounds so the server and clients keep up

    private static void boom(ServerLevel lv, double x, double z, double r) {
        LightningBolt b = crowd && ThreadLocalRandom.current().nextInt(6) != 0 ? null : EntityType.LIGHTNING_BOLT.create(lv);
        if (b != null) { b.moveTo(x, cur.gy(), z); b.setVisualOnly(true); lv.addFreshEntity(b); }
        lv.sendParticles(ParticleTypes.EXPLOSION, x, cur.gy() + 1, z, 6, r / 3, 0.5, r / 3, 0);
    }



    // ------------------------------------------------------------------ the roots can be hit

    private static final String TAG_ROOTBOX = "astral_rootbox";

    /** Removes any leftover invisible hit-box entities from the earlier versions. */
    private static void clearRootBoxes(ServerLevel lv) {
        if (lv != null) for (Entity e : lv.getAllEntities()) if (e.getTags().contains(TAG_ROOTBOX)) e.discard();
    }

    /**
     * A player swung at a root where there is no entity to hit: the client checked its own aim against the roots and told us.
     * We check it again against the same root model, then the swing hurts the tree like a normal hit.
     */
    private static final TreeModel[] TREE_MODELS = new TreeModel[2];

    private static synchronized TreeModel treeModel(boolean rot) {
        int i = rot ? 1 : 0;
        if (TREE_MODELS[i] == null) TREE_MODELS[i] = TreeModel.build(rot);
        return TREE_MODELS[i];
    }

    /** How far a swing reaches the tree: it is huge, so a fighter near any part of it can hit that part. */
    static final double TREE_REACH = 34, TREE_MARGIN = 3.0;

    static void rootHit(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel lv) || !lv.dimension().equals(Cosmos.COSMOS) || p.isSpectator() || !p.isAlive()) return;
        AstralData d = AstralData.get(lv.getServer());
        if (!d.endPhase.equals("raid") || p.getUUID().equals(traitor)) return;
        Mob boss = findBoss(lv, TAG_RAID);
        if (boss == null) return;
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        TreeModel m = treeModel(treeFleshy);
        double t = m.rayHitAny(eye.x - boss.getX(), eye.y - boss.getY(), eye.z - boss.getZ(), look.x, look.y, look.z, TREE_REACH, treeScale, TREE_MARGIN);
        if (t < 0) return;
        float str = p.getAttackStrengthScale(0.5f);
        double dmg = p.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2 + 0.8 * str * str);
        p.resetAttackStrengthTicker();
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        boss.hurt(p.damageSources().playerAttack(p), (float) dmg);
    }

    // ------------------------------------------------------------------ thralls, desire, wounds

    private static final String[] THRALLS = {"werewolf"};

    /** A LOTM zombie, werewolf or spirit rises at the spot; `kind` null picks one at random. */
    private static Mob spawnThrall(ServerLevel l, double x, double z, String kind) {
        if (kind == null) kind = THRALLS[ThreadLocalRandom.current().nextInt(THRALLS.length)];
        circle(l, x, z, 2f, 0.6f, 0.1f, 0.8f);
        Mob m = null;
        try {
            var t = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath("lotm", kind));
            if (t.isPresent() && t.get().create(l) instanceof Mob mm) m = mm;
        } catch (Throwable ex) {
            LOGGER.warn("[astral] could not build LOTM '{}': {}", kind, ex.toString());
        }
        if (m == null) m = EntityType.ZOMBIE.create(l);
        if (m == null) return null;
        m.setPos(x, cur.gy() + 1, z);
        m.setPersistenceRequired();
        m.addTag(TAG_MINION);
        var hp = m.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(300);
        m.setHealth(300);
        l.addFreshEntity(m);
        var near = fighters(l);
        if (!near.isEmpty()) m.setTarget(near.get(ThreadLocalRandom.current().nextInt(near.size())));
        return m;
    }

    /** A wound that does not kill: a share of maximum health (never below half a heart), and nothing heals for ten seconds. */
    private static void soften(ServerLevel l, ServerPlayer p, float frac) {
        p.setHealth(Math.max(1f, p.getHealth() - p.getMaxHealth() * frac));
        l.broadcastEntityEvent(p, (byte) 2);
        bloomNoHeal.merge(p.getUUID(), gt + 200, Math::max);
        p.displayClientMessage(Component.literal("The wound will not close for ten seconds.").withStyle(ChatFormatting.DARK_RED), true);
    }

    private static void addDesire(ServerPlayer p) {
        if (p.getUUID().equals(traitor)) return;
        long[] d = desire.computeIfAbsent(p.getUUID(), k -> new long[2]);
        d[0] = Math.min(10, d[0] + 1);
        d[1] = gt;
    }

    /** Once a second: Desire slows, dizzies and drains the mind, and fades when you leave the clouds. */
    private static void desireSecond(ServerLevel lv) {
        for (ServerPlayer p : fighters(lv)) {
            long[] d = desire.get(p.getUUID());
            if (d == null || d[0] <= 0) continue;
            int s = (int) d[0];
            if (gt - d[1] > 30) d[0]--;
            if (s >= 6) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
            Lotm.loseSanityPercent(p, 0.4 * s);
        }
    }

    private static final ResourceLocation DESIRE_HP = ResourceLocation.fromNamespaceAndPath("astral", "desire_hp");
    private static int traitorSeq = -1;

    /** Sharpness X on the held item, unless it is already higher. */
    private static void sharpen(ServerPlayer p) {
        try {
            var st = p.getMainHandItem();
            if (st.isEmpty()) return;
            var holder = p.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
            if (net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(holder, st) < 10) st.enchant(holder, 10);
        } catch (Throwable t) { LOGGER.warn("[astral] could not sharpen: {}", t.toString()); }
    }

    /** Takes the player out of their LOTM faction (the mod's own FactionData). */
    private static void leaveLotmFaction(ServerLevel lv, ServerPlayer p) {
        try {
            Class<?> c = Class.forName("net.swimmingtuna.lotm.world.worlddata.Faction.FactionData");
            Object data = c.getMethod("getInstance", ServerLevel.class).invoke(null, lv.getServer().overworld());
            Object name = c.getMethod("getPlayerFaction", UUID.class).invoke(data, p.getUUID());
            if (name == null || name.toString().isEmpty()) return;
            Object ok = c.getMethod("playerLeavesFaction", String.class, UUID.class).invoke(data, name.toString(), p.getUUID());
            LOGGER.info("[astral] {} left LOTM faction '{}': {}", p.getGameProfile().getName(), name, ok);
            p.sendSystemMessage(Component.literal("You have left " + name + ".").withStyle(ChatFormatting.DARK_RED));
        } catch (Throwable t) { LOGGER.warn("[astral] could not remove from LOTM faction: {}", String.valueOf(t.getCause() != null ? t.getCause() : t)); }
    }

    /** The raid is over: the ally gets their own sequence and health back. */
    private static void endTraitor(MinecraftServer server) {
        if (traitor == null) return;
        ServerPlayer t = server.getPlayerList().getPlayer(traitor);
        if (t != null) {
            if (traitorSeq >= 0) Lotm.setSequence(t, traitorSeq);
            var mh = t.getAttribute(Attributes.MAX_HEALTH);
            if (mh != null) mh.removeModifier(DESIRE_HP);
            t.setHealth(Math.min(t.getHealth(), t.getMaxHealth()));
        }
        traitorSeq = -1;
    }

    private static void pvp(ServerPlayer a, ServerPlayer p, float amount) {
        ServerLevel lv = (ServerLevel) p.level();
        stat(AstralData.get(lv.getServer()), a).damage += amount;
        float h = p.getHealth() - amount;
        lifeDamage.computeIfAbsent(p.getUUID(), k -> new float[2])[0] += Math.min(amount, p.getHealth());
        if (h > 0) { p.setHealth(h); lv.broadcastEntityEvent(p, (byte) 2); return; }
        p.setHealth(1f);
        killLife(lv, p);
    }

    private static void thrallHit(ServerPlayer p, float amount) {
        Long last = thrallHitAt.get(p.getUUID());
        if (last != null && gt - last < 10) return;
        thrallHitAt.put(p.getUUID(), gt);
        float cut = Math.min(amount, p.getMaxHealth() * 0.15f);
        lifeDamage.computeIfAbsent(p.getUUID(), k -> new float[2])[1] += Math.min(cut, Math.max(0f, p.getHealth() - 1f));
        noHeal.put(p.getUUID(), Math.max(noHeal.getOrDefault(p.getUUID(), 0L), gt) + 100);   // every werewolf attack adds 5 seconds, and they stack
        p.setHealth(Math.max(1f, p.getHealth() - cut));
        ((ServerLevel) p.level()).broadcastEntityEvent(p, (byte) 2);
    }

    /** Desire's offer: one fighter, once a fight. Type accept or refuse in chat. */
    private static void offerSecond(ServerLevel lv, float frac) {
        if (offered != null) {
            if (gt > offerUntil) resolveOffer(lv, lv.getServer().getPlayerList().getPlayer(offered), false);
            return;
        }
        if (offerDone || !treeFleshy || frac > 0.4f || rootsActive || cutscene) return;
        var f = fighters(lv);
        if (f.size() < 2) return;
        ServerPlayer p = f.get(ThreadLocalRandom.current().nextInt(f.size()));
        offerDone = true;
        offered = p.getUUID();
        offerUntil = gt + 300;
        p.sendSystemMessage(Component.literal("The Mother Tree of Desire whispers to you alone: \"Join me. Strength, safety, whatever you wish, and your friends will fall. Type accept or refuse in chat. You have fifteen seconds.\"").withStyle(ChatFormatting.LIGHT_PURPLE));
        p.displayClientMessage(Component.literal("Type accept or refuse in chat").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private static void resolveOffer(ServerLevel lv, ServerPlayer p, boolean accept) {
        offered = null;
        if (p == null) return;
        if (!accept) {
            p.sendSystemMessage(Component.literal("\"Then you are only another root to burn.\"").withStyle(ChatFormatting.DARK_RED));
            return;
        }
        acceptOffer(lv, p);
    }

    private static void acceptOffer(ServerLevel lv, ServerPlayer p) {
        miniStart = -1000;
        if (!p.isAlive()) return;
        traitor = p.getUUID();
        desire.remove(p.getUUID());
        var sb = p.getScoreboard();
        var team = sb.getPlayersTeam(p.getScoreboardName());
        if (team != null) sb.removePlayerFromTeam(p.getScoreboardName(), team);   // they no longer belong to any team
        leaveLotmFaction(lv, p);
        traitorSeq = Lotm.sequence(p);
        if (Lotm.setSequence(p, 0)) raidSeq.put(p.getUUID(), 0);   // Sequence 0 of their own pathway
        var mh = p.getAttribute(Attributes.MAX_HEALTH);
        if (mh != null && mh.getModifier(DESIRE_HP) == null) { mh.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(DESIRE_HP, 200, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE)); p.heal(200); }
        sharpen(p);
        tellLevel(lv, p.getGameProfile().getName() + " has taken Desire's hand. They fight for the tree now. Do not trust them.", ChatFormatting.DARK_RED);
        p.sendSystemMessage(Component.literal("You are the tree's ally. Her creatures will not touch you. Hunt the others.").withStyle(ChatFormatting.DARK_PURPLE));
        for (Entity e : lv.getAllEntities()) if (e instanceof Mob m && m.getTags().contains(TAG_MINION) && m.getTarget() == p) m.setTarget(null);
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer p = event.getPlayer();
        if (offered == null || !p.getUUID().equals(offered)) return;
        String m = event.getRawText().trim().toLowerCase(java.util.Locale.ROOT);
        if (!m.equals("accept") && !m.equals("refuse")) return;
        event.setCanceled(true);
        resolveOffer((ServerLevel) p.level(), p, m.equals("accept"));
    }

    // ---- pathway counters: secret, never mentioned in game. Using certain LOTM abilities at the right moment changes an attack.

    private static final class Cloud {
        final double x, z, vx, vz, r; final long start; int mode;   // 0 pollen, 1 holy (heals), 2 calm (gives sanity), 3 blown away
        Cloud(double x, double z, double vx, double vz, double r, long start) { this.x = x; this.z = z; this.vx = vx; this.vz = vz; this.r = r; this.start = start; }
        double nowX() { return x + vx * (gt - start); }
        double nowZ() { return z + vz * (gt - start); }
    }
    private static final class Fruits {
        final double[][] pos; final boolean[] taken, sweet;
        Fruits(double[][] pos, boolean[] taken, boolean[] sweet) { this.pos = pos; this.taken = taken; this.sweet = sweet; }
    }
    private static final List<Cloud> clouds = new ArrayList<>();
    private static final List<Fruits> fruits = new ArrayList<>();
    private static final List<double[]> summonSeals = new ArrayList<>();   // x, z, radius
    private static final List<Mob> summonMobs = new ArrayList<>();
    private static final List<double[]> globes = new ArrayList<>();        // x, y, z, last tick it holds, radius
    private static long summonStart = -1000, temptUntil = 0, dazedUntil = 0, bloomRevealUntil = 0;
    private static double[] bloomTrue = null;

    private static final Set<String> COUNTER_ITEMS = Set.of("purification", "areapurification", "manipulateemotion", "windmanipulation", "hurricane", "tornado",
            "trickwind", "hypnosis", "battlehypnotism", "holyeye", "discern", "globeoftwilight", "spatialmaze", "spatialcage");
    private static final List<Object[]> pendingUse = new ArrayList<>();   // {uuid, ability id, tick to look again}

    private static java.lang.reflect.Method svActive;
    private static boolean svFailed;

    private static boolean spiritVision(ServerPlayer p) {
        if (svFailed) return false;
        try {
            if (svActive == null) svActive = Class.forName("net.swimmingtuna.lotm.util.SpiritVisionHelper").getMethod("isActive", Entity.class);
            return (Boolean) svActive.invoke(null, p);
        } catch (Throwable t) { svFailed = true; return false; }
    }

    /** How strong an ability is for this player: Sequence 1 is 1.0, each step stronger adds 25%, each step weaker takes 10% (never below 20%). */
    static double seqScale(ServerPlayer p) {
        int sq = Lotm.sequence(p);
        if (sq < 0) return 1.0;
        return sq <= 1 ? 1.0 + 0.25 * (1 - sq) : Math.max(0.2, 1.0 - 0.1 * (sq - 1));
    }

    @SubscribeEvent
    public static void onAbilityUse(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.level().dimension().equals(Cosmos.COSMOS) || p.isSpectator()) return;
        var item = event.getItemStack().getItem();
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        String norm = key.getPath().replace("_", "");
        if (!key.getNamespace().equals("lotm") || !COUNTER_ITEMS.contains(norm) || p.getCooldowns().isOnCooldown(item)) return;   // on cooldown: nothing is cast
        for (Object[] o : pendingUse) if (o[0].equals(p.getUUID()) && o[1].equals(key.getPath())) return;
        pendingUse.add(new Object[]{p.getUUID(), norm, gt + 2});
    }

    /** Every tick: abilities that really went off (they are now on cooldown) are looked at, and raised globes are drawn. */
    private static void counterTick(ServerLevel lv) {
        if (!pendingUse.isEmpty()) {
            List<Object[]> due = new ArrayList<>();
            for (Object[] o : pendingUse) if ((Long) o[2] <= gt) due.add(o);
            pendingUse.removeAll(due);
            for (Object[] o : due) {
                ServerPlayer p = lv.getServer().getPlayerList().getPlayer((UUID) o[0]);
                if (p == null || !p.isAlive() || p.isSpectator() || p.level() != lv) continue;
                LOGGER.info("[astral] counter attempt: {} used {}", p.getGameProfile().getName(), o[1]);
                counter(lv, p, (String) o[1]);   // no longer needs the vanilla cooldown to show (LOTM does not always use it)
            }
        }
        if (!globes.isEmpty()) {
            globes.removeIf(g -> gt > g[3]);
            if (gt % 5 == 0) for (double[] g : globes) tele(lv, 1, g[0], g[1], g[2], (float) g[4], 0f, 1f, 0.75f, 0.3f, 6);
        }
    }

    private static void counter(ServerLevel lv, ServerPlayer p, String id) {
        double m = seqScale(p);
        int sq = Lotm.sequence(p);
        switch (id) {
            case "purification", "areapurification" -> {
                if (sq >= 0 && sq <= 4) for (Cloud c : clouds) if (c.mode == 0 && Math.hypot(c.nowX() - p.getX(), c.nowZ() - p.getZ()) <= c.r + 12 * m) { c.mode = 1; fx(lv, 12, c.nowX(), cur.gy(), c.nowZ(), c.r, 0, 0, rgb(1f, 0.85f, 0.4f), 1); }
                for (Fruits fs : fruits) for (int i = 0; i < fs.pos.length; i++)
                    if (!fs.taken[i] && !fs.sweet[i] && Math.hypot(fs.pos[i][0] - p.getX(), fs.pos[i][1] - p.getZ()) <= 25 * m) {
                        fs.sweet[i] = true;
                        fx(lv, 11, fs.pos[i][0], cur.gy(), fs.pos[i][1], 0, 0, 0, 0, 1);   // the model becomes a sweet one
                        fx(lv, 10, fs.pos[i][0], cur.gy(), fs.pos[i][1], 0, treeFleshy ? 1 : 0, 0, 0, 120);
                    }
            }
            case "manipulateemotion" -> { for (Cloud c : clouds) if (c.mode == 0 && Math.hypot(c.nowX() - p.getX(), c.nowZ() - p.getZ()) <= c.r + 12 * m) { c.mode = 2; fx(lv, 12, c.nowX(), cur.gy(), c.nowZ(), c.r, 0, 0, rgb(0.4f, 0.65f, 1f), 1); } }
            case "windmanipulation", "hurricane", "tornado", "trickwind" -> {
                for (Cloud c : clouds) if (c.mode != 3 && Math.hypot(c.nowX() - p.getX(), c.nowZ() - p.getZ()) <= c.r + 30 * m) {
                    c.mode = 3;
                    fx(lv, 9, c.nowX(), cur.gy(), c.nowZ(), c.r, 0, 0, 0, 5);
                    lv.sendParticles(ParticleTypes.CLOUD, c.nowX(), cur.gy() + 1, c.nowZ(), 40, c.r / 2, 1, c.r / 2, 0.1);
                }
            }
            case "hypnosis", "battlehypnotism" -> {
                if (gt >= temptUntil) return;   // only while she is singing
                Vec3 to = new Vec3(0.5 - p.getX(), cur.gy() + 20 - p.getEyeY(), 0.5 - p.getZ());
                if (to.length() > 220 || p.getLookAngle().dot(to.normalize()) < 0.85) return;   // it has to be cast at her
                long dur = Math.round(120 * m);
                dazedUntil = rt + dur;
                nextPattern = Math.max(nextPattern, rt + dur);
                lv.sendParticles(ParticleTypes.END_ROD, 0.5, cur.gy() + 12, 0.5, 60, 8, 8, 8, 0.05);
            }
            case "holyeye", "discern" -> { if (bloomTrue != null) bloomRevealUntil = gt + Math.round(160 * m); }
            case "globeoftwilight" -> {
                globes.add(new double[]{p.getX(), p.getY() + 1, p.getZ(), gt + 80, 5 * m});
                lv.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 40, 2.5 * m, 2.5 * m, 2.5 * m, 0.02);
            }
            case "spatialmaze", "spatialcage" -> {
                if (gt - summonStart > 60) return;   // it has to be cast within 3 seconds of the summon
                double r = 30 * m;
                summonSeals.add(new double[]{p.getX(), p.getZ(), r});
                for (Mob w : summonMobs) if (w.isAlive() && Math.hypot(w.getX() - p.getX(), w.getZ() - p.getZ()) <= r) {
                    lv.sendParticles(ParticleTypes.PORTAL, w.getX(), w.getY() + 1, w.getZ(), 30, 0.5, 1, 0.5, 0.5);
                    w.discard();
                }
            }
            default -> { }
        }
    }

    // ---- the traitor grows with every kill

    private static final int[] GROWTH_CAP = {5, 4, 2};              // Strength V, Resistance IV, Speed II
    private static final int[] GROWTH_SLOTS = {0, 0, 1, 2};         // Strength, Strength, Resistance, Speed
    private static final int[] growth = {1, 1, 1};
    private static final int[] outsideLevel = {0, 0, 0};            // the level of the same effect when it comes from somewhere else
    private static int growthSlot = 0;

    private static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> growthEffect(int i) {
        return i == 0 ? MobEffects.DAMAGE_BOOST : i == 1 ? MobEffects.DAMAGE_RESISTANCE : MobEffects.MOVEMENT_SPEED;
    }

    private static int growthTotal(int i, int level) { return Math.max(outsideLevel[i], 1) + level - 1; }

    private static void applyGrowth(ServerPlayer t) {
        for (int i = 0; i < 3; i++) {
            var ex = t.getEffect(growthEffect(i));
            outsideLevel[i] = ex != null && ex.getDuration() > 70 ? ex.getAmplifier() + 1 : 0;   // ours is always refreshed to 60 ticks
            t.addEffect(new MobEffectInstance(growthEffect(i), 60, growthTotal(i, growth[i]) - 1, false, false));
        }
    }

    private static void resetGrowth() {
        for (int i = 0; i < 3; i++) { growth[i] = 1; outsideLevel[i] = 0; }
        growthSlot = 0;
    }

    /** The traitor took a life: the next upgrade in the order Strength, Strength, Resistance, Speed. One that would pass its cap is skipped. */
    private static void traitorKill(ServerLevel lv) {
        if (traitor == null) return;
        ServerPlayer t = lv.getServer().getPlayerList().getPlayer(traitor);
        if (t == null || t.isSpectator() || !t.isAlive()) return;
        for (int k = 0; k < GROWTH_SLOTS.length; k++) {
            int slot = (growthSlot + k) % GROWTH_SLOTS.length, i = GROWTH_SLOTS[slot];
            if (growthTotal(i, growth[i] + 1) > GROWTH_CAP[i]) continue;
            growth[i]++;
            growthSlot = (slot + 1) % GROWTH_SLOTS.length;
            applyGrowth(t);
            t.displayClientMessage(Component.literal("A life taken. You grow stronger.").withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }
        treeHeal(t, t.getMaxHealth() * 0.10f);
        t.displayClientMessage(Component.literal("A life taken. Nothing left to gain, so you heal.").withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    // ---- Echo of Wishes: the game that replaces a straight refusal

    private static final class Echo {
        UUID who; double cx, cz; int round; int[] seq; long phaseStart; boolean showing = true; int step = 0; int standing = -1;
    }
    private static Echo echo = null;
    private static final double ECHO_R = 6, ECHO_STEP_R = 1.6;
    private static final float[][] ECHO_COL = {{1f, 0.15f, 0.15f}, {1f, 0.8f, 0.2f}, {0.6f, 0.2f, 1f}, {1f, 1f, 1f}};

    private static void echoRound(Echo e, ThreadLocalRandom rnd) {
        int n = 3 + e.round;
        e.seq = new int[n];
        int prev = -1;
        for (int i = 0; i < n; i++) { int v; do { v = rnd.nextInt(4); } while (v == prev); e.seq[i] = v; prev = v; }
        e.showing = true; e.step = 0; e.standing = -1; e.phaseStart = gt + 20;
    }

    private static void startEcho(ServerLevel lv, ServerPlayer p) {
        Echo e = new Echo();
        e.who = p.getUUID(); e.cx = p.getX(); e.cz = p.getZ(); e.round = 0;
        echoRound(e, ThreadLocalRandom.current());
        echo = e;
        p.sendSystemMessage(Component.literal("\"Then remember what you wanted.\"").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static double[] echoPos(Echo e, int i) {
        double a = Math.PI / 2 * i + 0.4;
        return new double[]{e.cx + Math.cos(a) * ECHO_R, e.cz + Math.sin(a) * ECHO_R};
    }

    private static void echoTick(ServerLevel lv) {
        Echo e = echo;
        if (e == null) return;
        ServerPlayer p = lv.getServer().getPlayerList().getPlayer(e.who);
        if (p == null || !p.isAlive() || p.isSpectator() || p.level() != lv || !fighters(lv).contains(p)) { echo = null; return; }
        long t = gt - e.phaseStart;
        int lit = -1;
        if (e.showing) {
            if (t >= 0) {
                int idx = (int) (t / 30);
                if (idx >= e.seq.length) { e.showing = false; e.phaseStart = gt; e.step = 0; t = 0; }
                else if (t % 30 < 20) lit = e.seq[idx];
            }
        }
        for (int i = 0; i < 4; i++) {
            if (gt % 4 != 0 && i != lit) continue;
            double[] f = echoPos(e, i);
            float[] c = ECHO_COL[i];
            double h = i == lit ? 3.0 : 0.7;
            for (double y = 0; y < h; y += 0.5) dust(lv, f[0], cur.gy() + 0.3 + y, f[1], c[0], c[1], c[2]);
            if (i == lit) { lv.sendParticles(ParticleTypes.END_ROD, f[0], cur.gy() + 2, f[1], 4, 0.3, 0.6, 0.3, 0.02); if (t % 30 == 0) lv.playSound(null, BlockPos.containing(f[0], cur.gy(), f[1]), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2f, 0.6f + i * 0.25f); }
        }
        if (e.showing) {
            if (gt % 10 == 0) p.displayClientMessage(Component.literal("Watch the flowers.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        if (gt % 10 == 0) p.displayClientMessage(Component.literal("Step on them in the same order.  Round " + (e.round + 1) + " of 3").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        if (t > 200) { echoFail(lv, p); return; }
        int on = -1;
        for (int i = 0; i < 4; i++) { double[] f = echoPos(e, i); if (Math.hypot(p.getX() - f[0], p.getZ() - f[1]) < ECHO_STEP_R && Math.abs(p.getY() - cur.gy()) < 4) on = i; }
        if (on == e.standing) return;
        e.standing = on;
        if (on < 0) return;
        if (on != e.seq[e.step]) { echoFail(lv, p); return; }
        double[] f = echoPos(e, on);
        lv.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2f, 1.2f);
        lv.sendParticles(ParticleTypes.HAPPY_VILLAGER, f[0], cur.gy() + 1, f[1], 12, 0.4, 0.6, 0.4, 0.05);
        if (++e.step >= e.seq.length) {
            if (++e.round >= 3) {
                echo = null;
                p.sendSystemMessage(Component.literal("\"...Then keep your little life.\"").withStyle(ChatFormatting.GOLD));
                return;
            }
            echoRound(e, ThreadLocalRandom.current());
            e.phaseStart = gt + 30;
        } else e.phaseStart = e.phaseStart;   // the clock for the round keeps running
    }

    private static void echoFail(ServerLevel lv, ServerPlayer p) {
        echo = null;
        p.sendSystemMessage(Component.literal("\"You forgot. How sad.\"").withStyle(ChatFormatting.DARK_RED));
        killLife(lv, p);
    }

    /** Every second: the ally is strong, the thralls hunt anyone but the ally, and Desire works on the rest. */
    private static void raidAlliesSecond(ServerLevel lv) {
        if (traitor != null) {
            ServerPlayer t = lv.getServer().getPlayerList().getPlayer(traitor);
            if (t != null && !t.isSpectator() && t.isAlive()) {
                applyGrowth(t);
                sharpen(t);
            }
        }
        var f = fighters(lv);
        for (Entity e : lv.getAllEntities()) {
            if (!(e instanceof Mob m) || !m.getTags().contains(TAG_MINION) || f.isEmpty()) continue;
            var tg = m.getTarget();
            if (tg == null || !tg.isAlive() || tg.getUUID().equals(traitor)) m.setTarget(f.get(ThreadLocalRandom.current().nextInt(f.size())));
        }
        desireSecond(lv);
    }

    private static final String[] NOTICE = {
            "METEORS: red circles mark where meteors will land. Keep moving out of them.",
            "RINGS: purple rings roll out from the boss. JUMP over them.",
            "SUMMON: five werewolves rise around the boss. Kill them before they overwhelm you.",
            "SLAM: a red circle under you means a strike is coming. RUN out of it.",
            "PURGE: green circles are safe. Stand INSIDE one before the ground is struck.",
            "TEMPTATION: look AWAY from the boss and CROUCH. It drags your view in random directions: pull the OPPOSITE way.",
            "POWER: the boss gathers a Beyonder ability. A ring shows it is charging, then it strikes at one of you. Get away from where it faces.",
            "CURSE: purple smoke gathers on every one of you. In a few seconds the whole battlefield is cursed: weakened, unlucky and corrupted. It cannot be dodged, so stay alive and stay together.",
            "POLLEN: purple clouds drift across the arena. Standing in one stacks Desire: it slows you, clouds your head and drains your mind. Stay out of them.",
            "MIRROR BLOOM: three flowers appear and only one is true. Touch a false one and it bursts for a quarter of your health and stops all healing. The true one cleanses everyone. Look closely at the sparks. If nobody finds it, they all wither.",
            "FRUIT: fruit falls across the arena. Take it before it ripens or it sprouts a werewolf. Later, the fruit is rotten.",
            "METEOR STORM: red circles mark where the meteors land, all at once. Red lines show where lightning will branch out of each impact straight after. Stay off both.",
            "TEMPEST: lightning strikes everywhere at once, so stand inside a blue circle. Then meteors fall everywhere, so stand inside a green circle. Then lightning webs the whole arena: find a gap between the red lines.",
            "MEMORY: a screen opens with four coloured pads. Remember the order they light up, then click them in the same order.",
            "THREAD: a screen opens with a glowing thread. Trace along it with your mouse without leaving it.",
            "MIND ASSAULT: the tree claws at your mind. A screen opens: click the circles as their rings close and hold and follow the sliders. Fail it and your mind pays for it.",
            "PARRY: the tree gathers its power into one blast. A bar opens with a sweeping marker: click when it is in the MIDDLE. Dead centre takes nothing, close takes less, a miss takes a life."};

    /** The first time a player meets an attack, they are told how to avoid it. */
    private static void notice(ServerLevel lv, int which) {
        AstralData d = AstralData.get(lv.getServer());
        String key = String.valueOf(which);
        for (ServerPlayer p : lv.players()) {
            if (p.isSpectator()) continue;
            String seen = d.tutorSeen.getOrDefault(p.getUUID(), "");
            if (java.util.Arrays.asList(seen.split(",")).contains(key)) continue;
            d.tutorSeen.put(p.getUUID(), seen.isEmpty() ? key : seen + "," + key);
            d.setDirty();
            p.sendSystemMessage(Component.literal("New attack - " + NOTICE[which]).withStyle(ChatFormatting.GOLD));
            p.displayClientMessage(Component.literal(NOTICE[which]).withStyle(ChatFormatting.GOLD), true);
        }
    }

    /**
     * The temptation minigame: the boss keeps dragging your view in a random direction (it changes every second or so).
     * Crouch, keep facing away from it and pull the OPPOSITE way. Stray too far from facing away, or stand up, and
     * a meter fills; when it is full you give in and are added to `failed`.
     */
    private static void temptation(ServerLevel lv, int ticks, Set<UUID> failed) { temptation(lv, ticks, failed, 1); }

    /** `fill` is how fast the give-in meter fills while you look the wrong way (the call is shorter, so it fills faster). */
    private static void temptation(ServerLevel lv, int ticks, Set<UUID> failed, int fill) {
        Map<UUID, Integer> meter = new HashMap<>();
        float[] pull = {0f, 0f};
        final int warm = 60;   // the warning before the drag starts
        double[] dir = {ThreadLocalRandom.current().nextDouble() * Math.PI * 2, 0.0};   // the drag's direction and how fast it is turning
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (ServerPlayer p : fighters(lv)) p.sendSystemMessage(Component.literal("Come to me, child... (turn away and CROUCH)").withStyle(ChatFormatting.LIGHT_PURPLE));
        for (int t = 0; t < ticks; t++) {
            final int tt = t;
            at(t, l -> {
                Vec3 boss = new Vec3(cur.cx(), cur.gy() + 2, cur.cz());
                if (tt % 10 == 0) l.sendParticles(ParticleTypes.CHERRY_LEAVES, boss.x, cur.gy() + 3, boss.z, 20, 6, 3, 6, 0.02);
                dir[1] = Math.max(-0.06, Math.min(0.06, dir[1] * 0.97 + (rnd.nextDouble() - 0.5) * 0.014));   // the turn speeds up and slows down on its own: no sudden changes
                dir[0] += dir[1];
                pull[0] = (float) (Math.cos(dir[0]) * 1.1);
                pull[1] = (float) (Math.sin(dir[0]) * 0.7);
                for (ServerPlayer p : fighters(l)) {
                    UUID id = p.getUUID();
                    if (failed.contains(id) || god.contains(id)) continue;   // the Astral god (testing) is never dragged
                    if (tt < warm) { if (tt % 10 == 0) p.displayClientMessage(Component.literal("Turn away and crouch!").withStyle(ChatFormatting.LIGHT_PURPLE), true); continue; }
                    double dx = p.getX() - boss.x, dz = p.getZ() - boss.z;
                    float away = (float) (net.minecraft.util.Mth.atan2(-dx, dz) * 57.29578);
                    double dev = Math.hypot(net.minecraft.util.Mth.wrapDegrees(p.getYRot() - away), p.getXRot());
                    boolean ok = p.isShiftKeyDown() && dev < 70;
                    int m = meter.getOrDefault(id, 0);
                    m = ok ? Math.max(0, m - 2) : m + fill;
                    meter.put(id, m);
                    // the drag: a small relative turn of the camera every tick
                    p.connection.teleport(p.getX(), p.getY(), p.getZ(), pull[0], pull[1],
                                java.util.EnumSet.of(net.minecraft.world.entity.RelativeMovement.X, net.minecraft.world.entity.RelativeMovement.Y,
                                        net.minecraft.world.entity.RelativeMovement.Z, net.minecraft.world.entity.RelativeMovement.Y_ROT,
                                        net.minecraft.world.entity.RelativeMovement.X_ROT));
                    if (tt % 4 == 0) {
                        String h = pull[0] > 0.8 ? "►" : pull[0] < -0.8 ? "◄" : "";
                        String v = pull[1] > 0.5 ? "▼" : pull[1] < -0.5 ? "▲" : "";
                        int bars = Math.min(10, m / 10);
                        p.displayClientMessage(Component.literal("Pulled " + h + v + "  - pull the OTHER way!   " + "█".repeat(bars) + "░".repeat(10 - bars))
                                .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE), true);
                    }
                    if (m >= 100) {
                        failed.add(id);
                        p.sendSystemMessage(Component.literal("You gave in.").withStyle(ChatFormatting.DARK_RED));
                        if (!corrupt(p, 50)) hit(l, p);
                    }
                }
            });
        }
    }

    /** One scripted attack. Every one of them can be dodged: leave the marked ground, jump the low ones, stand in the safe circles. */
    private static void pattern(ServerLevel lv, int which) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        final double cx = cur.cx(), cz = cur.cz(), bx = cx, bz = cz;
        notice(lv, which);
        if (cur.raid()) for (ServerPlayer p : fighters(lv)) corrupt(p, 2);   // every attack corrupts everyone a little, hit or dodged
        crowd = false;   // no effects are skipped for big groups: lag is fixed elsewhere
        final boolean big = cur.raid();
        if (big) sendAnim(lv, which == 15 ? 5 : which == 16 ? 6 : which, DURATION[which] + 40);
        final double reach = big ? ARENA_R : 42;
        final double mr = (big ? (rootStage >= 2 ? 22 : 12) : 4.5) * (big ? treeScale : 1), sl = (big ? 24 : 5.5) * (big ? treeScale : 1);   // meteor and slam sizes: in the raid they match the tree
        switch (which) {
            case 15 -> miniAttack(lv, 4);
            case 16 -> parry(lv);
            case 6 -> power(lv);
            case 7 -> { // a curse on everyone: cannot be dodged, but it does not cost a life
                if (!crowd) for (ServerPlayer p : fighters(lv)) fx(lv, 5, p.getX(), p.getY(), p.getZ(), 0, 0, 1.5, rgb(0.55f, 0.15f, 0.8f), 70);
                for (int t = 0; t < 60; t += 6) at(t, l -> {
                    for (ServerPlayer p : fighters(l)) l.sendParticles(ParticleTypes.WITCH, p.getX(), p.getY() + 1, p.getZ(), 12, 0.6, 0.9, 0.6, 0.05);
                });
                at(60, l -> {
                    sfx(l, SoundEvents.WITHER_AMBIENT, 2f, 0.5f);
                    for (ServerPlayer p : fighters(l)) {
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 300, 1));
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, 300, 1));
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.UNLUCK, 300, 2));
                        if (!corrupt(p, 10)) LOGGER.warn("[astral] the curse could not add corruption to {}", p.getGameProfile().getName());
                        p.sendSystemMessage(Component.literal("You are cursed.").withStyle(ChatFormatting.DARK_PURPLE));
                    }
                });
            }
            case 0 -> { // meteors: red marks under every fighter, they land 1.5 s later
                int shots = 4 + rnd.nextInt(3);
                for (int k = 0; k < shots; k++) at(k * 30L, l -> {
                    for (ServerPlayer p : fighters(l)) {
                        double x = p.getX() + rnd.nextDouble(-3, 3), z = p.getZ() + rnd.nextDouble(-3, 3);
                        for (int t = 0; t < 64; t += 5) at(t, ll -> circle(ll, x, z, mr, 1f, 0.1f, 0.1f));
                        if (!crowd) {
                            boolean lotm = lotmMeteor(l, x, z, mr, 64);
                            fx(l, 0, x, cur.gy(), z, 64, mr, lotm ? 1 : 0, rgb(1f, 0.45f, 0.1f), 96);   // the impact is always drawn; the fall only when LOTM's meteor is not there
                        }
                        at(64, ll -> { boom(ll, x, z, mr); strike(ll, x, z, mr, 99); });
                    }
                });
            }
            case 1 -> { // two rings rolling out from the Outer God: jump them. Fighters up in the air get a sky ring at their own height instead
                int rings = 2 + rnd.nextInt(2);
                final double spd = big ? ARENA_R / 150.0 : 0.6;   // the rings roll all the way to the edge of the arena
                for (int ring = 0; ring < rings; ring++) {
                    long start = 25 + ring * 30L;
                    final List<Double> sky = new ArrayList<>();   // the heights of the sky rings, worked out when the ring is sent
                    at(start, l -> {
                        fx(l, 2, cur.cx(), cur.gy(), cur.cz(), 2, spd, 0, rgb(0.7f, 0.25f, 1f), big ? 152 : 52);
                        List<Double> ys = new ArrayList<>();
                        for (ServerPlayer p : fighters(l)) if (p.getY() > cur.gy() + FLY_H) ys.add(p.getY());
                        java.util.Collections.sort(ys);
                        double sum = 0; int n = 0; double last = 0;
                        for (double y : ys) {
                            if (n > 0 && y - last > 6) { sky.add(sum / n); sum = 0; n = 0; }
                            sum += y; n++; last = y;
                        }
                        if (n > 0) sky.add(sum / n);
                    });
                    for (int t = 0; t < (big ? 150 : 50); t++) {
                        final int tt = t;
                        at(start + t, l -> {
                            double r = 2 + tt * (spd);
                            ringOnly(l, cur.cx(), cur.cz(), r, 0.7f, 0.2f, 1f);
                            if (tt == 0) for (double y : sky) fx(l, 16, cur.cx(), y, cur.cz(), spd, 2, 0, 0, big ? 152 : 52);
                            for (ServerPlayer p : fighters(l)) { // only the ring itself, and only fighters standing on the ground
                                double dist = Math.hypot(p.getX() - cur.cx(), p.getZ() - cur.cz());
                                if (Math.abs(dist - r) >= (big ? 3.0 : 1.0)) continue;
                                if (p.getY() > cur.gy() + FLY_H) {   // up in the air: only a sky ring at their height can hit them
                                    for (double y : sky) if (Math.abs(p.getY() - y) < 7.0) { hit(l, p); break; }
                                } else if (!(p.getY() >= cur.gy() + 0.9 && jumping(p))) hit(l, p);
                            }
                        });
                    }
                }
            }
            case 2 -> { // summon: five werewolves step out of the ground
                summonStart = gt;
                summonSeals.clear();
                summonMobs.clear();
                for (int i = 0; i < 5; i++) at(10 + i * 8, l -> {
                    double a = rnd.nextDouble() * Math.PI * 2, d = 6 + rnd.nextDouble() * 8;
                    double sx = bx + Math.cos(a) * d, sz = bz + Math.sin(a) * d;
                    for (double[] seal : summonSeals) if (Math.hypot(sx - seal[0], sz - seal[1]) <= seal[2]) return;   // locked away: it never appears
                    Mob m = spawnThrall(l, sx, sz, null);
                    if (m != null) summonMobs.add(m);
                });
            }
            case 8 -> { // pollen veil: clouds of Desire drift over the arena; standing in one stacks Desire
                final int n = big ? 3 : 2;
                final double cr = big ? 90 : 34;   // a bank of fog, not a puff
                final List<Cloud> mine = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, d = big ? 90 + rnd.nextDouble() * (reach * 0.55) : 6 + rnd.nextDouble() * 14, v = rnd.nextDouble() * Math.PI * 2;   // far from the tree
                    Cloud k = new Cloud(cx + Math.cos(a) * d, cz + Math.sin(a) * d, Math.cos(v) * 0.08, Math.sin(v) * 0.08, cr, gt);
                    mine.add(k);
                    clouds.add(k);
                    fx(lv, 3, k.x, cur.gy(), k.z, k.vx, k.vz, cr, rgb(0.7f, 0.2f, 0.9f), 185);
                }
                for (int t = 0; t < 180; t += 4) {
                    final int tt = t;
                    at(t, l -> {
                        for (Cloud k : mine) {
                            if (k.mode == 3) continue;   // blown away
                            double x = k.x + k.vx * tt, z = k.z + k.vz * tt;
                            float cr1 = k.mode == 1 ? 1f : k.mode == 2 ? 0.3f : 0.7f, cg1 = k.mode == 1 ? 0.9f : k.mode == 2 ? 0.7f : 0.2f, cb1 = k.mode == 1 ? 0.4f : k.mode == 2 ? 1f : 0.9f;
                            l.sendParticles(k.mode == 1 ? ParticleTypes.END_ROD : k.mode == 2 ? ParticleTypes.ENCHANT : ParticleTypes.WITCH, x, cur.gy() + 1, z, 140, k.r / 2, 1.5, k.r / 2, 0.02);
                            if (k.mode == 0 && tt >= 20 && tt % 8 == 0) { for (ServerPlayer p : fighters(l)) if (Math.hypot(p.getX() - x, p.getZ() - z) < k.r && p.getY() < cur.gy() + 6) addDesire(p); }
                            else if (k.mode != 0) {
                                for (ServerPlayer p : fighters(l)) {
                                    if (Math.hypot(p.getX() - x, p.getZ() - z) >= k.r || p.getY() >= cur.gy() + 6) continue;
                                    desire.remove(p.getUUID());
                                    if (k.mode == 1) treeHeal(p, p.getMaxHealth() * 0.006f);           // 3% a second
                                    else { double sp = Lotm.sanityPercent(p); if (sp >= 0) Lotm.setSanityPercent(p, Math.min(100, sp + 0.4)); }   // 2% a second
                                }
                            }
                        }
                    });
                }
                at(185, l -> clouds.removeAll(mine));
            }
            case 9 -> { // mirror bloom: three flowers, one true. Touch a false one and it bursts
                final int real = rnd.nextInt(3);
                double[][] f = new double[3][2];
                for (double[] k : f) { double a = rnd.nextDouble() * Math.PI * 2, d = 8 + rnd.nextDouble() * (big ? reach * 0.5 : 16); k[0] = cx + Math.cos(a) * d; k[1] = cz + Math.sin(a) * d; }
                boolean[] gone = new boolean[3];
                boolean[] done = {false};
                bloomTrue = f[real];
                bloomRevealUntil = 0;
                at(565, l -> bloomTrue = null);
                at(0, l -> { for (double[] k : f) fx(l, 17, k[0], cur.gy(), k[1], treeFleshy ? 1 : 0, 0, 0, 0, 575); });
                at(564, l -> { for (double[] k : f) fx(l, 11, k[0], cur.gy(), k[1], 0, 0, 0, 0, 1); });
                for (int t = 0; t <= 560; t += 2) {
                    final int tt = t;
                    at(t, l -> {
                        if (done[0]) { bloomTrue = null; return; }
                        if (tt % 8 == 0) {
                            if (gt < bloomRevealUntil) tele(l, 3, f[real][0], cur.gy(), f[real][1], 0f, 45f, 1f, 1f, 0.6f, 30);   // a column of light for everyone
                            for (ServerPlayer p : fighters(l)) if (spiritVision(p)) l.sendParticles(p, ParticleTypes.END_ROD, true, f[real][0], cur.gy() + 1.8, f[real][1], 3, 0.1, 0.5, 0.1, 0.01);   // only they see the true flower glow
                        }
                        for (int i = 0; i < 3 && !done[0]; i++) {
                            if (gone[i]) continue;
                            double x = f[i][0], z = f[i][1];
                            if (tt % 4 == 0) l.sendParticles(ParticleTypes.ENCHANT, x, cur.gy() + 1.2, z, 4, 0.3, 0.6, 0.3, 0.1);
                            if (tt % 12 == 0) l.sendParticles(i == real ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.WITCH, x, cur.gy() + 1.2, z, 3, 0.25, 0.4, 0.25, 0);
                            for (ServerPlayer p : fighters(l)) {
                                if (Math.hypot(p.getX() - x, p.getZ() - z) > 1.8 || Math.abs(p.getY() - cur.gy()) > 4) continue;
                                if (i == real) {
                                    done[0] = true;
                                    for (double[] k : f) fx(l, 11, k[0], cur.gy(), k[1], 0, 0, 0, 0, 1);
                                    l.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, cur.gy() + 1.2, z, 40, 1, 1, 1, 0.1);
                                    for (ServerPlayer q : fighters(l)) { desire.remove(q.getUUID()); treeHeal(q, q.getMaxHealth() * 0.25f); q.displayClientMessage(Component.literal("The true bloom: you are cleansed.").withStyle(ChatFormatting.GREEN), true); }
                                } else {
                                    gone[i] = true;
                                    fx(l, 11, x, cur.gy(), z, 0, 0, 0, 0, 1);
                                    boom(l, x, z, 4);
                                    for (ServerPlayer q : fighters(l)) if (Math.hypot(q.getX() - x, q.getZ() - z) < 4.5) soften(l, q, 0.25f);
                                }
                                break;
                            }
                        }
                        if (tt == 560 && !done[0]) { // nobody found the true bloom: they all wither at once
                            sfx(l, SoundEvents.WITHER_AMBIENT, 2f, 0.7f);
                            for (ServerPlayer q : fighters(l)) soften(l, q, 0.25f);
                        }
                    });
                }
            }
            case 10 -> { // fruit rain: fruit falls; take it or it sprouts a thrall. After half health the fruit is rotten
                final boolean sour = treeFleshy;
                final int n = big ? 8 : 5;
                double[][] f = new double[n][2];
                for (double[] k : f) { double a = rnd.nextDouble() * Math.PI * 2, d = 5 + rnd.nextDouble() * (big ? reach * 0.55 : 18); k[0] = cx + Math.cos(a) * d; k[1] = cz + Math.sin(a) * d; }
                boolean[] taken = new boolean[n];
                boolean[] sweet = new boolean[n];
                for (int i = 0; i < n; i++) sweet[i] = !sour;
                Fruits fs = new Fruits(f, taken, sweet);
                for (int i = 0; i < n; i++) fx(lv, 10, f[i][0], cur.gy(), f[i][1], sweet[i] ? 0 : 1, sour ? 1 : 0, 0, 0, 545);   // the model and its beam
                fruits.add(fs);
                at(545, l -> fruits.remove(fs));
                for (int t = 0; t <= 540; t += 2) {
                    final int tt = t;
                    at(t, l -> {
                        for (int i = 0; i < n; i++) {
                            if (taken[i]) continue;
                            double x = f[i][0], z = f[i][1];
                            final boolean rotten = !sweet[i];
                            if (tt % 6 == 0) l.sendParticles(rotten ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD, x, cur.gy() + 1.2, z, 3, 0.2, 0.5, 0.2, 0.01);
                            if (tt == 540) { taken[i] = true; fx(l, 11, x, cur.gy(), z, 0, 0, 0, 0, 1); spawnThrall(l, x, z, "werewolf"); continue; }
                            for (ServerPlayer p : fighters(l)) {
                                if (Math.hypot(p.getX() - x, p.getZ() - z) > 1.8 || Math.abs(p.getY() - cur.gy()) > 4) continue;
                                taken[i] = true;
                                fx(l, 11, x, cur.gy(), z, 0, 0, 0, 0, 1);
                                if (rotten) { soften(l, p, 0.15f); p.displayClientMessage(Component.literal("The fruit was rotten.").withStyle(ChatFormatting.DARK_RED), true); }
                                else { treeHeal(p, p.getMaxHealth() * 0.3f); desire.remove(p.getUUID()); p.displayClientMessage(Component.literal("Sweet. You feel whole.").withStyle(ChatFormatting.GOLD), true); }
                                break;
                            }
                        }
                    });
                }
            }
            case 3 -> { // slam: a big red circle under every fighter, 2 s to leave it
                for (ServerPlayer p : fighters(lv)) {
                    double x = p.getX(), z = p.getZ();
                    for (int t = 0; t < 50; t += 4) at(t, l -> { if (treeFleshy) circle(l, x, z, sl, 0.8f, 0.05f, 0.25f); else circle(l, x, z, sl, 1f, 0.7f, 0.85f); });
                    if (!crowd) fx(lv, 1, x, cur.gy(), z, 50, sl, treeFleshy ? 1 : 0, rgb(1f, 0.35f, 0.05f), 88);
                    at(50, l -> { boom(l, x, z, sl); strike(l, x, z, sl, 99); });
                }
            }
            case 5 -> { // temptation: look away from the tree and crouch, or be corrupted
                Set<UUID> failed = new HashSet<>();
                temptUntil = gt + 165;
                temptation(lv, 160, failed);
                at(165, l -> { for (ServerPlayer p : fighters(l)) if (!failed.contains(p.getUUID())) p.sendSystemMessage(Component.literal("You resisted.").withStyle(ChatFormatting.GREEN)); });
            }
            case 11 -> { // meteor storm: every meteor lands at once, then lightning branches wildly out of each impact along the red lines
                final int fall = 34, flash = 38;
                final List<double[]> spots = new ArrayList<>();
                for (ServerPlayer p : fighters(lv)) {
                    double x = p.getX() + rnd.nextDouble(-3, 3), z = p.getZ() + rnd.nextDouble(-3, 3);
                    boolean near = false;
                    for (double[] h : spots) if (Math.hypot(h[0] - x, h[1] - z) < 10) { near = true; break; }
                    if (!near && spots.size() < 14) spots.add(new double[]{x, z});
                }
                final List<List<Bolts.Seg>> webs = new ArrayList<>();
                for (double[] h : spots) {
                    final double x = h[0], z = h[1];
                    final long seed = rnd.nextInt(1_000_000);
                    final int arms = 6 + rnd.nextInt(3);
                    final double wr = big ? 130 : 30;
                    webs.add(Bolts.web(seed, x, z, wr, arms));
                    for (int t = 0; t < fall; t += 5) at(t, ll -> circle(ll, x, z, mr, 1f, 0.1f, 0.1f));
                    boolean lotm = lotmMeteor(lv, x, z, mr, fall);
                    fx(lv, 0, x, cur.gy(), z, fall, mr, lotm ? 1 : 0, rgb(1f, 0.45f, 0.1f), 62);
                    fx(lv, 13, x, cur.gy(), z, seed, wr, flash, arms, flash + 24);
                    at(fall, ll -> { boom(ll, x, z, mr); strike(ll, x, z, mr, 99); sfx(ll, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.7f + (float) rnd.nextDouble() * 0.5f); });
                }
                if (big) {   // lightning over the WHOLE arena: webs also grow from a grid of points across it, not only from where the players stand
                    for (double gx = -ARENA_R + 60; gx <= ARENA_R - 60; gx += 110) for (double gz = -ARENA_R + 60; gz <= ARENA_R - 60; gz += 110) {
                        if (Math.hypot(gx, gz) > ARENA_R - 20) continue;
                        final double x = cx + gx + rnd.nextDouble(-25, 25), z = cz + gz + rnd.nextDouble(-25, 25);
                        final long seed = rnd.nextInt(1_000_000);
                        webs.add(Bolts.web(seed, x, z, 120, 5));
                        fx(lv, 13, x, cur.gy(), z, seed, 120, flash, 5, flash + 24);
                    }
                }
                at(0, l -> sfx(l, SoundEvents.WARDEN_SONIC_CHARGE, 4f, 0.8f));
                at(flash, l -> {
                    stormSounds(l, webs);
                    int shown = 0;
                    for (List<Bolts.Seg> web : webs) {
                        for (int k = 3; k < web.size() && shown < 24; k += Math.max(1, web.size() / 2)) {   // a couple of real bolts per web
                            Bolts.Seg sg = web.get(k);
                            LightningBolt lb = EntityType.LIGHTNING_BOLT.create(l);
                            if (lb != null) { lb.moveTo(sg.x1(), cur.gy(), sg.z1()); lb.setVisualOnly(true); l.addFreshEntity(lb); shown++; }
                        }
                    }
                    for (ServerPlayer p : fighters(l)) {
                        if (p.getY() >= cur.gy() + 12) continue;
                        for (List<Bolts.Seg> web : webs) if (Bolts.distance(web, p.getX(), p.getZ()) < 1.7) { hit(l, p); break; }
                    }
                });
            }
            case 12 -> { // tempest (below 35%): lightning everywhere (find a safe circle), then meteors everywhere (different safe circles), then a web of lightning over the whole arena (find a gap)
                final double sr = 7;
                final int t1 = 60;   // the lightning at t1, the meteors land 0.5 s later, the web strikes 0.5 s after that   // a wind-up showing every safe circle, then the three strikes follow one another almost instantly
                final List<double[]> safeA = new ArrayList<>(), safeB = new ArrayList<>();
                // every safe circle is somewhere a fighter can really reach: 4 to 11 blocks from them (a wind-up of 2.5 s), inside the arena
                // and the second set sits right on top of the first, so you can step from the lightning's circle into the meteors' circle
                for (ServerPlayer p : fighters(lv)) {
                    boolean covered = false;
                    for (double[] q : safeA) if (Math.hypot(p.getX() - q[0], p.getZ() - q[1]) < 5) { covered = true; break; }
                    if (covered || safeA.size() >= 40) continue;
                    double[] pt = reachable(cx, cz, p.getX(), p.getZ(), 4, 11, reach - 12, rnd);
                    safeA.add(pt);
                }
                if (safeA.isEmpty()) safeA.add(new double[]{cx + 30, cz});
                // the lightning and the meteors hit at the same moment and are escaped the same way: stand inside a circle
                final int mf = 16;
                for (int t = 0; t < t1; t += 5) at(t, l -> { for (double[] q : safeA) circle(l, q[0], q[1], sr, 0.3f, 1f, 0.5f); });
                at(0, l -> sfx(l, SoundEvents.WARDEN_SONIC_CHARGE, 4f, 0.7f));
                at(t1 - mf, l -> {
                    sfx(l, SoundEvents.ENDER_DRAGON_GROWL, 3f, 0.6f);
                    List<double[]> mets = new ArrayList<>();
                    for (int tries = 0; tries < 2500 && mets.size() < 150; tries++) {
                        double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * reach;
                        double x = cx + Math.cos(a) * d, z = cz + Math.sin(a) * d;
                        boolean okp = true;
                        for (double[] q : safeA) if (Math.hypot(x - q[0], z - q[1]) < sr + 6) { okp = false; break; }
                        for (double[] q : mets) if (Math.hypot(x - q[0], z - q[1]) < 10) { okp = false; break; }
                        if (okp) mets.add(new double[]{x, z});
                    }
                    for (double[] h : mets) {
                        final double x = h[0], z = h[1];
                        fx(l, 0, x, cur.gy(), z, mf, 8, 0, rgb(1f, 0.45f, 0.1f), mf + 22);
                        at(mf, ll -> { boom(ll, x, z, 8); strike(ll, x, z, 8, 99); });
                    }
                    at(mf, ll -> sfx(ll, SoundEvents.GENERIC_EXPLODE.value(), 6f, 0.7f));
                });
                at(t1, l -> {
                    sfx(l, SoundEvents.LIGHTNING_BOLT_THUNDER, 6f, 0.7f);
                    sfx(l, SoundEvents.LIGHTNING_BOLT_IMPACT, 6f, 0.9f);
                    fx(l, 19, cx, cur.gy(), cz, rnd.nextInt(100000), reach, 300, 0, 20);
                    for (ServerPlayer p : fighters(l)) {
                        boolean ok = false;
                        for (double[] q : safeA) if (Math.hypot(p.getX() - q[0], p.getZ() - q[1]) <= sr) ok = true;
                        for (double[] g : globes) if (gt <= g[3] && p.distanceToSqr(g[0], g[1], g[2]) <= g[4] * g[4]) ok = true;
                        if (!ok) hit(l, p);
                    }
                });
                // 3: the whole arena webbed in lightning: find a gap
                at(t1 + 20 + 5 - 3, l -> {   // the web strikes about 0.7 s after the rest (0.25 s later than before)
                    final int flash = 3;
                    sfx(l, SoundEvents.WARDEN_SONIC_CHARGE, 4f, 0.5f);
                    final List<List<Bolts.Seg>> webs = new ArrayList<>();
                    final List<double[]> src = new ArrayList<>();   // a grid over the whole arena, close enough that no patch is left bare
                    for (double gx = -reach; gx <= reach; gx += 62) for (double gz = -reach; gz <= reach; gz += 62) if (Math.hypot(gx, gz) <= reach + 20) src.add(new double[]{cx + gx + rnd.nextDouble(-8, 8), cz + gz + rnd.nextDouble(-8, 8)});
                    for (double[] o : src) {
                        long seed = rnd.nextInt(1_000_000);
                        webs.add(Bolts.web(seed, o[0], o[1], 105, 6));
                        fx(l, 13, o[0], cur.gy(), o[1], seed, 105, flash, 6, flash + 24);
                    }
                    at(flash, ll -> {
                        stormSounds(ll, webs);
                        for (ServerPlayer p : fighters(ll)) {
                            if (p.getY() >= cur.gy() + 12) continue;
                            for (List<Bolts.Seg> web : webs) if (Bolts.distance(web, p.getX(), p.getZ()) < 1.7) { hit(ll, p); break; }
                        }
                    });
                });
            }
            default -> { // purge: the tree gathers its power for 7 seconds, then everything not inside a safe circle is struck
                final int windup = 140;
                final double sr = big ? 7 : 3.2;
                final List<double[]> safe = new ArrayList<>();
                if (big) {
                    // a safe circle is put 10 to 22 blocks from every fighter who has none within reach, so everybody can get to one in time
                    for (ServerPlayer p : fighters(lv)) {
                        boolean covered = false;
                        for (double[] s : safe) if (Math.hypot(p.getX() - s[0], p.getZ() - s[1]) < 30) { covered = true; break; }
                        if (covered || safe.size() >= 16) continue;
                        double a = rnd.nextDouble() * Math.PI * 2, d = 10 + rnd.nextDouble() * 12;
                        safe.add(new double[]{p.getX() + Math.cos(a) * d, p.getZ() + Math.sin(a) * d});
                    }
                }
                while (safe.size() < 3) { double a = rnd.nextDouble() * Math.PI * 2, d = 4 + rnd.nextDouble() * (big ? 40 : 14); safe.add(new double[]{cx + Math.cos(a) * d, cz + Math.sin(a) * d}); }
                for (int t = 0; t < windup; t += 5) at(t, l -> { for (double[] s : safe) circle(l, s[0], s[1], sr, 0.2f, 1f, 0.3f); });
                if (big) fx(lv, 14, cx, cur.gy(), cz, windup, ARENA_R, 0, rgb(0.85f, 0.4f, 1f), windup + 60);   // the tree winds up and bursts
                at(windup, l -> {
                    for (ServerPlayer p : fighters(l)) {
                        boolean ok = false;
                        for (double[] s : safe) { double dx = p.getX() - s[0], dz = p.getZ() - s[1]; if (dx * dx + dz * dz <= sr * sr) ok = true; }
                        for (double[] g : globes) if (gt <= g[3] && p.distanceToSqr(g[0], g[1], g[2]) <= g[4] * g[4]) ok = true;   // inside a raised globe
                        if (!ok) hit(l, p);
                    }
                    boom(l, cx, cz, big ? 60 : 30);
                    sfx(l, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.6f);
                    if (!big) fx(l, 4, cx, cur.gy(), cz, 40, 0, 0, rgb(0.85f, 0.4f, 1f), 36);
                });
            }
        }
    }

    // ------------------------------------------------------------------ the roots (Mother Tree of Desire)

    private static boolean rootsActive = false;
    private static long rootsEnd = 0;
    private static int rootStage = 0;
    private static boolean ultimateDone = false;   // (no longer used: the meteor can be reused, see nextUltimate)
    private static long nextUltimate = 0;          // the 15% meteor is off cooldown from this raid tick on
    static float treeScale = 1f;   // the tree grows with the number of fighters
    private static final double ULT_SAFE_R = 26;     // under the tree: the trunk is 15 wide and the crown shades the ground around it
    private static final List<BlockPos> roots = new ArrayList<>();
    private static int activeKind = 0;       // 0 roots, 1 desire's call, 2 heart seeds
    private static long phaseEnd = 0;
    private static final List<BlockPos> seeds = new ArrayList<>();
    private static final Set<UUID> callFailed = new HashSet<>();
    private static final float[] THRESHOLDS = {0.75f, 0.5f, 0.25f};
    private static final int CUT_TICKS = 260;   // the transformation cutscene: 13 seconds
    private static boolean cutscene = false;

    /** 50%: everyone watches the tree wither, rot and swallow flesh to become its fleshy form; then Desire's call begins. */
    private static void startTransformation(ServerLevel lv) {
        rootsActive = true;
        activeKind = 3;
        cutscene = true;
        timed.clear();
        for (ServerPlayer p : lv.players()) PacketDistributor.sendToPlayer(p, new Net.CutscenePayload(1, CUT_TICKS));
        sfx(lv, SoundEvents.ENDER_DRAGON_GROWL, 2f, 0.5f);
        for (int t = 40; t < 140; t += 20 - (t - 40) / 12) { final int tt = t; at(t, l -> sfx(l, SoundEvents.WARDEN_HEARTBEAT, 3f, 0.6f + tt / 250f)); }   // a heart that beats faster
        at(100, l -> sfx(l, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.5f, 0.5f));
        at(138, l -> sfx(l, SoundEvents.WITHER_SPAWN, 2f, 0.45f));            // the swap: the flesh takes over
        at(150, l -> sfx(l, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 3f, 0.4f));
        at(185, l -> sfx(l, SoundEvents.WARDEN_EMERGE, 3f, 0.6f));
        at(235, l -> sfx(l, SoundEvents.WARDEN_ROAR, 3f, 0.6f));
        at(CUT_TICKS, l -> {
            Mob b = findBoss(l, TAG_RAID);
            if (b != null) { b.setCustomName(Component.literal(RAID_NAME + "\u200B").withStyle(ChatFormatting.DARK_RED)); b.setCustomNameVisible(true); } // the invisible mark tells clients to draw the fleshy form for good
            cutscene = false;
            rootsActive = false;
            treeFleshy = true;
            if (raidBar != null) raidBar.setName(barName("", ChatFormatting.DARK_PURPLE));
            startCall(l);
        });
    }

    /** A sound every player in the level hears at full strength, however far from the tree. */
    private static void sfx(ServerLevel lv, net.minecraft.sounds.SoundEvent e, float vol, float pitch) {
        for (ServerPlayer p : lv.players()) p.playNotifySound(e, SoundSource.MASTER, vol, pitch);
    }

    private static boolean burnt(ServerLevel lv, BlockPos b) {
        if (!lv.getBlockState(b).is(Blocks.MANGROVE_WOOD) || !lv.getBlockState(b.above()).is(Blocks.MANGROVE_WOOD)) return true;
        BlockPos u = b.above();
        for (BlockPos q : new BlockPos[]{b.north(), b.south(), b.east(), b.west(), u.north(), u.south(), u.east(), u.west(), u.above()})
            if (lv.getBlockState(q).getBlock() instanceof BaseFireBlock) return true;
        return false;
    }

    private static void removeRoots(ServerLevel lv) {
        for (BlockPos b : roots) {
            if (lv.getBlockState(b).is(Blocks.MANGROVE_WOOD)) lv.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
            if (lv.getBlockState(b.above()).is(Blocks.MANGROVE_WOOD)) lv.setBlock(b.above(), Blocks.AIR.defaultBlockState(), 3);
        }
        roots.clear();
    }

    private static void endPhase(ServerLevel lv, String msg, ChatFormatting c) {
        rootsActive = false;
        rootStage++;
        if (raidBar != null) raidBar.setName(barName("", ChatFormatting.DARK_PURPLE));
        tellLevel(lv, msg, c);
    }

    private static long healOkUntil = 0;   // only the fight's own heals (a failed call, seeds, the meteor, the roots) may raise her health

    private static void healBoss(Mob boss, float fraction) { healOkUntil = gt + 4; boss.setHealth(Math.min(RAID_HP, boss.getHealth() + RAID_HP * fraction)); }

    private static void removeSeeds(ServerLevel lv) {
        for (BlockPos b : seeds) if (lv.getBlockState(b).is(Blocks.SHROOMLIGHT)) lv.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
        seeds.clear();
    }

    /** 50%: the tree sings to everyone for 20 seconds. Each one who gives in heals it by 10%. */
    private static void startCall(ServerLevel lv) {
        rootsActive = true;
        activeKind = 1;
        phaseEnd = rt + 260;
        callFailed.clear();
        tellLevel(lv, treeName() + " sings. It cannot be hurt while you listen. Do NOT give in: every one who does heals it.", ChatFormatting.LIGHT_PURPLE);
        osuHits.clear();
        temptation(lv, 100, callFailed, 3);   // the first five seconds: turn away and crouch
        at(100, l -> {   // then the cursor is free and the click game begins
            miniOpen(l, 4, callFailed, MINI_HINT[4], 16, 140, 11);   // Desire's call is the hard click game: curved sliders, quicker rings
        });
        at(255, l -> {
            for (ServerPlayer p : fighters(l)) {
                UUID id = p.getUUID();
                if (callFailed.contains(id)) continue;
                int[] r = osuHits.get(id);
                boolean ok = r != null && r[1] == miniCount && r[0] >= miniNeed;
                if (ok) continue;
                callFailed.add(id);
                p.sendSystemMessage(Component.literal("You gave in.").withStyle(ChatFormatting.DARK_RED));
                if (!corrupt(p, 50)) hit(l, p);
            }
            Mob b = findBoss(l, TAG_RAID);
            if (b != null) healBoss(b, 0.10f * callFailed.size());
            endPhase(l, callFailed.isEmpty() ? "Nobody gave in. The tree is exposed!" : callFailed.size() + " gave in and fed it. The tree is exposed again.", ChatFormatting.GOLD);
        });
    }

    private static final Map<UUID, int[]> osuHits = new HashMap<>();
    private static int miniCount = 10, miniNeed = 6, miniTicks = 100;
    private static long miniStart = -1000;
    private static final String[] MINI_HINT = {"Now click the wishes before they close.", "Now remember the order, then click the pads the same way.", "Now follow the light along the thread.", "Click when the marker is in the middle of the bar.", "Resist it: click the circles as their rings close, and hold and follow the sliders."};

    /** Opens a minigame on every fighter's screen (except those skipped) and remembers what a pass needs: 60% of it. */
    private static void miniOpen(ServerLevel l, int game, Set<UUID> skip, String hint) {
        if (game == 4) miniOpen(l, game, skip, hint, 22, 220, 15);
        else if (game == 3) miniOpen(l, game, skip, hint, 100, 100, 0);
        else miniOpen(l, game, skip, hint, game == 0 ? 14 : 16, game == 0 ? 144 : 100, game == 0 ? 10 : (int) Math.ceil(16 * 0.6));
    }

    private static void miniOpen(ServerLevel l, int game, Set<UUID> skip, String hint, int count, int ticks, int need) {
        int seed = ThreadLocalRandom.current().nextInt(1_000_000);
        miniCount = count;
        miniTicks = ticks;
        miniNeed = need;
        miniStart = rt;
        osuHits.clear();
        for (ServerPlayer p : fighters(l)) {
            if (skip.contains(p.getUUID())) continue;
            p.sendSystemMessage(Component.literal(hint).withStyle(ChatFormatting.LIGHT_PURPLE));
            PacketDistributor.sendToPlayer(p, new Net.OsuStart(game, seed, miniCount, miniTicks));
        }
    }

    /** An attack made of a minigame: whoever does not reach 60% is struck. */
    private static void miniAttack(ServerLevel lv, int game) {
        miniOpen(lv, game, new HashSet<>(), MINI_HINT[game]);
        at(miniTicks + 25, l -> {
            for (ServerPlayer p : fighters(l)) {
                int[] r = osuHits.get(p.getUUID());
                if (r != null && r[1] == miniCount && r[0] >= miniNeed) continue;
                p.sendSystemMessage(Component.literal("You failed it.").withStyle(ChatFormatting.DARK_RED));
                if (game == 4) { Lotm.loseSanityPercent(p, 12); if (!corrupt(p, 30)) hit(l, p); } else hit(l, p);
            }
            miniStart = -1000;
        });
    }

    /** The tree gathers its power into one blast. Everyone parries on their own screen: the closer to the middle, the less it hurts. */
    private static void parry(ServerLevel lv) {
        miniOpen(lv, 3, new HashSet<>(), MINI_HINT[3]);
        final int burst = miniTicks + 22;
        sfx(lv, SoundEvents.WARDEN_SONIC_CHARGE, 4f, 0.5f);
        fx(lv, 14, cur.cx(), cur.gy(), cur.cz(), burst, ARENA_R, 0, rgb(0.9f, 0.45f, 1f), burst + 60);   // the tree winds up and bursts
        at(burst, l -> {
            sfx(l, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.6f);
            for (ServerPlayer p : fighters(l)) {
                int[] r = osuHits.get(p.getUUID());
                double acc = r != null && r[1] == 100 ? r[0] / 100.0 : 0;
                if (acc > 0.88) p.sendSystemMessage(Component.literal("Perfect parry. The blast breaks on you.").withStyle(ChatFormatting.GREEN));
                else if (acc > 0.65) { p.sendSystemMessage(Component.literal("A good parry. It still stings.").withStyle(ChatFormatting.YELLOW)); soften(l, p, 0.2f); }
                else if (acc > 0.35) { p.sendSystemMessage(Component.literal("A weak parry. It tears into you.").withStyle(ChatFormatting.GOLD)); soften(l, p, 0.55f); }
                else { p.sendSystemMessage(Component.literal("You missed the parry.").withStyle(ChatFormatting.DARK_RED)); hit(l, p); }
            }
            miniStart = -1000;
        });
    }

    /** A client reports how its minigame went. Only counted after it could have ended and before the answer is read. */
    static void osuResult(ServerPlayer p, int hits, int total) {
        if (rt < miniStart + miniTicks - 10 || rt > miniStart + miniTicks + 90) return;
        osuHits.put(p.getUUID(), new int[]{Math.max(0, Math.min(hits, total)), total});
    }

    /** 25%: heart seeds grow; break them (any way) within 40 seconds or they hit everyone and feed the tree. */
    private static void startSeeds(ServerLevel lv) {
        rootsActive = true;
        activeKind = 2;
        phaseEnd = rt + 1000;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int nSeeds = Math.min(24, Math.max(2, fighters(lv).size() / 6));
        double smax = PlanetGenerator.GRASS_R - 8, smin = Math.min(smax - 25, 70);   // only on the grass
        for (int i = 0, tries = 0; i < nSeeds && tries < nSeeds * 10; tries++) {
            double a = Math.PI * 2 * i / nSeeds + rnd.nextDouble() * 0.9, r = smin + Math.sqrt(rnd.nextDouble()) * (smax - smin);
            BlockPos b = BlockPos.containing(0.5 + Math.cos(a) * r, groundY, 0.5 + Math.sin(a) * r);
            if (!lv.getBlockState(b).canBeReplaced()) continue;
            lv.setBlock(b, Blocks.SHROOMLIGHT.defaultBlockState(), 3);
            seeds.add(b);
            i++;
        }
        tellLevel(lv, "Heart seeds have grown! The tree cannot be hurt. Break them all within 50 seconds, or each one feeds it 5% and the rest strike you all.", ChatFormatting.RED);
    }

    private static void seedsSecond(ServerLevel lv, Mob boss) {
        seeds.removeIf(b -> {
            if (lv.getBlockState(b).is(Blocks.SHROOMLIGHT)) return false;
            lv.sendParticles(ParticleTypes.EXPLOSION, b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
            return true;
        });
        if (seeds.isEmpty()) { endPhase(lv, "Every seed is broken. The tree is exposed!", ChatFormatting.GOLD); return; }
        long left = Math.max(0, (phaseEnd - rt) / 20);
        if (raidBar != null) raidBar.setName(barName("  -  seeds left: " + seeds.size() + "  (" + left + "s)", ChatFormatting.RED));
        for (BlockPos b : seeds) {
            lv.sendParticles(ParticleTypes.CRIMSON_SPORE, b.getX() + 0.5, b.getY() + 1.5, b.getZ() + 0.5, 6, 0.3, 0.5, 0.3, 0);
            tele(lv, 3, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, 0f, 45f, 1f, 0.2f, 0.3f, 30);
        }
        compass(lv, seeds, "Nearest seed");
        if (rt >= phaseEnd) {
            healBoss(boss, 0.05f * seeds.size());
            removeSeeds(lv);
            for (ServerPlayer p : fighters(lv)) killLife(lv, p);
            endPhase(lv, "The seeds burst.", ChatFormatting.DARK_RED);
        }
    }

    /**
     * 15%: a giant meteor falls from the sky. It is never announced and nothing marks the ground: the only place it cannot reach is under the tree.
     * Everyone outside ULT_SAFE_R of the trunk loses a whole life when it lands.
     */
    private static void sendAnim(ServerLevel lv, int which, int ticks) {
        Net.TreeAnimPayload pk = new Net.TreeAnimPayload(which, ticks, ThreadLocalRandom.current().nextInt(1000));
        for (ServerPlayer p : lv.players()) PacketDistributor.sendToPlayer(p, pk);
    }

    // ---- Divine Kingdom: Thorn Paradise, once she is under 10%

    private static boolean immuneBarShown = false;
    private static boolean kingdomDone = false;
    private static long kingdomEnd = 0, ultFallUntil = 0, ultImmuneUntil = 0;
    private static int kingdomLivesLeft = 0;   // lives that may still be lost before it ends: 80% of the fighters when it was raised
    private static final int KINGDOM_TICKS = 400;

    private static Object kingdomCall(Mob boss, String method) {
        try {
            Class<?> c = Class.forName("net.swimmingtuna.lotm.item.BeyonderAbilities.Prisoner.DivineKingdomThornParadise");
            return c.getMethod(method, net.minecraft.world.entity.LivingEntity.class).invoke(null, boss);
        } catch (Throwable t) {
            LOGGER.warn("[astral] Thorn Paradise {} failed: {}", method, String.valueOf(t.getCause() != null ? t.getCause() : t));
            return null;
        }
    }

    private static void fillSpirituality(Mob boss) {
        try { boss.getClass().getMethod("setSpirituality", int.class).invoke(boss, boss.getClass().getMethod("getMaxSpirituality").invoke(boss)); } catch (Throwable ignored) { }
    }

    private static void startKingdom(ServerLevel lv, Mob boss) {
        kingdomDone = true;
        kingdomEnd = rt + KINGDOM_TICKS;
        kingdomLivesLeft = Math.max(1, (int) Math.ceil(0.8 * fighters(lv).size()));
        fillSpirituality(boss);
        kingdomCall(boss, "activate");
        tellLevel(lv, treeName() + " raises a kingdom of thorns.", ChatFormatting.DARK_GREEN);
        LOGGER.info("[astral] Thorn Paradise raised: ends after {} lives or 20 seconds", kingdomLivesLeft);
    }

    private static void kingdomSecond(ServerLevel lv, Mob boss) {
        if (kingdomEnd == 0) return;
        fillSpirituality(boss);   // LOTM lets it fall when the caster runs dry
        if (rt >= kingdomEnd || kingdomLivesLeft <= 0) {
            kingdomEnd = 0;
            kingdomCall(boss, "deactivate");
            tellLevel(lv, "The thorns wither back into nothing.", ChatFormatting.GREEN);
        }
    }

    private static final int FALL_TICKS = 600;      // 30 s, the client's MeteorScene.FALL uses the same
    private static long ultCutsceneUntil = 0;

    private static void ultimate(ServerLevel lv) {
        sendAnim(lv, 20, FALL_TICKS + 40);
        ultImmuneUntil = rt + FALL_TICKS + 2;   // she takes no damage from the moment the cutscene starts until it lands
        ultFallUntil = rt + FALL_TICKS + 60;
        nextPattern = Math.max(nextPattern, rt + FALL_TICKS + 120);   // no other attack while it falls
        ultCutsceneUntil = rt + 170;
        for (ServerPlayer p : lv.players()) PacketDistributor.sendToPlayer(p, new Net.CutscenePayload(22, 160));   // the cutscene: everyone watches it start to fall
        final double cx = 0.5, cz = 0.5, top = cur.gy() + 1100;
        LOGGER.info("[astral] the tree's ultimate meteor begins");
        fx(lv, 6, cx, cur.gy(), cz, top - cur.gy(), FALL_TICKS, 9, rgb(1f, 0.4f, 0.08f), FALL_TICKS + 70);
        for (int t = 0; t < FALL_TICKS; t++) {
            final int tt = t;
            at(t, l -> {
                double f = tt / (double) FALL_TICKS;
                double y = top - (top - cur.gy()) * (0.6 * f + 0.4 * f * f);   // slow: it only speeds up a little near the ground
                double size = 60 + f * 90;
                l.sendParticles(ParticleTypes.FLAME, cx, y, cz, 30, size * 0.5, size * 0.4, size * 0.5, 0.02);
                l.sendParticles(ParticleTypes.LAVA, cx, y, cz, 10, size * 0.5, size * 0.4, size * 0.5, 0);
                l.sendParticles(ParticleTypes.LARGE_SMOKE, cx, y + size * 0.5, cz, 20, size * 0.5, size * 0.6, size * 0.5, 0.05);
                if (tt % 20 == 0) sfx(l, SoundEvents.WARDEN_HEARTBEAT, 3f, 0.4f + tt / 800f);
                if (tt == FALL_TICKS * 2 / 5) sfx(l, SoundEvents.ENDER_DRAGON_GROWL, 2f, 0.4f);
                if (tt == FALL_TICKS * 11 / 15) sfx(l, SoundEvents.WARDEN_ROAR, 3f, 0.5f);
            });
        }
        at(FALL_TICKS, l -> {
            ultImmuneUntil = 0;
            sfx(l, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.5f);
            sfx(l, SoundEvents.LIGHTNING_BOLT_THUNDER, 4f, 0.6f);
            l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cur.gy() + 2, cz, 30, 120, 6, 120, 0);
            for (int k = 0; k < 12; k++) {
                double a = Math.PI * 2 * k / 12;
                boom(l, cx + Math.cos(a) * 60, cz + Math.sin(a) * 60, 40);
                boom(l, cx + Math.cos(a) * 140, cz + Math.sin(a) * 140, 60);
                boom(l, cx + Math.cos(a) * 220, cz + Math.sin(a) * 220, 60);
            }
            AstralData ud = AstralData.get(l.getServer());
            float heal = 0f;
            for (ServerPlayer p : fighters(l)) {
                if (Math.hypot(p.getX() - cx, p.getZ() - cz) <= ULT_SAFE_R) continue;
                if (god.contains(p.getUUID())) continue;
                if (blastShielded(l, new net.minecraft.world.phys.Vec3(cx, cur.gy() + 4, cz), p)) { p.displayClientMessage(Component.literal("The blocks took the blast.").withStyle(ChatFormatting.GOLD), true); continue; }   // behind blocks: the meteor cannot reach you
                int before = ud.raidLives.getOrDefault(p.getUUID(), 0);
                p.setHealth(Math.min(p.getHealth(), 1f));
                killLife(l, p);
                heal += 0.01f;                                                   // everyone it hits feeds her 1%
                if (before > 0 && ud.raidLives.getOrDefault(p.getUUID(), 0) <= 0) heal += 0.015f;   // and a kill another 1.5%
            }
            Mob tree = findBoss(l, TAG_RAID);
            if (tree != null && heal > 0f) {
                healBoss(tree, heal);
                tellLevel(l, "The meteor feeds her: she heals " + String.format("%.1f", heal * 100) + "%.", ChatFormatting.DARK_RED);
            }
        });
    }

    private static void startRoots(ServerLevel lv) {
        rootsActive = true;
        activeKind = 0;
        int players = fighters(lv).size();
        int n = Math.min(60, 2 + rootStage + players);   // one fighter: 3 to 5 roots; a crowd: up to 60
        rootsEnd = rt + Math.min(2400, 1200 + 40L * n);          // 60 seconds and a little more for every root
        double rmax = PlanetGenerator.GRASS_R - 8, rmin = Math.min(rmax - 25, 70);      // only on the grass, outside the trunk itself
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0, tries = 0; i < n && tries < n * 8; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = rmin + Math.sqrt(rnd.nextDouble()) * (rmax - rmin);
            BlockPos b = BlockPos.containing(0.5 + Math.cos(a) * r, groundY, 0.5 + Math.sin(a) * r);
            if (!lv.getBlockState(b).canBeReplaced() || !lv.getBlockState(b.above()).canBeReplaced()) continue;   // grass and flowers do not stop a root
            lv.setBlock(b, Blocks.MANGROVE_WOOD.defaultBlockState(), 3);
            lv.setBlock(b.above(), Blocks.MANGROVE_WOOD.defaultBlockState(), 3);
            roots.add(b);
            i++;
        }
        tellLevel(lv, "Roots surge over the battlefield! The tree cannot be hurt. BURN THE ROOTS (flint and steel, or any fire next to them) before time runs out, or it heals to full.", ChatFormatting.DARK_GREEN);
    }

    /** Tells every fighter how far and in which direction the closest of these blocks is, as a compass arrow. */
    private static void compass(ServerLevel lv, List<BlockPos> targets, String what) {
        if (targets.isEmpty()) return;
        String[] arrows = {"↓", "↙", "←", "↖", "↑", "↗", "→", "↘"};
        for (ServerPlayer p : fighters(lv)) {
            BlockPos best = null; double bd = Double.MAX_VALUE;
            for (BlockPos b : targets) { double d = b.distToCenterSqr(p.getX(), p.getY(), p.getZ()); if (d < bd) { bd = d; best = b; } }
            if (best == null) continue;
            double want = Math.toDegrees(Math.atan2(-(best.getX() + 0.5 - p.getX()), best.getZ() + 0.5 - p.getZ()));   // the yaw that faces it
            double rel = net.minecraft.util.Mth.wrapDegrees(want - p.getYRot());                                      // 0 = straight ahead
            int idx = (int) Math.floorMod(Math.round(rel / 45.0) + 4, 8);                                             // 4 = ahead (arrow up)
            p.displayClientMessage(Component.literal(what + ": " + arrows[idx] + "  " + (int) Math.sqrt(bd) + " blocks   (" + targets.size() + " left)").withStyle(ChatFormatting.GREEN), true);
        }
    }

    private static void tellLevel(ServerLevel lv, String msg, ChatFormatting c) {
        for (ServerPlayer p : lv.players()) p.sendSystemMessage(Component.literal(msg).withStyle(c));
    }

    /** Once a second during the raid. */
    /** The tree grows with the number of fighters: 1.0 for one, up to 1.5 for thirteen or more. */
    private static void updateTreeScale(ServerLevel lv) {
        float want = (float) Math.max(1.0, Math.min(1.5, 1 + 0.04 * (fighters(lv).size() - 1)));
        if (Math.abs(want - treeScale) < 0.01f) return;
        treeScale = want;
        for (ServerPlayer p : lv.players()) PacketDistributor.sendToPlayer(p, new Net.TreeScalePayload(treeScale));
    }

    private static void rootsSecond(ServerLevel lv) {
        Mob boss = findBoss(lv, TAG_RAID);
        if (boss == null) return;
        updateTreeScale(lv);
        float frac = boss.getHealth() / RAID_HP;
        int bucket = (int) Math.floor(frac * 20);
        offerSecond(lv, frac);
        raidAlliesSecond(lv);
        if (bucket > lineBucket) lineBucket = bucket;   // healed
        else if (bucket < lineBucket) {
            if (!treeFleshy && frac > 0.5f) {   // every 5% above half she pleads; after the change she no longer does
                String line = WORLD_TREE_LINES[ThreadLocalRandom.current().nextInt(WORLD_TREE_LINES.length)];
                tellLevel(lv, "The World Tree: " + line, ChatFormatting.LIGHT_PURPLE);
            }
            lineBucket = bucket;
        }
        kingdomSecond(lv, boss);
        if (raidBar != null && rt < ultImmuneUntil) { raidBar.setName(barName("  -  the meteor shields her", ChatFormatting.DARK_RED)); immuneBarShown = true; }
        else if (immuneBarShown) { immuneBarShown = false; if (!rootsActive) raidBar.setName(barName("", ChatFormatting.DARK_PURPLE)); }
        if (!rootsActive) {
            if (!kingdomDone && !cutscene && rootStage >= THRESHOLDS.length && rt >= ultFallUntil && frac < 0.10f) { startKingdom(lv, boss); return; }
            if (rootStage < THRESHOLDS.length && boss.getHealth() <= THRESHOLDS[rootStage] * RAID_HP + 3f) {
                if (rootStage == 0) startRoots(lv); else if (rootStage == 1) startTransformation(lv); else startSeeds(lv);
            } else if (rootStage >= THRESHOLDS.length && !cutscene && rt >= nextUltimate && boss.getHealth() / RAID_HP <= 0.15f) {
                nextUltimate = rt + 2400 + ThreadLocalRandom.current().nextInt(1201);   // 120 to 180 seconds until it can fall again, drawn anew each time
                ultimate(lv);
            }
            return;
        }
        if (activeKind == 2) { seedsSecond(lv, boss); return; }
        if (activeKind == 1 || activeKind == 3) return; // desire's call and the transformation end on their own timers
        roots.removeIf(b -> {
            if (!burnt(lv, b)) return false;
            lv.sendParticles(ParticleTypes.FLAME, b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5, 20, 0.3, 0.6, 0.3, 0.02);
            if (lv.getBlockState(b).is(Blocks.MANGROVE_WOOD)) lv.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
            if (lv.getBlockState(b.above()).is(Blocks.MANGROVE_WOOD)) lv.setBlock(b.above(), Blocks.AIR.defaultBlockState(), 3);
            return true;
        });
        if (roots.isEmpty()) {
            rootsActive = false;
            rootStage++;
            if (raidBar != null) raidBar.setName(barName("", ChatFormatting.DARK_PURPLE));
            tellLevel(lv, "The roots burn. The tree is exposed again!", ChatFormatting.GOLD);
            return;
        }
        long left = Math.max(0, (rootsEnd - rt) / 20);
        if (raidBar != null) raidBar.setName(barName("  -  roots left: " + roots.size() + "  (" + left + "s)", ChatFormatting.DARK_GREEN));
        for (BlockPos b : roots) {
            lv.sendParticles(ParticleTypes.HAPPY_VILLAGER, b.getX() + 0.5, b.getY() + 2.3, b.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0);
            tele(lv, 3, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, 0f, 45f, 0.2f, 1f, 0.3f, 30);
        }
        compass(lv, roots, "Nearest root");
        if (rt >= rootsEnd) {
            removeRoots(lv);
            healOkUntil = gt + 4;
            boss.setHealth(RAID_HP);
            rootsActive = false;
            rootStage = 0;
            if (raidBar != null) raidBar.setName(barName("", ChatFormatting.DARK_PURPLE));
            lineBucket = 20;
            tellLevel(lv, "The roots drink. " + treeName() + " is whole again.", ChatFormatting.DARK_RED);
        }
    }

    private static void raidTick(MinecraftServer server, AstralData d) {
        ServerLevel lv = dim(server, "raid");
        if (lv == null) return;
        if (gathering) { gatherTick(lv); return; }
        if (raidOver) return;   // the tree is dying: only the scheduled animation runs
        rt++;
        cur = new Arena(lv, 0.5, 0.5, groundY, true);
        if (introQueue && rt >= INTRO_TICKS + CHAT_AFTER_EXTRA) { introQueue = false; for (Runnable r : introChat) r.run(); introChat.clear(); }
        sanityLockTick(lv);
        echoTick(lv);
        counterTick(lv);
        if (rt % 20 == 0) { rootsSecond(lv); auraSecond(lv); giftSecond(lv); }
        if (aggro) treeCast(lv);
        else if (rt >= graceUntil && rt % 5 == 0) {
            for (ServerPlayer p : fighters(lv)) if (Math.hypot(p.getX() - 0.5, p.getZ() - 0.5) < WAKE_R) { wake(lv, p.getGameProfile().getName() + " came too close"); break; }
        }
        if (rt % 5 == 0) for (ServerPlayer p : fighters(lv)) { double sp = Lotm.sanityPercent(p); if (sp >= 0 && sp <= 3) sanityBreak(p); }   // before LOTM's own collapse
        if (aggro && rt >= nextPattern && !cutscene && !(rootsActive && activeKind == 1) && !fighters(lv).isEmpty()) {
            int[] opts = NEXT[lastPattern];
            int which = opts[ThreadLocalRandom.current().nextInt(opts.length)];
            Mob tb = findBoss(lv, TAG_RAID);
            boolean tempest = tb != null && tb.getHealth() < RAID_HP * 0.35f && rt >= nextTempest;
            if (tempest) { which = 12; nextTempest = rt + 1500; }
            if (which == 15 && !treeFleshy) which = 5;   // the mind assault belongs to the fleshy form
            if (which == 4 && disharmony(lv)) which = 10;   // the purge is never used while anyone's legs disagree with them (the Cursing Aura)
            lastPattern = which;
            pattern(lv, which);
            nextPattern = rt + DURATION[which] + 90 + ThreadLocalRandom.current().nextInt(60);   // longer gaps: the attacks are bigger now
        }
    }

    private static void processTimed() {
        List<Timed> due = new ArrayList<>();
        for (Timed t : timed) if (t.at() <= gt) due.add(t);
        timed.removeAll(due);
        for (Timed t : due) { cur = t.a(); t.run().accept(t.a().lv()); }
    }

    /**
     * Nobody regresses in a boss fight: a regress spawns a corrupted rampager (LOTM's fallen persona) that would fight the players.
     * LOTM's own 10 minute regression protection is kept topped up, a sanity that is about to collapse is lifted, and a sequence that dropped anyway is put back.
     */
    private static void holdSequences(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator() || !(p.level().dimension().equals(Cosmos.COSMOS) || Cosmos.isPlanetDim(p.level().dimension()))) continue;
            var tag = p.getPersistentData();
            tag.putInt("sequenceRegressionProtectionTimer", 6000);
            tag.putInt("sequenceImmunityTimer", 6000);
            double sp = Lotm.sanityPercent(p);
            if (sp >= 0 && sp <= 3 && !p.level().dimension().equals(Cosmos.COSMOS)) Lotm.setSanityPercent(p, 50);   // the raid takes a life for it instead (sanityBreak)
            int sq = Lotm.sequence(p), kept = raidSeq.getOrDefault(p.getUUID(), -1);
            if (sq < 0) continue;
            if (kept < 0 || sq < kept) raidSeq.put(p.getUUID(), sq);
            else if (sq > kept && Lotm.setSequence(p, kept)) {
                LOGGER.info("[astral] {} was regressed from Seq {} to Seq {} in a boss fight: put back", p.getGameProfile().getName(), kept, sq);
                p.sendSystemMessage(Component.literal("Something tried to tear your sequence away. The Astral World holds it.").withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
    }

    private static void secondRaid(MinecraftServer server, AstralData d) {
        ServerLevel lv = dim(server, "raid");
        if (lv == null) return;
        // everyone belongs in the arena; the battlefield grows with the crowd (about 100 blocks alone, the whole arena at 100 players)
        int crowdSize = 0;
        for (int v : d.raidLives.values()) if (v > 0) crowdSize++;
        final double leash = PlanetGenerator.PLAT_R - 3;   // the whole stone platform, right to its edge, is the battlefield; only beyond it are you pulled back
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean fresh = !d.raidLives.containsKey(p.getUUID());
            d.raidLives.putIfAbsent(p.getUUID(), LIVES);
            if (p.level() != lv) p.teleportTo(lv, 0.5 + SPAWN_R, groundY, 0.5, p.getYRot(), 0f);
            if (fresh) blessPlayer(p);
            else if (!gathering && !p.isSpectator() && p.isAlive()) {
                double px = p.getX() - 0.5, pz = p.getZ() - 0.5, dist = Math.hypot(px, pz);
                if (dist > leash) { // the battlefield has an edge: nobody outruns the tree
                    double k = (leash - 12) / dist;
                    p.teleportTo(lv, 0.5 + px * k, Math.max(groundY, p.getY()), 0.5 + pz * k, p.getYRot(), p.getXRot());
                    p.displayClientMessage(Component.literal("The Astral World will not let you flee. Fight!").withStyle(ChatFormatting.DARK_RED), true);
                } else if (dist > leash - 20) p.displayClientMessage(Component.literal("The edge of the battlefield is near. You cannot run from this.").withStyle(ChatFormatting.RED), true);
            }
            if (d.raidLives.get(p.getUUID()) <= 0) setGameMode(p, GameType.SPECTATOR);
            else {
                long[] dz = desire.get(p.getUUID());
                Component bar = Component.literal("Lives: " + "♥".repeat(d.raidLives.get(p.getUUID()))).withStyle(ChatFormatting.RED);
                if (dz != null && dz[0] > 0) bar = bar.copy().append(Component.literal("    Desire x" + dz[0]).withStyle(ChatFormatting.LIGHT_PURPLE));   // one line: Desire no longer covers the lives
                p.displayClientMessage(bar, true);
            }
        }
        Mob boss = findBoss(lv, TAG_RAID);
        if (boss == null) {
            if (!d.bossesDead.contains("raid")) { // it was lost somehow without dying: bring it back at full strength
                Mob m = makeMob(lv, RAID_PATHWAY, RAID_NAME, 0.5, groundY + 2, 0.5);
                if (m != null) { m.addTag(TAG_BOSS); m.addTag(TAG_RAID); m.setNoAi(true); m.setNoGravity(true); if (raidRespawnHp > 0) { m.setHealth(Math.min(RAID_HP, raidRespawnHp)); raidRespawnHp = -1f; } lv.addFreshEntity(m); }
            }
        } else {
            if (raidBar == null) raidBar = new ServerBossEvent(barName("", ChatFormatting.DARK_PURPLE),
                    BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_20);
            raidBar.setProgress(Math.max(0f, Math.min(1f, boss.getHealth() / RAID_HP)));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) raidBar.addPlayer(p);
        }
        boolean anyAlive = false;
        for (var en : d.raidLives.entrySet()) if (en.getValue() > 0 && !en.getKey().equals(traitor)) anyAlive = true;   // the tree's ally does not keep the fight alive
        if (!anyAlive && !d.raidLives.isEmpty()) {
            tell(server, "It is over.", ChatFormatting.DARK_RED);
            endTraitor(server);
            leaderboard(server, d);
            Stage.set(server, 5); // phase 5 again, no countdown
        }
    }

    private static boolean deathAnimStarted = false;
    static boolean raidOver = false;

    /** Her death: the flesh melts into a puddle and the puddle dries into dust (about 15 seconds), then the victory closes the fight. */
    private static void raidWon(MinecraftServer server, AstralData d) {
        ServerLevel rl = dim(server, "raid");
        if (!deathAnimStarted && rl != null) {
            deathAnimStarted = true;
            raidOver = true;
            timed.clear();
            for (ServerPlayer p : rl.players()) PacketDistributor.sendToPlayer(p, new Net.CutscenePayload(24, 300));
            cur = new Arena(rl, 0.5, 0.5, groundY, true);
            fx(rl, 20, 0.5, groundY, 0.5, 80 * treeScale, 0, 0, rgb(0.42f, 0.06f, 0.1f), 310);
            sfx(rl, SoundEvents.WARDEN_DEATH, 5f, 0.5f);
            for (int t = 0; t < 280; t += 14) at(t, l -> { sfx(l, SoundEvents.SLIME_BLOCK_BREAK, 4f, 0.4f); sfx(l, SoundEvents.HONEY_BLOCK_SLIDE, 3f, 0.5f); });
            at(160, l -> sfx(l, SoundEvents.WITHER_BREAK_BLOCK, 3f, 0.5f));
            at(300, l -> raidWon(server, d));   // the second time through it carries on
            return;
        }
        deathAnimStarted = false;
        raidOver = false;
        LOGGER.info("[astral] the raid boss fell: Earth returns, stage 0 for {} days", Settings.recoveryDays);
        int resume = d.resumeStage;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.displayClientMessage(Component.literal("VICTORY").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        tell(server, "Victory! " + RAID_NAME + " has been defeated.", ChatFormatting.GOLD);
        leaderboard(server, d);
        endTraitor(server);
        resetAll(server); // everyone goes home to a restored Earth
        internal = true;
        try { Stage.set(server, 0); } finally { internal = false; }
        d.resumeStage = resume;
        d.hp = AstralBarrier.getMax(server);
        d.endPhase = "recover";
        d.endUntil = System.currentTimeMillis() + (long) (Settings.recoveryDays * DAY);
        d.setDirty();
        tell(server, "The barrier is whole again and Earth has returned. The server closes in 30 seconds (operators may stay).", ChatFormatting.GOLD);
        beginClose(server, 30, "Victory! The server is now closed. Thank you for playing.");
    }

    private static void secondRecover(MinecraftServer server, AstralData d) {
        if (System.currentTimeMillis() < d.endUntil) return;
        int resume = Math.max(1, d.resumeStage);
        d.endPhase = "";
        d.setDirty();
        Stage.set(server, resume);
    }

    // ------------------------------------------------------------------ events

    // ------------------------------------------------------------------ boss movement

    private static final Map<String, UUID> bossIds = new HashMap<>();
    private static final Map<String, double[]> bossMove = new HashMap<>(); // angle, radius, direction, next change (tick), unused
    /** Sovereigns that only fight once you come close or hurt them (Neptune). */
    private static final Set<String> PASSIVE = Set.of("neptune");
    private static final double WAKE_RANGE = 30;
    private static final Set<String> woken = new java.util.HashSet<>();

    /** True when this boss should be fighting: not a passive one, or already provoked, or someone has come within WAKE_RANGE. */
    private static boolean aggro(String planet, ServerLevel lv, Mob m) {
        if (!PASSIVE.contains(planet) || woken.contains(planet)) return true;
        for (ServerPlayer p : lv.players())
            if (!p.isSpectator() && p.isAlive() && p.distanceToSqr(m) < WAKE_RANGE * WAKE_RANGE) { woken.add(planet); return true; }
        return false;
    }

    private static final double LEASH = 34;   // bosses never leave this circle around the arrival point

    private static void blink(ServerLevel lv, Mob m, double x, double z, double hover) {
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, m.getX(), m.getY() + 5, m.getZ(), 60, 3, 4, 3, 0.4);
        int gy = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        m.setPos(x, gy + hover, z);
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, x, gy + hover + 5, z, 60, 3, 4, 3, 0.4);
    }

    /** Scripted movement, one style per sovereign. None of them just chase you: they orbit, blink, or hold a distance and circle. */
    private static void bossMoveTick(MinecraftServer server, AstralData d) {
        java.util.Random rnd = ThreadLocalRandom.current();
        for (BossDef b : BOSSES) {
            if (d.bossesDead.contains(b.planet())) continue;
            UUID id = bossIds.get(b.planet());
            ServerLevel lv = dim(server, b.planet());
            if (id == null || lv == null) continue;
            if (!(lv.getEntity(id) instanceof Mob m) || !m.isAlive()) continue;
            if (!aggro(b.planet(), lv, m)) continue;
            ServerPlayer near = null;
            for (ServerPlayer p : lv.players()) if (!p.isSpectator() && p.isAlive() && (near == null || p.distanceToSqr(m) < near.distanceToSqr(m))) near = p;
            if (near == null) continue;
            double[] st = bossMove.computeIfAbsent(b.planet(), k -> new double[]{Math.atan2(m.getZ() - 0.5, m.getX() - 0.5), 20, rnd.nextBoolean() ? 1 : -1, gt + 200, 0});
            double x = m.getX(), z = m.getZ(), hover = 0;
            switch (b.planet()) {
                case "mercury", "uranus" -> { // blink: stands still, then steps through a door somewhere else
                    hover = b.planet().equals("mercury") ? 5 : 6;
                    if (gt >= st[3]) {
                        double a = rnd.nextDouble() * Math.PI * 2, r = 14 + rnd.nextDouble() * 10;
                        double bx = 0.5 + Math.cos(a) * r, bz = 0.5 + Math.sin(a) * r;
                        if (b.planet().equals("uranus")) { // behind whoever is nearest
                            double ang = Math.atan2(near.getZ() - 0.5, near.getX() - 0.5) + Math.PI + (rnd.nextDouble() - .5);
                            bx = 0.5 + Math.cos(ang) * 22; bz = 0.5 + Math.sin(ang) * 22;
                        }
                        blink(lv, m, bx, bz, hover);
                        x = bx; z = bz;
                        st[3] = gt + (b.planet().equals("mercury") ? 120 : 200) + rnd.nextInt(80);
                    }
                }
                case "mars" -> { // a walker: keeps its distance, backs off if you crowd it, and circles
                    double dx = near.getX() - x, dz = near.getZ() - z, dist = Math.max(0.01, Math.hypot(dx, dz));
                    double ux = dx / dist, uz = dz / dist;
                    if (gt >= st[3]) { st[2] = -st[2]; st[3] = gt + 200 + rnd.nextInt(200); }
                    if (dist > 18) { x += ux * 0.2; z += uz * 0.2; }
                    else if (dist < 11) { x -= ux * 0.16; z -= uz * 0.16; }
                    else { x += -uz * st[2] * 0.1; z += ux * st[2] * 0.1; }
                }
                default -> { // fliers circle the arena, each in its own way
                    double speed, radius;
                    switch (b.planet()) {
                        case "venus" -> { speed = 0.012; radius = 22 + 6 * Math.sin(gt * 0.004); hover = 9; }
                        case "jupiter" -> { speed = 0.006; radius = 18 + 4 * Math.sin(gt * 0.01); hover = 5; }
                        case "saturn" -> { speed = 0.01; radius = 19 + 7 * Math.sin(gt * 0.006); hover = 3; }
                        default -> { speed = 0.008; radius = 22; hover = 7; }
                    }
                    if (gt >= st[3]) { st[2] = -st[2]; st[3] = gt + 400 + rnd.nextInt(300); }
                    st[0] += st[2] * speed;
                    x = 0.5 + Math.cos(st[0]) * radius;
                    z = 0.5 + Math.sin(st[0]) * radius;
                }
            }
            double dx = x - 0.5, dz = z - 0.5, dd = Math.hypot(dx, dz);
            if (dd > LEASH) { x = 0.5 + dx / dd * LEASH; z = 0.5 + dz / dd * LEASH; }
            int gy = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            double y = m.getY() + (gy + hover - m.getY()) * 0.08;
            m.setPos(x, y, z);
            float yaw = (float) Math.toDegrees(Math.atan2(-(near.getX() - x), near.getZ() - z));
            m.setYRot(yaw); m.setYHeadRot(yaw); m.yBodyRot = yaw;
        }
    }

    // ------------------------------------------------------------------ no instant kills

    /** LOTM flags and timers that kill or wither a target (found in the LOTM jar: Hands of Death, Death Timer, Death Seal, All Dying, Absolute Death, Purification mark, killUnpreventably). */
    private static final String[] DEATH_FLAGS = {"lotmUnpreventableDeath", "lotmExecuting", "ignoreAttackEvent", "handsOfDeathDuration", "handsOfDeathCeiling",
            "handsOfDeathCaster", "lotmDeathTimer", "deathSealActive", "deathSealEntity", "deathSealOwnerUUID", "deathSealTimer", "allDying", "allDyingOwnerUUID",
            "allDyingTimer", "purificationDeathMark", "unshadowedDomainPurgeUntil",
            "warriorLightConcealment", "warriorLightEvasion", "warriorLightEvasionDistance"}; // the last three: Warrior light form, which makes a boss unhittable
    private static final Map<UUID, Float> lastHp = new HashMap<>();
    private static final Map<UUID, Long> lastPlayerHit = new HashMap<>();   // when a player last hurt each boss
    static final double ARENA_R = 340;        // the raid arena: fighters may roam this far from the tree (built for up to ~100 players)
    private static final double SPAWN_R = 120;    // fighters arrive well outside the tree's trunk (the model's trunk is about 15 blocks wide at the foot)
    private static final float MAX_HIT = 0.10f;     // no single hit takes more than this share of a boss's health
    private static final float MAX_DROP = 0.12f;    // anything that takes more than this in one tick is not a hit but an execution
    private static final float RAID_HIT = 0.02f;    // the Mother Tree: no single hit takes more than this share
    private static final float RAID_RATE = 0.04f;   // ...and nothing takes more than this share of it per second, however many hits an ability lands
    private static final float BOSS_RATE = 0.08f;   // same for the planet sovereigns
    private static final Map<UUID, long[]> rateWin = new HashMap<>();   // boss -> {window start tick, damage taken in the window (as float bits)}

    /** Caps what a boss can lose per second so multi-hit abilities (Pillar of Lightning and the like) cannot chain past the per-hit cap. Returns the allowed amount. */
    private static float rateLimit(net.minecraft.world.entity.LivingEntity e, float amount, float rate) { return rateLimit(e, e.getUUID(), amount, rate); }

    private static float rateLimit(net.minecraft.world.entity.LivingEntity e, UUID key, float amount, float rate) {
        long[] w = rateWin.computeIfAbsent(key, k -> new long[]{gt, Float.floatToIntBits(0f)});
        if (gt - w[0] >= 20) { w[0] = gt; w[1] = Float.floatToIntBits(0f); }
        float used = Float.intBitsToFloat((int) w[1]);
        float allowed = Math.max(0f, e.getMaxHealth() * rate - used);
        float out = Math.min(amount, allowed);
        w[1] = Float.floatToIntBits(used + out);
        return out;
    }

    private static java.lang.reflect.Method claimedM, unclaimM;
    private static boolean ownerLookupFailed;

    /** The Death pathway cannot enslave a boss: any claim on it is undone on the next tick. */
    private static void freeFromOwner(Mob m) {
        if (ownerLookupFailed) return;
        try {
            if (claimedM == null) {
                Class<?> c = Class.forName("net.swimmingtuna.lotm.capabilities.spirit_data.OwnershipUtils");
                claimedM = c.getMethod("isClaimed", net.minecraft.world.entity.LivingEntity.class);
                unclaimM = c.getMethod("unclaimEntity", net.minecraft.world.entity.LivingEntity.class);
            }
            if (m.getPersistentData().contains("lotmCapturedBy")) m.getPersistentData().remove("lotmCapturedBy");
            if ((Boolean) claimedM.invoke(null, m)) {
                unclaimM.invoke(null, m);
                m.setTarget(null);
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            ownerLookupFailed = true;
            LOGGER.warn("[astral] could not find LOTM ownership code, bosses are not protected from enslavement: {}", String.valueOf(e));
        } catch (Throwable t) { /* try again next tick */ }
    }

    private static void stripDeath(Mob m) {
        m.removeEffect(net.minecraft.world.effect.MobEffects.POISON);
        m.removeEffect(net.minecraft.world.effect.MobEffects.WITHER);
        m.removeEffect(net.minecraft.world.effect.MobEffects.HARM);
        var tag = m.getPersistentData();
        for (String k : DEATH_FLAGS) if (tag.contains(k)) tag.remove(k);
        for (String k : new ArrayList<>(tag.getAllKeys())) {   // Depravity and Corruption (Hanged Man, Profane Language) wear a boss down outside every phase rule
            String lk = k.toLowerCase(java.util.Locale.ROOT);
            if (lk.startsWith("profanedepravity") || lk.startsWith("profanedegenerate") || lk.contains("corruption") || lk.contains("curse") || lk.startsWith("strawdoll")) tag.remove(k);   // woven curses, curse dolls and cursed items too
        }
        tag.putLong("insideProtectiveStructure", Long.MAX_VALUE);   // LOTM's own flag for 'protected from the World of Pallor': it shrinks, drains and strips abilities from anything else
        for (var inst : new ArrayList<>(m.getActiveEffects()))   // every harmful effect (curses, decay, venom, fragility and the rest of the Prisoner's weave) is wiped from a boss
            if (inst.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) m.removeEffect(inst.getEffect());
        for (var inst : new ArrayList<>(m.getActiveEffects()))
            inst.getEffect().unwrapKey().ifPresent(key -> {
                if (key.location().getNamespace().equals("lotm") && (key.location().getPath().contains("death") || key.location().getPath().contains("doom"))) m.removeEffect(inst.getEffect());
            });
    }

    /** Every tick: strip death effects from every boss and undo any health loss that is too big to be a hit (setHealth, kill and the like). */
    private static void protectBosses(MinecraftServer server) {
        for (BossDef b : BOSSES) guardLevel(dim(server, b.planet()));
        guardLevel(dim(server, "raid"));
    }

    /** Hitbox size in blocks (width, height) per boss, matched to the drawn model. The mob itself is player-sized (0.6 x 1.8). */
    private static final Map<String, double[]> HITBOX = Map.of("mercury", new double[]{14, 13}, "venus", new double[]{24, 10}, "mars", new double[]{8, 7},
            "jupiter", new double[]{17, 13}, "saturn", new double[]{11, 11}, "uranus", new double[]{18, 20}, "neptune", new double[]{15, 15}, "tree", new double[]{120, 84});

    private static boolean pkOk = false, pkTried = false;
    private static Object pkWidth, pkHeight;
    private static java.lang.reflect.Method pkData, pkGet, pkSet;

    /** Pehkui (it is in the pack, loaded through Connector) scales hitboxes without limit and per axis. Reached by reflection so the mod needs no dependency. */
    private static boolean pehkui(Mob m, float w, float h) {
        try {
            if (!pkTried) {
                pkTried = true;
                Class<?> types = Class.forName("virtuoel.pehkui.api.ScaleTypes");
                pkWidth = types.getField("HITBOX_WIDTH").get(null);
                pkHeight = types.getField("HITBOX_HEIGHT").get(null);
                for (var me : Class.forName("virtuoel.pehkui.api.ScaleType").getMethods()) if (me.getName().equals("getScaleData") && me.getParameterCount() == 1) pkData = me;
                Class<?> sd = Class.forName("virtuoel.pehkui.api.ScaleData");
                pkGet = sd.getMethod("getBaseScale");
                pkSet = sd.getMethod("setScale", float.class);
                pkOk = pkData != null;
                LOGGER.info("[astral] Pehkui found: boss hitboxes use it");
            }
            if (!pkOk) return false;
            Object[][] axes = {{pkWidth, w}, {pkHeight, h}};
            for (Object[] a : axes) {
                Object data = pkData.invoke(a[0], m);
                float cur = ((Number) pkGet.invoke(data)).floatValue(), want = (Float) a[1];
                if (Math.abs(cur - want) > 0.01f * want) pkSet.invoke(data, want);
            }
            return true;
        } catch (Throwable t) {
            if (pkOk) LOGGER.warn("[astral] Pehkui could not scale a hitbox, using the vanilla scale attribute instead: {}", String.valueOf(t));
            else if (pkTried) LOGGER.info("[astral] Pehkui not usable ({}): using the vanilla scale attribute for hitboxes", String.valueOf(t));
            pkOk = false;
            return false;
        }
    }

    /** While the tree cannot be hurt (roots, seeds, the call, the transformation) its box shrinks to nothing, so clicks reach the roots and seeds that stand inside the big one. */
    private static void sizeHitbox(Mob m) {
        String n = m.hasCustomName() ? m.getCustomName().getString() : "";
        double[] box = n.startsWith(RAID_NAME) || n.startsWith(EARLY_TREE) ? (rootsActive ? new double[]{4, 20} : HITBOX.get("tree")) : n.startsWith("Sovereign of ") ? HITBOX.get(n.substring(13).trim().toLowerCase(java.util.Locale.ROOT)) : null;
        if (box == null) return;
        var inst = m.getAttribute(Attributes.SCALE);
        if (pehkui(m, (float) (box[0] / 0.6), (float) (box[1] / 1.8))) {
            if (inst != null && Math.abs(inst.getBaseValue() - 1.0) > 0.01) { inst.setBaseValue(1.0); m.refreshDimensions(); }   // not both
            return;
        }
        double want = Math.min(16, Math.max(box[0] / 0.6, box[1] / 1.8));   // the vanilla attribute stops at 16, one scale for both axes
        if (inst != null && Math.abs(inst.getBaseValue() - want) > 0.01) { inst.setBaseValue(want); m.refreshDimensions(); }
    }

    // ---- the tree's hitbox is several boxes: the trunk (the boss itself) and invisible parts for the root plate and the canopy that pass every hit on to her
    static final String TAG_PART = "astral_part";
    private static final double[][] TREE_PARTS = {{330, 14, 0}, {160, 14, 14}, {130, 14, 84}, {250, 42, 98}, {280, 14, 140}, {360, 84, 154}, {240, 16, 238}};   // width, height, floor above the tree's foot: measured from the model (the boss's own box is the trunk, 120 wide and 84 tall)
    private static final Map<Integer, UUID> partIds = new HashMap<>();
    private static net.minecraft.world.scores.PlayerTeam partTeam;

    /** Keeps the tree's extra hitboxes in place and the right size. While she cannot be hurt (roots, seeds, call, transformation) they shrink away like the trunk does. */
    private static void treeParts(ServerLevel lv, Mob boss) {
        var sb = lv.getScoreboard();
        if (partTeam == null || sb.getPlayerTeam("astral_parts") == null) {
            partTeam = sb.getPlayerTeam("astral_parts") != null ? sb.getPlayerTeam("astral_parts") : sb.addPlayerTeam("astral_parts");
            partTeam.setCollisionRule(net.minecraft.world.scores.Team.CollisionRule.NEVER);
        }
        for (int i = 0; i < TREE_PARTS.length; i++) {
            double[] pd = TREE_PARTS[i];
            Entity ex = partIds.get(i) == null ? null : lv.getEntity(partIds.get(i));
            Mob part;
            if (ex instanceof Mob pm && pm.isAlive()) part = pm;
            else {
                part = EntityType.IRON_GOLEM.create(lv);
                if (part == null) return;
                part.addTag(TAG_PART);
                part.setNoAi(true); part.setNoGravity(true); part.setSilent(true); part.setInvisible(true); part.setPersistenceRequired();
                part.noPhysics = true;
                var mh = part.getAttribute(Attributes.MAX_HEALTH);
                if (mh != null) mh.setBaseValue(1_000_000.0);
                part.setHealth(1_000_000f);
                part.moveTo(boss.getX(), boss.getY() + pd[2], boss.getZ());
                lv.addFreshEntity(part);
                sb.addPlayerToTeam(part.getStringUUID(), partTeam);
                partIds.put(i, part.getUUID());
            }
            part.getPersistentData().putUUID("partOf", boss.getUUID());
            part.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            if (Math.abs(part.getX() - boss.getX()) > 0.01 || Math.abs(part.getY() - (boss.getY() + pd[2])) > 0.01 || Math.abs(part.getZ() - boss.getZ()) > 0.01) part.setPos(boss.getX(), boss.getY() + pd[2], boss.getZ());
            double w = rootsActive ? 1 : pd[0], h = rootsActive ? 1 : pd[1];
            if (!pehkui(part, (float) (w / 1.4), (float) (h / 2.7))) {
                var inst = part.getAttribute(Attributes.SCALE);
                double want = Math.min(16, Math.max(w / 1.4, h / 2.7));
                if (inst != null && Math.abs(inst.getBaseValue() - want) > 0.01) { inst.setBaseValue(want); part.refreshDimensions(); }
            }
        }
    }

    /** Parts whose tree is gone are removed. */
    private static void sweepParts(ServerLevel lv) {   // the invisible hitbox entities of older versions: the tree is hit by its model now
        for (Entity e : lv.getAllEntities()) if (e.getTags().contains(TAG_PART)) e.discard();
    }

    private static final Set<UUID> silentHandled = new HashSet<>();
    private static float raidRespawnHp = -1f;

    /**
     * A boss whose health hit 0 with no death event (an ability that sets health, such as the Death pathway's withering wind) would otherwise
     * be removed by the game and later respawned at full health. Near the end that is a kill; earlier it comes back with the health it had.
     */
    private static void silentDeath(ServerLevel lv, Mob m) {
        MinecraftServer server = lv.getServer();
        if (server == null || !silentHandled.add(m.getUUID())) return;
        AstralData d = AstralData.get(server);
        boolean raid = m.getTags().contains(TAG_RAID);
        String planet = null;
        for (String t : m.getTags()) if (t.startsWith(TAG_PLANET)) planet = t.substring(TAG_PLANET.length());
        if (raid ? d.bossesDead.contains("raid") : (planet == null || d.bossesDead.contains(planet))) return;   // a normal death already took care of it
        Float l = lastHp.get(m.getUUID());
        boolean finish = l != null && l <= m.getMaxHealth() * 0.30f;
        if (raid && (rootStage < THRESHOLDS.length || rootsActive)) finish = false;   // it cannot skip its phases
        LOGGER.info("[astral] '{}' lost all its health without a death event (last {}): {}", m.getName().getString(), l, finish ? "counted as a kill" : "it comes back with that health");
        if (finish) {
            if (raid) { d.bossesDead.add("raid"); server.execute(() -> raidWon(server, d)); }
            else bossDied(server, d, planet);
        } else if (raid && l != null) raidRespawnHp = Math.max(1f, l);
    }

    private static void guardLevel(ServerLevel lv) {
        if (lv == null) return;
        sweepParts(lv);
        for (Entity e : lv.getAllEntities()) {
            if (e instanceof Mob fp && fp.getPersistentData().getBoolean("isFallenPersona")) { fp.discard(); continue; }   // a regress would spawn a corrupted rampager at the players
            if (e instanceof Mob dm && dm.getTags().contains(TAG_BOSS) && !dm.isAlive() && dm.getHealth() <= 0f) silentDeath(lv, dm);
            if (!(e instanceof Mob m) || !m.getTags().contains(TAG_BOSS) || !m.isAlive()) continue;
            stripDeath(m);
            freeFromOwner(m);
            sizeHitbox(m);
            if (m.getTags().contains(TAG_RAID)) {   // the tree never moves: hits and the hitbox changing size must not push it up or down
                var pd = m.getPersistentData();
                if (!pd.contains("astralHomeY")) { pd.putDouble("astralHomeY", m.getY()); pd.putDouble("astralHomeX", m.getX()); pd.putDouble("astralHomeZ", m.getZ()); }
                double hx = pd.getDouble("astralHomeX"), hy = pd.getDouble("astralHomeY"), hz = pd.getDouble("astralHomeZ");
                m.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                if (Math.abs(m.getX() - hx) > 0.01 || Math.abs(m.getY() - hy) > 0.01 || Math.abs(m.getZ() - hz) > 0.01) m.setPos(hx, hy, hz);
            }
            float max = m.getMaxHealth(), hp = m.getHealth();
            if (m.getTags().contains(TAG_RAID) && rootStage < THRESHOLDS.length && hp < THRESHOLDS[rootStage] * RAID_HP + 3f && !rootsActive) { // however it got hurt (an ability that sets health), it cannot pass the next phase; within 3 HP of the line counts as on it
                hp = THRESHOLDS[rootStage] * RAID_HP;   // lands exactly on the line, which starts the phase
                m.setHealth(hp);
            }
            Float last = lastHp.get(m.getUUID());
            if (m.getTags().contains(TAG_RAID) && last != null && hp > last + 0.5f && gt > healOkUntil) { hp = last; m.setHealth(hp); }   // no natural regeneration, no healing from anything else: she only heals when the fight says so
            if (last != null && hp < last - max * MAX_DROP) { hp = Math.max(1f, last - max * MAX_HIT); m.setHealth(hp); }
            lastHp.put(m.getUUID(), hp);
        }
    }

    /** The Mercury sovereign (a Door Seq 0) must not cast Gravity Manipulation: it is bugged. Clearing its flags on everyone there stops the effect. */
    private static void stripGravityManipulation(MinecraftServer server) {
        ServerLevel lv = dim(server, "mercury");
        if (lv == null) return;
        for (Entity e : lv.getAllEntities()) {
            if (!(e instanceof net.minecraft.world.entity.LivingEntity)) continue;
            var tag = e.getPersistentData();
            if (tag.contains("affectedByGravityManipulation") && tag.getInt("affectedByGravityManipulation") != 0) tag.putInt("affectedByGravityManipulation", 0);
            if (tag.contains("keyOfStarsGravityManipulation") && tag.getInt("keyOfStarsGravityManipulation") != 0) tag.putInt("keyOfStarsGravityManipulation", 0);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        flushTele();
        AstralData d = AstralData.get(server);
        gt++;
        closeTick(server);
        if (d.endPhase.isEmpty() || d.endPhase.equals("classic")) return;
        if (d.endPhase.equals("call")) callTick(server, d);
        if (d.endPhase.equals("raid")) raidTick(server, d);
        processTimed();
        if (d.endPhase.equals("bosses") || d.endPhase.equals("raid")) protectBosses(server);
        if (d.endPhase.equals("bosses")) bossMoveTick(server, d);
        if (d.endPhase.equals("bosses") && server.getTickCount() % 2 == 0) stripGravityManipulation(server);
        if (server.getTickCount() % 20 != 0) return;
        switch (d.endPhase) {
            case "evac" -> secondEvac(server, d);
            case "bosses" -> { ensureAllies(server, d); holdSequences(server); secondBosses(server, d); }
            case "raid" -> { ensureAllies(server, d); holdSequences(server); secondRaid(server, d); }
            case "recover" -> secondRecover(server, d);
            default -> { }
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        Entity e = event.getEntity();
        if (e.level().isClientSide) return;
        if (e instanceof Mob sm && sm.getTags().contains(TAG_BOSS) && (event.getSource().getEntity() == e || event.getSource().getDirectEntity() == e)) { // it did this to itself
            event.setCanceled(true);
            Float l = lastHp.get(sm.getUUID());
            sm.setHealth(Math.max(1f, l != null ? l : sm.getMaxHealth()));
            stripDeath(sm);
            return;
        }
        if (e instanceof Mob nm && nm.getTags().contains(TAG_BOSS)) { // a boss only dies to players: nothing else, however it happened, may finish it
            Long hit = lastPlayerHit.get(nm.getUUID());
            Float lastForDeath = lastHp.get(nm.getUUID());
            if ((hit == null || gt - hit > 1200) && (lastForDeath == null || lastForDeath > nm.getMaxHealth() * 0.30f)) {   // a Death-pathway kill on a boss already near the end counts, though no hit was logged
                LOGGER.info("[astral] '{}' died with no player hit in the last minute ({}); undone", nm.getName().getString(), event.getSource().type().msgId());
                event.setCanceled(true);
                Float l = lastHp.get(nm.getUUID());
                nm.setHealth(Math.max(1f, l != null ? l : nm.getMaxHealth()));
                stripDeath(nm);
                return;
            }
        }
        if (e instanceof Mob bm && bm.getTags().contains(TAG_BOSS)) { // a boss at good health cannot die: that is an execution, not a fight
            Float last = lastHp.get(bm.getUUID());
            if (last != null && last > bm.getMaxHealth() * MAX_DROP) {
                event.setCanceled(true);
                stripDeath(bm);
                bm.setHealth(Math.max(1f, last - bm.getMaxHealth() * MAX_HIT));
                return;
            }
        }
        MinecraftServer server = e.getServer();
        if (server == null) return;
        AstralData d = AstralData.get(server);
        if (e instanceof ServerPlayer pl) { if (d.endPhase.equals("bosses")) stat(d, pl).deaths++; return; }
        if (!e.getTags().contains(TAG_BOSS)) return;
        LOGGER.info("[astral] boss '{}' died in {} at {} {} {}: {} (attacker {})", e.getName().getString(), e.level().dimension().location(),
                (int) e.getX(), (int) e.getY(), (int) e.getZ(), event.getSource().type().msgId(), event.getSource().getEntity());
        if (e.getTags().contains(TAG_RAID)) {
            if (d.endPhase.equals("raid")) { d.bossesDead.add("raid"); server.execute(() -> raidWon(server, d)); }
            return;
        }
        for (String t : e.getTags()) if (t.startsWith(TAG_PLANET)) bossDied(server, d, t.substring(TAG_PLANET.length()));
    }

    /** The bosses have 1000 real HP; shrinking every hit makes that 10,000 (planet) or 1,000,000 (raid). In the arena nobody is hurt, hits cost lives instead. */
    private static boolean environmental(net.minecraft.world.damagesource.DamageSource src) {
        return src.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) || src.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)
                || src.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING) || src.is(net.minecraft.tags.DamageTypeTags.IS_FALL)
                || src.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || src.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING)
                || src.is(net.minecraft.world.damagesource.DamageTypes.STARVE) || src.is(net.minecraft.world.damagesource.DamageTypes.CACTUS)
                || src.is(net.minecraft.world.damagesource.DamageTypes.DRY_OUT) || src.is(net.minecraft.world.damagesource.DamageTypes.HOT_FLOOR);
    }

    private static final Map<UUID, Long> dropLogged = new HashMap<>();

    /** Why a hit on a boss did nothing: logged (at most once a second per boss) so a report of "X does no damage" can be traced. */
    private static void logDrop(Entity e, LivingIncomingDamageEvent event, String why) {
        Long last = dropLogged.get(e.getUUID());
        if (last != null && gt - last < 20) return;
        dropLogged.put(e.getUUID(), gt);
        LOGGER.info("[astral] hit on '{}' did nothing ({}): type {}, amount {}, attacker {}, direct {}", e.getName().getString(), why, event.getSource().type().msgId(),
                event.getAmount(), event.getSource().getEntity(), event.getSource().getDirectEntity());
    }

    /** Testing: the creative-only Tree Smiter. Hitting the tree (or one of her hitboxes) with it takes 10% of her maximum health, whatever the damage rules say; her phase lines still hold. */
    @SubscribeEvent
    public static void onSmite(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.isCreative() || p.getMainHandItem().getItem() != AstralFloor.SMITER.get()) return;
        Entity t = event.getTarget();
        Mob tree = null;
        if (t.getTags().contains(TAG_RAID) && t instanceof Mob m) tree = m;
        else if (t.getTags().contains(TAG_PART) && t.getPersistentData().hasUUID("partOf") && t.level() instanceof ServerLevel sl && sl.getEntity(t.getPersistentData().getUUID("partOf")) instanceof Mob m) tree = m;
        if (tree == null) return;
        event.setCanceled(true);
        if (p.getCooldowns().isOnCooldown(AstralFloor.SMITER.get())) return;
        p.getCooldowns().addCooldown(AstralFloor.SMITER.get(), 4);
        if (rootsActive || rt < ultImmuneUntil || kingdomEnd != 0 || rt < graceUntil) {
            p.displayClientMessage(Component.literal("She cannot be hurt right now.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        float hp = tree.getHealth(), bound = rootStage < THRESHOLDS.length ? THRESHOLDS[rootStage] * RAID_HP : 0f;
        float next = Math.max(bound, hp - RAID_HP * 0.10f);
        tree.setHealth(next);
        p.displayClientMessage(Component.literal("Smite: " + String.format("%.1f", next / RAID_HP * 100) + "% left").withStyle(ChatFormatting.GOLD), true);
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        Entity e = event.getEntity();
        if (e.getTags().contains(TAG_PART)) {   // a hit on one of her extra hitboxes is a hit on her
            event.setCanceled(true);
            if (!environmental(event.getSource()) && e.getPersistentData().hasUUID("partOf") && e.level() instanceof ServerLevel pl) {
                Entity tree = pl.getEntity(e.getPersistentData().getUUID("partOf"));
                Entity by = event.getSource().getEntity();
                if (tree instanceof Mob tm && tm.isAlive() && by != tm && !(by instanceof Mob bm && (bm.getTags().contains(TAG_PART) || bm.getTags().contains(TAG_BOSS)))) tm.hurt(event.getSource(), event.getAmount());
            }
            return;
        }
        if (e instanceof ServerPlayer hgp && HungerGames.fighting(hgp)) return;   // the hunger games use the same dimension: players hurt each other normally there
        if (e instanceof ServerPlayer && god.contains(e.getUUID())) { event.setCanceled(true); return; }   // /astralgod
        if (e instanceof ServerPlayer && !aggro && rt < INTRO_TICKS && raidPhase(e)) { event.setCanceled(true); return; }   // nobody is hurt while the camera shows the tree
        if (e.getTags().contains(TAG_RAID) && rt < graceUntil && raidPhase(e)) {   // the tree sleeps: nothing can hurt it
            event.setCanceled(true);
            if (event.getSource().getEntity() instanceof ServerPlayer atk) atk.displayClientMessage(Component.literal("She sleeps. She cannot be hurt for " + Math.max(1, (graceUntil - rt + 19) / 20) + " more seconds.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        if (e instanceof ServerPlayer && rt < ultCutsceneUntil) { event.setCanceled(true); return; }   // nobody is hurt while watching the meteor start to fall
        if (e instanceof ServerPlayer && e.getServer() != null && "call".equals(AstralData.get(e.getServer()).endPhase)) { event.setCanceled(true); return; }   // nobody can be hurt while the Fool is called
        if (traitor != null && (e.getTags().contains(TAG_BOSS) || e.getTags().contains(TAG_MINION)) && event.getSource().getEntity() instanceof ServerPlayer ta && ta.getUUID().equals(traitor)) { event.setCanceled(true); return; }   // the ally cannot hurt the tree or her thralls
        if (e.getTags().contains(TAG_RAID) && rt < ultImmuneUntil) { logDrop(e, event, "the meteor is falling"); event.setCanceled(true); return; }
        if (rootsActive && e.getTags().contains(TAG_RAID)) { logDrop(e, event, "phase active, untouchable"); event.setCanceled(true); return; } // untouchable while its roots stand
        if (e.getTags().contains(TAG_BOSS) && event.getSource().getEntity() instanceof ServerPlayer atk && e.getServer() != null) {
            AstralData d = AstralData.get(e.getServer());
            lastPlayerHit.put(e.getUUID(), gt);
            if (tracking(d)) stat(d, atk).damage += event.getAmount(); // counted before shrinking: this is the 10,000 / 1,000,000 scale
        }
        if (e.getTags().contains(TAG_BOSS) && !environmental(event.getSource())) for (String t : e.getTags()) if (t.startsWith(TAG_PLANET) && PASSIVE.contains(t.substring(TAG_PLANET.length()))) woken.add(t.substring(TAG_PLANET.length()));
        if (e.getTags().contains(TAG_BOSS)) { // bosses are hurt by players (and their allies' abilities), never by themselves or by each other
            Entity by = event.getSource().getEntity(), direct = event.getSource().getDirectEntity();
            if (by == e || direct == e || (by instanceof Mob bm && bm.getTags().contains(TAG_BOSS))) { logDrop(e, event, "from the boss itself"); event.setCanceled(true); return; }
        }
        if (e.getTags().contains(TAG_BOSS) && environmental(event.getSource())) { logDrop(e, event, "environmental"); event.setCanceled(true); return; } // the world cannot kill a sovereign
        if (e.getTags().contains(TAG_RAID)) {
            ServerPlayer owner = ownerOf(event.getSource());
            boolean sailor = false;
            if (owner != null && e.getServer() != null) {
                AstralData od = AstralData.get(e.getServer());
                if (owner.isSpectator() || !owner.isAlive() || od.raidLives.getOrDefault(owner.getUUID(), 1) <= 0) {   // a fallen fighter's storms, bolts and summons no longer count
                    logDrop(e, event, "the one who cast it has fallen"); event.setCanceled(true); return;
                }
                sailor = Lotm.pathwayId(owner).equals("sailor");
            }
            if (e.level() instanceof ServerLevel wl) wake(wl, "it was struck");
            var le = (net.minecraft.world.entity.LivingEntity) e;
            if (kingdomEnd != 0) { logDrop(e, event, "the divine kingdom shields her"); event.setCanceled(true); return; }   // she cannot be hurt inside her divine kingdom
            boolean zap = lightningy(event.getSource());
            if (zap && starBolt(event.getSource().getDirectEntity())) { logDrop(e, event, "star of lightning: she shrugs it off"); event.setCanceled(true); return; }   // Star of Lightning cannot hurt her at all
            float res = (zap ? 0.4f : 1f) * (sailor ? 0.6f : 1f);   // she resists lightning: 60% less; a Tyrant's storms and calamities (and all else a Tyrant does) do 40% less on top
            float raw = Math.max(1f, event.getAmount());
            float comp = (float) (100.0 * Math.pow(raw, 0.45));   // weak hits now count (a 5 does 0.02%) while huge hits are squeezed (a 100,000 does 1.8%), and a bigger hit is always worth more
            float a = rateLimit(le, Math.min(comp * RAID_DAMAGE * res * (rt < dazedUntil ? 1.25f : 1f), le.getMaxHealth() * RAID_HIT * res), RAID_RATE);
            if (zap) a = rateLimit(le, new UUID(le.getUUID().getMostSignificantBits() ^ 0x5A5AL, le.getUUID().getLeastSignificantBits()), a, 0.0008f);   // Ocean of Lightning only drains her: 0.08% of her health a second at most   // dazed: 25% more, still under her caps
            if (rootStage < THRESHOLDS.length) {
                float line = THRESHOLDS[rootStage] * RAID_HP, room = Math.max(0f, le.getHealth() - line);
                a = Math.min(a, room); // the hit that crosses a phase line is cut down to land exactly on it (and starts the phase), never skipped
                if (a > 0f && room - a < 3f) a = room;   // never stop a hair above the line: a hit that would leave her within 3 HP of it takes her all the way to it
            }
            if (a > 0f) { lastHp.put(le.getUUID(), Math.max(0f, le.getHealth() - a)); }   // what her health must be after this hit: the regeneration guard compares against this, so it can never undo the hit
            if (a <= 0f) { logDrop(e, event, "capped to zero"); event.setCanceled(true); } else event.setAmount(a);
            return;
        }
        if (e.getTags().contains(TAG_BOSS)) {
            var le = (net.minecraft.world.entity.LivingEntity) e;
            float a = rateLimit(le, Math.min(event.getAmount() * BOSS_DAMAGE, le.getMaxHealth() * MAX_HIT), BOSS_RATE);
            if (a <= 0f) { logDrop(e, event, "capped to zero"); event.setCanceled(true); } else event.setAmount(a);
            return;
        }
        if (e instanceof ServerPlayer p && p.level().dimension().equals(Cosmos.COSMOS)) {
            event.setCanceled(true);
            Entity by = event.getSource().getEntity();
            if (by instanceof ServerPlayer a && a != p && traitor != null && (a.getUUID().equals(traitor) || p.getUUID().equals(traitor))) { pvp(a, p, event.getAmount()); return; }   // only the ally and the others can hurt each other
            if (by instanceof Mob tm && tm.getTags().contains(TAG_MINION)) { if (!p.getUUID().equals(traitor)) thrallHit(p, event.getAmount()); return; }
            // LOTM kills a player at 0 sanity with damage it deals to the player itself: the arena blocks all damage, so that death costs a life instead
            if (by == p || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) sanityBreak(p);
        }
    }

    /** A mind that gives way in the raid: costs a life, never a sequence. Sanity is reset so LOTM's own collapse (which regresses and kills) never fires. */
    private static void sanityBreak(ServerPlayer p) {
        if (cutscene || p.isSpectator() || !p.isAlive() || p.getServer() == null || !AstralData.get(p.getServer()).endPhase.equals("raid")) return;
        Long last = sanityDeathAt.get(p.getUUID());
        if (last != null && gt - last <= 100) return;
        sanityDeathAt.put(p.getUUID(), gt);
        LOGGER.info("[astral] {} lost their mind in the raid: a life is taken", p.getGameProfile().getName());
        ServerLevel plv = (ServerLevel) p.level();
        Lotm.setSanityPercent(p, 50);
        p.sendSystemMessage(Component.literal("Your mind gives way.").withStyle(ChatFormatting.DARK_PURPLE));
        p.setHealth(Math.min(p.getHealth(), 1f));
        killLife(plv, p);
    }

    private static final Map<UUID, Long> sanityDeathAt = new HashMap<>();
    private static final Map<UUID, Integer> raidSeq = new HashMap<>();   // the sequence each fighter carried in, and the best they reach

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        if (bloomNoHeal.getOrDefault(p.getUUID(), 0L) > gt) { event.setCanceled(true); return; }
        if (!treeHealing && noHeal.getOrDefault(p.getUUID(), 0L) > gt) { event.setCanceled(true); return; }
        AstralData d = AstralData.get(p.getServer());
        if (tracking(d)) stat(d, p).heal += Math.min(event.getAmount(), p.getMaxHealth() - p.getHealth());
    }

    /** A second copy of a boss (summoned back out of a Death-pathway spirit store, say) never enters the world while the real one lives. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob m) || !m.getTags().contains(TAG_BOSS)) return;
        var server = event.getLevel().getServer();
        if (server == null) return;
        for (String tag : m.getTags()) {
            if (!tag.equals(TAG_RAID) && !tag.startsWith(TAG_PLANET)) continue;
            for (ServerLevel lv : server.getAllLevels())
                for (Entity e : lv.getAllEntities())
                    if (e != m && e instanceof Mob o && o.isAlive() && !o.getUUID().equals(m.getUUID()) && o.getTags().contains(tag)) { event.setCanceled(true); return; }
        }
    }

    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        if (event.getDimension().equals(Level.OVERWORLD) && AstralData.get(p.getServer()).earthGone) {
            event.setCanceled(true);
            p.displayClientMessage(Component.literal("Earth is gone.").withStyle(ChatFormatting.RED), true);
        }
    }

    private static void relocate(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        AstralData d = AstralData.get(server);
        if (d.earthGone && p.level().dimension().equals(Level.OVERWORLD)) {
            ServerLevel m = dim(server, "mercury");
            if (m != null) { m.getChunk(0, 0); p.teleportTo(m, 0.5, PlanetGenerator.spawnY(), 0.5, p.getYRot(), 0f); }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) p.getServer().execute(() -> relocate(p));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.getServer() != null) p.getServer().execute(() -> relocate(p));
    }

    // ------------------------------------------------------------------ for testing

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("astralend")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> {
                    AstralData d = AstralData.get(ctx.getSource().getServer());
                    long left = Math.max(0, d.endUntil - System.currentTimeMillis());
                    String m = "phase=" + (d.endPhase.isEmpty() ? "none" : d.endPhase) + " earthGone=" + d.earthGone + " bossesDead=" + d.bossesDead
                            + " timeLeft=" + String.format("%.2f", left / 86_400_000.0) + "d resumeStage=" + d.resumeStage;
                    ctx.getSource().sendSuccess(() -> Component.literal(m), false);
                    return 1;
                }))
                .then(Commands.literal("kill").then(Commands.argument("planet", StringArgumentType.word()).executes(ctx -> {
                    MinecraftServer s = ctx.getSource().getServer();
                    AstralData d = AstralData.get(s);
                    if (!d.endPhase.equals("bosses")) return 0;
                    bossDied(s, d, StringArgumentType.getString(ctx, "planet"));
                    return 1;
                })))
                .then(Commands.literal("bosses").executes(ctx -> { // skip straight to the planet bosses
                    MinecraftServer s = ctx.getSource().getServer();
                    destroyEarth(s, AstralData.get(s));
                    return 1;
                }))
                .then(Commands.literal("spawn").then(Commands.argument("planet", StringArgumentType.word()).executes(ctx -> { // a look-only copy in front of you
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    String id = StringArgumentType.getString(ctx, "planet");
                    for (BossDef b : BOSSES) if (b.planet().equals(id)) {
                        Vec3 f = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(10));
                        Mob m = makeMob(p.serverLevel(), b.pathway(), b.title(), f.x, p.getY(), f.z, BOSS_SEQ.getOrDefault(b.planet(), 0));
                        if (m == null) return 0;
                        m.setNoAi(true);
                        p.serverLevel().addFreshEntity(m);
                        return 1;
                    }
                    if (id.equals("tree")) {
                        Vec3 f = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(60));
                        Mob m = makeMob(p.serverLevel(), RAID_PATHWAY, RAID_NAME, f.x, p.getY(), f.z);
                        if (m == null) return 0;
                        m.setNoAi(true);
                        p.serverLevel().addFreshEntity(m);
                        return 1;
                    }
                    return 0;
                })))
                .then(Commands.literal("raid").executes(ctx -> {
                    MinecraftServer s = ctx.getSource().getServer();
                    startRaid(s, AstralData.get(s));
                    return 1;
                }))
                .then(Commands.literal("revive").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.players()).executes(ctx -> {   // lets a fallen or kicked fighter back into the raid
                    MinecraftServer s = ctx.getSource().getServer();
                    AstralData d = AstralData.get(s);
                    ServerLevel lv = dim(s, "raid");
                    if (!d.endPhase.equals("raid") || lv == null) { ctx.getSource().sendFailure(Component.literal("The raid is not running.")); return 0; }
                    int n = 0;
                    for (ServerPlayer p : net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "player")) {
                        d.raidLives.put(p.getUUID(), LIVES);
                        setGameMode(p, GameType.SURVIVAL);
                        double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
                        p.teleportTo(lv, 0.5 + Math.cos(a) * SPAWN_R, groundY, 0.5 + Math.sin(a) * SPAWN_R, p.getYRot(), 0f);
                        p.sendSystemMessage(Component.literal("You are back in the fight, with " + LIVES + " lives.").withStyle(ChatFormatting.GOLD));
                        n++;
                    }
                    d.setDirty();
                    final int count = n;
                    ctx.getSource().sendSuccess(() -> Component.literal("Revived " + count + " player(s)."), true);
                    return n;
                })))
                .then(Commands.literal("reset").executes(ctx -> {
                    MinecraftServer s = ctx.getSource().getServer();
                    resetAll(s);
                    Stage.set(s, 0);
                    return 1;
                })));
    }
}
