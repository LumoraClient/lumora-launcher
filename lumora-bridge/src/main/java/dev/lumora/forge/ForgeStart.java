package dev.lumora.forge;

import dev.lumora.bridge.ClientStart;
import dev.lumora.loader.api.LoadedJar;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared start for Forge and NeoForge (both run Minecraft with Mojang's names from 1.20.6, like the Lumora Client).
 * Folders and the mod list come from FML through reflection, so the bridge needs neither loader at compile time; if
 * that fails, the game folder (the launcher starts the game in it) and the Lumora jar alone are used.
 */
final class ForgeStart {
    private ForgeStart() {
    }

    /** @param fml FML package of the loader: {@code net.minecraftforge.fml} or {@code net.neoforged.fml} */
    static void start(String fml, Class<?> anchor) {
        String descriptor = ClientStart.descriptor();
        Path gameDir = gameDir(fml);
        List<LoadedJar> components = new ArrayList<>(mods(fml));
        if (components.stream().noneMatch(c -> c.id().equals(ClientStart.MOD_ID))) {
            components.add(new LoadedJar(ClientStart.MOD_ID, "Lumora Client", "", ownJar(gameDir)));
        } else {
            // the Lumora entry needs its jar file (built-in resource packs are read from it)
            components.replaceAll(c -> c.id().equals(ClientStart.MOD_ID) && c.file() == null
                    ? new LoadedJar(c.id(), c.name(), c.version(), ownJar(gameDir)) : c);
        }
        ClientStart.initLoader(gameDir, ClientStart.minecraft(descriptor), components);
        ClientStart.startClient(anchor.getClassLoader());
    }

    private static Path gameDir(String fml) {
        try {
            Class<?> paths = Class.forName(fml + ".loading.FMLPaths");
            Object gameDir = paths.getField("GAMEDIR").get(null);
            return (Path) gameDir.getClass().getMethod("get").invoke(gameDir);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Path.of("").toAbsolutePath();
        }
    }

    /** The loaded mods (id, name, version) from FML's LoadingModList; empty if it cannot be read. */
    private static List<LoadedJar> mods(String fml) {
        List<LoadedJar> out = new ArrayList<>();
        try {
            Class<?> list = Class.forName(fml + ".loading.LoadingModList");
            Object instance = list.getMethod("get").invoke(null);
            for (Object info : (List<?>) list.getMethod("getMods").invoke(instance)) {
                String id = String.valueOf(call(info, "getModId"));
                Object name = call(info, "getDisplayName");
                Object version = call(info, "getVersion");
                out.add(new LoadedJar(id, name == null ? id : name.toString(), version == null ? "" : version.toString(), null));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // older or changed FML: only Lumora itself is known
        }
        return out;
    }

    private static Object call(Object o, String method) throws ReflectiveOperationException {
        Method m = o.getClass().getMethod(method);
        return m.invoke(o);
    }

    /** The managed copy in mods/ (lumora-client-mc&lt;version&gt;-forge.jar), or null. */
    private static Path ownJar(Path gameDir) {
        try (var s = Files.list(gameDir.resolve("mods"))) {
            return s.filter(p -> p.getFileName().toString().matches("lumora-client-mc[0-9.]+-forge\\.jar")).findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
