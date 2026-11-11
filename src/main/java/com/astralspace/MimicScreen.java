package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client only: the menu of the Marauder (Seq 0) and the Lord of Mysteries. Pick a pathway, then a sequence; the held card wears that card's face and, when placed, shows its scene. */
public final class MimicScreen extends Screen {
    private final boolean lord;
    private String picked;   // the pathway chosen, while choosing its sequence

    private MimicScreen(boolean lord) { super(Component.literal("Wear another card's face")); this.lord = lord; }

    public static void open(boolean lord) { Minecraft.getInstance().setScreen(new MimicScreen(lord)); }

    @Override
    protected void init() {
        clearWidgets();
        int w = 118, h = 20, gap = 4;
        if (picked == null) {
            int cols = 4, n = Tarot.PATHWAYS.length, x0 = width / 2 - (cols * (w + gap) - gap) / 2, y0 = 40;
            for (int i = 0; i < n; i++) {
                final String id = Tarot.PATHWAYS[i];
                addRenderableWidget(Button.builder(Component.literal(Tarot.pretty(id)), b -> { picked = id; init(); }).bounds(x0 + (i % cols) * (w + gap), y0 + (i / cols) * (h + gap), w, h).build());
            }
            int y1 = y0 + ((n + cols - 1) / cols) * (h + gap) + 6;
            if (lord) for (int i = 0; i < Tarot.GOO_IDS.length; i++) {
                if (i == 0) continue;
                final String id = "goo_" + Tarot.GOO_IDS[i];
                int k = i - 1;
                addRenderableWidget(Button.builder(Component.literal(Tarot.GOO_NAMES[i]), b -> choose(id, 0)).bounds(x0 + (k % cols) * (w + gap), y1 + (k / cols) * (h + gap), w, h).build());
            }
            addRenderableWidget(Button.builder(Component.literal("Show my own face"), b -> choose("", 0)).bounds(width / 2 - 70, height - 28, 140, 20).build());
        } else {
            int cols = 5, x0 = width / 2 - (cols * (60 + gap) - gap) / 2, y0 = 70;
            for (int s = 0; s <= 9; s++) {
                final int sq = s;
                addRenderableWidget(Button.builder(Component.literal("Seq " + s), b -> choose(picked, sq)).bounds(x0 + (s % cols) * (60 + gap), y0 + (s / cols) * (h + gap), 60, h).build());
            }
            addRenderableWidget(Button.builder(Component.literal("Back"), b -> { picked = null; init(); }).bounds(width / 2 - 40, y0 + 2 * (h + gap) + 10, 80, 20).build());
        }
    }

    private void choose(String path, int seq) {
        PacketDistributor.sendToServer(new Net.MimicPayload(path, seq));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        g.drawCenteredString(font, picked == null ? "Wear the face of which card?" : "Which sequence of " + Tarot.pretty(picked) + "?", width / 2, 16, 0xFFE9A0);
        if (picked == null) g.drawCenteredString(font, lord ? "Any pathway, or another Great Old One" : "Any pathway, Seq 0 to 9", width / 2, 28, 0xB0B0C0);
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
