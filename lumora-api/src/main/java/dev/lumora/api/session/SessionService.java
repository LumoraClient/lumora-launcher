package dev.lumora.api.session;

import java.util.Optional;
import java.util.UUID;

/**
 * The signed-in Minecraft account (permission {@code session}). Addons never see the access token; they can only
 * ask Lumora to announce the account to Mojang's session server for a server hash, the same call the game makes
 * before joining an online-mode server. Services like voice chat then verify the account with Mojang.
 *
 * @since 1.1.0
 */
public interface SessionService {
    /** Profile of the signed-in account; empty without a session (e.g. demo or offline). */
    Optional<Profile> profile();

    /**
     * {@code POST sessionserver.mojang.com/session/minecraft/join} for {@code serverHash}. Blocks while the request
     * runs: call it on a background thread.
     *
     * @throws Exception with a readable message when there is no session or Mojang rejects it
     */
    void joinServer(String serverHash) throws Exception;

    record Profile(UUID id, String name) {}
}
