package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * The card binder, as a book rather than a menu.
 *
 * <p>One spread per Great Old One: its own card in a socket on the left, then a row for
 * each of its pathways running Sequence 9 down to 0 across two leaves with a gutter
 * between them. A held face shows its art, a missing one is a cut socket, so a page reads
 * as a bar chart of how far someone has got without anyone counting.
 *
 * <p>Drawn in the visual language of the mod's own screens: a gold border with the
 * pathway colour punctuating it, gold diamonds on the rails, slanted nameplates, and a
 * diamond lattice over near-black. The accent follows the player's own pathway, so a
 * Sailor's binder is blue and a Seer's purple; gold where they are not a Beyonder.
 *
 * <p>Clicking a held card takes it out immediately. Changing chapter peels the page.
 */
public final class BinderScreen extends Screen {

    // ---- the palette, shared with the rest of the mod's screens -------------------
    private static final int INK     = 0xFF0A080C;
    private static final int PANEL   = 0xFF141018;
    private static final int PANEL_2 = 0xFF1C1822;
    private static final int GOLD    = 0xFFD4AF37;
    private static final int GOLD_HI = 0xFFF3DC8A;
    private static final int BRONZE  = 0xFF7A5C1E;
    private static final int GREY    = 0xFFA0A8B4;
    private static final int GREY_D  = 0xFF5A606C;

    /** How long a page takes to peel across, in milliseconds. */
    private static final long PEEL_MS = 260;

    private final Map<String, Integer> have = new HashMap<>();
    private int tab = 0;
    private boolean shiny = false;
    private String hover = null;

    /** Geometry, worked out in init() so the book fills whatever window it is given. */
    private int left, top, right, bottom, mid, cw, ch, rowH, gridTop, leafGap;

