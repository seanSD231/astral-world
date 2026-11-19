package com.astralspace;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.astralspace.Artifacts.Stat;

/**
 * The sets: what two pieces pay, and what four pieces do.
 *
 * <p>Two pieces is a number -- crit, attack, health, defence -- because a half set should be a
 * small legible push in one direction and nothing more. Four pieces is a mechanic: something that
 * watches what you do and answers it, so the set changes how you fight rather than what your sheet
 * says. The mechanics themselves live in {@link SetEffects}; what is kept here is the name of one
 * and the line that describes it.</p>
 *
 * <p>They are named for organisations and for Beyonder practice rather than for pathways. A pathway
 * already has a card and a hologram of its own, and naming a set after one says nothing about what
 * wearing it does. A church, a school of thought or a habit of mind does: the Evernight fights in
 * the dark, Steam takes a beating and gives it back, the Acting Method only works for as long as
 * nobody has laid a finger on you.</p>
 */
public final class ArtifactSets {
    private ArtifactSets() { }

    /**
     * A set. {@code pathway} is only where its colour and its symbol come from, so the menu has
     * something to draw with; it says nothing about what the set is for.
     */
    public record Set(String id, String pathway, String title, String flavour,
                      Map<Stat, Double> two, String four, String fourText) {
        public int colour() { return Pathways.colour(pathway); }
        public String symbol() { return "sym_" + pathway; }
        public String twoLine() {
            StringBuilder b = new StringBuilder();
            for (var e : two.entrySet()) {
                if (b.length() > 0) b.append(", ");
                b.append(e.getKey().line(e.getValue()));
            }
            return b.toString();
        }
        public String fourLine() { return fourText; }
    }

    private static final Map<String, Set> SETS = new LinkedHashMap<>();
    private static final List<Set> ORDER = new ArrayList<>();

    private static Map<Stat, Double> m(Object... kv) {
        EnumMap<Stat, Double> out = new EnumMap<>(Stat.class);
        for (int i = 0; i < kv.length; i += 2) out.put((Stat) kv[i], ((Number) kv[i + 1]).doubleValue());
        return out;
    }

    private static void set(String id, String pathway, String title, String flavour,
                            Map<Stat, Double> two, String fourText) {
        Set s = new Set(id, pathway, title, flavour, two, id, fourText);
        SETS.put(id, s);
        ORDER.add(s);
    }

    static {
        set("evernight", "sleepless", "Rite of the Evernight",
            "She watches at night, and so do they.",
            m(Stat.CRIT_RATE, .12),
            "Your blows mark what they land on. Killing something marked gives back spirituality and hides you for two seconds.");

        set("storms", "sailor", "Hymn of the Storms",
            "Nobody argues with the weather.",
            m(Stat.ATK_PCT, .18),
            "Every ten seconds your next blow calls lightning down on it. Standing in water halves the wait.");

        set("steam", "savant", "Gearwork of Steam",
            "Pressure is only a problem if it has nowhere to go.",
            m(Stat.DEF_PCT, .22),
            "Being hurt builds pressure. At five your next blow vents it as a shockwave, and leaves you quick for four seconds.");

        set("acting", "seer", "The Acting Method",
            "Be the part. Nobody looks for the man under it.",
            m(Stat.CRIT_DMG, .28),
            "Eight seconds untouched and you are in the role: a third more damage, until something lands on you.");

        set("harvest", "planter", "Mother's Harvest",
            "Stand still long enough and you belong to the ground.",
            m(Stat.HP_PCT, .20),
            "Stand still two seconds to root: healing over time and a great deal of defence, until you move.");

        set("ledger", "reader", "Wisdom's Ledger",
            "It is all written down, and it is all read back.",
            m(Stat.SPIRIT_MAX, 220),
            "Every blow is recorded. A different kind of blow within six seconds hits a quarter harder; repeating yourself does nothing.");

        set("alchemy", "apothecary", "Psychology Alchemy",
            "A mind is a thing you can put in a bottle.",
            m(Stat.ABILITY_POWER, .16),
            "Healing, given or taken, turns into ability power for eight seconds. It stacks.");

        set("fog", "monster", "The Grey Fog",
            "Somewhere above all of it, and quite unbothered.",
            m(Stat.COOLDOWN, .16),
            "Falling under half your health puts the fog round you: a step clear, and a second and a half nobody can touch you. Once a minute.");

        set("thread", "mystery_pryer", "Spirit Thread",
            "0-08. It was sealed for a reason.",
            m(Stat.ABILITY_AREA, .20),
            "What you hit is threaded to what else you have hit, for six seconds. A fifth of what you deal to one is dealt to the rest.");

        set("control", "criminal", "Loss of Control",
            "It is not that you cannot stop. It is that you will not.",
            m(Stat.CRIT_RATE, .12),
            "Under two fifths of your health: half again the attack, and half the healing.");

        set("tarot", "spectator", "The Tarot Club",
            "Above the fog, under false names, trading what cannot be bought.",
            m(Stat.SPIRIT_REGEN, .22),
            "Every twenty seconds your next blow strikes for a third more and gives you back a good deal of spirituality.");

        set("ascetic", "warrior", "The Moses Ascetic Order",
            "Faith is what is left when everything else has been taken away.",
            m(Stat.ABILITY_RESIST, .18),
            "Below half health you take a fifth less from everything, and mend a little every second you stay there.");
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

    /** Only the two-piece halves are stats. The four-piece halves are mechanics and are not added up here. */
    public static Map<Stat, Double> bonuses(ItemStack[] worn) {
        EnumMap<Stat, Double> out = new EnumMap<>(Stat.class);
        for (var e : counts(worn).entrySet()) {
            Set s = SETS.get(e.getKey());
            if (s == null || e.getValue() < 2) continue;
            for (var b : s.two().entrySet()) out.merge(b.getKey(), b.getValue(), Double::sum);
        }
        return out;
    }
}
