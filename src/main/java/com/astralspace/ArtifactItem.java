package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The item side of an artifact: one item per slot, with everything that makes a given piece what it
 * is written into its data rather than into a separate item per set. Two hundred-odd items would
 * otherwise be needed for the sets alone, and every one of them would want its own model.
 */
public class ArtifactItem extends Item {
    private final Artifacts.Slot slot;

    public ArtifactItem(Artifacts.Slot slot) {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
        this.slot = slot;
    }

    public Artifacts.Slot slot() { return slot; }

    // ---- the registry ----

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AstralMod.MOD_ID);
    private static final Map<Artifacts.Slot, DeferredItem<Item>> BY_SLOT = new EnumMap<>(Artifacts.Slot.class);

    static {
        for (Artifacts.Slot s : Artifacts.Slot.values())
            BY_SLOT.put(s, ITEMS.register("artifact_" + s.id(), () -> new ArtifactItem(s)));
    }

    public static Item of(Artifacts.Slot s) { return BY_SLOT.get(s).get(); }

    public static void register(IEventBus modBus) { ITEMS.register(modBus); }

    // ---- what it says about itself ----

    @Override
    public Component getName(ItemStack st) {
        Artifacts.Piece p = Artifacts.read(st);
        if (p == null) return super.getName(st);
        ArtifactSets.Set set = ArtifactSets.get(p.set());
        String name = set == null ? slot.title : set.title();
        int c = set == null ? 0xFFFFFF : set.colour();
        return Component.literal(name).withColor(c);
    }

    @Override
    public void appendHoverText(ItemStack st, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        Artifacts.Piece p = Artifacts.read(st);
        if (p == null) {
            tip.add(Component.literal("Catalogued, and found to hold nothing.").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        ArtifactSets.Set set = ArtifactSets.get(p.set());
        int c = set == null ? 0xFFFFFF : set.colour();

        tip.add(Component.literal("Sealed Artifact Fragment \u00b7 " + Artifacts.grade(p.rarity()))
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal("   " + stars(p.rarity())).withColor(0xFFC24A)));
        tip.add(Component.literal(slot.title).withStyle(ChatFormatting.GRAY)
                .append(Component.literal("   attuned " + p.level() + " / " + p.maxLevel())
                        .withStyle(ChatFormatting.DARK_GRAY)));
        tip.add(Component.empty());
        tip.add(Component.literal(p.main().line(p.mainValue())).withColor(0xFFF0C0));
        for (Artifacts.Sub s : p.subs())
            tip.add(Component.literal(" · " + s.stat().line(s.value())
                    + (s.rolls() > 1 ? "  (" + s.rolls() + ")" : "")).withStyle(ChatFormatting.GRAY));

        if (set != null) {
            tip.add(Component.empty());
            tip.add(Component.literal(set.title()).withColor(c)
                    .append(Component.literal("   " + set.file()).withStyle(ChatFormatting.DARK_GRAY)));
            tip.add(Component.literal("  2  ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(set.twoLine()).withStyle(ChatFormatting.GRAY)));
            tip.add(Component.literal("  4  ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(set.fourLine()).withStyle(ChatFormatting.GRAY)));
            tip.add(Component.literal("     \u2013 " + set.fourCost()).withColor(0xC07070));
            String ab = SetAbilities.name(set.id(), p.rarity() - 1);
            tip.add(Component.literal("  5  ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(ab.isEmpty() ? set.fiveLine() : "Grants " + ab)
                            .withStyle(ChatFormatting.GRAY)));
            tip.add(Component.literal("     \u2013 " + set.fiveCost()).withColor(0xC07070));
            tip.add(Component.literal(set.flavour()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    private static String stars(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append('✦');
        return b.toString();
    }
}
