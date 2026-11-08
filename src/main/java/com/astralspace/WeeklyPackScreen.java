package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** The weekly pack menu: pick which packs you take this week. Left click adds a pack, right click removes one. */
public final class WeeklyPackScreen extends Screen {
    private static final String[] TYPE = {"Normal", "Rainbow", "Shiny"};
    private static final String[] SUFFIX = {"_pack", "_rainbow_pack", "_shiny_pack"};
    private final int total;
    private final int[] cap;
    private final boolean claimed;
    private final int[][] picked = new int[3][Tarot.PATHWAYS.length];
    private int tab = 0;
    private Button claimBtn;
    private static final int COLS = 6;
    private int cell = 52;

    private WeeklyPackScreen(int total, int rainbow, int shiny, boolean claimed) {
        super(Component.literal("Weekly Tarot Packs"));
        this.total = total; this.cap = new int[]{total, rainbow, shiny}; this.claimed = claimed;
    }

    public static void open(int total, int rainbow, int shiny, boolean claimed) { Minecraft.getInstance().setScreen(new WeeklyPackScreen(total, rainbow, shiny, claimed)); }

    private int picks() { int n = 0; for (int[] a : picked) for (int v : a) n += v; return n; }
    private int picksOf(int t) { int n = 0; for (int v : picked[t]) n += v; return n; }
    private int gridLeft() { return width / 2 - COLS * cell / 2; }
    private int gridTop() { return 82; }

    @Override
    protected void init() {
        int cx = width / 2;
        cell = Math.max(30, Math.min(52, (height - gridTop() - 6) / 4));
        for (int t = 0; t < 3; t++) {
            final int tt = t;
            Button b = Button.builder(Component.literal(TYPE[t] + (t == 0 ? "" : " (" + cap[t] + ")")), x -> tab = tt).bounds(cx - 156 + t * 106, 34, 100, 20).build();
            b.active = cap[t] > 0 && !claimed;
            addRenderableWidget(b);
        }
        claimBtn = Button.builder(Component.literal("Take packs"), b -> {
            StringBuilder s = new StringBuilder();
            for (int t = 0; t < 3; t++) for (int i = 0; i < Tarot.PATHWAYS.length; i++) if (picked[t][i] > 0) s.append(Tarot.PATHWAYS[i]).append(':').append(t).append(':').append(picked[t][i]).append(',');
            final String payload = s.toString();
            StringBuilder msg = new StringBuilder();
            for (int t = 0; t < 3; t++) {
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < Tarot.PATHWAYS.length; i++) if (picked[t][i] > 0) line.append(line.length() > 0 ? ", " : "").append(Tarot.pretty(Tarot.PATHWAYS[i])).append(" x").append(picked[t][i]);
                if (line.length() > 0) msg.append(TYPE[t]).append(": ").append(line).append('\n');
            }
            msg.append("You cannot change this until the week resets.");
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
                if (yes) { PacketDistributor.sendToServer(new Net.WeeklyClaimPayload(payload)); mc.setScreen(null); }
                else mc.setScreen(this);
            }, Component.literal("Take these " + total + " weekly pack" + (total == 1 ? "" : "s") + "?"), Component.literal(msg.toString()), Component.literal("Confirm"), Component.literal("Back")));
        }).bounds(cx - 105, 58, 100, 20).build();
        addRenderableWidget(claimBtn);
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(cx + 5, 58, 100, 20).build());
    }

    private int cellAt(double mx, double my) {
        int c = (int) Math.floor((mx - gridLeft()) / cell), r = (int) Math.floor((my - gridTop()) / cell);
        if (mx < gridLeft() || my < gridTop() || c < 0 || c >= COLS || r < 0) return -1;
        int i = r * COLS + c; return i < Tarot.PATHWAYS.length ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int i = cellAt(mx, my);
        if (i < 0 || claimed) return false;
        if (button == 0 && picks() < total && picksOf(tab) < cap[tab]) picked[tab][i]++;
        else if (button == 1 && picked[tab][i] > 0) picked[tab][i]--;
        return true;
    }

    private static ResourceLocation icon(String path, int t) { return ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "textures/item/" + path + SUFFIX[t] + ".png"); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0080810);
        super.render(g, mx, my, pt);
        int cx = width / 2;
        g.drawCenteredString(font, "Weekly Tarot Packs", cx, 6, 0xFFFFD870);
        String info = claimed ? "You already took your packs this week. They reset on the next reset day."
            : "Pick " + total + " pack" + (total == 1 ? "" : "s") + ":  " + picks() + " / " + total + "     (left click adds, right click removes)";
        g.drawCenteredString(font, info, cx, 20, claimed ? 0xFFFF9090 : 0xFFD8D8E8);
        int hover = cellAt(mx, my);
        for (int i = 0; i < Tarot.PATHWAYS.length; i++) {
            int x = gridLeft() + (i % COLS) * cell, y = gridTop() + (i / COLS) * cell; int ic = cell * 40 / 52, ix = (cell - ic) / 2;
            int n = picked[tab][i]; int all = 0; for (int t = 0; t < 3; t++) all += picked[t][i];
            g.fill(x + 1, y + 1, x + cell - 1, y + cell - 1, i == hover ? 0x60FFFFFF : all > 0 ? 0x4090B0FF : 0x30000000);
            g.blit(icon(Tarot.PATHWAYS[i], tab), x + ix, y + 1, ic, ic, 0, 0, 188, 188, 188, 188);
            String nm = Tarot.pretty(Tarot.PATHWAYS[i]); if (font.width(nm) > cell - 2) nm = font.plainSubstrByWidth(nm, cell - 6) + ".";
            g.drawCenteredString(font, nm, x + cell / 2, y + ic + 2, 0xFFE0E0F0);
            if (n > 0) { g.fill(x + cell - 16, y + 2, x + cell - 2, y + 14, 0xE0000000); g.drawCenteredString(font, String.valueOf(n), x + cell - 9, y + 4, 0xFFFFD870); }
            if (all - n > 0) g.drawString(font, "+" + (all - n), x + 4, y + 4, 0xFFA0C0FF);
        }
        if (claimBtn != null) claimBtn.active = !claimed && picks() == total;
    }

    @Override public boolean isPauseScreen() { return false; }
}
