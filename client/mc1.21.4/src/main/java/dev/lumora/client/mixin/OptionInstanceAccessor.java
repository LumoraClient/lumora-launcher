package dev.lumora.client.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Fullbright: sets the brightness above the slider range (OptionInstance#set would refuse it). */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor<T> {
    @Accessor("value")
    void lumora$setValue(T value);
}
