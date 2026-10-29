package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client only: a four-lane rhythm game. Notes fall; press the lane's key (D F J K, or the arrow keys) as a note crosses the line. */
public final class RhythmScreen extends Screen {
    private static final int FALL = 30, WINDOW = 7;
    private static final int[][] KEYS = {{GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_LEFT}, {GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_DOWN}, {GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_UP}, {GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_RIGHT}};
    private static final String[] LABEL = {"D", "F", "J", "K"};
    private final int total, duration;
    private final int[] lane, at;
    private final boolean[] hit, done;
    private final int[] flash = new int[4];
    private int tick = 0, hits = 0;
    private boolean sent = false;

    public RhythmScreen(int seed, int count, int ticks) {
        super(Component.literal("Rhythm"));
        total = count; duration = ticks;
        java.util.Random r = new java.util.Random(seed);
        lane = new int[count]; at = new int[count]; hit = new boolean[count]; done = new boolean[count];
        int span = ticks - FALL - WINDOW - 10, prev = -1;
        for (int i = 0; i < count; i++) {
            at[i] = FALL + 6 + (int) Math.round(span * (i + r.nextDouble() * 0.4) / count);
            int l = r.nextInt(4);
            if (l == prev) l = (l + 1 + r.nextInt(3)) % 4;
            lane[i] = prev = l;
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void tick() {
        tick++;
        for (int i = 0; i < 4; i++) if (flash[i] > 0) flash[i]--;
        for (int i = 0; i < total; i++) if (!done[i] && tick > at[i] + WINDOW) done[i] = true;
        if (tick >= duration && !sent) {
            sent = true;
            PacketDistributor.sendToServer(new Net.OsuResult(hits, total));
            onClose();
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        for (int l = 0; l < 4; l++) {
            if (key != KEYS[l][0] && key != KEYS[l][1]) continue;
            flash[l] = 6;
            for (int i = 0; i < total; i++) {
                if (done[i] || lane[i] != l || Math.abs(tick - at[i]) > WINDOW) continue;
                hit[i] = true; done[i] = true; hits++;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f + 0.12f * l, 1f));
                break;
            }
            return true;
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xB0100820);
        float t = tick + pt;
        int lw = 46, gap = 8, x0 = width / 2 - (4 * lw + 3 * gap) / 2, top = 40, line = height - 60;
        for (int l = 0; l < 4; l++) {
            int x = x0 + l * (lw + gap);
            g.fill(x, top, x + lw, line + 16, 0x60301858);
            g.fill(x, line - 2, x + lw, line + 2, flash[l] > 0 ? 0xFFFFFFFF : 0xFFB070FF);
            g.drawCenteredString(font, LABEL[l], x + lw / 2, line + 22, 0xFFE0FF);
        }
        for (int i = 0; i < total; i++) {
            if (done[i]) continue;
            double since = t - (at[i] - FALL);
            if (since < 0) continue;
            int x = x0 + lane[i] * (lw + gap), y = (int) (top + (line - top) * (since / FALL));
            g.fill(x + 3, y - 6, x + lw - 3, y + 6, 0xFFFFD060);
            g.fill(x + 3, y - 6, x + lw - 3, y - 4, 0xFFFFFFFF);
        }
        g.drawCenteredString(font, "Press D F J K (or the arrow keys) as the notes reach the line.", width / 2, 12, 0xFFD0F0);
        g.drawCenteredString(font, hits + " / " + total, width / 2, 24, 0xFFFFFF);
    }
}
