package dev.lumora.launcher.core.perf;

import dev.lumora.launcher.core.util.FileUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Performance profiles for Minecraft's options.txt. Only keys that already exist in the file are changed, so a
 * version that names an option differently (1.8.9 "fancyGraphics" vs. "graphicsMode" since 1.16) is never given an
 * unknown key. Before the first change the original file is kept as options.txt.lumora-backup.
 */
public final class GraphicsPresets {
    public static final String BACKUP = "options.txt.lumora-backup";

    public enum Preset {
        MAX_FPS("Maximale FPS", "Kurze Sichtweite, schnelle Grafik, wenig Partikel, keine Wolken und Schatten – für schwache PCs und PvP."),
        BALANCED("Ausgewogen", "Mittlere Sichtweite, schöne Grafik, weniger Partikel – gute Bilder bei flüssigen FPS."),
        QUALITY("Beste Grafik", "Große Sichtweite, alle Partikel, Wolken, Schatten und V-Sync – für starke PCs.");

        public final String label;
        public final String description;

        Preset(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    private GraphicsPresets() {
    }

    /** The option values of a preset (keys as in options.txt). */
    public static Map<String, String> values(Preset p) {
        Map<String, String> v = new LinkedHashMap<>();
        switch (p) {
            case MAX_FPS -> {
                v.put("renderDistance", "8");
                v.put("simulationDistance", "6");
                v.put("maxFps", "260");
                v.put("enableVsync", "false");
                v.put("particles", "2");
                v.put("entityShadows", "false");
                v.put("graphicsMode", "0");
                v.put("fancyGraphics", "false");
                v.put("biomeBlendRadius", "0");
                v.put("renderClouds", "\"false\"");
                v.put("entityDistanceScaling", "0.75");
            }
            case BALANCED -> {
                v.put("renderDistance", "12");
                v.put("simulationDistance", "8");
                v.put("maxFps", "144");
                v.put("enableVsync", "false");
                v.put("particles", "1");
                v.put("entityShadows", "true");
                v.put("graphicsMode", "1");
                v.put("fancyGraphics", "true");
                v.put("biomeBlendRadius", "2");
                v.put("renderClouds", "\"fast\"");
                v.put("entityDistanceScaling", "1.0");
            }
            case QUALITY -> {
                v.put("renderDistance", "16");
                v.put("simulationDistance", "12");
                v.put("maxFps", "260");
                v.put("enableVsync", "true");
                v.put("particles", "0");
                v.put("entityShadows", "true");
                v.put("graphicsMode", "1");
                v.put("fancyGraphics", "true");
                v.put("biomeBlendRadius", "3");
                v.put("renderClouds", "\"true\"");
                v.put("entityDistanceScaling", "1.25");
            }
        }
        return v;
    }

    /**
     * Applies a preset to the instance's options.txt.
     *
     * @return the keys that were changed (empty if options.txt does not exist yet: start the version once first)
     */
    public static List<String> apply(Path gameDir, Preset p) throws IOException {
        Path file = gameDir.resolve("options.txt");
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        Path backup = gameDir.resolve(BACKUP);
        if (!Files.exists(backup)) {
            Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
        }
        // options.txt is UTF-8 since 1.13; older versions write plain ASCII for these keys
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Map<String, String> wanted = values(p);
        List<String> changed = new ArrayList<>();
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            int colon = line.indexOf(':');
            String key = colon > 0 ? line.substring(0, colon) : null;
            if (key != null && wanted.containsKey(key)) {
                String value = wanted.get(key);
                // renderClouds is quoted since 1.13 ("fast"), plain before (fast)
                if (key.equals("renderClouds") && !line.substring(colon + 1).startsWith("\"")) {
                    value = value.replace("\"", "");
                }
                String next = key + ":" + value;
                if (!next.equals(line)) {
                    changed.add(key);
                }
                out.add(next);
            } else {
                out.add(line);
            }
        }
        if (!changed.isEmpty()) {
            FileUtil.writeAtomically(file, (String.join("\n", out) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return changed;
    }

    /** Restores options.txt from the backup taken before the first preset. False if there is none. */
    public static boolean restore(Path gameDir) throws IOException {
        Path backup = gameDir.resolve(BACKUP);
        if (!Files.isRegularFile(backup)) {
            return false;
        }
        Files.copy(backup, gameDir.resolve("options.txt"), StandardCopyOption.REPLACE_EXISTING);
        Files.delete(backup);
        return true;
    }

    /** Which preset the current options.txt matches exactly (all its known keys), or null. */
    public static Preset current(Path gameDir) {
        Path file = gameDir.resolve("options.txt");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Map<String, String> now = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    now.put(line.substring(0, colon), line.substring(colon + 1).replace("\"", ""));
                }
            }
        } catch (IOException e) {
            return null;
        }
        for (Preset p : Preset.values()) {
            boolean all = true;
            int seen = 0;
            for (Map.Entry<String, String> e : values(p).entrySet()) {
                String v = now.get(e.getKey());
                if (v != null) {
                    seen++;
                    all &= v.equals(e.getValue().replace("\"", ""));
                }
            }
            if (all && seen > 0) {
                return p;
            }
        }
        return null;
    }
}
