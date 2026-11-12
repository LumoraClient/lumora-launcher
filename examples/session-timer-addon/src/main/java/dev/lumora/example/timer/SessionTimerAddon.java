package dev.lumora.example.timer;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.event.client.WorldJoinEvent;
import dev.lumora.api.event.client.WorldLeaveEvent;
import dev.lumora.api.input.Keys;

/**
 * Example addon: registers the session timer widget, listens for world join/leave, offers a keybind to restart the
 * timer and addon settings (Lumora API 1.3: sections, a setting shown only while its switch is on, a row of buttons
 * and a button that runs an action). Everything is removed by Lumora when the addon is switched off - no cleanup
 * code needed.
 */
public final class SessionTimerAddon implements LumoraAddon {
    private BooleanSetting notifyOnJoin;
    private ChoiceSetting notifyType;
    private volatile SessionTimerWidget widget;

    @Override
    public void onInitialize(AddonContext context) {
        SettingsGroup s = context.configuration().settings();
        notifyOnJoin = s.toggle("notify_on_join", true).section("session-timer.setting.section.notify");
        // only shown while the notification is on
        notifyType = s.selection("notify_type", "info", "info", "success").requires(notifyOnJoin);
        // a button: runs on the client thread, nothing is stored
        s.button("restart", () -> {
            SessionTimerWidget w = widget;
            if (w != null) {
                w.start();
            }
        }).section("session-timer.setting.section.timer");
    }

    @Override
    public void onEnable(AddonContext context) {
        SessionTimerWidget w = new SessionTimerWidget(context.game(), context.theme().widgetStyle());
        widget = w;
        context.widgets().register(w);
        context.events().subscribe(WorldJoinEvent.class, e -> {
            w.start();
            if (notifyOnJoin.get()) {
                String title = context.translations().translate("session-timer.title");
                String text = context.translations().translate("session-timer.started");
                if ("success".equals(notifyType.get())) {
                    context.notifications().success(title, text);
                } else {
                    context.notifications().info(title, text);
                }
            }
        });
        context.events().subscribe(WorldLeaveEvent.class, e -> w.stop());
        context.keybinds().register("reset", "session-timer.key.reset", Keys.J, () -> {
            w.start();
            context.notifications().success(context.translations().translate("session-timer.title"),
                    context.translations().translate("session-timer.reset"));
        });
        if (context.game().inWorld()) {
            w.start();
        }
        context.logger().info("Session timer enabled");
    }

    @Override
    public void onDisable(AddonContext context) {
        widget = null;
    }
}
