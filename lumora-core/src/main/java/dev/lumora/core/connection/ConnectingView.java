package dev.lumora.core.connection;

import dev.lumora.core.theme.LumoraTheme;
import dev.lumora.api.connection.ConnectionPhase;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.Platform;
import dev.lumora.core.browser.BrowserPlatform;
import dev.lumora.core.browser.ServerPinger;
import dev.lumora.core.social.GameSession;

import java.util.List;

/**
 * Lumora's connecting screen, shown while Minecraft's own connect screen keeps running underneath (it still drives
 * the connection). Shows the server with a live ping (players, version, MOTD), the route "you - resolved address -
 * server" like LiquidBounce's connect screen, every phase of the join with its time, the account and a cancel button.
 */
final class ConnectingView extends ConnectionView {
    /** Protocol of 1.20.2, the first version with a configuration phase. */
    static final int CONFIGURATION_PROTOCOL = 764;

    private final ConnectionTracker tracker;
    private final ConnectionAttempt attempt;
    private volatile ServerPinger.Status ping;
    private volatile boolean pingStarted;
    private boolean cancelled;

    ConnectingView(Platform platform, ConnectionModule module, ConnectionTracker tracker, ConnectionAttempt attempt) {
        super(platform, module);
        this.tracker = tracker;
        this.attempt = attempt;
    }

    private void startPing(String address) {
        if (pingStarted || address.isEmpty() || !module.pingPreview()) {
            return;
        }
        pingStarted = true;
        BrowserPlatform game = platform.browsers();
        int protocol = game == null ? -1 : game.protocolVersion();
        Thread t = new Thread(() -> ping = ServerPinger.ping(address, protocol), "lumora-connect-ping");
        t.setDaemon(true);
        t.start();
    }

