package com.astralspace;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.astralspace.Artifacts.Slot;
import com.astralspace.Artifacts.Stat;

/**
 * The artifact menu: the whole screen, not a window on it.
 *
 * <p>A row of slot tabs along the top, every piece you own for the chosen slot laid out as a wall
 * of tiles on the left, and the one in hand written out in full on the right -- name, slot, main
 * stat, stars, level, sub-stats, and what its set pays at two pieces and at four. The worn piece
 * comes first in the wall and is marked, so equipping is picking a different tile.</p>
 *
 * <p>The colour of the whole thing follows the pathway of the set in hand, which is this mod's
 * answer to colouring by element.</p>
 */
public final class ArtifactScreen extends Screen {

    private int picked;                     // which slot tab
    private int sel = -2;                   // -1 - ordinal for a worn piece, else an inventory index
    private float t;
    private int scroll, detailScroll;
    private final List<int[]> tiles = new ArrayList<>();    // {x, y, w, h, ref}

    private ArtifactScreen() { super(Component.literal("Artifacts")); }

    public static void open() { Minecraft.getInstance().setScreen(new ArtifactScreen()); }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---- geometry, worked out from the window so it fills whatever it is given ----

    private int padX() { return Math.max(16, width / 22); }
    private int padY() { return Math.max(14, height / 18); }
    private int tabY() { return padY() + 22; }
    private int gridX() { return padX() + 6; }
    private int gridY() { return tabY() + 34; }
    private int detailX() { return width - padX() - detailW(); }
    private int detailW() { return Math.max(200, Math.min(300, width / 3)); }
    private int gridW() { return detailX() - gridX() - 16; }
    private int gridH() { return height - gridY() - padY() - 26; }
    private int cell() { return 54; }

    // ---- what is where ----

    private ItemStack stackOf(int ref) {
        var pl = Minecraft.getInstance().player;
        if (pl == null) return ItemStack.EMPTY;
        if (ref <= -1) {
            int s = -ref - 1;
            ItemStack[] w = ArtifactSlots.clientWorn();
            return s >= 0 && s < w.length && w[s] != null ? w[s] : ItemStack.EMPTY;
        }
        return ref < pl.getInventory().getContainerSize() ? pl.getInventory().getItem(ref) : ItemStack.EMPTY;
    }

    private Slot slot() { return Slot.values()[picked]; }

    private int accent() {
        Artifacts.Piece p = Artifacts.read(stackOf(sel));
        if (p != null) {
            ArtifactSets.Set s = ArtifactSets.get(p.set());
            if (s != null) return s.colour();
        }
        var pl = Minecraft.getInstance().player;
        if (pl != null) try {
            String own = Launcher.readPathway(pl.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
            if (!own.isEmpty()) return Pathways.colour(own);
        } catch (Throwable ignored) { }
        return Pathways.GOLD;
    }

    /** Every piece that fits the chosen slot: the worn one first, then the bag. */
    private List<Integer> refs() {
        List<Integer> out = new ArrayList<>();
        ItemStack worn = stackOf(-1 - picked);
        if (!worn.isEmpty()) out.add(-1 - picked);
        var pl = Minecraft.getInstance().player;
        if (pl != null) {
            var inv = pl.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (st.isEmpty() || !(st.getItem() instanceof ArtifactItem ai) || ai.slot() != slot()) continue;
                if (Artifacts.read(st) == null) continue;
                out.add(i);
            }
        }
        return out;
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        t += pt / 20f;
        int c = accent();
        tiles.clear();

        backdrop(g, c);
        header(g, c);
        tabs(g, mx, my, c);
        wall(g, mx, my, c);
        detail(g, c);
        footer(g, c);

        super.render(g, mx, my, pt);
        for (int[] h : tiles)
            if (mx >= h[0] && mx <= h[0] + h[2] && my >= h[1] && my <= h[1] + h[3]) {
                g.renderTooltip(font, stackOf(h[4]), mx, my);
                break;
            }
    }

