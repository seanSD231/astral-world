package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** The fun-item menu, laid out like the weekly packs: click one, then take it. One pick per rank period. */
public final class FunItemScreen extends Screen {
    private final List<ItemStack> items = new ArrayList<>();
    private final List<String> ids;
    private final int days, left;
    private int chosen = -1;
    private Button takeBtn;
    private static final int COLS = 5;
    private int cell = 64;

    private FunItemScreen(List<String> ids, int days, int left) {
        super(Component.literal("Fun Item"));
        this.ids = ids; this.days = days; this.left = left;
        for (String id : ids) items.add(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))));
    }

    public static void open(List<String> ids, int days, int left) {
        Minecraft.getInstance().setScreen(new FunItemScreen(ids, days, left));
    }

    private int gridLeft() { return width / 2 - Math.min(COLS, items.size()) * cell / 2; }
    private int gridTop() { return 76; }

    @Override
    protected void init() {
        int cx = width / 2;
        cell = Math.max(44, Math.min(64, (height - gridTop() - 16) / 2));
        takeBtn = Button.builder(Component.literal("Take it"), b -> {
            if (chosen < 0) return;
            final String id = ids.get(chosen);
            String name = items.get(chosen).getHoverName().getString();
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new ConfirmScreen(yes -> {
                if (yes) { PacketDistributor.sendToServer(new Net.FunPickPayload(id)); mc.setScreen(null); }
                else mc.setScreen(this);
            }, Component.literal("Take the " + name + "?"),
               Component.literal("You cannot pick again for " + days + " day" + (days == 1 ? "" : "s") + "."),
               Component.literal("Confirm"), Component.literal("Back")));
        }).bounds(cx - 105, 46, 100, 20).build();
        takeBtn.active = false;
        addRenderableWidget(takeBtn);
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(cx + 5, 46, 100, 20).build());
    }

    private int cellAt(double mx, double my) {
        int c = (int) Math.floor((mx - gridLeft()) / cell), r = (int) Math.floor((my - gridTop()) / cell);
        if (mx < gridLeft() || my < gridTop() || c < 0 || c >= COLS || r < 0) return -1;
        int i = r * COLS + c;
        return i < items.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int i = cellAt(mx, my);
        if (i < 0 || left > 0) return false;
        chosen = chosen == i ? -1 : i;
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xC0080810);
        super.render(g, mx, my, pt);
        int cx = width / 2;
        g.drawCenteredString(font, "Fun Item", cx, 6, 0xFFFFD870);
        String info = left > 0
            ? "You already took one. The next is ready in " + left + " day" + (left == 1 ? "" : "s") + "."
            : "Pick one. Your rank gets another every " + days + " day" + (days == 1 ? "" : "s") + ".";
        g.drawCenteredString(font, info, cx, 20, left > 0 ? 0xFFFF9090 : 0xFFD8D8E8);
        int hover = cellAt(mx, my);
        for (int i = 0; i < items.size(); i++) {
            int x = gridLeft() + (i % COLS) * cell, y = gridTop() + (i / COLS) * cell;
            g.fill(x + 1, y + 1, x + cell - 1, y + cell - 1, i == chosen ? 0x60FFD870 : i == hover ? 0x40FFFFFF : 0x30000000);
            if (i == chosen) {
                g.fill(x + 1, y + 1, x + cell - 1, y + 3, 0xFFFFD870);
                g.fill(x + 1, y + cell - 3, x + cell - 1, y + cell - 1, 0xFFFFD870);
            }
            g.renderItem(items.get(i), x + cell / 2 - 8, y + cell / 2 - 16);
            String nm = items.get(i).getHoverName().getString();
            if (font.width(nm) > cell - 2) nm = font.plainSubstrByWidth(nm, cell - 8) + ".";
            g.drawCenteredString(font, nm, x + cell / 2, y + cell - 14, 0xFFE0E0F0);
        }
        if (hover >= 0) g.renderTooltip(font, items.get(hover), mx, my);
        if (takeBtn != null) takeBtn.active = left <= 0 && chosen >= 0;
    }

    @Override public boolean isPauseScreen() { return false; }
}
