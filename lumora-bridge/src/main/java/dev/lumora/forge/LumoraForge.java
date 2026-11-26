package dev.lumora.forge;

/** Forge's entry: Forge constructs classes annotated with its {@code @Mod} while loading mods. */
@net.minecraftforge.fml.common.Mod("lumora")
public final class LumoraForge {
    public LumoraForge() {
        ForgeStart.start("net.minecraftforge.fml", LumoraForge.class);
    }
}
