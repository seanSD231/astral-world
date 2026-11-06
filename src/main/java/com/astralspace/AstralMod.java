package com.astralspace;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(AstralMod.MOD_ID)
public class AstralMod {
    public static final String MOD_ID = "astral_world";

    /** Vanilla stops max health at 1024. The cap is a field of the attribute, so this lifts it (modded bosses do the same) on both client and server. */
    private static void liftHealthCap() {
        try {
            var attr = net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH.value();
            var f = net.minecraft.world.entity.ai.attributes.RangedAttribute.class.getDeclaredField("maxValue");
            f.setAccessible(true);
            f.setDouble(attr, 1.0E9);
            com.mojang.logging.LogUtils.getLogger().info("[astral] max health cap lifted to {}", attr.sanitizeValue(5.0E8));
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().warn("[astral] could not lift the max health cap, the Mother Tree is limited to 1024 HP: {}", t.toString());
        }
    }

    public AstralMod(IEventBus modBus) {
        liftHealthCap();
        modBus.addListener(Net::register);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(CoinBridge::patchValues));
        AstralFloor.register(modBus);
        Cosmos.register(modBus);
        PartyItems.register(modBus);
        AstralBarrier.onBreak(Stage::onBarrierBroken);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            AstralClient.init(modBus);
        }
    }
}
