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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spending a five-fragment ability.
 *
 * <p>Each one does something at once and then charges for it, and the charge is always worse than
 * the ability is good for about as long as the ability lasted. That is the shape of the whole
 * system: a fragment will do the thing, but it has no sense of when, so the sense has to be yours.
 * Nothing here is free and nothing is on a short enough leash to be spammed.</p>
 */
public final class SetAbilities {
    private SetAbilities() { }

    /** Thirty seconds between castings, per set. */
    private static final int COOLDOWN = 600;

    private static final Map<UUID, Map<String, Long>> USED = new HashMap<>();
    /** Marks a player is carrying from an ability: set id to the tick it runs out. */
    private static final Map<UUID, Map<String, Long>> LIVE = new HashMap<>();

    public static void forget(UUID id) { USED.remove(id); LIVE.remove(id); }

    private static boolean live(Player p, String key) {
        Map<String, Long> m = LIVE.get(p.getUUID());
        return m != null && m.getOrDefault(key, 0L) > p.level().getGameTime();
    }

    private static void mark(Player p, String key, int ticks) {
        LIVE.computeIfAbsent(p.getUUID(), k -> new HashMap<>())
            .put(key, p.level().getGameTime() + ticks);
    }

    /** True while this player is mirroring everything back. */
    public static boolean mirroring(Player p) { return live(p, "mirror_on"); }

    /** True while nothing can touch them. */
    public static boolean untouchable(Player p) { return live(p, "immune"); }

    /** True while they may not be mended at all. */
    public static boolean sealedFromHealing(Player p) { return live(p, "no_heal"); }

    /** True while their next blows all count as a new kind. */
    public static boolean readBack(Player p) { return live(p, "read_back"); }

    /** True while everything is untouched again as far as they are concerned. */
    public static boolean firstBlood(Player p) { return live(p, "first_blood"); }

    /** True while they are held in the role whatever lands on them. */
    public static boolean onStage(Player p) { return live(p, "stage"); }

    // ---- casting ----

