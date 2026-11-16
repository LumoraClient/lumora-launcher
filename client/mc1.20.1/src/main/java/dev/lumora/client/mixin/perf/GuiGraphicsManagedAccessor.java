package dev.lumora.client.mixin.perf;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Frame Optimization: GuiGraphics' own "managed" mode (what drawManaged uses), so a run of rectangles is drawn with
 * one draw call instead of one per rectangle (see McHudGraphics#beginFills).
 */
@Mixin(GuiGraphics.class)
public interface GuiGraphicsManagedAccessor {
    @Accessor("managed")
    boolean lumora$managed();

    @Accessor("managed")
    void lumora$managed(boolean managed);
}
