package dev.lumora.launcher.core.mods;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Display data of a resource pack zip (pack.mcmeta description, pack.png) in the shape of {@link ModInfo}. */
final class PackInfo {
    private PackInfo() {
    }

    static ModInfo read(Path zip, ProfileCatalog.Artifact a) {
        String description = null;
        byte[] icon = null;
        try (ZipFile z = new ZipFile(zip.toFile())) {
            ZipEntry meta = z.getEntry("pack.mcmeta");
            if (meta != null) {
                try (InputStream in = z.getInputStream(meta)) {
                    JsonObject o = Json.GSON.fromJson(new String(in.readNBytes(256 * 1024), StandardCharsets.UTF_8), JsonObject.class);
                    if (o != null && o.has("pack") && o.get("pack").isJsonObject()) {
                        description = text(o.getAsJsonObject("pack").get("description"));
                    }
                } catch (JsonParseException | IllegalStateException ignored) {
                    // description is optional
                }
            }
            ZipEntry png = z.getEntry("pack.png");
            if (png != null && png.getSize() <= ModInfo.MAX_ICON_BYTES) {
                try (InputStream in = z.getInputStream(png)) {
                    byte[] data = in.readNBytes(ModInfo.MAX_ICON_BYTES + 1);
                    icon = data.length > ModInfo.MAX_ICON_BYTES ? null : data;
                }
            }
            String name = a.name.contains(" (") ? a.name.substring(0, a.name.indexOf(" (")) : a.name;
            return new ModInfo(a.key(), name, a.version, description == null ? null : description.replaceAll("§.", "").strip(),
                    List.of(), a.license, null, null, 0, icon, a.fileName, Files.size(zip));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Chat component (string, object with "text"/"extra" or array) to plain text. */
    static String text(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return "";
        }
        if (e.isJsonPrimitive()) {
            return e.getAsString();
        }
        StringBuilder sb = new StringBuilder();
        if (e.isJsonArray()) {
            e.getAsJsonArray().forEach(x -> sb.append(text(x)));
        } else if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("text")) {
                sb.append(text(o.get("text")));
            }
            if (o.has("extra")) {
                sb.append(text(o.get("extra")));
            }
        }
        return sb.toString();
    }
}
