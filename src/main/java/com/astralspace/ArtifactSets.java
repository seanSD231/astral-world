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
 * <p>A set is a case file rather than a brand: five objects pulled out of the same incident, which
 * resonate with one another because they were steeped in the same event. That is what the two
 * halves are. Two of them on one person bleeds a little of the incident's nature into them, which
 * is a number. Four and enough of the incident is present for it to begin re-enacting itself
 * around the bearer, which is a behaviour -- and a behaviour it can only take the shape the event
 * originally had. It is also why the four-piece is never just a bigger number.</p>
 *
 * <p>{@code file} is the Church's own designation for the incident and {@code flavour} is a line
 * out of the file on it.</p>
 */
public final class ArtifactSets {
    private ArtifactSets() { }

    /**
     * A set. {@code pathway} is only where its colour and its symbol come from, so the menu has
     * something to draw with; it says nothing about what the set is for.
     */
    public record Set(String id, String pathway, String title, String file, String flavour,
                      Map<Stat, Double> two, String fourText, String fourCost,
                      String fiveText, Map<Stat, Double> fivePen) {
        /** The price of carrying the ability, written out. */
        public String fiveCost() {
            StringBuilder b = new StringBuilder();
            for (var e : fivePen.entrySet()) {
                if (b.length() > 0) b.append(", ");
                b.append(e.getKey().line(e.getValue()));
            }
            return b + " for as long as you carry it";
        }
        public String four() { return id; }
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
        public String fiveLine() { return fiveText; }
    }

    private static final Map<String, Set> SETS = new LinkedHashMap<>();
    private static final List<Set> ORDER = new ArrayList<>();

    private static Map<Stat, Double> m(Object... kv) {
        EnumMap<Stat, Double> out = new EnumMap<>(Stat.class);
        for (int i = 0; i < kv.length; i += 2) out.put((Stat) kv[i], ((Number) kv[i + 1]).doubleValue());
        return out;
    }

    private static void set(String id, String pathway, String title, String file, String flavour,
                            Map<Stat, Double> two, String fourText, String fourCost,
                            String fiveText, Map<Stat, Double> fivePen) {
        Set s = new Set(id, pathway, title, file, flavour, two, fourText, fourCost, fiveText, fivePen);
        SETS.put(id, s);
        ORDER.add(s);
    }

