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
 * The altar's menu. The left column is the state of the tournament and what you can do about it;
 * the right is the list of names before the draw and the branches after it.
 *
 * <p>Laid out off the font with every panel clipped, same as the portal's.</p>
 */
public final class TourneyScreen extends Screen {

    /** A clickable rectangle: x, y, w, h, plus the command and its argument. */
    private record Hit(int x, int y, int w, int h, String cmd, int arg) { }

    private String state = "IDLE", modeId = "seq5";
    private int fee, pot, coins;
    private boolean in, op;
    private final List<String> names = new ArrayList<>();
    private final List<String[]> bracket = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private int scroll;

    private TourneyScreen() { super(Component.literal("Tournament")); }

    public static void open(String blob) {
        Minecraft mc = Minecraft.getInstance();
        TourneyScreen s = mc.screen instanceof TourneyScreen t ? t : new TourneyScreen();
        s.read(blob);
        if (mc.screen != s) mc.setScreen(s);
    }

    private void read(String blob) {
        names.clear();
        bracket.clear();
        String[] lines = blob.split("\n");
        String[] head = lines[0].split("\t");
        state = head[0];
        modeId = head[1];
        fee = Integer.parseInt(head[2]);
        pot = Integer.parseInt(head[3]);
        in = "1".equals(head[4]);
        op = "1".equals(head[5]);
        coins = Integer.parseInt(head[6]);
        for (int i = 1; i < lines.length; i++) {
            String[] f = lines[i].split("\t");
            if ("E".equals(f[0]) && f.length > 1) names.add(f[1]);
            else if ("R".equals(f[0]) && f.length > 4) bracket.add(new String[]{f[1], f[2], f[3], f[4]});
        }
    }

    private Arena.Mode mode() { return Arena.Mode.of(modeId); }

    @Override public boolean isPauseScreen() { return false; }

    private int padX() { return Math.max(16, width / 18); }
    private int padY() { return Math.max(14, height / 14); }
    private int lh() { return font.lineHeight + 1; }

    private void send(String cmd, int arg) {
        PacketDistributor.sendToServer(new TourneyNet.Act(cmd, arg));
    }

    @Override
    public void onClose() {
        send("close", 0);
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int c = 0xFFD37A;
        hits.clear();

        g.fill(0, 0, width, height, 0xF00A0806);
        g.fillGradient(0, 0, width, height / 2, Pathways.alpha(Pathways.deep(c, .5f), .24f), 0x00000000);
        g.fill(0, 0, width, 2, Pathways.alpha(Pathways.pale(c, .4f), .8f));

        int y = padY();
        g.drawString(font, Component.literal("TOURNAMENT").withColor(Pathways.pale(c, .5f) & 0xFFFFFF),
                     padX(), y, 0xFFFFFF, false);
        String sub = switch (state) {
            case "SIGNUP" -> "taking names";
            case "RUNNING" -> "under way";
            default -> "not open";
        };
        g.drawString(font, Component.literal(sub).withStyle(ChatFormatting.DARK_GRAY),
                     padX() + font.width("TOURNAMENT") + 12, y, 0xFFFFFF, false);

        left(g, mx, my, c);
        right(g, c);
        super.render(g, mx, my, pt);
    }

    // ---------------------------------------------------------------- the left column

    private void left(GuiGraphics g, int mx, int my, int c) {
        int x = padX(), w = Math.max(170, width / 4);
        int y = padY() + lh() + 8;
        int h = height - y - padY();

        g.fill(x, y, x + w, y + h, 0x9E0D0B08);
        g.renderOutline(x, y, w, h, Pathways.alpha(c, .4f));

        int ly = y + 7;
        g.drawString(font, Component.literal(mode().title).withColor(Pathways.pale(c, .4f) & 0xFFFFFF),
                     x + 8, ly, 0xFFFFFF, false);
        ly += lh();
        g.drawString(font, Component.literal(mode().blurb).withStyle(ChatFormatting.DARK_GRAY),
                     x + 8, ly, 0xFFFFFF, false);
        ly += lh() + 4;
        g.drawString(font, Component.literal("Stake  " + fee).withStyle(ChatFormatting.GRAY), x + 8, ly, 0xFFFFFF, false);
        ly += lh();
        g.drawString(font, Component.literal("Pot    " + pot).withColor(c & 0xFFFFFF), x + 8, ly, 0xFFFFFF, false);
        ly += lh();
        g.drawString(font, Component.literal("Yours  " + coins).withStyle(ChatFormatting.DARK_GRAY), x + 8, ly, 0xFFFFFF, false);
        ly += lh() + 6;

        if ("SIGNUP".equals(state)) {
            ly = button(g, mx, my, x + 8, ly, w - 16, in ? "Withdraw" : "Pay in and enter",
                        in ? "leave" : "join", 0, c);
        } else if ("RUNNING".equals(state)) {
            g.drawString(font, Component.literal(in ? "You are in the draw." : "Watching.")
                    .withStyle(ChatFormatting.DARK_GRAY), x + 8, ly, 0xFFFFFF, false);
            ly += lh() + 4;
        } else {
            g.drawString(font, Component.literal("Nobody has opened one.")
                    .withStyle(ChatFormatting.DARK_GRAY), x + 8, ly, 0xFFFFFF, false);
            ly += lh() + 4;
        }

        if (!op) return;

        ly += 6;
        g.fill(x + 8, ly, x + w - 8, ly + 1, Pathways.alpha(c, .25f));
        ly += 7;
        g.drawString(font, Component.literal("OPERATOR").withStyle(ChatFormatting.DARK_GRAY), x + 8, ly, 0xFFFFFF, false);
        ly += lh() + 3;

        if (!"RUNNING".equals(state)) {
            int next = (mode().ordinal() + 1) % Arena.Mode.values().length;
            ly = button(g, mx, my, x + 8, ly, w - 16, "Game: " + mode().title, "mode", next, c);
        }
        if ("IDLE".equals(state)) {
            int half = (w - 20) / 2;
            int keep = ly;
            button(g, mx, my, x + 8, ly, half, "Stake −" + step(), "fee", Math.max(0, fee - step()), c);
            ly = button(g, mx, my, x + 8 + half + 4, keep, half, "Stake +" + step(), "fee", fee + step(), c);
            ly = button(g, mx, my, x + 8, ly, w - 16, "Open signups", "open", 0, c);
        } else if ("SIGNUP".equals(state)) {
            ly = button(g, mx, my, x + 8, ly, w - 16, "Draw the bracket (" + names.size() + ")", "start", 0, c);
            ly = button(g, mx, my, x + 8, ly, w - 16, "Call it off", "cancel", 0, 0xC86464);
        } else {
            ly = button(g, mx, my, x + 8, ly, w - 16, "Call it off", "cancel", 0, 0xC86464);
        }
    }

