package dev.lumora.client.mixin;

import dev.lumora.client.api.McHudGraphics;
import dev.lumora.core.extras.TabModule;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.compat.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora tab module: ping in milliseconds instead of the signal bars. */
@Mixin(PlayerTabOverlay.class)
public abstract class TabPingMixin {
    @Unique
    private static final McHudGraphics LUMORA_G = new McHudGraphics();

    @Inject(method = "renderPingIcon", at = @At("HEAD"), cancellable = true)
    private void lumora$ping(PoseStack pose, int width, int x, int y, PlayerInfo info, CallbackInfo ci) {
        TabModule t = TabModule.active();
        if (t == null || !t.pingNumbers()) {
            return;
        }
        ci.cancel();
        TabModule.drawPing(LUMORA_G.with(GuiGraphics.of(pose)), info.getLatency(), x + width - 1, y);
    }
}
