package com.astralspace;

import static com.astralspace.Mesh.*;

import java.util.ArrayList;
import java.util.List;

/**
 * The seven planet sovereigns, each shaped like the mythical creature form of a boon pathway (see CHANGELOG.md):
 * mercury = Sublunary Eye, venus = Mind Dragon, mars = Second Law, jupiter = Condenser, saturn = Tail-Devourer,
 * uranus = Eternal Edict, neptune = Eternal Aeon. Units are blocks before the renderer scales them down.
 */
final class SovModels {
    private SovModels() {}

    static Mesh build(String planet) {
        Mesh m = new Mesh(planet.hashCode());
        switch (planet) {
            case "mercury" -> mercury(m);
            case "venus" -> dragon(m);
            case "mars" -> decay(m);
            case "jupiter" -> condenser(m);
            case "saturn" -> hunger(m);
            case "uranus" -> edict(m);
            case "neptune" -> aeon(m);
            default -> m.ball(v(0, 3, 0), 3, c(120, 120, 120), false);
        }
        return m;
    }

    static int[] c(int r, int g, int b) { return new int[]{r, g, b}; }

    // =============================================================== Mercury: Sublunary Eye
    private static void mercury(Mesh m) {
        for (int k = 0; k < 260; k++) {
            double x = m.rnd.nextGaussian() * 1.25;
            double[] base = v(x, 0, m.rnd.nextGaussian() * .8);
            double h = m.rr(13, 22) * Math.exp(-Math.abs(x) / 2.3) + 2.5;
            double[][] pts = new double[12][];
            for (int i = 0; i < 12; i++) {
                double t = i / 11.0;
                pts[i] = add(base, v(Math.sin(t * 4 + k) * .55 * t + x * .15 * t, t * h, Math.cos(t * 3 + k * .7) * .3 * t));
            }
            double w = m.rr(.4, .95);
            double[] rad = new double[12];
            for (int i = 0; i < 12; i++) rad[i] = w * (1 - .85 * Math.pow(i / 11.0, 1.3)) + .04;
            int cc = 4 + m.rnd.nextInt(13);
            m.tube(pts, rad, c(cc, cc - 1, cc + 10), 6, false, false, .9);
        }
        int[] str = c(170, 160, 240);
        for (int k = 0; k < 22; k++) {
            double a = (m.rnd.nextBoolean() ? 1 : -1) * m.rr(.4, 1);
            double[] p0 = v(m.rr(-.8, .8), m.rr(11, 16), 0);
            m.tube(spline(p0, add(p0, v(a * 3, 3, m.rr(-1, 1))), add(p0, v(a * 7, 3, m.rr(-2, 2))), add(p0, v(a * 10, -4, m.rr(-2, 2)))), .06, str, 4, true, false, 0);
        }
        for (int sx = -1; sx <= 1; sx += 2) {      // the eyes: small red lights in holes in the dark
            double[] ec = v(sx * .8, 12.8, 2.7);
            m.ball(add(ec, v(0, 0, -.15)), v(.62, .3, .05), c(2, 1, 4), false, null);
            m.ball(add(ec, v(0, 0, .02)), v(.13, .1, .05), c(255, 25, 25), true, null);
            for (int q = 0; q < 3; q++) {
                double[] e1 = add(ec, v(sx * (.62 + .05 * q), 0, 0));
                m.tube(spline(e1, add(e1, v(sx * m.rr(.6, 1.6), m.rr(.4, 2.2), m.rr(-.4, .2))), add(e1, v(sx * m.rr(1.5, 3.5), m.rr(1.5, 4), m.rr(-.8, .4)))), .05, str, 4, true, false, 0);
            }
        }
        double[][] worlds = {{-10, 13, -4, 2.0, 255, 190, 70}, {9, 14, -3, 1.3, 120, 170, 240}, {-8, 5, -3, 1.0, 230, 110, 90}, {10, 6, -2, 1.6, 130, 210, 150}};
        int wi = 0;
        for (double[] w : worlds) {
            m.part("world" + (wi++), v(0, 9, -3));
            double[] wc = v(w[0], w[1], w[2]);
            m.ball(wc, w[3], c((int) w[4], (int) w[5], (int) w[6]), true);
            m.ring(wc, w[3] * 1.7, .05, c(240, 235, 255), .5, .3, true, 40, 4);
            for (int k = 0; k < 8; k++) {
                double[] p0 = v(m.rr(-1, 1), m.rr(9, 15), m.rr(-.2, .5));
                m.tube(spline(p0, v((p0[0] + wc[0]) / 2 + m.rr(-2, 2), (p0[1] + wc[1]) / 2 + m.rr(-2, 2), m.rr(-.5, 1.5)), wc), .04, c(200, 190, 255), 4, true, false, 0);
            }
        }
    }

