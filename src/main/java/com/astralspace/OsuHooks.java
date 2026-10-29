package com.astralspace;

/** Client only, loaded only when called: opens the click game (kept apart so a dedicated server never loads the screen class). */
final class OsuHooks {
    private OsuHooks() {}

    static void open(int seed, int count, int ticks) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new OsuScreen(seed, count, ticks));
    }
}
