package dev.lumora.launcher.core.launch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.lumora.launcher.core.BuildInfo;
import dev.lumora.launcher.core.mojang.ResolvedVersion;
import dev.lumora.launcher.core.mojang.Rules;
import dev.lumora.launcher.core.mojang.VersionJson;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.OsInfo;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds the java command line from a resolved version JSON, exactly as the official launcher would. */
public final class LaunchCommandBuilder {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)}");

    public record Request(
            ResolvedVersion version,
            Path javaExecutable,
            Path librariesDir,
            Path clientJar,
            Path assetsDir,
            Path gameDir,
            Path nativesDir,
            Path loggingConfig,
            Identity identity,
            int minMemoryMb,
            int maxMemoryMb,
            List<String> extraJvmArgs,
            Integer width,
            Integer height,
            List<String> extraSystemProperties,
            QuickPlay quickPlay,
            Loader loader) {
        public Request(ResolvedVersion version, Path javaExecutable, Path librariesDir, Path clientJar, Path assetsDir, Path gameDir,
                       Path nativesDir, Path loggingConfig, Identity identity, int minMemoryMb, int maxMemoryMb,
                       List<String> extraJvmArgs, Integer width, Integer height, List<String> extraSystemProperties,
                       QuickPlay quickPlay) {
            this(version, javaExecutable, librariesDir, clientJar, assetsDir, gameDir, nativesDir, loggingConfig, identity,
                    minMemoryMb, maxMemoryMb, extraJvmArgs, width, height, extraSystemProperties, quickPlay, null);
        }
    }

    /**
     * The Lumora Loader instead of a mod loader: its jar is put on the class path in place of the Minecraft jar, which
     * it loads itself together with {@code jars} (the Lumora client); Minecraft's main class becomes the loader's
     * target.
     */
    public record Loader(Path jar, List<Path> jars, Path mappings) {
        public static final String MAIN_CLASS = "dev.lumora.loader.Main";

        public Loader {
            jars = List.copyOf(jars);
        }

        /** For unobfuscated Minecraft (26.1 and later): no mappings. */
        public Loader(Path jar, List<Path> jars) {
            this(jar, jars, null);
        }
    }

    /**
     * Minecraft's official Quick Play (since 1.20): start straight into a world (folder name in saves/) or onto a
     * server ("host[:port]"). Uses the version JSON's own --quickPlaySingleplayer / --quickPlayMultiplayer arguments.
     */
    public record QuickPlay(Kind kind, String target) {
        public enum Kind { SINGLEPLAYER, MULTIPLAYER }

        public QuickPlay {
            if (target == null || target.isBlank() || target.startsWith("-") || target.contains("\n")) {
                throw new IllegalArgumentException("invalid quick play target");
            }
        }
    }

    /** Values for the auth placeholders. {@code demo} sets the official is_demo_user feature. */
    public record Identity(String username, String uuid, String accessToken, String xuid, String userType, boolean demo) {
    }

    private LaunchCommandBuilder() {
    }

    public static List<String> build(Request r, OsInfo os) {
        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", r.identity().username());
        vars.put("version_name", r.version().id());
        vars.put("game_directory", r.gameDir().toAbsolutePath().toString());
        vars.put("assets_root", r.assetsDir().toAbsolutePath().toString());
        vars.put("game_assets", r.assetsDir().toAbsolutePath().toString());
        vars.put("assets_index_name", r.version().assetIndex().id);
        vars.put("auth_uuid", r.identity().uuid());
        vars.put("auth_access_token", r.identity().accessToken());
        vars.put("auth_session", r.identity().accessToken());
        // 1.8.9 - 1.12.2 pass Twitch-era user properties; an empty JSON object is what the official launcher sends
        vars.put("user_properties", "{}");
        vars.put("clientid", "");
        vars.put("auth_xuid", r.identity().xuid() == null ? "0" : r.identity().xuid());
        vars.put("user_type", r.identity().userType());
        vars.put("version_type", r.version().type() == null ? "release" : r.version().type());
        vars.put("natives_directory", r.nativesDir().toAbsolutePath().toString());
        vars.put("launcher_name", "lumora-launcher");
        vars.put("launcher_version", BuildInfo.VERSION);
        vars.put("classpath_separator", File.pathSeparator);
        vars.put("library_directory", r.librariesDir().toAbsolutePath().toString());
        vars.put("classpath", classpath(r));
        if (r.width() != null && r.height() != null) {
            vars.put("resolution_width", r.width().toString());
            vars.put("resolution_height", r.height().toString());
        }

        Map<String, Boolean> features = new HashMap<>();
        features.put("is_demo_user", r.identity().demo());
        features.put("has_custom_resolution", r.width() != null && r.height() != null);
        if (r.quickPlay() != null) {
            boolean single = r.quickPlay().kind() == QuickPlay.Kind.SINGLEPLAYER;
            features.put(single ? "is_quick_play_singleplayer" : "is_quick_play_multiplayer", true);
            vars.put(single ? "quickPlaySingleplayer" : "quickPlayMultiplayer", r.quickPlay().target());
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(r.javaExecutable().toAbsolutePath().toString());
        cmd.add("-Xms" + r.minMemoryMb() + "M");
        cmd.add("-Xmx" + r.maxMemoryMb() + "M");
        for (String p : r.extraSystemProperties()) {
            cmd.add(p);
        }
        if (r.loader() != null) {
            cmd.add("-Dlumora.loader.game=" + r.clientJar().toAbsolutePath());
            cmd.add("-Dlumora.loader.jars=" + String.join(File.pathSeparator,
                    r.loader().jars().stream().map(p -> p.toAbsolutePath().toString()).toList()));
            cmd.add("-Dlumora.loader.target=" + r.version().mainClass());
            if (r.loader().mappings() != null) {
                // obfuscated Minecraft: Mojang's official names, applied by the loader before the game starts
                cmd.add("-Dlumora.loader.mappings=" + r.loader().mappings().toAbsolutePath());
            }
        }
        cmd.addAll(r.extraJvmArgs());
        if (r.loggingConfig() != null && r.version().logging() != null && r.version().logging().argument != null) {
            cmd.add(r.version().logging().argument.replace("${path}", r.loggingConfig().toAbsolutePath().toString()));
        }
        cmd.addAll(expand(r.version().jvmArgs(), vars, os, features));
        cmd.add(r.loader() != null ? Loader.MAIN_CLASS : r.version().mainClass());
        List<String> game = expand(r.version().gameArgs(), vars, os, features);
        if (r.quickPlay() != null && r.quickPlay().kind() == QuickPlay.Kind.MULTIPLAYER && !game.contains("--quickPlayMultiplayer")) {
            // versions before 1.20 have no Quick Play, but every version since 1.6 understands --server/--port
            String target = r.quickPlay().target();
            String host = target;
            String port = "25565";
            int colon = target.lastIndexOf(':');
            if (colon > 0 && target.indexOf(':') == colon && target.substring(colon + 1).matches("\\d{1,5}")) {
                host = target.substring(0, colon);
                port = target.substring(colon + 1);
            }
            game.add("--server");
            game.add(host);
            game.add("--port");
            game.add(port);
        }
        if (r.identity().demo() && !game.contains("--demo")) {
            // legacy minecraftArguments have no feature rules; the official launcher appended --demo for them
            game.add("--demo");
        }
        cmd.addAll(game);
        return cmd;
    }

    static String classpath(Request r) {
        List<String> entries = new ArrayList<>();
        if (r.loader() != null) {
            // the loader first; the Minecraft jar is loaded by the loader itself (with the mixins applied), not from here
            entries.add(r.loader().jar().toAbsolutePath().toString());
        }
        for (ResolvedVersion.Library lib : r.version().libraries()) {
            entries.add(r.librariesDir().resolve(lib.path()).toAbsolutePath().toString());
        }
        if (r.loader() == null) {
            entries.add(r.clientJar().toAbsolutePath().toString());
        }
        return String.join(File.pathSeparator, entries);
    }

    static List<String> expand(List<JsonElement> args, Map<String, String> vars, OsInfo os, Map<String, Boolean> features) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : args) {
            if (e.isJsonPrimitive()) {
                addExpanded(out, e.getAsString(), vars);
            } else if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                List<VersionJson.Rule> rules = o.has("rules")
                        ? Json.GSON.fromJson(o.get("rules"), new TypeToken<List<VersionJson.Rule>>() { }.getType())
                        : List.of();
                if (!Rules.allowed(rules, os, features)) {
                    continue;
                }
                JsonElement value = o.get("value");
                if (value == null) {
                    continue;
                }
                if (value.isJsonArray()) {
                    value.getAsJsonArray().forEach(v -> addExpanded(out, v.getAsString(), vars));
                } else {
                    addExpanded(out, value.getAsString(), vars);
                }
            }
        }
        return out;
    }

    private static void addExpanded(List<String> out, String template, Map<String, String> vars) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        boolean unresolved = false;
        while (m.find()) {
            String v = vars.get(m.group(1));
            if (v == null) {
                unresolved = true;
                v = "";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        // Arguments consisting only of an unknown placeholder (e.g. quick play paths) are dropped;
        // their option name was already gated by a feature rule.
        if (unresolved && sb.isEmpty()) {
            return;
        }
        out.add(sb.toString());
    }
}
