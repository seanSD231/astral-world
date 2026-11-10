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
        e.registerSpriteSet(PartyItems.STAR.get(), set -> new StarDot.Provider(set, StarDot.PALETTE));
        e.registerSpriteSet(PartyItems.STAR_BLUE.get(), set -> new StarDot.Provider(set, StarDot.BLUE));
        e.registerSpriteSet(PartyItems.SLIT.get(), Slit.Provider::new);
        e.registerSpriteSet(PartyItems.FLASH.get(), set -> new Blast.Provider(set, 0));
        e.registerSpriteSet(PartyItems.RING.get(), set -> new Blast.Provider(set, 1));
        e.registerSpriteSet(PartyItems.STAR_CORE.get(), set -> new Blast.Provider(set, 2));
        e.registerSpriteSet(PartyItems.NEBULA.get(), set -> new Blast.Provider(set, 3));
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
        static final float[][] BLUE = {{.35f, .6f, 1f}, {.5f, .78f, 1f}, {.25f, .45f, 1f}, {.7f, .9f, 1f}};
        static final float[][] PALETTE = {{1f, .82f, .3f}, {1f, .35f, .8f}, {.3f, .92f, 1f}, {.68f, .45f, 1f}, {.4f, 1f, .62f}, {1f, .52f, .4f}, {1f, 1f, 1f}};
        private final float base, phase;
        StarDot(ClientLevel lv, double x, double y, double z, double vx, double vy, double vz, SpriteSet set, float[][] pal) {
            super(lv, x, y, z, vx, vy, vz);
            this.xd = vx + (random.nextFloat() - .5f) * .01; this.yd = vy + (random.nextFloat() - .5f) * .01; this.zd = vz + (random.nextFloat() - .5f) * .01;
            pickSprite(set);
            float[] c = pal[random.nextInt(pal.length)];
            rCol = c[0]; gCol = c[1]; bCol = c[2];
            boolean fast = vx * vx + vy * vy + vz * vz > .01;
            lifetime = fast ? 40 + random.nextInt(25) : 32 + random.nextInt(20); gravity = 0f; friction = .9f; hasPhysics = false;
            base = 0.05f + random.nextFloat() * 0.05f; quadSize = base; phase = random.nextFloat() * 6.28f;
        }
        @Override public void tick() { super.tick(); alpha = Math.min(1f, Math.max(0f, 2.2f * (1f - (float) age / lifetime))); }
        @Override public float getQuadSize(float pt) { return base * (0.7f + 0.3f * (float) Math.sin((age + pt) * 1.4 + phase)); }
        @Override protected int getLightColor(float pt) { return 0xF000F0; }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
        record Provider(SpriteSet set, float[][] pal) implements ParticleProvider<SimpleParticleType> {
            @Override public Particle createParticle(SimpleParticleType t, ClientLevel lv, double x, double y, double z, double vx, double vy, double vz) { return new StarDot(lv, x, y, z, vx, vy, vz, set, pal); }
        }
    }

    /** A dot of the black seam of a door: solid dark, lingers, fades late. */
    static final class Slit extends TextureSheetParticle {
        Slit(ClientLevel lv, double x, double y, double z, SpriteSet set) {
            super(lv, x, y, z, 0, 0, 0);
            pickSprite(set); rCol = .02f; gCol = .02f; bCol = .05f; lifetime = 26 + random.nextInt(8); quadSize = 0.1f; hasPhysics = false; gravity = 0f;
        }
        @Override public void tick() { super.tick(); alpha = Math.min(1f, Math.max(0f, 2f * (1f - (float) age / lifetime))); }
        @Override protected int getLightColor(float pt) { return 0xF000F0; }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
        record Provider(SpriteSet set) implements ParticleProvider<SimpleParticleType> {
            @Override public Particle createParticle(SimpleParticleType t, ClientLevel lv, double x, double y, double z, double vx, double vy, double vz) { return new Slit(lv, x, y, z, set); }
        }
    }

    /**
     * Big sky effects. kind 0 flash, 1 shockwave ring, 2 star (vz &gt; 0 swells, flickers and collapses over vz ticks; vz &lt; 0 goes nova
     * for -vz ticks), 3 drifting nebula gas (velocity = vx,vy,vz). For 0-2: vx = size, vy = colour index (negative = random), vz = life.
     */
    static final class Blast extends TextureSheetParticle {
        static final float[][] STARS = {{.7f, .85f, 1f}, {1f, .95f, .7f}, {1f, .75f, .4f}, {1f, .45f, .35f}, {1f, 1f, 1f}};
        static final float[][] GAS = {{1f, .3f, .7f}, {.25f, .85f, .95f}, {1f, .55f, .25f}, {.6f, .35f, 1f}, {.4f, .5f, 1f}, {1f, .4f, .45f}};
        private final float size, br, bg, bb; private final int kind; private final boolean swell;
        Blast(ClientLevel lv, double x, double y, double z, double vx, double vy, double vz, SpriteSet set, int kind) {
            super(lv, x, y, z, 0, 0, 0);
            pickSprite(set); this.kind = kind; hasPhysics = false; gravity = 0f; quadSize = 0.1f;
            float[] c;
            if (kind == 3) {
                c = GAS[random.nextInt(GAS.length)]; size = 6f + random.nextFloat() * 8f; swell = false;
                xd = vx; yd = vy; zd = vz; friction = .96f; lifetime = 70 + random.nextInt(70);
            } else {
                size = (float) vx; swell = kind == 2 && vz > 0;
                c = vy < 0 ? (kind == 2 ? STARS[random.nextInt(STARS.length)] : StarDot.PALETTE[random.nextInt(StarDot.PALETTE.length)])
                           : (kind == 2 ? STARS[(int) vy % STARS.length] : StarDot.PALETTE[(int) vy % StarDot.PALETTE.length]);
                lifetime = vz != 0 ? (int) Math.abs(vz) : kind == 1 ? 18 : 11;
            }
            br = c[0]; bg = c[1]; bb = c[2]; rCol = br; gCol = bg; bCol = bb;
            if (kind == 2) roll = random.nextFloat() * 6.28f;
        }
        @Override public void tick() {
            super.tick();
            float p = (float) age / lifetime;
            oRoll = roll;
            if (kind == 2) roll += swell ? .05f : .12f;
            if (kind == 3) alpha = Math.min(.42f, 1.2f * Math.min(p * 5f, 1f) * (1f - p));
            else if (swell) { alpha = 1f; float w = Math.min(1f, p * 1.3f); rCol = br + (1f - br) * w; gCol = bg + (1f - bg) * w; bCol = bb + (1f - bb) * w; }
            else alpha = Math.max(0f, (float) Math.pow(1f - p, kind == 1 ? 1.2 : 1.8));
        }
        @Override public float getQuadSize(float pt) {
            float p = Math.min(1f, (age + pt) / lifetime);
            if (kind == 3) return size * (.6f + .6f * p);
            if (swell) {   // swells and flickers, then collapses just before it goes
                if (p < .75f) return size * .5f * (.35f + .65f * p / .75f) * (1f + .07f * (float) Math.sin((age + pt) * 1.9f));
                float q = (p - .75f) / .25f; return size * .5f * (1f - .85f * q * q);
            }
            return size * (1f - (float) Math.pow(1f - p, kind == 1 ? 2.0 : 3.0)) * .5f + .05f;
        }
        @Override protected int getLightColor(float pt) { return 0xF000F0; }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
        record Provider(SpriteSet set, int kind) implements ParticleProvider<SimpleParticleType> {
            @Override public Particle createParticle(SimpleParticleType t, ClientLevel lv, double x, double y, double z, double vx, double vy, double vz) { return new Blast(lv, x, y, z, vx, vy, vz, set, kind); }
        }
    }
}
