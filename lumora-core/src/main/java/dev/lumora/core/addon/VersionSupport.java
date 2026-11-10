package dev.lumora.core.addon;

import dev.lumora.api.LumoraAPI;
import dev.lumora.api.Version;
import dev.lumora.api.addon.AddonDescriptor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Why an addon or feature is not available for the running Minecraft version: the version is too old, too new, or
 * not among the listed ones; or the addon needs a newer Lumora API. The hub shows the reason in a hover window.
 */
public final class VersionSupport {
    public enum Kind {
        /** The running Minecraft version is older than every supported one. */
        TOO_OLD,
        /** The running Minecraft version is newer than every supported one. */
        TOO_NEW,
        /** Supported versions lie below and above, but not this one. */
        NOT_LISTED,
        /** The addon needs a newer Lumora API (Lumora itself is too old). */
        LUMORA_TOO_OLD
    }

    /**
     * @param kind      what does not fit
     * @param current   running Minecraft version (or Lumora API version for {@link Kind#LUMORA_TOO_OLD})
     * @param supported supported Minecraft versions, oldest first (or the needed API version)
     */
    public record Problem(Kind kind, String current, List<String> supported) {
        public Problem {
            supported = List.copyOf(supported);
        }

        /** Oldest supported version for display ("1.9.x" is shown as "1.9"). */
        public String oldest() {
            return supported.isEmpty() ? "?" : shown(supported.get(0));
        }

        public String newest() {
            return supported.isEmpty() ? "?" : supported.get(supported.size() - 1);
        }

        private static String shown(String pattern) {
            return pattern.endsWith(".x") || pattern.endsWith(".*") ? pattern.substring(0, pattern.length() - 2) : pattern;
        }

        /** Hover text (German or English): what is not available, why, and what would work. */
        public List<String> describe(boolean german, String name) {
            List<String> out = new ArrayList<>();
            if (kind == Kind.LUMORA_TOO_OLD) {
                out.add(german ? name + " braucht eine neuere Lumora-Version." : name + " needs a newer Lumora version.");
                out.add(german ? "Lumora-API " + oldest() + " nötig, vorhanden ist " + current + ". Aktualisiere Lumora."
                        : "Lumora API " + oldest() + " needed, this is " + current + ". Update Lumora.");
                return out;
            }
            if (kind == Kind.TOO_OLD) {
                // an old version will not get support later: no "(yet)"
                out.add(german ? name + " ist mit Minecraft " + current + " nicht kompatibel."
                        : name + " is not compatible with Minecraft " + current + ".");
            } else {
                out.add(german ? name + " ist (noch) nicht mit Minecraft " + current + " kompatibel."
                        : name + " is not (yet) compatible with Minecraft " + current + ".");
            }
            out.add(switch (kind) {
                case TOO_OLD -> german ? "Diese Version ist zu alt: nötig ist mindestens " + oldest() + "."
                        : "This version is too old: " + oldest() + " or newer is needed.";
                case TOO_NEW -> german ? "Diese Version ist zu neu: unterstützt wird nur bis " + newest() + "."
                        : "This version is too new: only up to " + newest() + " is supported.";
                default -> german ? "Unterstützt werden nur: " + String.join(", ", supported) + "."
                        : "Supported are only: " + String.join(", ", supported) + ".";
            });
            return out;
        }
    }

    private VersionSupport() {
    }

    /** Problem of this addon with the running versions, or {@code null} if it fits. */
    public static Problem check(AddonDescriptor d, String minecraft) {
        Version api = Version.parse(LumoraAPI.API_VERSION);
        Version need = d.apiVersion();
        if (need.major() == api.major() && need.compareTo(api) > 0) {
            return new Problem(Kind.LUMORA_TOO_OLD, api.toString(), List.of(need.toString()));
        }
        if (minecraft == null || d.supportsMinecraft(minecraft)) {
            return null;
        }
        return minecraft(d.minecraftVersions(), minecraft);
    }

    /** Problem of a feature that runs on {@code supported} only, or {@code null} if {@code minecraft} is among them. */
    public static Problem minecraft(List<String> supported, String minecraft) {
        List<String> sorted = new ArrayList<>(supported);
        sorted.removeIf(p -> p.equals("*"));
        if (sorted.size() < supported.size()) {
            return null;
        }
        sorted.sort(Comparator.comparing((String p) -> p, VersionSupport::compare));
        boolean below = false;
        boolean above = false;
        for (String p : sorted) {
            int c = compare(p, minecraft);
            if (c == 0) {
                return null;
            }
            if (c < 0) {
                below = true;
            } else {
                above = true;
            }
        }
        Kind kind = above && !below ? Kind.TOO_OLD : below && !above ? Kind.TOO_NEW : Kind.NOT_LISTED;
        return new Problem(kind, minecraft, sorted);
    }

    /**
     * Minecraft versions for a built-in feature that needs at least {@code min} (e.g. elytras: 1.9): every version
     * ({@code "*"}) where the running one is new enough, else {@code min.x}, so the hub explains "too old".
     */
    public static java.util.List<String> atLeast(String min, String running) {
        return running == null || compare(running, min) >= 0 ? List.of("*") : List.of(min + ".x");
    }

    /**
     * Compares Minecraft versions part by part ({@code 1.8.9 < 1.12.2 < 1.21 < 1.21.11 < 26.1}). A pattern ending in
     * {@code .x} or {@code .*} equals every version it covers.
     */
    public static int compare(String a, String b) {
        String[] pa = a.toLowerCase(Locale.ROOT).split("\\.");
        String[] pb = b.toLowerCase(Locale.ROOT).split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            String x = i < pa.length ? pa[i] : "0";
            String y = i < pb.length ? pb[i] : "0";
            if (x.equals("x") || x.equals("*") || y.equals("x") || y.equals("*")) {
                return 0;
            }
            int c = Integer.compare(number(x), number(y));
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static int number(String s) {
        int end = 0;
        while (end < s.length() && Character.isDigit(s.charAt(end))) {
            end++;
        }
        try {
            return end == 0 ? 0 : Integer.parseInt(s.substring(0, end));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** Whether the addon comes from someone other than Lumora (shown as a note in the hover window). */
    public static boolean thirdParty(AddonDescriptor d) {
        if (d.builtIn()) {
            return false;
        }
        for (String a : d.authors()) {
            if (a.trim().equalsIgnoreCase("Lumora")) {
                return false;
            }
        }
        return true;
    }

    /** Note for addons by other authors, like "made by a user and not by Lumora". */
    public static String thirdPartyNote(boolean german, AddonDescriptor d) {
        String who = d.authors().isEmpty() ? null : String.join(", ", d.authors());
        if (german) {
            return (who == null ? "Dieses Addon wurde von einem Nutzer" : "Dieses Addon wurde von " + who)
                    + " erstellt, nicht von Lumora. Darauf haben wir keinen Einfluss.";
        }
        return (who == null ? "This addon was made by a user" : "This addon was made by " + who)
                + " and not by Lumora. We have no control over this.";
    }
}
