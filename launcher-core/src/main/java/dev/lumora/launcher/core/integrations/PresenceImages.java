package dev.lumora.launcher.core.integrations;

import java.util.Locale;

/**
 * Picture links of the Discord Rich Presence. Discord loads them itself through its media proxy (nothing is downloaded by
 * the launcher), so they must be public https addresses: the Lumora logo from Lumora's own public repository, the
 * account's skin head from mc-heads.net (by profile UUID) and a server's own server-list icon from the public
 * mcsrvstat.us API (only used when "Show server in Discord" is on, the address is shown then anyway).
 */
public final class PresenceImages {
    /** The big picture: Lumora's launcher icon. */
    public static final String LOGO = "https://raw.githubusercontent.com/LumoraClient/lumora-launcher/main/launcher-app/src/main/resources/lumora/icon-256.png";

    private PresenceImages() {
    }

    /** The skin head of the account, null without a valid profile UUID (32 hex digits, dashes allowed). */
    public static String head(String uuid) {
        if (uuid == null) {
            return null;
        }
        String u = uuid.replace("-", "").toLowerCase(Locale.ROOT);
        return u.matches("[0-9a-f]{32}") ? "https://mc-heads.net/avatar/" + u + "/128" : null;
    }

    /** The server-list icon of the server, null without a clean address (see GameStatus#cleanServer). */
    public static String server(String address) {
        if (address == null || address.isEmpty() || !GameStatus.cleanServer(address).equals(address)) {
            return null;
        }
        return "https://api.mcsrvstat.us/icon/" + address;
    }
}
