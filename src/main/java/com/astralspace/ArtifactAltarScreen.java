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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The altar's face: a piece on the left, what is being burnt to raise it on the right, and the
 * level it will come out at written where you can see it before you commit.
 *
 * <p>The sums are done again here only so the preview is honest. Nothing it works out is sent: the
 * packet carries which piece and which fodder, and the server does the arithmetic that counts.</p>
 */
public final class ArtifactAltarScreen extends Screen {
    private static final int W = 356, H = 214;

    private int left, top;
    /** Inventory index, or -1 - slot for one of the worn five. */
    private int target = Integer.MIN_VALUE;
    private final Set<Integer> fodder = new LinkedHashSet<>();
    private final List<int[]> targetHits = new ArrayList<>();     // {x, y, ref}
    private final List<int[]> fodderHits = new ArrayList<>();

    private ArtifactAltarScreen() { super(Component.literal("Altar")); }

    public static void open() { Minecraft.getInstance().setScreen(new ArtifactAltarScreen()); }

    @Override
    protected void init() { left = (width - W) / 2; top = (height - H) / 2; }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---- what is being worked on ----

    private ItemStack stackOf(int ref) {
        var pl = Minecraft.getInstance().player;
        if (pl == null) return ItemStack.EMPTY;
        if (ref < 0) {
            int s = -ref - 1;
            ItemStack[] w = ArtifactSlots.clientWorn();
            return s >= 0 && s < w.length && w[s] != null ? w[s] : ItemStack.EMPTY;
        }
        return ref < pl.getInventory().getContainerSize() ? pl.getInventory().getItem(ref) : ItemStack.EMPTY;
    }

    private int accent() {
        Artifacts.Piece p = Artifacts.read(stackOf(target));
        if (p != null) {
            ArtifactSets.Set s = ArtifactSets.get(p.set());
            if (s != null) return s.colour();
        }
        return Pathways.GOLD;
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int c = accent();
        targetHits.clear();
        fodderHits.clear();

        g.fill(left - 4, top - 4, left + W + 4, top + H + 4, 0xF00A0A0E);
        g.fillGradient(left - 4, top - 4, left + W + 4, top + 60, Pathways.alpha(Pathways.deep(c, .55f), .22f), 0x00000000);
        g.renderOutline(left - 4, top - 4, W + 8, H + 8, Pathways.alpha(c, .55f));
        g.fill(left - 3, top - 3, left + W + 3, top - 2, Pathways.alpha(Pathways.pale(c, .4f), .8f));
        g.drawString(font, Component.literal("ALTAR").withColor(Pathways.pale(c, .5f) & 0xFFFFFF), left + 6, top + 4, 0xFFFFFF, false);
        g.drawString(font, Component.literal("Burn pieces to raise one. What it does not spend, it keeps.")
                .withStyle(ChatFormatting.DARK_GRAY), left + 6, top + 15, 0xFFFFFF, false);

        drawTarget(g, mx, my, c);
        drawFodder(g, mx, my, c);
        drawResult(g, mx, my, c);

        super.render(g, mx, my, pt);
        for (int[] h : targetHits) if (over(mx, my, h)) { g.renderTooltip(font, stackOf(h[2]), mx, my); return; }
        for (int[] h : fodderHits) if (over(mx, my, h)) { g.renderTooltip(font, stackOf(h[2]), mx, my); return; }
    }

    private static boolean over(int mx, int my, int[] h) {
        return mx >= h[0] && mx <= h[0] + 17 && my >= h[1] && my <= h[1] + 17;
    }

