package dev.lumora.api;

import dev.lumora.api.addon.AddonInfo;
import dev.lumora.api.event.EventBus;
import dev.lumora.api.game.GameContext;
import dev.lumora.api.hud.HudEditor;
import dev.lumora.api.hud.WidgetInfo;
import dev.lumora.api.i18n.Translations;
import dev.lumora.api.notify.Notifications;
import dev.lumora.api.resource.Resources;

import java.util.List;
import java.util.Optional;

/** Read access to the running Lumora client and its services. All methods are safe to call on the client thread. */
public interface Lumora {
    /** {@link LumoraAPI#API_VERSION}. */
    String apiVersion();

    /** Version of the Lumora client mod, e.g. "0.1.0". */
    String clientVersion();

    /** Minecraft version of this runtime, e.g. "26.1.2". */
    String minecraftVersion();

    /**
     * Global event bus. Listeners registered here are owned by nobody and stay until unregistered; addons should
     * use {@link dev.lumora.api.addon.AddonContext#events()} instead.
     */
    EventBus events();

    GameContext game();

    Translations translations();

    Resources resources();

    /** Notifications shown in the Lumora style (top right). */
    Notifications notifications();

    /** All known addons (including failed and disabled ones), in load order. */
    List<AddonInfo> addons();

    Optional<AddonInfo> addon(String id);

    /** All registered HUD widgets, in render order. */
    List<WidgetInfo> widgets();

    HudEditor hudEditor();
}
