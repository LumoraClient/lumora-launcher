package dev.lumora.launcher.core.content;

import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectSummary;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectVersion;
import dev.lumora.launcher.core.platform.PlatformModel.Side;
import dev.lumora.launcher.core.platform.ProjectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Checks whether a project version fits an instance before anything is downloaded. Every issue says what is wrong,
 * why, and what the player can do; BLOCK issues stop the installation, WARN issues are shown and need a confirmation.
 */
public final class CompatibilityChecker {
    public enum Severity { BLOCK, WARN }

    /** One finding; {@code text} is already translated (what + why), {@code hint} what to do next. */
    public record Issue(Severity severity, String code, String text, String hint) {
    }

    /** Loader names of shader packs (Modrinth tags shader versions with the shader mod they need). */
    static final Set<String> SHADER_LOADERS = Set.of("iris", "optifine", "canvas", "vanilla");
    /** Mod ids that load shader packs. */
    public static final Set<String> SHADER_MODS = Set.of("iris", "oculus", "optifabric", "canvas");

    private CompatibilityChecker() {
    }

    /** Which project types an instance of this kind can use at all. */
    public static boolean supports(InstanceKind kind, ProjectType type) {
        return switch (type) {
            case MOD -> kind.modded();
            case SHADER -> kind == InstanceKind.FABRIC || kind == InstanceKind.QUILT || kind == InstanceKind.FORGE || kind == InstanceKind.NEOFORGE;
            case RESOURCE_PACK, DATAPACK -> true;
            case MODPACK -> false;
        };
    }

    /**
     * @param project   the project (for its type and sides); may be null for dependencies looked up by version only
     * @param type      what the version is installed as
     * @param hasShaderMod whether the instance already has a shader loader mod (Iris, Oculus)
     */
    public static List<Issue> check(InstanceKind kind, String minecraftVersion, ProjectType type, ProjectSummary project,
                                    ProjectVersion version, boolean hasShaderMod) {
        List<Issue> out = new ArrayList<>();
        String title = project != null && project.title() != null ? project.title() : version.name();
        if (!supports(kind, type)) {
            if (type == ProjectType.MOD) {
                out.add(new Issue(Severity.BLOCK, "mods_unsupported", Lang.t("compat.mods_unsupported", kind.label()),
                        Lang.t("compat.mods_unsupported.hint")));
            } else if (type == ProjectType.SHADER) {
                out.add(new Issue(Severity.BLOCK, "shaders_unsupported", Lang.t("compat.shaders_unsupported", kind.label()),
                        Lang.t("compat.shaders_unsupported.hint")));
            } else {
                out.add(new Issue(Severity.BLOCK, "type_unsupported", Lang.t("compat.type_unsupported"), ""));
            }
            return out;
        }
        boolean versionFits = version.gameVersions().isEmpty() || version.gameVersions().contains(minecraftVersion);
        if (!versionFits) {
            String supported = summarize(version.gameVersions());
            if (type == ProjectType.MOD) {
                out.add(new Issue(Severity.BLOCK, "mc_version", Lang.t("compat.mc_version", title, minecraftVersion, supported),
                        Lang.t("compat.mc_version.hint")));
            } else {
                // packs often work across versions; Minecraft marks them as "made for another version"
                out.add(new Issue(Severity.WARN, "pack_version", Lang.t("compat.pack_version", title, minecraftVersion, supported),
                        Lang.t("compat.pack_version.hint")));
            }
        }
        if (type == ProjectType.MOD) {
            List<String> accepted = kind.modLoaders(minecraftVersion);
            boolean loaderFits = version.loaders().stream().anyMatch(accepted::contains);
            if (!loaderFits) {
                out.add(new Issue(Severity.BLOCK, "loader", Lang.t("compat.loader", title, kind.label(), loaderNames(version.loaders())),
                        Lang.t("compat.loader.hint")));
            } else if (!version.loaders().contains(accepted.get(0))) {
                // e.g. a Fabric mod on Quilt, a Forge mod on NeoForge 1.20.1
                out.add(new Issue(Severity.WARN, "loader_compat", Lang.t("compat.loader_compat", title, loaderNames(version.loaders()), kind.label()), ""));
            }
            if (project != null && project.client() == Side.UNSUPPORTED) {
                out.add(new Issue(Severity.WARN, "server_only", Lang.t("compat.server_only", title), Lang.t("compat.server_only.hint")));
            }
        }
        if (type == ProjectType.SHADER) {
            if (!version.loaders().isEmpty() && version.loaders().stream().noneMatch(l -> l.equals("iris") || l.equals("vanilla"))) {
                out.add(new Issue(Severity.WARN, "shader_loader", Lang.t("compat.shader_loader", title, loaderNames(version.loaders())), ""));
            } else if (!hasShaderMod) {
                out.add(new Issue(Severity.WARN, "shader_mod", Lang.t("compat.shader_mod"), Lang.t("compat.shader_mod.hint")));
            }
        }
        return out;
    }

    public static boolean blocked(List<Issue> issues) {
        return issues.stream().anyMatch(i -> i.severity() == Severity.BLOCK);
    }

    /** "1.20.1, 1.20.4 … 1.21.4" for long lists. */
    static String summarize(List<String> versions) {
        if (versions.isEmpty()) {
            return "–";
        }
        List<String> releases = versions.stream().filter(v -> v.matches("\\d+(\\.\\d+)+")).toList();
        List<String> list = releases.isEmpty() ? versions : releases;
        if (list.size() <= 4) {
            return String.join(", ", list);
        }
        return list.get(0) + ", " + list.get(1) + " … " + list.get(list.size() - 1);
    }

    static String loaderNames(List<String> loaders) {
        List<String> out = new ArrayList<>();
        for (String l : loaders) {
            InstanceKind k = InstanceKind.of(l);
            out.add(k != null ? k.label() : Character.toUpperCase(l.charAt(0)) + l.substring(1));
        }
        return out.isEmpty() ? "–" : String.join(", ", out);
    }
}
