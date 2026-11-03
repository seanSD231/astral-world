package com.astralspace;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A level of the osu cabinet: a name, a tempo, an optional music disc, and circles (time in ticks, position 0..1000 across and down).
 * Levels travel as share codes: "OSU1:" and a gzip-compressed, base64 text, which can be pasted into chat, a book or a file.
 */
public final class OsuLevel {
    public static final int MIN_SLIDE = 6, MAX_SLIDE = 160;
    public static final int MAX_NOTES = 400, MAX_TICKS = 20 * 600, MAX_NAME = 32, MAX_DISC = 6000;
    public static final String PREFIX = "OSU1:";

    public String name = "Untitled", author = "", disc = "";
    public int bpm = 120;
    /** {tick, x, y} for a circle, or {tick, x, y, endX, endY, durationTicks} for a slider; kept in time order. */
    public final List<int[]> notes = new ArrayList<>();

    public void sort() { notes.sort((a, b) -> Integer.compare(a[0], b[0])); }

    public static boolean isSlider(int[] n) { return n.length >= 6; }
    /** A curved slider has a bend point {.., cx, cy}: a quadratic curve from the head, bending toward it, to the end. */
    public static boolean isCurved(int[] n) { return n.length >= 8; }

    /** Position (0..1000 across and down) of a slider's ball at fraction f (0 head, 1 end). */
    public static double[] at(int[] n, double f) {
        f = Math.max(0, Math.min(1, f));
        if (isCurved(n)) {
            double u = 1 - f;
            return new double[]{u * u * n[1] + 2 * u * f * n[6] + f * f * n[3], u * u * n[2] + 2 * u * f * n[7] + f * f * n[4]};
        }
        return new double[]{n[1] + (n[3] - n[1]) * f, n[2] + (n[4] - n[2]) * f};
    }

    /** The tick at which the last note is over (a slider ends when its drag ends). */
    public int lengthTicks() {
        int m = 0;
        for (int[] n : notes) m = Math.max(m, isSlider(n) ? n[0] + n[5] : n[0]);
        return m;
    }

    private static String clean(String s, int max) {
        s = s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    public String encode() {
        sort();
        StringBuilder sb = new StringBuilder();
        sb.append(clean(name, MAX_NAME)).append('\n').append(clean(author, 16)).append('\n').append(bpm).append('\n').append(clean(disc, MAX_DISC)).append('\n');
        for (int[] n : notes) {
            sb.append(n[0]).append(',').append(n[1]).append(',').append(n[2]);
            if (isSlider(n)) sb.append(',').append(n[3]).append(',').append(n[4]).append(',').append(n[5]);
            if (isCurved(n)) sb.append(',').append(n[6]).append(',').append(n[7]);
            sb.append(';');
        }
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bo)) { gz.write(sb.toString().getBytes(StandardCharsets.UTF_8)); }
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bo.toByteArray());
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    /** Reads a share code. Throws IllegalArgumentException when it is not a valid level (never trust a code from another player). */
    public static OsuLevel decode(String code) {
        try {
            code = code.trim();
            if (!code.startsWith(PREFIX)) throw new IllegalArgumentException("not an osu level code");
            byte[] raw = Base64.getUrlDecoder().decode(code.substring(PREFIX.length()));
            if (raw.length > 60_000) throw new IllegalArgumentException("too big");
            byte[] text;
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(raw))) { text = gz.readNBytes(200_000); }
            String[] lines = new String(text, StandardCharsets.UTF_8).split("\n", 5);
            if (lines.length < 5) throw new IllegalArgumentException("incomplete level");
            OsuLevel l = new OsuLevel();
            l.name = clean(lines[0], MAX_NAME);
            if (l.name.isEmpty()) l.name = "Untitled";
            l.author = clean(lines[1], 16);
            l.bpm = Math.max(30, Math.min(300, Integer.parseInt(lines[2].trim())));
            l.disc = clean(lines[3], MAX_DISC);
            for (String s : lines[4].split(";")) {
                if (s.isBlank()) continue;
                String[] p = s.split(",");
                int t = Integer.parseInt(p[0].trim()), x = Integer.parseInt(p[1].trim()), y = Integer.parseInt(p[2].trim());
                if (t < 0 || t > MAX_TICKS) continue;
                if (p.length >= 6) {
                    int ex = Integer.parseInt(p[3].trim()), ey = Integer.parseInt(p[4].trim()), du = Integer.parseInt(p[5].trim());
                    int[] sl = new int[]{t, Math.max(0, Math.min(1000, x)), Math.max(0, Math.min(1000, y)), Math.max(0, Math.min(1000, ex)), Math.max(0, Math.min(1000, ey)), Math.max(MIN_SLIDE, Math.min(MAX_SLIDE, du))};
                    if (p.length >= 8) sl = new int[]{sl[0], sl[1], sl[2], sl[3], sl[4], sl[5], Math.max(0, Math.min(1000, Integer.parseInt(p[6].trim()))), Math.max(0, Math.min(1000, Integer.parseInt(p[7].trim())))};
                    l.notes.add(sl);
                } else l.notes.add(new int[]{t, Math.max(0, Math.min(1000, x)), Math.max(0, Math.min(1000, y))});
                if (l.notes.size() > MAX_NOTES) throw new IllegalArgumentException("too many circles");
            }
            if (l.notes.isEmpty()) throw new IllegalArgumentException("no circles");
            l.sort();
            return l;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("not a valid level");
        }
    }

    /** A quick random level for the cabinet's "quick play": slow, with every fifth shape a slider, and nothing close to another shape that is on screen at the same time. */
    public static OsuLevel random(long seed, int count) {
        java.util.Random r = new java.util.Random(seed);
        OsuLevel l = new OsuLevel();
        l.name = "Quick play";
        int t = 50;
        List<double[]> occ = new ArrayList<>();   // {x, y, until tick}
        for (int i = 0; i < count; i++) {
            boolean slide = i % 5 == 4;
            t += 20 + r.nextInt(10) + (slide ? 10 : 0);
            int x = 0, y = 0;
            for (int tries = 0; tries < 60; tries++) {
                x = 120 + r.nextInt(760); y = 150 + r.nextInt(700);
                boolean ok = true;
                for (double[] o : occ) if (o[2] > t - 40 && Math.hypot((x - o[0]) * 1.7, y - o[1]) < 260) { ok = false; break; }
                if (ok) break;
            }
            if (slide) {
                int dur = 26 + r.nextInt(14), ex = x, ey = y;
                for (int tries = 0; tries < 30; tries++) {
                    double a = r.nextDouble() * Math.PI * 2, d = 200 + r.nextDouble() * 150;
                    ex = (int) (x + Math.cos(a) * d / 1.7); ey = (int) (y + Math.sin(a) * d);
                    if (ex > 100 && ex < 900 && ey > 130 && ey < 870) break;
                }
                ex = Math.max(100, Math.min(900, ex)); ey = Math.max(130, Math.min(870, ey));
                if (r.nextBoolean()) l.notes.add(new int[]{t, x, y, ex, ey, dur, (x + ex) / 2 + r.nextInt(240) - 120, (y + ey) / 2 + r.nextInt(240) - 120});   // curved
                else l.notes.add(new int[]{t, x, y, ex, ey, dur});
                occ.add(new double[]{x, y, t + dur}); occ.add(new double[]{ex, ey, t + dur});
                t += dur;
            } else {
                l.notes.add(new int[]{t, x, y});
                occ.add(new double[]{x, y, t});
            }
        }
        return l;
    }
}
