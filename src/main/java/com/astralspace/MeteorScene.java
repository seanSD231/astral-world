package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client only: the cutscene of the tree's 15% meteor (cutscene kind 22). When the meteor starts to fall every fighter's view swings up to it,
 * the screen shakes under it and black bars close in, and then control returns while it keeps falling, slowly.
 */
public final class MeteorScene {
    /** Seconds the meteor needs to land (the server uses the same numbers). */
    public static final double FALL = 30.0;
    private static final double TOP = 1100.0;
    private static float startYaw, startPitch;
    private static boolean wasActive = false;

    private MeteorScene() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(MeteorScene::onTick);
        NeoForge.EVENT_BUS.addListener(MeteorScene::onFrame);
        NeoForge.EVENT_BUS.addListener(MeteorScene::onGuiLayer);
        NeoForge.EVENT_BUS.addListener(MeteorScene::onGui);
        NeoForge.EVENT_BUS.addListener(MeteorScene::onMove);
        NeoForge.EVENT_BUS.addListener(MeteorScene::onInteract);
    }

    public static boolean active() {
        if (Net.ClientState.cutsceneKind != 22) return false;
        long el = System.currentTimeMillis() - Net.ClientState.cutsceneStart;
        return el >= 0 && el < Net.ClientState.cutsceneTicks * 50L;
    }

    private static double seconds() { return (System.currentTimeMillis() - Net.ClientState.cutsceneStart) / 1000.0; }

    private static float clamp01(double v) { return (float) Math.max(0, Math.min(1, v)); }

    /** Where the meteor is, relative to the eye, at `s` seconds after it began to fall. */
    private static double[] aim(Minecraft mc, double s) {
        double f = Math.min(1, s / FALL);
        double gy = Double.isNaN(TreeRenderer.lastY) ? mc.player.getY() : TreeRenderer.lastY;
        double y = gy + TOP - TOP * (0.6 * f + 0.4 * f * f);
        double dx = TreeRenderer.lastX - mc.player.getX(), dz = TreeRenderer.lastZ - mc.player.getZ(), dy = y - mc.player.getEyeY();
        float yaw = Math.hypot(dx, dz) < 6 ? startYaw : (float) Math.toDegrees(Math.atan2(-dx, dz));   // standing in the tree: keep facing the way you were instead of spinning round its middle
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(1.0, Math.hypot(dx, dz))));
        return new double[]{yaw, Mth.clamp(pitch, -85f, 85f)};
    }

    private static void point(Minecraft mc) {
        double s = seconds(), d = Net.ClientState.cutsceneTicks / 20.0;
        float t = Math.min(clamp01(s / 1.4), clamp01((d - s) / 1.0));   // swing up, hold, hand control back
        t = t * t * (3 - 2 * t);
        double[] a = aim(mc, s);
        float shake = (float) (Math.min(1.0, s / 2.0) * Math.min(1.0, (d - s) / 1.0));
        float yaw = startYaw + Mth.wrapDegrees((float) a[0] - startYaw) * t + (float) Math.sin(s * 38) * 0.35f * shake;
        float pitch = startPitch + ((float) a[1] - startPitch) * t + (float) Math.cos(s * 31) * 0.35f * shake;
        mc.player.setYRot(yaw); mc.player.yRotO = yaw; mc.player.setYHeadRot(yaw);
        mc.player.setXRot(pitch); mc.player.xRotO = pitch;
    }

    private static void onTick(ClientTickEvent.Post ev) {
        Minecraft mc = Minecraft.getInstance();
        boolean act = active() && mc.player != null && mc.level != null;
        if (act && !wasActive) { startYaw = mc.player.getYRot(); startPitch = mc.player.getXRot(); }
        wasActive = act;
        if (act) point(mc);
    }

    private static void onFrame(RenderFrameEvent.Pre ev) {
        Minecraft mc = Minecraft.getInstance();
        if (active() && mc.player != null) point(mc);
    }

    private static void onGuiLayer(RenderGuiLayerEvent.Pre ev) {
        if (active()) ev.setCanceled(true);
    }

    private static void onMove(MovementInputUpdateEvent ev) {
        if (!active()) return;
        var in = ev.getInput();
        in.leftImpulse = 0; in.forwardImpulse = 0; in.jumping = false; in.shiftKeyDown = false;
        in.up = false; in.down = false; in.left = false; in.right = false;
    }

    private static void onInteract(InputEvent.InteractionKeyMappingTriggered ev) {
        if (active()) ev.setCanceled(true);
    }

    private static void onGui(RenderGuiEvent.Post ev) {
        if (!active()) return;
        GuiGraphics g = ev.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        double s = seconds(), d = Net.ClientState.cutsceneTicks / 20.0;
        float in = Math.min(clamp01(s / 1.2), clamp01((d - s) / 1.0));
        int bar = (int) (h * 0.12f * in);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        int red = (int) (60 * in * (0.6 + 0.4 * Math.sin(s * 5)));      // a heat-red pulse over everything
        g.fill(0, 0, w, h, (red << 24) | 0xFF2000);
        for (int i = 0; i < 6; i++) {                                    // dark vignette closing in
            int m = (int) (in * (i + 1) * w * 0.012f);
            g.fill(0, 0, m, h, 0x22000000); g.fill(w - m, 0, w, h, 0x22000000);
        }
    }
}
