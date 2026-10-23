package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client only: the Mother Tree of Desire's own health bar. Above half health it is mystical and beautiful (blossom gradient,
 * gold-trimmed bark, twinkling lights, drifting petals); at half and below it turns fleshy and disgusting (veined, pulsing,
 * pustules, ragged edges, dripping).
 */
public final class TreeBossBar {
    private static final String NAME = "Mother Tree of Desire", EARLY_NAME = "The World Tree";
    private static final int[] PINK = {0xFFFF8FC8, 0xFFB69CFF, 0xFF7CE8B0}, FLESH = {0xFFD25A62, 0xFF7A1C2E, 0xFFC8D060};
    private static float trail = 1f;

    private TreeBossBar() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(TreeBossBar::onBar);
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static float hash(int n) {
        int h = n * 0x27d4eb2d;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h & 0xFFFF) / 65535f;
    }

    private static void onBar(CustomizeGuiOverlayEvent.BossEventProgress ev) {
        LerpingBossEvent be = ev.getBossEvent();
        String title = be.getName().getString();
        boolean fleshy = title.contains(NAME);
        if (!fleshy && !title.contains(EARLY_NAME)) return;
        ev.setCanceled(true);
        ev.setIncrement(46);
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = ev.getGuiGraphics();
        float real = Math.max(0f, Math.min(1f, be.getProgress()));
        // the Mother Tree lies: after the change she shows 65% at her real half, and 0% only when she is really at 0 as -25%
        float shown = fleshy ? Math.max(real <= 0.5f ? 1.8f * real - 0.25f : real, -0.25f) : real;
        float p = Math.max(0f, Math.min(1f, shown));
        trail = p >= trail ? p : Math.max(p, trail - 0.003f);
        boolean rotten = fleshy;
        double sec = System.currentTimeMillis() / 1000.0;

        int w = 340, h = 14, x = (g.guiWidth() - w) / 2, y = ev.getY() + 18;
        int fw = (int) (w * p), tw = (int) (w * trail);

        if (!rotten) {
            // ---- mystical: soft glow, mossy bark with gold trim
            float breathe = 0.5f + 0.5f * (float) Math.sin(sec * 1.6);
            g.fill(x - 7, y - 7, x + w + 7, y + h + 7, ((int) (0x1A + 0x22 * breathe) << 24) | 0xFF9BD2);
            g.fill(x - 4, y - 4, x + w + 4, y + h + 4, 0xFF1D1226);
            g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFFE8C77A);
            g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF2A1A34);
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0C0712);
            g.fill(x + tw - 1, y, x + tw, y + h, 0x00000000);
            g.fill(x, y, x + tw, y + h, 0x88FFFFFF);                                            // trail
            for (int i = 0; i < fw; i += 2) {                                                      // blossom pink -> lilac -> spring green
                float t = i / (float) w;
                int c = t < 0.5f ? mix(0xFFFF8FC8, 0xFFB69CFF, t * 2) : mix(0xFFB69CFF, 0xFF7CE8B0, (t - 0.5f) * 2);
                int top = mix(c, 0xFFFFFFFF, 0.35f), bot = mix(c, 0xFF2A1A4A, 0.45f);
                g.fillGradient(x + i, y, Math.min(x + i + 2, x + fw), y + h, top, bot);
            }
            for (int k = 0; k < 14; k++) {                                                          // twinkling lights inside the fill
                float tw2 = (float) Math.sin(sec * (1.5 + hash(k) * 2) + k * 1.7);
                if (tw2 < 0.3f) continue;
                int sx = x + (int) (hash(k + 50) * Math.max(1, fw - 2)), sy = y + 2 + (int) (hash(k + 90) * (h - 5));
                g.fill(sx, sy, sx + 2, sy + 1, 0xFFFFFFFF);
                g.fill(sx, sy - 1, sx + 1, sy + 2, 0xCCFFFFFF);
            }
            for (int k = 0; k < 9; k++) {                                                           // petals drifting down past the bar
                double ph = (sec * 0.35 + hash(k) * 3) % 1.0;
                int px = x + (int) (hash(k + 10) * w + Math.sin(sec * 1.3 + k) * 6), py = y - 14 + (int) (ph * (h + 26));
                int a = (int) (200 * Math.sin(ph * Math.PI));
                if (a > 20) { g.fill(px, py, px + 3, py + 2, (a << 24) | 0xFFB3DD); g.fill(px + 1, py - 1, px + 2, py + 3, (a << 24) | 0xFFD6EC); }
            }
            for (int i = 4; i < w - 4; i += 7) {                                                    // vines along the frame, with leaves and blossoms
                int vy = (i / 7) % 2 == 0 ? y - 4 : y + h + 3;
                g.fill(x + i, vy, x + i + 5, vy + 1, 0xFF3F8A52);
                if ((i / 7) % 3 == 0) { g.fill(x + i + 1, vy + (vy < y ? -2 : 1), x + i + 3, vy + (vy < y ? 0 : 3), 0xFF7CE8B0); }
                if ((i / 7) % 5 == 0) { g.fill(x + i + 2, vy - 1, x + i + 4, vy + 2, 0xFFFF9BD2); g.fill(x + i + 3, vy, x + i + 4, vy + 1, 0xFFFFF2A8); }
            }
            for (int s = -1; s <= 1; s += 2) {                                                      // a glowing blossom at each end
                int ex = s < 0 ? x - 10 : x + w + 10, ey = y + h / 2;
                g.fill(ex - 4, ey - 1, ex + 4, ey + 2, 0xFFFF9BD2); g.fill(ex - 1, ey - 4, ex + 2, ey + 5, 0xFFFF9BD2);
                g.fill(ex - 3, ey - 3, ex + 4, ey + 4, 0x66FFD6EC);
                g.fill(ex - 1, ey - 1, ex + 2, ey + 2, 0xFFFFF2A8);
            }
        } else {
            // ---- fleshy and disgusting: ragged bone-edged frame, veined pulsing fill, pustules, drips
            float beat = (float) Math.max(0, Math.sin(sec * (p < 0.2f ? 6.0 : 3.2)));
            beat = beat * beat;
            g.fill(x - 7, y - 7, x + w + 7, y + h + 7, ((int) (0x18 + 0x40 * beat) << 24) | 0xC02030);
            g.fill(x - 4, y - 4, x + w + 4, y + h + 4, 0xFF1A0508);
            for (int i = -4; i < w + 4; i += 3) {                                                   // ragged, uneven edge
                int top = y - 3 - (int) (hash(i + 7) * 3), bot = y + h + 3 + (int) (hash(i + 31) * 3);
                g.fill(x + i, top, x + i + 3, y, 0xFF4A1A22); g.fill(x + i, y + h, x + i + 3, bot, 0xFF4A1A22);
                if (hash(i) > 0.7f) { g.fill(x + i, top, x + i + 2, top + 1, 0xFFB89A6A); }
            }
            g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF2C0A10);
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0A0204);
            g.fill(x, y, x + tw, y + h, 0x99E8C8B0);                                                // trail, like pus-pale skin
            for (int i = 0; i < fw; i += 2) {
                float t = i / (float) w;
                int c = mix(0xFFD25A62, 0xFF7A1C2E, t * 1.2f + 0.2f * beat);
                g.fillGradient(x + i, y, Math.min(x + i + 2, x + fw), y + h, mix(c, 0xFFFFB0A0, 0.18f + 0.2f * beat), mix(c, 0xFF2A0610, 0.55f));
            }
            for (int v = 0; v < 5; v++) {                                                           // wandering dark veins
                int vy0 = y + 2 + (int) (hash(v + 3) * (h - 4));
                for (int i = 0; i < fw; i += 2) {
                    int vy = vy0 + (int) Math.round(Math.sin(i * 0.11 + v * 2.1 + sec * 0.8) * 2.2);
                    vy = Math.max(y, Math.min(y + h - 1, vy));
                    g.fill(x + i, vy, x + i + 2, vy + 1, 0x99400814);
                }
            }
            for (int k = 0; k < 8; k++) {                                                           // pustules
                if (fw < 10) break;
                int px = x + 6 + (int) (hash(k + 20) * (fw - 12)), py = y + 3 + (int) (hash(k + 60) * (h - 8));
                int r = 2 + (int) (hash(k + 80) * 2);
                float swell = 1f + 0.2f * (float) Math.sin(sec * 2 + k);
                int rr = Math.max(2, Math.round(r * swell));
                g.fill(px - rr, py - rr + 1, px + rr, py + rr - 1, 0xFF3C0A12);
                g.fill(px - rr + 1, py - rr, px + rr - 1, py + rr, 0xFF3C0A12);
                g.fill(px - rr + 1, py - rr + 1, px + rr - 1, py + rr - 1, 0xFFC8D060);
                g.fill(px - 1, py - 1, px, py, 0xFFFFFFE0);
            }
            for (int k = 0; k < 9; k++) {                                                           // slow red drips under the bar
                int dx = x + 8 + (int) (hash(k + 5) * (w - 16));
                double ph = (sec * (0.3 + hash(k + 15) * 0.3) + hash(k) * 4) % 1.0;
                int len = 3 + (int) (ph * 14);
                g.fill(dx, y + h + 3, dx + 2, y + h + 3 + len, 0xCC8A1220);
                g.fill(dx, y + h + 3 + len, dx + 2, y + h + 5 + len, 0xFFB02030);
            }
            for (int s = -1; s <= 1; s += 2) {                                                      // an eye-like growth at each end
                int ex = s < 0 ? x - 10 : x + w + 10, ey = y + h / 2;
                g.fill(ex - 5, ey - 3, ex + 5, ey + 4, 0xFF3C0A12);
                g.fill(ex - 4, ey - 2, ex + 4, ey + 3, 0xFFE8D8A0);
                g.fill(ex - 1, ey - 2, ex + 2, ey + 3, 0xFF7A0A1A);
                g.fill(ex, ey - 1, ex + 1, ey + 1, 0xFF000000);
            }
        }

        float[] marks = rotten ? new float[]{0.2f} : new float[]{0.75f, 0.5f};                       // her real 25% reads as 20% once she lies
        for (float mk : marks) {
            int nx = x + (int) (w * mk);
            g.fill(nx - 1, y - 3, nx + 1, y + h + 3, 0xFF0B0612);
            g.fill(nx, y - 3, nx + 1, y - 1, rotten ? 0xFFB89A6A : 0xFFFFF2A8);
        }

        // under half health the bar is a lie, and now and then it slips: slices tear sideways, the name and the number break up
        long slot = (long) (sec * 8);
        boolean glitch = fleshy && real <= 0.5f && hash((int) (slot / 3)) < 0.14f;
        String pct = Math.round(shown * 100) + "%";
        if (glitch) {
            int[] tearCols = {0xAA00FFFF, 0xAAFF00AA, 0xCC000000, 0xAAFF2020};
            for (int i = 0; i < 6; i++) {
                int ry = y - 2 + (int) (hash((int) (slot * 7 + i)) * (h + 4)), rh = 1 + (int) (hash((int) (slot * 11 + i)) * 4);
                int off = (int) ((hash((int) (slot * 13 + i)) - 0.5f) * 44), rw = (int) (w * (0.15f + hash((int) (slot * 17 + i)) * 0.6f));
                int rx = x + (int) (hash((int) (slot * 19 + i)) * (w - rw));
                g.fill(rx + off, ry, rx + off + rw, ry + rh, tearCols[(int) (hash((int) (slot * 23 + i)) * tearCols.length)]);
            }
            float q = hash((int) (slot * 29));
            pct = q < 0.35f ? (int) (hash((int) (slot * 31)) * 200 - 40) + "%" : q < 0.6f ? "ERR%" : q < 0.8f ? "???" : "-" + (int) (hash((int) (slot * 37)) * 99) + "%";
        }

        int cx = g.guiWidth() / 2;
        int[] pal = rotten ? FLESH : PINK;
        int gx = glitch ? (int) ((hash((int) (slot * 41)) - 0.5f) * 14) : 0;
        if (glitch) {   // red and cyan ghosts of the name behind it
            g.pose().pushPose();
            g.pose().translate(cx, y - 16, 0);
            g.pose().scale(1.25f, 1.25f, 1f);
            g.drawCenteredString(mc.font, title, -3, 0, 0x88FF2030);
            g.drawCenteredString(mc.font, title, 3, 1, 0x8830F0FF);
            g.pose().popPose();
        }
        g.pose().pushPose();
        g.pose().translate(cx + gx, y - 16, 0);
        g.pose().scale(1.25f, 1.25f, 1f);
        gradientText(g, mc, title, 0, 0, pal, sec, 0.07f);
        g.pose().popPose();
        gradientText(g, mc, pct, cx - gx, y + 3, pal, sec, 0.2f);
    }

    /** Centered text whose colours slide along the letters, cycling through the palette. */
    private static void gradientText(GuiGraphics g, Minecraft mc, String text, int cx, int y, int[] pal, double sec, float spread) {
        int total = mc.font.width(text), x = cx - total / 2;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            float t = (float) (((i * spread + sec * 0.45) % 1.0 + 1.0) % 1.0) * pal.length;
            int a = (int) Math.floor(t) % pal.length;
            int c = mix(pal[a], pal[(a + 1) % pal.length], t - (float) Math.floor(t));
            g.drawString(mc.font, ch, x, y, c);
            x += mc.font.width(ch);
        }
    }
}
