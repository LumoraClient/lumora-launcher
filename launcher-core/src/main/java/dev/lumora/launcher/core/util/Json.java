package dev.lumora.launcher.core.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared Gson instance and file helpers. */
public final class Json {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private Json() {
    }

    public static <T> T read(Path file, Class<T> type) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        try {
            T value = GSON.fromJson(text, type);
            if (value == null) {
                throw new IOException("Empty JSON document: " + file);
            }
            return value;
        } catch (JsonParseException e) {
            throw new IOException("Invalid JSON in " + file + ": " + e.getMessage(), e);
        }
    }

    public static void write(Path file, Object value) throws IOException {
        FileUtil.writeAtomically(file, GSON.toJson(value).getBytes(StandardCharsets.UTF_8));
    }
}
