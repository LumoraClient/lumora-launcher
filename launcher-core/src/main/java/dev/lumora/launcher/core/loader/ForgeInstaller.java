package dev.lumora.launcher.core.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.install.ProgressListener;
import dev.lumora.launcher.core.mojang.MavenCoordinate;
import dev.lumora.launcher.core.mojang.VersionJson;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Hashing;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Installs Forge and NeoForge from their official installer jar without running the installer itself. The installer
 * only describes what to do; this class does the client side of it:
 * <ol>
 *   <li>download the installer from the project's Maven (verified with the {@code .sha1} published next to it)</li>
 *   <li>read {@code install_profile.json} and the launch profile {@code version.json}</li>
 *   <li>download every library with the SHA-1 the profile states, or take it from the installer's {@code maven/}</li>
 *   <li>run the client "processors" (the projects' own tools that patch the Minecraft jar) with the version's Java,
 *       and check each declared output against its SHA-1</li>
 *   <li>write the launch profile to {@code versions/<id>/<id>.json}</li>
 * </ol>
 * Three formats are supported: the old {@code install}+{@code versionInfo} profile (up to 1.12.2 early builds, e.g.
 * 1.8.9), spec 0 (1.12.2) and spec 1 (1.13 and later, NeoForge). The Mojang mappings that newer Forge versions ask a
 * processor to download are downloaded here instead, through the launcher's verified downloader.
 */
final class ForgeInstaller {
    private static final Pattern TOKEN = Pattern.compile("\\{([A-Z0-9_]+)}");
    private static final long PROCESSOR_TIMEOUT_MINUTES = 10;
    /** Written next to the launch profile once processors and libraries are complete. */
    static final String DONE_MARKER = ".lumora-installed";

    private final Downloader downloader;
    private final Path libraries;
    private final Path versions;

    ForgeInstaller(Downloader downloader, Path libraries, Path versions) {
        this.downloader = downloader;
        this.libraries = libraries;
        this.versions = versions;
    }

    /**
     * @param installerUrl  https URL of the installer jar on the project's Maven (allow-listed host)
     * @param installerCoord Maven coordinate of the installer (its place below libraries/)
     * @param profileId     id the launch profile gets (folder below versions/)
     * @param name          "Forge" / "NeoForge" for progress messages
     */
    VersionJson install(URI installerUrl, MavenCoordinate installerCoord, String profileId, String name,
                        ModLoader.Install req, ProgressListener progress) throws IOException {
        Path profileFile = FileUtil.resolveInside(versions, profileId + "/" + profileId + ".json");
        Path done = profileFile.resolveSibling(DONE_MARKER);
        if (!req.repair() && Files.isRegularFile(profileFile) && Files.isRegularFile(done) && outputsPresent(done)) {
            return Json.read(profileFile, VersionJson.class);
        }

        progress.stage("Lade " + name + "-Installer " + req.loaderVersion());
        progress.progress(-1, "");
        Path installer = FileUtil.resolveInside(libraries, installerCoord.path());
        String sha1 = downloader.fetchString(URI.create(installerUrl + ".sha1")).strip().split("\\s+")[0];
        downloader.download(new Downloader.Task(installerUrl, installer, Hashing.Expected.sha1(sha1), 0), null,
                req.repair() ? Downloader.Verify.FULL : Downloader.Verify.QUICK);

        Path work = FileUtil.resolveInside(versions, profileId + "/.install");
        deleteTree(work);
        Files.createDirectories(work);
        try (ZipFile zip = new ZipFile(installer.toFile())) {
            JsonObject profile = readJson(zip, "install_profile.json");
            List<String> outputs = new ArrayList<>();
            JsonObject version;
            if (profile.has("install") && profile.has("versionInfo")) {
                version = legacy(zip, profile, progress);
            } else {
                String jsonPath = profile.has("json") ? profile.get("json").getAsString() : "/version.json";
                version = readJson(zip, jsonPath.startsWith("/") ? jsonPath.substring(1) : jsonPath);
                List<JsonObject> libs = new ArrayList<>();
                addAll(libs, profile.getAsJsonArray("libraries"));
                addAll(libs, version.getAsJsonArray("libraries"));
                fetchLibraries(zip, libs, req, name, progress);
                outputs = runProcessors(zip, profile, installer, work, req, name, progress);
            }
            version.addProperty("id", profileId);
            version.addProperty("inheritsFrom", req.minecraftVersion());
            String body = Json.GSON.toJson(version);
            VersionJson parsed = Json.GSON.fromJson(body, VersionJson.class);
            if (parsed.mainClass == null) {
                throw new IOException(name + "-Profil ohne mainClass");
            }
            FileUtil.writeAtomically(profileFile, body.getBytes(StandardCharsets.UTF_8));
            FileUtil.writeAtomically(done, String.join("\n", outputs).getBytes(StandardCharsets.UTF_8));
            return parsed;
        } catch (JsonParseException | IllegalStateException | ClassCastException | NullPointerException e) {
            throw new IOException("Ungültiger " + name + "-Installer: " + e.getMessage(), e);
        } finally {
            deleteTree(work);
        }
    }

    /** All processor outputs listed in the marker still exist (a cleaned libraries folder needs a new install). */
    private boolean outputsPresent(Path done) throws IOException {
        for (String line : Files.readAllLines(done, StandardCharsets.UTF_8)) {
            if (!line.isBlank() && !Files.isRegularFile(Path.of(line.strip()))) {
                return false;
            }
        }
        return true;
    }

    // ---- old format: install + versionInfo (Forge 1.8.9 and other builds before 1.12.2-14.23.5.2851)

    private JsonObject legacy(ZipFile zip, JsonObject profile, ProgressListener progress) throws IOException {
        JsonObject install = profile.getAsJsonObject("install");
        JsonObject info = profile.getAsJsonObject("versionInfo").deepCopy();
        MavenCoordinate forge = MavenCoordinate.parse(install.get("path").getAsString());
        Path forgeJar = FileUtil.resolveInside(libraries, forge.path());
        progress.stage("Entpacke " + forge.fileName());
        extract(zip, install.get("filePath").getAsString(), forgeJar);
        JsonArray libs = new JsonArray();
        for (JsonElement e : info.getAsJsonArray("libraries")) {
            JsonObject l = e.getAsJsonObject();
            if (l.has("clientreq") && !l.get("clientreq").getAsBoolean()) {
                continue; // server only
            }
            MavenCoordinate c = MavenCoordinate.parse(l.get("name").getAsString());
            JsonObject artifact = new JsonObject();
            artifact.addProperty("path", c.path());
            if (c.dedupeKey().equals(forge.dedupeKey())) {
                artifact.addProperty("url", ""); // taken from the installer above
                artifact.addProperty("sha1", Hashing.hash(forgeJar, Hashing.Algorithm.SHA1));
                artifact.addProperty("size", Files.size(forgeJar));
            } else {
                String base = l.has("url") ? l.get("url").getAsString() : "https://libraries.minecraft.net/";
                String url = (base.endsWith("/") ? base : base + "/") + c.path();
                artifact.addProperty("url", url);
                if (l.has("checksums") && l.get("checksums").isJsonArray()) {
                    // the old profile lists the accepted SHA-1s itself (the Maven has no .sha1 for some of them)
                    List<String> accepted = new ArrayList<>();
                    l.getAsJsonArray("checksums").forEach(s -> accepted.add(s.getAsString().toLowerCase(java.util.Locale.ROOT)));
                    artifact.addProperty("sha1", fetchChecked(URI.create(url), FileUtil.resolveInside(libraries, c.path()), accepted));
                }
                // otherwise no sha1: the client installer reads the .sha1 the Maven publishes next to the jar
            }
            JsonObject downloads = new JsonObject();
            downloads.add("artifact", artifact);
            JsonObject lib = new JsonObject();
            lib.addProperty("name", c.coordinates());
            lib.add("downloads", downloads);
            libs.add(lib);
        }
        info.add("libraries", libs);
        return info;
    }

    /** Max. size of one old Forge library (the Scala compiler is the largest, about 13 MB). */
    private static final int MAX_LEGACY_LIBRARY = 64 << 20;

    /**
     * Makes sure {@code target} is the file at {@code url} and its SHA-1 is one of {@code accepted}; returns that hash.
     * An existing file that matches is kept.
     */
    private String fetchChecked(URI url, Path target, List<String> accepted) throws IOException {
        if (Files.isRegularFile(target)) {
            String have = Hashing.hash(target, Hashing.Algorithm.SHA1);
            if (accepted.contains(have)) {
                return have;
            }
        }
        byte[] data = downloader.fetchBytes(url, MAX_LEGACY_LIBRARY);
        String sha1 = Hashing.hash(data, Hashing.Algorithm.SHA1);
        if (!accepted.contains(sha1)) {
            throw new IOException("Prüfsumme passt nicht: " + target.getFileName() + " (" + sha1 + ")");
        }
        FileUtil.writeAtomically(target, data);
        return sha1;
    }

    // ---- spec 0 / spec 1

    private void fetchLibraries(ZipFile zip, List<JsonObject> libs, ModLoader.Install req, String name,
                                ProgressListener progress) throws IOException {
        progress.stage("Lade " + name + "-Bibliotheken");
        List<Downloader.Task> tasks = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (JsonObject l : libs) {
            JsonObject a = l.has("downloads") && l.getAsJsonObject("downloads").has("artifact")
                    ? l.getAsJsonObject("downloads").getAsJsonObject("artifact") : null;
            MavenCoordinate c = MavenCoordinate.parse(l.get("name").getAsString());
            String path = a != null && a.has("path") && !a.get("path").getAsString().isEmpty() ? a.get("path").getAsString() : c.path();
            if (seen.putIfAbsent(path, true) != null) {
                continue;
            }
            Path target = FileUtil.resolveInside(libraries, path);
            String url = a != null && a.has("url") ? a.get("url").getAsString() : "";
            String sha1 = a != null && a.has("sha1") ? a.get("sha1").getAsString() : "";
            if (!url.isEmpty()) {
                if (sha1.isEmpty()) {
                    sha1 = downloader.fetchString(URI.create(url + ".sha1")).strip().split("\\s+")[0];
                }
                tasks.add(new Downloader.Task(URI.create(url), target, Hashing.Expected.sha1(sha1),
                        a.has("size") ? a.get("size").getAsLong() : 0));
            } else if (zip.getEntry("maven/" + path) != null) {
                // shipped inside the installer (e.g. the Forge jar of 1.12.2)
                if (req.repair() || !Files.isRegularFile(target) || (!sha1.isEmpty() && !Hashing.matches(target, Hashing.Expected.sha1(sha1)))) {
                    extract(zip, "maven/" + path, target);
                }
                if (!sha1.isEmpty() && !Hashing.matches(target, Hashing.Expected.sha1(sha1))) {
                    throw new IOException(path + " im Installer hat eine falsche Prüfsumme");
                }
            }
            // else: produced by a processor below
        }
        downloader.downloadAll(tasks, (bytes, total, files, filesTotal) -> progress.progress(
                filesTotal == 0 ? 1 : (double) files / filesTotal, name + "-Bibliotheken: " + files + "/" + filesTotal),
                req.repair() ? Downloader.Verify.FULL : Downloader.Verify.QUICK);
    }

    private List<String> runProcessors(ZipFile zip, JsonObject profile, Path installer, Path work, ModLoader.Install req,
                                       String name, ProgressListener progress) throws IOException {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("SIDE", "client");
        data.put("MINECRAFT_JAR", req.vanillaJar().toAbsolutePath().toString());
        data.put("MINECRAFT_VERSION", req.minecraftVersion());
        data.put("INSTALLER", installer.toAbsolutePath().toString());
        data.put("LIBRARY_DIR", libraries.toAbsolutePath().toString());
        data.put("ROOT", libraries.toAbsolutePath().getParent().toString());
        if (profile.has("data")) {
            for (Map.Entry<String, JsonElement> e : profile.getAsJsonObject("data").entrySet()) {
                JsonObject sides = e.getValue().getAsJsonObject();
                if (sides.has("client")) {
                    data.put(e.getKey(), dataValue(zip, sides.get("client").getAsString(), work));
                }
            }
        }
        List<JsonObject> processors = new ArrayList<>();
        if (profile.has("processors")) {
            for (JsonElement e : profile.getAsJsonArray("processors")) {
                JsonObject p = e.getAsJsonObject();
                if (!p.has("sides") || containsString(p.getAsJsonArray("sides"), "client")) {
                    processors.add(p);
                }
            }
        }
        List<String> allOutputs = new ArrayList<>();
        int i = 0;
        for (JsonObject p : processors) {
            i++;
            progress.stage(name + " wird eingerichtet (" + i + "/" + processors.size() + ")");
            progress.progress((double) (i - 1) / processors.size(), "");
            List<String> args = new ArrayList<>();
            for (JsonElement a : p.getAsJsonArray("args")) {
                args.add(resolve(a.getAsString(), data));
            }
            Map<Path, String> outputs = new LinkedHashMap<>();
            if (p.has("outputs")) {
                for (Map.Entry<String, JsonElement> o : p.getAsJsonObject("outputs").entrySet()) {
                    outputs.put(Path.of(resolve(o.getKey(), data)), unquote(resolve(o.getValue().getAsString(), data)));
                }
            }
            outputs.keySet().forEach(o -> allOutputs.add(o.toString()));
            if (!outputs.isEmpty() && !req.repair() && outputsMatch(outputs)) {
                continue;
            }
            int task = args.indexOf("--task");
            if (task >= 0 && task + 1 < args.size() && args.get(task + 1).equals("DOWNLOAD_MOJMAPS")) {
                downloadMojmaps(args, req);
                continue;
            }
            run(p, args, work, req, name);
            if (!outputsMatch(outputs)) {
                throw new IOException(name + "-Einrichtung: Ergebnis von " + p.get("jar").getAsString() + " hat eine falsche Prüfsumme");
            }
        }
        // outputs without a stated hash (e.g. the patched jar of NeoForge) must at least exist
        for (JsonObject p : processors) {
            for (JsonElement a : p.getAsJsonArray("args")) {
                String s = a.getAsString();
                if (s.equals("{PATCHED}") && data.containsKey("PATCHED")) {
                    Path patched = Path.of(data.get("PATCHED"));
                    if (!Files.isRegularFile(patched)) {
                        throw new IOException(name + "-Einrichtung hat " + patched.getFileName() + " nicht erzeugt");
                    }
                    if (!allOutputs.contains(patched.toString())) {
                        allOutputs.add(patched.toString());
                    }
                }
            }
        }
        progress.progress(1, "");
        return allOutputs;
    }

    /** Newer Forge versions let a processor download Mojang's mappings; done here with the launcher's verified downloader. */
    private void downloadMojmaps(List<String> args, ModLoader.Install req) throws IOException {
        String side = valueAfter(args, "--side", "client");
        Path output = Path.of(valueAfter(args, "--output", null));
        VersionJson.Download d = req.vanilla().downloads == null ? null : req.vanilla().downloads.get(side + "_mappings");
        if (d == null || d.url == null || d.sha1 == null) {
            throw new IOException("Minecraft " + req.minecraftVersion() + " hat keine offiziellen " + side + "-Mappings");
        }
        downloader.download(new Downloader.Task(URI.create(d.url), output, Hashing.Expected.sha1(d.sha1), d.size), null,
                req.repair() ? Downloader.Verify.FULL : Downloader.Verify.QUICK);
    }

    private void run(JsonObject p, List<String> args, Path work, ModLoader.Install req, String name) throws IOException {
        Path jar = FileUtil.resolveInside(libraries, MavenCoordinate.parse(p.get("jar").getAsString()).path());
        if (!Files.isRegularFile(jar)) {
            throw new IOException(name + "-Werkzeug fehlt: " + jar.getFileName());
        }
        String main;
        try (JarFile jf = new JarFile(jar.toFile())) {
            main = jf.getManifest() == null ? null : jf.getManifest().getMainAttributes().getValue("Main-Class");
        }
        if (main == null || !main.matches("[A-Za-z0-9_.$]+")) {
            throw new IOException(name + "-Werkzeug ohne Main-Class: " + jar.getFileName());
        }
        List<String> cp = new ArrayList<>();
        cp.add(jar.toAbsolutePath().toString());
        if (p.has("classpath")) {
            for (JsonElement c : p.getAsJsonArray("classpath")) {
                cp.add(FileUtil.resolveInside(libraries, MavenCoordinate.parse(c.getAsString()).path()).toAbsolutePath().toString());
            }
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(req.java().toAbsolutePath().toString());
        cmd.add("-cp");
        cmd.add(String.join(File.pathSeparator, cp));
        cmd.add(main);
        cmd.addAll(args);
        Path log = work.resolve("processor.log");
        Log.info(name + " processor " + p.get("jar").getAsString());
        Process proc = new ProcessBuilder(cmd).directory(work.toFile()).redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        try {
            if (!proc.waitFor(PROCESSOR_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                proc.destroyForcibly();
                throw new IOException(name + "-Werkzeug " + p.get("jar").getAsString() + " hat nach " + PROCESSOR_TIMEOUT_MINUTES + " min nicht geantwortet");
            }
        } catch (InterruptedException e) {
            proc.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new java.io.InterruptedIOException("Abgebrochen");
        }
        if (proc.exitValue() != 0) {
            List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
            String tail = String.join("\n", lines.subList(Math.max(0, lines.size() - 12), lines.size()));
            Log.warn(name + " processor failed:\n" + tail);
            throw new IOException(name + "-Werkzeug " + p.get("jar").getAsString() + " ist fehlgeschlagen (Code "
                    + proc.exitValue() + "): " + (lines.isEmpty() ? "" : lines.getLast()));
        }
    }

    private static boolean outputsMatch(Map<Path, String> outputs) throws IOException {
        for (Map.Entry<Path, String> o : outputs.entrySet()) {
            if (!Files.isRegularFile(o.getKey()) || !Hashing.matches(o.getKey(), Hashing.Expected.sha1(o.getValue()))) {
                return false;
            }
        }
        return true;
    }

    /** A data entry: [maven coordinate] -> library path, 'literal' -> literal, /path -> extracted from the installer. */
    private String dataValue(ZipFile zip, String value, Path work) throws IOException {
        if (value.startsWith("[") && value.endsWith("]")) {
            return FileUtil.resolveInside(libraries, MavenCoordinate.parse(value.substring(1, value.length() - 1)).path()).toAbsolutePath().toString();
        }
        if (value.startsWith("'") && value.endsWith("'")) {
            return value; // quotes are removed where the literal is used (hashes) or kept out of paths below
        }
        if (value.startsWith("/")) {
            Path target = FileUtil.resolveInside(work, value.substring(1));
            extract(zip, value.substring(1), target);
            return target.toAbsolutePath().toString();
        }
        return value;
    }

    /** Replaces {TOKEN}s and a whole-argument [maven coordinate]. */
    String resolve(String arg, Map<String, String> data) throws IOException {
        if (arg.startsWith("[") && arg.endsWith("]")) {
            return FileUtil.resolveInside(libraries, MavenCoordinate.parse(arg.substring(1, arg.length() - 1)).path()).toAbsolutePath().toString();
        }
        Matcher m = TOKEN.matcher(arg);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = data.get(m.group(1));
            if (v == null) {
                throw new IOException("Unbekannter Platzhalter {" + m.group(1) + "} im Installer");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(unquote(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String unquote(String v) {
        return v.length() >= 2 && v.startsWith("'") && v.endsWith("'") ? v.substring(1, v.length() - 1) : v;
    }

    private static String valueAfter(List<String> args, String option, String fallback) throws IOException {
        int i = args.indexOf(option);
        if (i >= 0 && i + 1 < args.size()) {
            return args.get(i + 1);
        }
        if (fallback == null) {
            throw new IOException("Installer-Aufruf ohne " + option);
        }
        return fallback;
    }

    private static boolean containsString(JsonArray a, String s) {
        for (JsonElement e : a) {
            if (s.equals(e.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static void addAll(List<JsonObject> out, JsonArray a) {
        if (a != null) {
            a.forEach(e -> out.add(e.getAsJsonObject()));
        }
    }

    private static JsonObject readJson(ZipFile zip, String entry) throws IOException {
        ZipEntry e = zip.getEntry(entry);
        if (e == null) {
            throw new IOException("Installer ohne " + entry);
        }
        try (InputStream in = zip.getInputStream(e)) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** Extracts one entry (no path traversal: the target is resolved by the caller inside a known folder). */
    private static void extract(ZipFile zip, String entry, Path target) throws IOException {
        ZipEntry e = zip.getEntry(entry);
        if (e == null || e.isDirectory()) {
            throw new IOException("Installer ohne " + entry);
        }
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream in = zip.getInputStream(e)) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        FileUtil.moveReplacing(tmp, target);
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