    // =============================================================== Venus: Mind Dragon
    private static void dragonHead(Mesh m, double[] h, double[] d, double sc, int[] gray, int[] dark) {
        double[][] R = orient(d, v(0, 1, 0));
        java.util.function.Function<double[], double[]> L = p -> add(h, app(R, mul(p, sc)));
        m.blob(L.apply(v(0, 0, -.3)), v(.8 * sc, .78 * sc, .9 * sc), gray, .04, 8, 12, false, R, 6, .8);
        m.blob(L.apply(v(0, .15, 1.0)), v(1.15 * sc, .85 * sc, 1.8 * sc), gray, .05, 10, 14, false, R, 7, .8);
        m.blob(L.apply(v(0, -.1, 3.0)), v(.7 * sc, .5 * sc, 1.5 * sc), gray, .04, 8, 12, false, R, 8, .8);
        m.blob(L.apply(v(0, .12, 4.3)), v(.6 * sc, .34 * sc, .5 * sc), gray, .04, 6, 10, false, R, 12, .8);
        m.blob(L.apply(v(0, -.72, 2.0)), v(.8 * sc, .26 * sc, 2.0 * sc), dark, .04, 8, 12, false, R, 9, .8);
        for (int sx = -1; sx <= 1; sx += 2) {
            m.blob(L.apply(v(sx * .95, -.2, .3)), v(.34 * sc, .55 * sc, .7 * sc), gray, .06, 6, 10, false, R, 13, .8);
            m.blob(L.apply(v(sx * .62, .55, 1.0)), v(.34 * sc, .2 * sc, .7 * sc), dark, .05, 6, 10, false, R, 10, .8);
            m.ball(L.apply(v(sx * .95, .46, 1.35)), v(.1 * sc, .08 * sc, .22 * sc), c(214, 160, 40), true, R);
            m.ball(L.apply(v(sx * 1.02, .46, 1.38)), v(.02 * sc, .08 * sc, .05 * sc), c(10, 6, 0), false, R);
            m.limb(new double[][]{L.apply(v(sx * .8, .62, 1.0)), L.apply(v(sx * .95, .62, 1.4)), L.apply(v(sx * .8, .55, 1.8))}, new double[]{.12 * sc, .1 * sc, .06 * sc}, dark, 6, .8, 0);
            m.ball(L.apply(v(sx * .22, .2, 4.6)), v(.12 * sc, .07 * sc, .12 * sc), c(10, 10, 12), false, R);
            m.limb(new double[][]{L.apply(v(sx * .5, .7, .1)), L.apply(v(sx * 1.2, 1.3, -.9)), L.apply(v(sx * 1.7, 1.3, -2.2)), L.apply(v(sx * 2.0, .7, -3.4))}, new double[]{.36 * sc, .28 * sc, .16 * sc, .02}, c(70, 62, 58), 8, .9, 0);
            for (int q = 0; q < 5; q++) m.cone(L.apply(v(sx * (.6 + q * .3), .8 + q * .18, -.4 - q * .6)), L.apply(v(sx * (.6 + q * .3), 1.25 + q * .18, -.7 - q * .6)), .07 * sc, c(70, 62, 58), 5, false);
            m.limb(new double[][]{L.apply(v(sx * .62, .15, -.6)), L.apply(v(sx * 1.5, .05, -1.6)), L.apply(v(sx * 2.0, -.2, -2.6))}, new double[]{.14 * sc, .1 * sc, .02}, c(66, 60, 58), 6, .8, 0);
            for (int k = 0; k < 9; k++) {
                double q = 1.2 + k * .32;
                m.cone(L.apply(v(sx * .5, -.46, q)), L.apply(v(sx * .5, -.9, q)), .055 * sc, c(238, 232, 216), 5, false);
                if (k < 5) m.cone(L.apply(v(sx * .38, .2, q + 1.0)), L.apply(v(sx * .38, -.2, q + 1.0)), .05 * sc, c(238, 232, 216), 5, false);
            }
            for (int q = 0; q < 4; q++) m.cone(L.apply(v(sx * 1.0, -.1 + q * .1, .5 + q * .5)), L.apply(v(sx * 1.35, -.3 + q * .1, .2 + q * .5)), .07 * sc, dark, 5, false);
        }
        for (int k = 0; k < 8; k++) m.cone(L.apply(v(0, .8, -.2 - k * .5)), L.apply(v(0, 1.7 + .1 * k, -.9 - k * .6)), .15 * sc, dark, 6, false);
    }

