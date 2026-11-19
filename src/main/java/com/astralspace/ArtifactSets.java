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

    /** A set: the pathway it belongs to, what it is called, and what it pays at two and at four. */
    public record Set(String id, String pathway, String title, String flavour,
                      Map<Stat, Double> two, Map<Stat, Double> four) {
        public int colour() { return Pathways.colour(pathway); }
        /** The pathway symbol already in the mod, used as the face of the set in the menu. */
        public String symbol() { return "sym_" + pathway; }
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

    private static void set(String pathway, String title, String flavour,
                            Map<Stat, Double> two, Map<Stat, Double> four) {
        Set s = new Set(pathway, pathway, title, flavour, two, four);
        SETS.put(pathway, s);
        ORDER.add(s);
    }

    static {
        // ---- one for each of the twenty-two pathways, keyed by the same ids the rest of the mod uses ----
        set("seer", "Mask of the Fool", "Every part is a part you can play.",
            m(Stat.ABILITY_DUR, .15), m(Stat.ABILITY_POWER, .18, Stat.COOLDOWN, .08));
        set("apprentice", "Lintel of the Door", "Arriving is the whole of the trick.",
            m(Stat.SPEED_PCT, .12), m(Stat.COOLDOWN, .18, Stat.SPEED_PCT, .08));
        set("marauder", "Ledger of the Error", "Someone always pays, and it is rarely you.",
            m(Stat.CRIT_RATE, .08), m(Stat.CRIT_DMG, .30));
        set("spectator", "Dream of the Visionary", "Everyone agrees, eventually.",
            m(Stat.ABILITY_AREA, .18), m(Stat.ABILITY_AREA, .20, Stat.ABILITY_DUR, .16));
        set("bard", "Crown of the Sun", "Nothing unclean stands in the light.",
            m(Stat.ATK_PCT, .14), m(Stat.ABILITY_POWER, .14, Stat.HP_PCT, .12));
        set("sailor", "Tide of the Tyrant", "The sea does not negotiate.",
            m(Stat.ATK_PCT, .14), m(Stat.ATK_PCT, .14, Stat.ABILITY_AREA, .20));
        set("monster", "Turn of the White Tower", "Luck is a thing that can be taken.",
            m(Stat.CRIT_RATE, .08), m(Stat.CRIT_RATE, .12, Stat.ABILITY_RESIST, .14));
        set("sleepless", "Veil of Darkness", "What is not seen is not stopped.",
            m(Stat.SPIRIT_REGEN, .18), m(Stat.ABILITY_POWER, .16, Stat.SPIRIT_COST, -.12));
        set("corpse_collector", "Pall of Death", "An ending, arranged.",
            m(Stat.ATK_PCT, .14), m(Stat.CRIT_DMG, .24, Stat.ABILITY_POWER, .10));
        set("warrior", "Mail of the Twilight Giant", "Older than the walls, and still standing.",
            m(Stat.DEF_PCT, .20), m(Stat.HP_PCT, .18, Stat.ABILITY_RESIST, .14));
        set("assassin", "Dance of the Demoness", "Change the rules while they read them.",
            m(Stat.CRIT_RATE, .08), m(Stat.CRIT_DMG, .26, Stat.SPEED_PCT, .08));
        set("criminal", "Brand of the Red Priest", "Burn it down and see what stands.",
            m(Stat.ATK_PCT, .14), m(Stat.ATK_PCT, .20, Stat.CRIT_DMG, .14));
        set("secrets_supplicant", "Chains of the Hanged Man", "Borne, because someone has to bear it.",
            m(Stat.HP_PCT, .16), m(Stat.ABILITY_RESIST, .16, Stat.DEF_PCT, .14));
        set("planter", "Bloom of the Mother", "Everything grows back.",
            m(Stat.HP_PCT, .16), m(Stat.HP_PCT, .18, Stat.SPIRIT_REGEN, .22));
        set("apothecary", "Phase of the Moon", "It comes round whether you watch or not.",
            m(Stat.SPIRIT_MAX, 160), m(Stat.SPIRIT_REGEN, .20, Stat.HP_PCT, .14));
        set("prisoner", "Hunger of the Abyss", "It eats, and it is never finished.",
            m(Stat.ABILITY_POWER, .14), m(Stat.ABILITY_POWER, .18, Stat.SPIRIT_REGEN, .20));
        set("hunter", "Wheel of the Paragon", "Turn it far enough and it comes out right.",
            m(Stat.CRIT_RATE, .08), m(Stat.CRIT_RATE, .10, Stat.CRIT_DMG, .18));
        set("reader", "Seal of the Black Emperor", "Everything spoken, kept.",
            m(Stat.SPIRIT_MAX, 160), m(Stat.ABILITY_POWER, .16, Stat.COOLDOWN, .10));
        set("savant", "Lamp of the Hermit", "Knowing the word is most of the work.",
            m(Stat.SPIRIT_MAX, 160), m(Stat.SPIRIT_COST, -.16, Stat.ABILITY_POWER, .12));
        set("arbiter", "Scales of the Justiciar", "Weighed, and found wanting.",
            m(Stat.DEF_PCT, .20), m(Stat.ATK_PCT, .16, Stat.ABILITY_RESIST, .12));
        set("lawyer", "Links of the Chained", "Held down is not the same as held.",
            m(Stat.DEF_PCT, .20), m(Stat.ABILITY_RESIST, .20, Stat.HP_PCT, .12));
        set("mystery_pryer", "Silence of the Mystery Pryer", "Ask quietly. Ask once.",
            m(Stat.ABILITY_DUR, .15), m(Stat.ABILITY_POWER, .14, Stat.ABILITY_DUR, .14));

        // ---- and four from this mod's own corners, each borrowing a pathway's colour ----
        set2("tingen", "monster", "Soot of Tingen", "A city that keeps its own counsel.",
            m(Stat.DEF_PCT, .18), m(Stat.HP_PCT, .14, Stat.COOLDOWN, .10));
        set2("arena", "warrior", "Honour of the Arena", "Won in front of witnesses.",
            m(Stat.ATK_PCT, .14), m(Stat.CRIT_RATE, .10, Stat.ATK_PCT, .14));
        set2("cosmos", "spectator", "Drift of the Cosmos", "Very far away, and not indifferent.",
            m(Stat.ABILITY_AREA, .18), m(Stat.ABILITY_POWER, .14, Stat.ABILITY_AREA, .16));
        set2("masquerade", "assassin", "Grace of the Masquerade", "Nobody here is anybody.",
            m(Stat.SPEED_PCT, .12), m(Stat.CRIT_DMG, .20, Stat.ABILITY_DUR, .12));
    }

    private static void set2(String id, String pathway, String title, String flavour,
                             Map<Stat, Double> two, Map<Stat, Double> four) {
        Set s = new Set(id, pathway, title, flavour, two, four);
        SETS.put(id, s);
        ORDER.add(s);
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
