package com.astralspace;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
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

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The landscape of each planet's own dimension, one huge biome shaped like the real planet:
 * Mercury (craters), Venus (lava, volcanoes), Mars (red canyons, a great shield volcano), Jupiter (banded cloud
 * tops and the Great Red Spot), Saturn (pale bands, its ring overhead), Uranus (pale ice and spikes), Neptune (dark
 * blue storm seas and the Great Dark Spot). Anything else (Earth) is a plain meadow.
 * Around the arrival point the land is flattened into a wide disc so the planet bosses have room to fight.
 */
public final class PlanetGenerator extends ChunkGenerator {
    public static final MapCodec<PlanetGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            com.mojang.serialization.Codec.STRING.fieldOf("planet").forGetter(g -> g.planet)).apply(i, PlanetGenerator::new));

    public static final int BASE = 64;
    /** The arena is a round grassy field of this radius; beyond it there is only sky. */
    public static final double FIELD_R = EndGame.ARENA_R + 70;
    /** The temple: a raised stone platform this wide (the arena plus a rim), standing DROP blocks above the grass field around it. */
    public static final double PLAT_R = EndGame.ARENA_R + 13;
    public static final int DROP = 20;
    public static final double GRASS_R = 185;   // the tree's grass patch: wide enough that none of its roots reach the stone

    private final String planet;

    public PlanetGenerator(BiomeSource source, String planet) { super(source); this.planet = planet; }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    /** Where to stand on arrival (the middle of the flat disc). */
    public static double spawnY() { return BASE + 1.0; }

    // ------------------------------------------------------------------ noise

    private static double hash(int a, int b, int c) {
        long h = a * 73856093L ^ b * 19349663L ^ c * 83492791L;
        h = (h ^ (h >>> 13)) * 0x5DEECE66DL;
        return ((h ^ (h >>> 17)) & 0xFFFFF) / (double) 0xFFFFF;
    }

    private static double vn(double x, double z, int seed) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, iz, seed), b = hash(ix + 1, iz, seed), c = hash(ix, iz + 1, seed), d = hash(ix + 1, iz + 1, seed);
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }

    /** 0..1 */
    private static double fbm(double x, double z, double scale, int seed, int octaves) {
        double s = 0, amp = 1, tot = 0, f = 1.0 / scale;
        for (int o = 0; o < octaves; o++) { s += vn(x * f, z * f, seed + o * 31) * amp; tot += amp; amp *= 0.5; f *= 2; }
        return s / tot;
    }

    private static double smooth(double a, double b, double v) {
        double t = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------------ shapes

    private static double craters(int x, int z) {
        int cs = 56, cx0 = Math.floorDiv(x, cs), cz0 = Math.floorDiv(z, cs);
        double add = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int ix = cx0 + dx, iz = cz0 + dz;
            if (hash(ix, iz, 11) > 0.72) continue;
            double px = (ix + hash(ix, iz, 12)) * cs, pz = (iz + hash(ix, iz, 13)) * cs;
            double r = 8 + hash(ix, iz, 14) * 15;
            double q = Math.hypot(x - px, z - pz) / r;
            if (q < 1) add -= (1 - q * q) * r * 0.5;
            else if (q < 1.3) add += (1.3 - q) / 0.3 * 3.0;
        }
        return add;
    }

    private static double cones(int x, int z, int cs, double chance, double rMin, double rMax, double height, int seed) {
        int cx0 = Math.floorDiv(x, cs), cz0 = Math.floorDiv(z, cs);
        double add = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int ix = cx0 + dx, iz = cz0 + dz;
            if (hash(ix, iz, seed) > chance) continue;
            double px = (ix + 0.2 + hash(ix, iz, seed + 1) * 0.6) * cs, pz = (iz + 0.2 + hash(ix, iz, seed + 2) * 0.6) * cs;
            double r = rMin + hash(ix, iz, seed + 3) * (rMax - rMin);
            double q = Math.hypot(x - px, z - pz) / r;
            if (q < 1) add += (1 - q) * height * (0.6 + hash(ix, iz, seed + 4) * 0.8);
        }
        return add;
    }

    /** The surface height of a planet at (x, z). */
    static int height(String id, int x, int z) {
        double h;
        switch (id) {
            case "mercury" -> h = BASE + (fbm(x, z, 90, 1, 3) - 0.5) * 16 + craters(x, z);
            case "venus" -> {
                h = BASE + (fbm(x, z, 110, 2, 4) - 0.5) * 24;
                double lake = fbm(x, z, 140, 3, 2);
                if (lake < 0.38) h -= (0.38 - lake) / 0.38 * 22;               // lava basins
                h += cones(x, z, 190, 0.45, 26, 40, 34, 20);                    // volcanoes
            }
            case "mars" -> {
                h = BASE + 2 + (fbm(x, z, 100, 4, 4) - 0.5) * 26;
                double rift = Math.abs(fbm(x + 90 * Math.sin(z * 0.004), z, 260, 5, 2) - 0.5);
                if (rift < 0.035) h -= (0.035 - rift) / 0.035 * 24;             // Valles Marineris
                double d = Math.hypot(x - 340, z - 220);
                h += Math.min(1, Math.max(0, 1 - d / 190)) * 62;                // Olympus Mons
                if (d < 38) h -= (1 - d / 38) * 10;                             // its caldera
            }
            case "jupiter" -> h = BASE + (fbm(x, z, 60, 6, 3) - 0.5) * 9 + 3 * Math.sin(z * 0.035);
            case "saturn" -> h = BASE + (fbm(x, z, 80, 7, 3) - 0.5) * 6 + 2 * Math.sin(z * 0.03);
            case "uranus" -> {
                h = BASE + (fbm(x, z, 120, 8, 3) - 0.5) * 12;
                h += cones(x, z, 44, 0.3, 3, 7, 26, 30);                        // ice spikes
            }
            case "neptune" -> {
                double w = Math.abs(fbm(x, z, 70, 9, 4) - 0.5) * 2;             // ridged storm waves
                h = BASE - 4 + (1 - w) * 22;
            }
            case "raid" -> {   // the platform is perfectly level (the fight is built on it); the field around it lies lower, with a stair down at each gateway
                double rr = Math.hypot(x, z);
                h = rr <= PLAT_R ? BASE : (gateway(x, z) ? BASE - Math.min(DROP, 1 + (int) ((rr - PLAT_R) / 2)) : BASE - DROP);
            }
            default -> h = BASE + (fbm(x, z, 120, 10, 3) - 0.5) * 6;
        }
        double dist = Math.hypot(x, z);
        double f = 1 - smooth(26, 64, dist);                                   // flat disc around the arrival point
        if (!id.equals("raid")) h = h + (BASE - h) * f;
        return (int) Math.max(8, Math.min(230, Math.round(h)));
    }

    /** The four gateways of the temple: a strip about 18 wide along each axis. */
    static boolean gateway(double x, double z) { return Math.abs(x) < 9 || Math.abs(z) < 9; }

    /** Stone bricks, mossy and cracked, mixed. */
    private static Block brickMix(int x, int z) {
        double b = hash(x, z, 11);
        return b < 0.36 ? Blocks.STONE_BRICKS : b < 0.68 ? Blocks.MOSSY_STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS;
    }

    /** The top block of the temple floor at (x, z). */
    private static Block raidTop(int x, int z) {
        double R = Math.hypot(x, z), b = hash(x, z, 9);
        if (R < 180) return hash(x / 2, z / 2, 0) < 0.12 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK;
        if (R < GRASS_R) return b < 0.7 ? Blocks.COARSE_DIRT : Blocks.PODZOL;
        if (R < 205) {   // the altar ring: dark polished stone with a gold line
            if (Math.abs(R - 195) < 1.2) return Blocks.CHISELED_STONE_BRICKS;
            if (Math.abs(R - 190) < 0.6 || Math.abs(R - 200) < 0.6) return Blocks.POLISHED_ANDESITE;
            return brickMix(x, z);
        }
        for (int k = 0; k < 8; k++) {   // eight pale paths, chequered, from the altar ring to the rim
            double ang = k * Math.PI / 4, ca = x * Math.cos(ang) + z * Math.sin(ang), sa = -x * Math.sin(ang) + z * Math.cos(ang);
            if (Math.abs(sa) < 4.2 && ca > 205 && ca < PLAT_R + 0.5) {
                if (Math.abs(sa) > 3.4) return Blocks.STONE_BRICKS;
                return (Math.floorMod((int) Math.floor(ca / 4) + (int) Math.floor(sa / 4), 2) == 0) ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE;
            }
        }
        for (double rr : new double[]{245, 300}) {   // dark rings with glowing glyph dots
            if (Math.abs(R - rr) < 2.6) {
                if (Math.abs(R - rr) < 0.9 && Math.floorMod((int) Math.floor(Math.atan2(z, x) * R / 5), 2) == 0) return Blocks.CHISELED_STONE_BRICKS;
                return b < 0.5 ? Blocks.STONE_BRICKS : Blocks.POLISHED_ANDESITE;
            }
        }
        if (R < 345) return brickMix(x, z);
        return brickMix(x, z);   // the rim
    }

    // ------------------------------------------------------------------ materials

    private static final Block[] JUP = {Blocks.ORANGE_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.WHITE_CONCRETE,
            Blocks.ORANGE_CONCRETE, Blocks.TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.WHITE_TERRACOTTA};
    private static final Block[] SAT = {Blocks.SMOOTH_SANDSTONE, Blocks.YELLOW_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.SANDSTONE, Blocks.YELLOW_CONCRETE_POWDER};
    private static final Block[] NEP = {Blocks.BLUE_CONCRETE, Blocks.BLUE_TERRACOTTA, Blocks.LIGHT_BLUE_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.CYAN_TERRACOTTA, Blocks.BLUE_CONCRETE, Blocks.PURPLE_CONCRETE};
    private static final Block[] STRATA = {Blocks.RED_SANDSTONE, Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.WHITE_TERRACOTTA};

    /** The block at depth `depth` below the surface (0 = the top block) of a column. */
    private static Block block(String id, int x, int z, int y, int h) {
        int depth = h - y;
        double n = hash(x / 2, z / 2, y / 2);
        switch (id) {
            case "mercury":
                if (depth == 0) return n < 0.4 ? Blocks.ANDESITE : n < 0.7 ? Blocks.STONE : n < 0.9 ? Blocks.COBBLESTONE : Blocks.BASALT;
                return depth < 4 ? Blocks.STONE : Blocks.DEEPSLATE;
            case "venus":
                if (h <= 63 && depth < 3) return Blocks.MAGMA_BLOCK;
                if (depth == 0) return n < 0.4 ? Blocks.YELLOW_TERRACOTTA : n < 0.75 ? Blocks.ORANGE_TERRACOTTA : Blocks.SANDSTONE;
                return depth < 5 ? Blocks.TERRACOTTA : depth < 12 ? Blocks.BASALT : Blocks.BLACKSTONE;
            case "mars":
                if (depth == 0) return n < 0.8 ? Blocks.RED_SAND : Blocks.RED_SANDSTONE;
                return STRATA[Math.floorMod(y / 3 + (int) (hash(0, 0, 77) * 7), STRATA.length)];
            case "jupiter": {
                double dx = (x - 300) / 95.0, dz = (z - 20) / 55.0;
                double q = Math.sqrt(dx * dx + dz * dz);
                if (q < 1) { // the Great Red Spot
                    double swirl = q * 6 + Math.atan2(dz, dx) * 1.5;
                    int k = Math.floorMod((int) Math.floor(swirl), 3);
                    return k == 0 ? Blocks.RED_CONCRETE : k == 1 ? Blocks.RED_TERRACOTTA : Blocks.ORANGE_TERRACOTTA;
                }
                double warp = 14 * Math.sin(x * 0.02) + 9 * Math.sin(x * 0.05 + z * 0.01) + 20 * (fbm(x, z, 70, 40, 2) - 0.5);
                return JUP[Math.floorMod((int) Math.floor((z + warp) / 22.0), JUP.length)];
            }
            case "saturn": {
                double warp = 10 * Math.sin(x * 0.02) + 14 * (fbm(x, z, 90, 41, 2) - 0.5);
                return SAT[Math.floorMod((int) Math.floor((z + warp) / 20.0), SAT.length)];
            }
            case "uranus":
                if (depth == 0) return n < 0.7 ? Blocks.SNOW_BLOCK : n < 0.9 ? Blocks.LIGHT_BLUE_CONCRETE_POWDER : Blocks.PACKED_ICE;
                return depth < 5 ? Blocks.PACKED_ICE : Blocks.BLUE_ICE;
            case "neptune": {
                double dx = (x + 300) / 90.0, dz = (z - 120) / 60.0;
                if (dx * dx + dz * dz < 1) return depth == 0 ? Blocks.BLACK_CONCRETE : Blocks.BLUE_TERRACOTTA; // the Great Dark Spot
                double warp = 12 * Math.sin(x * 0.03) + 16 * (fbm(x, z, 60, 42, 2) - 0.5);
                return NEP[Math.floorMod((int) Math.floor((z + warp) / 18.0), NEP.length)];
            }
            case "raid": {
                double r = Math.hypot(x, z);
                if (r <= PLAT_R) {
                    if (depth == 0) return raidTop(x, z);
                    if (r > PLAT_R - 3 && y >= BASE - DROP) return brickMix(x, z);   // the cliff face under the rim
                    return depth < 4 ? Blocks.DIRT : Blocks.STONE;
                }
                if (depth == 0) return gateway(x, z) ? (hash(x, z, 12) < 0.5 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS) : (n < 0.12 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK);   // the stairs down at the gateways
                return depth < 4 ? Blocks.DIRT : Blocks.STONE;
            }
            default:
                if (depth == 0) return Blocks.GRASS_BLOCK;
                return depth < 4 ? Blocks.DIRT : Blocks.STONE;
        }
    }

    // ------------------------------------------------------------------ the chunk generator

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState(), lava = Blocks.LAVA.defaultBlockState();
        boolean saturn = planet.equals("saturn");
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                int h = height(planet, x, z);
                if (planet.equals("raid") && Math.hypot(x, z) > FIELD_R) continue;   // the field is a round island in the night
                chunk.setBlockState(pos.set(x, 0, z), bedrock, false);
                boolean banded = planet.equals("jupiter") || planet.equals("saturn") || planet.equals("neptune");
                for (int y = 1; y <= h; y++) {
                    Block b = banded ? block(planet, x, z, h, h) : block(planet, x, z, y, h);
                    chunk.setBlockState(pos.set(x, y, z), b.defaultBlockState(), false);
                }
                if (planet.equals("raid")) decorateRaid(chunk, pos, x, z, h);
                if (planet.equals("venus")) for (int y = h + 1; y <= 62; y++) chunk.setBlockState(pos.set(x, y, z), lava, false);
                if (saturn && Math.abs(z + 300) >= 150 && Math.abs(z + 300) <= 230 && hash(x, z, 5) < 0.85) { // its ring, high overhead
                    Block r = hash(z, x, 6) < 0.6 ? Blocks.PACKED_ICE : hash(x, 2, z) < 0.5 ? Blocks.CALCITE : Blocks.LIGHT_GRAY_CONCRETE;
                    chunk.setBlockState(pos.set(x, 200, z), r.defaultBlockState(), false);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }


    // ------------------------------------------------------------------ the ruined church: the whole raised platform is its floor

    /** Inside the walls of the church (the whole raised platform), `k` blocks in from the wall. */
    public static boolean churchInside(double x, double z, int k) { return Math.hypot(x, z) < PLAT_R - 7 - k; }

    private static final double ROOF_IN = 215;   // the roof only covers the outer ring: the middle fell in long ago (and the tree needs the room)

    /** Height of the roof's underside above the floor at radius r. */
    private static int roofH(double r) { return (int) Math.round(80 + (PLAT_R - 3 - r) * 0.5); }

    private static Block ruinBlock(int x, int y, int z) {
        double b = hash(x * 3 + y, z, 13);
        return b < 0.40 ? Blocks.STONE_BRICKS : b < 0.68 ? Blocks.MOSSY_STONE_BRICKS : b < 0.88 ? Blocks.CRACKED_STONE_BRICKS : b < 0.95 ? Blocks.COBBLESTONE : Blocks.MOSSY_COBBLESTONE;
    }

    /** True where the roof has fallen in: about 45%, more in the north. */
    private static boolean roofHole(int x, int z) {
        double n = vn(x / 55.0, z / 55.0, 91) * 0.88 + vn(x / 5.0, z / 5.0, 92) * 0.12 - 0.08 * z / 350.0;
        return n > 0.52;
    }

    /** One column of the church: a round outer wall with pointed windows, four arched gates, buttresses, a ring of pillars, and a half-fallen roof with ribs over the outer ring. */
    private static void churchColumn(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int h) {
        double r = Math.hypot(x, z);
        if (r < 190 || r > PLAT_R + 1) return;
        double ang = Math.atan2(z, x);
        int top = 0;
        // ---- the outer wall, with the gates cut through it
        boolean wall = r >= PLAT_R - 7 && r <= PLAT_R - 1.5;
        int wh = 0;
        if (wall) {
            double keep = vn(x / 40.0 + 3, z / 40.0 - 7, 72), bro = vn(x / 5.0, z / 5.0, 73);
            wh = keep > 0.38 ? 82 : 20 + (int) (60 * Math.pow(bro, 1.3));
            top = Math.max(top, wh);
        }
        double u = ang * (PLAT_R - 4);                              // distance along the wall
        double gc = Math.min(Math.abs(x) < 9.5 ? Math.abs(x) : 99, Math.abs(z) < 9.5 ? Math.abs(z) : 99);   // how far from the middle of a gate (99 = not at a gate)
        // ---- buttresses against the inside of the wall
        if (r >= PLAT_R - 14 && r < PLAT_R - 7 && gc > 15) {
            int k = Math.floorMod((int) Math.floor(u), 26);
            if (k <= 4) { double cell = hash((int) Math.floor(u / 26), 1, 76); top = Math.max(top, (int) ((62 - (PLAT_R - 7 - r) * 7) * (0.35 + 0.65 * cell))); }
        }
        // ---- the ring of great pillars and the broken inner ring
        top = Math.max(top, pillarTop(x, z, r, ang, 300, 48, 2.2, 1));
        top = Math.max(top, pillarTop(x, z, r, ang, 224, 14, 2.8, 2));
        // ---- the roof over the outer ring: a shell three blocks thick, ribs, holes
        int roofLo = -1, roofHi = -1;
        if (r >= ROOF_IN && r <= PLAT_R - 1.5) {
            int rh = roofH(r);
            boolean rib = Math.floorMod((int) Math.floor(ang * 180 / Math.PI * 4 + 0.5), 60) <= 1 || Math.abs(r - 282) < 1.6;   // radial ribs every 15 degrees and a hoop
            boolean ribBreak = vn(x / 18.0, z / 18.0, 93) < 0.22;
            boolean hole = roofHole(x, z);
            if (rib && !ribBreak) { roofLo = rh - 3; roofHi = rh + 2; }
            else if (!hole) { roofLo = rh; roofHi = rh + 2; }
            // the roof is held by the wall: right at the wall it stays
            if (r > PLAT_R - 9 && wh > 70) { roofLo = rh - 2; roofHi = rh + 2; }
        }
        for (int y = 1; y <= top; y++) {
            if (wall && y <= wh) {
                if (gc < 9.5) {   // a pointed gate arch
                    double half = 9 - Math.max(0, y - 28) * 0.42;
                    if (gc < half) continue;
                }
                double m = ((u % 24) + 24) % 24;
                double half = y < 44 ? 3.5 : 3.5 - (y - 44) * 0.22;   // a tall pointed window
                if (gc > 15 && y >= 14 && y <= 62 && Math.abs(m - 12) < half) continue;
            }
            if (h + y > 250) break;
            chunk.setBlockState(pos.set(x, h + y, z), ruinBlock(x, y, z).defaultBlockState(), false);
        }
        if (roofLo >= 0) for (int y = roofLo; y <= roofHi; y++) if (h + y <= 250) chunk.setBlockState(pos.set(x, h + y, z), ruinBlock(x + y, y, z).defaultBlockState(), false);
        // ---- light: invisible lamps so the covered ring is not pitch dark
        if (r < PLAT_R - 8 && Math.floorMod(x, 22) == 0 && Math.floorMod(z, 22) == 0) {
            BlockState lamp = Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 13);
            chunk.setBlockState(pos.set(x, h + 30, z), lamp, false);
            chunk.setBlockState(pos.set(x, h + 70, z), lamp, false);
        }
        // ---- rubble on the floor, thicker where the roof is gone
        if (top == 0 && r >= 207 && r < PLAT_R - 9) {
            int cx = Math.floorDiv(x, 6), cz = Math.floorDiv(z, 6);
            if (hash(cx, cz, 9) < 0.09) {
                double d = Math.hypot(x - (cx * 6 + 3), z - (cz * 6 + 3));
                int rh = (int) Math.round(3.2 * (1 - d / 3.2) * (0.6 + 0.8 * hash(cx, cz, 10)));
                for (int y = 1; y <= rh; y++) chunk.setBlockState(pos.set(x, h + y, z), ruinBlock(x, y, z).defaultBlockState(), false);
            }
        }
    }

    /** Height of the pillar ring at radius `ring` (n pillars) at this column, 0 if none: a few still hold up the roof, most are broken. */
    private static int pillarTop(int x, int z, double r, double ang, double ring, int n, double rad, int seed) {
        if (Math.abs(r - ring) > rad + 2) return 0;
        double step = Math.PI * 2 / n;
        int k = (int) Math.round(ang / step);
        double a = k * step, px = ring * Math.cos(a), pz = ring * Math.sin(a);
        if (Math.max(Math.abs(x - px), Math.abs(z - pz)) > rad) return 0;
        double q = hash(Math.floorMod(k, n), seed, 5);
        int full = ring > 250 ? roofH(ring) : 150;
        return q < 0.28 ? full : q < 0.7 ? (int) (full * (0.25 + 0.5 * hash(k, seed, 6))) : 3 + (int) (hash(k, seed, 7) * 9);
    }

    /** The field: grass and flowers (the old ring of broken pillars at the edge is gone). The middle is clear (the tree stands there). */
    private static void decorateRaid(ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int z, int h) {
        double rr = Math.hypot(x, z);
        if (rr <= PLAT_R + 1) churchColumn(chunk, pos, x, z, h);
        // grass and flowers where the ground is grass
        if (hash(x, 3, z) < 0.35 && chunk.getBlockState(pos.set(x, h, z)).is(Blocks.GRASS_BLOCK)) {
            double q = hash(x, 4, z);
            BlockState p = q < 0.80 ? Blocks.SHORT_GRASS.defaultBlockState() : q < 0.88 ? Blocks.FERN.defaultBlockState()
                    : q < 0.92 ? Blocks.DANDELION.defaultBlockState() : q < 0.96 ? Blocks.AZURE_BLUET.defaultBlockState() : Blocks.CORNFLOWER.defaultBlockState();
            chunk.setBlockState(pos.set(x, h + 1, z), p, false);
        }
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes, StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) { }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) { }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) { }

    @Override
    public int getGenDepth() { return 256; }

    @Override
    public int getSeaLevel() { return 62; }

    @Override
    public int getMinY() { return 0; }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) { return height(planet, x, z) + 1; }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        return new NoiseColumn(level.getMinBuildHeight(), new BlockState[0]);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) { }
}