    /** Left: the piece being raised, picked from the five worn or from the bag. */
    private void drawTarget(GuiGraphics g, int mx, int my, int c) {
        int x = left + 6, y = top + 28, w = 150;
        g.fill(x, y, x + w, y + 150, 0xC00B0B11);
        g.renderOutline(x, y, w, 150, Pathways.alpha(c, .35f));
        g.drawString(font, Component.literal("THE PIECE").withColor(Pathways.pale(c, .45f) & 0xFFFFFF), x + 6, y + 5, 0xFFFFFF, false);

        ItemStack st = stackOf(target);
        Artifacts.Piece p = Artifacts.read(st);
        if (p == null) {
            g.drawString(font, Component.literal("Pick one below.").withStyle(ChatFormatting.DARK_GRAY), x + 6, y + 20, 0xFFFFFF, false);
        } else {
            ArtifactSets.Set set = ArtifactSets.get(p.set());
            int sc = set == null ? c : set.colour();
            if (set != null) {
                RenderSystem.enableBlend();
                g.setColor(((sc >> 16 & 255) / 255f), ((sc >> 8 & 255) / 255f), ((sc & 255) / 255f), .2f);
                g.blit(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "textures/holo/" + set.symbol() + ".png"),
                       x + w - 40, y + 16, 0, 0, 36, 36, 36, 36);
                g.setColor(1, 1, 1, 1);
            }
            g.renderItem(st, x + 7, y + 20);
            g.drawString(font, Component.literal(set == null ? "" : set.title()).withColor(sc & 0xFFFFFF), x + 28, y + 19, 0xFFFFFF, false);
            g.drawString(font, Component.literal(p.slot().title + "  +" + p.level() + " / " + p.maxLevel())
                    .withStyle(ChatFormatting.GRAY), x + 28, y + 30, 0xFFFFFF, false);
            g.drawString(font, Component.literal(p.main().line(p.mainValue())).withColor(0xFFF0C0), x + 7, y + 44, 0xFFFFFF, false);
            int ly = y + 56;
            for (Artifacts.Sub s : p.subs()) {
                g.drawString(font, Component.literal("· " + s.stat().line(s.value())).withStyle(ChatFormatting.DARK_GRAY), x + 7, ly, 0xFFFFFF, false);
                ly += 10;
            }
        }

