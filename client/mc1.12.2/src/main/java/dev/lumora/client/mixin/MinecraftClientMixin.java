package dev.lumora.client.mixin;

import dev.lumora.client.api.LumoraRuntime;
import dev.lumora.client.gui.LumoraTitleScreen;
import dev.lumora.client.gui.ScreenTransition;
import dev.lumora.client.perf.FrameStats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 1.12.2 client hooks:
 * <ul>
 *   <li>setScreen: the Lumora main menu wherever Minecraft would open its title screen (1.12.2 also turns
 *       {@code setScreen(null)} without a world into the title screen), and the capture for the menu cross-fade.
 *       Exact class check, so subclasses of TitleScreen from other mods are left alone.</li>
 *   <li>runGameLoop: frame times for the benchmark (read-only).</li>
 *   <li>reloadResources: the API's ResourcesReloadedEvent (1.12.2 reloads synchronously).</li>
 * </ul>
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void lumora$setScreen(Screen screen, CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        boolean title = screen == null ? mc.world == null : screen.getClass() == TitleScreen.class;
        if (title && LumoraTitleScreen.shouldReplace()) {
            ci.cancel();
            mc.setScreen(new LumoraTitleScreen());
            return;
        }
        ScreenTransition.capture(mc, mc.currentScreen, screen);
    }

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void lumora$onFrame(CallbackInfo ci) {
        FrameStats.INSTANCE.onFrame(System.nanoTime());
    }

    @Inject(method = "reloadResources", at = @At("RETURN"))
    private void lumora$reloaded(CallbackInfo ci) {
        LumoraRuntime.resourcesReloaded();
    }
}
