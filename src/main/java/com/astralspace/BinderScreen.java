package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * The card binder, laid out the way the collection actually is: one page per Great Old One, that Great Old One's
 * pathways as rows, and sequence 9 down to 0 across each row. The rarest cards sit in the right-hand columns, so a
 * page reads as a bar chart of how far someone has got without anybody having to count.
 *
 * A face you hold is drawn as its card art; one you do not is a dark plate with the pathway's letter, so the gaps are
 * the thing you see. The shiny toggle swaps the whole page rather than doubling the grid.
 */
public final class BinderScreen extends Screen {
    private final Map<String, Integer> have = new HashMap<>();
    private int tab = 0;
    private boolean shiny = false;
    private int cw = 22, ch = 33, gridX, gridY, tabY;   // cards are 2:3, so the slots are too
    private String hover = null;

    private BinderScreen(String packed) {
        super(Component.literal("Card Binder"));
        load(packed);
    }

    private void load(String packed) {
        have.clear();
        for (String part : packed.split(",")) {
            int c = part.lastIndexOf(':');
            if (c <= 0) continue;
            try { have.put(part.substring(0, c), Integer.parseInt(part.substring(c + 1))); } catch (NumberFormatException ignored) { }
        }
    }

    public static void open(String packed) {
        Screen now = Minecraft.getInstance().screen;
        if (now instanceof BinderScreen b) { b.load(packed); return; }    // a refresh after taking a card: keep the page
        Minecraft.getInstance().setScreen(new BinderScreen(packed));
    }

    private String[] rows() { return Tarot.GOO_PATHS[tab]; }

    @Override
    protected void init() {
        int widest = 0;
        for (String[] g : Tarot.GOO_PATHS) widest = Math.max(widest, g.length);
        ch = Math.max(21, Math.min(45, Math.min((width - 170) / 10 * 3 / 2, (height - 150) / widest)));
        cw = ch * 2 / 3;
        gridX = width / 2 - cw * 10 / 2 + 26;
        gridY = 64;
        tabY = 28;
    }

