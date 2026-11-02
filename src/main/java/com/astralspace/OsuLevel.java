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
    public static final int MAX_NOTES = 400, MAX_TICKS = 20 * 600, MAX_NAME = 32, MAX_DISC = 6000;
    public static final String PREFIX = "OSU1:";

    public String name = "Untitled", author = "", disc = "";
    public int bpm = 120;
    /** {tick, x, y}, kept in time order. */
    public final List<int[]> notes = new ArrayList<>();

    public void sort() { notes.sort((a, b) -> Integer.compare(a[0], b[0])); }

    public int lengthTicks() { return notes.isEmpty() ? 0 : notes.get(notes.size() - 1)[0]; }

    private static String clean(String s, int max) {
        s = s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    public String encode() {
        sort();
        StringBuilder sb = new StringBuilder();
        sb.append(clean(name, MAX_NAME)).append('\n').append(clean(author, 16)).append('\n').append(bpm).append('\n').append(clean(disc, MAX_DISC)).append('\n');
        for (int[] n : notes) sb.append(n[0]).append(',').append(n[1]).append(',').append(n[2]).append(';');
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
                l.notes.add(new int[]{t, Math.max(0, Math.min(1000, x)), Math.max(0, Math.min(1000, y))});
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

    /** A quick random level for the cabinet's "quick play". */
    public static OsuLevel random(long seed, int count) {
        java.util.Random r = new java.util.Random(seed);
        OsuLevel l = new OsuLevel();
        l.name = "Quick play";
        int t = 40;
        for (int i = 0; i < count; i++) {
            t += 8 + r.nextInt(8);
            l.notes.add(new int[]{t, 120 + r.nextInt(760), 150 + r.nextInt(700)});
        }
        return l;
    }
}
