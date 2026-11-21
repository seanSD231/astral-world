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

    /** The jellyfish that walks upright: the bell off the top of it, glass-clear and still cold. */
    public static final DeferredItem<Item> MEDUSA_BELL = keepsake("medusa_bell",
            "Still full of the water it drifted in.", ChatFormatting.AQUA, Rarity.UNCOMMON);

    /** The red flesh thing: the one part of it that was ever properly alive, and it grew. */
    public static final DeferredItem<Item> MUTATED_HEART = keepsake("mutated_heart",
            "It has more chambers than it ought to.", ChatFormatting.DARK_RED, Rarity.UNCOMMON);

    /** The iron knight: what was behind the visor, and it was never a lens. */
    public static final DeferredItem<Item> DEPRAVED_EYE = keepsake("depraved_eye",
            "A man's eye, and it has seen too much to shut.", ChatFormatting.RED, Rarity.RARE);

    /** The drowned spirit: the light left over when it finally let go. */
    public static final DeferredItem<Item> DROWNED_WISP = keepsake("drowned_wisp",
            "It drifts toward water, whoever is holding it.", ChatFormatting.AQUA, Rarity.UNCOMMON);

    /** The red scholar: not the book, the cloth, with soul still running in the weave. */
    public static final DeferredItem<Item> SPIRITUAL_CLOTH = keepsake("spiritual_cloth",
            "Warm, and it was never worn by anything living.", ChatFormatting.AQUA, Rarity.RARE);

    /** The skull lord: the skull itself, and the light still in its sockets. */
    public static final DeferredItem<Item> SOVEREIGN_SKULL = keepsake("sovereign_skull",
            "The light in it has not gone out, and will not.", ChatFormatting.BLUE, Rarity.RARE);

    /** The walking knight: what was left of it, and it is not done. */
    public static final DeferredItem<Item> VENGEFUL_SPIRIT = keepsake("vengeful_spirit",
            "It kept the helm. It did not keep the man.", ChatFormatting.AQUA, Rarity.EPIC);

    /** Every drop, in the order the mobs were shown. */
    public static final List<DeferredItem<Item>> ALL = List.of(
            MEDUSA_BELL, MUTATED_HEART, DEPRAVED_EYE, DROWNED_WISP,
            SPIRITUAL_CLOTH, SOVEREIGN_SKULL, VENGEFUL_SPIRIT);

    /** Touched so the static fields above are initialised while the registers are still open. */
    public static void touch() { }
}
