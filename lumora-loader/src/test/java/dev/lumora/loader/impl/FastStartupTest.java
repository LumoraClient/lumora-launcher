package dev.lumora.loader.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "Faster Startup": the switch read from the module's saved settings, and the same classes either way. */
class FastStartupTest {
    @TempDir
    Path dir;

    @Test
    void onUnlessTheModuleOrTheSwitchIsOff() throws IOException {
        Path config = dir.resolve("config");
        assertTrue(Boot.fastStartup(config), "nothing saved yet: on (the default)");
        Files.createDirectories(config.resolve("lumora/addons"));
        Files.writeString(config.resolve("lumora/addons.json"), "{\"disabled\":[\"lumora-freelook\"],\"seen\":[\"lumora-performance\"]}");
        Files.writeString(config.resolve("lumora/addons/lumora-performance.json"), "{\n  \"startup\": \"true\"\n}");
        assertTrue(Boot.fastStartup(config));
        Files.writeString(config.resolve("lumora/addons/lumora-performance.json"), "{\n  \"frame\": \"true\",\n  \"startup\": \"false\"\n}");
        assertFalse(Boot.fastStartup(config), "switch off");
        Files.writeString(config.resolve("lumora/addons/lumora-performance.json"), "{}");
        Files.writeString(config.resolve("lumora/addons.json"), "{\"disabled\":[\"lumora-performance\"]}");
        assertFalse(Boot.fastStartup(config), "module off");
    }

    /** A jar with one class, in a folder with a space (URL encoding of the class URLs). */
    private Path gameJar() throws IOException {
        Path src = dir.resolve("src/demo/Thing.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, "package demo; public class Thing { public static String hello() { return \"hi\"; } }");
        Path out = dir.resolve("classes");
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        assertEquals(0, javac.run(null, null, err, "-proc:none", "--release", "21", "-d", out.toString(), src.toString()), err.toString());
        Path jar = dir.resolve("game folder/game.jar");
        Files.createDirectories(jar.getParent());
        try (OutputStream o = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(o)) {
            zip.putNextEntry(new ZipEntry("demo/Thing.class"));
            zip.write(Files.readAllBytes(out.resolve("demo/Thing.class")));
            zip.closeEntry();
        }
        return jar;
    }

    @Test
    void fastAndNormalLoadTheSameClassWithTheSameCodeSource() throws Exception {
        Path jar = gameJar();
        URL[] urls = {jar.toUri().toURL()};
        // the old path reads through jar: URL connections; the JDK would keep the test jar open (Windows cannot delete it)
        boolean caches = java.net.URLConnection.getDefaultUseCaches("jar");
        java.net.URLConnection.setDefaultUseCaches("jar", false);
        Class<?> normal;
        Class<?> fast;
        try (GameClassLoader a = new GameClassLoader(urls, FastStartupTest.class.getClassLoader());
             GameClassLoader b = new GameClassLoader(urls, FastStartupTest.class.getClassLoader())) {
            b.fast(true);
            normal = a.loadClass("demo.Thing");
            fast = b.loadClass("demo.Thing");
            assertNotSame(normal, fast);
            assertEquals("hi", fast.getMethod("hello").invoke(null));
            assertEquals(normal.getProtectionDomain().getCodeSource().getLocation(), fast.getProtectionDomain().getCodeSource().getLocation());
            assertEquals(urls[0], fast.getProtectionDomain().getCodeSource().getLocation());
            assertTrue(b.stats().startsWith("1 classes"), b.stats());
            assertTrue(b.stats().endsWith("fast true"), b.stats());
        } finally {
            java.net.URLConnection.setDefaultUseCaches("jar", caches);
        }
    }
}
