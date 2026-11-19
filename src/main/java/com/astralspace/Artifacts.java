package com.astralspace;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/**
 * Artifacts: five pieces worn at once, each with one main stat and up to four sub-stats, levelled
 * up for more of both, and belonging to a set that pays out at two pieces and again at four.
 *
 * <p>This is Astral's own system and stands beside Sol's Relic System rather than inside it. Sol's
 * defines its sets in a config file over items that already exist and has no slots of its own, so
 * there is nowhere in it to put a Flower of Life; the two run together and their stats both land on
 * the player's attributes in the ordinary way.</p>
 *
 * <p>Every stat is an attribute modifier, and the ones that belong to Lord of the Mysteries are
 * looked up by name at runtime. Without that mod installed those stats simply find nothing to
 * modify and go quiet; nothing here needs it to be present.</p>
 */
public final class Artifacts {
    private Artifacts() { }

    // ---- the five pieces ----

    /** Where a piece is worn. The main stat it can roll depends on this, as it does in the games. */
    public enum Slot {
        FLOWER("Flower of Life"), PLUME("Plume of Death"), SANDS("Sands of Hours"),
        GOBLET("Goblet of Night"), CIRCLET("Circlet of Fate");

        public final String title;
        Slot(String t) { this.title = t; }
        public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    // ---- stats ----

    /**
     * A stat an artifact can carry. {@code attr} names the attribute it moves; a {@code lotm:} one
     * is only there when that mod is. {@code pct} stats are a share of the base value, flat ones are
     * added outright. CRIT_RATE and CRIT_DMG have no attribute anywhere, so they are held here and
     * read back out when a blow lands.
     */
    public enum Stat {
        HP_FLAT       ("HP",              "minecraft:generic.max_health",  false, 1,  120,  1.00),
        ATK_FLAT      ("ATK",             "minecraft:generic.attack_damage", false, 1,   8,  1.00),
        DEF_FLAT      ("DEF",             "minecraft:generic.armor",       false, 1,   6,  0.90),
        HP_PCT        ("HP",              "minecraft:generic.max_health",  true,  2,  .07,  1.00),
        ATK_PCT       ("ATK",             "minecraft:generic.attack_damage", true, 2,  .07,  1.00),
        DEF_PCT       ("DEF",             "minecraft:generic.armor",       true,  2,  .09,  0.90),
        CRIT_RATE     ("CRIT Rate",       "",                              true,  2,  .05,  0.60),
        CRIT_DMG      ("CRIT DMG",        "",                              true,  2,  .10,  0.60),
        SPEED_PCT     ("Move Speed",      "minecraft:generic.movement_speed", true, 2, .04, 0.50),
        SPIRIT_MAX    ("Max Spirituality", "lotm:max_spirituality",        false, 0,  90,  0.80),
        SPIRIT_REGEN  ("Spirituality Regen", "lotm:spirituality_regen",    true,  2,  .09, 0.70),
        SPIRIT_COST   ("Spirituality Cost", "lotm:spirituality_cost",      true,  2, -.05, 0.45),
        ABILITY_POWER ("Ability Power",   "lotm:ability_power",            true,  2,  .07, 0.75),
        ABILITY_AREA  ("Ability Area",    "lotm:ability_area",             true,  2,  .08, 0.45),
        ABILITY_DUR   ("Ability Duration", "lotm:ability_duration",        true,  2,  .08, 0.45),
        ABILITY_RESIST("Ability Resist",  "lotm:ability_resistance",       true,  2,  .06, 0.55),
        COOLDOWN      ("Cooldown Reduction", "lotm:cooldown_reduction",    true,  2,  .05, 0.50);

        public final String title;
        /** The attribute this moves, empty when the stat is settled here instead. */
        public final String attr;
        public final boolean pct;
        /** Decimal places shown. */
        public final int dp;
        /** What one roll of this is worth at five stars, before the rarity and level scaling. */
        public final double roll;
        /** How often it turns up as a sub-stat, relative to the rest. */
        public final double weight;

        Stat(String title, String attr, boolean pct, int dp, double roll, double weight) {
            this.title = title; this.attr = attr; this.pct = pct;
            this.dp = dp; this.roll = roll; this.weight = weight;
        }

        public boolean lotm() { return attr.startsWith("lotm:"); }

        /** Reads nicely: "ATK +7.0%" or "Max Spirituality +90". */
        public String line(double v) {
            String n = pct ? String.format("%." + dp + "f%%", v * 100) : String.format("%." + dp + "f", v);
            return title + (v < 0 ? " " : " +") + n;
        }

        private Holder<Attribute> holder;
        private boolean looked;

