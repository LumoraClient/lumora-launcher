package dev.lumora.launcher.core.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.instance.InstanceManager;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Finds instances of other launchers on this PC and copies them into new Lumora instances. Read-only towards the other
 * launcher: its folders are only read, never changed. Formats:
 * <ul>
 *   <li>Prism Launcher / MultiMC: {@code instance.cfg} + {@code mmc-pack.json}, game folder {@code .minecraft} or {@code minecraft}</li>
 *   <li>CurseForge app: {@code minecraftinstance.json} in the instance folder</li>
 *   <li>Modrinth App: {@code profile.json} of a profile (older app versions)</li>
 *   <li>Official launcher: {@code launcher_profiles.json}, profiles with their own game folder</li>
 *   <li>Lumora archive: a zip exported by Lumora ({@link InstanceExporter#exportArchive})</li>
 * </ul>
 */
public final class LauncherImport {
    public enum Source { PRISM, MULTIMC, CURSEFORGE, MODRINTH_APP, OFFICIAL, FOLDER }

    /** One instance found somewhere. {@code kind} null = the loader could not be recognised (imported as Vanilla). */
    public record Found(Source source, String name, String minecraftVersion, InstanceKind kind, String loaderVersion, Path gameDir,
                        Path icon) {
    }

    /** Folders a copy leaves behind: runtime leftovers of the other launcher. */
    private static final Pattern SKIP = Pattern.compile(
            "(logs|crash-reports|natives|\\.natives|\\.cache|\\.fabric|\\.mixin\\.out|essential|\\.lumora)(/.*)?|.*\\.lock|usercache\\.json|usernamecache\\.json");

    private LauncherImport() {
    }

    /** Default folders of the known launchers on this PC (only the ones that exist). */
    public static Map<Source, Path> knownLocations() {
        Map<Source, Path> out = new LinkedHashMap<>();
        String appData = System.getenv("APPDATA");
        String home = System.getProperty("user.home");
        if (appData != null) {
            put(out, Source.PRISM, Path.of(appData, "PrismLauncher", "instances"));
            put(out, Source.MODRINTH_APP, Path.of(appData, "ModrinthApp", "profiles"));
            put(out, Source.OFFICIAL, Path.of(appData, ".minecraft", "launcher_profiles.json"));
        }
        if (home != null) {
            put(out, Source.CURSEFORGE, Path.of(home, "curseforge", "minecraft", "Instances"));
            put(out, Source.MULTIMC, Path.of(home, "MultiMC", "instances"));
        }
        return out;
    }

    private static void put(Map<Source, Path> out, Source s, Path p) {
        if (Files.exists(p)) {
            out.put(s, p);
        }
    }

    /** Instances in one launcher's folder (or, for {@link Source#FOLDER}, anything recognisable in or below a folder). */
    public static List<Found> scan(Source source, Path location) {
        List<Found> out = new ArrayList<>();
        try {
            switch (source) {
                case OFFICIAL -> out.addAll(official(location));
                case FOLDER -> scanFolder(location, out, 0);
                default -> {
                    if (!Files.isDirectory(location)) {
                        return out;
                    }
                    try (var dirs = Files.list(location)) {
                        for (Path d : dirs.filter(Files::isDirectory).sorted().toList()) {
                            Found f = detect(d, source);
                            if (f != null) {
                                out.add(f);
                            }
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Launcher scan of " + location + " failed: " + e.getMessage());
        }
        return out;
    }

    private static void scanFolder(Path dir, List<Found> out, int depth) throws IOException {
        if (depth > 3 || !Files.isDirectory(dir)) {
            return;
        }
        Found f = detect(dir, Source.FOLDER);
        if (f != null) {
            out.add(f);
            return;
        }
        if (Files.isRegularFile(dir.resolve("launcher_profiles.json"))) {
            out.addAll(official(dir.resolve("launcher_profiles.json")));
        }
        try (var dirs = Files.list(dir)) {
            for (Path d : dirs.filter(Files::isDirectory).sorted().limit(200).toList()) {
                scanFolder(d, out, depth + 1);
            }
        }
    }

    /** Recognises one instance folder of any known launcher, or null. */
    static Found detect(Path d, Source hint) {
        try {
            if (Files.isRegularFile(d.resolve("mmc-pack.json"))) {
                return prism(d, hint == Source.MULTIMC ? Source.MULTIMC : hint == Source.FOLDER ? Source.FOLDER : Source.PRISM);
            }
            if (Files.isRegularFile(d.resolve("minecraftinstance.json"))) {
                return curseForge(d, hint == Source.FOLDER ? Source.FOLDER : Source.CURSEFORGE);
            }
            if (Files.isRegularFile(d.resolve("profile.json"))) {
                return modrinthApp(d, hint == Source.FOLDER ? Source.FOLDER : Source.MODRINTH_APP);
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Could not read instance " + d.getFileName() + ": " + e.getMessage());
        }
        return null;
    }

    static Found prism(Path d, Source source) throws IOException {
        JsonObject pack = read(d.resolve("mmc-pack.json"));
        String mc = null;
        InstanceKind kind = InstanceKind.VANILLA;
        String loader = null;
        for (JsonElement c : pack.getAsJsonArray("components")) {
            JsonObject co = c.getAsJsonObject();
            String uid = str(co, "uid");
            String version = str(co, "version");
            if (uid == null) {
                continue;
            }
            switch (uid) {
                case "net.minecraft" -> mc = version;
                case "net.fabricmc.fabric-loader" -> {
                    kind = InstanceKind.FABRIC;
                    loader = version;
                }
                case "org.quiltmc.quilt-loader" -> {
                    kind = InstanceKind.QUILT;
                    loader = version;
                }
                case "net.minecraftforge" -> {
                    kind = InstanceKind.FORGE;
                    loader = version;
                }
                case "net.neoforged" -> {
                    kind = InstanceKind.NEOFORGE;
                    loader = version;
                }
                default -> {
                }
            }
        }
        String name = d.getFileName().toString();
        Path cfg = d.resolve("instance.cfg");
        if (Files.isRegularFile(cfg)) {
            for (String line : Files.readAllLines(cfg, StandardCharsets.UTF_8)) {
                if (line.startsWith("name=")) {
                    name = line.substring(5).strip();
                }
            }
        }
        Path game = Files.isDirectory(d.resolve(".minecraft")) ? d.resolve(".minecraft") : d.resolve("minecraft");
        return found(source, name, mc, kind, loader, game, Files.isRegularFile(d.resolve("icon.png")) ? d.resolve("icon.png") : null);
    }

    static Found curseForge(Path d, Source source) throws IOException {
        JsonObject o = read(d.resolve("minecraftinstance.json"));
        String mc = str(o, "gameVersion");
        InstanceKind kind = InstanceKind.VANILLA;
        String loader = null;
        if (o.has("baseModLoader") && o.get("baseModLoader").isJsonObject()) {
            Object[] r = CurseForgePackImporter.loader(str(o.getAsJsonObject("baseModLoader"), "name"), mc == null ? "" : mc);
            if (r[0] != null) {
                kind = (InstanceKind) r[0];
                loader = (String) r[1];
            }
        }
        String name = str(o, "name") != null ? str(o, "name") : d.getFileName().toString();
        return found(source, name, mc, kind, loader, d, null);
    }

    static Found modrinthApp(Path d, Source source) throws IOException {
        JsonObject o = read(d.resolve("profile.json"));
        JsonObject meta = o.has("metadata") && o.get("metadata").isJsonObject() ? o.getAsJsonObject("metadata") : o;
        String mc = str(meta, "game_version");
        InstanceKind kind = InstanceKind.of(str(meta, "loader"));
        String loader = null;
        if (meta.has("loader_version") && meta.get("loader_version").isJsonObject()) {
            loader = str(meta.getAsJsonObject("loader_version"), "id");
        }
        String name = str(meta, "name") != null ? str(meta, "name") : d.getFileName().toString();
        return found(source, name, mc, kind == null ? InstanceKind.VANILLA : kind, loader, d, null);
    }

    private static final Pattern FABRIC_ID = Pattern.compile("fabric-loader-([^-]+)-(.+)");
    private static final Pattern QUILT_ID = Pattern.compile("quilt-loader-([^-]+)-(.+)");
    private static final Pattern FORGE_ID = Pattern.compile("(.+?)-forge-(.+)");
    private static final Pattern NEOFORGE_ID = Pattern.compile("neoforge-(.+)");

    /** Profiles of the official launcher that have their own game folder (the shared .minecraft is never copied whole). */
    static List<Found> official(Path profilesFile) throws IOException {
        List<Found> out = new ArrayList<>();
        if (!Files.isRegularFile(profilesFile)) {
            return out;
        }
        JsonObject o = read(profilesFile);
        if (!o.has("profiles") || !o.get("profiles").isJsonObject()) {
            return out;
        }
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("profiles").entrySet()) {
            JsonObject p = e.getValue().getAsJsonObject();
            String dir = str(p, "gameDir");
            String version = str(p, "lastVersionId");
            if (dir == null || version == null || version.startsWith("latest-")) {
                continue;
            }
            Path game = Path.of(dir);
            if (!Files.isDirectory(game)) {
                continue;
            }
            InstanceKind kind = InstanceKind.VANILLA;
            String mc = version;
            String loader = null;
            Matcher m;
            if ((m = FABRIC_ID.matcher(version)).matches()) {
                kind = InstanceKind.FABRIC;
                loader = m.group(1);
                mc = m.group(2);
            } else if ((m = QUILT_ID.matcher(version)).matches()) {
                kind = InstanceKind.QUILT;
                loader = m.group(1);
                mc = m.group(2);
            } else if ((m = FORGE_ID.matcher(version)).matches()) {
                kind = InstanceKind.FORGE;
                mc = m.group(1);
                loader = mc + "-" + m.group(2);
            } else if ((m = NEOFORGE_ID.matcher(version)).matches()) {
                kind = InstanceKind.NEOFORGE;
                loader = m.group(1);
                mc = null; // the official profile does not name the Minecraft version of NeoForge
            }
            String name = str(p, "name") != null && !str(p, "name").isBlank() ? str(p, "name") : version;
            Found f = found(Source.OFFICIAL, name, mc, kind, loader, game, null);
            if (f != null) {
                out.add(f);
            }
        }
        return out;
    }

    private static Found found(Source s, String name, String mc, InstanceKind kind, String loader, Path game, Path icon) {
        if (mc == null || !mc.matches("[0-9A-Za-z._-]{1,32}") || !Files.isDirectory(game)) {
            return null;
        }
        if (loader != null && !loader.matches("[A-Za-z0-9._+-]{1,80}")) {
            loader = null;
        }
        if (kind == InstanceKind.FORGE && loader != null && !loader.startsWith(mc + "-")) {
            loader = mc + "-" + loader;
        }
        String n = name == null || name.isBlank() ? game.getFileName().toString() : name.strip();
        if (n.length() > InstanceManager.MAX_NAME) {
            n = n.substring(0, InstanceManager.MAX_NAME);
        }
        return new Found(s, n, mc, kind, loader, game, icon);
    }

    /** Size of what an import copies. */
    public static long size(Found f, boolean worlds) {
        long total = FileUtil.treeSize(f.gameDir());
        return worlds ? total : total - FileUtil.treeSize(f.gameDir().resolve("saves"));
    }

    /**
     * Copies a found instance into a new, independent Lumora instance named {@code name}. The source is only read. On
     * failure or cancel the new instance is removed again.
     *
     * @param progress (files done, files total)
     */
    public static Instance importFound(Found f, String name, boolean worlds, InstanceManager instances, BiConsumer<Integer, Integer> progress)
            throws IOException {
        Instance spec = new Instance();
        spec.name = name;
        spec.kind = f.kind().id();
        spec.minecraftVersion = f.minecraftVersion();
        spec.loaderVersion = f.loaderVersion();
        spec.loaderPinned = f.loaderVersion() != null;
        spec.source = "import:" + f.source().name().toLowerCase(Locale.ROOT);
        Instance inst = instances.create(spec);
        try {
            copy(f.gameDir(), inst.gameDir(), rel -> SKIP.matcher(rel).matches() || !worlds && (rel.equals("saves") || rel.startsWith("saves/")), progress);
            if (f.icon() != null && Files.isRegularFile(f.icon())) {
                inst = instances.setIcon(inst, f.icon());
            }
            Log.info("Imported " + f.source() + " instance " + f.name() + " as " + inst.id);
            return inst;
        } catch (IOException | RuntimeException e) {
            try {
                instances.delete(inst);
            } catch (IOException cleanup) {
                Log.warn("Could not remove the incomplete instance: " + cleanup.getMessage());
            }
            throw e;
        }
    }

    private static void copy(Path from, Path to, java.util.function.Predicate<String> skip, BiConsumer<Integer, Integer> progress) throws IOException {
        Path src = from.toAbsolutePath().normalize();
        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(src)) {
            for (Path p : walk.toList()) {
                if (Files.isSymbolicLink(p) || !Files.isRegularFile(p)) {
                    continue;
                }
                String rel = src.relativize(p).toString().replace(java.io.File.separatorChar, '/');
                if (!skip.test(rel)) {
                    files.add(p);
                }
            }
        }
        int n = 0;
        for (Path p : files) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.io.InterruptedIOException(Lang.t("content.cancelled"));
            }
            Path dest = FileUtil.resolveInside(to, src.relativize(p).toString());
            Files.createDirectories(dest.getParent());
            Files.copy(p, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (progress != null) {
                progress.accept(++n, files.size());
            }
        }
    }

    /** Whether a zip is a Lumora instance archive. */
    public static boolean isLumoraArchive(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            return zip.getEntry(InstanceExporter.ARCHIVE_MARKER) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /** Imports a Lumora archive as a new instance (safe paths only). */
    public static Instance importArchive(Path file, String name, InstanceManager instances) throws IOException {
        Instance spec;
        try (ZipFile zip = new ZipFile(file.toFile())) {
            byte[] raw = SafeZip.read(zip, InstanceExporter.ARCHIVE_MARKER, 1024 * 1024);
            if (raw == null) {
                throw new IOException(Lang.t("import.error.not_archive"));
            }
            spec = Json.GSON.fromJson(new String(raw, StandardCharsets.UTF_8), Instance.class);
        } catch (com.google.gson.JsonParseException e) {
            throw new IOException(Lang.t("import.error.not_archive"), e);
        }
        Instance clean = new Instance();
        clean.name = name != null && !name.isBlank() ? name : spec.name;
        clean.kind = spec.kind;
        clean.minecraftVersion = spec.minecraftVersion;
        clean.loaderVersion = spec.loaderVersion;
        clean.loaderPinned = spec.loaderPinned;
        clean.minMemoryMb = spec.minMemoryMb;
        clean.maxMemoryMb = spec.maxMemoryMb;
        clean.windowWidth = spec.windowWidth;
        clean.windowHeight = spec.windowHeight;
        clean.jvmArgs = spec.jvmArgs;
        clean.source = "archive";
        Instance inst = instances.create(clean);
        try (ZipFile zip = new ZipFile(file.toFile())) {
            SafeZip.extract(zip, "minecraft/", inst.gameDir(), rel -> true);
            if (spec.icon != null && spec.icon.matches("icon\\.(png|jpg|jpeg)") && zip.getEntry(spec.icon) != null) {
                Path tmp = Files.createTempFile("lumora-icon", "." + spec.icon.substring(5));
                try {
                    Files.write(tmp, SafeZip.read(zip, spec.icon, 2 * 1024 * 1024));
                    inst = instances.setIcon(inst, tmp);
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
            return inst;
        } catch (IOException | RuntimeException e) {
            try {
                instances.delete(inst);
            } catch (IOException cleanup) {
                Log.warn("Could not remove the incomplete instance: " + cleanup.getMessage());
            }
            throw e;
        }
    }

    private static JsonObject read(Path f) throws IOException {
        if (Files.size(f) > 4 * 1024 * 1024) {
            throw new IOException(f.getFileName() + " too large");
        }
        return JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o == null ? null : o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
}
