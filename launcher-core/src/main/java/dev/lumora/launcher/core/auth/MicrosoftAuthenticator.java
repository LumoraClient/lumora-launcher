package dev.lumora.launcher.core.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.download.UserAgent;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;
import dev.lumora.launcher.core.util.Redactor;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * Microsoft account sign-in for Minecraft: Java Edition using the documented chain
 * <ol>
 *   <li>Microsoft identity platform, OAuth 2.0 device authorization grant (consumers tenant,
 *       scope {@code XboxLive.signin offline_access})</li>
 *   <li>Xbox Live user token (user.auth.xboxlive.com)</li>
 *   <li>XSTS token for relying party {@code rp://api.minecraftservices.com/}</li>
 *   <li>Minecraft Services {@code /authentication/login_with_xbox}, entitlement and profile check</li>
 * </ol>
 * The Azure client ID is supplied by the distributor/user (no built-in credentials). New Azure apps must
 * be approved by Mojang for the Minecraft Services API, otherwise step 4 answers 403.
 */
public final class MicrosoftAuthenticator {
    public record Endpoints(URI deviceCode, URI token, URI xboxUser, URI xsts, URI mcLogin, URI mcEntitlements,
                            URI mcProfile, URI authorize) {
        public static Endpoints official() {
            return new Endpoints(
                    URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"),
                    URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"),
                    URI.create("https://user.auth.xboxlive.com/user/authenticate"),
                    URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"),
                    URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"),
                    URI.create("https://api.minecraftservices.com/entitlements/mcstore"),
                    URI.create("https://api.minecraftservices.com/minecraft/profile"),
                    URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize"));
        }
    }

    /** Opens the Microsoft sign-in page (normally the system browser). */
    public interface BrowserOpener {
        void open(URI authorizeUrl) throws IOException;
    }

    static final Duration BROWSER_LOGIN_TIMEOUT = Duration.ofMinutes(10);

    /** What the user has to do: open {@code verificationUri} and enter {@code userCode}. */
    public record DeviceCodePrompt(String userCode, String verificationUri, Duration expiresIn, String message) {
    }

    public interface PromptListener {
        void show(DeviceCodePrompt prompt);
    }

    static final String SCOPE = "XboxLive.signin offline_access";

    private final HttpClient http;
    private final Endpoints endpoints;
    private final String clientId;
    private final Clock clock;
    private final Duration minPollInterval;

    public MicrosoftAuthenticator(String clientId) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(), Endpoints.official(), clientId,
                Clock.systemUTC(), Duration.ofSeconds(1));
    }

    public MicrosoftAuthenticator(HttpClient http, Endpoints endpoints, String clientId, Clock clock, Duration minPollInterval) {
        this.http = http;
        this.endpoints = endpoints;
        this.clientId = clientId;
        this.clock = clock;
        this.minPollInterval = minPollInterval;
    }

    /** Interactive sign-in. {@code cancelled} is polled between token requests. */
    public MinecraftSession signIn(PromptListener listener, BooleanSupplier cancelled) throws AuthException {
        requireClientId();
        JsonObject dc = postForm(endpoints.deviceCode(), Map.of("client_id", clientId, "scope", SCOPE), "device code");
        if (dc.has("error")) {
            throw msError(dc);
        }
        String deviceCode = str(dc, "device_code");
        String userCode = str(dc, "user_code");
        String uri = str(dc, "verification_uri");
        long expires = dc.has("expires_in") ? dc.get("expires_in").getAsLong() : 900;
        long interval = dc.has("interval") ? dc.get("interval").getAsLong() : 5;
        listener.show(new DeviceCodePrompt(userCode, uri, Duration.ofSeconds(expires),
                dc.has("message") ? dc.get("message").getAsString() : null));

        Instant deadline = clock.instant().plusSeconds(expires);
        Duration wait = Duration.ofSeconds(interval).compareTo(minPollInterval) < 0 ? minPollInterval : Duration.ofSeconds(interval);
        while (true) {
            if (cancelled.getAsBoolean()) {
                throw new AuthException(AuthException.Reason.CANCELLED, "Anmeldung abgebrochen.");
            }
            if (clock.instant().isAfter(deadline)) {
                throw new AuthException(AuthException.Reason.EXPIRED, "Der Anmeldecode ist abgelaufen. Bitte erneut versuchen.");
            }
            sleep(wait);
            if (cancelled.getAsBoolean()) {
                throw new AuthException(AuthException.Reason.CANCELLED, "Anmeldung abgebrochen.");
            }
            JsonObject tok = postForm(endpoints.token(), Map.of(
                    "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id", clientId,
                    "device_code", deviceCode), "token");
            if (!tok.has("error")) {
                return completeWithMicrosoftToken(tok);
            }
            String err = tok.get("error").getAsString();
            switch (err) {
                case "authorization_pending" -> { }
                case "slow_down" -> wait = wait.plusSeconds(5);
                case "authorization_declined" -> throw new AuthException(AuthException.Reason.DECLINED,
                        "Die Anmeldung wurde im Browser abgelehnt.");
                case "expired_token" -> throw new AuthException(AuthException.Reason.EXPIRED,
                        "Der Anmeldecode ist abgelaufen. Bitte erneut versuchen.");
                default -> throw msError(tok);
            }
        }
    }

    /**
     * Interactive sign-in in the browser: OAuth 2.0 authorization code flow with PKCE (RFC 7636) and a
     * loopback redirect ({@code http://localhost:<random port>}), the flow Microsoft documents for desktop apps.
     * The user signs in on Microsoft's own page; the launcher only receives a one-time code, which is bound
     * to the PKCE verifier and the random {@code state} of this attempt.
     */
    public MinecraftSession signInWithBrowser(BrowserOpener opener, BooleanSupplier cancelled) throws AuthException {
        requireClientId();
        String verifier = randomUrlSafe(48);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier));
        String state = randomUrlSafe(24);
        LoopbackReceiver receiver;
        try {
            receiver = LoopbackReceiver.start(state);
        } catch (IOException e) {
            throw new AuthException(AuthException.Reason.NETWORK,
                    "Lokaler Anmelde-Empfänger konnte nicht gestartet werden: " + e.getMessage(), e);
        }
        try {
            String redirectUri = receiver.redirectUri();
            Map<String, String> q = new LinkedHashMap<>();
            q.put("client_id", clientId);
            q.put("response_type", "code");
            q.put("redirect_uri", redirectUri);
            q.put("response_mode", "query");
            q.put("scope", SCOPE);
            q.put("state", state);
            q.put("code_challenge", challenge);
            q.put("code_challenge_method", "S256");
            q.put("prompt", "select_account");
            URI authorizeUrl = URI.create(endpoints.authorize() + "?" + formEncode(q));
            try {
                opener.open(authorizeUrl);
            } catch (IOException e) {
                throw new AuthException(AuthException.Reason.NETWORK, "Browser konnte nicht geöffnet werden: " + e.getMessage(), e);
            }
            Map<String, String> result = receiver.await(BROWSER_LOGIN_TIMEOUT, cancelled);
            if (result == null) {
                if (cancelled.getAsBoolean()) {
                    throw new AuthException(AuthException.Reason.CANCELLED, "Anmeldung abgebrochen.");
                }
                throw new AuthException(AuthException.Reason.EXPIRED, "Die Anmeldung im Browser wurde nicht rechtzeitig abgeschlossen.");
            }
            if (result.containsKey("error")) {
                String err = result.get("error");
                if ("access_denied".equals(err) || "consent_required".equals(err)) {
                    throw new AuthException(AuthException.Reason.DECLINED, "Die Anmeldung wurde im Browser abgebrochen oder abgelehnt.");
                }
                JsonObject o = new JsonObject();
                o.addProperty("error", err);
                o.addProperty("error_description", result.getOrDefault("error_description", ""));
                throw msError(o);
            }
            String code = result.get("code");
            Redactor.registerSecret(code);
            JsonObject tok = postForm(endpoints.token(), Map.of(
                    "grant_type", "authorization_code",
                    "client_id", clientId,
                    "code", code,
                    "redirect_uri", redirectUri,
                    "code_verifier", verifier,
                    "scope", SCOPE), "token");
            if (tok.has("error")) {
                throw msError(tok);
            }
            return completeWithMicrosoftToken(tok);
        } finally {
            receiver.close();
        }
    }

    private static String randomUrlSafe(int bytes) {
        byte[] b = new byte[bytes];
        new java.security.SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static byte[] sha256(String s) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.US_ASCII));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String formEncode(Map<String, String> form) {
        return form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    /** Silent refresh using the stored Microsoft refresh token. */
    public MinecraftSession refresh(MinecraftSession previous) throws AuthException {
        requireClientId();
        if (previous == null || previous.msRefreshToken() == null) {
            throw new AuthException(AuthException.Reason.SESSION_INVALID, "Keine gespeicherte Anmeldung vorhanden.");
        }
        JsonObject tok = postForm(endpoints.token(), Map.of(
                "grant_type", "refresh_token",
                "client_id", clientId,
                "refresh_token", previous.msRefreshToken(),
                "scope", SCOPE), "token refresh");
        if (tok.has("error")) {
            String err = tok.get("error").getAsString();
            if ("invalid_grant".equals(err) || "interaction_required".equals(err)) {
                throw new AuthException(AuthException.Reason.SESSION_INVALID,
                        "Die gespeicherte Anmeldung ist abgelaufen. Bitte erneut anmelden.");
            }
            throw msError(tok);
        }
        return completeWithMicrosoftToken(tok);
    }

    private MinecraftSession completeWithMicrosoftToken(JsonObject tok) throws AuthException {
        String msAccess = str(tok, "access_token");
        String msRefresh = tok.has("refresh_token") ? tok.get("refresh_token").getAsString() : null;
        Redactor.registerSecret(msAccess);
        Redactor.registerSecret(msRefresh);

        // Xbox Live user token
        JsonObject xblReq = new JsonObject();
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName", "user.auth.xboxlive.com");
        props.addProperty("RpsTicket", "d=" + msAccess);
        xblReq.add("Properties", props);
        xblReq.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblReq.addProperty("TokenType", "JWT");
        HttpResponse<String> xblResp = postJson(endpoints.xboxUser(), xblReq, null, "Xbox Live");
        if (xblResp.statusCode() != 200) {
            throw new AuthException(AuthException.Reason.XBOX_OTHER,
                    "Xbox-Live-Anmeldung fehlgeschlagen (HTTP " + xblResp.statusCode() + ").");
        }
        JsonObject xbl = parse(xblResp.body());
        String xblToken = str(xbl, "Token");
        Redactor.registerSecret(xblToken);
        String uhs = userHash(xbl);

        // XSTS
        JsonObject xstsReq = new JsonObject();
        JsonObject xp = new JsonObject();
        xp.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        xp.add("UserTokens", userTokens);
        xstsReq.add("Properties", xp);
        xstsReq.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsReq.addProperty("TokenType", "JWT");
        HttpResponse<String> xstsResp = postJson(endpoints.xsts(), xstsReq, null, "XSTS");
        if (xstsResp.statusCode() == 401) {
            throw xstsError(parseOrEmpty(xstsResp.body()));
        }
        if (xstsResp.statusCode() != 200) {
            throw new AuthException(AuthException.Reason.XBOX_OTHER,
                    "XSTS-Autorisierung fehlgeschlagen (HTTP " + xstsResp.statusCode() + ").");
        }
        JsonObject xsts = parse(xstsResp.body());
        String xstsToken = str(xsts, "Token");
        Redactor.registerSecret(xstsToken);

        // Minecraft Services
        JsonObject mcReq = new JsonObject();
        mcReq.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        HttpResponse<String> mcResp = postJson(endpoints.mcLogin(), mcReq, null, "Minecraft login");
        if (mcResp.statusCode() == 403) {
            throw new AuthException(AuthException.Reason.APP_NOT_APPROVED,
                    "Minecraft Services hat die Anmeldung abgelehnt (HTTP 403). Die konfigurierte Azure-App-ID ist "
                            + "vermutlich nicht von Mojang für die Minecraft-API freigeschaltet (siehe docs/TROUBLESHOOTING.md).");
        }
        if (mcResp.statusCode() != 200) {
            throw new AuthException(AuthException.Reason.INVALID_RESPONSE,
                    "Minecraft-Anmeldung fehlgeschlagen (HTTP " + mcResp.statusCode() + ").");
        }
        JsonObject mc = parse(mcResp.body());
        String mcToken = str(mc, "access_token");
        Redactor.registerSecret(mcToken);
        long mcExpires = mc.has("expires_in") ? mc.get("expires_in").getAsLong() : 86400;

        verifyOwnership(mcToken);
        HttpResponse<String> profileResp = get(endpoints.mcProfile(), mcToken, "profile");
        if (profileResp.statusCode() == 404) {
            throw new AuthException(AuthException.Reason.NO_PROFILE,
                    "Für dieses Konto existiert kein Minecraft-Profil. Bitte zuerst im offiziellen Launcher "
                            + "bzw. auf minecraft.net einen Spielernamen festlegen.");
        }
        if (profileResp.statusCode() != 200) {
            throw new AuthException(AuthException.Reason.INVALID_RESPONSE,
                    "Minecraft-Profil konnte nicht geladen werden (HTTP " + profileResp.statusCode() + ").");
        }
        JsonObject profile = parse(profileResp.body());
        String name = str(profile, "name");
        String id = str(profile, "id");
        String xuid = xuidFromJwt(mcToken);
        MinecraftSession session = new MinecraftSession(name, id, xuid, mcToken,
                clock.instant().plusSeconds(mcExpires), msRefresh);
        Log.info("Signed in as " + name);
        return session;
    }

    private void verifyOwnership(String mcToken) throws AuthException {
        HttpResponse<String> resp = get(endpoints.mcEntitlements(), mcToken, "entitlements");
        if (resp.statusCode() != 200) {
            throw new AuthException(AuthException.Reason.INVALID_RESPONSE,
                    "Lizenzprüfung fehlgeschlagen (HTTP " + resp.statusCode() + ").");
        }
        JsonObject ent = parse(resp.body());
        boolean owned = false;
        if (ent.has("items") && ent.get("items").isJsonArray()) {
            for (JsonElement item : ent.getAsJsonArray("items")) {
                String n = item.getAsJsonObject().has("name") ? item.getAsJsonObject().get("name").getAsString() : "";
                if (n.equals("game_minecraft") || n.equals("product_minecraft")) {
                    owned = true;
                    break;
                }
            }
        }
        if (!owned) {
            throw new AuthException(AuthException.Reason.NOT_OWNED,
                    "Dieses Microsoft-Konto besitzt keine Lizenz für Minecraft: Java Edition.");
        }
    }

    static AuthException xstsError(JsonObject body) {
        long code = body.has("XErr") ? body.get("XErr").getAsLong() : 0;
        if (code == 2148916233L) {
            return new AuthException(AuthException.Reason.NO_XBOX_ACCOUNT,
                    "Dieses Microsoft-Konto hat kein Xbox-Profil. Bitte einmal auf xbox.com anmelden und ein Profil anlegen.");
        }
        if (code == 2148916235L) {
            return new AuthException(AuthException.Reason.XBOX_REGION_BLOCKED,
                    "Xbox Live ist in deinem Land/deiner Region nicht verfügbar.");
        }
        if (code == 2148916236L || code == 2148916237L) {
            return new AuthException(AuthException.Reason.XBOX_OTHER,
                    "Für dieses Konto ist eine Altersverifizierung bei Xbox erforderlich (xbox.com).");
        }
        if (code == 2148916238L) {
            return new AuthException(AuthException.Reason.CHILD_ACCOUNT,
                    "Kinderkonto: Ein Erwachsener muss das Konto zu einer Microsoft-Familie hinzufügen.");
        }
        return new AuthException(AuthException.Reason.XBOX_OTHER, "XSTS-Autorisierung abgelehnt (XErr " + code + ").");
    }

    private AuthException msError(JsonObject body) {
        String err = body.has("error") ? body.get("error").getAsString() : "unknown";
        String desc = body.has("error_description") ? body.get("error_description").getAsString() : "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("AADSTS\\d+").matcher(desc);
        String code = m.find() ? m.group() : err;
        if ("invalid_client".equals(err) || "unauthorized_client".equals(err) || desc.contains("AADSTS700016")) {
            return new AuthException(AuthException.Reason.NOT_CONFIGURED,
                    "Die konfigurierte Azure-App-ID wird von Microsoft nicht akzeptiert (" + code + "). "
                            + "Prüfe, ob die App 'Persönliche Microsoft-Konten' und 'Public client flows' erlaubt.");
        }
        if ("temporarily_unavailable".equals(err)) {
            return new AuthException(AuthException.Reason.NETWORK,
                    "Microsoft meldet einen vorübergehenden Fehler (" + code + "). Tritt er dauerhaft auf, ist die "
                            + "Azure-App-ID meist nicht registriert oder nicht für persönliche Microsoft-Konten freigegeben "
                            + "(siehe docs/TROUBLESHOOTING.md).");
        }
        return new AuthException(AuthException.Reason.INVALID_RESPONSE, "Microsoft-Anmeldung fehlgeschlagen (" + code + ").");
    }

    private void requireClientId() throws AuthException {
        if (clientId == null || clientId.isBlank()) {
            throw new AuthException(AuthException.Reason.NOT_CONFIGURED,
                    "Es ist keine Azure-App-ID (Client-ID) für die Microsoft-Anmeldung konfiguriert. "
                            + "Trage sie in den Einstellungen ein oder setze LUMORA_MS_CLIENT_ID (siehe docs/BUILD.md).");
        }
    }

    private static String userHash(JsonObject xbl) throws AuthException {
        try {
            return xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
        } catch (RuntimeException e) {
            throw new AuthException(AuthException.Reason.INVALID_RESPONSE, "Unerwartete Xbox-Live-Antwort (uhs fehlt).", e);
        }
    }

    /** Reads the xuid claim from the (unverified) Minecraft JWT; used only as a launch argument. */
    static String xuidFromJwt(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return "0";
            }
            JsonObject payload = Json.GSON.fromJson(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8), JsonObject.class);
            return payload != null && payload.has("xuid") ? payload.get("xuid").getAsString() : "0";
        } catch (RuntimeException e) {
            return "0";
        }
    }

    private JsonObject postForm(URI uri, Map<String, String> form, String what) throws AuthException {
        String body = formEncode(form);
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", UserAgent.VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> resp = send(req, what);
        JsonObject o = parseOrEmpty(resp.body());
        if (o.has("error")) {
            // OAuth error document (also used by Microsoft with 5xx, e.g. AADSTS70012); mapped by the caller.
            return o;
        }
        if (resp.statusCode() >= 500) {
            throw new AuthException(AuthException.Reason.NETWORK, "Microsoft-Anmeldedienst nicht erreichbar (HTTP " + resp.statusCode() + ").");
        }
        if (resp.statusCode() != 200 && !o.has("error")) {
            throw new AuthException(AuthException.Reason.INVALID_RESPONSE, "Unerwartete Antwort beim Schritt '" + what + "' (HTTP " + resp.statusCode() + ").");
        }
        return o;
    }

    private HttpResponse<String> postJson(URI uri, JsonObject body, String bearer, String what) throws AuthException {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .header("User-Agent", UserAgent.VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(Json.GSON.toJson(body)));
        if (bearer != null) {
            b.header("Authorization", "Bearer " + bearer);
        }
        return send(b.build(), what);
    }

    private HttpResponse<String> get(URI uri, String bearer, String what) throws AuthException {
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + bearer).header("Accept", "application/json")
                .header("User-Agent", UserAgent.VALUE).GET().build();
        return send(req, what);
    }

    private HttpResponse<String> send(HttpRequest req, String what) throws AuthException {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AuthException(AuthException.Reason.NETWORK, "Netzwerkfehler bei '" + what + "': " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthException(AuthException.Reason.CANCELLED, "Anmeldung unterbrochen.", e);
        }
    }

    private static JsonObject parse(String body) throws AuthException {
        try {
            JsonObject o = Json.GSON.fromJson(body, JsonObject.class);
            if (o == null) {
                throw new AuthException(AuthException.Reason.INVALID_RESPONSE, "Leere Antwort vom Anmeldedienst.");
            }
            return o;
        } catch (JsonParseException | IllegalStateException e) {
            throw new AuthException(AuthException.Reason.INVALID_RESPONSE, "Ungültige Antwort vom Anmeldedienst.", e);
        }
    }

    private static JsonObject parseOrEmpty(String body) {
        try {
            JsonObject o = Json.GSON.fromJson(body, JsonObject.class);
            return o != null ? o : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private static String str(JsonObject o, String key) throws AuthException {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            throw new AuthException(AuthException.Reason.INVALID_RESPONSE, "Antwort des Anmeldedienstes ohne Feld '" + key + "'.");
        }
        return o.get(key).getAsString();
    }

    private static void sleep(Duration d) throws AuthException {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthException(AuthException.Reason.CANCELLED, "Anmeldung unterbrochen.", e);
        }
    }
}
