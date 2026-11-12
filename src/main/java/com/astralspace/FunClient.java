package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Draws a player squashed or stretched while a fun shroom is on them. Only how they are drawn changes:
 * the shrink shroom works through the scale attribute instead, so that one also changes the hitbox.
 * The title mod's "you unlocked a title" chatter is swallowed here too, so faction titles arrive quietly.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID, value = Dist.CLIENT)
public final class FunClient {
    private FunClient() {}

    @SubscribeEvent
    public static void pre(RenderPlayerEvent.Pre e) {
        float[] sh = FunItems.shapeOf(e.getEntity().getId());
        if (sh == null) return;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.scale(sh[0], sh[1], sh[0]);
    }

    @SubscribeEvent
    public static void post(RenderPlayerEvent.Post e) {
        if (FunItems.shapeOf(e.getEntity().getId()) != null) e.getPoseStack().popPose();
    }

    /** Faction and milestone titles announce themselves in chat; this keeps them out of it. */
    @SubscribeEvent
    public static void chat(net.neoforged.neoforge.client.event.ClientChatReceivedEvent e) {
        if (!Settings.hideTitleMessages) return;
        String s = e.getMessage().getString();
        if (s.startsWith("[TITLE]") || s.contains("You unlocked the title") || s.contains("You unlocked a new title")
            || s.contains("Title unlocked") || s.contains("new title:")) e.setCanceled(true);
    }
}
