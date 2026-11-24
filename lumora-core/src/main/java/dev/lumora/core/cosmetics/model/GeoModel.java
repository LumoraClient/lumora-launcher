package dev.lumora.core.cosmetics.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lumora.core.cosmetics.AttachPoint;
import dev.lumora.core.io.Json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A baked {@code .geo.json} model (Blockbench "Bedrock" / "GeckoLib Animated Model", format 1.12+ and the old
 * 1.8 format): bones with pivots and rotations, cubes as textured quads.
 * <p>
 * The baking follows GeckoLib 5 ({@code GeometryBone.bake}, {@code GeometryCube.bake}, {@code VertexSet},
 * {@code GeometryQuadUvs}; MIT, see {@code dev.lumora.core.cosmetics.anim.Easing}): positions are kept in GeckoLib's
 * space (pixels, x mirrored against Bedrock, y up); cube rotations are baked into the vertices. {@link MeshBuilder}
 * applies bone animations and turns the result into Minecraft model space.
 */
public final class GeoModel {
    /** Floats per quad: 4 vertices with x, y, z, u, v, then the normal. */
    static final int QUAD = 4 * 5 + 3;

    final int textureWidth;
    final int textureHeight;
    final Bone[] bones;

    /** One bone in GeckoLib space. Bones are ordered so a parent comes before its children. */
    static final class Bone {
        final String name;
        final int parent;
        final float pivotX;
        final float pivotY;
        final float pivotZ;
        final float rotX;
        final float rotY;
        final float rotZ;
        final AttachPoint attach;
        final float[] quads;
        final int quadCount;

        Bone(String name, int parent, float pivotX, float pivotY, float pivotZ, float rotX, float rotY, float rotZ,
             AttachPoint attach, float[] quads, int quadCount) {
            this.name = name;
            this.parent = parent;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
            this.rotX = rotX;
            this.rotY = rotY;
            this.rotZ = rotZ;
            this.attach = attach;
            this.quads = quads;
            this.quadCount = quadCount;
        }
    }

