package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client only: the end calls the Fool. The camera is turned up to the sky, the world fills with grey fog full of drifting
 * lights of colour, and a voice prays. Runs while the server's "call" cutscene (kind 20) is playing.
 */
public final class CallScene {
    private static final String[] LINES = {
            "The Fool that doesn't belong to this era.",
            "The Mysterious Ruler above the Gray Fog.",
            "The King of Yellow and Black who wields good luck.",
            "I pray for your blessing.",
            "I pray for salvation.",
            "I pray for you to save us all."};
    private static final double[] AT = {2.0, 7.0, 12.5, 18.0, 22.0, 26.0};   // the server prints the same lines in chat
    private static final int[] LIGHTS = {0xFFD870, 0xB48CFF, 0x7FE0D0, 0xFF9AB8, 0x8CB4FF, 0xFFB070};
    private static boolean wasActive = false;
    private static int tick = 0;

    private CallScene() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(CallScene::onTick);
        NeoForge.EVENT_BUS.addListener(CallScene::onFrame);
        NeoForge.EVENT_BUS.addListener(CallScene::onGuiLayer);
        NeoForge.EVENT_BUS.addListener(CallScene::onGui);
        NeoForge.EVENT_BUS.addListener(CallScene::onMove);
        NeoForge.EVENT_BUS.addListener(CallScene::onInteract);
        NeoForge.EVENT_BUS.addListener(CallScene::onFog);
        NeoForge.EVENT_BUS.addListener(CallScene::onFogColor);
    }

    public static boolean active() {
        if (Net.ClientState.cutsceneKind != 20) return false;
        long el = System.currentTimeMillis() - Net.ClientState.cutsceneStart;
        return el >= 0 && el < Net.ClientState.cutsceneTicks * 50L;
    }

    private static double seconds() { return (System.currentTimeMillis() - Net.ClientState.cutsceneStart) / 1000.0; }

    private static float clamp01(double v) { return (float) Math.max(0, Math.min(1, v)); }

    private static void onTick(ClientTickEvent.Post ev) {
        Minecraft mc = Minecraft.getInstance();
        boolean act = active() && mc.player != null && mc.level != null;
        if (act && !wasActive) tick = 0;
        wasActive = act;
        if (!act) return;
        tick++;
        mc.player.setXRot(-90f);
        mc.player.xRotO = -90f;
        if (tick % 60 == 1) mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 1.2f, 0.5f, false);
        double s = seconds();
        for (double at : AT) if (Math.abs(s - at) < 0.026) mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 1.5f, 0.5f, false);
    }

    private static void onFrame(RenderFrameEvent.Pre ev) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.player == null) return;
        mc.player.setXRot(-90f);
        mc.player.xRotO = -90f;
    }

    private static void onGuiLayer(RenderGuiLayerEvent.Pre ev) {
        if (active() && !ev.getName().equals(VanillaGuiLayers.CHAT)) ev.setCanceled(true);
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

    private static void onFog(ViewportEvent.RenderFog ev) {
        if (!active()) return;
        float f = clamp01(seconds() / 3.0);
        ev.setNearPlaneDistance(0f);
        ev.setFarPlaneDistance(160f - 146f * f);
        ev.setCanceled(true);
    }

    private static void onFogColor(ViewportEvent.ComputeFogColor ev) {
        if (!active()) return;
        float f = clamp01(seconds() / 3.0);
        ev.setRed(ev.getRed() + (0.60f - ev.getRed()) * f);
        ev.setGreen(ev.getGreen() + (0.60f - ev.getGreen()) * f);
        ev.setBlue(ev.getBlue() + (0.66f - ev.getBlue()) * f);
    }

    /** A soft glowing disc drawn as stacked scanline fills. */
    private static void glow(GuiGraphics g, int cx, int cy, int radius, int rgb, int alpha) {
        for (int ring = 0; ring < 3; ring++) {
            int r = radius * (3 - ring) / 3;
            int col = ((alpha / 3) << 24) | rgb;
            for (int dy = -r; dy <= r; dy += 3) {
                int half = (int) Math.sqrt((double) r * r - (double) dy * dy);
                g.fill(cx - half, cy + dy, cx + half, cy + dy + 3, col);
            }
        }
    }

    private static void onGui(RenderGuiEvent.Post ev) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = ev.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        double s = seconds(), d = Net.ClientState.cutsceneTicks / 20.0;
        float fade = Math.min(clamp01(s / 3.0), clamp01((d - s) / 1.2));
        g.fill(0, 0, w, h, ((int) (200 * fade) << 24) | 0x9C9CA8);   // the grey fog itself
        for (int i = 0; i < 12; i++) {                                // lights of colour drifting inside it
            int cx = (int) (w * (0.5 + 0.48 * Math.sin(s * (0.21 + 0.03 * i) + i * 1.7)));
            int cy = (int) (h * (0.5 + 0.42 * Math.cos(s * (0.17 + 0.025 * i) + i * 2.3)));
            int r = (int) (Math.min(w, h) * (0.16 + 0.07 * Math.sin(s * 0.6 + i)));
            glow(g, cx, cy, r, LIGHTS[i % LIGHTS.length], (int) (120 * fade));
        }
        for (int i = 0; i < LINES.length; i++) {                      // the voice
            double t = s - AT[i];
            if (t < 0 || t > 4.6) continue;
            float a = clamp01(Math.min(t / 0.8, (4.6 - t) / 1.0));
            int alpha = (int) (255 * a);
            if (alpha < 8) continue;
            g.pose().pushPose();
            g.pose().translate(w / 2f, h * 0.62f, 0);
            g.pose().scale(1.9f, 1.9f, 1f);
            g.drawCenteredString(mc.font, LINES[i], 0, 0, (alpha << 24) | 0xFFF2CC);
            g.pose().popPose();
        }
    }
}
