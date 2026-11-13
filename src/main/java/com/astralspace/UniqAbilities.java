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

    // ---- the Book of Feathered Serpents: Hands of Death, for five seconds

    /** Who has the book's hands on: {the game time they end, 1 if LOTM's own Hands of Life and Death is what is running, 1 if this code switched it on}. */
    private static final java.util.Map<java.util.UUID, long[]> HANDS = new java.util.HashMap<>();
    private static final int HANDS_TICKS = 100;

    private static Object handsAbility() throws Exception {
        return Class.forName("net.swimmingtuna.lotm.pathway.corpsecollector.abilities.HandsOfLifeAndDeath").getMethod("ability").invoke(null);
    }
    private static boolean handsOn(Object ab, ServerPlayer sp) throws Exception {
        return (Boolean) ab.getClass().getMethod("isOn", LivingEntity.class).invoke(ab, sp);
    }

    /**
     * Death: the book opens and for five seconds your hands are the Hands of Death. LOTM's own ability is switched on
     * for you (as a system cast: no cost, no cooldown, whatever your pathway) and off again when the time is up, so a
     * bare-fisted punch does exactly what a Death's does. A hit with something in your hand lays the same decay on
     * through LOTM's trait; if LOTM cannot be reached at all, the hit takes 12% of their health and withers them instead.
     * At Seq 1/0 Death each hit also catches every foe within four blocks of the one you struck.
     */
    public static void hands(ServerPlayer sp, ItemStack st) {
        boolean lotm = false, mine = false;
        try {
            Object ab = handsAbility();
            if (!handsOn(ab, sp)) {
                Class<?> caster = Class.forName("net.swimmingtuna.lotm.ability.AbilityCaster");
                Class<?> srcC = Class.forName("net.swimmingtuna.lotm.ability.AbilityCaster$CastSource"), optC = Class.forName("net.swimmingtuna.lotm.ability.AbilityCaster$CastOptions");
                Object src = srcC.getField("HELD_ITEM").get(null), opt = optC.getField("SYSTEM").get(null);
                for (var m : caster.getMethods()) if (m.getName().equals("cast") && m.getParameterCount() == 7 && m.getParameterTypes()[6] == optC) {
                    m.invoke(null, sp, ab, src, net.minecraft.world.InteractionHand.MAIN_HAND, null, null, opt); break;
                }
                mine = handsOn(ab, sp);
            }
            lotm = handsOn(ab, sp);
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().info("[astral] book: LOTM's Hands of Death could not be switched on ({}); using the mod's own", t.toString());
        }
        HANDS.put(sp.getUUID(), new long[]{sp.level().getGameTime() + HANDS_TICKS, lotm ? 1 : 0, mine ? 1 : 0});
        ServerLevel lv = sp.serverLevel();
        lv.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 2f, .6f);
        lv.playSound(null, sp.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1f, 1.6f);
        lv.sendParticles(ParticleTypes.SOUL, sp.getX(), sp.getY() + 1, sp.getZ(), 30, .5, .7, .5, .04);
        over(sp, 5, () -> lv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, sp.getX(), sp.getY() + 1, sp.getZ(), 10, .45, .5, .45, .01));
        sp.displayClientMessage(Component.literal("The book opens. For five seconds your hands are the Hands of Death.").withStyle(ChatFormatting.DARK_GREEN), true);
        fx(sp, 1);
        later(HANDS_TICKS, () -> handsEnd(sp));
    }

    private static void handsEnd(ServerPlayer sp) {
        long[] h = HANDS.get(sp.getUUID());
        if (h == null || sp.level().getGameTime() < h[0] - 1) return;   // used again since: that later call will end it
        HANDS.remove(sp.getUUID());
        if (h[2] == 1) try { Object ab = handsAbility(); ab.getClass().getMethod("turnOff", LivingEntity.class).invoke(ab, sp); } catch (Throwable ignored) { }
        sp.displayClientMessage(Component.literal("The book closes.").withStyle(ChatFormatting.GRAY), true);
    }

    /** One touch of the book's hands on one target. */
    private static void handsTouch(ServerPlayer sp, LivingEntity target, boolean lotm) {
        if (lotm) try {
            Object ab = handsAbility();
            Object inst = Class.forName("net.swimmingtuna.lotm.ability.ActiveCasts").getMethod("get", LivingEntity.class, Class.forName("net.swimmingtuna.lotm.item.BeyonderAbilities.SimpleAbilityItem")).invoke(null, sp, ab);
            Object snap = inst.getClass().getMethod("snap").invoke(inst);
            Class<?> valueC = Class.forName("net.swimmingtuna.lotm.ability.Value");
            int decay = Math.max(1, (int) (float) (Float) snap.getClass().getMethod("value", valueC).invoke(snap, ab.getClass().getField("decayTime").get(ab)));
            Object trait = ((net.neoforged.neoforge.registries.DeferredHolder<?, ?>) Class.forName("net.swimmingtuna.lotm.pathway.corpsecollector.CorpseCollectorTraits").getField("HANDS_OF_DEATH").get(null)).get();
            for (var m : Class.forName("net.swimmingtuna.lotm.ability.trait.Traits").getMethods()) if (m.getName().equals("grant") && m.getParameterCount() == 5) { m.invoke(null, target, trait, decay, 1f, snap); return; }
        } catch (Throwable ignored) { }
        target.hurt(sp.damageSources().indirectMagic(sp, sp), Math.max(10f, target.getMaxHealth() * .12f));
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 2));
    }

    /** While the book's hands are on, every hit carries them. A bare-fisted punch with LOTM's ability running is left to LOTM. */
    public static void onHandsAttack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !(e.getTarget() instanceof LivingEntity target) || !target.isAlive()) return;
        long[] h = HANDS.get(sp.getUUID());
        if (h == null) return;
        if (sp.level().getGameTime() > h[0]) { HANDS.remove(sp.getUUID()); return; }
        boolean lotm = h[1] == 1, emp = UniqPower.emp(sp, "corpse_collector");
        if (!(lotm && sp.getMainHandItem().isEmpty())) handsTouch(sp, target, lotm);
        ServerLevel lv = sp.serverLevel();
        lv.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY() + target.getBbHeight() * .5, target.getZ(), 12, .3, .4, .3, .03);
        if (emp) for (LivingEntity o : lv.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(4), x -> x != target && foe(sp, x))) {
            handsTouch(sp, o, lotm);
            lv.sendParticles(ParticleTypes.SCULK_SOUL, o.getX(), o.getY() + o.getBbHeight() * .5, o.getZ(), 8, .3, .4, .3, .03);
        }
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
    private static final net.minecraft.resources.ResourceLocation WHEEL_HP = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "wheel_vigor");

    /** Adds spirituality through LOTM (a share of the player's maximum), quietly doing nothing without LOTM. */
    private static void spirit(ServerPlayer sp, double shareOfMax) {
        try {
            Class<?> c = Class.forName("net.swimmingtuna.lotm.beyonder.Spirituality");
            int max = (int) c.getMethod("getMaxSpirituality", LivingEntity.class).invoke(null, sp);
            int amt = (int) Math.round(max * shareOfMax);
            if (amt > 0) c.getMethod("addSpirituality", LivingEntity.class, int.class).invoke(null, sp, amt);
            else if (amt < 0) c.getMethod("useSpirituality", LivingEntity.class, int.class).invoke(null, sp, -amt);
        } catch (Throwable ignored) { }
    }

    /** Runs `each` once a second for `seconds` seconds, while the player lives. */
    private static void over(ServerPlayer sp, int seconds, Runnable each) {
        if (seconds <= 0 || !sp.isAlive() || sp.hasDisconnected()) return;
        each.run();
        later(20, () -> over(sp, seconds - 1, each));
    }

    private static void eff(ServerPlayer sp, Holder<MobEffect> e, int seconds, int amp) { sp.addEffect(new MobEffectInstance(e, seconds * 20, amp)); }

    /** One of the wheel's boons. Each is strong on its own; the jackpot is all of them at once. */
    private static String boon(ServerPlayer sp, int which) {
        switch (which) {
            case 0 -> {   // spirit: a third back at once, then 4% of the maximum every second for half a minute
                spirit(sp, .34); over(sp, 30, () -> spirit(sp, .04));
                return "Wellspring: your spirituality pours back";
            }
            case 1 -> {   // life: 40 more health, full heal, then 5% of maximum health every second for half a minute
                var mh = sp.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (mh != null) { mh.removeModifier(WHEEL_HP); mh.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(WHEEL_HP, 40, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE)); }
                sp.setHealth(sp.getMaxHealth());
                over(sp, 30, () -> sp.heal(sp.getMaxHealth() * .05f));
                later(20 * 60, () -> { var m2 = sp.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH); if (m2 != null) m2.removeModifier(WHEEL_HP); if (sp.getHealth() > sp.getMaxHealth()) sp.setHealth(sp.getMaxHealth()); });
                return "Vigor: forty more health, and it keeps coming back";
            }
            case 2 -> { eff(sp, MobEffects.DAMAGE_BOOST, 45, 3); eff(sp, MobEffects.DIG_SPEED, 45, 2); return "Lucky Strike: Strength IV and Haste III"; }
            case 3 -> { eff(sp, MobEffects.DAMAGE_RESISTANCE, 45, 2); eff(sp, MobEffects.FIRE_RESISTANCE, 45, 0); eff(sp, MobEffects.ABSORPTION, 45, 4); return "Charmed Life: Resistance III and twenty absorption"; }
            case 4 -> { eff(sp, MobEffects.MOVEMENT_SPEED, 60, 3); eff(sp, MobEffects.JUMP, 60, 2); eff(sp, MobEffects.SLOW_FALLING, 60, 0); eff(sp, MobEffects.LUCK, 60, 4); return "Fair Wind: Speed IV, Luck V, and you fall like a feather"; }
            default -> {  // the jackpot
                for (int k = 0; k < 5; k++) boon(sp, k);
                sp.level().playSound(null, sp.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.2f, 1f);
                return "JACKPOT: every boon on the wheel";
            }
        }
    }

    /** One of the wheel's banes: short, and none of them deadly. */
    private static String bane(ServerPlayer sp, int which) {
        switch (which) {
            case 0 -> { eff(sp, MobEffects.MOVEMENT_SLOWDOWN, 15, 1); eff(sp, MobEffects.UNLUCK, 30, 2); return "Lead Feet: slowed and unlucky"; }
            case 1 -> { eff(sp, MobEffects.WEAKNESS, 15, 1); return "Butterfingers: weakened"; }
            case 2 -> { spirit(sp, -.2); return "The House's Cut: a fifth of your spirituality is gone"; }
            default -> { eff(sp, MobEffects.BLINDNESS, 6, 0); eff(sp, MobEffects.CONFUSION, 8, 0); return "Snake Eyes: the world spins"; }
        }
    }

    /**
     * Monster: spin the wheel. Seven times in ten it lands on a boon (nine in ten for a Seq 1/0 Monster, who also spins twice):
     * spirituality pouring back, forty more health with strong regeneration, Strength IV, Resistance III with absorption,
     * Speed IV with Luck V, or, one spin in twelve, all of them at once. Otherwise a short bane.
     */
    public static void wheel(ServerPlayer sp, ItemStack st) {
        fx(sp, 2);
        boolean emp = UniqPower.emp(sp, "monster");
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, .8f);
        for (int i = 1; i <= 5; i++) { final float pitch = .7f + i * .15f; later(i * 9, () -> sp.level().playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1f, pitch)); }   // the wheel ticking down
        for (int spinN = 0; spinN < (emp ? 2 : 1); spinN++) {
            boolean good = sp.getRandom().nextInt(10) < (emp ? 9 : 7);
            int pick = good ? (sp.getRandom().nextInt(12) == 0 ? 5 : sp.getRandom().nextInt(5)) : sp.getRandom().nextInt(4);
            later(60 + spinN * 30, () -> {
                if (!sp.isAlive()) return;
                String what = good ? boon(sp, pick) : bane(sp, pick);
                sp.level().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, good ? 1.4f : 0.5f);
                if (good && sp.level() instanceof net.minecraft.server.level.ServerLevel lv) lv.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, sp.getX(), sp.getY() + 1, sp.getZ(), pick == 5 ? 120 : 40, .5, .8, .5, .3);
                sp.sendSystemMessage(Component.literal("The wheel lands on " + what + ".").withStyle(good ? ChatFormatting.GOLD : ChatFormatting.DARK_RED));
                sp.displayClientMessage(Component.literal(what).withStyle(good ? ChatFormatting.GOLD : ChatFormatting.DARK_RED), true);
            });
        }
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
