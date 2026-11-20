package com.astralspace;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The standings, one ladder per mode.
 *
 * <p>Kept on the world rather than on each player, because a leaderboard has to know about people
 * who are not logged in. Everybody starts at a thousand. A win is worth less than a loss costs, so
 * the pool of points slowly drains and a rating has to be defended rather than banked -- and
 * because of that nobody is ever pushed below a hundred.</p>
 *
 * <p>The exact amounts move with the gap between the two: beating somebody far above you is worth
 * a great deal and beating somebody far below you is worth almost nothing, which is what stops the
 * top of a ladder being farmed off the bottom of it.</p>
 */
public final class Ladder extends SavedData {
    public static final int START = 1000, FLOOR = 100;

    /** What a run of results is worth. The loss side is deliberately the larger of the two. */
    private static final int K_WIN = 24, K_LOSS = 32;

    public record Row(UUID id, String name, int points, int wins, int losses) { }

    private final Map<Arena.Mode, Map<UUID, Row>> books = new HashMap<>();

    public Ladder() { for (Arena.Mode m : Arena.Mode.values()) books.put(m, new HashMap<>()); }

    // ---- reading ----

    public Row row(Arena.Mode m, UUID id, String name) {
        return books.get(m).computeIfAbsent(id, k -> new Row(id, name, START, 0, 0));
    }

    public int points(Arena.Mode m, ServerPlayer p) {
        return row(m, p.getUUID(), p.getGameProfile().getName()).points();
    }

    /** Everybody on one ladder, best first. */
    public List<Row> standings(Arena.Mode m) {
        List<Row> out = new ArrayList<>(books.get(m).values());
        out.sort(Comparator.comparingInt(Row::points).reversed());
        return out;
    }

    /** Where somebody stands, counting from one. Zero when they have never played. */
    public int place(Arena.Mode m, UUID id) {
        List<Row> all = standings(m);
        for (int i = 0; i < all.size(); i++) if (all.get(i).id().equals(id)) return i + 1;
        return 0;
    }

    // ---- writing ----

    /** Settles one result and hands back what each side moved, winner first. */
    public int[] settle(Arena.Mode m, ServerPlayer w, ServerPlayer l) {
        Row rw = row(m, w.getUUID(), w.getGameProfile().getName());
        Row rl = row(m, l.getUUID(), l.getGameProfile().getName());
        double expect = 1.0 / (1.0 + Math.pow(10, (rl.points() - rw.points()) / 400.0));
        int gain = Math.max(1, (int) Math.round(K_WIN * (1 - expect)));
        int drop = Math.max(2, (int) Math.round(K_LOSS * (1 - expect)));
        int nw = rw.points() + gain;
        int nl = Math.max(FLOOR, rl.points() - drop);
        books.get(m).put(w.getUUID(), new Row(w.getUUID(), w.getGameProfile().getName(), nw, rw.wins() + 1, rw.losses()));
        books.get(m).put(l.getUUID(), new Row(l.getUUID(), l.getGameProfile().getName(), nl, rl.wins(), rl.losses() + 1));
        setDirty();
        return new int[]{gain, rl.points() - nl, nw, nl};
    }

    // ---- kept with the world ----

    @Override
    public CompoundTag save(CompoundTag t, net.minecraft.core.HolderLookup.Provider reg) {
        for (Arena.Mode m : Arena.Mode.values()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (Row r : books.get(m).values()) {
                CompoundTag e = new CompoundTag();
                e.putUUID("id", r.id());
                e.putString("n", r.name());
                e.putInt("p", r.points());
                e.putInt("w", r.wins());
                e.putInt("l", r.losses());
                list.add(e);
            }
            t.put(m.id(), list);
        }
        return t;
    }

    public static Ladder load(CompoundTag t, net.minecraft.core.HolderLookup.Provider reg) {
        Ladder d = new Ladder();
        for (Arena.Mode m : Arena.Mode.values()) {
            net.minecraft.nbt.ListTag list = t.getList(m.id(), 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                UUID id = e.getUUID("id");
                d.books.get(m).put(id, new Row(id, e.getString("n"), e.getInt("p"), e.getInt("w"), e.getInt("l")));
            }
        }
        return d;
    }

    private static final SavedData.Factory<Ladder> FACTORY =
            new SavedData.Factory<>(Ladder::new, Ladder::load, null);

    public static Ladder of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "astral_ladder");
    }
}
