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

    /** Server to client: who is behind a mask, in what colour, under what name. Colour -1 means the mask came off. */
    public record Mask(java.util.UUID id, int colour, String name) implements CustomPacketPayload {
        public static final Type<Mask> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "mask"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Mask> CODEC = StreamCodec.of(
            (b, m) -> { b.writeUUID(m.id); b.writeVarInt(m.colour); b.writeUtf(m.name, 32); },
            b -> new Mask(b.readUUID(), b.readVarInt(), b.readUtf(32)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client: they put a mask on, so ask them what to be called. */
    public record MaskAsk(int colour) implements CustomPacketPayload {
        public static final Type<MaskAsk> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "mask_ask"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MaskAsk> CODEC = StreamCodec.of(
            (b, m) -> b.writeVarInt(m.colour),
            b -> new MaskAsk(b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: the name they chose. */
    public record MaskWear(int colour, String name) implements CustomPacketPayload {
        public static final Type<MaskWear> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "mask_wear"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MaskWear> CODEC = StreamCodec.of(
            (b, m) -> { b.writeVarInt(m.colour); b.writeUtf(m.name, 32); },
            b -> new MaskWear(b.readVarInt(), b.readUtf(32)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client: one of the five artifact slots, so the menu can show what is on. */
    public record Artifact(int slot, net.minecraft.world.item.ItemStack stack) implements CustomPacketPayload {
        public static final Type<Artifact> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "artifact"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Artifact> CODEC = StreamCodec.of(
            (b, m) -> { b.writeVarInt(m.slot); net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(b, m.stack); },
            b -> new Artifact(b.readVarInt(), net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(b)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: move a piece between a slot and the bag. -1 for the slot means take it off. */
    public record ArtifactAct(int slot, int invSlot) implements CustomPacketPayload {
        public static final Type<ArtifactAct> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "artifact_act"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ArtifactAct> CODEC = StreamCodec.of(
            (b, m) -> { b.writeVarInt(m.slot); b.writeVarInt(m.invSlot); },
            b -> new ArtifactAct(b.readVarInt(), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client: open the artifact menu. */
    public record ArtifactOpen() implements CustomPacketPayload {
        public static final Type<ArtifactOpen> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "artifact_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ArtifactOpen> CODEC = StreamCodec.of((b, m) -> { }, b -> new ArtifactOpen());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client: open the altar. */
    public record AltarOpen() implements CustomPacketPayload {
        public static final Type<AltarOpen> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "altar_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AltarOpen> CODEC = StreamCodec.of((b, m) -> { }, b -> new AltarOpen());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: raise this piece on what is in this list. */
    public record AltarUpgrade(int target, java.util.List<Integer> fodder) implements CustomPacketPayload {
        public static final Type<AltarUpgrade> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "altar_upgrade"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AltarUpgrade> CODEC = StreamCodec.of(
            (b, m) -> { b.writeVarInt(m.target); b.writeVarInt(Math.min(64, m.fodder.size()));
                        for (int i = 0; i < Math.min(64, m.fodder.size()); i++) b.writeVarInt(m.fodder.get(i)); },
            b -> { int t = b.readVarInt(); int n = Math.min(64, b.readVarInt());
                   java.util.List<Integer> f = new java.util.ArrayList<>();
                   for (int i = 0; i < n; i++) f.add(b.readVarInt());
                   return new AltarUpgrade(t, f); });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client: pick a domain. */
    public record DomainOpen() implements CustomPacketPayload {
        public static final Type<DomainOpen> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "domain_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DomainOpen> CODEC = StreamCodec.of((b, m) -> { }, b -> new DomainOpen());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: this set, this deep. */
    public record DomainEnter(String set, int difficulty) implements CustomPacketPayload {
        public static final Type<DomainEnter> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "domain_enter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DomainEnter> CODEC = StreamCodec.of(
            (b, m) -> { b.writeUtf(m.set, 48); b.writeVarInt(m.difficulty); },
            b -> new DomainEnter(b.readUtf(48), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(PayloadRegistrar r) {
        r.playToClient(DomainOpen.TYPE, DomainOpen.CODEC, (p, ctx) -> ctx.enqueueWork(DomainScreen::open));
        r.playToServer(DomainEnter.TYPE, DomainEnter.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) ArtifactDomain.enter(sp, p.set(), p.difficulty()); }));
        r.playToClient(AltarOpen.TYPE, AltarOpen.CODEC, (p, ctx) -> ctx.enqueueWork(ArtifactAltarScreen::open));
        r.playToServer(AltarUpgrade.TYPE, AltarUpgrade.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) ArtifactAltar.upgrade(sp, p.target(), p.fodder()); }));
        r.playToClient(ArtifactOpen.TYPE, ArtifactOpen.CODEC, (p, ctx) -> ctx.enqueueWork(ArtifactScreen::open));
        r.playToClient(Artifact.TYPE, Artifact.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ArtifactSlots.setClient(p.slot(), p.stack())));
        r.playToServer(ArtifactAct.TYPE, ArtifactAct.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) ArtifactCommands.act(sp, p.slot(), p.invSlot()); }));
        r.playToClient(MaskAsk.TYPE, MaskAsk.CODEC, (p, ctx) -> ctx.enqueueWork(() -> MaskNameScreen.open(p.colour())));
        r.playToServer(MaskWear.TYPE, MaskWear.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) Masks.wear(sp, p.colour(), p.name()); }));
        r.playToClient(World.TYPE, World.CODEC, (p, ctx) -> ctx.enqueueWork(() -> UniqWorld.add(p.kind(), p.id(), p.id2(), p.x(), p.y(), p.z(), p.x2(), p.y2(), p.z2(), p.dur())));
        r.playToClient(Fx.TYPE, Fx.CODEC, (p, ctx) -> ctx.enqueueWork(() -> WornClient.fx(p.entity(), p.kind())));
        r.playToClient(Mask.TYPE, Mask.CODEC, (p, ctx) -> ctx.enqueueWork(() -> MaskClient.set(p.id(), p.colour(), p.name())));
        r.playToClient(Nova.TYPE, Nova.CODEC, (p, ctx) -> ctx.enqueueWork(() -> NovaRender.add(p.x(), p.y(), p.z(), p.scale())));
        r.playToServer(Key.TYPE, Key.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) WornUniq.press(sp); }));
    }
}
