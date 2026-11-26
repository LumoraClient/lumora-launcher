package dev.lumora.bridge;

import dev.lumora.loader.api.ClientEntrypoint;
import dev.lumora.loader.api.LoadedJar;
import dev.lumora.loader.api.LumoraLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What every mod loader bridge does: fill the Lumora Loader API ({@link LumoraLoader}) and start the client entry points
 * listed in the jar's {@code lumora.loader.json}, exactly once. The client's platform hooks are ordinary mixins that the
 * mod loader applies like any other mod's.
 */
public final class ClientStart {
    /** Mod id of the Lumora Client in every loader's metadata. */
    public static final String MOD_ID = "lumora";
    private static final Pattern ENTRYPOINTS = Pattern.compile("\"entrypoints\"\\s*:\\s*\\[([^\\]]*)\\]");
    private static final Pattern MINECRAFT = Pattern.compile("\"minecraft\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern NAME = Pattern.compile("\"([A-Za-z0-9_.$]+)\"");
    private static boolean started;

    private ClientStart() {
    }

    /** Fills {@link LumoraLoader} (later calls are ignored by it). Touches no Minecraft classes. */
    public static void initLoader(Path gameDir, String minecraft, List<LoadedJar> components) {
        LumoraLoader.init(gameDir, minecraft, components);
    }

    /** Starts the entry points of the {@code lumora.loader.json} next to this class, with {@code loader}. */
    public static synchronized void startClient(ClassLoader loader) {
        if (started) {
            return;
        }
        started = true;
        for (String name : entrypoints(descriptor())) {
            try {
                Object instance = Class.forName(name, true, loader).getDeclaredConstructor().newInstance();
                if (!(instance instanceof ClientEntrypoint entry)) {
                    throw new IllegalStateException(name + " does not implement " + ClientEntrypoint.class.getName());
                }
                entry.onInitializeClient();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot start " + name + ": " + e.getMessage(), e);
            }
        }
    }

    /** The {@code lumora.loader.json} of the Lumora jar this class is in. */
    public static String descriptor() {
        try (InputStream in = ClientStart.class.getResourceAsStream("/lumora.loader.json")) {
            if (in == null) {
                throw new IllegalStateException("lumora.loader.json missing in the Lumora Client jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read lumora.loader.json: " + e.getMessage(), e);
        }
    }

    /**
     * Class names of the {@code "entrypoints"} array. No JSON library: the old Minecraft versions ship Gson versions
     * without the parser methods used elsewhere, and the file is written by our own build.
     */
    public static List<String> entrypoints(String json) {
        Matcher m = ENTRYPOINTS.matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("no entrypoints");
        }
        List<String> out = new ArrayList<>();
        Matcher name = NAME.matcher(m.group(1));
        while (name.find()) {
            out.add(name.group(1));
        }
        return out;
    }

    /** The Minecraft version the jar was built for ({@code "minecraft"} in the descriptor), empty if missing. */
    public static String minecraft(String json) {
        Matcher m = MINECRAFT.matcher(json);
        return m.find() ? m.group(1) : "";
    }
}
