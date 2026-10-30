package dev.lumora.launcher.core.util;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes secrets from text before it is logged or shown. Two layers:
 * <ol>
 *   <li>Every concrete secret registered via {@link #registerSecret(String)} is replaced verbatim.</li>
 *   <li>Generic patterns (Bearer headers, JWT-like strings, "--accessToken x", JSON token fields).</li>
 * </ol>
 */
public final class Redactor {
    public static final String MASK = "<redacted>";

    private static final Set<String> SECRETS = new CopyOnWriteArraySet<>();

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+"),
            Pattern.compile("(?i)(--accessToken\\s+)\\S+"),
            Pattern.compile("(?i)(\"(?:access_token|refresh_token|id_token|accessToken|refreshToken|Token|token)\"\\s*:\\s*\")[^\"]+"),
            Pattern.compile("(?i)((?:access_token|refresh_token|code)=)[^&\\s]+"),
            // JWT: three base64url segments, first one starting with eyJ ({"...)
            Pattern.compile("()eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"));

    private Redactor() {
    }

    public static void registerSecret(String secret) {
        if (secret != null && secret.length() >= 8) {
            SECRETS.add(secret);
        }
    }

    public static void clearSecrets() {
        SECRETS.clear();
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        for (String secret : SECRETS) {
            out = out.replace(secret, MASK);
        }
        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(out);
            out = m.replaceAll(r -> Matcher.quoteReplacement(r.group(1) + MASK));
        }
        return out;
    }

    /** Redacts a launch command line: the value following --accessToken is always masked. */
    public static List<String> redactCommand(List<String> command) {
        String[] copy = command.toArray(String[]::new);
        for (int i = 0; i < copy.length; i++) {
            if ("--accessToken".equals(copy[i]) && i + 1 < copy.length) {
                copy[i + 1] = MASK;
                i++;
            } else {
                copy[i] = redact(copy[i]);
            }
        }
        return List.of(copy);
    }
}
