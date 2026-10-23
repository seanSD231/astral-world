package com.astralspace;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Cosmos (outer space with the planets of the solar system) and its travel rules.
 *  - /cosmos takes you there. Ops can always use it; Beyonders can if the Lord of the Mysteries wiki gives their
 *    sequence a way into the Cosmos: the Door pathway from Seq 3 (Wanderer "enters the Cosmos and reaches other planets",
 *    Planeswalker, Key of Stars, Door), and the Seq 0 of the six pathways of the Outer Deities (Mother, Moon, Abyss, Chained,
 *    Black Emperor, Justiciar), who live in the Cosmos.
 *  - There is no gravity in the Cosmos, and everyone there is corrupted fast.
 *  - Walking up to a planet takes you to that planet's own (blank) dimension; /cosmos leave brings you back.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class Cosmos {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceKey<Level> COSMOS = key("cosmos");

    /** Door (Apprentice): Wanderer (Seq 3) and everything above it. */
    private static final int DOOR_SEQUENCE = 3;
    /** Mother, Moon, Abyss, Chained, Black Emperor, Justiciar: only their Seq 0, the true gods. */
    private static final Set<String> OUTER_DEITY_PATHWAYS = Set.of("planter", "apothecary", "criminal", "prisoner", "lawyer", "arbiter");

    private static final ResourceLocation NO_GRAVITY = ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "cosmos_no_gravity");
    private static final long PORTAL_COOLDOWN_MS = 8000L;

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, AstralMod.MOD_ID);
    static {
        GENERATORS.register("cosmos", () -> CosmosGenerator.CODEC);
        GENERATORS.register("planet", () -> PlanetGenerator.CODEC);
    }

    /** Where each player came from (until the server restarts; after that /cosmos leave uses the world spawn). */
    private static final Map<UUID, double[]> origins = new HashMap<>();
    private static final Map<UUID, String> originDims = new HashMap<>();
    private static final Map<UUID, Long> lastPortal = new HashMap<>();
    private static final Map<UUID, Boolean> canTravelSeen = new HashMap<>();
    private static final Map<UUID, Object> visitPathway = new HashMap<>();

    private Cosmos() {}

    public static ResourceKey<Level> key(String id) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, id));
    }

    public static void register(IEventBus modBus) { GENERATORS.register(modBus); }

    // ------------------------------------------------------------------ who may go

    public static boolean canTravel(ServerPlayer p) {
        int seq = Lotm.sequence(p);
        if (seq < 0) return false;
        String path = Lotm.pathwayId(p);
        if (path.equals("apprentice")) return seq <= DOOR_SEQUENCE;
        return seq == 0 && OUTER_DEITY_PATHWAYS.contains(path);
    }

    private static boolean allowed(net.minecraft.commands.CommandSourceStack src) { return src.hasPermission(2); }   // operators only

    // ------------------------------------------------------------------ places

    /** Standing spot in every planet dimension. */
    public static final double[] PLANET_SPAWN = {0.5, PlanetGenerator.spawnY(), 0.5};

    public static boolean isPlanetDim(ResourceKey<Level> dim) { return planetOf(dim) != null; }

    private static CosmosGenerator.Planet planetOf(ResourceKey<Level> dim) {
        for (CosmosGenerator.Planet pl : CosmosGenerator.PLANETS) if (dim.equals(key(pl.id()))) return pl;
        return null;
    }

    /** A spot in space next to a planet, just outside the distance where it pulls you in. */
    private static double[] near(CosmosGenerator.Planet pl) { return new double[]{pl.x() + pl.r() + 10.5, pl.y(), pl.z() + 0.5}; }

    /** Where /cosmos arrives: next to Earth, or Mercury once Earth is gone. */
    private static CosmosGenerator.Planet earth() { return CosmosGenerator.PLANETS.get(CosmosGenerator.earthGone ? 0 : 2); }

    /** The people standing right next to a traveller come along (as with the Traveler's Door). */
    private static java.util.List<ServerPlayer> companions(ServerPlayer p) {
        java.util.List<ServerPlayer> list = new java.util.ArrayList<>();
        for (ServerPlayer o : p.serverLevel().players())
            if (o != p && o.isAlive() && !o.isSpectator() && o.distanceToSqr(p) <= 64) list.add(o);
        return list;
    }

    private static void teleport(ServerPlayer p, ServerLevel lv, double x, double y, double z) {
        lastPortal.put(p.getUUID(), System.currentTimeMillis());
        p.fallDistance = 0;
        p.teleportTo(lv, x, y, z, p.getYRot(), 0f);
    }

    /** /cosmos: the Cosmos is only a starry night sky with the arena field in it. Arrive next to the ruin. */
    private static int enter(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        ServerLevel lv = server.getLevel(COSMOS);
        if (lv == null) { p.sendSystemMessage(Component.literal("The Cosmos is not loaded. Is the mod installed correctly?").withStyle(ChatFormatting.RED)); return 0; }
        if (!p.level().dimension().equals(COSMOS)) {
            origins.put(p.getUUID(), new double[]{p.getX(), p.getY(), p.getZ()});
            originDims.put(p.getUUID(), p.level().dimension().location().toString());
        }
        lv.getChunk(1, 1);
        double x = 30.5, z = 0.5;
        double y = lv.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z) + 1.0;
        teleport(p, lv, x, y, z);
        return 1;
    }

    /** /cosmos to <planet>: straight to a planet's own dimension, bringing the people next to you. */
    private static int toPlanet(ServerPlayer p, String id) {
        MinecraftServer server = p.getServer();
        CosmosGenerator.Planet pl = null;
        for (CosmosGenerator.Planet x : CosmosGenerator.PLANETS) if (x.id().equals(id)) pl = x;
        if (pl == null || (pl.id().equals("earth") && CosmosGenerator.earthGone)) {
            p.sendSystemMessage(Component.literal("There is no planet like that.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (pl.id().equals("earth")) { // Earth is the overworld
            ServerLevel ow = server.overworld();
            var s = ow.getSharedSpawnPos();
            for (ServerPlayer o : companions(p)) teleport(o, ow, s.getX() + 1.5, s.getY(), s.getZ() + 0.5);
            teleport(p, ow, s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
            p.displayClientMessage(Component.literal(pl.name()).withStyle(ChatFormatting.AQUA), true);
            return 1;
        }
        ServerLevel dest = server.getLevel(key(pl.id()));
        if (dest == null) return 0;
        dest.getChunk(0, 0);
        for (ServerPlayer o : companions(p)) teleport(o, dest, PLANET_SPAWN[0] + 1, PLANET_SPAWN[1], PLANET_SPAWN[2]);
        teleport(p, dest, PLANET_SPAWN[0], PLANET_SPAWN[1], PLANET_SPAWN[2]);
        p.displayClientMessage(Component.literal(pl.name()).withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static int leave(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        CosmosGenerator.Planet pl = planetOf(p.level().dimension());
        if (pl != null) { // from a planet back into space beside it
            ServerLevel lv = server.getLevel(COSMOS);
            if (lv == null) return 0;
            double[] at = near(pl);
            teleport(p, lv, at[0], at[1], at[2]);
            return 1;
        }
        double[] o = origins.get(p.getUUID());
        String dim = originDims.get(p.getUUID());
        ServerLevel target = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dim)));
        if (target != null && target.dimension().equals(Level.OVERWORLD) && AstralData.get(server).earthGone) {
            p.sendSystemMessage(Component.literal("Earth is gone.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (target == null || o == null) {
            if (AstralData.get(server).earthGone) { p.sendSystemMessage(Component.literal("Earth is gone.").withStyle(ChatFormatting.RED)); return 0; }
            target = server.overworld();
            var s = target.getSharedSpawnPos();
            teleport(p, target, s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
        } else {
            teleport(p, target, o[0], o[1], o[2]);
        }
        return 1;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cosmos")
                .requires(Cosmos::allowed)
                .executes(ctx -> enter(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource().getPlayerOrException()))));
    }

}