    /** Page peel: where it came from, and when it started. */
    private int peelFrom = -1;
    private long peelAt = 0;
    private int peelDir = 1;

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
        if (now instanceof BinderScreen b) { b.load(packed); return; }   // a refresh after taking a card keeps the page
        Minecraft.getInstance().setScreen(new BinderScreen(packed));
    }

    private String[] rows() { return Tarot.GOO_PATHS[tab]; }

    // ---- the accent ---------------------------------------------------------------

    /** LOTM's own colour for each pathway, as its getColorFormatting returns. */
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

    /**
     * The player's own pathway, read from LOTM's attachment on the client.
     *
     * <p>Shares {@link Launcher#readPathway} rather than naming a key, because LOTM has
     * renamed that field before. Gold when they are not a Beyonder.
     */
    private int accent() {
        var player = Minecraft.getInstance().player;
        if (player == null) return GOLD;
        try {
            String pathway = Launcher.readPathway(player.saveWithoutId(new CompoundTag()));
            return pathway.isEmpty() ? GOLD : accentOf(pathway);
        } catch (Throwable ignored) {
            return GOLD;
        }
    }

    // ---- geometry -------------------------------------------------------------------

    @Override
    protected void init() {
        int tallest = 1;
        for (String[] g : Tarot.GOO_PATHS) tallest = Math.max(tallest, g.length);

        left = 18; right = width - 46; top = 14; bottom = height - 14;   // room for the tabs on the right
        leafGap = 18;
        mid = (left + right) / 2;

        int marginL = 104;                       // the pathway label column
        int usable = (right - left) - marginL - leafGap - 24;
        cw = Math.max(18, Math.min(62, usable / 10));
        ch = cw * 3 / 2;
        rowH = ch + 10;

        gridTop = top + 62;
        int needed = gridTop + tallest * rowH + 44;
        if (needed > bottom) {                   // shrink the cards rather than overflow
            ch = Math.max(21, (bottom - gridTop - 44) / tallest - 10);
            cw = ch * 2 / 3;
            rowH = ch + 10;
        }
    }

    private int gridLeft() { return left + 104; }

    /** The x of a column: 9..5 on the left leaf, 4..0 on the right. */
    private int colX(int c) {
        return c < 5 ? gridLeft() + c * cw : gridLeft() + c * cw + leafGap;
    }

    private int[] slotAt(double mx, double my) {
        int r = (int) Math.floor((my - gridTop) / (double) rowH);
        if (r < 0 || r >= rows().length) return null;
        for (int c = 0; c < 10; c++) {
            int x = colX(c);
            if (mx >= x && mx < x + cw - 3 && my >= gridTop + r * rowH && my < gridTop + r * rowH + ch) {
                return new int[]{r, c};
            }
        }
        return null;
    }

    // ---- input -----------------------------------------------------------------------

    private void goTo(int chapter) {
        if (chapter == tab || chapter < 0 || chapter >= Tarot.GOO_IDS.length) return;
        peelDir = chapter > tab ? 1 : -1;
        peelFrom = tab;
        peelAt = System.currentTimeMillis();
        tab = chapter;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // the chapter tabs down the right edge
        for (int i = 0; i < Tarot.GOO_IDS.length; i++) {
            int ty = tabTop(i);
            if (mx >= right + 2 && mx < right + 40 && my >= ty && my < ty + tabH()) { goTo(i); return true; }
        }
        // the foot strip
        if (my >= bottom - 26 && my < bottom - 4) {
            if (mx >= left + 10 && mx < left + 150) { goTo(tab - 1); return true; }
            if (mx >= right - 150 && mx < right - 10) { goTo(tab + 1); return true; }
            if (mx >= mid - 84 && mx < mid + 84) {
                PacketDistributor.sendToServer(new Net.BinderActPayload(1, ""));
                return true;
            }
        }
        // the shiny toggle, on the head
        if (mx >= right - 150 && mx < right - 10 && my >= top + 8 && my < top + 30) { shiny = !shiny; return true; }

        // the Great Old One's own card
        if (mx >= left + 14 && mx < left + 14 + cw && my >= gridTop && my < gridTop + ch) {
            take(Binder.key("goo_" + Tarot.GOO_IDS[tab], 0, shiny));
            return true;
        }
        int[] s = slotAt(mx, my);
        if (s != null) { take(Binder.key(rows()[s[0]], 9 - s[1], shiny)); return true; }
        return super.mouseClicked(mx, my, button);
    }

    private void take(String key) {
        if (have.getOrDefault(key, 0) > 0) PacketDistributor.sendToServer(new Net.BinderActPayload(0, key));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 263) { goTo(tab - 1); return true; }   // left arrow
        if (key == 262) { goTo(tab + 1); return true; }   // right arrow
        return super.keyPressed(key, scan, mods);
    }

    private int tabH() { return Math.min(44, (bottom - top - 20) / Tarot.GOO_IDS.length); }
    private int tabTop(int i) { return top + 24 + i * (tabH() + 4); }

    // ---- drawing helpers ---------------------------------------------------------------

    /** A parallelogram, the shape the mod's nameplates and bands are built from. */
    private void slant(GuiGraphics g, int x, int y, int w, int h, int skew, int colour) {
        for (int i = 0; i < h; i++) {
            int off = h <= 1 ? 0 : skew * (h - 1 - i) / (h - 1);
            g.fill(x + off, y + i, x + off + w, y + i + 1, colour);
        }
    }

    /** A small diamond, as sits on every rail in the game's panels. */
    private void diamond(GuiGraphics g, int cx, int cy, int r, int colour) {
        for (int i = -r; i <= r; i++) {
            int half = r - Math.abs(i);
            g.fill(cx - half, cy + i, cx + half + 1, cy + i + 1, colour);
        }
    }

    /**
     * The faint diamond lattice the mod's panels carry.
     *
     * <p>Drawn as sparse diamonds rather than full diagonal lines: a line-by-line weave
     * across a window this size is tens of thousands of quads every frame, and this reads
     * the same from where the player sits.
     */
    private void lattice(GuiGraphics g, int x0, int y0, int x1, int y1, int colour) {
        final int step = 46;
        for (int y = y0 + 14; y < y1 - 6; y += step) {
            for (int x = x0 + 14 + ((y - y0) / step % 2) * step / 2; x < x1 - 6; x += step) {
                diamond(g, x, y, 6, colour);
            }
        }
    }

    // ---- one slot -----------------------------------------------------------------------

    private void slot(GuiGraphics g, int x, int y, String pathway, int seq, int mx, int my, int accent) {
        String k = Binder.key(pathway, seq, shiny);
        int n = have.getOrDefault(k, 0);
        int w = cw - 3, h = ch - 3;
        boolean over = mx >= x && mx < x + w && my >= y && my < y + h;
        boolean goo = pathway.startsWith("goo_");

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF050407);   // the recess it sits in
        if (n > 0) {
            g.blit(CardArt.ready(pathway, seq, shiny), x, y, 0, 0, w, h, w, h);
            g.renderOutline(x, y, w, h, shiny ? GOLD_HI : BRONZE);
            if (n > 1) {
                String c = String.valueOf(n);
                g.drawString(font, c, x + w - 3 - font.width(c), y + h - 9, GOLD_HI, true);
            }
        } else {
            g.fill(x, y, x + w, y + h, 0xFF100E14);
            // a cut socket: the cross says "nothing here" without a letter to read
            for (int i = 3; i < Math.min(w, h) - 3; i++) {
                g.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFF191520);
                g.fill(x + w - 1 - i, y + i, x + w - i, y + i + 1, 0xFF191520);
            }
            g.renderOutline(x, y, w, h, 0xFF1C1A24);
        }
        if (over) {
            g.renderOutline(x, y, w, h, accent);
            hover = (shiny ? "Shiny " : "")
                  + (goo ? Tarot.pretty(pathway.substring(4)) : Tarot.pretty(pathway) + " - Seq " + seq)
                  + (n > 0 ? "  (" + n + ")" : "  - missing");
        }
    }

    // ---- the page ------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        hover = null;
        renderBackground(g, mx, my, pt);
        int accent = accent();

        // the book itself
        g.fill(left, top, right, bottom, PANEL);
        lattice(g, left + 1, top + 1, right - 1, bottom - 1, 0x11FFFFFF);
        frame(g, accent);

        head(g, mx, my, accent);
        leaves(g, mx, my, accent);
        foot(g, mx, my, accent);
        tabs(g, mx, my, accent);
        peel(g);

        if (hover != null) g.renderTooltip(font, Component.literal(hover), mx, my);
    }

    /** Gold border, the pathway colour punctuating it, diamonds on the rails. */
    private void frame(GuiGraphics g, int accent) {
        g.renderOutline(left, top, right - left, bottom - top, accent);
        int railW = Math.min(150, (right - left) / 4);
        g.fill(mid - railW / 2, top, mid + railW / 2, top + 1, GOLD);
        g.fill(mid - railW / 2, bottom - 1, mid + railW / 2, bottom, GOLD);
        int railH = Math.min(150, (bottom - top) / 3);
        g.fill(left, (top + bottom) / 2 - railH / 2, left + 1, (top + bottom) / 2 + railH / 2, GOLD);
        g.fill(right - 1, (top + bottom) / 2 - railH / 2, right - 1 + 1, (top + bottom) / 2 + railH / 2, GOLD);

        for (int[] c : new int[][]{{left, top}, {right, top}, {left, bottom}, {right, bottom}}) {
            diamond(g, c[0], c[1], 3, GOLD);
        }
        diamond(g, mid, top, 5, GOLD);
        diamond(g, mid, top, 2, accent);
        diamond(g, mid, bottom, 5, GOLD);
        diamond(g, mid, bottom, 2, accent);
        diamond(g, left, (top + bottom) / 2, 4, GOLD);
        diamond(g, right, (top + bottom) / 2, 4, GOLD);

        // the gutter: two leaves, not one panel
        int gx = gridLeft() + 5 * cw + leafGap / 2 - 1;
        g.fillGradient(gx - 7, top + 48, gx, bottom - 30, 0x00000000, 0x66000000);
        g.fillGradient(gx, top + 48, gx + 7, bottom - 30, 0x66000000, 0x00000000);
    }

    private void head(GuiGraphics g, int mx, int my, int accent) {
        slant(g, left + 1, top + 1, right - left - 2, 44, 0, PANEL_2);
        slant(g, left + 1, top + 1, Math.min(300, (right - left) / 2), 44, 20, 0xFF221C2C);
        slant(g, left + 1, top + 42, right - left - 2, 2, 0, GOLD);

        g.drawString(font, roman(tab + 1), left + 16, top + 16, BRONZE, false);
        g.drawString(font, Tarot.GOO_NAMES[tab], left + 42, top + 12, GOLD_HI, true);
        StringBuilder paths = new StringBuilder();
        for (String p : rows()) paths.append(paths.length() == 0 ? "" : "  ·  ").append(Tarot.pretty(p));
        g.drawString(font, paths.toString(), left + 42, top + 26, GREY_D, false);

        // the shiny toggle, where the game puts its page label
        int tx = right - 150, ty = top + 8;
        boolean over = mx >= tx && mx < right - 10 && my >= ty && my < ty + 22;
        String label = shiny ? "SHINY FACES" : "NORMAL FACES";
        g.drawString(font, label, right - 12 - font.width(label), ty + 2, shiny ? GOLD_HI : GREY, false);
        String count = held() + " / " + Binder.faces();
        g.drawString(font, count, right - 12 - font.width(count), ty + 13, over ? GOLD_HI : GOLD, false);
        if (over) hover = "Click to turn to the " + (shiny ? "normal" : "shiny") + " faces";
    }

    private int held() {
        int n = 0;
        for (Map.Entry<String, Integer> e : have.entrySet()) {
            if (e.getValue() > 0 && e.getKey().endsWith("s") == shiny) n++;
        }
        return n;
    }

    private void leaves(GuiGraphics g, int mx, int my, int accent) {
        // the Great Old One's own card, in a socket of its own
        slot(g, left + 14, gridTop, "goo_" + Tarot.GOO_IDS[tab], 0, mx, my, accent);

        String[] rs = rows();
        for (int r = 0; r < rs.length; r++) {
            int y = gridTop + r * rowH;
            int complete = 0;
            for (int c = 0; c < 10; c++) if (have.getOrDefault(Binder.key(rs[r], 9 - c, shiny), 0) > 0) complete++;
            boolean full = complete == 10;

            // the row's nameplate, lit when the row is finished
            if (full) slant(g, left + 10, y + ch / 2 - 13, 88, 26, 7, 0xFF241C08);
            g.drawString(font, Tarot.pretty(rs[r]), left + 16, y + ch / 2 - 9, full ? GOLD_HI : GREY_D, false);
            g.drawString(font, full ? "COMPLETE" : complete + " / 10", left + 16, y + ch / 2 + 1,
                    full ? GOLD : 0xFF3A3E48, false);
            if (full) g.renderOutline(left + 10, y + ch / 2 - 13, 88, 26, GOLD);

            for (int c = 0; c < 10; c++) slot(g, colX(c), y, rs[r], 9 - c, mx, my, accent);
        }
        // the sequence numbers along the head of the leaves
        for (int c = 0; c < 10; c++) {
            String s = String.valueOf(9 - c);
            g.drawString(font, s, colX(c) + (cw - 3 - font.width(s)) / 2, gridTop - 11, 0xFF55506A, false);
        }
    }

    private void foot(GuiGraphics g, int mx, int my, int accent) {
        int y = bottom - 28;
        slant(g, left + 1, y, right - left - 2, 27, 0, PANEL_2);
        slant(g, left + 1, y, right - left - 2, 1, 0, BRONZE);

        if (tab > 0) {
            boolean over = mx >= left + 10 && mx < left + 150 && my >= bottom - 26 && my < bottom - 4;
            g.drawString(font, "< " + Tarot.GOO_NAMES[tab - 1], left + 14, y + 10, over ? GOLD_HI : GREY_D, false);
        }
        if (tab < Tarot.GOO_IDS.length - 1) {
            String s = Tarot.GOO_NAMES[tab + 1] + " >";
            boolean over = mx >= right - 150 && mx < right - 10 && my >= bottom - 26 && my < bottom - 4;
            g.drawString(font, s, right - 14 - font.width(s), y + 10, over ? GOLD_HI : GREY_D, false);
        }
        boolean over = mx >= mid - 84 && mx < mid + 84 && my >= bottom - 26 && my < bottom - 4;
        slant(g, mid - 84, y + 4, 168, 19, 7, over ? 0xFF2E2510 : 0xFF221B0A);
        g.renderOutline(mid - 84, y + 4, 168, 19, over ? GOLD_HI : GOLD);
        String file = "FILE LOOSE CARDS";
        g.drawString(font, file, mid - font.width(file) / 2, y + 10, over ? GOLD_HI : GOLD, false);
    }

    /** The chapter tabs, each carrying that Great Old One's own face. */
    private void tabs(GuiGraphics g, int mx, int my, int accent) {
        int h = tabH();
        for (int i = 0; i < Tarot.GOO_IDS.length; i++) {
            int y = tabTop(i);
            boolean on = i == tab;
            boolean over = mx >= right + 2 && mx < right + 40 && my >= y && my < y + h;
            slant(g, right + 2, y, 36, h, 8, on ? 0xFF2C240C : over ? 0xFF201B2A : 0xFF16131A);
            g.renderOutline(right + 2, y, 36, h, on ? GOLD : over ? BRONZE : 0xFF2A2632);

            int s = Math.min(h - 8, 26);
            ResourceLocation art = CardArt.ready("goo_" + Tarot.GOO_IDS[i], 0, false);
            g.blit(art, right + 8, y + (h - s) / 2, 0, 0, s * 2 / 3, s, s * 2 / 3, s);
            if (on) diamond(g, right + 38, y + h / 2, 3, GOLD);
            if (over) hover = Tarot.GOO_NAMES[i];
        }
    }

    /** A page peeling across when the chapter changes. */
    private void peel(GuiGraphics g) {
        if (peelFrom < 0) return;
        long dt = System.currentTimeMillis() - peelAt;
        if (dt >= PEEL_MS) { peelFrom = -1; return; }

        float t = dt / (float) PEEL_MS;
        int x0 = left + 1, x1 = right - 1;
        int edge = peelDir > 0
                ? (int) (x1 - (x1 - x0) * t)      // sweeping left, uncovering the new page
                : (int) (x0 + (x1 - x0) * t);

        if (peelDir > 0) {
            g.fill(edge, top + 46, x1, bottom - 29, 0xF00E0C14);
            g.fillGradient(edge, top + 46, Math.min(edge + 18, x1), bottom - 29, 0x99000000, 0x00000000);
            g.fill(edge, top + 46, edge + 1, bottom - 29, GOLD_HI);
        } else {
            g.fill(x0, top + 46, edge, bottom - 29, 0xF00E0C14);
            g.fillGradient(Math.max(edge - 18, x0), top + 46, edge, bottom - 29, 0x00000000, 0x99000000);
            g.fill(edge - 1, top + 46, edge, bottom - 29, GOLD_HI);
        }
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX";
            default -> String.valueOf(n);
        };
    }

    @Override public boolean isPauseScreen() { return false; }
}
