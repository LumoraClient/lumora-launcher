package dev.lumora.client.mixin;

import dev.lumora.core.extras.NameTagsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Module "Name Tags", "Show own name tag": Minecraft never shows the tag of the camera player. In third person the
 * camera check sees no camera entity, so every other rule (hidden HUD, invisible, team visibility) still applies.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class NameTagOwnMixin {
    @Redirect(method = "hasLabel", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;field_11098:Lnet/minecraft/entity/Entity;"))
    private Entity lumora$ownNameTag(EntityRenderDispatcher dispatcher) {
        Entity camera = dispatcher.field_11098;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (camera != null && camera == mc.player && NameTagsModule.showOwn() && mc.options.perspective != 0) {
            return null;
        }
        return camera;
    }
}
