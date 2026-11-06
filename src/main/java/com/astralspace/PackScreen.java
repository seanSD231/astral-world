package com.astralspace;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

/**
 * Client only: opening a tarot pack. The pack fills the screen; hold left click and drag across the top to tear it open;
 * three face-down cards rise out (the back shows the pathway and the card's rarity shine); click a card to flip it.
 * The cards are already in the inventory by now, closing the screen early loses nothing.
 */
public final class PackScreen extends Screen {
    private static final String[] RARITY = {"common", "uncommon", "rare", "epic", "legendary", "mythic"};
    private static final int[] RCOL = {0xAAAAB4, 0x6EDC78, 0x5ABEFF, 0xAA5AE6, 0xFFBE3C, 0xFF6EC8};
    private static final int MYTHIC_FRAMES = 40, MYTHIC_MS = 150;

    private final String pack;
    private final String[] path;
    private final int[] seq;
    private final boolean[] shiny;
    private final long[] flipAt;
    private final long born = System.currentTimeMillis();
    private double tear = 0;          // 0..1 across the top
    private boolean dragging = false;
    private long tornAt = -1;
    private double lastTearSound = 0;

    private PackScreen(String pack, String cards) {
        super(Component.literal("Tarot Pack"));
        this.pack = pack;
        String[] parts = cards.split(",");
        path = new String[parts.length]; seq = new int[parts.length]; shiny = new boolean[parts.length]; flipAt = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String[] x = parts[i].split(":");
            path[i] = x[0]; seq[i] = Integer.parseInt(x[1]); shiny[i] = x.length > 2 && x[2].equals("1"); flipAt[i] = -1;
        }
    }

    public static void open(String pack, String cards) { Minecraft.getInstance().setScreen(new PackScreen(pack, cards)); }

    private static ResourceLocation tex(String p) { return ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "textures/gui/tarot/" + p + ".png"); }
    private static int rarity(int seq) { return seq >= 8 ? 0 : seq >= 6 ? 1 : seq >= 4 ? 2 : seq >= 2 ? 3 : seq == 1 ? 4 : 5; }
    private static float ease(float x) { x = Math.max(0, Math.min(1, x)); return 1 - (1 - x) * (1 - x) * (1 - x); }

    @Override public boolean isPauseScreen() { return false; }

    private void ui(net.minecraft.sounds.SoundEvent s, float pitch, float vol) { Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, vol)); }

    // ---- pack geometry
    private int S() { return (int) Math.min(height * 0.82, width * 0.7); }
    private int PX() { return (width - S()) / 2; }
    private int PY() { return (height - S()) / 2 + 6; }
    private double cW() { return S() * 128.0 / 188.0; }
    private double left() { return PX() + (S() - cW()) / 2.0; }
    private double tearY() { return PY() + S() * 0.09; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (tornAt < 0) {
            if (Math.abs(my - tearY()) <= S() * 0.08 && mx >= left() - 8 && mx <= left() + cW() * (tear < 0.02 ? 0.3 : tear + 0.12)) { dragging = true; }
            return true;
        }
        if (System.currentTimeMillis() - tornAt < 900) return true;
        for (int i = 0; i < path.length; i++) {
            int[] r = cardRect(i);
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) {
                if (flipAt[i] < 0) {
                    flipAt[i] = System.currentTimeMillis();
                    ui(SoundEvents.BOOK_PAGE_TURN, 1.1f, 1f);
                }
                return true;
            }
        }
        if (allFlipped() && System.currentTimeMillis() - lastFlip() > 700) onClose();
        return true;
    }

    private boolean allFlipped() { for (long f : flipAt) if (f < 0) return false; return true; }
    private long lastFlip() { long m = 0; for (long f : flipAt) m = Math.max(m, f); return m; }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging || tornAt >= 0) return false;
        if (Math.abs(my - tearY()) <= S() * 0.12) {
            double p = (mx - left()) / cW();
            if (p > tear) tear = Math.min(1, p);
            if (tear - lastTearSound > 0.09) { lastTearSound = tear; ui(SoundEvents.BOOK_PAGE_TURN, 1.6f + (float) tear * .5f, .7f); }
            if (tear >= 0.97) { tear = 1; tornAt = System.currentTimeMillis(); dragging = false; ui(SoundEvents.FIREWORK_ROCKET_BLAST, 1.2f, .8f); ui(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.1f, .7f); }
        }
        return true;
    }

    @Override public boolean mouseReleased(double mx, double my, int button) { dragging = false; return super.mouseReleased(mx, my, button); }

    // ---- cards
    private int[] cardRect(int i) {
        int ch = (int) Math.min(height * 0.62, width / 3.3 * 1.5), cw = ch * 2 / 3;
        int cx = width / 2 + (int) ((i - (path.length - 1) / 2.0) * cw * 1.22), cy = height / 2;
        long t0 = tornAt < 0 ? Long.MAX_VALUE : tornAt + 350 + i * 170L;
        float p = ease((System.currentTimeMillis() - t0) / 520f);
        return new int[]{cx - cw / 2, cy - ch / 2 + (int) ((1 - p) * ch * 0.45), cw, ch, (int) (p * 255)};
    }

    private void drawShine(GuiGraphics g, int rar, int x, int y, int w, int h) {
        if (rar == 5) {
            int frame = (int) ((System.currentTimeMillis() / MYTHIC_MS) % MYTHIC_FRAMES);
            g.blit(tex("shine_mythic"), x, y, w, h, 0, frame * 360f, 240, 360, 240, 360 * MYTHIC_FRAMES);
        } else {
            g.blit(tex(rar <= 1 ? "shine_common" : rar == 2 ? "shine_rare" : rar == 3 ? "shine_epic" : "shine_legendary"), x, y, w, h, 0, 0, 240, 360, 240, 360);
        }
    }

    /** The shiny foil and sparkles, an animated overlay that goes over any rarity. */
    private void drawFoil(GuiGraphics g, int x, int y, int w, int h) {
        int frame = (int) ((System.currentTimeMillis() / 100) % 40);
        g.blit(tex("shiny_foil"), x, y, w, h, 0, frame * 360f, 240, 360, 240, 360 * 40);
    }

    private void drawFront(GuiGraphics g, int i, int x, int y, int w, int h) {
        int rar = rarity(seq[i]);
        g.blit(tex("front_" + RARITY[rar]), x, y, w, h, 0, 0, 240, 360, 240, 360);
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + h / 2f, 0);
        float s = w / 240f; g.pose().scale(s, s, 1);
        g.blit(tex("sym_" + path[i]), -52, -34 - 52, 104, 104, 0, 0, 128, 128, 128, 128);
        g.pose().pushPose(); g.pose().scale(1.8f, 1.8f, 1);
        g.drawCenteredString(font, Tarot.pretty(path[i]), 0, (int) (96 / 1.8f), 0xFFFFFF);
        g.pose().popPose();
        g.pose().pushPose(); g.pose().scale(1.3f, 1.3f, 1);
        g.drawCenteredString(font, "Sequence " + seq[i], 0, (int) (128 / 1.3f), 0xCCCCD8);
        g.drawCenteredString(font, RARITY[rar].substring(0, 1).toUpperCase() + RARITY[rar].substring(1), 0, (int) (148 / 1.3f), RCOL[rar]);
        g.pose().popPose();
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = System.currentTimeMillis();
        g.fill(0, 0, width, height, 0xD0080810);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        if (tornAt < 0 || now - tornAt < 900) renderPack(g, now);
        if (tornAt >= 0) {
            for (int i = 0; i < path.length; i++) {
                int[] r = cardRect(i);
                if (r[4] <= 0) continue;
                float fp = flipAt[i] < 0 ? 0 : Math.min(1f, (now - flipAt[i]) / 450f);
                int rar = rarity(seq[i]);
                boolean hover = flipAt[i] < 0 && now - tornAt > 900 && mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
                // a burst of the rarity colour behind a freshly flipped rare card
                if (flipAt[i] >= 0 && rar >= 2) {
                    float bp = Math.min(1f, (now - flipAt[i] - 200) / 700f);
                    if (bp > 0 && bp < 1) {
                        int a = (int) ((1 - bp) * (rar >= 4 ? 120 : 80)); int e = (int) (8 + bp * 40);
                        g.fill(r[0] - e, r[1] - e, r[0] + r[2] + e, r[1] + r[3] + e, (a << 24) | RCOL[rar]);
                    }
                }
                g.setColor(1, 1, 1, r[4] / 255f);
                g.pose().pushPose();
                float cx = r[0] + r[2] / 2f, cy = r[1] + r[3] / 2f;
                g.pose().translate(cx, cy, 0);
                float sx = (float) Math.abs(Math.cos(Math.PI * fp)), lift = hover ? 1.06f : 1f;
                g.pose().scale(Math.max(0.02f, sx) * lift, lift, 1);
                g.pose().translate(-cx, -cy, 0);
                if (fp < 0.5f) {
                    g.blit(tex("back_" + path[i]), r[0], r[1], r[2], r[3], 0, 0, 240, 360, 240, 360);
                    drawShine(g, rar, r[0], r[1], r[2], r[3]);
                    if (shiny[i]) drawFoil(g, r[0], r[1], r[2], r[3]);
                } else {
                    drawFront(g, i, r[0], r[1], r[2], r[3]);
                    if (shiny[i]) drawFoil(g, r[0], r[1], r[2], r[3]);
                }
                g.pose().popPose();
                g.setColor(1, 1, 1, 1);
            }
        }
        String hint = tornAt < 0 ? "Hold left click and drag across the top to tear it open"
            : now - tornAt < 900 ? "" : allFlipped() ? "Click anywhere to close" : "Click a card to flip it";
        g.drawCenteredString(font, hint, width / 2, height - 22, 0xFFD8D8E8);
    }

    private void renderPack(GuiGraphics g, long now) {
        int S = S(), px = PX(), py = PY();
        ResourceLocation t = tex("pack_" + pack);
        int cutPx = (int) (tearY() - py);                 // pixels of the pack above the tear line
        float cutV = cutPx * 188f / S;
        float age = tornAt < 0 ? 0 : (now - tornAt) / 900f;
        float fade = tornAt < 0 ? 1 : Math.max(0, 1 - Math.max(0, age - 0.15f) / 0.55f);
        g.setColor(1, 1, 1, fade);
        // body, sliding down once torn
        int bodyDy = tornAt < 0 ? 0 : (int) (ease(age) * S * 0.18);
        g.blit(t, px, py + cutPx + bodyDy, S, S - cutPx, 0, cutV, 188, 188 - (int) cutV, 188, 188);
        if (tornAt < 0) {
            int tipX = (int) (left() + cW() * tear);
            int lx = (int) left() - 6;
            // the torn-off left part of the strip lifts and curls a little
            if (tear > 0.02) {
                int uTip = (int) ((tipX - px) * 188f / S);
                g.pose().pushPose();
                g.pose().translate(lx, (int) tearY(), 0); g.pose().mulPose(Axis.ZP.rotationDegrees((float) (-7 * Math.min(1, tear * 2)))); g.pose().translate(-lx, -(int) tearY(), 0);
                g.blit(t, px, py - 2, tipX - px, cutPx, 0, 0, uTip, (int) cutV, 188, 188);
                g.pose().popPose();
                g.blit(t, tipX, py, px + S - tipX, cutPx, uTip, 0, 188 - uTip, (int) cutV, 188, 188);
                // the torn edge
                for (int x = lx + 6; x < tipX; x += 3) g.fill(x, (int) tearY() + ((x / 3) % 2 == 0 ? 0 : 1), x + 3, (int) tearY() + 3 + ((x / 3) % 2 == 0 ? 0 : 1), 0xE0FFFFFF);
            } else g.blit(t, px, py, S, cutPx, 0, 0, 188, (int) cutV, 188, 188);
            // a dashed guide along the top, with a marker where the drag begins
            if (tear < 0.02) for (int x = (int) left(); x < left() + cW(); x += 12) g.fill(x, (int) tearY(), x + 6, (int) tearY() + 2, 0x90FFFFFF);
            g.fill(tipX - 2, (int) tearY() - 6, tipX + 2, (int) tearY() + 8, 0xFFFFE080);
        } else {
            // the strip flies off up and to the right, spinning
            float e = Math.min(1, age * 1.4f);
            g.pose().pushPose();
            float cxs = px + S / 2f, cys = py + cutPx / 2f;
            g.pose().translate(cxs + e * S * 0.55f, cys - e * S * 0.35f + e * e * S * 0.25f, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(e * 35f));
            g.pose().translate(-cxs, -cys, 0);
            g.setColor(1, 1, 1, Math.max(0, 1 - e * 0.9f));
            g.blit(t, px, py, S, cutPx, 0, 0, 188, (int) cutV, 188, 188);
            g.pose().popPose();
        }
        g.setColor(1, 1, 1, 1);
    }
}
