package dev.lumora.core.cosmetics.model;

/** A small 4x4 float matrix (column-major, like JOML) for baking bone transforms without Minecraft classes. */
final class Mat4 {
    final float[] m = new float[16];
    private final float[] scratch = new float[16];
    private final float[] rot = new float[16];

    Mat4() {
        identity();
    }

    Mat4 identity() {
        java.util.Arrays.fill(m, 0);
        m[0] = m[5] = m[10] = m[15] = 1;
        return this;
    }

    Mat4 set(Mat4 o) {
        System.arraycopy(o.m, 0, m, 0, 16);
        return this;
    }

    /** this = this * other */
    Mat4 mul(float[] o) {
        float[] r = scratch;
        for (int c = 0; c < 4; c++) {
            for (int row = 0; row < 4; row++) {
                r[c * 4 + row] = m[row] * o[c * 4] + m[4 + row] * o[c * 4 + 1] + m[8 + row] * o[c * 4 + 2] + m[12 + row] * o[c * 4 + 3];
            }
        }
        System.arraycopy(r, 0, m, 0, 16);
        return this;
    }

    Mat4 translate(float x, float y, float z) {
        if (x == 0 && y == 0 && z == 0) {
            return this;
        }
        m[12] += m[0] * x + m[4] * y + m[8] * z;
        m[13] += m[1] * x + m[5] * y + m[9] * z;
        m[14] += m[2] * x + m[6] * y + m[10] * z;
        m[15] += m[3] * x + m[7] * y + m[11] * z;
        return this;
    }

    Mat4 scale(float x, float y, float z) {
        if (x == 1 && y == 1 && z == 1) {
            return this;
        }
        for (int i = 0; i < 4; i++) {
            m[i] *= x;
            m[4 + i] *= y;
            m[8 + i] *= z;
        }
        return this;
    }

    /** Rotation Z, then Y, then X (radians), as {@code Quaternionf.rotationZYX} and Minecraft's ModelPart. */
    Mat4 rotateZYX(float z, float y, float x) {
        if (z != 0) {
            rot(2, z);
        }
        if (y != 0) {
            rot(1, y);
        }
        if (x != 0) {
            rot(0, x);
        }
        return this;
    }

    private void rot(int axis, float angle) {
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        float[] r = rot;
        java.util.Arrays.fill(r, 0);
        r[0] = r[5] = r[10] = r[15] = 1;
        switch (axis) {
            case 0 -> {
                r[5] = c;
                r[6] = s;
                r[9] = -s;
                r[10] = c;
            }
            case 1 -> {
                r[0] = c;
                r[2] = -s;
                r[8] = s;
                r[10] = c;
            }
            default -> {
                r[0] = c;
                r[1] = s;
                r[4] = -s;
                r[5] = c;
            }
        }
        mul(r);
    }

    float px(float x, float y, float z) {
        return m[0] * x + m[4] * y + m[8] * z + m[12];
    }

    float py(float x, float y, float z) {
        return m[1] * x + m[5] * y + m[9] * z + m[13];
    }

    float pz(float x, float y, float z) {
        return m[2] * x + m[6] * y + m[10] * z + m[14];
    }

    float nx(float x, float y, float z) {
        return m[0] * x + m[4] * y + m[8] * z;
    }

    float ny(float x, float y, float z) {
        return m[1] * x + m[5] * y + m[9] * z;
    }

    float nz(float x, float y, float z) {
        return m[2] * x + m[6] * y + m[10] * z;
    }
}
