package dev.lumora.launcher.core;

import dev.lumora.launcher.core.auth.AuthException;
import dev.lumora.launcher.core.auth.MicrosoftAuthenticator;
import dev.lumora.launcher.core.auth.MinecraftSession;
import dev.lumora.launcher.core.auth.AccountStore;
import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.diagnostics.CrashAnalyzer;
import dev.lumora.launcher.core.install.ClientInstaller;
import dev.lumora.launcher.core.install.ProgressListener;
import dev.lumora.launcher.core.launch.GameProcess;
import dev.lumora.launcher.core.launch.LaunchCommandBuilder;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.util.Log;
import dev.lumora.launcher.core.util.OsInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * The "Play" flow: install/repair -> ensure a valid Minecraft session -> build command -> start
 * and supervise the game. One game at a time per launcher; the instance file lock additionally
 * prevents parallel starts from a second launcher window.
 */
public final class PlayService {
    public enum State { IDLE, INSTALLING, LOGGING_IN, LAUNCHING, PLAYING, ERROR }

    public interface Listener extends ProgressListener {
        void state(State state, String message);

        void gameOutput(String line);

        /** Called when the game ended; hints may be empty. */
        default void gameExited(GameProcess.Exit exit, List<CrashAnalyzer.Hint> hints) {
        }
    }

    public enum Mode {
        /** Normal start with a Microsoft account. */
        ACCOUNT,
        /**
         * Automated smoke test: starts the official demo mode (is_demo_user feature, no account) and asks the
         * companion mod to write a report and quit once the title screen is reached. Not exposed in the UI.
         */
        SMOKE_TEST_DEMO
    }

    private final LauncherPaths paths;
    private final ClientInstaller installer;
    private final AccountStore sessions;
    private final OsInfo os;
    private final AtomicReference<GameProcess> running = new AtomicReference<>();
    private volatile State state = State.IDLE;
    private final dev.lumora.launcher.core.stats.PlayStats stats;

    private final dev.lumora.launcher.core.history.ConnectionHistory history;

    public PlayService(LauncherPaths paths, ClientInstaller installer, AccountStore sessions, OsInfo os) {
        this(paths, installer, sessions, os, new dev.lumora.launcher.core.history.ConnectionHistory(paths.connectionHistory()));
    }

    public PlayService(LauncherPaths paths, ClientInstaller installer, AccountStore sessions, OsInfo os,
                       dev.lumora.launcher.core.history.ConnectionHistory history) {
        this.history = history;
        this.paths = paths;
        this.stats = new dev.lumora.launcher.core.stats.PlayStats(paths.playStats());
        this.installer = installer;
        this.sessions = sessions;
        this.os = os;
    }

    /** Local sign-in and server history. */
    public dev.lumora.launcher.core.history.ConnectionHistory history() {
        return history;
    }

    /** Play time per profile (recorded when a real game session ends). */
    public dev.lumora.launcher.core.stats.PlayStats stats() {
        return stats;
    }

    public State state() {
        return state;
    }

    public Optional<GameProcess> runningGame() {
        return Optional.ofNullable(running.get()).filter(GameProcess::isAlive);
    }

    public static String effectiveClientId(LauncherConfig config) {
        String env = System.getenv("LUMORA_MS_CLIENT_ID");
        if (config.microsoftClientId != null) {
            return config.microsoftClientId;
        }
        return LauncherConfig.isValidClientId(env) ? env.strip() : null;
    }

    /**
     * Runs the whole flow on the calling (background) thread and returns once the game process started.
     *
     * @param interactiveLogin used when neither a valid stored session nor a refresh token is available
     * @param smokeReport      only for {@link Mode#SMOKE_TEST_DEMO}: where the companion mod writes its report
     */
    public GameProcess play(ProfileCatalog.Profile profile, LauncherConfig config, Mode mode, boolean repair,
                            InteractiveLogin interactiveLogin, BooleanSupplier cancelled,
                            Path smokeReport, Listener listener) throws IOException, AuthException {
        return play(profile, config, mode, repair, interactiveLogin, cancelled, smokeReport, null, listener);
    }

    /** @param quickPlay optional: start straight into a world or onto a server (Minecraft Quick Play) */
    public GameProcess play(ProfileCatalog.Profile profile, LauncherConfig config, Mode mode, boolean repair,
                            InteractiveLogin interactiveLogin, BooleanSupplier cancelled,
                            Path smokeReport, LaunchCommandBuilder.QuickPlay quickPlay, Listener listener) throws IOException, AuthException {
        return play(profile, dev.lumora.launcher.core.profile.Flavor.LUMORA, config, mode, repair, interactiveLogin, cancelled,
                smokeReport, quickPlay, listener);
    }

