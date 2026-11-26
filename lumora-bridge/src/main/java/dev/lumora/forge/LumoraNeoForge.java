package dev.lumora.forge;

/** NeoForge's entry: NeoForge constructs classes annotated with its {@code @Mod} while loading mods. */
@net.neoforged.fml.common.Mod("lumora")
public final class LumoraNeoForge {
    public LumoraNeoForge() {
        ForgeStart.start("net.neoforged.fml", LumoraNeoForge.class);
    }
}
