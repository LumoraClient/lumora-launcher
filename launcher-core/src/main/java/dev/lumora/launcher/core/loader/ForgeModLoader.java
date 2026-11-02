package dev.lumora.launcher.core.loader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.install.ProgressListener;
import dev.lumora.launcher.core.mojang.MavenCoordinate;
import dev.lumora.launcher.core.mojang.VersionJson;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * MinecraftForge from maven.minecraftforge.net. Versions come from the Maven metadata, the recommendation from Forge's
 * promotions file. Forge asks launchers that automate its installation to point users to the project's support page,
 * which the loader page does ({@link #supportUrl()}).
 */
public final class ForgeModLoader implements ModLoader {
    static final String MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge/";
    static final URI PROMOTIONS = URI.create("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json");

    private final Downloader downloader;
    private final ForgeInstaller installer;
    private final Cache cache = new Cache();

    public ForgeModLoader(Downloader downloader, Path libraries, Path versions) {
        this.downloader = downloader;
        this.installer = new ForgeInstaller(downloader, libraries, versions);
    }

    @Override
    public String id() {
        return "forge";
    }

    @Override
    public String displayName() {
        return "Forge";
    }

    @Override
    public String description() {
        return "Der klassische Loader für große Modpacks und Tech-Mods. Von 1.8.9 bis 26.3.";
    }

    @Override
    public URI supportUrl() {
        // the address Forge's own install profile names for launchers that automate the installation
        return URI.create("https://www.patreon.com/LexManos/");
    }

    @Override
    public boolean needsOwnClientJar() {
        return true;
    }

    @Override
    public List<LoaderVersion> versions(String mc) throws IOException {
        List<String> all = MavenMetadata.versions(cache.get(downloader, URI.create(MAVEN + "maven-metadata.xml")));
        String recommended = null;
        try {
            JsonObject promos = JsonParser.parseString(cache.get(downloader, PROMOTIONS)).getAsJsonObject().getAsJsonObject("promos");
            String key = promos.has(mc + "-recommended") ? mc + "-recommended" : mc + "-latest";
            recommended = promos.has(key) ? promos.get(key).getAsString() : null;
        } catch (IOException | JsonParseException | IllegalStateException | NullPointerException e) {
            // no recommendation: the newest version is offered first anyway
        }
        return list(all, mc, recommended);
    }

    /** Maven versions "1.20.1-47.4.28" / "1.8.9-11.15.1.2318-1.8.9" of {@code mc}, newest first. */
    static List<LoaderVersion> list(List<String> maven, String mc, String recommended) {
        List<LoaderVersion> out = new ArrayList<>();
        for (String v : maven) {
            if (!v.startsWith(mc + "-")) {
                continue;
            }
            String label = label(v, mc);
            if (!label.matches("\\d+(\\.\\d+){2,3}")) {
                continue; // branch builds with odd names are left out
            }
            out.add(new LoaderVersion(v, label, true, label.equals(recommended)));
        }
        out.sort((a, b) -> MavenMetadata.compare(b.label(), a.label()));
        if (out.stream().noneMatch(LoaderVersion::recommended) && !out.isEmpty()) {
            LoaderVersion first = out.getFirst();
            out.set(0, new LoaderVersion(first.version(), first.label(), true, true));
        }
        return out;
    }

    /** "1.8.9-11.15.1.2318-1.8.9" -> "11.15.1.2318". */
    static String label(String mavenVersion, String mc) {
        String s = mavenVersion.substring(mc.length() + 1);
        return s.endsWith("-" + mc) ? s.substring(0, s.length() - mc.length() - 1) : s;
    }

    @Override
    public String profileId(String mc, String loaderVersion) {
        return "forge-" + loaderVersion;
    }

    @Override
    public VersionJson install(Install r, ProgressListener progress) throws IOException {
        if (!r.loaderVersion().startsWith(r.minecraftVersion() + "-")) {
            throw new IOException("Forge " + r.loaderVersion() + " gehört nicht zu Minecraft " + r.minecraftVersion());
        }
        MavenCoordinate installer = MavenCoordinate.parse("net.minecraftforge:forge:" + r.loaderVersion() + ":installer");
        URI url = URI.create(MAVEN + r.loaderVersion() + "/" + installer.fileName());
        return this.installer.install(url, installer, profileId(r.minecraftVersion(), r.loaderVersion()), "Forge", r, progress);
    }

    /** Small in-memory cache for the large version lists (10 minutes). */
    static final class Cache {
        private final java.util.Map<URI, String> bodies = new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.Map<URI, Long> times = new java.util.concurrent.ConcurrentHashMap<>();

        String get(Downloader d, URI uri) throws IOException {
            Long t = times.get(uri);
            if (t != null && System.currentTimeMillis() - t < 600_000) {
                return bodies.get(uri);
            }
            String body = d.fetchString(uri);
            bodies.put(uri, body);
            times.put(uri, System.currentTimeMillis());
            return body;
        }
    }
}
