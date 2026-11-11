package com.astralspace;

import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/** The off-hand placeholder that holds the ball of lightning draws nothing in first person (the Photon ball is the visual). */
@EventBusSubscriber(modid = AstralMod.MOD_ID, value = Dist.CLIENT)
public final class OffhandHide {
    private OffhandHide() {}
    @SubscribeEvent
    public static void onHand(RenderHandEvent e) {
        if (e.getHand() == InteractionHand.OFF_HAND && e.getItemStack().is(AstralFloor.LIGHTNING_HOLD.get())) e.setCanceled(true);
    }
}
