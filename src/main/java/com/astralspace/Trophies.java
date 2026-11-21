package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.List;

/**
 * What the seven leave behind.
 *
 * <p>One drop apiece, each the part of the thing that was doing the work: what a spirit of glass is
 * made of, what a knight was looking through, what a walker was carrying for a head. They are
 * materials and keepsakes rather than tools &mdash; nothing here has a use of its own yet, so what
 * they are for is still open.</p>
 *
 * <p>Registered on {@link AstralFloor}'s item register, so nothing new has to be wired up.</p>
 */
public final class Trophies {
    private Trophies() { }

    /** An item that carries a line of flavour under its name. */
    private static final class Keepsake extends Item {
        private final String tale;
        private final ChatFormatting tint;
        Keepsake(Properties p, String tale, ChatFormatting tint) { super(p); this.tale = tale; this.tint = tint; }
        @Override
        public void appendHoverText(ItemStack st, TooltipContext ctx, List<Component> out, TooltipFlag flag) {
            out.add(Component.literal(tale).withStyle(tint, ChatFormatting.ITALIC));
        }
    }

    private static DeferredItem<Item> keepsake(String id, String tale, ChatFormatting tint, Rarity rarity) {
        return AstralFloor.ITEMS.register(id,
                () -> new Keepsake(new Item.Properties().rarity(rarity).stacksTo(16), tale, tint));
    }

    /** The glass spirit: a pane of it, still cold, still faintly lit from inside. */
    public static final DeferredItem<Item> RIMEGLASS_SHARD = keepsake("rimeglass_shard",
            "Cold long after the rest of it melted.", ChatFormatting.AQUA, Rarity.UNCOMMON);

    /** The jellyfish that walks upright: the bell off the top of it, swollen out of all shape. */
    public static final DeferredItem<Item> MUTATED_BELL = keepsake("mutated_bell",
            "Still full of the water it drowned in.", ChatFormatting.DARK_RED, Rarity.UNCOMMON);

    /** The iron knight: what was behind the visor, and it was never a lens. */
    public static final DeferredItem<Item> DEPRAVED_EYE = keepsake("depraved_eye",
            "A man's eye, and it has seen too much to shut.", ChatFormatting.RED, Rarity.RARE);

    /** The drowned spirit: the light left over when it finally let go. */
    public static final DeferredItem<Item> DROWNED_WISP = keepsake("drowned_wisp",
            "It drifts toward water, whoever is holding it.", ChatFormatting.AQUA, Rarity.UNCOMMON);

    /** The red scholar: the book it would not put down. */
    public static final DeferredItem<Item> GILDED_FOLIO = keepsake("gilded_folio",
            "The same four lines, on every page of it.", ChatFormatting.GOLD, Rarity.RARE);

    /** The pale column: one link out of the chain that came up through the ground. */
    public static final DeferredItem<Item> SEVERED_LINK = keepsake("severed_link",
            "Cut at both ends. The rest is still down there.", ChatFormatting.WHITE, Rarity.RARE);

    /** The walker: one of the blades it stood on. */
    public static final DeferredItem<Item> STRIDER_TALON = keepsake("strider_talon",
            "It walked on these. It did not need the ground.", ChatFormatting.AQUA, Rarity.EPIC);

    /** Every drop, in the order the mobs were shown. */
    public static final List<DeferredItem<Item>> ALL = List.of(
            RIMEGLASS_SHARD, MUTATED_BELL, DEPRAVED_EYE, DROWNED_WISP,
            GILDED_FOLIO, SEVERED_LINK, STRIDER_TALON);

    /** Touched so the static fields above are initialised while the registers are still open. */
    public static void touch() { }
}
