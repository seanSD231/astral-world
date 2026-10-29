package com.astralspace;

/** Client only, loaded only when called: opens a minigame (kept apart so a dedicated server never loads the screen classes). */
final class OsuHooks {
    private OsuHooks() {}

    static void open(int game, int seed, int count, int ticks) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(switch (game) {
            case 1 -> new MemoryScreen(seed, count, ticks);
            case 2 -> new ThreadScreen(seed, count, ticks);
            default -> new OsuScreen(seed, count, ticks);
        });
    }
}
