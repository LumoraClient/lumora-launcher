package dev.lumora.launcher.core.update;

import dev.lumora.launcher.core.java.JavaProbeAccess;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {
    @Test
    void versionComparisonAcrossModrinthStyles() {
        assertTrue(UpdateChecker.isNewer("0.6.9+26.1.2", "0.6.8+26.1.2"));
        assertFalse(UpdateChecker.isNewer("0.6.8+26.1.2", "0.6.8+26.1.2"));
        assertTrue(UpdateChecker.isNewer("mc26.1.2-0.9.3-fabric", "0.9.2+mc26.1.2"));
        assertFalse(UpdateChecker.isNewer("mc26.1.2-0.9.2-fabric", "0.9.2+mc26.1.2"));
        assertTrue(UpdateChecker.isNewer("4.5.7", "4.5.6"));
    }

    @Test
    void parsesFirstReleaseWithPrimaryFile() throws IOException {
        ProfileCatalog.Artifact template = new ProfileCatalog.Artifact();
        template.id = "viafabricplus";
        template.name = "ViaFabricPlus";
        template.modrinthProjectId = "rIC2XJV4";
        String body = """
                [{"version_number":"4.5.7-beta","version_type":"beta","files":[]},
                 {"version_number":"4.5.7","version_type":"release","files":[
                   {"primary":false,"filename":"src.jar","url":"https://cdn.modrinth.com/a","size":1,"hashes":{"sha512":"x"}},
                   {"primary":true,"filename":"ViaFabricPlus-4.5.7.jar","url":"https://cdn.modrinth.com/b","size":42,"hashes":{"sha512":"%s"}}]}]
                """.formatted("c".repeat(128));
        ProfileCatalog.Artifact a = UpdateChecker.parseLatest(body, template);
        assertEquals("4.5.7", a.version);
        assertEquals("ViaFabricPlus-4.5.7.jar", a.fileName);
        assertEquals(42, a.size);
        assertNull(UpdateChecker.parseLatest("[]", template));
        assertThrows(IOException.class, () -> UpdateChecker.parseLatest("{\"not\":\"array\"}", template));
    }

    @Test
    void javaVersionParsing() throws IOException {
        assertEquals(25, JavaProbeAccess.parse("    java.specification.version = 25\n"));
        assertEquals(8, JavaProbeAccess.parse("    java.specification.version = 1.8\n"));
        assertThrows(IOException.class, () -> JavaProbeAccess.parse("nothing"));
    }
}
