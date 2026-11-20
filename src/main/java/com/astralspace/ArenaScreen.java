package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The portal's menu: pick a game, pick a pathway if the game gives you one, see where you stand,
 * and join the queue.
 *
 * <p>Everything is sized off the font rather than fixed offsets, and every panel clips what it
 * writes, so it reads the same at any GUI scale.</p>
 */
public final class ArenaScreen extends Screen {

    private final ArenaNet.Board board;
    private Arena.Mode mode = Arena.Mode.OPEN;
    private int path = -1;                       // which pathway, for the kitted games
    private int pathScroll;
    private final List<int[]> pathHits = new ArrayList<>();
    private final List<int[]> modeHits = new ArrayList<>();

    private ArenaScreen(ArenaNet.Board board) {
        super(Component.literal("Arena"));
        this.board = board;
    }

    public static void open(ArenaNet.Board b) { Minecraft.getInstance().setScreen(new ArenaScreen(b)); }

    @Override
    public boolean isPauseScreen() { return false; }

    private int padX() { return Math.max(16, width / 18); }
    private int padY() { return Math.max(14, height / 14); }
    private int lh() { return font.lineHeight + 1; }
    private int accent() { return mode == Arena.Mode.OPEN ? 0xFFD24A : (mode == Arena.Mode.SEQ5 ? 0x6FA2FF : 0xC566E6); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int c = accent();
        pathHits.clear();

        g.fill(0, 0, width, height, 0xF0070910);
        g.fillGradient(0, 0, width, height / 2, Pathways.alpha(Pathways.deep(c, .5f), .26f), 0x00000000);
        g.fill(0, 0, width, 2, Pathways.alpha(Pathways.pale(c, .4f), .8f));

        int y = padY();
        g.drawString(font, Component.literal("ARENA").withColor(Pathways.pale(c, .5f) & 0xFFFFFF), padX(), y, 0xFFFFFF, false);
        String free = board.free() + " of " + Arena.SLOTS + " arenas free";
        g.drawString(font, Component.literal(free).withStyle(ChatFormatting.DARK_GRAY),
                     padX() + font.width("ARENA") + 12, y, 0xFFFFFF, false);

        modes(g, mx, my, c);
        standing(g, c);
        if (mode.kitted()) pathways(g, mx, my, c);
        buttons(g, mx, my, c);
        super.render(g, mx, my, pt);
    }

    /**
     * The three games, as tall cards down the left.
     *
     * <p>The blurb is wrapped to the width of the card and the card is grown to hold it. It used
     * to be cut off with an ellipsis, which told the player nothing about the game they were
     * picking.</p>
     */
    private void modes(GuiGraphics g, int mx, int my, int c) {
        int x = padX(), y = padY() + lh() + 8, w = Math.max(170, width / 4);
        modeHits.clear();
        for (Arena.Mode m : Arena.Mode.values()) {
            var lines = font.split(Component.literal(m.blurb), w - 16);
            int h = lh() * (2 + Math.max(1, lines.size())) + 12;
            boolean on = m == mode, hot = mx >= x && mx <= x + w && my >= y && my <= y + h;
            int mc = m == Arena.Mode.OPEN ? 0xFFD24A : (m == Arena.Mode.SEQ5 ? 0x6FA2FF : 0xC566E6);
            g.fill(x, y, x + w, y + h, on ? Pathways.alpha(mc, .26f) : (hot ? 0xFF13131B : 0xA00A0A12));
            if (on) g.fill(x, y, x + 3, y + h, Pathways.alpha(Pathways.pale(mc, .4f), 1f));
            g.renderOutline(x, y, w, h, Pathways.alpha(mc, on ? .9f : .25f));
            g.drawString(font, Component.literal(m.title).withColor(mc & 0xFFFFFF), x + 8, y + 4, 0xFFFFFF, false);
            int ly = y + 4 + lh();
            for (var line : lines) {
                g.drawString(font, line, x + 8, ly, 0xFF8A8A8A, false);
                ly += lh();
            }
            g.drawString(font, Component.literal(board.points(m) + " pts  \u00B7  "
                            + (board.place(m) > 0 ? "#" + board.place(m) : "unplaced")
                            + "  \u00B7  " + board.waiting(m) + " waiting").withStyle(ChatFormatting.GRAY),
                         x + 8, ly, 0xFFFFFF, false);
            modeHits.add(new int[]{x, y, w, h, m.ordinal()});
            y += h + 4;
        }
    }

    /** The board for whichever game is picked. */
    private void standing(GuiGraphics g, int c) {
        int x = width - padX() - Math.max(170, width / 4), y = padY() + lh() + 8;
        int w = Math.max(170, width / 4), h = height - y - padY() - lh() * 2 - 14;
        g.fill(x, y, x + w, y + h, 0xC00A0C14);
        g.renderOutline(x, y, w, h, Pathways.alpha(c, .35f));
        g.drawString(font, Component.literal("TOP TEN · " + mode.title).withColor(Pathways.pale(c, .45f) & 0xFFFFFF),
                     x + 8, y + 5, 0xFFFFFF, false);
        int ly = y + 6 + lh() * 2;
        List<ArenaNet.Entry> top = board.top(mode);
        if (top.isEmpty())
            g.drawString(font, Component.literal("Nobody has fought here yet.").withStyle(ChatFormatting.DARK_GRAY),
                         x + 8, ly, 0xFFFFFF, false);
        for (int i = 0; i < top.size() && ly < y + h - lh(); i++) {
            ArenaNet.Entry e = top.get(i);
            g.drawString(font, Component.literal((i + 1) + ".").withStyle(ChatFormatting.DARK_GRAY), x + 8, ly, 0xFFFFFF, false);
            g.drawString(font, Component.literal(trim(e.name(), w - 70)).withStyle(ChatFormatting.GRAY), x + 26, ly, 0xFFFFFF, false);
            String pts = String.valueOf(e.points());
            g.drawString(font, Component.literal(pts).withColor(Pathways.pale(c, .4f) & 0xFFFFFF),
                         x + w - font.width(pts) - 8, ly, 0xFFFFFF, false);
            ly += lh();
        }
    }

