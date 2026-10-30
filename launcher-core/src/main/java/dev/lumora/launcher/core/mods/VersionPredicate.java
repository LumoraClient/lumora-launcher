package dev.lumora.launcher.core.mods;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric dependency version predicates: space separated terms are AND-ed; supported operators are
 * {@code * = > >= < <= ~ ^} and an "x" wildcard ({@code 1.21.x}).
 */
public final class VersionPredicate {
    private record Term(String op, ModVersion version, int wildcardDepth) {
    }

    private final String source;
    private final List<Term> terms;

    private VersionPredicate(String source, List<Term> terms) {
        this.source = source;
        this.terms = terms;
    }

    public static VersionPredicate parse(String predicate) {
        String p = predicate == null ? "*" : predicate.strip();
        List<Term> terms = new ArrayList<>();
        if (p.isEmpty() || p.equals("*")) {
            return new VersionPredicate(p, terms);
        }
        for (String t : p.split("\\s+")) {
            String op;
            if (t.startsWith(">=") || t.startsWith("<=")) {
                op = t.substring(0, 2);
            } else if (t.startsWith(">") || t.startsWith("<") || t.startsWith("=") || t.startsWith("~") || t.startsWith("^")) {
                op = t.substring(0, 1);
            } else {
                op = "=";
                t = "=" + t;
            }
            String v = t.substring(op.length());
            int wildcard = -1;
            if (v.endsWith(".x") || v.endsWith(".X") || v.endsWith(".*")) {
                v = v.substring(0, v.length() - 2);
                wildcard = v.split("\\.").length;
            }
            terms.add(new Term(op, ModVersion.parse(v), wildcard));
        }
        return new VersionPredicate(p, terms);
    }

    public boolean test(String version) {
        ModVersion v = ModVersion.parse(version);
        for (Term t : terms) {
            if (!test(t, v)) {
                return false;
            }
        }
        return true;
    }

    private static boolean test(Term t, ModVersion v) {
        if (t.wildcardDepth > 0) {
            return prefixEquals(v, t.version, t.wildcardDepth);
        }
        int c = v.compareTo(t.version);
        return switch (t.op) {
            case "=" -> v.sameAs(t.version);
            case ">" -> c > 0;
            case ">=" -> c >= 0;
            case "<" -> c < 0;
            case "<=" -> c <= 0;
            // ~1.2.3: >=1.2.3 and same major.minor
            case "~" -> c >= 0 && prefixEquals(v, t.version, Math.min(2, size(t.version)));
            // ^1.2.3: >=1.2.3 and same major
            case "^" -> c >= 0 && prefixEquals(v, t.version, 1);
            default -> false;
        };
    }

    private static int size(ModVersion v) {
        return v.isSemantic() ? v.core().size() : 0;
    }

    private static boolean prefixEquals(ModVersion v, ModVersion ref, int depth) {
        if (!v.isSemantic() || !ref.isSemantic()) {
            return false;
        }
        for (int i = 0; i < depth; i++) {
            int a = i < v.core().size() ? v.core().get(i) : 0;
            int b = i < ref.core().size() ? ref.core().get(i) : 0;
            if (a != b) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return source;
    }
}
