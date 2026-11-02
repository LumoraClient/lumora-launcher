package dev.lumora.client.mixin.platform;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** {@code Screen.addRenderableWidget} for the screen-init hook (adds buttons to vanilla screens). */
@Mixin(Screen.class)
public interface ScreenWidgetsInvoker {
    @Invoker("addRenderableWidget")
    GuiEventListener lumora$addRenderableWidget(GuiEventListener widget);
}
