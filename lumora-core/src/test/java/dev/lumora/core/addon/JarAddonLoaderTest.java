package dev.lumora.core.addon;

import dev.lumora.api.addon.AddonInfo;
import dev.lumora.api.addon.AddonState;
import dev.lumora.api.i18n.Translations;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.FakePlatform;
import dev.lumora.core.LumoraCore;
import dev.lumora.testaddon.SampleJarAddon;
import dev.lumora.testaddon.lib.Greeter;
import dev.lumora.testaddon.lib.GreeterAddon;
import dev.lumora.testaddon.user.UserAddon;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lumora's own addon loader: jars from the addons folder, without a mod loader. */
class JarAddonLoaderTest {
    @TempDir
    Path dir;
    private Path addons;
    private LumoraCore core;
    private JarAddonLoader loader;

    /** Hides the test addons from the parent, so they can only come from the jars (like in the game). */
    static final class Hiding extends ClassLoader {
        Hiding(ClassLoader real) {
            super(real);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("dev.lumora.testaddon.")) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }

    static final CoreLogger QUIET = new CoreLogger() {
        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void warn(String message, Throwable error) {
        }

        @Override
        public void error(String message, Throwable error) {
        }
    };

    @BeforeEach
    void setUp() {
        addons = dir.resolve("addons");
        TestSink.EVENTS.clear();
    }

    @AfterEach
    void tearDown() {
        if (core != null) {
            core.stopping();
        }
        if (loader != null) {
            loader.close();
        }
    }

    static String descriptor(String id, String entrypoint, String extra) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + id + "\",\"version\":\"1.0.0\",\"api\":\"1.0.0\",\"entrypoint\":\""
                + entrypoint + "\"" + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    static byte[] classBytes(Class<?> c) throws IOException {
        try (InputStream in = c.getResourceAsStream("/" + c.getName().replace('.', '/') + ".class")) {
            return in.readAllBytes();
        }
    }

    static String path(Class<?> c) {
        return c.getName().replace('.', '/') + ".class";
    }

    static Path jar(Path folder, String name, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(folder);
        Path file = folder.resolve(name);
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return file;
    }

