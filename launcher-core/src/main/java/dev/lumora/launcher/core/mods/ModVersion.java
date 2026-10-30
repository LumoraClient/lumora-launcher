package dev.lumora.launcher.core.mods;

import java.util.ArrayList;
import java.util.List;

/**
 * Version comparison modelled after Fabric Loader's SemanticVersion: numeric core components,
 * optional "-prerelease" (lower than the release), "+build" metadata ignored. Versions whose core is not
 * numeric are only comparable for equality.
 */
public record ModVersion(String raw, List<Integer> core, String prerelease) implements Comparable<ModVersion> {

    public static ModVersion parse(String raw) {
        String s = raw.strip();
        int plus = s.indexOf('+');
        if (plus >= 0) {
            s = s.substring(0, plus);
        }
        String pre = null;
        int dash = s.indexOf('-');
        if (dash >= 0) {
            pre = s.substring(dash + 1);
            s = s.substring(0, dash);
        }
        List<Integer> parts = new ArrayList<>();
        for (String p : s.split("\\.")) {
            if (p.isEmpty() || !p.chars().allMatch(Character::isDigit) || p.length() > 9) {
                return new ModVersion(raw.strip(), null, null);
            }
            parts.add(Integer.parseInt(p));
        }
        return new ModVersion(raw.strip(), List.copyOf(parts), pre);
    }

    public boolean isSemantic() {
        return core != null;
    }

    @Override
    public int compareTo(ModVersion o) {
        if (!isSemantic() || !o.isSemantic()) {
            return raw.compareTo(o.raw);
        }
        int n = Math.max(core.size(), o.core.size());
        for (int i = 0; i < n; i++) {
            int a = i < core.size() ? core.get(i) : 0;
            int b = i < o.core.size() ? o.core.get(i) : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        if (prerelease == null || o.prerelease == null) {
            return prerelease == null ? (o.prerelease == null ? 0 : 1) : -1;
        }
        return comparePrerelease(prerelease, o.prerelease);
    }

    private static int comparePrerelease(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.min(x.length, y.length); i++) {
            boolean xn = x[i].chars().allMatch(Character::isDigit) && !x[i].isEmpty();
            boolean yn = y[i].chars().allMatch(Character::isDigit) && !y[i].isEmpty();
            int c;
            if (xn && yn) {
                c = Long.compare(Long.parseLong(x[i]), Long.parseLong(y[i]));
            } else if (xn != yn) {
                c = xn ? -1 : 1;
            } else {
                c = x[i].compareTo(y[i]);
            }
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(x.length, y.length);
    }

    /** Equality of the comparable part (build metadata ignored). */
    public boolean sameAs(ModVersion o) {
        return isSemantic() && o.isSemantic() ? compareTo(o) == 0 : raw.equals(o.raw);
    }
}
