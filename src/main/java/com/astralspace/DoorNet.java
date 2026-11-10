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

    /** Server to client: a worn Uniqueness did its thing (1 serpent strike, 2 wheel spin). */
    public record Fx(int entity, int kind) implements CustomPacketPayload {
        public static final Type<Fx> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "uniq_fx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Fx> CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT, Fx::entity, net.minecraft.network.codec.ByteBufCodecs.VAR_INT, Fx::kind, Fx::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client: an ability out in the world (1 serpent strike, 2 shadow spikes, 3 walking doll, 4 ball of storms), drawn by {@link UniqWorld}. */
    public record World(int kind, int id, int id2, double x, double y, double z, double x2, double y2, double z2, int dur) implements CustomPacketPayload {
        public static final Type<World> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "uniq_world"));
        public static final StreamCodec<RegistryFriendlyByteBuf, World> CODEC = StreamCodec.of(
            (b, w) -> { b.writeVarInt(w.kind); b.writeVarInt(w.id); b.writeVarInt(w.id2); b.writeDouble(w.x); b.writeDouble(w.y); b.writeDouble(w.z); b.writeDouble(w.x2); b.writeDouble(w.y2); b.writeDouble(w.z2); b.writeVarInt(w.dur); },
            b -> new World(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(PayloadRegistrar r) {
        r.playToClient(World.TYPE, World.CODEC, (p, ctx) -> ctx.enqueueWork(() -> UniqWorld.add(p.kind(), p.id(), p.id2(), p.x(), p.y(), p.z(), p.x2(), p.y2(), p.z2(), p.dur())));
        r.playToClient(Fx.TYPE, Fx.CODEC, (p, ctx) -> ctx.enqueueWork(() -> WornClient.fx(p.entity(), p.kind())));
        r.playToClient(Nova.TYPE, Nova.CODEC, (p, ctx) -> ctx.enqueueWork(() -> NovaRender.add(p.x(), p.y(), p.z(), p.scale())));
        r.playToServer(Key.TYPE, Key.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) WornUniq.press(sp); }));
    }
}
