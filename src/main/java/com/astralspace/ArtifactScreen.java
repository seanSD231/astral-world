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
 * The artifact menu.
 *
 * <p>Three columns, as the games lay it out: the five pieces down the left, whichever of them is
 * picked blown up in the middle, and everything they come to on the right. The colour of the whole
 * thing follows the pathway of the set in hand, so a Hanged Man piece turns the panel its red and a
 * Fool piece its violet, and when nothing is picked it falls back to the player's own pathway.</p>
 *
 * <p>Nothing here decides anything. Clicking sends a request and the server is what moves a piece
 * and sends the slot back, so a dropped packet leaves the menu showing what is really on.</p>
 */
public final class ArtifactScreen extends Screen {
    private static final int W = 420, H = 232;
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private int left, top;
    private int picked = 0;
    private float t;
    private int scroll;
    private final List<int[]> bagHits = new ArrayList<>();     // {x, y, inventory index}

    private ArtifactScreen() { super(Component.literal("Artifacts")); }

    public static void open() { Minecraft.getInstance().setScreen(new ArtifactScreen()); }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---- what is on, and what is in the bag ----

    private ItemStack[] worn() { return ArtifactSlots.clientWorn(); }

    private ItemStack at(Slot s) {
        ItemStack st = worn()[s.ordinal()];
        return st == null ? ItemStack.EMPTY : st;
    }

    private Slot slot() { return Slot.values()[picked]; }

    /** The accent: the picked piece's pathway, or the player's own when the slot is empty. */
    private int accent() {
        Artifacts.Piece p = Artifacts.read(at(slot()));
        if (p != null) {
            ArtifactSets.Set set = ArtifactSets.get(p.set());
            if (set != null) return set.colour();
        }
        var pl = Minecraft.getInstance().player;
        if (pl != null) try {
            String own = Launcher.readPathway(pl.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
            if (!own.isEmpty()) return Pathways.colour(own);
        } catch (Throwable ignored) { }
        return Pathways.GOLD;
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        t += pt / 20f;
        renderBackground(g, mx, my, pt);
        int c = accent();

        frame(g, left - 4, top - 4, W + 8, H + 8, c);
        bagHits.clear();

        drawSlots(g, mx, my, c);
        drawDetail(g, mx, my, c);
        drawTotals(g, c);

        super.render(g, mx, my, pt);
        drawHover(g, mx, my);
    }

    /** The shell: a dark panel washed with the pathway colour, with a lit edge and a top rule. */
    private void frame(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + h, 0xF00A0A0E);
        g.fillGradient(x, y, x + w, y + h / 2, Pathways.alpha(Pathways.deep(c, .55f), .22f), 0x00000000);
        g.fillGradient(x, y + h - 40, x + w, y + h, 0x00000000, Pathways.alpha(Pathways.deep(c, .35f), .18f));
        g.renderOutline(x, y, w, h, Pathways.alpha(c, .55f));
        g.fill(x + 1, y + 1, x + w - 1, y + 2, Pathways.alpha(Pathways.pale(c, .4f), .8f));
        g.drawString(font, Component.literal("ARTIFACTS").withColor(Pathways.pale(c, .5f) & 0xFFFFFF),
                x + 10, y + 8, 0xFFFFFF, false);
        String sub = "Five worn at once · two pieces of a set, and four";
        g.drawString(font, Component.literal(sub).withStyle(ChatFormatting.DARK_GRAY), x + 10, y + 19, 0xFFFFFF, false);
    }

    // ---- left: the five pieces ----