    /** The whole window: a deep field lit from the middle, with a drift of motes over it. */
    private void backdrop(GuiGraphics g, int c) {
        g.fill(0, 0, width, height, 0xFF070910);
        g.fillGradient(0, 0, width, height / 2, Pathways.alpha(Pathways.deep(c, .5f), .30f), 0x00000000);
        g.fillGradient(0, height / 2, width, height, 0x00000000, Pathways.alpha(Pathways.deep(c, .32f), .26f));
        for (int i = 0; i < 90; i++) {                        // a slow drift of light, so the field is not flat
            float u = fr(i * .61803f), v = fr(i * .38196f + t * (.004f + .006f * fr(i * .137f)));
            int x = (int) (u * width), y = (int) ((1 - v) * height);
            int a = (int) ((40 + 70 * Math.abs(Math.sin(t * .8 + i))) * (i % 5 == 0 ? 1.4f : .8f));
            g.fill(x, y, x + (i % 7 == 0 ? 2 : 1), y + (i % 7 == 0 ? 2 : 1), (Math.min(200, a) << 24) | (Pathways.pale(c, .5f) & 0xFFFFFF));
        }
        g.fill(0, 0, width, 2, Pathways.alpha(Pathways.pale(c, .4f), .7f));
        g.fill(0, height - 2, width, height, Pathways.alpha(Pathways.deep(c, .6f), .7f));
    }

    private void header(GuiGraphics g, int c) {
        g.drawString(font, Component.literal("ARTIFACTS").withColor(Pathways.pale(c, .5f) & 0xFFFFFF),
                     padX(), padY(), 0xFFFFFF, false);
        int n = 0;
        for (Slot s : Slot.values()) if (!stackOf(-1 - s.ordinal()).isEmpty()) n++;
        g.drawString(font, Component.literal(n + " / 5 worn").withStyle(ChatFormatting.DARK_GRAY),
                     padX() + font.width("ARTIFACTS") + 12, padY(), 0xFFFFFF, false);
        // the sets paying out right now, along the top right
        var counts = ArtifactSets.counts(ArtifactSlots.clientWorn());
        int x = width - padX();
        for (var e : counts.entrySet()) {
            ArtifactSets.Set set = ArtifactSets.get(e.getKey());
            if (set == null || e.getValue() < 2) continue;
            String s = set.title() + " " + (e.getValue() >= 4 ? "4" : "2");
            x -= font.width(s) + 14;
            g.drawString(font, Component.literal(s).withColor(set.colour() & 0xFFFFFF), x, padY(), 0xFFFFFF, false);
        }
    }

    /** The five slot tabs, with the worn piece's colour under each. */
    private void tabs(GuiGraphics g, int mx, int my, int c) {
        int w = 54, h = 26, x0 = padX(), y = tabY();
        for (Slot s : Slot.values()) {
            int x = x0 + s.ordinal() * (w + 4);
            boolean on = s.ordinal() == picked;
            boolean hot = mx >= x && mx <= x + w && my >= y && my <= y + h;
            Artifacts.Piece p = Artifacts.read(stackOf(-1 - s.ordinal()));
            ArtifactSets.Set set = p == null ? null : ArtifactSets.get(p.set());
            int sc = set == null ? 0x6A6A74 : set.colour();
            g.fill(x, y, x + w, y + h, on ? Pathways.alpha(sc, .28f) : (hot ? 0xFF13131B : 0xA00A0A12));
            g.renderOutline(x, y, w, h, Pathways.alpha(sc, on ? 1f : .3f));
            if (on) g.fill(x, y + h - 2, x + w, y + h, Pathways.alpha(Pathways.pale(sc, .4f), 1f));
            g.renderItem(new ItemStack(ArtifactItem.of(s)), x + 4, y + 5);
            g.drawString(font, Component.literal(s.title.split(" ")[0])
                    .withColor(on ? Pathways.pale(sc, .5f) & 0xFFFFFF : 0xFF6A6A74), x + 23, y + 9, 0xFFFFFF, false);
        }
    }

