package dev.lumora.launcher.app.social;

import dev.lumora.launcher.app.AppContext;
import dev.lumora.launcher.core.BuildInfo;
import dev.lumora.launcher.core.auth.AccountStore;
import dev.lumora.launcher.core.auth.AuthException;
import dev.lumora.launcher.core.auth.MinecraftSession;
import dev.lumora.launcher.core.util.Log;
import dev.lumora.social.MojangSession;
import dev.lumora.social.Rules;
import dev.lumora.social.SocialClient;
import dev.lumora.social.SocialEvent;
import dev.lumora.social.model.Presence;
import dev.lumora.social.model.Profile;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Owns the launcher's Lumora Social connection: created from the configured server and the active Microsoft account,
 * recreated when either changes. The account is proven with the official Mojang session join (token refreshed silently
 * when needed); the token itself never leaves this process except to sessionserver.mojang.com.
 * Listeners run on the Swing thread.
 */
public final class SocialController {
    /** Test/screenshot only: "Name:uuid" connects without an account (accepted only by a server started with --dev-auth). */
    static final String DEV_PROFILE = "lumora.social.devProfile";

    private final AppContext ctx;
    private final List<Consumer<SocialEvent>> listeners = new CopyOnWriteArrayList<>();
    private SocialClient client;
    private String clientKey;

    public SocialController(AppContext ctx) {
        this.ctx = ctx;
    }

    /** Current client or null (no server configured or not signed in). Swing thread. */
    public SocialClient client() {
        return client;
    }

    public void addListener(Consumer<SocialEvent> l) {
        listeners.add(l);
    }

    /** Signed-in profile that would be used, or empty. */
    public Optional<Profile> profile() {
        String dev = System.getProperty(DEV_PROFILE);
        if (dev != null && dev.contains(":")) {
            String[] p = dev.split(":", 2);
            String uuid = Rules.uuid(p[1]);
            if (uuid != null && Rules.validName(p[0])) {
                return Optional.of(new Profile(uuid, p[0]));
            }
        }
        Optional<AccountStore.Account> a = ctx.accounts.activeAccount();
        if (a.isEmpty()) {
            return Optional.empty();
        }
        String uuid = Rules.uuid(a.get().uuid());
        return uuid == null || !Rules.validName(a.get().username()) ? Optional.empty() : Optional.of(new Profile(uuid, a.get().username()));
    }

    /** (Re)creates the client after a change of server or account. Swing thread. */
    public void apply() {
        Optional<Profile> p = profile();
        String server = ctx.config.socialServer;
        String key = server == null || p.isEmpty() ? null : server + "|" + p.get().uuid;
        if (key != null && key.equals(clientKey)) {
            if (client != null && client.status() == SocialClient.Status.REJECTED) {
                client.reconnect(); // e.g. signed in again after an expired session
            }
            return;
        }
        if (client != null) {
            SocialClient old = client;
            client = null;
            Thread t = new Thread(old::close, "lumora-social-close");
            t.setDaemon(true);
            t.start();
        }
        clientKey = key;
        if (key != null) {
            Profile me = p.get();
            client = new SocialClient(URI.create(server), new SocialClient.Identity() {
                @Override
                public Profile profile() {
                    return me;
                }

                @Override
                public void join(String serverId) throws IOException {
                    MinecraftSession s;
                    try {
                        s = ctx.play.freshSession(ctx.config);
                    } catch (AuthException e) {
                        throw new IOException("Anmeldung abgelaufen. Bitte im Launcher neu anmelden.", e);
                    }
                    if (s == null || !me.uuid.equals(Rules.uuid(s.uuid()))) {
                        throw new IOException("Keine gültige Minecraft-Sitzung. Bitte im Launcher anmelden.");
                    }
                    MojangSession.join(s.accessToken(), me.uuid, serverId);
                }
            }, "launcher", BuildInfo.VERSION, new Presence(Presence.State.LAUNCHER, "", ""));
            client.addListener(e -> SwingUtilities.invokeLater(() -> fire(e)));
            client.start();
            Log.info("Lumora Social: connecting to " + server + " as " + me.name);
        }
        fire(new SocialEvent(SocialEvent.Kind.STATUS, null, null, null));
    }

    /** After the user signed in again: retry a rejected login. */
    public void reconnect() {
        if (client != null) {
            client.reconnect();
        }
    }

    private void fire(SocialEvent e) {
        for (Consumer<SocialEvent> l : listeners) {
            l.accept(e);
        }
    }

    public void shutdown() {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
