package com.astralspace;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** The levels players have shared on the osu cabinets of this world. */
public final class OsuLibrary extends SavedData {
    public static final int MAX_LEVELS = 300;

    public static final class Entry {
        public final int id;
        public final UUID author;
        public final String code;
        public final OsuLevel level;
        Entry(int id, UUID author, String code, OsuLevel level) { this.id = id; this.author = author; this.code = code; this.level = level; }
    }

    public final Map<Integer, Entry> levels = new LinkedHashMap<>();
    private int nextId = 1;

    public static OsuLibrary get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(OsuLibrary::new, OsuLibrary::load, null), "astral_osu");
    }

    public Entry add(UUID author, OsuLevel level) {
        if (levels.size() >= MAX_LEVELS) { int oldest = levels.keySet().iterator().next(); levels.remove(oldest); }   // the oldest is dropped
        String code = level.encode();
        Entry e = new Entry(nextId++, author, code, level);
        levels.put(e.id, e);
        setDirty();
        return e;
    }

    public boolean remove(int id) {
        boolean r = levels.remove(id) != null;
        if (r) setDirty();
        return r;
    }

    public static OsuLibrary load(CompoundTag tag, HolderLookup.Provider registries) {
        OsuLibrary lib = new OsuLibrary();
        lib.nextId = Math.max(1, tag.getInt("Next"));
        ListTag list = tag.getList("Levels", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            try {
                OsuLevel l = OsuLevel.decode(c.getString("Code"));
                int id = c.getInt("Id");
                lib.levels.put(id, new Entry(id, c.hasUUID("Author") ? c.getUUID("Author") : new UUID(0, 0), c.getString("Code"), l));
            } catch (IllegalArgumentException ignored) { }
        }
        return lib;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Next", nextId);
        ListTag list = new ListTag();
        for (Entry e : levels.values()) {
            CompoundTag c = new CompoundTag();
            c.putInt("Id", e.id);
            c.putUUID("Author", e.author);
            c.putString("Code", e.code);
            list.add(c);
        }
        tag.put("Levels", list);
        return tag;
    }
}
