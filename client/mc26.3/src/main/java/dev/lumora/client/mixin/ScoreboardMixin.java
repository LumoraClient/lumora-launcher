package dev.lumora.client.mixin;

import dev.lumora.core.extras.TabModule;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora tab module: sidebar scoreboard hidden, moved, scaled or without the red numbers. */
@Mixin(Hud.class)
public abstract class ScoreboardMixin {
    @Unique
    private boolean lumora$pushed;

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void lumora$sidebarStart(GuiGraphicsExtractor g, Objective objective, CallbackInfo ci) {
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
        g.pose().pushMatrix();
        g.pose().translate(w + t.scoreboardX(), h + t.scoreboardY());
        g.pose().scale(s, s);
        g.pose().translate(-w, -h);
        lumora$pushed = true;
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("RETURN"))
    private void lumora$sidebarEnd(GuiGraphicsExtractor g, Objective objective, CallbackInfo ci) {
        if (lumora$pushed) {
            lumora$pushed = false;
            g.pose().popMatrix();
        }
    }

    @ModifyVariable(method = "displayScoreboardSidebar", at = @At("STORE"), ordinal = 0)
    private NumberFormat lumora$numbers(NumberFormat format) {
        TabModule t = TabModule.active();
        return t != null && t.hideNumbers() ? BlankFormat.INSTANCE : format;
    }
}
