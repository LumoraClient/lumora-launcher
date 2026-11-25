package dev.lumora.launcher.core.platform;

import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectSummary;
import dev.lumora.launcher.core.platform.PlatformModel.SearchPage;
import dev.lumora.launcher.core.platform.PlatformModel.SearchQuery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * All content platforms behind one entry point (search and metadata service). The interface asks the repository; the
 * repository asks the providers. Lumora's own categories ({@link LumoraCategory}) are translated into each platform's
 * category slugs here, so the filter list looks the same whatever the platform.
 */
public final class ModRepository {
    /**
     * Lumora's category list. {@code modrinth} are Modrinth category slugs (any may match); {@code keyword} is added to
     * the search text where a platform has no matching category.
     */
    public enum LumoraCategory {
        ADVENTURE("adventure", List.of("adventure"), null, List.of("Adventure and RPG")),
        LIBRARY("library", List.of("library"), null, List.of("API and Library")),
        EQUIPMENT("equipment", List.of("equipment"), null, List.of("Armor, Tools, and Weapons")),
        AUTOMATION("automation", List.of("technology"), "automation", List.of("Automation", "Processing")),
        BIOMES("biomes", List.of("worldgen"), "biome", List.of("Biomes")),
        BLOCKS("blocks", List.of("decoration"), "blocks", List.of("Cosmetic")),
        BUILDING("building", List.of("decoration", "utility"), "building", List.of("Cosmetic", "Utility & QoL")),
        COSMETICS("cosmetics", List.of("decoration"), "cosmetic", List.of("Cosmetic")),
        DECORATION("decoration", List.of("decoration"), null, List.of("Cosmetic")),
        ECONOMY("economy", List.of("economy"), null, List.of("Server Utility")),
        GEAR("gear", List.of("equipment"), null, List.of("Armor, Tools, and Weapons")),
        EXPLORATION("exploration", List.of("adventure", "worldgen"), null, List.of("Adventure and RPG", "Structures", "Dimensions")),
        FOOD("food", List.of("food"), null, List.of("Food")),
        MAGIC("magic", List.of("magic"), null, List.of("Magic")),
        MAPS("maps", List.of("utility"), "map", List.of("Map and Information")),
        MOBS("mobs", List.of("mobs"), null, List.of("Mobs")),
        OPTIMIZATION("optimization", List.of("optimization"), null, List.of("Performance")),
        QUALITY_OF_LIFE("qol", List.of("utility", "game-mechanics"), null, List.of("Utility & QoL")),
        STORAGE("storage", List.of("storage"), null, List.of("Storage")),
        TECHNOLOGY("technology", List.of("technology"), null, List.of("Technology")),
        TRANSPORT("transport", List.of("transportation"), null, List.of("Player Transport", "Energy, Fluid, and Item Transport")),
        UTILITY("utility", List.of("utility"), null, List.of("Utility & QoL")),
        WORLDGEN("worldgen", List.of("worldgen"), null, List.of("World Gen"));

        private final String id;
        private final List<String> modrinth;
        private final String keyword;
        private final List<String> curseforge;

        LumoraCategory(String id, List<String> modrinth, String keyword, List<String> curseforge) {
            this.id = id;
            this.modrinth = modrinth;
            this.keyword = keyword;
            this.curseforge = curseforge;
        }

        /** CurseForge category names (matched by name, the API ids differ per class). */
        public List<String> curseforge() {
            return curseforge;
        }

        public String id() {
            return id;
        }

        /** Language key of the name ({@code discover.cat.<id>}). */
        public String langKey() {
            return "discover.cat." + id;
        }

        public List<String> modrinth() {
            return modrinth;
        }

        public String keyword() {
            return keyword;
        }

        public static Optional<LumoraCategory> of(String id) {
            for (LumoraCategory c : values()) {
                if (c.id.equals(id)) {
                    return Optional.of(c);
                }
            }
            return Optional.empty();
        }
    }

    private final List<ModPlatformProvider> providers;

    public ModRepository(List<ModPlatformProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    /** The platforms Lumora talks to: Modrinth, and CurseForge once the player entered their own API key. */
    public static ModRepository standard(Downloader downloader, java.util.function.Supplier<String> curseForgeKey) {
        return new ModRepository(List.of(new ModrinthProvider(downloader), new CurseForgeProvider(downloader, curseForgeKey)));
    }

    public List<ModPlatformProvider> providers() {
        return providers;
    }

    /** Providers usable right now. */
    public List<ModPlatformProvider> available() {
        return providers.stream().filter(ModPlatformProvider::available).toList();
    }

    public Optional<ModPlatformProvider> provider(Platform platform) {
        return providers.stream().filter(p -> p.platform() == platform).findFirst();
    }

    public ModPlatformProvider require(Platform platform) throws IOException {
        ModPlatformProvider p = provider(platform).orElseThrow(() -> new IOException("platform " + platform + " not available"));
        if (!p.available()) {
            throw new IOException("platform " + platform + " not available");
        }
        return p;
    }

    /**
     * Searches one platform, or all available ones ({@code platform} null): then the pages of every platform are
     * interleaved, the total is the sum.
     *
     * @param categories Lumora category ids
     */
    public SearchPage search(Platform platform, SearchQuery query, List<String> categories) throws IOException {
        List<ModPlatformProvider> targets = platform == null ? available() : List.of(require(platform));
        List<List<ProjectSummary>> pages = new ArrayList<>();
        int total = 0;
        IOException failure = null;
        for (ModPlatformProvider p : targets) {
            try {
                SearchPage page = p.search(translate(p.platform(), query, categories));
                pages.add(page.hits());
                total += page.total();
            } catch (IOException e) {
                failure = e;
            }
        }
        if (pages.isEmpty() && failure != null) {
            throw failure;
        }
        List<ProjectSummary> merged = new ArrayList<>();
        for (int i = 0; ; i++) {
            boolean any = false;
            for (List<ProjectSummary> page : pages) {
                if (i < page.size()) {
                    merged.add(page.get(i));
                    any = true;
                }
            }
            if (!any) {
                break;
            }
        }
        return new SearchPage(merged, query.offset(), total);
    }

    /** Lumora categories to the platform's slugs (any may match); keywords of categories without a slug join the text. */
    static SearchQuery translate(Platform platform, SearchQuery q, List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return q;
        }
        List<String> slugs = new ArrayList<>(q.categories());
        StringBuilder text = new StringBuilder(q.text());
        for (String id : categories) {
            LumoraCategory c = LumoraCategory.of(id).orElse(null);
            if (c == null) {
                continue;
            }
            for (String s : platform == Platform.CURSEFORGE ? c.curseforge() : c.modrinth()) {
                if (!slugs.contains(s)) {
                    slugs.add(s);
                }
            }
            if (c.keyword() != null && categories.size() == 1) {
                text.append(text.isEmpty() ? "" : " ").append(c.keyword());
            }
        }
        return new SearchQuery(text.toString(), q.type(), q.gameVersion(), q.loaders(), slugs, q.environment(), q.openSourceOnly(),
                q.sort(), q.offset(), q.limit());
    }
}
