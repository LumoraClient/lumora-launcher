package dev.lumora.client.mixin;

import dev.lumora.client.api.McHudGraphics;
import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.core.extras.TabModule;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora tab module: ping in milliseconds instead of the signal bars. */
@Mixin(PlayerListHud.class)
public abstract class TabPingMixin {
    @Unique
    private static final McHudGraphics LUMORA_G = new McHudGraphics();

    @Inject(method = "renderLatencyIcon", at = @At("HEAD"), cancellable = true)
    private void lumora$ping(int width, int x, int y, PlayerListEntry entry, CallbackInfo ci) {
        TabModule t = TabModule.active();
        if (t == null || !t.pingNumbers()) {
            return;
        }
        ci.cancel();
        TabModule.drawPing(LUMORA_G.with(GuiGraphics.create()), entry.getLatency(), x + width - 1, y);
    }
}
