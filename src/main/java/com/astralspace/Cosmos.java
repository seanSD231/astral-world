package com.astralspace;

import com.mojang.serialization.MapCodec;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The Cosmos dimension (outer space with planets): its generator type, the /cosmos command and a rescue from the void. */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class Cosmos {
    public static final ResourceKey<Level> COSMOS = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "cosmos"));

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, AstralMod.MOD_ID);
    static {
        GENERATORS.register("cosmos", () -> CosmosGenerator.CODEC);
    }

    /** Where each player came from (kept until the server restarts; after that /cosmos leave uses the world spawn). */
    private static final Map<UUID, double[]> origins = new HashMap<>();
    private static final Map<UUID, String> originDims = new HashMap<>();

    private Cosmos() {}

    public static void register(IEventBus modBus) { GENERATORS.register(modBus); }

    /** The standing spot on top of the home planet. */
    private static double[] home(ServerLevel lv) {
        lv.getChunk(CosmosGenerator.HOME_X >> 4, CosmosGenerator.HOME_Z >> 4); // makes sure it is generated
        int top = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, CosmosGenerator.HOME_X, CosmosGenerator.HOME_Z);
        return new double[]{CosmosGenerator.HOME_X + 0.5, Math.max(top, CosmosGenerator.HOME_Y + CosmosGenerator.HOME_R), CosmosGenerator.HOME_Z + 0.5};
    }

    private static int enter(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        ServerLevel lv = server.getLevel(COSMOS);
        if (lv == null) { p.sendSystemMessage(Component.literal("The Cosmos is not loaded. Is the mod installed correctly?")); return 0; }
        if (!p.level().dimension().equals(COSMOS)) {
            origins.put(p.getUUID(), new double[]{p.getX(), p.getY(), p.getZ()});
            originDims.put(p.getUUID(), p.level().dimension().location().toString());
        }
        double[] h = home(lv);
        p.teleportTo(lv, h[0], h[1], h[2], p.getYRot(), 0f);
        return 1;
    }

    private static int leave(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        double[] o = origins.get(p.getUUID());
        String dim = originDims.get(p.getUUID());
        ServerLevel target = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dim)));
        if (target == null || o == null) {
            target = server.overworld();
            var s = target.getSharedSpawnPos();
            p.teleportTo(target, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, p.getYRot(), 0f);
        } else {
            p.teleportTo(target, o[0], o[1], o[2], p.getYRot(), p.getXRot());
        }
        return 1;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cosmos")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> enter(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource().getPlayerOrException()))));
    }

    /** Anyone who falls into the void between the planets is put back on the home planet. */
    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 0) return;
        ServerLevel lv = server.getLevel(COSMOS);
        if (lv == null) return;
        for (ServerPlayer p : lv.players()) {
            if (p.getY() < -20) {
                double[] h = home(lv);
                p.fallDistance = 0;
                p.teleportTo(lv, h[0], h[1], h[2], p.getYRot(), 0f);
                p.displayClientMessage(Component.literal("The void gave you back."), true);
            }
        }
    }
}
