package com.astralspace;

import java.util.Random;

/**
 * The Mother Tree of Desire as a smooth mesh (plain Java, no Minecraft classes, so it can be built anywhere).
 * Two forms: the healthy world-tree (braided trunk, 16 flat buttress roots, forking branches, luminous blossoms,
 * glowing veins) and the rotten, fleshy form it turns into at half HP.
 * Everything is quads. Lighting is baked into the vertex colours (the GPU smooths it across each quad).
 * Units are blocks: about 150 blocks tall, the canopy about 300 across. The origin is the middle of the trunk's foot.
 */
final class TreeModel {
    /** x, y, z, r, g, b per vertex (colours 0..255), 4 vertices per quad. */
    float[] data = new float[1 << 16];
    int floats = 0;

    int quadCount() { return floats / 24; }

    /** Animated sections, each running from its offset to the next one's: {name, float offset, pivot}. trunk, roots, limbK, fruitK_G (a third of limb K's blossoms), vineK. */
    final java.util.List<Object[]> parts = new java.util.ArrayList<>();
    final double[] limbAngle = new double[12];
    final double[][] limbPivot = new double[12][];
    final java.util.List<java.util.List<double[]>> limbTips = new java.util.ArrayList<>();
    final java.util.Map<String, double[]> centroids = new java.util.HashMap<>();
    /** Where each buttress root runs: its angle and length (the server builds invisible hit boxes along them). */
    final java.util.List<Double> rootAngle = new java.util.ArrayList<>(), rootLen = new java.util.ArrayList<>();
    private final java.util.List<double[]> leafPts = new java.util.ArrayList<>();   // {x, y, z, dirx, diry, dirz} along the twigs
    private void part(String name, double[] pivot) { parts.add(new Object[]{name, floats, pivot}); }

    private final Random rnd;
    private final boolean rot;

    private TreeModel(boolean rot, long seed) { this.rot = rot; this.rnd = new Random(seed); }

    static TreeModel build(boolean rot) {
        TreeModel m = new TreeModel(rot, rot ? 4242L : 9L);
        m.make();
        return m;
    }

    // ------------------------------------------------------------------ vector bits

