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

    private record Pending(ServerLevel lv, BlockPos at, LivingEntity from, float damage, int fuse) { }

    private static final List<Pending> WAITING = new ArrayList<>();

    public static void drop(ServerLevel lv, BlockPos at, LivingEntity from, float damage) {
        WAITING.add(new Pending(lv, at, from, damage, 25));
    }

    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        if (WAITING.isEmpty()) return;
        for (int i = WAITING.size() - 1; i >= 0; i--) {
            Pending p = WAITING.get(i);
            int fuse = p.fuse() - 1;
            WAITING.set(i, new Pending(p.lv(), p.at(), p.from(), p.damage(), fuse));
            if (fuse % 3 == 0) ring(p, 4.0);
            if (fuse > 0) continue;
            WAITING.remove(i);
            land(p);
        }
    }

    private static void ring(Pending p, double r) {
        for (int k = 0; k < 18; k++) {
            double a = k * Math.PI * 2 / 18;
            p.lv().sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    p.at().getX() + .5 + Math.cos(a) * r, p.at().getY() + .2, p.at().getZ() + .5 + Math.sin(a) * r,
                    1, 0, 0, 0, 0);
        }
    }

    private static void land(Pending p) {
        ServerLevel lv = p.lv();
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
