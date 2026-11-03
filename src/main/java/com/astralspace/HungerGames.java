package com.astralspace;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;
import java.util.function.Supplier;

/**
 * A hunger games event in the raid arena (without the tree). An op starts it; every player is asked yes or no; the yeses are carried to
 * evenly spaced places along the edge of the grass, chests are spread over the arena (many in the middle), and the last one standing wins.
 * Everything a player had (place, inventory, health, experience, sequence) is put back afterwards.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class HungerGames {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private enum Phase { OFF, INVITE, COUNTDOWN, RUNNING, ENDING }

    private record Saved(ResourceKey<Level> dim, double x, double y, double z, float yaw, float pitch, GameType mode, ListTag inv,
                         float hp, int food, float sat, int xpLevel, float xpProg, int xpTotal, int seq, ListTag curios) { }

    private static final double ARENA_R = EndGame.ARENA_R, RING_R = 270;
    private static final int INVITE_TICKS = 20 * 60, COUNTDOWN_TICKS = 20 * 10, OUTER_CHESTS = 170, CENTRE_CHESTS = 50, CHURCH_CHESTS = 60;

    private static Phase phase = Phase.OFF;
    private static MinecraftServer server;
    private static long phaseEnd = 0;
    private static boolean seqEvent = false, seqKeep = true;
    private static int seqSet = 9;
    private static double groundY = 4;
    private static final Map<UUID, Boolean> answers = new LinkedHashMap<>();
    private static final Set<UUID> alive = new LinkedHashSet<>(), players = new LinkedHashSet<>();
    /** Everyone taken into the games and not yet put back (also players who left in the middle: they are restored when they return). */
    private static final Map<UUID, Saved> saved = new HashMap<>();
    private static final List<BlockPos> chests = new ArrayList<>();
    private static int outerLeft = 0, centreLeft = 0, churchLeft = 0;
    private static final Random rnd = new Random();

    /** True while this player is a living contestant in a running game (the arena's no-damage rule does not apply to them). */
    public static boolean fighting(ServerPlayer p) { return (phase == Phase.RUNNING || phase == Phase.ENDING) && alive.contains(p.getUUID()); }

    // ------------------------------------------------------------------ commands

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hungergames")
                .then(Commands.literal("start").requires(s -> s.hasPermission(2))
                        .executes(ctx -> start(ctx.getSource().getServer(), false, true, 9, ctx.getSource()))
                        .then(Commands.literal("noseq").executes(ctx -> start(ctx.getSource().getServer(), false, true, 9, ctx.getSource())))
                        .then(Commands.literal("seq")
                                .then(Commands.literal("keep").executes(ctx -> start(ctx.getSource().getServer(), true, true, 9, ctx.getSource())))
                                .then(Commands.literal("set").then(Commands.argument("sequence", IntegerArgumentType.integer(0, 9))
                                        .executes(ctx -> start(ctx.getSource().getServer(), true, false, IntegerArgumentType.getInteger(ctx, "sequence"), ctx.getSource()))))))
                .then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    if (phase == Phase.OFF && saved.isEmpty()) { ctx.getSource().sendFailure(Component.literal("No hunger games are running.")); return 0; }
                    finish(ctx.getSource().getServer(), "The hunger games were stopped.");
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(phase == Phase.OFF ? "No hunger games running." : "Hunger games: " + phase + ", " + alive.size() + " alive of " + players.size()), false);
                    return 1;
                }))
                .then(Commands.literal("join").then(Commands.argument("answer", StringArgumentType.word()).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (phase != Phase.INVITE) { p.sendSystemMessage(Component.literal("There is no hunger games invitation right now.").withStyle(ChatFormatting.GRAY)); return 0; }
                    String a = StringArgumentType.getString(ctx, "answer");
                    boolean yes = a.equalsIgnoreCase("yes");
                    if (!yes && !a.equalsIgnoreCase("no")) { p.sendSystemMessage(Component.literal("Answer yes or no.")); return 0; }
                    answers.put(p.getUUID(), yes);
                    p.sendSystemMessage(Component.literal(yes ? "You are in. You will be carried to the arena when the invitation ends." : "You are out.").withStyle(yes ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                    return 1;
                }))));
    }

    private static int start(MinecraftServer s, boolean seqEv, boolean keep, int setTo, net.minecraft.commands.CommandSourceStack src) {
        if (phase != Phase.OFF || !saved.isEmpty()) { src.sendFailure(Component.literal("Hunger games are already running (use /hungergames stop).")); return 0; }
        String ph = AstralData.get(s).endPhase;
        if (!ph.isEmpty() && !ph.equals("classic")) { src.sendFailure(Component.literal("The end event is running; the arena is in use.")); return 0; }
        ServerLevel lv = s.getLevel(Cosmos.key("cosmos"));
        if (lv == null) { src.sendFailure(Component.literal("The arena dimension is missing.")); return 0; }
        server = s;
        seqEvent = seqEv; seqKeep = keep; seqSet = setTo;
        answers.clear(); alive.clear(); players.clear(); chests.clear();
        lv.getChunk(0, 0);
        groundY = PlanetGenerator.BASE + 1;   // the floor of the church (a roof covers the heightmap)
        outerLeft = OUTER_CHESTS; centreLeft = CENTRE_CHESTS; churchLeft = CHURCH_CHESTS;
        phase = Phase.INVITE;
        phaseEnd = s.getTickCount() + INVITE_TICKS;
        String rule = !seqEv ? "Not a sequence event: you lose your sequence for the games and get it back afterwards."
                : keep ? "Sequence event: you keep your sequence." : "Sequence event: everyone plays at sequence " + setTo + " and is put back afterwards.";
        MutableComponent yes = Component.literal("[ YES ]").withStyle(st -> st.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/hungergames join yes")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Join the hunger games"))));
        MutableComponent no = Component.literal("[ NO ]").withStyle(st -> st.withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/hungergames join no")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Stay out"))));
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal("== HUNGER GAMES ==").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            p.sendSystemMessage(Component.literal("Do you want to play? Last one standing wins. You are carried to the arena and everything you carry is put back afterwards.").withStyle(ChatFormatting.YELLOW));
            p.sendSystemMessage(Component.literal(rule).withStyle(ChatFormatting.GRAY));
            p.sendSystemMessage(Component.empty().append(yes).append(Component.literal("   ")).append(no).append(Component.literal("   (60 seconds)").withStyle(ChatFormatting.DARK_GRAY)));
        }
        LOGGER.info("[hungergames] invitation sent ({})", rule);
        return 1;
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (phase == Phase.OFF) return;
        MinecraftServer s = event.getServer();
        server = s;
        ServerLevel lv = s.getLevel(Cosmos.key("cosmos"));
        if (lv == null) { finish(s, "The arena is gone; the hunger games ended."); return; }
        long now = s.getTickCount();
        switch (phase) {
            case INVITE -> {
                for (int i = 0; i < 6 && (outerLeft > 0 || centreLeft > 0 || churchLeft > 0); i++) placeChest(lv);   // chests are put out while people answer
                boolean allAnswered = s.getPlayerList().getPlayers().stream().allMatch(p -> answers.containsKey(p.getUUID()));
                if ((allAnswered || now >= phaseEnd) && outerLeft == 0 && centreLeft == 0 && churchLeft == 0) begin(s, lv);
                else if (now >= phaseEnd + 20 * 30) begin(s, lv);   // never wait forever for the chests
            }
            case COUNTDOWN -> {
                long left = phaseEnd - now;
                if (left % 20 == 0 && left > 0) {
                    int sec = (int) (left / 20);
                    for (ServerPlayer p : onlinePlayers(s)) title(p, sec <= 3 ? ChatFormatting.RED : ChatFormatting.GOLD, String.valueOf(sec), "The games begin");
                }
                if (left <= 0) go(s);
            }
            case RUNNING -> {
                if (now % 20 == 0) running(s, lv);
            }
            case ENDING -> { if (now >= phaseEnd) finish(s, null); }
            default -> { }
        }
    }

    private static List<ServerPlayer> onlinePlayers(MinecraftServer s) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID u : players) { ServerPlayer p = s.getPlayerList().getPlayer(u); if (p != null) out.add(p); }
        return out;
    }

    private static void title(ServerPlayer p, ChatFormatting c, String big, String small) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 18, 4));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(small).withStyle(ChatFormatting.GRAY)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(big).withStyle(c, ChatFormatting.BOLD)));
    }

    // ------------------------------------------------------------------ chests and loot

    private static void placeChest(ServerLevel lv) {
        int kind = centreLeft > 0 ? 0 : churchLeft > 0 ? 1 : 2;   // 0 middle, 1 inside the church, 2 anywhere on the stone
        double maxR = PlanetGenerator.PLAT_R - 10;
        for (int tries = 0; tries < 60; tries++) {
            int x, z;
            if (kind == 0) {
                double a = rnd.nextDouble() * Math.PI * 2, r = 5 + rnd.nextDouble() * 38;
                x = (int) Math.round(Math.cos(a) * r); z = (int) Math.round(Math.sin(a) * r);
            } else if (kind == 1) {
                x = rnd.nextInt(681) - 340; z = rnd.nextInt(681) - 340;
                if (!PlanetGenerator.churchInside(x, z, 3)) continue;
            } else {
                double a = rnd.nextDouble() * Math.PI * 2, r = Math.max(45, Math.sqrt(rnd.nextDouble()) * maxR);
                x = (int) Math.round(Math.cos(a) * r); z = (int) Math.round(Math.sin(a) * r);
            }
            BlockPos p = surface(lv, x, z);
            if (p == null || chests.contains(p)) continue;
            Direction face = Direction.Plane.HORIZONTAL.getRandomDirection(RandomSource.create(rnd.nextLong()));
            lv.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, face), 3);
            if (lv.getBlockEntity(p) instanceof ChestBlockEntity c) fill(c, kind == 0);
            chests.add(p);
            if (kind == 0) centreLeft--; else if (kind == 1) churchLeft--; else outerLeft--;
            return;
        }
        if (kind == 0) centreLeft--; else if (kind == 1) churchLeft--; else outerLeft--;   // no room found: skip this one
    }

    /** A free floor spot at (x, z): about the height of the arena floor, solid and dry underneath. Null inside walls, on top of them or in water. */
    private static BlockPos surface(ServerLevel lv, int x, int z) {
        lv.getChunk(x >> 4, z >> 4);
        BlockPos p = new BlockPos(x, (int) groundY, z);
        BlockState below = lv.getBlockState(p.below());
        if (!below.isSolid() || !below.getFluidState().isEmpty()) return null;
        if (!lv.getBlockState(p).isAir() || !lv.getBlockState(p.above()).isAir()) return null;
        return p;
    }

    private static final class Entry {
        final int weight; final Supplier<ItemStack> item;
        Entry(int w, Supplier<ItemStack> i) { weight = w; item = i; }
    }

    private static Entry e(int w, Supplier<ItemStack> s) { return new Entry(w, s); }
    private static ItemStack st(net.minecraft.world.item.Item i, int n) { return new ItemStack(i, n); }
    private static ItemStack potion(net.minecraft.world.item.Item base, net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> p) { return PotionContents.createItemStack(base, p); }

    private static final List<Entry> COMMON = List.of(
            e(10, () -> st(Items.STONE_SWORD, 1)), e(5, () -> st(Items.IRON_SWORD, 1)), e(4, () -> st(Items.STONE_AXE, 1)), e(3, () -> st(Items.IRON_AXE, 1)),
            e(6, () -> st(Items.BOW, 1)), e(9, () -> st(Items.ARROW, 6 + rnd.nextInt(10))), e(3, () -> st(Items.CROSSBOW, 1)),
            e(5, () -> st(Items.LEATHER_HELMET, 1)), e(5, () -> st(Items.LEATHER_CHESTPLATE, 1)), e(5, () -> st(Items.LEATHER_LEGGINGS, 1)), e(5, () -> st(Items.LEATHER_BOOTS, 1)),
            e(4, () -> st(Items.CHAINMAIL_CHESTPLATE, 1)), e(4, () -> st(Items.CHAINMAIL_LEGGINGS, 1)), e(3, () -> st(Items.IRON_HELMET, 1)), e(3, () -> st(Items.IRON_BOOTS, 1)),
            e(3, () -> st(Items.IRON_CHESTPLATE, 1)), e(3, () -> st(Items.IRON_LEGGINGS, 1)), e(4, () -> st(Items.SHIELD, 1)),
            e(12, () -> st(Items.BREAD, 3 + rnd.nextInt(4))), e(9, () -> st(Items.COOKED_BEEF, 2 + rnd.nextInt(4))), e(8, () -> st(Items.APPLE, 2 + rnd.nextInt(3))), e(5, () -> st(Items.GOLDEN_CARROT, 2)),
            e(3, () -> st(Items.GOLDEN_APPLE, 1)), e(3, () -> st(Items.ENDER_PEARL, 1)), e(5, () -> st(Items.COBBLESTONE, 16 + rnd.nextInt(24))), e(4, () -> st(Items.OAK_PLANKS, 16 + rnd.nextInt(16))),
            e(3, () -> st(Items.WATER_BUCKET, 1)), e(3, () -> st(Items.COBWEB, 2 + rnd.nextInt(3))), e(3, () -> st(Items.FISHING_ROD, 1)), e(3, () -> st(Items.SNOWBALL, 8)),
            e(2, () -> st(Items.FLINT_AND_STEEL, 1)), e(4, () -> potion(Items.SPLASH_POTION, Potions.HEALING)), e(3, () -> potion(Items.SPLASH_POTION, Potions.HARMING)),
            e(3, () -> potion(Items.POTION, Potions.SWIFTNESS)), e(2, () -> st(Items.TNT, 2)));

    private static final List<Entry> RICH = List.of(
            e(7, () -> st(Items.DIAMOND_SWORD, 1)), e(4, () -> st(Items.DIAMOND_AXE, 1)), e(1, () -> st(Items.NETHERITE_SWORD, 1)), e(2, () -> st(Items.TRIDENT, 1)),
            e(5, () -> st(Items.DIAMOND_HELMET, 1)), e(5, () -> st(Items.DIAMOND_CHESTPLATE, 1)), e(5, () -> st(Items.DIAMOND_LEGGINGS, 1)), e(5, () -> st(Items.DIAMOND_BOOTS, 1)),
            e(3, () -> st(Items.NETHERITE_CHESTPLATE, 1)), e(3, () -> st(Items.IRON_CHESTPLATE, 1)), e(3, () -> st(Items.IRON_LEGGINGS, 1)),
            e(6, () -> st(Items.BOW, 1)), e(8, () -> st(Items.ARROW, 16 + rnd.nextInt(16))), e(4, () -> st(Items.CROSSBOW, 1)), e(4, () -> st(Items.SHIELD, 1)),
            e(8, () -> st(Items.GOLDEN_APPLE, 1 + rnd.nextInt(3))), e(2, () -> st(Items.ENCHANTED_GOLDEN_APPLE, 1)), e(2, () -> st(Items.TOTEM_OF_UNDYING, 1)),
            e(7, () -> st(Items.COOKED_BEEF, 6)), e(5, () -> st(Items.ENDER_PEARL, 2 + rnd.nextInt(2))), e(5, () -> potion(Items.SPLASH_POTION, Potions.STRONG_HEALING)),
            e(4, () -> potion(Items.SPLASH_POTION, Potions.STRONG_HARMING)), e(3, () -> potion(Items.POTION, Potions.LONG_SWIFTNESS)), e(3, () -> potion(Items.POTION, Potions.STRONG_STRENGTH)),
            e(3, () -> st(Items.TNT, 4)), e(2, () -> st(Items.FLINT_AND_STEEL, 1)), e(3, () -> st(Items.WATER_BUCKET, 1)), e(3, () -> st(Items.COBWEB, 4)));

    private static ItemStack pick(List<Entry> table) {
        int total = 0;
        for (Entry en : table) total += en.weight;
        int r = rnd.nextInt(total);
        for (Entry en : table) { r -= en.weight; if (r < 0) return en.item.get(); }
        return ItemStack.EMPTY;
    }

    private static void fill(ChestBlockEntity c, boolean rich) {
        int n = rich ? 6 + rnd.nextInt(5) : 3 + rnd.nextInt(4);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 27; i++) slots.add(i);
        Collections.shuffle(slots, rnd);
        for (int i = 0; i < n; i++) c.setItem(slots.get(i), pick(rich ? RICH : COMMON));
        c.setChanged();
    }

    // ------------------------------------------------------------------ starting

    private static void begin(MinecraftServer s, ServerLevel lv) {
        List<ServerPlayer> in = new ArrayList<>();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (Boolean.TRUE.equals(answers.get(p.getUUID()))) in.add(p);
        if (in.size() < 2) {
            for (ServerPlayer p : s.getPlayerList().getPlayers()) p.sendSystemMessage(Component.literal("Not enough players said yes (" + in.size() + "): the hunger games are cancelled.").withStyle(ChatFormatting.RED));
            clearArena(lv);
            reset();
            return;
        }
        int n = in.size();
        for (int i = 0; i < n; i++) {
            ServerPlayer p = in.get(i);
            saved.put(p.getUUID(), snapshot(p));
            players.add(p.getUUID());
            alive.add(p.getUUID());
            // wipe: nothing carried in, nothing left over
            p.getInventory().clearContent();
            clearCurios(p);
            p.removeAllEffects();
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(5f);
            p.setExperienceLevels(0);
            p.experienceProgress = 0f; p.totalExperience = 0;
            p.connection.send(new ClientboundSetExperiencePacket(0f, 0, 0));
            applySeq(p);
            double[] spot = ringSpot(lv, i, n);
            double dx = -spot[0], dz = -spot[2];
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            p.setGameMode(GameType.SURVIVAL);
            p.teleportTo(lv, spot[0], spot[1], spot[2], yaw, 0f);
            p.setInvulnerable(true);
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, COUNTDOWN_TICKS + 20, 7, false, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.JUMP, COUNTDOWN_TICKS + 20, 128, false, false, false));
            p.sendSystemMessage(Component.literal("You are in the arena. The games begin in 10 seconds. Chests are all over the arena; the middle has the most.").withStyle(ChatFormatting.GOLD));
        }
        phase = Phase.COUNTDOWN;
        phaseEnd = s.getTickCount() + COUNTDOWN_TICKS;
        LOGGER.info("[hungergames] {} players, {} chests", n, chests.size());
    }

    private static Saved snapshot(ServerPlayer p) {
        return new Saved(p.level().dimension(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(), p.gameMode.getGameModeForPlayer(),
                p.getInventory().save(new ListTag()), p.getHealth(), p.getFoodData().getFoodLevel(), p.getFoodData().getSaturationLevel(),
                p.experienceLevel, p.experienceProgress, p.totalExperience, Lotm.sequence(p), saveCurios(p));
    }

    /** Curios slots (rings, charms, etc.) by reflection, so Curios stays optional. Null when Curios is not installed. */
    private static net.neoforged.neoforge.items.IItemHandlerModifiable curiosOf(ServerPlayer p) {
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object opt = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class).invoke(null, p);
            if (!(opt instanceof Optional<?> o) || o.isEmpty()) return null;
            Object h = o.get();
            Object eq = h.getClass().getMethod("getEquippedCurios").invoke(h);
            return eq instanceof net.neoforged.neoforge.items.IItemHandlerModifiable m ? m : null;
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Throwable t) {
            LOGGER.warn("[hungergames] could not reach Curios: {}", t.toString());
            return null;
        }
    }

    private static ListTag saveCurios(ServerPlayer p) {
        ListTag out = new ListTag();
        var h = curiosOf(p);
        if (h == null) return out;
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack st = h.getStackInSlot(i);
            if (st.isEmpty()) continue;
            net.minecraft.nbt.CompoundTag c = new net.minecraft.nbt.CompoundTag();
            c.putInt("Slot", i);
            c.put("Item", st.save(p.registryAccess()));
            out.add(c);
        }
        return out;
    }

    private static void clearCurios(ServerPlayer p) {
        var h = curiosOf(p);
        if (h == null) return;
        for (int i = 0; i < h.getSlots(); i++) h.setStackInSlot(i, ItemStack.EMPTY);
    }

    private static void loadCurios(ServerPlayer p, ListTag list) {
        var h = curiosOf(p);
        if (h == null) return;
        clearCurios(p);
        for (int i = 0; i < list.size(); i++) {
            net.minecraft.nbt.CompoundTag c = list.getCompound(i);
            int slot = c.getInt("Slot");
            if (slot < 0 || slot >= h.getSlots()) continue;
            ItemStack.parse(p.registryAccess(), c.get("Item")).ifPresent(st -> h.setStackInSlot(slot, st));
        }
    }

    private static void applySeq(ServerPlayer p) {
        int cur = Lotm.sequence(p);
        if (cur < 0) return;   // not a Beyonder: nothing to change
        if (!seqEvent) Lotm.setSequence(p, 9);
        else if (!seqKeep) Lotm.setSequence(p, seqSet);
    }

    /** Evenly spaced place number i of n along the edge of the grass: the nearest free floor to the ideal spot, searching a little inwards and sideways. */
    private static double[] ringSpot(ServerLevel lv, int i, int n) {
        double base = Math.PI * 2 * i / n;
        double span = Math.PI * 2 / n * 0.4;
        for (double r = RING_R; r >= 90; r -= 5) {
            for (int k = 0; k < 9; k++) {
                double a = base + (k == 0 ? 0 : ((k % 2 == 0 ? 1 : -1) * (k / 2 + (k % 2)) * span / 4.0));
                int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
                BlockPos p = surface(lv, x, z);
                if (p != null) return new double[]{p.getX() + 0.5, p.getY(), p.getZ() + 0.5};
            }
        }
        return new double[]{0.5 + Math.cos(base) * 60, groundY, 0.5 + Math.sin(base) * 60};
    }

    private static void go(MinecraftServer s) {
        phase = Phase.RUNNING;
        for (ServerPlayer p : onlinePlayers(s)) {
            p.setInvulnerable(false);
            p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            p.removeEffect(MobEffects.JUMP);
            title(p, ChatFormatting.GREEN, "GO", alive.size() + " players");
            p.playNotifySound(net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, net.minecraft.sounds.SoundSource.MASTER, 0.6f, 1.2f);
        }
    }

    // ------------------------------------------------------------------ during the games

    private static void running(MinecraftServer s, ServerLevel lv) {
        for (UUID u : new ArrayList<>(alive)) {
            ServerPlayer p = s.getPlayerList().getPlayer(u);
            if (p == null) { eliminate(s, u, null, "left the games"); continue; }
            if (p.level() != lv) { p.teleportTo(lv, 0.5, groundY, 0.5, 0f, 0f); continue; }
            double d = Math.hypot(p.getX(), p.getZ());
            if (d > PlanetGenerator.PLAT_R - 3) {   // the edge of the stone
                double k = (PlanetGenerator.PLAT_R - 8) / d;
                int x = (int) (p.getX() * k), z = (int) (p.getZ() * k);
                lv.getChunk(x >> 4, z >> 4);
                p.teleportTo(lv, x + 0.5, groundY, z + 0.5, p.getYRot(), p.getXRot());
                p.displayClientMessage(Component.literal("That is the edge of the arena."), true);
            }
        }
        checkWinner(s);
    }

    private static void checkWinner(MinecraftServer s) {
        if (phase != Phase.RUNNING || alive.size() > 1) return;
        String msg;
        if (alive.size() == 1) {
            ServerPlayer w = s.getPlayerList().getPlayer(alive.iterator().next());
            msg = (w != null ? w.getGameProfile().getName() : "Someone") + " wins the hunger games!";
            if (w != null) title(w, ChatFormatting.GOLD, "VICTORY", "You are the last one standing");
        } else msg = "Nobody is left standing. No winner.";
        for (ServerPlayer p : s.getPlayerList().getPlayers()) p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        phase = Phase.ENDING;
        phaseEnd = s.getTickCount() + 20 * 12;   // twelve seconds to celebrate, then everybody goes home
        for (ServerPlayer p : onlinePlayers(s)) p.sendSystemMessage(Component.literal("Everyone goes back in 12 seconds.").withStyle(ChatFormatting.GRAY));
    }

    private static void eliminate(MinecraftServer s, UUID u, ServerPlayer killer, String how) {
        if (!alive.remove(u)) return;
        ServerPlayer p = s.getPlayerList().getPlayer(u);
        String name = p != null ? p.getGameProfile().getName() : u.toString().substring(0, 8);
        if (p != null) {
            p.getInventory().dropAll();   // what they gathered stays on the field for the others
            clearCurios(p);
            p.setHealth(p.getMaxHealth());
            p.removeAllEffects();
            p.setGameMode(GameType.SPECTATOR);
            title(p, ChatFormatting.RED, "ELIMINATED", "You can watch the rest");
        }
        String m = name + " " + how + (killer != null ? " (" + killer.getGameProfile().getName() + ")" : "") + ". " + alive.size() + " remain.";
        for (ServerPlayer q : s.getPlayerList().getPlayers()) if (players.contains(q.getUUID())) q.sendSystemMessage(Component.literal(m).withStyle(ChatFormatting.RED));
        checkWinner(s);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (phase != Phase.COUNTDOWN && phase != Phase.RUNNING && phase != Phase.ENDING) return;
        if (!(event.getEntity() instanceof ServerPlayer p) || !alive.contains(p.getUUID())) return;
        event.setCanceled(true);   // no death screen, no respawn: the player becomes a spectator and is restored at the end
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer k && k != p ? k : null;
        eliminate(p.server, p.getUUID(), killer, killer != null ? "was eliminated" : "died");
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || phase == Phase.OFF || !alive.contains(p.getUUID())) return;
        eliminate(p.server, p.getUUID(), null, "left the games");
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (phase == Phase.INVITE) { p.sendSystemMessage(Component.literal("A hunger games invitation is open: /hungergames join yes").withStyle(ChatFormatting.YELLOW)); return; }
        if (!saved.containsKey(p.getUUID())) return;
        if (phase == Phase.OFF || phase == Phase.ENDING) restore(p);
        else if (players.contains(p.getUUID()) && !alive.contains(p.getUUID())) p.setGameMode(GameType.SPECTATOR);
    }

    // ------------------------------------------------------------------ the end

    private static void restore(ServerPlayer p) {
        Saved sv = saved.remove(p.getUUID());
        if (sv == null) return;
        p.setInvulnerable(false);
        p.removeAllEffects();
        p.getInventory().clearContent();
        p.getInventory().load(sv.inv());
        loadCurios(p, sv.curios());
        p.setGameMode(sv.mode());
        p.setHealth(Math.max(1f, Math.min(sv.hp(), p.getMaxHealth())));
        p.getFoodData().setFoodLevel(sv.food());
        p.getFoodData().setSaturation(sv.sat());
        p.setExperienceLevels(sv.xpLevel());
        p.experienceProgress = sv.xpProg(); p.totalExperience = sv.xpTotal();
        p.connection.send(new ClientboundSetExperiencePacket(sv.xpProg(), sv.xpTotal(), sv.xpLevel()));
        if (sv.seq() >= 0 && Lotm.sequence(p) != sv.seq()) Lotm.setSequence(p, sv.seq());
        ServerLevel to = p.server.getLevel(sv.dim());
        if (to != null) p.teleportTo(to, sv.x(), sv.y(), sv.z(), sv.yaw(), sv.pitch());
        p.inventoryMenu.broadcastChanges();
        p.sendSystemMessage(Component.literal("You are back. Everything you had has been returned.").withStyle(ChatFormatting.GREEN));
    }

    private static void clearArena(ServerLevel lv) {
        for (BlockPos p : chests) {
            if (lv.getBlockState(p).is(Blocks.CHEST)) { lv.removeBlockEntity(p); lv.setBlock(p, Blocks.AIR.defaultBlockState(), 2); }
        }
        chests.clear();
        for (Entity en : lv.getAllEntities()) if (en instanceof ItemEntity) en.discard();
    }

    private static void finish(MinecraftServer s, String why) {
        ServerLevel lv = s.getLevel(Cosmos.key("cosmos"));
        if (why != null) for (ServerPlayer p : s.getPlayerList().getPlayers()) p.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.GOLD));
        for (UUID u : new ArrayList<>(saved.keySet())) {
            ServerPlayer p = s.getPlayerList().getPlayer(u);
            if (p != null) restore(p);   // players who are offline are restored when they log in
        }
        if (lv != null) clearArena(lv);
        reset();
        LOGGER.info("[hungergames] over; {} players still to be restored on login", saved.size());
    }

    private static void reset() {
        phase = Phase.OFF;
        answers.clear(); alive.clear(); players.clear(); chests.clear();
        outerLeft = 0; centreLeft = 0; churchLeft = 0;
    }
}
