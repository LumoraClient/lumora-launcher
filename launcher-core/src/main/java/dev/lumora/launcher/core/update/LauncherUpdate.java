package dev.lumora.launcher.core.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Hashing;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Self-update of the portable LumoraClient.exe from GitHub Releases (official REST API,
 * {@code GET /repos/{owner}/{repo}/releases/latest}).
 * <p>A release must carry three assets: {@code LumoraClient.exe}, {@code SHA256SUMS.txt} and {@code SHA256SUMS.txt.sig}
 * (Ed25519 signature of SHA256SUMS.txt, base64). The launcher only installs an EXE whose SHA-256 is listed in a
 * SHA256SUMS.txt signed with the release key; its public half ships inside the launcher, the private half stays with
 * whoever builds releases (packaging/SignRelease.java). A compromised GitHub account alone cannot push an update.
 */
public final class LauncherUpdate {
    /** Where releases are published. */
    public static final String REPOSITORY = "LumoraClient/lumora-launcher";
    public static final String EXE = "LumoraClient.exe";
    public static final String SUMS = "SHA256SUMS.txt";
    public static final String SIG = "SHA256SUMS.txt.sig";
    /** Hosts of the GitHub API and of release downloads (assets are served from GitHub's object storage). */
    static final Set<String> HOSTS = Set.of("api.github.com", "github.com", "objects.githubusercontent.com",
            "release-assets.githubusercontent.com");
    static final long MAX_EXE = 200L * 1024 * 1024;

    /** A newer release found on GitHub. */
    public record Release(String version, String name, String notes, String pageUrl, URI exe, long exeSize, URI sums, URI sig) {
    }

    private final Downloader downloader;
    private final String api;
    private final PublicKey key;