    /** @param flavor Lumora (Minecraft with the Lumora client via the Lumora Loader) or Vanilla for the profile's Minecraft version */
    public GameProcess play(ProfileCatalog.Profile profile, dev.lumora.launcher.core.profile.Flavor flavor, LauncherConfig config,
                            Mode mode, boolean repair, InteractiveLogin interactiveLogin, BooleanSupplier cancelled,
                            Path smokeReport, LaunchCommandBuilder.QuickPlay quickPlay, Listener listener) throws IOException, AuthException {
        String instanceId = flavor.instanceId(profile.id);
        if (runningGame().isPresent() && !config.multipleInstances) {
            throw new GameProcess.AlreadyRunningException(instanceId);
        }
        try {
            setState(listener, State.INSTALLING, "Prüfe Installation…");
            ClientInstaller.Prepared prepared = installer.prepare(profile, flavor, config, repair, listener);
            if (flavor == dev.lumora.launcher.core.profile.Flavor.LUMORA) {
                dev.lumora.launcher.core.install.GameThemeSeeder.seed(prepared.layout().lumoraDir(), config);
            }
            LaunchCommandBuilder.Identity identity;
            if (mode == Mode.SMOKE_TEST_DEMO) {
                identity = new LaunchCommandBuilder.Identity("DemoSmokeTest",
                        UUID.nameUUIDFromBytes("lumora-smoke".getBytes()).toString().replace("-", ""), "0", "0", "msa", true);
            } else {
                setState(listener, State.LOGGING_IN, "Anmeldung wird geprüft…");
                MinecraftSession session = ensureSession(config, interactiveLogin, cancelled);
                history.record(dev.lumora.launcher.core.history.ConnectionHistory.Kind.GAME_START, session.uuid(), session.username(),
                        instanceId, Instant.now());
                identity = new LaunchCommandBuilder.Identity(session.username(), session.uuid(), session.accessToken(),
                        session.xuid(), "msa", false);
            }

            setState(listener, State.LAUNCHING, "Minecraft wird gestartet…");
            List<String> sysProps = new ArrayList<>();
            sysProps.add("-Dlumora.launcher.version=" + BuildInfo.VERSION);
            sysProps.add("-Dlumora.profile=" + instanceId);
            // Lumora settings and addons: shared by all versions or the instance's own (InstanceLayout)
            sysProps.add("-Dlumora.dir=" + prepared.layout().lumoraDir().toAbsolutePath());
            sysProps.add("-Dlumora.instanceDir=" + prepared.layout().versionDir().toAbsolutePath());
            sysProps.add("-Dlumora.diagnosticsDir=" + paths.diagnostics().toAbsolutePath());
            // the game reports menu / singleplayer / server there (Discord Rich Presence); a stale file of the last run goes first
            Path statusFile = dev.lumora.launcher.core.integrations.GameStatus.file(prepared.layout().versionDir());
            java.nio.file.Files.deleteIfExists(statusFile);
            sysProps.add("-Dlumora.statusFile=" + statusFile.toAbsolutePath());
            if (mode == Mode.SMOKE_TEST_DEMO && smokeReport != null) {
                sysProps.add("-Dlumora.smokeTest=" + smokeReport.toAbsolutePath());
            }
            if (config.socialServer != null) {
                // the address only; the game proves the account itself with its own session (no token on the command line)
                sysProps.add("-Dlumora.social.server=" + config.socialServer);
                sysProps.add("-Dlumora.social.shareServer=" + config.socialShareServer);
            }
            List<String> jvmArgs = new ArrayList<>(config.extraJvmArgs);
            jvmArgs.addAll(dev.lumora.launcher.core.launch.LaunchOptions.jvmArgs(config, paths.diagnostics()));
            List<String> cmd = LaunchCommandBuilder.build(new LaunchCommandBuilder.Request(
                    prepared.version(), prepared.javaExecutable(), paths.libraries(), prepared.clientJar(),
                    paths.assets(), prepared.gameDir(), prepared.nativesDir(), prepared.loggingConfig(), identity,
                    config.minMemoryMb, config.maxMemoryMb, jvmArgs,
                    config.windowWidth, config.windowHeight, sysProps, quickPlay, prepared.loader()), os);

            cmd = new ArrayList<>(cmd);
            cmd.addAll(dev.lumora.launcher.core.launch.LaunchOptions.gameArgs(config));
            String player = identity.username();
            boolean track = mode != Mode.SMOKE_TEST_DEMO;
            GameProcess game = GameProcess.start(cmd, prepared.gameDir(), instanceId,
                    dev.lumora.launcher.core.launch.LaunchOptions.environment(config), line -> {
                        listener.gameOutput(line);
                        String server = track ? dev.lumora.launcher.core.history.ConnectionHistory.serverFromLog(line) : null;
                        if (server != null) {
                            history.recordServer(server, player, instanceId, Instant.now());
                        }
                    });
            java.time.Instant started = java.time.Instant.now();
            game.statusFile(statusFile);
            running.set(game);
            setState(listener, State.PLAYING, "Spiel läuft (PID " + game.pid() + ")");
            game.onExit().thenAccept(original -> {
                running.compareAndSet(game, null);
                GameProcess.Exit exit = original;
                if (original.crashed() && original.crashReport().isEmpty()) {
                    // no crash report from Minecraft: keep the end of the output so the crash page can show it
                    try {
                        Path record = dev.lumora.launcher.core.crash.CrashReports.recordExit(prepared.gameDir(), instanceId,
                                original.code(), original.outputLog(), java.time.Instant.now());
                        exit = new GameProcess.Exit(original.code(), Optional.of(record), original.outputLog());
                    } catch (IOException e) {
                        Log.warn("Could not record crash: " + e.getMessage());
                    }
                }
                if (mode != Mode.SMOKE_TEST_DEMO) {
                    stats.record(instanceId, started, java.time.Instant.now());
                }
                List<CrashAnalyzer.Hint> hints = exit.crashed()
                        ? CrashAnalyzer.analyze(exit.outputLog(), exit.crashReport()) : List.of();
                if (exit.crashed()) {
                    setState(listener, State.ERROR, "Minecraft wurde mit Fehlercode " + exit.code() + " beendet.");
                } else {
                    setState(listener, State.IDLE, "Bereit");
                }
                listener.gameExited(exit, hints);
            });
            return game;
        } catch (IOException | AuthException | RuntimeException e) {
            setState(listener, State.ERROR, e.getMessage());
            throw e;
        }
    }

