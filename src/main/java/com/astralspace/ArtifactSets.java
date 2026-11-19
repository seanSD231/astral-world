package com.astralspace;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.astralspace.Artifacts.Stat;

/**
 * The sets, and what wearing two or four of one pays.
 *
 * <p>Most are the pathways, so a set reads as a way of fighting rather than a pile of numbers: the
 * Sun burns and holds its ground, the Assassin wants a knife in the dark, the Sleepless never runs
 * out. A few more are drawn from this mod's own corners.</p>
 *
 * <p>Bonuses are stats, not scripts. A stat is a number that stacks cleanly with everything else in
 * the game, survives a reload and can be shown to the player, and a four-piece worth having can be
 * written as one. Keeping them to stats also means a set never half-works because some other mod
 * got to the event first.</p>
 */
public final class ArtifactSets {
    private ArtifactSets() { }

    /** A set: what it is called, what it pays at two pieces, and what it pays at four. */
    public record Set(String id, String title, int colour, String flavour,
                      Map<Stat, Double> two, Map<Stat, Double> four) {
        public String twoLine() { return line(two); }
        public String fourLine() { return line(four); }
        private static String line(Map<Stat, Double> m) {
            StringBuilder b = new StringBuilder();
            for (var e : m.entrySet()) {
                if (b.length() > 0) b.append(", ");
                b.append(e.getKey().line(e.getValue()));
            }
            return b.toString();
        }
    }

    private static final Map<String, Set> SETS = new LinkedHashMap<>();
    private static final List<Set> ORDER = new ArrayList<>();

    private static Map<Stat, Double> m(Object... kv) {
        EnumMap<Stat, Double> out = new EnumMap<>(Stat.class);
        for (int i = 0; i < kv.length; i += 2) out.put((Stat) kv[i], ((Number) kv[i + 1]).doubleValue());
        return out;
    }

    private static void set(String id, String title, int colour, String flavour,
                            Map<Stat, Double> two, Map<Stat, Double> four) {
        Set s = new Set(id, title, colour, flavour, two, four);
        SETS.put(id, s);
        ORDER.add(s);
    }

