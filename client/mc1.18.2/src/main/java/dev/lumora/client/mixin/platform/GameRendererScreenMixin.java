package dev.lumora.client.mixin.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.platform.Hooks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** After the current screen was drawn (Minecraft up to 1.18.2 has no renderWithTooltip to hook into). */
@Mixin(GameRenderer.class)
public abstract class GameRendererScreenMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;render(Lcom/mojang/blaze3d/vertex/PoseStack;IIF)V"))
    private void lumora$screen(Screen screen, PoseStack pose, int mouseX, int mouseY, float delta) {
        screen.render(pose, mouseX, mouseY, delta);
        Hooks.fireScreenExtract(screen, pose, mouseX, mouseY, delta);
    }
}
