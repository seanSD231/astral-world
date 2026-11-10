package com.astralspace;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Client only: the confetti popper's confetti and the bubble wand's bubbles. */
public final class PartyParticles {
    private PartyParticles() {}

    public static void register(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(PartyItems.CONFETTI.get(), Confetti.Provider::new);
        e.registerSpriteSet(PartyItems.BUBBLE.get(), Bubble.Provider::new);
        e.registerSpriteSet(PartyItems.STAR.get(), StarDot.Provider::new);
    }

    /** A piece of paper that tumbles and flutters down. The colour is picked per piece. */
    static final class Confetti extends TextureSheetParticle {
        private static final float[][] COLS = {{1f, .31f, .43f}, {1f, .82f, .3f}, {.3f, .75f, 1f}, {.5f, .9f, .45f}, {1f, .5f, .86f}, {1f, 1f, 1f}, {1f, .6f, .2f}};
        private final float phase, spin;

        Confetti(ClientLevel lv, double x, double y, double z, double vx, double vy, double vz, SpriteSet set) {
            super(lv, x, y, z, vx, vy, vz);
            this.xd = vx; this.yd = vy; this.zd = vz;
            pickSprite(set);
            float[] c = COLS[random.nextInt(COLS.length)];
            rCol = c[0]; gCol = c[1]; bCol = c[2];
            lifetime = 200 + random.nextInt(150);   // five times as long
            gravity = 0.03f; friction = 0.9f;
            quadSize = 0.07f + random.nextFloat() * 0.04f;
            phase = random.nextFloat() * 6.28f; spin = (random.nextFloat() - .5f) * 0.5f;
            roll = oRoll = random.nextFloat() * 6.28f;
            hasPhysics = true;
        }

        @Override
        public void tick() {
            oRoll = roll; roll += spin;
            xd += Math.sin(age * 0.35 + phase) * 0.004; zd += Math.cos(age * 0.31 + phase) * 0.004;
            if (age > lifetime - 20) alpha = Math.max(0, (lifetime - age) / 20f);
            super.tick();
            if (onGround) { xd *= 0.5; zd *= 0.5; }
        }

        @Override public float getQuadSize(float pt) { return quadSize * (0.35f + 0.65f * (float) Math.abs(Math.cos((age + pt) * 0.33 + phase))); }
        @Override protected int getLightColor(float pt) { return 0xF000F0; }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

        record Provider(SpriteSet set) implements ParticleProvider<SimpleParticleType> {
            @Override public Particle createParticle(SimpleParticleType t, ClientLevel lv, double x, double y, double z, double vx, double vy, double vz) { return new Confetti(lv, x, y, z, vx, vy, vz, set); }
        }
    }

    /** A soap bubble that wobbles, drifts up, then pops. */
    static final class Bubble extends TextureSheetParticle {
        private final SpriteSet set;
        private final float phase;
        private final float size0;

        Bubble(ClientLevel lv, double x, double y, double z, double vx, double vy, double vz, SpriteSet set) {
            super(lv, x, y, z, vx, vy, vz);
            this.set = set;
            this.xd = vx; this.yd = vy; this.zd = vz;
            lifetime = 34 + random.nextInt(30);
            gravity = 0; friction = 0.955f; hasPhysics = false;
            size0 = 0.09f + random.nextFloat() * 0.11f; quadSize = size0;
            phase = random.nextFloat() * 6.28f;
            alpha = 0.95f;
            setSprite(set.get(0, 4));
        }

        @Override
        public void tick() {
            int popAt = lifetime - 5;
            if (age < popAt) {
                xd += Math.sin(age * 0.25 + phase) * 0.0035; zd += Math.cos(age * 0.22 + phase) * 0.0035; yd += 0.0016;
                setSprite(set.get((age / 5) % 3, 4));
            } else {
                xd = yd = zd = 0;
                setSprite(set.get(age >= lifetime - 2 ? 4 : 3, 4));
                quadSize = size0 * 1.5f;
            }
            super.tick();
        }

        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

        record Provider(SpriteSet set) implements ParticleProvider<SimpleParticleType> {
            @Override public Particle createParticle(SimpleParticleType t, ClientLevel lv, double x, double y, double z, double vx, double vy, double vz) { return new Bubble(lv, x, y, z, vx, vy, vz, set); }
        }
    }

    /** A tiny glowing photon dot: the Door rapier's stars. Short lived, fades out, a little shimmer. */
    static final class StarDot extends TextureSheetParticle {
        private static final float[][] PALETTE = {{1f, .82f, .3f}, {1f, .35f, .8f}, {.3f, .92f, 1f}, {.68f, .45f, 1f}, {.4f, 1f, .62f}, {1f, .52f, .4f}, {1f, 1f, 1f}};
        private final float base, phase;
        StarDot(ClientLevel lv, double x, double y, double z, double vx, double vy, double vz, SpriteSet set) {
            super(lv, x, y, z, vx, vy, vz);
            this.xd = vx + (random.nextFloat() - .5f) * .01; this.yd = vy + (random.nextFloat() - .5f) * .01; this.zd = vz + (random.nextFloat() - .5f) * .01;
            pickSprite(set);
            float[] c = PALETTE[random.nextInt(PALETTE.length)];
            rCol = c[0]; gCol = c[1]; bCol = c[2];
            boolean fast = vx * vx + vy * vy + vz * vz > .01;
            lifetime = fast ? 16 + random.nextInt(8) : 9 + random.nextInt(8); gravity = 0f; friction = .9f; hasPhysics = false;
            base = 0.05f + random.nextFloat() * 0.05f; quadSize = base; phase = random.nextFloat() * 6.28f;
        }
        @Override public void tick() { super.tick(); alpha = Math.max(0f, 1f - (float) age / lifetime); }
        @Override public float getQuadSize(float pt) { return base * (0.7f + 0.3f * (float) Math.sin((age + pt) * 1.4 + phase)); }
        @Override protected int getLightColor(float pt) { return 0xF000F0; }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
        record Provider(SpriteSet set) implements ParticleProvider<SimpleParticleType> {
            @Override public Particle createParticle(SimpleParticleType t, ClientLevel lv, double x, double y, double z, double vx, double vy, double vz) { return new StarDot(lv, x, y, z, vx, vy, vz, set); }
        }
    }
}