    private static void wing(Mesh m, int sz, double[] sh, int[] mem, int[] bone) {
        m.part("wing" + sz, sh);
        double k = .72;
        double[] El = add(sh, mul(v(2.4, 4.2, sz * 4.5 - sh[2]), k)), Wr = add(sh, mul(v(-.8, 7.8, sz * 8.2 - sh[2]), k));
        m.limb(new double[][]{sh, El, Wr}, new double[]{.8, .62, .45}, bone, 10, .9, 0);
        double[][] raw = {{Wr[0] - 9.5, Wr[1] + 4.8, sz * 13.5}, {Wr[0] - 5.0, Wr[1] + 7.0, sz * 17.5}, {Wr[0] + 2.5, Wr[1] + 5.5, sz * 18.0}, {Wr[0] + 8.5, Wr[1] - .5, sz * 14.5}};
        double[][] tips = new double[4][];
        for (int i = 0; i < 4; i++) tips[i] = add(Wr, mul(sub(raw[i], Wr), k));
        for (double[] t : tips) {
            m.limb(new double[][]{Wr, add(lerp(Wr, t, .5), v(0, .5, 0)), t}, new double[]{.36, .24, .06}, bone, 8, .8, 0);
            m.cone(t, add(t, mul(norm(sub(t, Wr)), .9)), .05, bone, 5, false);
        }
        double[] B1 = v(sh[0] + 4.8, sh[1] - 1.4, sz * 1.3), B2 = v(sh[0] + 1.2, sh[1] - .6, sz * 1.0);
        for (int i = 0; i < 3; i++) m.tri(Wr, tips[i], tips[i + 1], mem, 6, .5, i + sz);
        m.tri(Wr, tips[3], B1, mem, 6, .4, 7); m.tri(Wr, B1, B2, mem, 5, .3, 3); m.tri(Wr, B2, sh, mem, 4, .2, 5);
        m.cone(Wr, add(Wr, v(-.3, .9, sz * .5)), .18, bone, 5, false);
        for (int i = 0; i < 3; i++) { double[] mid = lerp(tips[i], tips[i + 1], .5); m.limb(new double[][]{Wr, add(lerp(Wr, mid, .5), v(0, .2, 0)), add(mul(mid, .9), mul(Wr, .1))}, new double[]{.14, .1, .04}, bone, 6, .6, 0); }
    }

