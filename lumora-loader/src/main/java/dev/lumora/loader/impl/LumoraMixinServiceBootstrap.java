package dev.lumora.loader.impl;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

/** Tells Mixin which service class to use (META-INF/services). */
public final class LumoraMixinServiceBootstrap implements IMixinServiceBootstrap {
    @Override
    public String getName() {
        return "Lumora Loader";
    }

    @Override
    public String getServiceClassName() {
        return LumoraMixinService.class.getName();
    }

    @Override
    public void bootstrap() {
    }
}
