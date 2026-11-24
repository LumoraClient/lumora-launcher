package dev.lumora.core.cosmetics;

import dev.lumora.core.cosmetics.anim.PoseBuffer;

/**
 * The emote's effect on one player for this frame, in the terms the version adapters need:
 * <ul>
 * <li>per model part (index = {@link AttachPoint#ordinal()}, 0..5) a rotation in radians in Minecraft's
 * {@code ModelPart} convention with a weight, and an offset in model pixels: adapters call
 * {@link #rotation(int, int, float)} and {@link #position(int, int, float)} with Minecraft's own value after
 * {@code setupAnim};</li>
 * <li>a whole-body transform ({@link #hasRoot()}) applied in the entity renderer after the body turn, in blocks and
 * radians: translate, then rotate Z, Y, X.</li>
 * </ul>
 * Rotations: Bedrock degrees map 1:1 to {@code ModelPart} radians (both spaces differ by a half turn around Z, which
 * leaves the Z-Y-X angles as they are); positions {@code (x, -y, z)}. The Bedrock rig carries head and arms on the
 * body, Minecraft does not: a body rotation is passed on to their pivots and angles here.
 */
public final class EmotePose {
    private static final int PARTS = 6;
    private final float[] rot = new float[PARTS * 3];
    private final float[] rotWeight = new float[PARTS];
    private final float[] pos = new float[PARTS * 3];
    private boolean active;
    private boolean root;
    private float rootX;
    private float rootY;
    private float rootZ;
    private float rootRotX;
    private float rootRotY;
    private float rootRotZ;

    public boolean active() {
        return active;
    }

    /** Minecraft's rotation {@code vanilla} (radians) of the part blended towards the emote. Axis 0..2 = x, y, z. */
    public float rotation(int part, int axis, float vanilla) {
        if (!active) {
            return vanilla;
        }
        float w = rotWeight[part];
        return w <= 0 ? vanilla : vanilla + (rot[part * 3 + axis] - vanilla) * w;
    }

    /** Minecraft's position {@code vanilla} (pixels) of the part plus the emote's offset. */
    public float position(int part, int axis, float vanilla) {
        return active ? vanilla + pos[part * 3 + axis] : vanilla;
    }

    /** {@code true} if the emote moves this part at all. */
    public boolean moves(int part) {
        return active && (rotWeight[part] > 0 || pos[part * 3] != 0 || pos[part * 3 + 1] != 0 || pos[part * 3 + 2] != 0);
    }

    public boolean hasRoot() {
        return active && root;
    }

    public float rootX() {
        return rootX;
    }

    public float rootY() {
        return rootY;
    }

    public float rootZ() {
        return rootZ;
    }

    public float rootRotX() {
        return rootRotX;
    }

    public float rootRotY() {
        return rootRotY;
    }

    public float rootRotZ() {
        return rootRotZ;
    }

    void clear() {
        active = false;
        root = false;
        java.util.Arrays.fill(rot, 0);
        java.util.Arrays.fill(rotWeight, 0);
        java.util.Arrays.fill(pos, 0);
        rootX = rootY = rootZ = rootRotX = rootRotY = rootRotZ = 0;
    }

