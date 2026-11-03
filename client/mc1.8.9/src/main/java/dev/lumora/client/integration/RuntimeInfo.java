package dev.lumora.client.integration;

import org.lwjgl.opengl.GL11;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only status of the integrated components on Minecraft 1.8.9. The 1.8.9 profile uses ViaFabric (no public API for
 * the target version) instead of ViaFabricPlus, so the ViaFabricPlus methods of the newer modules report "not available".
 */
public final class RuntimeInfo {
    /** This module is built for exactly one Minecraft version (1.8.9 has no SharedConstants version object). */
    public static final String MINECRAFT_VERSION = "1.8.9";

    private RuntimeInfo() {
    }

    public static Optional<String> modVersion(String id) {
        return dev.lumora.client.platform.LoaderEnv.version(id);
    }

    public static boolean sodiumLoaded() {
        return false;
    }

    public static boolean viaFabricPlusLoaded() {
        return false;
    }

    public static boolean viaFabricLoaded() {
        return dev.lumora.client.platform.LoaderEnv.isLoaded("viafabric");
    }

    /** Renderer as reported by the OpenGL context. Render thread only. */
    public static Map<String, String> gpuDevice() {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            out.put("backend", "OpenGL");
            out.put("renderer", GL11.glGetString(GL11.GL_RENDERER));
            out.put("vendor", GL11.glGetString(GL11.GL_VENDOR));
            out.put("version", GL11.glGetString(GL11.GL_VERSION));
        } catch (RuntimeException | LinkageError e) {
            out.put("error", e.toString());
        }
        return out;
    }

    /** ViaFabricPlus target version: not available on 1.8.9. */
    public static String vfpTargetVersion() {
        return null;
    }

    /** ViaFabricPlus protocol screen: not available on 1.8.9. */
    public static boolean openVfpProtocolSelection(net.minecraft.client.gui.screen.Screen parent) {
        return false;
    }

    public static void registerVfpLogging() {
    }
}
