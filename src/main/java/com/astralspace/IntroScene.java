package com.astralspace;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client only: the introduction to the tree fight (cutscene kind 23). Right after the teleport the camera slowly circles the Mother Tree
 * so everyone sees what they are up against, then control returns. Hold jump to skip.
 */
public final class IntroScene {
    private static boolean running = false, skipped = false;
    private static ArmorStand cam;
    private static double tx, ty, tz;
    private static int skipHeld = 0, tick = 0;
    private static final java.util.Random R = new java.util.Random();

    /** Seconds, angle (deg), radius, camera height above the tree's base, look-at height. */
    private static final double[][] KEYS = {{0.0, 205, 175, 10, 50}, {3.5, 245, 150, 22, 62}, {7.0, 300, 135, 50, 78}, {10.0, 335, 165, 35, 70}};
    private static final String[] LINES = {"The World Tree.", "She has heard your prayers.", "Do not wake her until you are ready."};
    private static final double[] LINE_AT = {0.8, 3.6, 6.6};

    private IntroScene() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(IntroScene::onTick);
        NeoForge.EVENT_BUS.addListener(IntroScene::onGuiLayer);
        NeoForge.EVENT_BUS.addListener(IntroScene::onGui);
        NeoForge.EVENT_BUS.addListener(IntroScene::onMove);
        NeoForge.EVENT_BUS.addListener(IntroScene::onInteract);
    }

    public static boolean active() {
        if (Net.ClientState.cutsceneKind != 23 || skipped) return false;
        long el = System.currentTimeMillis() - Net.ClientState.cutsceneStart;
        return el >= 0 && el < Net.ClientState.cutsceneTicks * 50L;
    }

    private static double seconds() { return (System.currentTimeMillis() - Net.ClientState.cutsceneStart) / 1000.0; }
    private static double duration() { return Net.ClientState.cutsceneTicks / 20.0; }
    private static float clamp01(double v) { return (float) Math.max(0, Math.min(1, v)); }
    private static double ease(double t) { t = clamp01(t); return t * t * (3 - 2 * t); }
    private static double mix(double a, double b, double t) { return a + (b - a) * t; }

    private static double[] pathAt(double s) {
        double sc = s * (KEYS[KEYS.length - 1][0] / Math.max(1, duration()));   // the keyframes stretch to the length of the scene
        double[] a = KEYS[0], b = KEYS[KEYS.length - 1];
        for (int i = 0; i < KEYS.length - 1; i++) if (sc <= KEYS[i + 1][0]) { a = KEYS[i]; b = KEYS[i + 1]; break; }
        double t = ease((sc - a[0]) / Math.max(0.001, b[0] - a[0]));
        return new double[]{mix(a[1], b[1], t), mix(a[2], b[2], t), mix(a[3], b[3], t), mix(a[4], b[4], t)};
    }

    private static void findTree(Minecraft mc) {
        if (!Double.isNaN(TreeRenderer.lastY)) { tx = TreeRenderer.lastX; ty = TreeRenderer.lastY; tz = TreeRenderer.lastZ; }
        else { tx = 0.5; ty = PlanetGenerator.BASE + 2; tz = 0.5; }
    }

    private static void place(double s, boolean snap) {
        if (cam == null) return;
        double[] p = pathAt(s);
        double ang = Math.toRadians(p[0]);
        double nx = tx + Math.cos(ang) * p[1], ny = ty + p[2] + 1.6, nz = tz + Math.sin(ang) * p[1];
        double dx = tx - nx, dy = ty + p[3] - ny, dz = tz - nz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        if (snap) { cam.xo = cam.xOld = nx; cam.yo = cam.yOld = ny - cam.getEyeHeight(); cam.zo = cam.zOld = nz; cam.yRotO = yaw; cam.xRotO = pitch; }
        else { cam.xo = cam.xOld = cam.getX(); cam.yo = cam.yOld = cam.getY(); cam.zo = cam.zOld = cam.getZ(); cam.yRotO = cam.getYRot(); cam.xRotO = cam.getXRot(); }
        cam.setPos(nx, ny - cam.getEyeHeight(), nz);
        cam.setYRot(yaw); cam.setXRot(pitch); cam.setYHeadRot(yaw); cam.yHeadRotO = yaw;
    }

    private static void begin(Minecraft mc) {
        running = true; skipped = false; skipHeld = 0; tick = 0;
        findTree(mc);
        cam = new ArmorStand(mc.level, tx, ty + 10, tz);
        cam.setInvisible(true);
        mc.setCameraEntity(cam);
        mc.level.playLocalSound(tx, ty + 20, tz, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5f, 0.5f, false);
        place(0, true);
    }

    private static void end(Minecraft mc) {
        running = false;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        cam = null;
    }

    private static void onTick(ClientTickEvent.Post ev) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { running = false; return; }
        boolean act = active();
        if (act && !running) begin(mc);
        if (!act && running) { end(mc); return; }
        if (!running) return;
        tick++;
        boolean jump = InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyJump.getKey().getValue());
        skipHeld = jump ? skipHeld + 1 : 0;
        if (skipHeld > 20) { skipped = true; end(mc); return; }
        place(seconds(), false);
        for (int i = 0; i < 6; i++) {   // blossoms drift down around her
            double x = tx + R.nextGaussian() * 40, z = tz + R.nextGaussian() * 40, y = ty + 25 + R.nextDouble() * 90;
            mc.level.addParticle(ParticleTypes.CHERRY_LEAVES, x, y, z, R.nextGaussian() * 0.04, -0.12 - R.nextDouble() * 0.15, R.nextGaussian() * 0.04);
        }
        if (tick % 40 == 0) mc.level.playLocalSound(tx, ty + 10, tz, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 4f, 0.7f, false);
    }

    private static void onGuiLayer(RenderGuiLayerEvent.Pre ev) {
        if (running && !ev.getName().equals(VanillaGuiLayers.CHAT)) ev.setCanceled(true);
    }

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
        for (int i = 0; i < LINES.length; i++) {
            double t = s - LINE_AT[i];
            if (t < 0 || t > 2.8) continue;
            float a = clamp01(Math.min(t / 0.5, (2.8 - t) / 0.6));
            int alpha = (int) (255 * a);
            if (alpha < 8) continue;
            g.pose().pushPose();
            var sl = Wrap.lines(mc.font, LINES[i], (int) (w * 0.86f / 1.6f));
            g.pose().translate(w / 2f, h - bh - 26 - (sl.size() - 1) * 14f, 0);
            g.pose().scale(1.6f, 1.6f, 1f);
            for (int li = 0; li < sl.size(); li++) g.drawCenteredString(mc.font, sl.get(li), 0, li * 10, (alpha << 24) | 0xFFD6F0);
            g.pose().popPose();
        }
        float black = (float) Math.max(clamp01(1 - s / 0.8), clamp01(1 - (d - s) / 0.8));
        if (black > 0.01f) g.fill(0, 0, w, h, ((int) (255 * black) << 24));
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
