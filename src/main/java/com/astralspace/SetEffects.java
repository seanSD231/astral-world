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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What four pieces of a set actually do.
 *
 * <p>Each one watches something the player does -- landing a blow, being hurt, healing, standing
 * still, running low -- and answers it. The state is a handful of counters per player, kept on the
 * server and thrown away on logout, because none of it is worth saving: every effect is about the
 * last few seconds, and a player who reconnects should start those few seconds again.</p>
 *
 * <p>Nothing here fires unless four pieces of that set are actually worn, which is checked against
 * the worn slots every time rather than cached, so taking a piece off stops the effect at once.</p>
 */
public final class SetEffects {
    private SetEffects() { }

    private static boolean has(Player p, String set) {
        Integer n = ArtifactSets.counts(ArtifactSlots.worn(p)).get(set);
        return n != null && n >= 4;
    }

    /** Everything the sets remember about one player. */
    private static final class State {
        int pressure;              // steam
        long untouched;            // acting: the tick they were last hit
        long stillSince = -1;      // harvest: the tick they stopped moving
        double lastX, lastY, lastZ;
        String lastBlow = "";      // ledger
        long lastBlowAt;
        int alchemy;               // alchemy: stacks
        long alchemyAt;
        long fogAt;                // fog: when it last went off
        long tarotAt;              // tarot: when the free blow was last spent
        long asceticAt;            // ascetic: the last tick it mended
        long stormAt;              // storms: when the bolt last fell
        final Map<UUID, Long> marked = new HashMap<>();     // evernight
        final List<UUID> threaded = new ArrayList<>();      // thread
        long threadedAt;
    }

    private static final Map<UUID, State> S = new HashMap<>();

    private static State st(Player p) { return S.computeIfAbsent(p.getUUID(), k -> new State()); }

    public static void forget(UUID id) { S.remove(id); }

    // ---- a blow landing ----

    /**
     * Runs before the damage is applied, so the multipliers stack onto one another and whatever
     * else the game had already decided about the blow still counts.
     */
    public static void onDeal(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        LivingEntity target = e.getEntity();
        State s = st(p);
        long now = p.level().getGameTime();
        float mult = 1f;
        ServerLevel lv = p.serverLevel();

        if (has(p, "acting") && now - s.untouched >= 160) {
            mult *= 1.33f;
            lv.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + 1, target.getZ(), 10, .3, .5, .3, .1);
        }

        if (has(p, "ledger")) {
            String kind = e.getSource().getMsgId();
            if (!kind.equals(s.lastBlow) && now - s.lastBlowAt < 120) mult *= 1.25f;
            s.lastBlow = kind;
            s.lastBlowAt = now;
        }

        if (has(p, "control") && p.getHealth() < p.getMaxHealth() * .4f) mult *= 1.5f;

