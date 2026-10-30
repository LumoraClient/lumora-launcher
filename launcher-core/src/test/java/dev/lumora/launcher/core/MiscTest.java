package dev.lumora.launcher.core;

import dev.lumora.launcher.core.diagnostics.CrashAnalyzer;
import dev.lumora.launcher.core.java.JavaRuntimeInstaller;
import dev.lumora.launcher.core.mojang.MavenCoordinate;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.OsInfo;
import dev.lumora.launcher.core.util.Redactor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiscTest {
    @AfterEach
    void clear() {
        Redactor.clearSecrets();
    }

    @Test
    void redactorMasksKnownSecretsAndPatterns() {
        Redactor.registerSecret("super-secret-value-42");
        String in = "token super-secret-value-42 | Authorization: Bearer abc.def-ghi | {\"access_token\":\"zzz\",\"x\":1}"
                + " | refresh_token=qqq&a=b | eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.c2lnbmF0dXJlc2ln";
        String out = Redactor.redact(in);
        assertFalse(out.contains("super-secret-value-42"));
        assertFalse(out.contains("abc.def-ghi"));
        assertFalse(out.contains("zzz"));
        assertFalse(out.contains("qqq"));
        assertFalse(out.contains("eyJzdWIiOiIxMjM0NTY3ODkwIn0"));
        assertTrue(out.contains("\"x\":1"), "non-secret content survives");
    }

    @Test
    void crashAnalyzerRecognisesMemoryAndModProblems() {
        List<CrashAnalyzer.Hint> h = CrashAnalyzer.analyze("Incompatible mods found!\njava.lang.OutOfMemoryError: Java heap space");
        assertEquals(2, h.size());
        assertTrue(h.get(1).advice().contains("Arbeitsspeicher"));
        assertTrue(CrashAnalyzer.analyze("all good").isEmpty());
    }

    @Test
    void javaRuntimePlatformKeys() {
        assertEquals("windows-x64", new OsInfo(OsInfo.Name.WINDOWS, OsInfo.Arch.X86_64, "10").javaRuntimePlatform());
        assertEquals("linux", new OsInfo(OsInfo.Name.LINUX, OsInfo.Arch.X86_64, "6").javaRuntimePlatform());
        assertEquals("mac-os-arm64", new OsInfo(OsInfo.Name.OSX, OsInfo.Arch.ARM64, "15").javaRuntimePlatform());
        assertNotNull(new JavaRuntimeInstaller(null, Path.of("rt"), new OsInfo(OsInfo.Name.WINDOWS, OsInfo.Arch.X86_64, "10")));
    }

    @Test
    void mavenCoordinates() {
        assertEquals("net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar", MavenCoordinate.parse("net.fabricmc:fabric-loader:0.19.5").path());
        assertEquals("com/mojang/jtracy/1.0.37/jtracy-1.0.37-natives-windows.jar", MavenCoordinate.parse("com.mojang:jtracy:1.0.37:natives-windows").path());
        assertEquals("a/b/1/b-1.zip", MavenCoordinate.parse("a:b:1@zip").path());
        assertThrows(IllegalArgumentException.class, () -> MavenCoordinate.parse("a:../../b:1"));
        assertThrows(IllegalArgumentException.class, () -> MavenCoordinate.parse("onlyone"));
    }

    @Test
    void pathTraversalIsBlocked() throws IOException {
        Path base = Path.of("base");
        assertTrue(FileUtil.resolveInside(base, "a/b.jar").startsWith(base.toAbsolutePath().normalize()));
        assertThrows(IOException.class, () -> FileUtil.resolveInside(base, "../outside.jar"));
        assertThrows(IOException.class, () -> FileUtil.resolveInside(base, "a/../../outside.jar"));
    }

    @Test
    void launcherPathsLayout() {
        LauncherPaths p = new LauncherPaths(Path.of("root"));
        assertEquals(Path.of("root", "instances", "26.1.2"), p.instance("26.1.2"));
        assertEquals(Path.of("root", "config", "launcher.json"), p.configFile());
    }

    @Test
    void catalogArtifactsHaveDistinctFileNamesPerProfile() throws IOException {
        ProfileCatalog c = ProfileCatalog.loadBundled();
        for (ProfileCatalog.Profile p : c.profiles()) {
            var all = new java.util.ArrayList<>(c.profileArtifacts(p));
            p.resourcePacks.forEach(id -> all.add(c.artifact(id)));
            var names = all.stream().map(a -> a.fileName).toList();
            assertEquals(names.size(), names.stream().distinct().count(), p.id);
        }
    }
}
