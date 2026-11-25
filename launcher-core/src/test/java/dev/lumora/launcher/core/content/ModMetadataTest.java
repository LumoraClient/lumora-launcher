package dev.lumora.launcher.core.content;

import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.platform.ProjectType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModMetadataTest {
    @TempDir
    Path tmp;

    static byte[] zipBytes(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    Path jar(String name, Map<String, byte[]> entries) throws IOException {
        Path p = tmp.resolve("mods").resolve(name);
        Files.createDirectories(p.getParent());
        try (OutputStream os = Files.newOutputStream(p)) {
            os.write(zipBytes(entries));
        }
        return p;
    }

    static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void readsFabricWithBundledJars() throws IOException {
        byte[] nested = zipBytes(Map.of("fabric.mod.json", b("{\"id\":\"cloth-config\"}")));
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("fabric.mod.json", b("""
                {"id":"modmenu","name":"Mod Menu","version":"13.0","description":"Mods","authors":["Prospector",{"name":"Terra"}],
                 "icon":{"64":"assets/icon64.png","256":"assets/icon256.png"},"depends":{"fabric-api":"*","minecraft":">=1.21"},
                 "breaks":{"optifabric":"*"},"jars":[{"file":"META-INF/jars/cloth.jar"}],"provides":["menu"]}"""));
        e.put("assets/icon64.png", new byte[]{1, 2, 3});
        e.put("META-INF/jars/cloth.jar", nested);
        ModMetadata m = ModMetadata.read(jar("modmenu.jar", e));
        assertEquals("modmenu", m.mainId());
        assertEquals(List.of("modmenu", "menu", "cloth-config"), m.ids(), "own id, provides, bundled jars");
        assertEquals("Mod Menu", m.name());
        assertEquals(List.of("Prospector", "Terra"), m.authors());
        assertEquals(Set.of("fabric"), m.loaders());
        assertEquals(Set.of("fabric-api", "minecraft"), m.depends().keySet());
        assertEquals(Set.of("optifabric"), m.breaks());
        assertEquals(3, m.icon().length, "largest icon up to 128 px");
    }

    @Test
    void readsForgeAndNeoForgeToml() throws IOException {
        String toml = """
                modLoader="javafml" # comment
                loaderVersion="[47,)"
                license="MIT"
                [[mods]]
                modId="jei"
                version="${file.jarVersion}"
                displayName="Just Enough Items"
                authors="mezz, others"
                description='''
                Item list
                and recipes'''
                [[dependencies.jei]]
                    modId="forge"
                    mandatory=true
                    versionRange="[47,)"
                [[dependencies.jei]]
                    modId="architectury"
                    type="required"
                [[dependencies.jei]]
                    modId="rei"
                    type="incompatible"
                """;
        ModMetadata forge = ModMetadata.read(jar("jei.jar", Map.of("META-INF/mods.toml", b(toml),
                "META-INF/MANIFEST.MF", b("Manifest-Version: 1.0\nImplementation-Version: 15.2.0\n"))));
        assertEquals("jei", forge.mainId());
        assertEquals("15.2.0", forge.version(), "${file.jarVersion} from the manifest");
        assertEquals("Just Enough Items", forge.name());
        assertEquals(List.of("mezz", "others"), forge.authors());
        assertEquals("Item list\nand recipes", forge.description());
        assertEquals(Set.of("forge"), forge.loaders());
        assertTrue(forge.depends().containsKey("architectury"));
        assertEquals(Set.of("rei"), forge.breaks());
        ModMetadata neo = ModMetadata.read(jar("neo.jar", Map.of("META-INF/neoforge.mods.toml", b(toml))));
        assertEquals(Set.of("neoforge"), neo.loaders());
        assertNull(ModMetadata.read(jar("lib.jar", Map.of("a/B.class", new byte[]{0}))), "no metadata: not a mod");
    }

    @Test
    void readsQuiltAndOldMcmodInfo() throws IOException {
        ModMetadata q = ModMetadata.read(jar("q.jar", Map.of("quilt.mod.json", b("""
                {"schema_version":1,"quilt_loader":{"id":"qmod","version":"1.0","metadata":{"name":"Q Mod","contributors":{"Me":"Owner"}},
                 "depends":["quilt_loader",{"id":"qsl","versions":">=7"},{"id":"opt","optional":true}],"provides":[{"id":"qalias"}]}}"""))));
        assertEquals(List.of("qmod", "qalias"), q.ids());
        assertEquals(Set.of("quilt_loader", "qsl"), q.depends().keySet(), "optional dependencies are not required");
        ModMetadata old = ModMetadata.read(jar("o.jar", Map.of("mcmod.info", b("[{\"modid\":\"oldmod\",\"name\":\"Old\",\"version\":\"1.0\"}]"))));
        assertEquals("oldmod", old.mainId());
        assertEquals(Set.of("forge"), old.loaders());
    }

    @Test
    void conflictsAreFoundAmongTheSwitchedOnMods() throws IOException {
        jar("a.jar", Map.of("fabric.mod.json", b("{\"id\":\"a\",\"name\":\"A\",\"depends\":{\"fabric-api\":\"*\",\"b\":\"*\",\"fabric-lifecycle-events-v1\":\"*\"}}")));
        jar("a2.jar", Map.of("fabric.mod.json", b("{\"id\":\"a\",\"name\":\"A again\"}")));
        jar("fapi.jar", Map.of("fabric.mod.json", b("{\"id\":\"fabric-api\",\"name\":\"Fabric API\"}")));
        jar("c.jar", Map.of("fabric.mod.json", b("{\"id\":\"c\",\"name\":\"C\",\"breaks\":{\"fabric-api\":\"*\"}}")));
        jar("f.jar", Map.of("META-INF/mods.toml", b("[[mods]]\nmodId=\"f\"\n")));
        List<InstalledContent.Item> mods = InstalledContent.scan(tmp, ProjectType.MOD, null);
        List<ConflictDetector.Problem> problems = ConflictDetector.detect(InstanceKind.FABRIC, mods);
        assertEquals(Set.of(ConflictDetector.Kind.DUPLICATE, ConflictDetector.Kind.MISSING_DEPENDENCY, ConflictDetector.Kind.INCOMPATIBLE,
                ConflictDetector.Kind.WRONG_LOADER), Set.copyOf(problems.stream().map(ConflictDetector.Problem::kind).toList()));
        assertEquals(1, problems.stream().filter(p -> p.kind() == ConflictDetector.Kind.MISSING_DEPENDENCY).count(),
                "only b is missing: Fabric API modules count as present with Fabric API");
        assertTrue(ConflictDetector.detect(InstanceKind.VANILLA, mods).isEmpty());
    }
}