        // every artifact within reach: the five worn first, then the bag
        int bx = x + 7, by = y + 108, n = 0;
        g.drawString(font, Component.literal("worn and carried").withStyle(ChatFormatting.DARK_GRAY), bx, by - 11, 0xFFFFFF, false);
        ItemStack[] w2 = ArtifactSlots.clientWorn();
        for (int i = 0; i < w2.length; i++) {
            if (w2[i] == null || w2[i].isEmpty()) continue;
            n = pip(g, mx, my, bx, by, n, -1 - i, targetHits, true);
        }
        var pl = Minecraft.getInstance().player;
        if (pl != null) {
            var inv = pl.getInventory();
            for (int i = 0; i < inv.getContainerSize() && n < 28; i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty() || !(s.getItem() instanceof ArtifactItem) || Artifacts.read(s) == null) continue;
                if (fodder.contains(i)) continue;
                n = pip(g, mx, my, bx, by, n, i, targetHits, true);
            }
        }
    }

    /** Right: what is going into it. */
    private void drawFodder(GuiGraphics g, int mx, int my, int c) {
        int x = left + 162, y = top + 28, w = 110;
        g.fill(x, y, x + w, y + 150, 0xC00B0B11);
        g.renderOutline(x, y, w, 150, Pathways.alpha(c, .35f));
        g.drawString(font, Component.literal("TO BURN").withColor(Pathways.pale(c, .45f) & 0xFFFFFF), x + 6, y + 5, 0xFFFFFF, false);

        int bx = x + 7, by = y + 20, n = 0;
        var pl = Minecraft.getInstance().player;
        if (pl != null) {
            var inv = pl.getInventory();
            for (int i = 0; i < inv.getContainerSize() && n < 30; i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty() || !(s.getItem() instanceof ArtifactItem) || Artifacts.read(s) == null) continue;
                if (target >= 0 && i == target) continue;
                n = pip(g, mx, my, bx, by, n, i, fodderHits, fodder.contains(i));
            }
        }
        if (n == 0) g.drawString(font, Component.literal("no spare pieces").withStyle(ChatFormatting.DARK_GRAY), bx, by + 3, 0xFFFFFF, false);
        g.drawString(font, Component.literal("click to pick, again to drop").withStyle(ChatFormatting.DARK_GRAY), x + 6, y + 139, 0xFFFFFF, false);
    }

    /** One little framed artifact, lit when it counts. Hands back the next index. */
    private int pip(GuiGraphics g, int mx, int my, int bx, int by, int n, int ref, List<int[]> hits, boolean lit) {
        int px = bx + (n % 7) * 19, py = by + (n / 7) * 19;
        ItemStack st = stackOf(ref);
        Artifacts.Piece p = Artifacts.read(st);
        ArtifactSets.Set set = p == null ? null : ArtifactSets.get(p.set());
        int sc = set == null ? 0x606068 : set.colour();
        boolean hot = mx >= px && mx <= px + 17 && my >= py && my <= py + 17;
        g.fill(px, py, px + 17, py + 17, lit || hot ? Pathways.alpha(sc, hot ? .35f : .2f) : 0xA00C0C12);
        g.renderOutline(px, py, 17, 17, Pathways.alpha(sc, lit ? .95f : (hot ? .7f : .3f)));
        g.renderItem(st, px + 1, py + 1);
        if (ref < 0) g.drawString(font, Component.literal("●").withColor(sc & 0xFFFFFF), px + 12, py - 1, 0xFFFFFF, false);
        hits.add(new int[]{px, py, ref});
        return n + 1;
    }

    /** Below: what it adds up to, and the button. */
    private void drawResult(GuiGraphics g, int mx, int my, int c) {
        int x = left + 278, y = top + 28, w = 72;
        g.fill(x, y, x + w, y + 150, 0xC00B0B11);
        g.renderOutline(x, y, w, 150, Pathways.alpha(c, .35f));

        ItemStack st = stackOf(target);
        Artifacts.Piece p = Artifacts.read(st);
        int exp = p == null ? 0 : ArtifactAltar.banked(st);
        for (int i : fodder) {
            Artifacts.Piece f = Artifacts.read(stackOf(i));
            if (f != null) exp += ArtifactAltar.fodderValue(f) + ArtifactAltar.banked(stackOf(i));
        }
        int gained = 0, spend = exp;
        if (p != null) {
            while (p.level() + gained < p.maxLevel()) {
                int cost = ArtifactAltar.cost(p.rarity(), p.level() + gained);
                if (spend < cost) break;
                spend -= cost;
                gained++;
            }
        }

        g.drawString(font, Component.literal("GAIN").withColor(Pathways.pale(c, .45f) & 0xFFFFFF), x + 6, y + 5, 0xFFFFFF, false);
        String lv = p == null ? "—" : "+" + p.level() + " → +" + (p.level() + gained);
        g.drawString(font, Component.literal(lv).withColor(gained > 0 ? 0xFFD24A : 0xFF707078), x + 6, y + 20, 0xFFFFFF, false);
        g.drawString(font, Component.literal(exp + " exp").withStyle(ChatFormatting.GRAY), x + 6, y + 34, 0xFFFFFF, false);
        if (p != null) {
            int next = p.level() + gained < p.maxLevel() ? ArtifactAltar.cost(p.rarity(), p.level() + gained) : 0;
            if (next > 0) {
                g.drawString(font, Component.literal("next").withStyle(ChatFormatting.DARK_GRAY), x + 6, y + 48, 0xFFFFFF, false);
                int bw = w - 12;
                g.fill(x + 6, y + 59, x + 6 + bw, y + 63, 0xFF18181F);
                g.fill(x + 6, y + 59, x + 6 + (int) (bw * Math.min(1f, spend / (float) next)), y + 63, Pathways.alpha(c, 1f));
                g.drawString(font, Component.literal(spend + "/" + next).withStyle(ChatFormatting.DARK_GRAY), x + 6, y + 66, 0xFFFFFF, false);
            } else {
                g.drawString(font, Component.literal("at the top").withStyle(ChatFormatting.DARK_GRAY), x + 6, y + 48, 0xFFFFFF, false);
            }
            g.drawString(font, Component.literal("to max").withStyle(ChatFormatting.DARK_GRAY), x + 6, y + 82, 0xFFFFFF, false);
            g.drawString(font, Component.literal(String.valueOf(ArtifactAltar.toMax(p))).withStyle(ChatFormatting.GRAY), x + 6, y + 92, 0xFFFFFF, false);
        }

        boolean can = p != null && !fodder.isEmpty();
        boolean hot = mx >= x + 6 && mx <= x + w - 6 && my >= y + 120 && my <= y + 140;
        g.fill(x + 6, y + 120, x + w - 6, y + 140, can ? Pathways.alpha(c, hot ? .55f : .3f) : 0xFF14141A);
        g.renderOutline(x + 6, y + 120, w - 12, 20, Pathways.alpha(can ? c : 0x404048, .8f));
        g.drawCenteredString(font, Component.literal("Burn").withColor(can ? 0xFFFFFF : 0xFF60606A), x + w / 2, y + 126, 0xFFFFFF);
    }

    // ---- clicks ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int[] h : targetHits) {
            if (over((int) mx, (int) my, h)) { target = h[2]; fodder.remove(h[2]); click(); return true; }
        }
        for (int[] h : fodderHits) {
            if (over((int) mx, (int) my, h)) {
                if (!fodder.remove(h[2])) fodder.add(h[2]);
                click();
                return true;
            }
        }
        int x = left + 278, y = top + 28, w = 72;
        if (mx >= x + 6 && mx <= x + w - 6 && my >= y + 120 && my <= y + 140) {
            if (Artifacts.read(stackOf(target)) != null && !fodder.isEmpty()) {
                PacketDistributor.sendToServer(new DoorNet.AltarUpgrade(target, new ArrayList<>(fodder)));
                fodder.clear();
                click();
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.1f, .4f));
    }
}
