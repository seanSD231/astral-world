package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client only: the Mother Tree of Desire's own health bar, drawn in place of the vanilla boss bar. */
public final class TreeBossBar {
    private static final String NAME = "Mother Tree of Desire";
    private static float trail = 1f;

    private TreeBossBar() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(TreeBossBar::onBar);
    }

    private static void onBar(CustomizeGuiOverlayEvent.BossEventProgress ev) {
        LerpingBossEvent be = ev.getBossEvent();
        if (!be.getName().getString().contains(NAME)) return;
        ev.setCanceled(true);
        ev.setIncrement(40);
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = ev.getGuiGraphics();
        float p = Math.max(0f, Math.min(1f, be.getProgress()));
        trail = p >= trail ? p : Math.max(p, trail - 0.003f);   // the pale trail shows what the last hits took

        int w = 340, h = 14, x = (g.guiWidth() - w) / 2, y = ev.getY() + 14;
        boolean rotten = p <= 0.5f;
        long t = System.currentTimeMillis();
        float pulse = 0.5f + 0.5f * (float) Math.sin(t / (p < 0.25f ? 160.0 : 420.0));

        // frame: dark wood with a soft glow that quickens as it weakens
        int glow = rotten ? 0x60C03050 : 0x60A060E0;
        g.fill(x - 5, y - 5, x + w + 5, y + h + 5, (((int) (0x30 + 0x40 * pulse)) << 24) | (glow & 0xFFFFFF));
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF1A0F22);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, rotten ? 0xFF6A3A48 : 0xFF7A5A98);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0B0612);

        int tw = (int) (w * trail), fw = (int) (w * p);
        g.fill(x, y, x + tw, y + h, 0xCCF2DCEC);                                  // trail
        int top = rotten ? 0xFFD98A86 : 0xFFC8F27A, bottom = rotten ? 0xFF5A1530 : 0xFF2F7A3A;
        if (p < 0.25f) { top = 0xFFFF6A7A; bottom = 0xFF7A0E22; }
        g.fillGradient(x, y, x + fw, y + h, top, bottom);
        g.fill(x, y, x + fw, y + 1, 0x80FFFFFF);                                    // highlight

        for (int k = 1; k <= 3; k++) {                                              // the phases at 75, 50 and 25 percent
            int nx = x + w * k / 4;
            g.fill(nx - 1, y - 3, nx + 1, y + h + 3, 0xFF0B0612);
            g.fill(nx, y - 3, nx + 1, y - 1, 0xFFE8C8FF);
        }
        for (int s = -1; s <= 1; s += 2) {                                          // ornaments at the ends
            int ex = s < 0 ? x - 8 : x + w + 8;
            g.fill(ex - 3, y + h / 2 - 3, ex + 3, y + h / 2 + 3, 0xFF7A5A98);
            g.fill(ex - 2, y + h / 2 - 2, ex + 2, y + h / 2 + 2, rotten ? 0xFFFF6A7A : 0xFFC8F27A);
        }

        int cx = g.guiWidth() / 2;
        g.pose().pushPose();
        g.pose().translate(cx, y - 14, 0);
        g.pose().scale(1.2f, 1.2f, 1f);
        g.drawCenteredString(mc.font, be.getName(), 0, 0, rotten ? 0xFFFF9AA8 : 0xFFF2D6FF);
        g.pose().popPose();
        g.drawCenteredString(mc.font, Math.round(p * 100) + "%", cx, y + 3, 0xFFFFFFFF);
    }
}
