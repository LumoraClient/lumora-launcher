package dev.lumora.launcher.core.library;

import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Read-only overview of an instance folder for the launcher's library page: worlds (saves/), the multiplayer
 * server list (servers.dat), screenshots and resource packs. Nothing is modified.
 */
public final class InstanceLibrary {
    public enum GameMode { SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR, UNKNOWN }

    public record World(String folder, String name, Instant lastPlayed, GameMode mode, boolean hardcore, Path dir, Path icon) {
    }

    /** @param icon the server's favicon as stored by Minecraft (Base64 PNG, may be null) */
    public record Server(String name, String address, String icon) {
        public Server(String name, String address) {
            this(name, address, null);
        }

        /** The favicon PNG bytes, or null if there is none or it is invalid. */
        public byte[] iconPng() {
            if (icon == null || icon.isBlank() || icon.length() > 200_000) {
                return null;
            }
            try {
                return java.util.Base64.getDecoder().decode(icon.replace("data:image/png;base64,", "").strip());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public record Screenshot(Path file, Instant taken, long size) {
    }

    public record ResourcePack(String name, Path path, boolean folder) {
    }

    private InstanceLibrary() {
    }

    public static List<World> worlds(Path instance) {
        Path saves = instance.resolve("saves");
        List<World> out = new ArrayList<>();
        for (Path dir : list(saves)) {
            Path levelDat = dir.resolve("level.dat");
            if (!Files.isDirectory(dir) || !Files.isRegularFile(levelDat)) {
                continue;
            }
            String folder = dir.getFileName().toString();
            String name = folder;
            Instant last = modified(levelDat);
            GameMode mode = GameMode.UNKNOWN;
            boolean hardcore = false;
            try {
                Map<String, Object> root = Nbt.read(levelDat);
                String n = Nbt.get(root, String.class, "Data", "LevelName");
                if (n != null && !n.isBlank()) {
                    name = n;
                }
                Long lp = Nbt.get(root, Long.class, "Data", "LastPlayed");
                if (lp != null && lp > 0) {
                    last = Instant.ofEpochMilli(lp);
                }
                Integer gt = Nbt.get(root, Integer.class, "Data", "GameType");
                if (gt != null && gt >= 0 && gt < 4) {
                    mode = GameMode.values()[gt];
                }
                Byte hc = Nbt.get(root, Byte.class, "Data", "hardcore");
                hardcore = hc != null && hc != 0;
            } catch (IOException | RuntimeException e) {
                Log.warn("Could not read " + levelDat + ": " + e.getMessage());
            }
            Path icon = dir.resolve("icon.png");
            out.add(new World(folder, name, last, mode, hardcore, dir, Files.isRegularFile(icon) ? icon : null));
        }
        out.sort(Comparator.comparing(World::lastPlayed, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    public static List<Server> servers(Path instance) {
        Path file = instance.resolve("servers.dat");
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        List<Server> out = new ArrayList<>();
        try {
            List<?> list = Nbt.get(Nbt.read(file), List.class, "servers");
            if (list != null) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m && m.get("ip") instanceof String ip && !ip.isBlank()) {
                        Object name = m.get("name");
                        Object icon = m.get("icon");
                        out.add(new Server(name instanceof String s && !s.isBlank() ? s : ip, ip.strip(), icon instanceof String i ? i : null));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Could not read " + file + ": " + e.getMessage());
        }
        return out;
    }

    public static List<Screenshot> screenshots(Path instance) {
        List<Screenshot> out = new ArrayList<>();
        for (Path p : list(instance.resolve("screenshots"))) {
            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
            if (Files.isRegularFile(p) && (n.endsWith(".png") || n.endsWith(".jpg"))) {
                try {
                    out.add(new Screenshot(p, modified(p), Files.size(p)));
                } catch (IOException ignored) {
                    // vanished meanwhile
                }
            }
        }
        out.sort(Comparator.comparing(Screenshot::taken).reversed());
        return out;
    }

    public static List<ResourcePack> resourcePacks(Path instance) {
        List<ResourcePack> out = new ArrayList<>();
        for (Path p : list(instance.resolve("resourcepacks"))) {
            String n = p.getFileName().toString();
            boolean folder = Files.isDirectory(p) && Files.isRegularFile(p.resolve("pack.mcmeta"));
            if (folder || (Files.isRegularFile(p) && n.toLowerCase(Locale.ROOT).endsWith(".zip"))) {
                out.add(new ResourcePack(folder ? n : n.substring(0, n.length() - 4), p, folder));
            }
        }
        out.sort(Comparator.comparing(r -> r.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    private static List<Path> list(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static Instant modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }
}
