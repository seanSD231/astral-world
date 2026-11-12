package com.astralspace;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/** /enchanting and /anvil (ops): open the screens anywhere. The enchanting screen always offers levels 10 / 20 / 30. */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class GuiCommands {
    private GuiCommands() {}

    /** Marker position far below the world, so the enchanting event can tell these screens from a real table. */
    private static final int MARKER_Y = -20000;

    private static final class Access implements ContainerLevelAccess {
        private final Level level;
        Access(Level level) { this.level = level; }
        @Override public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> fn) { return Optional.empty(); } // always still valid
        @Override public void execute(BiConsumer<Level, BlockPos> consumer) { consumer.accept(level, new BlockPos(0, MARKER_Y, 0)); }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("enchanting").requires(c -> allowed(c, "enchanting"))
            .executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new EnchantmentMenu(id, inv, new Access(p.level())), Component.translatable("container.enchant")));
                return 1;
            }));
        event.getDispatcher().register(Commands.literal("anvil").requires(c -> allowed(c, "anvil"))
            .executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new AnvilMenu(id, inv, new Access(p.level())), Component.translatable("container.repair")));
                return 1;
            }));
    }

    @SubscribeEvent
    public static void onEnchantLevel(EnchantmentLevelSetEvent event) {
        if (event.getPos().getY() != MARKER_Y) return;
        int row = event.getEnchantRow();
        event.setEnchantLevel(row == 0 ? 10 : row == 1 ? 20 : 30);
        event.getDispatcher().register(Commands.literal("enderchest").requires(c -> allowed(c, "enderchest"))
            .executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                p.openMenu(new SimpleMenuProvider((id, inv, pl) -> net.minecraft.world.inventory.ChestMenu.threeRows(id, inv, p.getEnderChestInventory()),
                    Component.translatable("container.enderchest")));
                return 1;
            }));
    }

    /** Ops always may; everyone else needs the rank node astral_world.cmd.&lt;name&gt;. */
    private static boolean allowed(net.minecraft.commands.CommandSourceStack c, String name) {
        if (!(c.getEntity() instanceof ServerPlayer p)) return false;
        return c.hasPermission(2) || Ranks.flag(p, "astral_world.cmd." + name);
    }
}