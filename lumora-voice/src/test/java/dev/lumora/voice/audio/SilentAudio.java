package dev.lumora.voice.audio;

import dev.lumora.voice.VoiceConfig;

/** Test helper: a real {@link VoiceAudio} whose devices never open (no microphone, no speaker). */
public final class SilentAudio {
    private SilentAudio() {}

    public static VoiceAudio create(VoiceConfig config) {
        return new VoiceAudio(config.audioSettings(), bytes -> {}, message -> {}, new VoiceAudio.Devices() {
            public AudioDevices.Input input(AudioBackend backend, String id) throws Exception { throw new IllegalStateException("tests never open a microphone"); }
            public AudioDevices.Output output(AudioBackend backend, String id) throws Exception { throw new IllegalStateException("tests never open a speaker"); }
        });
    }
}
