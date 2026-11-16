package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Keeps SolsTitleSystem's combat statistics honest, and gives ops a way to put them back.
 *
 * SolsTitleSystem records a "highest hit damage" per player and hands out titles for it. LOTM has abilities that deal
 * the integer limit of damage to the player themselves, and that counts: the stat goes to 2,147,483,647, the top
 * damage title unlocks, and there is no way in that mod to undo it, because its data has no reset and the title
 * re-unlocks from the stat.
 *
 * So: every few seconds any highest-hit above {@code title_max_hit_damage} is thrown away (set to 0) and logged, which
 * stops the bug ever landing a title; and {@code /astralstats} shows and resets the figures for players it already hit.
 *
 * All of it is reflection into com.solmochi.titlesystem, so the mod stays optional and a version that renames things
 * simply turns this off instead of breaking.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class TitleStats {
    private TitleStats() {}
    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static boolean looked = false;
    private static Method getData, markDirty, revokeTitle, getEquipped, setEquipped, getVanity, setVanity;
    private static Field fHighest, fDealt, fTaken;

    /** Finds SolsTitleSystem once. False when it is not installed, or its data no longer looks like this. */
    private static boolean ready() {
        if (looked) return getData != null;
        looked = true;
        try {
            Class<?> cap = Class.forName("com.solmochi.titlesystem.capability.TitleCapability");
            Class<?> data = Class.forName("com.solmochi.titlesystem.capability.PlayerTitleData");
            getData = cap.getMethod("getData", net.minecraft.world.entity.player.Player.class);
            markDirty = data.getMethod("markDirty");
            revokeTitle = data.getMethod("revokeTitle", int.class);
            getEquipped = data.getMethod("getEquippedTitleId");
            setEquipped = data.getMethod("setEquippedTitleId", int.class);
            getVanity = data.getMethod("getVanityTitleId");
            setVanity = data.getMethod("setVanityTitleId", int.class);
            fHighest = data.getDeclaredField("highestHitDamage"); fHighest.setAccessible(true);
            fDealt = data.getDeclaredField("totalDamageDealt");   fDealt.setAccessible(true);
            fTaken = data.getDeclaredField("totalDamageTaken");   fTaken.setAccessible(true);
            LOGGER.info("[astral] SolsTitleSystem found, watching highest-hit damage (cap {})", Settings.titleMaxHit);
            return true;
        } catch (ClassNotFoundException e) {
            return false;                                  // the mod is not installed: nothing to do
        } catch (Throwable t) {
            LOGGER.warn("[astral] SolsTitleSystem is installed but its title data does not look as expected, so damage stats are left alone: {}", t.toString());
            getData = null;
            return false;
        }
    }

    private static Object data(ServerPlayer p) {
        if (!ready()) return null;
        try { return getData.invoke(null, p); } catch (Throwable t) { return null; }
    }

    private static void dirty(Object d) { try { markDirty.invoke(d); } catch (Throwable ignored) { } }

    /** Every 5 seconds: anything above the cap never happened. */
    @SubscribeEvent
    public static void onTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        if (Settings.titleMaxHit <= 0 || e.getServer().getTickCount() % 100 != 0 || !ready()) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            Object d = data(p);
            if (d == null) continue;
            try {
                int hit = fHighest.getInt(d);
                if (hit <= Settings.titleMaxHit) continue;
                fHighest.setInt(d, 0);
                dirty(d);
                LOGGER.warn("[astral] {}'s highest hit was {}, over the {} cap, so it was thrown away. A damage title already"
                    + " unlocked from it has to be revoked by hand: /sol titlesystem revoke {} <id>",
                    p.getGameProfile().getName(), hit, Settings.titleMaxHit, p.getGameProfile().getName());
                p.sendSystemMessage(Component.literal("A damage figure of " + hit + " was recorded against you and thrown away:"
                    + " it is past anything the server counts as a real hit.").withStyle(ChatFormatting.GRAY));
            } catch (Throwable ignored) { }
        }
    }

    // ---------------------------------------------------------- put aside and give back, for the hunger games

    /**
     * Everything the title mod holds that an arena should not touch: the titles worn, and the combat figures titles
     * are given for. Returns {equipped, vanity, highest hit, damage dealt, damage taken}, or null when the mod is
     * not installed. Fights inside a hunger games are not the server's combat record.
     */
    public static long[] setAside(ServerPlayer p) {
        Object d = data(p);
        if (d == null) return null;
        try {
            long[] was = {(int) getEquipped.invoke(d), (int) getVanity.invoke(d), fHighest.getInt(d), fDealt.getLong(d), fTaken.getLong(d)};
            setEquipped.invoke(d, 0);
            setVanity.invoke(d, 0);
            dirty(d);
            return was;
        } catch (Throwable t) { return null; }
    }

    /** Puts back what {@link #setAside} took, including the combat figures, so the arena leaves no mark. */
    public static void giveBack(ServerPlayer p, long[] was) {
        Object d = data(p);
        if (d == null || was == null || was.length < 5) return;
        try {
            setEquipped.invoke(d, (int) was[0]);
            setVanity.invoke(d, (int) was[1]);
            fHighest.setInt(d, (int) was[2]);
            fDealt.setLong(d, was[3]);
            fTaken.setLong(d, was[4]);
            dirty(d);
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ /astralstats

    @SubscribeEvent
    public static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("astralstats").requires(c -> c.hasPermission(2))
            .then(Commands.argument("player", EntityArgument.player()).executes(c -> show(c.getSource(), EntityArgument.getPlayer(c, "player"))))
            .then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player())
                .executes(c -> wipe(c.getSource(), EntityArgument.getPlayer(c, "player"), true, true))
                .then(Commands.literal("hit").executes(c -> wipe(c.getSource(), EntityArgument.getPlayer(c, "player"), true, false)))
                .then(Commands.literal("all").executes(c -> wipe(c.getSource(), EntityArgument.getPlayer(c, "player"), true, true)))))
            .then(Commands.literal("revoke").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("title", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                    .executes(c -> revoke(c.getSource(), EntityArgument.getPlayer(c, "player"),
                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "title")))))));
    }

    private static int missing(CommandSourceStack src) {
        src.sendFailure(Component.literal("SolsTitleSystem is not installed, or its title data is not the shape this mod knows."));
        return 0;
    }

    private static int show(CommandSourceStack src, ServerPlayer who) {
        Object d = data(who);
        if (d == null) return missing(src);
        try {
            src.sendSuccess(() -> Component.literal(who.getGameProfile().getName() + ": highest hit " + fHighestOf(d)
                + ", dealt " + fLongOf(fDealt, d) + ", taken " + fLongOf(fTaken, d)
                + "  (cap " + Settings.titleMaxHit + ")").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        } catch (Throwable t) { return missing(src); }
    }

    private static int fHighestOf(Object d) { try { return fHighest.getInt(d); } catch (Throwable t) { return -1; } }
    private static long fLongOf(Field f, Object d) { try { return f.getLong(d); } catch (Throwable t) { return -1; } }

    private static int wipe(CommandSourceStack src, ServerPlayer who, boolean hit, boolean totals) {
        Object d = data(who);
        if (d == null) return missing(src);
        try {
            if (hit) fHighest.setInt(d, 0);
            if (totals) { fDealt.setLong(d, 0L); fTaken.setLong(d, 0L); }
            dirty(d);
            src.sendSuccess(() -> Component.literal(who.getGameProfile().getName() + ": " + (totals ? "combat statistics" : "highest hit")
                + " reset to zero. A title already unlocked from them stays until it is revoked:"
                + " /astralstats revoke " + who.getGameProfile().getName() + " <title id>").withStyle(ChatFormatting.YELLOW), true);
            return 1;
        } catch (Throwable t) { return missing(src); }
    }

    private static int revoke(CommandSourceStack src, ServerPlayer who, int title) {
        Object d = data(who);
        if (d == null) return missing(src);
        try {
            revokeTitle.invoke(d, title);
            dirty(d);
            src.sendSuccess(() -> Component.literal("Title " + title + " taken from " + who.getGameProfile().getName()
                + ". Reset their statistics too, or it unlocks again.").withStyle(ChatFormatting.YELLOW), true);
            return 1;
        } catch (Throwable t) { return missing(src); }
    }
}
