package dev.lumora.social;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The official Minecraft session join ({@code POST sessionserver.mojang.com/session/minecraft/join}), the same call the
 * game makes before joining an online-mode server. The access token goes only to Mojang; the Lumora Social server
 * then asks Mojang with {@code hasJoined} whether this player joined the random server id it handed out. The token is
 * never logged and never sent anywhere else.
 */
public final class MojangSession {
    public static final URI JOIN = URI.create("https://sessionserver.mojang.com/session/minecraft/join");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    private MojangSession() {
    }

    /**
     * @param uuid profile UUID without dashes
     * @throws IOException with a German message when Mojang rejects the session (expired token, no license)
     */
    public static void join(String accessToken, String uuid, String serverId) throws IOException {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IOException("Keine Minecraft-Sitzung (nicht angemeldet).");
        }
        String body = Js.obj().put("accessToken", accessToken).put("selectedProfile", uuid).put("serverId", serverId).toString();
        HttpRequest req = HttpRequest.newBuilder(JOIN).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").header("User-Agent", "LumoraSocial/" + Rules.PROTOCOL)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<Void> res;
        try {
            res = HTTP.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Abgebrochen", e);
        }
        if (res.statusCode() == 204 || res.statusCode() == 200) {
            return;
        }
        if (res.statusCode() == 403 || res.statusCode() == 401) {
            throw new IOException("Minecraft-Sitzung abgelaufen oder ungültig. Bitte im Launcher neu anmelden.");
        }
        if (res.statusCode() == 429) {
            throw new IOException("Mojang-Sitzungsdienst: zu viele Anfragen, später erneut versuchen.");
        }
        throw new IOException("Mojang-Sitzungsdienst antwortet mit HTTP " + res.statusCode() + ".");
    }
}
