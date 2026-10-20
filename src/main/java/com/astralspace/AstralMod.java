package com.astralspace;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(AstralMod.MOD_ID)
public class AstralMod {
    public static final String MOD_ID = "astral_world";

    public AstralMod(IEventBus modBus) {
        modBus.addListener(Net::register);
        AstralFloor.register(modBus);
        Cosmos.register(modBus);
        AstralBarrier.onBreak(Stage::onBarrierBroken);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            AstralClient.init(modBus);
        }
    }
}
