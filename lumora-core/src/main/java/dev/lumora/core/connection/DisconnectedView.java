package dev.lumora.core.connection;

import dev.lumora.api.connection.ConnectionPhase;
import dev.lumora.api.connection.DisconnectKind;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.Platform;
import dev.lumora.core.browser.BrowserPlatform;

import java.util.List;

/**
 * Lumora's disconnect screen: what happened (classified reason with a short explanation and what helps), the server's
 * own message, where the connection ended and how long it lasted, "Erneut verbinden" and, with automatic reconnect,
 * a countdown that can be stopped.
 */
final class DisconnectedView extends ConnectionView {
    private final ConnectionTracker tracker;
    private final ConnectionTracker.Ended ended;
    /** System time of the automatic reconnect, 0 = none (stopped or not planned). */
    private long reconnectAt;
    private boolean gone;

    DisconnectedView(Platform platform, ConnectionModule module, ConnectionTracker tracker, ConnectionTracker.Ended ended) {
        super(platform, module);
        this.tracker = tracker;
        this.ended = ended;
        this.reconnectAt = ended.reconnectIn() >= 0 && canReconnect() ? System.currentTimeMillis() + ended.reconnectIn() : 0;
    }

    private boolean canReconnect() {
        return platform.browsers() != null && !ended.connection().address().isEmpty();
    }

    @Override
    void draw(HudGraphics g, int width, int height, int mx, int my) {
        if (reconnectAt > 0 && System.currentTimeMillis() >= reconnectAt) {
            reconnectAt = 0;
            reconnect(true);
            return;
        }
        DisconnectKind kind = ended.kind();
        int cw = Math.min(360, width - 24);
        int cx = width / 2;
        int x = cx - cw / 2;
        List<String> message = ended.message().isBlank() ? List.of() : wrap(g, ended.message().strip(), cw - 28);
        if (message.size() > 6) {
            message = message.subList(0, 6);
        }
        List<String> hint = wrap(g, hint(kind), cw - 28);
        // title and server line (36), message, hint, then the button row (gap 10 + 18 + 8)
        int ch = 36 + message.size() * 10 + (message.isEmpty() ? 0 : 4) + hint.size() * 10 + 36;
        int y = Math.max(44, (height - ch) / 2 + 10);
        brand(g, cx, y - 40, t("Verbindung getrennt", "Disconnected"));
        g.card(x, y, cw, ch, 6, SURFACE, BORDER);
        int color = switch (kind) {
            case USER -> MUTED;
            case BANNED, WHITELIST, AUTH, OUTDATED, DUPLICATE_LOGIN, PROTOCOL -> FAIL;
            default -> WARN;
        };
        g.fill(x + 1, y + 1, x + 4, y + ch - 1, color);
        int px = x + 14;
        int py = y + 10;
        g.text("§l" + title(kind), px, py, color, false);
        py += 12;
        String server = ended.connection().name().isEmpty() ? shown(ended.connection().address()) : ended.connection().name();
        g.text(ConnectingView.fit(g, server + "  ·  " + where(), cw - 28), px, py, MUTED, false);
        py += 14;
        for (String line : message) {
            g.text(line, px, py, TEXT, false);
            py += 10;
        }
        if (!message.isEmpty()) {
            py += 4;
        }
        for (String line : hint) {
            g.text(line, px, py, DIM, false);
            py += 10;
        }

        int by = y + ch - 26;
        int bx = x + 12;
        if (canReconnect()) {
            button(g, bx, by, 112, 18, t("Erneut verbinden", "Reconnect"), Style.PRIMARY, mx, my, () -> reconnect(false));
            bx += 118;
        }
        button(g, bx, by, 84, 18, t("Zurück", "Back"), Style.SECONDARY, mx, my, this::back);
        int right = x + cw - 12;
        if (reconnectAt > 0) {
            long left = Math.max(0, reconnectAt - System.currentTimeMillis());
            String count = t("Neuer Versuch in ", "Retrying in ") + (left / 1000 + 1) + " s";
            button(g, right - 60, by, 60, 18, t("Stopp", "Stop"), Style.GHOST, mx, my, () -> reconnectAt = 0);
            g.rightText(count, right - 66, by + 5, ACCENT_HOVER, false);
        } else if (canReconnect() && kind.retryable()) {
            toggle(g, right, by + 4, t("Automatisch", "Automatic"), module.autoReconnect(), mx, my, () -> {
                module.setAutoReconnect(!module.autoReconnect());
                if (module.autoReconnect()) {
                    long delay = module.policy().delayMillis(kind, ended.attempt());
                    reconnectAt = delay >= 0 ? System.currentTimeMillis() + delay : 0;
                }
            });
        }
    }

    /** "beim Anmelden · nach 12 min" */
    private String where() {
        ConnectionPhase p = ended.lastPhase();
        String phase = p == null ? "" : switch (p) {
            case CONNECTING -> t("beim Verbinden", "while connecting");
            case LOGIN -> t("beim Login", "during login");
            case AUTHENTICATING -> t("bei der Kontoprüfung", "while verifying the account");
            case ENCRYPTING -> t("beim Verschlüsseln", "while encrypting");
            case CONFIGURING -> t("bei der Konfiguration", "during configuration");
            case JOINING -> t("beim Laden der Welt", "while loading the world");
            case PLAYING -> t("im Spiel", "while playing");
            case DISCONNECTED -> "";
        };
        long joined = ended.connection().joinedMillis();
        if (p == ConnectionPhase.PLAYING && joined > 0) {
            phase += t(" nach ", " after ") + duration(System.currentTimeMillis() - joined - sinceEnd());
        }
        return phase;
    }

