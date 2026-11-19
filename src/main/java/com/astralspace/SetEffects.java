package com.astralspace;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the fragments do.
 *
 * <p>Three tiers and every one of them costs something, because a fragment carries a piece of what
 * the sealed artifact did without the thing's judgement about when to do it. Two fragments is two
 * stats and a third taken away, which is handled where the stats are added up. Four is a mechanic
 * with a drawback riding along. Five is an ability, as strong as the fragments are fine, with a
 * price set by that ability.</p>
 *
 * <p>Everything is checked against the worn slots at the moment it fires rather than cached, so
 * taking one piece off stops the tier at once. The state is counters per player, kept on the server
 * and dropped at logout: all of it is about the last few seconds.</p>
 */
public final class SetEffects {
    private SetEffects() { }

    // ---- which tiers are live ----

    private static int count(Player p, String set) {
        Integer n = ArtifactSets.counts(ArtifactSlots.worn(p)).get(set);
        return n == null ? 0 : n;
    }

    private static boolean four(Player p, String set) { return count(p, set) >= 4; }

    private static boolean five(Player p, String set) { return count(p, set) >= 5; }

    /**
     * How fine the five fragments are, nought to one. Five of the best is the whole of the thing;
     * five of the worst is its shape and little else. Both the ability and its price ride on this.
     */
    private static float tier(Player p, String set) {
        int sum = 0, n = 0;
        for (ItemStack st : ArtifactSlots.worn(p)) {
            if (st == null || st.isEmpty()) continue;
            Artifacts.Piece pc = Artifacts.read(st);
            if (pc == null || !pc.set().equals(set)) continue;
            sum += pc.rarity();
            n++;
        }
        return n == 0 ? 0 : (sum / (float) n) / 5f;
    }

    // ---- what is remembered ----

    private static final class State {
        int pressure, alchemy, streak, letterBlows;
        long untouched, roleBroke, lastBlowAt, alchemyAt, stillSince = -1, movedAt;
        long fogAt, fogSpent, tarotAt, asceticAt, stormAt, mirrorAt, lampAt, quietAt, quietSpent, idleSince;
        double lastX, lastY, lastZ;
        String lastBlow = "", prevBlow = "";
        final Map<UUID, Long> marked = new HashMap<>();
        final Map<UUID, Integer> struck = new HashMap<>();
        final List<UUID> threaded = new ArrayList<>();
        long threadedAt;
    }

    private static final Map<UUID, State> S = new HashMap<>();
    private static final Map<UUID, Long> BURST = new HashMap<>();

    private static State st(Player p) { return S.computeIfAbsent(p.getUUID(), k -> new State()); }

    public static void forget(UUID id) { S.remove(id); BURST.remove(id); }

    private static boolean lit(ServerPlayer p) {
        return p.serverLevel().getMaxLocalRawBrightness(p.blockPosition()) >= 9;
    }

    // ================= a blow landing =================

    public static void onDeal(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        LivingEntity target = e.getEntity();
        ServerLevel lv = p.serverLevel();
        State s = st(p);
        long now = p.level().getGameTime();
        boolean light = lit(p);
        float mult = 1f;

        if (four(p, "acting")) {
            if (now - s.untouched >= 160) {
                mult *= 1.33f;
                lv.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + 1, target.getZ(), 10, .3, .5, .3, .1);
            } else if (now - s.roleBroke < 80) mult *= .8f;
        }

        if (four(p, "ledger")) {
            String kind = e.getSource().getMsgId();
            if (now - s.lastBlowAt < 120) {
                if (!kind.equals(s.lastBlow)) {
                    mult *= 1.25f;
                    s.streak = kind.equals(s.prevBlow) ? 1 : s.streak + 1;
                } else { mult *= .8f; s.streak = 0; }
            } else s.streak = 0;
            s.prevBlow = s.lastBlow;
            s.lastBlow = kind;
        }
        s.lastBlowAt = now;

        if (four(p, "control") && p.getHealth() < p.getMaxHealth() * .4f) {
            mult *= 1.5f;
        }

