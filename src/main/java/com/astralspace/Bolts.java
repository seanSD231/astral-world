package com.astralspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Storm attack's web of lightning. The server and every client build the same web from the same seed, so only the seed
 * is sent: the server uses it to find who is struck, the clients use it to draw the red warning lines and then the bolts.
 */
public final class Bolts {
    private Bolts() {}

    /** One piece of the web: from (x0, z0) to (x1, z1); depth 0 is a main arm, higher is a branch off a branch. */
    public record Seg(double x0, double z0, double x1, double z1, int depth) {}

    public static List<Seg> web(long seed, double cx, double cz, double reach) {
        Random r = new Random(seed);
        List<Seg> out = new ArrayList<>();
        int arms = 9 + r.nextInt(4);
        double base = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < arms; i++) {
            double ang = base + Math.PI * 2 * i / arms + (r.nextDouble() - 0.5) * 0.35;
            grow(r, out, cx, cz, ang, reach * (0.7 + r.nextDouble() * 0.3), 0);
        }
        return out;
    }

    private static void grow(Random r, List<Seg> out, double x, double z, double ang, double len, int depth) {
        double done = 0;
        while (done < len && out.size() < 420) {
            double step = Math.min(len - done, 9 + r.nextDouble() * 8);
            ang += (r.nextDouble() - 0.5) * (depth == 0 ? 0.5 : 0.8);
            double nx = x + Math.cos(ang) * step, nz = z + Math.sin(ang) * step;
            out.add(new Seg(x, z, nx, nz, depth));
            x = nx; z = nz; done += step;
            if (depth < 3 && done > 10 && r.nextDouble() < (depth == 0 ? 0.42 : 0.3)) {
                double side = r.nextBoolean() ? 1 : -1;
                grow(r, out, x, z, ang + side * (0.5 + r.nextDouble() * 0.7), (len - done) * (0.3 + r.nextDouble() * 0.4) + 6, depth + 1);
            }
        }
    }

    /** Horizontal distance from (px, pz) to the nearest piece of the web. */
    public static double distance(List<Seg> web, double px, double pz) {
        double best = Double.MAX_VALUE;
        for (Seg s : web) {
            double dx = s.x1() - s.x0(), dz = s.z1() - s.z0(), l2 = dx * dx + dz * dz;
            double t = l2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ((px - s.x0()) * dx + (pz - s.z0()) * dz) / l2));
            double ex = s.x0() + dx * t - px, ez = s.z0() + dz * t - pz;
            best = Math.min(best, Math.sqrt(ex * ex + ez * ez));
        }
        return best;
    }
}
