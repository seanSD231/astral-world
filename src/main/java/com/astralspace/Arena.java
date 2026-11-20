package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The ranked arenas: four of them, always open, fed by a queue.
 *
 * <p>Four rather than one because the ladder never closes and a single arena would turn a busy
 * evening into a line. When all four are busy the rest of the queue simply waits, and the moment a
 * fight finishes the next pair goes in.</p>
 *
 * <p>Three modes, each with a ladder of its own, because they are different games: carrying your
 * own gear in is a test of what you have built, and the two kitted modes are a test of how you
 * fight. In the kitted modes everything about you -- inventory, health, experience, pathway and
 * sequence -- is set aside at the door and given back at the end, so nothing you own is ever at
 * stake and nothing you own can help you.</p>
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class Arena {
    private Arena() { }

    public static final int SLOTS = 4;
    private static final int R = 72, HIGH = 30, Y = 90, GAP = 300;
    private static final int TIMEOUT = 20 * 60 * 8;

    /** The three games. Each keeps its own standings. */
    public enum Mode {
        OPEN("open", "No Restrictions", "Walk in with everything you own.", -1),
        SEQ5("seq5", "Sequence 5", "A kit and a pathway of your choosing. Nothing of yours comes in.", 5),
        SEQ2("seq2", "Sequence 2", "The same, fought far higher up.", 2);

        public final String id, title, blurb;
        /** The sequence everybody is set to, or -1 to leave them as they are. */
        public final int seq;

        Mode(String id, String title, String blurb, int seq) {
            this.id = id; this.title = title; this.blurb = blurb; this.seq = seq;
        }

        public String id() { return id; }
        public boolean kitted() { return seq >= 0; }

        public static Mode of(String id) {
            for (Mode m : values()) if (m.id.equals(id)) return m;
            return OPEN;
        }
    }

    // ---- who is waiting and who is fighting ----

    /** {@code at} and {@code dim} are where they queued from: walk far enough off and they lose the place. */
    private record Waiting(Mode mode, String pathway, long since, Vec3 at, ResourceKey<Level> dim) { }

    /** How far you may wander from where you queued before the queue gives up on you. */
    private static final double LEASH = 50.0;

    private static final class Bout {
        final Mode mode; final int slot; final UUID a, b;
        /** Null for a ladder match. A tournament hangs its bracket off this instead. */
        java.util.function.BiConsumer<UUID, UUID> then;
        final Map<UUID, Object> stash = new HashMap<>();
        final Map<UUID, Vec3> home = new HashMap<>();
        final Map<UUID, ResourceKey<Level>> homeDim = new HashMap<>();
        int age;
        Bout(Mode mode, int slot, UUID a, UUID b) { this.mode = mode; this.slot = slot; this.a = a; this.b = b; }
        UUID other(UUID x) { return x.equals(a) ? b : a; }
    }

    private static final Map<UUID, Waiting> QUEUE = new LinkedHashMap<>();
    private static final Map<UUID, Bout> IN = new HashMap<>();
    private static final Bout[] BUSY = new Bout[SLOTS];

    public static boolean fighting(Player p) { return IN.containsKey(p.getUUID()); }

    public static int queued(Mode m) {
        int n = 0;
        for (Waiting w : QUEUE.values()) if (w.mode() == m) n++;
        return n;
    }

    public static int freeSlots() {
        int n = 0;
        for (Bout b : BUSY) if (b == null) n++;
        return n;
    }

    public static Mode queuedMode(Player p) {
        Waiting w = QUEUE.get(p.getUUID());
        return w == null ? null : w.mode();
    }

    // ---- joining ----

    public static void join(ServerPlayer p, Mode m, String pathway) {
        if (fighting(p)) { say(p, "You are already in a match.", ChatFormatting.GRAY); return; }
        if (Duel.dueling(p)) { say(p, "Finish your duel first.", ChatFormatting.RED); return; }
        if (HungerGames.fighting(p)) { say(p, "Not during the hunger games.", ChatFormatting.RED); return; }
        if (Raid.raiding(p)) { say(p, "Not from inside a raid.", ChatFormatting.RED); return; }
        // Re-picking keeps the time you have already waited, so you can change your pathway or
        // your game as often as you like without going to the back of the line for it.
        Waiting had = QUEUE.get(p.getUUID());
        long since = had != null ? had.since() : System.currentTimeMillis();
        QUEUE.put(p.getUUID(), new Waiting(m, pathway == null ? "" : pathway, since,
                                           p.position(), p.level().dimension()));
        Ladder l = Ladder.of(p.server);
        if (had != null && had.mode() == m) {
            String nice = pathway == null || pathway.isEmpty() ? "your own pathway" : pathway.replace('_', ' ');
            say(p, "Changed to " + nice + ". You keep your place in the queue.", ChatFormatting.AQUA);
            return;
        }
        p.sendSystemMessage(Component.literal("Queued · " + m.title + " · " + l.points(m, p) + " points")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal("   (" + queued(m) + " waiting, " + freeSlots() + " arenas free)")
                        .withStyle(ChatFormatting.DARK_GRAY)));
    }

    public static void leave(ServerPlayer p) {
        if (QUEUE.remove(p.getUUID()) == null) say(p, "You are not in the queue.", ChatFormatting.GRAY);
        else say(p, "Out of the queue.", ChatFormatting.GRAY);
    }

    // ---- matching ----

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        Tournament.onTick(e.getServer());
        MinecraftServer server = e.getServer();
        if (server.getTickCount() % 20 == 0) { leash(server); match(server); }
        if (server.getTickCount() % 10 == 0) watch(server);
    }

    /** Drops anyone who has wandered more than {@link #LEASH} blocks from where they queued. */
    private static void leash(MinecraftServer server) {
        if (QUEUE.isEmpty()) return;
        var gone = new java.util.ArrayList<UUID>();
        for (var en : QUEUE.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            Waiting w = en.getValue();
            if (p == null) continue;
            if (!p.level().dimension().equals(w.dim()) || p.position().distanceTo(w.at()) > LEASH) gone.add(en.getKey());
        }
        for (UUID id : gone) {
            QUEUE.remove(id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                say(p, "You walked too far from where you queued. You are out of the queue.", ChatFormatting.YELLOW);
                ArenaNet.sendBoard(p);
            }
        }
    }

    /**
     * Pairs the two closest in points within each mode, widening what counts as close by twelve
     * points a second of waiting. Nothing is paired when every arena is busy: the queue simply
     * holds until one comes free.
     */
    private static void match(MinecraftServer server) {
        if (QUEUE.isEmpty() || freeSlots() == 0) return;
        Ladder ladder = Ladder.of(server);
        long now = System.currentTimeMillis();

        for (Mode m : Mode.values()) {
            List<ServerPlayer> waiting = new ArrayList<>();
            for (var en : new ArrayList<>(QUEUE.entrySet())) {
                if (en.getValue().mode() != m) continue;
                ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
                if (p == null || fighting(p) || Duel.dueling(p)) { QUEUE.remove(en.getKey()); continue; }
                waiting.add(p);
            }
            if (waiting.size() < 2) continue;
            waiting.sort(java.util.Comparator.comparingInt(x -> ladder.points(m, x)));
            for (int i = 0; i + 1 < waiting.size() && freeSlots() > 0; i++) {
                ServerPlayer a = waiting.get(i), b = waiting.get(i + 1);
                long waited = (now - Math.min(QUEUE.get(a.getUUID()).since(), QUEUE.get(b.getUUID()).since())) / 1000;
                if (Math.abs(ladder.points(m, a) - ladder.points(m, b)) > 70 + 12 * waited) continue;
                begin(server, m, a, b);
                i++;
            }
        }
    }

    // ---- the room ----

    private static ServerLevel arenaLevel(MinecraftServer s) {
        ServerLevel lv = s.getLevel(Duel.DUEL);
        return lv == null ? s.overworld() : lv;
    }

    private static BlockPos origin(int slot) { return new BlockPos(slot * GAP, Y, 0); }

    /**
     * Builds a slot if it is not already standing at this size. At the old radius the arena was
     * torn down and raised again every bout, which is affordable for a 48 block floor and is not
     * for a 144 block one, so it stays up between matches now.
     */
    private static void ensure(ServerLevel lv, BlockPos o) {
        if (!lv.getBlockState(o).isAir()
            && !lv.getBlockState(o.offset(R - 3, 0, 0)).isAir()
            && !lv.getBlockState(o.offset(0, 0, R - 3)).isAir()
            && lv.getBlockState(o.above(HIGH + 1)).isAir())      // and has no lid left on it
            return;
        build(lv, o, true);
    }

    /** Everything in a slot that is not a player: corpses, drops, and whatever an ability left behind. */
    private static void sweep(ServerLevel lv, int slot) {
        for (Entity x : lv.getEntities((Entity) null,
                new AABB(origin(slot)).inflate(R + 8, HIGH + 10, R + 8), e -> !(e instanceof ServerPlayer)))
            x.discard();
    }

    private static void build(ServerLevel lv, BlockPos o, boolean up) {
        BlockState floor = up ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        BlockState rim = up ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        BlockState wall = up ? Blocks.BARRIER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // everything first, so an arena built smaller once cannot leave walls standing in this one
        for (int x = -R - 4; x <= R + 4; x++)
            for (int z = -R - 4; z <= R + 4; z++)
                for (int y = 0; y <= HIGH + 2; y++)
                    lv.setBlock(m.set(o.getX() + x, o.getY() + y, o.getZ() + z), Blocks.AIR.defaultBlockState(), 2);
        if (!up) return;
        for (int x = -R - 2; x <= R + 2; x++) for (int z = -R - 2; z <= R + 2; z++) {
            double d = Math.hypot(x, z);
            if (d <= R) lv.setBlock(m.set(o.getX() + x, o.getY(), o.getZ() + z), d >= R - 1.5 ? rim : floor, 2);
            if (d > R && d <= R + 1.8)   // walls only: the roof is gone
                for (int y = 1; y <= HIGH; y++) lv.setBlock(m.set(o.getX() + x, o.getY() + y, o.getZ() + z), wall, 2);
        }
    }

    /**
     * Starts a bout outside the ladder and calls back with the winner when it is over. Used by the
     * bracket, which keeps its own score and must not touch anybody's standing.
     */
    public static boolean exhibition(Mode m, ServerPlayer a, ServerPlayer b,
                                     java.util.function.BiConsumer<UUID, UUID> then) {
        if (a == b || fighting(a) || fighting(b) || Duel.dueling(a) || Duel.dueling(b)) return false;
        if (freeSlots() == 0) return false;
        if (m.kitted()) {                       // a bracket fighter keeps their own pathway
            QUEUE.put(a.getUUID(), new Waiting(m, Lotm.pathwayId(a), System.currentTimeMillis(), a.position(), a.level().dimension()));
            QUEUE.put(b.getUUID(), new Waiting(m, Lotm.pathwayId(b), System.currentTimeMillis(), b.position(), b.level().dimension()));
        }
        begin(a.server, m, a, b);
        Bout bout = IN.get(a.getUUID());
        if (bout == null) return false;
        bout.then = then;
        return true;
    }

    private static void begin(MinecraftServer server, Mode m, ServerPlayer a, ServerPlayer b) {
        int slot = -1;
        for (int i = 0; i < SLOTS; i++) if (BUSY[i] == null) { slot = i; break; }
        if (slot < 0) return;
        String pa = pathOf(a), pb = pathOf(b);
        QUEUE.remove(a.getUUID());
        QUEUE.remove(b.getUUID());

        ServerLevel lv = arenaLevel(server);
        BlockPos o = origin(slot);
        ensure(lv, o);
        sweep(lv, slot);   // nothing from the last fight is allowed to greet the next one

        Bout bout = new Bout(m, slot, a.getUUID(), b.getUUID());
        BUSY[slot] = bout;
        IN.put(a.getUUID(), bout);
        IN.put(b.getUUID(), bout);

        enter(a, bout, lv, o, -1, m, pa);
        enter(b, bout, lv, o, 1, m, pb);

        Ladder ladder = Ladder.of(server);
        Component line = Component.literal(m.title + " · ")
                .append(Component.literal(a.getGameProfile().getName() + " (" + ladder.points(m, a) + ")").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" vs ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(b.getGameProfile().getName() + " (" + ladder.points(m, b) + ")").withStyle(ChatFormatting.AQUA));
        a.sendSystemMessage(line);
        b.sendSystemMessage(line);
        lv.playSound(null, o, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.4f, 1.2f);
    }

    private static String pathOf(ServerPlayer p) {
        Waiting w = QUEUE.get(p.getUUID());
        return w == null ? "" : w.pathway();
    }

    /** Puts one fighter in, setting aside everything that has to come back out. */
    private static void enter(ServerPlayer p, Bout bout, ServerLevel lv, BlockPos o, int side, Mode m, String path) {
        bout.home.put(p.getUUID(), p.position());
        bout.homeDim.put(p.getUUID(), p.level().dimension());
        if (m.kitted()) {
            bout.stash.put(p.getUUID(), HungerGames.setAsideAll(p));
            p.getInventory().clearContent();
            p.removeAllEffects();
            p.clearFire();
            if (path != null && !path.isEmpty()) Lotm.setPathwayAndSequence(p, path, m.seq);
            else Lotm.setSequence(p, m.seq);
            kit(p, m);
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
        }
        p.teleportTo(lv, o.getX() + .5 + side * (R - 6), o.getY() + 1, o.getZ() + .5,
                     java.util.Set.of(), side < 0 ? -90 : 90, 0);
        p.setDeltaMovement(Vec3.ZERO);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false));
    }

    /** What a kitted fighter is given. Plain, and the same for both, so the pathway is the difference. */
    private static void kit(ServerPlayer p, Mode m) {
        boolean high = m == Mode.SEQ2;
        var inv = p.getInventory();
        inv.add(new ItemStack(high ? Items.DIAMOND_SWORD : Items.IRON_SWORD));
        inv.add(new ItemStack(Items.COOKED_BEEF, 16));
        inv.add(new ItemStack(Items.GOLDEN_APPLE, high ? 3 : 1));
        inv.armor.set(3, new ItemStack(high ? Items.DIAMOND_HELMET : Items.IRON_HELMET));
        inv.armor.set(2, new ItemStack(high ? Items.DIAMOND_CHESTPLATE : Items.IRON_CHESTPLATE));
        inv.armor.set(1, new ItemStack(high ? Items.DIAMOND_LEGGINGS : Items.IRON_LEGGINGS));
        inv.armor.set(0, new ItemStack(high ? Items.DIAMOND_BOOTS : Items.IRON_BOOTS));
    }

    // ---- watching one ----

    private static void watch(MinecraftServer server) {
        for (int i = 0; i < SLOTS; i++) {
            Bout bout = BUSY[i];
            if (bout == null) continue;
            bout.age += 10;
            ServerPlayer a = server.getPlayerList().getPlayer(bout.a), b = server.getPlayerList().getPlayer(bout.b);
            if (a == null && b == null) { end(server, bout, null, "nobody left"); continue; }
            if (a == null) { end(server, bout, bout.b, "disconnected"); continue; }
            if (b == null) { end(server, bout, bout.a, "disconnected"); continue; }
            if (bout.age > TIMEOUT) { end(server, bout, null, "ran out of time"); continue; }
            BlockPos o = origin(bout.slot);
            for (ServerPlayer p : List.of(a, b))
                if (!p.isDeadOrDying() && p.distanceToSqr(o.getX() + .5, o.getY() + 1, o.getZ() + .5) > (R + 8) * (R + 8))
                    p.teleportTo(p.serverLevel(), o.getX() + .5, o.getY() + 1, o.getZ() + .5,
                                 java.util.Set.of(), p.getYRot(), p.getXRot());
        }
    }

    /** A kitted fighter cannot really die in here: the killing blow just ends it. */
    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Bout bout = IN.get(p.getUUID());
        if (bout == null) return;
        if (e.getAmount() < p.getHealth()) return;
        e.setCanceled(true);
        p.setHealth(Math.max(1f, p.getMaxHealth() * .05f));
        end(p.server, bout, bout.other(p.getUUID()), "defeated");
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Bout bout = IN.get(p.getUUID());
        if (bout != null) end(p.server, bout, bout.other(p.getUUID()), "defeated");
    }

    public static void forfeit(ServerPlayer p) {
        Bout bout = IN.get(p.getUUID());
        if (bout == null) { say(p, "You are not in a match.", ChatFormatting.GRAY); return; }
        end(p.server, bout, bout.other(p.getUUID()), "yielded");
    }

    /** Ends a bout. A null winner is a draw: nobody's standing moves. */
    private static void end(MinecraftServer server, Bout bout, UUID winner, String how) {
        if (BUSY[bout.slot] != bout) return;
        BUSY[bout.slot] = null;
        IN.remove(bout.a);
        IN.remove(bout.b);

        ServerLevel lv = arenaLevel(server);
        for (UUID id : List.of(bout.a, bout.b)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            // Whoever lost was taken to the edge of death and the killing blow cancelled, so
            // without this they walk out of the arena on half a heart.
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            p.clearFire();
            p.removeAllEffects();
            Object token = bout.stash.get(id);
            if (token != null) HungerGames.giveBackAll(p, token);
            else {
                ServerLevel back = server.getLevel(bout.homeDim.get(id));
                Vec3 home = bout.home.get(id);
                if (back != null && home != null)
                    p.teleportTo(back, home.x, home.y, home.z, java.util.Set.of(), p.getYRot(), p.getXRot());
            }
        }
        sweep(lv, bout.slot);

        if (bout.then != null) {                      // a tournament bout: the bracket settles it
            var then = bout.then;
            bout.then = null;
            then.accept(winner, winner == null ? null : bout.other(winner));
            return;
        }
        if (winner == null) return;
        ServerPlayer w = server.getPlayerList().getPlayer(winner);
        ServerPlayer l = server.getPlayerList().getPlayer(bout.other(winner));
        if (w == null || l == null) return;
        Ladder ladder = Ladder.of(server);
        int[] moved = ladder.settle(bout.mode, w, l);
        w.sendSystemMessage(Component.literal("Won · +" + moved[0] + " → " + moved[2]
                + "   #" + ladder.place(bout.mode, w.getUUID())).withStyle(ChatFormatting.GREEN));
        l.sendSystemMessage(Component.literal("Lost (" + how + ") · -" + moved[1] + " → " + moved[3]
                + "   #" + ladder.place(bout.mode, l.getUUID())).withStyle(ChatFormatting.RED));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) { Tournament.onLogout(sp); TourneyNet.forget(sp); }
        QUEUE.remove(e.getEntity().getUUID());
        Bout bout = IN.get(e.getEntity().getUUID());
        if (bout != null && e.getEntity() instanceof ServerPlayer sp)
            end(sp.server, bout, bout.other(sp.getUUID()), "disconnected");
    }

    private static void say(ServerPlayer p, String s, ChatFormatting f) {
        p.sendSystemMessage(Component.literal(s).withStyle(f));
    }
}
