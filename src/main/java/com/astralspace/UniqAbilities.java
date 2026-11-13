package com.astralspace;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** What the G key does for each worn Uniqueness. LOTM is reached by reflection, like everywhere else. */
public final class UniqAbilities {
    private UniqAbilities() {}

    /** Death: the feathered serpent strikes everything nearby for 25, you and your allies included. At Seq 1/0 it reaches 50 blocks, strikes only foes, takes 30% of their health and slows them. */
    public static void serpent(ServerPlayer sp, ItemStack st) {
        ServerLevel lv = sp.serverLevel();
        boolean emp = UniqPower.emp(sp, "corpse_collector");
        List<LivingEntity> hit = emp ? lv.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(50), e -> foe(sp, e) && e.distanceToSqr(sp) <= 2500)
                                     : lv.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(8), e -> e.isAlive());
        if (emp && hit.isEmpty()) { sp.displayClientMessage(Component.literal("The serpent finds no enemy.").withStyle(ChatFormatting.GRAY), true); refund = true; return; }
        lv.playSound(null, sp.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2f, 1.4f);
        for (LivingEntity e : hit) {
            world(lv, new DoorNet.World(1, sp.getId(), e.getId(), sp.getX(), sp.getY(), sp.getZ(), e.getX(), e.getY(), e.getZ(), 18));
            later(8, () -> {
                if (!e.isAlive()) return;
                e.hurt(emp ? lv.damageSources().playerAttack(sp) : lv.damageSources().magic(), emp ? Math.max(25f, e.getMaxHealth() * .3f) : 25f);
                if (emp) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                lv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, e.getX(), e.getY() + e.getBbHeight() * .5, e.getZ(), 14, .3, .4, .3, .05);
                lv.playSound(null, e.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1f, .6f);
            });
        }
        fx(sp, 1);
    }

    /** A Photon effect at a spot (0 twilight hit, 2 storm ball), played for this many ticks. */
    public static void fxPoint(ServerLevel lv, int idx, double x, double y, double z, float scale, int life) { fxPoint(lv, idx, 0, x, y, z, scale, life); }
    public static void fxPoint(ServerLevel lv, int idx, int variant, double x, double y, double z, float scale, int life) {
        world(lv, new DoorNet.World(5, idx, variant, x, y, z, scale, 0, 0, life));
    }

    /** A Photon effect carried along a path (1 twilight wave). */
    public static void fxPath(ServerLevel lv, int idx, Vec3 a, Vec3 b, int ticks) { fxPath(lv, idx, 0, a, b, ticks); }
    public static void fxPath(ServerLevel lv, int idx, int variant, Vec3 a, Vec3 b, int ticks) {
        world(lv, new DoorNet.World(6, idx, variant, a.x, a.y, a.z, b.x, b.y, b.z, ticks));
    }

    static void world(ServerLevel lv, DoorNet.World w) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(lv, null, w.x(), w.y(), w.z(), 192.0, w);
    }

    private static final List<Holder<MobEffect>> GOOD = List.of(MobEffects.DAMAGE_BOOST, MobEffects.REGENERATION, MobEffects.MOVEMENT_SPEED,
        MobEffects.DAMAGE_RESISTANCE, MobEffects.ABSORPTION, MobEffects.LUCK, MobEffects.JUMP, MobEffects.FIRE_RESISTANCE);
    private static final List<Holder<MobEffect>> BAD = List.of(MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.POISON,
        MobEffects.DIG_SLOWDOWN, MobEffects.UNLUCK, MobEffects.CONFUSION, MobEffects.BLINDNESS, MobEffects.HUNGER);

    /** Monster: the wheel spins (3 seconds) and only then lands on a boon or a bane. */
    public static void wheel(ServerPlayer sp, ItemStack st) {
        fx(sp, 2);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, .8f);
        boolean good = sp.getRandom().nextInt(UniqPower.emp(sp, "monster") ? 4 : 2) != 0;   // even odds; three to one for a Seq 1/0 Monster
        Holder<MobEffect> eff = good ? GOOD.get(sp.getRandom().nextInt(GOOD.size())) : BAD.get(sp.getRandom().nextInt(BAD.size()));
        later(60, () -> {
            if (!sp.isAlive()) return;
            sp.addEffect(new MobEffectInstance(eff, good ? 20 * 45 : 20 * 20, good ? 1 : 0));
            sp.level().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, good ? 1.4f : 0.5f);
            sp.displayClientMessage(Component.literal("The wheel lands on " + eff.value().getDisplayName().getString()).withStyle(good ? ChatFormatting.GOLD : ChatFormatting.DARK_RED), true);
        });
    }

    /** Sun: three flaring suns, one ahead and one to either side of it. At Seq 1/0: five larger suns in a wider fan, and when they have burst six more rise around you and burst too; each sun hits less hard. */
    public static void suns(ServerPlayer sp, ItemStack st) {
        boolean emp = UniqPower.emp(sp, "bard");
        Vec3 look = sp.getLookAngle(); Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        Vec3 mid = sp.position().add(0, 3.0, 0).add(flat.scale(emp ? 19 : 15));
        Vec3[] at = emp ? new Vec3[]{ mid, mid.add(side.scale(13)).subtract(flat.scale(2)), mid.subtract(side.scale(13)).subtract(flat.scale(2)), mid.add(side.scale(25)).subtract(flat.scale(8)), mid.subtract(side.scale(25)).subtract(flat.scale(8)) }
                        : new Vec3[]{ mid, mid.add(side.scale(10)).subtract(flat.scale(1)), mid.subtract(side.scale(10)).subtract(flat.scale(1)) };
        try {
            Class<?> c = Class.forName("net.swimmingtuna.lotm.pathway.bard.abilities.FlaringSun");
            var m = c.getMethod("spawnMiniatureSun", net.minecraft.world.level.Level.class, Vec3.class, LivingEntity.class, float.class, float.class);
            List<Object> first = new java.util.ArrayList<>();
            for (Vec3 p : at) first.add(m.invoke(null, sp.level(), p, sp, emp ? 28f : 40f, emp ? 13f : 9f));
            if (emp) afterSuns(sp, m, first, 0);
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().warn("[astral] flaring sun failed: {}", t.toString());
            sp.displayClientMessage(Component.literal("The suns will not rise.").withStyle(ChatFormatting.GRAY), true);
            refund = true;
        }
    }

    /** Waits for the first suns to be gone (they have burst), then raises six more in a ring round the caster. */
    private static void afterSuns(ServerPlayer sp, java.lang.reflect.Method spawn, List<Object> first, int waited) {
        later(5, () -> {
            boolean gone = waited > 400;
            if (!gone) { gone = true; for (Object o : first) if (o instanceof net.minecraft.world.entity.Entity e && !e.isRemoved()) { gone = false; break; } }
            if (!gone) { afterSuns(sp, spawn, first, waited + 5); return; }
            if (!sp.isAlive()) return;
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                try { spawn.invoke(null, sp.level(), sp.position().add(Math.cos(a) * 11, 3.0, Math.sin(a) * 11), sp, 22f, 8f); } catch (Throwable ignored) { }
            }
        });
    }

    /** Who has the Quill's storytelling screen open, and until when: only their stories are told on the Quill's authority. */
    private static final java.util.Map<java.util.UUID, Long> QUILLING = new java.util.HashMap<>();
    private static final int QUILL_TICKS = 20 * 180;
    private static net.minecraft.world.item.Item prophecyItem;
    private static boolean prophecyLooked;

    /** LOTM's own Prophecy ability item, found by its class so the registry name does not matter. */
    private static net.minecraft.world.item.Item prophecy() {
        if (!prophecyLooked) {
            prophecyLooked = true;
            try {
                Class<?> pc = Class.forName("net.swimmingtuna.lotm.pathway.spectator.abilities.Prophecy");
                for (net.minecraft.world.item.Item it : BuiltInRegistries.ITEM) if (pc.isInstance(it)) { prophecyItem = it; break; }
            } catch (Throwable ignored) { }
        }
        return prophecyItem;
    }

    /** A Prophecy left over from the old way of lending one out: it is taken away wherever it turns up. */
    public static boolean lent(ItemStack st) {
        var d = st.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().contains("astral_lent");
    }

    public static void returnProphecy(ServerPlayer sp) {
        QUILLING.remove(sp.getUUID());
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (lent(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
    }

    private static void tickQuills(net.minecraft.server.MinecraftServer server) {
        if (QUILLING.isEmpty()) return;
        long now = server.overworld().getGameTime();
        QUILLING.values().removeIf(until -> now > until);
    }

    /**
     * Watches this player's connection for LOTM's "tell this story" packet. LOTM throws the story away unless the teller
     * holds a Prophecy, and deletes a Prophecy from anyone who should not have one, so lending the item never worked.
     * Instead, when the packet comes from someone with the Quill's screen open, the story is told here: the Prophecy is
     * put in their hand, LOTM's own tell() is called, and their hand is put back, all inside one server task so nothing
     * of LOTM's can run in between. LOTM's own handler then sees no Prophecy and does nothing, as before.
     */
    public static void watchStories(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        try {
            var pipe = sp.connection.getConnection().channel().pipeline();
            if (pipe.get("astral_quill") != null || pipe.get("packet_handler") == null) return;
            final java.util.UUID id = sp.getUUID();
            final net.minecraft.server.MinecraftServer server = sp.server;
            pipe.addBefore("packet_handler", "astral_quill", new io.netty.channel.ChannelInboundHandlerAdapter() {
                @Override public void channelRead(io.netty.channel.ChannelHandlerContext ctx, Object msg) throws Exception {
                    try {
                        if (msg instanceof net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket pk
                                && pk.payload().getClass().getName().endsWith(".TellStoryC2S") && QUILLING.containsKey(id)) {
                            Object words = pk.payload().getClass().getMethod("words").invoke(pk.payload());
                            if (words instanceof java.util.List<?> w) {
                                java.util.List<String> copy = new java.util.ArrayList<>();
                                for (Object o : w) copy.add(String.valueOf(o));
                                server.execute(() -> { ServerPlayer p = server.getPlayerList().getPlayer(id); if (p != null) tellByQuill(p, copy); });
                            }
                        }
                    } catch (Throwable t) { com.mojang.logging.LogUtils.getLogger().warn("[astral] quill: could not read the story packet: {}", t.toString()); }
                    super.channelRead(ctx, msg);
                }
            });
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().warn("[astral] quill: could not watch {}'s connection: {}", sp.getGameProfile().getName(), t.toString());
        }
    }

    /** Tells the story with a Prophecy in hand for exactly as long as LOTM's tell() takes. */
    private static void tellByQuill(ServerPlayer sp, java.util.List<String> words) {
        if (QUILLING.remove(sp.getUUID()) == null) return;
        net.minecraft.world.item.Item pi = prophecy();
        if (pi == null) { sp.displayClientMessage(Component.literal("The quill stays still.").withStyle(ChatFormatting.GRAY), true); return; }
        if (pi.getClass().isInstance(sp.getMainHandItem().getItem())) return;   // their own Prophecy: LOTM tells it itself
        int slot = sp.getInventory().selected;
        ItemStack held = sp.getInventory().getItem(slot);
        try {
            sp.getInventory().setItem(slot, new ItemStack(pi));
            Class.forName("net.swimmingtuna.lotm.pathway.spectator.story.Storytelling").getMethod("tell", ServerPlayer.class, java.util.List.class).invoke(null, sp, words);
            com.mojang.logging.LogUtils.getLogger().info("[astral] quill: {} told a story of {} words", sp.getGameProfile().getName(), words.size());
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().warn("[astral] quill: the story could not be told: {}", t.toString());
            sp.displayClientMessage(Component.literal("The quill stays still.").withStyle(ChatFormatting.GRAY), true);
        } finally {
            sp.getInventory().setItem(slot, held);
        }
    }

    /** Spectator: the Quill opens the Storytelling screen (the Seq 1 prophecy ability), through LOTM's own packet. */
    public static void quill(ServerPlayer sp, ItemStack st) {
        QUILLING.put(sp.getUUID(), sp.level().getGameTime() + QUILL_TICKS);   // their next story is told on the Quill's authority (see watchStories)
        try {
            Class<?> story = Class.forName("net.swimmingtuna.lotm.pathway.spectator.story.Storytelling");
            Class<?> pk = Class.forName("net.swimmingtuna.lotm.network.spectator.OpenStorytellingScreenS2C");
            story.getMethod("setPendingTarget", net.minecraft.world.entity.player.Player.class, LivingEntity.class).invoke(null, sp, sp);
            Object ctx = story.getMethod("contextFor", ServerPlayer.class).invoke(null, sp);
            Class<?> cc = ctx.getClass();
            Object waiting = story.getMethod("waitingFor", ServerPlayer.class).invoke(null, sp);
            int spirit = (int) Class.forName("net.swimmingtuna.lotm.beyonder.Spirituality").getMethod("getSpirituality", LivingEntity.class).invoke(null, sp);
            Object packet = null;
            for (var k : pk.getConstructors()) if (k.getParameterCount() == 7)
                packet = k.newInstance(cc.getMethod("sequence").invoke(ctx), spirit, cc.getMethod("targetName").invoke(ctx), cc.getMethod("playerNames").invoke(ctx),
                    cc.getMethod("tellerName").invoke(ctx), cc.getMethod("nearby").invoke(ctx), waiting);
            Class.forName("net.swimmingtuna.lotm.networking.LOTMNetworkHandler").getMethod("sendToPlayer",
                net.minecraft.network.protocol.common.custom.CustomPacketPayload.class, ServerPlayer.class).invoke(null, packet, sp);
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().warn("[astral] storytelling failed: {}", t.toString());
            sp.displayClientMessage(Component.literal("The quill stays still.").withStyle(ChatFormatting.GRAY), true);
            returnProphecy(sp);
        }
    }

    // ---- stage 2 ----
    private static final java.util.List<Object[]> LATER = new java.util.ArrayList<>();   // {ticksLeft, Runnable}

    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        tickPins(e.getServer());
        tickQuills(e.getServer());
        tickShadows(e.getServer());
        for (int i = LATER.size() - 1; i >= 0; i--) {
            Object[] o = LATER.get(i);
            int left = (int) o[0] - 1; o[0] = left;
            if (left <= 0) { LATER.remove(i); try { ((Runnable) o[1]).run(); } catch (Throwable t) { com.mojang.logging.LogUtils.getLogger().warn("[astral] delayed uniqueness effect failed: {}", t.toString()); } }
        }
    }

    /** An ability sets this when it did nothing, so the key press costs no cooldown. */
    public static boolean refund;

    static void later(int ticks, Runnable r) { LATER.add(new Object[]{ticks, r}); }

    /** Who is held in place, and until when (server tick). */
    private static final java.util.Map<java.util.UUID, long[]> PINNED = new java.util.HashMap<>();

    /**
     * Holds someone where they stand. Slowness does the walking; the holding itself is done tick by tick in {@link #tickPins},
     * because Jump Boost with a huge amplifier is sent to the client as a byte and flings the player into the sky instead.
     */
    private static void pin(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false));
        PINNED.put(e.getUUID(), new long[]{e.level().getGameTime() + ticks, e.getId()});
        e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
    }

    private static void tickPins(net.minecraft.server.MinecraftServer server) {
        if (PINNED.isEmpty()) return;
        for (var it = PINNED.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            LivingEntity e = null;
            for (ServerLevel lv : server.getAllLevels()) { if (lv.getEntity((int) en.getValue()[1]) instanceof LivingEntity le) { e = le; break; } }
            if (e == null || !e.isAlive() || e.level().getGameTime() > en.getValue()[0]) { it.remove(); continue; }
            Vec3 m = e.getDeltaMovement();
            e.setDeltaMovement(0, Math.min(0, m.y), 0);                 // they may fall, they may not walk or jump
            if (e instanceof ServerPlayer sp) { sp.hurtMarked = true; sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp)); }
        }
    }

    static boolean foe(ServerPlayer sp, LivingEntity e) {
        if (e == sp || !e.isAlive() || e.isAlliedTo(sp)) return false;
        return !(e instanceof net.minecraft.world.entity.TamableAnimal t && t.isOwnedBy(sp));
    }

    /** Hanged Man: the shadow spreads 20 blocks and a cage of shadow closes on everyone in it, spikes of shadow impaling them.
     *  At Seq 1/0: half again as far, double damage, a two second stun, and then bleeding, twilight, slowness and darkness for five seconds. */
    public static void cage(ServerPlayer sp, ItemStack st) {
        ServerLevel lv = sp.serverLevel();
        boolean emp = UniqPower.emp(sp, "secrets_supplicant");
        double r = emp ? 30 : 20;
        fx(sp, emp ? 5 : 3);
        lv.playSound(null, sp.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 1.5f, 0.5f);
        Shadow field = new Shadow(lv, sp, emp, r, lv.getGameTime() + SHADOW_LIFE);
        SHADOWS.add(field);
        for (LivingEntity e : field.caught()) field.take(e, 8 + (int) (e.distanceTo(sp) * .6));
    }

    /** The shadow left standing after the ability: for five seconds it keeps taking anyone who walks into it. */
    private static final int SHADOW_LIFE = 20 * 5;
    private static final List<Shadow> SHADOWS = new java.util.ArrayList<>();

    private static final class Shadow {
        final ServerLevel lv; final ServerPlayer owner; final boolean emp; final double r; final long until;
        final Vec3 c; final java.util.Set<java.util.UUID> had = new java.util.HashSet<>();
        Shadow(ServerLevel lv, ServerPlayer sp, boolean emp, double r, long until) {
            this.lv = lv; this.owner = sp; this.emp = emp; this.r = r; this.until = until; this.c = sp.position();
        }
        /** Everyone standing in the shadow who has not been taken yet. It reaches well up, so nobody escapes by jumping. */
        List<LivingEntity> caught() {
            return lv.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(r, 24, r),
                x -> foe(owner, x) && !had.contains(x.getUUID()) && (x.getX() - c.x) * (x.getX() - c.x) + (x.getZ() - c.z) * (x.getZ() - c.z) <= r * r);
        }
        void take(LivingEntity e, int delay) {
            had.add(e.getUUID());
            later(delay, () -> {
                if (!e.isAlive()) return;
                double up = e.getY() - c.y;
                if (up > 1.5) {                                        // in the air: the shadow surges up and drags them down to it
                    world(lv, new DoorNet.World(2, e.getId(), emp ? 3 : 2, e.getX(), c.y, e.getZ(), 0, (float) up, 0, 30));
                    e.setDeltaMovement(e.getDeltaMovement().x * .2, -Math.max(1.2, up * .25), e.getDeltaMovement().z * .2);
                    e.hurtMarked = true;
                    e.resetFallDistance();
                }
                pin(e, 20 * 12);
                world(lv, new DoorNet.World(2, e.getId(), emp ? 1 : 0, e.getX(), e.getY(), e.getZ(), 0, 0, 0, 60));
                lv.playSound(null, e.blockPosition(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, .6f, 1.6f);
                e.hurt(lv.damageSources().magic(), emp ? 28f : 14f);
                if (emp) {
                    UniqPower.stun(e, 40);
                    later(40, () -> {
                        if (!e.isAlive()) return;
                        UniqPower.bleed(e, 100); UniqPower.effect(e, "twilight", 100, 0);
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0)); e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
                    });
                }
            });
        }
    }

    private static void tickShadows(net.minecraft.server.MinecraftServer server) {
        for (int i = SHADOWS.size() - 1; i >= 0; i--) {
            Shadow f = SHADOWS.get(i);
            if (f.lv.getGameTime() > f.until || !f.owner.isAlive()) { SHADOWS.remove(i); continue; }
            for (LivingEntity e : f.caught()) f.take(e, 4);
        }
    }

    /** Chained: the doll climbs down, walks to the nearest opponent and binds them where they stand.
     *  At Seq 1/0 a doll goes to every foe near: each waits a second, appears beside its target and stuns it for five seconds. */
    public static void doll(ServerPlayer sp, ItemStack st) {
        ServerLevel lv = sp.serverLevel();
        List<LivingEntity> foes = lv.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(24), e -> foe(sp, e));
        foes.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
        if (foes.isEmpty()) { sp.displayClientMessage(Component.literal("The doll finds nobody.").withStyle(ChatFormatting.GRAY), true); refund = true; return; }
        if (UniqPower.emp(sp, "prisoner")) {
            for (LivingEntity tgt : foes) {
                world(lv, new DoorNet.World(3, sp.getId(), tgt.getId(), sp.getX(), sp.getY(), sp.getZ(), tgt.getX(), tgt.getY(), tgt.getZ(), -(20 + 100)));   // a negative time: the doll teleports after 20 ticks
                later(20, () -> {
                    if (!tgt.isAlive()) return;
                    pin(tgt, 100); UniqPower.stun(tgt, 100);   // it appears beside them: there is no outrunning this one
                    lv.playSound(null, tgt.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, .8f, 1.6f);
                    lv.playSound(null, tgt.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.5f, 0.7f);
                    binding(lv, tgt);
                });
            }
            return;
        }
        LivingEntity tgt = foes.get(0);
        int walk = Math.max(14, (int) (sp.distanceTo(tgt) / .35));
        int total = walk + 16;
        world(lv, new DoorNet.World(3, sp.getId(), tgt.getId(), sp.getX(), sp.getY(), sp.getZ(), tgt.getX(), tgt.getY(), tgt.getZ(), total));
        final Vec3 from = sp.position();
        later(walk, () -> {
            if (!tgt.isAlive()) return;
            if (tgt.position().distanceTo(from) > sp.distanceTo(tgt) + 10 || tgt.distanceTo(sp) > 34) {   // it walks: outrun it and it never arrives
                lv.playSound(null, tgt.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, .7f, 1.4f);
                if (sp.isAlive()) sp.displayClientMessage(Component.literal("The doll could not keep up.").withStyle(ChatFormatting.GRAY), true);
                return;
            }
            pin(tgt, 80); UniqPower.stun(tgt, 80);
            lv.playSound(null, tgt.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.5f, 0.7f);
            binding(lv, tgt);
        });
    }

    private static void binding(ServerLevel lv, LivingEntity tgt) {
        for (int j = 0; j < 20; j++) {
            double a = j * Math.PI / 10;
            for (int h = 0; h < 4; h++) lv.sendParticles(ParticleTypes.ENCHANTED_HIT, tgt.getX() + Math.cos(a + h) * 0.7, tgt.getY() + h * 0.45, tgt.getZ() + Math.sin(a + h) * 0.7, 1, 0, 0, 0, 0);
        }
    }

    /** Tyrant: a ball of lightning is thrown, then swells into a huge sphere of storm and chaos.
     *  At Seq 1/0 the storm covers ten times the ground, hits far harder, lasts nine seconds, lets no player out and blinds everyone in it for half a second every three. */
    public static void storm(ServerPlayer sp, ItemStack st) {
        ServerLevel lv = sp.serverLevel();
        boolean emp = UniqPower.emp(sp, "sailor");
        Vec3 eye = sp.getEyePosition(), dir = sp.getLookAngle();
        net.minecraft.world.phys.BlockHitResult hit = lv.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(dir.scale(40)),
            net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, sp));
        Vec3 end = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? eye.add(dir.scale(40)) : hit.getLocation().subtract(dir.scale(1.2));
        Vec3 start = eye.add(dir.scale(.8)).subtract(0, .4, 0);
        int fly = Math.max(4, (int) (eye.distanceTo(end) / 2.5));
        final double R = emp ? 44 : 14;                 // ten times the ground is about 3.16 times the radius
        final int life = emp ? 180 : 90;
        final float dmg = emp ? 56f : 26f;
        world(lv, new DoorNet.World(4, fly, (int) (R * 10), start.x, start.y, start.z, end.x, end.y, end.z, fly + 22 + (life - 20) + 16));
        lv.playSound(null, sp.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1f, 1.6f);
        later(fly, () -> {
            lv.playSound(null, net.minecraft.core.BlockPos.containing(end), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6f, 0.5f);
            for (int t = 6; t < life; t += 2) {
                final int tt = t;
                later(t, () -> {
                    double r = Math.min(R, 2 + R * tt / 22.0);
                    if (emp) {   // nobody walks out of it
                        UniqPower.contain(lv, end, r, sp);
                        if (tt % 60 == 6) for (ServerPlayer pl : lv.players())
                            if (pl != sp && pl.position().add(0, pl.getBbHeight() / 2, 0).distanceTo(end) <= r)
                                pl.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 10, 0, false, false));
                    }
                    if (tt % 10 == 6) {
                        for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(end, end).inflate(r), x -> x != sp && x.isAlive() && x.position().add(0, x.getBbHeight() / 2, 0).distanceTo(end) <= r)) {
                            e.hurt(lv.damageSources().lightningBolt(), dmg);
                            e.setDeltaMovement(e.getDeltaMovement().add((lv.random.nextDouble() - .5) * 1.2, 0.35, (lv.random.nextDouble() - .5) * 1.2));
                            e.hurtMarked = true;
                        }
                        for (int k = 0; k < (emp ? 4 : 1); k++) {
                            net.minecraft.world.entity.LightningBolt b = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(lv);
                            if (b != null) { b.setVisualOnly(true); b.moveTo(end.x + (lv.random.nextDouble() - .5) * 2 * r * 0.8, end.y - Math.min(r, 14) * 0.3, end.z + (lv.random.nextDouble() - .5) * 2 * r * 0.8); lv.addFreshEntity(b); }
                        }
                    }
                });
            }
        });
    }

    /** Tyrant's no-shield rule: while the lightning ball is carried, a shield blocks nothing. */
    public static void shield(net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack w = WornUniq.findWorn(sp);
        if (!w.isEmpty() && BuiltInRegistries.ITEM.getKey(w.getItem()).getPath().equals("storm_katana")) {
            e.setBlocked(false);
            sp.displayClientMessage(Component.literal("The lightning ball will not let you hold a shield.").withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** The Prophecy the Quill lends is not theirs to drop. */
    public static void onToss(net.neoforged.neoforge.event.entity.item.ItemTossEvent e) {
        if (lent(e.getEntity().getItem())) { e.setCanceled(true); e.getEntity().discard(); if (e.getPlayer() instanceof ServerPlayer sp) returnProphecy(sp); }
    }

    /** Logging out mid-story: give them their own item back. */
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) returnProphecy(sp);
    }

    /** Tells everyone who can see this player to play a worn-item effect (1 serpent strike, 2 wheel spin). */
    public static void fx(ServerPlayer sp, int kind) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new DoorNet.Fx(sp.getId(), kind));
    }
}
