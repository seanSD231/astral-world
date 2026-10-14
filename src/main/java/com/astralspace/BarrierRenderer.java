package com.astralspace;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Client only: the Astral Barrier. A huge, very translucent purple dome that curves down at the edges like the surface of a
 * planet, with a faint grid, fading out toward the horizon. Drawn in the Astral World and in the overworld sky (not in the
 * Nether/End dimensions). It hangs as high as the game can still draw it (the far clip plane is about 4 x render distance).
 * One crack per missing HP, in 6 solid looks (dark, muted, semi-translucent, with a dim edge).
 * Every crack always looks the same for everyone. The dome follows the player and the cracks repeat every TILE blocks.
 */
public final class BarrierRenderer {
    private static final float CURVE = 1600f;   // radius of the "planet": the barrier sags d*d/(2*CURVE) blocks at distance d
    private static final float CELL = 50f;      // size of one cell of the dome mesh
    private static final float TILE = 1024f;    // the crack pattern repeats every TILE blocks
    private static final float GRID = 128f;     // faint grid lines every GRID blocks
    private static final float PIECE = 24f;     // lines are cut into pieces this long so they follow the curve
    private static final float CRACK_CELL = 64f;   // every crack gets its own 64 x 64 square, so cracks can never overlap
    private static final int CELLS = (int) (TILE / CRACK_CELL);   // 16 x 16 squares per tile
    private static final int MAX_CRACKS = CELLS * CELLS;          // 256 per tile (about one per 4 x 4 chunks)
    private static final float CRACK_RANGE = 420f;  // cracks further than this from the player are not drawn
    private static final int[] CELL_ORDER = new int[MAX_CRACKS]; // the order the squares fill up in (shuffled, but the same for everyone)
    static {
        for (int i = 0; i < MAX_CRACKS; i++) CELL_ORDER[i] = i;
        Random r = new Random(0xA57A1CE11L);
        for (int i = MAX_CRACKS - 1; i > 0; i--) { int j = r.nextInt(i + 1); int t = CELL_ORDER[i]; CELL_ORDER[i] = CELL_ORDER[j]; CELL_ORDER[j] = t; }
    }
    private static final net.minecraft.resources.ResourceLocation ASTRAL =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("astral_world", "astral_world");

    private static final int BASE_ALPHA = 38, GRID_ALPHA = 60, CORE_ALPHA = 170, OUTLINE_ALPHA = 90;

    /**
     * Five crack looks: {angleJitter, lengthMin, lengthMax, radialsMin, radialsMax, rings, width,
     *                    outlineR, outlineG, outlineB, coreStartR, G, B, coreEndR, G, B}
     * Dark, muted colours (black, dark red, grey-white, navy). Drawn semi-translucent, like cracks in glass.
     */
    private static final float[][] VARIANTS = {
            {0.35f, 120, 260, 6, 10, 3, 2.4f, 40, 40, 48, 235, 235, 240, 175, 175, 185}, // pale white fading to light grey, dark edge
            {0.25f, 180, 320, 5, 8, 2, 1.8f, 90, 90, 100, 205, 205, 212, 120, 120, 130}, // long hairline, light grey to mid grey
            {0.55f, 100, 220, 6, 10, 3, 2.2f, 30, 30, 38, 245, 245, 248, 150, 150, 160}, // bright white to grey, jagged
            {0.60f, 80, 180, 8, 12, 3, 2.0f, 30, 30, 40, 225, 225, 232, 190, 190, 200},  // pale glass white, dark edge, very jagged
            {0.30f, 140, 260, 4, 6, 2, 3.0f, 55, 55, 65, 170, 170, 180, 95, 95, 105},    // thicker mid grey fading darker
    };

    private static final class Crack {
        int variant;
        float centerX, centerZ;
        final List<float[][]> lines = new ArrayList<>(); // polylines of {x, z, width, t}  (t = 0 at the impact, 1 at the tips)
        final List<float[]> shards = new ArrayList<>();  // faint glass fragments: triangles {x0, z0, x1, z1, x2, z2, brightness}
    }
    private static final Map<Integer, Crack> CRACKS = new HashMap<>();

    private static float cx, cz, baseY, half;

    /** Which vanilla render type draws the cracks / the dome. Shader packs (Photon) treat each one differently: try them with /astralgfx. */
    public static volatile int crackMode = 0, planeMode = 3;
    private static final String[] MODES = {"debugQuads (flat colour)", "leash (lit like an entity)", "gui (flat, translucent)", "lightning (additive glow)"};

