package dev.lumora.api.event.config;

import dev.lumora.api.config.Setting;
import dev.lumora.api.event.LumoraEvent;

/**
 * A setting changed. {@code owner} is the widget id ({@code addon:widget}) or the addon id for addon-wide
 * settings.
 */
public record SettingChangedEvent(String owner, Setting<?> setting) implements LumoraEvent {
}
