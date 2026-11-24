package dev.lumora.core.cosmetics.model;

import dev.lumora.core.cosmetics.AttachPoint;
import dev.lumora.core.cosmetics.anim.PoseBuffer;

/**
 * Turns a {@link GeoModel} and the animated bone values into a {@link CosmeticMesh}.
 * <p>
 * Bone order of operations as GeckoLib's {@code RenderUtil.prepMatrixForBone}: animated position, to the pivot,
 * rotation Z-Y-X (base + animated), animated scale, back from the pivot; children inherit the parent's matrix.
 * Animated rotations are Bedrock degrees (X and Y mirrored into GeckoLib space as GeckoLib does when loading),
 * animated positions are Bedrock pixels ({@code -x} in GeckoLib space).
 * <p>
 * GeckoLib space (x mirrored, y up) and Minecraft's model space (y down, x not mirrored against Bedrock) differ by
 * a half turn around Z: {@code (x, y, z) -> (-x, -y, z)}. A point relative to the part pivot {@code a} becomes
 * {@code (-(x - ax), -(y - ay), z - az) / 16}. That is a proper rotation, so faces keep their winding.
 */
public final class MeshBuilder {
    private final GeoModel model;
    private final Mat4[] matrices;
    private final CosmeticMesh mesh;

    public MeshBuilder(GeoModel model) {
        this.model = model;
        this.matrices = new Mat4[model.bones.length];
        for (int i = 0; i < matrices.length; i++) {
            matrices[i] = new Mat4();
        }
        this.mesh = new CosmeticMesh(model.quadsPerAttach());
    }

    public GeoModel model() {
        return model;
    }

    /**
     * Builds the mesh for this frame. {@code pose} holds the animated values of the model's bones in model order
     * (as made with {@link GeoModel#boneNames()}); {@code null} draws the rest pose.
     */
    public CosmeticMesh build(PoseBuffer pose) {
        java.util.Arrays.fill(mesh.vertices, 0);
        for (int i = 0; i < model.bones.length; i++) {
            GeoModel.Bone b = model.bones[i];
            Mat4 m = matrices[i];
            if (b.parent >= 0) {
                m.set(matrices[b.parent]);
            } else {
                m.identity();
            }
            float tx = 0;
            float ty = 0;
            float tz = 0;
            float rx = 0;
            float ry = 0;
            float rz = 0;
            float sx = 1;
            float sy = 1;
            float sz = 1;
            if (pose != null) {
                tx = -pose.blended(i, PoseBuffer.POS, 0);
                ty = pose.blended(i, PoseBuffer.POS, 1);
                tz = pose.blended(i, PoseBuffer.POS, 2);
                rx = (float) Math.toRadians(-pose.blended(i, PoseBuffer.ROT, 0));
                ry = (float) Math.toRadians(-pose.blended(i, PoseBuffer.ROT, 1));
                rz = (float) Math.toRadians(pose.blended(i, PoseBuffer.ROT, 2));
                sx = pose.blended(i, PoseBuffer.SCALE, 0);
                sy = pose.blended(i, PoseBuffer.SCALE, 1);
                sz = pose.blended(i, PoseBuffer.SCALE, 2);
            }
            m.translate(tx, ty, tz)
                    .translate(b.pivotX, b.pivotY, b.pivotZ)
                    .rotateZYX(b.rotZ + rz, b.rotY + ry, b.rotX + rx)
                    .scale(sx, sy, sz)
                    .translate(-b.pivotX, -b.pivotY, -b.pivotZ);
            if (b.quadCount > 0 && (sx != 0 || sy != 0 || sz != 0)) {
                emit(b, m);
            }
        }
        return mesh;
    }

    private void emit(GeoModel.Bone b, Mat4 m) {
        AttachPoint a = b.attach;
        int slot = a.ordinal();
        float[] out = mesh.data[slot];
        int v = mesh.vertices[slot];
        // the part pivot in GeckoLib space
        float ax = -a.pivotX;
        float ay = a.pivotY;
        float az = a.pivotZ;
        float[] q = b.quads;
        for (int quad = 0; quad < b.quadCount; quad++) {
            int base = quad * GeoModel.QUAD;
            float nx = m.nx(q[base + 20], q[base + 21], q[base + 22]);
            float ny = m.ny(q[base + 20], q[base + 21], q[base + 22]);
            float nz = m.nz(q[base + 20], q[base + 21], q[base + 22]);
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-6f) {
                nx /= len;
                ny /= len;
                nz /= len;
            }
            for (int i = 0; i < 4; i++) {
                int p = base + i * 5;
                float x = m.px(q[p], q[p + 1], q[p + 2]);
                float y = m.py(q[p], q[p + 1], q[p + 2]);
                float z = m.pz(q[p], q[p + 1], q[p + 2]);
                int o = v * CosmeticMesh.STRIDE;
                out[o] = -(x - ax) / 16f;
                out[o + 1] = -(y - ay) / 16f;
                out[o + 2] = (z - az) / 16f;
                out[o + 3] = q[p + 3];
                out[o + 4] = q[p + 4];
                out[o + 5] = -nx;
                out[o + 6] = -ny;
                out[o + 7] = nz;
                v++;
            }
        }
        mesh.vertices[slot] = v;
    }
}
