package com.astralspace;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;

/**
 * The story stage of the world. Stage 0 = nothing yet. Stage 1 starts automatically when the first player ever
 * reaches Seq 0 (see AstralServer). Each stage can have a message that EVERY player sees once: online players
 * right away, offline players when they next log in. Effects of a stage are checked with Stage.atLeast(server, n).
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class Stage {
    public static final Map<Integer, String> MESSAGES = Map.of(
            1, "You feel a strange gaze from above."
    );

    private Stage() {}

    public static int current(MinecraftServer server) { return AstralData.get(server).stage; }

    public static boolean atLeast(MinecraftServer server, int stage) { return current(server) >= stage; }

    /** Moves the world to a stage (up or down). Players see the new stage's message once. */
    public static void set(MinecraftServer server, int stage) {
        AstralData d = AstralData.get(server);
        d.stage = Math.max(0, stage);
        // lowering the stage lets players see the later messages again when the stage comes back
        d.seenStage.replaceAll((k, v) -> Math.min(v, d.stage));
        d.setDirty();
    }

    /** Once a second: show the current stage's message to every online player who has not seen it. */
    public static void notifyPass(MinecraftServer server) {
        AstralData d = AstralData.get(server);
        if (d.stage <= 0) return;
        String message = MESSAGES.get(d.stage);
        if (message == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            if (d.seenStage.getOrDefault(id, 0) >= d.stage) continue;
            d.seenStage.put(id, d.stage);
            d.setDirty();
            show(p, message);
        }
    }

    private static void show(ServerPlayer p, String message) {
        PacketDistributor.sendToPlayer(p, new Net.StagePayload(message));
        p.sendSystemMessage(gradient(message)); // a copy in chat, so it can be re-read
        BuiltInRegistries.SOUND_EVENT.getHolder(ResourceLocation.withDefaultNamespace("entity.warden.heartbeat")).ifPresent(h ->
                p.connection.send(new ClientboundSoundPacket(h, SoundSource.MASTER, p.getX(), p.getY(), p.getZ(), 1.0f, 0.6f, p.getRandom().nextLong())));
    }

    /** Dark red to crimson in chat (pure black would be unreadable on the chat background). */
    private static MutableComponent gradient(String text) {
        MutableComponent out = Component.empty();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            float t = n > 1 ? i / (float) (n - 1) : 0f;
            int r = (int) (60 + (220 - 60) * t), g = (int) (20 * t), b = (int) (10 + 50 * t);
            out.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(Style.EMPTY.withColor((r << 16) | (g << 8) | b)));
        }
        return out;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("astralstage")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("get").executes(ctx -> {
                    int s = current(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("Astral World stage: " + s).withStyle(ChatFormatting.LIGHT_PURPLE), false);
                    return 1;
                }))
                .then(Commands.literal("set").then(Commands.argument("stage", IntegerArgumentType.integer(0)).executes(ctx -> {
                    int s = IntegerArgumentType.getInteger(ctx, "stage");
                    set(ctx.getSource().getServer(), s);
                    ctx.getSource().sendSuccess(() -> Component.literal("Astral World stage set to " + s), true);
                    return 1;
                }))));
    }
}
