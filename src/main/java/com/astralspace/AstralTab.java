package com.astralspace;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's own creative tab: every tarot pack (normal, rainbow, shiny) plus the party items, the smiter and the cabinet. */
public final class AstralTab {
    private AstralTab() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AstralMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.astral_world.main"))
            .icon(() -> new ItemStack(Tarot.ITEMS.getEntries().stream().filter(h -> h.getId().getPath().equals("sailor_shiny_pack")).findFirst().map(h -> (net.minecraft.world.item.Item) h.get()).orElse(PartyItems.POPPER.get())))
            .displayItems((params, out) -> {
                for (String mode : new String[]{"_pack", "_rainbow_pack", "_shiny_pack"})
                    for (String p : Tarot.PATHWAYS)
                        Tarot.ITEMS.getEntries().stream().filter(h -> h.getId().getPath().equals(p + mode)).findFirst().ifPresent(h -> out.accept(h.get()));
                out.accept(PartyItems.POPPER.get());
                out.accept(PartyItems.WAND.get());
                out.accept(AstralFloor.SMITER.get());
                out.accept(AstralFloor.CABINET_ITEM.get());
            }).build());

    /** Every tarot card, Seq 9 to 0, normal and shiny, grouped by pathway. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CARDS = TABS.register("cards", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.astral_world.cards"))
            .icon(() -> Tarot.makeCard("sailor", 0, true))
            .withTabsBefore(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "main"))
            .displayItems((params, out) -> {
                for (String p : Tarot.PATHWAYS)
                    for (int seq = 9; seq >= 0; seq--) {
                        out.accept(Tarot.makeCard(p, seq, false));
                        out.accept(Tarot.makeCard(p, seq, true));
                    }
                for (String id : Tarot.GOO_IDS) out.accept(Tarot.makeCard("goo_" + id, 0, false));
            }).build());

    public static void register(IEventBus modBus) { TABS.register(modBus); }
}