    static {
        // ---- the pathways ----
        set("fool", "Mask of the Fool", 0x9B6BD8, "Every part is a part you can play.",
            m(Stat.ABILITY_DUR, .15), m(Stat.ABILITY_POWER, .18, Stat.COOLDOWN, .08));
        set("door", "Lintel of the Door", 0xE0B050, "Arriving is the whole of the trick.",
            m(Stat.SPEED_PCT, .12), m(Stat.COOLDOWN, .18, Stat.SPEED_PCT, .08));
        set("error", "Ledger of the Error", 0x3A8F6A, "Someone always pays, and it is rarely you.",
            m(Stat.CRIT_RATE, .08), m(Stat.CRIT_DMG, .30));
        set("hanged_man", "Chains of the Hanged Man", 0x8E2030, "Borne, because someone has to bear it.",
            m(Stat.HP_PCT, .16), m(Stat.ABILITY_RESIST, .16, Stat.DEF_PCT, .14));
        set("sun", "Crown of the Sun", 0xFFC24A, "Nothing unclean stands in the light.",
            m(Stat.ATK_PCT, .14), m(Stat.ABILITY_POWER, .14, Stat.HP_PCT, .12));
        set("tyrant", "Tide of the Tyrant", 0x2E6FB0, "The sea does not negotiate.",
            m(Stat.ATK_PCT, .14), m(Stat.ATK_PCT, .14, Stat.ABILITY_AREA, .20));
        set("darkness", "Veil of Darkness", 0x4B3C7A, "What is not seen is not stopped.",
            m(Stat.CRIT_RATE, .08), m(Stat.ABILITY_POWER, .16, Stat.SPIRIT_COST, -.10));
        set("death", "Pall of Death", 0x586070, "An ending, arranged.",
            m(Stat.ATK_PCT, .14), m(Stat.CRIT_DMG, .24, Stat.ABILITY_POWER, .10));
        set("twilight_giant", "Mail of the Twilight Giant", 0xB07A3A, "Older than the walls, and still standing.",
            m(Stat.DEF_PCT, .20), m(Stat.HP_PCT, .18, Stat.ABILITY_RESIST, .14));
        set("black_emperor", "Seal of the Black Emperor", 0x202028, "Everything spoken, kept.",
            m(Stat.SPIRIT_MAX, 160), m(Stat.ABILITY_POWER, .16, Stat.COOLDOWN, .10));
        set("demoness", "Dance of the Demoness", 0xC03C8A, "Change the rules while they read them.",
            m(Stat.SPEED_PCT, .12), m(Stat.CRIT_RATE, .14, Stat.ABILITY_DUR, .14));
        set("red_priest", "Brand of the Red Priest", 0xD04020, "Burn it down and see what stands.",
            m(Stat.ATK_PCT, .14), m(Stat.ATK_PCT, .20, Stat.CRIT_DMG, .14));
        set("abyss", "Hunger of the Abyss", 0x1E4038, "It eats, and it is never finished.",
            m(Stat.ABILITY_POWER, .14), m(Stat.ABILITY_POWER, .18, Stat.SPIRIT_REGEN, .20));
        set("paragon", "Wheel of the Paragon", 0xD8C070, "Turn it far enough and it comes out right.",
            m(Stat.CRIT_RATE, .08), m(Stat.CRIT_RATE, .10, Stat.CRIT_DMG, .18));
        set("mother", "Bloom of the Mother", 0x4FA050, "Everything grows back.",
            m(Stat.HP_PCT, .16), m(Stat.HP_PCT, .18, Stat.SPIRIT_REGEN, .22));
        set("chained", "Links of the Chained", 0x6A6A74, "Held down is not the same as held.",
            m(Stat.DEF_PCT, .20), m(Stat.ABILITY_RESIST, .20, Stat.HP_PCT, .12));
        set("visionary", "Dream of the Visionary", 0x7A6AC8, "Everyone agrees, eventually.",
            m(Stat.ABILITY_AREA, .18), m(Stat.ABILITY_AREA, .20, Stat.ABILITY_DUR, .16));
        set("hermit", "Lamp of the Hermit", 0xC8A850, "Knowing the word is most of the work.",
            m(Stat.SPIRIT_MAX, 160), m(Stat.SPIRIT_COST, -.16, Stat.ABILITY_POWER, .12));
        set("sailor", "Gale of the Sailor", 0x4090B0, "Weather happens to other people.",
            m(Stat.SPEED_PCT, .12), m(Stat.ATK_PCT, .14, Stat.SPEED_PCT, .10));
        set("sleepless", "Vigil of the Sleepless", 0x5878A0, "Still awake. Still here.",
            m(Stat.SPIRIT_REGEN, .18), m(Stat.SPIRIT_REGEN, .22, Stat.SPIRIT_COST, -.12));
        set("assassin", "Edge of the Assassin", 0x383840, "Once, properly, and then gone.",
            m(Stat.CRIT_RATE, .08), m(Stat.CRIT_DMG, .26, Stat.SPEED_PCT, .08));
        set("marauder", "Spoils of the Marauder", 0x7A4090, "Whatever is not nailed down.",
            m(Stat.ATK_PCT, .14), m(Stat.ATK_PCT, .16, Stat.SPEED_PCT, .10));

        // ---- this mod's own corners ----
        set("tingen", "Soot of Tingen", 0x6E6258, "A city that keeps its own counsel.",
            m(Stat.DEF_PCT, .18), m(Stat.HP_PCT, .14, Stat.COOLDOWN, .10));
        set("arena", "Honour of the Arena", 0xC05030, "Won in front of witnesses.",
            m(Stat.ATK_PCT, .14), m(Stat.CRIT_RATE, .10, Stat.ATK_PCT, .14));
        set("cosmos", "Drift of the Cosmos", 0x30407A, "Very far away, and not indifferent.",
            m(Stat.ABILITY_AREA, .18), m(Stat.ABILITY_POWER, .14, Stat.ABILITY_AREA, .16));
        set("masquerade", "Grace of the Masquerade", 0x2A2A34, "Nobody here is anybody.",
            m(Stat.SPEED_PCT, .12), m(Stat.CRIT_DMG, .20, Stat.ABILITY_DUR, .12));
    }

    public static Set get(String id) { return SETS.get(id); }

    public static List<Set> all() { return java.util.Collections.unmodifiableList(ORDER); }

    public static Set random(net.minecraft.util.RandomSource r) { return ORDER.get(r.nextInt(ORDER.size())); }

    /** How many pieces of each set are being worn. */
    public static Map<String, Integer> counts(ItemStack[] worn) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (ItemStack st : worn) {
            if (st == null || st.isEmpty()) continue;
            Artifacts.Piece p = Artifacts.read(st);
            if (p == null || p.set().isEmpty()) continue;
            out.merge(p.set(), 1, Integer::sum);
        }
        return out;
    }

    /** Everything the sets being worn pay out, added together. */
    public static Map<Stat, Double> bonuses(ItemStack[] worn) {
        EnumMap<Stat, Double> out = new EnumMap<>(Stat.class);
        for (var e : counts(worn).entrySet()) {
            Set s = SETS.get(e.getKey());
            if (s == null) continue;
            if (e.getValue() >= 2) for (var b : s.two().entrySet()) out.merge(b.getKey(), b.getValue(), Double::sum);
            if (e.getValue() >= 4) for (var b : s.four().entrySet()) out.merge(b.getKey(), b.getValue(), Double::sum);
        }
        return out;
    }
}
