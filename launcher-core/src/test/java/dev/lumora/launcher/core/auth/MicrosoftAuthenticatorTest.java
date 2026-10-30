package dev.lumora.launcher.core.auth;

import dev.lumora.launcher.core.testutil.LocalServer;
import dev.lumora.launcher.core.util.Redactor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the full Microsoft -> Xbox -> XSTS -> Minecraft chain against a local mock of each endpoint. */
class MicrosoftAuthenticatorTest {
    static final String CLIENT_ID = "01234567-89ab-cdef-0123-456789abcdef";
    static final String MC_JWT = "eyJhbGciOiJIUzI1NiJ9." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"xuid\":\"2535400000000000\",\"sub\":\"x\"}".getBytes(StandardCharsets.UTF_8)) + ".c2lnbmF0dXJlLXNpZw";

    LocalServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
        Redactor.clearSecrets();
    }

    MicrosoftAuthenticator auth(LocalServer s) {
        var ep = new MicrosoftAuthenticator.Endpoints(s.uri("/devicecode"), s.uri("/token"), s.uri("/xbl"), s.uri("/xsts"),
                s.uri("/mclogin"), s.uri("/entitlements"), s.uri("/profile"), s.uri("/authorize"));
        return new MicrosoftAuthenticator(HttpClient.newHttpClient(), ep, CLIENT_ID, Clock.systemUTC(), Duration.ofMillis(5));
    }

    LocalServer happyServer() throws Exception {
        return happyServer(true);
    }

    /** @param pendingFirst the first token poll answers authorization_pending (device code flow) */
    LocalServer happyServer(boolean pendingFirst) throws Exception {
        return new LocalServer()
                .on("/devicecode", (ex, n) -> LocalServer.Response.json(200,
                        "{\"device_code\":\"DEV\",\"user_code\":\"ABCD-EFGH\",\"verification_uri\":\"https://microsoft.com/link\",\"expires_in\":900,\"interval\":0}"))
                .on("/token", (ex, n) -> pendingFirst && n == 1
                        ? LocalServer.Response.json(400, "{\"error\":\"authorization_pending\"}")
                        : LocalServer.Response.json(200, "{\"access_token\":\"ms-access-token-xyz\",\"refresh_token\":\"ms-refresh-token-xyz\",\"expires_in\":3600}"))
                .on("/xbl", (ex, n) -> LocalServer.Response.json(200,
                        "{\"Token\":\"xbl-token-abcdef\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"UHS123\"}]}}"))
                .on("/xsts", (ex, n) -> LocalServer.Response.json(200,
                        "{\"Token\":\"xsts-token-abcdef\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"UHS123\"}]}}"))
                .on("/mclogin", (ex, n) -> LocalServer.Response.json(200, "{\"access_token\":\"" + MC_JWT + "\",\"expires_in\":86400}"))
                .on("/entitlements", (ex, n) -> LocalServer.Response.json(200, "{\"items\":[{\"name\":\"product_minecraft\"},{\"name\":\"game_minecraft\"}]}"))
                .on("/profile", (ex, n) -> LocalServer.Response.json(200, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}"));
    }

    @Test
    void fullDeviceCodeFlow() throws Exception {
        server = happyServer();
        AtomicReference<MicrosoftAuthenticator.DeviceCodePrompt> shown = new AtomicReference<>();
        MinecraftSession s = auth(server).signIn(shown::set, () -> false);
        assertEquals("ABCD-EFGH", shown.get().userCode());
        assertEquals("Notch", s.username());
        assertEquals("069a79f444e94726a5befca90e38aaf5", s.uuid());
        assertEquals("2535400000000000", s.xuid());
        assertEquals("ms-refresh-token-xyz", s.msRefreshToken());
        assertTrue(server.lastBody("/xsts").contains("rp://api.minecraftservices.com/"));
        assertTrue(server.lastBody("/mclogin").contains("XBL3.0 x=UHS123;xsts-token-abcdef"));
        assertEquals(2, server.count("/token"), "pending, then success");
        assertFalse(s.toString().contains(MC_JWT), "toString must not leak tokens");
        assertFalse(Redactor.redact("token=" + s.accessToken() + " refresh " + s.msRefreshToken()).contains("ms-refresh-token-xyz"));
    }

    @Test
    void refreshUsesStoredRefreshToken() throws Exception {
        server = happyServer(false);
        MinecraftSession old = new MinecraftSession("Notch", "u", "x", "old", java.time.Instant.EPOCH, "stored-refresh-token");
        MinecraftSession s = auth(server).refresh(old);
        assertNotNull(s.accessToken());
        assertTrue(server.lastBody("/token").contains("grant_type=refresh_token"));
        assertTrue(server.lastBody("/token").contains("stored-refresh-token"));
        assertEquals(0, server.count("/devicecode"));
    }

    @Test
    void expiredRefreshTokenRequiresInteractiveLogin() throws Exception {
        server = new LocalServer().on("/token", (ex, n) -> LocalServer.Response.json(400, "{\"error\":\"invalid_grant\"}"));
        MinecraftSession old = new MinecraftSession("Notch", "u", "x", "old", java.time.Instant.EPOCH, "dead-refresh-token");
        AuthException e = assertThrows(AuthException.class, () -> auth(server).refresh(old));
        assertEquals(AuthException.Reason.SESSION_INVALID, e.reason());
    }

    @Test
    void noXboxAccountGivesSpecificError() throws Exception {
        server = new LocalServer()
                .on("/devicecode", (ex, n) -> LocalServer.Response.json(200,
                        "{\"device_code\":\"D\",\"user_code\":\"U\",\"verification_uri\":\"https://microsoft.com/link\",\"expires_in\":60,\"interval\":0}"))
                .on("/token", (ex, n) -> LocalServer.Response.json(200, "{\"access_token\":\"ms-access-token-xyz\",\"refresh_token\":\"r-token-123\"}"))
                .on("/xbl", (ex, n) -> LocalServer.Response.json(200, "{\"Token\":\"xbl-token\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"U\"}]}}"))
                .on("/xsts", (ex, n) -> LocalServer.Response.json(401, "{\"XErr\":2148916233,\"Message\":\"\"}"));
        AuthException e = assertThrows(AuthException.class, () -> auth(server).signIn(p -> { }, () -> false));
        assertEquals(AuthException.Reason.NO_XBOX_ACCOUNT, e.reason());
    }

    @Test
    void unapprovedAzureAppIsExplained() throws Exception {
        server = new LocalServer()
                .on("/devicecode", (ex, n) -> LocalServer.Response.json(200,
                        "{\"device_code\":\"D\",\"user_code\":\"U\",\"verification_uri\":\"https://microsoft.com/link\",\"expires_in\":60,\"interval\":0}"))
                .on("/token", (ex, n) -> LocalServer.Response.json(200, "{\"access_token\":\"ms-access-token-xyz\",\"refresh_token\":\"r-token-123\"}"))
                .on("/xbl", (ex, n) -> LocalServer.Response.json(200, "{\"Token\":\"xbl-token\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"U\"}]}}"))
                .on("/xsts", (ex, n) -> LocalServer.Response.json(200, "{\"Token\":\"xsts-token\"}"))
                .on("/mclogin", (ex, n) -> LocalServer.Response.json(403, "{\"path\":\"/authentication/login_with_xbox\",\"errorMessage\":\"Invalid app registration\"}"));
        AuthException e = assertThrows(AuthException.class, () -> auth(server).signIn(p -> { }, () -> false));
        assertEquals(AuthException.Reason.APP_NOT_APPROVED, e.reason());
    }

    @Test
    void accountWithoutLicenseIsRejected() throws Exception {
        server = new LocalServer()
                .on("/devicecode", (ex, n) -> LocalServer.Response.json(200,
                        "{\"device_code\":\"D\",\"user_code\":\"U\",\"verification_uri\":\"https://microsoft.com/link\",\"expires_in\":60,\"interval\":0}"))
                .on("/token", (ex, n) -> LocalServer.Response.json(200, "{\"access_token\":\"ms-access-token-xyz\",\"refresh_token\":\"r-token-123\"}"))
                .on("/xbl", (ex, n) -> LocalServer.Response.json(200, "{\"Token\":\"xbl-token\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"U\"}]}}"))
                .on("/xsts", (ex, n) -> LocalServer.Response.json(200, "{\"Token\":\"xsts-token\"}"))
                .on("/mclogin", (ex, n) -> LocalServer.Response.json(200, "{\"access_token\":\"mc-token-123456\",\"expires_in\":86400}"))
                .on("/entitlements", (ex, n) -> LocalServer.Response.json(200, "{\"items\":[]}"));
        AuthException e = assertThrows(AuthException.class, () -> auth(server).signIn(p -> { }, () -> false));
        assertEquals(AuthException.Reason.NOT_OWNED, e.reason());
        assertEquals(0, server.count("/profile"));
    }

    @Test
    void declinedAndCancelled() throws Exception {
        server = new LocalServer()
                .on("/devicecode", (ex, n) -> LocalServer.Response.json(200,
                        "{\"device_code\":\"D\",\"user_code\":\"U\",\"verification_uri\":\"https://microsoft.com/link\",\"expires_in\":60,\"interval\":0}"))
                .on("/token", (ex, n) -> LocalServer.Response.json(400, "{\"error\":\"authorization_declined\"}"));
        assertEquals(AuthException.Reason.DECLINED,
                assertThrows(AuthException.class, () -> auth(server).signIn(p -> { }, () -> false)).reason());
        assertEquals(AuthException.Reason.CANCELLED,
                assertThrows(AuthException.class, () -> auth(server).signIn(p -> { }, () -> true)).reason());
    }

    /** Real response observed from login.microsoftonline.com for an unregistered client id (2026-10-15). */
    @Test
    void unknownClientIdTemporarilyUnavailableIsExplained() throws Exception {
        server = new LocalServer().on("/devicecode", (ex, n) -> LocalServer.Response.json(503,
                "{\"error\":\"temporarily_unavailable\",\"error_description\":\"AADSTS70012: The request could not be completed. Please try again later.\",\"error_codes\":[70012]}"));
        AuthException e = assertThrows(AuthException.class, () -> auth(server).signIn(p -> { }, () -> false));
        assertEquals(AuthException.Reason.NETWORK, e.reason());
        assertTrue(e.getMessage().contains("AADSTS70012"), e.getMessage());
        assertTrue(e.getMessage().contains("Azure-App-ID"));
    }

    @Test
    void missingClientIdIsAConfigurationError() {
        var a = new MicrosoftAuthenticator(null);
        AuthException e = assertThrows(AuthException.class, () -> a.signIn(p -> { }, () -> false));
        assertEquals(AuthException.Reason.NOT_CONFIGURED, e.reason());
    }

    @Test
    void networkErrorIsReported() {
        var ep = new MicrosoftAuthenticator.Endpoints(java.net.URI.create("http://127.0.0.1:1/devicecode"), null, null, null, null, null, null, null);
        var a = new MicrosoftAuthenticator(HttpClient.newHttpClient(), ep, CLIENT_ID, Clock.systemUTC(), Duration.ofMillis(5));
        assertEquals(AuthException.Reason.NETWORK, assertThrows(AuthException.class, () -> a.signIn(p -> { }, () -> false)).reason());
    }

    @Test
    void xuidFallbackForOpaqueTokens() {
        assertEquals("0", MicrosoftAuthenticator.xuidFromJwt("opaque"));
        assertEquals("2535400000000000", MicrosoftAuthenticator.xuidFromJwt(MC_JWT));
    }
}
