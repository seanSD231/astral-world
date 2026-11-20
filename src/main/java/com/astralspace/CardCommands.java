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
            .then(Commands.literal("both").executes(ctx -> give(ctx, true, true)))
            .then(Commands.literal("admin").executes(CardCommands::giveAdmin))
            .then(Commands.literal("summon")
                .then(Commands.argument("card", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((c, b) -> {
                        for (String q : Tarot.PATHWAYS) b.suggest(q);
                        for (String q : Tarot.GOO_IDS) b.suggest("goo_" + q);
                        for (String q : Tarot.ADMIN_IDS) b.suggest("admin_" + q);
                        return b.buildFuture();
                    })
                    .executes(c -> summon(c, 9, false, false))
                    .then(Commands.argument("seq", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 9))
                        .executes(c -> summon(c, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "seq"), false, false))
                        .then(Commands.argument("shiny", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                            .executes(c -> summon(c, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "seq"),
                                                  com.mojang.brigadier.arguments.BoolArgumentType.getBool(c, "shiny"), false))
                            .then(Commands.argument("symbol", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .executes(c -> summon(c, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "seq"),
                                                      com.mojang.brigadier.arguments.BoolArgumentType.getBool(c, "shiny"),
                                                      com.mojang.brigadier.arguments.BoolArgumentType.getBool(c, "symbol")))))))));
    }

    /**
     * /astralcards summon &lt;card&gt; [seq] [shiny] [symbol] - stands a hologram up where you are
     * looking, with no card spent. Takes a pathway id, {@code goo_<id>} or {@code admin_<id>}.
     */
    private static int summon(CommandContext<CommandSourceStack> ctx, int seq, boolean shiny, boolean symbol) {
        ServerPlayer p;
        try { p = ctx.getSource().getPlayerOrException(); }
        catch (Exception e) { ctx.getSource().sendFailure(Component.literal("Run this as a player.")); return 0; }
        String card = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "card");

        boolean known = Tarot.gooIndex(card) >= 0 || Tarot.adminIndex(card) >= 0;
        if (!known) for (String q : Tarot.PATHWAYS) if (q.equals(card)) known = true;
        if (!known) { ctx.getSource().sendFailure(Component.literal("No card called " + card + ".")); return 0; }

        var lv = p.serverLevel();
        var hit = p.pick(12.0, 0f, false);
        net.minecraft.world.phys.Vec3 at = hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? new net.minecraft.world.phys.Vec3(
                        Math.floor(hit.getLocation().x) + .5,
                        ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos().getY() + 1.01,
                        Math.floor(hit.getLocation().z) + .5)
                : p.position();

        HoloCardEntity e = new HoloCardEntity(HoloCardEntity.TYPE.get(), lv);
        e.setCard(card, Math.max(0, Math.min(9, seq)), shiny);
        e.setOwner(p);
        e.setSymbolOnly(symbol);
        e.moveTo(at.x, at.y, at.z, p.getYRot(), 0);
        lv.addFreshEntity(e);
        final String what = card;
        ctx.getSource().sendSuccess(() -> Component.literal("Stood up " + what
                + (Tarot.gooIndex(what) >= 0 || Tarot.adminIndex(what) >= 0 ? "" : " seq " + seq) + "."), false);
        return 1;
    }

    /** /astralcards admin - one of every admin card. They come from nowhere else but an admin pack. */
    private static int giveAdmin(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        net.minecraft.server.level.ServerPlayer p;
        try { p = ctx.getSource().getPlayerOrException(); } catch (Exception e) { ctx.getSource().sendFailure(Component.literal("Run this as a player.")); return 0; }
        for (int i = 0; i < Tarot.ADMIN_IDS.length; i++) {
            ItemStack card = Tarot.makeAdminCard(i);
            if (!p.getInventory().add(card)) p.drop(card, false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + Tarot.ADMIN_IDS.length + " admin card(s)."), false);
        return 1;
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
        if (normal) {
            List<ItemStack> goo = new ArrayList<>();
            for (String id : Tarot.GOO_IDS) goo.add(Tarot.makeCard("goo_" + id, 0, false));
            ItemStack box = new ItemStack(Items.WHITE_SHULKER_BOX);
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(goo));
            box.set(DataComponents.CUSTOM_NAME, Component.literal("Great Old One cards"));
            if (!p.getInventory().add(box)) p.drop(box, false);
            boxes++;
        }
        final int n = boxes;
        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + n + " shulker boxes (one per sequence, 22 cards each, plus one of the 9 Great Old Ones)."), false);
        return 1;
    }
}
