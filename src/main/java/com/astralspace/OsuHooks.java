package com.astralspace;

import net.minecraft.core.BlockPos;

import java.util.List;

/** Client only, loaded only when called: opens screens (kept apart so a dedicated server never loads the screen classes). */
final class OsuHooks {
    private OsuHooks() {}

    static void open(int game, int seed, int count, int ticks) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(switch (game) {
            case 2 -> new ThreadScreen(seed, count, ticks);
            case 3 -> new ParryScreen(seed, ticks);
            case 4 -> new OsuScreen(seed, count, ticks, true);
            default -> new OsuScreen(seed, count, ticks);
        });
    }

    static void openCabinet(BlockPos pos) {
        OsuClient.cabinet = pos;
        net.minecraft.client.Minecraft.getInstance().setScreen(new CabinetScreen());
    }

    static void showList(List<String> rows) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen back = mc.screen instanceof LevelBrowser b ? b.back : mc.screen;
        mc.setScreen(new LevelBrowser(true, rows, back));
    }

    static void gotCode(String code) { OsuClient.codeArrived(code); }
}
