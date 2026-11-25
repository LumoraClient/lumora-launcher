package dev.lumora.launcher.core.platform;

import dev.lumora.launcher.core.platform.PlatformModel.Category;
import dev.lumora.launcher.core.platform.PlatformModel.Project;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectSummary;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectVersion;
import dev.lumora.launcher.core.platform.PlatformModel.SearchPage;
import dev.lumora.launcher.core.platform.PlatformModel.SearchQuery;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One content platform (Modrinth, later CurseForge, ...). Implementations only talk to the platform's official API,
 * respect its limits, and map the answers onto {@link PlatformModel}. Everything above (search page, installer,
 * dependency resolver, update check) works with this interface only, so a new platform is one more implementation
 * registered in {@link ModRepository}.
 */
public interface ModPlatformProvider {
    Platform platform();

    /** False when the provider cannot be used right now (e.g. a platform that needs a key the player has not set). */
    default boolean available() {
        return true;
    }

    Set<ProjectType> supportedTypes();

    SearchPage search(SearchQuery query) throws IOException;

    /** Full project page by id or slug. */
    Project project(String idOrSlug) throws IOException;

    /** Several projects at once (missing ones are left out). */
    List<ProjectSummary> projects(List<String> ids) throws IOException;

    /**
     * Versions of a project, newest first, optionally only those for one of {@code loaders} and one of
     * {@code gameVersions} (empty lists = no filter).
     */
    List<ProjectVersion> versions(String projectId, List<String> loaders, List<String> gameVersions) throws IOException;

    ProjectVersion version(String versionId) throws IOException;

    /** Versions that contain the files with these SHA-1 hashes (identifies installed files); unknown hashes are absent. */
    Map<String, ProjectVersion> versionsByHash(List<String> sha1) throws IOException;

    /** Newest version for one of {@code loaders} and {@code gameVersions} per installed file (by SHA-1); for updates. */
    Map<String, ProjectVersion> latestByHash(List<String> sha1, List<String> loaders, List<String> gameVersions) throws IOException;

    /** The platform's categories. */
    List<Category> categories() throws IOException;

    /**
     * Which project versions local files are (to recognise files the launcher did not install). Default: by SHA-1 through
     * {@link #versionsByHash}; platforms with another scheme (CurseForge fingerprints) override it.
     */
    default Map<java.nio.file.Path, ProjectVersion> identify(List<java.nio.file.Path> files) throws IOException {
        Map<String, java.nio.file.Path> byHash = new java.util.LinkedHashMap<>();
        for (java.nio.file.Path f : files) {
            byHash.put(dev.lumora.launcher.core.util.Hashing.hash(f, dev.lumora.launcher.core.util.Hashing.Algorithm.SHA1), f);
        }
        Map<java.nio.file.Path, ProjectVersion> out = new java.util.LinkedHashMap<>();
        if (byHash.isEmpty()) {
            return out;
        }
        versionsByHash(new java.util.ArrayList<>(byHash.keySet())).forEach((hash, v) -> {
            java.nio.file.Path p = byHash.get(hash);
            if (p != null) {
                out.put(p, v);
            }
        });
        return out;
    }

    /** Project page in the browser. */
    String projectPage(ProjectType type, String idOrSlug);
}
