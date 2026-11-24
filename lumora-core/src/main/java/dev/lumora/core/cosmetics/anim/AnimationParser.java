package dev.lumora.core.cosmetics.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.lumora.core.io.Json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code .animation.json} files as Blockbench writes them for GeckoLib ("GeckoLib Animated Model") and for
 * Bedrock: {@code {"animations": {"animation.name": {"loop", "animation_length", "bones": {...}, "sound_effects",
 * "particle_effects", "timeline"}}}}. Keyframes may be a constant ({@code [x, y, z]}, a number or a Molang string),
 * a map of times, objects with {@code "vector"}, {@code "easing"}, {@code "easingArgs"} (GeckoLib) or with
 * {@code "pre"}/{@code "post"} and {@code "lerp_mode"} (Bedrock), as in GeckoLib's {@code ActorBoneAnimationEntry}.
 * Compiled with Gson 2.2.4 (the oldest Minecraft Gson).
 */
public final class AnimationParser {
    private AnimationParser() {
    }

    /**
     * All animations of the file by name.
     *
     * @throws IllegalArgumentException if the file is not an animation file or a keyframe cannot be read
     */
    public static Map<String, Animation> parse(String json) {
        JsonElement root;
        try {
            root = Json.parse(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("not JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isJsonObject() || !root.getAsJsonObject().has("animations")
                || !root.getAsJsonObject().get("animations").isJsonObject()) {
            throw new IllegalArgumentException("no \"animations\" object");
        }
        Map<String, Animation> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().getAsJsonObject("animations").entrySet()) {
            if (e.getValue().isJsonObject()) {
                try {
                    out.put(e.getKey(), animation(e.getKey(), e.getValue().getAsJsonObject()));
                } catch (RuntimeException ex) {
                    throw new IllegalArgumentException(e.getKey() + ": " + ex.getMessage(), ex);
                }
            }
        }
        return out;
    }

    private static Animation animation(String name, JsonObject o) {
        Animation.Loop loop = Animation.Loop.PLAY_ONCE;
        if (o.has("loop") && o.get("loop").isJsonPrimitive()) {
            JsonPrimitive p = o.getAsJsonPrimitive("loop");
            if (p.isBoolean()) {
                loop = p.getAsBoolean() ? Animation.Loop.LOOP : Animation.Loop.PLAY_ONCE;
            } else if ("hold_on_last_frame".equals(p.getAsString())) {
                loop = Animation.Loop.HOLD_ON_LAST_FRAME;
            } else if ("true".equals(p.getAsString())) {
                loop = Animation.Loop.LOOP;
            }
        }
        Map<String, Animation.BoneTrack> bones = new LinkedHashMap<>();
        double last = 0;
        if (o.has("bones") && o.get("bones").isJsonObject()) {
            for (Map.Entry<String, JsonElement> b : o.getAsJsonObject("bones").entrySet()) {
                if (!b.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject bone = b.getValue().getAsJsonObject();
                Track rot = track(bone.get("rotation"));
                Track pos = track(bone.get("position"));
                Track scale = track(bone.get("scale"));
                for (Track t : new Track[] {rot, pos, scale}) {
                    if (t != null) {
                        last = Math.max(last, t.lastTime());
                    }
                }
                bones.put(b.getKey(), new Animation.BoneTrack(rot, pos, scale));
            }
        }
        List<Animation.Event> events = new ArrayList<>();
        events(o.get("sound_effects"), "sound", events);
        events(o.get("particle_effects"), "particle", events);
        events(o.get("timeline"), "timeline", events);
        for (Animation.Event ev : events) {
            last = Math.max(last, ev.time());
        }
        events.sort(Comparator.comparingDouble(Animation.Event::time));
        double length = last;
        if (o.has("animation_length") && o.get("animation_length").isJsonPrimitive()) {
            length = o.get("animation_length").getAsDouble();
        }
        return new Animation(name, length, loop, bones, events);
    }

    private static void events(JsonElement el, String kind, List<Animation.Event> out) {
        if (el == null || !el.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            double time = time(e.getKey());
            JsonElement v = e.getValue();
            List<JsonElement> items = new ArrayList<>();
            if (v.isJsonArray()) {
                for (JsonElement item : v.getAsJsonArray()) {
                    items.add(item);
                }
            } else {
                items.add(v);
            }
            for (JsonElement item : items) {
                String value = null;
                if (item.isJsonPrimitive()) {
                    value = item.getAsString();
                } else if (item.isJsonObject() && item.getAsJsonObject().has("effect")) {
                    value = item.getAsJsonObject().get("effect").getAsString();
                }
                if (value != null) {
                    out.add(new Animation.Event(time, kind, value));
                }
            }
        }
    }

    private static double time(String key) {
        try {
            return Double.parseDouble(key.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad keyframe time '" + key + "'");
        }
    }

    /** One keyframe while reading. */
    private record Frame(double time, Molang.Expr[] pre, Molang.Expr[] post, Easing easing, Molang.Expr arg) {
    }

    static Track track(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return null;
        }
        List<Frame> frames = new ArrayList<>();
        if (el.isJsonObject() && !el.getAsJsonObject().has("vector") && !el.getAsJsonObject().has("pre")
                && !el.getAsJsonObject().has("post")) {
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                frames.add(frame(time(e.getKey()), e.getValue()));
            }
            frames.sort(Comparator.comparingDouble(Frame::time));
        } else {
            frames.add(frame(0, el));
        }
        if (frames.isEmpty()) {
            return null;
        }
        int n = frames.size();
        double[] times = new double[n];
        Molang.Expr[][] pre = new Molang.Expr[n][];
        Molang.Expr[][] post = new Molang.Expr[n][];
        Easing[] easing = new Easing[n];
        Molang.Expr[] args = new Molang.Expr[n];
        for (int i = 0; i < n; i++) {
            Frame f = frames.get(i);
            times[i] = f.time;
            pre[i] = f.pre;
            post[i] = f.post;
            easing[i] = n == 1 ? Easing.LINEAR : f.easing;
            args[i] = f.arg;
        }
        return new Track(times, pre, post, easing, args);
    }

    private static Frame frame(double time, JsonElement el) {
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            String easingName = o.has("lerp_mode") ? o.get("lerp_mode").getAsString()
                    : o.has("easing") ? o.get("easing").getAsString() : null;
            Easing easing = Easing.of(easingName);
            Molang.Expr arg = null;
            if (o.has("easingArgs") && o.get("easingArgs").isJsonArray() && o.getAsJsonArray("easingArgs").size() > 0) {
                arg = expr(o.getAsJsonArray("easingArgs").get(0));
            }
            Molang.Expr[] value = o.has("vector") ? vector(o.get("vector")) : null;
            Molang.Expr[] pre = o.has("pre") ? vectorOf(o.get("pre")) : value;
            Molang.Expr[] post = o.has("post") ? vectorOf(o.get("post")) : value;
            if (pre == null) {
                pre = post;
            }
            if (post == null) {
                post = pre;
            }
            if (pre == null) {
                throw new IllegalArgumentException("keyframe at " + time + " has no value");
            }
            return new Frame(time, pre, post, easing, arg);
        }
        Molang.Expr[] v = vector(el);
        return new Frame(time, v, v, Easing.LINEAR, null);
    }

