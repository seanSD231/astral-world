package com.astralspace;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Testing command: /astralcards [normal|shiny|both] gives shulker boxes holding the Seq 4, 2, 1 and 0 card of every pathway
 * (22 cards per box, one box per sequence, so they all fit). Shiny boxes are purple, normal boxes are the default colour.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class CardCommands {
    private CardCommands() {}
    private static final int[] SEQS = {4, 2, 1, 0};

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("astralcards").requires(c -> c.hasPermission(2))
            .executes(ctx -> give(ctx, true, true))
            .then(Commands.literal("normal").executes(ctx -> give(ctx, true, false)))
            .then(Commands.literal("shiny").executes(ctx -> give(ctx, false, true)))
            .then(Commands.literal("both").executes(ctx -> give(ctx, true, true))));
    }

    private static int give(CommandContext<CommandSourceStack> ctx, boolean normal, boolean shiny) {
        ServerPlayer p;
        try { p = ctx.getSource().getPlayerOrException(); } catch (Exception e) { ctx.getSource().sendFailure(Component.literal("Run this as a player.")); return 0; }
        int boxes = 0;
        for (boolean sh : new boolean[]{false, true}) {
            if (sh ? !shiny : !normal) continue;
            for (int seq : SEQS) {
                List<ItemStack> cards = new ArrayList<>();
                for (String path : Tarot.PATHWAYS) cards.add(Tarot.makeCard(path, seq, sh));
                ItemStack box = new ItemStack(sh ? Items.PURPLE_SHULKER_BOX : Items.SHULKER_BOX);
                box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(cards));
                box.set(DataComponents.CUSTOM_NAME, Component.literal((sh ? "Shiny " : "") + "Tarot cards Seq " + seq));
                if (!p.getInventory().add(box)) p.drop(box, false);
                boxes++;
            }
        }
        final int n = boxes;
        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + n + " shulker boxes (one per sequence, 22 cards each)."), false);
        return 1;
    }
}
