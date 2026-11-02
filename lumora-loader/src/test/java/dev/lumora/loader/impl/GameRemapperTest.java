package dev.lumora.loader.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Obfuscated classes -> Mojang names: classes, fields, methods through the hierarchy, lambdas, signatures dropped. */
class GameRemapperTest {
    @TempDir
    Path dir;

    static final String MAPPINGS = """
            # comment
            com.example.Base -> a:
                int value -> c
                java.lang.String label -> d
                1:3:int compute() -> b
                4:6:int compute(int) -> b
            com.example.Child -> e:
                7:8:void <init>() -> <init>
            com.example.Fn -> f:
                int apply(com.example.Base) -> g
            com.example.User -> h:
                int use() -> i
            """;

    @Test
    void parsesMembersWithObfuscatedDescriptors() throws IOException {
        MojangMappings m = MojangMappings.read(new StringReader(MAPPINGS));
        assertEquals("com/example/Base", m.classes.get("a"));
        assertEquals("compute", m.methods.get("a.b:()I"));
        assertEquals("compute", m.methods.get("a.b:(I)I"));
        assertEquals("apply", m.methods.get("f.g:(La;)I"), "argument types use obfuscated names");
        assertEquals("value", m.fields.get("a.c:I"));
        assertEquals("label", m.fields.get("a.d:Ljava/lang/String;"));
        assertNull(m.methods.get("e.<init>:()V"), "constructors keep their name");
    }

    @Test
    void remappedJarRunsWithMojangNames() throws Exception {
        Path src = dir.resolve("src");
        write(src.resolve("a.java"), "public class a { public int c = 2; public String d = \"x\"; public int b() { return c + 1; } public int b(int k) { return k * c; } }");
        write(src.resolve("e.java"), "public class e extends a { }");
        write(src.resolve("f.java"), "public interface f { int g(a v); }");
        // calls an inherited method through the subclass, reads an inherited field, and uses a lambda for f
        write(src.resolve("h.java"), "public class h { public static int i() { e x = new e(); f fn = v -> v.b(3) + v.c; return x.b() + fn.g(x); } }");
        Path classes = dir.resolve("classes");
        Files.createDirectories(classes);
        List<String> args = new ArrayList<>(List.of("--release", "21", "-d", classes.toString()));
        try (Stream<Path> s = Files.list(src)) {
            s.forEach(p -> args.add(p.toString()));
        }
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, err, args.toArray(String[]::new)), err.toString());
        Path game = dir.resolve("game.jar");
        try (OutputStream o = Files.newOutputStream(game); ZipOutputStream zip = new ZipOutputStream(o); Stream<Path> s = Files.list(classes)) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write("Manifest-Version: 1.0\r\n\r\nName: a.class\r\nSHA-256-Digest: abc\r\n\r\n".getBytes());
            zip.putNextEntry(new ZipEntry("META-INF/MOJANGCS.SF"));
            zip.write(new byte[]{1});
            zip.putNextEntry(new ZipEntry("assets/x.txt"));
            zip.write("asset".getBytes());
            for (Path p : s.toList()) {
                zip.putNextEntry(new ZipEntry(p.getFileName().toString()));
                zip.write(Files.readAllBytes(p));
            }
        }
        Path mappings = dir.resolve("client.txt");
        Files.writeString(mappings, MAPPINGS);

        Path out = GameRemapper.remapCached(game, mappings, dir.resolve("cache"));
        assertEquals(out, GameRemapper.remapCached(game, mappings, dir.resolve("cache")), "second call uses the cache");
        try (ZipFile z = new ZipFile(out.toFile())) {
            assertNotNull(z.getEntry("com/example/Base.class"));
            assertNotNull(z.getEntry("assets/x.txt"), "resources stay");
            assertNull(z.getEntry("META-INF/MOJANGCS.SF"), "signature dropped");
            assertTrue(new String(z.getInputStream(z.getEntry("META-INF/MANIFEST.MF")).readAllBytes()).indexOf("Digest") < 0);
        }
        try (URLClassLoader l = new URLClassLoader(new URL[]{out.toUri().toURL()}, null)) {
            Class<?> base = l.loadClass("com.example.Base");
            assertNotNull(base.getMethod("compute"));
            assertNotNull(base.getMethod("compute", int.class));
            assertNotNull(base.getField("value"));
            assertNotNull(base.getField("label"));
            assertEquals(base, l.loadClass("com.example.Child").getSuperclass());
            assertNotNull(l.loadClass("com.example.Fn").getMethod("apply", base));
            // runs: x.compute() = 3, lambda: compute(3) + value = 6 + 2 = 8 -> 11
            assertEquals(11, l.loadClass("com.example.User").getMethod("use").invoke(null));
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
