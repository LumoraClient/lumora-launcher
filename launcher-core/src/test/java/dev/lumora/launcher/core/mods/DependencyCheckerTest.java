package dev.lumora.launcher.core.mods;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyCheckerTest {
    static final Map<String, String> ENV = Map.of("minecraft", "26.1.2", "fabricloader", "0.19.5", "java", "25");

    @TempDir
    Path tmp;

    static byte[] jar(String modJson, Map<String, byte[]> nested) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry("fabric.mod.json"));
            z.write(modJson.getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            for (var e : nested.entrySet()) {
                z.putNextEntry(new ZipEntry("META-INF/jars/" + e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    FabricModMetadata mod(String json) throws IOException {
        return mod(json, Map.of());
    }

    FabricModMetadata mod(String json, Map<String, byte[]> nested) throws IOException {
        Path p = Files.createTempFile(tmp, "mod", ".jar");
        Files.write(p, jar(json, nested));
        return FabricModMetadata.read(p);
    }

    @Test
    void detectsSodiumVulkanModConflictFromBreaks() throws IOException {
        var vulkan = mod("""
                {"id":"vulkanmod","version":"0.6.8+26.1.2","depends":{"fabricloader":">=0.18.6","minecraft":">=26.1"}}""");
        var sodium = mod("""
                {"id":"sodium","version":"0.9.2+mc26.1.2","depends":{"minecraft":"26.1.2"},"breaks":{"vulkanmod":"*","iris":"<=1.11.3"}}""");
        var problems = DependencyChecker.check(List.of(vulkan, sodium), ENV);
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.getFirst().message().contains("incompatible with 'vulkanmod'"));
        assertTrue(DependencyChecker.check(List.of(vulkan), ENV).isEmpty());
        assertTrue(DependencyChecker.check(List.of(sodium), ENV).isEmpty());
    }

    @Test
    void breaksWithVersionRangeOnlyTriggersInRange() throws IOException {
        var sodium = mod("""
                {"id":"sodium","version":"0.9.2","breaks":{"fabric-api":"<0.145.1"}}""");
        var oldApi = mod("{\"id\":\"fabric-api\",\"version\":\"0.140.0+26.1\"}");
        var newApi = mod("{\"id\":\"fabric-api\",\"version\":\"0.155.3+26.1.2\"}");
        assertEquals(1, DependencyChecker.check(List.of(sodium, oldApi), ENV).size());
        assertTrue(DependencyChecker.check(List.of(sodium, newApi), ENV).isEmpty());
    }

    @Test
    void missingAndWrongVersionDependencies() throws IOException {
        var vfp = mod("""
                {"id":"viafabricplus","version":"4.5.6","depends":{"fabric-networking-api-v1":">=3.1.1",
                 "minecraft":">=26.1 <=26.1.2","java":">=25"}}""");
        var problems = DependencyChecker.check(List.of(vfp), ENV);
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().message().contains("fabric-networking-api-v1"));

        var wrongMc = DependencyChecker.check(List.of(vfp), Map.of("minecraft", "26.2", "java", "25"));
        assertTrue(wrongMc.stream().anyMatch(p -> p.message().contains("'minecraft'") && p.message().contains("26.2")));
    }

    @Test
    void nestedJarsAndProvidesSatisfyDependencies() throws IOException {
        byte[] nested = jar("{\"id\":\"fabric-networking-api-v1\",\"version\":\"6.3.1+554860db4c\"}", Map.of());
        var vfp = mod("""
                {"id":"viafabricplus","version":"4.5.6","provides":["viaversion"],
                 "depends":{"fabric-networking-api-v1":">=3.1.1"}}""", Map.of("net.jar", nested));
        var user = mod("{\"id\":\"addon\",\"version\":\"1.0\",\"depends\":{\"viaversion\":\"*\"}}");
        assertTrue(DependencyChecker.check(List.of(vfp, user), ENV).isEmpty());
        assertEquals(1, vfp.nested().size());
    }

    @Test
    void alternativesAreOred() throws IOException {
        var m = mod("{\"id\":\"x\",\"version\":\"1\",\"depends\":{\"minecraft\":[\"26.1\",\"26.1.2\"]}}");
        assertTrue(DependencyChecker.check(List.of(m), ENV).isEmpty());
    }

    @Test
    void duplicateTopLevelModIsReported() throws IOException {
        var a = mod("{\"id\":\"vulkanmod\",\"version\":\"0.6.7\"}");
        var b = mod("{\"id\":\"vulkanmod\",\"version\":\"0.6.8\"}");
        assertFalse(DependencyChecker.check(List.of(a, b), ENV).isEmpty());
    }

    @Test
    void nonFabricJarIsRejected() throws IOException {
        Path p = tmp.resolve("plain.jar");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry("a.txt"));
            z.closeEntry();
        }
        Files.write(p, bos.toByteArray());
        assertThrows(IOException.class, () -> FabricModMetadata.read(p));
    }

    @Test
    void versionPredicates() {
        assertTrue(VersionPredicate.parse(">=0.18.6").test("0.19.5"));
        assertFalse(VersionPredicate.parse(">=0.18.6").test("0.18.5"));
        assertTrue(VersionPredicate.parse(">=26.1 <=26.1.2").test("26.1.1"));
        assertFalse(VersionPredicate.parse(">=26.1 <=26.1.2").test("26.2"));
        assertTrue(VersionPredicate.parse("~26.1.2").test("26.1.5"));
        assertFalse(VersionPredicate.parse("~26.1.2").test("26.2.0"));
        assertTrue(VersionPredicate.parse("^1.2.3").test("1.9.0"));
        assertFalse(VersionPredicate.parse("^1.2.3").test("2.0.0"));
        assertTrue(VersionPredicate.parse("1.21.x").test("1.21.11"));
        assertTrue(VersionPredicate.parse("*").test("anything"));
        assertTrue(VersionPredicate.parse("26.1.2").test("26.1.2"));
        assertTrue(VersionPredicate.parse("<0.145.1").test("0.140.0+26.1"));
        assertTrue(VersionPredicate.parse(">=25").test("25"));
    }

    @Test
    void semanticVersionOrdering() {
        List<String> ordered = List.of("0.9.2-alpha.1", "0.9.2-beta.1", "0.9.2-beta.2", "0.9.2", "0.9.2+mc26.1.2", "0.9.10");
        List<ModVersion> parsed = new ArrayList<>(ordered.stream().map(ModVersion::parse).toList());
        for (int i = 0; i < parsed.size() - 1; i++) {
            assertTrue(parsed.get(i).compareTo(parsed.get(i + 1)) <= 0, ordered.get(i) + " <= " + ordered.get(i + 1));
        }
        assertEquals(0, ModVersion.parse("0.9.2").compareTo(ModVersion.parse("0.9.2+build")));
    }

    @Test
    void mixinExtrasShippedWithTheLoaderSatisfiesDependencies() throws IOException {
        var loader = mod("""
                {"id":"fabricloader","version":"0.19.5"}""", Map.of("mixinextras-fabric-0.5.5.jar", jar("""
                {"id":"mixinextras","version":"0.5.5"}""", Map.of())));
        var lithium = mod("""
                {"id":"lithium","version":"0.26.2+mc26.3","depends":{"mixinextras":">=0.5.5"}}""");
        assertEquals(Map.of("mixinextras", "0.5.5"), DependencyChecker.bundledWith(loader));
        assertEquals(1, DependencyChecker.check(List.of(lithium), ENV).size());
        Map<String, String> env = new HashMap<>(ENV);
        env.putAll(DependencyChecker.bundledWith(loader));
        assertTrue(DependencyChecker.check(List.of(lithium), env).isEmpty());
    }
}
