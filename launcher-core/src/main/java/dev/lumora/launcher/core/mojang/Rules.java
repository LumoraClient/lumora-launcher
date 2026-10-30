package dev.lumora.launcher.core.mojang;

import dev.lumora.launcher.core.util.OsInfo;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Evaluates Mojang rule lists: start with "disallow", apply each matching rule in order; an empty or
 * absent rule list means "allow".
 */
public final class Rules {
    private Rules() {
    }

    public static boolean allowed(List<VersionJson.Rule> rules, OsInfo os, Map<String, Boolean> features) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        boolean allowed = false;
        for (VersionJson.Rule rule : rules) {
            if (matches(rule, os, features)) {
                allowed = "allow".equals(rule.action);
            }
        }
        return allowed;
    }

    static boolean matches(VersionJson.Rule rule, OsInfo os, Map<String, Boolean> features) {
        if (rule.os != null) {
            if (rule.os.name != null && !osNameMatches(rule.os.name, os)) {
                return false;
            }
            if (rule.os.arch != null && !rule.os.arch.equals(os.mojangArch())) {
                return false;
            }
            if (rule.os.version != null) {
                try {
                    if (!Pattern.compile(rule.os.version).matcher(os.version()).find()) {
                        return false;
                    }
                } catch (PatternSyntaxException e) {
                    return false;
                }
            }
        }
        if (rule.features != null) {
            for (Map.Entry<String, Boolean> f : rule.features.entrySet()) {
                boolean actual = features.getOrDefault(f.getKey(), false);
                if (actual != f.getValue()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean osNameMatches(String ruleName, OsInfo os) {
        String base = os.name().mojangName;
        if (ruleName.equals(base)) {
            return true;
        }
        // Mojang uses e.g. "windows-arm64" / "linux-arm64" for architecture specific natives.
        return os.arch() == OsInfo.Arch.ARM64 && ruleName.equals(base + "-arm64");
    }
}
