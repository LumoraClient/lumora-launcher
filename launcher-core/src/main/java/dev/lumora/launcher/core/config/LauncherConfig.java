package dev.lumora.launcher.core.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/** Persistent launcher settings (config/launcher.json). Mutable POJO for Gson. */
public final class LauncherConfig {
    public static final int CURRENT_SCHEMA = 2;
    /** Default accent since the blue redesign (schema 2); the violet before it. */
    public static final String DEFAULT_ACCENT = "#3B82F6";
    static final String OLD_DEFAULT_ACCENT = "#8B5CF6";
    public static final int MIN_MEMORY_MB = 1024;
    public static final int MAX_MEMORY_MB = 65536;

    /** Azure application (client) IDs are GUIDs. */
    private static final Pattern GUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public int schemaVersion = CURRENT_SCHEMA;
    /** Data directory; null means the platform default (%APPDATA%\LumoraClient on Windows). */
    public String installDir;
    public String selectedProfile;
    public int maxMemoryMb = 4096;
    public int minMemoryMb = 1024;
    /** Optional java executable. Null means: use Mojang's official runtime for the profile. */
    public String javaPath;
    public List<String> extraJvmArgs = new ArrayList<>();
    public List<String> enabledExtensions = new ArrayList<>();
    /** Keys of built-in resource packs the player switched off (packs are on by default). */
    public List<String> disabledResourcePacks = new ArrayList<>();
    /**
     * Azure AD application (client) ID used for Microsoft sign-in. Must belong to an app registration
     * that Mojang approved for the Minecraft Services API. Can also be supplied via LUMORA_MS_CLIENT_ID.
     */
    public String microsoftClientId;
    public Integer windowWidth;
    public Integer windowHeight;
    public boolean closeLauncherOnGameStart = false;
    public boolean checkForUpdates = true;
    /**
     * Launcher started directly from its extracted folder (pinned taskbar icon, own shortcut) still updates itself,
     * and a pin of the launcher window starts LumoraClient.exe. Off: only a start through LumoraClient.exe updates.
     */
    public boolean portableSelfUpdate = true;
    /**
     * Lumora Social server (friends, groups, chat), normalised ws:// or wss:// URI; null = not used. There is no default
     * server: whoever hosts one (see docs/SOCIAL.md) gives out its address.
     */
    public String socialServer;
    /** Friends see the multiplayer server address you play on (else only "Mehrspieler"). */
    public boolean socialShareServer = true;
    /** Windows notifications (tray) for new Lumora Social messages while the launcher window is not in front. */
    public boolean socialNotifications = true;
    /** Settings page shows the settings marked "Erweitert". */
    public boolean advancedSettings = false;
    /** Closing the window keeps the launcher running in the system tray (friends can reach you in the chat). */
    public boolean minimizeToTray = false;
    /** Files downloaded at the same time (1-16, applies after a restart). */
    public int parallelDownloads = 8;
    /** Page shown when the launcher opens: home, news, servers or social. */
    public String startPage = "home";
    /** Changelog also lists Minecraft snapshots (otherwise releases only). */
    public boolean showSnapshots = false;
    /** User's own servers in the server list (addresses). */
    public List<String> customServers = new ArrayList<>();

    // --- Startart und Spiel
    /** What the play button starts: lumora or vanilla (see Flavor; old mod loader ids fall back to lumora). */
    public String selectedFlavor = "lumora";
    /** After the game started: keep (launcher stays open), hide (minimise) or close (launcher ends, game keeps running). */
    public String afterLaunch = "keep";
    /** Several Minecraft instances at the same time (each instance folder still runs only once). */
    public boolean multipleInstances = false;
    /** Extra environment variables for the game process, "KEY=VALUE". */
    public List<String> environmentVariables = new ArrayList<>();
    /** Always start Minecraft in fullscreen (--fullscreen). */
    public boolean forceFullscreen = false;
    /** Tuned garbage collector flags (G1, short pauses), unless own JVM arguments choose a collector. */
    public boolean performanceFlags = true;

    // --- Erscheinungsbild
    /** Accent colour of the launcher, #RRGGBB. */
    public String accentColor = DEFAULT_ACCENT;
    /** Hard pixel corners instead of rounded ones (applies after a restart). */
    public boolean pixelStyle = false;
    /** No sliding/fading animations. */
    public boolean reduceMotion = false;
    /** Java2D hardware acceleration (Direct3D on Windows); off helps with driver problems. Applies after a restart. */
    public boolean hardwareAcceleration = true;

    // --- Chat
    /** Unread counter on the navigation and the tray icon. */
    public boolean unreadBadge = true;
    /** Notifications while the window is in the background: all, messages or none. */
    public String notificationLevel = "all";

