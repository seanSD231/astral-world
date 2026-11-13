package com.astralspace;

import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * Client only: the Mother Tree fight in Photon. {@link AttackFx} still draws the exact shapes a fighter has to read
 * (where a ring is, how far a blast reaches, where a meteor will land); this class plays the body of each attack
 * beside them as real particle effects from assets/astral_world/fx/mtod/ (written by tools/fx/gen_fx.py): fire and
 * smoke trailing a meteor, the fire, sparks and dust of an impact, earth thrown up along a rolling ring, lightning
 * and leaves on the ring in the air, pollen haze, the light streaming into the Purge, the curse's violet wisps.
 *
 * Every attack arrives as one message; {@link #onFx} turns it into a small schedule of effects to start, move and
 * stop. Without LOTM (whose Photon bridge plays them) nothing here runs and the fight looks as it did.
 */
final class AttackPhoton {
    private AttackPhoton() {}

    private static final int MAX_LIVE = 70;          // a crowd's worth of attacks must not bury the client
    private static final double DX = 0.32, DZ = 0.18;   // the slant a meteor comes in on (as in AttackFx)
    private static long now = 0;

    /** One thing to play: from `start` to `end` (client ticks). A ring is replayed at its new radius every few ticks. */
    private static final class Job {
        long start, end, nextRing;
        String fx; double x, y, z; float scale;
        double r0, speed; boolean ring;
        DoubleFunction<Vec3> path;                  // for an effect that moves: where it is `ticks` after its start
        Object handle; boolean begun;
    }
    private static final List<Job> JOBS = new ArrayList<>();

    static void init() { NeoForge.EVENT_BUS.addListener(AttackPhoton::tick); }

    private static Job job(int delay, int dur, String fx, double x, double y, double z, double scale) {
        Job j = new Job(); j.start = now + Math.max(0, delay); j.end = j.start + Math.max(2, dur);
        j.fx = "astral_world:mtod/" + fx; j.x = x; j.y = y; j.z = z; j.scale = (float) Math.max(.2, scale);
        if (JOBS.size() < MAX_LIVE) JOBS.add(j);
        return j;
    }
    /** A burst: its emitters fire once and loop every 40 ticks, so it is never left running longer than that. */
    private static void burst(int delay, String fx, double x, double y, double z, double scale) { job(delay, 38, fx, x, y, z, scale); }
    private static void ring(int delay, int dur, String fx, double x, double y, double z, double r0, double speed) {
        Job j = job(delay, dur, fx, x, y, z, Math.max(1, r0)); j.ring = true; j.r0 = r0; j.speed = speed;
    }
    private static void moving(int delay, int dur, String fx, double scale, DoubleFunction<Vec3> path) {
        Vec3 p0 = path.apply(0); Job j = job(delay, dur, fx, p0.x, p0.y, p0.z, scale); j.path = path;
    }

    /** One attack message, as AttackFx receives it (see its list of kinds). `life` is in ticks. */
    static void onFx(int kind, double x, double y, double z, double a, double b, double c, int col, int life) {
        if (!LotmFx.available()) return;
        switch (kind) {
            case 0 -> {   // meteor: a = ticks of fall, b = radius, c = LOTM draws the fall itself
                final double fall = Math.max(1, a);
                if (c <= .5) moving(0, (int) fall, "fireball", Math.max(1.5, b * .5), t -> { double h = 190 * (1 - Math.pow(Math.min(1, t / fall), 1.35)); return new Vec3(x + DX * h, y + h + .5, z + DZ * h); });
                burst((int) fall, "impact", x, y + .2, z, Math.max(3, b));
            }
            case 1 -> burst((int) a, c > .5 ? "impact_flesh" : "impact_bloom", x, y + .2, z, Math.max(3, b));            // the thorn spike: a = ticks until it lands, b = radius
            case 2, 15 -> ring(0, life, "ring_dust", x, y + .3, z, kind == 2 ? a : b, kind == 2 ? b : a);                  // shock wave (r0, speed) and the root ring (speed, r0)
            case 16 -> ring(0, life, "ring_storm", x, y + 3.5, z, b, a);                                                  // the ring in the air (speed, r0)
            case 3 -> moving(0, life, "pollen", Math.max(2, c), t -> new Vec3(x + a * t, y + 1.2, z + b * t));            // pollen drifting: a, b = its velocity, c = radius
            case 4 -> burst(0, "blast", x, y + .5, z, Math.max(3, a * .5));                                               // a = how far it reaches
            case 5 -> job(0, life, "curse", x, y + 1, z, Math.max(1.5, c));
            case 7 -> job(0, life, "curse", x, y, z, Math.max(1.5, a));
            case 6 -> {   // the great meteor: a = height, b = ticks of fall, c = size
                final double fall = Math.max(1, b), sc = c > 0 ? c : 1;
                moving(0, (int) fall, "fireball", 9 * sc, t -> { double f = Math.min(1, t / fall); return new Vec3(x, y + a - a * (.6 * f + .4 * f * f), z); });
                burst((int) fall, "impact", x, y + .3, z, 40 * sc); burst((int) fall + 2, "blast", x, y + 1, z, 60 * sc);
            }
            case 14 -> { job(0, (int) a, "gather", x, y + 42, z, 18); burst((int) a, "blast", x, y + 1, z, Math.max(6, b * .5)); }   // the Purge and the supernova: a = windup, b = reach
            case 19 -> job(0, Math.min(life, 30), "storm_air", x, y, z, Math.max(4, b));                                   // lightning all over: b = radius
            case 13 -> job((int) c, 30, "storm_air", x, y, z, Math.max(4, b));                                             // the meteor storm's lightning: b = reach, c = warning
            case 17 -> job(0, life, a > .5 ? "flower_flesh" : "flower", x, y, z, 1);
            case 20 -> job(0, life, "puddle", x, y + .1, z, Math.max(2, a));
            case 10 -> job(0, life, "fruit", x, y, z, 1);
            default -> { }
        }
    }

    /** A fruit was taken, or the fight was reset: stop what stands near that spot (or everything, for a reset). */
    static void clearNear(double x, double z, double within) {
        for (Iterator<Job> it = JOBS.iterator(); it.hasNext();) { Job j = it.next(); if (within < 0 || Math.hypot(j.x - x, j.z - z) < within && (j.fx.endsWith("fruit") || j.fx.contains("flower"))) { LotmFx.kill(j.handle); it.remove(); } }
    }

    /** Pollen was dispelled near a spot: its haze goes with it, wherever it has drifted to. */
    static void dispel(double x, double z, double within) {
        for (Iterator<Job> it = JOBS.iterator(); it.hasNext();) {
            Job j = it.next();
            if (!j.fx.endsWith("pollen") || j.path == null) continue;
            Vec3 p = j.path.apply(Math.max(0, now - j.start));
            if (Math.hypot(p.x - x, p.z - z) < within) { LotmFx.kill(j.handle); it.remove(); }
        }
    }

    private static void tick(ClientTickEvent.Post e) {
        now++;
        if (JOBS.isEmpty()) return;
        if (net.minecraft.client.Minecraft.getInstance().level == null) { for (Job j : JOBS) LotmFx.kill(j.handle); JOBS.clear(); return; }
        for (Iterator<Job> it = JOBS.iterator(); it.hasNext();) {
            Job j = it.next();
            if (now >= j.end) { LotmFx.kill(j.handle); it.remove(); continue; }
            if (now < j.start) continue;
            if (j.ring) {
                if (now >= j.nextRing) {   // the same ring again, at where the wave has got to
                    LotmFx.kill(j.handle);
                    j.handle = LotmFx.start(j.fx, j.x, j.y, j.z, (float) Math.max(1, j.r0 + j.speed * (now - j.start)));
                    j.nextRing = now + 3;
                }
            } else if (!j.begun) {
                j.begun = true;
                if (j.path != null) {
                    final long t0 = j.start; final DoubleFunction<Vec3> path = j.path;
                    j.handle = LotmFx.carry(j.fx, pt -> path.apply(now - t0 + pt), j.scale);
                } else j.handle = LotmFx.start(j.fx, j.x, j.y, j.z, j.scale);
            }
        }
    }
}