    /** Fills this pose from an emote layer sampled into {@code buffer} (bones in {@link AttachPoint} order). */
    void from(PoseBuffer buffer) {
        clear();
        for (int p = 0; p < PARTS; p++) {
            float w = buffer.weight(p, PoseBuffer.ROT);
            if (w > 0) {
                rotWeight[p] = w;
                for (int a = 0; a < 3; a++) {
                    rot[p * 3 + a] = (float) Math.toRadians(buffer.value(p, PoseBuffer.ROT, a));
                }
                active = true;
            }
            float pw = buffer.weight(p, PoseBuffer.POS);
            if (pw > 0) {
                pos[p * 3] = buffer.value(p, PoseBuffer.POS, 0) * pw;
                pos[p * 3 + 1] = -buffer.value(p, PoseBuffer.POS, 1) * pw;
                pos[p * 3 + 2] = buffer.value(p, PoseBuffer.POS, 2) * pw;
                active = true;
            }
        }
        carryOnBody();
        int r = AttachPoint.ROOT.ordinal();
        float rw = buffer.weight(r, PoseBuffer.ROT);
        float pw = buffer.weight(r, PoseBuffer.POS);
        if (rw > 0 || pw > 0) {
            root = true;
            active = true;
            // GeckoLib space at the feet: translate (-x, y, z) / 16, rotations (-x, -y, z)
            rootX = -buffer.value(r, PoseBuffer.POS, 0) * pw / 16f;
            rootY = buffer.value(r, PoseBuffer.POS, 1) * pw / 16f;
            rootZ = buffer.value(r, PoseBuffer.POS, 2) * pw / 16f;
            rootRotX = (float) Math.toRadians(-buffer.value(r, PoseBuffer.ROT, 0) * rw);
            rootRotY = (float) Math.toRadians(-buffer.value(r, PoseBuffer.ROT, 1) * rw);
            rootRotZ = (float) Math.toRadians(buffer.value(r, PoseBuffer.ROT, 2) * rw);
        }
    }

    /** Hip (waist) in Minecraft model pixels: the body bends here, like the "waist" bone of the Bedrock rig. */
    private static final float HIP_Y = 12;

    /**
     * The body bends at the hip and carries head and arms, as in the Bedrock player rig: the body part (whose
     * Minecraft pivot is the neck) is moved so the hip stays in place, and the pivots of head and arms turn with it.
     */
    private void carryOnBody() {
        int b = AttachPoint.BODY.ordinal();
        float w = rotWeight[b];
        if (w <= 0) {
            return;
        }
        float bx = rot[b * 3] * w;
        float by = rot[b * 3 + 1] * w;
        float bz = rot[b * 3 + 2] * w;
        if (bx == 0 && by == 0 && bz == 0) {
            return;
        }
        float[] hip = rotateZYX(0, HIP_Y, 0, bz, by, bx);
        pos[b * 3] += -hip[0];
        pos[b * 3 + 1] += HIP_Y - hip[1];
        pos[b * 3 + 2] += -hip[2];
        for (AttachPoint part : new AttachPoint[] {AttachPoint.HEAD, AttachPoint.RIGHT_ARM, AttachPoint.LEFT_ARM}) {
            int p = part.ordinal();
            // rest pivot in Minecraft model pixels relative to the hip
            float x = part.pivotX;
            float y = 24 - part.pivotY - HIP_Y;
            float z = part.pivotZ;
            float[] r = rotateZYX(x, y, z, bz, by, bx);
            pos[p * 3] += r[0] - x;
            pos[p * 3 + 1] += r[1] - y;
            pos[p * 3 + 2] += r[2] - z;
            // the part's own angles: emote value (weighted) on top of the body's
            float pw = rotWeight[p];
            for (int a = 0; a < 3; a++) {
                float own = rot[p * 3 + a] * pw;
                float body = a == 0 ? bx : a == 1 ? by : bz;
                rot[p * 3 + a] = own + body;
            }
            // the new value is already weighted: the vanilla angle is kept only where neither moves the part
            rotWeight[p] = Math.max(pw, w);
            if (rotWeight[p] > 0) {
                for (int a = 0; a < 3; a++) {
                    rot[p * 3 + a] /= rotWeight[p];
                }
            }
        }
    }

    private static float[] rotateZYX(float x, float y, float z, float rz, float ry, float rx) {
        // R = Rz * Ry * Rx applied to the point
        float cx = (float) Math.cos(rx);
        float sx = (float) Math.sin(rx);
        float y1 = y * cx - z * sx;
        float z1 = y * sx + z * cx;
        float cy = (float) Math.cos(ry);
        float sy = (float) Math.sin(ry);
        float x2 = x * cy + z1 * sy;
        float z2 = -x * sy + z1 * cy;
        float cz = (float) Math.cos(rz);
        float sz = (float) Math.sin(rz);
        return new float[] {x2 * cz - y1 * sz, x2 * sz + y1 * cz, z2};
    }
}
