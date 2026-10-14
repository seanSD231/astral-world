package com.astralspace;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(AstralMod.MOD_ID)
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public class AstralMod {
    public static final String MOD_ID = "astral_space";

    // Stage 1: only proves the mod loads. /astralmod replies with the mod version.
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("astralmod").executes(ctx -> {
                ctx.getSource().sendSuccess(() -> Component.literal("Astral Space mod is loaded."), false);
                return Command.SINGLE_SUCCESS;
            })
        );
    }
}
