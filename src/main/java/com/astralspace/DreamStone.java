package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Dreaming Stone: the Darkness pathway's Dream Shaping, laid in the ground and pointed at a
 * crowd instead of at one sleeper.
 *
 * <p>Crouch-use opens LOTM's own shaping menu &mdash; the same one the ability opens, not a copy of
 * it &mdash; so the dream is built, picked and edited exactly as it always was. An empty-handed use
 * then casts whatever is selected at <em>every</em> player in range, one at a time, with whoever
 * used the stone as the caster. Use it holding anything to turn the keeping of items on and off.</p>
 *
 * <p>Keeping items is done here rather than in the dream: while a sleeper is under, the stone
 * remembers what they are carrying each tick, and the moment they wake it gives back anything that
 * went missing in the waking. That way nothing of the dream's own rules has to be unpicked.</p>
 *
 * <p>No recipe. An operator places it, and only an operator can work it.</p>
 */
public final class DreamStone {
    private DreamStone() { }

    /** How far the stone reaches for sleepers. */
    private static final double REACH = 48.0;

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AstralMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Tile>> TYPE =
            TYPES.register("dream_stone", () ->
                    BlockEntityType.Builder.of(Tile::new, AstralFloor.DREAM_STONE.get()).build(null));

    public static void register(IEventBus bus) { TYPES.register(bus); }

    // ---------------------------------------------------------------- the block

    public static final class Stone extends Block implements EntityBlock {
        public Stone(Properties p) { super(p); }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new Tile(pos, st); }

        @Override
        public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T>
                getTicker(Level level, BlockState st, BlockEntityType<T> type) {
            return level.isClientSide ? null
                    : (lv, pos, state, be) -> { if (be instanceof Tile t) Tile.tick(lv, pos, state, t); };
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState st, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.CONSUME;
            if (!sp.hasPermissions(2)) {
                sp.displayClientMessage(Component.literal("The stone does not stir for you.").withStyle(ChatFormatting.GRAY), true);
                return InteractionResult.CONSUME;
            }
            if (!(level.getBlockEntity(pos) instanceof Tile tile)) return InteractionResult.CONSUME;

            if (sp.isShiftKeyDown()) {                       // the ability's own menu, not a copy of it
                if (!Lotm.dreamMenu(sp))
                    sp.displayClientMessage(Component.literal("The dream would not open.").withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS;
            }
            tile.castAround(sp);
            return InteractionResult.CONSUME;
        }

        @Override
        protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack held, BlockState st, Level level, BlockPos pos,
                                              Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
            if (level.isClientSide) return net.minecraft.world.ItemInteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer sp) || !sp.hasPermissions(2))
                return net.minecraft.world.ItemInteractionResult.CONSUME;
            if (!(level.getBlockEntity(pos) instanceof Tile tile))
                return net.minecraft.world.ItemInteractionResult.CONSUME;
            // Holding the ability, LOTM's own use fired first and stepped the selection on.
            // The block answers the click itself now, whatever is in the hand.
            if (sp.isShiftKeyDown() || held.getDescriptionId().contains("dreamshaping")) {
                if (!Lotm.dreamMenu(sp))
                    sp.displayClientMessage(Component.literal("The dream would not open.").withStyle(ChatFormatting.RED), true);
                return net.minecraft.world.ItemInteractionResult.sidedSuccess(false);
            }
            tile.keep = !tile.keep;
            tile.setChanged();
            sp.displayClientMessage(Component.literal(tile.keep
                    ? "They will keep what they find in the dream."
                    : "What they find in the dream stays there.").withStyle(ChatFormatting.AQUA), true);
            return net.minecraft.world.ItemInteractionResult.CONSUME;
        }
    }

    // ---------------------------------------------------------------- the block entity

    public static final class Tile extends BlockEntity {
        boolean keep;
        /** Who this stone put under, and what they were carrying on the last tick they were under. */
        private final Map<UUID, List<ItemStack>> under = new HashMap<>();
        private int tick;

        public Tile(BlockPos pos, BlockState st) { super(TYPE.get(), pos, st); }

        /** Casts the selected dream at everyone in range, one at a time. */
        void castAround(ServerPlayer by) {
            if (!(level instanceof ServerLevel lv)) return;
            int n = 0;
            for (ServerPlayer p : lv.getPlayers(q -> q.distanceToSqr(worldPosition.getX() + .5,
                    worldPosition.getY() + .5, worldPosition.getZ() + .5) < REACH * REACH)) {
                if (p == by || p.isSpectator()) continue;
                if (Lotm.castDreamOn(by, p)) {
                    n++;
                    if (keep) under.put(p.getUUID(), new ArrayList<>());
                }
            }
            by.displayClientMessage(n == 0
                    ? Component.literal("Nobody here could be put under.").withStyle(ChatFormatting.GRAY)
                    : Component.literal("The dream takes " + n + (n == 1 ? " sleeper." : " sleepers."))
                            .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            setChanged();
        }

        /**
         * Watches whoever this stone put under. While they dream their inventory is remembered;
         * when they wake, anything the waking took off them is handed straight back.
         */
        public static void tick(Level level, BlockPos pos, BlockState st, Tile t) {
            if (level.isClientSide || t.under.isEmpty() || !(level instanceof ServerLevel lv)) return;
            if (++t.tick % 10 != 0) return;
            MinecraftServer server = lv.getServer();
            var done = new ArrayList<UUID>();
            for (var en : t.under.entrySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
                if (p == null) { done.add(en.getKey()); continue; }
                if (Lotm.isDreaming(p)) {                       // still under: remember what they hold
                    List<ItemStack> snap = new ArrayList<>();
                    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                        ItemStack s = p.getInventory().getItem(i);
                        if (!s.isEmpty()) snap.add(s.copy());
                    }
                    en.setValue(snap);
                    continue;
                }
                giveBack(p, en.getValue());                      // awake: put back what the waking took
                done.add(en.getKey());
            }
            for (UUID id : done) t.under.remove(id);
            if (!done.isEmpty()) t.setChanged();
        }

        /** Hands back every stack from the last dreaming tick that the player no longer has. */
        private static void giveBack(ServerPlayer p, List<ItemStack> had) {
            if (had == null || had.isEmpty()) return;
            var inv = p.getInventory();
            for (ItemStack want : had) {
                int have = 0;
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack s = inv.getItem(i);
                    if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, want)) have += s.getCount();
                }
                int missing = want.getCount() - have;
                if (missing <= 0) continue;
                ItemStack back = want.copy();
                back.setCount(missing);
                if (!inv.add(back)) p.drop(back, false);
            }
        }

        @Override
        protected void saveAdditional(CompoundTag t, net.minecraft.core.HolderLookup.Provider reg) {
            super.saveAdditional(t, reg);
            t.putBoolean("keep", keep);
        }

        @Override
        protected void loadAdditional(CompoundTag t, net.minecraft.core.HolderLookup.Provider reg) {
            super.loadAdditional(t, reg);
            keep = t.getBoolean("keep");
        }
    }
}
