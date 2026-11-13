package com.astralspace;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Everything staked in a MineVegas casino machine goes into one chest instead of being deleted.
 *
 * MineVegas has no house vault and fires no event: when a machine starts, it empties its bet container with
 * removeItemNoUpdate and the losing stacks are simply dropped on the floor of the method. Winnings are not those
 * items coming back — they are minted fresh by CasinoExchange.payout and thrown out of the machine. So everything
 * that is in the bet container the moment a machine starts is gone for good, and that is what this collects.
 *
 * Nothing of MineVegas is compiled against: the five machines that take bets (roulette, slot machine, plinko,
 * fortune wheel, duck derby) all expose a public bets() and a public boolean for "running", so both are found by
 * reflection on the block entity's class. Machines near a player are polled every tick; the moment one goes from
 * idle to running, the contents noted the tick before are put in the vault. Blackjack and poker tables keep their
 * stakes per seat and expose no container, so they are not covered.
 *
 * The vault is one chest (or any container block), set with /casinovault set while looking at it.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class CasinoVault {
    private static final Logger LOGGER = LogUtils.getLogger();
    private CasinoVault() {}

    /** A machine being watched: where it is, its last seen stake, and whether it was running last tick. */
    private static final class Watch {
        ServerLevel level;
        BlockPos pos;
        List<ItemStack> stake = List.of();
        boolean running;
        long seen;
    }
    private static final Map<String, Watch> WATCHED = new HashMap<>();
    private static final Map<Class<?>, Method[]> SHAPE = new HashMap<>();   // machine class -> {bets(), running()}
    private static final List<ItemStack> PENDING = new ArrayList<>();        // waiting for room in the vault

    /** {@code bets()} and the "is it running" method of a MineVegas machine, or null if this is not one. */
    private static Method[] shape(Class<?> c) {
        Method[] m = SHAPE.get(c);
        if (m != null) return m.length == 0 ? null : m;
        m = new Method[0];
        if (c.getName().startsWith("fr.minevegas.casino.")) try {
            Method bets = c.getMethod("bets");
            if (Container.class.isAssignableFrom(bets.getReturnType())) {
                Method run = null;
                for (String n : new String[]{"isSpinning", "isPlaying", "isRacing", "isRoundOn"})
                    try { run = c.getMethod(n); break; } catch (NoSuchMethodException ignored) { }
                if (run != null && run.getReturnType() == boolean.class) {
                    m = new Method[]{bets, run};
                    LOGGER.info("[astral] casino vault: watching {} through {} and {}", c.getSimpleName(), bets.getName(), run.getName());
                }
            }
        } catch (Throwable ignored) { }
        SHAPE.put(c, m);
        return m.length == 0 ? null : m;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        AstralData d = AstralData.get(server);
        long now = server.getTickCount();
        if (now % 40 == 0) find(server, now);          // every two seconds, look for machines near the players
        if (WATCHED.isEmpty()) return;
        for (Iterator<Watch> it = WATCHED.values().iterator(); it.hasNext();) {
            Watch w = it.next();
            if (now - w.seen > 200) { it.remove(); continue; }   // nobody has been near it for ten seconds
            BlockEntity be = w.level.isLoaded(w.pos) ? w.level.getBlockEntity(w.pos) : null;
            if (be == null) { it.remove(); continue; }
            Method[] sh = shape(be.getClass());
            if (sh == null) { it.remove(); continue; }
            try {
                boolean running = (Boolean) sh[1].invoke(be);
                if (running && !w.running) bank(server, d, w.stake, be);   // it just started: last tick's bets are the house's
                w.running = running;
                if (!running) w.stake = copyOf((Container) sh[0].invoke(be));
            } catch (Throwable t) { it.remove(); }
        }
        if (!PENDING.isEmpty() && now % 20 == 0) deposit(server, d, null);
    }

    /** Casino machines within 24 blocks of any player, added to the watch list. */
    private static void find(MinecraftServer server, long now) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerLevel lv = p.serverLevel();
            BlockPos at = p.blockPosition();
            int cx = at.getX() >> 4, cz = at.getZ() >> 4;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                var ch = lv.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (shape(be.getClass()) == null || be.getBlockPos().distSqr(at) > 24 * 24) continue;
                    Watch w = WATCHED.computeIfAbsent(lv.dimension().location() + "@" + be.getBlockPos().asLong(), k -> new Watch());
                    w.level = lv; w.pos = be.getBlockPos().immutable(); w.seen = now;
                }
            }
        }
    }

    private static List<ItemStack> copyOf(Container c) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) { ItemStack st = c.getItem(i); if (!st.isEmpty()) out.add(st.copy()); }
        return out;
    }

    /** A machine has started: what was staked goes to the vault. */
    private static void bank(MinecraftServer server, AstralData d, List<ItemStack> stake, BlockEntity be) {
        if (stake.isEmpty() || d.vaultDim.isEmpty()) return;
        PENDING.addAll(stake);
        LOGGER.info("[astral] casino vault: {} stack(s) staked at {} go to the vault", stake.size(), be.getBlockPos().toShortString());
        deposit(server, d, null);
    }

    /** Moves what is pending into the vault. Anything that will not fit stays pending and is tried again. */
    private static void deposit(MinecraftServer server, AstralData d, ServerPlayer tell) {
        if (PENDING.isEmpty() || d.vaultDim.isEmpty()) return;
        Container box = vault(server, d);
        if (box == null) { if (tell != null) tell.sendSystemMessage(Component.literal("The casino vault is not there any more.").withStyle(ChatFormatting.RED)); return; }
        for (Iterator<ItemStack> it = PENDING.iterator(); it.hasNext();) {
            ItemStack st = it.next();
            for (int i = 0; i < box.getContainerSize() && !st.isEmpty(); i++) {
                ItemStack in = box.getItem(i);
                if (in.isEmpty()) { box.setItem(i, st.copy()); st = ItemStack.EMPTY; }
                else if (ItemStack.isSameItemSameComponents(in, st)) {
                    int room = Math.min(in.getMaxStackSize(), box.getMaxStackSize()) - in.getCount();
                    int move = Math.min(room, st.getCount());
                    if (move > 0) { in.grow(move); st.shrink(move); }
                }
            }
            if (st.isEmpty()) it.remove();
            else { box.setChanged(); LOGGER.warn("[astral] casino vault is full: {} x{} is still waiting", st.getItem(), st.getCount()); return; }
        }
        box.setChanged();
    }

    private static Container vault(MinecraftServer server, AstralData d) {
        if (d.vaultDim.isEmpty()) return null;
        for (ServerLevel lv : server.getAllLevels()) {
            if (!lv.dimension().location().toString().equals(d.vaultDim)) continue;
            BlockPos pos = BlockPos.of(d.vaultPos);
            if (!lv.isLoaded(pos)) return null;
            return lv.getBlockEntity(pos) instanceof Container c ? c : null;
        }
        return null;
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("casinovault").requires(c -> c.hasPermission(2))
            .executes(c -> status(c.getSource().getPlayerOrException()))
            .then(Commands.literal("set").executes(c -> set(c.getSource().getPlayerOrException())))
            .then(Commands.literal("clear").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                AstralData d = AstralData.get(p.server);
                d.vaultDim = ""; d.vaultPos = 0; d.setDirty();
                p.sendSystemMessage(Component.literal("Casino vault cleared. Lost stakes are deleted again, as MineVegas does by itself.").withStyle(ChatFormatting.YELLOW));
                return 1;
            })));
    }

    private static int set(ServerPlayer p) {
        HitResult hr = p.pick(6, 0f, false);
        AstralData d = AstralData.get(p.server);
        if (!(hr instanceof BlockHitResult bh) || !(p.serverLevel().getBlockEntity(bh.getBlockPos()) instanceof Container)) {
            p.sendSystemMessage(Component.literal("Look at a chest (or any container) within six blocks and run it again.").withStyle(ChatFormatting.RED));
            return 0;
        }
        d.vaultDim = p.serverLevel().dimension().location().toString();
        d.vaultPos = bh.getBlockPos().asLong();
        d.setDirty();
        p.sendSystemMessage(Component.literal("Casino vault set at " + bh.getBlockPos().toShortString() + ". Everything staked in a casino machine is put here instead of being deleted.")
            .withStyle(ChatFormatting.GREEN));
        deposit(p.server, d, p);
        return 1;
    }

    private static int status(ServerPlayer p) {
        AstralData d = AstralData.get(p.server);
        if (d.vaultDim.isEmpty()) {
            p.sendSystemMessage(Component.literal("No casino vault. Look at a chest and run /casinovault set.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        BlockPos pos = BlockPos.of(d.vaultPos);
        boolean there = vault(p.server, d) != null;
        p.sendSystemMessage(Component.literal("Casino vault: " + pos.toShortString() + " in " + d.vaultDim
            + (there ? "" : " (not loaded, or no longer a container)") + ". Watching " + WATCHED.size() + " machine(s), " + PENDING.size() + " stack(s) waiting for room.")
            .withStyle(there ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        return 1;
    }
}
