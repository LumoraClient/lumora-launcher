package dev.lumora.launcher.core.install;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lumora.launcher.core.config.LauncherConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameThemeSeederTest {
    @TempDir
    Path game;

    private JsonObject read(String rel) throws Exception {
        return JsonParser.parseString(Files.readString(game.resolve(rel), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static LauncherConfig config(String theme, String seed) {
        LauncherConfig c = new LauncherConfig();
        c.gameTheme = theme;
        c.gameThemeSeed = seed;
        return c;
    }

    @Test
    void vanillaKeepsOtherSettingsAndDisablesThemeAddons() throws Exception {
        Files.createDirectories(game.resolve("config/lumora"));
        Files.writeString(game.resolve("config/lumora-client.json"), "{\"zoomFactor\": 6.0, \"lumoraFont\": true}");
        Files.writeString(game.resolve("config/lumora/addons.json"), "{\"disabled\":[\"lumora-zoom\"],\"seen\":[\"a\"]}");
        GameThemeSeeder.seed(game, config("vanilla", "s1"));

        JsonObject client = read("config/lumora-client.json");
        assertEquals("vanilla", client.get("theme").getAsString());
        assertFalse(client.get("customMainMenu").getAsBoolean());
        assertFalse(client.get("lumoraFont").getAsBoolean());
        assertEquals("off", client.get("menuShaders").getAsString());
        assertEquals(6.0, client.get("zoomFactor").getAsDouble());
        JsonObject addons = read("config/lumora/addons.json");
        String disabled = addons.get("disabled").toString();
        assertTrue(disabled.contains("lumora-zoom") && disabled.contains("lumora-buttons") && disabled.contains("lumora-browser"));
        assertEquals("[\"a\"]", addons.get("seen").toString());
    }

    @Test
    void appliesOncePerSeed() throws Exception {
        GameThemeSeeder.seed(game, config("vanilla", "s1"));
        // the player switches back to Lumora in the game: the same installer choice must not undo that
        Files.writeString(game.resolve("config/lumora-client.json"), "{\"theme\": \"lumora\"}");
        GameThemeSeeder.seed(game, config("vanilla", "s1"));
        assertEquals("lumora", read("config/lumora-client.json").get("theme").getAsString());
        // a new installer run applies its choice again
        GameThemeSeeder.seed(game, config("vanilla", "s2"));
        assertEquals("vanilla", read("config/lumora-client.json").get("theme").getAsString());
    }

    @Test
    void lumoraEnablesThemeAddonsAgain() throws Exception {
        GameThemeSeeder.seed(game, config("vanilla", "s1"));
        GameThemeSeeder.seed(game, config("lumora", "s2"));
        JsonObject client = read("config/lumora-client.json");
        assertEquals("lumora", client.get("theme").getAsString());
        assertTrue(client.get("customLoadingScreen").getAsBoolean());
        assertEquals("[]", read("config/lumora/addons.json").get("disabled").toString());
    }

    @Test
    void nothingWithoutAChoice() {
        GameThemeSeeder.seed(game, new LauncherConfig());
        assertFalse(Files.exists(game.resolve("config")));
    }
}