    /** The pathway to be given, for the two kitted games. */
    private void pathways(GuiGraphics g, int mx, int my, int c) {
        int x = padX() + Math.max(170, width / 4) + 16, y = padY() + lh() + 8;
        int w = width - padX() - Math.max(170, width / 4) - x - 16;
        int h = height - y - padY() - lh() * 2 - 14;
        if (w < 80) return;
        g.fill(x, y, x + w, y + h, 0xC00A0C14);
        g.renderOutline(x, y, w, h, Pathways.alpha(c, .35f));
        g.drawString(font, Component.literal("PATHWAY").withColor(Pathways.pale(c, .45f) & 0xFFFFFF), x + 8, y + 5, 0xFFFFFF, false);
        g.drawString(font, Component.literal("Left blank, you fight on your own.").withStyle(ChatFormatting.DARK_GRAY),
                     x + 8, y + 5 + lh(), 0xFFFFFF, false);

        int top = y + 6 + lh() * 2 + 4, bh = h - (top - y) - 6;
        int rows = Math.max(1, bh / lh());
        pathScroll = Math.max(0, Math.min(pathScroll, Math.max(0, Lotm.PATH_IDS.length - rows)));
        g.enableScissor(x + 2, top, x + w - 2, top + bh);
        for (int i = 0; i < Lotm.PATH_IDS.length; i++) {
            int ry = top + (i - pathScroll) * lh();
            if (ry < top - lh() || ry > top + bh) continue;
            String id = Lotm.PATH_IDS[i];
            boolean on = i == path, hot = mx >= x + 4 && mx <= x + w - 4 && my >= ry && my <= ry + lh();
            int pc = Pathways.colour(id);
            if (on || hot) g.fill(x + 4, ry, x + w - 4, ry + lh(), Pathways.alpha(pc, on ? .3f : .14f));
            g.drawString(font, Component.literal(id.replace('_', ' ')).withColor(on ? 0xFFFFFF : (pc & 0xFFFFFF)),
                         x + 8, ry + 1, 0xFFFFFF, false);
            pathHits.add(new int[]{x + 4, ry, x + w - 8, lh(), i});
        }
        g.disableScissor();
    }

    /** Queue and leave, along the bottom. */
    private void buttons(GuiGraphics g, int mx, int my, int c) {
        int y = height - padY() - lh() - 8, w = 110, x = padX();
        boolean queuedHere = false;
        button(g, mx, my, x, y, w, "Join the queue", c);
        button(g, mx, my, x + w + 8, y, 90, "Leave", 0x9A9AA6);
        g.drawString(font, Component.literal("Matches are found by points. The closer you are, the sooner you fight.")
                .withStyle(ChatFormatting.DARK_GRAY), x + w + 110, y + 4, 0xFFFFFF, false);
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, String s, int c) {
        boolean hot = mx >= x && mx <= x + w && my >= y && my <= y + lh() + 8;
        g.fill(x, y, x + w, y + lh() + 8, Pathways.alpha(c, hot ? .5f : .28f));
        g.renderOutline(x, y, w, lh() + 8, Pathways.alpha(c, .85f));
        g.drawCenteredString(font, Component.literal(s).withColor(0xFFFFFF), x + w / 2, y + 4, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int[] hit : modeHits)
            if (mx >= hit[0] && mx <= hit[0] + hit[2] && my >= hit[1] && my <= hit[1] + hit[3]) {
                mode = Arena.Mode.values()[hit[4]];
                click();
                return true;
            }
        for (int[] hit : pathHits)
            if (mx >= hit[0] && mx <= hit[0] + hit[2] && my >= hit[1] && my <= hit[1] + hit[3]) {
                path = path == hit[4] ? -1 : hit[4];
                click();
                return true;
            }
        int by = height - padY() - lh() - 8;
        if (my >= by && my <= by + lh() + 8) {
            if (mx >= padX() && mx <= padX() + 110) {
                PacketDistributor.sendToServer(new ArenaNet.Join(mode.id(),
                        mode.kitted() && path >= 0 ? Lotm.PATH_IDS[path] : ""));
                Minecraft.getInstance().setScreen(null);
                return true;
            }
            if (mx >= padX() + 118 && mx <= padX() + 208) {
                PacketDistributor.sendToServer(new ArenaNet.Leave());
                Minecraft.getInstance().setScreen(null);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        pathScroll -= (int) dy;
        return true;
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.1f, .4f));
    }

    private String trim(String s, int px) {
        if (font.width(s) <= px) return s;
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (font.width(b.toString() + ch + "…") > px) break;
            b.append(ch);
        }
        return b + "…";
    }
}