        /** The attribute itself, or nothing when the mod that owns it is not here. */
        public Holder<Attribute> attribute() {
            if (!looked) {
                looked = true;
                if (!attr.isEmpty()) {
                    ResourceLocation rl = ResourceLocation.parse(attr);
                    Optional<Holder.Reference<Attribute>> h =
                            BuiltInRegistries.ATTRIBUTE.getHolder(ResourceKey.create(Registries.ATTRIBUTE, rl));
                    holder = h.orElse(null);
                }
            }
            return holder;
        }

        public static Stat of(String name) {
            for (Stat s : values()) if (s.name().equals(name)) return s;
            return HP_FLAT;
        }
    }

    /** The main stat each slot may roll. A Flower is always flat HP and a Plume always flat ATK. */
    public static Stat[] mains(Slot slot) {
        return switch (slot) {
            case FLOWER -> new Stat[]{Stat.HP_FLAT};
            case PLUME -> new Stat[]{Stat.ATK_FLAT};
            case SANDS -> new Stat[]{Stat.HP_PCT, Stat.ATK_PCT, Stat.DEF_PCT, Stat.SPIRIT_REGEN, Stat.COOLDOWN};
            case GOBLET -> new Stat[]{Stat.HP_PCT, Stat.ATK_PCT, Stat.DEF_PCT, Stat.ABILITY_POWER, Stat.ABILITY_RESIST};
            case CIRCLET -> new Stat[]{Stat.HP_PCT, Stat.ATK_PCT, Stat.DEF_PCT, Stat.CRIT_RATE, Stat.CRIT_DMG, Stat.ABILITY_AREA};
        };
    }

    // ---- what a piece is worth ----

    /** Levels run to four a star, so a five star piece tops out at twenty. */
    public static int maxLevel(int rarity) { return Math.max(0, rarity) * 4; }

    /** Rarity one to five; a one star piece is worth about a fifth of a five star one. */
    private static double rarityScale(int rarity) { return .22 + .195 * (Math.max(1, Math.min(5, rarity)) - 1); }

    /** The main stat triples over a full climb, and a lesser piece starts and ends lower. */
    public static double mainValue(Stat s, int rarity, int level) {
        int max = Math.max(1, maxLevel(rarity));
        double grow = 1.0 + 2.0 * (level / (double) max);
        return round(s.roll * 1.55 * rarityScale(rarity) * grow, s);
    }

    /** One roll of a sub-stat, before however many times it has since been rolled again. */
    public static double subRoll(Stat s, int rarity, net.minecraft.util.RandomSource r) {
        double band = .78 + r.nextDouble() * .44;                 // the roll itself, high or low
        return round(s.roll * .62 * rarityScale(rarity) * band, s);
    }

    private static double round(double v, Stat s) {
        return s.pct ? Math.round(v * 10000) / 10000.0 : Math.round(v * 10) / 10.0;
    }

    // ---- a piece, as it is written on the item ----

    public record Sub(Stat stat, double value, int rolls) { }

    /** Everything an artifact is. Read off an item with {@link #read}, written back with {@link #write}. */
    public record Piece(Slot slot, String set, int rarity, int level, Stat main, double mainV, List<Sub> subs) {
        public double mainValue() { return mainV; }
        public int maxLevel() { return Artifacts.maxLevel(rarity); }
    }

    private static final String TAG = "astral_artifact";

    public static boolean is(ItemStack st) {
        return st.getItem() instanceof ArtifactItem && read(st) != null;
    }

    public static Piece read(ItemStack st) {
        CustomData cd = st.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;
        CompoundTag root = cd.copyTag();
        if (!root.contains(TAG)) return null;
        CompoundTag t = root.getCompound(TAG);
        Slot slot = st.getItem() instanceof ArtifactItem ai ? ai.slot() : Slot.FLOWER;
        List<Sub> subs = new ArrayList<>();
        ListTag ls = t.getList("subs", 10);
        for (int i = 0; i < ls.size(); i++) {
            CompoundTag s = ls.getCompound(i);
            subs.add(new Sub(Stat.of(s.getString("s")), s.getDouble("v"), s.getInt("r")));
        }
        Stat main = Stat.of(t.getString("main"));
        int rarity = Math.max(1, Math.min(5, t.getInt("rarity")));
        int level = Math.max(0, Math.min(maxLevel(rarity), t.getInt("level")));
        return new Piece(slot, t.getString("set"), rarity, level, main, mainValue(main, rarity, level), subs);
    }

