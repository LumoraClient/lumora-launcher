package dev.lumora.example.timer;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.event.client.WorldJoinEvent;
import dev.lumora.api.event.client.WorldLeaveEvent;
import dev.lumora.api.input.Keys;

/**
 * Example addon: registers the session timer widget, listens for world join/leave, offers a keybind to restart the
 * timer and an addon setting for the join notification. Everything is removed by Lumora when the addon is
 * switched off - no cleanup code needed.
 */
public final class SessionTimerAddon implements LumoraAddon {
    private BooleanSetting notifyOnJoin;

    @Override
    public void onInitialize(AddonContext context) {
        notifyOnJoin = context.configuration().settings().toggle("notify_on_join", true);
    }

    @Override
    public void onEnable(AddonContext context) {
        SessionTimerWidget widget = new SessionTimerWidget(context.game());
        context.widgets().register(widget);
        context.events().subscribe(WorldJoinEvent.class, e -> {
            widget.start();
            if (notifyOnJoin.get()) {
                context.notifications().info(context.translations().translate("session-timer.title"),
                        context.translations().translate("session-timer.started"));
            }
        });
        context.events().subscribe(WorldLeaveEvent.class, e -> widget.stop());
        context.keybinds().register("reset", "session-timer.key.reset", Keys.J, () -> {
            widget.start();
            context.notifications().success(context.translations().translate("session-timer.title"),
                    context.translations().translate("session-timer.reset"));
        });
        if (context.game().inWorld()) {
            widget.start();
        }
        context.logger().info("Session timer enabled");
    }
}