    static {
        set("evernight", "sleepless", "Rite of the Evernight", "Case BK-114",
            "Recovered from a night chapel in Backlund where the rite ran to its end with nobody left to stop it. Everything taken out of that room still prefers the dark.",
            m(Stat.CRIT_RATE, .12, Stat.ABILITY_POWER, .10, Stat.HP_PCT, -.12),
            "Your blows mark what they land on for ten seconds. Finishing something marked hides you for two seconds and gives spirituality back.",
            "In daylight your crit rate is halved.",
            "Hands you one of the shadow authorities, from a sense of danger up to a world of shadows.",
            m(Stat.HP_PCT, -.18));

        set("storms", "sailor", "Hymn of the Storms", "Case PH-007",
            "Off Pritz Harbour a congregation sang the hymn through a squall and the squall answered. Eleven objects came back aboard. Four of them are still wet.",
            m(Stat.ATK_PCT, .18, Stat.SPEED_PCT, .08, Stat.ABILITY_RESIST, -.14),
            "Every ten seconds your next blow calls lightning down on it. Being wet halves the wait.",
            "The bolt finds you too, for a tenth of what it deals.",
            "Hands you one of the lightning authorities, from a spark up to an ocean of it.",
            m(Stat.DEF_PCT, -.20));

        set("steam", "savant", "Gearwork of Steam", "Case BK-902",
            "A boiler house that held a pressure it had no business holding, for eight days, and then did not. Nothing recovered from it will sit still under a load.",
            m(Stat.DEF_PCT, .22, Stat.HP_PCT, .10, Stat.SPEED_PCT, -.12),
            "Being hurt builds pressure. At five your next blow vents it as a shockwave and leaves you quick for four seconds.",
            "While it is at five and unvented you take 15% more.",
            "Hands you one of the force authorities, from a sonic boom up to a calamity.",
            m(Stat.SPEED_PCT, -.18));

        set("acting", "seer", "The Acting Method", "Case TN-031",
            "A Tingen dressing room where somebody held a part for nine weeks without once breaking it. The part is still in the clothes.",
            m(Stat.CRIT_DMG, .28, Stat.ABILITY_DUR, .12, Stat.DEF_PCT, -.18),
            "Eight seconds untouched and you are in the role: a third more damage, until something lands on you.",
            "For four seconds after it breaks you deal a fifth less.",
            "Hands you one of the persona authorities, from placating up to alteration.",
            m(Stat.DEF_PCT, -.18));

        set("harvest", "planter", "Mother's Harvest", "Case CO-046",
            "A field that went on yielding four winters after it was salted. The men sent to dig it up were found standing in it, unhurt, and unwilling to leave.",
            m(Stat.HP_PCT, .20, Stat.SPIRIT_REGEN, .12, Stat.SPEED_PCT, -.12),
            "Stand still two seconds to root: healing over time and a great deal of defence, until you move.",
            "While you are moving, healing on you is cut by a third.",
            "Hands you one of the flesh and blood authorities, from mending up to immortality.",
            m(Stat.ATK_PCT, -.20));

        set("ledger", "reader", "Wisdom's Ledger", "Case BK-250",
            "Every page of it is a different hand and none of them repeat. The archivist who noticed that is the last entry.",
            m(Stat.SPIRIT_MAX, 220, Stat.ABILITY_POWER, .12, Stat.CRIT_RATE, -.08),
            "A different kind of blow within six seconds of the last hits a quarter harder.",
            "Repeating yourself inside six seconds hits a fifth softer.",
            "Hands you one of the written authorities, from symbolisation up to sealing.",
            m(Stat.CRIT_RATE, -.12));

        set("alchemy", "apothecary", "Psychology Alchemy", "Case BY-018",
            "A Bayam clinic where the treatment worked. Patients left better than they came, and what was taken out of them had to go somewhere.",
            m(Stat.ABILITY_POWER, .16, Stat.SPIRIT_REGEN, .10, Stat.ABILITY_RESIST, -.14),
            "Healing, given or taken, turns into ability power for eight seconds, up to five times over.",
            "All healing on you is a quarter weaker.",
            "Hands you one of the mind authorities, from reading one up to a storm of plague.",
            m(Stat.ABILITY_RESIST, -.18));

        set("fog", "monster", "The Grey Fog", "Case 0-00",
            "No incident site. No recovery team. The file holds four objects and the note that they were already in the vault when the vault was built.",
            m(Stat.COOLDOWN, .16, Stat.ABILITY_AREA, .12, Stat.ATK_PCT, -.14),
            "Falling under half your health puts the fog round you: a step clear, and a second and a half nobody can touch you. Once a minute.",
            "For five seconds afterwards you deal a third less.",
            "Hands you one of the fortune authorities, from a sense of danger up to the cycle of fate.",
            m(Stat.ATK_PCT, -.18));

        set("thread", "mystery_pryer", "Spirit Thread", "Case 0-08",
            "Sealed, catalogued and buried twice. The second burial was because the first one came back up attached to something.",
            m(Stat.ABILITY_AREA, .20, Stat.CRIT_RATE, .08, Stat.HP_PCT, -.14),
            "What you hit is threaded to what else you have hit for six seconds. A fifth of what you deal to one is dealt to the rest.",
            "While the threads are live you take a tenth more.",
            "Hands you one of the binding authorities, from binding up to a spatial seal.",
            m(Stat.SPEED_PCT, -.20));

        set("control", "criminal", "Loss of Control", "Case FP-073",
            "Whatever he was before, he was not that for long. The objects were on him when it finished and they have not calmed down since.",
            m(Stat.CRIT_RATE, .12, Stat.ATK_PCT, .14, Stat.ABILITY_RESIST, -.20),
            "Under two fifths of your health you deal half again as much.",
            "All healing on you is halved, always.",
            "Hands you one of the frenzy authorities, from berserk up to nightmare.",
            m(Stat.ABILITY_RESIST, -.22));

        set("tarot", "spectator", "The Tarot Club", "Case 0-19",
            "Nobody recovered these. They arrived, one at a time, correctly addressed, from a place the Church has no name for.",
            m(Stat.SPIRIT_REGEN, .22, Stat.COOLDOWN, .12, Stat.DEF_PCT, -.16),
            "Every twenty seconds your next blow strikes a third harder and gives a great deal of spirituality back.",
            "Between those blows you deal a tenth less.",
            "Hands you one of the fate authorities, from prophecy up to reincarnation.",
            m(Stat.HP_PCT, -.18));

        set("ascetic", "warrior", "The Moses Ascetic Order", "Case IN-012",
            "They fasted until the siege broke. It broke. Everything they were still wearing when the walls came down holds its edge best when there is nothing left.",
            m(Stat.ABILITY_RESIST, .18, Stat.DEF_PCT, .14, Stat.ATK_PCT, -.16),
            "Below half health you take a fifth less from everything, and mend a little every second you stay there.",
            "Above half health you deal a sixth less.",
            "Hands you one of the holy authorities, from an oath up to the incarnation of order.",
            m(Stat.ATK_PCT, -.18));

        set("mirror", "lawyer", "The Mirror Sea", "Case 0-21",
            "Taken from a drawing room with one mirror in it and two sets of footprints leading away.",
            m(Stat.ABILITY_POWER, .16, Stat.CRIT_DMG, .14, Stat.SPEED_PCT, -.12),
            "Every fifteen seconds your next blow is copied onto something else nearby for three quarters.",
            "The copy is dealt to you as well, at a tenth.",
            "Hands you one of the door authorities, from a mirage up to exile.",
            m(Stat.HP_PCT, -.16));

        set("gloves", "hunter", "The Red Gloves", "Case LO-088",
            "A hunting party of six went out. The gloves came back. The file does not say how.",
            m(Stat.ATK_PCT, .16, Stat.CRIT_RATE, .09, Stat.HP_PCT, -.12),
            "The first blow you land on anything hits half again as hard.",
            "Every blow after that on the same thing softens, to a third less.",
            "Hands you one of the twilight authorities, from a sense of danger up to the dawn armoury.",
            m(Stat.DEF_PCT, -.18));

        set("quiet", "corpse_collector", "The Quiet Hours", "Case TN-004",
            "An undertaker's back room where the work was done properly and the clients did not always stay down.",
            m(Stat.HP_PCT, .18, Stat.DEF_PCT, .12, Stat.ABILITY_POWER, -.16),
            "A blow that would finish you leaves you at a sliver instead. Once every five minutes.",
            "For twenty seconds afterwards you deal two fifths less.",
            "Hands you one of the underworld authorities, from speaking with them up to ruling them.",
            m(Stat.ABILITY_POWER, -.18));

        set("bell", "bard", "The Sound of the Bell", "Case IN-055",
            "It was rung once, at the wrong hour, and the town below it agreed about something for a week.",
            m(Stat.ABILITY_AREA, .16, Stat.ABILITY_DUR, .16, Stat.CRIT_DMG, -.20),
            "What you hit deals a fifth less for six seconds.",
            "A fifth off your own crit rate.",
            "Hands you one of the sound authorities, from singing up to a kingdom of light.",
            m(Stat.CRIT_DMG, -.20));

        set("knife", "assassin", "The Assassin's Study", "Case BK-370",
            "Nothing in the room was out of place, which was how they knew.",
            m(Stat.CRIT_DMG, .26, Stat.SPEED_PCT, .09, Stat.DEF_PCT, -.20),
            "Striking something that has not yet seen you hits three fifths harder.",
            "Anything that is looking at you hits you a fifth harder.",
            "Hands you one of the concealment authorities, from hiding up to the bearing of sins.",
            m(Stat.HP_PCT, -.20));

        set("lamp", "arbiter", "The Last Lamp", "Case LO-140",
            "The lamp was still burning. It had been burning for nine years and there was nothing in it.",
            m(Stat.SPIRIT_MAX, 180, Stat.ABILITY_RESIST, .16, Stat.SPEED_PCT, -.10),
            "Standing in light you mend steadily and take a tenth less.",
            "In the dark you deal a quarter less.",
            "Vigil: in light, every ten seconds one ability costs you nothing.",
            "In the dark you take a quarter more.");

        set("sealwax", "prisoner", "The Unopened Letter", "Case 0-33",
            "It is addressed to somebody who has not been born. The seal has been tested and will not break.",
            m(Stat.SPIRIT_MAX, 200, Stat.COOLDOWN, .14, Stat.CRIT_RATE, -.10),
            "Going ten seconds without striking anything makes your next blow land for double.",
            "Every blow you land cuts your ability power a twentieth, to a fifth, until you stop again.",
            "Hands you one of the sealed authorities, from a concealed bundle up to a gamma ray burst.",
            m(Stat.DEF_PCT, -.18));
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

    /**
     * Only the two-fragment half is stats, and one of the three in it is always negative. The four
     * and five halves are mechanics and are run in {@link SetEffects} rather than added up here.
     */
    public static Map<Stat, Double> bonuses(ItemStack[] worn) {
        EnumMap<Stat, Double> out = new EnumMap<>(Stat.class);
        for (var e : counts(worn).entrySet()) {
            Set s = SETS.get(e.getKey());
            if (s == null || e.getValue() < 2) continue;
            for (var b : s.two().entrySet()) out.merge(b.getKey(), b.getValue(), Double::sum);
            // the price of carrying the authority, paid for as long as all five are on
            if (e.getValue() >= 5)
                for (var b : s.fivePen().entrySet()) out.merge(b.getKey(), b.getValue(), Double::sum);
        }
        return out;
    }
}
