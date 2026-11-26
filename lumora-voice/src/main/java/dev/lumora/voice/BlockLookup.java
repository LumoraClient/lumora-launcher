package dev.lumora.voice;

import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/** Eingaben für die Blockliste: UUID (mit/ohne Bindestriche) oder Minecraft-Name, notfalls per Mojang-API aufgelöst. */
public final class BlockLookup {
    public record Profile(UUID id, String name) {}
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{32}");
    private BlockLookup() {}

    public static boolean validName(String value) { return value != null && NAME.matcher(value).matches(); }
    /** UUID mit oder ohne Bindestriche, sonst {@code null}. */
    public static UUID parseUuid(String value) {
        if (value == null) return null;
        String text = value.trim();
        if (HEX.matcher(text).matches()) text = text.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");
        if (text.length() != 36) return null;
        try { return UUID.fromString(text); } catch (IllegalArgumentException invalid) { return null; }
    }
    /** UUID, die Offline-Server (ohne Mojang-Anmeldung) für diesen Namen vergeben. */
    public static UUID offlineUuid(String name) { return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)); }
    /** Antwort von {@code api.mojang.com/users/profiles/minecraft/<name>}. */
    public static Profile parseProfile(String json) {
        var root = new JsonParser().parse(json).getAsJsonObject();
        UUID id = parseUuid(root.get("id").getAsString());
        String name = root.get("name").getAsString();
        if (id == null || !validName(name)) throw new IllegalArgumentException("Unerwartete Mojang-Antwort");
        return new Profile(id, name);
    }
    /** Fragt Mojang nach der UUID eines Namens; leeres Ergebnis, wenn es den Namen nicht gibt. */
    public static CompletableFuture<Optional<Profile>> mojang(String name) {
        if (!validName(name)) return CompletableFuture.failedFuture(new IllegalArgumentException("Ungültiger Name"));
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
        var request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name)).timeout(Duration.ofSeconds(8)).GET().build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() == 204 || response.statusCode() == 404) return Optional.<Profile>empty();
            if (response.statusCode() != 200 || response.body().length() > 4096) throw new IllegalStateException("Mojang antwortet mit " + response.statusCode());
            return Optional.of(parseProfile(response.body()));
        }).whenComplete((result, failure) -> http.close());
    }
}
