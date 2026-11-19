package dev.lumora.client.anim;

import net.minecraft.world.item.ItemStack;

import java.util.Collection;

/** Lumora "Animations": the contents Minecraft built for a creative tab, before the 1.8 layout replaces them. */
public interface LegacyTabItems {
    Collection<ItemStack> lumora$originalItems();
}
