package dev.lumora.launcher.core.pack;

import dev.lumora.launcher.core.content.ModMetadata;
import dev.lumora.launcher.core.platform.ProjectType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Tells what a local file is by looking inside it (not by its name alone): a mod jar, a Modrinth modpack, a resource
 * pack, a data pack, a shader pack or a world. Nothing is executed or unpacked.
 */
public final class FileClassifier {
    public enum Kind { MOD, MODPACK, RESOURCE_PACK, DATAPACK, SHADER, WORLD, INSTANCE_ARCHIVE, UNKNOWN;

        /** Folder below the game folder for files that are simply copied; null for modpacks, worlds and unknown files. */
        public ProjectType projectType() {
            return switch (this) {
                case MOD -> ProjectType.MOD;
                case RESOURCE_PACK -> ProjectType.RESOURCE_PACK;
                case DATAPACK -> ProjectType.DATAPACK;
                case SHADER -> ProjectType.SHADER;
                default -> null;
            };
        }
    }

    /** @param mod metadata for mod jars (null otherwise) */
    public record Result(Kind kind, ModMetadata mod) {
    }

    private FileClassifier() {
    }

    public static Result classify(Path file) {
        if (!Files.isRegularFile(file)) {
            return new Result(Kind.UNKNOWN, null);
        }
        String ext = SafeZip.extension(file.getFileName().toString());
        try {
            if (ext.equals("jar")) {
                ModMetadata m = ModMetadata.read(file);
                return new Result(m != null ? Kind.MOD : Kind.UNKNOWN, m);
            }
            if (ext.equals("mrpack")) {
                return new Result(Kind.MODPACK, null);
            }
            if (!ext.equals("zip")) {
                return new Result(Kind.UNKNOWN, null);
            }
            try (ZipFile zip = new ZipFile(file.toFile())) {
                if (zip.getEntry(MrpackImporter.INDEX) != null) {
                    return new Result(Kind.MODPACK, null);
                }
                if (zip.getEntry(InstanceExporter.ARCHIVE_MARKER) != null) {
                    return new Result(Kind.INSTANCE_ARCHIVE, null);
                }
                if (zip.getEntry("manifest.json") != null && CurseForgePackImporter.isCurseForgePack(file)) {
                    return new Result(Kind.MODPACK, null);
                }
                boolean mcmeta = zip.getEntry("pack.mcmeta") != null;
                boolean assets = false;
                boolean data = false;
                boolean shaders = false;
                boolean level = false;
                Enumeration<? extends ZipEntry> en = zip.entries();
                int n = 0;
                while (en.hasMoreElements() && n++ < 20_000) {
                    String name = en.nextElement().getName().replace('\\', '/');
                    assets |= name.startsWith("assets/");
                    data |= name.startsWith("data/");
                    shaders |= name.startsWith("shaders/");
                    level |= name.equals("level.dat") || name.matches("[^/]+/level\\.dat");
                }
                if (level) {
                    return new Result(Kind.WORLD, null);
                }
                if (shaders && !mcmeta) {
                    return new Result(Kind.SHADER, null);
                }
                if (mcmeta && data && !assets) {
                    return new Result(Kind.DATAPACK, null);
                }
                if (mcmeta) {
                    return new Result(Kind.RESOURCE_PACK, null);
                }
            }
        } catch (IOException | RuntimeException e) {
            return new Result(Kind.UNKNOWN, null);
        }
        return new Result(Kind.UNKNOWN, null);
    }
}
