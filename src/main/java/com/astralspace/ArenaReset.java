package com.astralspace;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Puts the arena back the way the world generator made it. The generator is deterministic, so each chunk is generated again on the side
 * and compared with the real one; every block that differs (broken, placed, burned, chests, roots ...) is set back. A few chunks a tick.
 * Runs after every Mother Tree raid and every hunger games, and on /arenareset.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class ArenaReset {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final Deque<ChunkPos> queue = new ArrayDeque<>();
    private static long changed = 0;

    private ArenaReset() { }

    public static boolean busy() { return !queue.isEmpty(); }

    public static void start(MinecraftServer s) {
        ServerLevel lv = s.getLevel(Cosmos.key("cosmos"));
        if (lv == null) return;
        queue.clear();
        changed = 0;
        int r = (int) (PlanetGenerator.FIELD_R / 16) + 1;
        for (int cx = -r; cx <= r; cx++) for (int cz = -r; cz <= r; cz++) {
            if (Math.hypot(cx * 16 + 8, cz * 16 + 8) <= PlanetGenerator.FIELD_R + 24) queue.add(new ChunkPos(cx, cz));
        }
        LOGGER.info("[arenareset] resetting {} chunks", queue.size());
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arenareset").requires(c -> c.hasPermission(2)).executes(ctx -> {
            if (busy()) { ctx.getSource().sendFailure(Component.literal("The arena is already being reset.")); return 0; }
            start(ctx.getSource().getServer());
            ctx.getSource().sendSuccess(() -> Component.literal("Resetting the arena to how it was generated (takes about half a minute)."), true);
            return 1;
        }));
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (queue.isEmpty()) return;
        MinecraftServer s = event.getServer();
        ServerLevel lv = s.getLevel(Cosmos.key("cosmos"));
        if (lv == null || !(lv.getChunkSource().getGenerator() instanceof PlanetGenerator gen)) { queue.clear(); return; }
        for (int i = 0; i < 3 && !queue.isEmpty(); i++) {
            ChunkPos cp = queue.poll();
            try { resetChunk(lv, gen, cp); } catch (Throwable t) { LOGGER.warn("[arenareset] chunk {} failed: {}", cp, t.toString()); }
        }
        if (queue.isEmpty()) {
            for (Entity en : lv.getAllEntities()) if (en instanceof ItemEntity) en.discard();
            LOGGER.info("[arenareset] done, {} blocks put back", changed);
        }
    }

    private static void resetChunk(ServerLevel lv, PlanetGenerator gen, ChunkPos cp) {
        LevelChunk real = lv.getChunk(cp.x, cp.z);
        ProtoChunk fresh = new ProtoChunk(cp, UpgradeData.EMPTY, lv, lv.registryAccess().registryOrThrow(Registries.BIOME), null);
        gen.fillFromNoise(Blender.empty(), lv.getChunkSource().randomState(), lv.structureManager(), fresh).join();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int si = 0; si < real.getSectionsCount(); si++) {
            LevelChunkSection a = real.getSection(si), b = fresh.getSection(si);
            if (a.hasOnlyAir() && b.hasOnlyAir()) continue;
            int y0 = real.getMinBuildHeight() + si * 16;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 16; y++) {
                BlockState want = b.getBlockState(x, y, z);
                if (a.getBlockState(x, y, z) == want) continue;
                pos.set(cp.getMinBlockX() + x, y0 + y, cp.getMinBlockZ() + z);
                lv.setBlock(pos, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                changed++;
            }
        }
    }
}
