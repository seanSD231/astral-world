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

    /** Server -> client: play a cutscene (kind 1 = the Mother Tree's transformation) lasting this many ticks. */
    public record CutscenePayload(int kind, int ticks) implements CustomPacketPayload {
        public static final Type<CutscenePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "cutscene"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CutscenePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CutscenePayload::kind,
                ByteBufCodecs.VAR_INT, CutscenePayload::ticks,
                CutscenePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server -> client: the tree begins this attack animation (the server's pattern number, or 20 for the ultimate). */
    public record TreeAnimPayload(int attack, int ticks, int seed) implements CustomPacketPayload {
        public static final Type<TreeAnimPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "tree_anim"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TreeAnimPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TreeAnimPayload::attack,
                ByteBufCodecs.VAR_INT, TreeAnimPayload::ticks,
                ByteBufCodecs.VAR_INT, TreeAnimPayload::seed,
                TreeAnimPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client -> server: I swung at a root of the tree (no entity there to hit). */
    public record RootHitPayload(int unused) implements CustomPacketPayload {
        public static final Type<RootHitPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "root_hit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RootHitPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RootHitPayload::unused,
                RootHitPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server -> client: open the click game of Desire's call (osu-style): `count` circles over `ticks` ticks, laid out from `seed`. */
    public record OsuStart(int game, int seed, int count, int ticks) implements CustomPacketPayload {
        public static final Type<OsuStart> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "osu_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OsuStart> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OsuStart::game,
                ByteBufCodecs.VAR_INT, OsuStart::seed,
                ByteBufCodecs.VAR_INT, OsuStart::count,
                ByteBufCodecs.VAR_INT, OsuStart::ticks,
                OsuStart::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client -> server: how the click game went. */
    public record OsuResult(int hits, int total) implements CustomPacketPayload {
        public static final Type<OsuResult> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "osu_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OsuResult> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OsuResult::hits,
                ByteBufCodecs.VAR_INT, OsuResult::total,
                OsuResult::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** One danger marker (kind 0 filled ring, 1 ring, 2 beam, 3 tall beacon). Sent in batches. */
    public record TelegraphPayload(int kind, float x, float y, float z, float a, float b, int color, int life) {
        static void write(RegistryFriendlyByteBuf buf, TelegraphPayload t) {
            buf.writeByte(t.kind); buf.writeFloat(t.x); buf.writeFloat(t.y); buf.writeFloat(t.z); buf.writeFloat(t.a); buf.writeFloat(t.b); buf.writeInt(t.color); buf.writeShort(t.life);
        }
        static TelegraphPayload read(RegistryFriendlyByteBuf buf) {
            return new TelegraphPayload(buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readShort());
        }
    }

    /** One attack effect (see AttackFx for the kinds). The client animates it by itself from the moment it arrives. */
    public record FxPayload(int kind, float x, float y, float z, float a, float b, float c, int color, int life) implements CustomPacketPayload {
        public static final Type<FxPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "attack_fx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FxPayload> CODEC = StreamCodec.of(
                (buf, f) -> { buf.writeByte(f.kind); buf.writeFloat(f.x); buf.writeFloat(f.y); buf.writeFloat(f.z); buf.writeFloat(f.a); buf.writeFloat(f.b); buf.writeFloat(f.c); buf.writeInt(f.color); buf.writeShort(f.life); },
                buf -> new FxPayload(buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readShort()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server -> client: every danger marker of one tick in a single packet (the raid can hold ~100 players). Drawn on top of everything. */
    public record TelegraphBatch(java.util.List<TelegraphPayload> items) implements CustomPacketPayload {
        public static final Type<TelegraphBatch> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "telegraph"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TelegraphBatch> CODEC = StreamCodec.of(
                (buf, b) -> { buf.writeVarInt(b.items.size()); for (TelegraphPayload t : b.items) TelegraphPayload.write(buf, t); },
                buf -> { int n = buf.readVarInt(); java.util.List<TelegraphPayload> l = new java.util.ArrayList<>(n); for (int k = 0; k < n; k++) l.add(TelegraphPayload.read(buf)); return new TelegraphBatch(l); });

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server -> client: silence the game completely (stage 5) or give the sound back. */
    public record MutePayload(boolean mute) implements CustomPacketPayload {
        public static final Type<MutePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "mute"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MutePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, MutePayload::mute,
                MutePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server -> every player: the sky barrier (its health decides how many cracks it has). */
    public record BarrierPayload(boolean show, int hp, int max, int y, int yOverworld, String gods) implements CustomPacketPayload {
        public static final Type<BarrierPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "barrier"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BarrierPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, BarrierPayload::show,
                ByteBufCodecs.VAR_INT, BarrierPayload::hp,
                ByteBufCodecs.VAR_INT, BarrierPayload::max,
                ByteBufCodecs.VAR_INT, BarrierPayload::y,
                ByteBufCodecs.VAR_INT, BarrierPayload::yOverworld,
                ByteBufCodecs.STRING_UTF8, BarrierPayload::gods,
                BarrierPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** What the client currently knows. Plain fields, safe to load on a server. */
    /** Server -> client: how big the tree is drawn (it grows with the number of fighters). */
    public record TreeScalePayload(float scale) implements CustomPacketPayload {
        public static final Type<TreeScalePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "tree_scale"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TreeScalePayload> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, TreeScalePayload::scale, TreeScalePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static final class ClientState {
        public static volatile float treeScale = 1f;
        public static volatile long meltStart = 0L;
        public static volatile int meltTicks = 300;
        public static final java.util.List<double[]> telegraphs = new java.util.ArrayList<>();
        public static final java.util.List<double[]> fx = new java.util.ArrayList<>();
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
        /** Pathway ids that have a Seq 0 (a "god"), as ",id,id,". */
        public static volatile String gods = ",";
        /** Cutscene: when it started (ms), how many ticks it lasts, and which one (0 = none). */
        public static volatile long cutsceneStart = 0;
        public static volatile int cutsceneTicks = 0, cutsceneKind = 0;
        public static volatile int animAttack = -1, animTicks = 0, animSeed = 0;   // the tree's current attack animation
        public static volatile long animStart = 0;
        public static volatile boolean treeGone = false;   // the raid ended: forget where the tree stood
        /** Stage 5: all sound is switched off. */
        public static volatile boolean muted = false;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        OsuNet.register(registrar);
        registrar.playToClient(TreeScalePayload.TYPE, TreeScalePayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientState.treeScale = p.scale()));
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
            ClientState.gods = payload.gods();
        }));
        registrar.playToClient(FlashPayload.TYPE, FlashPayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            ClientState.flashAlpha = payload.alpha();
            ClientState.flashMs = Math.max(50, payload.ms());
            ClientState.flashStart = System.currentTimeMillis();
        }));
        registrar.playToClient(CutscenePayload.TYPE, CutscenePayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (payload.kind() == 9) { ClientState.treeGone = true; ClientState.meltStart = 0; return; }
            if (payload.kind() == 24) { ClientState.meltStart = System.currentTimeMillis(); ClientState.meltTicks = payload.ticks(); return; }
            ClientState.cutsceneKind = payload.kind();
            ClientState.cutsceneTicks = payload.ticks();
            ClientState.cutsceneStart = System.currentTimeMillis();
        }));
        registrar.playToClient(TreeAnimPayload.TYPE, TreeAnimPayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            ClientState.animAttack = payload.attack();
            ClientState.animTicks = payload.ticks();
            ClientState.animSeed = payload.seed();
            ClientState.animStart = System.currentTimeMillis();
        }));
        registrar.playToServer(RootHitPayload.TYPE, RootHitPayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) EndGame.rootHit(sp);
        }));
        registrar.playToClient(OsuStart.TYPE, OsuStart.CODEC, (payload, ctx) -> ctx.enqueueWork(() ->
                OsuHooks.open(payload.game(), payload.seed(), payload.count(), payload.ticks())));
        registrar.playToServer(OsuResult.TYPE, OsuResult.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) EndGame.osuResult(sp, payload.hits(), payload.total());
        }));
        registrar.playToClient(TelegraphBatch.TYPE, TelegraphBatch.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            synchronized (ClientState.telegraphs) {
                for (TelegraphPayload t : payload.items()) {
                    if (ClientState.telegraphs.size() >= 2500) break;
                    ClientState.telegraphs.add(new double[]{t.kind(), t.x(), t.y(), t.z(), t.a(), t.b(), t.color(), System.currentTimeMillis() + t.life() * 50L});
                }
            }
        }));
        registrar.playToClient(FxPayload.TYPE, FxPayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            synchronized (ClientState.fx) {
                if (payload.kind() == 9) {   // dispel: take away the pollen clouds near this spot, wherever they have drifted to by now
                    long nowMs = System.currentTimeMillis();
                    ClientState.fx.removeIf(f -> {
                        if ((int) f[0] != 3) return false;
                        double t = (nowMs - f[8]) / 50.0;
                        return Math.hypot(f[1] + f[4] * t - payload.x(), f[3] + f[5] * t - payload.z()) < payload.a() + 8;
                    });
                    return;
                }
                if (payload.kind() == 12) {   // a counter touched a pollen cloud: it takes on the colour of the counter, so everyone can see it was done
                    long nowMs = System.currentTimeMillis();
                    for (double[] f : ClientState.fx) {
                        if ((int) f[0] != 3) continue;
                        double t = (nowMs - f[8]) / 50.0;
                        if (Math.hypot(f[1] + f[4] * t - payload.x(), f[3] + f[5] * t - payload.z()) < 6) f[7] = payload.color();
                    }
                    return;
                }
                if (payload.kind() == 11) {   // a fruit was taken (or rotted into a werewolf): its model and beam go
                    ClientState.fx.removeIf(f -> ((int) f[0] == 10 || (int) f[0] == 17) && Math.hypot(f[1] - payload.x(), f[3] - payload.z()) < 1.0);
                    return;
                }
                if (ClientState.fx.size() < 600)
                    ClientState.fx.add(new double[]{payload.kind(), payload.x(), payload.y(), payload.z(), payload.a(), payload.b(), payload.c(), payload.color(), System.currentTimeMillis(), payload.life()});
            }
        }));
        registrar.playToClient(MutePayload.TYPE, MutePayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> ClientState.muted = payload.mute()));
        registrar.playToClient(StagePayload.TYPE, StagePayload.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            ClientState.stageText = payload.text();
            ClientState.stageFrom = payload.from();
            ClientState.stageTo = payload.to();
            ClientState.stageSub = payload.sub();
            ClientState.stageStart = System.currentTimeMillis();
        }));
    }
}
