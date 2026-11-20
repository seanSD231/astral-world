package com.astralspace;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Two packets: the board going out, one button press coming back. */
public final class TourneyNet {
    private TourneyNet() { }

    /** Who has the altar screen open, so the board can be pushed as the bracket moves. */
    private static final Set<UUID> WATCHING = new HashSet<>();

    public static boolean watching(ServerPlayer p) { return WATCHING.contains(p.getUUID()); }
    public static void forget(ServerPlayer p) { WATCHING.remove(p.getUUID()); }

    public record Board(String blob) implements CustomPacketPayload {
        public static final Type<Board> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "tourney_board"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Board> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Board::blob, Board::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** {@code cmd} is one of join, leave, open, start, cancel, mode, fee, close. */
    public record Act(String cmd, int arg) implements CustomPacketPayload {
        public static final Type<Act> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "tourney_act"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Act::cmd,
                ByteBufCodecs.VAR_INT, Act::arg,
                Act::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(PayloadRegistrar r) {
        r.playToClient(Board.TYPE, Board.CODEC, (p, ctx) -> ctx.enqueueWork(() -> TourneyScreen.open(p.blob())));
        r.playToServer(Act.TYPE, Act.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            switch (p.cmd()) {
                case "join" -> Tournament.join(sp);
                case "leave" -> Tournament.withdraw(sp);
                case "open" -> Tournament.open(sp);
                case "start" -> Tournament.start(sp);
                case "cancel" -> Tournament.cancel(sp);
                case "mode" -> Tournament.setMode(sp, Arena.Mode.values()[
                        Math.floorMod(p.arg(), Arena.Mode.values().length)]);
                case "fee" -> Tournament.setFee(sp, p.arg());
                case "close" -> { WATCHING.remove(sp.getUUID()); return; }
                default -> { return; }
            }
            if (WATCHING.contains(sp.getUUID())) sendBoard(sp);
        }));
    }

    public static void sendBoard(ServerPlayer p) {
        WATCHING.add(p.getUUID());
        PacketDistributor.sendToPlayer(p, new Board(Tournament.blob(p)));
    }
}