    @Override
    void draw(HudGraphics g, int width, int height, int mx, int my) {
        String address = attempt.serverAddress();
        String name = attempt.serverName();
        startPing(address == null ? "" : address);
        int cw = Math.min(340, width - 24);
        // height from the content: title, info line(s), route, phases, footer
        ServerPinger.Status shownPing = ping;
        boolean motdLine = shownPing != null && shownPing.ok() && shownPing.motd() != null
                && !shownPing.motd().split("\n", 2)[0].isBlank();
        boolean configStep = platform.browsers() != null && platform.browsers().protocolVersion() >= CONFIGURATION_PROTOCOL;
        int ch = 10 + 11 + 10 + (motdLine ? 10 : 0) + 24 + (configStep ? 6 : 5) * 12 + 30;
        int cx = width / 2;
        int x = cx - cw / 2;
        // brand above the card when there is room, card pushed up on small windows
        int y = Math.max(height >= ch + 60 ? 44 : 6, (height - ch) / 2 + 10);
        if (y + ch > height - 4) {
            y = Math.max(4, height - ch - 4);
        }
        if (y >= 40) {
            brand(g, cx, y - 40, t("Verbinde mit dem Server", "Connecting to the server"));
        }
        g.card(x, y, cw, ch, 6, SURFACE, BORDER);

        // server and live ping
        int px = x + 12;
        int py = y + 10;
        String title = name == null || name.isEmpty() ? shown(address) : name;
        g.text("§l" + fit(g, title, cw - 90), px, py, TEXT, false);
        ServerPinger.Status s = shownPing;
        String pingText;
        int pingColor;
        if (!module.pingPreview()) {
            pingText = "";
            pingColor = MUTED;
        } else if (s == null) {
            pingText = t("Ping …", "Ping …");
            pingColor = MUTED;
        } else if (!s.ok()) {
            pingText = t("kein Ping", "no ping");
            pingColor = WARN;
        } else {
            pingText = s.pingMs() + " ms";
            pingColor = s.pingMs() < 80 ? OK : s.pingMs() < 180 ? WARN : FAIL;
        }
        g.rightText(pingText, x + cw - 12, py, pingColor, false);
        py += 11;
        if (s != null && s.ok()) {
            String info = s.online() + "/" + s.max() + t(" Spieler", " players") + "  ·  " + ServerPinger.Motd.strip(s.versionName());
            g.text(fit(g, info, cw - 24), px, py, MUTED, false);
            py += 10;
            String motd = s.motd() == null ? "" : s.motd().split("\n", 2)[0];
            if (!motd.isBlank()) {
                g.text(fit(g, motd, cw - 24), px, py, TEXT, false);
                py += 10;
            }
        } else {
            g.text(fit(g, shown(address), cw - 24), px, py, MUTED, false);
            py += 10;
        }

        // route: you <-> resolved socket <-> server (as LiquidBounce shows it)
        py += 4;
        String remote = tracker.remoteAddress();
        String route = "§9" + t("Du", "You") + " §8⟷ §6" + (remote.isEmpty() ? "…" : shown(remote)) + " §8⟷ §a" + shown(address);
        g.text(fit(g, route, cw - 24), px, py, TEXT, false);
        py += 14;
        g.fill(px, py, x + cw - 12, py + 1, BORDER);
        py += 6;

        // phases
        boolean config = configStep;
        ConnectionPhase now = tracker.phase();
        List<Object[]> steps = new java.util.ArrayList<>(List.of(
                new Object[]{ConnectionPhase.CONNECTING, t("Adresse auflösen und verbinden", "Resolve address and connect")},
                new Object[]{ConnectionPhase.LOGIN, t("Handshake und Login", "Handshake and login")},
                new Object[]{ConnectionPhase.AUTHENTICATING, t("Konto bei Mojang bestätigen", "Verify account with Mojang")},
                new Object[]{ConnectionPhase.ENCRYPTING, t("Verbindung verschlüsseln", "Encrypt the connection")}));
        if (config) {
            steps.add(new Object[]{ConnectionPhase.CONFIGURING, t("Konfiguration (Daten, Pakete)", "Configuration (data, packs)")});
        }
        steps.add(new Object[]{ConnectionPhase.JOINING, t("Welt laden", "Load the world")});
        for (Object[] step : steps) {
            ConnectionPhase p = (ConnectionPhase) step[0];
            String label = (String) step[1];
            boolean current = p == now;
            boolean done = now != null && now != ConnectionPhase.DISCONNECTED && p.ordinal() < now.ordinal();
            boolean skipped = done && !tracker.reached(p);
            int iconX = px + 4;
            if (current) {
                spinner(g, iconX + 2, py + 4, LumoraTheme.accentHover());
            } else if (skipped) {
                g.fill(iconX - 1, py + 3, iconX + 5, py + 5, DIM);
            } else if (done) {
                g.text("✔", iconX - 2, py, OK, false);
            } else {
                g.roundedRect(iconX - 1, py + 1, 6, 6, 3, 0xFF313D50);
            }
            int color = current ? TEXT : done && !skipped ? MUTED : DIM;
            g.text(skipped ? label + t(" (Offline-Server)", " (offline server)") : label, px + 16, py, color, false);
            long ms = tracker.millis(p);
            if ((current || done) && !skipped && ms >= 0) {
                g.rightText(duration(ms), x + cw - 12, py, current ? LumoraTheme.accentHover() : DIM, false);
            }
            py += 12;
        }

        // footer: account, attempt, elapsed, cancel
        int fy = y + ch - 24;
        String account = platform.session().map(GameSession::name).orElse("");
        String foot = (account.isEmpty() ? "" : t("als ", "as ") + account + "  ·  ") + duration(tracker.elapsedMillis())
                + (tracker.attemptNumber() > 0 ? "  ·  " + t("Versuch ", "attempt ") + (tracker.attemptNumber() + 1)
                + "/" + (tracker.policy().maxAttempts() + 1) : "");
        g.text(fit(g, foot, cw - 110), px, fy + 6, DIM, false);
        button(g, x + cw - 92, fy, 80, 18, t("Abbrechen", "Cancel"), Style.SECONDARY, mx, my, this::cancel);
    }

    private void cancel() {
        if (cancelled) {
            return;
        }
        cancelled = true;
        tracker.cancelled();
        attempt.cancel();
    }

    @Override
    public boolean closeRequested() {
        cancel(); // Escape cancels like the button (the attempt returns to the server list itself)
        return false;
    }

    static String fit(HudGraphics g, String s, int max) {
        if (g.textWidth(s) <= max) {
            return s;
        }
        String out = s;
        while (out.length() > 1 && g.textWidth(out + "…") > max) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }
}
