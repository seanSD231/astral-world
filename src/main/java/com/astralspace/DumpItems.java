package com.astralspace;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code /astralitems} &mdash; writes the item ids out of a chest into the log, ready to copy.
 *
 * <p>Run it looking at a container, or give it coordinates. Every distinct item in the chest is
 * listed once with how many there are, and then once more as a single comma-separated line, which
 * is the line you actually want when you are pasting a list into a config or a recipe.</p>
 *
 * <p>It reads anything with slots, not only chests: barrels, shulker boxes, hoppers, and any
 * modded container that is an ordinary {@link Container}. Double chests are read whole.</p>
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class DumpItems {
    private DumpItems() { }

    private static final Logger LOG = LoggerFactory.getLogger("astral-items");

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("astralitems")
            .requires(s -> s.hasPermission(2))
            .executes(c -> run(c, null))
            .then(Commands.argument("at", BlockPosArgument.blockPos())
                .executes(c -> run(c, BlockPosArgument.getLoadedBlockPos(c, "at")))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, BlockPos given) {
        ServerPlayer p;
        try { p = ctx.getSource().getPlayerOrException(); }
        catch (Exception ex) { ctx.getSource().sendFailure(Component.literal("Run this as a player.")); return 0; }

        ServerLevel lv = p.serverLevel();
        BlockPos pos = given;
        if (pos == null) {                                   // whatever they are looking at, up to 8 blocks
            HitResult hit = p.pick(8.0, 0f, false);
            if (hit.getType() != HitResult.Type.BLOCK) {
                ctx.getSource().sendFailure(Component.literal("Look at a chest, or give me coordinates."));
                return 0;
            }
            pos = ((BlockHitResult) hit).getBlockPos();
        }

        Container box = containerAt(lv, pos);
        if (box == null) {
            ctx.getSource().sendFailure(Component.literal("Nothing at " + pos.toShortString() + " holds items."));
            return 0;
        }

        // counted in the order they appear, so the list comes out the way the chest looks
        Map<String, Integer> counts = new LinkedHashMap<>();
        int stacks = 0, total = 0;
        for (int i = 0; i < box.getContainerSize(); i++) {
            ItemStack st = box.getItem(i);
            if (st.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
            counts.merge(id, st.getCount(), Integer::sum);
            stacks++;
            total += st.getCount();
        }

        if (counts.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("That one is empty."));
            return 0;
        }

        String where = lv.dimension().location() + " " + pos.toShortString();
        LOG.info("---- {} : {} kinds, {} stacks, {} items ----", where, counts.size(), stacks, total);
        for (var en : counts.entrySet()) LOG.info("{} x{}", en.getKey(), en.getValue());
        String flat = String.join(", ", counts.keySet());
        LOG.info("ids: {}", flat);
        LOG.info("---- end {} ----", where);

        // and in chat: click it to put the whole list on the clipboard without leaving the game
        ctx.getSource().sendSuccess(() -> Component.literal(
                counts.size() + " kinds, " + total + " items — written to the log. ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal("[copy the ids]")
                    .withStyle(st -> st.withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, flat))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Copies all " + counts.size() + " ids, comma separated."))))),
                false);
        return counts.size();
    }

    /** The container at a position, both halves of it if it is a double chest. */
    private static Container containerAt(ServerLevel lv, BlockPos pos) {
        BlockState st = lv.getBlockState(pos);
        if (st.getBlock() instanceof ChestBlock chest) {
            Container both = ChestBlock.getContainer(chest, st, lv, pos, true);
            if (both != null) return both;
        }
        BlockEntity be = lv.getBlockEntity(pos);
        return be instanceof Container c ? c : null;
    }
}
