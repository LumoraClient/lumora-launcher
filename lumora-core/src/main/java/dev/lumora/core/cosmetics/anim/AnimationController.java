package dev.lumora.core.cosmetics.anim;

import java.util.function.Consumer;

/**
 * Plays one animation at a time with smooth transitions, like a GeckoLib {@code AnimationController}: switching
 * cross-fades from the old animation for the transition time, stopping fades back to the rest pose, an animation
 * played once fades out by itself at its end. Several controllers can run at the same time on the same bones
 * (each writes with {@link #sample} in priority order, or {@link #sampleAdditive} on top).
 * <p>
 * Times are seconds on any steady clock (the caller passes "now"), so a start time from another player could be
 * used later for synchronised playback.
 */
public final class AnimationController {
    private final Molang.Scope scope;
    private Animation current;
    private double currentStart;
    private Animation previous;
    private double previousStart;
    private double transitionStart = Double.NEGATIVE_INFINITY;
    private double transitionLength;
    private int nextEvent;
    private final float[] tmp = new float[9];
    private final float[] tmpPrev = new float[9];

    public AnimationController(Molang.Scope scope) {
        this.scope = scope;
    }

    public Animation current() {
        return current;
    }

    /** Start time of the current animation. */
    public double currentStart() {
        return currentStart;
    }

    /** {@code true} while an animation plays or one still fades out. */
    public boolean active(double now) {
        return current != null || previous != null && now - transitionStart < transitionLength;
    }

    /**
     * Plays {@code animation} from {@code start} (normally now), cross-fading for {@code transition} seconds. The same
     * animation that is still running keeps going unless {@code restart}.
     */
    public void play(Animation animation, double start, double transition, boolean restart) {
        if (animation == null) {
            stop(start, transition);
            return;
        }
        if (animation == current && !restart && !current.finished(start - currentStart)) {
            return;
        }
        previous = current;
        previousStart = currentStart;
        current = animation;
        currentStart = start;
        transitionStart = start;
        transitionLength = Math.max(0, transition);
        nextEvent = 0;
    }

    /** Fades back to the rest pose over {@code transition} seconds. */
    public void stop(double now, double transition) {
        if (current == null) {
            return;
        }
        previous = current;
        previousStart = currentStart;
        current = null;
        transitionStart = now;
        transitionLength = Math.max(0, transition);
    }

    /** Stops at once without a transition. */
    public void reset() {
        current = null;
        previous = null;
        transitionStart = Double.NEGATIVE_INFINITY;
    }

    /** Ends a once-played animation that reached its end (fading out); calls {@code events} for passed keyframes. */
    public void update(double now, double fadeOut, Consumer<Animation.Event> events) {
        if (current == null) {
            return;
        }
        double elapsed = now - currentStart;
        if (events != null && !current.events().isEmpty()) {
            if (current.loop() == Animation.Loop.LOOP && current.length() > 0) {
                int lap = (int) Math.floor(elapsed / current.length());
                double t = elapsed - lap * current.length();
                if (nextEvent > 0 && t < current.events().get(nextEvent - 1).time()) {
                    nextEvent = 0; // new lap
                }
                fire(t, events);
            } else {
                fire(elapsed, events);
            }
        }
        if (current.finished(elapsed)) {
            stop(now, fadeOut);
        }
    }

    private void fire(double t, Consumer<Animation.Event> events) {
        while (nextEvent < current.events().size() && current.events().get(nextEvent).time() <= t) {
            events.accept(current.events().get(nextEvent++));
        }
    }

    private double fade(double now) {
        if (transitionLength <= 0) {
            return 1;
        }
        double t = (now - transitionStart) / transitionLength;
        return t >= 1 ? 1 : Math.max(0, t);
    }

    /**
     * Writes this controller's bones into {@code out}, layered over what earlier (lower priority) controllers wrote,
     * scaled by {@code weight}.
     */
    public void sample(double now, PoseBuffer out, float weight) {
        double f = fade(now);
        if (f >= 1) {
            previous = null;
        }
        if (current == null && previous == null) {
            return;
        }
        for (int b = 0; b < out.size(); b++) {
            String bone = out.bone(b);
            int ci = current == null ? -1 : current.boneIndex(bone);
            int pi = previous == null ? -1 : previous.boneIndex(bone);
            if (ci < 0 && pi < 0) {
                continue;
            }
            for (int ch = 0; ch < 3; ch++) {
                int channel = ch * 3;
                boolean hasC = ci >= 0 && sampleChannel(current, ci, now - currentStart, channel, tmp);
                boolean hasP = pi >= 0 && sampleChannel(previous, pi, now - previousStart, channel, tmpPrev);
                float w;
                if (hasC && hasP) {
                    for (int a = 0; a < 3; a++) {
                        tmp[a] = (float) (tmpPrev[a] + (tmp[a] - tmpPrev[a]) * f);
                    }
                    w = 1;
                } else if (hasC) {
                    w = (float) f;
                } else if (hasP) {
                    System.arraycopy(tmpPrev, 0, tmp, 0, 3);
                    w = (float) (1 - f);
                } else {
                    continue;
                }
                out.layer(b, channel, tmp[0], tmp[1], tmp[2], w * weight);
            }
        }
    }

    /** Like {@link #sample} but adds on top of the existing values (concurrent overlay animations). */
    public void sampleAdditive(double now, PoseBuffer out, float weight) {
        double f = fade(now);
        if (f >= 1) {
            previous = null;
        }
        Animation a = current != null ? current : previous;
        if (a == null) {
            return;
        }
        double start = current != null ? currentStart : previousStart;
        float w = (float) (current != null ? f : 1 - f) * weight;
        for (int b = 0; b < out.size(); b++) {
            int i = a.boneIndex(out.bone(b));
            if (i < 0) {
                continue;
            }
            for (int ch = 0; ch < 3; ch++) {
                if (sampleChannel(a, i, now - start, ch * 3, tmp)) {
                    out.add(b, ch * 3, tmp[0], tmp[1], tmp[2], w);
                }
            }
        }
    }

    private boolean sampleChannel(Animation a, int bone, double elapsed, int channel, float[] into) {
        Animation.BoneTrack t = a.bone(bone);
        Track track = channel == PoseBuffer.ROT ? t.rotation() : channel == PoseBuffer.POS ? t.position() : t.scale();
        if (track == null) {
            return false;
        }
        double time = a.timeAt(elapsed);
        scope.set(Molang.Query.ANIM_TIME, time);
        track.sample(time, scope, into, 0);
        return true;
    }
}
