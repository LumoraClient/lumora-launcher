package dev.lumora.client.mixin;

import dev.lumora.client.compat.SharedJarFileSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.util.Map;

/**
 * Minecraft 1.12.2 opens its own jar to read the built-in advancements ({@code class_3348#method_14937}, unnamed in
 * Yarn); that crashes when the jar is already open (see {@link SharedJarFileSystem}). Recipes: {@link RecipeJarFileSystemMixin}.
 */
@Mixin(targets = "net.minecraft.achievement.class_3348")
public abstract class JarFileSystemMixin {
    @Redirect(method = "method_14937", at = @At(value = "INVOKE",
            target = "Ljava/nio/file/FileSystems;newFileSystem(Ljava/net/URI;Ljava/util/Map;)Ljava/nio/file/FileSystem;"))
    private FileSystem lumora$sharedJar(URI uri, Map<String, ?> env) throws IOException {
        return SharedJarFileSystem.open(uri, env);
    }
}
