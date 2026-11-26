package dev.lumora.launcher.core.mods;

import dev.lumora.launcher.core.util.Hashing;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/**
 * Addon jars that are part of the Lumora Client but too large for every client jar (LumoraVoice with its audio
 * libraries, {@value #VOICE}). The launcher ships them once in {@code bundled/} (checked against bundled-mods.json) and
 * hands them to the game with {@code -Dlumora.builtinAddons}; the client loads them as built-in modules.
 * <p>
 * The game gets a copy in {@code <lumora>/builtin/}, named after its hash: a running game keeps its file locked on
 * Windows, so a launcher update never has to replace a file in use, and older copies go once no game holds them.
 */
public final class BuiltInAddons {
    public static final String VOICE = "lumora-voice.jar";
    public static final String PROPERTY = "lumora.builtinAddons";

    private BuiltInAddons() {
    }

    /** The JVM argument for the game, or null if the launcher ships no built-in addon (development) or it is broken. */
    public static String sysProp(Path bundledDir, Path lumoraDir, Consumer<String> warn) {
        try {
            BundledMods bundled = new BundledMods(bundledDir);
            if (!bundled.contains(VOICE)) {
                return null;
            }
            Path copy = stage(bundled.verified(VOICE), lumoraDir.resolve("builtin"));
            return "-D" + PROPERTY + "=" + copy.toAbsolutePath();
        } catch (IOException | RuntimeException e) {
            warn.accept("LumoraVoice could not be prepared: " + e.getMessage());
            return null;
        }
    }

    /** Copies {@code source} into {@code dir} as {@code lumora-voice-<hash>.jar} (if not there yet), removes old copies. */
    static Path stage(Path source, Path dir) throws IOException {
        Files.createDirectories(dir);
        String hash = Hashing.hash(source, Hashing.Algorithm.SHA256).substring(0, 16);
        Path target = dir.resolve("lumora-voice-" + hash + ".jar");
        if (!Files.isRegularFile(target) || Files.size(target) != Files.size(source)) {
            Path tmp = dir.resolve(target.getFileName() + ".tmp");
            Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        try (DirectoryStream<Path> s = Files.newDirectoryStream(dir, "lumora-voice-*.jar")) {
            for (Path p : s) {
                if (!p.equals(target)) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException inUse) {
                        // still used by a running game: removed next time
                    }
                }
            }
        }
        return target;
    }
}
