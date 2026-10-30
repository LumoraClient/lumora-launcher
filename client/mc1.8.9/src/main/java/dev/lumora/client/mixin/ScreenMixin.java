package dev.lumora.client.mixin;

import dev.lumora.client.LumoraClient;
import dev.lumora.client.gui.LumoraUi;
import dev.lumora.client.compat.GuiGraphics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Minecraft 1.8.9 screen hooks (Legacy Fabric has no screen events):
 * <ul>
 *   <li>init: the Lumora entry in the title and pause menu ({@link LumoraClient#afterScreenInit}).</li>
 *   <li>mouseClicked: a click on that entry opens the hub instead of reaching the screen's own button handler.</li>
 *   <li>renderDirtBackground: menus outside a world show the Lumora landscape (darkened for readable text) instead of
 *       the dirt texture. Off with "menuBackground": false.</li>
 * </ul>
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    /** Dims the landscape behind menu text (the newer versions blur it). */
    private static final int DIM = 0x990F0C16;

    @Shadow
    protected List<ButtonWidget> buttons;
    @Shadow
    public int width;
    @Shadow
    public int height;

    @Inject(method = "init(Lnet/minecraft/client/MinecraftClient;II)V", at = @At("TAIL"))
    private void lumora$afterInit(MinecraftClient client, int w, int h, CallbackInfo ci) {
        LumoraClient.afterScreenInit((Screen) (Object) this, buttons);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void lumora$hubButton(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (button != 0) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        for (ButtonWidget b : buttons) {
            if (b.id == LumoraClient.HUB_BUTTON_ID && b.isMouseOver(mc, mouseX, mouseY)
                    && LumoraClient.buttonClicked((Screen) (Object) this, b)) {
                b.playDownSound(mc.getSoundManager());
                ci.cancel();
                return;
            }
        }
    }

    @Inject(method = "renderDirtBackground", at = @At("HEAD"), cancellable = true)
    private void lumora$background(int vOffset, CallbackInfo ci) {
        if (LumoraClient.config() != null && LumoraClient.config().menuBackground) {
            GuiGraphics g = GuiGraphics.create();
            LumoraUi.background(g, width, height, LumoraUi.BACKGROUND_MENU);
            g.fill(0, 0, width, height, DIM);
            ci.cancel();
        }
    }
}