    /** Production: GitHub API, bundled public key. */
    public LauncherUpdate() throws IOException {
        this(new Downloader(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15)).build(), HOSTS, false, 3, Duration.ofMillis(500), 1),
                "https://api.github.com/repos/" + REPOSITORY, bundledKey());
    }

    /** Tests: own API base and key. */
    public LauncherUpdate(Downloader downloader, String api, PublicKey key) {
        this.downloader = downloader;
        this.api = api;
        this.key = key;
    }

    /** The latest release if it is newer than {@code currentVersion}; empty when up to date or no release exists. */
    public Optional<Release> check(String currentVersion) throws IOException {
        return check(currentVersion, false);
    }

    /**
     * Like {@link #check(String)}; with {@code prereleases} (channel "Vorab") the newest of the recent releases including
     * pre-releases. Drafts never count, and every release needs the signed checksums as before.
     */
    public Optional<Release> check(String currentVersion, boolean prereleases) throws IOException {
        if (prereleases) {
            JsonArray all;
            try {
                all = JsonParser.parseString(downloader.fetchString(URI.create(api + "/releases?per_page=20"))).getAsJsonArray();
            } catch (RuntimeException e) {
                throw new IOException("Unerwartete Antwort von GitHub: " + e.getMessage());
            }
            Release best = null;
            for (JsonElement e : all) {
                Release r = e.isJsonObject() ? parse(e.getAsJsonObject(), true) : null;
                if (r != null && (best == null || compare(r.version(), best.version()) > 0)) {
                    best = r;
                }
            }
            return best != null && compare(best.version(), currentVersion) > 0 ? Optional.of(best) : Optional.empty();
        }
        String body;
        try {
            body = downloader.fetchString(URI.create(api + "/releases/latest"));
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("HTTP 404")) {
                return Optional.empty(); // no release published yet
            }
            throw e;
        }
        Release r = parse(body);
        if (r == null || compare(r.version(), currentVersion) <= 0) {
            return Optional.empty();
        }
        return Optional.of(r);
    }

    /** Release info from the GitHub API JSON, or null if a required asset is missing. */
    static Release parse(String json) throws IOException {
        JsonObject o;
        try {
            o = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Unerwartete Antwort von GitHub: " + e.getMessage());
        }
        return parse(o, false);
    }

    /** Like {@link #parse(String)}; {@code allowPrerelease}: pre-releases count too (drafts never). */
    static Release parse(JsonObject o, boolean allowPrerelease) throws IOException {
        if (o.has("draft") && o.get("draft").getAsBoolean()
                || !allowPrerelease && o.has("prerelease") && o.get("prerelease").getAsBoolean()) {
            return null;
        }
        String tag = str(o, "tag_name");
        if (tag == null) {
            return null;
        }
        Map<String, JsonObject> assets = new HashMap<>();
        JsonArray arr = o.has("assets") && o.get("assets").isJsonArray() ? o.getAsJsonArray("assets") : new JsonArray();
        for (JsonElement e : arr) {
            JsonObject a = e.getAsJsonObject();
            String n = str(a, "name");
            if (n != null) {
                assets.put(n, a);
            }
        }
        JsonObject exe = assets.get(EXE);
        JsonObject sums = assets.get(SUMS);
        JsonObject sig = assets.get(SIG);
        if (exe == null || sums == null || sig == null) {
            Log.warn("Release " + tag + " lacks " + EXE + ", " + SUMS + " or " + SIG + "; not offered as update");
            return null;
        }
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        String notes = str(o, "body");
        String page = str(o, "html_url");
        return new Release(version, str(o, "name") != null ? str(o, "name") : tag, notes == null ? "" : notes,
                page != null && page.startsWith("https://github.com/") ? page : null,
                URI.create(str(exe, "browser_download_url")), exe.has("size") ? exe.get("size").getAsLong() : -1,
                URI.create(str(sums, "browser_download_url")), URI.create(str(sig, "browser_download_url")));
    }

    /**
     * Downloads the release EXE into {@code dir}, after checking the signed checksum list. Returns the verified file;
     * nothing unverified is left behind.
     */
    public Path download(Release r, Path dir) throws IOException {
        String sums = downloader.fetchString(r.sums());
        String sig = downloader.fetchString(r.sig()).strip();
        if (!verify(sums.getBytes(StandardCharsets.UTF_8), sig)) {
            throw new IOException("Die Signatur des Updates ist ungültig. Das Update wurde nicht installiert.");
        }
        String sha256 = sha256Of(sums, EXE);
        if (sha256 == null) {
            throw new IOException(EXE + " fehlt in " + SUMS);
        }
        if (r.exeSize() > MAX_EXE) {
            throw new IOException("Update ist unerwartet groß");
        }
        Files.createDirectories(dir);
        Path target = dir.resolve("LumoraClient-" + r.version() + ".exe");
        downloader.download(new Downloader.Task(r.exe(), target, new Hashing.Expected(Hashing.Algorithm.SHA256, sha256), r.exeSize()),
                null, Downloader.Verify.FULL);
        if (!Hashing.matches(target, new Hashing.Expected(Hashing.Algorithm.SHA256, sha256))) {
            Files.deleteIfExists(target);
            throw new IOException("Prüfsumme des Updates stimmt nicht");
        }
        return target;
    }

    /**
     * Replaces the portable EXE the user started (its path comes from the portable starter) with the verified new one.
     * The starter has already exited at this point, so the file is not locked.
     */
    public static void install(Path verifiedExe, Path portableExe) throws IOException {
        Path tmp = portableExe.resolveSibling(portableExe.getFileName() + ".new");
        Files.copy(verifiedExe, tmp, StandardCopyOption.REPLACE_EXISTING);
        Path old = portableExe.resolveSibling(portableExe.getFileName() + ".old");
        Files.deleteIfExists(old);
        if (Files.exists(portableExe)) {
            Files.move(portableExe, old, StandardCopyOption.REPLACE_EXISTING);
        }
        FileUtil.moveReplacing(tmp, portableExe);
        try {
            Files.deleteIfExists(old);
        } catch (IOException e) {
            // still in use: removed at the next update
            Log.info("Old launcher kept as " + old);
        }
        Log.info("Launcher updated: " + portableExe);
    }

    /** The portable EXE this launcher was started from, if any (set by packaging/windows/portable/PortableLauncher.cs). */
    public static Optional<Path> portableExe() {
        return existingExe(System.getenv("LUMORA_PORTABLE_EXE"));
    }

    /** File next to the extracted versions in which the portable starter records where it lives (one line). */
    public static final String STARTER_RECORD = "starter-path.txt";
    /** Marker the portable starter writes into every folder it extracted. */
    static final String EXTRACTED_MARKER = ".lumora-extracted";
    /** Copy of LumoraClient.exe the launcher keeps itself when it cannot find the user's one. */
    public static final String MANAGED_EXE = "LumoraClient.exe";

    /** Where a self-update writes the new LumoraClient.exe, and how that place was found. */
    public record Target(Path exe, Source source) {
        public enum Source {
            /** started through LumoraClient.exe (environment variable of the starter) */
            STARTER,
            /** started directly from the extracted folder (pinned taskbar icon, shortcut); path the starter recorded */
            RECORDED,
            /** started directly and the starter left no record (versions before 0.2.2): a copy the launcher keeps */
            MANAGED_COPY
        }

        /** True when the file is the user's own LumoraClient.exe (not the launcher's managed copy). */
        public boolean starter() {
            return source != Source.MANAGED_COPY;
        }
    }

    /**
     * Target of a self-update. Through the starter it is always the starter's EXE. With {@code directStart} (setting
     * "Update auch beim direkten Start") a launcher started directly from {@code portable\<hash>\LumoraLauncher} also
     * updates itself: into the EXE the starter recorded, else into {@code %LOCALAPPDATA%\LumoraClient\LumoraClient.exe}.
     */
    public static Optional<Target> selfUpdateTarget(boolean directStart) {
        String app = System.getProperty("jpackage.app-path");
        return selfUpdateTarget(System.getenv("LUMORA_PORTABLE_EXE"), app == null ? null : Path.of(app), directStart);
    }

    static Optional<Target> selfUpdateTarget(String starterEnv, Path appPath, boolean directStart) {
        Optional<Path> starter = existingExe(starterEnv);
        if (starter.isPresent()) {
            return Optional.of(new Target(starter.get(), Target.Source.STARTER));
        }
        if (!directStart) {
            return Optional.empty();
        }
        Optional<Path> base = portableBase(appPath);
        if (base.isEmpty()) {
            return Optional.empty();
        }
        Optional<Path> recorded = recordedStarter(base.get());
        if (recorded.isPresent()) {
            return Optional.of(new Target(recorded.get(), Target.Source.RECORDED));
        }
        Path root = base.get().getParent();
        return root == null ? Optional.empty() : Optional.of(new Target(root.resolve(MANAGED_EXE), Target.Source.MANAGED_COPY));
    }

    /** True when this launcher runs from a folder the portable starter extracted. */
    public static boolean startedFromPortableFolder() {
        String app = System.getProperty("jpackage.app-path");
        return app != null && portableBase(Path.of(app)).isPresent();
    }

    /**
     * {@code <base>} for {@code <base>\<16 hex>\LumoraLauncher\LumoraLauncher.exe} when {@code <16 hex>} carries the
     * starter's marker; empty for every other location (installed launcher, IDE).
     */
    static Optional<Path> portableBase(Path appPath) {
        if (appPath == null) {
            return Optional.empty();
        }
        Path app = appPath.toAbsolutePath().normalize();
        Path imageDir = app.getParent();
        Path hashDir = imageDir == null ? null : imageDir.getParent();
        if (hashDir == null || hashDir.getParent() == null || hashDir.getFileName() == null
                || !hashDir.getFileName().toString().matches("[0-9a-f]{16}")
                || !Files.isRegularFile(hashDir.resolve(EXTRACTED_MARKER))) {
            return Optional.empty();
        }
        return Optional.of(hashDir.getParent());
    }

    /** The EXE path in {@code <base>/starter-path.txt}, if it still exists. */
    static Optional<Path> recordedStarter(Path base) {
        Path record = base.resolve(STARTER_RECORD);
        try {
            if (!Files.isRegularFile(record) || Files.size(record) > 4096) {
                return Optional.empty();
            }
            String line = Files.readString(record, StandardCharsets.UTF_8).strip();
            return line.contains("\n") ? Optional.empty() : existingExe(line);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<Path> existingExe(String p) {
        if (p == null || p.isBlank() || p.indexOf('\0') >= 0) {
            return Optional.empty();
        }
        try {
            Path path = Path.of(p);
            return path.isAbsolute() && Files.isRegularFile(path)
                    && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".exe")
                    ? Optional.of(path) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    boolean verify(byte[] data, String base64Signature) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update(data);
            return s.verify(Base64.getDecoder().decode(base64Signature));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Hash of a file name in a "hash  name" list (sha256sum format). */
    static String sha256Of(String sums, String file) {
        for (String line : sums.split("\\R")) {
            String[] p = line.strip().split("\\s+\\*?", 2);
            if (p.length == 2 && p[1].strip().equals(file) && p[0].matches("[0-9a-fA-F]{64}")) {
                return p[0].toLowerCase(java.util.Locale.ROOT);
            }
        }
        return null;
    }

    /** Compares "0.2.0", "0.10.1", "1.0.0-beta" numerically (pre-release suffix sorts before the release). */
    public static int compare(String a, String b) {
        String[] x = a.split("[-+]", 2);
        String[] y = b.split("[-+]", 2);
        String[] xa = x[0].split("\\.");
        String[] ya = y[0].split("\\.");
        for (int i = 0; i < Math.max(xa.length, ya.length); i++) {
            int xi = i < xa.length ? num(xa[i]) : 0;
            int yi = i < ya.length ? num(ya[i]) : 0;
            if (xi != yi) {
                return Integer.compare(xi, yi);
            }
        }
        boolean xp = x.length > 1;
        boolean yp = y.length > 1;
        return xp == yp ? 0 : xp ? -1 : 1;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
    }

    /** Public release key bundled as /lumora/update-key.pub (X.509, base64). */
    static PublicKey bundledKey() throws IOException {
        try (InputStream in = LauncherUpdate.class.getResourceAsStream("/lumora/update-key.pub")) {
            if (in == null) {
                throw new IOException("Update-Schlüssel fehlt im Launcher");
            }
            byte[] der = Base64.getDecoder().decode(new String(in.readAllBytes(), StandardCharsets.US_ASCII).strip());
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IOException("Update-Schlüssel ungültig: " + e.getMessage());
        }
    }
}