    /**
     * How the user signs in interactively (the UI chooses: browser redirect, device code, or both).
     * Implementations call {@link MicrosoftAuthenticator#signInWithBrowser} or {@link MicrosoftAuthenticator#signIn}.
     */
    @FunctionalInterface
    public interface InteractiveLogin {
        MinecraftSession login(MicrosoftAuthenticator auth, BooleanSupplier cancelled) throws AuthException;
    }

    private MinecraftSession ensureSession(LauncherConfig config, InteractiveLogin login,
                                           BooleanSupplier cancelled) throws AuthException, IOException {
        MinecraftSession stored = sessions.active();
        if (stored != null && stored.accessTokenValid(Instant.now())) {
            Log.info("Using stored session for " + stored.username());
            return stored;
        }
        MicrosoftAuthenticator auth = new MicrosoftAuthenticator(effectiveClientId(config));
        MinecraftSession fresh = null;
        if (stored != null && stored.msRefreshToken() != null) {
            try {
                fresh = auth.refresh(stored);
                history.record(dev.lumora.launcher.core.history.ConnectionHistory.Kind.REFRESH, fresh.uuid(), fresh.username(), "", Instant.now());
            } catch (AuthException e) {
                if (e.reason() != AuthException.Reason.SESSION_INVALID) {
                    throw e;
                }
                Log.info("Stored refresh token rejected, interactive sign-in required");
                sessions.removeActive();
            }
        }
        if (fresh == null) {
            fresh = login.login(auth, cancelled);
            history.record(dev.lumora.launcher.core.history.ConnectionHistory.Kind.SIGN_IN, fresh.uuid(), fresh.username(), "", Instant.now());
        }
        sessions.save(fresh);
        return fresh;
    }

    /**
     * The active account with a valid Minecraft token, refreshed silently if needed (no dialog); null when signed out
     * or when only an interactive sign-in would help. Used by Lumora Social for the Mojang session join.
     */
    public synchronized MinecraftSession freshSession(LauncherConfig config) throws AuthException, IOException {
        MinecraftSession stored = sessions.active();
        if (stored == null) {
            return null;
        }
        if (stored.accessTokenValid(Instant.now())) {
            return stored;
        }
        String clientId = effectiveClientId(config);
        if (stored.msRefreshToken() == null || clientId == null) {
            return null;
        }
        MinecraftSession fresh = new MicrosoftAuthenticator(clientId).refresh(stored);
        sessions.save(fresh);
        history.record(dev.lumora.launcher.core.history.ConnectionHistory.Kind.REFRESH, fresh.uuid(), fresh.username(), "", Instant.now());
        return fresh;
    }

    /** Sign-in without launching (account button in the UI). */
    public MinecraftSession signIn(LauncherConfig config, InteractiveLogin login,
                                   BooleanSupplier cancelled) throws AuthException, IOException {
        MinecraftSession s = login.login(new MicrosoftAuthenticator(effectiveClientId(config)), cancelled);
        sessions.save(s);
        history.record(dev.lumora.launcher.core.history.ConnectionHistory.Kind.SIGN_IN, s.uuid(), s.username(), "", Instant.now());
        return s;
    }

    public void signOut() throws IOException {
        sessions.activeAccount().ifPresent(a -> history.record(dev.lumora.launcher.core.history.ConnectionHistory.Kind.SIGN_OUT,
                a.uuid(), a.username(), "", Instant.now()));
        sessions.removeActive();
    }

    /** All signed-in accounts (switch, add, remove). */
    public AccountStore accounts() {
        return sessions;
    }

    public MinecraftSession storedSession() {
        return sessions.active();
    }

    private void setState(Listener l, State s, String message) {
        state = s;
        Log.info("State " + s + ": " + message);
        l.state(s, message);
    }
}
