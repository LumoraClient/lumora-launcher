package dev.lumora.launcher.core.content;

import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.InstanceKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Looks at the switched-on mods of an instance and finds what would make the game fail to start or behave oddly:
 * the same mod twice, mods for another loader, missing required mods and declared incompatibilities. Read-only.
 */
public final class ConflictDetector {
    public enum Kind { DUPLICATE, WRONG_LOADER, MISSING_DEPENDENCY, INCOMPATIBLE }

    /** One problem: the files involved and a translated explanation with a hint what to do. */
    public record Problem(Kind kind, List<String> files, String text, String hint) {
    }

    /** Old and new ids of the same mods (Fabric API was "fabric" before it became "fabric-api"). */
    private static final Map<String, String> ALIASES = Map.of("fabric", "fabric-api", "quilted_fabric_api", "fabric-api");

    private ConflictDetector() {
    }

    public static List<Problem> detect(InstanceKind kind, List<InstalledContent.Item> mods) {
        List<Problem> out = new ArrayList<>();
        if (!kind.modded()) {
            return out;
        }
        Set<String> metadataLoaders = switch (kind) {
            case QUILT -> Set.of("quilt", "fabric");
            case NEOFORGE -> Set.of("neoforge", "forge");
            default -> Set.of(kind.loaderId());
        };
        Map<String, List<String>> byId = new LinkedHashMap<>();
        Set<String> present = new HashSet<>();
        for (InstalledContent.Item i : mods) {
            if (!i.enabled() || i.mod() == null) {
                continue;
            }
            for (String id : i.mod().ids()) {
                present.add(normal(id));
            }
            String main = i.mod().mainId();
            if (main != null) {
                byId.computeIfAbsent(normal(main), k -> new ArrayList<>()).add(i.fileName());
            }
        }
        for (Map.Entry<String, List<String>> e : byId.entrySet()) {
            if (e.getValue().size() > 1) {
                out.add(new Problem(Kind.DUPLICATE, e.getValue(), Lang.t("conflict.duplicate", e.getKey(), String.join(", ", e.getValue())),
                        Lang.t("conflict.duplicate.hint")));
            }
        }
        Map<String, String> names = new HashMap<>();
        for (InstalledContent.Item i : mods) {
            if (i.enabled() && i.mod() != null) {
                for (String id : i.mod().ids()) {
                    names.putIfAbsent(normal(id), i.displayName());
                }
            }
        }
        for (InstalledContent.Item i : mods) {
            if (!i.enabled() || i.mod() == null) {
                continue;
            }
            if (i.mod().loaders().stream().noneMatch(metadataLoaders::contains)) {
                out.add(new Problem(Kind.WRONG_LOADER, List.of(i.fileName()), Lang.t("conflict.wrong_loader", i.displayName(),
                        CompatibilityChecker.loaderNames(new ArrayList<>(i.mod().loaders())), kind.label()), Lang.t("conflict.wrong_loader.hint")));
                continue;
            }
            for (String dep : i.mod().depends().keySet()) {
                String id = normal(dep);
                if (ModMetadata.PLATFORM_IDS.contains(id) || present.contains(id) || id.startsWith("fabric-") && present.contains("fabric-api")) {
                    continue;
                }
                out.add(new Problem(Kind.MISSING_DEPENDENCY, List.of(i.fileName()), Lang.t("conflict.missing", i.displayName(), dep),
                        Lang.t("conflict.missing.hint", dep)));
            }
            for (String b : i.mod().breaks()) {
                String id = normal(b);
                if (present.contains(id) && !i.mod().ids().contains(b)) {
                    out.add(new Problem(Kind.INCOMPATIBLE, List.of(i.fileName()), Lang.t("conflict.incompatible", i.displayName(),
                            names.getOrDefault(id, b)), Lang.t("conflict.incompatible.hint")));
                }
            }
        }
        return out;
    }

    private static String normal(String id) {
        return ALIASES.getOrDefault(id, id);
    }
}
