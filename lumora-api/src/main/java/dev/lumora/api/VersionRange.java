package dev.lumora.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Version requirement as used in addon dependencies. Supported forms, combinable with spaces (all must match):
 * {@code *}, {@code 1.2.3} (exact), {@code >=1.2}, {@code >1.2}, {@code <=2}, {@code <2}, {@code 1.2.x} (any
 * patch of 1.2), {@code ^1.2.0} (same major, at least 1.2.0), {@code ~1.2.0} (same minor, at least 1.2.0).
 */
public final class VersionRange {
    private enum Op { EQ, GE, GT, LE, LT }

    private record Term(Op op, Version version) {
    }

    private final String text;
    private final List<Term> terms;

    private VersionRange(String text, List<Term> terms) {
        this.text = text;
        this.terms = terms;
    }

    public static final VersionRange ANY = new VersionRange("*", List.of());

    /** Parses a requirement; throws {@link IllegalArgumentException} for invalid input. */
    public static VersionRange parse(String value) {
        if (value == null || value.isBlank() || value.trim().equals("*")) {
            return ANY;
        }
        List<Term> terms = new ArrayList<>();
        for (String token : value.trim().split("\\s+")) {
            add(terms, token, value);
        }
        return new VersionRange(value.trim(), List.copyOf(terms));
    }

    private static void add(List<Term> terms, String token, String full) {
        if (token.equals("*")) {
            return;
        }
        if (token.startsWith(">=")) {
            terms.add(new Term(Op.GE, Version.parse(token.substring(2))));
        } else if (token.startsWith("<=")) {
            terms.add(new Term(Op.LE, Version.parse(token.substring(2))));
        } else if (token.startsWith(">")) {
            terms.add(new Term(Op.GT, Version.parse(token.substring(1))));
        } else if (token.startsWith("<")) {
            terms.add(new Term(Op.LT, Version.parse(token.substring(1))));
        } else if (token.startsWith("^")) {
            Version v = Version.parse(token.substring(1));
            terms.add(new Term(Op.GE, v));
            terms.add(new Term(Op.LT, Version.parse((v.major() + 1) + ".0.0-0")));
        } else if (token.startsWith("~")) {
            Version v = Version.parse(token.substring(1));
            terms.add(new Term(Op.GE, v));
            terms.add(new Term(Op.LT, Version.parse(v.major() + "." + (v.minor() + 1) + ".0-0")));
        } else if (token.endsWith(".x") || token.endsWith(".*")) {
            String base = token.substring(0, token.length() - 2);
            String[] parts = base.split("\\.");
            Version low = Version.parse(base);
            Version high = parts.length == 1
                    ? Version.parse((low.major() + 1) + ".0.0-0")
                    : Version.parse(low.major() + "." + (low.minor() + 1) + ".0-0");
            terms.add(new Term(Op.GE, low));
            terms.add(new Term(Op.LT, high));
        } else if (token.startsWith("=")) {
            terms.add(new Term(Op.EQ, Version.parse(token.substring(1))));
        } else if (!token.isEmpty() && Character.isDigit(token.charAt(0))) {
            terms.add(new Term(Op.EQ, Version.parse(token)));
        } else {
            throw new IllegalArgumentException("invalid version requirement: " + full);
        }
    }

    public boolean matches(Version v) {
        for (Term t : terms) {
            int c = v.compareTo(t.version());
            boolean ok = switch (t.op()) {
                case EQ -> c == 0;
                case GE -> c >= 0;
                case GT -> c > 0;
                case LE -> c <= 0;
                case LT -> c < 0;
            };
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return text;
    }
}
