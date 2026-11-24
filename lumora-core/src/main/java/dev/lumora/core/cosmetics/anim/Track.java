package dev.lumora.core.cosmetics.anim;

/**
 * Keyframes of one transform (rotation, position or scale) of one bone: times in seconds, three axis values per
 * keyframe (separate "pre" and "post" values for jumps), the easing into each keyframe and its first argument.
 * Values are Bedrock values as written in the file (degrees, pixels, factors).
 * <p>
 * Interpolation as in GeckoLib's {@code AnimationProcessor}: between keyframe i and i+1 the easing of keyframe i+1
 * goes from the post value of i to the pre value of i+1; before the first keyframe its value holds, after the last
 * one too. Catmull-Rom uses the neighbours of both keyframes.
 */
public final class Track {
    final double[] times;
    final Molang.Expr[][] pre;
    final Molang.Expr[][] post;
    final Easing[] easing;
    final Molang.Expr[] easingArg;

    Track(double[] times, Molang.Expr[][] pre, Molang.Expr[][] post, Easing[] easing, Molang.Expr[] easingArg) {
        this.times = times;
        this.pre = pre;
        this.post = post;
        this.easing = easing;
        this.easingArg = easingArg;
    }

    /** A track that always has these values. */
    public static Track constant(double x, double y, double z) {
        Molang.Expr[] v = {Molang.constant(x), Molang.constant(y), Molang.constant(z)};
        return new Track(new double[] {0}, new Molang.Expr[][] {v}, new Molang.Expr[][] {v}, new Easing[] {Easing.LINEAR},
                new Molang.Expr[] {null});
    }

    public int size() {
        return times.length;
    }

    /** Time of the last keyframe. */
    public double lastTime() {
        return times.length == 0 ? 0 : times[times.length - 1];
    }

    /** Writes the three values at {@code time} into {@code out[offset..offset+2]}. */
    public void sample(double time, Molang.Scope scope, float[] out, int offset) {
        int n = times.length;
        if (n == 0) {
            return;
        }
        if (n == 1 || time <= times[0]) {
            Molang.Expr[] v = time < times[0] || n == 1 ? pre[0] : post[0];
            for (int a = 0; a < 3; a++) {
                out[offset + a] = (float) v[a].eval(scope);
            }
            return;
        }
        if (time >= times[n - 1]) {
            for (int a = 0; a < 3; a++) {
                out[offset + a] = (float) post[n - 1][a].eval(scope);
            }
            return;
        }
        int i = 0;
        while (i < n - 2 && time >= times[i + 1]) {
            i++;
        }
        double span = times[i + 1] - times[i];
        double delta = span <= 0 ? 1 : (time - times[i]) / span;
        Easing e = easing[i + 1];
        double arg = easingArg[i + 1] == null ? Double.NaN : easingArg[i + 1].eval(scope);
        for (int a = 0; a < 3; a++) {
            double from = post[i][a].eval(scope);
            double to = pre[i + 1][a].eval(scope);
            double v;
            if (e == Easing.CATMULLROM) {
                double p0 = i > 0 ? post[i - 1][a].eval(scope) : from;
                double p3 = i + 2 < n ? pre[i + 2][a].eval(scope) : to;
                v = Easing.catmullRom(delta, p0, from, to, p3);
            } else {
                v = e.interpolate(delta, from, to, arg);
            }
            out[offset + a] = (float) v;
        }
    }
}
