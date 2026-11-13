package dev.lumora.launcher.core.install;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Carries the design chosen in the installer ("Lumora Theme" or classic Minecraft) into a Lumora instance, once per
 * choice: the same switches as Hub › Themes in the game (theme, Lumora main menu, menu background, loading screen,
 * menu shaders, Lumora font and the theme addons). A marker in the instance remembers which choice
 * ({@link LauncherConfig#gameThemeSeed}) was applied, so a later change in the game is never overwritten.
 */
public final class GameThemeSeeder {
    public static final String LUMORA = "lumora";
    public static final String VANILLA = "vanilla";
    static final String MARKER = ".lumora-theme-seed";
    /** Same list as dev.lumora.core.hub.HubView.THEME_ADDONS. */
    static final List<String> THEME_ADDONS = List.of("lumora-browser", "lumora-connection", "lumora-buttons");

    private GameThemeSeeder() {
    }

    /** Applies the configured design to {@code gameDir} if this choice was not applied there yet. Never throws. */
    public static void seed(Path gameDir, LauncherConfig config) {
        if (config.gameThemeSeed == null || config.gameTheme == null) {
            return;
        }
        Path configDir = gameDir.resolve("config");
        Path marker = configDir.resolve(MARKER);
        try {
            if (Files.isRegularFile(marker) && config.gameThemeSeed.equals(Files.readString(marker, StandardCharsets.UTF_8).strip())) {
                return;
            }
            apply(configDir, VANILLA.equals(config.gameTheme));
            Files.createDirectories(configDir);
            FileUtil.writeAtomically(marker, config.gameThemeSeed.getBytes(StandardCharsets.UTF_8));
            Log.info("Design '" + config.gameTheme + "' from the installer applied to " + gameDir.getFileName());
        } catch (IOException | RuntimeException e) {
            Log.warn("Could not apply the design from the installer to " + gameDir, e);
        }
    }

    static void apply(Path configDir, boolean vanilla) throws IOException {
        boolean lumora = !vanilla;
        Path clientFile = configDir.resolve("lumora-client.json");
        JsonObject client = readObject(clientFile);
        client.addProperty("theme", lumora ? LUMORA : VANILLA);
        client.addProperty("customMainMenu", lumora);
        client.addProperty("menuBackground", lumora);
        client.addProperty("customLoadingScreen", lumora);
        client.addProperty("menuShaders", lumora ? "auto" : "off");
        if (vanilla) {
            // Minecraft's own font; the Lumora theme keeps whatever font the player had (off by default)
            client.addProperty("lumoraFont", false);
        }
        write(clientFile, client);

        Path addonsFile = configDir.resolve("lumora").resolve("addons.json");
        JsonObject addons = readObject(addonsFile);
        JsonArray disabled = addons.get("disabled") instanceof JsonArray a ? a : new JsonArray();
        JsonArray next = new JsonArray();
        for (JsonElement e : disabled) {
            if (e.isJsonPrimitive() && !THEME_ADDONS.contains(e.getAsString())) {
                next.add(e);
            }
        }
        if (vanilla) {
            for (String id : THEME_ADDONS) {
                next.add(new JsonPrimitive(id));
            }
        }
        addons.add("disabled", next);
        write(addonsFile, addons);
    }

    private static JsonObject readObject(Path file) {
        if (!Files.isRegularFile(file)) {
            return new JsonObject();
        }
        try {
            JsonElement e = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return e instanceof JsonObject o ? o : new JsonObject();
        } catch (IOException | RuntimeException e) {
            // the game backs up invalid files itself; start from its defaults
            return new JsonObject();
        }
    }

    private static void write(Path file, JsonObject o) throws IOException {
        Files.createDirectories(file.getParent());
        FileUtil.writeAtomically(file, Json.GSON.toJson(o).getBytes(StandardCharsets.UTF_8));
    }
}