    /** The wall of pieces for the chosen slot. */
    private void wall(GuiGraphics g, int mx, int my, int c) {
        int gx = gridX(), gy = gridY(), gw = gridW(), gh = gridH(), cs = cell();
        g.fill(gx - 6, gy - 6, gx + gw + 6, gy + gh + 6, 0x900A0A12);
        g.renderOutline(gx - 6, gy - 6, gw + 12, gh + 12, Pathways.alpha(c, .3f));

        int cols = Math.max(1, gw / cs);
        List<Integer> all = refs();
        int rows = (all.size() + cols - 1) / cols;
        int maxScroll = Math.max(0, rows * cs - gh);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        if (all.isEmpty())
            g.drawString(font, Component.literal("Nothing for this slot yet.").withStyle(ChatFormatting.DARK_GRAY),
                         gx + 6, gy + 8, 0xFFFFFF, false);

        g.enableScissor(gx - 4, gy - 4, gx + gw + 4, gy + gh + 4);
        for (int i = 0; i < all.size(); i++) {
            int ref = all.get(i);
            int x = gx + (i % cols) * cs, y = gy + (i / cols) * cs - scroll;
            if (y + cs < gy - cs || y > gy + gh + cs) continue;
            tile(g, mx, my, x, y, cs - 4, ref, ref == sel);
            tiles.add(new int[]{x, y, cs - 4, cs - 4, ref});
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int bh = Math.max(16, (int) (gh * (gh / (float) (rows * cs))));
            int by = gy + (int) ((gh - bh) * (scroll / (float) maxScroll));
            g.fill(gx + gw + 1, gy, gx + gw + 4, gy + gh, 0xFF14141C);
            g.fill(gx + gw + 1, by, gx + gw + 4, by + bh, Pathways.alpha(c, .8f));
        }
    }

    /** One piece as a tile: its set's colour behind it, stars under it, the level in the corner. */
    private void tile(GuiGraphics g, int mx, int my, int x, int y, int s, int ref, boolean on) {
        ItemStack st = stackOf(ref);
        Artifacts.Piece p = Artifacts.read(st);
        if (p == null) return;
        ArtifactSets.Set set = ArtifactSets.get(p.set());
        int sc = set == null ? 0x6A6A74 : set.colour();
        boolean hot = mx >= x && mx <= x + s && my >= y && my <= y + s;

        g.fillGradient(x, y, x + s, y + s, Pathways.alpha(Pathways.deep(sc, .55f), .85f), Pathways.alpha(Pathways.deep(sc, .25f), .95f));
        if (on) g.fill(x, y, x + s, y + s, Pathways.alpha(sc, .3f));
        g.renderOutline(x, y, s, s, Pathways.alpha(sc, on ? 1f : (hot ? .8f : .35f)));

        if (set != null) {
            RenderSystem.enableBlend();
            g.setColor(((sc >> 16 & 255) / 255f), ((sc >> 8 & 255) / 255f), ((sc & 255) / 255f), on ? .4f : .22f);
            g.blit(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "textures/holo/" + set.symbol() + ".png"),
                   x + 2, y + 2, 0, 0, s - 4, s - 4, s - 4, s - 4);
            g.setColor(1, 1, 1, 1);
        }
        g.pose().pushPose();
        g.pose().translate(x + s / 2f - 12, y + 6, 0);
        g.pose().scale(1.5f, 1.5f, 1);
        g.renderItem(st, 0, 0);
        g.pose().popPose();

