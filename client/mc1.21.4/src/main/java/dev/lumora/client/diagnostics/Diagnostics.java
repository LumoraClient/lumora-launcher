package dev.lumora.client.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.lumora.client.integration.RuntimeInfo;
import dev.lumora.client.perf.FrameStats;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.SharedConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the in-game diagnostics report (no personal data, no tokens: only versions and hardware info). */
public final class Diagnostics {
    private static final Logger LOG = LoggerFactory.getLogger("Lumora");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private Diagnostics() {
    }

    public static Path directory() {
        String prop = System.getProperty("lumora.diagnosticsDir");
        return prop != null && !prop.isBlank() ? Path.of(prop) : FabricLoader.getInstance().getGameDir().resolve("lumora-diagnostics");
    }

    public static String profile() {
        return System.getProperty("lumora.profile", "unknown");
    }

    public static Map<String, Object> collect() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("timestamp", Instant.now().toString());
        r.put("profile", profile());
        r.put("launcherVersion", System.getProperty("lumora.launcher.version", "unknown"));
        r.put("minecraft", SharedConstants.getCurrentVersion().getName());
        r.put("java", Runtime.version().toString());
        r.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
        r.put("maxHeapMb", Runtime.getRuntime().maxMemory() / 1048576);
        r.put("usedHeapMb", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576);
        r.put("cpuCores", Runtime.getRuntime().availableProcessors());
        r.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());

        Map<String, Object> renderer = new LinkedHashMap<>();
        renderer.put("sodiumLoaded", RuntimeInfo.sodiumLoaded());
        renderer.put("gpuDevice", RuntimeInfo.gpuDevice());
        r.put("renderer", renderer);

        Map<String, Object> protocol = new LinkedHashMap<>();
        protocol.put("viafabricplusLoaded", RuntimeInfo.viaFabricPlusLoaded());
        protocol.put("targetVersion", RuntimeInfo.vfpTargetVersion());
        r.put("protocol", protocol);

        List<String> mods = new ArrayList<>();
        // Top-level mods only; jar-in-jar libraries (e.g. Fabric API modules) would drown the list.
        FabricLoader.getInstance().getAllMods().stream()
                .filter(c -> c.getContainingMod().isEmpty())
                .map(ModContainer::getMetadata)
                .sorted(Comparator.comparing(m -> m.getId()))
                .forEach(m -> mods.add(m.getId() + " " + m.getVersion().getFriendlyString()));
        r.put("mods", mods);
        return r;
    }

    public static Path write(String name, Map<String, Object> data) {
        Path dir = directory();
        Path file = dir.resolve(name);
        try {
            Files.createDirectories(dir);
            Files.writeString(file, GSON.toJson(data), StandardCharsets.UTF_8);
            LOG.info("Wrote diagnostics to {}", file);
        } catch (IOException e) {
            LOG.warn("Could not write diagnostics {}", file, e);
        }
        return file;
    }

    public static Path writeBenchmark(FrameStats.Summary s) {
        Map<String, Object> data = collect();
        data.put("benchmark", s);
        data.put("benchmarkSummary", s.format());
        return write("benchmark-" + profile() + "-" + System.currentTimeMillis() + ".json", data);
    }
}
