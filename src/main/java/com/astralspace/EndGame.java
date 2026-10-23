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
    private static final int LIVES = 5;
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

    private static void setGameMode(ServerPlayer p, GameType t) { if (p.gameMode.getGameModeForPlayer() != t) p.setGameMode(t); }

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
            "The Fool that doesn't belong to this era.",
            "The Mysterious Ruler above the Gray Fog.",
            "The King of Yellow and Black who wields good luck.",
            "I pray for your blessing.",
            "I pray for salvation.",
            "I pray for you to save us all."};
    private static final int[] PRAYER_AT = {40, 140, 250, 360, 440, 520};   // ticks after the fog begins (the client draws the same lines)
    private static final int CALL_TICKS = 640;

    /** The sky turns to grey fog, a voice prays, and then everyone is taken to the arena. */
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
            Component line = Component.literal(PRAYER[spoken]).withStyle(ChatFormatting.ITALIC, ChatFormatting.GOLD);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(line);
                p.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.MASTER, 1.5f, 0.5f);
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
            p.sendSystemMessage(Component.literal("The Fool lifts you to Sequence 4.").withStyle(ChatFormatting.GOLD));
        }
        PacketDistributor.sendToPlayer(p, new Net.StagePayload("The Fool's Blessing", 0x3A2A00, 0xFFD870, "You are not alone"));
        p.sendSystemMessage(Component.literal("You have received the Fool's blessing.").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        offerBlessing(p);
    }

    private static void offerBlessing(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("Choose one gift (click):").withStyle(ChatFormatting.YELLOW));
        p.sendSystemMessage(blessingButton("[+50 max HP]", "hp").append(Component.literal("  ")).append(blessingButton("[+5 attack damage]", "attack"))
                .append(Component.literal("  ")).append(blessingButton("[+8 armor, +6 armor toughness]", "armor")));
    }

    private static net.minecraft.network.chat.MutableComponent blessingButton(String label, String id) {
        return Component.literal(label).withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/astralblessing " + id)));
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
        m.setCustomName(Component.literal(name).withStyle(ChatFormatting.DARK_RED));
        m.setCustomNameVisible(true);
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
    private static double groundY = 4;
    private static final Map<UUID, Long> lastHit = new HashMap<>();
    /** After each attack only some others may follow (meteors only after a slam or rings), and which one is chosen is random. 0 meteors, 1 rings, 2 beam, 3 slam, 4 safe circles, 5 temptation. */
    private static final int[][] NEXT = {{2, 4, 3, 7, 10}, {0, 3, 4, 5, 8}, {3, 1, 4, 5, 7, 9}, {0, 1, 2, 7, 9}, {2, 3, 1, 5, 10}, {3, 1, 2, 7, 8}, {0, 1, 2, 3, 9}, {3, 4, 0, 2, 8}, {3, 4, 1, 9}, {2, 3, 10, 0}, {0, 1, 3, 8}};
    private static final int[] DURATION = {140, 220, 110, 70, 80, 200, 90, 130, 200, 230, 210};
    private static int lastPattern = 3;

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
        timed.clear();
        raidSeq.clear();
        rt = 0; cutscene = false; nextTreeCast = 0; nextPattern = 100; lastPattern = 3; lastHit.clear(); rootsActive = false; rootStage = 0; roots.clear(); seeds.clear(); activeKind = 0; ultimateDone = false; treeFleshy = false; lineBucket = 20;
        traitor = null; offered = null; offerDone = false; noHeal.clear(); desire.clear(); thrallHitAt.clear();
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
            double a = Math.PI * 2 * i / Math.max(1, all.size());
            double sr = SPAWN_R - 15 + (i++ % 4) * 10;   // four rings, so a crowd is not stacked on one line
            p.teleportTo(lv, 0.5 + Math.cos(a) * sr, groundY, 0.5 + Math.sin(a) * sr, p.getYRot(), 0f);
            removeBlessing(p);
            blessPlayer(p);
        }
        tell(server, RAID_NAME + " has come. You have " + LIVES + " lives. Do not be where it strikes.", ChatFormatting.DARK_PURPLE);
    }

    private static List<ServerPlayer> fighters(ServerLevel lv) {
        List<ServerPlayer> l = new ArrayList<>();
        AstralData d = AstralData.get(lv.getServer());
        if (cur != null && !cur.raid()) { for (ServerPlayer p : lv.players()) if (!p.isSpectator() && p.isAlive()) l.add(p); return l; }
        for (ServerPlayer p : lv.players()) if (!p.isSpectator() && p.isAlive() && d.raidLives.getOrDefault(p.getUUID(), 0) > 0 && !p.getUUID().equals(traitor)) l.add(p);   // the tree's new ally is never a target
        return l;
    }

    private static void loseLife(ServerLevel lv, ServerPlayer p) {
        if (cutscene) return;
        AstralData d = AstralData.get(lv.getServer());
        long now = lv.getServer().getTickCount();
        Long last = lastHit.get(p.getUUID());
        if (last != null && now - last < 40) return; // just hit: a moment of grace
        lastHit.put(p.getUUID(), now);
        Lotm.addCorruptionPercent(p, Lotm.randomPathway(), 80); // every hit corrupts heavily and shakes the mind
        float dmg = p.getMaxHealth() * 0.8f;
        if (p.getHealth() > dmg) { // it takes 80% of their health: a second hit is certain death
            p.setHealth(p.getHealth() - dmg);
            lv.broadcastEntityEvent(p, (byte) 2);
            p.sendSystemMessage(Component.literal("It struck you down to a sliver. Another hit and you fall.").withStyle(ChatFormatting.DARK_RED));
            return;
        }
        // a killing blow: the life is taken here, so totems and revival abilities never get the chance to answer it
        stat(d, p).deaths++;
        int lives = d.raidLives.merge(p.getUUID(), -1, Integer::sum);
        d.setDirty();
        lv.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1f, 1.2f);
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

    private static void ringOnly(ServerLevel lv, double x, double z, double r, float cr, float cg, float cb) {
        tele(lv, 1, x, cur.gy(), z, (float) r, 0f, cr, cg, cb, 4);
    }

    private static void circle(ServerLevel lv, double x, double z, double r, float cr, float cg, float cb) {
        tele(lv, 0, x, cur.gy(), z, (float) r, 0f, cr, cg, cb, 8);
    }

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
    static void rootHit(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel lv) || !lv.dimension().equals(Cosmos.COSMOS) || p.isSpectator() || !p.isAlive()) return;
        AstralData d = AstralData.get(lv.getServer());
        if (!d.endPhase.equals("raid") || p.getUUID().equals(traitor)) return;
        Mob boss = findBoss(lv, TAG_RAID);
        if (boss == null) return;
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        TreeModel m = TreeModel.build(treeFleshy);
        double t = m.rayHitRoots(eye.x - boss.getX(), eye.y - boss.getY(), eye.z - boss.getZ(), look.x, look.y, look.z, p.entityInteractionRange() + 2.5);
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
    private static void spawnThrall(ServerLevel l, double x, double z, String kind) {
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
        if (m == null) return;
        m.setPos(x, cur.gy() + 1, z);
        m.setPersistenceRequired();
        m.addTag(TAG_MINION);
        var hp = m.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(300);
        m.setHealth(300);
        l.addFreshEntity(m);
        var near = fighters(l);
        if (!near.isEmpty()) m.setTarget(near.get(ThreadLocalRandom.current().nextInt(near.size())));
    }

    /** A wound that does not kill: a share of maximum health (never below half a heart), and nothing heals for ten seconds. */
    private static void soften(ServerLevel l, ServerPlayer p, float frac) {
        p.setHealth(Math.max(1f, p.getHealth() - p.getMaxHealth() * frac));
        l.broadcastEntityEvent(p, (byte) 2);
        noHeal.put(p.getUUID(), gt + 200);
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
            if (s >= 3) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, s / 3 - 1, false, false));
            if (s >= 6) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
            Lotm.loseSanityPercent(p, 0.4 * s);
            p.displayClientMessage(Component.literal("Desire x" + s).withStyle(ChatFormatting.LIGHT_PURPLE), true);
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
        if (h > 0) { p.setHealth(h); lv.broadcastEntityEvent(p, (byte) 2); return; }
        p.setHealth(1f);
        lastHit.remove(p.getUUID());
        loseLife(lv, p);
    }

    private static void thrallHit(ServerPlayer p, float amount) {
        Long last = thrallHitAt.get(p.getUUID());
        if (last != null && gt - last < 10) return;
        thrallHitAt.put(p.getUUID(), gt);
        p.setHealth(Math.max(1f, p.getHealth() - Math.min(amount, p.getMaxHealth() * 0.15f)));
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
            lastHit.remove(p.getUUID());
            loseLife(lv, p);   // refusing costs a life: the tree strikes
            return;
        }
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

    /** Every second: the ally is strong, the thralls hunt anyone but the ally, and Desire works on the rest. */
    private static void raidAlliesSecond(ServerLevel lv) {
        if (traitor != null) {
            ServerPlayer t = lv.getServer().getPlayerList().getPlayer(traitor);
            if (t != null && !t.isSpectator() && t.isAlive()) {
                t.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 3, false, false));        // Strength IV
                t.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, false, false));   // Resistance I
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, false, false));      // Speed I
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
            "CURSE: purple smoke gathers on every one of you. In a few seconds the whole battlefield is cursed: slowed, weakened, unlucky and corrupted. It cannot be dodged, so stay alive and stay together.",
            "POLLEN: purple clouds drift across the arena. Standing in one stacks Desire: it slows you, clouds your head and drains your mind. Stay out of them.",
            "MIRROR BLOOM: three flowers appear and only one is true. Touch a false one and it bursts for a quarter of your health and stops all healing. The true one cleanses everyone. Look closely at the sparks. If nobody finds it, they all wither.",
            "FRUIT: fruit falls across the arena. Take it before it ripens or it sprouts a thrall. Later, the fruit is rotten."};

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
    private static void temptation(ServerLevel lv, int ticks, Set<UUID> failed) {
        Map<UUID, Integer> meter = new HashMap<>();
        float[] pull = {0f, 0f};
        int[] changeAt = {60};
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (ServerPlayer p : fighters(lv)) p.sendSystemMessage(Component.literal("Come to me, child... (turn away and CROUCH)").withStyle(ChatFormatting.LIGHT_PURPLE));
        for (int t = 0; t < ticks; t++) {
            final int tt = t;
            at(t, l -> {
                Vec3 boss = new Vec3(cur.cx(), cur.gy() + 2, cur.cz());
                if (tt % 10 == 0) l.sendParticles(ParticleTypes.CHERRY_LEAVES, boss.x, cur.gy() + 3, boss.z, 20, 6, 3, 6, 0.02);
                if (tt >= changeAt[0]) {
                    double th = rnd.nextDouble() * Math.PI * 2;
                    pull[0] = (float) (Math.cos(th) * 1.1);
                    pull[1] = (float) (Math.sin(th) * 0.7);
                    changeAt[0] = tt + 15 + rnd.nextInt(15);
                }
                for (ServerPlayer p : fighters(l)) {
                    UUID id = p.getUUID();
                    if (failed.contains(id)) continue;
                    if (tt < 60) { if (tt % 10 == 0) p.displayClientMessage(Component.literal("Turn away and crouch!").withStyle(ChatFormatting.LIGHT_PURPLE), true); continue; }
                    double dx = p.getX() - boss.x, dz = p.getZ() - boss.z;
                    float away = (float) (net.minecraft.util.Mth.atan2(-dx, dz) * 57.29578);
                    double dev = Math.hypot(net.minecraft.util.Mth.wrapDegrees(p.getYRot() - away), p.getXRot());
                    boolean ok = p.isShiftKeyDown() && dev < 70;
                    int m = meter.getOrDefault(id, 0);
                    m = ok ? Math.max(0, m - 2) : m + 1;
                    meter.put(id, m);
                    if (tt % 2 == 0) // the drag: a relative turn of the camera
                        p.connection.teleport(p.getX(), p.getY(), p.getZ(), pull[0] * 2, pull[1] * 2,
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
                        if (!Lotm.addCorruptionPercent(p, Lotm.randomPathway(), 60)) hit(l, p);
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
        crowd = fighters(lv).size() > 12;
        final boolean big = cur.raid();
        if (big) sendAnim(lv, which, DURATION[which] + 40);
        final double reach = big ? ARENA_R : 42;
        final double mr = big && rootStage >= 2 ? 11 : 3.5, sl = big ? 16 : 5.5;   // meteor and slam sizes: in the raid they match the tree
        switch (which) {
            case 6 -> power(lv);
            case 7 -> { // a curse on everyone: cannot be dodged, but it does not cost a life
                for (int t = 0; t < 60; t += 6) at(t, l -> {
                    for (ServerPlayer p : fighters(l)) l.sendParticles(ParticleTypes.WITCH, p.getX(), p.getY() + 1, p.getZ(), 12, 0.6, 0.9, 0.6, 0.05);
                });
                at(60, l -> {
                    sfx(l, SoundEvents.WITHER_AMBIENT, 2f, 0.5f);
                    for (ServerPlayer p : fighters(l)) {
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 300, 1));
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 300, 1));
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, 300, 1));
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.UNLUCK, 300, 2));
                        if (!Lotm.addCorruptionPercent(p, Lotm.randomPathway(), 35)) LOGGER.warn("[astral] the curse could not add corruption to {}", p.getGameProfile().getName());
                        p.sendSystemMessage(Component.literal("You are cursed.").withStyle(ChatFormatting.DARK_PURPLE));
                    }
                });
            }
            case 0 -> { // meteors: red marks under every fighter, they land 1.5 s later
                int shots = 4 + rnd.nextInt(3);
                for (int k = 0; k < shots; k++) at(k * 20L, l -> {
                    for (ServerPlayer p : fighters(l)) {
                        double x = p.getX() + rnd.nextDouble(-3, 3), z = p.getZ() + rnd.nextDouble(-3, 3);
                        for (int t = 0; t < 30; t += 5) at(t, ll -> circle(ll, x, z, mr, 1f, 0.1f, 0.1f));
                        at(30, ll -> { boom(ll, x, z, mr); strike(ll, x, z, mr, 99); });
                    }
                });
            }
            case 1 -> { // two rings rolling out from the Outer God: jump them
                int rings = 2 + rnd.nextInt(2);
                for (int ring = 0; ring < rings; ring++) {
                    long start = 25 + ring * 30L;
                    for (int t = 0; t < (big ? 150 : 50); t++) {
                        final int tt = t;
                        at(start + t, l -> {
                            double r = 2 + tt * (big ? 1.3 : 0.6);
                            ringOnly(l, cur.cx(), cur.cz(), r, 0.7f, 0.2f, 1f);
                            for (ServerPlayer p : fighters(l)) { // only the ring itself, and only fighters standing on the ground
                                double dist = Math.hypot(p.getX() - cur.cx(), p.getZ() - cur.cz());
                                if (Math.abs(dist - r) < (big ? 3.0 : 1.0) && !(p.getY() >= cur.gy() + 0.9 && jumping(p))) hit(l, p);
                            }
                        });
                    }
                }
            }
            case 2 -> { // summon: zombies, werewolves and spirits step out of the ground
                for (int i = 0; i < 5; i++) at(10 + i * 8, l -> {
                    double a = rnd.nextDouble() * Math.PI * 2, d = 6 + rnd.nextDouble() * 8;
                    spawnThrall(l, bx + Math.cos(a) * d, bz + Math.sin(a) * d, null);
                });
            }
            case 8 -> { // pollen veil: clouds of Desire drift over the arena; standing in one stacks Desire
                final int n = big ? 3 : 2;
                final double cr = big ? 8 : 5;
                double[][] c = new double[n][4];
                for (double[] k : c) {
                    double a = rnd.nextDouble() * Math.PI * 2, d = 6 + rnd.nextDouble() * (big ? reach * 0.5 : 14), v = rnd.nextDouble() * Math.PI * 2;
                    k[0] = cx + Math.cos(a) * d; k[1] = cz + Math.sin(a) * d; k[2] = Math.cos(v) * 0.08; k[3] = Math.sin(v) * 0.08;
                }
                for (int t = 0; t < 180; t += 4) {
                    final int tt = t;
                    at(t, l -> {
                        for (double[] k : c) {
                            double x = k[0] + k[2] * tt, z = k[1] + k[3] * tt;
                            circle(l, x, z, cr, 0.7f, 0.2f, 0.9f);
                            l.sendParticles(ParticleTypes.WITCH, x, cur.gy() + 1, z, 14, cr / 2, 0.8, cr / 2, 0.02);
                            if (tt >= 20 && tt % 8 == 0) for (ServerPlayer p : fighters(l)) if (Math.hypot(p.getX() - x, p.getZ() - z) < cr && p.getY() < cur.gy() + 6) addDesire(p);
                        }
                    });
                }
            }
            case 9 -> { // mirror bloom: three flowers, one true. Touch a false one and it bursts
                final int real = rnd.nextInt(3);
                double[][] f = new double[3][2];
                for (double[] k : f) { double a = rnd.nextDouble() * Math.PI * 2, d = 8 + rnd.nextDouble() * (big ? reach * 0.5 : 16); k[0] = cx + Math.cos(a) * d; k[1] = cz + Math.sin(a) * d; }
                boolean[] gone = new boolean[3];
                boolean[] done = {false};
                for (int t = 0; t <= 200; t += 2) {
                    final int tt = t;
                    at(t, l -> {
                        if (done[0]) return;
                        for (int i = 0; i < 3 && !done[0]; i++) {
                            if (gone[i]) continue;
                            double x = f[i][0], z = f[i][1];
                            if (tt % 8 == 0) circle(l, x, z, 1.4, 1f, 0.5f, 0.9f);
                            l.sendParticles(ParticleTypes.ENCHANT, x, cur.gy() + 1.2, z, 6, 0.3, 0.6, 0.3, 0.1);
                            if (tt % 12 == 0) l.sendParticles(i == real ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.WITCH, x, cur.gy() + 1.2, z, 3, 0.25, 0.4, 0.25, 0);
                            for (ServerPlayer p : fighters(l)) {
                                if (Math.hypot(p.getX() - x, p.getZ() - z) > 1.8 || Math.abs(p.getY() - cur.gy()) > 4) continue;
                                if (i == real) {
                                    done[0] = true;
                                    l.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, cur.gy() + 1.2, z, 40, 1, 1, 1, 0.1);
                                    for (ServerPlayer q : fighters(l)) { desire.remove(q.getUUID()); q.heal(q.getMaxHealth() * 0.25f); q.displayClientMessage(Component.literal("The true bloom: you are cleansed.").withStyle(ChatFormatting.GREEN), true); }
                                } else {
                                    gone[i] = true;
                                    boom(l, x, z, 4);
                                    for (ServerPlayer q : fighters(l)) if (Math.hypot(q.getX() - x, q.getZ() - z) < 4.5) soften(l, q, 0.25f);
                                }
                                break;
                            }
                        }
                        if (tt == 200 && !done[0]) { // nobody found the true bloom: they all wither at once
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
                for (int t = 0; t <= 170; t += 2) {
                    final int tt = t;
                    at(t, l -> {
                        for (int i = 0; i < n; i++) {
                            if (taken[i]) continue;
                            double x = f[i][0], z = f[i][1];
                            if (tt % 8 == 0) circle(l, x, z, 1.5, sour ? 0.6f : 1f, sour ? 0.1f : 0.85f, sour ? 0.2f : 0.2f);
                            l.sendParticles(sour ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD, x, cur.gy() + 1.2, z, 3, 0.2, 0.5, 0.2, 0.01);
                            if (tt == 170) { taken[i] = true; spawnThrall(l, x, z, sour ? "werewolf" : null); continue; }
                            for (ServerPlayer p : fighters(l)) {
                                if (Math.hypot(p.getX() - x, p.getZ() - z) > 1.8 || Math.abs(p.getY() - cur.gy()) > 4) continue;
                                taken[i] = true;
                                if (sour) { soften(l, p, 0.25f); p.displayClientMessage(Component.literal("The fruit was rotten.").withStyle(ChatFormatting.DARK_RED), true); }
                                else { p.heal(p.getMaxHealth() * 0.3f); desire.remove(p.getUUID()); p.displayClientMessage(Component.literal("Sweet. You feel whole.").withStyle(ChatFormatting.GOLD), true); }
                                break;
                            }
                        }
                    });
                }
            }
            case 3 -> { // slam: a big red circle under every fighter, 2 s to leave it
                for (ServerPlayer p : fighters(lv)) {
                    double x = p.getX(), z = p.getZ();
                    for (int t = 0; t < 40; t += 4) at(t, l -> circle(l, x, z, sl, 1f, 0.3f, 0f));
                    at(40, l -> { boom(l, x, z, sl); strike(l, x, z, sl, 99); });
                }
            }
            case 5 -> { // temptation: look away from the tree and crouch, or be corrupted
                Set<UUID> failed = new HashSet<>();
                temptation(lv, 160, failed);
                at(165, l -> { for (ServerPlayer p : fighters(l)) if (!failed.contains(p.getUUID())) p.sendSystemMessage(Component.literal("You resisted.").withStyle(ChatFormatting.GREEN)); });
            }
            default -> { // the whole arena is struck except for three safe circles
                final double sr = big ? 7 : 3.2;
                double[][] safe = new double[big ? Math.max(3, (fighters(lv).size() + 4) / 5) : 3][2];   // a crowd needs room: one circle for every five fighters
                for (double[] s : safe) { double a = rnd.nextDouble() * Math.PI * 2, d = 4 + rnd.nextDouble() * (big ? reach * 0.8 : 14); s[0] = cx + Math.cos(a) * d; s[1] = cz + Math.sin(a) * d; }
                for (int t = 0; t < 70; t += 5) at(t, l -> { for (double[] s : safe) circle(l, s[0], s[1], sr, 0.2f, 1f, 0.3f); });
                at(70, l -> {
                    for (ServerPlayer p : fighters(l)) {
                        boolean ok = false;
                        for (double[] s : safe) { double dx = p.getX() - s[0], dz = p.getZ() - s[1]; if (dx * dx + dz * dz <= sr * sr) ok = true; }
                        if (!ok) hit(l, p);
                    }
                    boom(l, cx, cz, big ? 60 : 30);
                });
            }
        }
    }

    // ------------------------------------------------------------------ the roots (Mother Tree of Desire)

    private static boolean rootsActive = false;
    private static long rootsEnd = 0;
    private static int rootStage = 0;
    private static boolean ultimateDone = false;   // the 15% meteor is used once per fight
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
        tellLevel(lv, "The Mother Tree of Desire withers... and something worse wakes inside it.", ChatFormatting.DARK_RED);
        sfx(lv, SoundEvents.ENDER_DRAGON_GROWL, 2f, 0.5f);
        for (int t = 40; t < 140; t += 20 - (t - 40) / 12) { final int tt = t; at(t, l -> sfx(l, SoundEvents.WARDEN_HEARTBEAT, 3f, 0.6f + tt / 250f)); }   // a heart that beats faster
        at(100, l -> sfx(l, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.5f, 0.5f));
        at(138, l -> sfx(l, SoundEvents.WITHER_SPAWN, 2f, 0.45f));            // the swap: the flesh takes over
        at(150, l -> sfx(l, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 3f, 0.4f));
        at(185, l -> sfx(l, SoundEvents.WARDEN_EMERGE, 3f, 0.6f));
        at(235, l -> sfx(l, SoundEvents.WARDEN_ROAR, 3f, 0.6f));
        at(CUT_TICKS, l -> {
            Mob b = findBoss(l, TAG_RAID);
            if (b != null) b.setCustomName(Component.literal(RAID_NAME + "\u200B").withStyle(ChatFormatting.DARK_RED)); // the invisible mark tells clients to draw the fleshy form for good
            cutscene = false;
            rootsActive = false;
            treeFleshy = true;
            if (raidBar != null) raidBar.setName(barName("", ChatFormatting.DARK_PURPLE));
            tellLevel(l, "The Mother Tree of Desire curses you all. From now on you see her health as she wants you to see it.", ChatFormatting.DARK_RED);
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

    private static void healBoss(Mob boss, float fraction) { boss.setHealth(Math.min(RAID_HP, boss.getHealth() + RAID_HP * fraction)); }

    private static void removeSeeds(ServerLevel lv) {
        for (BlockPos b : seeds) if (lv.getBlockState(b).is(Blocks.SHROOMLIGHT)) lv.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
        seeds.clear();
    }

    /** 50%: the tree sings to everyone for 20 seconds. Each one who gives in heals it by 10%. */
    private static void startCall(ServerLevel lv) {
        rootsActive = true;
        activeKind = 1;
        phaseEnd = rt + 210;
        callFailed.clear();
        tellLevel(lv, "The Mother Tree sings. It cannot be hurt while you listen. Do NOT give in: every one who does heals it.", ChatFormatting.LIGHT_PURPLE);
        temptation(lv, 200, callFailed);
        at(205, l -> {
            Mob b = findBoss(l, TAG_RAID);
            if (b != null) healBoss(b, 0.10f * callFailed.size());
            endPhase(l, callFailed.isEmpty() ? "Nobody gave in. The tree is exposed!" : callFailed.size() + " gave in and fed it. The tree is exposed again.", ChatFormatting.GOLD);
        });
    }

    /** 25%: heart seeds grow; break them (any way) within 40 seconds or they hit everyone and feed the tree. */
    private static void startSeeds(ServerLevel lv) {
        rootsActive = true;
        activeKind = 2;
        phaseEnd = rt + 800;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int nSeeds = Math.min(24, Math.max(3, fighters(lv).size() / 6));
        double smax = Math.min(ARENA_R - 55, 40 + nSeeds * 8.0);
        for (int i = 0; i < nSeeds; i++) {
            double a = Math.PI * 2 * i / nSeeds + rnd.nextDouble() * 0.5, r = 25 + rnd.nextDouble() * Math.max(10, smax - 25);
            BlockPos b = BlockPos.containing(0.5 + Math.cos(a) * r, groundY, 0.5 + Math.sin(a) * r);
            if (!lv.getBlockState(b).isAir()) continue;
            lv.setBlock(b, Blocks.SHROOMLIGHT.defaultBlockState(), 3);
            seeds.add(b);
        }
        tellLevel(lv, "Heart seeds have grown! The tree cannot be hurt. Break them all within 40 seconds, or each one feeds it 5% and the rest strike you all.", ChatFormatting.RED);
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
            for (ServerPlayer p : fighters(lv)) loseLife(lv, p);
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

    private static void ultimate(ServerLevel lv) {
        sendAnim(lv, 20, 340);
        nextPattern = Math.max(nextPattern, rt + 420);   // no other attack while it falls
        final double cx = 0.5, cz = 0.5, top = cur.gy() + 190;
        LOGGER.info("[astral] the tree's ultimate meteor begins");
        for (int t = 0; t < 300; t++) {
            final int tt = t;
            at(t, l -> {
                double y = top - (top - cur.gy()) * (tt / 300.0) * (tt / 300.0);   // it speeds up as it falls
                double size = 6 + tt / 300.0 * 10;
                l.sendParticles(ParticleTypes.FLAME, cx, y, cz, 30, size * 0.5, size * 0.4, size * 0.5, 0.02);
                l.sendParticles(ParticleTypes.LAVA, cx, y, cz, 10, size * 0.5, size * 0.4, size * 0.5, 0);
                l.sendParticles(ParticleTypes.LARGE_SMOKE, cx, y + size * 0.5, cz, 20, size * 0.5, size * 0.6, size * 0.5, 0.05);
                if (tt % 20 == 0) sfx(l, SoundEvents.WARDEN_HEARTBEAT, 3f, 0.4f + tt / 400f);
                if (tt == 120) sfx(l, SoundEvents.ENDER_DRAGON_GROWL, 2f, 0.4f);
                if (tt == 220) sfx(l, SoundEvents.WARDEN_ROAR, 3f, 0.5f);
            });
        }
        at(300, l -> {
            sfx(l, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.5f);
            sfx(l, SoundEvents.LIGHTNING_BOLT_THUNDER, 4f, 0.6f);
            l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cur.gy() + 2, cz, 12, 40, 4, 40, 0);
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                boom(l, cx + Math.cos(a) * 60, cz + Math.sin(a) * 60, 20);
            }
            for (ServerPlayer p : fighters(l)) {
                if (Math.hypot(p.getX() - cx, p.getZ() - cz) <= ULT_SAFE_R) continue;
                p.setHealth(Math.min(p.getHealth(), 1f));
                lastHit.remove(p.getUUID());
                loseLife(l, p);
            }
        });
    }

    private static void startRoots(ServerLevel lv) {
        rootsActive = true;
        activeKind = 0;
        int players = fighters(lv).size();
        int n = Math.min(70, 6 + 2 * rootStage + players);   // one fighter: about 7 roots; a crowd: up to 70
        rootsEnd = rt + Math.min(2400, 900 + 30L * n);          // 45 seconds and a little more for every root
        double rmax = Math.min(ARENA_R - 45, 45 + n * 3.0);      // few roots stay near the middle, many reach the edge
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 30 + rnd.nextDouble() * Math.max(10, rmax - 30);
            BlockPos b = BlockPos.containing(0.5 + Math.cos(a) * r, groundY, 0.5 + Math.sin(a) * r);
            if (!lv.getBlockState(b).canBeReplaced() || !lv.getBlockState(b.above()).canBeReplaced()) continue;   // grass and flowers do not stop a root
            lv.setBlock(b, Blocks.MANGROVE_WOOD.defaultBlockState(), 3);
            lv.setBlock(b.above(), Blocks.MANGROVE_WOOD.defaultBlockState(), 3);
            roots.add(b);
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
    private static void rootsSecond(ServerLevel lv) {
        Mob boss = findBoss(lv, TAG_RAID);
        if (boss == null) return;
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
        if (!rootsActive) {
            if (rootStage < THRESHOLDS.length && boss.getHealth() / RAID_HP <= THRESHOLDS[rootStage]) {
                if (rootStage == 0) startRoots(lv); else if (rootStage == 1) startTransformation(lv); else startSeeds(lv);
            } else if (rootStage >= THRESHOLDS.length && !ultimateDone && !cutscene && boss.getHealth() / RAID_HP <= 0.15f) {
                ultimateDone = true;
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
            boss.setHealth(RAID_HP);
            rootsActive = false;
            rootStage = 0;
            if (raidBar != null) raidBar.setName(barName("", ChatFormatting.DARK_PURPLE));
            lineBucket = 20;
            tellLevel(lv, "The roots drink. The Mother Tree of Desire is whole again.", ChatFormatting.DARK_RED);
        }
    }

    private static void raidTick(MinecraftServer server, AstralData d) {
        ServerLevel lv = dim(server, "raid");
        if (lv == null) return;
        rt++;
        cur = new Arena(lv, 0.5, 0.5, groundY, true);
        if (rt % 20 == 0) rootsSecond(lv);
        treeCast(lv);
        if (rt % 5 == 0) for (ServerPlayer p : fighters(lv)) { double sp = Lotm.sanityPercent(p); if (sp >= 0 && sp <= 3) sanityBreak(p); }   // before LOTM's own collapse
        if (rt >= nextPattern && !cutscene && !fighters(lv).isEmpty()) {
            int[] opts = NEXT[lastPattern];
            int which = opts[ThreadLocalRandom.current().nextInt(opts.length)];
            lastPattern = which;
            pattern(lv, which);
            nextPattern = rt + DURATION[which] + 30 + ThreadLocalRandom.current().nextInt(40);
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
        final double leash = Math.min(ARENA_R, 150 + 12 * Math.sqrt(Math.max(1, crowdSize)));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean fresh = !d.raidLives.containsKey(p.getUUID());
            d.raidLives.putIfAbsent(p.getUUID(), LIVES);
            if (p.level() != lv) p.teleportTo(lv, 0.5 + SPAWN_R, groundY, 0.5, p.getYRot(), 0f);
            if (fresh) blessPlayer(p);
            else if (!p.isSpectator() && p.isAlive()) {
                double px = p.getX() - 0.5, pz = p.getZ() - 0.5, dist = Math.hypot(px, pz);
                if (dist > leash) { // the battlefield has an edge: nobody outruns the tree
                    double k = (leash - 12) / dist;
                    p.teleportTo(lv, 0.5 + px * k, Math.max(groundY, p.getY()), 0.5 + pz * k, p.getYRot(), p.getXRot());
                    p.displayClientMessage(Component.literal("The Astral World will not let you flee. Fight!").withStyle(ChatFormatting.DARK_RED), true);
                } else if (dist > leash - 20) p.displayClientMessage(Component.literal("The edge of the battlefield is near. You cannot run from this.").withStyle(ChatFormatting.RED), true);
            }
            if (d.raidLives.get(p.getUUID()) <= 0) setGameMode(p, GameType.SPECTATOR);
            else p.displayClientMessage(Component.literal("Lives: " + "♥".repeat(d.raidLives.get(p.getUUID()))).withStyle(ChatFormatting.RED), true);
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

    private static void raidWon(MinecraftServer server, AstralData d) {
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
    static final double ARENA_R = 260;        // the raid arena: fighters may roam this far from the tree (built for up to ~100 players)
    private static final double SPAWN_R = 75;     // fighters arrive well outside the tree's trunk (the model's trunk is about 15 blocks wide at the foot)
    private static final float MAX_HIT = 0.10f;     // no single hit takes more than this share of a boss's health
    private static final float MAX_DROP = 0.12f;    // anything that takes more than this in one tick is not a hit but an execution
    private static final float RAID_HIT = 0.02f;    // the Mother Tree: no single hit takes more than this share
    private static final float RAID_RATE = 0.04f;   // ...and nothing takes more than this share of it per second, however many hits an ability lands
    private static final float BOSS_RATE = 0.08f;   // same for the planet sovereigns
    private static final Map<UUID, long[]> rateWin = new HashMap<>();   // boss -> {window start tick, damage taken in the window (as float bits)}

    /** Caps what a boss can lose per second so multi-hit abilities (Pillar of Lightning and the like) cannot chain past the per-hit cap. Returns the allowed amount. */
    private static float rateLimit(net.minecraft.world.entity.LivingEntity e, float amount, float rate) {
        long[] w = rateWin.computeIfAbsent(e.getUUID(), k -> new long[]{gt, Float.floatToIntBits(0f)});
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
            "jupiter", new double[]{17, 13}, "saturn", new double[]{11, 11}, "uranus", new double[]{18, 20}, "neptune", new double[]{15, 15}, "tree", new double[]{30, 150});

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

    private static void sizeHitbox(Mob m) {
        String n = m.hasCustomName() ? m.getCustomName().getString() : "";
        double[] box = n.startsWith(RAID_NAME) ? HITBOX.get("tree") : n.startsWith("Sovereign of ") ? HITBOX.get(n.substring(13).trim().toLowerCase(java.util.Locale.ROOT)) : null;
        if (box == null) return;
        var inst = m.getAttribute(Attributes.SCALE);
        if (pehkui(m, (float) (box[0] / 0.6), (float) (box[1] / 1.8))) {
            if (inst != null && Math.abs(inst.getBaseValue() - 1.0) > 0.01) { inst.setBaseValue(1.0); m.refreshDimensions(); }   // not both
            return;
        }
        double want = Math.min(16, Math.max(box[0] / 0.6, box[1] / 1.8));   // the vanilla attribute stops at 16, one scale for both axes
        if (inst != null && Math.abs(inst.getBaseValue() - want) > 0.01) { inst.setBaseValue(want); m.refreshDimensions(); }
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
        for (Entity e : lv.getAllEntities()) {
            if (e instanceof Mob fp && fp.getPersistentData().getBoolean("isFallenPersona")) { fp.discard(); continue; }   // a regress would spawn a corrupted rampager at the players
            if (e instanceof Mob dm && dm.getTags().contains(TAG_BOSS) && !dm.isAlive() && dm.getHealth() <= 0f) silentDeath(lv, dm);
            if (!(e instanceof Mob m) || !m.getTags().contains(TAG_BOSS) || !m.isAlive()) continue;
            stripDeath(m);
            freeFromOwner(m);
            sizeHitbox(m);
            float max = m.getMaxHealth(), hp = m.getHealth();
            if (m.getTags().contains(TAG_RAID) && rootStage < THRESHOLDS.length && hp < THRESHOLDS[rootStage] * RAID_HP - 0.5f) { // however it got hurt (an ability that sets health), it cannot pass the next phase
                hp = THRESHOLDS[rootStage] * RAID_HP - 0.5f;
                m.setHealth(hp);
            }
            Float last = lastHp.get(m.getUUID());
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

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        Entity e = event.getEntity();
        if (traitor != null && (e.getTags().contains(TAG_BOSS) || e.getTags().contains(TAG_MINION)) && event.getSource().getEntity() instanceof ServerPlayer ta && ta.getUUID().equals(traitor)) { event.setCanceled(true); return; }   // the ally cannot hurt the tree or her thralls
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
            var le = (net.minecraft.world.entity.LivingEntity) e;
            float a = rateLimit(le, Math.min(event.getAmount() * RAID_DAMAGE, le.getMaxHealth() * RAID_HIT), RAID_RATE);
            if (rootStage < THRESHOLDS.length) a = Math.min(a, Math.max(0f, le.getHealth() - THRESHOLDS[rootStage] * RAID_HP + 0.5f)); // it can never be hit past the next phase, however hard
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
        lastHit.remove(p.getUUID());
        loseLife(plv, p);
    }

    private static final Map<UUID, Long> sanityDeathAt = new HashMap<>();
    private static final Map<UUID, Integer> raidSeq = new HashMap<>();   // the sequence each fighter carried in, and the best they reach

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        if (noHeal.getOrDefault(p.getUUID(), 0L) > gt) { event.setCanceled(true); return; }
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
                .then(Commands.literal("reset").executes(ctx -> {
                    MinecraftServer s = ctx.getSource().getServer();
                    resetAll(s);
                    Stage.set(s, 0);
                    return 1;
                })));
    }
}
