package dev.lumora.core.cosmetics.anim;

import java.util.Locale;

/**
 * Easing curves of keyframe animations, by the names GeckoLib and Blockbench write into {@code .animation.json}
 * ({@code "easing": "easeInOutSine"}, {@code "lerp_mode": "catmullrom"}).
 * <p>
 * Ported from GeckoLib 5 ({@code com.geckolib.animation.object.EasingType}, MIT License, Copyright (c) 2026 GeckoLib,
 * https://github.com/bernie-g/geckolib) without its Minecraft and fastutil types. The step function is from
 * Boris Chumichev's easing library (MIT License, Copyright (c) 2015 Boris Chumichev) as in GeckoLib. Both notices are
 * in docs/THIRD_PARTY_NOTICES.md.
 */
public enum Easing {
    LINEAR("linear"),
    STEP("step"),
    EASE_IN_SINE("easeinsine"),
    EASE_OUT_SINE("easeoutsine"),
    EASE_IN_OUT_SINE("easeinoutsine"),
    EASE_IN_QUAD("easeinquad"),
    EASE_OUT_QUAD("easeoutquad"),
    EASE_IN_OUT_QUAD("easeinoutquad"),
    EASE_IN_CUBIC("easeincubic"),
    EASE_OUT_CUBIC("easeoutcubic"),
    EASE_IN_OUT_CUBIC("easeinoutcubic"),
    EASE_IN_QUART("easeinquart"),
    EASE_OUT_QUART("easeoutquart"),
    EASE_IN_OUT_QUART("easeinoutquart"),
    EASE_IN_QUINT("easeinquint"),
    EASE_OUT_QUINT("easeoutquint"),
    EASE_IN_OUT_QUINT("easeinoutquint"),
    EASE_IN_EXPO("easeinexpo"),
    EASE_OUT_EXPO("easeoutexpo"),
    EASE_IN_OUT_EXPO("easeinoutexpo"),
    EASE_IN_CIRC("easeincirc"),
    EASE_OUT_CIRC("easeoutcirc"),
    EASE_IN_OUT_CIRC("easeinoutcirc"),
    EASE_IN_BACK("easeinback"),
    EASE_OUT_BACK("easeoutback"),
    EASE_IN_OUT_BACK("easeinoutback"),
    EASE_IN_ELASTIC("easeinelastic"),
    EASE_OUT_ELASTIC("easeoutelastic"),
    EASE_IN_OUT_ELASTIC("easeinoutelastic"),
    EASE_IN_BOUNCE("easeinbounce"),
    EASE_OUT_BOUNCE("easeoutbounce"),
    EASE_IN_OUT_BOUNCE("easeinoutbounce"),
    /** Smooth spline through the neighbouring keyframes (Blockbench "Catmull-Rom"). */
    CATMULLROM("catmullrom");

    private final String id;

    Easing(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** The easing with this name (any case); unknown names and {@code null} are linear, as in GeckoLib. */
    public static Easing of(String name) {
        if (name == null) {
            return LINEAR;
        }
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals("none")) {
            return LINEAR;
        }
        for (Easing e : values()) {
            if (e.id.equals(n)) {
                return e;
            }
        }
        return LINEAR;
    }

    /**
     * Value between {@code from} and {@code to} at {@code delta} (0..1). {@code arg} is the first easing argument
     * ({@code NaN} if none). Catmull-Rom with neighbours goes through {@link #catmullRom}.
     */
    public double interpolate(double delta, double from, double to, double arg) {
        if (delta >= 1) {
            return to;
        }
        if (this == LINEAR) {
            return from + (to - from) * delta;
        }
        return from + (to - from) * curve(delta, arg);
    }

