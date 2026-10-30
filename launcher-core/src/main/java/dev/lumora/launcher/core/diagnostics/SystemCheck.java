package dev.lumora.launcher.core.diagnostics;

import dev.lumora.launcher.core.BuildInfo;
import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.util.OsInfo;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Pre-launch environment checks (OS, memory, GPU, disk space, data directory). */
public final class SystemCheck {
    public enum Status { OK, WARN, FAIL }

    public record Item(String name, Status status, String detail) {
    }

    public record Report(List<Item> items, List<String> gpus, long totalRamMb) {
        public String render() {
            StringBuilder sb = new StringBuilder();
            for (Item i : items) {
                sb.append(String.format("[%-4s] %-22s %s%n", i.status(), i.name(), i.detail()));
            }
            return sb.toString();
        }
    }

    private SystemCheck() {
    }

    public static Report run(OsInfo os, LauncherPaths paths) {
        List<Item> items = new ArrayList<>();
        items.add(new Item("Launcher", Status.OK, BuildInfo.NAME + " " + BuildInfo.VERSION
                + " (Java " + Runtime.version() + ")"));
        items.add(new Item("Betriebssystem", os.arch().is64Bit() ? Status.OK : Status.FAIL,
                System.getProperty("os.name") + " " + os.version() + " " + os.mojangArch()
                        + (os.arch().is64Bit() ? "" : " - 64-Bit-System erforderlich")));

        long ram = totalRamMb();
        items.add(new Item("Arbeitsspeicher", ram >= 6144 ? Status.OK : Status.WARN, ram + " MB"
                + (ram > 0 && ram < 6144 ? " - empfohlen sind mind. 8 GB" : "")));

        List<String> gpus = gpuNames(os);
        items.add(new Item("Grafikkarte(n)", gpus.isEmpty() ? Status.WARN : Status.OK,
                gpus.isEmpty() ? "nicht ermittelbar" : String.join("; ", gpus)));


        try {
            Path probe = Files.exists(paths.root()) ? paths.root() : paths.root().getRoot();
            FileStore store = Files.getFileStore(probe);
            long freeMb = store.getUsableSpace() / 1048576;
            items.add(new Item("Freier Speicherplatz", freeMb > 3000 ? Status.OK : Status.WARN,
                    freeMb + " MB auf " + store + (freeMb <= 3000 ? " - mind. 3 GB empfohlen" : "")));
        } catch (IOException e) {
            items.add(new Item("Freier Speicherplatz", Status.WARN, "nicht ermittelbar: " + e.getMessage()));
        }
        items.add(new Item("Datenverzeichnis", Status.OK, paths.root().toAbsolutePath().toString()));
        return new Report(List.copyOf(items), gpus, ram);
    }

    static long totalRamMb() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean b) {
            return b.getTotalMemorySize() / 1048576;
        }
        return -1;
    }

    static List<String> gpuNames(OsInfo os) {
        List<String> cmd = switch (os.name()) {
            case WINDOWS -> List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "Get-CimInstance Win32_VideoController | ForEach-Object { $_.Name + ' (Treiber ' + $_.DriverVersion + ')' }");
            case LINUX -> List.of("sh", "-c", "command -v lspci >/dev/null && lspci | grep -Ei 'vga|3d|display' | cut -d: -f3-");
            case OSX -> List.of("sh", "-c", "system_profiler SPDisplaysDataType | grep 'Chipset Model' | cut -d: -f2");
        };
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(15, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return List.of();
            }
            return out.lines().map(String::strip).filter(s -> !s.isEmpty()).limit(4).toList();
        } catch (IOException e) {
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }
}
