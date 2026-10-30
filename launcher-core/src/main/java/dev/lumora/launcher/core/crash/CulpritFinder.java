package dev.lumora.launcher.core.crash;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Guesses which mod caused a crash, the way crash helpers like "Not Enough Crashes" do, but read-only from the
 * outside: it indexes the jars in mods/ (mod id, name, mixin configs, class packages) and scores
 * <ul>
 *   <li>mixin configs named in the report (e.g. "sodium.mixins.json") - a strong signal,</li>
 *   <li>stack frames whose class belongs to a mod's packages - weighted by how high up they are,</li>
 *   <li>mod ids named in Fabric Loader's error messages ("Mod 'X' (x) ...").</li>
 * </ul>
 * Minecraft, Java, Fabric Loader and Mixin frames are ignored. The result is a hint, not a proof.
 */
public final class CulpritFinder {
    private static final Pattern FRAME = Pattern.compile("^\\s*at\\s+(?:[\\w.\\-]+//)?([\\w$.]+)\\.[\\w$<>]+\\(", Pattern.MULTILINE);
    private static final Pattern MIXIN_CONFIG = Pattern.compile("([\\w.\\-]+\\.json)");
    private static final Pattern LOADER_MOD = Pattern.compile("[Mm]od '([^']+)' \\(([a-z0-9_\\-]+)\\)");
    private static final List<String> IGNORED = List.of("java.", "javax.", "jdk.", "sun.", "com.sun.", "net.minecraft.",
            "com.mojang.", "net.fabricmc.loader.", "org.spongepowered.", "com.llamalad7.mixinextras.", "org.lwjgl.",
            "io.netty.", "com.google.", "org.apache.", "it.unimi.", "org.slf4j.", "org.objectweb.");
    private static final int MAX_JAR_ENTRIES = 200_000;

    record Mod(String id, String name, Path file, Set<String> mixinConfigs, Set<String> packages) {
    }

    private CulpritFinder() {
    }

    public static List<CrashReport.Suspect> find(String report, Path modsDir) {
        return find(report, index(modsDir));
    }

    static List<CrashReport.Suspect> find(String report, List<Mod> mods) {
        Map<String, Integer> score = new LinkedHashMap<>();
        Map<String, String> reason = new HashMap<>();
        Map<String, Mod> byId = new HashMap<>();
        mods.forEach(m -> byId.put(m.id(), m));

        // 1) mixin configs named anywhere in the report
        Set<String> configsInReport = new HashSet<>();
        Matcher mc = MIXIN_CONFIG.matcher(report);
        while (mc.find()) {
            configsInReport.add(mc.group(1).toLowerCase(Locale.ROOT));
        }
        for (Mod m : mods) {
            for (String cfg : m.mixinConfigs()) {
                if (configsInReport.contains(cfg.toLowerCase(Locale.ROOT)) && report.toLowerCase(Locale.ROOT).contains("mixin")) {
                    add(score, reason, m.id(), 60, "Mixin-Konfiguration „" + cfg + "“ wird im Bericht genannt");
                }
            }
        }
        // 2) stack frames, the topmost ones count most
        Matcher f = FRAME.matcher(report);
        int position = 0;
        Map<String, Integer> frames = new HashMap<>();
        while (f.find() && position < 400) {
            String cls = f.group(1);
            position++;
            if (IGNORED.stream().anyMatch(cls::startsWith)) {
                continue;
            }
            Mod m = owner(cls, mods);
            if (m != null) {
                int weight = position <= 10 ? 8 : position <= 40 ? 3 : 1;
                score.merge(m.id(), weight, Integer::sum);
                frames.merge(m.id(), 1, Integer::sum);
            }
        }
        frames.forEach((id, n) -> reason.putIfAbsent(id, n + (n == 1 ? " Zeile" : " Zeilen") + " im Stacktrace stammen aus dieser Mod"));
        // 3) Fabric Loader dependency/format errors name the mod directly
        Matcher lm = LOADER_MOD.matcher(report);
        while (lm.find()) {
            String id = lm.group(2);
            if (byId.containsKey(id)) {
                add(score, reason, id, 40, "wird in der Fehlermeldung von Fabric Loader genannt");
            }
        }
        List<CrashReport.Suspect> out = new ArrayList<>();
        score.forEach((id, s) -> {
            Mod m = byId.get(id);
            if (m != null && s >= 3) {
                out.add(new CrashReport.Suspect(id, m.name(), m.file(), reason.getOrDefault(id, ""), s));
            }
        });
        out.sort(Comparator.comparingInt(CrashReport.Suspect::score).reversed());
        return out.size() > 3 ? List.copyOf(out.subList(0, 3)) : List.copyOf(out);
    }

