package dev.lumora.client.mixin;

import dev.lumora.client.api.McHudGraphics;
import dev.lumora.core.extras.CrosshairModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora crosshair module: draws its own crosshair where vanilla would draw one. */
@Mixin(Gui.class)
public abstract class CrosshairMixin {
    @Unique
    private static final McHudGraphics LUMORA_G = new McHudGraphics();

    @Shadow
    private boolean canRenderCrosshairForSpectator(HitResult hit) {
        throw new AssertionError("mixin");
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void lumora$crosshair(GuiGraphics g, float partialTick, CallbackInfo ci) {
        CrosshairModule c = CrosshairModule.active();
        if (c == null) {
            return;
        }
        ci.cancel();
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR && !canRenderCrosshairForSpectator(mc.hitResult)) {
            return;
        }
        c.draw(LUMORA_G.with(g), g.guiWidth(), g.guiHeight(), Minecraft.getInstance().getWindow().getWidth(),
                Minecraft.getInstance().getWindow().getHeight());
    }
}
