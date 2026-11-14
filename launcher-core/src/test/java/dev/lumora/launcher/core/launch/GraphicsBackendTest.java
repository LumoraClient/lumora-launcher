package dev.lumora.launcher.core.launch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphicsBackendTest {
    @TempDir
    Path dir;

    @Test
    void onlyFrom26Point2() {
        assertTrue(GraphicsBackend.supported("26.2"));
        assertTrue(GraphicsBackend.supported("26.3"));
        assertTrue(GraphicsBackend.supported("27.1"));
        assertFalse(GraphicsBackend.supported("26.1.2"));
        assertFalse(GraphicsBackend.supported("1.21.11"));
        assertFalse(GraphicsBackend.supported(null));
    }

    @Test
    void writesTheChoiceAndLeavesTheRest() throws Exception {
        Files.writeString(dir.resolve("options.txt"), "version:4800\npreferredGraphicsBackend:\"default\"\nfov:0.5\n");
        assertTrue(GraphicsBackend.apply(dir, "26.3", "vulkan"));
        assertEquals("version:4800\npreferredGraphicsBackend:\"vulkan\"\nfov:0.5\n", Files.readString(dir.resolve("options.txt")));
        assertFalse(GraphicsBackend.apply(dir, "26.3", "vulkan"), "unchanged: no write");
    }

    @Test
    void defaultOrOldVersionsTouchNothing() throws Exception {
        assertFalse(GraphicsBackend.apply(dir, "26.3", "default"));
        assertFalse(GraphicsBackend.apply(dir, "26.1.2", "opengl"));
        assertFalse(Files.exists(dir.resolve("options.txt")));
        // first start of a version: options.txt is created with just the backend, Minecraft fills in the rest
        assertTrue(GraphicsBackend.apply(dir, "26.2", "opengl"));
        assertEquals("preferredGraphicsBackend:\"opengl\"\n", Files.readString(dir.resolve("options.txt")));
    }
}
