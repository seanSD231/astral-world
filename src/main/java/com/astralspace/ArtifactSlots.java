package com.astralspace;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.astralspace.Artifacts.Slot;
import com.astralspace.Artifacts.Stat;

/**
 * What each player has on, and what it does to them.
 *
 * <p>The five pieces live in the player's own persistent data rather than in the inventory, so they
 * are not something to be knocked out of a hotbar or dropped on death, and they survive a relog.
 * The stats are put on as transient attribute modifiers under fixed ids: transient so nothing is
 * ever written into the player's saved attribute list, and fixed ids so re-applying replaces the
 * last lot rather than piling a second copy on top of it.</p>
 *
 * <p>Crit rate and crit damage have no attribute in Minecraft or in Lord of the Mysteries, so they
 * are kept here and read when a blow lands.</p>
 */
public final class ArtifactSlots {
    private ArtifactSlots() { }

    private static final String TAG = "astral_artifacts";
    private static final Map<UUID, ItemStack[]> WORN = new HashMap<>();
    private static final Map<UUID, double[]> CRIT = new HashMap<>();   // {rate, damage}

    private static ItemStack[] blank() {
        ItemStack[] a = new ItemStack[Slot.values().length];
        java.util.Arrays.fill(a, ItemStack.EMPTY);
        return a;
    }

    public static ItemStack[] worn(Player p) { return WORN.computeIfAbsent(p.getUUID(), k -> blank()); }

    public static ItemStack get(Player p, Slot s) { return worn(p)[s.ordinal()]; }

    /** The extra share of experience a player's artifacts are worth right now. */
    public static double expBonus(Player p) { double[] c = CRIT.get(p.getUUID()); return c == null ? 0 : c[0]; }

    // ---- putting pieces in and taking them out ----

    /**
     * Puts a piece in its slot and hands back whatever was there. The piece has to belong to the
     * slot: a Plume does not go in a Circlet's place however it is sent up from a client.
     */
    public static ItemStack equip(ServerPlayer p, Slot slot, ItemStack st) {
        if (!st.isEmpty() && (!(st.getItem() instanceof ArtifactItem ai) || ai.slot() != slot)) return st;
        if (!st.isEmpty() && Artifacts.read(st) == null) return st;
        ItemStack[] w = worn(p);
        ItemStack was = w[slot.ordinal()];
        w[slot.ordinal()] = st.copy();
        apply(p);
        save(p);
        sync(p);
        return was;
    }

    public static ItemStack unequip(ServerPlayer p, Slot slot) { return equip(p, slot, ItemStack.EMPTY); }

    // ---- the stats themselves ----

    /** Works out everything the worn pieces come to and puts it on the player. */
    public static void apply(Player p) {
        Map<Stat, Double> totals = Artifacts.totals(worn(p));
        double exp = 0;
        for (Stat s : Stat.values()) {
            double v = totals.getOrDefault(s, 0.0);
            if (s == Stat.EXP_MULT) { exp = v; continue; }   // no attribute anywhere: kept here
            if (s == Stat.ABILITY_POWER) v += SetEffects.abilityShift(p);   // what the live set effects add or take
            Holder<Attribute> h = s.attribute();
            if (h == null) continue;                       // the mod that owns it is not here
            AttributeInstance inst = p.getAttribute(h);
            if (inst == null) continue;
            var id = Artifacts.modId(s, s.pct);
            inst.removeModifier(id);
            if (v != 0) inst.addTransientModifier(new AttributeModifier(id, v, Artifacts.op(s)));
        }
        CRIT.put(p.getUUID(), new double[]{exp, 0});
        if (p instanceof ServerPlayer sp) SetAbilities.sync(sp);
        // Raising max health does not raise what is in the bar, and lowering it can leave a player
        // standing above their own maximum, which the game will not tidy up on its own.
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    /** Takes every artifact modifier back off, for when they log out or the pieces are cleared. */
    public static void strip(Player p) {
        for (Stat s : Stat.values()) {
            Holder<Attribute> h = s.attribute();
            if (h == null) continue;
            AttributeInstance inst = p.getAttribute(h);
            if (inst != null) inst.removeModifier(Artifacts.modId(s, s.pct));
        }
        CRIT.remove(p.getUUID());
    }

    // ---- keeping them ----

    private static void save(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        CompoundTag t = new CompoundTag();
        ItemStack[] w = worn(p);
        HolderLookup.Provider reg = p.registryAccess();
        for (Slot s : Slot.values()) {
            ItemStack st = w[s.ordinal()];
            if (st == null || st.isEmpty()) continue;
            Tag enc = st.save(reg, new CompoundTag());
            t.put(s.id(), enc);
        }
        root.put(TAG, t);
    }

    private static void load(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        ItemStack[] w = blank();
        if (root.contains(TAG)) {
            CompoundTag t = root.getCompound(TAG);
            HolderLookup.Provider reg = p.registryAccess();
            for (Slot s : Slot.values()) {
                if (!t.contains(s.id())) continue;
                w[s.ordinal()] = ItemStack.parse(reg, t.getCompound(s.id())).orElse(ItemStack.EMPTY);
            }
        }
        WORN.put(p.getUUID(), w);
    }

    public static void sync(ServerPlayer p) {
        ItemStack[] w = worn(p);
        for (Slot s : Slot.values())
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                    new DoorNet.Artifact(s.ordinal(), w[s.ordinal()]));
    }

    // ---- events ----

    public static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        load(sp);
        apply(sp);
        sync(sp);
    }

    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) { strip(sp); WORN.remove(sp.getUUID()); }
    }

    /** A respawned player is a new entity, so the modifiers have to go on again. */
    public static void onRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        load(sp);
        apply(sp);
        sync(sp);
    }

    public static void onDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) { apply(sp); sync(sp); }
    }

    /** Experience picked up, raised by whatever the artifacts are worth. */
    public static void onXp(net.neoforged.neoforge.event.entity.player.PlayerXpEvent.PickupXp e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        double k = expBonus(p);
        if (k <= 0) return;
        int extra = (int) Math.floor(e.getOrb().getValue() * k);
        if (extra > 0) p.giveExperiencePoints(extra);
    }

    // ---- the client's copy, for the menu ----

    private static final ItemStack[] CLIENT_WORN = blank();

    public static void setClient(int slot, ItemStack st) {
        if (slot >= 0 && slot < CLIENT_WORN.length) CLIENT_WORN[slot] = st;
    }

    public static ItemStack[] clientWorn() { return CLIENT_WORN; }
}