    private static void add(Map<String, Integer> score, Map<String, String> reason, String id, int s, String why) {
        score.merge(id, s, Integer::sum);
        reason.put(id, why);
    }

    /** The mod whose packages contain this class (longest matching package wins). */
    static Mod owner(String className, List<Mod> mods) {
        Mod best = null;
        int bestLen = 0;
        for (Mod m : mods) {
            for (String pkg : m.packages()) {
                if (className.startsWith(pkg + ".") && pkg.length() > bestLen) {
                    best = m;
                    bestLen = pkg.length();
                }
            }
        }
        return best;
    }

    /** Index of all Fabric mods in the folder; unreadable jars are skipped. */
    static List<Mod> index(Path modsDir) {
        List<Mod> out = new ArrayList<>();
        if (!Files.isDirectory(modsDir)) {
            return out;
        }
        List<Path> jars;
        try (Stream<Path> s = Files.list(modsDir)) {
            jars = s.filter(p -> p.getFileName().toString().endsWith(".jar")).toList();
        } catch (IOException e) {
            return out;
        }
        for (Path jar : jars) {
            try {
                Mod m = read(jar);
                if (m != null) {
                    out.add(m);
                }
            } catch (IOException | RuntimeException ignored) {
                // not a readable mod jar
            }
        }
        // packages claimed by several jars say nothing about the culprit
        Map<String, Integer> owners = new HashMap<>();
        out.forEach(m -> m.packages().forEach(p -> owners.merge(p, 1, Integer::sum)));
        List<Mod> cleaned = new ArrayList<>();
        for (Mod m : out) {
            Set<String> unique = new HashSet<>();
            m.packages().stream().filter(p -> owners.get(p) == 1).forEach(unique::add);
            cleaned.add(new Mod(m.id(), m.name(), m.file(), m.mixinConfigs(), unique));
        }
        return cleaned;
    }

    static Mod read(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry json = zip.getEntry("fabric.mod.json");
            if (json == null) {
                return null;
            }
            JsonObject o;
            try (InputStream in = zip.getInputStream(json)) {
                o = Json.GSON.fromJson(new String(in.readNBytes(1024 * 1024), StandardCharsets.UTF_8), JsonObject.class);
            } catch (JsonParseException | IllegalStateException e) {
                return null;
            }
            if (o == null || !o.has("id")) {
                return null;
            }
            String id = o.get("id").getAsString();
            String name = o.has("name") && o.get("name").isJsonPrimitive() ? o.get("name").getAsString() : id;
            Set<String> mixins = new HashSet<>();
            if (o.has("mixins") && o.get("mixins").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("mixins")) {
                    if (e.isJsonPrimitive()) {
                        mixins.add(e.getAsString());
                    } else if (e.isJsonObject() && e.getAsJsonObject().has("config")) {
                        mixins.add(e.getAsJsonObject().get("config").getAsString());
                    }
                }
            }
            // class packages, shortened to at most 4 segments (e.g. net.caffeinemc.mods.sodium)
            Set<String> packages = new HashSet<>();
            int[] n = {0};
            zip.stream().takeWhile(e -> n[0]++ < MAX_JAR_ENTRIES).forEach(e -> {
                String name0 = e.getName();
                if (!name0.endsWith(".class") || name0.startsWith("META-INF/") || !name0.contains("/")) {
                    return;
                }
                String[] parts = name0.substring(0, name0.lastIndexOf('/')).split("/");
                int len = Math.min(parts.length, 4);
                if (len >= 2) {
                    packages.add(String.join(".", java.util.Arrays.copyOf(parts, len)));
                }
            });
            return new Mod(id, name, jar, mixins, packages);
        }
    }
}
