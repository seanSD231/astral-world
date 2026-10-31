package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
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
            "The Fool that does not belong to this era.",
            "You are the mysterious ruler above the gray fog.",
            "You are the King of Yellow and Black who wields good luck.",
            "I pray to you,",
            "Praying for the strength that stems from mystery,",
            "And praying for the bestowment that stems from good luck.",
            "Please grant us the power to protect our home.",
            "Something heard you",
            "Something is watching you",
            "Something is angry"};
    private static final double[] AT = {2.0, 6.5, 11.0, 15.5, 20.0, 24.5, 29.0, 36.0, 40.0, 44.0};   // the server prints the same lines in chat
    private static final double[] LEN = {4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 4.2, 3.6, 3.6, 3.6};
    private static final double GLITCH_FROM = 32.0, FIGURES_AT = 47.5;
    private static final int[] LIGHTS = {0xFFD870, 0xB48CFF, 0x7FE0D0, 0xFF9AB8, 0x8CB4FF, 0xFFB070};
    private static boolean wasActive = false;
    private static int tick = 0;
    private static final boolean[] VOICED = new boolean[7];   // the spoken line has started (line 6 has no recording yet)
    private static final int[] VOICE_LINE = {1, 2, 3, 4, 5, -1, 7};

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
        NeoForge.EVENT_BUS.addListener(CallScene::onChat);
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
        if (act && !wasActive) { tick = 0; java.util.Arrays.fill(VOICED, false); }
        wasActive = act;
        if (!act) return;
        tick++;
        mc.player.setXRot(-90f);
        mc.player.xRotO = -90f;
        if (tick % 60 == 1) mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 1.2f, 0.5f, false);
        double s = seconds();
        for (int i = 0; i < 7; i++) {
            if (VOICED[i] || s < AT[i] || s > AT[i] + 1.5 || VOICE_LINE[i] < 0) continue;
            VOICED[i] = true;
            var ev = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "prayer.line_" + VOICE_LINE[i]));
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(ev, 1f, 1.4f));
        }
        for (int i = 0; i < AT.length; i++) if (i < 7 && Math.abs(s - AT[i]) < 0.026) mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 1.5f, 0.5f, false);
        if (s > GLITCH_FROM && s < FIGURES_AT + 3 && tick % 7 == 0 && Math.random() < 0.5) mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.ENDERMAN_STARE, SoundSource.AMBIENT, 0.6f, 0.4f + (float) Math.random() * 0.4f, false);
    }

    private static void onFrame(RenderFrameEvent.Pre ev) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.player == null) return;
        mc.player.setXRot(-90f);
        mc.player.xRotO = -90f;
    }

    private static void onGuiLayer(RenderGuiLayerEvent.Pre ev) {
        if (active()) ev.setCanceled(true);   // chat too: nothing may cover the words
    }

    private static void onChat(ClientChatReceivedEvent ev) {
        if (active()) ev.setCanceled(true);   // no chat messages during the cutscene
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
        float dark = darkness();
        float rr = 0.20f + 0.08f * dark, gg = 0.20f * (1 - dark), bb = 0.23f * (1 - dark);
        if (glitchOn()) { rr = (float) Math.random() * 0.8f; gg = (float) Math.random() * 0.2f; bb = (float) Math.random() * 0.3f; }
        ev.setRed(ev.getRed() + (rr - ev.getRed()) * f);
        ev.setGreen(ev.getGreen() + (gg - ev.getGreen()) * f);
        ev.setBlue(ev.getBlue() + (bb - ev.getBlue()) * f);
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

    /** 0 in the prayer, rising to 1 as something answers. */
    private static float darkness() { return clamp01((seconds() - GLITCH_FROM) / 8.0) * (seconds() > 50.0 ? clamp01(1 - (seconds() - 50.0) / 2.0) : 1f); }

    /** True on the frames where the whole fog tears. */
    private static boolean glitchOn() {
        double s = seconds();
        if (s < GLITCH_FROM || s > 50.0) return false;
        long slot = (long) (s * 10);
        return hash((int) slot) < 0.12f + 0.3f * clamp01((s - GLITCH_FROM) / 10.0);
    }

    private static float hash(int n) {
        int h = n * 0x27d4eb2d;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h & 0xFFFF) / 65536f;
    }

    private static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), gg = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (gg << 8) | bl;
    }

    private static final String JUNK = "#%&@$?!/\\|<>01X";

    private static String scramble(String t, long seed, float amount) {
        StringBuilder sb = new StringBuilder(t);
        for (int i = 0; i < sb.length(); i++) if (sb.charAt(i) != ' ' && hash((int) (seed * 31 + i * 7)) < amount) sb.setCharAt(i, JUNK.charAt((int) (hash((int) (seed * 17 + i)) * JUNK.length())));
        return sb.toString();
    }

    /** One line of text, optionally glitching: tearing copies, scrambled letters, a gradient. */
    private static void drawLine(Minecraft mc, GuiGraphics g, String text, int w, int h, int alpha, boolean red, boolean glitchy, long slot, double s) {
        float scale = 1.9f;
        g.pose().pushPose();
        float maxW = (w * 0.86f) / scale;
        var rows = mc.font.getSplitter().splitLines(text, (int) maxW, net.minecraft.network.chat.Style.EMPTY);
        g.pose().translate(w / 2f, h * 0.62f - (rows.size() - 1) * 6f * scale, 0);
        g.pose().scale(scale, scale, 1f);
        for (int row = 0; row < rows.size(); row++) {
            g.pose().pushPose();
            g.pose().translate(0, row * 12f, 0);
            drawRow(mc, g, rows.get(row).getString(), alpha, red, glitchy, slot + row * 977L, s);
            g.pose().popPose();
        }
        g.pose().popPose();
        if (glitchy && hash((int) (slot * 3)) < 0.45f) {
            int by = (int) (h * 0.62f) - 4;
            for (int i = 0; i < 3; i++) {
                int ty = by + (int) (hash((int) (slot * 19 + i)) * 24), th = 2 + (int) (hash((int) (slot * 23 + i)) * 4);
                int off = (int) ((hash((int) (slot * 29 + i)) - 0.5f) * 80);
                g.fill(w / 2 - 170 + off, ty, w / 2 + 170 + off, ty + th, (red ? 0x66000000 : 0x33FFFFFF) | (red ? 0x300008 : 0xFFE9A0));
            }
        }
    }

    private static void drawRow(Minecraft mc, GuiGraphics g, String text, int alpha, boolean red, boolean glitchy, long slot, double s) {
        boolean burst = glitchy && hash((int) (slot * 3)) < 0.45f;
        String shownText = burst ? scramble(text, slot, 0.18f) : text;
        int tw = mc.font.width(shownText), x0 = -tw / 2;
        int jx = burst ? (int) ((hash((int) (slot * 5)) - 0.5f) * 10) : 0;
        if (burst) {   // colour-split ghosts
            g.drawString(mc.font, shownText, x0 - 3 + jx, 0, ((alpha / 2) << 24) | 0xFF0030, false);
            g.drawString(mc.font, shownText, x0 + 3 + jx, 1, ((alpha / 2) << 24) | 0x20E0FF, false);
        }
        if (red) {     // red to black gradient, letter by letter, sliding
            int x = x0 + jx;
            for (int i = 0; i < shownText.length(); i++) {
                String ch = String.valueOf(shownText.charAt(i));
                float t = (float) (0.5 + 0.5 * Math.sin(s * 3 + i * 0.5));
                int col = lerp(0xFF1010, 0x180004, t);
                int dy = burst && hash((int) (slot * 11 + i)) < 0.2f ? (int) ((hash((int) (slot * 13 + i)) - 0.5f) * 6) : 0;
                g.drawString(mc.font, ch, x + 1, dy + 1, (alpha << 24) | 0x000000, false);
                g.drawString(mc.font, ch, x, dy, (alpha << 24) | col, false);
                x += mc.font.width(ch);
            }
        } else {
            g.drawString(mc.font, shownText, x0 + jx, 0, (alpha << 24) | 0xFFF2CC, false);
        }
    }

    private static void onGui(RenderGuiEvent.Post ev) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = ev.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        double s = seconds(), d = Net.ClientState.cutsceneTicks / 20.0;
        float fade = Math.min(clamp01(s / 3.0), clamp01((d - s) / 1.2));
        float dark = darkness();
        boolean tear = glitchOn();
        long slot = (long) (s * 12);
        int fogRgb = lerp(0x45454E, 0x12040A, dark);
        g.fill(0, 0, w, h, ((int) (225 * fade) << 24) | fogRgb);   // the grey fog itself, going dark and red as it answers
        for (int i = 0; i < 12; i++) {                                // lights of colour drifting inside it
            int cx = (int) (w * (0.5 + 0.48 * Math.sin(s * (0.21 + 0.03 * i) + i * 1.7)));
            int cy = (int) (h * (0.5 + 0.42 * Math.cos(s * (0.17 + 0.025 * i) + i * 2.3)));
            int r = (int) (Math.min(w, h) * (0.16 + 0.07 * Math.sin(s * 0.6 + i)));
            glow(g, cx, cy, r, lerp(LIGHTS[i % LIGHTS.length], 0x801018, dark), (int) (80 * fade * (1 - 0.6f * dark)));
        }
        if (s > GLITCH_FROM && s < 50.0) {                            // the fog itself glitches
            float inten = 0.3f + 0.7f * clamp01((s - GLITCH_FROM) / 10.0);
            int n = tear ? 14 : 3;
            for (int i = 0; i < n; i++) {
                if (!tear && hash((int) (slot * 7 + i)) > inten) continue;
                int ty = (int) (hash((int) (slot * 41 + i)) * h), th = 2 + (int) (hash((int) (slot * 43 + i)) * (tear ? 26 : 8));
                int off = (int) ((hash((int) (slot * 47 + i)) - 0.5f) * w * 0.5f);
                int col = hash((int) (slot * 53 + i)) < 0.5f ? 0x66FF1030 : hash((int) (slot * 59 + i)) < 0.5f ? 0x5530F0FF : 0xAA000000;
                g.fill(Math.max(0, off), ty, Math.min(w, w + off), ty + th, col);
            }
            if (tear && hash((int) slot) < 0.25f) g.fill(0, 0, w, h, 0x99000000);   // a hard black flicker
        }
        for (int i = 0; i < LINES.length; i++) {                      // the voice
            double t = s - AT[i];
            if (t < 0 || t > LEN[i]) continue;
            float a = clamp01(Math.min(t / 0.8, (LEN[i] - t) / 1.0));
            int alpha = (int) (255 * a);
            if (alpha < 8) continue;
            boolean red = i >= 7 && i <= 9;
            boolean glitchy = (s > GLITCH_FROM && i < 10) || red;
            drawLine(mc, g, LINES[i], w, h, alpha, red, glitchy, slot, s);
        }
    }
}
