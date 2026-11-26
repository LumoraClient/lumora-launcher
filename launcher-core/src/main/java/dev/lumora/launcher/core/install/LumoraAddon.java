package dev.lumora.launcher.core.install;

import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Brings the Lumora Client into an instance with a mod loader:
 * <ul>
 *   <li>Fabric / Quilt: the bundled jar is added with {@code -Dfabric.addMods} / {@code -Dloader.addMods}; nothing is
 *       copied, the player's {@code mods/} stays as it is.</li>
 *   <li>Forge / NeoForge (no such property): a managed copy {@code mods/lumora-client-mc<v>-forge.jar}. It is replaced
 *       when the launcher brings a new one and removed when the option is switched off. Only files with exactly this
 *       name pattern are touched.</li>
 * </ul>
 */
public final class LumoraAddon {
    /** Names of the managed copies in {@code mods/}. */
    static final Pattern MANAGED = Pattern.compile("lumora-client-mc[0-9.]+-forge\\.jar");

    private LumoraAddon() {
    }

    /**
     * @param jar the verified Lumora jar for this loader and version, null = Lumora is not wanted (managed copies go)
     * @return the system properties for the game command
     */
    public static List<String> apply(Path gameDir, String loaderId, Path jar) throws IOException {
        List<String> props = new ArrayList<>();
        String variant = ClientInstaller.lumoraVariant(loaderId);
        Path mods = gameDir.resolve("mods");
        boolean copy = jar != null && "forge".equals(variant);
        removeManaged(mods, copy ? jar.getFileName().toString() : null);
        if (jar == null || variant == null) {
            return props;
        }
        if (copy) {
            Files.createDirectories(mods);
            Path target = mods.resolve(jar.getFileName().toString());
            if (!Files.isRegularFile(target) || Files.size(target) != Files.size(jar) || Files.mismatch(target, jar) != -1) {
                Path tmp = target.resolveSibling(target.getFileName() + ".part");
                Files.copy(jar, tmp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                Log.info("Lumora Client for " + loaderId + " placed in " + target);
            }
            return props;
        }
        String path = jar.toAbsolutePath().toString();
        props.add("-Dfabric.addMods=" + path);
        if ("quilt".equals(loaderId)) {
            props.add("-Dloader.addMods=" + path); // Quilt's own name for it
        }
        return props;
    }

    /** Removes managed Lumora copies from mods/, except {@code keep}. */
    private static void removeManaged(Path mods, String keep) throws IOException {
        if (!Files.isDirectory(mods)) {
            return;
        }
        try (var s = Files.list(mods)) {
            for (Path p : s.toList()) {
                String name = p.getFileName().toString();
                if (MANAGED.matcher(name).matches() && !name.equals(keep)) {
                    Files.deleteIfExists(p);
                    Log.info("Removed the Lumora Client copy " + name + " from " + mods);
                }
            }
        }
    }
}
