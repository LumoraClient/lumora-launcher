package dev.lumora.client.api;

import dev.lumora.api.addon.AddonDescriptor;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.core.addon.AddonCandidate;
import dev.lumora.core.addon.AddonDescriptorParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finds addons: every Fabric mod that has a {@code lumora-addon.json} next to its {@code fabric.mod.json}. The
 * entry point is loaded through the game's class loader only when the addon is initialized, so a mod with an
 * addon class still loads without Lumora.
 */
final class FabricAddonDiscovery {
    static final String FILE = "lumora-addon.json";

    private FabricAddonDiscovery() {
    }

    static List<AddonCandidate> discover() {
        List<AddonCandidate> out = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            Optional<Path> file = mod.findPath(FILE);
            if (file.isEmpty() || !Files.isRegularFile(file.get())) {
                continue;
            }
            String source = mod.getMetadata().getId() + " (" + mod.getMetadata().getVersion().getFriendlyString() + ")";
            try {
                AddonDescriptor d = AddonDescriptorParser.parse(Files.readString(file.get(), StandardCharsets.UTF_8), source);
                out.add(AddonCandidate.of(d, () -> create(d.entrypoint())));
            } catch (IOException e) {
                out.add(AddonCandidate.broken(source, FILE + " konnte nicht gelesen werden: " + e.getMessage()));
            } catch (AddonDescriptorParser.InvalidDescriptorException e) {
                out.add(AddonCandidate.broken(source, e.getMessage()));
            }
        }
        return out;
    }

    private static LumoraAddon create(String className) throws ReflectiveOperationException {
        Class<?> type = Class.forName(className, true, FabricAddonDiscovery.class.getClassLoader());
        if (!LumoraAddon.class.isAssignableFrom(type)) {
            throw new ClassCastException(className + " implementiert nicht " + LumoraAddon.class.getName());
        }
        return (LumoraAddon) type.getDeclaredConstructor().newInstance();
    }
}
