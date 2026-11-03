package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client only: the parry. The tree gathers its power; a marker sweeps across a bar. Click (or press space) as it crosses the middle:
 * dead centre takes no damage, close takes less, and missing takes the full blow. One try. The result goes to the server when the time is up.
 */
public final class ParryScreen extends Screen {
    private final int duration;
    private final double period, phase;   // ticks for one sweep across, and where in it the marker starts
    private int tick = 0;
    private double lockedAt = -1, acc = 0;
    private boolean sent = false;

    public ParryScreen(int seed, int ticks) {
        super(Component.literal("Parry"));
        this.duration = ticks;
        java.util.Random r = new java.util.Random(seed);
        this.period = 26 + r.nextInt(6);
        this.phase = r.nextDouble() * period * 2;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    /** The marker's place, 0..1, bouncing from end to end. */
    private double pos(double t) {
        double x = ((t + phase) / period) % 2.0;
        return x < 1 ? x : 2 - x;
    }

    @Override
    public void tick() {
        tick++;
        if (tick >= duration && !sent) {
            sent = true;
            PacketDistributor.sendToServer(new Net.OsuResult((int) Math.round(acc * 100), 100));
            onClose();
        }
    }

    private void lock() {
        if (lockedAt >= 0 || tick < 8) return;   // a short pause so a click that opened the screen does not count
        lockedAt = pos(tick);
        acc = Math.max(0, 1 - Math.abs(lockedAt - 0.5) * 2);
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(acc > 0.88 ? SoundEvents.ANVIL_PLACE : SoundEvents.SHIELD_BLOCK, acc > 0.88 ? 1.4f : 0.9f, 1f));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) { lock(); return true; }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 32) { lock(); return true; }   // space
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xB0200010);
        int bw = Math.min(width - 40, 300), x0 = width / 2 - bw / 2, y0 = height / 2 - 8, h = 18;
        g.fill(x0 - 2, y0 - 2, x0 + bw + 2, y0 + h + 2, 0xFFFFFFFF);
        g.fill(x0, y0, x0 + bw, y0 + h, 0xFF200818);
        // the middle is worth the most: bands from the edges inward
        for (int i = 0; i < bw / 2; i++) {
            double q = i / (bw / 2.0);   // 0 at the edge, 1 in the middle
            int col = q > 0.88 ? 0xFF60FF80 : q > 0.65 ? 0xFFFFE070 : q > 0.35 ? 0xFFFF9040 : 0xFF802020;
            g.fill(x0 + i, y0 + 3, x0 + i + 1, y0 + h - 3, col);
            g.fill(x0 + bw - 1 - i, y0 + 3, x0 + bw - i, y0 + h - 3, col);
        }
        double f = lockedAt >= 0 ? lockedAt : pos(tick + pt);
        int mxp = x0 + (int) (f * bw);
        g.fill(mxp - 2, y0 - 8, mxp + 2, y0 + h + 8, lockedAt >= 0 ? 0xFFFFE070 : 0xFFFFFFFF);
        g.drawCenteredString(font, "THE TREE GATHERS ITS POWER", width / 2, y0 - 40, 0xFFD0F0);
        g.drawCenteredString(font, "Click (or space) when the marker is in the middle", width / 2, y0 - 26, 0xB0B0D0);
        if (lockedAt >= 0) {
            String r = acc > 0.88 ? "PERFECT" : acc > 0.65 ? "GOOD" : acc > 0.35 ? "WEAK" : "MISSED";
            g.drawCenteredString(font, r, width / 2, y0 + h + 18, acc > 0.88 ? 0x60FF80 : acc > 0.65 ? 0xFFE070 : acc > 0.35 ? 0xFF9040 : 0xFF5050);
        }
        int barW = width / 3;
        g.fill(width / 2 - barW / 2, height - 20, width / 2 - barW / 2 + (int) (barW * Math.min(1f, (tick + pt) / duration)), height - 16, 0xFFE070A0);
    }
}