    private static RenderType renderType(int mode) {
        return switch (mode) {
            case 1 -> RenderType.leash();
            case 2 -> RenderType.gui();
            case 3 -> RenderType.lightning();
            default -> RenderType.debugQuads();
        };
    }

    public static String describe() {
        return "cracks: " + crackMode + " = " + MODES[crackMode] + ",  dome: " + planeMode + " = " + MODES[planeMode];
    }

    private static java.nio.file.Path gfxFile() {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("astral_world_gfx.properties");
    }

    public static void loadGfx() {
        try {
            java.nio.file.Path f = gfxFile();
            if (!java.nio.file.Files.exists(f)) return;
            java.util.Properties p = new java.util.Properties();
            try (java.io.Reader r = java.nio.file.Files.newBufferedReader(f)) { p.load(r); }
            crackMode = Math.max(0, Math.min(3, Integer.parseInt(p.getProperty("cracks", "0"))));
            planeMode = Math.max(0, Math.min(3, Integer.parseInt(p.getProperty("plane", "3"))));
            AstralSky.skyMode = Math.max(0, Math.min(2, Integer.parseInt(p.getProperty("sky", "0"))));
        } catch (java.io.IOException | NumberFormatException ignored) { }
    }

    public static void saveGfx() {
        try {
            java.util.Properties p = new java.util.Properties();
            p.setProperty("cracks", String.valueOf(crackMode));
            p.setProperty("plane", String.valueOf(planeMode));
            p.setProperty("sky", String.valueOf(AstralSky.skyMode));
            try (java.io.Writer w = java.nio.file.Files.newBufferedWriter(gfxFile())) { p.store(w, "Astral barrier render types (change with /astralgfx)"); }
        } catch (java.io.IOException ignored) { }
    }

    private BarrierRenderer() {}

    public static void init() {
        loadGfx();
        NeoForge.EVENT_BUS.addListener(BarrierRenderer::onRender);
    }

    /** A point at distance `dist` along a polyline of {x, z, distanceFromCenter} points. */
    private static float[] along(List<float[]> pts, float dist) {
        for (int k = 1; k < pts.size(); k++) {
            float[] p0 = pts.get(k - 1), p1 = pts.get(k);
            if (dist <= p1[2]) {
                float f = (dist - p0[2]) / Math.max(0.001f, p1[2] - p0[2]);
                return new float[]{p0[0] + (p1[0] - p0[0]) * f, p0[1] + (p1[1] - p0[1]) * f};
            }
        }
        return null; // the radial is shorter than that
    }

