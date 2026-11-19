package com.astralspace;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Choosing a domain: which set it is cut for, and how hard it is.
 *
 * <p>The sets are laid out as a wall of pathway symbols, each in its own colour, so the choice is
 * made by looking rather than by reading twenty-six names. The one in hand is lit, named, and has
 * its two- and four-piece lines spelled out underneath, because that is the thing actually being
 * chosen -- the set, not the room.</p>
 */
public final class DomainScreen extends Screen {
    private static final int W = 352, H = 230;
    private static final int COLS = 9, CELL = 36;

    private int left, top, picked, difficulty = 2;
    private float t;

    private DomainScreen() { super(Component.literal("Domain")); }

    public static void open() { Minecraft.getInstance().setScreen(new DomainScreen()); }

    @Override
    protected void init() { left = (width - W) / 2; top = (height - H) / 2; }

    @Override
    public boolean isPauseScreen() { return false; }

    private ArtifactSets.Set set() { return ArtifactSets.all().get(picked); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        t += pt / 20f;
        renderBackground(g, mx, my, pt);
        ArtifactSets.Set sel = set();
        int c = sel.colour();

        g.fill(left - 4, top - 4, left + W + 4, top + H + 4, 0xF00A0A0E);
        g.fillGradient(left - 4, top - 4, left + W + 4, top + 56, Pathways.alpha(Pathways.deep(c, .6f), .25f), 0x00000000);
        g.renderOutline(left - 4, top - 4, W + 8, H + 8, Pathways.alpha(c, .55f));
        g.fill(left - 3, top - 3, left + W + 3, top - 2, Pathways.alpha(Pathways.pale(c, .4f), .85f));
        g.drawString(font, Component.literal("DOMAIN").withColor(Pathways.pale(c, .5f) & 0xFFFFFF), left + 6, top + 4, 0xFFFFFF, false);
        g.drawString(font, Component.literal("Three waves. Cut for one set, and it pays that set most of the time.")
                .withStyle(ChatFormatting.DARK_GRAY), left + 6, top + 15, 0xFFFFFF, false);

        // the wall of sets
        int gx = left + 6, gy = top + 28;
        var all = ArtifactSets.all();
        for (int i = 0; i < all.size(); i++) {
            ArtifactSets.Set s = all.get(i);
            int x = gx + (i % COLS) * CELL, y = gy + (i / COLS) * CELL;
            boolean on = i == picked, hot = mx >= x && mx <= x + CELL - 3 && my >= y && my <= y + CELL - 3;
            int sc = s.colour();
            g.fill(x, y, x + CELL - 3, y + CELL - 3, on ? Pathways.alpha(sc, .3f) : (hot ? 0xFF14141C : 0xA00B0B11));
            g.renderOutline(x, y, CELL - 3, CELL - 3, Pathways.alpha(sc, on ? 1f : (hot ? .7f : .28f)));
            RenderSystem.enableBlend();
            float a = on ? .95f : (hot ? .7f : .45f);
            g.setColor(((sc >> 16 & 255) / 255f), ((sc >> 8 & 255) / 255f), ((sc & 255) / 255f), a);
            g.blit(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "textures/holo/" + s.symbol() + ".png"),
                   x + 4, y + 4, 0, 0, 25, 25, 25, 25);
            g.setColor(1, 1, 1, 1);
        }

        // what is picked
        int py = gy + ((all.size() + COLS - 1) / COLS) * CELL + 4;
        g.fill(left + 6, py, left + W - 6, py + 1, Pathways.alpha(c, .3f));
        g.drawString(font, Component.literal(sel.title()).withColor(c & 0xFFFFFF), left + 8, py + 7, 0xFFFFFF, false);
        g.drawString(font, Component.literal(sel.flavour()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                     left + 8, py + 18, 0xFFFFFF, false);
        g.drawString(font, Component.literal("2 pieces  ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(sel.twoLine()).withStyle(ChatFormatting.GRAY)), left + 8, py + 31, 0xFFFFFF, false);
        g.drawString(font, Component.literal("4 pieces  ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(sel.fourLine()).withStyle(ChatFormatting.GRAY)), left + 8, py + 41, 0xFFFFFF, false);

        // how hard
        int dy = py + 56;
        g.drawString(font, Component.literal("DEPTH").withColor(Pathways.pale(c, .45f) & 0xFFFFFF), left + 8, dy, 0xFFFFFF, false);
        for (int i = 1; i <= 5; i++) {
            int x = left + 48 + (i - 1) * 24;
            boolean on = i <= difficulty, hot = mx >= x && mx <= x + 20 && my >= dy - 4 && my <= dy + 12;
            g.fill(x, dy - 4, x + 20, dy + 12, on ? Pathways.alpha(c, .35f) : (hot ? 0xFF15151D : 0xA00B0B11));
            g.renderOutline(x, dy - 4, 20, 16, Pathways.alpha(c, on ? .9f : .3f));
            g.drawCenteredString(font, String.valueOf(i), x + 10, dy, on ? 0xFFFFFFFF : 0xFF60606A);
        }
        String note = switch (difficulty) {
            case 1 -> "shallow · 1 piece, up to 2✦";
            case 2 -> "1 piece, up to 3✦";
            case 3 -> "2 pieces, up to 3✦";
            case 4 -> "2 pieces, up to 4✦";
            default -> "deep · 2 pieces, up to 5✦";
        };
        g.drawString(font, Component.literal(note).withStyle(ChatFormatting.DARK_GRAY), left + 176, dy, 0xFFFFFF, false);

        // in
        int bx = left + W - 92, by = top + H - 24;
        boolean hot = mx >= bx && mx <= bx + 86 && my >= by && my <= by + 20;
        g.fill(bx, by, bx + 86, by + 20, Pathways.alpha(c, hot ? .6f : .32f));
        g.renderOutline(bx, by, 86, 20, Pathways.alpha(c, .9f));
        g.drawCenteredString(font, Component.literal("Enter").withColor(0xFFFFFF), bx + 43, by + 6, 0xFFFFFF);

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        var all = ArtifactSets.all();
        int gx = left + 6, gy = top + 28;
        for (int i = 0; i < all.size(); i++) {
            int x = gx + (i % COLS) * CELL, y = gy + (i / COLS) * CELL;
            if (mx >= x && mx <= x + CELL - 3 && my >= y && my <= y + CELL - 3) { picked = i; click(); return true; }
        }
        int py = gy + ((all.size() + COLS - 1) / COLS) * CELL + 4, dy = py + 56;
        for (int i = 1; i <= 5; i++) {
            int x = left + 48 + (i - 1) * 24;
            if (mx >= x && mx <= x + 20 && my >= dy - 4 && my <= dy + 12) { difficulty = i; click(); return true; }
        }
        int bx = left + W - 92, by = top + H - 24;
        if (mx >= bx && mx <= bx + 86 && my >= by && my <= by + 20) {
            PacketDistributor.sendToServer(new DoorNet.DomainEnter(set().id(), difficulty));
            Minecraft.getInstance().setScreen(null);
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
