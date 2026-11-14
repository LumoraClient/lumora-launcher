package dev.lumora.client.platform;

import dev.lumora.core.addon.AddonTranslations;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * JSON language files ({@code assets/<namespace>/lang/<language>.json}, the format from Minecraft 1.13 on) on Minecraft
 * 1.8.9 / 1.12.2, which only read {@code .lang} files. Lumora and its addons ship JSON only, so without this every
 * Lumora text shows its key. Called by {@code LegacyLangMixin} for each selected language right before the game reads
 * that language's {@code .lang} files: a {@code .lang} file of the same language still wins, later languages win over
 * earlier ones (as in the game).
 */
public final class JsonLang {
    /** Number formats are shown as plain {@code %s}, like the game does for {@code .lang} values. */
    private static final Pattern NUMBER_FORMAT = Pattern.compile("%(\\d+\\$)?[\\d.]*[df]");
    private static final int MAX_FILE_BYTES = 1024 * 1024;

    private JsonLang() {
    }

    public static void addTo(Map<String, String> translations, ResourceManager manager, String language) {
        String lang = language.toLowerCase(Locale.ROOT);
        Map<String, String> found = new LinkedHashMap<>();
        // addon jars from the addons folder: not part of the resource packs, so lowest priority
        for (Path jar : addonJars()) {
            found.putAll(AddonTranslations.load(jar, lang));
        }
        // resource packs (the Lumora jar and the player's packs), lowest pack first
        for (String namespace : manager.getAllNamespaces()) {
            List<Resource> resources;
            try {
                resources = manager.getAllResources(new Identifier(namespace, "lang/" + lang + ".json"));
            } catch (IOException | RuntimeException e) {
                continue; // no such file in this namespace
            }
            for (Resource r : resources) {
                try (InputStream in = r.getInputStream()) {
                    AddonTranslations.parseJson(new String(in.readNBytes(MAX_FILE_BYTES), StandardCharsets.UTF_8), found);
                } catch (IOException | RuntimeException e) {
                    // broken file: its keys stay untranslated
                }
            }
        }
        for (Map.Entry<String, String> e : found.entrySet()) {
            translations.put(e.getKey(), NUMBER_FORMAT.matcher(e.getValue()).replaceAll("%$1s"));
        }
    }

    private static List<Path> addonJars() {
        Path dir = LoaderEnv.lumoraDir().resolve("addons");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }
}
