package dev.lumora.fabric;

import dev.lumora.bridge.ClientStart;
import dev.lumora.loader.api.LoadedJar;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs the Lumora Client inside Fabric (and Quilt, which loads Fabric mods): the Lumora Loader API is answered from
 * Fabric Loader before the game starts, the client entry points run with the other mods' client initialisers. Other
 * Fabric mods appear as loaded components, so e.g. Sodium or ViaFabricPlus count as present.
 */
public final class LumoraFabric implements PreLaunchEntrypoint, ClientModInitializer {
    @Override
    public void onPreLaunch() {
        initLoaderApi();
    }

    @Override
    public void onInitializeClient() {
        initLoaderApi();
        ClientStart.startClient(LumoraFabric.class.getClassLoader());
    }

    private static void initLoaderApi() {
        FabricLoader fabric = FabricLoader.getInstance();
        List<LoadedJar> components = new ArrayList<>();
        for (ModContainer mod : fabric.getAllMods()) {
            var meta = mod.getMetadata();
            components.add(new LoadedJar(meta.getId(), meta.getName(), meta.getVersion().getFriendlyString(), file(mod).orElse(null)));
        }
        String minecraft = fabric.getModContainer("minecraft").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
        ClientStart.initLoader(fabric.getGameDir(), minecraft, components);
    }

    /** The jar a mod comes from; empty for nested or built-in mods. */
    private static Optional<Path> file(ModContainer mod) {
        try {
            List<Path> paths = mod.getOrigin().getPaths();
            return paths.size() == 1 && Files.isRegularFile(paths.get(0)) ? Optional.of(paths.get(0)) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty(); // nested jars and Minecraft itself have no single file
        }
    }
}
