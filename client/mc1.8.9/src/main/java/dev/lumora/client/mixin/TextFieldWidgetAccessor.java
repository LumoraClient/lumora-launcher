package dev.lumora.client.mixin;

import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Width of the chat's text field: shorter by the chat input buttons at its right end (Lumora API 1.9). */
@Mixin(TextFieldWidget.class)
public interface TextFieldWidgetAccessor {
    @Accessor("width")
    int lumora$width();

    @Mutable
    @Accessor("width")
    void lumora$setWidth(int width);
}
