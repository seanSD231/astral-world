package com.astralspace;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Client only: the level editor of the osu cabinet. A time cursor moves along the level (scroll wheel, arrow keys, or click the timeline at the bottom);
 * left click on the field puts a circle at the cursor and steps it on by half a beat, right click takes the nearest circle away.
 */
public final class OsuEditor extends Screen {
    private final Screen back;
    private final OsuLevel lvl = new OsuLevel();
    private EditBox nameBox, bpmBox;
    private int cur = 40;
    private boolean playing = false;
    private String status = "";

    public OsuEditor(OsuLevel from, Screen back) {
        super(Component.literal("Level editor"));
        this.back = back;
        if (from != null) {
            lvl.name = from.name; lvl.author = from.author; lvl.bpm = from.bpm; lvl.disc = from.disc;
            for (int[] n : from.notes) lvl.notes.add(n.clone());
        }
    }

    private int step() { return Math.max(2, Math.round(600f / lvl.bpm)); }   // half a beat in ticks

    private void readFields() {
        lvl.name = nameBox.getValue().isBlank() ? "Untitled" : nameBox.getValue().trim();
        try { lvl.bpm = Math.max(30, Math.min(300, Integer.parseInt(bpmBox.getValue().trim()))); } catch (NumberFormatException ignored) { }
    }

