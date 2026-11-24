package dev.lumora.core.cosmetics.anim;

import java.util.List;
import java.util.Map;

/**
 * One animation of an {@code .animation.json} file (GeckoLib/Bedrock format): length, loop mode, keyframes per bone
 * and timed events (sound, particle and instruction keyframes).
 */
public final class Animation {
    /** What happens at the end. */
    public enum Loop {
        /** Plays once and ends. */
        PLAY_ONCE,
        /** Starts again. */
        LOOP,
        /** Stays on the last frame until stopped. */
        HOLD_ON_LAST_FRAME
    }

    /** Keyframes of one bone; a missing transform is {@code null}. */
    public record BoneTrack(Track rotation, Track position, Track scale) {
    }

    /** A timed event: {@code kind} sound, particle or timeline; {@code value} the effect name or instruction. */
    public record Event(double time, String kind, String value) {
    }

    private final String name;
    private final double length;
    private final Loop loop;
    private final String[] boneNames;
    private final BoneTrack[] bones;
    private final List<Event> events;

    public Animation(String name, double length, Loop loop, Map<String, BoneTrack> bones, List<Event> events) {
        this.name = name;
        this.length = length;
        this.loop = loop;
        this.boneNames = bones.keySet().toArray(new String[0]);
        this.bones = new BoneTrack[boneNames.length];
        for (int i = 0; i < boneNames.length; i++) {
            this.bones[i] = bones.get(boneNames[i]);
        }
        this.events = List.copyOf(events);
    }

    public String name() {
        return name;
    }

    /** Length in seconds. */
    public double length() {
        return length;
    }

    public Loop loop() {
        return loop;
    }

    public List<Event> events() {
        return events;
    }

    public int boneCount() {
        return boneNames.length;
    }

    public String boneName(int index) {
        return boneNames[index];
    }

    public BoneTrack bone(int index) {
        return bones[index];
    }

    /** Index of the bone (exact name, then ignoring case and underscores), -1 if this animation does not move it. */
    public int boneIndex(String bone) {
        for (int i = 0; i < boneNames.length; i++) {
            if (boneNames[i].equals(bone)) {
                return i;
            }
        }
        String want = normalize(bone);
        for (int i = 0; i < boneNames.length; i++) {
            if (normalize(boneNames[i]).equals(want)) {
                return i;
            }
        }
        return -1;
    }

    static String normalize(String bone) {
        return bone.replace("_", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** Time inside the animation after {@code elapsed} seconds (wrapped for loops, clamped otherwise). */
    public double timeAt(double elapsed) {
        if (elapsed <= 0) {
            return 0;
        }
        if (loop == Loop.LOOP) {
            return length <= 0 ? 0 : elapsed % length;
        }
        return Math.min(elapsed, length);
    }

    /** {@code true} if an animation played once has reached its end after {@code elapsed} seconds. */
    public boolean finished(double elapsed) {
        return loop == Loop.PLAY_ONCE && elapsed >= length;
    }
}
