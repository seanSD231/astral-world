package com.astralspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Meteor Storm's web of lightning, branching out from each impact. The server and every client build the same web from the same seed, so only the seed
 * is sent: the server uses it to find who is struck, the clients use it to draw the red warning lines and then the bolts.
 */
public final class Bolts {
    private Bolts() {}

    /** One piece of the web: from (x0, z0) to (x1, z1); depth 0 is a main arm, higher is a branch off a branch. */
    public record Seg(double x0, double z0, double x1, double z1, int depth) {}

    public static List<Seg> web(long seed, double cx, double cz, double reach, int arms) {
        Random r = new Random(seed);
        List<Seg> out = new ArrayList<>();
        double base = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < arms; i++) {
            double ang = base + Math.PI * 2 * i / arms + (r.nextDouble() - 0.5) * 0.35;
            grow(r, out, cx, cz, ang, reach * (0.7 + r.nextDouble() * 0.3), 0, 300 + arms * 60);
        }
        return out;
    }

    private static void grow(Random r, List<Seg> out, double x, double z, double ang, double len, int depth, int cap) {
        double done = 0;
        while (done < len && out.size() < cap) {
            double step = Math.min(len - done, (depth == 0 ? 7 : 4) + r.nextDouble() * (depth == 0 ? 7 : 5));
            ang += (r.nextDouble() - 0.5) * (depth == 0 ? 0.9 : 1.4);   // wild: the bolt twists as it goes
            double nx = x + Math.cos(ang) * step, nz = z + Math.sin(ang) * step;
            out.add(new Seg(x, z, nx, nz, depth));
            x = nx; z = nz; done += step;
            for (int br = 0; br < 2; br++) {
                if (depth < 4 && done > 6 && r.nextDouble() < (depth == 0 ? 0.55 : 0.4) / (br + 1)) {
                    double side = r.nextBoolean() ? 1 : -1;
                    grow(r, out, x, z, ang + side * (0.4 + r.nextDouble() * 1.1), (len - done) * (0.25 + r.nextDouble() * 0.4) + 4, depth + 1, cap);
                }
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
