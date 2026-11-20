package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What you want to be called behind the mask. It opens the moment you put one on, so the name is
 * chosen there and then rather than carried on the item: a mask handed round a party is a different
 * person each time somebody wears it.
 */
public final class MaskNameScreen extends Screen {
    private final int colour;
    private EditBox box;
    private boolean sent, slim;

    private MaskNameScreen(int colour) {
        super(Component.literal("Behind the mask"));
        this.colour = colour;
    }

    public static void open(int colour) {
        Minecraft.getInstance().setScreen(new MaskNameScreen(colour));
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        box = new EditBox(font, cx - 92, cy - 8, 184, 20, Component.literal("Name"));
        box.setMaxLength(24);
        box.setHint(Component.literal("Masked"));
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.literal(shape()), b -> { slim = !slim; rebuild(); })
                .bounds(cx - 92, cy + 18, 184, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Put it on"), b -> send())
                .bounds(cx - 92, cy + 42, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Never mind"), b -> onClose())
                .bounds(cx + 2, cy + 42, 90, 20).build());
    }

    private String shape() { return slim ? "Build: Alex (slim arms)" : "Build: Steve (classic arms)"; }

    /** The shape button has to say what it now says, so the screen is laid out again. */
    private void rebuild() {
        String was = box == null ? "" : box.getValue();
        rebuildWidgets();
        if (box != null) box.setValue(was);
    }

    private void send() {
        if (sent) return;
        sent = true;
        PacketDistributor.sendToServer(new DoorNet.MaskWear(colour, box.getValue(), slim ? 1 : 0));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) { send(); return true; }   // enter, either of them
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        int cx = width / 2, cy = height / 2;
        int c = Masks.TRIM[Math.max(0, Math.min(Masks.TRIM.length - 1, colour))];
        g.fill(cx - 104, cy - 46, cx + 104, cy + 72, 0xD0101016);
        g.renderOutline(cx - 104, cy - 46, 208, 118, (c & 0xFFFFFF) | 0xA0000000);
        g.drawCenteredString(font, "Who are you tonight?", cx, cy - 36, c & 0xFFFFFF);
        g.drawCenteredString(font, "This is the name everyone will see.", cx, cy - 24, 0xFF9A9AA6);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
