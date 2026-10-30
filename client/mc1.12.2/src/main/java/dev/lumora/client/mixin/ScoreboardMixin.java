package dev.lumora.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.lumora.core.extras.TabModule;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.Window;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora tab module: sidebar scoreboard hidden, moved or scaled (hiding the numbers needs 1.20.3+). */
@Mixin(InGameHud.class)
public abstract class ScoreboardMixin {
    @Unique
    private boolean lumora$pushed;

    @Inject(method = "renderScoreboardObjective", at = @At("HEAD"), cancellable = true)
    private void lumora$sidebarStart(ScoreboardObjective objective, Window window, CallbackInfo ci) {
        TabModule t = TabModule.active();
        if (t == null) {
            return;
        }
        if (!t.showScoreboard()) {
            ci.cancel();
            return;
        }
        if (t.scoreboardUnchanged()) {
            return;
        }
        float s = t.scoreboardScale();
        float w = window.getWidth();
        float h = window.getHeight() / 2f;
        GlStateManager.pushMatrix();
        GlStateManager.translate(w + t.scoreboardX(), h + t.scoreboardY(), 0);
        GlStateManager.scale(s, s, 1);
        GlStateManager.translate(-w, -h, 0);
        lumora$pushed = true;
    }

    @Inject(method = "renderScoreboardObjective", at = @At("RETURN"))
    private void lumora$sidebarEnd(ScoreboardObjective objective, Window window, CallbackInfo ci) {
        if (lumora$pushed) {
            lumora$pushed = false;
            GlStateManager.popMatrix();
        }
    }
}
