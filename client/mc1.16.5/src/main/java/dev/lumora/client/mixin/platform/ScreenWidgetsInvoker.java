package dev.lumora.client.mixin.platform;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** {@code Screen.addButton} for the screen-init hook (adds buttons to vanilla screens). */
@Mixin(Screen.class)
public interface ScreenWidgetsInvoker {
    @Invoker("addButton")
    AbstractWidget lumora$addButton(AbstractWidget widget);
}
