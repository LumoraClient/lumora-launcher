package dev.lumora.core.cosmetics.anim;

import java.util.Arrays;

/**
 * Sampled transforms of a fixed list of bones, refilled every frame: per bone rotation (degrees), position (pixels)
 * and scale as Bedrock values, each with a weight 0..1 (0 = the animation does not touch it, 1 = fully animated).
 * Consumers blend from their rest/vanilla value towards the value by the weight.
 */
public final class PoseBuffer {
    public static final int ROT = 0;
    public static final int POS = 3;
    public static final int SCALE = 6;
    private static final int STRIDE = 9;

    private final String[] bones;
    private final float[] values;
    private final float[] weights;

    public PoseBuffer(String... bones) {
        this.bones = bones.clone();
        this.values = new float[bones.length * STRIDE];
        this.weights = new float[bones.length * 3];
        clear();
    }

    public int size() {
        return bones.length;
    }

    public String bone(int index) {
        return bones[index];
    }

    /** Index of the bone (exact or ignoring case/underscores), -1 if not in this buffer. */
    public int indexOf(String bone) {
        for (int i = 0; i < bones.length; i++) {
            if (bones[i].equals(bone)) {
                return i;
            }
        }
        String n = Animation.normalize(bone);
        for (int i = 0; i < bones.length; i++) {
            if (Animation.normalize(bones[i]).equals(n)) {
                return i;
            }
        }
        return -1;
    }

    /** Rest values (rotation 0, position 0, scale 1) and no weights. */
    public void clear() {
        Arrays.fill(values, 0);
        Arrays.fill(weights, 0);
        for (int i = 0; i < bones.length; i++) {
            values[i * STRIDE + SCALE] = 1;
            values[i * STRIDE + SCALE + 1] = 1;
            values[i * STRIDE + SCALE + 2] = 1;
        }
    }

    /** Value {@code axis} (0..2) of the channel {@link #ROT}, {@link #POS} or {@link #SCALE}. */
    public float value(int bone, int channel, int axis) {
        return values[bone * STRIDE + channel + axis];
    }

    /** Weight of the channel ({@link #ROT}, {@link #POS} or {@link #SCALE}). */
    public float weight(int bone, int channel) {
        return weights[bone * 3 + channel / 3];
    }

    /** Value blended from the rest value by the weight (what a cosmetic bone without vanilla motion shows). */
    public float blended(int bone, int channel, int axis) {
        float rest = channel == SCALE ? 1 : 0;
        float w = weight(bone, channel);
        return rest + (value(bone, channel, axis) - rest) * w;
    }

    void set(int bone, int channel, float x, float y, float z, float weight) {
        int o = bone * STRIDE + channel;
        values[o] = x;
        values[o + 1] = y;
        values[o + 2] = z;
        weights[bone * 3 + channel / 3] = weight;
    }

    /**
     * Lays this layer over what is already there: the existing value is blended towards {@code (x, y, z)} by
     * {@code weight}, and the weights combine like stacked layers ({@code 1 - (1 - a)(1 - b)}).
     */
    void layer(int bone, int channel, float x, float y, float z, float weight) {
        if (weight <= 0) {
            return;
        }
        int o = bone * STRIDE + channel;
        int wi = bone * 3 + channel / 3;
        float old = weights[wi];
        float total = 1 - (1 - old) * (1 - weight);
        // existing contribution keeps old * (1 - weight), the new one weight; normalised to the total
        float keep = old * (1 - weight) / total;
        float add = weight / total;
        values[o] = values[o] * keep + x * add;
        values[o + 1] = values[o + 1] * keep + y * add;
        values[o + 2] = values[o + 2] * keep + z * add;
        weights[wi] = total;
    }

    /** Adds on top (for concurrent "overlay" controllers): rotation and position add, scale multiplies. */
    void add(int bone, int channel, float x, float y, float z, float weight) {
        if (weight <= 0) {
            return;
        }
        int o = bone * STRIDE + channel;
        int wi = bone * 3 + channel / 3;
        if (channel == SCALE) {
            float bx = blended(bone, channel, 0);
            float by = blended(bone, channel, 1);
            float bz = blended(bone, channel, 2);
            values[o] = bx * (1 + (x - 1) * weight);
            values[o + 1] = by * (1 + (y - 1) * weight);
            values[o + 2] = bz * (1 + (z - 1) * weight);
        } else {
            values[o] = blended(bone, channel, 0) + x * weight;
            values[o + 1] = blended(bone, channel, 1) + y * weight;
            values[o + 2] = blended(bone, channel, 2) + z * weight;
        }
        weights[wi] = 1;
    }
}