    private GeoModel(int textureWidth, int textureHeight, Bone[] bones) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.bones = bones;
    }

    public int textureWidth() {
        return textureWidth;
    }

    public int textureHeight() {
        return textureHeight;
    }

    public int boneCount() {
        return bones.length;
    }

    public String boneName(int index) {
        return bones[index].name;
    }

    /** Names of all bones (for a {@link dev.lumora.core.cosmetics.anim.PoseBuffer}). */
    public String[] boneNames() {
        String[] n = new String[bones.length];
        for (int i = 0; i < n.length; i++) {
            n[i] = bones[i].name;
        }
        return n;
    }

    /** Number of quads attached to each point (index = {@link AttachPoint#ordinal()}). */
    public int[] quadsPerAttach() {
        int[] q = new int[AttachPoint.values().length];
        for (Bone b : bones) {
            q[b.attach.ordinal()] += b.quadCount;
        }
        return q;
    }

    /**
     * Reads the first geometry of a {@code .geo.json} file.
     *
     * @param defaultAttach where top-level bones without a player part name go
     * @throws IllegalArgumentException if it is not a geometry file
     */
    public static GeoModel parse(String json, AttachPoint defaultAttach) {
        JsonElement root;
        try {
            root = Json.parse(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("not JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isJsonObject()) {
            throw new IllegalArgumentException("not a geometry file");
        }
        JsonObject o = root.getAsJsonObject();
        JsonObject geo = null;
        int tw = 64;
        int th = 64;
        if (o.has("minecraft:geometry") && o.get("minecraft:geometry").isJsonArray()
                && o.getAsJsonArray("minecraft:geometry").size() > 0) {
            geo = o.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            if (geo.has("description") && geo.get("description").isJsonObject()) {
                JsonObject d = geo.getAsJsonObject("description");
                tw = d.has("texture_width") ? d.get("texture_width").getAsInt() : tw;
                th = d.has("texture_height") ? d.get("texture_height").getAsInt() : th;
            }
        } else {
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (e.getKey().startsWith("geometry.") && e.getValue().isJsonObject()) {
                    geo = e.getValue().getAsJsonObject();
                    tw = geo.has("texturewidth") ? geo.get("texturewidth").getAsInt() : tw;
                    th = geo.has("textureheight") ? geo.get("textureheight").getAsInt() : th;
                    break;
                }
            }
        }
        if (geo == null) {
            throw new IllegalArgumentException("no geometry");
        }
        if (tw <= 0 || th <= 0) {
            throw new IllegalArgumentException("bad texture size");
        }
        List<JsonObject> raw = new ArrayList<>();
        if (geo.has("bones") && geo.get("bones").isJsonArray()) {
            for (JsonElement b : geo.getAsJsonArray("bones")) {
                if (b.isJsonObject() && b.getAsJsonObject().has("name")) {
                    raw.add(b.getAsJsonObject());
                }
            }
        }
        return bake(raw, tw, th, defaultAttach);
    }

    private static GeoModel bake(List<JsonObject> raw, int tw, int th, AttachPoint defaultAttach) {
        Map<String, JsonObject> byName = new HashMap<>();
        for (JsonObject b : raw) {
            byName.put(b.get("name").getAsString(), b);
        }
        List<Bone> out = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        // parents first: repeat until all bones with a known (or missing) parent are placed
        List<JsonObject> left = new ArrayList<>(raw);
        while (!left.isEmpty()) {
            boolean placed = false;
            for (int i = 0; i < left.size(); i++) {
                JsonObject b = left.get(i);
                String parent = b.has("parent") ? b.get("parent").getAsString() : null;
                if (parent != null && byName.containsKey(parent) && !index.containsKey(parent)) {
                    continue;
                }
                int p = parent == null || !index.containsKey(parent) ? -1 : index.get(parent);
                String name = b.get("name").getAsString();
                AttachPoint attach = p >= 0 ? out.get(p).attach : AttachPoint.of(name, defaultAttach);
                index.put(name, out.size());
                out.add(bone(b, name, p, attach, tw, th));
                left.remove(i);
                placed = true;
                break;
            }
            if (!placed) {
                throw new IllegalArgumentException("bone parents form a loop");
            }
        }
        return new GeoModel(tw, th, out.toArray(new Bone[0]));
    }

    private static Bone bone(JsonObject b, String name, int parent, AttachPoint attach, int tw, int th) {
        float[] pivot = vec(b, "pivot", 0);
        float[] rot = vec(b, "rotation", 0);
        boolean mirror = b.has("mirror") && b.get("mirror").getAsBoolean();
        float inflate = b.has("inflate") ? b.get("inflate").getAsFloat() : 0;
        List<float[]> quads = new ArrayList<>();
        if (b.has("cubes") && b.get("cubes").isJsonArray()) {
            for (JsonElement c : b.getAsJsonArray("cubes")) {
                if (c.isJsonObject()) {
                    cube(c.getAsJsonObject(), mirror, inflate, tw, th, quads);
                }
            }
        }
        float[] data = new float[quads.size() * QUAD];
        for (int i = 0; i < quads.size(); i++) {
            System.arraycopy(quads.get(i), 0, data, i * QUAD, QUAD);
        }
        return new Bone(name, parent, -pivot[0], pivot[1], pivot[2],
                (float) Math.toRadians(-rot[0]), (float) Math.toRadians(-rot[1]), (float) Math.toRadians(rot[2]),
                attach, data, quads.size());
    }

    private static float[] vec(JsonObject o, String key, float fallback) {
        float[] v = {fallback, fallback, fallback};
        if (o.has(key) && o.get(key).isJsonArray()) {
            JsonArray a = o.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < a.size(); i++) {
                v[i] = a.get(i).getAsFloat();
            }
        }
        return v;
    }

    // --- cubes (GeckoLib GeometryCube / VertexSet / GeometryQuadUvs)

    private static final int WEST = 0;
    private static final int EAST = 1;
    private static final int NORTH = 2;
    private static final int SOUTH = 3;
    private static final int UP = 4;
    private static final int DOWN = 5;
    private static final String[] FACE_NAMES = {"west", "east", "north", "south", "up", "down"};
    private static final float[][] NORMALS = {{-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {0, 1, 0}, {0, -1, 0}};

    private static void cube(JsonObject c, boolean boneMirror, float boneInflate, int tw, int th, List<float[]> out) {
        float[] size = vec(c, "size", 0);
        float[] origin = vec(c, "origin", 0);
        boolean mirror = c.has("mirror") ? c.get("mirror").getAsBoolean() : boneMirror;
        float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : boneInflate;
        // GeckoLib: origin.add(size.x, 0, 0) * (-1, 1, 1), all in pixels here
        float ox = -(origin[0] + size[0]);
        float oy = origin[1];
        float oz = origin[2];
        float[][] v = vertexSet(ox, oy, oz, size[0], size[1], size[2], inflate);
        Mat4 rot = null;
        if (c.has("rotation")) {
            float[] r = vec(c, "rotation", 0);
            float[] p = vec(c, "pivot", 0);
            float px = -p[0];
            rot = new Mat4().translate(px, p[1], p[2])
                    .rotateZYX((float) Math.toRadians(r[2]), (float) Math.toRadians(-r[1]), (float) Math.toRadians(-r[0]))
                    .translate(-px, -p[1], -p[2]);
        }
        JsonElement uv = c.get("uv");
        boolean boxUv = uv == null || uv.isJsonArray();
        for (int face = 0; face < 6; face++) {
            float u;
            float vv;
            float uSize;
            float vSize;
            int uvRotation = 0;
            if (boxUv) {
                float bu = 0;
                float bv = 0;
                if (uv != null && uv.getAsJsonArray().size() >= 2) {
                    bu = uv.getAsJsonArray().get(0).getAsFloat();
                    bv = uv.getAsJsonArray().get(1).getAsFloat();
                }
                float sx = (float) Math.floor(size[0]);
                float sy = (float) Math.floor(size[1]);
                float sz = (float) Math.floor(size[2]);
                float[] r = boxUv(face, bu, bv, sx, sy, sz);
                u = r[0];
                vv = r[1];
                uSize = r[2];
                vSize = r[3];
            } else {
                JsonObject faces = uv.getAsJsonObject();
                if (!faces.has(FACE_NAMES[face]) || !faces.get(FACE_NAMES[face]).isJsonObject()) {
                    continue;
                }
                JsonObject f = faces.getAsJsonObject(FACE_NAMES[face]);
                float[] fuv = vec(f, "uv", 0);
                float[] fsize = f.has("uv_size") ? vec(f, "uv_size", 0) : defaultFaceSize(face, size);
                u = fuv[0];
                vv = fuv[1];
                uSize = fsize[0];
                vSize = fsize[1];
                if (f.has("uv_rotation")) {
                    uvRotation = Math.floorMod(f.get("uv_rotation").getAsInt(), 360) / 90;
                }
            }
            out.add(quad(v, face, boxUv, mirror, u, vv, uSize, vSize, uvRotation, tw, th, rot));
        }
    }

    private static float[] defaultFaceSize(int face, float[] size) {
        return switch (face) {
            case WEST, EAST -> new float[] {size[2], size[1]};
            case NORTH, SOUTH -> new float[] {size[0], size[1]};
            default -> new float[] {size[0], size[2]};
        };
    }

    /** The eight corners as GeckoLib's VertexSet names them. */
    private static float[][] vertexSet(float x, float y, float z, float sx, float sy, float sz, float inf) {
        float x0 = x - inf;
        float y0 = y - inf;
        float z0 = z - inf;
        float x1 = x + sx + inf;
        float y1 = y + sy + inf;
        float z1 = z + sz + inf;
        return new float[][] {
                {x0, y0, z0}, // 0 bottomLeftBack
                {x0, y0, z1}, // 1 bottomRightBack
                {x0, y1, z0}, // 2 topLeftBack
                {x0, y1, z1}, // 3 topRightBack
                {x1, y1, z0}, // 4 topLeftFront
                {x1, y1, z1}, // 5 topRightFront
                {x1, y0, z0}, // 6 bottomLeftFront
                {x1, y0, z1}, // 7 bottomRightFront
        };
    }

    private static int[] faceVertices(int face, boolean boxUv, boolean mirror) {
        int f = face;
        if (face == WEST && mirror) {
            f = EAST;
        } else if (face == EAST && mirror) {
            f = WEST;
        } else if (face == UP && mirror && !boxUv) {
            f = DOWN;
        } else if (face == DOWN && mirror && !boxUv) {
            f = UP;
        }
        return switch (f) {
            case WEST -> new int[] {3, 2, 0, 1};
            case EAST -> new int[] {4, 5, 7, 6};
            case NORTH -> new int[] {2, 4, 6, 0};
            case SOUTH -> new int[] {5, 3, 1, 7};
            case UP -> new int[] {3, 5, 4, 2};
            default -> new int[] {0, 6, 7, 1};
        };
    }

    /** u, v, uSize, vSize of a face for box UV (GeckoLib GeometryQuadUvs.ofBoxUv). */
    private static float[] boxUv(int face, float u, float v, float sx, float sy, float sz) {
        return switch (face) {
            case WEST -> new float[] {u + sz + sx, v + sz, sz, sy};
            case EAST -> new float[] {u, v + sz, sz, sy};
            case NORTH -> new float[] {u + sz, v + sz, sx, sy};
            case SOUTH -> new float[] {u + sz + sx + sz, v + sz, sx, sy};
            case UP -> new float[] {u + sz, v, sx, sz};
            default -> new float[] {u + sz + sx, v + sz, sx, -sz};
        };
    }

    private static float[] quad(float[][] set, int face, boolean boxUv, boolean mirror, float u, float v, float uSize,
                                float vSize, int uvRotation, int tw, int th, Mat4 rot) {
        int[] idx = faceVertices(face, boxUv, mirror);
        float u0 = u / tw;
        float v0 = v / th;
        float u1 = (u + uSize) / tw;
        float v1 = (v + vSize) / th;
        float nx = NORMALS[face][0];
        float ny = NORMALS[face][1];
        float nz = NORMALS[face][2];
        if (!mirror) {
            float t = u1;
            u1 = u0;
            u0 = t;
        } else {
            nx = -nx;
        }
        float[] uvs = switch (uvRotation) {
            case 1 -> new float[] {u1, v0, u1, v1, u0, v1, u0, v0};
            case 2 -> new float[] {u1, v1, u0, v1, u0, v0, u1, v0};
            case 3 -> new float[] {u0, v1, u0, v0, u1, v0, u1, v1};
            default -> new float[] {u0, v0, u1, v0, u1, v1, u0, v1};
        };
        float[] q = new float[QUAD];
        for (int i = 0; i < 4; i++) {
            float[] p = set[idx[i]];
            float x = p[0];
            float y = p[1];
            float z = p[2];
            if (rot != null) {
                float rx = rot.px(x, y, z);
                float ry = rot.py(x, y, z);
                float rz = rot.pz(x, y, z);
                x = rx;
                y = ry;
                z = rz;
            }
            q[i * 5] = x;
            q[i * 5 + 1] = y;
            q[i * 5 + 2] = z;
            q[i * 5 + 3] = uvs[i * 2];
            q[i * 5 + 4] = uvs[i * 2 + 1];
        }
        if (rot != null) {
            float rx = rot.nx(nx, ny, nz);
            float ry = rot.ny(nx, ny, nz);
            float rz = rot.nz(nx, ny, nz);
            nx = rx;
            ny = ry;
            nz = rz;
        }
        q[20] = nx;
        q[21] = ny;
        q[22] = nz;
        return q;
    }
}