    /** "pre"/"post" may be a plain vector or an object with "vector". */
    private static Molang.Expr[] vectorOf(JsonElement el) {
        if (el.isJsonObject() && el.getAsJsonObject().has("vector")) {
            return vector(el.getAsJsonObject().get("vector"));
        }
        return vector(el);
    }

    private static Molang.Expr[] vector(JsonElement el) {
        if (el.isJsonArray()) {
            JsonArray a = el.getAsJsonArray();
            if (a.size() == 0) {
                throw new IllegalArgumentException("empty vector");
            }
            Molang.Expr x = expr(a.get(0));
            if (a.size() < 3) {
                return new Molang.Expr[] {x, x, x};
            }
            return new Molang.Expr[] {x, expr(a.get(1)), expr(a.get(2))};
        }
        if (el.isJsonPrimitive()) {
            Molang.Expr x = expr(el);
            Molang.Expr[] v = new Molang.Expr[3];
            Arrays.fill(v, x);
            return v;
        }
        throw new IllegalArgumentException("bad vector " + el);
    }

    private static Molang.Expr expr(JsonElement el) {
        if (!el.isJsonPrimitive()) {
            throw new IllegalArgumentException("bad value " + el);
        }
        JsonPrimitive p = el.getAsJsonPrimitive();
        if (p.isNumber()) {
            return Molang.constant(p.getAsDouble());
        }
        if (p.isBoolean()) {
            return Molang.constant(p.getAsBoolean() ? 1 : 0);
        }
        return Molang.compile(p.getAsString());
    }
}
