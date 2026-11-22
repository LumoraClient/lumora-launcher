package dev.lumora.launcher.core.crash;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Crash analysis: known causes, the advice for them and the fix the launcher can do itself. */
class CrashDiagnosisTest {
    static final CrashDiagnosis.Context NEW = new CrashDiagnosis.Context("26.3", 4096, 16384, "default");
    static final CrashDiagnosis.Context OLD = new CrashDiagnosis.Context("1.21.11", 4096, 16384, "default");

    static CrashReport report(CrashReport.Source source, CrashReport.Suspect... suspects) {
        return new CrashReport("26.3", Path.of("crash.txt"), source, Instant.now(), "Unexpected error", null, null,
                List.of(suspects), List.of());
    }

    static CrashDiagnosis.Result diagnose(String text, CrashDiagnosis.Context ctx, CrashReport.Suspect... suspects) {
        return CrashDiagnosis.diagnose(report(CrashReport.Source.MINECRAFT, suspects), text, ctx);
    }

    @Test
    void memory() {
        CrashDiagnosis.Result r = diagnose("java.lang.OutOfMemoryError: Java heap space", NEW);
        assertEquals("memory", r.key());
        assertEquals(CrashDiagnosis.Action.MORE_MEMORY, r.fix().action());
        assertEquals(6144, r.fix().memoryMb(), "2 GB more");
        assertTrue(r.steps().get(0).contains("6.0 GB"), r.steps().get(0));
        assertTrue(r.evidence().contains("OutOfMemoryError"));

        // already as much as makes sense on this computer: no button, other advice
        CrashDiagnosis.Result full = diagnose("java.lang.OutOfMemoryError", new CrashDiagnosis.Context("26.3", 4096, 6000, "default"));
        assertEquals("memory_full", full.key());
        assertEquals(CrashDiagnosis.Action.NONE, full.fix().action());

        // the computer itself ran out of memory: less for Minecraft
        CrashDiagnosis.Result sys = diagnose("# Native memory allocation (malloc) failed to allocate 1048576 bytes", NEW);
        assertEquals("system_memory", sys.key());
        assertEquals(CrashDiagnosis.Action.LESS_MEMORY, sys.fix().action());
        assertEquals(3072, sys.fix().memoryMb());
    }

    @Test
    void graphics() {
        String driver = "# Problematic frame:\n# C  [nvoglv64.dll+0x1234]";
        CrashDiagnosis.Result switchable = diagnose(driver, NEW);
        assertEquals("driver_switch", switchable.key());
        assertEquals("vulkan", switchable.fix().backend(), "26.2+ can try Vulkan");
        CrashDiagnosis.Result old = diagnose(driver, OLD);
        assertEquals("driver", old.key());
        assertEquals(CrashDiagnosis.Action.NONE, old.fix().action(), "older versions: only a driver update helps");

        CrashDiagnosis.Result vk = diagnose("java.lang.IllegalStateException: VK_ERROR_DEVICE_LOST", NEW);
        assertEquals("vulkan", vk.key());
        assertEquals("opengl", vk.fix().backend());
        assertEquals(CrashDiagnosis.Action.GRAPHICS_BACKEND, vk.fix().action());
    }

    @Test
    void filesJavaAndSignIn() {
        assertEquals(CrashDiagnosis.Action.REPAIR, diagnose("java.util.zip.ZipException: invalid LOC header", NEW).fix().action());
        assertEquals("java", diagnose("java.lang.UnsupportedClassVersionError: has been compiled by a more recent version of the Java Runtime", NEW).key());
        assertEquals(CrashDiagnosis.Action.SIGN_IN, diagnose("Failed to verify username!", NEW).fix().action());
    }

    @Test
    void modsAndLumora() {
        CrashReport.Suspect own = new CrashReport.Suspect("coolmod", "Cool Mod", Path.of("mods/coolmod.jar"),
                "3 lines in the stack trace come from this mod", 30);
        CrashDiagnosis.Result mod = diagnose("java.lang.NoSuchMethodError: 'void net.minecraft.Foo.bar()'", NEW, own);
        assertEquals("mod_mismatch", mod.key());
        assertEquals(CrashDiagnosis.Action.DISABLE_SUSPECT, mod.fix().action());
        assertEquals("Cool Mod", mod.fix().suspect().name());
        assertTrue(mod.what().contains("Cool Mod"), mod.what());

        // Lumora's own mixins named in the report: repair, and a hint that it may be a Lumora bug
        CrashReport.Suspect lumora = new CrashReport.Suspect("lumora", "Lumora Client", Path.of("lumora.jar"),
                "Mixin configuration \"lumora.mixins.json\" is named in the report", 50);
        CrashDiagnosis.Result l = diagnose("java.lang.NullPointerException: Cannot invoke \"java.util.List.getFirst()\"", NEW, lumora);
        assertEquals("lumora_mixin", l.key());
        assertEquals(CrashDiagnosis.Action.REPAIR, l.fix().action());

        CrashDiagnosis.Result world = diagnose("Description: Exception ticking world", NEW);
        assertEquals(CrashDiagnosis.Action.OPEN_LIBRARY, world.fix().action());
    }

    @Test
    void unknownAndNoReport() {
        CrashDiagnosis.Result exit = CrashDiagnosis.diagnose(report(CrashReport.Source.LAUNCHER), "Exit code: -1073740791", NEW);
        assertEquals("exit", exit.key());
        CrashDiagnosis.Result unknown = diagnose("java.lang.RuntimeException: something", NEW);
        assertEquals("unknown", unknown.key());
        assertEquals(CrashDiagnosis.Action.REPAIR, unknown.fix().action());
        for (CrashDiagnosis.Result r : List.of(exit, unknown)) {
            assertFalse(r.what().startsWith("crash.analysis"), "text from the language file: " + r.what());
            assertTrue(r.steps().size() >= 2);
        }
    }

    @Test
    void readsTheJvmCrashLogOfTheSameTime(@TempDir Path game) throws Exception {
        Path crash = Files.createDirectories(game.resolve("crash-reports")).resolve("lumora-exit-1.txt");
        Files.writeString(crash, "Exit code: 1");
        Files.writeString(game.resolve("hs_err_pid42.log"), "# EXCEPTION_ACCESS_VIOLATION\n# C  [atio6axx.dll+0x99]");
        CrashReport r = new CrashReport("1.21.11", crash, CrashReport.Source.LAUNCHER, Instant.now(), "x", null, 1, List.of(), List.of());
        CrashDiagnosis.Result res = CrashDiagnosis.analyze(r, game, OLD);
        assertEquals("driver", res.key(), "the AMD driver in the hs_err log of the same time");
    }
}
