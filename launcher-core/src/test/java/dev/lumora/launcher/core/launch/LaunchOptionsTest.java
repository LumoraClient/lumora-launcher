package dev.lumora.launcher.core.launch;

import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.profile.Flavor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchOptionsTest {
    private static LauncherConfig repaired(LauncherConfig c) {
        c.validateAndRepair(List.of("p"), List.of(), "p");
        return c;
    }

    @Test
    void performanceFlagsUnlessOwnCollector() {
        LauncherConfig c = new LauncherConfig();
        assertEquals(LaunchOptions.PERFORMANCE_FLAGS, LaunchOptions.jvmArgs(c, Path.of("d")));
        c.extraJvmArgs = List.of("-XX:+UseZGC");
        assertTrue(LaunchOptions.jvmArgs(c, Path.of("d")).isEmpty(), "two collectors would stop the JVM");
        c.extraJvmArgs = List.of();
        c.performanceFlags = false;
        assertTrue(LaunchOptions.jvmArgs(c, Path.of("d")).isEmpty());
    }

    @Test
    void heapDumpAndDebugging() {
        LauncherConfig c = new LauncherConfig();
        c.performanceFlags = false;
        c.heapDumpOnOom = true;
        c.debugMode = "listen";
        List<String> args = LaunchOptions.jvmArgs(repaired(c), Path.of("diag"));
        assertTrue(args.contains("-XX:+HeapDumpOnOutOfMemoryError"));
        assertTrue(args.stream().anyMatch(a -> a.startsWith("-XX:HeapDumpPath=") && a.endsWith("diag")));
        assertTrue(args.contains("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:5005"));
        c.debugMode = "attach";
        c.debugAddress = "localhost:8000";
        assertTrue(LaunchOptions.jvmArgs(repaired(c), Path.of("diag"))
                .contains("-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=localhost:8000"));
    }

    @Test
    void debugAddressStaysOnLoopback() {
        assertTrue(LauncherConfig.isValidDebugAddress("127.0.0.1:5005"));
        assertTrue(LauncherConfig.isValidDebugAddress("[::1]:5005"));
        assertFalse(LauncherConfig.isValidDebugAddress("0.0.0.0:5005"), "never open the debug port to the network");
        assertFalse(LauncherConfig.isValidDebugAddress("192.168.1.2:5005"));
        assertFalse(LauncherConfig.isValidDebugAddress("localhost:70000"));
        assertFalse(LauncherConfig.isValidDebugAddress("localhost:5005,server=y"));
        LauncherConfig c = new LauncherConfig();
        c.debugAddress = "evil.example:5005";
        assertEquals("127.0.0.1:5005", repaired(c).debugAddress);
    }

    @Test
    void environmentAndFullscreen() {
        LauncherConfig c = new LauncherConfig();
        c.environmentVariables = LaunchOptions.parseEnvironment("__GL_THREADED_OPTIMIZATIONS=0, MESA_GL_VERSION_OVERRIDE=4.6\nbad line\n=x");
        repaired(c);
        assertEquals(Map.of("__GL_THREADED_OPTIMIZATIONS", "0", "MESA_GL_VERSION_OVERRIDE", "4.6"), LaunchOptions.environment(c));
        assertTrue(LaunchOptions.gameArgs(c).isEmpty());
        c.forceFullscreen = true;
        assertEquals(List.of("--fullscreen"), LaunchOptions.gameArgs(c));
    }

    @Test
    void newSettingsAreRepaired() {
        LauncherConfig c = new LauncherConfig();
        c.selectedFlavor = "rift";
        c.accentColor = "lila";
        c.notificationLevel = "loud";
        c.discordAppId = "not-a-snowflake";
        c.closeLauncherOnGameStart = true;
        c.debugMode = "remote";
        repaired(c);
        assertEquals("lumora", c.selectedFlavor);
        assertEquals(LauncherConfig.DEFAULT_ACCENT, c.accentColor);
        assertEquals("all", c.notificationLevel);
        assertNull(c.discordAppId);
        assertEquals("hide", c.afterLaunch, "old minimise setting carries over");
        assertFalse(c.closeLauncherOnGameStart);
        assertEquals("off", c.debugMode);
        c.discordAppId = " 123456789012345678 ";
        assertEquals("123456789012345678", repaired(c).discordAppId);
    }

    @Test
    void flavorInstances() {
        assertEquals("26.1.2", Flavor.LUMORA.instanceId("26.1.2"));
        assertEquals("26.1.2-vanilla", Flavor.VANILLA.instanceId("26.1.2"));
        // selectable mod loaders are removed: stored ids of them start Lumora
        for (String removed : new String[]{"fabric", "quilt", "forge", "neoforge"}) {
            assertEquals(Flavor.LUMORA, Flavor.of(removed), removed);
        }
        assertEquals(Flavor.VANILLA, Flavor.of("Vanilla"));
        assertEquals(Flavor.LUMORA, Flavor.of("rift"));
        assertEquals(Flavor.LUMORA, Flavor.of(null));
    }
}