    /** The eased progress for {@code t} in 0..1. */
    public double curve(double t, double arg) {
        boolean hasArg = !Double.isNaN(arg);
        return switch (this) {
            case LINEAR -> t;
            case STEP -> step(t, hasArg ? arg : 2);
            case EASE_IN_SINE -> sine(t);
            case EASE_OUT_SINE -> 1 - sine(1 - t);
            case EASE_IN_OUT_SINE -> inOut(t, Easing::sine);
            case EASE_IN_QUAD -> t * t;
            case EASE_OUT_QUAD -> 1 - (1 - t) * (1 - t);
            case EASE_IN_OUT_QUAD -> inOut(t, x -> x * x);
            case EASE_IN_CUBIC -> t * t * t;
            case EASE_OUT_CUBIC -> 1 - Math.pow(1 - t, 3);
            case EASE_IN_OUT_CUBIC -> inOut(t, x -> x * x * x);
            case EASE_IN_QUART -> Math.pow(t, 4);
            case EASE_OUT_QUART -> 1 - Math.pow(1 - t, 4);
            case EASE_IN_OUT_QUART -> inOut(t, x -> Math.pow(x, 4));
            case EASE_IN_QUINT -> Math.pow(t, 5);
            case EASE_OUT_QUINT -> 1 - Math.pow(1 - t, 5);
            case EASE_IN_OUT_QUINT -> inOut(t, x -> Math.pow(x, 5));
            case EASE_IN_EXPO -> exp(t);
            case EASE_OUT_EXPO -> 1 - exp(1 - t);
            case EASE_IN_OUT_EXPO -> inOut(t, Easing::exp);
            case EASE_IN_CIRC -> circle(t);
            case EASE_OUT_CIRC -> 1 - circle(1 - t);
            case EASE_IN_OUT_CIRC -> inOut(t, Easing::circle);
            case EASE_IN_BACK -> back(t, arg);
            case EASE_OUT_BACK -> 1 - back(1 - t, arg);
            case EASE_IN_OUT_BACK -> inOut(t, x -> back(x, arg));
            case EASE_IN_ELASTIC -> elastic(t, arg);
            case EASE_OUT_ELASTIC -> 1 - elastic(1 - t, arg);
            case EASE_IN_OUT_ELASTIC -> inOut(t, x -> elastic(x, arg));
            case EASE_IN_BOUNCE -> bounce(t, arg);
            case EASE_OUT_BOUNCE -> 1 - bounce(1 - t, arg);
            case EASE_IN_OUT_BOUNCE -> inOut(t, x -> bounce(x, arg));
            case CATMULLROM -> inOut(t, Easing::catmullRomCurve);
        };
    }

    /** Point on the Catmull-Rom spline between p1 and p2 with the neighbours p0 and p3 (GeckoLib's formula). */
    public static double catmullRom(double delta, double p0, double p1, double p2, double p3) {
        if (delta >= 1) {
            return p2;
        }
        return 0.5d * (2d * p1 + (p2 - p0) * delta
                + (2d * p0 - 5d * p1 + 4d * p2 - p3) * delta * delta
                + (3d * p1 - p0 - 3d * p2 + p3) * delta * delta * delta);
    }

    private interface Curve {
        double apply(double t);
    }

    private static double inOut(double t, Curve f) {
        if (t < 0.5d) {
            return f.apply(t * 2d) / 2d;
        }
        return 1 - f.apply((1 - t) * 2d) / 2d;
    }

    private static double catmullRomCurve(double n) {
        return 0.5d * (2d * (n + 1d) + 2d
                + (2d * n - 5d * (n + 1d) + 4d * (n + 2d) - (n + 3d))
                + (3d * (n + 1d) - n - 3d * (n + 2d) + (n + 3d)));
    }

    private static double sine(double n) {
        return 1 - Math.cos(n * Math.PI / 2f);
    }

    private static double circle(double n) {
        return 1 - Math.sqrt(Math.max(0, 1 - n * n));
    }

    private static double exp(double n) {
        return Math.pow(2, 10 * (n - 1));
    }

    private static double elastic(double t, double arg) {
        double n = Double.isNaN(arg) ? 1 : arg;
        return 1 - Math.pow(Math.cos(t * Math.PI / 2f), 3) * Math.cos(t * n * Math.PI);
    }

    private static double bounce(double t, double arg) {
        double n = Double.isNaN(arg) ? 0.5d : arg;
        double one = 121f / 16f * t * t;
        double two = 121f / 4f * n * Math.pow(t - 6f / 11f, 2) + 1 - n;
        double three = 121 * n * n * Math.pow(t - 9f / 11f, 2) + 1 - n * n;
        double four = 484 * n * n * n * Math.pow(t - 10.5f / 11f, 2) + 1 - n * n * n;
        return Math.min(Math.min(one, two), Math.min(three, four));
    }

    private static double back(double t, double arg) {
        double n = Double.isNaN(arg) ? 1.70158d : arg * 1.70158d;
        return t * t * ((n + 1) * t - n);
    }

    /*
     * The MIT License (MIT)
     *
     * Copyright (c) 2015 Boris Chumichev
     *
     * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
     * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
     * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
     * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
     *
     * The above copyright notice and this permission notice shall be included in all copies or substantial portions
     * of the Software.
     *
     * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
     * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
     * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
     * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
     * IN THE SOFTWARE.
     */
    private static double step(double t, double stepsArg) {
        int steps = Math.max(2, (int) stepsArg);
        double result = 0;
        if (t < 0) {
            return result;
        }
        double stepLength = 1 / (double) steps;
        if (t > (result = (steps - 1) * stepLength)) {
            return result;
        }
        int left = 0;
        int right = steps - 1;
        while (right - left != 1) {
            int test = left + (right - left) / 2;
            if (t >= test * stepLength) {
                left = test;
            } else {
                right = test;
            }
        }
        return left * stepLength;
    }
}
