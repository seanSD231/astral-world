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
    private final long[] gooFlipAt;   // a Great Old One needs a second click to turn over, after its gold has peeled away
    private final long born = System.currentTimeMillis();
    private double tear = 0;          // 0..1 across the top
    private boolean dragging = false;
    private long tornAt = -1;
    private double lastTearSound = 0;
    private final int[] gooStage;     // which sounds of a Great Old One reveal have played
    // Great Old One reveal, ms after the click: shake, turn gold, peel the gold away to show the pathway coloured back, hold, flip to the card
    private static final int G_GOLD = 650, G_PEEL = 1450, G_PEEL_END = 2250, G_FLIP = 2750, G_DONE = 3200, G_END = 3900;

    private PackScreen(String pack, String cards) {
        super(Component.literal("Tarot Pack"));
        this.pack = pack;
        String[] parts = cards.split(",");
        path = new String[parts.length]; seq = new int[parts.length]; shiny = new boolean[parts.length]; flipAt = new long[parts.length]; gooFlipAt = new long[parts.length]; gooStage = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String[] x = parts[i].split(":");
            path[i] = x[0]; seq[i] = Integer.parseInt(x[1]); shiny[i] = x.length > 2 && x[2].equals("1"); flipAt[i] = -1; gooFlipAt[i] = -1;
        }
        if (pack.equals("view")) {   // looking at a card (right click): no pack, already face up
            tornAt = System.currentTimeMillis() - 10_000;
            for (int i = 0; i < flipAt.length; i++) { flipAt[i] = tornAt; gooFlipAt[i] = tornAt; gooStage[i] = 3; }
        }
        // load every card's art now, while the pack is still closed, so nothing has to load (and stall the screen) when a card is flipped
        var tm = net.minecraft.client.Minecraft.getInstance().getTextureManager();
        for (int i = 0; i < path.length; i++) { if (seq[i] >= 0 || Tarot.hasAnim(path[i], seq[i])) tm.getTexture(artTex(i)); tm.getTexture(tex("back_" + backOf(path[i]))); if (isGoo(i)) tm.getTexture(tex("back_" + path[i])); }
        tm.getTexture(tex("shine_goo"));
        tm.getTexture(tex("shiny_foil"));
        for (String n : new String[]{"shine_common", "shine_rare", "shine_epic", "shine_legendary", "shine_mythic"}) tm.getTexture(tex(n));
        for (int i = 0; i < path.length; i++) tm.getTexture(tex("front_" + RARITY[rarity(seq[i])]));
    }

    public static void open(String pack, String cards) { Minecraft.getInstance().setScreen(new PackScreen(pack, cards)); }

    /** A Great Old One hides behind the back of one of its own pathways until it is clicked. */
    private static String backOf(String p) { int g = Tarot.gooIndex(p); return g >= 0 ? Tarot.GOO_PATHS[g][0] : p; }
    private boolean isGoo(int i) { return path[i].startsWith("goo_"); }
    /** Which ordinary rarity shine its disguise wears (uncommon, rare or epic). */
    private int disguise(int i) { return 1 + Math.floorMod(path[i].hashCode() + i, 3); }
    private boolean turned(int i) { return isGoo(i) ? gooFlipAt[i] >= 0 : flipAt[i] >= 0; }
    private boolean cardDone(int i, long now) { return isGoo(i) ? gooFlipAt[i] >= 0 && now - gooFlipAt[i] >= G_END - G_FLIP : flipAt[i] >= 0 && now - flipAt[i] >= 450; }
    private void give(int i) { if (!pack.equals("view")) net.neoforged.neoforge.network.PacketDistributor.sendToServer(new Net.PackRevealPayload(i)); }
    @Override public void removed() { give(-1); super.removed(); }
    private boolean allDone(long now) { for (int i = 0; i < flipAt.length; i++) if (!cardDone(i, now)) return false; return true; }
    /** No skipping the reveal: after the pack is torn, Esc only closes once every card has been turned over. */
    @Override public boolean shouldCloseOnEsc() { return tornAt < 0 || allDone(System.currentTimeMillis()); }
    private static ResourceLocation tex(String p) { return ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "textures/gui/tarot/" + p + ".png"); }
    private static int rarity(int seq) { return seq >= 8 ? 0 : seq >= 6 ? 1 : seq >= 4 ? 2 : seq >= 2 ? 3 : seq == 1 ? 4 : 5; }
    private static float ease(float x) { x = Math.max(0, Math.min(1, x)); return 1 - (1 - x) * (1 - x) * (1 - x); }

    @Override public boolean isPauseScreen() { return false; }

    /** GuiGraphics.fill and text are batched, and ending a batch switches blending off. Blits are drawn at once, so without turning blending
     *  back on, a card drawn after one of those batches (for example while another card's flip burst is on screen) shows its shine and
     *  sparkle overlays opaque, as a washed out diagonal band and a solid gold frame, for a few frames. */
    private static void blend() { RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); }

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
                    if (isGoo(i)) ui(SoundEvents.AMETHYST_BLOCK_RESONATE, .7f, 1f); else { ui(SoundEvents.BOOK_PAGE_TURN, 1.1f, 1f); give(i); }
                } else if (isGoo(i) && gooFlipAt[i] < 0 && System.currentTimeMillis() - flipAt[i] >= G_PEEL_END) {
                    gooFlipAt[i] = System.currentTimeMillis(); give(i);   // the second click: turn it over, and only now is the card handed over
                }
                return true;
            }
        }
        if (allFlipped() && allDone(System.currentTimeMillis()) && System.currentTimeMillis() - lastFlip() > 700) onClose();
        return true;
    }

    private boolean waitingGoo(long now) { for (int i = 0; i < flipAt.length; i++) if (isGoo(i) && flipAt[i] >= 0 && gooFlipAt[i] < 0 && now - flipAt[i] >= G_PEEL_END) return true; return false; }
    private boolean allFlipped() { for (int i = 0; i < flipAt.length; i++) if (!turned(i)) return false; return true; }
    private long lastFlip() { long m = 0; for (int i = 0; i < flipAt.length; i++) m = Math.max(m, Math.max(flipAt[i], gooFlipAt[i])); return m; }

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
        blend();
        if (rar == 5) {
            int frame = (int) ((System.currentTimeMillis() / MYTHIC_MS) % MYTHIC_FRAMES);
            g.blit(tex("shine_mythic"), x, y, w, h, 0, frame * 360f, 240, 360, 240, 360 * MYTHIC_FRAMES);
        } else {
            g.blit(tex(rar <= 1 ? "shine_common" : rar == 2 ? "shine_rare" : rar == 3 ? "shine_epic" : "shine_legendary"), x, y, w, h, 0, 0, 240, 360, 240, 360);
        }
    }

    /** Twinkling sparkles over a shiny card, on top of its rarity shine: 80 frames in two columns, 75 ms each (a 6 second loop). */
    private void drawFoil(GuiGraphics g, int x, int y, int w, int h) {
        blend();
        int f = (int) ((System.currentTimeMillis() / 75) % 80);
        g.blit(tex("shiny_foil"), x, y, w, h, (f / 40) * 240f, (f % 40) * 360f, 240, 360, 480, 360 * 40);
    }

    /** Designed card art exists for Seq 9 to 0 of every pathway as a still (Seq 0 is 480x720, the rest 240x360). */
    private ResourceLocation artTex(int i) { return tex("card_" + path[i] + "_" + seq[i] + (Tarot.hasAnim(path[i], seq[i]) ? "_anim" : "") + (shiny[i] ? "_shiny" : "")); }

    private boolean drawArt(GuiGraphics g, int i, int x, int y, int w, int h) {
        if (seq[i] >= 0) {
            int tw = seq[i] == 0 ? 480 : 240, th = seq[i] == 0 ? 720 : 360;
            g.blit(tex("card_" + path[i] + "_" + seq[i] + (shiny[i] ? "_shiny" : "")), x, y, w, h, 0, 0, tw, th, tw, th);
            return true;
        }
        if (Tarot.hasAnim(path[i], seq[i])) {
            int f = (int) ((System.currentTimeMillis() / 200) % 50);
            g.blit(artTex(i), x, y, w, h, (f / 10) * 240f, (f % 10) * 360f, 240, 360, 1200, 3600);
            return true;
        }
        return false;
    }

    private void drawFront(GuiGraphics g, int i, int x, int y, int w, int h) {
        if (drawArt(g, i, x, y, w, h)) return;
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
        g.drawCenteredString(font, path[i].startsWith("goo_") ? "Great Old One" : RARITY[rar].substring(0, 1).toUpperCase() + RARITY[rar].substring(1), 0, (int) (148 / 1.3f), path[i].startsWith("goo_") ? 0xFFFFFF : RCOL[rar]);
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
                if (isGoo(i) && flipAt[i] >= 0) {
                    drawGoo(g, i, r, now);
                    g.setColor(1, 1, 1, 1);
                    continue;
                }
                if (isGoo(i)) rar = disguise(i);
                boolean hover = flipAt[i] < 0 && now - tornAt > 900 && mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
                // a burst of the rarity colour behind a freshly flipped rare card
                if (flipAt[i] >= 0 && rar >= 2) {
                    float bp = Math.min(1f, (now - flipAt[i] - 200) / 700f);
                    if (bp > 0 && bp < 1) {
                        int a = (int) ((1 - bp) * (rar >= 4 ? 120 : 80)); int e = (int) (8 + bp * 40);
                        g.fill(r[0] - e, r[1] - e, r[0] + r[2] + e, r[1] + r[3] + e, (a << 24) | RCOL[rar]);
                        g.flush();
                    }
                }
                blend();
                g.setColor(1, 1, 1, r[4] / 255f);
                g.pose().pushPose();
                float cx = r[0] + r[2] / 2f, cy = r[1] + r[3] / 2f;
                g.pose().translate(cx, cy, 0);
                float sx = (float) Math.abs(Math.cos(Math.PI * fp)), lift = hover ? 1.06f : 1f;
                g.pose().scale(Math.max(0.02f, sx) * lift, lift, 1);
                g.pose().translate(-cx, -cy, 0);
                blend();
                if (fp < 0.5f) {
                    g.blit(tex("back_" + backOf(path[i])), r[0], r[1], r[2], r[3], 0, 0, 240, 360, 240, 360);
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
            : now - tornAt < 900 ? "" : allDone(now) ? "Click anywhere to close" : allFlipped() ? "" : waitingGoo(now) ? "Click the Great Old One again to turn it over" : "Click a card to flip it";
        g.drawCenteredString(font, hint, width / 2, height - 22, 0xFFD8D8E8);
    }

    private void goldSweep(GuiGraphics g, int x, int y, int w, int h, int u, int uw, float a, float al) {
        blend();
        g.setColor(1, 1, 1, a * al);
        int f = (int) ((System.currentTimeMillis() / 75) % 40);
        g.blit(tex("shine_goo"), x, y, w, h, u, f * 360f, uw, 360, 240, 360 * 40);
        g.setColor(1, 1, 1, al);
    }

    private void tinted(GuiGraphics g, ResourceLocation t, int x, int y, int w, int h, int u, int uw, float rr, float gg, float bb, float a) {
        blend();
        g.setColor(rr, gg, bb, a);
        g.blit(t, x, y, w, h, u, 0, uw, 360, 240, 360);
    }

    /** A Great Old One that has been clicked: shake, turn gold, the gold peels away from the pathway coloured back, then the card turns over. */
    private void drawGoo(GuiGraphics g, int i, int[] r, long now) {
        // before the second click the reveal waits on the pathway coloured back; the turn-over runs from that click
        long t = gooFlipAt[i] >= 0 ? G_FLIP + (now - gooFlipAt[i]) : Math.min(now - flipAt[i], G_FLIP - 1);
        float al = r[4] / 255f;
        int st = gooStage[i];
        if (st < 1 && t >= G_GOLD) { gooStage[i] = 1; ui(SoundEvents.BEACON_ACTIVATE, 1.7f, .6f); }
        if (st < 2 && t >= G_PEEL) { gooStage[i] = 2; ui(SoundEvents.AMETHYST_CLUSTER_BREAK, .7f, 1f); ui(SoundEvents.BOOK_PAGE_TURN, .6f, 1f); }
        if (st < 3 && t >= G_FLIP) { gooStage[i] = 3; ui(SoundEvents.ENDER_DRAGON_GROWL, 1.9f, .35f); ui(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, .6f); }
        int x = r[0], y = r[1], w = r[2], h = r[3];
        ResourceLocation plain = tex("back_" + backOf(path[i])), goo = tex("back_" + path[i]);
        // a gold glow behind the card that builds up, then bursts when it turns over
        if (t >= 0 && t < G_PEEL) {
            float gp = Math.min(1f, t / (float) G_PEEL);
            int e = (int) (4 + gp * 18), a = (int) (gp * gp * 90);
            g.fill(x - e, y - e, x + w + e, y + h + e, (a << 24) | 0xFFC040); g.flush();
        }
        if (t >= G_FLIP + 250 && t < G_END) {
            float bp = (t - G_FLIP - 250) / (float) (G_END - G_FLIP - 250);
            int e = (int) (8 + bp * 52), a = (int) ((1 - bp) * 140);
            g.fill(x - e, y - e, x + w + e, y + h + e, (a << 24) | 0xFFD060); g.flush();
        }
        blend();
        g.setColor(1, 1, 1, al);
        g.pose().pushPose();
        float cx = x + w / 2f, cy = y + h / 2f;
        g.pose().translate(cx, cy, 0);
        float sx = 1f; boolean front = false;
        if (t < G_FLIP) {
            float amp = Math.min(1f, t / 600f) * (t < G_PEEL ? 1f : Math.max(0f, 1f - (t - G_PEEL) / 300f));
            g.pose().mulPose(Axis.ZP.rotationDegrees((float) Math.sin(t * 0.055) * 4.5f * amp));
            g.pose().translate((float) Math.sin(t * 0.09) * 3f * amp, (float) Math.cos(t * 0.07) * 1.5f * amp, 0);
        } else {
            float fp = Math.min(1f, (t - G_FLIP) / 450f);
            sx = (float) Math.abs(Math.cos(Math.PI * fp)); front = fp >= 0.5f;
        }
        g.pose().scale(Math.max(0.02f, sx), 1f, 1);
        g.pose().translate(-cx, -cy, 0);
        blend();
        if (front) {
            drawFront(g, i, x, y, w, h);
            float fl = (t - G_FLIP - 225) / 700f;
            if (fl > 0 && fl < 1) { g.fill(x, y, x + w, y + h, (((int) ((1 - fl) * 170)) << 24) | 0xFFD878); g.flush(); }
        } else if (t >= G_PEEL) {
            // the pathway coloured back, with the gold card peeling off it in strips
            tinted(g, goo, x, y, w, h, 0, 240, 1, 1, 1, al);
            goldSweep(g, x, y, w, h, 0, 240, 1f, al);
            float p = (t - G_PEEL) / (float) (G_PEEL_END - G_PEEL);
            if (p < 1f) {
                int N = 10;
                for (int k = 0; k < N; k++) {
                    float q = Math.max(0f, Math.min(1f, (p - k * 0.045f) / 0.55f));
                    if (q >= 1f) continue;
                    int x0 = x + k * w / N, x1 = x + (k + 1) * w / N;
                    float mid = (k - (N - 1) / 2f);
                    g.pose().pushPose();
                    float px = (x0 + x1) / 2f, py = y + h / 2f;
                    g.pose().translate(px + mid * q * 9f, py - q * h * 0.45f, 0);
                    g.pose().mulPose(Axis.ZP.rotationDegrees(mid * q * 6f + q * 14f));
                    g.pose().translate(-px, -py, 0);
                    float a = (1f - q * q) * al;
                    tinted(g, plain, x0, y, x1 - x0, h, k * 240 / N, 240 / N, 1f, .78f, .3f, a);
                    goldSweep(g, x0, y, x1 - x0, h, k * 240 / N, 240 / N, 1f - q, al * (1f - q));
                    g.pose().popPose();
                }
            }
        } else {
            // the ordinary back it was hiding behind, turning gold
            blend(); g.setColor(1, 1, 1, al); g.blit(plain, x, y, w, h, 0, 0, 240, 360, 240, 360);
            drawShine(g, disguise(i), x, y, w, h);
            float ga = ease((t - G_GOLD) / 800f);
            if (ga > 0) { tinted(g, plain, x, y, w, h, 0, 240, 1f, .78f, .3f, ga * al); goldSweep(g, x, y, w, h, 0, 240, ga, al); }
        }
        g.pose().popPose();
        g.setColor(1, 1, 1, 1);
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
