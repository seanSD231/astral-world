package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Client only: the menu of the osu cabinet. */
public final class CabinetScreen extends Screen {
    public CabinetScreen() { super(Component.literal("Osu Cabinet")); }

    @Override
    protected void init() {
        int w = 180, x = width / 2 - w / 2, y = height / 2 - 60;
        addRenderableWidget(Button.builder(Component.literal("Quick play"), b -> minecraft.setScreen(new OsuPlay(OsuLevel.random(System.nanoTime(), 20), this))).bounds(x, y, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Shared levels"), b -> OsuClient.send(new OsuNet.ListReq())).bounds(x, y + 24, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("My levels"), b -> minecraft.setScreen(new LevelBrowser(false, null, this))).bounds(x, y + 48, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("New level"), b -> minecraft.setScreen(new OsuEditor(null, this))).bounds(x, y + 72, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Play a pasted code"), b -> {
            try { minecraft.setScreen(new OsuPlay(OsuLevel.decode(minecraft.keyboardHandler.getClipboard()), this)); }
            catch (IllegalArgumentException e) { OsuClient.say("Copy a level code first (it starts with OSU1:)."); }
        }).bounds(x, y + 96, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Colour: " + OsuClient.THEME_NAMES[OsuClient.theme()]), b -> {
            OsuClient.nextTheme();
            b.setMessage(Component.literal("Colour: " + OsuClient.THEME_NAMES[OsuClient.theme()]));
        }).bounds(x, y + 120, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(x, y + 148, w, 20).build());
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) { g.fill(0, 0, width, height, 0xD0100018); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, "OSU CABINET", width / 2, height / 2 - 92, 0xFFD0F0);
        g.drawCenteredString(font, "Make your own levels, share them, play for fun.", width / 2, height / 2 - 80, 0x9090B0);
    }
}
