package com.astralspace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * A shaft of the moon's light, marked on the ground before it lands.
 *
 * <p>Telegraphed and then slow: a boss that hits the instant you are targeted is a boss you cannot
 * play against. The mark is drawn for a second and a quarter, and standing out of it when it falls
 * is the whole of the answer.</p>
 */
public final class MoonBeam {
    private MoonBeam() { }

    /** kind 0: a shaft of light on a spot. kind 1: the lance, down a line. kind 2: the bloom, a ring going out. */
    private record Pending(ServerLevel lv, BlockPos at, LivingEntity from, float damage, int fuse,
                           int kind, double dx, double dz) { }

    private static final List<Pending> WAITING = new ArrayList<>();

    public static void drop(ServerLevel lv, BlockPos at, LivingEntity from, float damage) {
        WAITING.add(new Pending(lv, at, from, damage, 25, 0, 0, 0));
    }

    /** The lance: a long thin line laid out from it toward the target, then run through. */
    public static void lance(ServerLevel lv, BlockPos at, LivingEntity from, float damage, double dx, double dz) {
        double l = Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-4) return;
        WAITING.add(new Pending(lv, at, from, damage, 22, 1, dx / l, dz / l));
    }

    /** The bloom: the corolla thrown open and everything near it thrown with it. */
    public static void bloom(ServerLevel lv, BlockPos at, LivingEntity from, float damage) {
        WAITING.add(new Pending(lv, at, from, damage, 18, 2, 0, 0));
    }

    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        if (WAITING.isEmpty()) return;
        for (int i = WAITING.size() - 1; i >= 0; i--) {
            Pending p = WAITING.get(i);
            int fuse = p.fuse() - 1;
            WAITING.set(i, new Pending(p.lv(), p.at(), p.from(), p.damage(), fuse, p.kind(), p.dx(), p.dz()));
            if (fuse % 3 == 0) mark(p);
            if (fuse > 0) continue;
            WAITING.remove(i);
            land(p);
        }
    }

    /** The warning, drawn on the ground in the shape the blow will take. */
    private static void mark(Pending p) {
        ServerLevel lv = p.lv();
        switch (p.kind()) {
            case 1 -> {
                for (int k = 1; k <= 26; k++)
                    lv.sendParticles(ParticleTypes.DRIPPING_OBSIDIAN_TEAR,
                            p.at().getX() + .5 + p.dx() * k, p.at().getY() + .4, p.at().getZ() + .5 + p.dz() * k,
                            1, .22, .05, .22, 0);
            }
            case 2 -> {
                for (int k = 0; k < 28; k++) {
                    double a = k * Math.PI * 2 / 28;
                    lv.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR,
                            p.at().getX() + .5 + Math.cos(a) * 9, p.at().getY() + .3, p.at().getZ() + .5 + Math.sin(a) * 9,
                            1, 0, 0, 0, 0);
                }
            }
            default -> {
                for (int k = 0; k < 18; k++) {
                    double a = k * Math.PI * 2 / 18;
                    lv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            p.at().getX() + .5 + Math.cos(a) * 4, p.at().getY() + .2, p.at().getZ() + .5 + Math.sin(a) * 4,
                            1, 0, 0, 0, 0);
                }
            }
        }
    }

    private static void land(Pending p) {
        ServerLevel lv = p.lv();
        switch (p.kind()) {
            case 1 -> {   // the lance, run down the line it was laid along
                lv.playSound(null, p.at(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.HOSTILE, 1.8f, .8f);
                for (int k = 1; k <= 26; k++) {
                    double x = p.at().getX() + .5 + p.dx() * k, z = p.at().getZ() + .5 + p.dz() * k;
                    lv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, p.at().getY() + 1, z, 10, .3, .5, .3, .04);
                    for (Player t : lv.getEntitiesOfClass(Player.class,
                            new AABB(x - 1.6, p.at().getY() - 2, z - 1.6, x + 1.6, p.at().getY() + 4, z + 1.6),
                            q -> q.isAlive() && !q.isSpectator() && !q.isCreative())) {
                        t.hurt(lv.damageSources().indirectMagic(p.from(), p.from()), p.damage());
                        Corruption.add(t, 2);
                    }
                }
            }
            case 2 -> {   // the bloom, everything near it thrown off
                lv.playSound(null, p.at(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6f, 1.5f);
                for (int k = 0; k < 90; k++) {
                    double a = k * Math.PI * 2 / 90;
                    lv.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR,
                            p.at().getX() + .5 + Math.cos(a) * 9, p.at().getY() + 1.2, p.at().getZ() + .5 + Math.sin(a) * 9,
                            2, .4, .6, .4, .05);
                }
                for (Player t : lv.getEntitiesOfClass(Player.class, new AABB(p.at()).inflate(10, 5, 10),
                        q -> q.isAlive() && !q.isSpectator() && !q.isCreative())) {
                    t.hurt(lv.damageSources().indirectMagic(p.from(), p.from()), p.damage());
                    var away = t.position().subtract(p.at().getX() + .5, p.at().getY(), p.at().getZ() + .5).normalize();
                    t.push(away.x * 1.3, .5, away.z * 1.3);
                    Corruption.add(t, 3);
                }
            }
            default -> {
                lv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.at().getX() + .5, p.at().getY() + 3, p.at().getZ() + .5,
                        160, .9, 3, .9, .08);
                lv.playSound(null, p.at(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 1.6f, 1.4f);
                for (Player t : lv.getEntitiesOfClass(Player.class, new AABB(p.at()).inflate(4.0, 5, 4.0),
                        x -> x.isAlive() && !x.isSpectator() && !x.isCreative())) {
                    t.hurt(lv.damageSources().indirectMagic(p.from(), p.from()), p.damage());
                    Corruption.add(t, 3);
                }
            }
        }
    }
}
