package dev.lumora.launcher.core.content;

import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.platform.ModPlatformProvider;
import dev.lumora.launcher.core.platform.PlatformModel.Channel;
import dev.lumora.launcher.core.platform.PlatformModel.Dependency;
import dev.lumora.launcher.core.platform.PlatformModel.DependencyKind;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectSummary;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectVersion;
import dev.lumora.launcher.core.platform.ProjectType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves what an installation needs, as a tree the player can read ("Mod A → Fabric API, Library B, optional C"):
 * <ul>
 *   <li>required dependencies are looked up for the instance's loader and Minecraft version (the exact version the
 *       mod names if it fits, else the newest compatible release) and resolved recursively;</li>
 *   <li>projects already in the instance (by manifest or by file hash) are not installed again;</li>
 *   <li>optional dependencies are listed with a note but only installed when the player ticks them;</li>
 *   <li>incompatibilities with installed projects, in both directions, are conflicts;</li>
 *   <li>shader packs get Iris (or Oculus on Forge) when the instance has no shader mod yet.</li>
 * </ul>
 */
public final class DependencyResolver {
    public static final int MAX_DEPTH = 8;
    /** Modrinth slugs of the shader mods offered with shader packs (the API takes slugs wherever it takes ids). */
    public static final String IRIS = "iris";
    public static final String OCULUS = "oculus";

    public enum Status { NEW, UPDATE, INSTALLED, OPTIONAL, MISSING, CONFLICT }

    /**
     * One project in the tree.
     *
     * @param relation  how the parent needs it (null for the root)
     * @param reason    readable reason, e.g. "benötigt von Sodium Extra"
     */
    public record Node(String projectId, String title, String iconUrl, ProjectVersion version, DependencyKind relation, Status status,
                       String reason, List<Node> children) {
    }

    /** What is installed in the instance, by project id (platform files) and by mod id (jar metadata). */
    public record Installed(Map<String, String> projectToKey, Map<String, ProjectVersion> versions, Set<String> modIds) {
        public static Installed empty() {
            return new Installed(Map.of(), Map.of(), Set.of());
        }
    }

    public record Result(Node root, List<Node> install, List<CompatibilityChecker.Issue> issues) {
    }

    private final ModPlatformProvider provider;
    private final InstanceKind kind;
    private final String minecraftVersion;
    private final Map<String, ProjectSummary> titles = new HashMap<>();

    public DependencyResolver(ModPlatformProvider provider, InstanceKind kind, String minecraftVersion) {
        this.provider = provider;
        this.kind = kind;
        this.minecraftVersion = minecraftVersion;
    }

    /**
     * @param chosenOptional optional dependency project ids the player ticked
     */
    public Result resolve(ProjectSummary project, ProjectType type, ProjectVersion version, Installed installed,
                          Set<String> chosenOptional) throws IOException {
        List<CompatibilityChecker.Issue> issues = new ArrayList<>();
        List<Node> install = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(version.projectId());
        if (project != null) {
            titles.put(project.id(), project);
        }
        boolean hasShaderMod = installed.modIds().stream().anyMatch(CompatibilityChecker.SHADER_MODS::contains)
                || installed.projectToKey().containsKey(IRIS) || installed.projectToKey().containsKey(OCULUS);
        issues.addAll(CompatibilityChecker.check(kind, minecraftVersion, type, project, version, hasShaderMod));

        List<Dependency> deps = new ArrayList<>(version.dependencies());
        if (type == ProjectType.SHADER && !hasShaderMod && CompatibilityChecker.supports(kind, type)) {
            deps.add(new Dependency(kind == InstanceKind.FORGE ? OCULUS : IRIS, null, null, DependencyKind.REQUIRED));
        }
        prefetchTitles(deps);
        List<Node> children = new ArrayList<>();
        for (Dependency d : deps) {
            Node n = dependency(d, project != null ? project.title() : version.name(), installed, chosenOptional, seen, install, issues, 1);
            if (n != null) {
                children.add(n);
            }
        }
        // installed projects that declare the new one incompatible
        for (Map.Entry<String, ProjectVersion> e : installed.versions().entrySet()) {
            for (Dependency d : e.getValue().dependencies()) {
                if (d.kind() == DependencyKind.INCOMPATIBLE && version.projectId().equals(d.projectId())) {
                    String other = title(e.getValue().projectId(), e.getValue().name());
                    issues.add(new CompatibilityChecker.Issue(CompatibilityChecker.Severity.BLOCK, "conflict",
                            Lang.t("deps.conflict_reverse", other, title(version.projectId(), version.name())), Lang.t("deps.conflict.hint", other)));
                }
            }
        }
        Status rootStatus = installed.projectToKey().containsKey(version.projectId()) ? Status.UPDATE : Status.NEW;
        Node root = new Node(version.projectId(), title(version.projectId(), version.name()), project != null ? project.iconUrl() : null,
                version, null, rootStatus, null, children);
        install.add(0, root);
        return new Result(root, install, issues);
    }

