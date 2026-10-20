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
 * The Cosmos: empty space with the eight planets of our solar system (and the Moon by Earth), as hollow shells of
 * blocks. The planets are at fixed places (PLANETS), so every chunk can build its own slice of any planet that reaches into it.
 */
public final class CosmosGenerator extends ChunkGenerator {
    public static final MapCodec<CosmosGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)).apply(i, CosmosGenerator::new));

    /** A planet of the solar system, drawn to a compressed scale. Each one is also a dimension of its own (id). */
    public record Planet(String id, String name, int x, int y, int z, int r, int kind, boolean ring) {}

    private static final int MERCURY = 0, VENUS = 1, EARTH = 2, MARS = 3, JUPITER = 4, SATURN = 5, URANUS = 6, NEPTUNE = 7, MOON = 8;

    /** In order from the sun, along +x. Walking up to one takes you to its dimension. */
    public static final List<Planet> PLANETS = List.of(
            new Planet("mercury", "Mercury", 0, 120, 0, 7, MERCURY, false),
            new Planet("venus", "Venus", 230, 120, -50, 13, VENUS, false),
            new Planet("earth", "Earth", 460, 120, 40, 14, EARTH, false),
            new Planet("mars", "Mars", 680, 120, -40, 9, MARS, false),
            new Planet("jupiter", "Jupiter", 950, 120, 60, 46, JUPITER, false),
            new Planet("saturn", "Saturn", 1330, 120, -60, 40, SATURN, true),
            new Planet("uranus", "Uranus", 1680, 120, 30, 24, URANUS, false),
            new Planet("neptune", "Neptune", 1960, 120, -40, 23, NEPTUNE, false));

    /** Decoration only: the Moon next to Earth (not a destination). */
    private static final Planet THE_MOON = new Planet("moon", "Moon", 492, 126, 52, 4, MOON, false);

    private static final List<Planet> ALL;
    static {
        List<Planet> l = new ArrayList<>(PLANETS);
        l.add(THE_MOON);
        ALL = l;
    }

    /** Earth has been destroyed: it is no longer built (set by Cosmos from the saved data). */
    public static volatile boolean earthGone = false;

    /** Builds a planet (or clears it away, present = false) block by block in an existing level. */
    public static void setPlanet(net.minecraft.server.level.ServerLevel lv, Planet p, boolean present) {
        int reach = p.r + 3;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = p.x - reach; x <= p.x + reach; x++)
            for (int z = p.z - reach; z <= p.z + reach; z++)
                for (int y = Math.max(lv.getMinBuildHeight(), p.y - reach); y <= Math.min(lv.getMaxBuildHeight() - 1, p.y + reach); y++) {
                    BlockState s = present ? blockAt(p, x, y, z) : Blocks.AIR.defaultBlockState();
                    if (s == null) continue;
                    pos.set(x, y, z);
                    if (!present && lv.getBlockState(pos).isAir()) continue;
                    lv.setBlock(pos, s, 2);
                }
    }

    public CosmosGenerator(BiomeSource source) { super(source); }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    // ------------------------------------------------------------------ what a planet is made of

    private static final Block[] JUP = {Blocks.ORANGE_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.WHITE_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA};
    private static final Block[] SAT = {Blocks.SMOOTH_SANDSTONE, Blocks.YELLOW_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.SANDSTONE};
    private static final Block[] NEP = {Blocks.BLUE_CONCRETE, Blocks.BLUE_TERRACOTTA, Blocks.LIGHT_BLUE_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.CYAN_TERRACOTTA};

    private static double hash(int a, int b, int c) {
        long h = a * 73856093L ^ b * 19349663L ^ c * 83492791L;
        h = (h ^ (h >>> 13)) * 0x5DEECE66DL;
        return ((h ^ (h >>> 17)) & 0xFFFFF) / (double) 0xFFFFF;
    }

    private static final int SHELL = 4; // planets are hollow shells this thick (the inside is never seen, and it keeps generation fast)

    /** The block of this planet at (x, y, z), or null if the planet does not reach it. */
    private static BlockState blockAt(Planet p, int x, int y, int z) {
        int dx = x - p.x, dy = y - p.y, dz = z - p.z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean gas = p.kind == JUPITER || p.kind == SATURN || p.kind == URANUS || p.kind == NEPTUNE;
        double bump = gas ? 0 : 0.8 * (Math.sin(x * 0.19 + p.r) + Math.sin(z * 0.16 + y * 0.11 + p.r * 2.0));
        double reff = p.r + bump;
        if (d > reff) {
            if (p.ring) { // Saturn's ring: a thin, broken disc of ice and rock
                double rho = Math.sqrt(dx * dx + dz * dz);
                if (dy == 0 && rho > p.r * 1.45 && rho < p.r * 1.95 && hash(x, 0, z) < 0.85)
                    return (hash(z, 1, x) < 0.6 ? Blocks.PACKED_ICE : hash(x, 2, z) < 0.5 ? Blocks.CALCITE : Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState();
            }
            return null;
        }
        double depth = reff - d;
        if (depth >= SHELL) return null;
        double n = hash(x / 3, y / 3, z / 3);
        double lat = d == 0 ? 0 : dy / d;
        switch (p.kind) {
            case MERCURY:
                return (n < 0.45 ? Blocks.ANDESITE : n < 0.8 ? Blocks.STONE : Blocks.GRAY_CONCRETE).defaultBlockState();
            case VENUS:
                return (n < 0.45 ? Blocks.YELLOW_TERRACOTTA : n < 0.8 ? Blocks.ORANGE_TERRACOTTA : Blocks.SANDSTONE).defaultBlockState();
            case EARTH: {
                double c = hash(x / 5, y / 5, z / 5);
                if (Math.abs(lat) > 0.9) return Blocks.WHITE_CONCRETE.defaultBlockState(); // ice caps
                if (depth < 1.5 && hash(x / 2, y / 2 + 7, z / 2) < 0.12) return Blocks.WHITE_CONCRETE.defaultBlockState(); // clouds
                return (c < 0.55 ? Blocks.BLUE_CONCRETE : c < 0.9 ? Blocks.GREEN_CONCRETE : Blocks.BROWN_CONCRETE).defaultBlockState();
            }
            case MARS:
                if (Math.abs(lat) > 0.88) return Blocks.SNOW_BLOCK.defaultBlockState();
                return (n < 0.45 ? Blocks.RED_SANDSTONE : n < 0.8 ? Blocks.TERRACOTTA : Blocks.ORANGE_TERRACOTTA).defaultBlockState();
            case JUPITER: {
                double sx = p.x + p.r * 0.8, sy = p.y - p.r * 0.3, sz = p.z + p.r * 0.5; // the Great Red Spot
                if ((x - sx) * (x - sx) + (y - sy) * (y - sy) + (z - sz) * (z - sz) < 90 && dx > 0)
                    return Blocks.RED_CONCRETE.defaultBlockState();
                int band = (int) Math.floor((dy + 2.5 * Math.sin(dx * 0.12) + 2.0 * Math.sin(dz * 0.09)) / 4.0);
                return JUP[Math.floorMod(band, JUP.length)].defaultBlockState();
            }
            case SATURN: {
                int band = (int) Math.floor((dy + 2.0 * Math.sin(dx * 0.1) + 1.5 * Math.sin(dz * 0.08)) / 4.0);
                return SAT[Math.floorMod(band, SAT.length)].defaultBlockState();
            }
            case URANUS:
                return (n < 0.8 ? Blocks.LIGHT_BLUE_CONCRETE : Blocks.CYAN_CONCRETE).defaultBlockState();
            case NEPTUNE: {
                int band = (int) Math.floor((dy + 2.0 * Math.sin(dx * 0.13)) / 4.0);
                return NEP[Math.floorMod(band, NEP.length)].defaultBlockState();
            }
            default: // the Moon
                return (n < 0.5 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.ANDESITE).defaultBlockState();
        }
    }

    // ------------------------------------------------------------------ the chunk generator

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        int minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight() - 1;
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Planet p : ALL) {
            if (earthGone && p.id.equals("earth")) continue;
            int reach = p.r + 3 + (p.ring ? (int) (p.r * 1.0) : 0); // a ring reaches out to about 2 radii
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
