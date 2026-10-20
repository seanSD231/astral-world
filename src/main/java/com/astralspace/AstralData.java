package com.astralspace;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

/** Everything the mod remembers, saved with the world (data/astral_world.dat). */
public class AstralData extends SavedData {
    public record Return(String dim, double x, double y, double z) {}

    public int hp = 0;
    /** The story stage of the world (0 = nothing has happened yet). */
    public int stage = 0;
    /** Real time (epoch millis) when the current stage began. */
    public long stageSince = 0L;
    /** Stage 3 only: damage that has built up towards the next whole point of barrier damage. */
    public double damageAccum = 0.0;
    /** Stage 3's global message, chosen from the barrier's HP when the stage began. */
    public String stage3Text = "";
    /** Stage 5: a player was online while the end was happening (the world resets once everyone is gone). */
    public boolean stage5Seen = false;
    /** After the barrier broke: "" = the plain end, "evac" = RUN countdown, "bosses", "raid", "recover" (see EndGame). */
    public String endPhase = "";
    /** Real time (epoch millis) when the current end phase runs out. */
    public long endUntil = 0L;
    public boolean earthGone = false;
    /** The evacuation has been offered once already (a second phase 5 has no countdown). */
    public boolean evacOffered = false;
    /** The stage to return to after the raid is won. */
    public int resumeStage = 1;
    public final Set<String> bossesDead = new HashSet<>();
    public final Map<UUID, Integer> raidLives = new HashMap<>();
    public String raidBoss = "";
    /** Leaderboard numbers kept while the bosses and the raid last. */
    public static final class Stat { public String name = ""; public double damage, heal; public int deaths; }
    public final Map<UUID, Stat> stats = new HashMap<>();
    /** Attacks each player has already been taught about (comma-separated ids). */
    public final Map<UUID, String> tutorSeen = new HashMap<>();
    /** The newest stage message each player has already seen. */
    public final Map<UUID, Integer> seenStage = new HashMap<>();
    /** Every player ever seen at Seq 0 (kept while offline). */
    public final Map<UUID, String> seq0 = new HashMap<>();
    /** Which pathway (LOTM id) each known Seq 0 has. */
    public final Map<UUID, String> seq0Paths = new HashMap<>();
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
        if (!tag.contains("hpScale")) d.hp *= 10; // barrier HP became 10x bigger: keep a full barrier full
        d.stage = tag.getInt("stage");
        d.stageSince = tag.getLong("stageSince");
        d.damageAccum = tag.getDouble("damageAccum");
        d.stage3Text = tag.getString("stage3Text");
        d.stage5Seen = tag.getBoolean("stage5Seen");
        d.endPhase = tag.getString("endPhase");
        d.endUntil = tag.getLong("endUntil");
        d.earthGone = tag.getBoolean("earthGone");
        d.evacOffered = tag.getBoolean("evacOffered");
        d.resumeStage = tag.contains("resumeStage") ? tag.getInt("resumeStage") : 1;
        d.raidBoss = tag.getString("raidBoss");
        for (String k : tag.getString("bossesDead").split(",")) if (!k.isEmpty()) d.bossesDead.add(k);
        CompoundTag stt = tag.getCompound("stats");
        for (String k : stt.getAllKeys()) {
            CompoundTag e = stt.getCompound(k);
            Stat s = new Stat();
            s.name = e.getString("name"); s.damage = e.getDouble("damage"); s.heal = e.getDouble("heal"); s.deaths = e.getInt("deaths");
            d.stats.put(UUID.fromString(k), s);
        }
        CompoundTag ts = tag.getCompound("tutorSeen");
        for (String k : ts.getAllKeys()) d.tutorSeen.put(UUID.fromString(k), ts.getString(k));
        CompoundTag rl = tag.getCompound("raidLives");
        for (String k : rl.getAllKeys()) d.raidLives.put(UUID.fromString(k), rl.getInt(k));
        CompoundTag st = tag.getCompound("seenStage");
        for (String k : st.getAllKeys()) d.seenStage.put(UUID.fromString(k), st.getInt(k));
        CompoundTag s = tag.getCompound("seq0");
        for (String k : s.getAllKeys()) d.seq0.put(UUID.fromString(k), s.getString(k));
        CompoundTag sp = tag.getCompound("seq0Paths");
        for (String k : sp.getAllKeys()) d.seq0Paths.put(UUID.fromString(k), sp.getString(k));
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
        tag.putInt("hpScale", 10);
        tag.putInt("stage", stage);
        tag.putLong("stageSince", stageSince);
        tag.putDouble("damageAccum", damageAccum);
        tag.putString("stage3Text", stage3Text);
        tag.putBoolean("stage5Seen", stage5Seen);
        tag.putString("endPhase", endPhase);
        tag.putLong("endUntil", endUntil);
        tag.putBoolean("earthGone", earthGone);
        tag.putBoolean("evacOffered", evacOffered);
        tag.putInt("resumeStage", resumeStage);
        tag.putString("raidBoss", raidBoss);
        tag.putString("bossesDead", String.join(",", bossesDead));
        CompoundTag stt = new CompoundTag();
        stats.forEach((k, s) -> {
            CompoundTag e = new CompoundTag();
            e.putString("name", s.name); e.putDouble("damage", s.damage); e.putDouble("heal", s.heal); e.putInt("deaths", s.deaths);
            stt.put(k.toString(), e);
        });
        tag.put("stats", stt);
        CompoundTag ts = new CompoundTag();
        tutorSeen.forEach((k, v) -> ts.putString(k.toString(), v));
        tag.put("tutorSeen", ts);
        CompoundTag rl = new CompoundTag();
        raidLives.forEach((k, v) -> rl.putInt(k.toString(), v));
        tag.put("raidLives", rl);
        CompoundTag st = new CompoundTag();
        seenStage.forEach((k, v) -> st.putInt(k.toString(), v));
        tag.put("seenStage", st);
        CompoundTag s = new CompoundTag();
        seq0.forEach((k, v) -> s.putString(k.toString(), v));
        tag.put("seq0", s);
        CompoundTag sp = new CompoundTag();
        seq0Paths.forEach((k, v) -> sp.putString(k.toString(), v));
        tag.put("seq0Paths", sp);
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
