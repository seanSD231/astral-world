package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The card binder as a bound volume: one pathway to a page.
 *
 * <p>The page shows that pathway's ten faces large enough to look at, named by their
 * Sequence titles rather than numbers alone. Down the left, a spine and then the whole
 * collection -- every pathway under its Great Old One with a progress bar -- so a player
 * can see where they stand without turning anything.
 *
 * <p>The grid this replaces showed fifty cards at thumbnail size. The art is 240x360 and
 * deserves better; this fits ten of them on a page instead.
 *
 * <p>Drawn in the mod's own language: the accent is the player's own pathway, gold sits
 * at the corners and on the spine, plates are slanted, a diamond lattice lies over
 * near-black, and red is only for backing out. Clicking a held card takes it out.
 */
public final class BinderScreen extends Screen {

    private static final int PANEL   = 0xFF141018;
    private static final int PANEL_2 = 0xFF1C1822;
    private static final int CASE    = 0xFF0D0B11;
    private static final int SPINE   = 0xFF17121E;
    private static final int GOLD    = 0xFFD4AF37;
    private static final int GOLD_HI = 0xFFF3DC8A;
    private static final int BRONZE  = 0xFF7A5C1E;
    private static final int GREY    = 0xFFA0A8B4;
    private static final int GREY_D  = 0xFF5A606C;

    /**
     * The order pages turn in: down the rail, Great Old One by Great Old One.
     * {@link Tarot#PATHWAYS} is alphabetical, which is a fine way to find a pathway and
     * a strange way to leaf through a book.
     */
    private static final String[] ORDER = order();

