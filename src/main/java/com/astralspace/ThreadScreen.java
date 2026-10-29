package com.astralspace;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client only: follow the glowing thread. A light travels along it; keep your cursor near the light. It is checked `count` times along the way. */
public final class ThreadScreen extends Screen {
    private final int total, duration;
    private final double a1, p1, a2, p2;
    private int tick = 0, hits = 0, checked = 0;
    private double mouseX, mouseY;
    private boolean sent = false;
    private float good = 0;

    public ThreadScreen(int seed, int count, int ticks) {
        super(Component.literal("Thread"));
        total = count; duration = ticks;
        java.util.Random r = new java.util.Random(seed);
        a1 = 0.18 + r.nextDouble() * 0.08; p1 = r.nextDouble() * 6.28;
        a2 = 0.06 + r.nextDouble() * 0.05; p2 = r.nextDouble() * 6.28;
    }

    private double px(double u) { return (0.1 + 0.8 * u) * width; }
    private double py(double u) { return (0.5 + a1 * Math.sin(u * 5.0 + p1) + a2 * Math.sin(u * 11.0 + p2)) * height; }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void tick() {
        tick++;
        double u = Math.min(1.0, (double) tick / duration);
        while (checked < total && (checked + 1.0) / total <= u + 1e-9) {
            checked++;
            double d = Math.hypot(mouseX - px((double) checked / total), mouseY - py((double) checked / total));
            if (d <= 40) hits++;
        }
        if (tick >= duration && !sent) {
            sent = true;
            PacketDistributor.sendToServer(new Net.OsuResult(hits, total));
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        mouseX = mx; mouseY = my;
        g.fill(0, 0, width, height, 0xB0081820);
        double t = Math.min(1.0, (tick + pt) / duration);
        for (int k = 0; k <= 160; k++) {
            double u = k / 160.0;
            int x = (int) px(u), y = (int) py(u);
            g.fill(x - 3, y - 3, x + 3, y + 3, u <= t ? 0x6040E0A0 : 0xFF40E0A0);
        }
        int lx = (int) px(t), ly = (int) py(t);
        g.fill(lx - 9, ly - 9, lx + 9, ly + 9, 0x40FFFFFF);
        g.fill(lx - 5, ly - 5, lx + 5, ly + 5, 0xFFFFFFFF);
        boolean on = Math.hypot(mx - lx, my - ly) <= 40;
        g.fill(mx - 4, my - 4, mx + 4, my + 4, on ? 0xFFFFE070 : 0xFFFF6060);
        g.drawCenteredString(font, "Keep your cursor on the light as it follows the thread.", width / 2, 12, 0xD0FFF0);
        g.drawCenteredString(font, hits + " / " + total, width / 2, 24, 0xFFFFFF);
    }
}