    private static void dragon(Mesh m) {
        int[] gray = c(92, 92, 98), dark = c(58, 56, 62), mem = c(60, 50, 62), bone = c(80, 76, 86), belly = c(138, 126, 104), claw = c(225, 218, 200);
        double[][] sp = spline(java.util.Arrays.asList(v(22, 1.6, 6.5), v(18.5, .8, 5.6), v(15, .9, 3.2), v(11.5, 1.6, 1.4), v(8, 3.2, 1.2), v(4.6, 5.1, .5), v(1, 5.8, 0), v(-2.2, 6.0, 0),
                v(-4.8, 7.0, 0), v(-6.6, 9.2, -.4), v(-7.8, 11.4, 0), v(-9.4, 12.8, .5), v(-11.2, 13.2, .7)), 9);
        int n = sp.length;
        double[][] ctrl = {{0, .14}, {.1, .4}, {.2, .85}, {.3, 1.5}, {.38, 2.0}, {.48, 2.35}, {.6, 1.6}, {.72, 1.15}, {.86, .9}, {1, .82}};
        double[] rad = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / (n - 1.0), r = ctrl[ctrl.length - 1][1];
            for (int q = 0; q < ctrl.length - 1; q++) if (t <= ctrl[q + 1][0]) { r = ctrl[q][1] + (ctrl[q + 1][1] - ctrl[q][1]) * (t - ctrl[q][0]) / (ctrl[q + 1][0] - ctrl[q][0]); break; }
            rad[i] = r * 1.12;
        }
        m.limb(sp, rad, gray, 22, .9, .03);
        for (int i = 2; i < n - 4; i++) {
            double[] t = norm(sub(sp[i + 1], sp[i - 1]));
            double[] a = Math.abs(t[1]) < .9 ? v(0, 1, 0) : v(1, 0, 0);
            double[] u = norm(cross(t, a)), w = norm(cross(t, u));
            double rr = rad[i];
            if (rr < .3) continue;
            for (int k = 0; k < 22; k++) {
                double an = 2 * Math.PI * k / 22 + (i % 2) * .15;
                double[] d = add(mul(u, Math.cos(an)), mul(w, Math.sin(an)));
                boolean bel = d[1] < -.55;
                m.plate(add(add(sp[i], mul(d, rr * 1.0)), mul(t, .2)), norm(cross(d, t)), t, d, rr * (bel ? .26 : .2), rr * (bel ? .4 : .3) + .05, bel ? belly : ((i * 7 + k * 3) % 11 == 0 ? dark : gray));
            }
            if (i % 2 == 0) {
                double[] up = norm(sub(v(0, 1, 0), mul(t, t[1])));
                m.cone(add(sp[i], mul(up, rr * .95)), add(sp[i], mul(up, rr + .8 + .3 * Math.sin(i * .4))), .1 * rr + .06, dark, 6, false);
            }
        }
        double[] tp = sp[0], td = norm(sub(sp[0], sp[4]));
        m.blob(add(tp, mul(td, .9)), v(.35, .12, 1.3), dark, .04, 6, 10, false, orient(td, v(0, 1, 0)), 0, .7);
        m.cone(add(tp, mul(td, 1.9)), add(tp, mul(td, 3.3)), .2, dark, 6, false);
        int ih = (int) (n * .38), ic = (int) (n * .5);
        for (int sz = -1; sz <= 1; sz += 2) {
            double[] hp = add(sp[ih], v(.4, -.7, sz * 1.9)), kn = add(hp, v(-2.4, -1.5, sz * 1.0)), hk = add(kn, v(3.2, -1.4, -sz * .2)), ft = v(hk[0] - .8, .35, hk[2] + sz * .1);
            m.blob(add(hp, v(.2, -.2, 0)), v(1.5, 1.9, 1.15), gray, .08, 10, 14, false, null, 3 + sz, .9);
            m.limb(new double[][]{hp, kn, hk, ft}, new double[]{.95, .7, .46, .36}, gray, 14, .9, .04);
            m.blob(kn, v(.65, .65, .65), gray, .08, 6, 10, false, null, 17, .9);
            m.blob(add(ft, v(-.6, 0, 0)), v(1.0, .34, .8), gray, .06, 6, 10, false, null, 5, .9);
            for (int q = 0; q < 4; q++) {
                double z0 = (q - 1.5) * .5;
                m.limb(new double[][]{add(ft, v(-1.0, 0, z0 * .5)), add(ft, v(-1.9, 0, z0 * .8)), add(ft, v(-2.5, -.1, z0))}, new double[]{.2, .17, .12}, gray, 7, .7, 0);
                m.cone(add(ft, v(-2.5, -.1, z0)), add(ft, v(-3.0, -.25, z0 * 1.15)), .11, claw, 5, false);
            }
        }
        for (int sz = -1; sz <= 1; sz += 2) {
            double[] sh = add(sp[ic], v(0, -.9, sz * 2.1)), el = add(sh, v(2.0, -2.3, sz * .9)), wr = v(el[0] - 2.3, 1.5, el[2] + sz * .3), pw = v(wr[0] - .7, .35, wr[2]);
            m.blob(sh, v(1.9, 2.25, 1.35), gray, .08, 10, 14, false, null, 9 + sz, .9);
            m.limb(new double[][]{sh, el, wr, pw}, new double[]{1.2, .84, .6, .5}, gray, 14, .9, .04);
            m.blob(el, v(.82, .82, .82), gray, .08, 6, 10, false, null, 19, .9);
            m.blob(add(pw, v(-.7, 0, 0)), v(1.2, .38, .98), gray, .06, 6, 10, false, null, 11, .9);
            for (int q = 0; q < 4; q++) {
                double z0 = (q - 1.5) * .45;
                m.limb(new double[][]{add(pw, v(-1.2, 0, z0 * .6)), add(pw, v(-2.0, 0, z0 * .9)), add(pw, v(-2.7, -.05, z0 * 1.1))}, new double[]{.24, .2, .14}, gray, 7, .7, 0);
                m.cone(add(pw, v(-2.7, -.05, z0 * 1.1)), add(pw, v(-3.2, -.2, z0 * 1.25)), .14, claw, 5, false);
            }
        }
        for (int sz = -1; sz <= 1; sz += 2) wing(m, sz, add(sp[ic - 3], v(0, .9, sz * .6)), mem, bone);
        m.part("head", sp[n - 1]);
        dragonHead(m, sp[n - 1], norm(v(-1.0, -.2, .25)), 1.35, gray, dark);
    }

    // =============================================================== Mars: Second Law
    private static void decay(Mesh m) {
        int[] bl = c(52, 66, 92), pus = c(190, 200, 70);
        m.blob(v(0, 6.3, 0), v(4.4, 4.6, 3.5), bl, .28, 16, 24, false, null, 11, 1.2);
        m.blob(v(0, 2.2, 0), v(3.2, 2.6, 3.0), c(46, 58, 82), .3, 12, 20, false, null, 12, 1.2);
        m.blob(v(.4, 10.6, .6), v(1.5, 1.4, 1.4), c(60, 72, 96), .3, 10, 14, false, null, 13, 1.1);
        for (int sx = -1; sx <= 1; sx += 2) {
            m.part("body", v(0, 0, 0));
            m.ball(v(sx * .55, 10.7, 1.8), v(.28, .2, .12), c(10, 14, 12), false, null);
            m.ball(v(sx * .55, 10.7, 1.9), .07, c(190, 230, 80), true);
            m.part("arm" + sx, v(sx * 3.6, 8.4, 0));
            m.limb(new double[][]{v(sx * 3.6, 8.4, 0), v(sx * 5.4, 6.2, 1.0), v(sx * 5.8, 3.6, 2.2), v(sx * 5.2, 1.2, 2.8)}, new double[]{1.35, 1.15, 1.0, .8}, bl, 14, 1.2, .18);
            m.blob(v(sx * 5.2, .9, 3.0), v(1.2, .8, 1.1), c(48, 60, 84), .3, 8, 12, false, null, 19, 1.2);
        }
        m.part("body", v(0, 0, 0));
        int[][] skins = {{90, 110, 60}, {120, 140, 60}, {40, 46, 70}, {150, 170, 70}};
        for (int k = 0; k < 120; k++) {
            double a = m.rnd.nextDouble() * 2 * Math.PI, t = m.rr(-.9, .95), r = Math.sqrt(Math.max(0, 1 - t * t));
            double[] d = v(r * Math.cos(a), t, r * Math.sin(a));
            m.blob(add(v(0, 6.3, 0), v(d[0] * 4.6, d[1] * 4.8, d[2] * 3.7)), new double[]{m.rr(.18, .62), m.rr(.18, .62), m.rr(.18, .62)}, skins[m.rnd.nextInt(4)], 0, 4, 7, false, null, 0, 1.0);
        }
        for (int k = 0; k < 34; k++) {
            double a = m.rnd.nextDouble() * 2 * Math.PI, t = m.rr(-.6, .8), r = Math.sqrt(1 - t * t);
            double[] d = v(r * Math.cos(a), t, r * Math.sin(a));
            if (d[2] < -.2) continue;
            double[] cc = add(v(0, 6.3, 0), v(d[0] * 4.5, d[1] * 4.7, d[2] * 3.6));
            double sr = m.rr(.35, .8);
            m.blob(cc, v(sr, sr, sr), c(170, 190, 60), .3, 6, 10, true, null, k, .6);
            int drips = 1 + m.rnd.nextInt(3);
            for (int q = 0; q < drips; q++) {
                double ln = m.rr(1, 3.5);
                m.tube(new double[][]{cc, add(cc, v(0, -ln * .5, .1)), add(cc, v(0, -ln, .15))}, new double[]{.1, .09, .06}, pus, 5, true, true, 0);
                m.ball(add(cc, v(0, -ln - .15, .15)), v(.1, .16, .1), pus, true, null);
            }
        }
    }

    // =============================================================== Jupiter: Condenser
    private static void condenser(Mesh m) {
        double[] core = v(0, 9.0, 0);
        for (int i = 0; i < 1500; i++) {
            double[] d = norm(v(m.rnd.nextGaussian(), m.rnd.nextGaussian(), m.rnd.nextGaussian()));
            double lobes = noise(mul(d, 3), 1.0, 3) + .5 * noise(mul(d, 7), 1.0, 9);
            double R = 6.2 * (1 + .38 * lobes) * Math.pow(m.rr(.45, 1.0), .55);
            double[] cc = add(core, v(d[0] * R, d[1] * R, d[2] * R * .8));
            double heat = Math.pow(Math.max(0, 1 - R / 8.5), 1.25);
            int[] col = c((int) (60 + 195 * heat), (int) (22 + 205 * Math.pow(heat, 1.5)), (int) (16 + 160 * Math.pow(heat, 2.6)));
            double r = m.rr(.35, .85) * (1.1 - heat * .4);
            m.blob(cc, v(r, r, r), col, .1, 4, 6, heat > .45, null, 0, .8);
        }
        for (int k = 0; k < 7; k++) {
            double a = k * 2 * Math.PI / 7 + .3;
            double[][] pts = spline(java.util.Arrays.asList(core, add(core, v(Math.cos(a) * 4, m.rr(-2, 3), Math.sin(a) * 3)), add(core, v(Math.cos(a) * 8.5, m.rr(-4, 5), Math.sin(a) * 5)), add(core, v(Math.cos(a) * 12, m.rr(-5, 7), Math.sin(a) * 7))), 6);
            for (int i = 0; i < pts.length; i++) {
                double f = i / (double) pts.length;
                for (int q = 0; q < 2; q++) {
                    double r = m.rr(.25, .6) * (1 - f * .6);
                    m.blob(add(pts[i], v(m.rnd.nextGaussian() * .4, m.rnd.nextGaussian() * .4, m.rnd.nextGaussian() * .4)), v(r, r, r), c(255, (int) (120 - 60 * f), 40), 0, 3, 5, true, null, 0, 0);
                }
            }
        }
        m.part("core", core);
        m.ball(core, 2.3, c(255, 252, 240), true);
        m.part("rings", core);
        for (int k = 0; k < 3; k++) m.ring(core, 8.5 + k * 1.6, .07, c(255, 200, 120), .15 + k * .25, k * .9, true, 60, 4);
        m.part("sparks", core);
        for (int k = 0; k < 60; k++) {
            double a = m.rnd.nextDouble() * 6.28, r = m.rr(9, 17);
            m.ball(v(r * Math.cos(a), m.rr(1, 18), r * Math.sin(a) * .6), m.rr(.08, .3), c(255, 170, 80), true);
        }
    }

    // =============================================================== Saturn: Tail-Devourer
    private static void hunger(Mesh m) {
        double cx = 0, cy = 7.2;
        int layers = 22;
        double[][] prev = null;
        for (int L = 0; L < layers; L++) {
            double t = L / (layers - 1.0), R = 7.2 * Math.pow(1 - t, .8) + .4, z = -t * 14;
            double[][] ring = new double[36][];
            for (int k = 0; k < 36; k++) {
                double a = 2 * Math.PI * k / 36 + t * 5.5;
                double wob = 1 + .12 * noise(v(Math.cos(a) * 3, t * 6, Math.sin(a) * 3), 1.1, 0);
                ring[k] = v(cx + R * wob * Math.cos(a), cy + R * wob * Math.sin(a), z);
            }
            if (prev != null)
                for (int k = 0; k < 36; k++) {
                    int k2 = (k + 1) % 36;
                    double[] nn = norm(v(-(prev[k][0] - cx), -(prev[k][1] - cy), 0));
                    m.quad(prev[k], nn, ring[k], nn, ring[k2], nn, prev[k2], nn, (L + k) % 7 != 0 ? c(40, 18, 36) : c(74, 26, 44), false, 1.0);
                }
            prev = ring;
        }
        for (int row = 0; row < 5; row++)
            for (int k = 0; k < 30; k++) {
                double t = row * .15 + .02, a = 2 * Math.PI * k / 30 + row * .3 + t * 5.5, R = 7.2 * Math.pow(1 - t, .8) + .4;
                double[] p = v(cx + R * Math.cos(a), cy + R * Math.sin(a), -t * 14);
                double[] inn = norm(v(cx - p[0], cy - p[1], 0));
                double ln = 1.7 - row * .22;
                m.cone(p, add(add(p, mul(inn, ln)), v(0, 0, 1.1)), .32 - .04 * row, c(238, 232, 216), 7, false);
            }
        m.part("liquid", v(0, 7.2, 0));
        int[][] liquids = {{255, 90, 170}, {90, 230, 255}, {255, 220, 70}, {130, 255, 110}, {180, 110, 255}, {255, 140, 60}};
        for (int k = 0; k < 60; k++) {
            double t = m.rr(.15, .85), a = m.rnd.nextDouble() * 6.28, R = (7.2 * Math.pow(1 - t, .8) + .4) * m.rr(.2, .85);
            double r = m.rr(.25, .7);
            m.blob(v(cx + R * Math.cos(a), cy + R * Math.sin(a), -t * 14), v(r, r, r), liquids[m.rnd.nextInt(6)], 0, 5, 8, true, null, 0, 0);
        }
        for (int s = 0; s < 5; s++) {
            double a0 = m.rnd.nextDouble() * 6.28;
            double[][] pts = new double[40][]; double[] rad = new double[40];
            for (int i = 0; i < 40; i++) {
                double t = i / 39.0 * .9, a = a0 + t * 9, R = (7.2 * Math.pow(1 - t, .8) + .4) * .75;
                pts[i] = v(cx + R * Math.cos(a), cy + R * Math.sin(a), -t * 14 + .3); rad[i] = .14 + .1 * Math.sin(i * .4);
            }
            m.tube(pts, rad, liquids[s], 6, true, false, 0);
        }
        m.part("lip", v(0, 7.2, 0));
        double[][] lip = new double[61][]; double[] lr = new double[61];
        for (int k = 0; k <= 60; k++) { double a = 2 * Math.PI * k / 60; lip[k] = v(cx + 7.4 * Math.cos(a), cy + 7.4 * Math.sin(a), 0); lr[k] = .7 + .2 * noise(lip[k], 1.3, 0); }
        m.tube(lip, lr, c(92, 40, 56), 12, false, false, 1.2);
        for (int k = 0; k < 34; k++) {
            double a = m.rnd.nextDouble() * 6.28;
            m.cone(v(cx + 7.6 * Math.cos(a), cy + 7.6 * Math.sin(a), .3), v(cx + 9.8 * Math.cos(a), cy + 9.8 * Math.sin(a), .6), .28, c(30, 20, 28), 6, false);
        }
    }

    // =============================================================== Uranus: Eternal Edict
    private static void edict(Mesh m) {
        int[] gd = c(255, 214, 110), skin = c(176, 166, 170), cloth = c(42, 34, 52);
        double[] C = v(0, 15.5, -2.2);
        m.part("wheel", C);
        for (int k = 0; k < 32; k++) { double a = k * 2 * Math.PI / 32; m.tube(new double[][]{C, add(C, v(Math.cos(a) * 16, Math.sin(a) * 16, 0))}, .05, gd, 4, true, false, 0); }
        for (double r : new double[]{3, 6, 9, 12, 15}) {
            double[][] pts = new double[49][];
            for (int i = 0; i <= 48; i++) { double a = 2 * Math.PI * i / 48; pts[i] = add(C, v(Math.cos(a) * r, Math.sin(a) * r, 0)); }
            m.tube(pts, .06, gd, 4, true, false, 0);
        }
        int[][] knots = {{255, 150, 210}, {140, 225, 255}, {200, 255, 160}, {255, 230, 150}};
        for (int k = 0; k < 60; k++) { double a = m.rnd.nextDouble() * 6.28, r = m.rr(2, 15); m.ball(add(C, v(Math.cos(a) * r, Math.sin(a) * r, 0)), m.rr(.12, .3), knots[m.rnd.nextInt(4)], true); }
        m.part("cloth", v(0, 14, 0));
        for (int k = 0; k < 120; k++) {
            double a = m.rnd.nextDouble() * 2 * Math.PI, r = m.rr(.5, 2.4);
            m.shroud(v(r * Math.cos(a) * .8, m.rr(11, 15), r * Math.sin(a) * .6), m.rr(10, 16), m.rr(.15, .5), k % 3 != 0 ? cloth : c(70, 52, 86), m.rr(.2, 1.4), 9);
        }
        m.part("body", v(0, 0, 0));
        m.limb(new double[][]{v(0, 4, 0), v(.2, 9, .2), v(.3, 13.5, .5)}, new double[]{2.2, 1.4, 1.05}, c(46, 38, 56), 14, 1, .08);
        m.blob(v(.3, 14.0, .3), v(1.35, 1.85, .85), skin, .1, 10, 14, false, null, 4, .9);
        for (int q = 0; q < 8; q++) m.tube(new double[][]{v(-.9, 15.0 - q * .33, .72), v(.3, 15.1 - q * .33, .9), v(1.5, 15.0 - q * .33, .72)}, .04, c(120, 108, 112), 4, false, true, 0);
        for (int k = 0; k < 70; k++) {
            double t = m.rnd.nextDouble(), a = t * 52 + k, y = 6 + t * 9, r = (1.45 + .3 * Math.sin(t * 5)) * (1.15 - t * .3);
            m.tube(new double[][]{v(.2 + r * Math.cos(a), y, r * Math.sin(a) * .8), v(.2 + r * Math.cos(a + .9), y + .3, r * Math.sin(a + .9) * .8)}, .05, gd, 4, true, false, 0);
        }
        m.limb(new double[][]{v(.3, 15.4, .3), v(.5, 17.0, .9), v(.7, 18.4, 1.6)}, new double[]{.45, .36, .32}, skin, 10, .8, 0);
        double hx = .8, hy = 19.4, hz = 2.2;
        m.blob(v(hx, hy, hz), v(.85, 1.35, .85), skin, .12, 12, 16, false, rotX(.3), 21, .9);
        m.blob(v(hx, hy - .15, hz + .75), v(.15, .45, .15), skin, .1, 6, 8, false, null, 0, .5);
        m.tube(new double[][]{v(hx - .38, hy - .95, hz + .62), v(hx, hy - 1.02, hz + .72), v(hx + .38, hy - .95, hz + .62)}, .03, c(90, 60, 66), 4, false, true, 0);
        m.blob(v(hx, hy + .35, hz + .65), v(.68, .24, .18), skin, .15, 6, 10, false, null, 0, .4);       // skin over the eyes
        for (int q = -1; q <= 1; q += 2) m.tube(new double[][]{v(hx + q * .55, hy + .25, hz + .66), v(hx + q * .22, hy + .45, hz + .8)}, .03, c(110, 90, 96), 4, false, true, 0);
        for (int k = 0; k < 34; k++) m.shroud(v(hx + m.rr(-.7, .7), hy + 1.1, hz - .4), m.rr(8, 17), .07, c(70, 58, 82), .7, 9);
        for (int sg = -1; sg <= 1; sg += 2) {
            m.arm(v(.3 + sg * 1.5, 15.6, .4), v(.3 + sg * 4.3, 14.2, 1.8), v(.3 + sg * 6.0, 17.4, 3.0), .3, skin, v(sg * .3, .9, .5), .7);
            double[] ha = v(.3 + sg * 6.0, 18.2, 3.2);
            for (int k = 0; k < 22; k++)
                m.tube(spline(add(ha, v(m.rr(-.4, .4), m.rr(-.2, .5), 0)), add(ha, v(sg * m.rr(1, 4), m.rr(-1, 3), -m.rr(1, 3))), add(C, v(sg * m.rr(2, 13), m.rr(-8, 8), 0))), .04, gd, 4, true, false, 0);
        }
        m.part("blade", v(hx, 26, hz));
        m.tube(new double[][]{v(hx, 31, hz - .2), v(hx, 23.2, hz - .2)}, new double[]{.35, .3}, c(205, 212, 230), 10, true, false, 0);
        m.cone(v(hx, 23.2, hz - .2), v(hx, 21.2, hz - .2), .3, c(235, 240, 250), 8, true);
        m.line(v(hx - 1.5, 30.6, hz - .2), v(hx + 1.5, 30.6, hz - .2), .24, c(160, 140, 90), 3, 6, false);
    }

    // =============================================================== Neptune: Eternal Aeon
    private static void face(Mesh m, String kind, double[] c0, double[][] R, double sc) {
        java.util.function.Function<double[], double[]> L = p -> add(c0, app(R, mul(p, sc)));
        int[] blk = c(28, 22, 32), sil = c(176, 182, 196), hol = c(255, 240, 205);
        switch (kind) {
            case "sin" -> {
                m.blob(L.apply(v(0, 0, 0)), v(1.0 * sc, 1.15 * sc, 1.0 * sc), blk, .08, 8, 12, false, R, 31, .9);
                m.blob(L.apply(v(0, -.75, .5)), v(.72 * sc, .45 * sc, .65 * sc), blk, .08, 6, 10, false, R, 32, .9);
                for (int q = -1; q <= 1; q += 2) {
                    m.blob(L.apply(v(q * .45, .38, .82)), v(.3 * sc, .12 * sc, .2 * sc), c(14, 10, 16), .05, 6, 10, false, R, 0, .5);
                    m.ball(L.apply(v(q * .4, .12, .93)), v(.06 * sc, .025 * sc, .03 * sc), c(255, 50, 40), true, R);
                    m.limb(new double[][]{L.apply(v(q * .6, .95, 0)), L.apply(v(q * 1.3, 1.5, -.2)), L.apply(v(q * 1.5, 2.5, -.4))}, new double[]{.2 * sc, .15 * sc, .03}, c(12, 8, 14), 7, .8, 0);
                }
                m.tube(new double[][]{L.apply(v(-.42, -.62, .98)), L.apply(v(0, -.8, 1.05)), L.apply(v(.42, -.62, .98))}, .06 * sc, c(255, 60, 50), 4, true, true, 0);
                for (int q = -4; q <= 4; q++) m.cone(L.apply(v(q * .1, -.68, 1.04)), L.apply(v(q * .1, -.98, 1.04)), .04 * sc, c(235, 230, 220), 4, false);
            }
            case "suffer" -> {
                m.blob(L.apply(v(0, 0, 0)), v(.85 * sc, 1.2 * sc, .85 * sc), sil, .05, 8, 12, false, R, 33, .8);
                for (int q = -1; q <= 1; q += 2) {
                    m.blob(L.apply(v(q * .34, .12, .7)), v(.24 * sc, .28 * sc, .12 * sc), c(26, 24, 34), .03, 6, 10, false, R, 0, .3);
                    m.tube(new double[][]{L.apply(v(q * .44, -.14, .82)), L.apply(v(q * .5, -.85, .84))}, new double[]{.05 * sc, .03 * sc}, c(130, 160, 190), 4, true, true, 0);
                }
                m.blob(L.apply(v(0, -.62, .7)), v(.26 * sc, .36 * sc, .14 * sc), c(16, 14, 22), .05, 6, 10, false, R, 0, .3);
            }
            default -> {
                m.blob(L.apply(v(0, 0, 0)), v(.92 * sc, 1.15 * sc, .9 * sc), hol, .03, 8, 12, true, R, 34, .3);
                for (int q = -1; q <= 1; q += 2) m.tube(new double[][]{L.apply(v(q * .5, .18, .88)), L.apply(v(q * .18, .08, .97))}, new double[]{.05 * sc, .035 * sc}, c(120, 90, 60), 4, false, true, 0);
                m.tube(new double[][]{L.apply(v(-.28, -.5, .92)), L.apply(v(0, -.58, .97)), L.apply(v(.28, -.5, .92))}, .04 * sc, c(150, 100, 80), 4, false, true, 0);
                m.ring(L.apply(v(0, 1.7, -.2)), 1.14 * sc, .09, c(255, 240, 170), Math.PI / 2, 0, true, 40, 4);
            }
        }
    }

    private static void aeon(Mesh m) {
        double Rr = 8.5;
        double[] Cc = v(0, 13.0, 0);
        int[][] cols = {{22, 18, 26}, {170, 176, 190}, {250, 236, 200}};
        String[] kinds = {"sin", "suffer", "redeem"};
        double[] th0s = {Math.toRadians(90), Math.toRadians(210), Math.toRadians(330)};
        for (int k = 0; k < 3; k++) {
            int[] skin = cols[k]; double span = Math.toRadians(105), th0 = th0s[k];
            java.util.function.BiFunction<Double, Double, double[]> T = (f, off) -> add(Cc, v((Rr + off) * Math.cos(th0 + span * f), (Rr + off) * Math.sin(th0 + span * f), 0));
            for (int z = -1; z <= 1; z += 2)
                m.limb(new double[][]{add(T.apply(.02, 0.), v(0, 0, z * .9)), add(T.apply(.14, .4), v(0, 0, z * .9)), add(T.apply(.26, 0.), v(0, 0, z * .9)), add(T.apply(.36, 0.), v(0, 0, z * .9))}, new double[]{1.0, 1.35, 1.5, 1.35}, skin, 12, 1, .05);
            m.blob(T.apply(.4, 0.), v(2.2, 2.2, 2.2), skin, .08, 8, 12, false, null, 2 + k, 1);
            m.limb(new double[][]{T.apply(.4, 0.), T.apply(.52, 0.), T.apply(.64, 0.), T.apply(.74, 0.)}, new double[]{2.2, 2.6, 2.7, 2.4}, skin, 16, 1, .05);
            m.blob(T.apply(.72, 0.), v(2.7, 2.9, 2.4), skin, .08, 8, 12, false, null, 5 + k, 1);
            m.limb(new double[][]{T.apply(.8, 0.), T.apply(.88, 0.), T.apply(.94, 0.)}, new double[]{1.3, 1.0, .9}, skin, 10, 1, 0);
            double[] hc = T.apply(.99, 0.), inward = norm(sub(Cc, hc)), tang = norm(sub(T.apply(1.0, 0.), T.apply(.9, 0.)));
            face(m, kinds[k], hc, orient(inward, tang), 1.9);
            double[] sh = T.apply(.76, 1.0);
            for (int z = -1; z <= 1; z += 2) {
                double[] s0 = add(sh, v(0, 0, z * 1.2)), wr = add(Cc, v(m.rr(-.5, .5), m.rr(-.5, .5), z * .42));
                double[] el = add(add(lerp(s0, wr, .5), v(0, 0, z * .6)), mul(sub(s0, Cc), .05));
                m.arm(s0, el, wr, .85, skin, sub(Cc, wr), .7);
            }
        }
        m.part("core", Cc);
        m.ball(Cc, 1.1, c(1, 1, 3), false);
        m.part("rings", Cc);
        int[][] rc = {{14, 14, 18}, {190, 198, 214}, {255, 250, 235}, {120, 120, 140}};
        for (int k = 0; k < 4; k++) m.ring(Cc, 1.7 + k * .5, .07, rc[k], Math.PI / 2, 0, k % 2 == 1, 50, 5);
        m.part("outer", Cc);
        for (int k = 0; k < 3; k++) m.ring(Cc, Rr + 3.6 + k * .8, .06, c(170, 175, 200), Math.PI / 2, 0, true, 80, 4);
    }
}
