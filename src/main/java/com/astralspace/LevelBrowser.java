package com.astralspace;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Client only: a list of levels, either the ones shared on this world (rows from the server) or the ones saved on this computer. */
public final class LevelBrowser extends Screen {
    final Screen back;
    private final boolean shared;
    private final List<String[]> rows = new ArrayList<>();   // shared: id, name, author, circles, disc, mine   local: file name
    private int sel = -1, scroll = 0;

    public LevelBrowser(boolean shared, List<String> sharedRows, Screen back) {
        super(Component.literal(shared ? "Shared levels" : "My levels"));
        this.shared = shared;
        this.back = back;
        if (shared) { for (String r : sharedRows) rows.add(r.split("\\|", -1)); }
        else for (String n : OsuClient.localNames()) rows.add(new String[]{n});
    }

    private static final int ROW = 14;
    private int top() { return 40; }
    private int visible() { return Math.max(3, (height - 100) / ROW); }

    @Override
    protected void init() {
        int y = height - 48, x = width / 2 - 150;
        if (shared) {
            addRenderableWidget(Button.builder(Component.literal("Play"), b -> fetch("play")).bounds(x, y, 70, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Edit copy"), b -> fetch("edit")).bounds(x + 75, y, 70, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Copy code"), b -> fetch("copy")).bounds(x + 150, y, 70, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Delete"), b -> {
                if (sel >= 0 && sel < rows.size()) { OsuClient.send(new OsuNet.Delete(Integer.parseInt(rows.get(sel)[0]))); OsuClient.send(new OsuNet.ListReq()); }
            }).bounds(x + 225, y, 70, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Play"), b -> withLocal(l -> minecraft.setScreen(new OsuPlay(l, this)))).bounds(x, y, 56, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Edit"), b -> withLocal(l -> minecraft.setScreen(new OsuEditor(l, this)))).bounds(x + 60, y, 56, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Publish"), b -> withLocal(l -> { OsuClient.send(new OsuNet.Publish(l.encode())); OsuClient.say("Publishing..."); })).bounds(x + 120, y, 56, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Copy code"), b -> withLocal(l -> { minecraft.keyboardHandler.setClipboard(l.encode()); OsuClient.say("Code copied. Paste it to anyone."); })).bounds(x + 180, y, 62, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Delete"), b -> {
                if (sel >= 0 && sel < rows.size()) { OsuClient.delete(rows.get(sel)[0]); rows.remove(sel); sel = -1; }
            }).bounds(x + 246, y, 54, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreen(back)).bounds(width / 2 - 40, height - 24, 80, 20).build());
    }

    private void withLocal(java.util.function.Consumer<OsuLevel> use) {
        if (sel < 0 || sel >= rows.size()) return;
        try { use.accept(OsuClient.load(rows.get(sel)[0])); }
        catch (Exception e) { OsuClient.say("Could not read that level."); }
    }

    private void fetch(String what) {
        if (sel < 0 || sel >= rows.size()) return;
        OsuClient.pending = what;
        OsuClient.send(new OsuNet.Fetch(Integer.parseInt(rows.get(sel)[0])));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int i = (int) ((my - top()) / ROW) + scroll;
        if (my >= top() && i >= 0 && i < rows.size() && i < scroll + visible()) { sel = i; return true; }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, Math.min(Math.max(0, rows.size() - visible()), scroll - (int) Math.signum(sy)));
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) { g.fill(0, 0, width, height, 0xD0100018); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title.getString() + (rows.isEmpty() ? "  (none yet)" : ""), width / 2, 14, 0xFFD0F0);
        g.drawCenteredString(font, shared ? "Levels other players published on this world" : "Saved on this computer: .minecraft/astral_osu", width / 2, 26, 0x8888A8);
        for (int k = 0; k < visible() && scroll + k < rows.size(); k++) {
            int i = scroll + k, y = top() + k * ROW;
            String[] r = rows.get(i);
            if (i == sel) g.fill(width / 2 - 170, y - 1, width / 2 + 170, y + ROW - 2, 0x60FFFFFF);
            String line = shared ? r[1] + "   by " + r[2] + "   (" + r[3] + " circles)" + ("1".equals(r[4]) ? "  ♫" : "") : r[0];
            g.drawString(font, line, width / 2 - 166, y + 2, i == sel ? 0xFFFFFF : 0xC0C0D8, false);
        }
    }
}