    public static void cast(ServerPlayer p, String set) {
        ArtifactSets.Set s = ArtifactSets.get(set);
        if (s == null) return;
        Integer n = ArtifactSets.counts(ArtifactSlots.worn(p)).get(set);
        if (n == null || n < 5) {
            p.displayClientMessage(Component.literal("Not all five are on you.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return;
        }
        long now = p.level().getGameTime();
        Map<String, Long> used = USED.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        long last = used.getOrDefault(set, -99999L);
        if (now - last < COOLDOWN) {
            p.displayClientMessage(Component.literal(((COOLDOWN - (now - last)) / 20) + "s")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY), true);
            return;
        }
        used.put(set, now);

        ServerLevel lv = p.serverLevel();
        float k = .5f + .5f * tier(p, set);          // how fine the fragments are
        switch (set) {
            case "evernight" -> {
                blind(lv, p, 14, 160);
                p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 160, 0, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 160, 1, false, true));
                mark(p, "evernight_burn", 400);
                puff(lv, p, ParticleTypes.SQUID_INK, 140);
            }
            case "storms" -> {
                Vec3 look = p.getLookAngle();
                for (LivingEntity x : around(lv, p, 16)) {
                    Vec3 to = x.position().subtract(p.position()).normalize();
                    if (to.dot(look) < .35) continue;
                    bolt(lv, x);
                    x.hurt(lv.damageSources().playerAttack(p), 14f * k);
                }
                penalty(p, MobEffects.WEAKNESS, 300, 0);
                mark(p, "storms_thin", 300);
            }
            case "steam" -> {
                for (LivingEntity x : around(lv, p, 8)) {
                    x.hurt(lv.damageSources().playerAttack(p), 12f * k);
                    x.setRemainingFireTicks(80);
                    Vec3 away = x.position().subtract(p.position()).normalize();
                    x.push(away.x * 1.6, .6, away.z * 1.6);
                }
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 2, false, true));
                mark(p, "steam_soft", 300);
                puff(lv, p, ParticleTypes.CLOUD, 160);
            }
            case "acting" -> { mark(p, "stage", 200); puff(lv, p, ParticleTypes.ENCHANT, 90); }
            case "harvest" -> {
                mark(p, "rooted", 120);
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 3, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 3, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 9, false, false));
                puff(lv, p, ParticleTypes.HAPPY_VILLAGER, 80);
            }
            case "ledger" -> { mark(p, "read_back", 200); mark(p, "ledger_thin", 300); puff(lv, p, ParticleTypes.ENCHANT, 70); }
            case "alchemy" -> {
                for (Player a : lv.getEntitiesOfClass(Player.class, new AABB(p.blockPosition()).inflate(10)))
                    a.heal(a.getMaxHealth() * .3f * k);
                mark(p, "alchemy_full", 200);
                mark(p, "alchemy_soft", 300);
                puff(lv, p, ParticleTypes.HEART, 40);
            }
            case "fog" -> {
                Vec3 look = p.getLookAngle();
                p.teleportTo(p.getX() + look.x * 12, p.getY(), p.getZ() + look.z * 12);
                mark(p, "immune", 40);
                mark(p, "fog_weak", 160);
                puff(lv, p, ParticleTypes.CLOUD, 160);
            }
            case "thread" -> {
                for (LivingEntity x : around(lv, p, 10)) {
                    Vec3 to = p.position().subtract(x.position()).normalize().scale(1.4);
                    x.push(to.x, .3, to.z);
                    x.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 9, false, true));
                }
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1, false, true));
                puff(lv, p, ParticleTypes.SOUL, 120);
            }
            case "control" -> {
                p.setHealth(Math.max(1f, p.getHealth() * .75f));
                mark(p, "control_rage", 160);
                mark(p, "no_heal", 400);
                puff(lv, p, ParticleTypes.ANGRY_VILLAGER, 30);
            }
            case "tarot" -> {
                SetEffects.refreshTarot(p);
                spiritBurst(p);
                mark(p, "tarot_thin", 200);
                puff(lv, p, ParticleTypes.END_ROD, 80);
            }
            case "ascetic" -> { mark(p, "immune", 60); mark(p, "no_heal", 400); puff(lv, p, ParticleTypes.END_ROD, 60); }
            case "mirror" -> { mark(p, "mirror_on", 80); mark(p, "mirror_dry", 400); puff(lv, p, ParticleTypes.GLOW, 80); }
            case "gloves" -> { mark(p, "first_blood", 240); mark(p, "gloves_soft", 300); puff(lv, p, ParticleTypes.CRIT, 70); }
            case "quiet" -> {
                mark(p, "rooted", 80);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 9, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 5, false, false));
                mark(p, "quiet_spent", 400);
                puff(lv, p, ParticleTypes.TOTEM_OF_UNDYING, 90);
            }
            case "bell" -> {
                for (LivingEntity x : around(lv, p, 12)) {
                    x.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2, false, true));
                    x.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 2, false, true));
                    Vec3 away = x.position().subtract(p.position()).normalize();
                    x.push(away.x * 1.1, .35, away.z * 1.1);
                }
                mark(p, "bell_dull", 300);
                lv.playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2f, .7f);
                puff(lv, p, ParticleTypes.NOTE, 60);
            }
            case "knife" -> {
                p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 120, 0, false, false));
                for (LivingEntity x : around(lv, p, 16))
                    if (x instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == p) mob.setTarget(null);
                mark(p, "knife_open", 200);
                puff(lv, p, ParticleTypes.SMOKE, 80);
            }
            case "sealwax" -> {
                SetEffects.refreshLetter(p);
                mark(p, "sealwax_dull", 300);
                puff(lv, p, ParticleTypes.WAX_ON, 60);
            }
            default -> { }
        }
        lv.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, .8f, 1.2f);
        p.displayClientMessage(Component.literal(SetAbility.name(s)).withColor(s.colour()), true);
    }

    // ---- the prices, read back while they are live ----

    /** How much what this player deals is changed by a price they are still paying. */
    public static float dealt(Player p) {
        float k = 1f;
        if (live(p, "fog_weak")) k *= .67f;
        if (live(p, "quiet_spent")) k *= .6f;
        if (live(p, "tarot_thin")) k *= .9f;
        if (live(p, "control_rage")) k *= 2f;
        return k;
    }

    /** And how much what is dealt to them is changed. */
    public static float taken(Player p) {
        float k = 1f;
        if (live(p, "immune")) return 0f;
        if (live(p, "knife_open")) k *= 1.2f;
        if (live(p, "steam_soft")) k *= 1.15f;
        if (live(p, "gloves_soft")) k *= 1.15f;
        if (live(p, "storms_thin")) k *= 1.1f;
        if (live(p, "ledger_thin")) k *= 1.1f;
        if (live(p, "alchemy_soft")) k *= 1.2f;
        return k;
    }

    public static float healing(Player p) {
        if (sealedFromHealing(p)) return 0f;
        return live(p, "mirror_dry") ? .75f : 1f;
    }

    /** What the live prices do to ability power. */
    public static double ability(Player p) {
        double v = 0;
        if (live(p, "bell_dull")) v -= .2;
        if (live(p, "sealwax_dull")) v -= .2;
        if (live(p, "alchemy_full")) v += .3;
        return v;
    }

    public static boolean rooted(Player p) { return live(p, "rooted"); }

    public static boolean burnsInLight(Player p) { return live(p, "evernight_burn"); }

    // ---- odds and ends ----

    private static float tier(Player p, String set) {
        int sum = 0, n = 0;
        for (var st : ArtifactSlots.worn(p)) {
            if (st == null || st.isEmpty()) continue;
            Artifacts.Piece pc = Artifacts.read(st);
            if (pc == null || !pc.set().equals(set)) continue;
            sum += pc.rarity();
            n++;
        }
        return n == 0 ? 0 : (sum / (float) n) / 5f;
    }

    private static List<LivingEntity> around(ServerLevel lv, Player p, double r) {
        return lv.getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(r),
                q -> q.isAlive() && q != p && !(q instanceof Player));
    }

    private static void blind(ServerLevel lv, Player p, double r, int ticks) {
        for (LivingEntity x : around(lv, p, r)) x.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0, false, true));
    }

    private static void bolt(ServerLevel lv, LivingEntity at) {
        Entity b = EntityType.LIGHTNING_BOLT.create(lv);
        if (b instanceof net.minecraft.world.entity.LightningBolt lb) {
            lb.moveTo(at.getX(), at.getY(), at.getZ());
            lb.setVisualOnly(true);
            lv.addFreshEntity(lb);
        }
    }

    private static void penalty(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> e, int t, int amp) {
        p.addEffect(new MobEffectInstance(e, t, amp, false, true));
    }

    private static void puff(ServerLevel lv, Player p, net.minecraft.core.particles.ParticleOptions what, int n) {
        lv.sendParticles(what, p.getX(), p.getY() + 1, p.getZ(), n, 1.2, 1, 1.2, .1);
    }

    private static void spiritBurst(ServerPlayer p) {
        var h = Artifacts.Stat.SPIRIT_REGEN.attribute();
        if (h == null) return;
        var inst = p.getAttribute(h);
        if (inst == null) return;
        var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "set_ability_spirit");
        inst.removeModifier(id);
        inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                id, 5.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        mark(p, "spirit_burst", 200);
    }

    /** Takes the spirituality burst off once its moment is past, and keeps the rooted still. */
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 5 != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            if (rooted(p)) p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
            if (!live(p, "spirit_burst")) {
                var h = Artifacts.Stat.SPIRIT_REGEN.attribute();
                if (h == null) continue;
                var inst = p.getAttribute(h);
                if (inst != null) inst.removeModifier(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "set_ability_spirit"));
            }
        }
    }

    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        forget(e.getEntity().getUUID());
    }
}