    private static String[] order() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String[] group : Tarot.GOO_PATHS) java.util.Collections.addAll(out, group);
        return out.toArray(new String[0]);
    }

    private final Map<String, Integer> have = new HashMap<>();
    /** Which of the twenty-two pathways is open. */
    private int page = 0;
    private boolean shiny = false;
    private String hover = null;

    private int left, top, right, bottom, railX, railW, gridX, gridY, cw, ch, colGap, rowGap;
    /**
     * This page is laid out in real pixels, so it has to be drawn at a scale of its own
     * rather than the player's. At GUI scale 4 the screen is about 480 logical pixels
     * wide and a 268px rail swallows half of it.
     *
     * <p>Everything below works in a canvas of {@link #vw} x {@link #vh}, which is about
     * half the window's real pixels whatever the setting, and the whole thing is scaled
     * down to fit when it is drawn. Mouse coordinates come back the other way.
     */
    private double scale = 1;
    private int vw, vh, railLine = 19, railHead = 18, railGap = 6;

    private BinderScreen(String packed) {
        super(Component.literal("Card Binder"));
        load(packed);
    }

    private void load(String packed) {
        have.clear();
        for (String part : packed.split(",")) {
            int c = part.lastIndexOf(':');
            if (c <= 0) continue;
            try {
                have.put(part.substring(0, c), Integer.parseInt(part.substring(c + 1)));
            } catch (NumberFormatException ignored) { }
        }
    }

    public static void open(String packed) {
        Screen now = Minecraft.getInstance().screen;
        if (now instanceof BinderScreen b) { b.load(packed); return; }
        Minecraft.getInstance().setScreen(new BinderScreen(packed));
    }

    private String path() { return ORDER[page]; }

    // ---- colour ---------------------------------------------------------------------

    private static int accentOf(String pathway) {
        return switch (pathway) {
            case "spectator" -> 0xFF55FFFF;
            case "bard", "warrior" -> 0xFFFFFF55;
            case "sailor", "marauder", "apprentice" -> 0xFF5555FF;
            case "seer", "mystery_pryer", "prisoner" -> 0xFFAA00AA;
            case "secrets_supplicant", "hunter", "criminal" -> 0xFFAA0000;
            case "assassin" -> 0xFFFF55FF;
            case "corpse_collector", "planter" -> 0xFF55FF55;
            case "apothecary" -> 0xFF00AA00;
            case "lawyer" -> 0xFF0000AA;
            case "savant", "arbiter" -> 0xFFFFAA00;
            case "reader" -> 0xFFFFFFFF;
            case "monster" -> 0xFFAAAAAA;
            case "sleepless" -> 0xFF777777;
            default -> GOLD;
        };
    }

    /** The player's own pathway, through the same reader the launcher link uses. */
    private int accent() {
        var player = Minecraft.getInstance().player;
        if (player == null) return GOLD;
        try {
            String p = Launcher.readPathway(player.saveWithoutId(new CompoundTag()));
            return p.isEmpty() ? GOLD : accentOf(p);
        } catch (Throwable ignored) {
            return GOLD;
        }
    }

    /**
     * The Sequence title, taken from LOTM's own lang file rather than a copy of it.
     * Its keys drop the underscores: {@code pathway.corpsecollector.sequence0}.
     */
    private String seqName(String pathway, int seq) {
        String key = "pathway." + pathway.replace("_", "") + ".sequence" + seq;
        String got = Component.translatable(key).getString();
        return got.equals(key) ? "Sequence " + seq : got;
    }

    // ---- geometry --------------------------------------------------------------------

    @Override
    protected void init() {
        // Aim for about 1.5 real pixels per virtual one: small enough that the whole
        // collection fits down the rail, large enough that 9px text still reads.
        double gui = Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
        scale = Math.min(1.0, 1.5 / gui);
        vw = (int) (width / scale);
        vh = (int) (height / scale);

        left = 16; top = 14; right = vw - 16; bottom = vh - 14;
        railX = left + 46;                                     // past the spine
        railW = Math.max(176, Math.min(268, (right - left) / 5));

        gridX = railX + railW + 24;
        gridY = top + 96;
        colGap = 14;
        rowGap = 52;                                           // room for the sequence name

        int availW = right - 24 - gridX;
        // the bottom row needs its own label space, which the gap between rows does not
        // provide -- without this the last row's Sequence names sit under the foot strip
        int availH = bottom - 58 - gridY - 32;
        cw = Math.max(40, Math.min((availW - colGap * 4) / 5, ((availH - rowGap) / 2) * 2 / 3));
        ch = cw * 3 / 2;
        gridX += Math.max(0, (availW - (cw * 5 + colGap * 4)) / 2);

        // The rail is nine headings and twenty-two pathways. Rather than let it run off
        // the foot, measure what is there and divide the space it has.
        int lines = ORDER.length, heads = Tarot.GOO_IDS.length;
        int railSpace = (bottom - 58) - (top + 96);
        railLine = Math.max(11, Math.min(19, (railSpace - heads * 16 - heads * 4) / lines));
        railHead = Math.max(11, Math.min(18, railLine - 1));
        railGap = railLine >= 17 ? 6 : 2;
    }

    private int cardX(int i) { return gridX + (i % 5) * (cw + colGap); }
    private int cardY(int i) { return gridY + (i / 5) * (ch + rowGap); }

    // ---- input ------------------------------------------------------------------------

    private void goTo(int p) {
        if (p == page || p < 0 || p >= ORDER.length) return;
        page = p;
    }

    @Override
    public boolean mouseClicked(double rawX, double rawY, int button) {
        double mx = rawX / scale, my = rawY / scale;
        int[] hit = railHit(mx, my);
        if (hit != null) {
            if (hit[0] == 0) { goTo(hit[1]); return true; }
            take(Binder.key("goo_" + Tarot.GOO_IDS[hit[1]], 0, shiny));
            return true;
        }
        for (int i = 0; i < 10; i++) {
            int x = cardX(i), y = cardY(i);
            if (mx >= x && mx < x + cw && my >= y && my < y + ch) {
                take(Binder.key(path(), 9 - i, shiny));
                return true;
            }
        }
        if (mx >= right - 190 && mx < right - 16 && my >= top + 10 && my < top + 50) {
            shiny = !shiny;
            return true;
        }
        if (my >= bottom - 44 && my < bottom - 12) {
            if (mx >= left + 40 && mx < left + 240) { goTo(page - 1); return true; }
            if (mx >= right - 240 && mx < right - 40) { goTo(page + 1); return true; }
            int c = (left + right) / 2;
            if (mx >= c - 110 && mx < c + 110) {
                PacketDistributor.sendToServer(new Net.BinderActPayload(1, ""));
                return true;
            }
        }
        return super.mouseClicked(rawX, rawY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 263) { goTo(page - 1); return true; }
        if (key == 262) { goTo(page + 1); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private void take(String key) {
        if (have.getOrDefault(key, 0) > 0) PacketDistributor.sendToServer(new Net.BinderActPayload(0, key));
    }

    /** {0, pathway} for a pathway row, {1, goo} for a Great Old One's own card. */
    private int[] railHit(double mx, double my) {
        if (mx < railX - 8 || mx > railX + railW) return null;
        int y = top + 96;
        for (int g = 0; g < Tarot.GOO_IDS.length; g++) {
            if (my >= y - 2 && my < y + railHead && mx >= railX + railW - 24) return new int[]{1, g};
            y += railHead;
            for (String p : Tarot.GOO_PATHS[g]) {
                if (my >= y - 2 && my < y + railLine - 2) {
                    for (int i = 0; i < ORDER.length; i++) {
                        if (ORDER[i].equals(p)) return new int[]{0, i};
                    }
                }
                y += railLine;
            }
            y += railGap;
        }
        return null;
    }

    // ---- drawing helpers ----------------------------------------------------------------

    private void diamond(GuiGraphics g, int cx, int cy, int r, int colour) {
        for (int i = -r; i <= r; i++) {
            int half = r - Math.abs(i);
            g.fill(cx - half, cy + i, cx + half + 1, cy + i + 1, colour);
        }
    }

    private void slant(GuiGraphics g, int x, int y, int w, int h, int skew, int colour) {
        for (int i = 0; i < h; i++) {
            int off = h <= 1 ? 0 : skew * (h - 1 - i) / (h - 1);
            g.fill(x + off, y + i, x + off + w, y + i + 1, colour);
        }
    }

    private void lattice(GuiGraphics g, int x0, int y0, int x1, int y1) {
        final int step = 54;
        int row = 0;
        for (int y = y0 + 18; y < y1 - 8; y += step, row++) {
            for (int x = x0 + 18 + (row % 2) * step / 2; x < x1 - 8; x += step) {
                diamond(g, x, y, 5, 0x0AFFFFFF);
            }
        }
    }

    // ---- the page --------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        hover = null;
        renderBackground(g, mx, my, pt);
        int accent = accent();

        int vmx = (int) (mx / scale), vmy = (int) (my / scale);
        g.pose().pushPose();
        g.pose().scale((float) scale, (float) scale, 1f);
        render(g, vmx, vmy, accent);
        g.pose().popPose();

        if (hover != null) g.renderTooltip(font, Component.literal(hover), mx, my);
    }

    private void render(GuiGraphics g, int mx, int my, int accent) {
        shell(g, accent);
        head(g, mx, my, accent);
        rail(g, mx, my, accent);
        faces(g, mx, my, accent);
        foot(g, mx, my);
    }

    /** The bound volume: a bronze case, the accent panel inside it, a spine at the left. */
    private void shell(GuiGraphics g, int accent) {
        g.fill(left, top, right, bottom, CASE);
        g.renderOutline(left, top, right - left, bottom - top, BRONZE);
        g.fill(left + 6, top + 6, right - 6, bottom - 6, PANEL);
        g.renderOutline(left + 6, top + 6, right - left - 12, bottom - top - 12, accent);
        lattice(g, left + 6, top + 6, right - 6, bottom - 6);

        g.fill(left + 6, top + 6, left + 30, bottom - 6, SPINE);
        g.fill(left + 30, top + 6, left + 31, bottom - 6, BRONZE);
        for (int y = top + 52, i = 0; y < bottom - 40; y += 54, i++) {
            diamond(g, left + 18, y, 4, i % 2 == 0 ? GOLD : BRONZE);
        }

        int arm = 96;
        for (int[] c : new int[][]{{left + 6, top + 6, 1, 1}, {right - 6, top + 6, -1, 1},
                                   {left + 6, bottom - 6, 1, -1}, {right - 6, bottom - 6, -1, -1}}) {
            int x = c[0], y = c[1], dx = c[2], dy = c[3];
            g.fill(Math.min(x, x + dx * arm), y - 1, Math.max(x, x + dx * arm), y + 1, GOLD);
            g.fill(x - 1, Math.min(y, y + dy * arm / 2), x + 1, Math.max(y, y + dy * arm / 2), GOLD);
            diamond(g, x + dx * arm, y, 4, GOLD);
        }
    }

    private void head(GuiGraphics g, int mx, int my, int accent) {
        slant(g, left + 31, top + 7, right - left - 38, 54, 0, PANEL_2);
        slant(g, left + 31, top + 7, Math.min(330, (right - left) / 3), 54, 20,
                (accent & 0x00FFFFFF) | 0x33000000);
        slant(g, left + 31, top + 59, right - left - 38, 2, 0, GOLD);

        int gi = Tarot.gooOf(path());
        g.drawString(font, Tarot.GOO_NAMES[gi].toUpperCase(Locale.ROOT), left + 48, top + 15, GREY_D, false);
        g.drawString(font, Tarot.pretty(path()) + " Pathway", left + 48, top + 29, GOLD_HI, true);

        boolean over = mx >= right - 190 && mx < right - 16 && my >= top + 10 && my < top + 50;
        String label = shiny ? "SHINY FACES" : "NORMAL FACES";
        g.drawString(font, label, right - 22 - font.width(label), top + 16, shiny ? GOLD_HI : GREY, false);
        String count = held(path()) + " / 10";
        g.drawString(font, count, right - 22 - font.width(count), top + 30, over ? GOLD_HI : GOLD, false);
        if (over) hover = "Turn to the " + (shiny ? "normal" : "shiny") + " faces";
    }

    private int held(String pathway) {
        int n = 0;
        for (int s = 0; s <= 9; s++) if (have.getOrDefault(Binder.key(pathway, s, shiny), 0) > 0) n++;
        return n;
    }

    /** The whole collection: every pathway under its Great Old One, with a bar each. */
    private void rail(GuiGraphics g, int mx, int my, int accent) {
        g.drawString(font, "THE COLLECTION", railX, top + 74, accent, false);
        g.fill(railX + 92, top + 78, railX + railW - 10, top + 79, BRONZE);
        diamond(g, railX + railW - 6, top + 78, 3, GOLD);

        int y = top + 96;
        for (int gi = 0; gi < Tarot.GOO_IDS.length; gi++) {
            g.drawString(font, Tarot.GOO_NAMES[gi].toUpperCase(Locale.ROOT), railX, y, 0xFF43404E, false);

            String gk = Binder.key("goo_" + Tarot.GOO_IDS[gi], 0, shiny);
            int gn = have.getOrDefault(gk, 0);
            int gx = railX + railW - 22;
            boolean gOver = mx >= gx && mx < gx + 14 && my >= y - 2 && my < y + 16;
            if (gn > 0) {
                g.blit(CardArt.ready("goo_" + Tarot.GOO_IDS[gi], 0, shiny), gx, y - 2, 0, 0,
                        railHead * 2 / 3, railHead, railHead * 2 / 3, railHead);
                g.renderOutline(gx, y - 2, railHead * 2 / 3, railHead, gOver ? GOLD_HI : BRONZE);
            } else {
                g.fill(gx, y - 2, gx + railHead * 2 / 3, y + railHead - 2, 0xFF100E14);
                g.renderOutline(gx, y - 2, railHead * 2 / 3, railHead, gOver ? BRONZE : 0xFF231F2C);
            }
            if (gOver) hover = Tarot.GOO_NAMES[gi] + (gn > 0 ? "  (" + gn + ")" : "  - missing");
            y += railHead;

            for (String p : Tarot.GOO_PATHS[gi]) {
                boolean on = p.equals(path());
                boolean over = mx >= railX - 8 && mx <= railX + railW && my >= y - 2 && my < y + railLine - 2;
                if (on) {
                    slant(g, railX - 8, y - 3, railW + 6, railLine - 1, 5, 0xFF241C08);
                    g.renderOutline(railX - 8, y - 3, railW + 6, railLine - 1, GOLD);
                }
                g.drawString(font, Tarot.pretty(p), railX, y + 2, on ? GOLD_HI : over ? GREY : 0xFF8F8AA0, false);

                int got = held(p);
                int bx = railX + railW - 116, bw = 74;
                int by = y + railLine / 2 - 3;
                g.fill(bx, by, bx + bw, by + 6, 0xFF0C0B11);
                g.renderOutline(bx, by, bw, 6, 0xFF2A2632);
                if (got > 0) g.fill(bx, by, bx + bw * got / 10, by + 6, got == 10 ? GOLD : accentOf(p));
                String n = String.valueOf(got);
                // right-aligned against the rail's edge, not the bar's: a two digit 10
                // right-aligned ten pixels past a 78px bar lands back on top of it
                g.drawString(font, n, railX + railW - 2 - font.width(n), y + 2,
                        got == 10 ? GOLD : GREY_D, false);
                y += railLine;
            }
            y += railGap;
        }
    }

    /** The ten faces of the open pathway. */
    private void faces(GuiGraphics g, int mx, int my, int accent) {
        for (int i = 0; i < 10; i++) {
            int seq = 9 - i, x = cardX(i), y = cardY(i);
            int n = have.getOrDefault(Binder.key(path(), seq, shiny), 0);
            boolean over = mx >= x && mx < x + cw && my >= y && my < y + ch;

            g.fill(x - 2, y - 2, x + cw + 2, y + ch + 2, 0xFF050407);
            if (n > 0) {
                g.blit(CardArt.ready(path(), seq, shiny), x, y, 0, 0, cw, ch, cw, ch);
                g.renderOutline(x, y, cw, ch, shiny ? GOLD_HI : BRONZE);
                if (n > 1) {
                    String c = String.valueOf(n);
                    g.drawString(font, c, x + cw - 4 - font.width(c), y + ch - 11, GOLD_HI, true);
                }
            } else {
                g.fill(x, y, x + cw, y + ch, 0xFF100E14);
                for (int d = 6; d < Math.min(cw, ch) - 6; d++) {
                    g.fill(x + d, y + d, x + d + 1, y + d + 1, 0xFF191520);
                    g.fill(x + cw - 1 - d, y + d, x + cw - d, y + d + 1, 0xFF191520);
                }
                String s = String.valueOf(seq);
                g.drawString(font, s, x + (cw - font.width(s)) / 2, y + ch / 2 - 4, 0xFF2A2636, false);
                g.renderOutline(x, y, cw, ch, 0xFF1C1A24);
            }
            if (over) {
                g.renderOutline(x, y, cw, ch, accent);
                hover = (shiny ? "Shiny " : "") + Tarot.pretty(path()) + " - Seq " + seq
                        + (n > 0 ? "  (" + n + ")" : "  - missing");
            }

            String name = seqName(path(), seq).toUpperCase(Locale.ROOT);
            g.drawString(font, name, x + (cw - font.width(name)) / 2, y + ch + 8, n > 0 ? GOLD : GREY_D, false);
            String s = "SEQUENCE " + seq;
            g.drawString(font, s, x + (cw - font.width(s)) / 2, y + ch + 20, 0xFF43404E, false);
        }
    }

    private void foot(GuiGraphics g, int mx, int my) {
        int y = bottom - 46;
        slant(g, left + 31, y, right - left - 38, 40, 0, PANEL_2);
        slant(g, left + 31, y, right - left - 38, 1, 0, BRONZE);

        if (page > 0) {
            boolean over = mx >= left + 40 && mx < left + 240 && my >= bottom - 44 && my < bottom - 12;
            g.drawString(font, "< " + Tarot.pretty(ORDER[page - 1]),
                    left + 46, y + 16, over ? GOLD_HI : GREY_D, false);
        }
        if (page < ORDER.length - 1) {
            String s = Tarot.pretty(ORDER[page + 1]) + " >";
            boolean over = mx >= right - 240 && mx < right - 40 && my >= bottom - 44 && my < bottom - 12;
            g.drawString(font, s, right - 46 - font.width(s), y + 16, over ? GOLD_HI : GREY_D, false);
        }
        int c = (left + right) / 2;
        boolean over = mx >= c - 110 && mx < c + 110 && my >= bottom - 44 && my < bottom - 12;
        slant(g, c - 110, y + 8, 220, 22, 8, over ? 0xFF2E2510 : 0xFF221B0A);
        g.renderOutline(c - 110, y + 8, 220, 22, over ? GOLD_HI : GOLD);
        String file = "FILE LOOSE CARDS";
        g.drawString(font, file, c - font.width(file) / 2, y + 15, over ? GOLD_HI : GOLD, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
