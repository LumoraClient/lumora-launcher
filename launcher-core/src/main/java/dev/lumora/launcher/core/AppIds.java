package dev.lumora.launcher.core;

/**
 * Lumora's own application IDs, built in (no setting). Both are public identifiers, no secrets: the Microsoft sign-in
 * is a public client with PKCE (no client secret), Discord Rich Presence only needs the application ID.
 */
public final class AppIds {
    /** Azure application (client) ID of Lumora for the Microsoft / Xbox / Minecraft sign-in. */
    public static final String MICROSOFT_CLIENT_ID = "54473e32-df8f-42e9-a649-9419b0dab9d3";
    /** Discord application ID of Lumora for Rich Presence (always on; the local Discord app shows it). */
    public static final String DISCORD_APP_ID = "1568277782901948486";

    private AppIds() {
    }
}
