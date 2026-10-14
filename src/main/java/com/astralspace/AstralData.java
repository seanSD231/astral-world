package com.astralspace;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Everything the mod remembers, saved with the world (data/astral_world.dat). */
public class AstralData extends SavedData {
    public record Return(String dim, double x, double y, double z) {}

    public int hp = 0;
    /** Every player ever seen at Seq 0 (kept while offline). */
    public final Map<UUID, String> seq0 = new HashMap<>();
    /** The last Sequence seen for each player, to notice when someone reaches Seq 0. */
    public final Map<UUID, Integer> lastSeq = new HashMap<>();
    /** Where each player left from when they entered the Astral World. */
    public final Map<UUID, Return> returns = new HashMap<>();

    public static AstralData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AstralData::new, AstralData::load, null), "astral_world");
    }

    public static AstralData load(CompoundTag tag, HolderLookup.Provider registries) {
        AstralData d = new AstralData();
        d.hp = tag.getInt("hp");
        CompoundTag s = tag.getCompound("seq0");
        for (String k : s.getAllKeys()) d.seq0.put(UUID.fromString(k), s.getString(k));
        CompoundTag l = tag.getCompound("lastSeq");
        for (String k : l.getAllKeys()) d.lastSeq.put(UUID.fromString(k), l.getInt(k));
        CompoundTag r = tag.getCompound("returns");
        for (String k : r.getAllKeys()) {
            CompoundTag e = r.getCompound(k);
            d.returns.put(UUID.fromString(k), new Return(e.getString("dim"), e.getDouble("x"), e.getDouble("y"), e.getDouble("z")));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("hp", hp);
        CompoundTag s = new CompoundTag();
        seq0.forEach((k, v) -> s.putString(k.toString(), v));
        tag.put("seq0", s);
        CompoundTag l = new CompoundTag();
        lastSeq.forEach((k, v) -> l.putInt(k.toString(), v));
        tag.put("lastSeq", l);
        CompoundTag r = new CompoundTag();
        returns.forEach((k, v) -> {
            CompoundTag e = new CompoundTag();
            e.putString("dim", v.dim());
            e.putDouble("x", v.x());
            e.putDouble("y", v.y());
            e.putDouble("z", v.z());
            r.put(k.toString(), e);
        });
        tag.put("returns", r);
        return tag;
    }
}
