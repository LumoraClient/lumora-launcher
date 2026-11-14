package dev.lumora.launcher.core.launch;

import dev.lumora.launcher.core.util.FileUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Render backend of Minecraft 26.2 and newer (Vulkan or OpenGL, Minecraft's {@code PreferredGraphicsApi}): stored by the
 * game in options.txt as {@code preferredGraphicsBackend:"default"|"vulkan"|"opengl"} and read at startup. With a
 * choice other than "default" the launcher writes it before every start (like "Vollbild erzwingen" overrides the
 * in-game setting); "default" leaves options.txt alone.
 */
public final class GraphicsBackend {
    public static final String KEY = "preferredGraphicsBackend";
    public static final List<String> VALUES = List.of("default", "vulkan", "opengl");

    private GraphicsBackend() {
    }

    /** Whether this Minecraft version has the setting (26.2 and newer; 1.x and 26.0/26.1 do not). */
    public static boolean supported(String minecraftVersion) {
        if (minecraftVersion == null) {
            return false;
        }
        String[] p = minecraftVersion.split("[.\\-]");
        try {
            int major = Integer.parseInt(p[0]);
            int minor = p.length > 1 ? Integer.parseInt(p[1]) : 0;
            return major > 26 || major == 26 && minor >= 2;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Writes the backend into {@code gameDir/options.txt} (created if missing) when {@code backend} is "vulkan" or
     * "opengl" and the version supports it. Returns true if the file changed.
     */
    public static boolean apply(Path gameDir, String minecraftVersion, String backend) throws IOException {
        if (backend == null || "default".equals(backend) || !VALUES.contains(backend) || !supported(minecraftVersion)) {
            return false;
        }
        Path file = gameDir.resolve("options.txt");
        String line = KEY + ":\"" + backend + "\"";
        List<String> lines = Files.isRegularFile(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of();
        List<String> out = new ArrayList<>(lines.size() + 1);
        boolean found = false;
        boolean changed = false;
        for (String l : lines) {
            if (l.startsWith(KEY + ":")) {
                found = true;
                changed |= !l.equals(line);
                out.add(line);
            } else {
                out.add(l);
            }
        }
        if (!found) {
            out.add(line);
            changed = true;
        }
        if (changed) {
            Files.createDirectories(gameDir);
            FileUtil.writeAtomically(file, (String.join("\n", out) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return changed;
    }
}