    private int step() { return fee >= 1000 ? 500 : fee >= 200 ? 100 : 25; }

    private int button(GuiGraphics g, int mx, int my, int x, int y, int w, String label, String cmd, int arg, int c) {
        int h = lh() + 7;
        boolean hot = mx >= x && mx <= x + w && my >= y && my <= y + h;
        g.fill(x, y, x + w, y + h, Pathways.alpha(c, hot ? .46f : .24f));
        g.renderOutline(x, y, w, h, Pathways.alpha(c, .8f));
        g.drawString(font, Component.literal(label), x + (w - font.width(label)) / 2, y + 4, 0xFFFFFFFF, false);
        hits.add(new Hit(x, y, w, h, cmd, arg));
        return y + h + 4;
    }

    // ---------------------------------------------------------------- the right column

    private void right(GuiGraphics g, int c) {
        int w = Math.max(190, width / 3);
        int x = width - padX() - w;
        int y = padY() + lh() + 8;
        int h = height - y - padY();

        g.fill(x, y, x + w, y + h, 0x9E0D0B08);
        g.renderOutline(x, y, w, h, Pathways.alpha(c, .4f));

        boolean drawn = !bracket.isEmpty();
        String head = drawn ? "THE BRANCHES" : "ENTRANTS";
        g.drawString(font, Component.literal(head).withColor(Pathways.pale(c, .4f) & 0xFFFFFF),
                     x + 8, y + 7, 0xFFFFFF, false);

        List<Component> body = new ArrayList<>();
        if (drawn) {
            String round = "";
            for (String[] b : bracket) {
                if (!b[0].equals(round)) {
                    round = b[0];
                    body.add(Component.literal("Round " + round).withStyle(ChatFormatting.DARK_GRAY));
                }
                String line = " " + b[1] + "  vs  " + b[2];
                body.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
                String r = b[3];
                int col = "fighting".equals(r) ? 0xFFD37A : "waiting".equals(r) ? 0x6E6A62 : 0xA7D39B;
                body.add(Component.literal("   " + r).withColor(col));
            }
        } else if (names.isEmpty()) {
            body.add(Component.literal("Nobody yet.").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (int i = 0; i < names.size(); i++) {
                body.add(Component.literal((i + 1) + ".  " + names.get(i)).withStyle(ChatFormatting.GRAY));
            }
        }

        int top = y + 7 + lh() + 5, bh = h - (top - y) - 7;
        int rows = Math.max(1, bh / lh());
        scroll = Math.max(0, Math.min(scroll, Math.max(0, body.size() - rows)));

        g.enableScissor(x + 2, top, x + w - 2, top + bh);
        for (int i = scroll; i < body.size(); i++) {
            int ry = top + (i - scroll) * lh();
            if (ry > top + bh - lh()) break;
            g.drawString(font, body.get(i), x + 8, ry, 0xFFFFFF, false);
        }
        g.disableScissor();

        if (body.size() > rows) {
            g.drawString(font, Component.literal("▾").withStyle(ChatFormatting.DARK_GRAY),
                         x + w - 14, top + bh - lh(), 0xFFFFFF, false);
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            for (Hit h : hits) {
                if (mx >= h.x() && mx <= h.x() + h.w() && my >= h.y() && my <= h.y() + h.h()) {
                    send(h.cmd(), h.arg());
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Math.max(0, scroll - (int) Math.signum(dy) * 2);
        return true;
    }
}
