package dev.lumora.testaddon.user;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.core.addon.TestSink;
import dev.lumora.testaddon.lib.Greeter;

/** Uses a class of the "greeter" addon's jar (declared in "depends"). */
public final class UserAddon implements LumoraAddon {
    @Override
    public void onEnable(AddonContext context) {
        TestSink.EVENTS.add("user:" + Greeter.greet());
    }
}
