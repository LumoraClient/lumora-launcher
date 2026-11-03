package dev.lumora.client.platform;

import dev.lumora.loader.api.LoadedJar;
import dev.lumora.loader.api.LumoraLoader;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ZipResourcePack;

import java.util.List;
import java.util.Optional;

/** Lumora's own assets on Minecraft 1.8.9 / 1.12.2: the client jar as one of the game's default resource packs. */
public final class Packs {
    private Packs() {
    }

    public static void addTo(List<ResourcePack> defaults) {
        Optional<LoadedJar> jar = LumoraLoader.component(LoaderEnv.CLIENT_ID);
        jar.ifPresent(j -> defaults.add(new ZipResourcePack(j.file().toFile())));
    }
}
