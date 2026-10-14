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
 * One crack per missing HP, in 6 solid looks (dark gradients in black, red, blue and white, with an outline).
 * Every crack always looks the same for everyone. The dome follows the player and the cracks repeat every TILE blocks.
 */
public final class BarrierRenderer {
    private static final float CURVE = 1600f;   // radius of the "planet": the barrier sags d*d/(2*CURVE) blocks at distance d
    private static final float CELL = 50f;      // size of one cell of the dome mesh
    private static final float TILE = 1024f;    // the crack pattern repeats every TILE blocks
    private static final float GRID = 128f;     // faint grid lines every GRID blocks
    private static final float PIECE = 24f;     // lines are cut into pieces this long so they follow the curve
    private static final int MAX_CRACKS = 120;
    private static final net.minecraft.resources.ResourceLocation ASTRAL =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("astral_space", "astral_space");

    private static final int BASE_ALPHA = 38, GRID_ALPHA = 60;

    /**
     * Six crack looks: {angleJitter, segLenMin, segLenMax, segMin, segMax, branchChance, width,
     *                   outlineR, outlineG, outlineB, coreStartR, G, B, coreEndR, G, B}
     * All cracks are solid (no gaps). Colours: black, red, blue, white, as dark gradients only.
     */
    private static final float[][] VARIANTS = {
            {0.45f, 30, 70, 8, 13, 0.35f, 3.2f, 170, 20, 30, 0, 0, 0, 40, 0, 0},            // black, thin red outline
            {0.25f, 50, 110, 8, 12, 0.10f, 2.2f, 225, 225, 235, 0, 0, 0, 100, 0, 10},       // long black to dark red, white outline
            {0.60f, 15, 35, 10, 16, 0.60f, 4.0f, 55, 95, 225, 0, 0, 0, 0, 8, 70},           // black to deep blue, blue outline, many branches
            {0.80f, 20, 50, 8, 12, 0.40f, 3.0f, 170, 20, 30, 120, 0, 10, 0, 0, 0},          // dark red to black, jagged
            {0.90f, 14, 34, 10, 16, 0.45f, 2.6f, 15, 15, 25, 238, 238, 248, 238, 238, 248}, // solid white with a black outline, very jagged
            {0.30f, 40, 80, 4, 7, 0.20f, 6.0f, 55, 95, 225, 10, 30, 135, 0, 0, 0},          // wide deep blue to black, blue outline
    };

    private static final class Crack {
        int variant;
        final List<float[]> segs = new ArrayList<>(); // {x0, z0, x1, z1, width, t0, t1}  (t = position along the crack, 0..1)
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
        } catch (java.io.IOException | NumberFormatException ignored) { }
    }

    public static void saveGfx() {
        try {
            java.util.Properties p = new java.util.Properties();
            p.setProperty("cracks", String.valueOf(crackMode));
            p.setProperty("plane", String.valueOf(planeMode));
            try (java.io.Writer w = java.nio.file.Files.newBufferedWriter(gfxFile())) { p.store(w, "Astral barrier render types (change with /astralgfx)"); }
        } catch (java.io.IOException ignored) { }
    }

    private BarrierRenderer() {}

    public static void init() {
        loadGfx();
        NeoForge.EVENT_BUS.addListener(BarrierRenderer::onRender);
    }

    private static Crack crack(int index) {
        return CRACKS.computeIfAbsent(index, i -> {
            Random r = new Random(0x9E3779B97F4A7C15L * (i + 1) ^ 0xA57A1L);
            Crack c = new Crack();
            c.variant = r.nextInt(VARIANTS.length);
            float[] v = VARIANTS[c.variant];
            float widthMul = 0.8f + r.nextFloat() * 0.5f;
            float scale = 0.8f + r.nextFloat() * 0.5f;
            float x = r.nextFloat() * TILE, z = r.nextFloat() * TILE;
            double angle = r.nextDouble() * Math.PI * 2;
            int n = (int) v[3] + r.nextInt((int) (v[4] - v[3]) + 1);
            for (int k = 0; k < n; k++) {
                float len = (v[1] + r.nextFloat() * (v[2] - v[1])) * scale;
                angle += r.nextGaussian() * v[0];
                float nx = x + (float) Math.cos(angle) * len, nz = z + (float) Math.sin(angle) * len;
                float t0 = k / (float) n, t1 = (k + 1) / (float) n;
                c.segs.add(new float[]{x, z, nx, nz, v[6] * widthMul, t0, t1});
                if (r.nextFloat() < v[5]) { // a branch
                    float bx = nx, bz = nz;
                    double ba = angle + (r.nextBoolean() ? 1 : -1) * (0.6 + r.nextDouble() * 0.6);
                    int bn = 2 + r.nextInt(3);
                    for (int b = 0; b < bn; b++) {
                        float bl = (v[1] * 0.6f + r.nextFloat() * v[2] * 0.4f) * scale;
                        ba += r.nextGaussian() * v[0] * 0.8;
                        float ex = bx + (float) Math.cos(ba) * bl, ez = bz + (float) Math.sin(ba) * bl;
                        c.segs.add(new float[]{bx, bz, ex, ez, v[6] * widthMul * 0.6f, t1, Math.min(1f, t1 + 0.08f)});
                        bx = ex; bz = ez;
                    }
                }
                x = nx; z = nz;
            }
            return c;
        });
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
        int cracks = Math.min(missing, MAX_CRACKS);

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

        // 2) cracks: solid, one per missing HP, repeated in every tile around the player
        if (cracks > 0) {
            RenderType crackType = renderType(crackMode);
            VertexConsumer cv = buffers.getBuffer(crackType);
            int tx0 = (int) Math.floor(minX / TILE), tx1 = (int) Math.floor(maxX / TILE);
            int tz0 = (int) Math.floor(minZ / TILE), tz1 = (int) Math.floor(maxZ / TILE);
            for (int i = 0; i < cracks; i++) {
                Crack c = crack(i);
                float[] v = VARIANTS[c.variant];
                int[] outline = {(int) v[7], (int) v[8], (int) v[9]};
                for (float[] s : c.segs) {
                    int[] c0 = mix(v, 10, 13, s[5]), c1 = mix(v, 10, 13, s[6]);
                    float w = s[4] * lineScale;
                    for (int tx = tx0; tx <= tx1; tx++) {
                        for (int tz = tz0; tz <= tz1; tz++) {
                            float ox = tx * TILE, oz = tz * TILE;
                            float x0 = s[0] + ox, z0 = s[1] + oz, x1 = s[2] + ox, z1 = s[3] + oz;
                            if (x0 < minX - 100 || x0 > maxX + 100 || z0 < minZ - 100 || z0 > maxZ + 100) continue;
                            segment(cv, m, -0.4f, x0, z0, x1, z1, w * 1.8f, outline, outline, 255, w * 0.9f); // outline underneath
                            segment(cv, m, -0.9f, x0, z0, x1, z1, w, c0, c1, 255, w * 0.5f);                  // the solid core on top
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