    private Path sampleJar() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(JarAddonLoader.DESCRIPTOR, descriptor("sample", SampleJarAddon.class.getName(), "").getBytes(StandardCharsets.UTF_8));
        e.put(path(SampleJarAddon.class), classBytes(SampleJarAddon.class));
        e.put("assets/sample/lang/en_us.json", "{\"sample.hello\":\"Hello %s\",\"sample.only_en\":\"only english\"}".getBytes(StandardCharsets.UTF_8));
        e.put("assets/sample/lang/de_de.json", "{\"sample.hello\":\"Hallo %s\"}".getBytes(StandardCharsets.UTF_8));
        e.put("assets/sample/lang/de_DE.lang", "# legacy\nsample.legacy=Alt\n".getBytes(StandardCharsets.UTF_8));
        return jar(addons, "sample-1.0.0.jar", e);
    }

    private JarAddonLoader loader() {
        loader = new JarAddonLoader(addons, new Hiding(getClass().getClassLoader()), QUIET);
        return loader;
    }

    @Test
    void missingFolderIsCreatedAndOtherFilesIgnored() throws IOException {
        assertTrue(loader().discover().isEmpty());
        assertTrue(Files.isDirectory(addons));
        Files.writeString(addons.resolve("readme.txt"), "not an addon");
        Files.writeString(addons.resolve("old.jar.disabled"), "switched off");
        assertTrue(loader.discover().isEmpty());
        assertTrue(new JarAddonLoader(null, getClass().getClassLoader(), QUIET).discover().isEmpty());
    }

    @Test
    void loadsTheEntryPointFromTheJarsOwnClassLoader() throws Exception {
        Path file = sampleJar();
        List<AddonCandidate> found = loader().discover();
        assertEquals(1, found.size());
        AddonCandidate c = found.get(0);
        assertNull(c.error());
        assertEquals("sample", c.descriptor().id());
        assertEquals("addons/sample-1.0.0.jar", c.source());
        assertEquals(file, c.jar());
        Object addon = c.factory().create();
        assertInstanceOf(AddonClassLoader.class, addon.getClass().getClassLoader());
        assertFalse(addon.getClass() == SampleJarAddon.class, "must not come from the parent");
        // own resources first: the jar's descriptor, not another one on the class path
        assertTrue(new String(addon.getClass().getClassLoader().getResourceAsStream(JarAddonLoader.DESCRIPTOR).readAllBytes(),
                StandardCharsets.UTF_8).contains("\"sample\""));
    }

    @Test
    void brokenJarsBecomeFailedEntriesWithAReason() throws IOException {
        jar(addons, "a-nothing.jar", Map.of("readme.txt", new byte[]{1}));
        jar(addons, "b-fabric.jar", Map.of("fabric.mod.json", "{}".getBytes(StandardCharsets.UTF_8)));
        Files.createDirectories(addons);
        Files.write(addons.resolve("c-corrupt.jar"), new byte[]{1, 2, 3, 4, 5});
        jar(addons, "d-invalid.jar", Map.of(JarAddonLoader.DESCRIPTOR, "{\"id\":\"X\"}".getBytes(StandardCharsets.UTF_8)));
        jar(addons, "e-protected.jar", Map.of(JarAddonLoader.DESCRIPTOR,
                descriptor("evil", "net.minecraft.client.Evil", "").getBytes(StandardCharsets.UTF_8)));
        List<AddonCandidate> found = loader().discover();
        assertEquals(5, found.size());
        found.forEach(c -> assertNull(c.descriptor(), c.source()));
        assertTrue(found.get(0).error().contains("Enthält keine"), found.get(0).error());
        assertTrue(found.get(1).error().contains("Fabric-Mod"), found.get(1).error());
        assertTrue(found.get(2).error().contains("Keine gültige Jar-Datei"), found.get(2).error());
        assertTrue(found.get(3).error().contains("Addon-ID"), found.get(3).error());
        assertTrue(found.get(4).error().contains("geschützten Paket"), found.get(4).error());
        assertEquals(0, loader.loaderCount());
    }

    @Test
    void protectedPackages() {
        assertTrue(AddonClassLoader.isProtected("net.minecraft.client.Minecraft"));
        assertTrue(AddonClassLoader.isProtected("dev.lumora.core.LumoraCore"));
        assertTrue(AddonClassLoader.isProtected("dev.lumora.api.Lumora"));
        assertTrue(AddonClassLoader.isProtected("net.fabricmc.loader.api.FabricLoader"));
        assertTrue(AddonClassLoader.isProtected("java.lang.String"));
        assertFalse(AddonClassLoader.isProtected("dev.lumora.testaddon.SampleJarAddon"));
        assertFalse(AddonClassLoader.isProtected("com.example.MyAddon"));
    }

    @Test
    void anAddonSeesTheClassesOfItsDependencies() throws Exception {
        jar(addons, "greeter.jar", Map.of(
                JarAddonLoader.DESCRIPTOR, descriptor("greeter", GreeterAddon.class.getName(), "").getBytes(StandardCharsets.UTF_8),
                path(GreeterAddon.class), classBytes(GreeterAddon.class),
                path(Greeter.class), classBytes(Greeter.class)));
        jar(addons, "user.jar", Map.of(
                JarAddonLoader.DESCRIPTOR, descriptor("user", UserAddon.class.getName(), "\"depends\":{\"greeter\":\">=1.0.0\"}")
                        .getBytes(StandardCharsets.UTF_8),
                path(UserAddon.class), classBytes(UserAddon.class)));
        FakePlatform p = new FakePlatform(dir.resolve("config"));
        p.addons = addons;
        p.addonParent = new Hiding(getClass().getClassLoader());
        core = new LumoraCore(p);
        core.loadAddons(List.of());
        AddonInfo user = core.addons().stream().filter(a -> a.id().equals("user")).findFirst().orElseThrow();
        assertEquals(AddonState.ENABLED, user.state(), user.error().orElse(""));
        assertEquals(List.of("user:hi from lumora-addon-greeter"), TestSink.EVENTS);
    }

    @Test
    void withoutDeclaringTheDependencyTheClassIsNotVisible() throws Exception {
        jar(addons, "greeter.jar", Map.of(
                JarAddonLoader.DESCRIPTOR, descriptor("greeter", GreeterAddon.class.getName(), "").getBytes(StandardCharsets.UTF_8),
                path(GreeterAddon.class), classBytes(GreeterAddon.class),
                path(Greeter.class), classBytes(Greeter.class)));
        jar(addons, "user.jar", Map.of(
                JarAddonLoader.DESCRIPTOR, descriptor("user", UserAddon.class.getName(), "").getBytes(StandardCharsets.UTF_8),
                path(UserAddon.class), classBytes(UserAddon.class)));
        FakePlatform p = new FakePlatform(dir.resolve("config"));
        p.addons = addons;
        p.addonParent = new Hiding(getClass().getClassLoader());
        core = new LumoraCore(p);
        core.loadAddons(List.of());
        AddonInfo user = core.addons().stream().filter(a -> a.id().equals("user")).findFirst().orElseThrow();
        assertEquals(AddonState.FAILED, user.state());
        assertTrue(user.error().orElse("").contains("Greeter"), user.error().orElse(""));
        AddonInfo greeter = core.addons().stream().filter(a -> a.id().equals("greeter")).findFirst().orElseThrow();
        assertEquals(AddonState.ENABLED, greeter.state(), "one broken addon must not affect another");
    }

    @Test
    void runsThroughTheCoreAndReleasesTheJarOnStop() throws Exception {
        Path file = sampleJar();
        FakePlatform p = new FakePlatform(dir.resolve("config"));
        p.addons = addons;
        p.addonParent = new Hiding(getClass().getClassLoader());
        core = new LumoraCore(p);
        core.loadAddons(List.of());
        AddonInfo sample = core.addons().stream().filter(a -> a.id().equals("sample")).findFirst().orElseThrow();
        assertEquals(AddonState.ENABLED, sample.state(), sample.error().orElse(""));
        assertEquals("enable:lumora-addon-sample:Hello %s", TestSink.EVENTS.get(0), "translation from the jar (no args)");
        core.stopping();
        core = null;
        assertTrue(TestSink.EVENTS.contains("disable:sample"));
        Files.delete(file); // jar closed: not locked any more (matters on Windows)
    }

    @Test
    void duplicateIdBetweenJarAndModAddonFails() throws Exception {
        sampleJar();
        FakePlatform p = new FakePlatform(dir.resolve("config"));
        p.addons = addons;
        p.addonParent = new Hiding(getClass().getClassLoader());
        core = new LumoraCore(p);
        dev.lumora.api.addon.AddonDescriptor mod = AddonDescriptorParser.parse(descriptor("sample", "x.Y", ""), "sample (Fabric mod)");
        core.loadAddons(List.of(AddonCandidate.of(mod, () -> ctx -> {
        })));
        List<AddonInfo> samples = core.addons().stream().filter(a -> a.id().equals("sample")).toList();
        assertEquals(2, samples.size());
        assertEquals(AddonState.ENABLED, samples.stream().filter(a -> a.descriptor().source().startsWith("sample (")).findFirst()
                .orElseThrow().state(), "the first one (the mod) keeps the id");
        AddonInfo jarOne = samples.stream().filter(a -> a.descriptor().source().startsWith("addons/")).findFirst().orElseThrow();
        assertEquals(AddonState.FAILED, jarOne.state());
        assertTrue(jarOne.error().orElse("").contains("bereits vergeben"), jarOne.error().orElse(""));
        assertTrue(TestSink.EVENTS.isEmpty(), "the duplicate jar never runs");
    }

    @Test
    void translationsFromTheJar() throws IOException {
        Path file = sampleJar();
        String[] lang = {"de_de"};
        Translations game = new Translations() {
            @Override
            public String translate(String key, Object... args) {
                return key.equals("game.key") ? "from game" : key;
            }

            @Override
            public boolean has(String key) {
                return key.equals("game.key") || key.equals("sample.hello") && lang[0].equals("fr_fr");
            }

            @Override
            public String language() {
                return lang[0];
            }
        };
        AddonTranslations t = new AddonTranslations(game, file);
        assertEquals("Hallo Lumora", t.translate("sample.hello", "Lumora"));
        assertEquals("only english", t.translate("sample.only_en"), "falls back to en_us");
        assertEquals("from game", t.translate("game.key"), "game keys first (resource packs can override)");
        assertEquals("missing.key", t.translate("missing.key"));
        assertTrue(t.has("sample.hello"));
        assertFalse(t.has("missing.key"));
        lang[0] = "de_DE";
        assertEquals("Alt", t.translate("sample.legacy"), "legacy .lang, language code case-insensitive");
        lang[0] = "fr_fr";
        assertEquals("sample.hello", t.translate("sample.hello"), "the game's own value wins");
    }

    @Test
    void entryPointMustBeALumoraAddon() throws Exception {
        jar(addons, "lib.jar", Map.of(
                JarAddonLoader.DESCRIPTOR, descriptor("libonly", Greeter.class.getName(), "").getBytes(StandardCharsets.UTF_8),
                path(Greeter.class), classBytes(Greeter.class)));
        AddonCandidate c = loader().discover().get(0);
        assertNotNull(c.descriptor());
        assertThrows(ClassCastException.class, () -> c.factory().create());
    }
}
