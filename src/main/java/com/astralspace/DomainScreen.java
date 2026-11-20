package com.astralspace;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Choosing a domain: which set it is cut for, and how deep you are going.
 *
 * <p>The whole screen, like the artifact menu it feeds. The sets run down the left as a list of
 * rows rather than a wall of symbols, because what is actually being chosen is the pair of effects
 * at the bottom of each row, and a symbol cannot say "a third more damage until something lands on
 * you". The one in hand is blown up on the right with both halves spelled out, how many of it you
 * already own, and a depth gauge that says plainly what the run will pay.</p>
 */
public final class DomainScreen extends Screen {

    private int picked, difficulty = 2, scroll, detailScroll;
    private float t;
    private final List<int[]> rows = new ArrayList<>();      // {x, y, w, h, index}

    private DomainScreen() { super(Component.literal("Domain")); }

    public static void open() { Minecraft.getInstance().setScreen(new DomainScreen()); }

    @Override
    public boolean isPauseScreen() { return false; }

    private ArtifactSets.Set set() { return ArtifactSets.all().get(picked); }

    private int padX() { return Math.max(18, width / 20); }
    private int padY() { return Math.max(16, height / 16); }
    private int listX() { return padX(); }
    private int listY() { return padY() + 34; }
    private int listW() { return Math.max(190, (int) (width * .38f)); }
    private int listH() { return height - listY() - padY() - 28; }
    private int rowH() { return 34; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        t += pt / 20f;
        int c = set().colour();
        rows.clear();

        // the field
        g.fill(0, 0, width, height, 0xFF06070D);
        g.fillGradient(0, 0, width, height / 2, Pathways.alpha(Pathways.deep(c, .5f), .3f), 0x00000000);
        g.fillGradient(0, height - height / 3, width, height, 0x00000000, Pathways.alpha(Pathways.deep(c, .4f), .3f));
        for (int i = 0; i < 70; i++) {
            float u = fr(i * .61803f), v = fr(i * .38196f + t * (.003f + .005f * fr(i * .137f)));
            int x = (int) (u * width), y = (int) (v * height);
            int a = (int) (40 + 70 * Math.abs(Math.sin(t * .7 + i)));
            g.fill(x, y, x + 1, y + 1, (Math.min(170, a) << 24) | (Pathways.pale(c, .5f) & 0xFFFFFF));
        }
        g.fill(0, 0, width, 2, Pathways.alpha(Pathways.pale(c, .4f), .7f));

        g.drawString(font, Component.literal("CONTAINMENT").withColor(Pathways.pale(c, .5f) & 0xFFFFFF), padX(), padY(), 0xFFFFFF, false);
        g.drawString(font, Component.literal("A site still running its incident. Survive the re-enactment and it gives up what it holds.")
                .withStyle(ChatFormatting.DARK_GRAY), padX() + font.width("CONTAINMENT") + 12, padY(), 0xFFFFFF, false);

        list(g, mx, my, c);
        detail(g, mx, my, c);
        super.render(g, mx, my, pt);
    }

