package dev.lumora.client.mixin;

import dev.lumora.core.extras.NameTagsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Module "Name Tags", "Show own name tag": Minecraft never shows the tag of the camera player. In third person the
 * camera check sees no camera entity, so every other rule (hidden HUD, invisible, riding, team visibility) still applies.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class NameTagOwnMixin {
    @Redirect(method = "shouldShowName", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;getCameraEntity()Lnet/minecraft/world/entity/Entity;"))
    private Entity lumora$ownNameTag(Minecraft mc) {
        Entity camera = mc.getCameraEntity();
        if (camera != null && camera == mc.player && NameTagsModule.showOwn() && mc.options.getCameraType().ordinal() != 0) {
            return null;
        }
        return camera;
    }
}
