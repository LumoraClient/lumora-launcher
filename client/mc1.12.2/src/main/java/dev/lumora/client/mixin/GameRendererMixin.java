package dev.lumora.client.mixin;

import dev.lumora.client.api.LumoraRuntime;
import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.client.gui.ScreenTransition;
import dev.lumora.client.zoom.LumoraZoom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Minecraft 1.12.2 renderer hooks:
 * <ul>
 *   <li>getFov: zoom narrows the world field of view (the hand keeps its own, {@code changingFov == false}).</li>
 *   <li>render reads {@code options.sensitivity} only for mouse look: lower sensitivity while zoomed.</li>
 *   <li>render, right after the screen was drawn: the menu cross-fade and the Lumora notifications on top
 *       (Legacy Fabric has no screen render event).</li>
 * </ul>
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void lumora$zoom(float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (changingFov) {
            cir.setReturnValue(LumoraZoom.apply(cir.getReturnValue(), tickDelta));
        }
    }

    @Redirect(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/option/GameOptions;sensitivity:F"))
    private float lumora$zoomSensitivity(GameOptions options) {
        return (float) LumoraZoom.sensitivity(options.sensitivity);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;render(IIF)V", shift = At.Shift.AFTER))
    private void lumora$afterScreen(float tickDelta, long nanoTime, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null) {
            return;
        }
        GuiGraphics g = GuiGraphics.create();
        ScreenTransition.draw(g, mc.currentScreen);
        LumoraRuntime.afterScreenRender(g, mc.currentScreen.width);
    }
}
