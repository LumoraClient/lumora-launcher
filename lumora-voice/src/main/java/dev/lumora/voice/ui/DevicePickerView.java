package dev.lumora.voice.ui;

import dev.lumora.voice.audio.AudioDevices;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** "Mikrofon auswählen" / "Lautsprecher auswählen": all devices as a list, a click picks one and closes. */
final class DevicePickerView extends ListDialog {
    private final List<AudioDevices.Device> devices;
    private final String current;
    private final Consumer<String> pick;

    DevicePickerView(ViewHost host, String title, List<AudioDevices.Device> devices, String current, Consumer<String> pick) {
        super(host, title, "Abbrechen");
        this.devices = devices; this.current = current; this.pick = pick;
        for (int i = 0; i < devices.size(); i++) if (devices.get(i).id().equals(current)) startAt(i);
    }

    @Override
    protected List<Control> entries() {
        List<Control> out = new ArrayList<>();
        for (var device : devices) {
            boolean selected = device.id().equals(current);
            out.add(new Control(device.name(), device.id().isEmpty() ? "Gerät, das das System als Standard nutzt" : device.name(),
                    () -> selected ? "Ausgewählt" : "", () -> { pick.accept(device.id()); host.back(); }).toggle(() -> selected));
        }
        return out;
    }

    @Override
    protected String emptyText() { return "Keine Geräte gefunden."; }

    @Override
    protected List<Control> buttons() { return List.of(); }
}
