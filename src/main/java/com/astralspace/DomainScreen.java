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

    private int picked, difficulty = 2, scroll;
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
        g.fill(x, y, x + w, y + h, 0xB0080A12);
        g.renderOutline(x, y, w, h, Pathways.alpha(c, .35f));

        // the symbol, large and faint, behind the lot
        RenderSystem.enableBlend();
        g.setColor(((c >> 16 & 255) / 255f), ((c >> 8 & 255) / 255f), ((c & 255) / 255f), .13f);
        int ss = Math.min(w - 40, 150);
        g.blit(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "textures/holo/" + s.symbol() + ".png"),
               x + w - ss - 14, y + 26, 0, 0, ss, ss, ss, ss);
        g.setColor(1, 1, 1, 1);

        g.fillGradient(x, y, x + w, y + 32, Pathways.alpha(c, .85f), Pathways.alpha(Pathways.deep(c, .5f), .85f));
        g.drawString(font, Component.literal(s.title()).withColor(0xFFFFFF), x + 10, y + 7, 0xFFFFFF, false);
        g.drawString(font, Component.literal(s.file()).withColor(0xFFD8D8E0), x + w - font.width(s.file()) - 10, y + 7, 0xFFFFFF, false);
        g.drawString(font, Component.literal(s.flavour()).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY),
                     x + 10, y + 19, 0xFFFFFF, false);

        int ly = y + 44;
        ly = tier(g, x, ly, w, c, "TWO FRAGMENTS", s.twoLine(), null);
        ly = tier(g, x, ly, w, c, "FOUR \u00b7 THE INCIDENT REPEATS", s.fourLine(), s.fourCost());
        ly = tier(g, x, ly, w, c, "FIVE \u00b7 ALL OF IT", s.fiveLine(), s.fiveCost());

        // the depth gauge
        int dy = y + h - 64;
        g.fill(x + 10, dy - 10, x + w - 10, dy - 9, Pathways.alpha(c, .25f));
        g.drawString(font, Component.literal("DEPTH").withColor(Pathways.pale(c, .45f) & 0xFFFFFF), x + 10, dy, 0xFFFFFF, false);
        for (int i = 1; i <= 5; i++) {
            int bx = x + 54 + (i - 1) * 26, by = dy - 4;
            boolean on = i <= difficulty, hot = mx >= bx && mx <= bx + 22 && my >= by && my <= by + 16;
            g.fill(bx, by, bx + 22, by + 16, on ? Pathways.alpha(c, .4f) : (hot ? 0xFF15151D : 0xA00A0A12));
            g.renderOutline(bx, by, 22, 16, Pathways.alpha(c, on ? .9f : .28f));
            g.drawCenteredString(font, String.valueOf(i), bx + 11, by + 4, on ? 0xFFFFFFFF : 0xFF5A5A64);
        }
        String note = switch (difficulty) {
            case 1 -> "1 piece · up to 2✦ · zombies and skeletons";
            case 2 -> "1 piece · up to 3✦";
            case 3 -> "2 pieces · up to 3✦ · vindicators and witches";
            case 4 -> "2 pieces · up to 4✦";
            default -> "2 pieces · up to 5✦ · ravagers and evokers";
        };
        g.drawString(font, Component.literal(note).withStyle(ChatFormatting.DARK_GRAY), x + 10, dy + 20, 0xFFFFFF, false);

        int bx = x + w - 108, by = y + h - 28;
        boolean hot = mx >= bx && mx <= bx + 96 && my >= by && my <= by + 20;
        g.fill(bx, by, bx + 96, by + 20, Pathways.alpha(c, hot ? .6f : .32f));
        g.renderOutline(bx, by, 96, 20, Pathways.alpha(c, .9f));
        g.drawCenteredString(font, Component.literal("Enter").withColor(0xFFFFFF), bx + 48, by + 6, 0xFFFFFF);
    }

    /** One tier: its heading, what it gives in green, and what it takes in red. */
    private int tier(GuiGraphics g, int x, int y, int w, int c, String head, String gives, String takes) {
        g.drawString(font, Component.literal(head).withColor(Pathways.pale(c, .45f) & 0xFFFFFF), x + 10, y, 0xFFFFFF, false);
        y += 12;
        for (String line : wrap(gives, w - 24)) {
            g.drawString(font, Component.literal(line).withColor(0xFF9ADFA8), x + 10, y, 0xFFFFFF, false);
            y += 10;
        }
        if (takes != null)
            for (String line : wrap("\u2013 " + takes, w - 24)) {
                g.drawString(font, Component.literal(line).withColor(0xFFE07A7A), x + 10, y, 0xFFFFFF, false);
                y += 10;
            }
        return y + 8;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int[] r : rows)
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) { picked = r[4]; click(); return true; }

        int x = listX() + listW() + 22, y = listY() - 6, w = width - padX() - x, h = listH() + 12;
        int dy = y + h - 64;
        for (int i = 1; i <= 5; i++) {
            int bx = x + 54 + (i - 1) * 26, by = dy - 4;
            if (mx >= bx && mx <= bx + 22 && my >= by && my <= by + 16) { difficulty = i; click(); return true; }
        }
        int bx = x + w - 108, by = y + h - 28;
        if (mx >= bx && mx <= bx + 96 && my >= by && my <= by + 20) {
            PacketDistributor.sendToServer(new DoorNet.DomainEnter(set().id(), difficulty));
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll -= (int) (dy * 24);
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
