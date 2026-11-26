package dev.lumora.launcher.core;

import dev.lumora.launcher.core.auth.AuthException;
import dev.lumora.launcher.core.auth.MicrosoftAuthenticator;
import dev.lumora.launcher.core.auth.MinecraftSession;
import dev.lumora.launcher.core.auth.AccountStore;
import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.diagnostics.CrashAnalyzer;
import dev.lumora.launcher.core.i18n.Lang;
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
    /** Running games, the newest last (several with "multiple instances"). */
    private final java.util.List<GameProcess> running = new java.util.concurrent.CopyOnWriteArrayList<>();
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

    /** The newest running game. */
    public Optional<GameProcess> runningGame() {
        for (int i = running.size() - 1; i >= 0; i--) {
            GameProcess g = running.get(i);
            if (g.isAlive()) {
                return Optional.of(g);
            }
        }
        return Optional.empty();
    }

    /** All running games, the newest last. */
    public java.util.List<GameProcess> runningGames() {
        return running.stream().filter(GameProcess::isAlive).toList();
    }

    /** Lumora's built-in Azure application ID (see AppIds). */
    public static String effectiveClientId(LauncherConfig config) {
        return AppIds.MICROSOFT_CLIENT_ID;
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
        Target target = new Target(flavor.instanceId(profile.id), profile.minecraftVersion,
                flavor == dev.lumora.launcher.core.profile.Flavor.LUMORA,
                () -> installer.prepare(profile, flavor, config, repair, listener),
                config.minMemoryMb, config.maxMemoryMb, config.windowWidth, config.windowHeight, List.of());
        return run(target, config, mode, interactiveLogin, cancelled, smokeReport, quickPlay, listener);
    }

    /**
     * Starts an instance (see InstanceManager): built-in Lumora/Vanilla instances like {@link #play}, own instances in
     * their own folder with their own loader, Java, memory and window settings (unset values: launcher settings).
     */
    public GameProcess playInstance(dev.lumora.launcher.core.instance.Instance instance, dev.lumora.launcher.core.InstanceLayout layout,
                                    ProfileCatalog catalog, LauncherConfig config, Mode mode, boolean repair,
                                    InteractiveLogin interactiveLogin, BooleanSupplier cancelled,
                                    LaunchCommandBuilder.QuickPlay quickPlay, Listener listener) throws IOException, AuthException {
        dev.lumora.launcher.core.instance.InstanceKind kind = instance.kind();
        if (instance.builtIn) {
            ProfileCatalog.Profile profile = catalog.profile(instance.profileId).orElseThrow(() -> new IOException(instance.profileId));
            return play(profile, kind == dev.lumora.launcher.core.instance.InstanceKind.VANILLA
                    ? dev.lumora.launcher.core.profile.Flavor.VANILLA : dev.lumora.launcher.core.profile.Flavor.LUMORA,
                    config, mode, repair, interactiveLogin, cancelled, null, quickPlay, listener);
        }
        ClientInstallerCall prepare;
        List<String> extraSysProps = new ArrayList<>(); // filled while preparing (Lumora Client on a mod loader)
        boolean lumoraOnLoader = false;
        if (kind == dev.lumora.launcher.core.instance.InstanceKind.LUMORA) {
            ProfileCatalog.Profile profile = catalog.profiles().stream().filter(p -> p.minecraftVersion.equals(instance.minecraftVersion))
                    .findFirst().orElseThrow(() -> new IOException(Lang.t("instances.error.lumora_version", instance.minecraftVersion)));
            prepare = () -> installer.prepare(profile, dev.lumora.launcher.core.profile.Flavor.LUMORA, layout, instance.javaPath, config, repair, listener);
        } else {
            // the Lumora Client next to the mod loader (Fabric/Quilt/Forge/NeoForge), with the Java of its Lumora version
            String loaderId = kind.loaderId();
            ProfileCatalog.Profile lumora = instance.lumoraClient && installer.hasLumoraFor(loaderId, instance.minecraftVersion)
                    ? catalog.profiles().stream().filter(p -> p.minecraftVersion.equals(instance.minecraftVersion)).findFirst().orElse(null)
                    : null;
            prepare = () -> {
                ClientInstaller.Prepared p = installer.prepareGame(instance.minecraftVersion, loaderId, instance.loaderVersion, layout,
                        instance.javaPath, config, repair, listener, lumora == null ? null : lumora.javaComponent);
                Path jar = lumora == null ? null : installer.lumoraFor(loaderId, instance.minecraftVersion);
                extraSysProps.addAll(dev.lumora.launcher.core.install.LumoraAddon.apply(p.gameDir(), loaderId, jar));
                return p;
            };
            lumoraOnLoader = lumora != null;
        }
        int max = instance.maxMemoryMb != null ? instance.maxMemoryMb : config.maxMemoryMb;
        int min = Math.min(max, instance.minMemoryMb != null ? instance.minMemoryMb : config.minMemoryMb);
        Target target = new Target(instance.key(), instance.minecraftVersion,
                kind == dev.lumora.launcher.core.instance.InstanceKind.LUMORA || lumoraOnLoader,
                prepare, min, max, instance.windowWidth != null ? instance.windowWidth : config.windowWidth,
                instance.windowHeight != null ? instance.windowHeight : config.windowHeight,
                instance.jvmArgs == null ? List.of() : instance.jvmArgs, extraSysProps);
        return run(target, config, mode, interactiveLogin, cancelled, null, quickPlay, listener);
    }

    @FunctionalInterface
    private interface ClientInstallerCall {
        ClientInstaller.Prepared prepare() throws IOException;
    }

    /** What one start needs: instance id (locks, stats), Minecraft version, Lumora or not, preparation and launch settings. */
    private record Target(String instanceId, String minecraftVersion, boolean lumora, ClientInstallerCall prepare,
                          int minMemoryMb, int maxMemoryMb, Integer windowWidth, Integer windowHeight, List<String> jvmArgs,
                          List<String> extraSysProps) {
        Target(String instanceId, String minecraftVersion, boolean lumora, ClientInstallerCall prepare, int minMemoryMb, int maxMemoryMb,
               Integer windowWidth, Integer windowHeight, List<String> jvmArgs) {
            this(instanceId, minecraftVersion, lumora, prepare, minMemoryMb, maxMemoryMb, windowWidth, windowHeight, jvmArgs, List.of());
        }
    }

    private GameProcess run(Target target, LauncherConfig config, Mode mode, InteractiveLogin interactiveLogin, BooleanSupplier cancelled,
                            Path smokeReport, LaunchCommandBuilder.QuickPlay quickPlay, Listener listener) throws IOException, AuthException {
        String instanceId = target.instanceId();
        if (runningGame().isPresent() && !config.multipleInstances) {
            throw new GameProcess.AlreadyRunningException(instanceId);
        }
        GameProcess.Slot slot = null; // until the game process owns it
        try {
            setState(listener, State.INSTALLING, Lang.t("play.status.checking"));
            ClientInstaller.Prepared prepared = target.prepare().prepare();
            // this run's lock in the instance folder (with "multiple instances" the next free one)
            slot = GameProcess.lock(prepared.gameDir(), instanceId, config.multipleInstances);
            try {
                // render backend of 26.2+ (Einstellungen › Spiel › Grafik-Backend; "default" leaves options.txt alone)
                dev.lumora.launcher.core.launch.GraphicsBackend.apply(prepared.gameDir(), target.minecraftVersion(), config.graphicsBackend);
            } catch (IOException e) {
                Log.warn("Could not set the graphics backend: " + e.getMessage());
            }
            if (target.lumora()) {
                dev.lumora.launcher.core.install.GameThemeSeeder.seed(prepared.layout().lumoraDir(), config);
            }
            LaunchCommandBuilder.Identity identity;
            if (mode == Mode.SMOKE_TEST_DEMO) {
                identity = new LaunchCommandBuilder.Identity("DemoSmokeTest",
                        UUID.nameUUIDFromBytes("lumora-smoke".getBytes()).toString().replace("-", ""), "0", "0", "msa", true);
            } else {
                setState(listener, State.LOGGING_IN, Lang.t("play.status.signing_in"));
                MinecraftSession session = ensureSession(config, interactiveLogin, cancelled);
                history.record(dev.lumora.launcher.core.history.ConnectionHistory.Kind.GAME_START, session.uuid(), session.username(),
                        instanceId, Instant.now());
                identity = new LaunchCommandBuilder.Identity(session.username(), session.uuid(), session.accessToken(),
                        session.xuid(), "msa", false);
            }

            setState(listener, State.LAUNCHING, Lang.t("play.status.launching"));
            List<String> sysProps = new ArrayList<>();
            sysProps.add("-Dlumora.launcher.version=" + BuildInfo.VERSION);
            sysProps.add("-Dlumora.profile=" + instanceId);
            // Lumora settings and addons: shared by all versions or the instance's own (InstanceLayout)
            sysProps.add("-Dlumora.dir=" + prepared.layout().lumoraDir().toAbsolutePath());
            sysProps.add("-Dlumora.instanceDir=" + prepared.layout().versionDir().toAbsolutePath());
            if (config.hangDump) {
                // the game writes the stack traces of all threads when its client thread hangs (crash-reports/lumora-hang-*.txt)
                sysProps.add("-Dlumora.hangDump=true");
            }
            sysProps.add("-Dlumora.diagnosticsDir=" + paths.diagnostics().toAbsolutePath());
            // the game reports menu / singleplayer / server there (Discord Rich Presence); a stale file of the last run goes first
            Path statusFile = dev.lumora.launcher.core.integrations.GameStatus.file(prepared.layout().versionDir(), slot.suffix());
            java.nio.file.Files.deleteIfExists(statusFile);
            sysProps.add("-Dlumora.statusFile=" + statusFile.toAbsolutePath());
            if (mode == Mode.SMOKE_TEST_DEMO && smokeReport != null) {
                sysProps.add("-Dlumora.smokeTest=" + smokeReport.toAbsolutePath());
            }
            sysProps.addAll(target.extraSysProps());
            // the game connects to the official Lumora backend; only a developer's test server is passed on
            String socialOverride = System.getProperty("lumora.social.server");
            if (socialOverride != null && !socialOverride.isBlank()) {
                sysProps.add("-Dlumora.social.server=" + socialOverride);
            }
            sysProps.add("-Dlumora.social.shareServer=" + config.socialShareServer);
            if (target.lumora()) {
                // the client's own addons shipped once by the launcher (LumoraVoice)
                String builtIn = dev.lumora.launcher.core.mods.BuiltInAddons.sysProp(dev.lumora.launcher.core.mods.BundledMods.locate(),
                        prepared.layout().lumoraDir(), Log::warn);
                if (builtIn != null) {
                    sysProps.add(builtIn);
                }
            }
            List<String> jvmArgs = new ArrayList<>(config.extraJvmArgs);
            jvmArgs.addAll(target.jvmArgs());
            jvmArgs.addAll(dev.lumora.launcher.core.launch.LaunchOptions.jvmArgs(config, paths.diagnostics()));
            List<String> cmd = LaunchCommandBuilder.build(new LaunchCommandBuilder.Request(
                    prepared.version(), prepared.javaExecutable(), paths.libraries(), prepared.clientJar(),
                    paths.assets(), prepared.gameDir(), prepared.nativesDir(), prepared.loggingConfig(), identity,
                    target.minMemoryMb(), target.maxMemoryMb(), jvmArgs,
                    target.windowWidth(), target.windowHeight(), sysProps, quickPlay, prepared.loader()), os);

            cmd = new ArrayList<>(cmd);
            cmd.addAll(dev.lumora.launcher.core.launch.LaunchOptions.gameArgs(config));
            String player = identity.username();
            boolean track = mode != Mode.SMOKE_TEST_DEMO;
            GameProcess game = GameProcess.start(cmd, prepared.gameDir(), slot,
                    dev.lumora.launcher.core.launch.LaunchOptions.environment(config), line -> {
                        listener.gameOutput(line);
                        String server = track ? dev.lumora.launcher.core.history.ConnectionHistory.serverFromLog(line) : null;
                        if (server != null) {
                            history.recordServer(server, player, instanceId, Instant.now());
                        }
                    });
            slot = null; // the game process releases it when it ends
            java.time.Instant started = java.time.Instant.now();
            game.statusFile(statusFile);
            running.add(game);
            setState(listener, State.PLAYING, Lang.t("play.status.running", game.pid()));
            game.onExit().thenAccept(original -> {
                running.remove(game);
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
                    setState(listener, State.ERROR, Lang.t("play.status.exit_code", exit.code()));
                } else if (runningGame().isPresent()) {
                    // another run of "multiple instances" still plays
                    setState(listener, State.PLAYING, Lang.t("play.status.running", runningGame().get().pid()));
                } else {
                    setState(listener, State.IDLE, Lang.t("status.ready"));
                }
                listener.gameExited(exit, hints);
            });
            return game;
        } catch (IOException | AuthException | RuntimeException e) {
            if (slot != null) {
                try {
                    slot.close();
                } catch (IOException ignored) {
                    // the OS releases it with the channel anyway
                }
            }
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
