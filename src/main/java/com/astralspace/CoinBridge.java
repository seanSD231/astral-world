package com.astralspace;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Makes the Lord of the Mysteries coins (gold 100, silver 10, bronze 1) work with Create: Numismatics without either mod knowing.
 *
 * Numismatics has a fixed list of six coins, so instead of teaching it new coins the coins are swapped by value:
 * the moment a player uses a Numismatics block, menu or card, every LOTM coin they carry becomes Numismatics coins
 * (1 bronze = 1 spur, so any amount is exact); a couple of seconds after they stop, all Numismatics coins they carry become LOTM
 * coins again. Players therefore only ever hold LOTM coins. Nothing is lost or created: the total value is kept exactly.
 *
 * The "bank" (bank terminal and blaze banker) can't be broken or blown up, and the banking guide can't be used,
 * except by an op in creative mode, so only admins can place them.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class CoinBridge {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String[] LOTM_IDS = {"lotm:coin", "lotm:silver_coin", "lotm:bronze_coin"};
    private static final int[] LOTM_VALUE = {100, 10, 1};
    private static final String[] NUM_IDS = {"numismatics:sun", "numismatics:crown", "numismatics:cog",
            "numismatics:sprocket", "numismatics:bevel", "numismatics:spur"};
    private static final String[] NUM_ENUM = {"SUN", "CROWN", "COG", "SPROCKET", "BEVEL", "SPUR"};
    /** What Numismatics' coins are worth in LOTM terms: spur = bronze, bevel = silver, sprocket = gold, then the unused three above. */
    private static final int[] WANT_VALUE = {100000, 10000, 1000, 100, 10, 1};
    private static final String COIN_CLASS = "dev.ithundxr.createnumismatics.content.backend.Coin";
    private static int[] NUM_VALUE = {4096, 512, 64, 16, 8, 1};

    /** Blocks nobody (but a creative op) can break. Remove an entry here to let players break that block again. */
    private static final String[] PROTECTED_BLOCKS = {"numismatics:bank_terminal", "numismatics:blaze_banker"};
    private static final String BANKING_GUIDE = "numismatics:banking_guide";

    /** How long after the last Numismatics use the coins stay swapped. */
    private static final int HOLD_TICKS = 40;

    private static Item[] lotm, num;
    private static boolean checked, available;
    private static final Map<UUID, Long> touched = new HashMap<>();

    private static java.lang.reflect.Field valueField() throws ReflectiveOperationException {
        java.lang.reflect.Field f = Class.forName(COIN_CLASS).getField("value");
        f.setAccessible(true);
        return f;
    }

    /**
     * Numismatics keeps each coin's worth in a field (spur 1, bevel 8, sprocket 16 ...). LOTM's coins are worth 1, 10 and 100, so the
     * Numismatics coins are re-valued to match: spur 1, bevel 10, sprocket 100 (and cog 1000, crown 10000, sun 100000, which stay unused).
     * Run on both the client and the server so shops and the bank read in LOTM values. Bank balances are stored in spurs and are not affected.
     */
    public static void patchValues() {
        try {
            Class<?> coin = Class.forName(COIN_CLASS);
            java.lang.reflect.Field f = valueField();
            for (int i = 0; i < NUM_ENUM.length; i++) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object c = Enum.valueOf((Class) coin, NUM_ENUM[i]);
                f.setInt(c, WANT_VALUE[i]);
            }
            LOGGER.info("[astral] Numismatics coins re-valued to 1 / 10 / 100 (spur / bevel / sprocket)");
        } catch (ClassNotFoundException e) {
            // Numismatics is not installed
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not re-value the Numismatics coins, shops keep their own values (the coin swap still keeps totals exact): {}", t.toString());
        }
    }

    /** The coin values Numismatics really has right now (they may or may not have been changed). */
    private static int[] actualValues() {
        try {
            Class<?> coin = Class.forName(COIN_CLASS);
            java.lang.reflect.Field f = valueField();
            int[] out = new int[NUM_ENUM.length];
            for (int i = 0; i < out.length; i++) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object c = Enum.valueOf((Class) coin, NUM_ENUM[i]);
                out[i] = f.getInt(c);
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean ready() {
        if (checked) return available;
        checked = true;
        lotm = lookup(LOTM_IDS);
        num = lookup(NUM_IDS);
        available = lotm != null && num != null;
        if (available) {
            int[] v = actualValues();
            if (v != null) NUM_VALUE = v;
            // Re-valued: the three lowest coins are exactly bronze, silver and gold, so only those are ever handed out.
            if (NUM_VALUE[3] == 100 && NUM_VALUE[4] == 10 && NUM_VALUE[5] == 1) {
                swapItems = new Item[]{num[3], num[4], num[5]};
                swapValues = new int[]{100, 10, 1};
            } else {
                swapItems = num;
                swapValues = NUM_VALUE;
            }
        }
        LOGGER.info("[astral] coin bridge {} (Numismatics values {})", available ? "on, LOTM coins <-> Numismatics coins" : "off, LOTM or Numismatics coins were not found",
                java.util.Arrays.toString(NUM_VALUE));
        return available;
    }

    private static Item[] swapItems;
    private static int[] swapValues;

    private static Item[] lookup(String[] ids) {
        Item[] out = new Item[ids.length];
        for (int i = 0; i < ids.length; i++) {
            var opt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(ids[i]));
            if (opt.isEmpty()) return null;
            out[i] = opt.get();
        }
        return out;
    }

    private static int indexOf(Item[] items, Item it) {
        for (int i = 0; i < items.length; i++) if (items[i] == it) return i;
        return -1;
    }

    /** Takes every plain coin of the given kinds out of the inventory and returns their total value. */
    private static long take(ServerPlayer p, Item[] items, int[] values) {
        Inventory inv = p.getInventory();
        long total = 0;
        int size = inv.getContainerSize();
        for (int i = 0; i < size; i++) {
            if (i >= 36 && i <= 39) continue;   // armour slots
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !s.getComponentsPatch().isEmpty()) continue;
            int k = indexOf(items, s.getItem());
            if (k < 0) continue;
            total += (long) values[k] * s.getCount();
            inv.setItem(i, ItemStack.EMPTY);
        }
        return total;
    }

    /** Hands out `total` as coins, largest first. Whatever does not fit is dropped at the player's feet. */
    private static void give(ServerPlayer p, Item[] items, int[] values, long total) {
        for (int k = 0; k < items.length && total > 0; k++) {
            long n = total / values[k];
            total -= n * values[k];
            int max = new ItemStack(items[k]).getMaxStackSize();
            while (n > 0) {
                int c = (int) Math.min(max, n);
                n -= c;
                ItemStack stack = new ItemStack(items[k], c);
                p.getInventory().add(stack);
                if (!stack.isEmpty()) p.drop(stack, false);
            }
        }
    }

    private static boolean hasCoins(ServerPlayer p, Item[] items) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (i >= 36 && i <= 39) continue;
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && indexOf(items, s.getItem()) >= 0) return true;
        }
        return false;
    }

    /**
     * LOTM coins -> Numismatics coins. LOTM coins stack to 9999 but Numismatics coins only to 64, so a big purse would not fit.
     * Only as many coins are swapped as the inventory has room for; the rest stay LOTM coins and nothing is dropped or lost.
     * (Use the shop or bank again to bring in more.)
     */
    private static void toNumismatics(ServerPlayer p) {
        if (!hasCoins(p, lotm)) return;
        boolean tierMode = swapItems.length == 3 && swapValues[0] == 100 && swapValues[1] == 10 && swapValues[2] == 1;
        if (!tierMode) {   // the values could not be changed: swap by value, but only when everything fits
            long total = 0;
            for (int i = 0; i < 36; i++) { ItemStack s = p.getInventory().getItem(i); int k = indexOf(lotm, s.getItem()); if (k >= 0 && s.getComponentsPatch().isEmpty()) total += (long) LOTM_VALUE[k] * s.getCount(); }
            long stacks = 0; long rest = total;
            for (int k = 0; k < swapItems.length; k++) { long n = rest / swapValues[k]; rest -= n * swapValues[k]; stacks += (n + 63) / 64; }
            if (stacks > emptySlots(p) + countLotmSlots(p)) return;
            give(p, swapItems, swapValues, take(p, lotm, LOTM_VALUE));
            return;
        }
        // lotm[0] gold, lotm[1] silver, lotm[2] bronze  ->  swapItems[0] sprocket, [1] bevel, [2] spur, one to one
        for (int t = 0; t < 3; t++) {
            Item from = lotm[t], to = swapItems[t];
            int slots = 0; long have = 0;
            Inventory inv = p.getInventory();
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && s.getItem() == from && s.getComponentsPatch().isEmpty()) { slots++; have += s.getCount(); }
            }
            if (have == 0) continue;
            int budget = emptySlots(p) + slots;
            int maxTo = new ItemStack(to).getMaxStackSize(), maxFrom = new ItemStack(from).getMaxStackSize();
            long move = 0;
            for (int k = budget; k >= 1; k--) {   // k slots of Numismatics coins, the rest of the LOTM coins left over
                long n = Math.min(have, (long) maxTo * k);
                long r = have - n;
                if (k + (r + maxFrom - 1) / maxFrom <= budget) { move = n; break; }
            }
            if (move <= 0) continue;
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && s.getItem() == from && s.getComponentsPatch().isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            }
            long left = move;
            while (left > 0) {
                int c = (int) Math.min(maxTo, left);
                left -= c;
                ItemStack st = new ItemStack(to, c);
                inv.add(st);
                if (!st.isEmpty()) p.drop(st, false);
            }
            long rem = have - move;
            while (rem > 0) {
                int c = (int) Math.min(maxFrom, rem);
                rem -= c;
                ItemStack st = new ItemStack(from, c);
                inv.add(st);
                if (!st.isEmpty()) p.drop(st, false);
            }
        }
    }

    private static int emptySlots(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).isEmpty()) n++;
        return n;
    }

    private static int countLotmSlots(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.isEmpty() && indexOf(lotm, s.getItem()) >= 0 && s.getComponentsPatch().isEmpty()) n++;
        }
        return n;
    }

    /** Numismatics coins -> LOTM coins (same value). */
    private static void toLotm(ServerPlayer p) {
        if (!hasCoins(p, num)) return;
        give(p, lotm, LOTM_VALUE, take(p, num, NUM_VALUE));
    }

    private static void touch(ServerPlayer p) {
        touched.put(p.getUUID(), p.serverLevel().getServer().getTickCount() + 0L);
        try {
            toNumismatics(p);
        } catch (Throwable t) {
            LOGGER.warn("[astral] coin bridge could not swap coins: {}", t.toString());
        }
    }

    private static boolean isNumismatics(ResourceLocation id) {
        String ns = id.getNamespace();
        return ns.equals("numismatics") || ns.equals("numismaticsutils");
    }

    // ------------------------------------------------------------------ triggers

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !ready()) return;
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock());
        ResourceLocation held = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (held.toString().equals(BANKING_GUIDE) && !(p.isCreative() && p.hasPermissions(2))) {
            event.setCanceled(true);   // no new banks
            return;
        }
        if (isNumismatics(block) || (isNumismatics(held) && indexOf(num, event.getItemStack().getItem()) < 0)) touch(p);
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !ready()) return;
        ResourceLocation held = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (isNumismatics(held) && indexOf(num, event.getItemStack().getItem()) < 0) touch(p);   // cards, the portable terminal
    }

    @SubscribeEvent
    public static void onMenuOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !ready()) return;
        String cls = event.getContainer().getClass().getName();
        if (cls.startsWith("dev.ithundxr.createnumismatics") || cls.startsWith("io.github.chakyl.numismaticsutils")) touch(p);
    }

    /** Every half second: players that are done with Numismatics get LOTM coins back. */
    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        int now = server.getTickCount();
        if (now % 10 != 0 || !ready()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            try {
                if (p.containerMenu != p.inventoryMenu) continue;               // a menu is open: leave it
                Long last = touched.get(p.getUUID());
                if (last != null && now - last < HOLD_TICKS) continue;       // used it a moment ago
                toLotm(p);
                touched.remove(p.getUUID());
            } catch (Throwable t) {
                LOGGER.warn("[astral] coin bridge could not swap coins back: {}", t.toString());
            }
        }
    }

    // ------------------------------------------------------------------ the bank can't be broken

    private static boolean isProtected(Block b) {
        String id = BuiltInRegistries.BLOCK.getKey(b).toString();
        for (String s : PROTECTED_BLOCKS) if (s.equals(id)) return true;
        return false;
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!isProtected(event.getState().getBlock())) return;
        if (event.getPlayer() instanceof ServerPlayer sp && sp.isCreative() && sp.hasPermissions(2)) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        var level = event.getLevel();
        event.getAffectedBlocks().removeIf(pos -> isProtected(level.getBlockState(pos).getBlock()));
    }

    // ------------------------------------------------------------------ items that stay with their owner

    /** An item whose custom data holds astral_keep:1b (the Supporter+ credit card, the VIP portable terminal) is not dropped on death. */
    private static boolean keeps(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d != null && d.contains("astral_keep");
    }

    private static final Map<UUID, List<ItemStack>> kept = new HashMap<>();

    @SubscribeEvent
    public static void onDeathDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        var it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemEntity e = it.next();
            if (keeps(e.getItem())) {
                kept.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(e.getItem().copy());
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRespawnClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer p)) return;
        List<ItemStack> list = kept.remove(p.getUUID());
        if (list == null) return;
        for (ItemStack s : list) {
            if (!p.getInventory().add(s)) p.drop(s, false);
        }
    }
}
