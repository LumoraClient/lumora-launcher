package dev.lumora.launcher.core.loader;

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
 * NeoForge from maven.neoforged.net. From 1.20.2 on the version encodes the Minecraft version ("21.4.x" for 1.21.4,
 * "26.3.0.x" for 26.3); for 1.20.1 NeoForge still published "net.neoforged:forge:1.20.1-47.1.x".
 */
public final class NeoForgeModLoader implements ModLoader {
    static final String MAVEN = "https://maven.neoforged.net/releases/net/neoforged/";

    private final Downloader downloader;
    private final ForgeInstaller installer;
    private final ForgeModLoader.Cache cache = new ForgeModLoader.Cache();

    public NeoForgeModLoader(Downloader downloader, Path libraries, Path versions) {
        this.downloader = downloader;
        this.installer = new ForgeInstaller(downloader, libraries, versions);
    }

    @Override
    public String id() {
        return "neoforge";
    }

    @Override
    public String displayName() {
        return "NeoForge";
    }

    @Override
    public String description() {
        return "Moderner Nachfolger von Forge mit aktiver Community. Ab Minecraft 1.20.1.";
    }

    @Override
    public URI supportUrl() {
        return URI.create("https://neoforged.net/");
    }

    @Override
    public boolean needsOwnClientJar() {
        return true;
    }

    /** 1.20.1 uses the old artifact "forge". */
    static boolean oldArtifact(String mc) {
        return mc.equals("1.20.1");
    }

    /** Version prefix of {@code mc}: "1.21.4" -> "21.4.", "1.21" -> "21.0.", "26.3" -> "26.3.0.", "26.1.2" -> "26.1.2."; null if none. */
    static String prefix(String mc) {
        String[] p = mc.split("\\.");
        if (mc.startsWith("1.") && (p.length == 2 || p.length == 3)) {
            int minor = Integer.parseInt(p[1]);
            if (minor < 20 || (minor == 20 && p.length == 3 && Integer.parseInt(p[2]) < 2)) {
                return null;
            }
            return p[1] + "." + (p.length == 3 ? p[2] : "0") + ".";
        }
        if (!mc.startsWith("1.") && (p.length == 2 || p.length == 3)) {
            return p[0] + "." + p[1] + "." + (p.length == 3 ? p[2] : "0") + ".";
        }
        return null;
    }

    @Override
    public boolean supports(String mc) {
        return oldArtifact(mc) || prefix(mc) != null;
    }

    @Override
    public List<LoaderVersion> versions(String mc) throws IOException {
        if (oldArtifact(mc)) {
            List<String> maven = MavenMetadata.versions(cache.get(downloader, URI.create(MAVEN + "forge/maven-metadata.xml")));
            List<LoaderVersion> out = new ArrayList<>();
            for (String v : maven) {
                if (v.startsWith(mc + "-")) {
                    out.add(new LoaderVersion(v, v.substring(mc.length() + 1), !MavenMetadata.isPreRelease(v), false));
                }
            }
            return withRecommended(out);
        }
        String prefix = prefix(mc);
        if (prefix == null) {
            return List.of();
        }
        return list(MavenMetadata.versions(cache.get(downloader, URI.create(MAVEN + "neoforge/maven-metadata.xml"))), prefix);
    }

    static List<LoaderVersion> list(List<String> maven, String prefix) {
        List<LoaderVersion> out = new ArrayList<>();
        for (String v : maven) {
            if (v.startsWith(prefix)) {
                out.add(new LoaderVersion(v, v, !MavenMetadata.isPreRelease(v), false));
            }
        }
        return withRecommended(out);
    }

    /** Newest first; the newest stable build is the recommended one. */
    private static List<LoaderVersion> withRecommended(List<LoaderVersion> in) {
        List<LoaderVersion> out = new ArrayList<>(in);
        out.sort((a, b) -> MavenMetadata.compare(b.label(), a.label()));
        for (int i = 0; i < out.size(); i++) {
            LoaderVersion v = out.get(i);
            if (v.stable()) {
                out.set(i, new LoaderVersion(v.version(), v.label(), true, true));
                break;
            }
        }
        return out;
    }

    @Override
    public String profileId(String mc, String loaderVersion) {
        return "neoforge-" + loaderVersion;
    }

    @Override
    public VersionJson install(Install r, ProgressListener progress) throws IOException {
        String mc = r.minecraftVersion();
        String v = r.loaderVersion();
        MavenCoordinate installer;
        if (oldArtifact(mc)) {
            if (!v.startsWith(mc + "-")) {
                throw new IOException("NeoForge " + v + " gehört nicht zu Minecraft " + mc);
            }
            installer = MavenCoordinate.parse("net.neoforged:forge:" + v + ":installer");
        } else {
            String prefix = prefix(mc);
            if (prefix == null || !v.startsWith(prefix)) {
                throw new IOException("NeoForge " + v + " gehört nicht zu Minecraft " + mc);
            }
            installer = MavenCoordinate.parse("net.neoforged:neoforge:" + v + ":installer");
        }
        URI url = URI.create(MAVEN + installer.artifact() + "/" + v + "/" + installer.fileName());
        return this.installer.install(url, installer, profileId(mc, v), "NeoForge", r, progress);
    }
}
