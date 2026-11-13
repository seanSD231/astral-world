package com.astralspace;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.LocalDate;

/**
 * Weekly tarot packs. A player opens a menu (/weeklypacks) and picks their packs for the week. How many, and whether they may take
 * Rainbow or Shiny packs, comes from their FTB Ranks rank through these permission nodes (set them in the rank's config):
 *   astral_world.weekly_packs          number of packs per week (a whole number)
 *   astral_world.weekly_packs.rainbow  how many of those may be Rainbow packs (a number), or true = any of them
 *   astral_world.weekly_packs.shiny    how many of those may be Shiny packs (a number), or true = any of them
 * Players with no such node get weekly_packs_default from settings.properties and no Rainbow or Shiny packs.
 * The week resets on weekly_reset_day (1 = Monday ... 7 = Sunday), server time.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class WeeklyPacks {
    public static final String NODE = "astral_world.weekly_packs";
    private static final String TAG = "astral_weekly_claim";
    private WeeklyPacks() {}

    /** The first day (epoch day) of the current week. */
    static long weekStart() {
        long day = LocalDate.now().toEpochDay();
        int weekday = (int) (((day + 3) % 7 + 7) % 7) + 1;           // 1 = Monday (epoch day 0 was a Thursday)
        int since = ((weekday - Settings.weeklyResetDay) % 7 + 7) % 7;
        return day - since;
    }

    // ---- the rank's numbers ({@link Ranks} does the reflection; without FTB Ranks everyone gets the default)
    static int total(ServerPlayer p) { return Math.max(0, Ranks.integer(p, NODE, Settings.weeklyPacksDefault)); }
    static int[] caps(ServerPlayer p, int total) { return new int[]{total, Ranks.cap(p, NODE + ".rainbow", total), Ranks.cap(p, NODE + ".shiny", total)}; }
    static boolean claimed(ServerPlayer p) { return p.getPersistentData().contains(TAG) && p.getPersistentData().getLong(TAG) == weekStart(); }

    // ---- open the menu / claim
    static void open(ServerPlayer p) {
        int total = total(p);
        if (total <= 0) { p.sendSystemMessage(Component.literal("Your rank does not get any weekly tarot packs.").withStyle(ChatFormatting.GRAY)); return; }
        int[] c = caps(p, total);
        PacketDistributor.sendToPlayer(p, new Net.WeeklyOpenPayload(total, c[1], c[2], claimed(p)));
    }

    /** picks = "pathway:type:count,..." with type 0 = normal, 1 = rainbow, 2 = shiny. */
    static void claim(ServerPlayer p, String picks) {
        if (claimed(p)) { p.sendSystemMessage(Component.literal("You already took your weekly packs.").withStyle(ChatFormatting.RED)); return; }
        int total = total(p); int[] caps = caps(p, total);
        int[] byType = new int[3]; int sum = 0;
        java.util.List<ItemStack> give = new java.util.ArrayList<>();
        try {
            for (String s : picks.split(",")) {
                if (s.isBlank()) continue;
                String[] x = s.split(":");
                String path = x[0]; int type = Integer.parseInt(x[1]); int n = Integer.parseInt(x[2]);
                if (n <= 0) continue;
                if (type < 0 || type > 2 || !java.util.Arrays.asList(Tarot.PATHWAYS).contains(path)) throw new IllegalArgumentException();
                byType[type] += n; sum += n;
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, path + (type == 1 ? "_rainbow_pack" : type == 2 ? "_shiny_pack" : "_pack")));
                for (int left = n; left > 0; ) { int k = Math.min(left, 16); give.add(new ItemStack(item, k)); left -= k; }
            }
        } catch (RuntimeException e) { p.sendSystemMessage(Component.literal("That pick was not valid.").withStyle(ChatFormatting.RED)); return; }
        if (sum != total || byType[1] > caps[1] || byType[2] > caps[2]) { p.sendSystemMessage(Component.literal("That pick is not allowed for your rank.").withStyle(ChatFormatting.RED)); return; }
        for (ItemStack st : give) if (!p.getInventory().add(st)) p.drop(st, false);
        p.getPersistentData().putLong(TAG, weekStart());
        p.sendSystemMessage(Component.literal("You took your " + total + " weekly tarot pack" + (total == 1 ? "" : "s") + ". The next ones are ready when the week resets.").withStyle(ChatFormatting.GOLD));
    }

    /** Tell a player who logs in that they still have their weekly packs to pick (click the message to open the menu). */
    @SubscribeEvent
    public static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        UniqAbilities.later(45, () -> {           // a moment, so FTB Ranks has the player and their rank
            int n = total(p);
            if (n <= 0 || claimed(p) || !p.isAlive()) return;
            p.sendSystemMessage(Component.literal("You have " + n + " weekly tarot pack" + (n == 1 ? "" : "s") + " to pick. ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("[Click here or use /weeklypacks]").withStyle(s -> s.withColor(ChatFormatting.YELLOW).withUnderlined(true)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/weeklypacks")))));
        });
    }

    private static int report(net.minecraft.commands.CommandSourceStack src, ServerPlayer who) {
        src.sendSuccess(() -> Component.literal("FTB Ranks: " + (Ranks.present() ? "found" : "NOT found, so every rank perk is off"))
            .withStyle(Ranks.present() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        for (String n : ALL_NODES) {
            String line = "  " + n + "  ->  " + Ranks.describe(who, n);
            src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        int t = total(who); int[] c = caps(who, t);
        src.sendSuccess(() -> Component.literal(who.getGameProfile().getName() + ": " + t + " packs (" + c[1] + " rainbow, " + c[2] + " shiny), "
            + (claimed(who) ? "already taken this week" : "not taken yet") + "; fun items every "
            + FunItems.period(who) + " day(s), " + FunItems.waitLeft(who) + " to wait").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    /** Every node this mod reads, for the /astralranks report. */
    private static final String[] ALL_NODES = {NODE, NODE + ".rainbow", NODE + ".shiny", "astral_world.fun_items",
        "astral_world.cmd.enchanting", "astral_world.cmd.anvil", "astral_world.cmd.enderchest",
        "astral_world.perks.credit_card", "astral_world.perks.bank_terminal", "astral_world.perks.title"};

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        // /astralranks [player]: what this server actually reads out of FTB Ranks for that player, node by node.
        event.getDispatcher().register(Commands.literal("astralranks").requires(c -> c.hasPermission(2))
            .executes(c -> report(c.getSource(), c.getSource().getPlayerOrException()))
            .then(Commands.argument("player", EntityArgument.player())
                .executes(c -> report(c.getSource(), EntityArgument.getPlayer(c, "player")))));
        for (String name : new String[]{"weeklypacks", "packs"}) {
            event.getDispatcher().register(Commands.literal(name).requires(c -> c.getEntity() instanceof ServerPlayer)
                .executes(c -> { open((ServerPlayer) c.getSource().getEntity()); return 1; })
                .then(Commands.literal("reset").requires(c -> c.hasPermission(2))
                    .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                        ServerPlayer t = EntityArgument.getPlayer(c, "player");
                        t.getPersistentData().remove(TAG);
                        c.getSource().sendSuccess(() -> Component.literal(t.getGameProfile().getName() + " can take their weekly packs again."), true);
                        return 1;
                    }))));
        }
    }
}
