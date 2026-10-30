package dev.lumora.client.mixin;

import dev.lumora.client.zoom.LumoraZoom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Zoom: while zoomed the scroll wheel changes the zoom factor instead of the hotbar slot (1.8.9: MinecraftClient#tick). */
@Mixin(MinecraftClient.class)
public abstract class MouseScrollMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;scrollInHotbar(I)V"))
    private void lumora$zoomScroll(PlayerInventory inventory, int amount) {
        if (!LumoraZoom.scroll(amount)) {
            inventory.scrollInHotbar(amount);
        }
    }
}
