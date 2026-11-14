package dev.lumora.launcher.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AppIdsTest {
    @Test
    void builtInIdsHaveTheExpectedFormat() {
        // Azure application (client) ID: a GUID; Discord application ID: a numeric snowflake
        assertTrue(AppIds.MICROSOFT_CLIENT_ID.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertTrue(AppIds.DISCORD_APP_ID.matches("[0-9]{17,20}"));
        assertTrue(PlayService.effectiveClientId(new dev.lumora.launcher.core.config.LauncherConfig()).equals(AppIds.MICROSOFT_CLIENT_ID));
    }
}