    private final long shownAt = System.currentTimeMillis();

    private long sinceEnd() {
        return System.currentTimeMillis() - shownAt;
    }

    private String title(DisconnectKind k) {
        return switch (k) {
            case USER -> t("Verbindung beendet", "Connection closed");
            case TIMEOUT -> t("Zeitüberschreitung", "Timed out");
            case REFUSED -> t("Server nicht erreichbar", "Server not reachable");
            case UNKNOWN_HOST -> t("Adresse nicht gefunden", "Address not found");
            case LOST -> t("Verbindung verloren", "Connection lost");
            case AUTH -> t("Anmeldung fehlgeschlagen", "Login failed");
            case BANNED -> t("Gesperrt", "Banned");
            case WHITELIST -> t("Nicht auf der Whitelist", "Not whitelisted");
            case FULL -> t("Server voll", "Server full");
            case OUTDATED -> t("Falsche Version", "Wrong version");
            case THROTTLED -> t("Zu viele Verbindungsversuche", "Too many connection attempts");
            case SHUTDOWN -> t("Server wird neu gestartet", "Server restarting");
            case DUPLICATE_LOGIN -> t("Woanders angemeldet", "Logged in elsewhere");
            case KICKED -> t("Vom Server entfernt", "Kicked from the server");
            case PROTOCOL -> t("Netzwerkfehler", "Network error");
            case OTHER -> t("Verbindung getrennt", "Disconnected");
        };
    }

    private String hint(DisconnectKind k) {
        return switch (k) {
            case USER -> "";
            case TIMEOUT -> t("Der Server hat nicht rechtzeitig geantwortet. Prüfe deine Internetverbindung oder versuche es gleich noch einmal.",
                    "The server did not answer in time. Check your internet connection or try again in a moment.");
            case REFUSED -> t("Unter der Adresse läuft gerade kein Server, oder eine Firewall blockt die Verbindung. Stimmt der Port?",
                    "No server is running at this address right now, or a firewall blocks it. Is the port right?");
            case UNKNOWN_HOST -> t("Die Adresse gibt es nicht. Prüfe die Schreibweise in der Serverliste.",
                    "This address does not exist. Check the spelling in the server list.");
            case LOST -> t("Die Verbindung ist abgebrochen. Meist hilft es, erneut zu verbinden.",
                    "The connection dropped. Reconnecting usually helps.");
            case AUTH -> t("Dein Konto konnte nicht bestätigt werden. Starte das Spiel über den Lumora Launcher neu (er erneuert die Anmeldung) und prüfe, ob Mehrspieler in deinen Microsoft-Kontoeinstellungen erlaubt ist.",
                    "Your account could not be verified. Restart the game from the Lumora launcher (it renews the sign-in) and check that multiplayer is allowed in your Microsoft account settings.");
            case BANNED -> t("Der Server hat dich gesperrt. Nur das Team des Servers kann das aufheben.",
                    "The server banned you. Only the server's staff can lift it.");
            case WHITELIST -> t("Der Server lässt nur freigeschaltete Spieler herein. Frag das Team nach einer Freischaltung.",
                    "The server only lets approved players in. Ask the staff to add you.");
            case FULL -> t("Alle Plätze sind belegt. Mit „Automatisch“ versucht Lumora es in Abständen erneut.",
                    "All slots are taken. With \"Automatic\" Lumora keeps trying at intervals.");
            case OUTDATED -> t("Server und Client sprechen verschiedene Versionen. Wähle im Launcher die passende Minecraft-Version.",
                    "Server and client use different versions. Pick the matching Minecraft version in the launcher.");
            case THROTTLED -> t("Der Server bremst schnelle Neuverbindungen. Warte ein paar Sekunden.",
                    "The server slows down quick reconnects. Wait a few seconds.");
            case SHUTDOWN -> t("Der Server fährt herunter oder startet neu. In ein paar Sekunden ist er meist wieder da.",
                    "The server is stopping or restarting. It is usually back within seconds.");
            case DUPLICATE_LOGIN -> t("Mit diesem Konto hat sich gerade jemand anderes (oder ein zweites Spiel) angemeldet.",
                    "Someone else (or a second game) just logged in with this account.");
            case KICKED -> t("Der Server hat die Verbindung beendet.", "The server closed the connection.");
            case PROTOCOL -> t("Server und Client haben sich nicht verstanden. Oft liegt es an einer Mod oder einem Plugin.",
                    "Server and client did not understand each other. Often a mod or plugin is the cause.");
            case OTHER -> "";
        };
    }

    private void reconnect(boolean automatic) {
        BrowserPlatform game = platform.browsers();
        if (game == null || gone) {
            return;
        }
        gone = true;
        if (automatic) {
            tracker.markReconnect();
        }
        String address = ended.connection().address();
        String name = ended.connection().name();
        platform.views().close(this);
        game.join(address, name.isEmpty() ? address : name);
    }

    private void back() {
        gone = true;
        reconnectAt = 0;
        platform.views().close(this);
    }

    @Override
    public boolean closeRequested() {
        reconnectAt = 0;
        gone = true;
        return true;
    }

    @Override
    public void removed() {
        reconnectAt = 0;
    }
}
