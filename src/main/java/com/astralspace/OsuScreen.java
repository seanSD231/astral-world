package com.astralspace;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client only: the click game of Desire's call. Circles appear one after another; each has a ring that closes onto it. Click it as the ring closes.
 * The layout comes from the seed the server sent. When the time is up the result is sent back and the screen closes.
 */
public final class OsuScreen extends Screen {
    private static final int APPROACH = 16;      // ticks a ring takes to close
    private static final int WINDOW = 6;         // ticks either side of the moment it closes in which a click counts
    private final int total, duration;
    private final double[] px, py;
    private final int[] at;
    private final boolean[] hit, done;
    private int tick = 0, hits = 0;
    private boolean sent = false;

    public OsuScreen(int seed, int count, int ticks) {
        super(Component.literal("Desire"));
        this.total = count;
        this.duration = ticks;
        java.util.Random r = new java.util.Random(seed);
        px = new double[count]; py = new double[count]; at = new int[count]; hit = new boolean[count]; done = new boolean[count];
        int span = ticks - APPROACH - WINDOW - 4;
        for (int i = 0; i < count; i++) {
            at[i] = APPROACH + 2 + (int) Math.round(span * (i + r.nextDouble() * 0.6) / count);
            px[i] = 0.12 + r.nextDouble() * 0.76;
            py[i] = 0.15 + r.nextDouble() * 0.7;
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void tick() {
        tick++;
        for (int i = 0; i < total; i++) if (!done[i] && tick > at[i] + WINDOW) done[i] = true;   // too late: a miss
        if (tick >= duration && !sent) {
            sent = true;
            PacketDistributor.sendToServer(new Net.OsuResult(hits, total));
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double rad = 30;
        for (int i = 0; i < total; i++) {
            if (done[i] || tick < at[i] - APPROACH) continue;
            double cx = px[i] * width, cy = py[i] * height;
            if (Math.hypot(mx - cx, my - cy) > rad) continue;
            if (Math.abs(tick - at[i]) <= WINDOW) {
                hit[i] = true; done[i] = true; hits++;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f + 0.02f * hits, 1f));
            } else done[i] = true;   // far too early: spent
            return true;
        }
        return true;
    }

    private static void disc(GuiGraphics g, int cx, int cy, int r, int argb) {
        for (int y = -r; y <= r; y++) {
            int w = (int) Math.sqrt((double) r * r - (double) y * y);
            g.fill(cx - w, cy + y, cx + w + 1, cy + y + 1, argb);
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int argb) {
        int n = Math.max(24, r * 2);
        for (int k = 0; k < n; k++) {
            double a = Math.PI * 2 * k / n;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x - 1, y - 1, x + 1, y + 1, argb);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xB0200010);
        float t = tick + pt;
        for (int i = total - 1; i >= 0; i--) {
            if (done[i] && !hit[i]) continue;
            double since = t - (at[i] - APPROACH);
            if (since < 0) continue;
            int cx = (int) (px[i] * width), cy = (int) (py[i] * height);
            if (hit[i]) {
                double f = Math.min(1.0, (t - at[i]) / 8.0);
                if (f < 1) ring(g, cx, cy, 30 + (int) (f * 24), ((int) ((1 - f) * 255) << 24) | 0xFFD060);
                continue;
            }
            int alpha = (int) Math.min(255, since * 40);
            disc(g, cx, cy, 30, (alpha * 3 / 4 << 24) | 0xA0103A);
            disc(g, cx, cy, 24, (alpha * 3 / 4 << 24) | 0x601070);
            double k = Math.max(0, 1 - since / APPROACH);
            ring(g, cx, cy, 30 + (int) (k * 70), (alpha << 24) | 0xFFC0E0);
            g.drawCenteredString(font, String.valueOf(i + 1), cx, cy - 4, 0xFFFFFF);
        }
        g.drawCenteredString(font, "Click the circles as the rings close.", width / 2, 12, 0xFFD0F0);
        g.drawCenteredString(font, hits + " / " + total, width / 2, 26, 0xFFFFFF);
        int barW = width / 3;
        g.fill(width / 2 - barW / 2, height - 20, width / 2 - barW / 2 + (int) (barW * Math.min(1f, t / duration)), height - 16, 0xFFE070A0);
    }
}