    // --- Integrationen
    /** Discord Rich Presence (local Discord app over IPC); needs the user's own Discord application ID. */
    public boolean discordRichPresence = false;
    /** Discord application ID (a numeric "snowflake") from the Discord Developer Portal. */
    public String discordAppId;
    /**
     * Anonymous launcher statistics. There is no Lumora statistics server yet: the switch is stored, nothing is sent.
     * Off by default.
     */
    public boolean anonymousStats = false;

    // --- Fehlersuche
    /** Heap dump into the diagnostics folder when the game runs out of memory. */
    public boolean heapDumpOnOom = false;
    /** JVM debugging: off, listen (game waits for a debugger) or attach (game connects to a running debugger). */
    public String debugMode = "off";
    /** Debug address host:port, loopback only. */
    public String debugAddress = "127.0.0.1:5005";

    /** Lumora news the user has seen (ids), for the counter on "News". */
    public List<String> seenNews = new ArrayList<>();

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern ENV_VAR = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}=[^\\r\\n\\x00]{0,4096}$");
    private static final Pattern SNOWFLAKE = Pattern.compile("^[0-9]{15,21}$");
    private static final Pattern DEBUG_ADDRESS = Pattern.compile("^(localhost|127\\.0\\.0\\.1|\\[::1]):([0-9]{1,5})$");

    /** True for a loopback "host:port" with a valid port (debugging never opens a port to the network). */
    public static boolean isValidDebugAddress(String address) {
        if (address == null) {
            return false;
        }
        java.util.regex.Matcher m = DEBUG_ADDRESS.matcher(address.strip());
        if (!m.matches()) {
            return false;
        }
        int port = Integer.parseInt(m.group(2));
        return port >= 1 && port <= 65535;
    }

    public static boolean isValidDiscordAppId(String id) {
        return id != null && SNOWFLAKE.matcher(id.strip()).matches();
    }

    public static boolean isValidEnvironmentVariable(String entry) {
        return entry != null && ENV_VAR.matcher(entry).matches();
    }

    /** Clamps / repairs values in place and returns human readable notes about what was changed. */
    public List<String> validateAndRepair(List<String> knownProfiles, List<String> knownExtensions, String defaultProfile) {
        List<String> notes = new ArrayList<>();
        if (schemaVersion < 2 && OLD_DEFAULT_ACCENT.equalsIgnoreCase(accentColor)) {
            // blue redesign: the old default violet becomes the new default blue
            notes.add("accentColor " + accentColor + " -> " + DEFAULT_ACCENT);
            accentColor = DEFAULT_ACCENT;
        }
        if (schemaVersion != CURRENT_SCHEMA) {
            notes.add("schemaVersion " + schemaVersion + " -> " + CURRENT_SCHEMA);
            schemaVersion = CURRENT_SCHEMA;
        }
        String renamed = selectedProfile == null ? null
                : dev.lumora.launcher.core.install.InstanceMigration.LEGACY_PROFILE_IDS.get(selectedProfile);
        if (renamed != null && knownProfiles.contains(renamed)) {
            notes.add("profile '" + selectedProfile + "' is now '" + renamed + "'");
            selectedProfile = renamed;
        }
        if (selectedProfile == null || !knownProfiles.contains(selectedProfile)) {
            if (selectedProfile != null) {
                notes.add("unknown profile '" + selectedProfile + "' replaced by '" + defaultProfile + "'");
            }
            selectedProfile = defaultProfile;
        }
        if (maxMemoryMb < MIN_MEMORY_MB || maxMemoryMb > MAX_MEMORY_MB) {
            int fixed = Math.clamp(maxMemoryMb, MIN_MEMORY_MB, MAX_MEMORY_MB);
            notes.add("maxMemoryMb " + maxMemoryMb + " -> " + fixed);
            maxMemoryMb = fixed;
        }
        if (minMemoryMb < 256 || minMemoryMb > maxMemoryMb) {
            int fixed = Math.clamp(minMemoryMb, 256, maxMemoryMb);
            notes.add("minMemoryMb " + minMemoryMb + " -> " + fixed);
            minMemoryMb = fixed;
        }
        if (extraJvmArgs == null) {
            extraJvmArgs = new ArrayList<>();
        }
        List<String> cleanArgs = new ArrayList<>();
        for (String arg : extraJvmArgs) {
            if (arg == null || arg.isBlank() || arg.contains("\n") || arg.contains("\r")) {
                notes.add("dropped empty/multiline JVM argument");
            } else if (arg.startsWith("-cp") || arg.startsWith("-classpath") || arg.startsWith("--class-path")
                    || arg.startsWith("-Xmx") || arg.startsWith("-Xms")) {
                notes.add("dropped JVM argument managed by the launcher: " + arg);
            } else {
                cleanArgs.add(arg.strip());
            }
        }
        extraJvmArgs = cleanArgs;
        if (enabledExtensions == null) {
            enabledExtensions = new ArrayList<>();
        }
        List<String> ext = new ArrayList<>(new LinkedHashSet<>(enabledExtensions));
        if (ext.removeIf(e -> !knownExtensions.contains(e))) {
            notes.add("removed unknown extensions");
        }
        enabledExtensions = ext;
        if (disabledResourcePacks == null) {
            disabledResourcePacks = new ArrayList<>();
        }
        disabledResourcePacks = new ArrayList<>(new LinkedHashSet<>(disabledResourcePacks.stream()
                .filter(k -> k != null && k.matches("[a-z0-9][a-z0-9_.-]{0,63}")).toList()));
        if (microsoftClientId != null) {
            microsoftClientId = microsoftClientId.strip();
            if (microsoftClientId.isEmpty()) {
                microsoftClientId = null;
            } else if (!GUID.matcher(microsoftClientId).matches()) {
                notes.add("microsoftClientId is not a GUID and was cleared");
                microsoftClientId = null;
            }
        }
        if (windowWidth != null && (windowWidth < 640 || windowWidth > 16384)) {
            notes.add("windowWidth out of range, cleared");
            windowWidth = null;
        }
        if (windowHeight != null && (windowHeight < 480 || windowHeight > 16384)) {
            notes.add("windowHeight out of range, cleared");
            windowHeight = null;
        }
        if (javaPath != null && javaPath.isBlank()) {
            javaPath = null;
        }
        if (installDir != null && installDir.isBlank()) {
            installDir = null;
        }
        if (parallelDownloads < 1 || parallelDownloads > 16) {
            int fixed = Math.clamp(parallelDownloads, 1, 16);
            notes.add("parallelDownloads " + parallelDownloads + " -> " + fixed);
            parallelDownloads = fixed;
        }
        if (!List.of("home", "news", "servers", "social").contains(startPage)) {
            startPage = "home";
        }
        if (customServers == null) {
            customServers = new ArrayList<>();
        }
        customServers.removeIf(s -> s == null || s.isBlank() || s.length() > 255 || s.contains("\n") || s.startsWith("-"));
        if (java.util.Arrays.stream(dev.lumora.launcher.core.profile.Flavor.values()).noneMatch(f -> f.id().equals(selectedFlavor))) {
            selectedFlavor = "lumora";
        }
        if (closeLauncherOnGameStart) {
            // older setting "Launcher beim Spielstart minimieren"
            notes.add("closeLauncherOnGameStart -> afterLaunch=hide");
            afterLaunch = "hide";
            closeLauncherOnGameStart = false;
        }
        if (!List.of("keep", "hide", "close").contains(afterLaunch)) {
            afterLaunch = "keep";
        }
        if (environmentVariables == null) {
            environmentVariables = new ArrayList<>();
        }
        if (environmentVariables.removeIf(e -> !isValidEnvironmentVariable(e))) {
            notes.add("dropped invalid environment variables");
        }
        if (accentColor == null || !HEX_COLOR.matcher(accentColor).matches()) {
            accentColor = DEFAULT_ACCENT;
        }
        if (!List.of("all", "messages", "none").contains(notificationLevel)) {
            notificationLevel = "all";
        }
        if (discordAppId != null) {
            discordAppId = discordAppId.strip();
            if (discordAppId.isEmpty() || !isValidDiscordAppId(discordAppId)) {
                if (!discordAppId.isEmpty()) {
                    notes.add("discordAppId is not a Discord ID and was cleared");
                }
                discordAppId = null;
            }
        }
        if (!List.of("off", "listen", "attach").contains(debugMode)) {
            debugMode = "off";
        }
        if (!isValidDebugAddress(debugAddress)) {
            debugAddress = "127.0.0.1:5005";
        }
        if (seenNews == null) {
            seenNews = new ArrayList<>();
        }
        seenNews.removeIf(s -> s == null || s.length() > 100);
        if (socialServer != null) {
            try {
                socialServer = dev.lumora.social.Endpoint.parse(socialServer).toString();
            } catch (IllegalArgumentException e) {
                notes.add("socialServer invalid (" + e.getMessage() + "), cleared");
                socialServer = null;
            }
        }
        return notes;
    }

    public static boolean isValidClientId(String id) {
        return id != null && GUID.matcher(id.strip()).matches();
    }
}