    /** The sets as rows: name, colour, and the two halves in one line each. */
    private void list(GuiGraphics g, int mx, int my, int c) {
        int x = listX(), y = listY(), w = listW(), h = listH(), rh = rowH();
        g.fill(x - 6, y - 6, x + w + 6, y + h + 6, 0x900A0A12);
        g.renderOutline(x - 6, y - 6, w + 12, h + 12, Pathways.alpha(c, .3f));

        var all = ArtifactSets.all();
        int maxScroll = Math.max(0, all.size() * rh - h);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        g.enableScissor(x - 4, y - 4, x + w + 4, y + h + 4);
        var owned = ArtifactSets.counts(ArtifactSlots.clientWorn());
        for (int i = 0; i < all.size(); i++) {
            ArtifactSets.Set s = all.get(i);
            int ry = y + i * rh - scroll;
            if (ry + rh < y - rh || ry > y + h + rh) continue;
            boolean on = i == picked, hot = mx >= x && mx <= x + w && my >= ry && my <= ry + rh - 2;
            int sc = s.colour();
            g.fill(x, ry, x + w, ry + rh - 2, on ? Pathways.alpha(sc, .26f) : (hot ? 0xFF12121A : 0xA00A0A12));
            if (on) g.fill(x, ry, x + 3, ry + rh - 2, Pathways.alpha(Pathways.pale(sc, .4f), 1f));
            g.renderOutline(x, ry, w, rh - 2, Pathways.alpha(sc, on ? .85f : .22f));
            RenderSystem.enableBlend();
            g.setColor(((sc >> 16 & 255) / 255f), ((sc >> 8 & 255) / 255f), ((sc & 255) / 255f), on ? .9f : .55f);
            g.blit(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "textures/holo/" + s.symbol() + ".png"),
                   x + 5, ry + 5, 0, 0, 22, 22, 22, 22);
            g.setColor(1, 1, 1, 1);
            g.drawString(font, Component.literal(trim(s.title(), w - 44)).withColor(sc & 0xFFFFFF), x + 32, ry + 6, 0xFFFFFF, false);
            g.drawString(font, Component.literal(trim("2pc " + s.twoLine(), w - 44)).withStyle(ChatFormatting.DARK_GRAY),
                         x + 32, ry + 18, 0xFFFFFF, false);
            int have = owned.getOrDefault(s.id(), 0);
            for (int k = 0; k < 4; k++)
                g.fill(x + w - 24 + k * 5, ry + 7, x + w - 21 + k * 5, ry + 11,
                       k < have ? Pathways.alpha(sc, .95f) : 0xFF1C1C24);
            rows.add(new int[]{x, ry, w, rh - 2, i});
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int bh = Math.max(18, (int) (h * (h / (float) (all.size() * rh))));
            int by = y + (int) ((h - bh) * (scroll / (float) maxScroll));
            g.fill(x + w + 1, y, x + w + 4, y + h, 0xFF14141C);
            g.fill(x + w + 1, by, x + w + 4, by + bh, Pathways.alpha(c, .8f));
        }
    }

    /** The chosen set, in full, with the depth gauge and the way in. */
    private void detail(GuiGraphics g, int mx, int my, int c) {
        ArtifactSets.Set s = set();
        int x = listX() + listW() + 22, y = listY() - 6, w = width - padX() - x, h = listH() + 12;
        g.fill(x, y, x + w, y + h, 0xE0080A12);
        g.renderOutline(x, y, w, h, Pathways.alpha(c, .35f));

        // the symbol behind it all, faint
        RenderSystem.enableBlend();
        g.setColor(((c >> 16 & 255) / 255f), ((c >> 8 & 255) / 255f), ((c & 255) / 255f), .10f);
        int ss = Math.min(Math.min(w, h) - 40, 150);
        if (ss > 16) g.blit(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "textures/holo/" + s.symbol() + ".png"),
                            x + (w - ss) / 2, y + (h - ss) / 2, 0, 0, ss, ss, ss, ss);
        g.setColor(1, 1, 1, 1);

        // the head: the name, and the case number under it when there is no room beside it
        int lh = font.lineHeight + 1;
        boolean wide = font.width(s.title()) + font.width(s.file()) + 26 < w;
        int headH = 10 + lh + (wide ? 0 : lh) + lh;
        g.fillGradient(x, y, x + w, y + headH, Pathways.alpha(c, .9f), Pathways.alpha(Pathways.deep(c, .5f), .9f));
        g.drawString(font, Component.literal(trim(s.title(), w - 16)).withColor(0xFFFFFF), x + 8, y + 5, 0xFFFFFF, false);
        if (wide) g.drawString(font, Component.literal(s.file()).withColor(0xFFE4E4EC),
                               x + w - font.width(s.file()) - 8, y + 5, 0xFFFFFF, false);
        else g.drawString(font, Component.literal(s.file()).withColor(0xFFE4E4EC), x + 8, y + 5 + lh, 0xFFFFFF, false);
        g.drawString(font, Component.literal(trim(s.flavour(), w - 16))
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY), x + 8, y + 5 + (wide ? lh : lh * 2), 0xFFFFFF, false);

        // ---- the footer is laid out first and the rest is kept out of it ----
        int footH = lh * 2 + 34;
        int fy = y + h - footH;
        g.fill(x + 8, fy, x + w - 8, fy + 1, Pathways.alpha(c, .3f));

        int dyT = fy + 6;
        g.drawString(font, Component.literal("DEPTH").withColor(Pathways.pale(c, .45f) & 0xFFFFFF), x + 8, dyT, 0xFFFFFF, false);
        int bw2 = Math.max(16, Math.min(26, (w - 24 - font.width("DEPTH")) / 5));
        int bx0 = x + 12 + font.width("DEPTH");
        for (int i = 1; i <= 5; i++) {
            int bx = bx0 + (i - 1) * bw2, by = dyT - 3;
            boolean on = i <= difficulty, hot = mx >= bx && mx <= bx + bw2 - 3 && my >= by && my <= by + lh + 5;
            g.fill(bx, by, bx + bw2 - 3, by + lh + 5, on ? Pathways.alpha(c, .4f) : (hot ? 0xFF15151D : 0xA00A0A12));
            g.renderOutline(bx, by, bw2 - 3, lh + 5, Pathways.alpha(c, on ? .9f : .28f));
            g.drawCenteredString(font, String.valueOf(i), bx + (bw2 - 3) / 2, by + 3, on ? 0xFFFFFFFF : 0xFF5A5A64);
        }
        String note = switch (difficulty) {
            case 1 -> "1 piece \u00b7 up to 2\u2726";
            case 2 -> "1 piece \u00b7 up to 3\u2726";
            case 3 -> "2 pieces \u00b7 up to 3\u2726";
            case 4 -> "2 pieces \u00b7 up to 4\u2726";
            default -> "2 pieces \u00b7 up to 5\u2726";
        };
        g.drawString(font, Component.literal(trim(note, w - 16)).withStyle(ChatFormatting.DARK_GRAY),
                     x + 8, dyT + lh + 8, 0xFFFFFF, false);

        int bWd = Math.min(110, w - 16), bx = x + w - bWd - 8, by = y + h - lh - 12;
        boolean hot = mx >= bx && mx <= bx + bWd && my >= by && my <= by + lh + 8;
        g.fill(bx, by, bx + bWd, by + lh + 8, Pathways.alpha(c, hot ? .6f : .32f));
        g.renderOutline(bx, by, bWd, lh + 8, Pathways.alpha(c, .9f));
        g.drawCenteredString(font, Component.literal("Enter").withColor(0xFFFFFF), bx + bWd / 2, by + 4, 0xFFFFFF);

        // ---- the body, written into the gap that is left and clipped to it ----
        int by0 = y + headH + 6, bh = fy - 8 - by0;
        if (bh < lh) return;
        List<Component> lines = new ArrayList<>();
        tierLines(lines, c, "TWO FRAGMENTS", s.twoLine(), null, w - 20);
        tierLines(lines, c, "FOUR \u00b7 THE INCIDENT REPEATS", s.fourLine(), s.fourCost(), w - 20);
        tierLines(lines, c, "FIVE \u00b7 ALL OF IT", s.fiveLine(), s.fiveCost(), w - 20);
        String[] rungs = SetAbilities.names(s.id());
        for (int i = 0; i < 5; i++)
            if (rungs[i] != null && !rungs[i].isEmpty())
                lines.add(Component.literal("\u2726".repeat(i + 1) + "  " + rungs[i]).withStyle(ChatFormatting.GRAY));

        int max = Math.max(0, lines.size() * lh - bh);
        detailScroll = Math.max(0, Math.min(detailScroll, max));
        g.enableScissor(x + 2, by0, x + w - 2, by0 + bh);
        for (int i = 0; i < lines.size(); i++) {
            int ly = by0 + i * lh - detailScroll;
            if (ly + lh < by0 || ly > by0 + bh) continue;
            g.drawString(font, lines.get(i), x + 10, ly, 0xFFFFFF, false);
        }
        g.disableScissor();
        if (max > 0) {
            int sh = Math.max(14, (int) (bh * (bh / (float) (lines.size() * lh))));
            int sy = by0 + (int) ((bh - sh) * (detailScroll / (float) max));
            g.fill(x + w - 5, by0, x + w - 3, by0 + bh, 0xFF14141C);
            g.fill(x + w - 5, sy, x + w - 3, sy + sh, Pathways.alpha(c, .8f));
        }
    }

    /** One tier, flattened into lines: heading, what it gives in green, what it takes in red. */
    private void tierLines(List<Component> out, int c, String head, String gives, String takes, int px) {
        out.add(Component.literal(head).withColor(Pathways.pale(c, .45f) & 0xFFFFFF));
        for (String line : wrap(gives, px)) out.add(Component.literal(line).withColor(0xFF9ADFA8));
        if (takes != null) for (String line : wrap("\u2013 " + takes, px)) out.add(Component.literal(line).withColor(0xFFE07A7A));
        out.add(Component.empty());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int[] r : rows)
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) { picked = r[4]; detailScroll = 0; click(); return true; }

        int x = listX() + listW() + 22, y = listY() - 6, w = width - padX() - x, h = listH() + 12;
        int lh = font.lineHeight + 1, footH = lh * 2 + 34, fy = y + h - footH;
        int bw2 = Math.max(16, Math.min(26, (w - 24 - font.width("DEPTH")) / 5));
        int bx0 = x + 12 + font.width("DEPTH"), dyT = fy + 6;
        for (int i = 1; i <= 5; i++) {
            int bx = bx0 + (i - 1) * bw2, by = dyT - 3;
            if (mx >= bx && mx <= bx + bw2 - 3 && my >= by && my <= by + lh + 5) { difficulty = i; click(); return true; }
        }
        int bWd = Math.min(110, w - 16), bx = x + w - bWd - 8, by = y + h - lh - 12;
        if (mx >= bx && mx <= bx + bWd && my >= by && my <= by + lh + 8) {
            PacketDistributor.sendToServer(new DoorNet.DomainEnter(set().id(), difficulty));
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (mx > listX() + listW()) detailScroll -= (int) (dy * 24);
        else scroll -= (int) (dy * 24);
        return true;
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.1f, .4f));
    }

    private static float fr(float v) { return v - (float) Math.floor(v); }

    private List<String> wrap(String s, int px) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String probe = line.isEmpty() ? word : line + " " + word;
            if (font.width(probe) > px && !line.isEmpty()) { out.add(line.toString()); line = new StringBuilder(word); }
            else line = new StringBuilder(probe);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
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
