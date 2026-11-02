package dev.lumora.loader.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole loader without Minecraft: a small "game" jar (main class + a target class), a client jar with
 * {@code lumora.loader.json}, an entry point and a mixin into the target. Runs in its own JVM (Mixin is global).
 */
class LoaderEndToEndTest {
    @TempDir
    Path dir;

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void compile(Path src, Path out, String classpath) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        List<String> args = new ArrayList<>(List.of("-proc:none", "--release", "21", "-d", out.toString(), "-cp", classpath));
        try (Stream<Path> s = Files.walk(src)) {
            s.filter(p -> p.toString().endsWith(".java")).forEach(p -> args.add(p.toString()));
        }
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = javac.run(null, null, err, args.toArray(String[]::new));
        assertEquals(0, code, err.toString());
    }

    private static Path jar(Path classes, Path jar) throws IOException {
        try (OutputStream o = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(o); Stream<Path> s = Files.walk(classes)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                zip.putNextEntry(new ZipEntry(classes.relativize(p).toString().replace('\\', '/')));
                zip.write(Files.readAllBytes(p));
                zip.closeEntry();
            }
        }
        return jar;
    }

    @Test
    void mixinAppliesAndEntrypointRunsBeforeMain() throws Exception {
        String cp = System.getProperty("java.class.path");
        // the "game"
        Path gameSrc = dir.resolve("game-src");
        write(gameSrc.resolve("demo/game/Target.java"), "package demo.game; public class Target { public static String hello() { return \"vanilla\"; }"
                + " static String join(String a, String b) { return a + b; } public static String greet() { return join(\"a\", \"b\"); } }");
        write(gameSrc.resolve("demo/game/Main.java"), "package demo.game; public class Main { public static void main(String[] a) {"
                + " System.out.println(\"RESULT \" + Target.hello() + \" \" + Target.greet() + \" \" + String.join(\",\", a) + \" \" + Main.class.getClassLoader().getName()); } }");
        Path gameClasses = dir.resolve("game-classes");
        compile(gameSrc, gameClasses, cp);
        Path gameJar = jar(gameClasses, dir.resolve("game.jar"));
        // the client: entry point + mixin
        Path modSrc = dir.resolve("mod-src");
        write(modSrc.resolve("demo/mod/Entry.java"), "package demo.mod; public class Entry implements dev.lumora.loader.api.ClientEntrypoint {"
                + " public void onInitializeClient() { System.out.println(\"ENTRY \" + dev.lumora.loader.api.LumoraLoader.component(\"demo\").map(c -> c.version()).orElse(\"?\")"
                + " + \" \" + dev.lumora.loader.api.LumoraLoader.gameDir().getFileName()); } }");
        write(modSrc.resolve("demo/mod/mixin/TargetMixin.java"), "package demo.mod.mixin;"
                + " import org.spongepowered.asm.mixin.Mixin; import org.spongepowered.asm.mixin.injection.*;"
                + " import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;"
                + " @Mixin(demo.game.Target.class) public class TargetMixin {"
                + " @Inject(method = \"hello\", at = @At(\"HEAD\"), cancellable = true)"
                + " private static void lumora(CallbackInfoReturnable<String> cir) { cir.setReturnValue(\"mixed\"); }"
                // @ModifyArgs needs a class Mixin generates at runtime (org.spongepowered.asm.synthetic.args.Args$1)
                + " @ModifyArgs(method = \"greet\", at = @At(value = \"INVOKE\", target = \"Ldemo/game/Target;join(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;\"))"
                + " private static void lumora$args(org.spongepowered.asm.mixin.injection.invoke.arg.Args args) { args.set(1, \"Z\"); } }");
        Path modClasses = dir.resolve("mod-classes");
        compile(modSrc, modClasses, cp + java.io.File.pathSeparator + gameClasses);
        write(modClasses.resolve("lumora.loader.json"), "{\"schema\":1,\"id\":\"demo\",\"name\":\"Demo\",\"version\":\"1.2.3\","
                + "\"entrypoints\":[\"demo.mod.Entry\"],\"mixins\":[\"demo.mixins.json\"]}");
        write(modClasses.resolve("demo.mixins.json"), "{\"required\":true,\"package\":\"demo.mod.mixin\",\"compatibilityLevel\":\"JAVA_21\","
                + "\"mixins\":[\"TargetMixin\"],\"injectors\":{\"defaultRequire\":1}}");
        Path modJar = jar(modClasses, dir.resolve("mod.jar"));

        Path gameDir = Files.createDirectories(dir.resolve("instance"));
        Process p = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", cp, "-Dlumora.loader.game=" + gameJar, "-Dlumora.loader.jars=" + modJar,
                "-Dlumora.loader.target=demo.game.Main", "dev.lumora.loader.Main", "--gameDir", gameDir.toString(), "--version", "test")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), out);
        assertEquals(0, p.exitValue(), out);
        assertTrue(out.contains("ENTRY 1.2.3 instance"), out);
        assertTrue(out.contains("RESULT mixed aZ --gameDir," + gameDir + ",--version,test lumora-game"), out);
        assertTrue(out.indexOf("ENTRY") < out.indexOf("RESULT"), "entry point before main: " + out);
        assertTrue(out.contains("[Lumora Loader] 1.0.0: Minecraft test, 1 jar(s), 1 mixin config(s)"), out);
    }

    @Test
    void descriptorIsValidated() {
        Path jar = Path.of("x.jar");
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> JarDescriptor.parse("{\"id\":\"X\"}", jar));
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> JarDescriptor.parse("{\"id\":\"ok\",\"entrypoints\":[\"no-class\"]}", jar));
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> JarDescriptor.parse("{\"id\":\"ok\",\"mixins\":[\"../evil.txt\"]}", jar));
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> JarDescriptor.parse("[]", jar));
    }

    @Test
    void parentOnlyPackages() {
        assertTrue(GameClassLoader.parentOnly("org.spongepowered.asm.mixin.Mixin"));
        assertTrue(GameClassLoader.parentOnly("dev.lumora.loader.api.LumoraLoader"));
        assertTrue(GameClassLoader.parentOnly("java.lang.String"));
        org.junit.jupiter.api.Assertions.assertFalse(GameClassLoader.parentOnly("net.minecraft.client.Minecraft"));
        org.junit.jupiter.api.Assertions.assertFalse(GameClassLoader.parentOnly("dev.lumora.client.LumoraClient"));
    }
}
