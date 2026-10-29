package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client only: remember the order. Four coloured pads light up one after another; then click them in the same order. Counts the correct steps before the first mistake. */
public final class MemoryScreen extends Screen {
    private static final int SHOW = 12, GAP = 3;
    private static final int[] COL = {0xFFE05060, 0xFF50C878, 0xFF5090F0, 0xFFF0C040};
    private final int total, duration, showEnd;
    private final int[] seq;
    private int tick = 0, hits = 0, flashPad = -1, flashLeft = 0;
    private boolean sent = false, failed = false;

    public MemoryScreen(int seed, int count, int ticks) {
        super(Component.literal("Memory"));
        total = count; duration = ticks;
        java.util.Random r = new java.util.Random(seed);
        seq = new int[count];
        for (int i = 0; i < count; i++) seq[i] = r.nextInt(4);
        showEnd = 20 + count * (SHOW + GAP);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    private int litPad() {
        if (tick < 20 || tick >= showEnd) return -1;
        int k = (tick - 20) / (SHOW + GAP), within = (tick - 20) % (SHOW + GAP);
        return k < total && within < SHOW ? seq[k] : -1;
    }

    @Override
    public void tick() {
        tick++;
        if (flashLeft > 0) flashLeft--;
        if (tick >= duration && !sent) {
            sent = true;
            PacketDistributor.sendToServer(new Net.OsuResult(hits, total));
            onClose();
        }
    }

    private int[] padRect(int i) {
        int s = Math.min(width, height) / 4, gap = 12, x0 = width / 2 - s - gap / 2, y0 = height / 2 - s - gap / 2;
        return new int[]{x0 + (i % 2) * (s + gap), y0 + (i / 2) * (s + gap), s};
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tick < showEnd || failed || hits >= total) return true;
        for (int i = 0; i < 4; i++) {
            int[] r = padRect(i);
            if (mx < r[0] || mx > r[0] + r[2] || my < r[1] || my > r[1] + r[2]) continue;
            flashPad = i; flashLeft = 5;
            if (seq[hits] == i) {
                hits++;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7f + 0.2f * i, 1f));
            } else {
                failed = true;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 1f));
            }
            return true;
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xB0100820);
        int lit = litPad();
        for (int i = 0; i < 4; i++) {
            int[] r = padRect(i);
            boolean on = i == lit || (i == flashPad && flashLeft > 0);
            int col = COL[i];
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[2], on ? col : (0x50 << 24) | (col & 0xFFFFFF));
            if (on) g.fill(r[0] + 6, r[1] + 6, r[0] + r[2] - 6, r[1] + r[2] - 6, 0x60FFFFFF);
        }
        String msg = tick < showEnd ? "Watch the order..." : failed ? "Wrong." : "Now click them in the same order.";
        g.drawCenteredString(font, msg, width / 2, 12, 0xFFD0F0);
        g.drawCenteredString(font, hits + " / " + total, width / 2, 24, 0xFFFFFF);
    }
}
