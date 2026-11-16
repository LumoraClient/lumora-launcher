package dev.lumora.core.perf;

import java.util.EnumSet;
import java.util.Set;

/**
 * Central switchboard of Lumora's performance optimizations. The version adapter declares which optimizations it
 * implements ({@link #declareSupported}); the "Performance" module switches them on and off ({@link #set}). Hot paths
 * only read the public flags below (one field read, no lookup); an optimization the running Minecraft version does not
 * support always stays off.
 */
public final class PerformanceEngine {
    /** {@link Optimization#ENTITY_CULLING} active (read by the entity and block entity render hooks). */
    public static volatile boolean entityCulling;
    /** {@link Optimization#RENDERING} active. */
    public static volatile boolean rendering;
    /** {@link Optimization#SMART_HUD} active. */
    public static volatile boolean smartHud;
    /** {@link Optimization#FRAME} active. */
    public static volatile boolean frame;
    /** {@link Optimization#CLOUDS} active. */
    public static volatile boolean clouds;
    /** {@link Optimization#STARTUP} active. */
    public static volatile boolean startup;

    private static final Set<Optimization> SUPPORTED = EnumSet.noneOf(Optimization.class);
    private static OcclusionCuller culler;

    private PerformanceEngine() {
    }

    /** Called once by the core with what the version adapter implements; everything else stays off. */
    public static synchronized void declareSupported(Set<Optimization> supported) {
        SUPPORTED.clear();
        SUPPORTED.addAll(supported);
        for (Optimization o : Optimization.values()) {
            if (!SUPPORTED.contains(o)) {
                write(o, false);
            }
        }
    }

    public static synchronized boolean isSupported(Optimization o) {
        return SUPPORTED.contains(o);
    }

    /** Switches an optimization; a request for an unsupported one is ignored (it stays off). */
    public static synchronized void set(Optimization o, boolean on) {
        boolean value = on && SUPPORTED.contains(o);
        boolean before = isActive(o);
        write(o, value);
        if (o == Optimization.ENTITY_CULLING && before != value && culler != null) {
            culler.switched(value);
        }
    }

    /** All optimizations off (module switched off or the client stops). */
    public static synchronized void allOff() {
        for (Optimization o : Optimization.values()) {
            set(o, false);
        }
    }

    public static boolean isActive(Optimization o) {
        return switch (o) {
            case ENTITY_CULLING -> entityCulling;
            case RENDERING -> rendering;
            case SMART_HUD -> smartHud;
            case FRAME -> frame;
            case CLOUDS -> clouds;
            case STARTUP -> startup;
        };
    }

    private static void write(Optimization o, boolean value) {
        switch (o) {
            case ENTITY_CULLING -> entityCulling = value;
            case RENDERING -> rendering = value;
            case SMART_HUD -> smartHud = value;
            case FRAME -> frame = value;
            case CLOUDS -> clouds = value;
            case STARTUP -> startup = value;
        }
    }

    /** The occlusion culler of this client, or {@code null} if the adapter has no entity culling. */
    public static OcclusionCuller culler() {
        return culler;
    }

    public static synchronized void culler(OcclusionCuller c) {
        if (culler != null && culler != c) {
            culler.stop();
        }
        culler = c;
    }
}
