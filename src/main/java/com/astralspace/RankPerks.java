package com.astralspace;

import net.minecraft.ChatFormatting;
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

/**
 * What a rank is handed the moment the server sees it: the Supporter+ credit card, the VIP portable bank terminal, and a title.
 * Each is given once and remembered on the player, so logging in again does not hand out a second one. Both items are marked
 * astral_keep, which {@link CoinBridge} already honours, so they are not dropped on death.
 *
 * The nodes (see {@link Ranks}):
 *   astral_world.perks.credit_card     numismatics:yellow_card, named "Supporter+ Credit Card"
 *   astral_world.perks.bank_terminal   numismaticsutils:portable_bank_terminal, named "VIP Portable Bank Terminal"
 *   astral_world.perks.title           a title id, granted with /sol titlesystem grant &lt;player&gt; &lt;id&gt;
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class RankPerks {
    private RankPerks() {}
    private static final String GOT = "astral_rank_perks";   // which perks this player has already been handed

    @SubscribeEvent
    public static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) UniqAbilities.later(40, () -> give(p));   // a moment, so FTB Ranks has the player
    }

    /** Hands over anything this player's rank gets and has not had yet. Safe to call again at any time. */
    public static void give(ServerPlayer p) {
        if (!p.isAlive()) return;
        CompoundTag got = p.getPersistentData().getCompound(GOT);
        if (Ranks.flag(p, "astral_world.perks.credit_card") && !got.getBoolean("card"))
            if (item(p, "numismatics:yellow_card", "Supporter+ Credit Card", ChatFormatting.GOLD)) got.putBoolean("card", true);
        if (Ranks.flag(p, "astral_world.perks.bank_terminal") && !got.getBoolean("terminal"))
            if (item(p, "numismaticsutils:portable_bank_terminal", "VIP Portable Bank Terminal", ChatFormatting.LIGHT_PURPLE)) got.putBoolean("terminal", true);
        int title = Ranks.integer(p, "astral_world.perks.title", 0);
        if (title > 0 && got.getInt("title") != title && grantTitle(p, title)) got.putInt("title", title);
        p.getPersistentData().put(GOT, got);
    }

    /** Gives one named, keep-on-death item. False if that mod is not installed, so it is tried again next time. */
    private static boolean item(ServerPlayer p, String id, String name, ChatFormatting colour) {
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (it == null || it == net.minecraft.world.item.Items.AIR) return false;
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
            if (server.getCommands().getDispatcher().findNode(java.util.List.of("sol")) == null) return false;
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withPermission(4),
                "sol titlesystem grant " + p.getGameProfile().getName() + " " + id);
            return true;
        } catch (Throwable t) { return false; }
    }
}
