package dev.lumora.core.addon;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Addon jars that belong to the client itself (today LumoraVoice, {@code lumora-voice.jar}): too large to copy into
 * every client jar, so the launcher ships them once and names them in {@value #PROPERTY} (paths separated by the
 * platform's path separator). They load like jar addons but count as built-in modules: they show among the modules,
 * cannot be removed, and a copy of the same addon in an {@code addons/} folder is left out.
 */
public final class BuiltInAddons {
    public static final String PROPERTY = "lumora.builtinAddons";

    private BuiltInAddons() {
    }

    /** The built-in addon jars that exist, in the given order (empty without the property, e.g. in development). */
    public static List<Path> jars() {
        return parse(System.getProperty(PROPERTY));
    }

    static List<Path> parse(String value) {
        List<Path> out = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return out;
        }
        for (String part : value.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (part.isBlank()) {
                continue;
            }
            try {
                Path p = Path.of(part.strip());
                if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar") && !out.contains(p)) {
                    out.add(p);
                }
            } catch (InvalidPathException ignored) {
                // not a path: left out
            }
        }
        return out;
    }
}
