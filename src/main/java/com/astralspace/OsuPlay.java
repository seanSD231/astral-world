package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Client only: plays an osu cabinet level. Rings close onto circles; click each as its ring closes. Standalone: nothing is sent to the boss fight. */
public final class OsuPlay extends Screen {
    private static final int APPROACH = 22, LEAD = 40;
    private static final double R300 = 2.5, R100 = 5, R50 = 8;   // timing windows in ticks either side
    private final OsuLevel level;
    private final Screen back;
    private final int n;
    private final int[] state;   // 0 waiting, 1 hit300, 2 hit100, 3 hit50, 4 missed
    private final float[] hitAt;
    private int tick = -LEAD, combo = 0, best = 0;
    private double score = 0;
    private boolean musicOn = false, ended = false;
    private String flashText = "";
    private float flashT = -99;

    public OsuPlay(OsuLevel level, Screen back) {
        super(Component.literal(level.name));
        this.level = level;
        this.back = back;
        this.n = level.notes.size();
        this.state = new int[n];
        this.hitAt = new float[n];
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() { minecraft.setScreen(back); }

    @Override
    public void removed() { stopMusic(); }

    private void stopMusic() {
        if (musicOn && OsuClient.cabinet != null) OsuClient.send(new OsuNet.Music(OsuClient.cabinet, level.disc, false));
        musicOn = false;
    }

    private int lastTick() { return level.lengthTicks() + (int) R50 + 20; }

    @Override
    public void tick() {
        tick++;
        if (tick == 0 && !level.disc.isEmpty() && OsuClient.cabinet != null) {
            OsuClient.send(new OsuNet.Music(OsuClient.cabinet, level.disc, true));
            musicOn = true;
        }
        for (int i = 0; i < n; i++) if (state[i] == 0 && tick > level.notes.get(i)[0] + R50) { state[i] = 4; combo = 0; flash("Miss"); }
        if (!ended && tick > lastTick()) { ended = true; stopMusic(); rebuild(); }
    }

    private void rebuild() {
        clearWidgets();
        if (!ended) return;
        int x = width / 2 - 100, y = height / 2 + 50;
        addRenderableWidget(Button.builder(Component.literal("Retry"), b -> minecraft.setScreen(new OsuPlay(level, back))).bounds(x, y, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(x + 105, y, 95, 20).build());
    }

    private void flash(String s) { flashText = s; flashT = tick; }

    private double px(int i) { return width * 0.1 + level.notes.get(i)[1] / 1000.0 * width * 0.8; }
    private double py(int i) { return height * 0.14 + level.notes.get(i)[2] / 1000.0 * height * 0.74; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (ended) return super.mouseClicked(mx, my, button);
        float t = tick;
        int pick = -1;
        double bestD = 1e9;
        for (int i = 0; i < n; i++) {
            if (state[i] != 0) continue;
            double dt = t - level.notes.get(i)[0];
            if (Math.abs(dt) > R50) continue;
            if (Math.hypot(mx - px(i), my - py(i)) > 32) continue;
            if (Math.abs(dt) < bestD) { bestD = Math.abs(dt); pick = i; }
        }
        if (pick < 0) return true;
        double d = bestD;
        int base = d <= R300 ? 300 : d <= R100 ? 100 : 50;
        state[pick] = base == 300 ? 1 : base == 100 ? 2 : 3;
        hitAt[pick] = tick;
        combo++;
        best = Math.max(best, combo);
        score += base * (1 + combo * 0.02);
        flash(base == 300 ? "300" : base == 100 ? "100" : "50");
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f + Math.min(combo, 30) * 0.02f, 1f));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) { onClose(); return true; }   // Esc
        return super.keyPressed(key, scan, mods);
    }

    private static void disc(GuiGraphics g, int cx, int cy, int r, int argb) {
        for (int y = -r; y <= r; y++) {
            int w = (int) Math.sqrt((double) r * r - (double) y * y);
            g.fill(cx - w, cy + y, cx + w + 1, cy + y + 1, argb);
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int argb) {
        int k = Math.max(24, r * 2);
        for (int i = 0; i < k; i++) {
            double a = Math.PI * 2 * i / k;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x - 1, y - 1, x + 1, y + 1, argb);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) { g.fill(0, 0, width, height, 0xE0100018); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        float t = tick + pt;
        int count300 = 0, count100 = 0, count50 = 0, miss = 0;
        for (int i = 0; i < n; i++) { switch (state[i]) { case 1 -> count300++; case 2 -> count100++; case 3 -> count50++; case 4 -> miss++; default -> { } } }
        if (!ended) {
            for (int i = n - 1; i >= 0; i--) {
                int at = level.notes.get(i)[0];
                double since = t - (at - APPROACH);
                int cx = (int) px(i), cy = (int) py(i);
                if (state[i] >= 1 && state[i] <= 3) {
                    double f = Math.min(1.0, (t - hitAt[i]) / 8.0);
                    if (f < 1) ring(g, cx, cy, 30 + (int) (f * 22), ((int) ((1 - f) * 255) << 24) | (state[i] == 1 ? 0xFFE070 : state[i] == 2 ? 0x70E0FF : 0xB0B0B0));
                    continue;
                }
                if (state[i] == 4 || since < 0) continue;
                int alpha = (int) Math.min(255, since * 40);
                disc(g, cx, cy, 30, (alpha * 3 / 4 << 24) | 0xA0103A);
                disc(g, cx, cy, 24, (alpha * 3 / 4 << 24) | 0x601070);
                double k = Math.max(0, 1 - since / APPROACH);
                ring(g, cx, cy, 30 + (int) (k * 70), (alpha << 24) | 0xFFC0E0);
                g.drawCenteredString(font, String.valueOf(i + 1), cx, cy - 4, 0xFFFFFF);
            }
            g.drawString(font, level.name + (level.author.isEmpty() ? "" : "  by " + level.author), 8, 6, 0xFFD0F0, true);
            g.drawString(font, "Score " + (int) score + "   Combo " + combo, 8, 18, 0xFFFFFF, true);
            if (t - flashT < 14) g.drawCenteredString(font, flashText, width / 2, height - 24, 0xFFE070);
            int barW = width / 3;
            g.fill(width / 2 - barW / 2, height - 12, width / 2 - barW / 2 + (int) (barW * Math.max(0, Math.min(1f, t / lastTick()))), height - 9, 0xFFE070A0);
            g.drawString(font, "Esc: quit", width - 50, 6, 0x808098, false);
        } else {
            double acc = n == 0 ? 0 : (300.0 * count300 + 100.0 * count100 + 50.0 * count50) / (300.0 * n) * 100;
            String grade = acc >= 100 ? "SS" : acc >= 95 ? "S" : acc >= 90 ? "A" : acc >= 80 ? "B" : acc >= 70 ? "C" : "D";
            g.drawCenteredString(font, level.name, width / 2, height / 2 - 70, 0xFFD0F0);
            g.drawCenteredString(font, grade, width / 2, height / 2 - 52, 0xFFE070);
            g.drawCenteredString(font, "Score " + (int) score + "   Accuracy " + String.format("%.1f", acc) + "%   Best combo " + best, width / 2, height / 2 - 30, 0xFFFFFF);
            g.drawCenteredString(font, "300 x" + count300 + "   100 x" + count100 + "   50 x" + count50 + "   Miss x" + miss, width / 2, height / 2 - 16, 0xB0B0D0);
        }
    }
}
