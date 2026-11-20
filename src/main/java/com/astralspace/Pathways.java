package com.astralspace;

/**
 * The colour of each pathway, in one place.
 *
 * <p>The binder has carried its own copy of this since before anything else needed it. Other work
 * want the same colours, and a second hand-kept list would drift from the first, so new work reads
 * them from here.</p>
 */
public final class Pathways {
    private Pathways() { }

    public static final int GOLD = 0xFFE2BA60;

    /** ARGB for one of the twenty-two pathway ids, gold for anything not among them. */
    public static int colour(String pathway) {
        return switch (pathway) {
            case "spectator" -> 0xFF55FFFF;
            case "bard" -> 0xFFFFFF55;
            case "warrior" -> 0xFFFF8A1E;
            case "sailor" -> 0xFF5555FF;
            case "apprentice" -> 0xFF6FA2FF;
            case "marauder" -> 0xFF9B82B8;
            case "mystery_pryer", "prisoner" -> 0xFFAA00AA;
            case "seer" -> 0xFFC566E6;
            case "secrets_supplicant", "hunter", "criminal" -> 0xFFAA0000;
            case "assassin" -> 0xFFFF55FF;
            case "corpse_collector", "planter" -> 0xFF55FF55;
            case "apothecary" -> 0xFF00AA00;
            case "lawyer" -> 0xFF2A2ACC;
            case "savant", "arbiter" -> 0xFFFFAA00;
            case "reader" -> 0xFFFFFFFF;
            case "monster" -> 0xFFAAAAAA;
            case "sleepless" -> 0xFF777777;
            default -> GOLD;
        };
    }

    /** The same colour with a given alpha, for washes and glows. */
    public static int alpha(int argb, float a) {
        return (((int) (Math.max(0, Math.min(1, a)) * 255)) << 24) | (argb & 0xFFFFFF);
    }

    /** Toward black by k, for the deep side of a gradient. */
    public static int deep(int argb, float k) {
        int r = (int) (((argb >> 16) & 0xFF) * k), g = (int) (((argb >> 8) & 0xFF) * k), b = (int) ((argb & 0xFF) * k);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Toward white by k. */
    public static int pale(int argb, float k) {
        int r = (int) (((argb >> 16) & 0xFF) + (255 - ((argb >> 16) & 0xFF)) * k);
        int g = (int) (((argb >> 8) & 0xFF) + (255 - ((argb >> 8) & 0xFF)) * k);
        int b = (int) ((argb & 0xFF) + (255 - (argb & 0xFF)) * k);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
