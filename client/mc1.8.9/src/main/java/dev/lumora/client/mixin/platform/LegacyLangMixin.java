package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.JsonLang;
import net.minecraft.client.resource.language.TranslationStorage;
import net.minecraft.resource.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Map;

/**
 * JSON language files on Minecraft 1.8.9 / 1.12.2 (see {@link JsonLang}). The game builds the path
 * {@code lang/<language>.lang} once per selected language; right there the JSON entries of that language go in, so
 * the {@code .lang} files read next can still override them.
 */
@Mixin(TranslationStorage.class)
public abstract class LegacyLangMixin {
    @Shadow
    Map<String, String> translations;

    @Redirect(method = "load(Lnet/minecraft/resource/ResourceManager;Ljava/util/List;)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"))
    private String lumora$jsonLang(String format, Object[] args, ResourceManager manager, List<String> languages) {
        if (args.length > 0 && args[0] instanceof String language) {
            try {
                JsonLang.addTo(translations, manager, language);
            } catch (RuntimeException e) {
                // never break the game's own language loading
            }
        }
        return String.format(format, args);
    }
}
