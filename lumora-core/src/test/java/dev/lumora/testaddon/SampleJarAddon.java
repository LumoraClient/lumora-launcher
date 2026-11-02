package dev.lumora.testaddon;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.core.addon.TestSink;

/** Packed into a test jar by JarAddonLoaderTest; hidden from the parent loader there. */
public final class SampleJarAddon implements LumoraAddon {
    @Override
    public void onEnable(AddonContext context) {
        TestSink.EVENTS.add("enable:" + getClass().getClassLoader().getName() + ":" + context.translations().translate("sample.hello"));
    }

    @Override
    public void onDisable(AddonContext context) {
        TestSink.EVENTS.add("disable:sample");
    }
}
