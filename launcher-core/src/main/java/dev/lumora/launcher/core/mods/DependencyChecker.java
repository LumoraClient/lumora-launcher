package dev.lumora.launcher.core.mods;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-flight check of a mod set, mirroring what Fabric Loader would reject at startup, so the launcher
 * can show an understandable error instead of a crash. Considers top-level mods, jar-in-jar mods and
 * "provides" aliases. The highest version of a mod id wins, as in Fabric Loader.
 */
public final class DependencyChecker {
    public enum Severity { ERROR, WARNING }

    public record Problem(Severity severity, String modId, String message) {
        @Override
        public String toString() {
            return severity + ": " + message;
        }
    }

    private DependencyChecker() {
    }

    /**
     * @param mods        top-level mods (with nested metadata)
     * @param environment built-in ids such as minecraft, java, fabricloader mapped to their versions
     */
    public static List<Problem> check(List<FabricModMetadata> mods, Map<String, String> environment) {
        Map<String, String> present = new HashMap<>(environment);
        Map<String, String> origin = new HashMap<>();
        List<FabricModMetadata> all = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        Map<String, String> topLevel = new HashMap<>();
        for (FabricModMetadata m : mods) {
            String previous = topLevel.put(m.id(), m.sourceFile());
            if (previous != null) {
                problems.add(new Problem(Severity.ERROR, m.id(), "Mod '" + m.id() + "' is installed twice ("
                        + previous + ", " + m.sourceFile() + ")"));
            }
            collect(m, all);
        }
        for (FabricModMetadata m : all) {
            register(present, origin, m.id(), m.version(), m.sourceFile());
            for (String p : m.provides()) {
                register(present, origin, p, m.version(), m.sourceFile());
            }
        }
        // Only top-level mods are reported for unmet dependencies; nested library mods are validated by
        // their parent's authors and Fabric resolves them together with the parent.
        for (FabricModMetadata m : mods) {
            for (Map.Entry<String, List<String>> dep : m.depends().entrySet()) {
                String have = present.get(dep.getKey());
                if (have == null) {
                    problems.add(new Problem(Severity.ERROR, m.id(), "'" + m.id() + "' requires '" + dep.getKey()
                            + "' " + String.join(" | ", dep.getValue()) + ", which is not installed"));
                } else if (!anyMatches(dep.getValue(), have)) {
                    problems.add(new Problem(Severity.ERROR, m.id(), "'" + m.id() + "' requires '" + dep.getKey()
                            + "' " + String.join(" | ", dep.getValue()) + " but " + have + " is present"));
                }
            }
        }
        for (FabricModMetadata m : all) {
            for (Map.Entry<String, List<String>> br : m.breaks().entrySet()) {
                String have = present.get(br.getKey());
                if (have != null && anyMatches(br.getValue(), have)) {
                    problems.add(new Problem(Severity.ERROR, m.id(), "'" + m.id() + "' is incompatible with '"
                            + br.getKey() + "' " + have + " (declared breaks " + String.join(" | ", br.getValue())
                            + "; from " + origin.getOrDefault(br.getKey(), "environment") + ")"));
                }
            }
        }
        return problems;
    }

    /**
     * The mods a loader jar ships as jar-in-jar (Fabric Loader 0.19.5: MixinExtras 0.5.5), as environment entries for
     * {@link #check}: mods depending on them are satisfied by the loader itself.
     */
    public static Map<String, String> bundledWith(FabricModMetadata loader) {
        Map<String, String> out = new HashMap<>();
        Map<String, String> origin = new HashMap<>();
        List<FabricModMetadata> all = new ArrayList<>();
        for (FabricModMetadata n : loader.nested()) {
            collect(n, all);
        }
        for (FabricModMetadata m : all) {
            register(out, origin, m.id(), m.version(), m.sourceFile());
            for (String p : m.provides()) {
                register(out, origin, p, m.version(), m.sourceFile());
            }
        }
        return out;
    }

    private static void collect(FabricModMetadata m, List<FabricModMetadata> out) {
        out.add(m);
        for (FabricModMetadata n : m.nested()) {
            collect(n, out);
        }
    }

    private static void register(Map<String, String> present, Map<String, String> origin, String id, String version, String source) {
        String existing = present.get(id);
        if (existing == null || ModVersion.parse(version).compareTo(ModVersion.parse(existing)) > 0) {
            present.put(id, version);
            origin.put(id, source);
        }
    }

    private static boolean anyMatches(List<String> predicates, String version) {
        for (String p : predicates) {
            if (VersionPredicate.parse(p).test(version)) {
                return true;
            }
        }
        return false;
    }
}
