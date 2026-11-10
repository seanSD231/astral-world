package com.astralspace;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The Door key: the client only says "I pressed it"; the server checks the eyes, the cooldown and the target itself. */
public final class DoorNet {
    private DoorNet() {}

    public record Key() implements CustomPacketPayload {
        public static final Type<Key> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "door_key"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Key> CODEC = StreamCodec.unit(new Key());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client: a supernova went off at this spot (drawn by {@link NovaRender}). */
    public record Nova(double x, double y, double z, float scale) implements CustomPacketPayload {
        public static final Type<Nova> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "door_nova"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Nova> CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.DOUBLE, Nova::x, net.minecraft.network.codec.ByteBufCodecs.DOUBLE, Nova::y,
            net.minecraft.network.codec.ByteBufCodecs.DOUBLE, Nova::z, net.minecraft.network.codec.ByteBufCodecs.FLOAT, Nova::scale, Nova::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(PayloadRegistrar r) {
        r.playToClient(Nova.TYPE, Nova.CODEC, (p, ctx) -> ctx.enqueueWork(() -> NovaRender.add(p.x(), p.y(), p.z(), p.scale())));
        r.playToServer(Key.TYPE, Key.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) DoorEyes.trigger(sp); }));
    }
}
