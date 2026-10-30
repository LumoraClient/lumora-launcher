package dev.lumora.launcher.core.java;

import dev.lumora.launcher.core.util.OsInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaRuntimeInstallerTest {
    /** Structure of Mojang's real all.json (trimmed). */
    static final String ALL_JSON = """
            {"windows-x64":{"java-runtime-epsilon":[{"availability":{"group":6030,"progress":100},
              "manifest":{"sha1":"b374544c680d965fb5535977d7cb04c6befe1930","size":152664,
                "url":"https://piston-meta.mojang.com/v1/packages/b374544c680d965fb5535977d7cb04c6befe1930/manifest.json"},
              "version":{"name":"25.0.1","released":"2025-12-10T14:14:17+00:00"}}],"jre-legacy":[]},
             "linux":{"java-runtime-epsilon":[]}}
            """;

    @Test
    void selectsComponentForPlatform() throws IOException {
        var win = new JavaRuntimeInstaller(null, Path.of("rt"), new OsInfo(OsInfo.Name.WINDOWS, OsInfo.Arch.X86_64, "10"));
        JavaRuntimeInstaller.ComponentEntry e = win.selectComponent(ALL_JSON, "java-runtime-epsilon");
        assertEquals("b374544c680d965fb5535977d7cb04c6befe1930", e.manifest.sha1);
        assertEquals("25.0.1", e.version.name);
    }

    @Test
    void missingComponentOrPlatformIsAnError() {
        var win = new JavaRuntimeInstaller(null, Path.of("rt"), new OsInfo(OsInfo.Name.WINDOWS, OsInfo.Arch.X86_64, "10"));
        assertThrows(IOException.class, () -> win.selectComponent(ALL_JSON, "java-runtime-zeta"));
        var linux = new JavaRuntimeInstaller(null, Path.of("rt"), new OsInfo(OsInfo.Name.LINUX, OsInfo.Arch.X86_64, "6"));
        assertThrows(IOException.class, () -> linux.selectComponent(ALL_JSON, "java-runtime-epsilon"));
        assertThrows(IOException.class, () -> win.selectComponent("not json", "java-runtime-epsilon"));
    }
}
