package dev.lumora.client.mixin;

import dev.lumora.client.compat.SharedJarFileSystem;
import net.minecraft.recipe.RecipeDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.util.Map;

/** Minecraft 1.12.2 built-in recipes ({@code RecipeDispatcher#method_14261}): same jar access as in {@link JarFileSystemMixin}. */
@Mixin(RecipeDispatcher.class)
public abstract class RecipeJarFileSystemMixin {
    @Redirect(method = "method_14261", at = @At(value = "INVOKE",
            target = "Ljava/nio/file/FileSystems;newFileSystem(Ljava/net/URI;Ljava/util/Map;)Ljava/nio/file/FileSystem;"))
    private static FileSystem lumora$sharedJar(URI uri, Map<String, ?> env) throws IOException {
        return SharedJarFileSystem.open(uri, env);
    }
}
