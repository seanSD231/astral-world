package com.astralspace;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;

/**
 * Client only: the Mother Tree's transformation cutscene. Takes over the camera (a camera entity flies a scripted path
 * around the tree), hides the HUD, draws letterbox bars and subtitles, and drives the tree's wither -> rot -> flesh look.
 * Hold jump to skip.
 */
public final class TreeCutscene {
    /** Seconds into the scene when the healthy model is swapped for the fleshy one. */
    public static final double SWAP = 7.0;
    private static boolean running = false;
    private static ArmorStand cam;
    private static double tx, ty, tz;           // the tree
    private static double px, py, pz;           // camera position (eye)
    private static float yaw, pitch;
    private static int skipHeld = 0, tick = 0;
    private static boolean skipped = false, swapped = false;
    private static final java.util.Random R = new java.util.Random();

    private TreeCutscene() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(TreeCutscene::onTick);
        NeoForge.EVENT_BUS.addListener(TreeCutscene::onGuiLayer);
        NeoForge.EVENT_BUS.addListener(TreeCutscene::onGui);
        NeoForge.EVENT_BUS.addListener(TreeCutscene::onMove);
        NeoForge.EVENT_BUS.addListener(TreeCutscene::onInteract);
    }

    // ------------------------------------------------------------------ state

    /** True while a scene is playing for this player. */
    public static boolean active() {
        if (Net.ClientState.cutsceneKind != 1 || skipped) return false;
        long el = System.currentTimeMillis() - Net.ClientState.cutsceneStart;
        return el >= 0 && el < Net.ClientState.cutsceneTicks * 50L;
    }

    public static double seconds() { return (System.currentTimeMillis() - Net.ClientState.cutsceneStart) / 1000.0; }

    private static double duration() { return Net.ClientState.cutsceneTicks / 20.0; }

    private static float clamp01(double v) { return (float) Math.max(0, Math.min(1, v)); }
    private static double ease(double t) { t = clamp01(t); return t * t * (3 - 2 * t); }
    private static double mix(double a, double b, double t) { return a + (b - a) * t; }

    /** How the tree itself looks at second s: {scale x, scale y, tint r, g, b, shake}. */
    public static float[] treeState(double s) {
        if (s < SWAP) {
            float w = clamp01(s / 4.5), d = clamp01((s - 3.5) / 3.5);
            float r = (float) mix(mix(1, .70, w), .30, d), g = (float) mix(mix(1, .58, w), .22, d), b = (float) mix(mix(1, .36, w), .17, d);
            float sy = (float) (1 - .10 * ease(s / SWAP));
            return new float[]{(float) (1 - .03 * ease(s / SWAP)), sy, r, g, b, (float) (0.002 + 0.006 * ease(s / SWAP))};
        }
        double u = s - SWAP;                                                   // the fleshy form swells out as it swallows
        float swell = (float) (u < 3 ? 0.80 + 0.30 * ease(u / 2.2) : 1.0 + 0.04 * Math.sin((u - 3) * 5.5) * Math.exp(-(u - 3) * 0.5));
        if (u >= 2.2 && u < 3) swell = (float) (1.10 - 0.10 * ease((u - 2.2) / 0.8));
        float k = clamp01(u / 3.0);
        return new float[]{swell, swell, 1f, (float) mix(.55, 1, k), (float) mix(.55, 1, k), (float) (0.012 * Math.exp(-u * 0.7))};
    }

    // ------------------------------------------------------------------ camera path

    /** Keyframes: time, angle (deg), radius, height, look-at height. */
    private static final double[][] KEYS = {
            {0.0, 20, 115, 6, 75}, {3.0, 40, 95, 10, 70}, {7.0, 200, 70, 45, 45}, {9.2, 255, 52, 22, 25}, {10.5, 290, 34, 14, 22}, {13.0, 335, 135, 60, 70}};

    private static double[] pathAt(double s) {
        double[] a = KEYS[0], b = KEYS[KEYS.length - 1];
        for (int i = 0; i < KEYS.length - 1; i++) if (s <= KEYS[i + 1][0]) { a = KEYS[i]; b = KEYS[i + 1]; break; }
        double t = ease((s - a[0]) / Math.max(0.001, b[0] - a[0]));
        return new double[]{mix(a[1], b[1], t), mix(a[2], b[2], t), mix(a[3], b[3], t), mix(a[4], b[4], t)};
    }

    private static void findTree(Minecraft mc) {
        Entity best = null;
        for (Entity e : mc.level.entitiesForRendering())
            if (e.hasCustomName() && e.getCustomName().getString().startsWith("Mother Tree of Desire")) { best = e; break; }
        if (best != null) { tx = best.getX(); ty = best.getY(); tz = best.getZ(); }
        else { tx = 0.5; ty = mc.player.getY(); tz = 0.5; }
    }

    // ------------------------------------------------------------------ events

    private static void begin(Minecraft mc) {
        running = true; skipped = false; swapped = false; skipHeld = 0; tick = 0;
        findTree(mc);
        cam = new ArmorStand(mc.level, tx, ty + 10, tz);
        cam.setInvisible(true);
        mc.setCameraEntity(cam);
        mc.level.playLocalSound(tx, ty + 20, tz, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6f, 0.55f, false);
        place(0, true);
    }

    private static void end(Minecraft mc) {
        running = false;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        cam = null;
    }

    private static void place(double s, boolean snap) {
        double[] p = pathAt(s);
        double ang = Math.toRadians(p[0]);
        double shake = s < SWAP ? 0.15 + 0.5 * ease(s / SWAP) : (s < 10.5 ? 1.2 : 1.2 * Math.exp(-(s - 10.5) * 1.2));
        double nx = tx + Math.cos(ang) * p[1] + R.nextGaussian() * shake * 0.25, ny = ty + p[2] + R.nextGaussian() * shake * 0.25, nz = tz + Math.sin(ang) * p[1] + R.nextGaussian() * shake * 0.25;
        double dx = tx - nx, dy = ty + p[3] - ny, dz = tz - nz;
        float ny2 = (float) Math.toDegrees(Math.atan2(-dx, dz)) + (float) (R.nextGaussian() * shake * 0.12);
        float np = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))) + (float) (R.nextGaussian() * shake * 0.12);
        if (cam == null) return;
        if (snap) { cam.xo = cam.xOld = nx; cam.yo = cam.yOld = ny - cam.getEyeHeight(); cam.zo = cam.zOld = nz; cam.yRotO = ny2; cam.xRotO = np; }
        else { cam.xo = cam.xOld = cam.getX(); cam.yo = cam.yOld = cam.getY(); cam.zo = cam.zOld = cam.getZ(); cam.yRotO = cam.getYRot(); cam.xRotO = cam.getXRot(); }
        cam.setPos(nx, ny - cam.getEyeHeight(), nz);
        cam.setYRot(ny2); cam.setXRot(np); cam.setYHeadRot(ny2); cam.yHeadRotO = ny2;
        px = nx; py = ny; pz = nz; yaw = ny2; pitch = np;
    }

    private static void onTick(ClientTickEvent.Post ev) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { if (running) running = false; return; }
        boolean act = active();
        if (act && !running) begin(mc);
        if (!act && running) { end(mc); return; }
        if (!running) return;
        tick++;
        double s = seconds();
        // skip: hold jump for a second
        boolean jump = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyJump.getKey().getValue());
        skipHeld = jump ? skipHeld + 1 : 0;
        if (skipHeld > 20) { skipped = true; end(mc); return; }
        place(s, false);

        // ---- the show
        if (s < SWAP) {                                                    // withering: blossoms and leaves rain down, ash rises
            float f = clamp01(s / SWAP);
            for (int i = 0; i < 6 + (int) (14 * f); i++) {
                double x = tx + R.nextGaussian() * 38, z = tz + R.nextGaussian() * 38, y = ty + 30 + R.nextDouble() * 85;
                mc.level.addParticle(R.nextInt(3) == 0 ? ParticleTypes.ASH : ParticleTypes.CHERRY_LEAVES, x, y, z, R.nextGaussian() * 0.05, -0.15 - R.nextDouble() * 0.2, R.nextGaussian() * 0.05);
            }
            if (s > 2.5 && tick % 6 == 0) mc.level.playLocalSound(tx, ty + 10, tz, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 5f, (float) (0.6 + 0.4 * f), false);
            if (tick % 14 == 0) mc.level.playLocalSound(tx, ty + 30, tz, SoundEvents.WOOD_BREAK, SoundSource.HOSTILE, 5f, 0.5f + R.nextFloat() * 0.3f, false);
        } else {                                                           // rotting and absorbing: flesh is torn up and pulled into the trunk
            double u = s - SWAP;
            if (!swapped) {
                swapped = true;
                mc.level.playLocalSound(tx, ty + 25, tz, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 8f, 0.5f, false);
                mc.level.playLocalSound(tx, ty + 25, tz, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 5f, 0.45f, false);
            }
            if (u < 3.4) {
                DustParticleOptions flesh = new DustParticleOptions(new Vector3f(0.62f, 0.07f, 0.12f), 3.2f + R.nextFloat() * 2f);
                for (int i = 0; i < 45; i++) {
                    double a = R.nextDouble() * Math.PI * 2, r = 25 + R.nextDouble() * 55, y0 = ty + R.nextDouble() * 70;
                    double x = tx + Math.cos(a) * r, z = tz + Math.sin(a) * r;
                    double vx = (tx - x) * 0.11, vy = (ty + 25 - y0) * 0.06, vz = (tz - z) * 0.11;
                    mc.level.addParticle(flesh, x, y0, z, vx, vy, vz);
                }
                if (tick % 4 == 0) mc.level.playLocalSound(tx, ty + 10, tz, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 6f, 0.4f + R.nextFloat() * 0.2f, false);
                if (tick % 5 == 0) mc.level.playLocalSound(tx, ty + 10, tz, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 6f, 0.8f, false);
            }
            if (u < 1.2) for (int i = 0; i < 20; i++)
                mc.level.addParticle(ParticleTypes.CRIMSON_SPORE, tx + R.nextGaussian() * 20, ty + R.nextDouble() * 60, tz + R.nextGaussian() * 20, 0, 0.1, 0);
        }
    }

    private static void onGuiLayer(RenderGuiLayerEvent.Pre ev) {
        if (running && !ev.getName().equals(VanillaGuiLayers.CHAT)) ev.setCanceled(true);
    }

    private static final String[] LINES = {
            "The tree has been wounded.", "Its blossoms wither.", "It rots from within.", "It drinks the dead, and wears them as flesh.", "There is no beauty left in it."};
    private static final double[] LINE_AT = {0.4, 2.6, 4.9, 7.5, 10.4};

    private static void onGui(RenderGuiEvent.Post ev) {
        if (!running) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = ev.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        double s = seconds(), d = duration();
        float bars = (float) ease(Math.min(s / 0.8, (d - s) / 0.8));
        int bh = (int) (h * 0.12 * bars);
        g.fill(0, 0, w, bh, 0xFF000000);
        g.fill(0, h - bh, w, h, 0xFF000000);
        // subtitles
        for (int i = 0; i < LINES.length; i++) {
            double t = s - LINE_AT[i];
            if (t < 0 || t > 2.6) continue;
            float a = clamp01(Math.min(t / 0.5, (2.6 - t) / 0.6));
            int alpha = (int) (255 * a);
            if (alpha < 8) continue;
            boolean fleshy = i >= 3;
            g.pose().pushPose();
            var sl = Wrap.lines(mc.font, LINES[i], (int) (w * 0.86f / 1.6f));
            g.pose().translate(w / 2f, h - bh - 26 - (sl.size() - 1) * 14f, 0);
            g.pose().scale(1.6f, 1.6f, 1f);
            for (int li = 0; li < sl.size(); li++) g.drawCenteredString(mc.font, sl.get(li), 0, li * 10, (alpha << 24) | (fleshy ? 0xC8D060 : 0xFFD6F0));
            g.pose().popPose();
        }
        // fades and the flash at the moment it changes
        float black = (float) Math.max(clamp01(1 - s / 0.6), clamp01(1 - (d - s) / 0.7));
        if (black > 0.01f) g.fill(0, 0, w, h, ((int) (255 * black) << 24));
        double u = s - SWAP;
        if (u >= 0 && u < 1.0) g.fill(0, 0, w, h, ((int) (230 * (1 - u)) << 24) | 0xFFC8C8);
        if (s > 1.5) g.drawString(mc.font, "Hold SPACE to skip" + (skipHeld > 0 ? "  " + "|".repeat(Math.min(20, skipHeld)) : ""), 8, h - 12 - bh, 0x88FFFFFF, true);
    }

    private static void onMove(MovementInputUpdateEvent ev) {
        if (!running) return;
        var in = ev.getInput();
        in.leftImpulse = 0; in.forwardImpulse = 0; in.jumping = false; in.shiftKeyDown = false;
        in.up = false; in.down = false; in.left = false; in.right = false;
    }

    private static void onInteract(InputEvent.InteractionKeyMappingTriggered ev) {
        if (running) ev.setCanceled(true);
    }
}
