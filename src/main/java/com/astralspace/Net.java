package com.astralspace;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Server -> client: what the bottom-right HUD should show. */
public final class Net {
    private Net() {}

    public record HudPayload(boolean show, int hp, int max, String coords) implements CustomPacketPayload {
        public static final Type<HudPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "hud"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HudPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, HudPayload::show,
                ByteBufCodecs.VAR_INT, HudPayload::hp,
                ByteBufCodecs.VAR_INT, HudPayload::max,
                ByteBufCodecs.STRING_UTF8, HudPayload::coords,
                HudPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server -> client: a white screen flash at the given strength (0-255, 128 = half white) that fades out over ms. */
    public record FlashPayload(int alpha, int ms) implements CustomPacketPayload {
        public static final Type<FlashPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "flash"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FlashPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FlashPayload::alpha,
                ByteBufCodecs.VAR_INT, FlashPayload::ms,
                FlashPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server -> client: a story message to show in the animated horror text. */
    public record StagePayload(String text, int from, int to, String sub) implements CustomPacketPayload {
        public static final Type<StagePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "stage"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StagePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, StagePayload::text,
                ByteBufCodecs.INT, StagePayload::from,
                ByteBufCodecs.INT, StagePayload::to,
                ByteBufCodecs.STRING_UTF8, StagePayload::sub,
                StagePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server -> every player: the sky barrier (its health decides how many cracks it has). */
    public record BarrierPayload(boolean show, int hp, int max, int y, int yOverworld) implements CustomPacketPayload {
        public static final Type<BarrierPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "barrier"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BarrierPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, BarrierPayload::show,
                ByteBufCodecs.VAR_INT, BarrierPayload::hp,
                ByteBufCodecs.VAR_INT, BarrierPayload::max,
                ByteBufCodecs.VAR_INT, BarrierPayload::y,
                ByteBufCodecs.VAR_INT, BarrierPayload::yOverworld,
                BarrierPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** What the client currently knows. Plain fields, safe to load on a server. */
    public static final class ClientState {
        public static volatile boolean show;
        public static volatile int hp, max;
        public static volatile String coords = "";
        public static volatile String stageText = "", stageSub = "";
        public static volatile long stageStart = 0L;
        public static volatile int flashAlpha = 0, flashMs = 1;
        public static volatile long flashStart = 0L;
        public static volatile int stageFrom = 0x000000, stageTo = 0xDC143C;
        public static volatile boolean barrierShow;
        public static volatile int barrierHp, barrierMax, barrierY = 1000, barrierYOverworld = 1000;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(HudPayload.TYPE, HudPayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            ClientState.show = payload.show();
            ClientState.hp = payload.hp();
            ClientState.max = payload.max();
            ClientState.coords = payload.coords();
        }));
        registrar.playToClient(BarrierPayload.TYPE, BarrierPayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            ClientState.barrierShow = payload.show();
            ClientState.barrierHp = payload.hp();
            ClientState.barrierMax = payload.max();
            ClientState.barrierY = payload.y();
            ClientState.barrierYOverworld = payload.yOverworld();
        }));
        registrar.playToClient(FlashPayload.TYPE, FlashPayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            ClientState.flashAlpha = payload.alpha();
            ClientState.flashMs = Math.max(50, payload.ms());
            ClientState.flashStart = System.currentTimeMillis();
        }));
        registrar.playToClient(StagePayload.TYPE, StagePayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            ClientState.stageText = payload.text();
            ClientState.stageFrom = payload.from();
            ClientState.stageTo = payload.to();
            ClientState.stageSub = payload.sub();
            ClientState.stageStart = System.currentTimeMillis();
        }));
    }
}
