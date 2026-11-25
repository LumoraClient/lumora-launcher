package dev.lumora.launcher.core.platform;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The shared data model of all platforms. Providers map their API answers onto these records, so the interface, the
 * installer and the dependency resolver never see platform-specific JSON.
 */
public final class PlatformModel {
    private PlatformModel() {
    }

    /** Which side a project needs: what the platform states, UNKNOWN when it says nothing. */
    public enum Side { REQUIRED, OPTIONAL, UNSUPPORTED, UNKNOWN;

        public static Side of(String s) {
            if (s == null) {
                return UNKNOWN;
            }
            return switch (s) {
                case "required" -> REQUIRED;
                case "optional" -> OPTIONAL;
                case "unsupported" -> UNSUPPORTED;
                default -> UNKNOWN;
            };
        }
    }

    /** Release channel of a version. */
    public enum Channel { RELEASE, BETA, ALPHA;

        public static Channel of(String s) {
            return "beta".equals(s) ? BETA : "alpha".equals(s) ? ALPHA : RELEASE;
        }
    }

    /** One search hit or a project from a batch lookup. */
    public record ProjectSummary(Platform platform, String id, String slug, String title, String description, String author,
                                 long downloads, long follows, String iconUrl, List<String> categories, List<String> loaders,
                                 List<String> gameVersions, Instant updated, Instant created, String license, ProjectType type,
                                 String featuredImage, Side client, Side server) {
        /** Key used for favourites and manifests: "modrinth:AANobbMI". */
        public String key() {
            return platform.id() + ":" + id;
        }
    }

    /** @param url small preview, @param rawUrl the original picture (null if the platform has none) */
    public record GalleryImage(String url, String rawUrl, String title, String description, boolean featured) {
        /** The best picture for large display. */
        public String large() {
            return rawUrl != null ? rawUrl : url;
        }
    }

    /** Full project page. {@code links} keys: source, issues, wiki, discord, website; {@code donations} are URLs. */
    public record Project(ProjectSummary summary, String body, List<GalleryImage> gallery, Map<String, String> links,
                          List<String> donations, String licenseName, String licenseUrl, String pageUrl) {
    }

    public record ProjectFile(String url, String fileName, long size, String sha1, String sha512, boolean primary) {
    }

    public enum DependencyKind { REQUIRED, OPTIONAL, INCOMPATIBLE, EMBEDDED }

    /** A dependency as the version states it: a project, and optionally an exact version. */
    public record Dependency(String projectId, String versionId, String fileName, DependencyKind kind) {
    }

    public record ProjectVersion(Platform platform, String projectId, String id, String name, String number, Channel channel,
                                 Instant published, List<String> gameVersions, List<String> loaders, List<ProjectFile> files,
                                 List<Dependency> dependencies, long downloads, String changelog) {
        /** The file to install: the one marked primary, else the first. */
        public ProjectFile primaryFile() {
            for (ProjectFile f : files) {
                if (f.primary()) {
                    return f;
                }
            }
            return files.isEmpty() ? null : files.get(0);
        }
    }

    public enum Sort { RELEVANCE, DOWNLOADS, FOLLOWS, NEWEST, UPDATED }

    /** Environment filter: which side a project must work on. */
    public enum Environment { ANY, CLIENT, SERVER, BOTH }

    /**
     * A search. {@code loaders} and {@code categories} match if any listed value matches (OR within the list); all
     * filters together must match (AND).
     */
    public record SearchQuery(String text, ProjectType type, String gameVersion, List<String> loaders, List<String> categories,
                              Environment environment, boolean openSourceOnly, Sort sort, int offset, int limit) {
        public SearchQuery {
            text = text == null ? "" : text.strip();
            loaders = loaders == null ? List.of() : List.copyOf(loaders);
            categories = categories == null ? List.of() : List.copyOf(categories);
            environment = environment == null ? Environment.ANY : environment;
            sort = sort == null ? Sort.RELEVANCE : sort;
            offset = Math.max(0, offset);
            limit = Math.max(1, Math.min(100, limit));
        }
    }

    public record SearchPage(List<ProjectSummary> hits, int offset, int total) {
    }

    /** A category as the platform names it. */
    public record Category(String id, String name, ProjectType type, String header) {
    }
}