    private Node dependency(Dependency d, String parentTitle, Installed installed, Set<String> chosenOptional, Set<String> seen,
                            List<Node> install, List<CompatibilityChecker.Issue> issues, int depth) throws IOException {
        if (d.kind() == DependencyKind.EMBEDDED) {
            return null;
        }
        String projectId = d.projectId();
        ProjectVersion exact = null;
        if (projectId == null && d.versionId() != null) {
            exact = provider.version(d.versionId());
            projectId = exact.projectId();
        }
        if (projectId == null) {
            return null; // a dependency given only by file name cannot be looked up
        }
        String title = title(projectId, null);
        String reason = Lang.t("deps.required_by", parentTitle);
        if (d.kind() == DependencyKind.INCOMPATIBLE) {
            if (installed.projectToKey().containsKey(projectId)) {
                issues.add(new CompatibilityChecker.Issue(CompatibilityChecker.Severity.BLOCK, "conflict",
                        Lang.t("deps.conflict", parentTitle, title), Lang.t("deps.conflict.hint", title)));
                return new Node(projectId, title, icon(projectId), null, d.kind(), Status.CONFLICT, Lang.t("deps.incompatible_with", parentTitle), List.of());
            }
            return null;
        }
        if (installed.projectToKey().containsKey(projectId)) {
            return new Node(projectId, title, icon(projectId), installed.versions().get(projectId), d.kind(), Status.INSTALLED, reason, List.of());
        }
        if (!seen.add(projectId)) {
            return new Node(projectId, title, icon(projectId), null, d.kind(), Status.NEW, reason, List.of());
        }
        boolean optional = d.kind() == DependencyKind.OPTIONAL;
        if (optional && !chosenOptional.contains(projectId)) {
            return new Node(projectId, title, icon(projectId), null, d.kind(), Status.OPTIONAL, Lang.t("deps.optional_for", parentTitle), List.of());
        }
        if (depth > MAX_DEPTH) {
            issues.add(new CompatibilityChecker.Issue(CompatibilityChecker.Severity.BLOCK, "depth", Lang.t("deps.too_deep", title), ""));
            return new Node(projectId, title, icon(projectId), null, d.kind(), Status.MISSING, reason, List.of());
        }
        ProjectVersion v = null;
        if (d.versionId() != null) {
            ProjectVersion named = exact != null ? exact : provider.version(d.versionId());
            if (fits(named)) {
                v = named;
            }
        }
        if (v == null) {
            v = best(provider.versions(projectId, kind.modLoaders(minecraftVersion), List.of(minecraftVersion)).stream().filter(this::fits).toList());
        }
        if (v == null) {
            if (optional) {
                issues.add(new CompatibilityChecker.Issue(CompatibilityChecker.Severity.WARN, "optional_missing",
                        Lang.t("deps.optional_missing", title, kind.label(), minecraftVersion), ""));
            } else {
                issues.add(new CompatibilityChecker.Issue(CompatibilityChecker.Severity.BLOCK, "missing",
                        Lang.t("deps.missing", parentTitle, title, kind.label(), minecraftVersion), Lang.t("deps.missing.hint")));
            }
            return new Node(projectId, title, icon(projectId), null, d.kind(), Status.MISSING, reason, List.of());
        }
        prefetchTitles(v.dependencies());
        List<Node> children = new ArrayList<>();
        Node self = new Node(projectId, title, icon(projectId), v, d.kind(), Status.NEW, reason, children);
        install.add(self);
        for (Dependency sub : v.dependencies()) {
            if (sub.kind() == DependencyKind.OPTIONAL) {
                continue; // optional dependencies of dependencies are not offered (keeps the dialog short)
            }
            Node n = dependency(sub, title, installed, chosenOptional, seen, install, issues, depth + 1);
            if (n != null) {
                children.add(n);
            }
        }
        return self;
    }

    private boolean fits(ProjectVersion v) {
        return (v.gameVersions().isEmpty() || v.gameVersions().contains(minecraftVersion))
                && v.loaders().stream().anyMatch(kind.modLoaders(minecraftVersion)::contains);
    }

    /** Newest release, else the newest beta, else the newest alpha (lists are newest first). */
    public static ProjectVersion best(List<ProjectVersion> versions) {
        for (Channel c : Channel.values()) {
            for (ProjectVersion v : versions) {
                if (v.channel() == c) {
                    return v;
                }
            }
        }
        return null;
    }

    private void prefetchTitles(List<Dependency> deps) {
        List<String> ids = new ArrayList<>();
        for (Dependency d : deps) {
            if (d.projectId() != null && !titles.containsKey(d.projectId()) && !ids.contains(d.projectId())) {
                ids.add(d.projectId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        try {
            for (ProjectSummary s : provider.projects(ids)) {
                titles.put(s.id(), s);
            }
        } catch (IOException | RuntimeException e) {
            // names are cosmetic: the ids are shown instead
        }
    }

    private String title(String projectId, String fallback) {
        ProjectSummary s = titles.get(projectId);
        if (s != null && s.title() != null) {
            return s.title();
        }
        return fallback != null ? fallback : projectId;
    }

    private String icon(String projectId) {
        ProjectSummary s = titles.get(projectId);
        return s == null ? null : s.iconUrl();
    }

    /** Project titles looked up while resolving (for the dialog). */
    public Map<String, ProjectSummary> titles() {
        return new LinkedHashMap<>(titles);
    }
}
