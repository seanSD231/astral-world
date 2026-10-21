package com.astralspace;

import org.joml.Matrix4f;

/** Client-side motion for the sovereign meshes: a model-wide pulse, then each named part turns or sways about its pivot. t is in seconds. */
final class SovAnim {
    private SovAnim() {}

    private static void rot(Matrix4f m, double[] pv, float ax, float ay, float az, double angle) {
        m.translate((float) pv[0], (float) pv[1], (float) pv[2]).rotate((float) angle, ax, ay, az).translate((float) -pv[0], (float) -pv[1], (float) -pv[2]);
    }

    private static void pulse(Matrix4f m, double[] pv, double amount) {
        m.translate((float) pv[0], (float) pv[1], (float) pv[2]).scale((float) (1 + amount * .5), (float) (1 + amount), (float) (1 + amount * .5))
                .translate((float) -pv[0], (float) -pv[1], (float) -pv[2]);
    }

    private static final double[] SAT = {0, 7.2, 0}, NEP = {0, 13, 0}, MARS = {0, 6, 0}, JUP = {0, 9, 0}, VEN = {0, 5, 0};

    /** The whole model (breathing). */
    static void model(String planet, double t, Matrix4f m) {
        switch (planet) {
            case "mars" -> pulse(m, MARS, .025 * Math.sin(t * 1.3));
            case "jupiter" -> pulse(m, JUP, .035 * Math.sin(t * 1.7));
            case "venus" -> pulse(m, VEN, .012 * Math.sin(t * 1.0));
            default -> {}
        }
    }

    /** One named part of the model. */
    static void part(String planet, String name, double t, double[] pv, Matrix4f m) {
        switch (planet) {
            case "mercury" -> {
                if (name.equals("body")) rot(m, pv, 0, 0, 1, Math.sin(t * .7) * .03);
                else if (name.startsWith("world")) {
                    int i = name.charAt(5) - '0';
                    rot(m, pv, 0, 1, 0, Math.sin(t * .5 + i * 1.7) * .45);
                    m.translate(0, (float) (Math.sin(t * .8 + i * 1.3) * .6), 0);
                }
            }
            case "venus" -> {
                if (name.startsWith("wing")) {
                    int sz = name.endsWith("-1") ? -1 : 1;
                    double beat = Math.sin(t * 1.5);
                    rot(m, pv, 1, 0, 0, -sz * (.12 + .3 * beat));
                } else if (name.equals("head")) {
                    rot(m, pv, 0, 1, 0, Math.sin(t * .6) * .16);
                    rot(m, pv, 0, 0, 1, Math.sin(t * .9 + 1) * .07);
                } else if (name.equals("body")) rot(m, new double[]{0, 6, 0}, 1, 0, 0, Math.sin(t * .8) * .01);
            }
            case "mars" -> {
                if (name.startsWith("arm")) {
                    int sx = name.endsWith("-1") ? -1 : 1;
                    rot(m, pv, 0, 0, 1, sx * (.08 + .1 * Math.sin(t * 1.1 + (sx > 0 ? 0 : 1.6))));
                }
            }
            case "jupiter" -> {
                switch (name) {
                    case "core" -> pulse(m, pv, .09 * Math.sin(t * 3.1));
                    case "rings" -> rot(m, pv, 0, 1, 0, t * .5);
                    case "sparks" -> rot(m, pv, 0, 1, 0, t * .2);
                    default -> {}
                }
            }
            case "saturn" -> {
                switch (name) {
                    case "body" -> rot(m, SAT, 0, 0, 1, t * .18);
                    case "liquid" -> rot(m, SAT, 0, 0, 1, -t * .45);
                    case "lip" -> pulse(m, SAT, .02 * Math.sin(t * 3));
                    default -> {}
                }
            }
            case "uranus" -> {
                switch (name) {
                    case "wheel" -> rot(m, pv, 0, 0, 1, t * .12);
                    case "cloth" -> rot(m, pv, 0, 0, 1, Math.sin(t * .8) * .035);
                    case "blade" -> { m.translate(0, (float) (Math.sin(t * .7) * .5), 0); rot(m, pv, 0, 0, 1, Math.sin(t * .5) * .03); }
                    default -> {}
                }
            }
            case "neptune" -> {
                switch (name) {
                    case "body" -> rot(m, NEP, 0, 0, 1, t * .1);
                    case "rings" -> rot(m, NEP, 0, 1, 0, t * .9);
                    case "core" -> pulse(m, NEP, .08 * Math.sin(t * 2.4));
                    case "outer" -> pulse(m, NEP, .015 * Math.sin(t * 1.2));
                    default -> {}
                }
            }
            default -> {}
        }
    }
}
