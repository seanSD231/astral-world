package com.astralspace;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The altar: where a piece is fed other pieces and comes out stronger.
 *
 * <p>Experience, not currency. Every artifact is worth something as fodder, worth more the rarer it
 * is and more again for every level already put into it, and what it is worth goes into the piece
 * on the altar. A levelling run is all-or-nothing in one call, which keeps the client's part of it
 * to "here is the piece and here is what to burn" and leaves every sum on the server.</p>
 *
 * <p>Overflow is kept. Feeding a piece far past what it needs would otherwise quietly throw the
 * difference away, and a player cannot see the numbers well enough to feed it exactly.</p>
 */
public final class ArtifactAltar {
    private ArtifactAltar() { }

    /** What one more level costs, which climbs as the piece does. */
    public static int cost(int rarity, int level) {
        return (int) Math.round(55 * rarity * (1 + level * .52));
    }

    /** What a piece is worth burnt: its rarity, and everything already spent getting it where it is. */
    public static int fodderValue(Artifacts.Piece p) {
        int v = 90 * p.rarity();
        for (int l = 0; l < p.level(); l++) v += (int) (cost(p.rarity(), l) * .78);
        return v;
    }

    /** Everything it would take to carry a piece from where it is to the top. */
    public static int toMax(Artifacts.Piece p) {
        int v = 0;
        for (int l = p.level(); l < p.maxLevel(); l++) v += cost(p.rarity(), l);
        return v;
    }

    /** The experience banked on a piece but not yet spent on a level. */
    public static int banked(ItemStack st) {
        var cd = st.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        return cd.copyTag().getInt("astral_artifact_exp");
    }

    private static void bank(ItemStack st, int v) {
        net.minecraft.world.item.component.CustomData.update(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                st, t -> t.putInt("astral_artifact_exp", Math.max(0, v)));
    }

    /**
     * Feeds the fodder to the piece and levels it as far as that carries it.
     *
     * <p>{@code target} is an inventory index, or {@code -1 - ordinal} for one of the five worn
     * slots. Everything is checked here: the client sends indices and nothing else is trusted.</p>
     */
    public static void upgrade(ServerPlayer p, int target, List<Integer> fodder) {
        var inv = p.getInventory();
        boolean wornTarget = target < 0;
        int wornSlot = -target - 1;
        ItemStack st;
        if (wornTarget) {
            if (wornSlot < 0 || wornSlot >= Artifacts.Slot.values().length) return;
            st = ArtifactSlots.get(p, Artifacts.Slot.values()[wornSlot]);
        } else {
            if (target >= inv.getContainerSize()) return;
            st = inv.getItem(target);
        }
        if (st == null || st.isEmpty()) return;
        Artifacts.Piece piece = Artifacts.read(st);
        if (piece == null) return;
        if (piece.level() >= piece.maxLevel()) {
            p.displayClientMessage(Component.literal("That piece is already as far as it goes.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return;
        }

        // gather the fodder, skipping anything that is not an artifact or is the piece itself
        List<Integer> taken = new ArrayList<>();
        int exp = banked(st);
        for (int i : fodder) {
            if (i < 0 || i >= inv.getContainerSize()) continue;
            if (!wornTarget && i == target) continue;
            if (taken.contains(i)) continue;
            ItemStack f = inv.getItem(i);
            if (f.isEmpty() || !(f.getItem() instanceof ArtifactItem)) continue;
            Artifacts.Piece fp = Artifacts.read(f);
            if (fp == null) continue;
            exp += fodderValue(fp) + banked(f);
            taken.add(i);
        }
        if (taken.isEmpty()) {
            p.displayClientMessage(Component.literal("Nothing to feed it.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return;
        }

        int was = piece.level(), gained = 0;
        while (piece.level() + gained < piece.maxLevel()) {
            int c = cost(piece.rarity(), piece.level() + gained);
            if (exp < c) break;
            exp -= c;
            gained++;
        }
        for (int i : taken) inv.setItem(i, ItemStack.EMPTY);
        if (gained > 0) Artifacts.levelUp(st, gained, p.getRandom());
        bank(st, exp);                                  // whatever is left over stays on the piece

        if (wornTarget) {
            ArtifactSlots.equip(p, Artifacts.Slot.values()[wornSlot], st);
        } else {
            inv.setItem(target, st);
        }
        p.level().playSound(null, p.blockPosition(),
                gained > 0 ? SoundEvents.ANVIL_USE : SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, .7f, gained > 0 ? 1.2f : 1.6f);
        p.displayClientMessage(gained > 0
                ? Component.literal("+" + gained + "  →  level " + (was + gained)).withColor(0xFFD24A)
                : Component.literal("Not enough yet. It keeps what it was given.").withStyle(net.minecraft.ChatFormatting.GRAY), true);
    }
}
