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

    /** The canvas the page is laid out on; the window is fitted to it. */
    private static final int DESIGN_W = 1000, DESIGN_H = 600;   // a smaller canvas fitted to the window: everything on it comes out bigger

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
    /** A page is either a Great Old One's own, or one of its pathways. */
    private record Page(int goo, String path) {
        boolean isChapter() { return path == null; }
    }

    /** The admin page sits one past the last chapter: it belongs to no Great Old One. */
    private static final int ADMIN_GOO = Tarot.GOO_PATHS.length;
    /** Eleven places: the Bearer of Sins, and ten kept for whoever comes after him. */
    private static final int ADMIN_SLOTS = 11;

    private static final Page[] ORDER = order();

    private static Page[] order() {
        java.util.List<Page> out = new java.util.ArrayList<>();
        for (int g = 0; g < Tarot.GOO_PATHS.length; g++) {
            out.add(new Page(g, null));                       // the chapter itself
            for (String p : Tarot.GOO_PATHS[g]) out.add(new Page(g, p));
        }
        out.add(new Page(ADMIN_GOO, "admin"));                // and the admin leaf at the back
        return out.toArray(new Page[0]);
    }

    private boolean isAdmin() { return here().goo() == ADMIN_GOO; }
    private static int adminPage() { return ORDER.length - 1; }

    /** How many of the admin cards are held, and which. */
    private int adminHeld(int i) {
        return have.getOrDefault(Binder.key("admin_" + Tarot.ADMIN_IDS[i], 0, false), 0)
             + have.getOrDefault(Binder.key("admin_" + Tarot.ADMIN_IDS[i], 0, true), 0);
    }

    private int adminCount() {
        int n = 0;
        for (int i = 0; i < Tarot.ADMIN_IDS.length; i++) if (adminHeld(i) > 0) n++;
        return n;
    }

    /**
     * White until one is held, and then the colour of what is held: with one admin card in the
     * book it takes that card's colour, and with several it is their average.
     */
    private int adminAccent() {
        int r = 0, g2 = 0, b = 0, n = 0;
        for (int i = 0; i < Tarot.ADMIN_IDS.length; i++) {
            if (adminHeld(i) <= 0) continue;
            int c = Tarot.ADMIN_COLOURS[i];
            r += (c >> 16) & 255; g2 += (c >> 8) & 255; b += c & 255; n++;
        }
        return n == 0 ? 0xFFFFFFFF : 0xFF000000 | ((r / n) << 16) | ((g2 / n) << 8) | (b / n);
    }

    private Page here() { return ORDER[page]; }

    private String title(Page p) {
        return p.isChapter() ? Tarot.GOO_NAMES[p.goo()] : Tarot.pretty(p.path());
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX";
            default -> String.valueOf(n);
        };
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

    private String path() { return here().path(); }

    // ---- colour ---------------------------------------------------------------------

    private static int accentOf(String pathway) {
        return switch (pathway) {
            case "spectator" -> 0xFF55FFFF;
            case "bard" -> 0xFFFFFF55;
            case "warrior" -> 0xFFFF8A1E;                 // orange, not the Bard's yellow
            case "sailor" -> 0xFF5555FF;
            case "apprentice" -> 0xFF6FA2FF;              // a lighter blue than the Tyrant's
            case "marauder" -> 0xFF9B82B8;                // purple and grey, which is what the Error is
            case "mystery_pryer", "prisoner" -> 0xFFAA00AA;
            case "seer" -> 0xFFC566E6;                    // the Fool, in a lighter purple
            case "secrets_supplicant", "hunter", "criminal" -> 0xFFAA0000;
            case "assassin" -> 0xFFFF55FF;
            case "corpse_collector", "planter" -> 0xFF55FF55;
            case "apothecary" -> 0xFF00AA00;
            case "lawyer" -> 0xFF2A2ACC;                  // slightly lighter: it was almost black on the page
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
        // The page is drawn on a canvas of a fixed size and that canvas is fitted to the
        // window, so it looks the same whatever the GUI scale and whatever the monitor.
        //
        // Scaling against the GUI setting instead, as this did, gets it wrong at both
        // ends: huge at scale 4, and tiny on a large window at scale 1.
        scale = Math.min(width / (double) DESIGN_W, height / (double) DESIGN_H);
        scale = Math.max(0.25, Math.min(3.0, scale));
        vw = (int) (width / scale);
        vh = (int) (height / scale);

        left = 16; top = 14; right = vw - 16; bottom = vh - 14;
        railX = left + 46;                                     // past the spine
        railW = Math.max(160, Math.min(236, (right - left) / 5));

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
        railLine = Math.max(12, Math.min(19, (railSpace - heads * 17 - heads * 4) / lines));
        railHead = Math.max(12, Math.min(18, railLine));
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
            for (int i = 0; i < ORDER.length; i++) {
                if (ORDER[i].isChapter() && ORDER[i].goo() == hit[1]) { goTo(i); return true; }
            }
            return true;
        }
        if (here().isChapter()) {
            if (mx >= chapterCardX() && mx < chapterCardX() + chapterCardW()
                    && my >= chapterCardY() && my < chapterCardY() + chapterCardH()) {
                take(Binder.key("goo_" + Tarot.GOO_IDS[here().goo()], 0, shiny));
                return true;
            }
            String[] ps = Tarot.GOO_PATHS[here().goo()];
            for (int i = 0; i < ps.length; i++) {
                int ly = chapterCardY() + 18 + i * 30;
                if (my >= ly && my < ly + 26 && mx >= chapterListX() && mx < right - 40) {
                    for (int k = 0; k < ORDER.length; k++) {
                        if (ps[i].equals(ORDER[k].path())) { goTo(k); return true; }
                    }
                }
            }
        } else if (isAdmin()) {
            int percol = 6, aw = Math.max(34, Math.min(cw, (right - 24 - gridX - colGap * (percol - 1)) / percol));
            int ah = aw * 3 / 2;
            for (int i = 0; i < Tarot.ADMIN_IDS.length; i++) {
                int x = gridX + (i % percol) * (aw + colGap), y = gridY + (i / percol) * (ah + rowGap);
                if (mx >= x && mx < x + aw && my >= y && my < y + ah) {
                    take(Binder.key("admin_" + Tarot.ADMIN_IDS[i], 0, false));
                    return true;
                }
            }
        } else {
            for (int i = 0; i < 10; i++) {
                int x = cardX(i), y = cardY(i);
                if (mx >= x && mx < x + cw && my >= y && my < y + ch) {
                    take(Binder.key(path(), 9 - i, shiny));
                    return true;
                }
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
            if (my >= y && my < y + railHead) return new int[]{1, g};
            y += railHead;
            for (String p : Tarot.GOO_PATHS[g]) {
                if (my >= y && my < y + railLine - 1) {
                    for (int i = 0; i < ORDER.length; i++) {
                        if (p.equals(ORDER[i].path())) return new int[]{0, i};
                    }
                }
                y += railLine;
            }
            y += railGap;
        }
        if (my >= y && my < y + railHead) return new int[]{0, adminPage()};   // the admin leaf
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
        if (isAdmin()) adminFaces(g, mx, my);
        else if (here().isChapter()) chapter(g, mx, my, accent);
        else faces(g, mx, my, accent);
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

        int gi = here().goo();
        if (isAdmin()) {
            int ac = adminAccent();
            g.drawString(font, "ADMINISTRATION", left + 48, top + 15, GREY_D, false);
            g.drawString(font, "The Bearers", left + 48, top + 29, ac, true);
            String held = adminCount() + " / " + ADMIN_SLOTS;
            g.drawString(font, held, right - 22 - font.width(held), top + 30, ac, false);
            return;
        }
        if (here().isChapter()) {
            g.drawString(font, "CHAPTER " + roman(gi + 1), left + 48, top + 15, GREY_D, false);
            gradient(g, Tarot.GOO_NAMES[gi], left + 48, top + 29, gooColours(gi), gooHeld(gi), true);
        } else {
            gradient(g, Tarot.GOO_NAMES[gi].toUpperCase(Locale.ROOT), left + 48, top + 15,
                     gooColours(gi), gooHeld(gi), false);
            g.drawString(font, Tarot.pretty(path()) + " Pathway", left + 48, top + 29,
                         allShiny(path()) ? GOLD : accentOf(path()), true);
        }

        boolean over = mx >= right - 190 && mx < right - 16 && my >= top + 10 && my < top + 50;
        String label = shiny ? "SHINY FACES" : "NORMAL FACES";
        g.drawString(font, label, right - 22 - font.width(label), top + 16, shiny ? GOLD_HI : GREY, false);
        String count = here().isChapter() ? chapterHeld() + " / " + chapterTotal()
                                          : tally(held(path()), path());
        g.drawString(font, count, right - 22 - font.width(count), top + 30, over ? GOLD_HI : GOLD, false);
        if (over) hover = "Turn to the " + (shiny ? "normal" : "shiny") + " faces";
    }

    private int held(String pathway) { return heldOf(pathway, shiny); }

    private int heldOf(String pathway, boolean sh) {
        int n = 0;
        for (int s = 0; s <= 9; s++) if (have.getOrDefault(Binder.key(pathway, s, sh), 0) > 0) n++;
        return n;
    }

    /** A pathway whose ten shiny faces are all in: its name and its bar go gold, on either page. */
    private boolean allShiny(String pathway) { return heldOf(pathway, true) == 10; }

    /** Whether the Great Old One's own face has been found. */
    private boolean gooHeld(int gi) {
        return have.getOrDefault(Binder.key("goo_" + Tarot.GOO_IDS[gi], 0, false), 0) > 0
            || have.getOrDefault(Binder.key("goo_" + Tarot.GOO_IDS[gi], 0, true), 0) > 0;
    }

    /** The colours of a chapter: one per pathway under it. */
    private static int[] gooColours(int gi) {
        String[] ps = Tarot.GOO_PATHS[gi];
        int[] out = new int[Math.max(2, ps.length)];
        for (int i = 0; i < out.length; i++) out[i] = accentOf(ps[Math.min(i, ps.length - 1)]);
        return out;
    }

    private static int mixc(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** Samples a run of colours, wrapping, so a gradient can be slid along without a seam. */
    private static int sample(int[] cols, float t) {
        t = t - (float) Math.floor(t);
        float f = t * cols.length;
        int i = (int) f;
        return mixc(cols[i % cols.length], cols[(i + 1) % cols.length], f - i);
    }

    /**
     * A line of text written in a run of colours rather than one. Once the chapter's own face is in the binder the
     * run slides along the letters, so a finished chapter announces itself.
     */
    private void gradient(GuiGraphics g, String text, int x, int y, int[] cols, boolean moving, boolean shadow) {
        float slide = moving ? (System.currentTimeMillis() % 4000L) / 4000f : 0f;
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            float t = text.length() <= 1 ? 0f : i / (float) (text.length() - 1);
            g.drawString(font, ch, cx, y, sample(cols, t * 0.9f + slide), shadow);
            cx += font.width(ch);
        }
    }

    /**
     * A pathway's progress bar. The fill is always the pathway's own colour -- gold only
     * once all ten shiny faces are in -- and a brighter band slides along the filled part
     * so the bar is alive on the page. A finished bar breathes instead of sliding, which
     * reads as arrival rather than progress.
     */
    private void bar(GuiGraphics g, int x, int y, int w, int h, int got, String pathway) {
        boolean gold = allShiny(pathway), full = got == 10;
        int base = gold ? GOLD : accentOf(pathway);
        g.fill(x, y, x + w, y + h, 0xFF0C0B11);
        g.renderOutline(x, y, w, h, full ? dim(base, .55f) : 0xFF2A2632);
        if (got <= 0) return;
        int fw = Math.max(1, w * got / 10);
        g.fill(x, y, x + fw, y + h, base);
        float ph = (System.currentTimeMillis() % 2200L) / 2200f;
        if (full) {
            // a slow breath over the whole bar, strongest in the middle of the cycle
            float k = .55f + .45f * (float) Math.sin(ph * Math.PI * 2);
            g.fill(x, y, x + fw, y + h, (((int) (90 * k)) << 24) | (lift(base) & 0xFFFFFF));
        } else {
            // a three pixel highlight running the length of what has been collected
            int band = Math.max(2, fw / 7);
            int hx = x - band + (int) (ph * (fw + band));
            int a = Math.max(x, hx), b = Math.min(x + fw, hx + band);
            if (b > a) g.fill(a, y, b, y + h, 0x70000000 | (lift(base) & 0xFFFFFF));
        }
    }

    /** A colour pushed toward white, for the moving part of a bar. */
    private static int lift(int argb) {
        int r = (argb >> 16) & 255, gg = (argb >> 8) & 255, b = argb & 255;
        return 0xFF000000 | ((r + (255 - r) * 3 / 5) << 16) | ((gg + (255 - gg) * 3 / 5) << 8) | (b + (255 - b) * 3 / 5);
    }

    /** What a bar is labelled with: a count until it is full, and then what it means. */
    private String tally(int got, String pathway) {
        if (got < 10) return got + " / 10";
        return allShiny(pathway) ? "MASTERED" : shiny ? "ALL SHINY" : "COMPLETE";
    }

    /** A name trimmed to the room it has, so a long pathway never runs under the bar beside it. */
    private String fit(String name, int maxW) {
        if (font.width(name) <= maxW) return name;
        StringBuilder b = new StringBuilder(name);
        while (b.length() > 2 && font.width(b.toString() + ".") > maxW) b.setLength(b.length() - 1);
        return b.append('.').toString();
    }

    /** Dims a colour toward the page, for a row that is not the one being read. */
    private static int dim(int argb, float k) {
        int r = (int) (((argb >> 16) & 255) * k), gg = (int) (((argb >> 8) & 255) * k), b = (int) ((argb & 255) * k);
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    /** The whole collection: every pathway under its Great Old One, with a bar each. */
    private void rail(GuiGraphics g, int mx, int my, int accent) {
        g.drawString(font, "THE COLLECTION", railX, top + 74, accent, false);
        g.fill(railX + 92, top + 78, railX + railW - 10, top + 79, BRONZE);
        diamond(g, railX + railW - 6, top + 78, 3, GOLD);

        int y = top + 96;
        for (int gi = 0; gi < Tarot.GOO_IDS.length; gi++) {
            // Every row occupies exactly its line, and the text is centred in it. The
            // earlier offsets assumed a 19px line; the rail compresses to 11 on a short
            // window, where a box drawn three pixels high of the text overlapped the row
            // above it.
            boolean chapOn = here().isChapter() && here().goo() == gi;
            boolean chapOver = mx >= railX - 8 && mx <= railX + railW && my >= y && my < y + railHead;
            int ht = y + (railHead - 8) / 2;
            if (chapOn) {
                slant(g, railX - 8, y, railW + 6, railHead - 1, 4, 0xFF241C08);
                g.renderOutline(railX - 8, y, railW + 6, railHead - 1, GOLD);
            }
            boolean full = true;
            for (String p : Tarot.GOO_PATHS[gi]) if (!allShiny(p)) { full = false; break; }
            int[] cols = full ? new int[]{GOLD, GOLD_HI, GOLD} : gooColours(gi);
            float k = chapOn ? 1f : chapOver ? .85f : .6f;
            int[] shown = new int[cols.length];
            for (int c = 0; c < cols.length; c++) shown[c] = dim(cols[c], k);
            gradient(g, Tarot.GOO_NAMES[gi].toUpperCase(Locale.ROOT), railX, ht, shown, gooHeld(gi), false);
            int done = 0;
            for (String p : Tarot.GOO_PATHS[gi]) if (held(p) == 10) done++;
            String tally = done + "/" + Tarot.GOO_PATHS[gi].length;
            g.drawString(font, tally, railX + railW - 2 - font.width(tally), ht,
                    done == Tarot.GOO_PATHS[gi].length ? GOLD : 0xFF3A3744, false);
            if (chapOver) hover = Tarot.GOO_NAMES[gi] + " - chapter";
            y += railHead;

            for (String p : Tarot.GOO_PATHS[gi]) {
                boolean on = p.equals(path());
                boolean over = mx >= railX - 8 && mx <= railX + railW && my >= y && my < y + railLine - 1;
                int ty = y + (railLine - 9) / 2;
                if (on) {
                    slant(g, railX - 8, y, railW + 6, railLine - 1, 4, 0xFF241C08);
                    g.renderOutline(railX - 8, y, railW + 6, railLine - 1, GOLD);
                }
                boolean gold = allShiny(p);
                int base = gold ? GOLD : accentOf(p);
                // kept clear of the bar: "Secrets Supplicant" ran its last letter under it
                g.drawString(font, fit(Tarot.pretty(p), railW - 122), railX, ty, dim(base, on ? 1f : over ? .9f : .72f), false);

                int got = held(p);
                int bx = railX + railW - 116, bw = 74;
                int by = y + (railLine - 7) / 2;
                bar(g, bx, by, bw, 6, got, p);
                String n = String.valueOf(got);
                // right-aligned against the rail's edge, not the bar's: a two digit 10
                // right-aligned ten pixels past a 78px bar lands back on top of it
                g.drawString(font, n, railX + railW - 2 - font.width(n), ty,
                        gold ? GOLD : dim(base, got == 10 ? 1f : .8f), false);
                y += railLine;
            }
            y += railGap;
        }

        // the admin leaf, at the back of the book
        int ac = adminAccent();
        boolean aOn = isAdmin();
        boolean aOver = mx >= railX - 8 && mx <= railX + railW && my >= y && my < y + railHead;
        if (aOn || aOver) {
            slant(g, railX - 8, y, railW + 6, railHead - 1, 4, 0xFF241C08);
            g.renderOutline(railX - 8, y, railW + 6, railHead - 1, aOn ? GOLD : dim(ac, .6f));
        }
        int aty = y + (railHead - 9) / 2;
        g.drawString(font, "ADMIN", railX, aty, dim(ac, aOn ? 1f : aOver ? .9f : .72f), true);
        String atally = adminCount() + "/" + ADMIN_SLOTS;
        g.drawString(font, atally, railX + railW - 2 - font.width(atally), aty,
                     adminCount() > 0 ? ac : 0xFF3A3744, false);
        if (aOver) hover = "The Bearers";
    }

    private int chapterCardH() { return Math.min(bottom - 60 - (top + 96), 330); }
    private int chapterCardW() { return chapterCardH() * 2 / 3; }
    private int chapterCardX() { return gridX + 10; }
    private int chapterCardY() {
        return top + 96 + Math.max(0, (bottom - 60 - (top + 96) - chapterCardH()) / 2);
    }
    private int chapterListX() { return chapterCardX() + chapterCardW() + 36; }

    /** A chapter is its own face plus ten for each of its pathways. */
    private int chapterTotal() { return Tarot.GOO_PATHS[here().goo()].length * 10 + 1; }

    private int chapterHeld() {
        int n = have.getOrDefault(Binder.key("goo_" + Tarot.GOO_IDS[here().goo()], 0, shiny), 0) > 0 ? 1 : 0;
        for (String p : Tarot.GOO_PATHS[here().goo()]) n += held(p);
        return n;
    }

    /**
     * A chapter's own page: the Great Old One's face at the size it deserves, with the
     * chapter's pathways beside it as the way in.
     */
    private void chapter(GuiGraphics g, int mx, int my, int accent) {
        int gi = here().goo();
        String key = "goo_" + Tarot.GOO_IDS[gi];
        int n = have.getOrDefault(Binder.key(key, 0, shiny), 0);
        int x = chapterCardX(), y = chapterCardY(), w = chapterCardW(), h = chapterCardH();
        boolean over = mx >= x && mx < x + w && my >= y && my < y + h;

        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF050407);
        if (n > 0) {
            g.blit(CardArt.ready(key, 0, shiny), x, y, 0, 0, w, h, w, h);
            g.renderOutline(x, y, w, h, shiny ? GOLD_HI : GOLD);
            if (n > 1) {
                String c = String.valueOf(n);
                g.drawString(font, c, x + w - 5 - font.width(c), y + h - 12, GOLD_HI, true);
            }
        } else {
            g.fill(x, y, x + w, y + h, 0xFF100E14);
            for (int d = 10; d < Math.min(w, h) - 10; d++) {
                g.fill(x + d, y + d, x + d + 1, y + d + 1, 0xFF191520);
                g.fill(x + w - 1 - d, y + d, x + w - d, y + d + 1, 0xFF191520);
            }
            g.renderOutline(x, y, w, h, 0xFF231F2C);
        }
        if (over) {
            g.renderOutline(x, y, w, h, accent);
            hover = Tarot.GOO_NAMES[gi] + (n > 0 ? "  (" + n + ")" : "  - missing");
        }

        int lx = chapterListX();
        gradient(g, "PATHWAYS OF THIS CHAPTER", lx, y - 4, gooColours(gi), gooHeld(gi), false);
        g.fill(lx, y + 8, right - 40, y + 9, BRONZE);
        diamond(g, right - 36, y + 8, 3, GOLD);

        String[] ps = Tarot.GOO_PATHS[gi];
        for (int i = 0; i < ps.length; i++) {
            int ly = y + 18 + i * 30;
            boolean o = mx >= lx && mx < right - 40 && my >= ly && my < ly + 26;
            if (o) {
                slant(g, lx - 6, ly, right - 40 - lx + 6, 26, 6, 0xFF241C08);
                g.renderOutline(lx - 6, ly, right - 40 - lx + 6, 26, GOLD);
                hover = "Turn to " + Tarot.pretty(ps[i]);
            }
            int got = held(ps[i]);
            boolean gold = allShiny(ps[i]);
            g.drawString(font, Tarot.pretty(ps[i]), lx, ly + 5,
                         gold ? GOLD : dim(accentOf(ps[i]), got == 10 ? 1f : .8f), false);
            g.drawString(font, seqName(ps[i], 0), lx, ly + 16, 0xFF43404E, false);
            int bx = right - 210, bw = 130, by = ly + 10;
            bar(g, bx, by, bw, 7, got, ps[i]);
            String t = tally(got, ps[i]);
            g.drawString(font, t, right - 44 - font.width(t), ly + 7,
                         got == 10 ? (gold ? GOLD : lift(accentOf(ps[i]))) : GREY_D, false);
        }
        String note = "One face bears " + Tarot.GOO_NAMES[gi] + ". Its pathways hold ten each.";
        g.drawString(font, note, x, y + h + 12, 0xFF3A3744, false);
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

    /**
     * The admin leaf: the Bearer of Sins first, and ten places kept empty for whoever comes
     * after him. Six to a row, on the same cell as every other page.
     */
    private void adminFaces(GuiGraphics g, int mx, int my) {
        int ac = adminAccent();
        int percol = 6, aw = Math.max(34, Math.min(cw, (right - 24 - gridX - colGap * (percol - 1)) / percol));
        int ah = aw * 3 / 2;
        for (int i = 0; i < ADMIN_SLOTS; i++) {
            int x = gridX + (i % percol) * (aw + colGap), y = gridY + (i / percol) * (ah + rowGap);
            boolean real = i < Tarot.ADMIN_IDS.length;
            int n = real ? adminHeld(i) : 0;
            boolean over = mx >= x && mx < x + aw && my >= y && my < y + ah;

            g.fill(x - 2, y - 2, x + aw + 2, y + ah + 2, 0xFF050407);
            if (n > 0) {
                g.blit(CardArt.ready("admin_" + Tarot.ADMIN_IDS[i], 0, false), x, y, 0, 0, aw, ah, aw, ah);
                g.renderOutline(x, y, aw, ah, ac);
                if (n > 1) {
                    String c = String.valueOf(n);
                    g.drawString(font, c, x + aw - 4 - font.width(c), y + ah - 11, GOLD_HI, true);
                }
            } else {
                g.fill(x, y, x + aw, y + ah, 0xFF100E14);
                for (int d = 6; d < Math.min(aw, ah) - 6; d++) {
                    g.fill(x + d, y + d, x + d + 1, y + d + 1, 0xFF191520);
                    g.fill(x + aw - 1 - d, y + d, x + aw - d, y + d + 1, 0xFF191520);
                }
                String s2 = real ? "?" : "-";
                g.drawString(font, s2, x + (aw - font.width(s2)) / 2, y + ah / 2 - 4, 0xFF2A2636, false);
                g.renderOutline(x, y, aw, ah, 0xFF1C1A24);
            }
            if (over) {
                g.renderOutline(x, y, aw, ah, ac);
                hover = real ? Tarot.ADMIN_NAMES[i] + " - " + Tarot.ADMIN_TITLES[i]
                               + (n > 0 ? "  (" + n + ")" : "  - missing")
                             : "Kept empty";
            }
            String name = real ? Tarot.ADMIN_NAMES[i].toUpperCase(Locale.ROOT) : "EMPTY";
            g.drawString(font, name, x + (aw - font.width(name)) / 2, y + ah + 8, n > 0 ? ac : GREY_D, false);
            String s3 = real ? Tarot.ADMIN_TITLES[i].toUpperCase(Locale.ROOT) : "RESERVED";
            g.drawString(font, s3, x + (aw - font.width(s3)) / 2, y + ah + 20, 0xFF43404E, false);
        }
    }

    private void foot(GuiGraphics g, int mx, int my) {
        int y = bottom - 46;
        slant(g, left + 31, y, right - left - 38, 40, 0, PANEL_2);
        slant(g, left + 31, y, right - left - 38, 1, 0, BRONZE);

        if (page > 0) {
            boolean over = mx >= left + 40 && mx < left + 240 && my >= bottom - 44 && my < bottom - 12;
            g.drawString(font, "< " + title(ORDER[page - 1]),
                    left + 46, y + 16, over ? GOLD_HI : GREY_D, false);
        }
        if (page < ORDER.length - 1) {
            String s = title(ORDER[page + 1]) + " >";
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
