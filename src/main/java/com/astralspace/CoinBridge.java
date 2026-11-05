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
    private static final int[] NUM_VALUE = {4096, 512, 64, 16, 8, 1};

    /** Blocks nobody (but a creative op) can break. Remove an entry here to let players break that block again. */
    private static final String[] PROTECTED_BLOCKS = {"numismatics:bank_terminal", "numismatics:blaze_banker"};
    private static final String BANKING_GUIDE = "numismatics:banking_guide";

    /** How long after the last Numismatics use the coins stay swapped. */
    private static final int HOLD_TICKS = 40;

    private static Item[] lotm, num;
    private static boolean checked, available;
    private static final Map<UUID, Long> touched = new HashMap<>();

    private static boolean ready() {
        if (checked) return available;
        checked = true;
        lotm = lookup(LOTM_IDS);
        num = lookup(NUM_IDS);
        available = lotm != null && num != null;
        LOGGER.info("[astral] coin bridge {}", available ? "on (LOTM coins <-> Numismatics coins)" : "off, LOTM or Numismatics coins were not found");
        return available;
    }

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

    /** LOTM coins -> Numismatics coins (same value). */
    private static void toNumismatics(ServerPlayer p) {
        if (!hasCoins(p, lotm)) return;
        give(p, num, NUM_VALUE, take(p, lotm, LOTM_VALUE));
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
