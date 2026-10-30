package dev.lumora.launcher.core.auth;

import java.time.Instant;

/**
 * A signed-in Minecraft account. {@link #toString()} never includes tokens.
 *
 * @param msRefreshToken Microsoft refresh token, used to obtain a new Minecraft token without user interaction
 */
public record MinecraftSession(String username, String uuid, String xuid, String accessToken,
                               Instant accessTokenExpiresAt, String msRefreshToken) {

    public boolean accessTokenValid(Instant now) {
        return accessToken != null && accessTokenExpiresAt != null && now.plusSeconds(300).isBefore(accessTokenExpiresAt);
    }

    @Override
    public String toString() {
        return "MinecraftSession[" + username + ", " + uuid + ", expires " + accessTokenExpiresAt + "]";
    }
}
