package dev.lumora.launcher.core.download;

import dev.lumora.launcher.core.BuildInfo;

/** Modrinth requires a uniquely identifying User-Agent (https://docs.modrinth.com/api/#user-agents). */
public final class UserAgent {
    // Distributors should append their own contact (e.g. a repository URL) via the LUMORA_UA_CONTACT env var.
    public static final String VALUE = "LumoraLauncher/" + BuildInfo.VERSION
            + (System.getenv("LUMORA_UA_CONTACT") != null ? " (" + System.getenv("LUMORA_UA_CONTACT") + ")" : "");

    private UserAgent() {
    }
}
