package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.List;

/**
 * The card binder: one slot per card in the collection, and a card only goes in its own slot.
 *
 * 22 pathways of 10 sequences plus the 9 Great Old Ones is 229 faces, doubled by shinies to 458. Holding that as 458
 * ItemStacks would be a heavy thing to carry in an item's data and a slow thing to send, and every card of one face is
 * identical anyway, so the binder stores a count per face instead: a compound of key to count, where a key is
 * {@code <pathway>_<seq>} with an {@code s} on the end for the shiny. A card is rebuilt from its key on the way out.
 *
 * Storage only, by design: nothing is recorded once a card leaves, so an empty slot means an empty slot.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class Binder {
    private Binder() {}

    public static final DeferredItem<Item> BINDER = Tarot.ITEMS.register("card_binder", () -> new BinderItem(new Item.Properties().stacksTo(1)));

    /** Loads this class so its item registers into Tarot's register. Called before that register is hooked to the bus. */
    public static void init() { }

    /** Every face in the collection, in page order: each Great Old One, then its pathways, each sequence 9 down to 0. */
    public static int faces() { return Tarot.PATHWAYS.length * 10 + Tarot.GOO_IDS.length; }

    public static String key(String pathway, int seq, boolean shiny) { return pathway + "_" + seq + (shiny ? "s" : ""); }

    /** Splits a key back into {pathway, seq, shiny}. */
    public static Object[] unkey(String k) {
        boolean shiny = k.endsWith("s");
        String body = shiny ? k.substring(0, k.length() - 1) : k;
        int cut = body.lastIndexOf('_');
        return new Object[]{body.substring(0, cut), Integer.parseInt(body.substring(cut + 1)), shiny};
    }

    private static CompoundTag cards(ItemStack binder) {
        return binder.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("cards");
    }

    private static void cards(ItemStack binder, CompoundTag c) {
        CustomData.update(DataComponents.CUSTOM_DATA, binder, t -> t.put("cards", c));
    }

    public static int count(ItemStack binder, String k) { return cards(binder).getInt(k); }

    /** How many of the 458 faces this binder holds at least one of. */
    public static int filled(ItemStack binder) {
        CompoundTag c = cards(binder);
        int n = 0;
        for (String k : c.getAllKeys()) if (c.getInt(k) > 0) n++;
        return n;
    }

    /** The whole binder as "key:count,key:count", for sending to the screen. */
    public static String pack(ItemStack binder) {
        CompoundTag c = cards(binder);
        StringBuilder sb = new StringBuilder();
        for (String k : c.getAllKeys()) {
            int n = c.getInt(k);
            if (n <= 0) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(k).append(':').append(n);
        }
        return sb.toString();
    }

    /** True if that stack is a tarot card whose face belongs in a binder (anything with a pathway on it). */
    private static String faceOf(ItemStack s) {
        if (!s.is(Tarot.CARD.get())) return null;
        CompoundTag t = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String pw = t.getString("pathway");
        if (pw.isEmpty()) return null;                       // the plain test card has no face of its own
        if (t.contains("mimicPath")) return null;            // a card wearing another's face is not that card
        return key(pw, t.getInt("seq"), t.getBoolean("shiny"));
    }

    /** Sweeps every card out of a player's inventory into the binder. Returns how many went in. */
    public static int storeAll(ServerPlayer p, ItemStack binder) {
        CompoundTag c = cards(binder);
        int moved = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.isEmpty() || s == binder) continue;
            String k = faceOf(s);
            if (k == null) continue;
            c.putInt(k, c.getInt(k) + s.getCount());
            moved += s.getCount();
            p.getInventory().setItem(i, ItemStack.EMPTY);
        }
        if (moved > 0) cards(binder, c);
        return moved;
    }

    /** Takes one card of that face out of the binder and hands it over. False when there is none. */
    public static boolean take(ServerPlayer p, ItemStack binder, String k) {
        CompoundTag c = cards(binder);
        int have = c.getInt(k);
        if (have <= 0) return false;
        if (have == 1) c.remove(k); else c.putInt(k, have - 1);
        cards(binder, c);
        Object[] f = unkey(k);
        ItemStack card = Tarot.makeCard((String) f[0], (int) f[1], (boolean) f[2]);
        if (!p.getInventory().add(card)) p.drop(card, false);
        return true;
    }

    /** The binder a player is holding, or an empty stack. */
    public static ItemStack held(Player p) {
        if (p.getMainHandItem().is(BINDER.get())) return p.getMainHandItem();
        if (p.getOffhandItem().is(BINDER.get())) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    public static void open(ServerPlayer p) {
        ItemStack b = held(p);
        if (b.isEmpty()) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new Net.BinderOpenPayload(pack(b)));
        offer(p, b);
    }

    // ------------------------------------------------------------- finishing a row

    /** The row this player is being offered a pack for, until they take it. */
    private static final java.util.Map<java.util.UUID, String> PENDING = new java.util.HashMap<>();

    /** True when every sequence 9..0 of that pathway is in the binder, in that finish. */
    private static boolean rowFull(ItemStack binder, String pathway, boolean shiny) {
        CompoundTag c = cards(binder);
        for (int q = 0; q <= 9; q++) if (c.getInt(key(pathway, q, shiny)) <= 0) return false;
        return true;
    }

    private static String claims(ServerPlayer p) {
        return AstralData.get(p.getServer()).binderClaims.getOrDefault(p.getUUID(), "");
    }

    private static boolean claimed(ServerPlayer p, String row) {
        for (String s : claims(p).split(",")) if (s.equals(row)) return true;
        return false;
    }

    /**
     * Looks for a finished row this player has never been paid for and offers them a pack for it. One row at a time:
     * if several finish at once the next is offered when they next open the binder.
     *
     * The record is per player, kept in the world's saved data and not on the binder, so emptying a binder or crafting
     * a second one pays nothing twice.
     */
    public static void offer(ServerPlayer p, ItemStack binder) {
        for (boolean shiny : new boolean[]{false, true}) {
            for (String pw : Tarot.PATHWAYS) {
                String row = pw + (shiny ? "_s" : "");
                if (claimed(p, row) || !rowFull(binder, pw, shiny)) continue;
                PENDING.put(p.getUUID(), row);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new Net.BinderRewardPayload(shiny, row));
                return;
            }
        }
        PENDING.remove(p.getUUID());
    }

    /** Takes the pack they picked for the row they were offered. picks = "pathway:type:count" as the weekly menu sends. */
    public static void claim(ServerPlayer p, String picks) {
        String row = PENDING.get(p.getUUID());
        if (row == null) return;
        ItemStack b = held(p);
        boolean shinyRow = row.endsWith("_s");
        String pathway = shinyRow ? row.substring(0, row.length() - 2) : row;
        if (b.isEmpty() || claimed(p, row) || !rowFull(b, pathway, shinyRow)) { PENDING.remove(p.getUUID()); return; }

        int given = 0;
        for (String part : picks.split(",")) {
            String[] bits = part.split(":");
            if (bits.length != 3 || given >= 1) continue;
            int type;
            try { type = Integer.parseInt(bits[1]); } catch (NumberFormatException e) { continue; }
            if (shinyRow ? (type != 1 && type != 2) : type != 0) continue;      // the row decides what is on offer
            boolean known = false;
            for (String k : Tarot.PATHWAYS) if (k.equals(bits[0])) { known = true; break; }
            if (!known) continue;
            String id = bits[0] + (type == 0 ? "_pack" : type == 1 ? "_rainbow_pack" : "_shiny_pack");
            net.minecraft.world.item.Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, id));
            if (it == null || it == net.minecraft.world.item.Items.AIR) continue;
            ItemStack pk = new ItemStack(it);
            if (!p.getInventory().add(pk)) p.drop(pk, false);
            given++;
            p.sendSystemMessage(Component.literal("The " + Tarot.pretty(pathway) + " row is complete" + (shinyRow ? " in shiny" : "")
                + ". Your " + pk.getHoverName().getString() + " is yours.").withStyle(ChatFormatting.GOLD));
        }
        if (given == 0) return;

        AstralData d = AstralData.get(p.getServer());
        String was = claims(p);
        d.binderClaims.put(p.getUUID(), was.isEmpty() ? row : was + "," + row);
        d.setDirty();
        PENDING.remove(p.getUUID());
        offer(p, b);                                                           // another row may be waiting
    }

    /** What the screen asks for: 0 take one of `key`, 1 store everything in the inventory. */
    public static void act(ServerPlayer p, int what, String k) {
        ItemStack b = held(p);
        if (b.isEmpty()) return;
        if (what == 0) take(p, b, k);
        else if (what == 1) {
            int n = storeAll(p, b);
            p.displayClientMessage(Component.literal(n == 0 ? "No loose cards to file away." : "Filed " + n + " card" + (n == 1 ? "" : "s") + " away."), true);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new Net.BinderOpenPayload(pack(b)));
        offer(p, b);
    }

    public static class BinderItem extends Item {
        public BinderItem(Properties p) { super(p); }

        @Override
        public InteractionResultHolder<ItemStack> use(Level lv, Player p, InteractionHand hand) {
            if (!lv.isClientSide && p instanceof ServerPlayer sp) open(sp);
            return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand), lv.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack s, TooltipContext ctx, List<Component> out, TooltipFlag flag) {
            int have = filled(s), all = faces() * 2;
            out.add(Component.literal(have + " / " + all + " faces").withStyle(have >= all ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            out.add(Component.literal("Right-click to open. One slot per card.").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** A binder is never lost on death: a season's collecting should not go in a lava pit. */
    @SubscribeEvent
    public static void onDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        e.getDrops().removeIf(item -> {
            if (!item.getItem().is(BINDER.get())) return false;
            ItemStack keep = item.getItem().copy();
            UniqAbilities.later(1, () -> { if (!p.getInventory().add(keep)) p.drop(keep, false); });
            return true;
        });
    }
}
