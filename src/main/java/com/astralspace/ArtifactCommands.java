package com.astralspace;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Moving pieces in and out of the five slots, and the commands for making them in the first place
 * until the ways of finding them are in.
 */
public final class ArtifactCommands {
    private ArtifactCommands() { }

    /**
     * Swaps a piece between a slot and a place in the bag. The client asks for this, so everything
     * about it is checked again here: the slot number, the bag index, and that the piece in hand
     * actually belongs in the slot it is headed for.
     */
    public static void act(ServerPlayer p, int slot, int invSlot) {
        Artifacts.Slot[] slots = Artifacts.Slot.values();
        if (slot < 0 || slot >= slots.length) return;
        var inv = p.getInventory();
        if (invSlot < 0) {                                   // take it off, back into the bag
            ItemStack was = ArtifactSlots.unequip(p, slots[slot]);
            if (!was.isEmpty() && !inv.add(was)) p.drop(was, false);
            return;
        }
        if (invSlot >= inv.getContainerSize()) return;
        ItemStack st = inv.getItem(invSlot);
        if (st.isEmpty() || !(st.getItem() instanceof ArtifactItem ai) || ai.slot() != slots[slot]) return;
        if (Artifacts.read(st) == null) return;
        inv.setItem(invSlot, ItemStack.EMPTY);
        ItemStack was = ArtifactSlots.equip(p, slots[slot], st);
        if (!was.isEmpty() && !inv.add(was)) p.drop(was, false);
    }

    /**
     * Spends one Dust of Enlightenment to roll a piece's sub-stats again.
     *
     * <p>How many it has and how many times each has been rolled are both kept, so a piece that has
     * been carried to twenty does not lose what was spent getting it there -- only which stats
     * those rolls landed on and how high they came out. The main stat, the rarity and the level are
     * untouched, because rerolling those would be rerolling the piece rather than improving it.</p>
     */
    public static void reroll(ServerPlayer p, int ref) {
        var inv = p.getInventory();
        ItemStack st;
        Artifacts.Slot slot = null;
        if (ref <= -1) {
            int k = -ref - 1;
            if (k < 0 || k >= Artifacts.Slot.values().length) return;
            slot = Artifacts.Slot.values()[k];
            st = ArtifactSlots.get(p, slot);
        } else {
            if (ref >= inv.getContainerSize()) return;
            st = inv.getItem(ref);
        }
        if (st == null || st.isEmpty()) return;
        Artifacts.Piece piece = Artifacts.read(st);
        if (piece == null) return;

        int dust = -1;
        for (int i = 0; i < inv.getContainerSize(); i++)
            if (inv.getItem(i).is(AstralFloor.DUST.get())) { dust = i; break; }
        if (dust < 0) {
            p.displayClientMessage(Component.literal("No Dust of Enlightenment.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return;
        }
        inv.getItem(dust).shrink(1);
        Artifacts.reroll(st, p.getRandom());
        if (slot != null) ArtifactSlots.equip(p, slot, st); else inv.setItem(ref, st);
        p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.PLAYERS, .8f, 1.5f);
        p.displayClientMessage(Component.literal("Rolled again.").withColor(0xFFD24A), true);
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("artifacts").executes(c -> {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    c.getSource().getPlayerOrException(), new DoorNet.ArtifactOpen());
            return 1;
        }));
        d.register(Commands.literal("artifact")
            .requires(s -> s.hasPermission(2))
            .then(Commands.literal("give")
                .then(Commands.argument("set", StringArgumentType.word())
                    .suggests((c, b) -> { for (var s : ArtifactSets.all()) b.suggest(s.id()); return b.buildFuture(); })
                    .then(Commands.argument("slot", StringArgumentType.word())
                        .suggests((c, b) -> { for (var s : Artifacts.Slot.values()) b.suggest(s.id()); return b.buildFuture(); })
                        .then(Commands.argument("rarity", IntegerArgumentType.integer(1, 5))
                            .executes(c -> give(c.getSource(), StringArgumentType.getString(c, "set"),
                                    StringArgumentType.getString(c, "slot"), IntegerArgumentType.getInteger(c, "rarity"), 0))
                            .then(Commands.argument("level", IntegerArgumentType.integer(0, 20))
                                .executes(c -> give(c.getSource(), StringArgumentType.getString(c, "set"),
                                        StringArgumentType.getString(c, "slot"), IntegerArgumentType.getInteger(c, "rarity"),
                                        IntegerArgumentType.getInteger(c, "level"))))))))
            .then(Commands.literal("random")
                .then(Commands.argument("rarity", IntegerArgumentType.integer(1, 5))
                    .executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        var r = p.getRandom();
                        var set = ArtifactSets.random(r);
                        var slot = Artifacts.Slot.values()[r.nextInt(Artifacts.Slot.values().length)];
                        ItemStack st = Artifacts.roll(set.id(), slot, IntegerArgumentType.getInteger(c, "rarity"), r);
                        if (!p.getInventory().add(st)) p.drop(st, false);
                        return 1;
                    })))
            .then(Commands.literal("menu").requires(s -> true).executes(c -> {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        c.getSource().getPlayerOrException(), new DoorNet.ArtifactOpen());
                return 1;
            }))
            .then(Commands.literal("sets").executes(c -> {
                for (var s : ArtifactSets.all())
                    c.getSource().sendSuccess(() -> Component.literal(s.id() + "  —  " + s.title()).withColor(s.colour()), false);
                return 1;
            }))
            .then(Commands.literal("dust")
                .then(Commands.argument("n", IntegerArgumentType.integer(1, 64)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ItemStack st = new ItemStack(AstralFloor.DUST.get(), IntegerArgumentType.getInteger(c, "n"));
                    if (!p.getInventory().add(st)) p.drop(st, false);
                    return 1;
                })))
            .then(Commands.literal("clear").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                for (var s : Artifacts.Slot.values()) {
                    ItemStack was = ArtifactSlots.unequip(p, s);
                    if (!was.isEmpty() && !p.getInventory().add(was)) p.drop(was, false);
                }
                return 1;
            })));
    }

    private static int give(CommandSourceStack src, String set, String slotId, int rarity, int level) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (ArtifactSets.get(set) == null) {
            src.sendFailure(Component.literal("No set called " + set + ". Try /artifact sets."));
            return 0;
        }
        Artifacts.Slot slot = null;
        for (var s : Artifacts.Slot.values()) if (s.id().equals(slotId)) slot = s;
        if (slot == null) { src.sendFailure(Component.literal("No slot called " + slotId + ".")); return 0; }
        ItemStack st = Artifacts.roll(set, slot, rarity, p.getRandom());
        if (level > 0) Artifacts.levelUp(st, level, p.getRandom());
        if (!p.getInventory().add(st)) p.drop(st, false);
        return 1;
    }
}
