package dev.lumora.core.io;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * JSON parsing that works with every Gson version Minecraft ships. The core does not bundle Gson; Minecraft 1.8.9 and
 * 1.12.2 come with Gson 2.2.4, 1.16.5 and 1.17.1 with 2.8.0 - the static {@code JsonParser.parseString} needs Gson 2.8.6. The
 * instance parser exists in every version (deprecated, not removed, in newer Gson). The module compiles against
 * Gson 2.2.4 (see build.gradle.kts), so newer Gson API cannot slip in unnoticed.
 */
public final class Json {
    private Json() {
    }

    /** Lenient parse of one JSON value, like {@code JsonParser.parseString}. */
    @SuppressWarnings("deprecation")
    public static JsonElement parse(String json) throws JsonSyntaxException {
        return new JsonParser().parse(json);
    }
}
