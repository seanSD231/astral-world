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
    private String origFile = "";   // the file this level was opened from: saving over it needs no question
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
            origFile = OsuClient.fileName(from.name);
            for (int[] n : from.notes) lvl.notes.add(n.clone());
        }
    }

    private int step() { return Math.max(2, Math.round(600f / lvl.bpm)); }   // half a beat in ticks
    private static final int FT = 64;   // top of the playfield
    private Button saveBtn;
    private String confirmFile = "";
    private int[] dragNote, bending;
    private boolean curveMode = false;
    private double bendX, bendY;
    private double dragX, dragY, dragNowX, dragNowY;

    private boolean readFields() {
        String nm = nameBox.getValue().trim();
        try { lvl.bpm = Math.max(30, Math.min(300, Integer.parseInt(bpmBox.getValue().trim()))); } catch (NumberFormatException ignored) { }
        if (nm.isEmpty()) {
            lvl.name = "Untitled";
            status = "Give your level a name first (the box at the top left).";
            setFocused(nameBox);
            return false;
        }
        lvl.name = nm;
        return true;
    }

    @Override
    protected void init() {
        nameBox = new EditBox(font, 76, 6, 150, 16, Component.literal("Name"));
        nameBox.setMaxLength(OsuLevel.MAX_NAME);
        nameBox.setHint(Component.literal("Name your level").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        nameBox.setValue(lvl.name.equals("Untitled") ? "" : lvl.name);
        nameBox.setResponder(v -> { confirmFile = ""; if (saveBtn != null) saveBtn.setMessage(Component.literal("Save")); });
        addRenderableWidget(nameBox);
        bpmBox = new EditBox(font, 262, 6, 34, 16, Component.literal("BPM"));
        bpmBox.setMaxLength(3);
        bpmBox.setValue(String.valueOf(lvl.bpm));
        addRenderableWidget(bpmBox);
        int x = 8, y = 28, h = 18;
        addRenderableWidget(Button.builder(Component.literal("Test"), b -> { if (!readFields()) return; if (lvl.notes.isEmpty()) status = "Add some circles first."; else minecraft.setScreen(new OsuPlay(copy(), this)); }).bounds(x, y, 40, h).build());
        saveBtn = Button.builder(Component.literal("Save"), b -> {
            if (!readFields()) return;
            if (lvl.notes.isEmpty()) { status = "Add some circles first."; return; }
            String f = OsuClient.fileName(lvl.name);
            if (OsuClient.localNames().contains(f) && !f.equals(confirmFile) && !f.equals(origFile)) {
                confirmFile = f;
                saveBtn.setMessage(Component.literal("Replace?"));
                status = "A level called \"" + f + "\" is already saved. Click Replace? to overwrite it, or change the name.";
                return;
            }
            try { lvl.author = minecraft.player != null ? minecraft.player.getGameProfile().getName() : ""; OsuClient.save(lvl); status = "Saved in astral_osu/" + f + ".osu"; confirmFile = ""; saveBtn.setMessage(Component.literal("Save")); }
            catch (Exception e) { status = "Could not save."; }
        }).bounds(x + 44, y, 56, h).build();
        addRenderableWidget(saveBtn);
        addRenderableWidget(Button.builder(Component.literal("Copy code"), b -> {
            if (!readFields()) return;
            if (lvl.notes.isEmpty()) { status = "Add some circles first."; return; }
            minecraft.keyboardHandler.setClipboard(lvl.encode());
            status = "Code copied. Paste it to anyone.";
        }).bounds(x + 104, y, 62, h).build());
        addRenderableWidget(Button.builder(Component.literal("Publish"), b -> {
            if (!readFields()) return;
            if (lvl.notes.isEmpty()) { status = "Add some circles first."; return; }
            if (minecraft.player != null) lvl.author = minecraft.player.getGameProfile().getName();
            OsuClient.send(new OsuNet.Publish(lvl.encode()));
            status = "Publishing (adds a new entry to the shared list; it never replaces another level)...";
        }).bounds(x + 170, y, 50, h).build());
        addRenderableWidget(Button.builder(Component.literal(lvl.disc.isEmpty() ? "Link disc" : "Disc linked"), b -> {
            linkDisc();
            b.setMessage(Component.literal(lvl.disc.isEmpty() ? "Link disc" : "Disc linked"));
        }).bounds(x + 224, y, 64, h).build());
        addRenderableWidget(Button.builder(Component.literal(curveMode ? "Sliders: curved" : "Sliders: straight"), b -> {
            curveMode = !curveMode;
            bending = null;
            b.setMessage(Component.literal(curveMode ? "Sliders: curved" : "Sliders: straight"));
            status = curveMode ? "Curved: drag to make a slider, then click where it should bend." : "Straight sliders.";
        }).bounds(x + 292, y, 92, h).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> { readFields(); minecraft.setScreen(back); }).bounds(width - 50, 6, 42, h).build());
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
    private double py(int[] n) { return FT + 6 + n[2] / 1000.0 * (height - 40 - FT - 12); }
    private double ex(int[] n) { return width * 0.1 + n[3] / 1000.0 * width * 0.8; }
    private double ey(int[] n) { return FT + 6 + n[4] / 1000.0 * (height - 40 - FT - 12); }
    private boolean inField(double mx, double my) { return mx >= width * 0.05 && mx <= width * 0.95 && my >= FT && my <= height - 40; }
    private int[] pos(double mx, double my) {
        int nx = (int) Math.round((mx - width * 0.1) / (width * 0.8) * 1000), ny = (int) Math.round((my - FT - 6) / (height - 40 - FT - 12) * 1000);
        return new int[]{Math.max(0, Math.min(1000, nx)), Math.max(0, Math.min(1000, ny))};
    }
    private int span() { return Math.max(1200, lvl.lengthTicks() + 400); }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (my >= height - 34 && my <= height - 12) {   // the timeline
            cur = (int) Math.max(0, Math.min(span(), (mx - 10) / (width - 20) * span()));
            return true;
        }
        if (!inField(mx, my)) return false;
        if (bending != null) {   // the second click of a curved slider: where it bends
            if (button == 0) {
                int[] ps = pos(mx, my);
                int idx = -1;
                for (int i = 0; i < lvl.notes.size(); i++) if (lvl.notes.get(i) == bending) idx = i;
                if (idx >= 0) {
                    int[] b = bending;
                    lvl.notes.set(idx, new int[]{b[0], b[1], b[2], b[3], b[4], b[5], ps[0], ps[1]});
                    status = "Curved slider added.";
                }
            } else status = "Kept it straight.";
            bending = null;
            return true;
        }
        if (button == 0) {
            if (lvl.notes.size() >= OsuLevel.MAX_NOTES) { status = "That is the most circles a level can have."; return true; }
            int[] ps = pos(mx, my);
            int[] note = new int[]{cur, ps[0], ps[1]};
            lvl.notes.add(note);
            lvl.sort();
            dragNote = note; dragX = mx; dragY = my; dragNowX = mx; dragNowY = my;
            cur += step();
        } else if (button == 1) {
            int best = -1;
            double bd = 40;
            for (int i = 0; i < lvl.notes.size(); i++) {
                int[] n = lvl.notes.get(i);
                if (cur < n[0] - 22 || cur > n[0] + (OsuLevel.isSlider(n) ? n[5] : 0) + 22) continue;
                double d = Math.hypot(mx - px(n), my - py(n));
                if (d < bd) { bd = d; best = i; }
            }
            if (best >= 0) lvl.notes.remove(best);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragNote != null && button == 0) { dragNowX = mx; dragNowY = my; return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragNote != null && button == 0) {
            double d = Math.hypot(mx - dragX, my - dragY);
            if (d > 28) {   // dragged: the circle becomes a slider that ends where the mouse was let go
                int[] ps = pos(mx, my);
                int dur = (int) Math.max(OsuLevel.MIN_SLIDE, Math.min(OsuLevel.MAX_SLIDE, d / 4.0));
                int idx = -1;
                for (int i = 0; i < lvl.notes.size(); i++) if (lvl.notes.get(i) == dragNote) idx = i;
                if (idx >= 0) {
                    int[] sl = new int[]{dragNote[0], dragNote[1], dragNote[2], ps[0], ps[1], dur};
                    lvl.notes.set(idx, sl);
                    cur = sl[0] + dur + step();
                    if (curveMode) { bending = sl; status = "Now click where the slider should bend (right click keeps it straight)."; }
                    else status = "Slider added (drag further for a longer one).";
                }
            }
            dragNote = null;
            return true;
        }
        return super.mouseReleased(mx, my, button);
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
        g.drawString(font, "Level name:", 8, 10, nameBox.getValue().isBlank() ? 0xFFE070 : 0xA0A0C0, false);
        g.drawString(font, "BPM", 236, 10, 0xA0A0C0, false);
        g.fill((int) (width * 0.05), FT, (int) (width * 0.95), height - 40, 0x30FFFFFF);
        for (int i = 0; i < lvl.notes.size(); i++) {
            int[] n = lvl.notes.get(i);
            int dt = n[0] - cur;
            boolean sl = OsuLevel.isSlider(n);
            if (dt > 22 || cur > n[0] + (sl ? n[5] : 0) + 22) continue;
            if (dt < -22 && !(sl && cur <= n[0] + n[5])) continue;
            if (sl) {
                int steps = 40;
                for (int k = 0; k <= steps; k++) { double[] q = OsuLevel.at(n, k / (double) steps); int bx = (int) (width * 0.1 + q[0] / 1000.0 * width * 0.8), by = (int) (FT + 6 + q[1] / 1000.0 * (height - 40 - FT - 12));
                    for (int yy = -9; yy <= 9; yy++) { int w = (int) Math.sqrt(81.0 - yy * yy); g.fill(bx - w, by + yy, bx + w + 1, by + yy + 1, 0x60C060A0); } }
                g.drawCenteredString(font, "o", (int) ex(n), (int) ey(n) - 4, 0xFFFFC0);
            }
            int cx = (int) px(n), cy = (int) py(n);
            int a = (int) (255 * Math.max(0.2, 1 - Math.abs(Math.min(dt, 0) == 0 ? dt : dt) / 24.0));
            int col = dt == 0 ? 0xFFE070 : dt > 0 ? 0xFF80C0 : 0x8080A0;
            for (int yy = -14; yy <= 14; yy++) { int w = (int) Math.sqrt(14.0 * 14 - yy * yy); g.fill(cx - w, cy + yy, cx + w + 1, cy + yy + 1, ((a * 3 / 4) << 24) | col); }
            g.drawCenteredString(font, String.valueOf(i + 1), cx, cy - 4, 0xFFFFFF);
        }
        int y0 = height - 30;
        g.fill(10, y0, width - 10, y0 + 12, 0x60000000);
        for (int[] n : lvl.notes) {
            int x = 10 + (int) ((double) n[0] / span() * (width - 20));
            int x2 = OsuLevel.isSlider(n) ? 10 + (int) ((double) (n[0] + n[5]) / span() * (width - 20)) : x;
            g.fill(x, y0 + 2, x + 1, y0 + 10, 0xFFFF80C0);
            if (x2 > x) g.fill(x, y0 + 5, x2, y0 + 7, 0xFFC060A0);
        }
        if (bending != null) {
            double[] cp = {width * 0.1 + 0, 0};
            int xs = (int) px(bending), ys = (int) py(bending), xe = (int) ex(bending), ye = (int) ey(bending);
            int[] fake = {bending[0], bending[1], bending[2], bending[3], bending[4], bending[5], pos(mx, my)[0], pos(mx, my)[1]};
            for (int k = 0; k <= 30; k++) { double[] q = OsuLevel.at(fake, k / 30.0); int bx = (int) (width * 0.1 + q[0] / 1000.0 * width * 0.8), by = (int) (FT + 6 + q[1] / 1000.0 * (height - 40 - FT - 12)); g.fill(bx - 2, by - 2, bx + 2, by + 2, 0xFFFFE070); }
            g.fill(mx - 3, my - 3, mx + 3, my + 3, 0xFFFF80C0);
        }
        if (dragNote != null) {
            double dd = Math.hypot(dragNowX - dragX, dragNowY - dragY);
            if (dd > 6) { int steps = (int) (dd / 6); for (int k = 0; k <= steps; k++) { double f = k / (double) Math.max(1, steps); g.fill((int) (dragX + (dragNowX - dragX) * f) - 2, (int) (dragY + (dragNowY - dragY) * f) - 2, (int) (dragX + (dragNowX - dragX) * f) + 2, (int) (dragY + (dragNowY - dragY) * f) + 2, 0xFFFFE070); } }
        }
        int cxm = 10 + (int) ((double) cur / span() * (width - 20));
        g.fill(cxm - 1, y0 - 3, cxm + 1, y0 + 15, 0xFFFFE070);
        g.drawString(font, String.format("%.2fs", cur / 20.0) + "   " + lvl.notes.size() + " circles" + (playing ? "   playing" : ""), 8, height - 12, 0xC0C0D8, false);
        g.drawString(font, "Click: circle   Click and drag: slider   Right click: remove   Wheel / arrows: move   Space: play", width / 2 - 70, height - 12, 0x808098, false);
        if (!status.isEmpty()) g.drawString(font, status, 8, 50, 0xFFE070, false);
    }
}
