package com.astralspace;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * The Cosmos: empty space with planets. The sky is a grid of 160 x 160 block cells; each cell may hold one planet (and
 * sometimes a moon), found again from the cell's coordinates alone, so every chunk can build its own slice of any planet
 * that reaches into it. Cell (0, 0) always holds the home planet.
 */
public final class CosmosGenerator extends ChunkGenerator {
    public static final MapCodec<CosmosGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)).apply(i, CosmosGenerator::new));

    public static final int CELL = 160;
    /** The home planet: where /cosmos puts people. */
    public static final int HOME_X = 80, HOME_Y = 110, HOME_Z = 80, HOME_R = 26;

    private static final int EARTH = 0, ROCKY = 1, DESERT = 2, ICE = 3, LAVA = 4, GAS = 5, MOON = 6, CRYSTAL = 7;

    private record Planet(int x, int y, int z, int r, int kind, int seed, boolean ring) {}

    public CosmosGenerator(BiomeSource source) { super(source); }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    // ------------------------------------------------------------------ where the planets are

    private static void cellPlanets(int cx, int cz, List<Planet> out) {
        Random r = new Random(cx * 341873128712L + cz * 132897987541L + 0xC05305L);
        if (cx == 0 && cz == 0) {
            out.add(new Planet(HOME_X, HOME_Y, HOME_Z, HOME_R, EARTH, 7, false));
            return;
        }
        if (r.nextInt(100) >= 85) return; // an empty cell
        int x = cx * CELL + 50 + r.nextInt(60), z = cz * CELL + 50 + r.nextInt(60), y = 70 + r.nextInt(130);
        int roll = r.nextInt(100);
        int kind = roll < 18 ? EARTH : roll < 36 ? ROCKY : roll < 50 ? DESERT : roll < 64 ? ICE : roll < 74 ? LAVA : roll < 88 ? GAS : CRYSTAL;
        int radius = kind == GAS ? 34 + r.nextInt(18) : 12 + r.nextInt(24);
        int seed = r.nextInt(100000);
        out.add(new Planet(x, y, z, radius, kind, seed, kind == GAS && r.nextInt(100) < 70));
        if (r.nextInt(100) < 40) { // a moon
            double a = r.nextDouble() * Math.PI * 2;
            int d = (int) (radius * 1.9 + 14 + r.nextInt(14));
            int my = Math.max(24, Math.min(290, y + r.nextInt(31) - 15));
            out.add(new Planet(x + (int) (Math.cos(a) * d), my, z + (int) (Math.sin(a) * d), 5 + r.nextInt(5), MOON, r.nextInt(100000), false));
        }
    }

    private static List<Planet> near(int chunkX, int chunkZ) {
        List<Planet> list = new ArrayList<>();
        int cx = Math.floorDiv(chunkX * 16 + 8, CELL), cz = Math.floorDiv(chunkZ * 16 + 8, CELL);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) cellPlanets(cx + dx, cz + dz, list);
        return list;
    }

    // ------------------------------------------------------------------ what a planet is made of

    private static final Block[] BANDS = {Blocks.ORANGE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.BROWN_TERRACOTTA,
            Blocks.RED_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.PURPLE_TERRACOTTA, Blocks.BLUE_TERRACOTTA};

    private static double hash(int a, int b, int c) {
        long h = a * 73856093L ^ b * 19349663L ^ c * 83492791L;
        h = (h ^ (h >>> 13)) * 0x5DEECE66DL;
        return ((h ^ (h >>> 17)) & 0xFFFFF) / (double) 0xFFFFF;
    }

    /** The block of this planet at (x, y, z), or null if the planet does not reach it. */
    private static BlockState blockAt(Planet p, int x, int y, int z) {
        int dx = x - p.x, dy = y - p.y, dz = z - p.z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double bump = 0.9 * (Math.sin(x * 0.19 + p.seed) + Math.sin(z * 0.16 + y * 0.11 + p.seed * 2.0)); // a slightly lumpy surface
        double reff = p.r + (p.kind == GAS ? 0 : bump);
        if (d > reff) {
            if (p.ring) { // the ring: a thin, broken disc of ice and rock around the equator
                double rho = Math.sqrt(dx * dx + dz * dz);
                if (dy == 0 && rho > p.r * 1.45 && rho < p.r * 1.95 && hash(x, 0, z) < 0.8)
                    return (hash(z, 1, x) < 0.6 ? Blocks.PACKED_ICE : hash(x, 2, z) < 0.5 ? Blocks.STONE : Blocks.CALCITE).defaultBlockState();
            }
            return null;
        }
        double depth = reff - d;
        double n = hash(x / 3, y / 3, z / 3);
        switch (p.kind) {
            case EARTH:
                if (depth < 1.5) return (n < 0.25 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK).defaultBlockState();
                return (depth < 4 ? Blocks.DIRT : depth > p.r - 4 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
            case ROCKY:
                if (depth < 2) return (n < 0.4 ? Blocks.ANDESITE : n < 0.7 ? Blocks.STONE : Blocks.TUFF).defaultBlockState();
                return (depth > p.r * 0.6 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
            case DESERT:
                if (depth < 3) return (n < 0.5 ? Blocks.SANDSTONE : n < 0.8 ? Blocks.RED_SANDSTONE : Blocks.ORANGE_TERRACOTTA).defaultBlockState();
                return Blocks.SANDSTONE.defaultBlockState();
            case ICE:
                if (depth < 2) return (n < 0.5 ? Blocks.SNOW_BLOCK : Blocks.PACKED_ICE).defaultBlockState();
                return Blocks.BLUE_ICE.defaultBlockState();
            case LAVA:
                if (depth < 2) return (n < 0.5 ? Blocks.BLACKSTONE : n < 0.8 ? Blocks.BASALT : Blocks.MAGMA_BLOCK).defaultBlockState();
                if (depth > 6 && p.r >= 16 && d < p.r - 6) return Blocks.LAVA.defaultBlockState(); // a sealed molten core
                return Blocks.MAGMA_BLOCK.defaultBlockState();
            case GAS: { // horizontal bands, wavy
                int band = (int) Math.floor((dy + 3.0 * Math.sin(dx * 0.11 + p.seed) + 2.0 * Math.sin(dz * 0.07)) / 4.0) + p.seed;
                return BANDS[Math.floorMod(band, BANDS.length)].defaultBlockState();
            }
            case MOON:
                if (depth < 2) return (n < 0.5 ? Blocks.STONE : Blocks.ANDESITE).defaultBlockState();
                return Blocks.STONE.defaultBlockState();
            default: // CRYSTAL
                if (depth < 2.5) return (n < 0.7 ? Blocks.AMETHYST_BLOCK : Blocks.BUDDING_AMETHYST).defaultBlockState();
                return Blocks.PURPUR_BLOCK.defaultBlockState();
        }
    }

    // ------------------------------------------------------------------ the chunk generator

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        int minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight() - 1;
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Planet p : near(chunk.getPos().x, chunk.getPos().z)) {
            int reach = p.r + 3 + (p.ring ? (int) (p.r * 1.0) : 0);
            if (p.x + reach < x0 || p.x - reach > x0 + 15 || p.z + reach < z0 || p.z - reach > z0 + 15) continue;
            int yLo = Math.max(minY, p.y - p.r - 3), yHi = Math.min(maxY, p.y + p.r + 3);
            for (int x = x0; x < x0 + 16; x++) {
                for (int z = z0; z < z0 + 16; z++) {
                    double h2 = (x - p.x) * (double) (x - p.x) + (z - p.z) * (double) (z - p.z);
                    boolean inSphere = h2 <= (p.r + 3.0) * (p.r + 3.0);
                    if (inSphere) {
                        for (int y = yLo; y <= yHi; y++) {
                            BlockState s = blockAt(p, x, y, z);
                            if (s != null) chunk.setBlockState(pos.set(x, y, z), s, false);
                        }
                    }
                    if (p.ring && p.y >= minY && p.y <= maxY && h2 > p.r * 1.4 * p.r * 1.4 && h2 < p.r * 2.0 * p.r * 2.0) {
                        BlockState s = blockAt(p, x, p.y, z);
                        if (s != null) chunk.setBlockState(pos.set(x, p.y, z), s, false);
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes, StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) { }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) { }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) { }

    @Override
    public int getGenDepth() { return 320; }

    @Override
    public int getSeaLevel() { return -63; }

    @Override
    public int getMinY() { return 0; }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) { return level.getMinBuildHeight(); }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        return new NoiseColumn(level.getMinBuildHeight(), new BlockState[0]);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) { }
}
