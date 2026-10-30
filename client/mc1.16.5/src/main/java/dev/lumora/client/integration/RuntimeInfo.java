package dev.lumora.client.integration;

import com.mojang.blaze3d.platform.GlUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only status of the integrated components. ViaFabricPlus is accessed through reflection on its
 * public classes (verified against the viafabricplus-api 4.5.6 jar), so this mod has no compile-time dependency on it and degrades
 * gracefully when one is absent or its API changes.
 */
public final class RuntimeInfo {
    private static final Logger LOG = LogManager.getLogger("Lumora");

    private static final String VFP_ENTRY = "com.viaversion.viafabricplus.ViaFabricPlus";
    private static final String VFP_BASE = "com.viaversion.viafabricplus.api.ViaFabricPlusBase";
    private static final String VFP_CALLBACK = "com.viaversion.viafabricplus.api.events.ChangeProtocolVersionCallback";

    private RuntimeInfo() {
    }

    public static Optional<String> modVersion(String id) {
        return FabricLoader.getInstance().getModContainer(id).map(ModContainer::getMetadata).map(m -> m.getVersion().getFriendlyString());
    }

    public static boolean sodiumLoaded() {
        return FabricLoader.getInstance().isModLoaded("sodium");
    }

    public static boolean viaFabricPlusLoaded() {
        return FabricLoader.getInstance().isModLoaded("viafabricplus");
    }

    /** Renderer as reported by the OpenGL context (Minecraft 1.16.5 has no GpuDevice abstraction yet). */
    public static Map<String, String> gpuDevice() {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            out.put("backend", "OpenGL");
            out.put("renderer", GlUtil.getRenderer());
            out.put("vendor", GlUtil.getVendor());
            out.put("version", GlUtil.getOpenGLVersion());
        } catch (RuntimeException | LinkageError e) {
            out.put("error", e.toString());
        }
        return out;
    }

    private static Object vfp() throws ReflectiveOperationException {
        return Class.forName(VFP_ENTRY).getMethod("getImpl").invoke(null);
    }

    /** e.g. "1.8.x" when ViaFabricPlus translates to an older server; null if unavailable. */
    public static String vfpTargetVersion() {
        if (!viaFabricPlusLoaded()) {
            return null;
        }
        try {
            Object impl = vfp();
            if (impl == null) {
                return null;
            }
            Object pv = Class.forName(VFP_BASE).getMethod("getTargetVersion").invoke(impl);
            return pv == null ? null : (String) pv.getClass().getMethod("getName").invoke(pv);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.debug("ViaFabricPlus target version unavailable", e);
            return null;
        }
    }

    /** Opens ViaFabricPlus' own protocol selection screen. Returns false if not possible. */
    public static boolean openVfpProtocolSelection(net.minecraft.client.gui.screens.Screen parent) {
        try {
            Object impl = vfp();
            if (impl == null) {
                return false;
            }
            Method m = Class.forName(VFP_BASE).getMethod("openProtocolSelectionScreen", net.minecraft.client.gui.screens.Screen.class);
            m.invoke(impl, parent);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("Could not open ViaFabricPlus protocol selection", e);
            return false;
        }
    }

    /** Logs every protocol change made in ViaFabricPlus (diagnostics). */
    public static void registerVfpLogging() {
        if (!viaFabricPlusLoaded()) {
            return;
        }
        try {
            Object impl = vfp();
            if (impl == null) {
                return;
            }
            Class<?> cb = Class.forName(VFP_CALLBACK);
            Object proxy = Proxy.newProxyInstance(RuntimeInfo.class.getClassLoader(), new Class<?>[]{cb}, (p, method, args) -> {
                if (method.getName().equals("onChangeProtocolVersion") && args != null && args.length == 2) {
                    LOG.info("ViaFabricPlus protocol changed: {} -> {}", args[0], args[1]);
                    return null;
                }
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == args[0];
                    case "toString" -> "LumoraVfpLogger";
                    default -> null;
                };
            });
            Class.forName(VFP_BASE).getMethod("registerOnChangeProtocolVersionCallback", cb).invoke(impl, proxy);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("Could not register ViaFabricPlus callback", e);
        }
    }
}