    private static double[] v(double x, double y, double z) { return new double[]{x, y, z}; }
    private static double[] add(double[] a, double[] b) { return v(a[0] + b[0], a[1] + b[1], a[2] + b[2]); }
    private static double[] sub(double[] a, double[] b) { return v(a[0] - b[0], a[1] - b[1], a[2] - b[2]); }
    private static double[] mul(double[] a, double s) { return v(a[0] * s, a[1] * s, a[2] * s); }
    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    private static double[] cross(double[] a, double[] b) { return v(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]); }
    private static double[] norm(double[] a) { double l = Math.sqrt(dot(a, a)); return l < 1e-9 ? a : mul(a, 1 / l); }
    private double rr(double lo, double hi) { return lo + rnd.nextDouble() * (hi - lo); }

    private static final double[] LIGHT = norm(v(0.45, 0.6, 0.65));

    // ------------------------------------------------------------------ emitting quads

    private void vert(double[] p, double[] n, int[] base, boolean emissive, float jitter) {
        if (floats + 6 > data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
        double r, g, b;
        if (emissive) { r = base[0]; g = base[1]; b = base[2]; }
        else {
            double lam = Math.max(0, Math.min(1, (dot(norm(n), LIGHT) + 0.35) / 1.35));
            double up = 0.5 + 0.5 * norm(n)[1];
            double k = 0.34 + 0.85 * lam + 0.12 * up;
            r = base[0] * k * jitter; g = base[1] * k * jitter; b = base[2] * k * jitter;
            r += 18; g += 10; b += 30; // a little moonlit violet in the shadows
        }
        data[floats++] = (float) p[0]; data[floats++] = (float) p[1]; data[floats++] = (float) p[2];
        data[floats++] = (float) Math.min(255, r); data[floats++] = (float) Math.min(255, g); data[floats++] = (float) Math.min(255, b);
    }

    private void quad(double[] a, double[] na, double[] b, double[] nb, double[] c, double[] nc, double[] d, double[] nd, int[] base, boolean emissive) {
        float j = (float) (0.88 + rnd.nextDouble() * 0.24);
        vert(a, na, base, emissive, j); vert(b, nb, base, emissive, j); vert(c, nc, base, emissive, j); vert(d, nd, base, emissive, j);
    }

    // ------------------------------------------------------------------ shapes

    private static double[][] bez(double[] p0, double[] p1, double[] p2, double[] p3, int n) {
        double[][] out = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n, u = 1 - t;
            out[i] = add(add(mul(p0, u * u * u), mul(p1, 3 * u * u * t)), add(mul(p2, 3 * u * t * t), mul(p3, t * t * t)));
        }
        return out;
    }

    private void tube(double[][] spine, double[] radii, int sides, int[] base, boolean emissive, boolean lumpy) {
        int n = spine.length;
        double[][][] ring = new double[n][sides][];
        double[][][] nrm = new double[n][sides][];
        double phase = rnd.nextDouble() * 6;
        for (int i = 0; i < n; i++) {
            double[] t = norm(sub(spine[Math.min(i + 1, n - 1)], spine[Math.max(i - 1, 0)]));
            double[] ax = Math.abs(t[1]) < 0.9 ? v(0, 1, 0) : v(1, 0, 0);
            double[] u = norm(cross(t, ax)), w = norm(cross(t, u));
            double rad = radii[i] * (lumpy ? 1 + 0.2 * Math.sin(i * 1.35 + phase) : 1);
            for (int k = 0; k < sides; k++) {
                double a = 2 * Math.PI * k / sides;
                double[] d = add(mul(u, Math.cos(a)), mul(w, Math.sin(a)));
                ring[i][k] = add(spine[i], mul(d, rad));
                nrm[i][k] = d;
            }
        }
        for (int i = 0; i < n - 1; i++)
            for (int k = 0; k < sides; k++) {
                int k2 = (k + 1) % sides;
                quad(ring[i][k], nrm[i][k], ring[i + 1][k], nrm[i + 1][k], ring[i + 1][k2], nrm[i + 1][k2], ring[i][k2], nrm[i][k2], base, emissive);
            }
        // a small cap on the tip
        double[] tipDir = norm(sub(spine[n - 1], spine[Math.max(0, n - 2)]));
        double[] tip = add(spine[n - 1], mul(tipDir, radii[n - 1] * 0.4));
        for (int k = 0; k < sides; k++) {
            int k2 = (k + 1) % sides;
            quad(ring[n - 1][k], nrm[n - 1][k], ring[n - 1][k2], nrm[n - 1][k2], tip, tipDir, tip, tipDir, base, emissive);
        }
    }

    private void sphere(double[] c, double r, int[] base, int lat, int lon, double jit, boolean emissive, double sy) {
        double[][][] p = new double[lat + 1][lon][];
        double[][][] nn = new double[lat + 1][lon][];
        for (int i = 0; i <= lat; i++)
            for (int j = 0; j < lon; j++) {
                double th = Math.PI * i / lat, ph = 2 * Math.PI * j / lon;
                double[] d = v(Math.sin(th) * Math.cos(ph), Math.cos(th) * sy, Math.sin(th) * Math.sin(ph));
                double s = (i == 0 || i == lat) ? 1 : 1 + jit * (rnd.nextDouble() - 0.5);
                p[i][j] = add(c, mul(d, r * s));
                nn[i][j] = norm(d);
            }
        for (int i = 0; i < lat; i++)
            for (int j = 0; j < lon; j++) {
                int j2 = (j + 1) % lon;
                quad(p[i][j], nn[i][j], p[i + 1][j], nn[i + 1][j], p[i + 1][j2], nn[i + 1][j2], p[i][j2], nn[i][j2], base, emissive);
            }
    }

    // ------------------------------------------------------------------ the tree

    private int[] bark() { return rot ? new int[]{128, 52, 60} : new int[]{52, 38, 92}; }
    private int[] pick(int[][] pal) { return pal[rnd.nextInt(pal.length)]; }

    private static final int[][] PAL_OK = {{255, 170, 225}, {200, 160, 255}, {160, 225, 255}, {255, 235, 252}, {240, 130, 205}, {175, 150, 250}};
    private static final int[][] PAL_ROT = {{130, 150, 45}, {165, 120, 45}, {95, 30, 42}, {175, 65, 75}, {75, 85, 35}, {205, 170, 70}};

    private double[] strandPoint(int s, double t) {
        double ph = s * 2 * Math.PI / 3, big = 11 * Math.pow(1 - t, 0.7) + 3.2, a = ph + t * 3.2 * Math.PI;
        return v(big * Math.cos(a), t * 140, big * Math.sin(a));
    }

    private double strandRadius(double t) { return 11.5 * (1 - t * 0.55) + 13 * Math.pow(Math.max(0, 1 - t * 7), 2); }

    private final java.util.ArrayList<double[]> tips = new java.util.ArrayList<>();

    private void branch(double[] p0, double[] d, double length, double r0, int level, int maxLevel) {
        int seg = level == 0 ? 12 : level == 1 ? 9 : 7;
        int sides = level == 0 ? 8 : level == 1 ? 6 : 5;
        double[][] pts = new double[seg + 1][];
        pts[0] = p0;
        double[] cur = p0, dd = norm(d);
        for (int i = 0; i < seg; i++) {
            dd = norm(add(dd, v(rr(-.06, .06), rr(-.03, .045) - 0.028 * (1 + level * 0.5), rr(-.06, .06))));
            cur = add(cur, mul(dd, length / seg));
            pts[i + 1] = cur;
        }
        double[] rad = new double[seg + 1];
        for (int i = 0; i <= seg; i++) rad[i] = r0 * Math.pow(1 - i / (double) seg, 0.85) + 0.18;
        tube(pts, rad, sides, bark(), false, rot);
        if (level >= 1) for (int i = 2; i <= seg; i += 2) leafPts.add(new double[]{pts[i][0], pts[i][1], pts[i][2], dd[0], dd[1], dd[2]});
        if (level < maxLevel) {
            int nsub = level == 0 ? 4 : 3;
            for (int m = 0; m < nsub; m++) {
                int j = 3 + rnd.nextInt(Math.max(1, seg - 4));
                double[] sub = norm(v(rr(-1, 1), rr(0, 0.9), rr(-1, 1)));
                branch(pts[j], add(sub, mul(dd, 0.9)), length * rr(0.5, 0.62), r0 * 0.42, level + 1, maxLevel);
            }
            branch(pts[seg], add(dd, v(rr(-.3, .3), 0.2, rr(-.3, .3))), length * 0.55, r0 * 0.4, level + 1, maxLevel);
        } else {
            tips.add(pts[seg]);
            tips.add(pts[Math.max(0, seg - 4)]);
        }
    }

    private static final int[][] LEAF_OK = {{90, 70, 185}, {125, 95, 215}, {70, 140, 195}, {155, 105, 225}, {205, 125, 205}, {95, 175, 190}};
    private static final int[][] LEAF_ROT = {{125, 135, 42}, {155, 112, 42}, {92, 36, 42}, {168, 72, 72}, {72, 82, 36}};

    /** One leaf: a pointed diamond, hung off the twig, drooping more when rotten. */
    private void leaf(double[] p, double[] d) {
        double len = rot ? rr(4, 8) : rr(6, 11), wid = len * rr(0.38, 0.55);
        double[] dir = norm(add(add(d, v(rr(-1, 1), rr(-0.5, 0.6), rr(-1, 1))), v(0, rot ? -0.9 : -0.25, 0)));
        double[] side = norm(cross(dir, v(rr(-1, 1), 1, rr(-1, 1))));
        double[] base = p;
        double[] mid = add(base, mul(dir, len * 0.5));
        double[] tip = add(add(base, mul(dir, len)), v(0, rot ? -len * 0.3 : -len * 0.12, 0));
        double[] left = add(mid, mul(side, wid * 0.5)), right = sub(mid, mul(side, wid * 0.5));
        double[] n = norm(cross(sub(left, base), sub(right, base)));
        if (n[1] < 0) n = mul(n, -1);
        int[] col = rot ? LEAF_ROT[rnd.nextInt(LEAF_ROT.length)] : LEAF_OK[rnd.nextInt(LEAF_OK.length)];
        quad(base, n, left, n, tip, n, right, n, col, false);
    }

    private void make() {
        part("trunk", v(0, 0, 0));
        int[] bark = bark();
        int[] vein = rot ? new int[]{255, 80, 40} : new int[]{150, 235, 255};
        // braided trunk: three strands wound round each other
        for (int s = 0; s < 3; s++) {
            int n = 34;
            double[][] sp = new double[n][];
            double[] rad = new double[n];
            for (int i = 0; i < n; i++) { double t = i / (double) (n - 1); sp[i] = strandPoint(s, t); rad[i] = strandRadius(t); }
            tube(sp, rad, 12, bark, false, rot);
            // glowing veins winding up the strand
            for (int vv = 0; vv < 2; vv++) {
                int m = 90;
                double[][] vs = new double[m][];
                double[] vr = new double[m];
                for (int i = 0; i < m; i++) {
                    double t = 0.02 + 0.9 * i / (m - 1.0), a = s * 2 * Math.PI / 3 + t * 3.2 * Math.PI;
                    double[] c = strandPoint(s, t), out = v(Math.cos(a), 0, Math.sin(a)), up = v(0, 1, 0);
                    double w = t * 38 + vv * Math.PI + s;
                    double[] off = add(mul(out, Math.cos(w)), mul(norm(cross(out, up)), Math.sin(w)));
                    vs[i] = add(c, mul(off, strandRadius(t) + 0.25));
                    vr[i] = 0.42;
                }
                tube(vs, vr, 4, vein, true, false);
            }
        }
        double[] top = v(0, 148, 0);
        part("roots", v(0, 0, 0));
        // buttress roots, flat on the ground and sinking into it
        for (int k = 0; k < 16; k++) {
            double a = k * 2 * Math.PI / 16 + rnd.nextDouble() * 0.2;
            double[] d = v(Math.cos(a), 0, Math.sin(a));
            double L = 95 + rnd.nextDouble() * 70, lift = rr(6, 22);
            rootAngle.add(a); rootLen.add(L);
            double[][] sp = bez(add(mul(d, 16), v(0, 7, 0)), add(mul(d, L * 0.28), v(0, 7 + lift * 0.25, 0)), add(mul(d, L * 0.65), v(0, 3 + lift * 0.12, 0)), add(mul(d, L), v(0, -2.5, 0)), 18);
            double[] rad = new double[sp.length];
            for (int i = 0; i < sp.length; i++) rad[i] = 14 * Math.pow(1 - i / 18.0, 1.1) + 0.6;
            tube(sp, rad, 8, bark, false, rot);
            if (k % 2 == 0) { // a vein running along the root
                double[][] vs = new double[sp.length][];
                double[] vr = new double[sp.length];
                for (int i = 0; i < sp.length; i++) { vs[i] = add(sp[i], v(0, rad[i] * 0.95 + 0.2, 0)); vr[i] = 0.4; }
                tube(vs, vr, 4, vein, true, false);
            }
            for (int m = 0; m < 3; m++) {
                int j = 6 + rnd.nextInt(8);
                double a2 = a + (rnd.nextBoolean() ? 1 : -1) * rr(0.4, 0.9);
                double[] e = v(Math.cos(a2), 0, Math.sin(a2));
                double[][] q = bez(sp[j], add(add(sp[j], mul(e, 10)), v(0, 5, 0)), add(add(sp[j], mul(e, 22)), v(0, 2, 0)), add(add(sp[j], mul(e, 34)), v(0, -3, 0)), 7);
                double[] qr = new double[q.length];
                for (int i = 0; i < q.length; i++) qr[i] = 4.2 * (1 - i / 7.0) + 0.3;
                tube(q, qr, 5, bark, false, rot);
            }
        }
        // the rotten form: tentacle-roots rising out of the ground, and growths on the trunk
        if (rot) {
            for (int k = 0; k < 10; k++) {
                double a = k * 2 * Math.PI / 10 + rnd.nextDouble() * 0.4;
                double[] d = v(Math.cos(a), 0, Math.sin(a)), side = v(-d[2], 0, d[0]);
                double L = rr(45, 75);
                double[][] sp = bez(add(mul(d, 22), v(0, 4, 0)), add(mul(d, L * 0.5), v(0, rr(30, 60), 0)), add(mul(d, L * 0.9), v(0, rr(10, 40), 0)),
                        add(add(mul(d, L * 0.7), v(0, rr(45, 85), 0)), mul(side, rr(-14, 14))), 20);
                double[] rad = new double[sp.length];
                for (int i = 0; i < sp.length; i++) rad[i] = 7.5 * Math.pow(1 - i / 20.0, 0.8) + 0.5;
                tube(sp, rad, 8, bark, false, true);
            }
            part("trunk", v(0, 0, 0));
            int[][] flesh = {{170, 70, 80}, {140, 50, 60}, {190, 110, 90}, {110, 40, 55}};
            for (int k = 0; k < 70; k++) {
                double t = rr(0.03, 0.85);
                int s = rnd.nextInt(3);
                double a = s * 2 * Math.PI / 3 + t * 3.2 * Math.PI;
                double[] c = add(strandPoint(s, t), mul(v(Math.cos(a), 0, Math.sin(a)), strandRadius(t) * 0.8));
                sphere(c, rr(2.5, 7.5), pick(flesh), 5, 8, 0.25, false, 1);
            }
        }
        // branches: twelve great limbs, each splitting twice more (each limb is its own animated part)
        int[] tipStart = new int[13], leafStart = new int[13];
        for (int k = 0; k < 12; k++) {
            double a = k * 2 * Math.PI / 12 + rnd.nextDouble() * 0.3;
            double[] d = v(Math.cos(a) * 1.3, 0.55 + rnd.nextDouble() * 0.55, Math.sin(a) * 1.3);
            double[] st = add(top, v(Math.cos(a) * 5, -4 - rnd.nextDouble() * 14, Math.sin(a) * 5));
            limbAngle[k] = a;
            limbPivot[k] = st;
            part("limb" + k, st);
            tipStart[k] = tips.size();
            leafStart[k] = leafPts.size();
            branch(st, d, 78 + rnd.nextDouble() * 30, 9.5, 0, 2);
        }
        tipStart[12] = tips.size();
        leafStart[12] = leafPts.size();
        for (int k = 0; k < 12; k++) limbTips.add(new java.util.ArrayList<>(tips.subList(tipStart[k], tipStart[k + 1])));
        // leaves along the twigs: broad glowing violet and teal leaves while healthy, dry, curled and sickly ones once rotten
        for (int k = 0; k < 12; k++) {
            part("leaf" + k, limbPivot[k]);
            for (int i = leafStart[k]; i < leafStart[k + 1]; i++) {
                double[] lp = leafPts.get(i);
                int n = rot ? (rnd.nextDouble() < 0.5 ? 1 : 0) : 3;
                for (int c = 0; c < n; c++) leaf(v(lp[0], lp[1], lp[2]), v(lp[3], lp[4], lp[5]));
            }
        }
        // blossoms (healthy) or sparse rotten fruit at every tip, in thirds per limb so they can fall and regrow one group at a time
        int[][] pal = rot ? PAL_ROT : PAL_OK;
        for (int k = 0; k < 12; k++) {
            for (int g = 0; g < 3; g++) {
                String name = "fruit" + k + "_" + g;
                part(name, limbPivot[k]);
                double[] cen = v(0, 0, 0);
                int n = 0;
                for (int i = g; i < limbTips.get(k).size(); i += 3) {
                    double[] tp = limbTips.get(k).get(i);
                    cen = add(cen, tp); n++;
                    int count = rot ? (rnd.nextDouble() < 0.45 ? 1 : 0) : 2;
                    for (int c = 0; c < count; c++)
                        sphere(add(tp, v(rr(-4, 4), rr(-2, 4), rr(-4, 4))), rr(1.5, 3.0), pick(pal), 3, 6, 0.3, false, 1);
                }
                centroids.put(name, n == 0 ? v(0, 0, 0) : mul(cen, 1.0 / n));
            }
        }
        // hanging roots with glowing bulbs, hanging from their own limb
        int vines = rot ? 30 : 55;
        for (int k = 0; k < 12; k++) {
            part("vine" + k, limbPivot[k]);
            int mine = vines / 12 + (k < vines % 12 ? 1 : 0);
            for (int q = 0; q < mine && !limbTips.get(k).isEmpty(); q++) {
                double[] t = limbTips.get(k).get(rnd.nextInt(limbTips.get(k).size()));
                double L = rr(16, 60);
                double[][] sp = new double[11][];
                double[] rad = new double[11];
                for (int i = 0; i < 11; i++) { sp[i] = add(t, v(Math.sin(i * 0.5) * 1.2, -i * L / 10, Math.cos(i * 0.4) * 1.2)); rad[i] = 0.5; }
                tube(sp, rad, 4, rot ? new int[]{150, 55, 65} : new int[]{120, 90, 200}, false, false);
                int[][] bulbs = rot ? new int[][]{{205, 240, 80}, {255, 130, 50}, {170, 40, 50}} : new int[][]{{120, 230, 255}, {255, 120, 220}, {190, 150, 255}};
                sphere(add(sp[10], v(0, -1.5, 0)), rr(1.4, 2.4), pick(bulbs), 4, 6, 0, true, 1.4);
            }
        }
    }
}
