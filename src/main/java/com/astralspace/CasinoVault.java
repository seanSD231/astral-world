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
 * in the bet container the moment a machine starts is gone for good, win or lose, and only the chest decides
 * whether it can be got back.
 *
 * Which of it was actually lost is worked out from the machine's own payout list, read the same tick it starts:
 *   nothing paid out  -> the whole stake was lost, and all of it is banked.
 *   roulette          -> a slot wins when its index mod 3 is the wheel's result, so only the other slots are banked.
 *   anything else that paid out -> the play won something and no part of it can be told apart, so nothing is banked
 *                        and those items are lost exactly as MineVegas loses them.
 * Set casino_vault_losses_only to false in settings.properties to bank every stake instead, win or lose.
 *
 * Nothing of MineVegas is compiled against: the five machines that take bets (roulette, slot machine, plinko,
 * fortune wheel, duck derby) all expose a public bets() and a public boolean for "running", so both are found by
 * reflection on the block entity's class. Machines near a player are polled every tick; the moment one goes from
 * idle to running, the contents noted the tick before are put in the vault. Blackjack and poker tables keep their
 * stakes per seat and expose no container, so they are not covered.
 *
 * A vault is one chest (or any container block), set with /casinovault set while looking at it. It takes only from
 * the machines within casino_vault_radius blocks of it in the same dimension, so a casino keeps its own chest and a
 * machine somewhere else on the server is not drained into it. Several vaults can stand at once, one per casino;
 * a machine in range of more than one goes to the nearest.
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
    /** Vault id ("dimension@pos") -> what is waiting for room in it. */
    private static final Map<String, List<ItemStack>> PENDING_BY_VAULT = new HashMap<>();
    private static final Map<Class<?>, Method[]> SHAPE = new HashMap<>();   // machine class -> {bets(), running()}
    private static final Map<Class<?>, java.lang.reflect.Field[]> STATE = new HashMap<>();   // machine class -> {payouts, result}

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
        if (now % 40 == 0) find(server, d, now);       // every two seconds, look for machines standing near a vault
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
                if (running && !w.running) bank(server, d, w.stake, be);   // it just started: last tick's bets have been taken
                w.running = running;
                if (!running) w.stake = copyOf((Container) sh[0].invoke(be));
            } catch (Throwable t) { it.remove(); }
        }
        if (now % 20 == 0) for (String id : new ArrayList<>(PENDING_BY_VAULT.keySet())) deposit(server, id, null);
    }

    /**
     * The machines worth watching: near a player (so the chunks are there and someone is playing) and inside some
     * vault's range. A machine with no vault near it is left alone entirely, so the rest of the server costs nothing.
     */
    private static void find(MinecraftServer server, AstralData d, long now) {
        if (d.vaults.isEmpty()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerLevel lv = p.serverLevel();
            BlockPos at = p.blockPosition();
            int cx = at.getX() >> 4, cz = at.getZ() >> 4;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                var ch = lv.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (shape(be.getClass()) == null || be.getBlockPos().distSqr(at) > 24 * 24) continue;
                    if (nearestVault(d, lv, be.getBlockPos()) == null) continue;
                    Watch w = WATCHED.computeIfAbsent(lv.dimension().location() + "@" + be.getBlockPos().asLong(), k -> new Watch());
                    w.level = lv; w.pos = be.getBlockPos().immutable(); w.seen = now;
                }
            }
        }
    }

    /** The id of the closest vault to this spot within casino_vault_radius, or null if there is none. */
    private static String nearestVault(AstralData d, ServerLevel lv, BlockPos pos) {
        String dim = lv.dimension().location().toString(), best = null;
        double bestD = (double) Settings.casinoVaultRadius * Settings.casinoVaultRadius;
        for (String id : d.vaults) {
            int at = id.indexOf('@');
            if (at < 0 || !id.substring(0, at).equals(dim)) continue;
            double dd = BlockPos.of(Long.parseLong(id.substring(at + 1))).distSqr(pos);
            if (dd <= bestD) { bestD = dd; best = id; }
        }
        return best;
    }

    /** The stake, slot by slot, so a machine that bets per slot can have its losing slots picked out. */
    private static List<ItemStack> copyOf(Container c) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) out.add(c.getItem(i).copy());
        return out;
    }

    /** {payouts, result} of a machine, either of them null when it has no such field. */
    private static java.lang.reflect.Field[] state(Class<?> c) {
        java.lang.reflect.Field[] f = STATE.get(c);
        if (f != null) return f;
        f = new java.lang.reflect.Field[2];
        for (int i = 0; i < 2; i++) try {
            f[i] = c.getDeclaredField(i == 0 ? "payouts" : "result");
            f[i].setAccessible(true);
        } catch (Throwable t) { f[i] = null; }
        STATE.put(c, f);
        return f;
    }

    /** How many stacks this machine is about to pay out. -1 when it could not be read at all. */
    private static int paidOut(BlockEntity be) {
        java.lang.reflect.Field pf = state(be.getClass())[0];
        if (pf == null) return -1;
        try {
            Object v = pf.get(be);
            if (v instanceof Object[] arr) { int n = 0; for (Object o : arr) if (o instanceof List<?> l) n += count(l); return n; }
            return v instanceof List<?> l ? count(l) : -1;
        } catch (Throwable t) { return -1; }
    }
    private static int count(List<?> l) { int n = 0; for (Object o : l) if (o instanceof ItemStack st && !st.isEmpty()) n++; return n; }

    /**
     * A machine has started, so its bets have just been taken. Works out which of them lost and banks those.
     * See the note on this class for the three cases.
     */
    private static void bank(MinecraftServer server, AstralData d, List<ItemStack> stake, BlockEntity be) {
        String vault = be.getLevel() instanceof ServerLevel lv ? nearestVault(d, lv, be.getBlockPos()) : null;
        if (vault == null) return;   // no vault near this machine: its stakes are lost as MineVegas loses them
        boolean any = false;
        for (ItemStack st : stake) if (!st.isEmpty()) { any = true; break; }
        if (!any) return;
        String why;
        List<ItemStack> lost = new ArrayList<>();
        int paid = paidOut(be);
        if (!Settings.casinoVaultLossesOnly) { for (ItemStack st : stake) if (!st.isEmpty()) lost.add(st); why = "every stake (losses_only is off)"; }
        else if (paid == 0) { for (ItemStack st : stake) if (!st.isEmpty()) lost.add(st); why = "nothing was paid out, so the whole stake lost"; }
        else if (paid > 0 && be.getClass().getSimpleName().startsWith("Roulette") && state(be.getClass())[1] != null) {
            int result = -1;
            try { result = state(be.getClass())[1].getInt(be); } catch (Throwable ignored) { }
            if (result < 0) { why = "roulette paid out but its result could not be read, so nothing banked"; }
            else {
                for (int i = 0; i < stake.size(); i++) if (!stake.get(i).isEmpty() && i % 3 != result) lost.add(stake.get(i));
                why = "roulette landed on " + result + ", so the slots that missed it lost";
            }
        } else why = paid < 0 ? "the payout list could not be read, so nothing banked" : "the play paid out, so nothing banked";
        if (lost.isEmpty()) { LOGGER.info("[astral] casino vault: {} at {} - {}", be.getClass().getSimpleName(), be.getBlockPos().toShortString(), why); return; }
        PENDING_BY_VAULT.computeIfAbsent(vault, k -> new ArrayList<>()).addAll(lost);
        LOGGER.info("[astral] casino vault: {} lost stack(s) from {} at {} -> {} - {}", lost.size(), be.getClass().getSimpleName(), be.getBlockPos().toShortString(), vault, why);
        deposit(server, vault, null);
    }

    /** Moves what is pending into one vault. Anything that will not fit stays pending and is tried again. */
    private static void deposit(MinecraftServer server, String vaultId, ServerPlayer tell) {
        List<ItemStack> pending = PENDING_BY_VAULT.get(vaultId);
        if (pending == null || pending.isEmpty()) return;
        Container box = container(server, vaultId);
        if (box == null) { if (tell != null) tell.sendSystemMessage(Component.literal("That casino vault is not loaded, or is no longer a container.").withStyle(ChatFormatting.RED)); return; }
        for (Iterator<ItemStack> it = pending.iterator(); it.hasNext();) {
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
            else { box.setChanged(); LOGGER.warn("[astral] casino vault {} is full: {} x{} is still waiting", vaultId, st.getItem(), st.getCount()); return; }
        }
        box.setChanged();
    }

    /** The container a vault id points at, or null when it is unloaded or no longer a container. */
    private static Container container(MinecraftServer server, String vaultId) {
        int at = vaultId.indexOf('@');
        if (at < 0) return null;
        for (ServerLevel lv : server.getAllLevels()) {
            if (!lv.dimension().location().toString().equals(vaultId.substring(0, at))) continue;
            BlockPos pos = BlockPos.of(Long.parseLong(vaultId.substring(at + 1)));
            return lv.isLoaded(pos) && lv.getBlockEntity(pos) instanceof Container c ? c : null;
        }
        return null;
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("casinovault").requires(c -> c.hasPermission(2))
            .executes(c -> status(c.getSource().getPlayerOrException()))
            .then(Commands.literal("set").executes(c -> set(c.getSource().getPlayerOrException())))
            .then(Commands.literal("remove").executes(c -> remove(c.getSource().getPlayerOrException())))
            .then(Commands.literal("clear").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                AstralData d = AstralData.get(p.server);
                int n = d.vaults.size();
                d.vaults.clear(); d.setDirty(); PENDING_BY_VAULT.clear(); WATCHED.clear();
                p.sendSystemMessage(Component.literal(n + " casino vault(s) cleared. Lost stakes are deleted again, as MineVegas does by itself.").withStyle(ChatFormatting.YELLOW));
                return 1;
            })));
    }

    /** The container block the player is looking at, or null. */
    private static BlockPos lookingAtContainer(ServerPlayer p) {
        HitResult hr = p.pick(6, 0f, false);
        return hr instanceof BlockHitResult bh && p.serverLevel().getBlockEntity(bh.getBlockPos()) instanceof Container ? bh.getBlockPos().immutable() : null;
    }

    private static int set(ServerPlayer p) {
        BlockPos pos = lookingAtContainer(p);
        if (pos == null) { p.sendSystemMessage(Component.literal("Look at a chest (or any container) within six blocks and run it again.").withStyle(ChatFormatting.RED)); return 0; }
        AstralData d = AstralData.get(p.server);
        String id = p.serverLevel().dimension().location() + "@" + pos.asLong();
        if (d.vaults.contains(id)) { p.sendSystemMessage(Component.literal("That chest is already a casino vault.").withStyle(ChatFormatting.GRAY)); return 0; }
        d.vaults.add(id); d.setDirty();
        WATCHED.clear();   // the machines in range have changed
        p.sendSystemMessage(Component.literal("Casino vault set at " + pos.toShortString() + ". The lost stakes of every casino machine within "
            + Settings.casinoVaultRadius + " blocks of it come here; machines further off are not touched.").withStyle(ChatFormatting.GREEN));
        deposit(p.server, id, p);
        return 1;
    }

    private static int remove(ServerPlayer p) {
        AstralData d = AstralData.get(p.server);
        BlockPos pos = lookingAtContainer(p);
        String id = pos == null ? null : p.serverLevel().dimension().location() + "@" + pos.asLong();
        if (id == null || !d.vaults.remove(id)) { p.sendSystemMessage(Component.literal("Look at a chest that is a casino vault and run it again.").withStyle(ChatFormatting.RED)); return 0; }
        d.setDirty(); PENDING_BY_VAULT.remove(id); WATCHED.clear();
        p.sendSystemMessage(Component.literal("That chest is no longer a casino vault.").withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int status(ServerPlayer p) {
        AstralData d = AstralData.get(p.server);
        if (d.vaults.isEmpty()) {
            p.sendSystemMessage(Component.literal("No casino vaults. Look at a chest and run /casinovault set.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        p.sendSystemMessage(Component.literal(d.vaults.size() + " casino vault(s), each taking from machines within " + Settings.casinoVaultRadius
            + " blocks. Watching " + WATCHED.size() + " machine(s).").withStyle(ChatFormatting.GREEN));
        for (String id : d.vaults) {
            int at = id.indexOf('@');
            BlockPos pos = BlockPos.of(Long.parseLong(id.substring(at + 1)));
            boolean there = container(p.server, id) != null;
            List<ItemStack> wait = PENDING_BY_VAULT.get(id);
            p.sendSystemMessage(Component.literal("  " + pos.toShortString() + " in " + id.substring(0, at)
                + (there ? "" : " (not loaded, or no longer a container)")
                + (wait == null || wait.isEmpty() ? "" : " - " + wait.size() + " stack(s) waiting for room")).withStyle(there ? ChatFormatting.GRAY : ChatFormatting.YELLOW));
        }
        return 1;
    }
}
