package dev.lumora.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Small JSON helpers that work with every Gson version Minecraft ships (2.2.4 in 1.8.9). Reading is tolerant: a
 * missing or mistyped field gives the default instead of an exception, so a newer peer may add fields.
 */
public final class Js {
    private Js() {
    }

    /** Parses one JSON object; anything else (array, garbage) gives an exception. */
    @SuppressWarnings("deprecation")
    public static JsonObject object(String json) {
        JsonElement e = new JsonParser().parse(json);
        if (!e.isJsonObject()) {
            throw new IllegalArgumentException("JSON object expected");
        }
        return e.getAsJsonObject();
    }

    public static String str(JsonObject o, String key, String def) {
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return def;
        }
        return e.getAsString();
    }

    public static long num(JsonObject o, String key, long def) {
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return def;
        }
        try {
            return e.getAsLong();
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    public static boolean bool(JsonObject o, String key, boolean def) {
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) {
            return def;
        }
        return e.getAsBoolean();
    }

    public static JsonObject obj(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    /** The objects of an array field (non-objects are skipped), each converted by {@code read}. */
    public static <T> List<T> list(JsonObject o, String key, Function<JsonObject, T> read) {
        List<T> out = new ArrayList<>();
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonArray()) {
            return out;
        }
        for (JsonElement item : e.getAsJsonArray()) {
            if (item.isJsonObject()) {
                T t = read.apply(item.getAsJsonObject());
                if (t != null) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    public static <T> JsonArray array(List<T> items, Function<T, JsonElement> write) {
        JsonArray a = new JsonArray();
        for (T t : items) {
            a.add(write.apply(t));
        }
        return a;
    }

    public static JsonPrimitive prim(String s) {
        return new JsonPrimitive(s);
    }

    /** Builder for one message: {@code Js.op("msg.send").put("text", t).json()}. */
    public static Obj op(String op) {
        return new Obj().put("op", op);
    }

    public static Obj obj() {
        return new Obj();
    }

    public static final class Obj {
        private final JsonObject o = new JsonObject();

        public Obj put(String k, String v) {
            if (v != null) {
                o.addProperty(k, v);
            }
            return this;
        }

        public Obj put(String k, long v) {
            o.addProperty(k, v);
            return this;
        }

        public Obj put(String k, boolean v) {
            o.addProperty(k, v);
            return this;
        }

        public Obj put(String k, JsonElement v) {
            if (v != null) {
                o.add(k, v);
            }
            return this;
        }

        public JsonObject json() {
            return o;
        }

        @Override
        public String toString() {
            return o.toString();
        }
    }
}