        String stars = "✦".repeat(p.rarity());
        g.drawString(font, Component.literal(stars).withColor(0xFFC24A),
                     x + (s - font.width(stars)) / 2, y + s - 19, 0xFFFFFF, false);
        String lv = "+" + p.level();
        g.fill(x + 2, y + s - 10, x + 4 + font.width(lv), y + s - 1, 0xC0000000);
        g.drawString(font, Component.literal(lv).withStyle(ChatFormatting.WHITE), x + 3, y + s - 9, 0xFFFFFF, false);
        if (ref <= -1) {
            g.fill(x + s - 7, y + 2, x + s - 2, y + 7, Pathways.alpha(Pathways.pale(sc, .5f), 1f));
        }
    }

    /** The piece in hand, written out in full, clipped to the panel it is written into. */
    private void detail(GuiGraphics g, int c) {
        int x = detailX(), y = gridY() - 6, w = detailW(), h = gridH() + 12;
        g.fill(x, y, x + w, y + h, 0xE0080A12);
        g.renderOutline(x, y, w, h, Pathways.alpha(c, .35f));

        ItemStack st = stackOf(sel);
        Artifacts.Piece p = Artifacts.read(st);
        if (p == null) {
            g.drawCenteredString(font, Component.literal("Pick a piece.").withStyle(ChatFormatting.DARK_GRAY),
                                 x + w / 2, y + h / 2, 0xFFFFFF);
            return;
        }
        ArtifactSets.Set set = ArtifactSets.get(p.set());
        int sc = set == null ? c : set.colour();
        int lh = font.lineHeight + 1;

        // the head
        int headH = lh * 2 + 10;
        g.fillGradient(x, y, x + w, y + headH, Pathways.alpha(sc, .9f), Pathways.alpha(Pathways.deep(sc, .5f), .9f));
        g.drawString(font, Component.literal(trim(set == null ? p.slot().title : set.title(), w - 16)).withColor(0xFFFFFF),
                     x + 8, y + 5, 0xFFFFFF, false);
        g.drawString(font, Component.literal(trim(p.slot().title + "  \u00b7  " + Artifacts.grade(p.rarity()), w - 16))
                .withColor(0xFFE4E4EC), x + 8, y + 5 + lh, 0xFFFFFF, false);

        // the main stat, large, in a band of its own
        int my2 = y + headH + 4, bandH = lh * 3 + 6;
        g.fill(x + 6, my2, x + w - 6, my2 + bandH, Pathways.alpha(Pathways.deep(sc, .5f), .35f));
        g.drawString(font, Component.literal(p.main().title).withStyle(ChatFormatting.GRAY), x + 10, my2 + 3, 0xFFFFFF, false);
        String mv = p.main().pct ? String.format("%.1f%%", p.mainValue() * 100) : String.format("%.0f", p.mainValue());
        g.pose().pushPose();
        g.pose().translate(x + 10, my2 + 3 + lh, 0);
        g.pose().scale(1.6f, 1.6f, 1);
        g.drawString(font, Component.literal(mv).withColor(0xFFFFFF), 0, 0, 0xFFFFFF, false);
        g.pose().popPose();
        String stars = "\u2726".repeat(p.rarity());
        g.drawString(font, Component.literal(stars).withColor(0xFFC24A), x + w - font.width(stars) - 10, my2 + 3, 0xFFFFFF, false);
        String lvs = "+" + p.level() + " / " + p.maxLevel();
        g.drawString(font, Component.literal(lvs).withStyle(ChatFormatting.GRAY),
                     x + w - font.width(lvs) - 10, my2 + 3 + lh, 0xFFFFFF, false);
        int bx = x + 10, bw = w - 20, by = my2 + bandH - 5;
        g.fill(bx, by, bx + bw, by + 3, 0xFF16161E);
        int fill = p.maxLevel() == 0 ? 0 : (int) (bw * (p.level() / (float) p.maxLevel()));
        g.fillGradient(bx, by, bx + fill, by + 3, Pathways.alpha(Pathways.pale(sc, .35f), 1f), Pathways.alpha(sc, 1f));

        // everything else, flattened to lines and clipped into whatever is left
        int by0 = my2 + bandH + 6, bh = y + h - 6 - by0;
        if (bh < lh) return;
        List<Component> lines = new ArrayList<>();
        for (Artifacts.Sub sub : p.subs())
            lines.add(Component.literal("\u00b7 " + sub.stat().line(sub.value())
                    + (sub.rolls() > 1 ? "  (" + sub.rolls() + ")" : "")).withStyle(ChatFormatting.GRAY));
        for (int k = p.subs().size(); k < 4; k++)
            lines.add(Component.literal("\u00b7 \u2014").withStyle(ChatFormatting.DARK_GRAY));
        if (set != null) {
            int have = ArtifactSets.counts(ArtifactSlots.clientWorn()).getOrDefault(set.id(), 0);
            lines.add(Component.empty());
            lines.add(Component.literal(set.title() + " (" + have + "/5)").withColor(sc & 0xFFFFFF));
            lines.add(Component.literal(set.file()).withStyle(ChatFormatting.DARK_GRAY));
            tierLines(lines, "2  " + set.twoLine(), null, have >= 2, w - 20);
            tierLines(lines, "4  " + set.fourLine(), set.fourCost(), have >= 4, w - 20);
            String ab = SetAbilities.name(set.id(), SetAbilities.rung(Minecraft.getInstance().player, set.id()));
            tierLines(lines, "5  " + (ab.isEmpty() ? set.fiveLine() : "Grants " + ab), set.fiveCost(), have >= 5, w - 20);
            for (String line : wrap(set.flavour(), w - 20))
                lines.add(Component.literal(line).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }

        int max = Math.max(0, lines.size() * lh - bh);
        detailScroll = Math.max(0, Math.min(detailScroll, max));
        g.enableScissor(x + 2, by0, x + w - 2, by0 + bh);
        for (int i = 0; i < lines.size(); i++) {
            int ly = by0 + i * lh - detailScroll;
            if (ly + lh < by0 || ly > by0 + bh) continue;
            g.drawString(font, lines.get(i), x + 8, ly, 0xFFFFFF, false);
        }
        g.disableScissor();
        if (max > 0) {
            int sh = Math.max(14, (int) (bh * (bh / (float) (lines.size() * lh))));
            int sy = by0 + (int) ((bh - sh) * (detailScroll / (float) max));
            g.fill(x + w - 5, by0, x + w - 3, by0 + bh, 0xFF14141C);
            g.fill(x + w - 5, sy, x + w - 3, sy + sh, Pathways.alpha(sc, .8f));
        }
    }

    /** One tier, flattened: what it gives, then what it takes, greyed until it is on you. */
    private void tierLines(List<Component> out, String gives, String takes, boolean live, int px) {
        for (String line : wrap(gives, px))
            out.add(Component.literal(line).withColor(live ? 0xFF9ADFA8 : 0xFF5A6A5E));
        if (takes != null)
            for (String line : wrap("   \u2013 " + takes, px))
                out.add(Component.literal(line).withColor(live ? 0xFFE07A7A : 0xFF5A4A4A));
    }

    /** The bar along the bottom: what can be done with what is picked. */
    private void footer(GuiGraphics g, int c) {
        int y = height - padY() - 18;
        g.fill(padX(), y, width - padX(), y + 1, Pathways.alpha(c, .3f));
        boolean has = Artifacts.read(stackOf(sel)) != null;
        String a = sel <= -1 ? "Take off" : "Wear";
        button(g, padX(), y + 5, 70, a, has, c);
        button(g, padX() + 76, y + 5, 90, "Reroll (1 dust)", has && dust() > 0, c);
        g.drawString(font, Component.literal("Dust of Enlightenment: " + dust()).withStyle(ChatFormatting.DARK_GRAY),
                     padX() + 174, y + 9, 0xFFFFFF, false);
        g.drawString(font, Component.literal("Levels are raised at an Artifact Altar · Esc to close")
                .withStyle(ChatFormatting.DARK_GRAY), width - padX() - 250, y + 9, 0xFFFFFF, false);
    }

    private void button(GuiGraphics g, int x, int y, int w, String s, boolean live, int c) {
        g.fill(x, y, x + w, y + 14, live ? Pathways.alpha(c, .3f) : 0xFF101018);
        g.renderOutline(x, y, w, 14, Pathways.alpha(live ? c : 0x3A3A44, .9f));
        g.drawCenteredString(font, Component.literal(s).withColor(live ? 0xFFFFFF : 0xFF5A5A64), x + w / 2, y + 3, 0xFFFFFF);
    }

    private int dust() {
        var pl = Minecraft.getInstance().player;
        if (pl == null) return 0;
        int n = 0;
        var inv = pl.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++)
            if (inv.getItem(i).is(AstralFloor.DUST.get())) n += inv.getItem(i).getCount();
        return n;
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int w = 54, h = 26, x0 = padX(), ty = tabY();
        for (Slot s : Slot.values()) {
            int x = x0 + s.ordinal() * (w + 4);
            if (mx >= x && mx <= x + w && my >= ty && my <= ty + h) {
                picked = s.ordinal(); sel = -2; scroll = 0; detailScroll = 0; click(); return true;
            }
        }
        for (int[] hit : tiles)
            if (mx >= hit[0] && mx <= hit[0] + hit[2] && my >= hit[1] && my <= hit[1] + hit[3]) {
                sel = hit[4]; detailScroll = 0; click(); return true;
            }
        int y = height - padY() - 18;
        if (my >= y + 5 && my <= y + 19 && Artifacts.read(stackOf(sel)) != null) {
            if (mx >= padX() && mx <= padX() + 70) {
                PacketDistributor.sendToServer(new DoorNet.ArtifactAct(picked, sel <= -1 ? -1 : sel));
                sel = -2; click(); return true;
            }
            if (mx >= padX() + 76 && mx <= padX() + 166 && dust() > 0) {
                PacketDistributor.sendToServer(new DoorNet.ArtifactReroll(sel));
                click(); return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (mx >= detailX()) detailScroll -= (int) (dy * 24);
        else scroll -= (int) (dy * 24);
        return true;
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.1f, .4f));
    }

    // ---- odds and ends ----

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
