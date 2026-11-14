package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;

import java.util.LinkedHashSet;

/**
 * What a rank is handed: the Supporter+ credit card, the VIP portable bank terminal, and a title.
 * Each is given once and the record is kept in {@link AstralData}, so logging in again does not hand out a second one
 * and dying does not wipe the record (player persistent data does not survive a respawn). Both items are marked
 * astral_keep, which {@link CoinBridge} already honours, so they are not dropped on death.
 *
 * This is checked on login AND every 20 seconds for everyone online, because a rank node set while a player is
 * already connected used to do nothing at all until they relogged, with no way to force it.
 *
 * The nodes (see {@link Ranks}):
 *   astral_world.perks.credit_card     numismatics:yellow_card, named "Supporter+ Credit Card"
 *   astral_world.perks.bank_terminal   numismaticsutils:portable_bank_terminal, named "VIP Portable Bank Terminal"
 *   astral_world.perks.title           a title id, granted with /sol titlesystem grant &lt;player&gt; &lt;id&gt;
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class RankPerks {
    private RankPerks() {}
    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String GOT = "astral_rank_perks";   // the pre-0.2.243 record, read once to migrate

    @SubscribeEvent
    public static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) UniqAbilities.later(40, () -> give(p));   // a moment, so FTB Ranks has the player
    }

    /** Every 20 seconds: a rank given to someone already online takes effect without them having to relog. */
    @SubscribeEvent
    public static void onTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 400 != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) give(p);
    }

    /** Hands over anything this player's rank gets and has not had yet. Returns how many perks were handed over now. */
    public static int give(ServerPlayer p) {
        if (!p.isAlive() || p.getServer() == null) return 0;
        AstralData d = AstralData.get(p.getServer());
        String old = d.perksGiven.get(p.getUUID());
        LinkedHashSet<String> got = new LinkedHashSet<>();
        if (old != null) {
            for (String s : old.split(",")) if (!s.isBlank()) got.add(s);
        } else {                                                    // migrate the record kept on the player before 0.2.243
            CompoundTag t = p.getPersistentData().getCompound(GOT);
            if (t.getBoolean("card")) got.add("card");
            if (t.getBoolean("terminal")) got.add("terminal");
            if (t.getInt("title") > 0) got.add("title=" + t.getInt("title"));
        }

        int n = 0;
        if (Ranks.flag(p, "astral_world.perks.credit_card") && !got.contains("card")
            && item(p, "numismatics:yellow_card", "Supporter+ Credit Card", ChatFormatting.GOLD)) { got.add("card"); n++; }
        if (Ranks.flag(p, "astral_world.perks.bank_terminal") && !got.contains("terminal")
            && item(p, "numismaticsutils:portable_bank_terminal", "VIP Portable Bank Terminal", ChatFormatting.LIGHT_PURPLE)) { got.add("terminal"); n++; }
        int title = Ranks.integer(p, "astral_world.perks.title", 0);
        if (title > 0 && !got.contains("title=" + title) && grantTitle(p, title)) {
            got.removeIf(s -> s.startsWith("title="));
            got.add("title=" + title); n++;
        }

        String now = String.join(",", got);
        if (!now.equals(old)) { d.perksGiven.put(p.getUUID(), now); d.setDirty(); }
        if (n > 0) LOGGER.info("[astral] rank perks: gave {} {} new perk(s); now holds [{}]", p.getGameProfile().getName(), n, now);
        return n;
    }

    /** Gives one named, keep-on-death item. False if that mod is not installed, so it is tried again later. */
    private static boolean item(ServerPlayer p, String id, String name, ChatFormatting colour) {
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (it == null || it == net.minecraft.world.item.Items.AIR) {
            LOGGER.warn("[astral] rank perks: {} is not a registered item, so {} cannot have their {}", id, p.getGameProfile().getName(), name);
            return false;
        }
        ItemStack st = new ItemStack(it);
        st.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withColor(colour).withItalic(false)));
        CompoundTag tag = new CompoundTag(); tag.putByte("astral_keep", (byte) 1);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        if (!p.getInventory().add(st)) p.drop(st, false);
        p.sendSystemMessage(Component.literal("Your rank's " + name + " is yours.").withStyle(colour));
        return true;
    }

    /** Runs the title mod's own grant command. False when it is not installed, so it is tried again later. */
    private static boolean grantTitle(ServerPlayer p, int id) {
        var server = p.getServer();
        if (server == null) return false;
        try {
            if (server.getCommands().getDispatcher().findNode(java.util.List.of("sol")) == null) {
                LOGGER.warn("[astral] rank perks: no /sol command, so title {} cannot be granted to {}", id, p.getGameProfile().getName());
                return false;
            }
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withPermission(4),
                "sol titlesystem grant " + p.getGameProfile().getName() + " " + id);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[astral] rank perks: granting title {} to {} failed: {}", id, p.getGameProfile().getName(), t.toString());
            return false;
        }
    }

    // ---- /astralperks [player] runs the check now; /astralperks reset <player> forgets what they were given.
    @SubscribeEvent
    public static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("astralperks").requires(c -> c.hasPermission(2))
            .executes(c -> run(c.getSource(), c.getSource().getPlayerOrException()))
            .then(Commands.literal("all").executes(c -> {
                int n = 0;
                for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) n += give(p);
                final int total = n;
                c.getSource().sendSuccess(() -> Component.literal("Handed out " + total + " perk(s) across everyone online."), true);
                return 1;
            }))
            .then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                ServerPlayer t = EntityArgument.getPlayer(c, "player");
                AstralData d = AstralData.get(t.getServer());
                d.perksGiven.remove(t.getUUID()); d.setDirty();
                t.getPersistentData().remove(GOT);
                c.getSource().sendSuccess(() -> Component.literal(t.getGameProfile().getName() + " may be given their rank perks again."), true);
                return 1;
            })))
            .then(Commands.argument("player", EntityArgument.player())
                .executes(c -> run(c.getSource(), EntityArgument.getPlayer(c, "player")))));
    }

    private static int run(net.minecraft.commands.CommandSourceStack src, ServerPlayer who) {
        int n = give(who);
        String has = AstralData.get(who.getServer()).perksGiven.getOrDefault(who.getUUID(), "");
        src.sendSuccess(() -> Component.literal(who.getGameProfile().getName() + ": " + n + " perk(s) handed over now; holds ["
            + (has.isEmpty() ? "nothing" : has) + "]").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }
}
