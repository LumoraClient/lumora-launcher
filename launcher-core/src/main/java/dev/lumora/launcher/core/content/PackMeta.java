package dev.lumora.launcher.core.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Description and icon of a resource or data pack zip ({@code pack.mcmeta}, {@code pack.png}); read-only. */
public record PackMeta(String description, int format, byte[] icon) {
    private static final int MAX = 256 * 1024;

    /** Null if the zip has no pack.mcmeta (e.g. a shader pack). */
    public static PackMeta read(Path zipFile) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            ZipEntry meta = zip.getEntry("pack.mcmeta");
            if (meta == null) {
                return null;
            }
            String description = null;
            int format = 0;
            try (InputStream in = zip.getInputStream(meta)) {
                JsonObject o = JsonParser.parseString(new String(in.readNBytes(MAX), StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("pack") && o.get("pack").isJsonObject()) {
                    JsonObject p = o.getAsJsonObject("pack");
                    description = text(p.get("description"));
                    if (p.has("pack_format") && p.get("pack_format").isJsonPrimitive()) {
                        format = p.get("pack_format").getAsInt();
                    }
                }
            } catch (JsonParseException | IllegalStateException | NumberFormatException | UnsupportedOperationException e) {
                // description is optional
            }
            byte[] icon = null;
            ZipEntry png = zip.getEntry("pack.png");
            if (png != null && png.getSize() <= MAX) {
                try (InputStream in = zip.getInputStream(png)) {
                    byte[] data = in.readNBytes(MAX + 1);
                    icon = data.length <= MAX ? data : null;
                }
            }
            return new PackMeta(description, format, icon);
        } catch (java.util.zip.ZipException e) {
            throw new IOException("not a valid zip: " + zipFile.getFileName(), e);
        }
    }

    /** Chat component (string, object with text/extra, or array) to plain text without formatting codes. */
    static String text(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        append(sb, e, 0);
        return sb.toString().replaceAll("§.", "").strip();
    }

    private static void append(StringBuilder sb, JsonElement e, int depth) {
        if (depth > 8 || e == null || e.isJsonNull()) {
            return;
        }
        if (e.isJsonPrimitive()) {
            sb.append(e.getAsString());
        } else if (e.isJsonArray()) {
            e.getAsJsonArray().forEach(x -> append(sb, x, depth + 1));
        } else if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("text")) {
                append(sb, o.get("text"), depth + 1);
            }
            if (o.has("extra")) {
                append(sb, o.get("extra"), depth + 1);
            }
        }
    }
}
