package dev.lumora.launcher.core.auth;

import dev.lumora.launcher.core.testutil.LocalServer;
import dev.lumora.launcher.core.util.Redactor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browser sign-in (authorization code + PKCE + loopback redirect). A simulated browser follows the
 * redirect exactly like a real one would: it calls http://localhost:<port>/?code=..&state=..
 */
class BrowserLoginTest {
    static final String CLIENT_ID = "01234567-89ab-cdef-0123-456789abcdef";
    LocalServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
        Redactor.clearSecrets();
    }

    MicrosoftAuthenticator auth() {
        var ep = new MicrosoftAuthenticator.Endpoints(server.uri("/devicecode"), server.uri("/token"), server.uri("/xbl"),
                server.uri("/xsts"), server.uri("/mclogin"), server.uri("/entitlements"), server.uri("/profile"), server.uri("/authorize"));
        return new MicrosoftAuthenticator(HttpClient.newHttpClient(), ep, CLIENT_ID, Clock.systemUTC(), Duration.ofMillis(5));
    }

    /** Xbox / XSTS / Minecraft part of the chain (same as the device code tests). */
    LocalServer minecraftChain(LocalServer s) {
        return s.on("/xbl", (ex, n) -> LocalServer.Response.json(200, "{\"Token\":\"xbl-token-abcdef\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"UHS1\"}]}}"))
                .on("/xsts", (ex, n) -> LocalServer.Response.json(200, "{\"Token\":\"xsts-token-abcdef\"}"))
                .on("/mclogin", (ex, n) -> LocalServer.Response.json(200, "{\"access_token\":\"mc-token-abcdef123\",\"expires_in\":86400}"))
                .on("/entitlements", (ex, n) -> LocalServer.Response.json(200, "{\"items\":[{\"name\":\"game_minecraft\"}]}"))
                .on("/profile", (ex, n) -> LocalServer.Response.json(200, "{\"id\":\"abc123\",\"name\":\"Alex\"}"));
    }

    /** Simulated browser: follows the redirect to the launcher's loopback receiver. */
    static int browserRedirect(URI authorizeUrl, String extraQuery) throws Exception {
        Map<String, String> q = LoopbackReceiver.parseQuery(authorizeUrl.getRawQuery());
        String target = q.get("redirect_uri") + "/?" + extraQuery.replace("{state}", q.get("state"));
        HttpResponse<String> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(target)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return r.statusCode();
    }

    @Test
    void browserSignInWithPkce() throws Exception {
        AtomicReference<Map<String, String>> authorizeQuery = new AtomicReference<>();
        server = minecraftChain(new LocalServer().on("/token", (ex, n) -> {
            Map<String, String> form = LoopbackReceiver.parseQuery(server.lastBody("/token"));
            Map<String, String> aq = authorizeQuery.get();
            String expectedChallenge = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MicrosoftAuthenticator.sha256(form.get("code_verifier")));
            boolean ok = "authorization_code".equals(form.get("grant_type")) && "THE-CODE".equals(form.get("code"))
                    && aq.get("redirect_uri").equals(form.get("redirect_uri")) && expectedChallenge.equals(aq.get("code_challenge"));
            return ok ? LocalServer.Response.json(200, "{\"access_token\":\"ms-access-abcdef\",\"refresh_token\":\"ms-refresh-abcdef\"}")
                    : LocalServer.Response.json(400, "{\"error\":\"invalid_grant\",\"error_description\":\"pkce mismatch\"}");
        }));
        AtomicInteger browserStatus = new AtomicInteger();
        MinecraftSession s = auth().signInWithBrowser(url -> {
            Map<String, String> q = LoopbackReceiver.parseQuery(url.getRawQuery());
            authorizeQuery.set(q);
            assertEquals(server.uri("/authorize").getPath(), url.getPath());
            assertEquals(CLIENT_ID, q.get("client_id"));
            assertEquals("code", q.get("response_type"));
            assertEquals("S256", q.get("code_challenge_method"));
            assertEquals("XboxLive.signin offline_access", q.get("scope"));
            assertTrue(q.get("redirect_uri").startsWith("http://localhost:"));
            new Thread(() -> {
                try {
                    browserStatus.set(browserRedirect(url, "code=THE-CODE&state={state}"));
                } catch (Exception e) {
                    browserStatus.set(-1);
                }
            }).start();
        }, () -> false);
        assertEquals("Alex", s.username());
        assertEquals("ms-refresh-abcdef", s.msRefreshToken());
        assertEquals(200, browserStatus.get(), "the browser gets a success page");
        assertFalse(server.lastBody("/token").contains("client_secret"), "public client: no secret");
    }

    @Test
    void foreignStateIsRejectedAndDoesNotCompleteLogin() throws Exception {
        server = minecraftChain(new LocalServer().on("/token", (ex, n) -> LocalServer.Response.json(500, "{}")));
        AtomicBoolean cancel = new AtomicBoolean();
        AtomicInteger status = new AtomicInteger();
        AuthException e = assertThrows(AuthException.class, () -> auth().signInWithBrowser(url -> new Thread(() -> {
            try {
                status.set(browserRedirect(url, "code=INJECTED&state=not-the-state"));
            } catch (Exception ex) {
                status.set(-1);
            }
            cancel.set(true);
        }).start(), cancel::get));
        assertEquals(AuthException.Reason.CANCELLED, e.reason());
        assertEquals(400, status.get());
        assertEquals(0, server.count("/token"), "an injected code must never be redeemed");
    }

    @Test
    void cancelledInBrowserIsDeclined() throws Exception {
        server = minecraftChain(new LocalServer().on("/token", (ex, n) -> LocalServer.Response.json(500, "{}")));
        AuthException e = assertThrows(AuthException.class, () -> auth().signInWithBrowser(url -> new Thread(() -> {
            try {
                browserRedirect(url, "error=access_denied&error_description=user+cancelled&state={state}");
            } catch (Exception ignored) {
                // assertion below
            }
        }).start(), () -> false));
        assertEquals(AuthException.Reason.DECLINED, e.reason());
        assertEquals(0, server.count("/token"));
    }

    @Test
    void browserFailureIsReported() throws Exception {
        server = new LocalServer();
        AuthException e = assertThrows(AuthException.class, () -> auth().signInWithBrowser(url -> {
            throw new java.io.IOException("no browser");
        }, () -> false));
        assertEquals(AuthException.Reason.NETWORK, e.reason());
    }

    @Test
    void queryParsing() {
        Map<String, String> q = LoopbackReceiver.parseQuery("code=a%2Bb&state=x&empty=&flag");
        assertEquals("a+b", q.get("code"));
        assertEquals("", q.get("empty"));
        assertEquals("", q.get("flag"));
    }
}
