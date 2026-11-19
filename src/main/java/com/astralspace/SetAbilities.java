package com.astralspace;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What five fragments of one set actually hand you: a Beyonder ability out of Lord of the Mysteries
 * itself.
 *
 * <p>That mod keeps its abilities as items -- a Scribe's whole trade is carrying other people's
 * around -- so there is nothing to invent here. Each set has a ladder of five of them, weakest
 * first, and which rung you are given is decided by how fine the five fragments are: five 1-class
 * scraps give you the bottom of the ladder, five 0-class pieces give you the top of it.</p>
 *
 * <p>The granted stack is marked as ours. Nothing unmarked is ever taken away, so an ability a
 * player earned the ordinary way is never swept up by this, and the marked one is removed the
 * moment the fifth fragment comes off.</p>
 */
public final class SetAbilities {
    private SetAbilities() { }

    private static final String TAG = "astral_set_grant";

    /** Set id to its five rungs, weakest first. Every id is an item in that mod. */
    private static final Map<String, String[]> LADDER = new HashMap<>();

    static {
        LADDER.put("evernight", new String[]{"dangersense", "shadowarmor", "shadowtrap", "shadowsummon", "divinekingdomworldofshadows"});
        LADDER.put("storms", new String[]{"enableordisablelightning", "sailorlightning", "lightningball", "lightningpillar", "oceanoflightning"});
        LADDER.put("steam", new String[]{"sonicboom", "ragingblows", "earthquake", "volcaniceruption", "calamityincarnationtornado"});
        LADDER.put("acting", new String[]{"placate", "psychologicalinvisibility", "virtualpersona", "battlehypnotism", "alteration"});
        LADDER.put("harvest", new String[]{"envisionhealth", "fleshandbloodmend", "envisionlife", "fleshandbloodbodymodification", "fleshandbloodimmortality"});
        LADDER.put("ledger", new String[]{"symbolization", "secretkeeping", "conceptualization", "scribeabilities", "sealing"});
        LADDER.put("alchemy", new String[]{"mindreading", "manipulateemotion", "hypnosis", "mentalplague", "plaguestorm"});
        LADDER.put("fog", new String[]{"monsterdangersense", "luckchanneling", "misfortunebestowal", "probabilitymanipulationworldfortune", "cycleoffate"});
        LADDER.put("thread", new String[]{"binding", "poltergeist", "objectification", "spatialcage", "spatialseal"});
        LADDER.put("control", new String[]{"berserk", "frenzy", "bane", "mindstorm", "nightmare"});
        LADDER.put("tarot", new String[]{"prophecy", "falseprophecy", "luckgifting", "fatedconnection", "fatereincarnation"});
        LADDER.put("ascetic", new String[]{"holyoath", "purification", "holyequipment", "justicehalo", "incarnationoforder"});
        LADDER.put("mirror", new String[]{"doormirage", "replicate", "doorlayering", "trickblackcurtain", "exile"});
        LADDER.put("gloves", new String[]{"warriordangersense", "eyeofdemonhunting", "silverrapier", "silverswordmanifestation", "dawnarmory"});
        LADDER.put("quiet", new String[]{"spiritcommunication", "languageofthedead", "soulcontrol", "splitsoulresurrection", "sovereignofsouls"});
        LADDER.put("bell", new String[]{"singing", "amplification", "nullification", "unshadoweddomain", "kingdomoflight"});
        LADDER.put("knife", new String[]{"sleeplessconcealment", "grazing", "shadowsubstitute", "shadowcrush", "sinbearing"});
        LADDER.put("lamp", new String[]{"holyeye", "lightofholiness", "sunhalo", "purificationhalo", "lightauthority"});
        LADDER.put("sealwax", new String[]{"createconcealedbundle", "doorconcealment", "createconcealedspace", "spatialtearing", "doorgammarayburst"});
    }

    /** Which rung the five fragments on this player come to, nought to four. */
    public static int rung(net.minecraft.world.entity.player.Player p, String set) {
        int sum = 0, n = 0;
        for (ItemStack st : ArtifactSlots.worn(p)) {
            if (st == null || st.isEmpty()) continue;
            Artifacts.Piece pc = Artifacts.read(st);
            if (pc == null || !pc.set().equals(set)) continue;
            sum += pc.rarity();
            n++;
        }
        if (n == 0) return 0;
        return Math.max(0, Math.min(4, Math.round(sum / (float) n) - 1));
    }

    /** The item that rung names, or nothing when that mod is not installed. */
    public static Item item(String set, int rung) {
        String[] l = LADDER.get(set);
        if (l == null) return null;
        var rl = ResourceLocation.fromNamespaceAndPath("lotm", l[Math.max(0, Math.min(4, rung))]);
        return BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
    }

    /** What that rung is called, for the menus. Empty when there is nothing to name. */
    public static String name(String set, int rung) {
        Item it = item(set, rung);
        return it == null ? "" : new ItemStack(it).getHoverName().getString();
    }

    /** Every rung of a set, named, so the menu can show the whole ladder. */
    public static String[] names(String set) {
        String[] out = new String[5];
        for (int i = 0; i < 5; i++) out[i] = name(set, i);
        return out;
    }

    // ---- handing them out ----

    private static boolean ours(ItemStack st) {
        CustomData cd = st.get(DataComponents.CUSTOM_DATA);
        return cd != null && !cd.copyTag().getString(TAG).isEmpty();
    }

    private static String grantOf(ItemStack st) {
        CustomData cd = st.get(DataComponents.CUSTOM_DATA);
        return cd == null ? "" : cd.copyTag().getString(TAG);
    }

    /**
     * Brings what they are carrying into line with what they are wearing: one marked ability for
     * every set they have all five of, at the rung their fragments come to, and nothing else of
     * ours. Marked only, so an ability they came by honestly is never touched.
     */
    public static void sync(ServerPlayer p) {
        Map<String, Integer> want = new HashMap<>();
        for (var e : ArtifactSets.counts(ArtifactSlots.worn(p)).entrySet())
            if (e.getValue() >= 5) want.put(e.getKey(), rung(p, e.getKey()));

        var inv = p.getInventory();
        Set<String> held = new HashSet<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!ours(st)) continue;
            String set = grantOf(st);
            Integer r = want.get(set);
            Item should = r == null ? null : item(set, r);
            if (should == null || st.getItem() != should || !held.add(set)) inv.setItem(i, ItemStack.EMPTY);
        }
        for (var e : want.entrySet()) {
            if (held.contains(e.getKey())) continue;
            Item it = item(e.getKey(), e.getValue());
            ItemStack st;
            if (it == null) st = SetAbility.of(e.getKey());          // that mod is not here: our own stand-in
            else st = new ItemStack(it);
            CustomData.update(DataComponents.CUSTOM_DATA, st, t -> t.putString(TAG, e.getKey()));
            if (!inv.add(st)) p.drop(st, false);
            ArtifactSets.Set s = ArtifactSets.get(e.getKey());
            if (s != null) p.displayClientMessage(Component.literal("All five. ")
                    .withColor(s.colour())
                    .append(Component.literal(st.getHoverName().getString()).withColor(s.colour())), false);
        }
    }
}
