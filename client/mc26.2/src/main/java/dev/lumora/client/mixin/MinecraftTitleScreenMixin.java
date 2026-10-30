package dev.lumora.client.mixin;

import dev.lumora.client.gui.LumoraTitleScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shows the Lumora main menu wherever Minecraft would open its title screen (startup, disconnect, quitting a
 * world). Can be disabled in config/lumora-client.json ("customMainMenu": false) or per session via
 * "Klassisches Menü". Exact class check, so subclasses of TitleScreen from other mods are left alone.
 * Since 26.2 screens are opened through {@link Gui#setScreen}.
 */
@Mixin(Gui.class)
public abstract class MinecraftTitleScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void lumora$replaceTitleScreen(Screen screen, CallbackInfo ci) {
        if (screen != null && screen.getClass() == TitleScreen.class && LumoraTitleScreen.shouldReplace()) {
            ci.cancel();
            ((Gui) (Object) this).setScreen(new LumoraTitleScreen());
        }
    }
}
