package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Sword of Twilight: a real sword, a slender white blade edged in gold light. Right click calls down Twilight Verdict.
 * Its sealed second form (a black blade lined in red) is released only by its one true wielder speaking the blessing while holding it;
 * unsealed it strikes a quarter harder and everything it does turns black and red.
 */
public final class TwilightSword extends WornUniq {
    private static final String BLESSER = "seanSD2311", BLESSING = "i bless this sword to release its power", TAG = "tw_unsealed";

    public TwilightSword(String pathId, String title, Item.Properties props) { super(pathId, title, 0, "right click calls down a colossal blade (100, aged, slowed) and leaves a 15 second hurricane of twilight; swing at the air to strike a foe up to 48 blocks away from the void (70); every blow ages and slows; holding it you take a quarter less damage", (sp, st) -> { }, props); }

    public static boolean unsealed(ItemStack st) {
        CustomData d = st.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().getBoolean(TAG);
    }

    @Override
    public Component getName(ItemStack stack) {
        return unsealed(stack) ? Component.literal("Sword of Twilight, Unsealed").withStyle(ChatFormatting.DARK_RED) : super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tip, flag);
        tip.add(Component.literal("Right click: Twilight Verdict. Blades of light close on the place you look, then a colossal sword falls and its light lingers, dragging in and burning.").withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal("Seq 1/0 Warrior: a barrage of swords from every side (twilight and stun on each hit), a far greater blade that ages what it strikes, and heavier basic attacks.").withStyle(ChatFormatting.GOLD));
        if (unsealed(stack)) tip.add(Component.literal("Unsealed: 25% more damage.").withStyle(ChatFormatting.DARK_RED));
    }

    /** The blessing, spoken in chat by the one wielder while the sword is in a hand. */
    public static void chat(net.neoforged.neoforge.event.ServerChatEvent e) {
        ServerPlayer sp = e.getPlayer();
        if (!sp.getGameProfile().getName().equals(BLESSER)) return;
        String said = e.getRawText().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z ]", "").replaceAll(" +", " ").trim();
        if (!said.equals(BLESSING)) return;
        ItemStack st = sp.getMainHandItem().getItem() instanceof TwilightSword ? sp.getMainHandItem() : sp.getOffhandItem().getItem() instanceof TwilightSword ? sp.getOffhandItem() : ItemStack.EMPTY;
        if (st.isEmpty() || unsealed(st)) return;
        CustomData d = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag t = d == null ? new CompoundTag() : d.copyTag();
        t.putBoolean(TAG, true);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        ServerLevel lv = sp.serverLevel();
        lv.playSound(null, sp.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, .8f, 1.4f);
        lv.playSound(null, sp.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5f, .5f);
        UniqAbilities.fxPoint(lv, 3, 8, sp.getX(), sp.getY() + 1, sp.getZ(), 2.6f, 30);
        UniqAbilities.fxPoint(lv, 6, 8, sp.getX(), sp.getY() + 1.2, sp.getZ(), 1.5f, 16);
        sp.displayClientMessage(Component.literal("The seal on the Sword of Twilight breaks.").withStyle(ChatFormatting.DARK_RED), false);
    }

    private static java.lang.reflect.Method slowTime; private static boolean auraLooked;

    /** LOTM's Aura of Twilight, the thing holding the Sword of Twilight gives: everything near the holder has its time slowed. */
    public static void aura(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 10 != 0) return;
        if (!auraLooked) {
            auraLooked = true;
            try { slowTime = Class.forName("net.swimmingtuna.lotm.pathway.warrior.abilities.AuraOfTwilight")
                    .getMethod("slowTime", net.minecraft.world.entity.Entity.class, int.class); } catch (Throwable ignored) { }
        }
        if (slowTime == null) return;
        for (net.minecraft.server.level.ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            ItemStack st = p.getMainHandItem();
            if (!(st.getItem() instanceof TwilightSword)) st = p.getOffhandItem();
            if (!(st.getItem() instanceof TwilightSword)) continue;
            double r = unsealed(st) ? 10 : 7;
            for (LivingEntity x : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), q -> UniqAbilities.foe(p, q)))
                try { slowTime.invoke(null, x, 20); } catch (Throwable ignored) { }
            p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 3, r * .4, .6, r * .4, 0.002);
        }
    }

    /** Slows a thing's time through LOTM's Aura of Twilight; nothing happens without LOTM. */
    private static void slow(net.minecraft.world.entity.Entity x, int ticks) {
        if (slowTime != null) try { slowTime.invoke(null, x, ticks); } catch (Throwable ignored) { }
    }

    private static final java.util.Map<java.util.UUID, Long> STRUCK = new java.util.HashMap<>();

    /**
     * Swinging the sword at the air strikes the foe you are looking at, up to 48 blocks away, with a blade out of the
     * void: 70 damage (LOTM's own sword does 54), aged and slowed. Once every second and a half. The client says when
     * the sword was swung at nothing ({@link Net.TwSwingPayload}); everything else is decided here.
     */
    public static void voidStrike(ServerPlayer sp) {
        ItemStack st = sp.getMainHandItem();
        if (!(st.getItem() instanceof TwilightSword) || !sp.isAlive() || sp.isSpectator()) return;
        long now = sp.level().getGameTime(); Long last = STRUCK.get(sp.getUUID());
        if (last != null && now - last < 30) return;
        ServerLevel lv = sp.serverLevel();
        Vec3 eye = sp.getEyePosition(), look = sp.getLookAngle();
        LivingEntity best = null; double bestDot = .985;
        for (LivingEntity x : lv.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(48), q -> UniqAbilities.foe(sp, q))) {
            Vec3 to = x.position().add(0, x.getBbHeight() * .5, 0).subtract(eye); double d = to.length();
            if (d < 3.5 || d > 48) continue;
            double dot = to.scale(1 / d).dot(look);
            if (dot > bestDot && sp.hasLineOfSight(x)) { bestDot = dot; best = x; }
        }
        if (best == null) return;
        STRUCK.put(sp.getUUID(), now);
        final LivingEntity tg = best; final int vr = v(st); final float mul = mul(st);
        boolean emp = UniqPower.emp(sp, "warrior");
        Vec3 to = tg.position().add(0, tg.getBbHeight() * .5, 0), from = to.add(-look.x * 6 + (lv.random.nextDouble() - .5) * 4, 7, -look.z * 6 + (lv.random.nextDouble() - .5) * 4);
        UniqAbilities.fxPath(lv, 5, vr, from, to, 6);
        lv.playSound(null, sp.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1f, .7f);
        UniqAbilities.later(6, () -> {
            if (!tg.isAlive()) return;
            tg.invulnerableTime = 0;
            tg.hurt(lv.damageSources().playerAttack(sp), (emp ? 110f : 70f) * mul);
            UniqPower.age(tg, emp ? .15f : .08f); slow(tg, 80);
            UniqAbilities.fxPoint(lv, emp ? 6 : 0, vr, to.x, to.y, to.z, 1.2f, 14);
            lv.playSound(null, tg.blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.2f, .8f);
        });
    }

    /** Walls of light: whoever holds the sword takes a quarter less from everything (35% less once it is unsealed), and allies within six blocks an eighth less. */
    public static void shelter(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || p.level().isClientSide) return;
        ItemStack st = p.getMainHandItem();
        if (!(st.getItem() instanceof TwilightSword)) st = p.getOffhandItem();
        if (st.getItem() instanceof TwilightSword) { e.setAmount(e.getAmount() * (unsealed(st) ? .65f : .75f)); return; }
        for (Player o : p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(6), q -> q != p && q.isAlliedTo(p)))
            if (o.getMainHandItem().getItem() instanceof TwilightSword || o.getOffhandItem().getItem() instanceof TwilightSword) { e.setAmount(e.getAmount() * .875f); return; }
    }

    private static float mul(ItemStack st) { return unsealed(st) ? 1.25f : 1f; }
    private static int v(ItemStack st) { return unsealed(st) ? 8 : 0; }

    private static void extra(LivingEntity e, Player by, float dmg) {
        e.invulnerableTime = 0;
        e.hurt(by.level().damageSources().magic(), dmg);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.level() instanceof ServerLevel lv && attacker instanceof Player p) {
            boolean emp = UniqPower.emp(attacker, pathId());
            float base = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE);
            double x = target.getX(), y = target.getY() + target.getBbHeight() * .5, z = target.getZ();
            if (unsealed(stack)) extra(target, p, base * .25f);
            UniqPower.age(target, emp ? .12f : .06f); slow(target, 60);   // like LOTM's own sword, every blow ages and slows what it strikes (and this one does it harder)
            if (emp) {   // a Seq 1/0 Warrior: every blow is a cross of light that bursts, cuts whoever stands close and leaves twilight on them
                UniqAbilities.fxPoint(lv, 6, v(stack), x, y, z, 1.3f, 16);
                lv.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.2f, .7f);
                extra(target, p, 10f * mul(stack));
                UniqPower.effect(target, "twilight", 60, 0);
                for (LivingEntity o : lv.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.5), q -> q != target && q != p && q.isAlive() && !q.isAlliedTo(p))) {
                    o.hurt(lv.damageSources().playerAttack(p), 8f * mul(stack)); UniqPower.effect(o, "twilight", 60, 0);
                }
            } else UniqAbilities.fxPoint(lv, 0, v(stack), x, y, z, 1f, 14);
            lv.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.5f);
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        if (p.isShiftKeyDown()) return super.use(level, p, hand);
        ItemStack st = p.getItemInHand(hand);
        if (p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(st);
        if (level instanceof ServerLevel lv && p instanceof ServerPlayer sp) {
            int cost = UniqPower.cost(st);
            if (!UniqPower.pay(sp, cost)) { sp.displayClientMessage(Component.literal("Not enough spirituality (" + cost + ").").withStyle(ChatFormatting.GRAY), true); return InteractionResultHolder.fail(st); }
            boolean emp = UniqPower.emp(sp, pathId());
            final int vr = v(st); final float mul = mul(st);
            Vec3 o = p.getEyePosition().subtract(0, .3, 0), d = p.getLookAngle();
            lv.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4f, .6f);
            lv.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2f, .7f);
            net.minecraft.world.phys.HitResult hr = p.pick(emp ? 40 : 24, 0f, false);
            final Vec3 ctr = hr.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? o.add(d.scale(12)) : hr.getLocation();
            final double rad = emp ? 13.4 : 6.0;                    // five times the ground
            final int land;
            UniqAbilities.fxPoint(lv, 3, vr, ctr.x, ctr.y + .2, ctr.z, 2.2f, 24);
            if (emp) {
                // a barrage of small swords out of every direction; each one that lands leaves twilight and a short stun
                int n = 30; land = 52;
                for (int k = 0; k < n; k++) {
                    final int kk = k;
                    UniqAbilities.later(2 + k * 40 / n, () -> {
                        List<LivingEntity> near = lv.getEntitiesOfClass(LivingEntity.class, new AABB(ctr, ctr).inflate(rad), x -> x != p && x.isAlive() && !x.isAlliedTo(p));
                        LivingEntity tg = near.isEmpty() ? null : near.get(lv.random.nextInt(near.size()));
                        Vec3 to = tg != null ? tg.position().add(0, tg.getBbHeight() * .5, 0) : ctr.add((lv.random.nextDouble() - .5) * rad, .3, (lv.random.nextDouble() - .5) * rad);
                        double a = kk * 2.39996, el = .25 + lv.random.nextDouble() * 1.1;
                        Vec3 from = to.add(Math.cos(a) * Math.cos(el) * 13, Math.sin(el) * 13, Math.sin(a) * Math.cos(el) * 13);
                        UniqAbilities.fxPath(lv, 5, vr, from, to, 7);
                        UniqAbilities.later(7, () -> {
                            lv.playSound(null, BlockPos.containing(to), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, .7f, 1.3f + lv.random.nextFloat() * .4f);
                            UniqAbilities.fxPoint(lv, 0, vr, to.x, to.y, to.z, .7f, 10);
                            if (tg != null && tg.isAlive()) {
                                tg.invulnerableTime = 0; tg.hurt(lv.damageSources().playerAttack(p), 6f * mul);
                                UniqPower.effect(tg, "twilight", 100, 0); UniqPower.stun(tg, 15);
                            }
                        });
                    });
                }
            } else {
                land = 23;
                for (int k = 0; k < 8; k++) {   // eight crescents close on the spot
                    final double a = k * Math.PI / 4;
                    UniqAbilities.later(4 + k, () -> UniqAbilities.fxPath(lv, 1, vr, ctr.add(Math.cos(a) * 7, 1.6, Math.sin(a) * 7), ctr.add(0, 1.0, 0), 8));
                }
            }
            UniqAbilities.later(land - 9, () -> {   // the colossal blade starts to fall; it lands 9 ticks later (UniqWorld.BLADE_FALL)
                UniqAbilities.fxPoint(lv, 4, vr, ctr.x, ctr.y, ctr.z, emp ? 2.2f : 1f, emp ? 110 : 62);
                lv.playSound(null, BlockPos.containing(ctr), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.2f, 1.6f);
            });
            UniqAbilities.later(land, () -> {
                UniqAbilities.fxPoint(lv, 3, vr, ctr.x, ctr.y + .5, ctr.z, emp ? 13f : 6f, 26);
                lv.playSound(null, BlockPos.containing(ctr), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, emp ? 3f : 1.6f, .6f);
                for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, new AABB(ctr, ctr).inflate(rad), x -> x != p && x.isAlive() && x.position().distanceTo(ctr) <= rad + 1)) {
                    e.invulnerableTime = 0;
                    e.hurt(lv.damageSources().playerAttack(p), (emp ? 220f : 100f) * mul); e.knockback(1.3, ctr.x - e.getX(), ctr.z - e.getZ());   // LOTM's greatsword lands for 80
                    UniqPower.age(e, emp ? .4f : .2f); slow(e, 100);
                    UniqAbilities.fxPoint(lv, 0, vr, e.getX(), e.getY() + e.getBbHeight() * .5, e.getZ(), 1f, 14);
                }
            });
            for (int w = 1; w <= (emp ? 34 : 25); w++) {   // the hurricane of twilight the blade leaves: 15 seconds (20 for a Seq 1/0), a pulse every 12 ticks, dragging everything in
                final int ww = w;
                UniqAbilities.later(land + w * 12, () -> {
                    UniqAbilities.fxPoint(lv, 3, vr, ctr.x, ctr.y + .3, ctr.z, emp ? 7f : 3.2f, 14);
                    if (ww % 2 == 0) for (int k = 0; k < 3; k++) { double a = ww * .9 + k * 2.094; UniqAbilities.fxPath(lv, 1, vr, ctr.add(Math.cos(a) * rad, 1.4, Math.sin(a) * rad), ctr.add(Math.cos(a + 1.9) * rad * .35, 1.0, Math.sin(a + 1.9) * rad * .35), 10); }   // crescents wheeling round it
                    if (ww % 5 == 0) lv.playSound(null, BlockPos.containing(ctr), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, .8f, .6f);
                    for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, new AABB(ctr, ctr).inflate(rad), x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
                        e.hurt(lv.damageSources().playerAttack(p), (emp ? 20f : 10f) * mul);
                        e.push((ctr.x - e.getX()) * .1, .05, (ctr.z - e.getZ()) * .1); e.hurtMarked = true;
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
                        slow(e, 30);
                    }
                });
            }
            p.getCooldowns().addCooldown(this, 400);
        }
        p.swing(hand);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }
}
