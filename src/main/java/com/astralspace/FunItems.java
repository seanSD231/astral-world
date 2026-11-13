package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fun items: three shrooms that reshape whoever is right clicked (or yourself) for a minute.
 * All three only change how the player is drawn ({@link FunClient} springs them into the new shape and wobbles them):
 * the hitbox, reach and eye height stay exactly as they were.
 */
public final class FunItems {
    private FunItems() {}
    private static final int MINUTE = 20 * 60;
    private static final ResourceLocation SHRINK_ID = ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "shroom_shrink");

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AstralMod.MOD_ID);
    public static final DeferredItem<Item> SHRINK = ITEMS.register("shrink_shroom", () -> new Shroom(0));
    public static final DeferredItem<Item> SLIM = ITEMS.register("slim_shroom", () -> new Shroom(1));
    public static final DeferredItem<Item> WIDE = ITEMS.register("wide_shroom", () -> new Shroom(2));
    public static void register(IEventBus modBus) { ITEMS.register(modBus); }

    /** The four fun items a rank may pick from, by registry id. */
    public static final String[] PICKS = {"astral_world:shrink_shroom", "astral_world:slim_shroom", "astral_world:wide_shroom",
        "astral_world:bubble_wand", "astral_world:confetti_popper"};

    // ------------------------------------------------------------------ the shapes
    /** Who is currently reshaped: player id -> {wide, tall, the tick it ends}. */
    private static final Map<Integer, float[]> SHAPES = new HashMap<>();
    /** Client side: the same, for drawing. */
    public static final Map<Integer, float[]> CLIENT_SHAPES = new HashMap<>();

    public static float[] shapeOf(int id) { return CLIENT_SHAPES.get(id); }

    private static void setShape(ServerPlayer p, float wide, float tall, int ticks) {
        SHAPES.put(p.getId(), new float[]{wide, tall, p.getServer().getTickCount() + ticks});
        send(p, wide, tall);
    }

    private static void send(ServerPlayer p, float wide, float tall) {
        net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(new Net.ShapePayload(p.getId(), wide, tall));
    }

    /** Expires the shapes, and says again what is still going so anyone who just logged in sees it too. */
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        if (SHAPES.isEmpty()) return;
        int now = e.getServer().getTickCount();
        for (var it = SHAPES.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            ServerPlayer p = null;
            for (ServerPlayer q : e.getServer().getPlayerList().getPlayers()) if (q.getId() == en.getKey()) { p = q; break; }
            if (p == null) { it.remove(); continue; }
            if (now >= en.getValue()[2]) { it.remove(); send(p, 1f, 1f); p.level().playSound(null, p.blockPosition(), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.PLAYERS, 1.3f, .7f); p.displayClientMessage(Component.literal("You are yourself again.").withStyle(ChatFormatting.GRAY), true); continue; }
            if (now % 40 == 0) send(p, en.getValue()[0], en.getValue()[1]);
        }
    }

    // ------------------------------------------------------------------ the item
    static final class Shroom extends Item {
        private final int kind;   // 0 shrink, 1 thin, 2 wide
        Shroom(int kind) { super(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)); this.kind = kind; }

        private String what() { return kind == 0 ? "shrink" : kind == 1 ? "go very thin" : "go very wide"; }

        @Override
        public void appendHoverText(ItemStack st, Item.TooltipContext ctx, List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
            tip.add(Component.literal("Fun Item").withStyle(ChatFormatting.YELLOW));
            tip.add(Component.literal("Right click to make yourself " + what() + " for a minute, or right click someone else.").withStyle(ChatFormatting.GRAY));
        }

        @Override
        public InteractionResult interactLivingEntity(ItemStack st, Player user, LivingEntity target, InteractionHand hand) {
            if (user.level().isClientSide) return InteractionResult.SUCCESS;
            if (target instanceof ServerPlayer tp) { apply(tp, kind); if (!user.getAbilities().instabuild) st.shrink(1); return InteractionResult.CONSUME; }
            return InteractionResult.PASS;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
            ItemStack st = user.getItemInHand(hand);
            if (!level.isClientSide && user instanceof ServerPlayer sp) { apply(sp, kind); if (!sp.getAbilities().instabuild) st.shrink(1); }
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        }
    }

    /** {wide, tall} for each shroom: tiny all over, a beanpole, a pancake. Only how the player is drawn changes, never the hitbox. */
    private static final float[][] SHAPE = {{.25f, .25f}, {.16f, 1.3f}, {3.4f, .7f}};

    /** Reshapes a player for a minute, with a noise and a puff. A second shroom simply replaces the first. */
    public static void apply(ServerPlayer p, int kind) {
        AttributeInstance old = p.getAttribute(Attributes.SCALE);
        if (old != null) old.removeModifier(SHRINK_ID);   // the shrink used to be a real scale change: clear any left over
        setShape(p, SHAPE[kind][0], SHAPE[kind][1], MINUTE);
        if (p.level() instanceof net.minecraft.server.level.ServerLevel lv) {
            lv.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, p.getX(), p.getY() + 1, p.getZ(), 40, .4, .8, .4, .08);
            lv.sendParticles(kind == 0 ? net.minecraft.core.particles.ParticleTypes.END_ROD : kind == 1 ? net.minecraft.core.particles.ParticleTypes.GLOW : net.minecraft.core.particles.ParticleTypes.ITEM_SLIME,
                p.getX(), p.getY() + 1, p.getZ(), 30, .5, .9, .5, .05);
            // a run of notes: up the scale for getting small or thin, down it for getting wide
            for (int i = 0; i < 8; i++) {
                final float pitch = kind == 2 ? 1.6f - i * .15f : .6f + i * .19f;
                UniqAbilities.later(i * 2, () -> lv.playSound(null, p.getX(), p.getY(), p.getZ(),
                    (kind == 2 ? SoundEvents.NOTE_BLOCK_DIDGERIDOO : kind == 1 ? SoundEvents.NOTE_BLOCK_FLUTE : SoundEvents.NOTE_BLOCK_CHIME).value(), SoundSource.PLAYERS, 1.2f, pitch));
            }
            UniqAbilities.later(16, () -> lv.playSound(null, p.getX(), p.getY(), p.getZ(),
                kind == 2 ? SoundEvents.SLIME_SQUISH : kind == 1 ? SoundEvents.FISHING_BOBBER_RETRIEVE : SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.PLAYERS, 1.4f, kind == 2 ? .5f : 1.6f));
        }
        p.displayClientMessage(Component.literal(kind == 0 ? "You shrink to almost nothing." : kind == 1 ? "You stretch into a beanpole." : "You flatten out like a pancake.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    // ------------------------------------------------------------------ the rank pick
    private static final String TAG = "astral_fun_pick";

    /** How many days a rank waits between picks (7, 3 or 1); 0 means the rank gets none. */
    public static int period(ServerPlayer p) { return Math.max(0, Ranks.integer(p, "astral_world.fun_items", 0)); }

    /** Days still to wait before this player may pick again; 0 means now. */
    public static int waitLeft(ServerPlayer p) {
        int days = period(p);
        if (days <= 0) return 0;
        long now = java.time.LocalDate.now().toEpochDay(), last = p.getPersistentData().getLong(TAG);
        return last > 0 && now - last < days ? (int) (days - (now - last)) : 0;
    }

    /** Opens the pick menu, the same way the weekly packs do. */
    public static void open(ServerPlayer p) {
        int days = period(p);
        if (days <= 0) { p.sendSystemMessage(Component.literal("Your rank does not get fun items.").withStyle(ChatFormatting.GRAY)); return; }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new Net.FunOpenPayload(java.util.List.of(PICKS), days, waitLeft(p)));
    }

    /** Tells a player who logs in that a fun item is waiting (click the message to open the menu), like the weekly packs do. */
    public static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        UniqAbilities.later(45, () -> {           // a moment, so FTB Ranks has the player
            if (period(p) <= 0 || waitLeft(p) > 0 || !p.isAlive()) return;
            p.sendSystemMessage(Component.literal("You have a fun item to pick. ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("[Click here or use /funitem]").withStyle(s -> s.withColor(ChatFormatting.YELLOW).withUnderlined(true)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/funitem")))));
        });
    }

    public static void pick(ServerPlayer p, String id) {
        int days = period(p);
        if (days <= 0) { p.sendSystemMessage(Component.literal("Your rank does not get fun items.").withStyle(ChatFormatting.GRAY)); return; }
        long now = java.time.LocalDate.now().toEpochDay(), last = p.getPersistentData().getLong(TAG);
        if (last > 0 && now - last < days) {
            long left = days - (now - last);
            p.sendSystemMessage(Component.literal("You can pick again in " + left + " day" + (left == 1 ? "" : "s") + ".").withStyle(ChatFormatting.GRAY));
            return;
        }
        boolean ok = false;
        for (String s : PICKS) if (s.equals(id)) ok = true;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (!ok || item == null || item == net.minecraft.world.item.Items.AIR) {
            p.sendSystemMessage(Component.literal("Pick one of: shrink_shroom, slim_shroom, wide_shroom, bubble_wand, confetti_popper.").withStyle(ChatFormatting.GRAY));
            return;
        }
        ItemStack st = new ItemStack(item);
        if (!p.getInventory().add(st)) p.drop(st, false);
        p.getPersistentData().putLong(TAG, now);
        p.sendSystemMessage(Component.literal("Taken. Your next fun item is ready in " + days + " day" + (days == 1 ? "" : "s") + ".").withStyle(ChatFormatting.GOLD));
    }

    public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e) {
        var root = net.minecraft.commands.Commands.literal("funitem").requires(c -> c.getEntity() instanceof ServerPlayer)
            .executes(c -> { open((ServerPlayer) c.getSource().getEntity()); return 1; })
            .then(net.minecraft.commands.Commands.literal("reset").requires(c -> c.hasPermission(2))
                .then(net.minecraft.commands.Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player()).executes(c -> {
                    ServerPlayer t = net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "player");
                    t.getPersistentData().remove(TAG);
                    c.getSource().sendSuccess(() -> Component.literal(t.getGameProfile().getName() + " can pick a fun item again."), true);
                    return 1;
                })));
        for (String s : PICKS) {
            String name = s.substring(s.indexOf(':') + 1);
            root = root.then(net.minecraft.commands.Commands.literal(name).executes(c -> { pick((ServerPlayer) c.getSource().getEntity(), s); return 1; }));
        }
        e.getDispatcher().register(root);
    }
}
