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
        // The gem coins: a built-in pack that is always on and always on top, so Numismatics' own cog, crown and sun
        // art and names lose to ours whatever order the mods load in (relying on load order alone did not work).
        modBus.addListener((net.neoforged.neoforge.event.AddPackFindersEvent e) -> {
            if (e.getPackType() == net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
                e.addPackFinders(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "resourcepacks/gem_coins"), net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                    net.minecraft.network.chat.Component.literal("Astral World gem coins"), net.minecraft.server.packs.repository.PackSource.BUILT_IN, true, net.minecraft.server.packs.repository.Pack.Position.TOP);
        });
        modBus.addListener(CoinStacks::modify);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(() -> { CoinBridge.patchValues(); CoinStacks.patchLotmList(); }));
        AstralFloor.register(modBus);
        Cosmos.register(modBus);
        PartyItems.register(modBus);
        FunItems.register(modBus);
        HoloCardEntity.register(modBus);
        Tarot.register(modBus);
        AstralTab.register(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(DoorEyes::tickBursts);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(UniqAbilities::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ShadowGear::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ShadowGear::drop);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(UniqAbilities::shield);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Tarot::onItemJoin);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(FunItems::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(FunItems::commands);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(FunItems::onLogin);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(TwilightSword::chat);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(TwilightSword::aura);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(PotionLock::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(PotionLock::onLogin);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(PotionLock::onLogout);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(UniqAbilities::onToss);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(UniqAbilities::watchStories);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(UniqAbilities::onLogout);
        AstralBarrier.onBreak(Stage::onBarrierBroken);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            AstralClient.init(modBus);
        }
    }
}