    /** Cuts a polyline into pieces of at most PIECE blocks so it can follow the curve of the dome. */
    private static float[][] resample(float[][] pts) {
        List<float[]> out = new ArrayList<>();
        out.add(pts[0]);
        for (int k = 1; k < pts.length; k++) {
            float[] a = pts[k - 1], b = pts[k];
            float len = (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
            int n = Math.max(1, (int) Math.ceil(len / PIECE));
            for (int j = 1; j <= n; j++) {
                float f = j / (float) n;
                out.add(new float[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f, a[3] + (b[3] - a[3]) * f});
            }
        }
        return out.toArray(new float[0][]);
    }

    /** One jagged crack line from (x, z) heading `ang`: sharp kinks now and then, tapering to a point. */
    private static float[][] jagged(Random r, float x, float z, double ang, float length, float w0, float t0, float t1, float jitter) {
        List<float[]> pts = new ArrayList<>();
        float d = 0;
        pts.add(new float[]{x, z, w0, t0});
        while (d < length) {
            float step = Math.min(length - d, 8 + r.nextFloat() * 22);
            ang += r.nextGaussian() * jitter * 0.4;
            if (r.nextFloat() < 0.28f) ang += (r.nextBoolean() ? 1 : -1) * (0.45 + r.nextDouble() * 0.5); // a sharp kink, like glass
            x += (float) Math.cos(ang) * step; z += (float) Math.sin(ang) * step; d += step;
            float f = Math.min(1f, d / length);
            pts.add(new float[]{x, z, Math.max(0.12f, w0 * (float) Math.pow(1f - f, 1.2)), t0 + (t1 - t0) * f});
        }
        return pts.toArray(new float[0][]);
    }

    private static float[] pointAt(float[][] line, float f) { // roughly f (0..1) of the way along a polyline
        int i = Math.max(0, Math.min(line.length - 1, Math.round(f * (line.length - 1))));
        return line[i];
    }

    /**
     * A small crack: a short wandering line with a few tiny branches, squeezed into its own CRACK_CELL square (index i uses
     * square CELL_ORDER[i]). Because every crack stays inside its own square, two cracks never overlap.
     */
    private static Crack crack(int index) {
        return CRACKS.computeIfAbsent(index, i -> {
            Random r = new Random(0x9E3779B97F4A7C15L * (i + 1) ^ 0xA57A1L);
            Crack c = new Crack();
            c.variant = r.nextInt(VARIANTS.length);
            float[] v = VARIANTS[c.variant];
            float w0 = v[6] * 0.55f * (0.8f + r.nextFloat() * 0.5f);

            List<float[][]> raw = new ArrayList<>();
            double angle = r.nextDouble() * Math.PI * 2;
            float x = 0, z = 0;
            int n = 4 + r.nextInt(4);
            List<float[]> main = new ArrayList<>();
            main.add(new float[]{x, z, w0, 0f});
            for (int k = 0; k < n; k++) {
                float len = 5 + r.nextFloat() * 6;
                angle += r.nextGaussian() * 0.5;
                x += (float) Math.cos(angle) * len; z += (float) Math.sin(angle) * len;
                float t = (k + 1) / (float) n;
                main.add(new float[]{x, z, w0 * (1f - 0.4f * t), t});
                if (r.nextFloat() < 0.4f) { // a tiny branch
                    List<float[]> br = new ArrayList<>();
                    br.add(new float[]{x, z, w0 * 0.6f, t});
                    float bx = x, bz = z;
                    double ba = angle + (r.nextBoolean() ? 1 : -1) * (0.6 + r.nextDouble() * 0.6);
                    for (int q = 0; q < 3; q++) {
                        float bl = 3 + r.nextFloat() * 3;
                        ba += r.nextGaussian() * 0.35;
                        bx += (float) Math.cos(ba) * bl; bz += (float) Math.sin(ba) * bl;
                        br.add(new float[]{bx, bz, w0 * 0.6f * (1f - (q + 1) / 3.5f), Math.min(1f, t + 0.1f * (q + 1))});
                    }
                    raw.add(br.toArray(new float[0][]));
                }
            }
            raw.add(main.toArray(new float[0][]));

            // fit into the square (with a margin for the line width) and centre it there
            float minX = 1e9f, maxX = -1e9f, minZ = 1e9f, maxZ = -1e9f;
            for (float[][] line : raw) for (float[] p : line) {
                minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]); minZ = Math.min(minZ, p[1]); maxZ = Math.max(maxZ, p[1]);
            }
            float size = Math.max(0.001f, Math.max(maxX - minX, maxZ - minZ));
            float fit = Math.min(1f, (CRACK_CELL - 10f) / size);
            int cell = CELL_ORDER[i % MAX_CRACKS];
            c.centerX = (cell % CELLS + 0.5f) * CRACK_CELL;
            c.centerZ = (cell / CELLS + 0.5f) * CRACK_CELL;
            float mx = (minX + maxX) * 0.5f, mz = (minZ + maxZ) * 0.5f;
            for (float[][] line : raw) {
                float[][] out = new float[line.length][];
                for (int k = 0; k < line.length; k++) {
                    float[] p = line[k];
                    out[k] = new float[]{c.centerX + (p[0] - mx) * fit, c.centerZ + (p[1] - mz) * fit, p[2], p[3]};
                }
                c.lines.add(out);
            }
            return c;
        });
    }

    /**
     * One continuous ribbon along a polyline of {x, z, width, t}: each point gets a left and a right edge, and every pair of
     * neighbouring points makes one quad, so nothing overlaps (which matters because the ribbon is translucent).
     * widthScale multiplies the width, extra adds to it (the outline is the same ribbon, a little wider).
     */
    private static void ribbon(VertexConsumer vc, Matrix4f m, float yOff, float[][] pts, float ox, float oz,
                               float widthScale, float extra, float[] v, boolean outline, int alpha) {
        int n = pts.length;
        float[] lx = new float[n], lz = new float[n], rx = new float[n], rz = new float[n];
        int[][] col = new int[n][];
        for (int i = 0; i < n; i++) {
            float[] p = pts[i], pa = pts[Math.max(0, i - 1)], pb = pts[Math.min(n - 1, i + 1)];
            float dx = pb[0] - pa[0], dz = pb[1] - pa[1];
            float len = (float) Math.hypot(dx, dz);
            if (len < 0.0001f) { dx = 1; dz = 0; len = 1; }
            float nx = -dz / len, nz = dx / len;
            float h = (p[2] * widthScale + extra) * 0.5f;
            lx[i] = p[0] + ox + nx * h; lz[i] = p[1] + oz + nz * h;
            rx[i] = p[0] + ox - nx * h; rz[i] = p[1] + oz - nz * h;
            col[i] = outline ? new int[]{(int) v[7], (int) v[8], (int) v[9]} : mix(v, 10, 13, p[3]);
        }
        for (int i = 0; i + 1 < n; i++) {
            quad(vc, m, yOff, lx[i], lz[i], col[i], lx[i + 1], lz[i + 1], col[i + 1], rx[i + 1], rz[i + 1], col[i + 1], rx[i], rz[i], col[i], alpha);
        }
    }

    private static float yAt(float x, float z) {
        float dx = x - cx, dz = z - cz;
        return baseY - (dx * dx + dz * dz) / (2f * CURVE);
    }

    /** Fades to nothing toward the edge of the drawn area. */
    private static float fade(float x, float z) {
        float dx = x - cx, dz = z - cz;
        float d = (float) Math.sqrt(dx * dx + dz * dz) / half;
        float f = 1f - d * d;
        return f <= 0f ? 0f : f;
    }

    private static void vert(VertexConsumer vc, Matrix4f m, float x, float z, float yOff, int r, int g, int b, int a) {
        int alpha = Math.round(a * fade(x, z));
        vc.addVertex(m, x, yAt(x, z) + yOff, z).setColor(r, g, b, alpha).setLight(15728880);
    }

    /** One quad, drawn from both sides (the render types may cull back faces). */
    private static void quad(VertexConsumer vc, Matrix4f m, float yOff,
                             float x0, float z0, int[] c0, float x1, float z1, int[] c1,
                             float x2, float z2, int[] c2, float x3, float z3, int[] c3, int a) {
        vert(vc, m, x0, z0, yOff, c0[0], c0[1], c0[2], a); vert(vc, m, x1, z1, yOff, c1[0], c1[1], c1[2], a);
        vert(vc, m, x2, z2, yOff, c2[0], c2[1], c2[2], a); vert(vc, m, x3, z3, yOff, c3[0], c3[1], c3[2], a);
        vert(vc, m, x3, z3, yOff, c3[0], c3[1], c3[2], a); vert(vc, m, x2, z2, yOff, c2[0], c2[1], c2[2], a);
        vert(vc, m, x1, z1, yOff, c1[0], c1[1], c1[2], a); vert(vc, m, x0, z0, yOff, c0[0], c0[1], c0[2], a);
    }

    private static int[] mix(float[] v, int from, int to, float t) {
        return new int[]{(int) (v[from] + (v[to] - v[from]) * t), (int) (v[from + 1] + (v[to + 1] - v[from + 1]) * t),
                (int) (v[from + 2] + (v[to + 2] - v[from + 2]) * t)};
    }

    /**
     * A line of the given width between two points, lying on the curved barrier (cut into short pieces). Colour goes from
     * colour 0 to colour 1 along the line. `extend` pushes both ends out so neighbouring segments overlap with no gaps.
     */
    private static void segment(VertexConsumer vc, Matrix4f m, float yOff, float x0, float z0, float x1, float z1,
                                float width, int[] col0, int[] col1, int a, float extend) {
        float dx = x1 - x0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001f) return;
        float ux = dx / len, uz = dz / len;
        x0 -= ux * extend; z0 -= uz * extend; x1 += ux * extend; z1 += uz * extend;
        dx = x1 - x0; dz = z1 - z0; len += extend * 2;
        float px = -uz * width * 0.5f, pz = ux * width * 0.5f;
        int pieces = Math.max(1, (int) Math.ceil(len / PIECE));
        for (int k = 0; k < pieces; k++) {
            float t0 = k / (float) pieces, t1 = (k + 1) / (float) pieces;
            float ax = x0 + dx * t0, az = z0 + dz * t0, bx = x0 + dx * t1, bz = z0 + dz * t1;
            int[] ca = lerp(col0, col1, t0), cb = lerp(col0, col1, t1);
            quad(vc, m, yOff, ax + px, az + pz, ca, bx + px, bz + pz, cb, bx - px, bz - pz, cb, ax - px, az - pz, ca, a);
        }
    }

    private static int[] lerp(int[] a, int[] b, float t) {
        return new int[]{(int) (a[0] + (b[0] - a[0]) * t), (int) (a[1] + (b[1] - a[1]) * t), (int) (a[2] + (b[2] - a[2]) * t)};
    }

    private static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!Net.ClientState.barrierShow) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var dim = mc.level.dimension().location();
        float configured;
        if (dim.equals(ASTRAL)) configured = Net.ClientState.barrierY;
        else if (mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) configured = Net.ClientState.barrierYOverworld;
        else return;

        int missing = Math.max(0, Net.ClientState.barrierMax - Net.ClientState.barrierHp);
        int cracks = 0; // the share of the barrier that is missing, as a share of the squares that can hold a crack
        if (missing > 0 && Net.ClientState.barrierMax > 0)
            cracks = Math.min(MAX_CRACKS, Math.max(1, Math.round(MAX_CRACKS * (float) missing / Net.ClientState.barrierMax)));

        Vec3 cam = event.getCamera().getPosition();
        // as high as the game can still draw it: the far clip plane is about 4 x render distance away
        float far = mc.options.getEffectiveRenderDistance() * 16f * 4f;
        float maxHeight = far * 0.6f;
        baseY = Math.min(configured, (float) cam.y + maxHeight);
        baseY = Math.max(baseY, (float) cam.y + 60f); // always above the player
        half = Math.min(900f, far * 0.85f);
        float lineScale = Math.max(1f, (baseY - (float) cam.y) / 250f); // far away lines are drawn thicker so they stay visible

        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // The barrier is far away, past the normal fog: switch fog off while drawing it.
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(1.0E7f);
        RenderSystem.setShaderFogEnd(2.0E7f);

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();

        cx = (float) cam.x; cz = (float) cam.z;
        float minX = cx - half, maxX = cx + half, minZ = cz - half, maxZ = cz + half;

        // 1) the dome and its grid (additive, glows a little against the dark sky)
        RenderType planeType = renderType(planeMode);
        VertexConsumer vc = buffers.getBuffer(planeType);
        int[] purple = {170, 90, 255}, lilac = {210, 150, 255};
        for (float x = minX; x < maxX; x += CELL)
            for (float z = minZ; z < maxZ; z += CELL)
                quad(vc, m, 0f, x, z, purple, x + CELL, z, purple, x + CELL, z + CELL, purple, x, z + CELL, purple, BASE_ALPHA);
        for (float gx = (float) Math.floor(minX / GRID) * GRID; gx <= maxX; gx += GRID)
            segment(vc, m, 0f, gx, minZ, gx, maxZ, 1.6f * lineScale, lilac, lilac, GRID_ALPHA, 0f);
        for (float gz = (float) Math.floor(minZ / GRID) * GRID; gz <= maxZ; gz += GRID)
            segment(vc, m, 0f, minX, gz, maxX, gz, 1.6f * lineScale, lilac, lilac, GRID_ALPHA, 0f);
        buffers.endBatch(planeType);

        // 2) cracks: semi-translucent ribbons, one per missing HP, repeated in every tile around the player
        if (cracks > 0) {
            RenderType crackType = renderType(crackMode);
            VertexConsumer cv = buffers.getBuffer(crackType);
            int tx0 = (int) Math.floor(minX / TILE), tx1 = (int) Math.floor(maxX / TILE);
            int tz0 = (int) Math.floor(minZ / TILE), tz1 = (int) Math.floor(maxZ / TILE);
            float camX = (float) cam.x, camZ = (float) cam.z;
            for (int i = 0; i < cracks; i++) {
                Crack c = crack(i);
                float[] v = VARIANTS[c.variant];
                for (int tx = tx0; tx <= tx1; tx++) {
                    for (int tz = tz0; tz <= tz1; tz++) {
                        float ox = tx * TILE, oz = tz * TILE;
                        float dx = c.centerX + ox - camX, dz = c.centerZ + oz - camZ;
                        if (dx * dx + dz * dz > CRACK_RANGE * CRACK_RANGE) continue; // only the few nearby cracks are drawn
                        for (float[][] line : c.lines) {
                            ribbon(cv, m, -0.4f, line, ox, oz, lineScale, 0.8f * lineScale, v, true, OUTLINE_ALPHA); // dim edge underneath
                            ribbon(cv, m, -0.9f, line, ox, oz, lineScale, 0f, v, false, CORE_ALPHA);                 // the crack itself, semi-translucent
                        }
                    }
                }
            }
            buffers.endBatch(crackType);
        }

        ps.popPose();
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
    }
}
