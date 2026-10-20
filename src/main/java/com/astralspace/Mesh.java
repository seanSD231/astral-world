package com.astralspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A small smooth-mesh builder (plain Java, no Minecraft classes). Everything is quads with lighting baked into the vertex
 * colours. x, y, z, r, g, b per vertex (colours 0..255), 4 vertices per quad. Used for the planet sovereigns.
 */
final class Mesh {
    float[] data = new float[1 << 16];
    int floats = 0;
    final Random rnd;
    private static final double[] LIGHT = norm(v(0.3, 0.55, 0.75));
    private static final double[] FILL = norm(v(-0.5, 0.2, 0.4));

    Mesh(long seed) { rnd = new Random(seed); }
    int quadCount() { return floats / 24; }

    // ---------------------------------------------------------------- vectors
    static double[] v(double x, double y, double z) { return new double[]{x, y, z}; }
    static double[] add(double[] a, double[] b) { return v(a[0] + b[0], a[1] + b[1], a[2] + b[2]); }
    static double[] sub(double[] a, double[] b) { return v(a[0] - b[0], a[1] - b[1], a[2] - b[2]); }
    static double[] mul(double[] a, double s) { return v(a[0] * s, a[1] * s, a[2] * s); }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double[] cross(double[] a, double[] b) { return v(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]); }
    static double[] norm(double[] a) { double l = Math.sqrt(dot(a, a)); return l < 1e-9 ? a : mul(a, 1 / l); }
    static double len(double[] a) { return Math.sqrt(dot(a, a)); }
    static double[] lerp(double[] a, double[] b, double t) { return add(a, mul(sub(b, a), t)); }
    double rr(double lo, double hi) { return lo + rnd.nextDouble() * (hi - lo); }

    /** Rotation matrices, row-major 3x3. */
    static double[][] rotY(double a) { double c = Math.cos(a), s = Math.sin(a); return new double[][]{{c, 0, s}, {0, 1, 0}, {-s, 0, c}}; }
    static double[][] rotZ(double a) { double c = Math.cos(a), s = Math.sin(a); return new double[][]{{c, -s, 0}, {s, c, 0}, {0, 0, 1}}; }
    static double[][] rotX(double a) { double c = Math.cos(a), s = Math.sin(a); return new double[][]{{1, 0, 0}, {0, c, -s}, {0, s, c}}; }
    static double[] app(double[][] m, double[] p) {
        return v(m[0][0] * p[0] + m[0][1] * p[1] + m[0][2] * p[2], m[1][0] * p[0] + m[1][1] * p[1] + m[1][2] * p[2], m[2][0] * p[0] + m[2][1] * p[1] + m[2][2] * p[2]);
    }
    static double[][] mm(double[][] a, double[][] b) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) r[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
        return r;
    }
    /** Frame with z along dir, y as close to up as possible. */
    static double[][] orient(double[] dir, double[] up) {
        double[] z = norm(dir);
        double[] x = norm(cross(up, z));
        if (len(x) < 1e-6) x = v(1, 0, 0);
        double[] y = cross(z, x);
        return new double[][]{{x[0], y[0], z[0]}, {x[1], y[1], z[1]}, {x[2], y[2], z[2]}};
    }

    static double noise(double[] p, double f, double seed) {
        double x = p[0] * f + seed, y = p[1] * f, z = p[2] * f;
        return (Math.sin(3.1 * x + 1.7 * y) * Math.sin(2.3 * y + 2.9 * z) + .6 * Math.sin(6.3 * z + 4.1 * x - 2 * y) * Math.sin(5.1 * y + 3.3 * x)) * .6;
    }

    static double[][] spline(List<double[]> pts, int n) {
        List<double[]> P = new ArrayList<>();
        P.add(pts.get(0)); P.addAll(pts); P.add(pts.get(pts.size() - 1));
        List<double[]> out = new ArrayList<>();
        for (int i = 1; i < P.size() - 2; i++)
            for (int k = 0; k < n; k++) {
                double t = k / (double) n, t2 = t * t, t3 = t2 * t;
                double[] p0 = P.get(i - 1), p1 = P.get(i), p2 = P.get(i + 1), p3 = P.get(i + 2);
                double[] r = new double[3];
                for (int c = 0; c < 3; c++)
                    r[c] = .5 * ((2 * p1[c]) + (-p0[c] + p2[c]) * t + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * t2 + (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * t3);
                out.add(r);
            }
        out.add(pts.get(pts.size() - 1));
        return out.toArray(new double[0][]);
    }
    static double[][] spline(double[]... pts) { return spline(java.util.Arrays.asList(pts), 8); }

    // ---------------------------------------------------------------- emitting
    private void vert(double[] p, double[] n, int[] base, boolean emissive, double jit, double tex) {
        if (floats + 6 > data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
        double r, g, b;
        if (emissive) { r = base[0]; g = base[1]; b = base[2]; }
        else {
            double[] nn = norm(n);
            double lam = Math.max(0, Math.min(1, (dot(nn, LIGHT) + .35) / 1.35));
            double fill = Math.max(0, dot(nn, FILL) + .2) * .3 * (1 - lam);
            double k = (.3 + .9 * lam + fill) * jit * (1 + tex * .22 * noise(p, 1.6, 0));
            r = base[0] * k + 14; g = base[1] * k + 10; b = base[2] * k + 24;
        }
        data[floats++] = (float) p[0]; data[floats++] = (float) p[1]; data[floats++] = (float) p[2];
        data[floats++] = (float) Math.max(0, Math.min(255, r)); data[floats++] = (float) Math.max(0, Math.min(255, g)); data[floats++] = (float) Math.max(0, Math.min(255, b));
    }

    void quad(double[] a, double[] na, double[] b, double[] nb, double[] c, double[] nc, double[] d, double[] nd, int[] col, boolean em, double tex) {
        double j = .92 + rnd.nextDouble() * .16;
        vert(a, na, col, em, j, tex); vert(b, nb, col, em, j, tex); vert(c, nc, col, em, j, tex); vert(d, nd, col, em, j, tex);
    }

    // ---------------------------------------------------------------- shapes
    void tube(double[][] sp, double[] rad, int[] col, int sides, boolean em, boolean cap, double tex) {
        int n = sp.length;
        double[][][] ring = new double[n][sides][], nrm = new double[n][sides][];
        for (int i = 0; i < n; i++) {
            double[] t = norm(sub(sp[Math.min(i + 1, n - 1)], sp[Math.max(i - 1, 0)]));
            double[] ax = Math.abs(t[1]) < .9 ? v(0, 1, 0) : v(1, 0, 0);
            double[] u = norm(cross(t, ax)), w = norm(cross(t, u));
            for (int k = 0; k < sides; k++) {
                double a = 2 * Math.PI * k / sides;
                double[] d = add(mul(u, Math.cos(a)), mul(w, Math.sin(a)));
                ring[i][k] = add(sp[i], mul(d, rad[i])); nrm[i][k] = d;
            }
        }
        for (int i = 0; i < n - 1; i++)
            for (int k = 0; k < sides; k++) {
                int k2 = (k + 1) % sides;
                quad(ring[i][k], nrm[i][k], ring[i + 1][k], nrm[i + 1][k], ring[i + 1][k2], nrm[i + 1][k2], ring[i][k2], nrm[i][k2], col, em, tex);
            }
        if (cap && n >= 2) {
            double[] td = norm(sub(sp[n - 1], sp[n - 2]));
            double[] tip = add(sp[n - 1], mul(td, rad[n - 1] * .35));
            for (int k = 0; k < sides; k++) { int k2 = (k + 1) % sides; quad(ring[n - 1][k], nrm[n - 1][k], ring[n - 1][k2], nrm[n - 1][k2], tip, td, tip, td, col, em, tex); }
        }
    }
    void tube(double[][] sp, double rad, int[] col, int sides, boolean em, boolean cap, double tex) {
        double[] r = new double[sp.length]; java.util.Arrays.fill(r, rad); tube(sp, r, col, sides, em, cap, tex);
    }
    /** A limb: radii roughened by noise. */
    void limb(double[][] sp, double[] rad, int[] col, int sides, double tex, double rough) {
        double[] r = rad.clone();
        if (rough > 0) for (int i = 0; i < r.length; i++) r[i] *= 1 + rough * noise(sp[i], 1.7, 0);
        tube(sp, r, col, sides, false, true, tex);
    }
    void limb(double[] a, double[] b, double[] c, double ra, double rb, double rc, int[] col, double tex) {
        limb(new double[][]{a, b, c}, new double[]{ra, rb, rc}, col, 12, tex, .05);
    }
    void line(double[] a, double[] b, double r, int[] col, int n, int sides, boolean em) {
        double[][] sp = new double[n + 1][];
        for (int i = 0; i <= n; i++) sp[i] = lerp(a, b, i / (double) n);
        tube(sp, r, col, sides, em, true, 0);
    }
    void cone(double[] a, double[] b, double r, int[] col, int sides, boolean em) {
        tube(new double[][]{a, lerp(a, b, .5), b}, new double[]{r, r * .5, .02}, col, sides, em, false, 0);
    }

    void blob(double[] c, double[] r, int[] col, double disp, int lat, int lon, boolean em, double[][] rot, double seed, double tex) {
        double[][][] p = new double[lat + 1][lon][], nn = new double[lat + 1][lon][];
        for (int i = 0; i <= lat; i++)
            for (int j = 0; j < lon; j++) {
                double th = Math.PI * i / lat, ph = 2 * Math.PI * j / lon;
                double[] d = v(Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph));
                double k = 1 + disp * noise(mul(d, 2.2), 1, seed);
                double[] q = v(d[0] * r[0] * k, d[1] * r[1] * k, d[2] * r[2] * k);
                double[] n = norm(v(d[0] / r[0], d[1] / r[1], d[2] / r[2]));
                if (rot != null) { q = app(rot, q); n = app(rot, n); }
                p[i][j] = add(c, q); nn[i][j] = n;
            }
        for (int i = 0; i < lat; i++)
            for (int j = 0; j < lon; j++) {
                int j2 = (j + 1) % lon;
                quad(p[i][j], nn[i][j], p[i + 1][j], nn[i + 1][j], p[i + 1][j2], nn[i + 1][j2], p[i][j2], nn[i][j2], col, em, tex);
            }
    }
    void blob(double[] c, double r, int[] col, double disp, int lat, int lon, boolean em, double seed) { blob(c, v(r, r, r), col, disp, lat, lon, em, null, seed, 1); }
    void ball(double[] c, double r, int[] col, boolean em) { blob(c, v(r, r, r), col, 0, 8, 12, em, null, 0, 0); }
    void ball(double[] c, double[] r, int[] col, boolean em, double[][] rot) { blob(c, r, col, 0, 8, 12, em, rot, 0, 0); }

    void ring(double[] c, double R, double r, int[] col, double tx, double tz, boolean em, int seg, int sides) {
        double[][] M = mm(rotZ(tz), rotX(tx));
        double[][] pts = new double[seg + 1][];
        for (int i = 0; i <= seg; i++) { double a = 2 * Math.PI * i / seg; pts[i] = add(c, app(M, v(R * Math.cos(a), 0, R * Math.sin(a)))); }
        tube(pts, r, col, sides, em, false, 0);
    }

    void box(double[] c, double hx, double hy, double hz, int[] col, boolean em, double[][] rot) {
        int[][][] faces = {{{0, 0, -1}, {-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1}}, {{0, 0, 1}, {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}},
                {{-1, 0, 0}, {-1, -1, -1}, {-1, -1, 1}, {-1, 1, 1}, {-1, 1, -1}}, {{1, 0, 0}, {1, -1, -1}, {1, -1, 1}, {1, 1, 1}, {1, 1, -1}},
                {{0, 1, 0}, {-1, 1, -1}, {1, 1, -1}, {1, 1, 1}, {-1, 1, 1}}, {{0, -1, 0}, {-1, -1, -1}, {1, -1, -1}, {1, -1, 1}, {-1, -1, 1}}};
        for (int[][] f : faces) {
            double[] n = v(f[0][0], f[0][1], f[0][2]);
            double[][] P = new double[4][];
            for (int i = 0; i < 4; i++) {
                double[] q = v(f[i + 1][0] * hx, f[i + 1][1] * hy, f[i + 1][2] * hz);
                if (rot != null) q = app(rot, q);
                P[i] = add(c, q);
            }
            if (rot != null) n = app(rot, n);
            quad(P[0], n, P[1], n, P[2], n, P[3], n, col, em, 0);
        }
    }

    /** A closed surface through rings {cx, cy, cz, rx, rz, wobble}. */
    void loft(double[][] rings, int[] col, double tex) {
        int sides = 22;
        double[][][] R = new double[rings.length][sides][], N = new double[rings.length][sides][];
        for (int i = 0; i < rings.length; i++) {
            double[] g = rings[i];
            for (int k = 0; k < sides; k++) {
                double a = 2 * Math.PI * k / sides, w = 1 + g[5] * Math.sin(k * 3 + i);
                R[i][k] = v(g[0] + g[3] * w * Math.cos(a), g[1], g[2] + g[4] * w * Math.sin(a));
                N[i][k] = norm(v(Math.cos(a) * g[4], 0, Math.sin(a) * g[3]));
            }
        }
        for (int i = 0; i < rings.length - 1; i++)
            for (int k = 0; k < sides; k++) { int k2 = (k + 1) % sides; quad(R[i][k], N[i][k], R[i + 1][k], N[i + 1][k], R[i + 1][k2], N[i + 1][k2], R[i][k2], N[i][k2], col, false, tex); }
    }

    /** A ragged hanging strip. */
    void shroud(double[] top, double length, double w, int[] col, double sway, int n) {
        double ph = rnd.nextDouble() * 6;
        double[][] pts = new double[n][]; double[] rad = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            pts[i] = add(top, v(sway * Math.sin(t * 3 + ph) * t * length * .18, -t * length, sway * Math.cos(t * 2.3 + ph) * t * length * .1));
            rad[i] = w * (1 - .55 * t) + .02;
        }
        tube(pts, rad, col, 4, false, false, .9);
    }

    /** A two-sided cloth triangle fan panel, subdivided and sagging. */
    void tri(double[] a, double[] b, double[] c, int[] col, int n, double amp, double seed) {
        double[] nr = norm(cross(sub(b, a), sub(c, a)));
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n - i; j++) {
                double[] p00 = triP(a, b, c, i, j, n, amp, seed), p10 = triP(a, b, c, i + 1, j, n, amp, seed), p01 = triP(a, b, c, i, j + 1, n, amp, seed);
                quad(p00, nr, p10, nr, p01, nr, p01, nr, col, false, .5);
                if (j < n - i - 1) { double[] p11 = triP(a, b, c, i + 1, j + 1, n, amp, seed); quad(p10, nr, p11, nr, p01, nr, p01, nr, col, false, .5); }
            }
    }
    private double[] triP(double[] a, double[] b, double[] c, int i, int j, int n, double amp, double seed) {
        double u = i / (double) n, vv = j / (double) n, s = u + vv, fr = s > 1e-6 ? vv / s : 0;
        double[] p = add(a, add(mul(sub(b, a), u), mul(sub(c, a), vv)));
        p = add(p, mul(sub(a, p), .22 * Math.sin(Math.PI * fr) * Math.pow(s, 4)));
        return add(p, v(0, amp * Math.sin(u * 5 + vv * 4 + seed) * Math.sin(vv * 3 + seed) * (1 - Math.abs(u - .5)), 0));
    }

    /** A single small overlapping scale plate lying on a surface. */
    void plate(double[] c, double[] side, double[] fwd, double[] out, double hw, double hl, int[] col) {
        double[] a = sub(sub(c, mul(side, hw)), mul(fwd, hl)), b = sub(add(c, mul(side, hw)), mul(fwd, hl));
        double[] cc = add(add(c, mul(side, hw)), mul(fwd, hl)), d = add(sub(c, mul(side, hw)), mul(fwd, hl));
        double[] lift = mul(out, hl * .35);
        cc = add(cc, lift); d = add(d, lift);
        quad(a, out, b, out, cc, out, d, out, col, false, .6);
    }

    // ---------------------------------------------------------------- limbs with hands
    void hand(double[] wr, double[] dir, double size, int[] col) {
        double[][] R = orient(dir, v(0, 1, 0));
        double[] z = norm(dir);
        blob(add(wr, mul(z, size * .6)), v(size * .55, size * .2, size * .65), col, .05, 6, 10, false, R, 0, .5);
        for (int f = 0; f < 4; f++) {
            double x = (f - 1.5) * size * .34, ln = size * ((f == 1 || f == 2) ? .9 : .8);
            double[] p0 = add(wr, app(R, v(x, 0, size))), p1 = add(p0, app(R, v(x * .5, -size * .08, ln * .55))), p2 = add(p1, app(R, v(x * .3, -size * .2, ln * .45)));
            tube(new double[][]{p0, p1, p2}, new double[]{size * .1, size * .085, size * .06}, col, 6, false, true, .5);
        }
        double[] t0 = add(wr, app(R, v(size * .5, 0, size * .4)));
        tube(new double[][]{t0, add(wr, app(R, v(size * .85, -size * .1, size * .9))), add(wr, app(R, v(size, -size * .2, size * 1.3)))}, new double[]{size * .12, size * .1, size * .07}, col, 6, false, true, .5);
    }
    void arm(double[] sh, double[] el, double[] wr, double r, int[] col, double[] handDir, double hs) {
        blob(sh, v(r * 1.5, r * 1.5, r * 1.5), col, .12, 8, 12, false, null, 1, .9);
        limb(new double[][]{sh, add(lerp(sh, el, .5), v(0, 0, r * .2)), el}, new double[]{r * 1.1, r, r * .78}, col, 12, .9, .06);
        blob(el, v(r * .85, r * .85, r * .85), col, .1, 6, 10, false, null, 2, .9);
        limb(new double[][]{el, lerp(el, wr, .5), wr}, new double[]{r * .8, r * .7, r * .46}, col, 12, .9, .06);
        hand(wr, handDir != null ? handDir : sub(wr, el), hs > 0 ? hs : r, col);
    }
}
