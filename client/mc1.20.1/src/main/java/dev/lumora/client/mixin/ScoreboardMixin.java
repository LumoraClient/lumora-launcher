package dev.lumora.client.mixin;

import dev.lumora.core.extras.TabModule;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora tab module: sidebar scoreboard hidden, moved, scaled or without the red numbers. */
@Mixin(Gui.class)
public abstract class ScoreboardMixin {
    @Unique
    private boolean lumora$pushed;

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void lumora$sidebarStart(GuiGraphics g, Objective objective, CallbackInfo ci) {
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
        float w = g.guiWidth();
        float h = g.guiHeight() / 2f;
        g.pose().pushPose();
        g.pose().translate(w + t.scoreboardX(), h + t.scoreboardY(), 0);
        g.pose().scale(s, s, 1);
        g.pose().translate(-w, -h, 0);
        lumora$pushed = true;
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("RETURN"))
    private void lumora$sidebarEnd(GuiGraphics g, Objective objective, CallbackInfo ci) {
        if (lumora$pushed) {
            lumora$pushed = false;
            g.pose().popPose();
        }
    }

}