        if (has(p, "tarot") && now - s.tarotAt >= 400) {
            s.tarotAt = now;
            mult *= 1.33f;
            spirit(p, 60);
            lv.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .7f, 1.6f);
        }

        if (has(p, "steam") && s.pressure >= 5) {
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

        if (has(p, "storms")) {
            long wait = p.isInWaterOrRain() ? 100 : 200;   // in the weather it comes twice as often
            if (now - s.stormAt >= wait) {
                s.stormAt = now;
                Entity bolt = EntityType.LIGHTNING_BOLT.create(lv);
                if (bolt instanceof net.minecraft.world.entity.LightningBolt lb) {
                    lb.moveTo(target.getX(), target.getY(), target.getZ());
                    lb.setVisualOnly(true);
                    lv.addFreshEntity(lb);
                }
                target.hurt(lv.damageSources().playerAttack(p), 8f);
            }
        }

        if (has(p, "evernight")) s.marked.put(target.getUUID(), now);

        if (has(p, "thread")) {
            if (now - s.threadedAt > 120) { s.threaded.clear(); s.threadedAt = now; }
            if (!s.threaded.contains(target.getUUID())) s.threaded.add(target.getUUID());
            if (s.threaded.size() > 6) s.threaded.remove(0);
            float bleed = e.getNewDamage() * .2f;
            for (UUID u : new ArrayList<>(s.threaded)) {
                if (u.equals(target.getUUID())) continue;
                Entity x = lv.getEntity(u);
                if (x instanceof LivingEntity le && le.isAlive() && !(le instanceof Player)) {
                    le.hurt(lv.damageSources().indirectMagic(p, p), bleed);
                    lv.sendParticles(ParticleTypes.SOUL, le.getX(), le.getY() + 1, le.getZ(), 6, .2, .4, .2, .02);
                }
            }
        }

        if (mult != 1f) e.setNewDamage(e.getNewDamage() * mult);
    }

    // ---- being hurt ----

    public static void onTake(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        State s = st(p);
        s.untouched = p.level().getGameTime();

        if (has(p, "steam")) s.pressure = Math.min(5, s.pressure + 1);
        if (has(p, "ascetic") && p.getHealth() < p.getMaxHealth() * .5f) e.setNewDamage(e.getNewDamage() * .8f);

        if (has(p, "fog") && p.level().getGameTime() - s.fogAt >= 1200) {
            float after = p.getHealth() - e.getNewDamage();
            if (after < p.getMaxHealth() * .5f && after > 0) {
                s.fogAt = p.level().getGameTime();
                ServerLevel lv = p.serverLevel();
                Vec3 look = p.getLookAngle();
                p.teleportTo(p.getX() - look.x * 6, p.getY(), p.getZ() - look.z * 6);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 9, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false));
                lv.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 80, 1.2, 1, 1.2, .05);
                lv.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, .8f, 1.4f);
                p.displayClientMessage(Component.literal("The fog closes over you.").withColor(0xAAAAAA), true);
            }
        }
    }

    // ---- healing ----

    public static void onHeal(net.neoforged.neoforge.event.entity.living.LivingHealEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        State s = st(p);
        if (has(p, "control")) e.setAmount(e.getAmount() * .5f);
        if (has(p, "alchemy")) {
            s.alchemy = Math.min(5, s.alchemy + 1);
            s.alchemyAt = p.level().getGameTime();
        }
    }

    // ---- a kill ----

    public static void onKill(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        State s = st(p);
        if (!has(p, "evernight")) return;
        Long at = s.marked.remove(e.getEntity().getUUID());
        if (at == null) return;
        spirit(p, 80);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false));
        p.serverLevel().sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1, p.getZ(), 24, .4, .6, .4, .04);
    }

    // ---- every second ----

    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 10 != 0) return;
        long now = e.getServer().getTickCount();
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            State s = S.get(p.getUUID());
            if (s == null) continue;
            long gt = p.level().getGameTime();

            // harvest: rooted while still
            if (has(p, "harvest")) {
                boolean moved = Math.abs(p.getX() - s.lastX) > .04 || Math.abs(p.getZ() - s.lastZ) > .04
                             || Math.abs(p.getY() - s.lastY) > .04;
                if (moved) s.stillSince = -1;
                else if (s.stillSince < 0) s.stillSince = gt;
                if (s.stillSince >= 0 && gt - s.stillSince >= 40) {
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, 1, false, false));
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 1, false, false));
                    p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + .2, p.getZ(), 4, .5, .1, .5, 0);
                }
            }
            s.lastX = p.getX(); s.lastY = p.getY(); s.lastZ = p.getZ();

            // ascetic: mending while low
            if (has(p, "ascetic") && p.getHealth() < p.getMaxHealth() * .5f && gt - s.asceticAt >= 20) {
                s.asceticAt = gt;
                p.heal(Math.max(1f, p.getMaxHealth() * .015f));
                p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 5, .3, .5, .3, .01);
            }

            burstOff(p, gt);

            // alchemy stacks fall off
            if (s.alchemy > 0 && gt - s.alchemyAt > 160) s.alchemy = 0;

            // marks and threads expire
            s.marked.entrySet().removeIf(en -> gt - en.getValue() > 200);
            if (gt - s.threadedAt > 120) s.threaded.clear();
        }
    }

    /** How much extra ability power the alchemy stacks are worth right now, as a share. */
    public static double alchemy(Player p) {
        State s = S.get(p.getUUID());
        return s == null || !has(p, "alchemy") ? 0 : s.alchemy * .06;
    }

    /**
     * Giving spirituality back. There is no way to set another mod's resource from here, so what is
     * given is a short, strong burst of its regeneration instead -- the same thing arriving a little
     * more slowly. Without that mod installed the attribute is not there and nothing is given.
     */
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
        BURST.put(p.getUUID(), p.level().getGameTime() + (long) (seconds));
    }

    private static final Map<UUID, Long> BURST = new HashMap<>();

    /** Takes the spirituality burst back off once its moment has passed. */
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

    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        forget(e.getEntity().getUUID());
        BURST.remove(e.getEntity().getUUID());
    }
}
