package dev.lumora.launcher.core.loader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the version list of a Maven {@code maven-metadata.xml}. Only the {@code <version>} elements are needed, so no
 * XML parser (and no entity handling) is involved; values that are not plain version strings are skipped.
 */
final class MavenMetadata {
    private static final Pattern VERSION = Pattern.compile("<version>\\s*([A-Za-z0-9._+-]{1,80})\\s*</version>");

    private MavenMetadata() {
    }

    static List<String> versions(String xml) {
        List<String> out = new ArrayList<>();
        Matcher m = VERSION.matcher(xml == null ? "" : xml);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** Newest first by numeric parts ("47.4.10" > "47.4.9"); pre-releases sort below the matching release. */
    static void sortNewestFirst(List<String> versions) {
        versions.sort((a, b) -> compare(b, a));
    }

    static int compare(String a, String b) {
        int[] x = numbers(a);
        int[] y = numbers(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0;
            int q = i < y.length ? y[i] : 0;
            if (p != q) {
                return Integer.compare(p, q);
            }
        }
        boolean preA = isPreRelease(a);
        boolean preB = isPreRelease(b);
        if (preA != preB) {
            return preA ? -1 : 1;
        }
        return a.compareTo(b);
    }

    /** Beta, alpha, snapshot, pre-release or release candidate. */
    static boolean isPreRelease(String v) {
        String s = v.toLowerCase(java.util.Locale.ROOT);
        return s.contains("beta") || s.contains("alpha") || s.contains("snapshot") || s.contains("-pre") || s.contains("+pre")
                || s.contains("-rc") || s.contains("+rc") || s.contains("craftmine");
    }

    private static int[] numbers(String v) {
        // only the release part counts: "21.1.0-beta" -> 21.1.0, "0.20.0-beta.9" -> 0.20.0
        String release = v.split("[-+]", 2)[0];
        return Arrays.stream(release.split("\\."))
                .mapToInt(p -> p.matches("\\d{1,9}") ? Integer.parseInt(p) : 0).toArray();
    }
}
