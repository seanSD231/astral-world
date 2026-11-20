package com.astralspace;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/**
 * What the portal sends the client so its menu has something to draw.
 *
 * <p>One packet with the whole board in it rather than a request for each piece: the menu wants
 * every mode's standings the moment it opens, and three round trips to fill one screen is three
 * chances for it to open half empty.</p>
 */
public final class ArenaNet {
    private ArenaNet() { }

    /** One line of a leaderboard. */
    public record Entry(String name, int points) { }

    /** Everything the menu shows: per mode, your points, your place, how many wait, and the top ten. */
    public record Board(int free, List<Integer> mine, List<List<Entry>> tops) implements CustomPacketPayload {
        public static final Type<Board> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "arena_board"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Board> CODEC = StreamCodec.of(
            (b, m) -> {
                b.writeVarInt(m.free);
                for (int v : m.mine) b.writeVarInt(v);
                for (List<Entry> top : m.tops) {
                    b.writeVarInt(top.size());
                    for (Entry e : top) { b.writeUtf(e.name(), 32); b.writeVarInt(e.points()); }
                }
            },
            b -> {
                int free = b.readVarInt();
                List<Integer> mine = new ArrayList<>();
                for (int i = 0; i < Arena.Mode.values().length * 3; i++) mine.add(b.readVarInt());
                List<List<Entry>> tops = new ArrayList<>();
                for (int i = 0; i < Arena.Mode.values().length; i++) {
                    int n = b.readVarInt();
                    List<Entry> top = new ArrayList<>();
                    for (int k = 0; k < n; k++) top.add(new Entry(b.readUtf(32), b.readVarInt()));
                    tops.add(top);
                }
                return new Board(free, mine, tops);
            });

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public int points(Arena.Mode m) { return mine.get(m.ordinal() * 3); }
        public int place(Arena.Mode m) { return mine.get(m.ordinal() * 3 + 1); }
        public int waiting(Arena.Mode m) { return mine.get(m.ordinal() * 3 + 2); }
        public List<Entry> top(Arena.Mode m) { return tops.get(m.ordinal()); }
    }

    /** Client to server: put me in this queue, on this pathway. */
    public record Join(String mode, String pathway) implements CustomPacketPayload {
        public static final Type<Join> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "arena_join"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Join> CODEC = StreamCodec.of(
            (b, m) -> { b.writeUtf(m.mode, 16); b.writeUtf(m.pathway, 32); },
            b -> new Join(b.readUtf(16), b.readUtf(32)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: take me back out of it. */
    public record Leave() implements CustomPacketPayload {
        public static final Type<Leave> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "arena_leave"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Leave> CODEC =
                StreamCodec.of((b, m) -> { }, b -> new Leave());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(PayloadRegistrar r) {
        r.playToClient(Board.TYPE, Board.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ArenaScreen.open(p)));
        r.playToServer(Join.TYPE, Join.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                Arena.join(sp, Arena.Mode.of(p.mode()), p.pathway());
                sendBoard(sp);
            }
        }));
        r.playToServer(Leave.TYPE, Leave.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) { Arena.leave(sp); sendBoard(sp); }
        }));
    }

    /** Works the board out fresh and sends it. */
    public static void sendBoard(ServerPlayer p) {
        Ladder l = Ladder.of(p.server);
        List<Integer> mine = new ArrayList<>();
        List<List<Entry>> tops = new ArrayList<>();
        for (Arena.Mode m : Arena.Mode.values()) {
            mine.add(l.points(m, p));
            mine.add(l.place(m, p.getUUID()));
            mine.add(Arena.queued(m));
            List<Entry> top = new ArrayList<>();
            for (Ladder.Row r : l.standings(m)) {
                if (top.size() >= 10) break;
                top.add(new Entry(r.name(), r.points()));
            }
            tops.add(top);
        }
        PacketDistributor.sendToPlayer(p, new Board(Arena.freeSlots(), mine, tops));
    }
}