    /** The slot a point is over, as {row, column}, or null. */
    private int[] slotAt(double mx, double my) {
        int r = (int) Math.floor((my - gridY) / ch), c = (int) Math.floor((mx - gridX) / cw);
        if (r < 0 || c < 0 || c > 9 || r >= rows().length) return null;
        return new int[]{r, c};
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // the nine chapter tabs
        for (int i = 0; i < Tarot.GOO_IDS.length; i++) {
            int tx = width / 2 - Tarot.GOO_IDS.length * 26 / 2 + i * 26;
            if (mx >= tx && mx < tx + 24 && my >= tabY && my < tabY + 22) { tab = i; return true; }
        }
        // the shiny toggle
        if (mx >= width / 2 - 60 && mx < width / 2 + 60 && my >= height - 54 && my < height - 34) { shiny = !shiny; return true; }
        // file everything away
        if (mx >= width / 2 - 60 && mx < width / 2 + 60 && my >= height - 30 && my < height - 10) {
            PacketDistributor.sendToServer(new Net.BinderActPayload(1, ""));
            return true;
        }
        // the Great Old One's own socket, left of the rows
        if (mx >= gridX - cw - 12 && mx < gridX - 12 && my >= gridY && my < gridY + ch) {
            String k = Binder.key("goo_" + Tarot.GOO_IDS[tab], 0, shiny);
            if (have.getOrDefault(k, 0) > 0) PacketDistributor.sendToServer(new Net.BinderActPayload(0, k));
            return true;
        }
        int[] s = slotAt(mx, my);
        if (s != null) {
            String k = Binder.key(rows()[s[0]], 9 - s[1], shiny);
            if (have.getOrDefault(k, 0) > 0) PacketDistributor.sendToServer(new Net.BinderActPayload(0, k));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    /** One slot: the card's art when it is held, a dark plate when it is not, with a count when there is more than one. */
    private void slot(GuiGraphics g, int x, int y, String pathway, int seq, double mx, double my) {
        String k = Binder.key(pathway, seq, shiny);
        int n = have.getOrDefault(k, 0);
        int w = cw - 2, h = ch - 2;
        boolean over = mx >= x && mx < x + w && my >= y && my < y + h;
        boolean goo = pathway.startsWith("goo_");
        g.fill(x, y, x + w, y + h, n > 0 ? 0xFF1A1622 : 0xFF0C0C11);
        if (n > 0) {
            ResourceLocation art = ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID,
                "textures/gui/tarot/card_" + pathway + "_" + seq + (shiny ? "_shiny" : "") + ".png");
            // the whole card scaled into the slot: declaring the texture as w x h makes the UVs span all of it
            g.blit(art, x + 1, y + 1, 0, 0, w - 2, h - 2, w - 2, h - 2);
            if (n > 1) g.drawString(font, String.valueOf(n), x + w - 7 - font.width(String.valueOf(n)), y + h - 10, 0xFFE8C04A, true);
            g.renderOutline(x, y, w, h, shiny ? 0xFFE8C04A : 0xFF4A4458);
        } else {
            g.drawCenteredString(font, String.valueOf(Character.toUpperCase(pathway.charAt(goo ? 4 : 0))), x + w / 2, y + h / 2 - 4, 0xFF2A2636);
            g.renderOutline(x, y, w, h, 0xFF1C1A24);
        }
        if (over) {
            g.renderOutline(x, y, w, h, 0xFFFFFFFF);
            hover = (shiny ? "Shiny " : "") + (goo ? Tarot.pretty(pathway.substring(4)) : Tarot.pretty(pathway) + " - Seq " + seq)
                + (n > 0 ? "  (" + n + ")" : "  - missing");
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        hover = null;
        renderBackground(g, mx, my, pt);
        g.fill(0, 0, width, height, 0xC8100C16);

        int all = Binder.faces() * 2, held = 0;
        for (int v : have.values()) if (v > 0) held++;
        g.drawCenteredString(font, "Card Binder", width / 2, 10, 0xFFE8C04A);
        g.drawCenteredString(font, held + " / " + all + " faces", width / 2, height - 72, 0xFF9A93AD);

        // chapter tabs
        for (int i = 0; i < Tarot.GOO_IDS.length; i++) {
            int tx = width / 2 - Tarot.GOO_IDS.length * 26 / 2 + i * 26;
            boolean on = i == tab, over = mx >= tx && mx < tx + 24 && my >= tabY && my < tabY + 22;
            g.fill(tx, tabY, tx + 24, tabY + 22, on ? 0xFF2A2338 : 0xFF16131E);
            g.renderOutline(tx, tabY, 24, 22, on ? 0xFFE8C04A : over ? 0xFF6A6480 : 0xFF2A2636);
            g.drawCenteredString(font, String.valueOf(i + 1), tx + 12, tabY + 7, on ? 0xFFE8C04A : 0xFF8A83A0);
            if (over) hover = Tarot.pretty(Tarot.GOO_IDS[i]);
        }
        g.drawCenteredString(font, Tarot.pretty(Tarot.GOO_IDS[tab]), width / 2, tabY + 28, 0xFFC8B8E8);

        // the Great Old One's own card, in a socket of its own
        slot(g, gridX - cw - 12, gridY, "goo_" + Tarot.GOO_IDS[tab], 0, mx, my);

        // the pathways of this chapter, sequence 9 on the left down to 0 on the right
        String[] rs = rows();
        for (int r = 0; r < rs.length; r++) {
            int y = gridY + r * ch;
            boolean full = true;
            for (int c = 0; c < 10; c++) {
                slot(g, gridX + c * cw, y, rs[r], 9 - c, mx, my);
                if (have.getOrDefault(Binder.key(rs[r], 9 - c, shiny), 0) <= 0) full = false;
            }
            g.drawString(font, Tarot.pretty(rs[r]), gridX + 10 * cw + 6, y + (ch - 10) / 2, full ? 0xFFE8C04A : 0xFF6A6480, false);
        }
        for (int c = 0; c < 10; c++) g.drawCenteredString(font, String.valueOf(9 - c), gridX + c * cw + (cw - 2) / 2, gridY - 11, 0xFF55506A);

        button(g, width / 2 - 60, height - 54, shiny ? "Shiny faces" : "Normal faces", mx, my, shiny);
        button(g, width / 2 - 60, height - 30, "File loose cards", mx, my, false);

        if (hover != null) g.renderTooltip(font, Component.literal(hover), mx, my);
    }

    private void button(GuiGraphics g, int x, int y, String label, int mx, int my, boolean lit) {
        boolean over = mx >= x && mx < x + 120 && my >= y && my < y + 20;
        g.fill(x, y, x + 120, y + 20, over ? 0xFF2E2740 : 0xFF1A1622);
        g.renderOutline(x, y, 120, 20, lit ? 0xFFE8C04A : over ? 0xFF6A6480 : 0xFF2A2636);
        g.drawCenteredString(font, label, x + 60, y + 6, lit ? 0xFFE8C04A : 0xFFC0B8D0);
    }

    @Override public boolean isPauseScreen() { return false; }
}
