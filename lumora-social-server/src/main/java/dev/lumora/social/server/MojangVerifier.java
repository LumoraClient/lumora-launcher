package dev.lumora.social.server;

import com.google.gson.JsonObject;
import dev.lumora.social.Js;
import dev.lumora.social.Rules;
import dev.lumora.social.model.Profile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Proof of account ownership, exactly like an online-mode Minecraft server: the client joined the random server id
 * at Mojang ({@code session/minecraft/join}); this asks Mojang's official {@code hasJoined} for the verified profile.
 */
final class MojangVerifier implements Hub.Verifier {
    private static final String HAS_JOINED = "https://sessionserver.mojang.com/session/minecraft/hasJoined";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Override
    public Profile verify(String name, String serverId) throws IOException {
        URI uri = URI.create(HAS_JOINED + "?username=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "&serverId=" + URLEncoder.encode(serverId, StandardCharsets.UTF_8));
        HttpResponse<String> res;
        try {
            res = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "LumoraSocialServer/" + Rules.PROTOCOL).build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        if (res.statusCode() == 204) {
            return null; // not joined
        }
        if (res.statusCode() != 200) {
            throw new IOException("Mojang hasJoined HTTP " + res.statusCode());
        }
        JsonObject o = Js.object(res.body());
        String uuid = Rules.uuid(Js.str(o, "id", null));
        String n = Js.str(o, "name", null);
        return uuid == null || !Rules.validName(n) ? null : new Profile(uuid, n);
    }
}
