package dev.lumora.api;

import java.util.Objects;

/**
 * Semantic version {@code major.minor.patch[-prerelease][+build]}. Missing parts count as 0 ("1.2" = "1.2.0");
 * build metadata is ignored when comparing, a pre-release sorts before its release.
 */
public final class Version implements Comparable<Version> {
    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final String text;

    private Version(int major, int minor, int patch, String preRelease, String text) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = preRelease;
        this.text = text;
    }

    /** Parses a version; throws {@link IllegalArgumentException} for anything that is not {@code n[.n[.n]][-x][+y]}. */
    public static Version parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("empty version");
        }
        String v = value.trim();
        int plus = v.indexOf('+');
        String core = plus >= 0 ? v.substring(0, plus) : v;
        int dash = core.indexOf('-');
        String pre = dash >= 0 ? core.substring(dash + 1) : "";
        String numbers = dash >= 0 ? core.substring(0, dash) : core;
        String[] parts = numbers.split("\\.", -1);
        if (parts.length > 3) {
            throw new IllegalArgumentException("invalid version: " + value);
        }
        int[] n = new int[3];
        for (int i = 0; i < parts.length; i++) {
            n[i] = number(parts[i], value);
        }
        if (dash >= 0 && pre.isEmpty()) {
            throw new IllegalArgumentException("invalid version: " + value);
        }
        return new Version(n[0], n[1], n[2], pre, v);
    }

    /** Like {@link #parse} but returns {@code null} instead of throwing. */
    public static Version tryParse(String value) {
        try {
            return parse(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int number(String part, String full) {
        if (part.isEmpty() || part.length() > 9) {
            throw new IllegalArgumentException("invalid version: " + full);
        }
        for (int i = 0; i < part.length(); i++) {
            if (!Character.isDigit(part.charAt(i))) {
                throw new IllegalArgumentException("invalid version: " + full);
            }
        }
        return Integer.parseInt(part);
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    public String preRelease() {
        return preRelease;
    }

    @Override
    public int compareTo(Version o) {
        int c = Integer.compare(major, o.major);
        if (c == 0) {
            c = Integer.compare(minor, o.minor);
        }
        if (c == 0) {
            c = Integer.compare(patch, o.patch);
        }
        if (c == 0 && !preRelease.equals(o.preRelease)) {
            if (preRelease.isEmpty()) {
                return 1;
            }
            if (o.preRelease.isEmpty()) {
                return -1;
            }
            c = preRelease.compareTo(o.preRelease);
        }
        return c;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Version v && compareTo(v) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease);
    }

    @Override
    public String toString() {
        return text;
    }
}
