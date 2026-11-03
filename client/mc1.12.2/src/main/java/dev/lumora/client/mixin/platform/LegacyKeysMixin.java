package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.Hooks;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora's key bindings in Options > Controls; added before options.txt is read, so saved bindings apply. */
@Mixin(GameOptions.class)
public abstract class LegacyKeysMixin {
    @Shadow
    public KeyBinding[] allKeys;

    @Inject(method = "load()V", at = @At("HEAD"))
    private void lumora$keys(CallbackInfo ci) {
        allKeys = Hooks.withKeys(allKeys);
    }
}
