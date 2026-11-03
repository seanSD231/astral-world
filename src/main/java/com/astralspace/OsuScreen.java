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
    private static final int APPROACH = 24;      // ticks a ring takes to close
    private static final int WINDOW = 8;         // ticks either side of the moment it closes in which a click counts
    private final int total, duration;
    private final double[] px, py;
    private final int[] at;
    private final boolean[] hit, done, slider, active;
    private final double[] qx, qy;
    private final float[] track;
    private static final int SLIDE = 22;   // ticks a slider is followed
    private boolean mouseDown = false;
    private double lmx, lmy;
    private int tick = 0, hits = 0;
    private boolean sent = false;

    public OsuScreen(int seed, int count, int ticks) {
        super(Component.literal("Desire"));
        this.total = fit(count, ticks);   // fewer shapes now that there are sliders, and room after each slider
        this.duration = ticks;
        java.util.Random r = new java.util.Random(seed);
        int count0 = count; count = total;
        px = new double[count]; py = new double[count]; at = new int[count]; hit = new boolean[count]; done = new boolean[count];
        slider = new boolean[count]; active = new boolean[count]; qx = new double[count]; qy = new double[count]; track = new float[count];
        int span = ticks - APPROACH - WINDOW - 12 - SLIDE - 14;
        int nSl = count / 4;
        double base0 = Math.max(12, (span - nSl * (SLIDE + 14)) / (double) count), tt = APPROACH + 6;
        java.util.List<double[]> occ = new java.util.ArrayList<>();   // {x, y, until tick}: shapes still on screen
        for (int i = 0; i < count; i++) {
            at[i] = (int) Math.round(tt);
            tt += base0 + (i % 4 == 3 ? SLIDE + 14 : 0) + r.nextDouble() * 3;   // after a slider the next shape comes later: you cannot click while still following it
            double bx = 0.5, by = 0.5;
            for (int tries = 0; tries < 60; tries++) {
                bx = 0.12 + r.nextDouble() * 0.76; by = 0.15 + r.nextDouble() * 0.7;
                boolean ok = true;
                for (double[] o : occ) if (o[2] > at[i] - APPROACH - 6 && Math.hypot((bx - o[0]) * 1.8, by - o[1]) < 0.26) { ok = false; break; }
                if (ok) break;
            }
            px[i] = bx; py[i] = by;
            occ.add(new double[]{bx, by, at[i]});
            if (i % 4 == 3) {   // every fourth is a slider: hold and follow the ball to the other end
                slider[i] = true;
                double ax = bx, ay = by;
                for (int tries = 0; tries < 30; tries++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    ax = bx + Math.cos(a) * 0.15; ay = by + Math.sin(a) * 0.26;
                    if (ax > 0.1 && ax < 0.9 && ay > 0.15 && ay < 0.85) break;
                }
                qx[i] = Math.max(0.1, Math.min(0.9, ax)); qy[i] = Math.max(0.15, Math.min(0.85, ay));
                occ.add(new double[]{qx[i], qy[i], at[i] + SLIDE});
            }
        }
    }

    private static int fit(int count, int ticks) {
        int n = Math.max(3, count * 3 / 4), span = ticks - APPROACH - WINDOW - 12 - SLIDE - 14;
        while (n > 3 && (span - (n / 4) * (SLIDE + 14)) / (double) n < 16) n--;
        return n;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void tick() {
        tick++;
        for (int i = 0; i < total; i++) if (!done[i] && tick > at[i] + WINDOW) done[i] = true;   // too late: a miss
        for (int i = 0; i < total; i++) {
            if (!active[i]) continue;
            double[] b = ball(i, tick);
            if (mouseDown && Math.hypot(lmx - b[0], lmy - b[1]) <= 56) track[i]++;
            if (tick >= at[i] + SLIDE) { active[i] = false; if (track[i] >= SLIDE * 0.5f) hits++; }
        }
        if (tick >= duration && !sent) {
            sent = true;
            PacketDistributor.sendToServer(new Net.OsuResult(hits, total));
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double rad = 30;
        if (button == 0) { mouseDown = true; lmx = mx; lmy = my; }
        for (int i = 0; i < total; i++) {
            if (done[i] || tick < at[i] - APPROACH) continue;
            double cx = px[i] * width, cy = py[i] * height;
            if (Math.hypot(mx - cx, my - cy) > rad) continue;
            if (Math.abs(tick - at[i]) <= WINDOW) {
                hit[i] = true; done[i] = true;
                if (slider[i]) active[i] = true; else hits++;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f + 0.02f * hits, 1f));
            } else done[i] = true;   // far too early: spent
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) { if (button == 0) mouseDown = false; return super.mouseReleased(mx, my, button); }

    private double[] ball(int i, double t) {
        double f = Math.max(0, Math.min(1, (t - at[i]) / SLIDE));
        return new double[]{(px[i] + (qx[i] - px[i]) * f) * width, (py[i] + (qy[i] - py[i]) * f) * height};
    }

    private void body(GuiGraphics g, int i, int alpha) {
        double x0 = px[i] * width, y0 = py[i] * height, x1 = qx[i] * width, y1 = qy[i] * height;
        int steps = Math.max(2, (int) (Math.hypot(x1 - x0, y1 - y0) / 6));
        for (int k = 0; k <= steps; k++) { double f = k / (double) steps; disc(g, (int) (x0 + (x1 - x0) * f), (int) (y0 + (y1 - y0) * f), 22, (alpha / 2 << 24) | OsuClient.colours()[3]); }
        ring(g, (int) x1, (int) y1, 22, (alpha << 24) | OsuClient.colours()[2]);
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
        lmx = mx; lmy = my;
        for (int i = total - 1; i >= 0; i--) {
            if (done[i] && !hit[i]) continue;
            if (active[i]) {
                body(g, i, 255);
                double[] b = ball(i, t);
                ring(g, (int) b[0], (int) b[1], 44, mouseDown && Math.hypot(mx - b[0], my - b[1]) <= 56 ? 0xFFFFE070 : 0x80FFFFFF);
                disc(g, (int) b[0], (int) b[1], 18, 0xFFFFC040);
                continue;
            }
            double since = t - (at[i] - APPROACH);
            if (since < 0) continue;
            int cx = (int) (px[i] * width), cy = (int) (py[i] * height);
            if (hit[i]) {
                double f = Math.min(1.0, (t - at[i]) / 8.0);
                if (f < 1) ring(g, cx, cy, 30 + (int) (f * 24), ((int) ((1 - f) * 255) << 24) | 0xFFD060);
                continue;
            }
            int alpha = (int) Math.min(255, since * 40);
            if (slider[i]) body(g, i, alpha);
            int[] tc = OsuClient.colours();
            disc(g, cx, cy, 30, (alpha * 3 / 4 << 24) | tc[0]);
            disc(g, cx, cy, 24, (alpha * 3 / 4 << 24) | tc[1]);
            double k = Math.max(0, 1 - since / APPROACH);
            ring(g, cx, cy, 30 + (int) (k * 70), (alpha << 24) | tc[2]);
            g.drawCenteredString(font, String.valueOf(i + 1), cx, cy - 4, 0xFFFFFF);
        }
        g.drawCenteredString(font, "Click the circles as the rings close. Hold and follow the sliders.", width / 2, 12, 0xFFD0F0);
        g.drawCenteredString(font, hits + " / " + total, width / 2, 26, 0xFFFFFF);
        int barW = width / 3;
        g.fill(width / 2 - barW / 2, height - 20, width / 2 - barW / 2 + (int) (barW * Math.min(1f, t / duration)), height - 16, 0xFFE070A0);
    }
}