        if (four(p, "tarot")) {
            long wait = five(p, "tarot") ? 240 : 400;
            if (now - s.tarotAt >= wait) {
                s.tarotAt = now;
                mult *= 1.33f;
                spirit(p, 60);
                lv.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .7f, 1.6f);
            } else mult *= .9f;
        }

        if (four(p, "steam")) {
            if (s.pressure >= 5) {
                s.pressure = 0;
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, false, true));
                for (LivingEntity x : lv.getEntitiesOfClass(LivingEntity.class, new AABB(target.blockPosition()).inflate(5),
                        q -> q.isAlive() && q != p && !(q instanceof Player))) {
                    x.hurt(lv.damageSources().playerAttack(p), 6f);
                    Vec3 away = x.position().subtract(target.position()).normalize();
                    x.push(away.x * .8, .35, away.z * .8);
                }
                lv.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + .6, target.getZ(), 60, 1.2, .4, 1.2, .08);
                lv.playSound(null, target.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, .9f, 1.3f);
            }
        }

        if (four(p, "storms")) {
            long wait = p.isInWaterOrRain() ? 100 : 200;
            if (now - s.stormAt >= wait) {
                s.stormAt = now;
                bolt(lv, target);
                target.hurt(lv.damageSources().playerAttack(p), 8f);
                p.hurt(lv.damageSources().magic(), .8f);
            }
        }

        if (four(p, "mirror") && now - s.mirrorAt >= 300)
            for (LivingEntity x : lv.getEntitiesOfClass(LivingEntity.class, new AABB(target.blockPosition()).inflate(8),
                    q -> q.isAlive() && q != target && q != p && !(q instanceof Player))) {
                s.mirrorAt = now;
                x.hurt(lv.damageSources().indirectMagic(p, p), e.getNewDamage() * .75f);
                p.hurt(lv.damageSources().magic(), e.getNewDamage() * .1f);
                lv.sendParticles(ParticleTypes.GLOW, x.getX(), x.getY() + 1, x.getZ(), 14, .3, .5, .3, .03);
                break;
            }

        if (four(p, "gloves")) {
            int hits = s.struck.merge(target.getUUID(), 1, Integer::sum);
            mult *= hits == 1 ? 1.5f : Math.max(.67f, 1f - (hits - 1) * .08f);
        }

        if (four(p, "knife")) {
            boolean seen = target instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == p;
            if (!seen) {
                mult *= 1.6f;
                lv.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(), 20, .3, .4, .3, .2);
            }
        }

        if (four(p, "bell")) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1, false, true));
        }

        if (four(p, "sealwax")) {
            if (now - s.idleSince >= 200) {
                mult *= 2f;
                s.letterBlows = 0;
            } else s.letterBlows = Math.min(4, s.letterBlows + 1);
            s.idleSince = now;
        }

        if (four(p, "evernight")) s.marked.put(target.getUUID(), now);

        if (four(p, "lamp") && !light) mult *= .75f;

        if (four(p, "ascetic")) {
            boolean low = p.getHealth() < p.getMaxHealth() * .5f;
            if (!low) mult *= .84f;
        }

        if (four(p, "fog") && now - s.fogSpent < 100) mult *= .67f;
        if (four(p, "quiet") && now - s.quietSpent < 400) mult *= .6f;

        if (four(p, "thread")) {
            if (now - s.threadedAt > 120) s.threaded.clear();
            s.threadedAt = now;
            if (!s.threaded.contains(target.getUUID())) s.threaded.add(target.getUUID());
            if (s.threaded.size() > 6) s.threaded.remove(0);
            float share = five(p, "thread") ? .2f + .2f * tier(p, "thread") : .2f;
            float bleed = e.getNewDamage() * share;
            for (UUID u : new ArrayList<>(s.threaded)) {
                if (u.equals(target.getUUID())) continue;
                Entity x = lv.getEntity(u);
                if (x instanceof LivingEntity le && le.isAlive() && !(le instanceof Player)) {
                    le.hurt(lv.damageSources().indirectMagic(p, p), bleed);
                    if (five(p, "thread")) le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, true));
                    lv.sendParticles(ParticleTypes.SOUL, le.getX(), le.getY() + 1, le.getZ(), 6, .2, .4, .2, .02);
                }
            }
        }

        if (mult != 1f) e.setNewDamage(e.getNewDamage() * mult);
    }

    private static void bolt(ServerLevel lv, LivingEntity at) {
        Entity b = EntityType.LIGHTNING_BOLT.create(lv);
        if (b instanceof net.minecraft.world.entity.LightningBolt lb) {
            lb.moveTo(at.getX(), at.getY(), at.getZ());
            lb.setVisualOnly(true);
            lv.addFreshEntity(lb);
        }
    }

    // ================= being hurt =================

    public static void onTake(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        State s = st(p);
        long now = p.level().getGameTime();
        ServerLevel lv = p.serverLevel();
        boolean light = lit(p);
        float mult = 1f;

        s.untouched = now;

        if (four(p, "steam")) {
            s.pressure = Math.min(5, s.pressure + 1);
            if (s.pressure >= 5) mult *= 1.15f;
        }
        if (four(p, "ascetic") && p.getHealth() < p.getMaxHealth() * .5f) mult *= .8f;
        if (four(p, "thread") && now - s.threadedAt < 120) mult *= 1.1f;
        if (four(p, "knife") && e.getSource().getEntity() instanceof net.minecraft.world.entity.Mob mob
                && mob.getTarget() == p) mult *= 1.2f;


        float after = p.getHealth() - e.getNewDamage() * mult;

        if (four(p, "quiet") && after <= 0 && now - s.quietAt >= 6000) {
            s.quietAt = now;
            s.quietSpent = now;
            e.setNewDamage(0f);
            p.setHealth(Math.max(1f, p.getMaxHealth() * .04f));
            lv.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 120, .6, 1, .6, .4);
            lv.playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, .9f, .8f);
            p.displayClientMessage(Component.literal("Not yet.").withColor(0x9AB0C8), true);
            return;
        }

        if (four(p, "fog") && now - s.fogAt >= 1200 && after < p.getMaxHealth() * .5f && after > 0) {
            s.fogAt = now;
            s.fogSpent = now;
            Vec3 look = p.getLookAngle();
            p.teleportTo(p.getX() - look.x * 6, p.getY(), p.getZ() - look.z * 6);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 9, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false));
            lv.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 80, 1.2, 1, 1.2, .05);
            lv.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, .8f, 1.4f);
            p.displayClientMessage(Component.literal("The fog closes over you.").withColor(0xAAAAAA), true);
        }

        if (mult != 1f) e.setNewDamage(e.getNewDamage() * mult);
    }

    // ================= healing =================

    public static void onHeal(net.neoforged.neoforge.event.entity.living.LivingHealEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        State s = st(p);
        float k = 1f;
        if (four(p, "control")) k *= .5f;
        if (four(p, "alchemy")) {
            k *= .75f;
            s.alchemy = Math.min(5, s.alchemy + 1);
            s.alchemyAt = p.level().getGameTime();
        }
        if (four(p, "harvest") && p.level().getGameTime() - s.movedAt < 20) k *= .67f;
        if (k != 1f) e.setAmount(e.getAmount() * k);
    }

    // ================= a kill =================

    public static void onKill(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        State s = st(p);
        if (four(p, "evernight") && s.marked.remove(e.getEntity().getUUID()) != null) {
            spirit(p, 80);
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false));
            p.serverLevel().sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1, p.getZ(), 24, .4, .6, .4, .04);
        }
    }

    // ================= every half second =================

    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 10 != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            State s = S.get(p.getUUID());
            if (s == null) continue;
            long gt = p.level().getGameTime();
            ServerLevel lv = p.serverLevel();
            boolean light = lit(p);

            boolean moved = Math.abs(p.getX() - s.lastX) > .04 || Math.abs(p.getZ() - s.lastZ) > .04
                         || Math.abs(p.getY() - s.lastY) > .04;
            if (moved) {
                s.stillSince = -1;
                s.movedAt = gt;
            } else if (s.stillSince < 0) s.stillSince = gt;
            s.lastX = p.getX(); s.lastY = p.getY(); s.lastZ = p.getZ();

            if (four(p, "harvest") && s.stillSince >= 0 && gt - s.stillSince >= 40) {
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, 1, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 1, false, false));
                lv.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + .2, p.getZ(), 4, .5, .1, .5, 0);
            }
            if (four(p, "ascetic") && p.getHealth() < p.getMaxHealth() * .5f && gt - s.asceticAt >= 20) {
                s.asceticAt = gt;
                p.heal(Math.max(1f, p.getMaxHealth() * .015f));
            }
            if (four(p, "lamp") && light && gt - s.lampAt >= 20) {
                s.lampAt = gt;
                p.heal(Math.max(.5f, p.getMaxHealth() * .01f));
            }

            if (s.alchemy > 0 && gt - s.alchemyAt > 160) s.alchemy = 0;
            if (gt - s.lastBlowAt > 200) s.struck.clear();
            s.marked.entrySet().removeIf(en -> gt - en.getValue() > 200);
            if (gt - s.threadedAt > 120) s.threaded.clear();
            burstOff(p, gt);
        }
    }

    /** What the alchemy stacks are worth, less what the letter's blows have cost. */
    public static double abilityShift(Player p) {
        State s = S.get(p.getUUID());
        if (s == null) return 0;
        double v = 0;
        if (four(p, "alchemy")) v += s.alchemy * .06;
        if (four(p, "sealwax")) v -= s.letterBlows * .05;
        return v;
    }

    private static void spirit(ServerPlayer p, double seconds) {
        p.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1, p.getZ(), 12, .3, .5, .3, .02);
        var h = Artifacts.Stat.SPIRIT_REGEN.attribute();
        if (h == null) return;
        var inst = p.getAttribute(h);
        if (inst == null) return;
        var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "set_spirit_burst");
        inst.removeModifier(id);
        inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                id, 3.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        BURST.put(p.getUUID(), p.level().getGameTime() + (long) seconds);
    }

    private static void burstOff(ServerPlayer p, long gt) {
        Long until = BURST.get(p.getUUID());
        if (until == null || gt < until) return;
        BURST.remove(p.getUUID());
        var h = Artifacts.Stat.SPIRIT_REGEN.attribute();
        if (h == null) return;
        var inst = p.getAttribute(h);
        if (inst != null) inst.removeModifier(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "set_spirit_burst"));
    }

    /** The Tarot Club's free blow, made ready at once. */
    public static void refreshTarot(Player p) { State s = st(p); s.tarotAt = p.level().getGameTime() - 100000; }

    /** The Unopened Letter's held blow, made ready at once. */
    public static void refreshLetter(Player p) { State s = st(p); s.idleSince = p.level().getGameTime() - 100000; }

    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        forget(e.getEntity().getUUID());
    }
}
