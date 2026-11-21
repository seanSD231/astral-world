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

    /** The red flesh thing: the one part of it that was ever properly alive. */
    public static final DeferredItem<Item> CLOTTED_HEART = keepsake("clotted_heart",
            "It beats when nothing is watching it.", ChatFormatting.DARK_RED, Rarity.UNCOMMON);

    /** The iron knight: the lens its one eye burned behind. */
    public static final DeferredItem<Item> SENTINEL_EYE = keepsake("sentinel_eye",
            "Still warm, and still pointed at you.", ChatFormatting.RED, Rarity.RARE);

    /** The drowned spider: a skein of the thread it hung itself from. */
    public static final DeferredItem<Item> SPECTRAL_SILK = keepsake("spectral_silk",
            "Weighs nothing. Holds anything.", ChatFormatting.AQUA, Rarity.UNCOMMON);

    /** The red scholar: one leaf out of the book it would not put down. */
    public static final DeferredItem<Item> GILDED_FOLIO = keepsake("gilded_folio",
            "The same four lines, on every page of it.", ChatFormatting.GOLD, Rarity.RARE);

    /** The pale spire: one bone out of the column that came up through the ground. */
    public static final DeferredItem<Item> PALE_VERTEBRA = keepsake("pale_vertebra",
            "Part of something far longer, still down there.", ChatFormatting.WHITE, Rarity.RARE);

    /** The walking lantern: the light it carried where a head should be. */
    public static final DeferredItem<Item> WALKERS_LANTERN = keepsake("walkers_lantern",
            "It was looking for something. It has not stopped.", ChatFormatting.AQUA, Rarity.EPIC);

    /** Every drop, in the order the mobs were shown. */
    public static final List<DeferredItem<Item>> ALL = List.of(
            RIMEGLASS_SHARD, CLOTTED_HEART, SENTINEL_EYE, SPECTRAL_SILK,
            GILDED_FOLIO, PALE_VERTEBRA, WALKERS_LANTERN);

    /** Touched so the static fields above are initialised while the registers are still open. */
    public static void touch() { }
}