    private void drawSlots(GuiGraphics g, int mx, int my, int c) {
        int x = left + 6, y = top + 26, w = 116, rh = 36;
        for (Slot s : Slot.values()) {
            int sy = y + s.ordinal() * (rh + 3);
            boolean on = s.ordinal() == picked;
            boolean hot = mx >= x && mx <= x + w && my >= sy && my <= sy + rh;
            ItemStack st = at(s);
            Artifacts.Piece p = Artifacts.read(st);
            ArtifactSets.Set set = p == null ? null : ArtifactSets.get(p.set());
            int sc = set == null ? 0x707078 : set.colour();

            g.fill(x, sy, x + w, sy + rh, on ? 0xFF14141C : (hot ? 0xFF101018 : 0xC00C0C12));
            if (on) {
                g.fillGradient(x, sy, x + w, sy + rh, Pathways.alpha(sc, .26f), Pathways.alpha(sc, .05f));
                g.fill(x, sy, x + 2, sy + rh, Pathways.alpha(Pathways.pale(sc, .3f), 1f));
            }
            g.renderOutline(x, sy, w, rh, Pathways.alpha(sc, on ? .7f : .25f));

            // the pathway symbol behind the piece, faint, as the games show the artifact art
            if (set != null) {
                RenderSystem.enableBlend();
                g.setColor(((sc >> 16 & 255) / 255f), ((sc >> 8 & 255) / 255f), ((sc & 255) / 255f), on ? .30f : .16f);
                g.blit(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "textures/holo/" + set.symbol() + ".png"),
                       x + w - 34, sy + 2, 0, 0, 32, 32, 32, 32);
                g.setColor(1, 1, 1, 1);
            }

            if (st.isEmpty()) {
                g.drawString(font, Component.literal(s.title).withStyle(ChatFormatting.DARK_GRAY), x + 8, sy + 8, 0xFFFFFF, false);
                g.drawString(font, Component.literal("empty").withStyle(ChatFormatting.DARK_GRAY), x + 8, sy + 20, 0xFFFFFF, false);
            } else {
                g.renderItem(st, x + 6, sy + 10);
                String name = set == null ? s.title : set.title();
                g.drawString(font, Component.literal(trim(name, 84)).withColor(sc & 0xFFFFFF), x + 26, sy + 6, 0xFFFFFF, false);
                g.drawString(font, Component.literal(stars(p.rarity())).withColor(0xFFC24A), x + 26, sy + 17, 0xFFFFFF, false);
                g.drawString(font, Component.literal("+" + p.level()).withStyle(ChatFormatting.GRAY), x + 26, sy + 27, 0xFFFFFF, false);
                g.drawString(font, Component.literal(p.main().line(p.mainValue())).withStyle(ChatFormatting.DARK_GRAY),
                             x + 48, sy + 27, 0xFFFFFF, false);
            }
        }
    }

    // ---- middle: the piece itself ----

    private void drawDetail(GuiGraphics g, int mx, int my, int c) {
        int x = left + 128, y = top + 26, w = 148, h = H - 32;
        g.fill(x, y, x + w, y + h, 0xC00B0B11);
        g.renderOutline(x, y, w, h, Pathways.alpha(c, .35f));

        ItemStack st = at(slot());
        Artifacts.Piece p = Artifacts.read(st);
        ArtifactSets.Set set = p == null ? null : ArtifactSets.get(p.set());

        if (p == null) {
            g.drawCenteredString(font, Component.literal(slot().title).withStyle(ChatFormatting.GRAY), x + w / 2, y + 10, 0xFFFFFF);
            g.drawCenteredString(font, Component.literal("Nothing in this slot.").withStyle(ChatFormatting.DARK_GRAY), x + w / 2, y + 26, 0xFFFFFF);
            drawBag(g, x, y + 46, w, mx, my, c);
            return;
        }
        int sc = set == null ? c : set.colour();

        // the symbol, big and turning slowly, with the piece sitting on it
        RenderSystem.enableBlend();
        if (set != null) {
            g.pose().pushPose();
            g.pose().translate(x + w / 2f, y + 40, 0);
            g.pose().scale(2.1f, 2.1f, 1);
            g.setColor(((sc >> 16 & 255) / 255f), ((sc >> 8 & 255) / 255f), ((sc & 255) / 255f), .22f);
            g.blit(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "textures/holo/" + set.symbol() + ".png"),
                   -16, -16, 0, 0, 32, 32, 32, 32);
            g.setColor(1, 1, 1, 1);
            g.pose().popPose();
        }
        g.pose().pushPose();
        g.pose().translate(x + w / 2f - 16, y + 24, 0);
        g.pose().scale(2f, 2f, 1);
        g.renderItem(st, 0, 0);
        g.pose().popPose();

        g.drawCenteredString(font, Component.literal(trim(set == null ? slot().title : set.title(), w - 12))
                .withColor(sc & 0xFFFFFF), x + w / 2, y + 60, 0xFFFFFF);
        g.drawCenteredString(font, Component.literal(slot().title + "   " + stars(p.rarity()))
                .withStyle(ChatFormatting.DARK_GRAY), x + w / 2, y + 72, 0xFFFFFF);

        // the level, and how far along it is
        int bx = x + 10, bw = w - 20, by = y + 86;
        g.fill(bx, by, bx + bw, by + 4, 0xFF18181F);
        int fill = p.maxLevel() == 0 ? 0 : (int) (bw * (p.level() / (float) p.maxLevel()));
        g.fillGradient(bx, by, bx + fill, by + 4, Pathways.alpha(Pathways.pale(sc, .35f), 1f), Pathways.alpha(sc, 1f));
        g.drawString(font, Component.literal("+" + p.level()).withColor(sc & 0xFFFFFF), bx, by - 11, 0xFFFFFF, false);
        g.drawString(font, Component.literal("/ " + p.maxLevel()).withStyle(ChatFormatting.DARK_GRAY),
                     bx + font.width("+" + p.level()) + 3, by - 11, 0xFFFFFF, false);

        // the main stat, large
        int sy = by + 12;
        g.fill(bx, sy, bx + bw, sy + 24, Pathways.alpha(Pathways.deep(sc, .5f), .25f));
        g.drawString(font, Component.literal(p.main().title).withStyle(ChatFormatting.GRAY), bx + 4, sy + 4, 0xFFFFFF, false);
        String mv = p.main().pct ? String.format("%.1f%%", p.mainValue() * 100) : String.format("%.0f", p.mainValue());
        g.drawString(font, Component.literal(mv).withColor(Pathways.pale(sc, .55f) & 0xFFFFFF),
                     bx + bw - font.width(mv) - 4, sy + 4, 0xFFFFFF, false);

        // the sub-stats, with a pip for every roll that went into each
        int ly = sy + 30;
        for (Artifacts.Sub s : p.subs()) {
            g.drawString(font, Component.literal("· " + s.stat().line(s.value())).withStyle(ChatFormatting.GRAY),
                         bx + 2, ly, 0xFFFFFF, false);
            for (int k = 0; k < s.rolls(); k++)
                g.fill(bx + bw - 4 - k * 5, ly + 2, bx + bw - 1 - k * 5, ly + 6, Pathways.alpha(sc, .85f));
            ly += 11;
        }
        for (int k = p.subs().size(); k < 4; k++) {
            g.drawString(font, Component.literal("· —").withStyle(ChatFormatting.DARK_GRAY), bx + 2, ly, 0xFFFFFF, false);
            ly += 11;
        }

        drawBag(g, x, ly + 6, w, mx, my, c);
    }

    /** The pieces in the bag that would fit the slot in hand, and a way to take the worn one off. */
    private void drawBag(GuiGraphics g, int x, int y, int w, int mx, int my, int c) {
        var pl = Minecraft.getInstance().player;
        if (pl == null) return;
        g.fill(x + 6, y, x + w - 6, y + 1, Pathways.alpha(c, .25f));
        g.drawString(font, Component.literal("In your bag").withStyle(ChatFormatting.DARK_GRAY), x + 8, y + 5, 0xFFFFFF, false);

        if (!at(slot()).isEmpty()) {
            boolean hot = mx >= x + w - 52 && mx <= x + w - 8 && my >= y + 4 && my <= y + 14;
            g.drawString(font, Component.literal("take off").withColor(hot ? 0xFFE08080 : 0xFF806060),
                         x + w - 50, y + 5, 0xFFFFFF, false);
        }

        int bx = x + 8, by = y + 18, n = 0;
        var inv = pl.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || !(st.getItem() instanceof ArtifactItem ai) || ai.slot() != slot()) continue;
            if (Artifacts.read(st) == null) continue;
            if (n >= 24) break;
            int px = bx + (n % 7) * 19, py = by + (n / 7) * 19;
            Artifacts.Piece p = Artifacts.read(st);
            ArtifactSets.Set set = ArtifactSets.get(p.set());
            int sc = set == null ? 0x606068 : set.colour();
            boolean hot = mx >= px && mx <= px + 17 && my >= py && my <= py + 17;
            g.fill(px, py, px + 17, py + 17, hot ? Pathways.alpha(sc, .3f) : 0xA00C0C12);
            g.renderOutline(px, py, 17, 17, Pathways.alpha(sc, hot ? .9f : .4f));
            g.renderItem(st, px + 1, py + 1);
            bagHits.add(new int[]{px, py, i});
            n++;
        }
        if (n == 0) g.drawString(font, Component.literal("nothing that fits").withStyle(ChatFormatting.DARK_GRAY),
                                 bx, by + 3, 0xFFFFFF, false);
    }

    // ---- right: what it all comes to ----

    private void drawTotals(GuiGraphics g, int c) {
        int x = left + 282, y = top + 26, w = 132, h = H - 32;
        g.fill(x, y, x + w, y + h, 0xC00B0B11);
        g.renderOutline(x, y, w, h, Pathways.alpha(c, .35f));
        g.drawString(font, Component.literal("TOTAL").withColor(Pathways.pale(c, .45f) & 0xFFFFFF), x + 8, y + 7, 0xFFFFFF, false);

        Map<Stat, Double> totals = Artifacts.totals(worn());
        int ly = y + 21;
        for (Stat s : Stat.values()) {
            double v = totals.getOrDefault(s, 0.0);
            if (v == 0) continue;
            if (s.lotm() && s.attribute() == null) continue;
            g.drawString(font, Component.literal(s.title).withStyle(ChatFormatting.GRAY), x + 8, ly, 0xFFFFFF, false);
            String val = s.pct ? String.format("%+.1f%%", v * 100) : String.format("%+.0f", v);
            g.drawString(font, Component.literal(val).withColor(Pathways.pale(c, .5f) & 0xFFFFFF),
                         x + w - font.width(val) - 8, ly, 0xFFFFFF, false);
            ly += 10;
            if (ly > y + h - 70) break;
        }

        // the sets being worn, and which half of each is paying
        int sy = Math.max(ly + 6, y + h - 66);
        g.fill(x + 6, sy, x + w - 6, sy + 1, Pathways.alpha(c, .25f));
        g.drawString(font, Component.literal("SETS").withColor(Pathways.pale(c, .45f) & 0xFFFFFF), x + 8, sy + 6, 0xFFFFFF, false);
        int ry = sy + 18;
        var counts = ArtifactSets.counts(worn());
        if (counts.isEmpty())
            g.drawString(font, Component.literal("none").withStyle(ChatFormatting.DARK_GRAY), x + 8, ry, 0xFFFFFF, false);
        for (var e : counts.entrySet()) {
            ArtifactSets.Set set = ArtifactSets.get(e.getKey());
            if (set == null) continue;
            int sc = set.colour(), n = e.getValue();
            g.drawString(font, Component.literal(trim(set.title(), w - 40)).withColor(sc & 0xFFFFFF), x + 8, ry, 0xFFFFFF, false);
            for (int k = 0; k < 4; k++)
                g.fill(x + w - 26 + k * 5, ry + 2, x + w - 23 + k * 5, ry + 6,
                       k < n ? Pathways.alpha(sc, .95f) : 0xFF1E1E26);
            ry += 10;
            if (n >= 2) { g.drawString(font, Component.literal("  2pc " + set.twoLine()).withStyle(ChatFormatting.DARK_GRAY), x + 8, ry, 0xFFFFFF, false); ry += 9; }
            if (n >= 4) { g.drawString(font, Component.literal("  4pc " + set.fourLine()).withStyle(ChatFormatting.DARK_GRAY), x + 8, ry, 0xFFFFFF, false); ry += 9; }
            if (ry > y + h - 10) break;
        }
    }

    private void drawHover(GuiGraphics g, int mx, int my) {
        for (int[] hit : bagHits) {
            if (mx < hit[0] || mx > hit[0] + 17 || my < hit[1] || my > hit[1] + 17) continue;
            var pl = Minecraft.getInstance().player;
            if (pl == null) return;
            g.renderTooltip(font, pl.getInventory().getItem(hit[2]), mx, my);
            return;
        }
    }

    // ---- clicks ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = left + 6, y = top + 26, w = 116, rh = 36;
        for (Slot s : Slot.values()) {
            int sy = y + s.ordinal() * (rh + 3);
            if (mx >= x && mx <= x + w && my >= sy && my <= sy + rh) {
                picked = s.ordinal();
                click();
                return true;
            }
        }
        for (int[] hit : bagHits) {
            if (mx >= hit[0] && mx <= hit[0] + 17 && my >= hit[1] && my <= hit[1] + 17) {
                PacketDistributor.sendToServer(new DoorNet.ArtifactAct(picked, hit[2]));
                click();
                return true;
            }
        }
        // "take off" sits at the right of the bag's heading, wherever the detail column put it
        if (!at(slot()).isEmpty()) {
            int dx = left + 128, dw = 148;
            if (mx >= dx + dw - 52 && mx <= dx + dw - 8 && my >= top + 26 && my <= top + H) {
                PacketDistributor.sendToServer(new DoorNet.ArtifactAct(picked, -1));
                click();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.1f, .4f));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 265) { picked = (picked + Slot.values().length - 1) % Slot.values().length; return true; }   // up
        if (key == 264) { picked = (picked + 1) % Slot.values().length; return true; }                          // down
        return super.keyPressed(key, scan, mods);
    }

    // ---- odds and ends ----

    private String trim(String s, int px) {
        if (font.width(s) <= px) return s;
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (font.width(b.toString() + ch + "…") > px) break;
            b.append(ch);
        }
        return b + "…";
    }

    private static String stars(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append('✦');
        return b.toString();
    }
}