    @Override
    protected void init() {
        nameBox = new EditBox(font, 8, 8, 130, 16, Component.literal("Name"));
        nameBox.setMaxLength(OsuLevel.MAX_NAME);
        nameBox.setValue(lvl.name);
        addRenderableWidget(nameBox);
        bpmBox = new EditBox(font, 168, 8, 34, 16, Component.literal("BPM"));
        bpmBox.setMaxLength(3);
        bpmBox.setValue(String.valueOf(lvl.bpm));
        addRenderableWidget(bpmBox);
        int x = 212, y = 7, h = 18;
        addRenderableWidget(Button.builder(Component.literal("Test"), b -> { readFields(); if (lvl.notes.isEmpty()) status = "Add some circles first."; else minecraft.setScreen(new OsuPlay(copy(), this)); }).bounds(x, y, 38, h).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            readFields();
            if (lvl.notes.isEmpty()) { status = "Add some circles first."; return; }
            try { lvl.author = minecraft.player != null ? minecraft.player.getGameProfile().getName() : ""; OsuClient.save(lvl); status = "Saved in astral_osu/" + OsuClient.fileName(lvl.name) + ".osu"; }
            catch (Exception e) { status = "Could not save."; }
        }).bounds(x + 42, y, 38, h).build());
        addRenderableWidget(Button.builder(Component.literal("Copy code"), b -> {
            readFields();
            if (lvl.notes.isEmpty()) { status = "Add some circles first."; return; }
            minecraft.keyboardHandler.setClipboard(lvl.encode());
            status = "Code copied. Paste it to anyone.";
        }).bounds(x + 84, y, 58, h).build());
        addRenderableWidget(Button.builder(Component.literal("Publish"), b -> {
            readFields();
            if (lvl.notes.isEmpty()) { status = "Add some circles first."; return; }
            if (minecraft.player != null) lvl.author = minecraft.player.getGameProfile().getName();
            OsuClient.send(new OsuNet.Publish(lvl.encode()));
            status = "Publishing...";
        }).bounds(x + 146, y, 46, h).build());
        addRenderableWidget(Button.builder(Component.literal(lvl.disc.isEmpty() ? "Link disc" : "Disc linked"), b -> {
            linkDisc();
            b.setMessage(Component.literal(lvl.disc.isEmpty() ? "Link disc" : "Disc linked"));
        }).bounds(x + 196, y, 56, h).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> { readFields(); minecraft.setScreen(back); }).bounds(width - 44, y, 38, h).build());
    }

    private OsuLevel copy() {
        OsuLevel c = new OsuLevel();
        c.name = lvl.name; c.author = lvl.author; c.bpm = lvl.bpm; c.disc = lvl.disc;
        for (int[] n : lvl.notes) c.notes.add(n.clone());
        c.sort();
        return c;
    }

    /** Takes the music disc in your hand (vanilla or Etched) as this level's music; with an empty hand it takes the link away. */
    private void linkDisc() {
        var p = minecraft.player;
        if (p == null) return;
        var stack = p.getMainHandItem();
        if (stack.isEmpty()) { lvl.disc = ""; status = "Disc link removed. Hold a music disc to link one."; return; }
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        boolean isDisc = key.getPath().contains("disc") || key.getNamespace().equals("etched") || stack.has(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE);
        if (!isDisc) { status = "That is not a music disc."; return; }
        var enc = net.minecraft.world.item.ItemStack.CODEC.encodeStart(p.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), stack.copyWithCount(1));
        String s = enc.result().map(net.minecraft.nbt.Tag::toString).orElse("");
        if (s.isEmpty() || s.length() > OsuLevel.MAX_DISC) { status = "That disc could not be linked."; return; }
        lvl.disc = s;
        status = "Linked: " + stack.getHoverName().getString() + " (plays from a jukebox next to the cabinet)";
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void tick() { if (playing) cur++; }

    private double px(int[] n) { return width * 0.1 + n[1] / 1000.0 * width * 0.8; }
    private double py(int[] n) { return height * 0.14 + n[2] / 1000.0 * height * 0.74; }
    private boolean inField(double mx, double my) { return mx >= width * 0.05 && mx <= width * 0.95 && my >= height * 0.12 && my <= height - 40; }
    private int span() { return Math.max(1200, lvl.lengthTicks() + 400); }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (my >= height - 34 && my <= height - 12) {   // the timeline
            cur = (int) Math.max(0, Math.min(span(), (mx - 10) / (width - 20) * span()));
            return true;
        }
        if (!inField(mx, my)) return false;
        if (button == 0) {
            if (lvl.notes.size() >= OsuLevel.MAX_NOTES) { status = "That is the most circles a level can have."; return true; }
            int nx = (int) Math.round((mx - width * 0.1) / (width * 0.8) * 1000), ny = (int) Math.round((my - height * 0.14) / (height * 0.74) * 1000);
            lvl.notes.add(new int[]{cur, Math.max(0, Math.min(1000, nx)), Math.max(0, Math.min(1000, ny))});
            lvl.sort();
            cur += step();
        } else if (button == 1) {
            int best = -1;
            double bd = 40;
            for (int i = 0; i < lvl.notes.size(); i++) {
                int[] n = lvl.notes.get(i);
                if (Math.abs(n[0] - cur) > 22) continue;
                double d = Math.hypot(mx - px(n), my - py(n));
                if (d < bd) { bd = d; best = i; }
            }
            if (best >= 0) lvl.notes.remove(best);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        cur = Math.max(0, cur + (int) Math.signum(sy) * step());
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (nameBox.isFocused() || bpmBox.isFocused()) return super.keyPressed(key, scan, mods);
        switch (key) {
            case 262 -> { cur += step(); return true; }                    // right
            case 263 -> { cur = Math.max(0, cur - step()); return true; }  // left
            case 32 -> { playing = !playing; return true; }                // space
            default -> { return super.keyPressed(key, scan, mods); }
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) { g.fill(0, 0, width, height, 0xE0100018); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawString(font, "BPM", 144, 12, 0xA0A0C0, false);
        g.fill((int) (width * 0.05), (int) (height * 0.12), (int) (width * 0.95), height - 40, 0x30FFFFFF);
        for (int i = 0; i < lvl.notes.size(); i++) {
            int[] n = lvl.notes.get(i);
            int dt = n[0] - cur;
            if (Math.abs(dt) > 22) continue;
            int cx = (int) px(n), cy = (int) py(n);
            int a = (int) (255 * (1 - Math.abs(dt) / 24.0));
            int col = dt == 0 ? 0xFFE070 : dt > 0 ? 0xFF80C0 : 0x8080A0;
            for (int yy = -14; yy <= 14; yy++) { int w = (int) Math.sqrt(14.0 * 14 - yy * yy); g.fill(cx - w, cy + yy, cx + w + 1, cy + yy + 1, ((a * 3 / 4) << 24) | col); }
            g.drawCenteredString(font, String.valueOf(i + 1), cx, cy - 4, 0xFFFFFF);
        }
        int y0 = height - 30;
        g.fill(10, y0, width - 10, y0 + 12, 0x60000000);
        for (int[] n : lvl.notes) { int x = 10 + (int) ((double) n[0] / span() * (width - 20)); g.fill(x, y0 + 2, x + 1, y0 + 10, 0xFFFF80C0); }
        int cxm = 10 + (int) ((double) cur / span() * (width - 20));
        g.fill(cxm - 1, y0 - 3, cxm + 1, y0 + 15, 0xFFFFE070);
        g.drawString(font, String.format("%.2fs", cur / 20.0) + "   " + lvl.notes.size() + " circles" + (playing ? "   playing" : ""), 8, height - 12, 0xC0C0D8, false);
        g.drawString(font, "Click: add   Right click: remove   Wheel / arrows: move   Space: play", width / 2 - 10, height - 12, 0x808098, false);
        if (!status.isEmpty()) g.drawString(font, status, 8, 30, 0xFFE070, false);
    }
}
