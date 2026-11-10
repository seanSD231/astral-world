package com.astralspace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The floor of the Astral World: an invisible, unbreakable block of our own (instead of the vanilla barrier block, which
 * many mods and commands remove). It cannot be broken (even by creative players, unless they are ops), blown up or pushed,
 * and any floor block that goes missing near a player is put back.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class AstralFloor {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AstralMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AstralMod.MOD_ID);

    public static final DeferredBlock<Block> FLOOR = BLOCKS.register("astral_floor", () -> new FloorBlock(
            BlockBehaviour.Properties.of().strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<Item> FLOOR_ITEM = ITEMS.register("astral_floor",
            () -> new BlockItem(FLOOR.get(), new Item.Properties()));

    /** The osu cabinet: use it to play the click game for fun, make levels, share them and link them to music discs. */
    public static final DeferredBlock<Block> CABINET = BLOCKS.register("osu_cabinet", () -> new OsuCabinetBlock(
            BlockBehaviour.Properties.of().strength(2.0F, 6.0F).requiresCorrectToolForDrops().lightLevel(s -> 7)));
    public static final DeferredItem<Item> CABINET_ITEM = ITEMS.register("osu_cabinet", () -> new BlockItem(CABINET.get(), new Item.Properties()));

    /** Testing: a creative-only item. Every hit on the Mother Tree takes 10% of her maximum health (see EndGame.smite). */
    public static final DeferredItem<Item> SMITER = ITEMS.register("tree_smiter",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)) {
                @Override public boolean isFoil(net.minecraft.world.item.ItemStack stack) { return true; }
            });

    /** Uniquenesses: hold right click for 5 seconds to take one in (through LOTM's own accommodation). {item id, LOTM pathway id, name, pathway title} */
    public static final String[][] UNIQ = {
        {"starry_sniper", "apprentice", "Eyes of the Door", "Door", ""},
        {"quill_of_alzuhod", "spectator", "Quill of Alzuhod", "Visionary", ""},
        {"shadow_sword", "secrets_supplicant", "Sword of Shadow", "Hanged Man", "3d"},
        {"spear_of_sun", "bard", "Spear of Sun and Justice", "Sun", "3d"},
        {"storm_katana", "sailor", "Storm Katana", "Tyrant", "3d"},
        {"bone_greatsword", "corpse_collector", "Great Bone Sword", "Death", "3d"},
        {"twilight_longsword", "warrior", "Longsword of Twilight", "Warrior", "3d"},
        {"snake_rifle", "monster", "Wheel of Fortune Rifle", "Monster", "3d"},
        {"chained_bow", "prisoner", "Chained Bow", "Chained", "3d"}};
    public static final java.util.List<DeferredItem<Item>> UNIQUENESSES = new java.util.ArrayList<>();
    static { for (String[] u : UNIQ) UNIQUENESSES.add(ITEMS.register(u[0], () -> u[0].equals("starry_sniper") ? new DoorEyes() : new Uniqueness(u[1], u[3], u[4].equals("3d")))); }
    /** The Sword of Shadow's own entry, and the shield that appears in the off hand while it is held. */
    public static final DeferredItem<Item> SHADOW_SWORD_ITEM = DeferredItem.createItem(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("astral_world", "shadow_sword"));
    public static final DeferredItem<Item> SHADOW_SHIELD = ITEMS.register("shadow_shield", ShadowShield::new);

    /** Invisible, like the barrier block. */
    private static final class FloorBlock extends Block {
        FloorBlock(Properties p) { super(p); }

        @Override
        protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    }

    private AstralFloor() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES) { e.accept(SMITER.get()); for (var u : UNIQUENESSES) e.accept(u.get()); e.accept(SHADOW_SHIELD.get()); }
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) e.accept(CABINET_ITEM.get());
        });
    }

    private static boolean isAstral(net.minecraft.world.level.LevelAccessor level) {
        return level instanceof ServerLevel s && s.dimension().equals(AstralServer.ASTRAL);
    }

    /** Nobody breaks the floor, except an op in creative mode (so admins can still edit it). */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.getState().is(FLOOR.get())) return;
        var p = event.getPlayer();
        if (p instanceof ServerPlayer sp && sp.isCreative() && sp.hasPermissions(2)) return;
        event.setCanceled(true);
    }

    /** Explosions never take the floor with them. */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        var level = event.getLevel();
        event.getAffectedBlocks().removeIf(pos -> level.getBlockState(pos).is(FLOOR.get()));
    }

    /** Every two seconds, the floor around each player in the Astral World is checked and any missing piece is put back. */
    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 40 != 0) return;
        ServerLevel astral = server.getLevel(AstralServer.ASTRAL);
        if (astral == null) return;
        BlockState floor = FLOOR.get().defaultBlockState();
        int minY = astral.getMinBuildHeight();
        for (ServerPlayer p : astral.players()) {
            int cx = (int) Math.floor(p.getX()), cz = (int) Math.floor(p.getZ());
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int dx = -12; dx <= 12; dx++) {
                for (int dz = -12; dz <= 12; dz++) {
                    pos.set(cx + dx, minY, cz + dz);
                    if (!astral.hasChunkAt(pos)) continue;
                    if (!astral.getBlockState(pos).is(FLOOR.get())) astral.setBlock(pos, floor, 2);
                }
            }
        }
    }
}