    public static void write(ItemStack st, Piece p) {
        CompoundTag t = new CompoundTag();
        t.putString("set", p.set());
        t.putInt("rarity", p.rarity());
        t.putInt("level", p.level());
        t.putString("main", p.main().name());
        ListTag ls = new ListTag();
        for (Sub s : p.subs()) {
            CompoundTag c = new CompoundTag();
            c.putString("s", s.stat().name());
            c.putDouble("v", s.value());
            c.putInt("r", s.rolls());
            ls.add(c);
        }
        t.put("subs", ls);
        CustomData.update(DataComponents.CUSTOM_DATA, st, root -> root.put(TAG, t));
    }

    // ---- rolling a new one ----

    /** A fresh piece of the given set, slot and rarity, at level nought. */
    public static ItemStack roll(String set, Slot slot, int rarity, net.minecraft.util.RandomSource r) {
        ItemStack st = new ItemStack(ArtifactItem.of(slot));
        Stat[] pool = mains(slot);
        Stat main = pool[r.nextInt(pool.length)];
        List<Sub> subs = new ArrayList<>();
        int n = Math.max(1, Math.min(4, rarity - 1 + (r.nextDouble() < .2 ? 1 : 0)));
        for (int i = 0; i < n; i++) addSub(subs, main, rarity, r);
        write(st, new Piece(slot, set, rarity, 0, main, mainValue(main, rarity, 0), subs));
        return st;
    }

    /** Picks a sub-stat that is neither the main stat nor one already on the piece, by weight. */
    private static void addSub(List<Sub> subs, Stat main, int rarity, net.minecraft.util.RandomSource r) {
        double total = 0;
        List<Stat> pool = new ArrayList<>();
        for (Stat s : Stat.values()) {
            if (s == main) continue;
            if (s.lotm() && s.attribute() == null) continue;      // nothing to move: leave it out
            boolean had = false;
            for (Sub x : subs) if (x.stat() == s) had = true;
            if (had) continue;
            pool.add(s);
            total += s.weight;
        }
        if (pool.isEmpty()) return;
        double pick = r.nextDouble() * total;
        for (Stat s : pool) {
            pick -= s.weight;
            if (pick <= 0) { subs.add(new Sub(s, subRoll(s, rarity, r), 1)); return; }
        }
        Stat s = pool.get(pool.size() - 1);
        subs.add(new Sub(s, subRoll(s, rarity, r), 1));
    }

    /**
     * Levels a piece. Every fourth level either hands it a sub-stat it does not have yet or rolls
     * one of the four it does have again, which is the whole gamble of the thing.
     */
    public static ItemStack levelUp(ItemStack st, int by, net.minecraft.util.RandomSource r) {
        Piece p = read(st);
        if (p == null) return st;
        int level = p.level(), max = p.maxLevel();
        List<Sub> subs = new ArrayList<>(p.subs());
        for (int i = 0; i < by && level < max; i++) {
            level++;
            if (level % 4 != 0) continue;
            if (subs.size() < 4) addSub(subs, p.main(), p.rarity(), r);
            else {
                int k = r.nextInt(subs.size());
                Sub s = subs.get(k);
                subs.set(k, new Sub(s.stat(), round(s.value() + subRoll(s.stat(), p.rarity(), r), s.stat()), s.rolls() + 1));
            }
        }
        write(st, new Piece(p.slot(), p.set(), p.rarity(), level, p.main(), mainValue(p.main(), p.rarity(), level), subs));
        return st;
    }

    // ---- adding it all up ----

    /** Every stat on a set of worn pieces, the set bonuses included. */
    public static java.util.Map<Stat, Double> totals(ItemStack[] worn) {
        java.util.EnumMap<Stat, Double> out = new java.util.EnumMap<>(Stat.class);
        for (ItemStack st : worn) {
            if (st == null || st.isEmpty()) continue;
            Piece p = read(st);
            if (p == null) continue;
            out.merge(p.main(), p.mainValue(), Double::sum);
            for (Sub s : p.subs()) out.merge(s.stat(), s.value(), Double::sum);
        }
        for (var e : ArtifactSets.bonuses(worn).entrySet()) out.merge(e.getKey(), e.getValue(), Double::sum);
        return out;
    }

    /** The modifier id a stat's contribution is filed under, so it can be replaced cleanly. */
    public static ResourceLocation modId(Stat s, boolean pct) {
        return ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID,
                "artifact_" + s.name().toLowerCase(java.util.Locale.ROOT) + (pct ? "_p" : "_f"));
    }

    static AttributeModifier.Operation op(Stat s) {
        return s.pct ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE : AttributeModifier.Operation.ADD_VALUE;
    }

    /** Kept so the health one can be told apart when a player's max health is put back. */
    static Holder<Attribute> maxHealth() { return Attributes.MAX_HEALTH; }
}
