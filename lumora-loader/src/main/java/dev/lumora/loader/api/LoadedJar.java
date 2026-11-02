package dev.lumora.loader.api;

import java.nio.file.Path;

/**
 * One jar the Lumora Loader started (the Lumora client of this Minecraft version), from its {@code lumora.loader.json}.
 *
 * @param file the jar itself; {@link LumoraLoader#root(String)} gives a file system view of its content
 */
public record LoadedJar(String id, String name, String version, Path file) {
}
