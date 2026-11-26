package dev.lumora.voice;

import dev.lumora.voice.audio.AudioProcessing;
import dev.lumora.voice.audio.EnvironmentalAudio;
import dev.lumora.voice.audio.NoiseSuppression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AudioAndConfigTest {
    @TempDir Path directory;
    @Test void activationPreservesShortSpeechPausesAndThenCloses() {
        var dsp = new AudioProcessing();
        assertFalse(dsp.voiceActive(-60, -40, 60));
        assertTrue(dsp.voiceActive(-20, -40, 60));
        for (int frame = 0; frame < 3; frame++) assertTrue(dsp.voiceActive(-60, -40, 60));
        assertFalse(dsp.voiceActive(-60, -40, 60));
        dsp.reset(); assertFalse(dsp.voiceActive(-60, -40, 60));
    }
    @Test void gateRejectsQuietInputAndMicrophoneGainClampsLoudInput() {
        var config = new VoiceConfig(); config.highPassFilter = false; config.noiseGate = true; config.gateThresholdDb = -40;
        short[] quiet = new short[960]; Arrays.fill(quiet, (short) 20);
        assertArrayEquals(new short[960], new AudioProcessing().process(quiet, config.audioSettings()));
        short[] loud = new short[960]; Arrays.fill(loud, (short) 20000); config.microphoneVolume = 4;
        short[] processed = new AudioProcessing().process(loud, config.audioSettings());
        assertEquals(32767, processed[500]); assertTrue(AudioProcessing.db(processed) > -1);
    }
    @Test void highPassRemovesDcWithoutErasingSpeechFrequency() {
        var config = new VoiceConfig(); var dsp = new AudioProcessing(); short[] dc = new short[960]; Arrays.fill(dc, (short) 6000);
        short[] filtered = dsp.process(dc, config.audioSettings()); assertTrue(Math.abs(filtered[959]) < 5);
        short[] voice = new short[960]; for (int i = 0; i < voice.length; i++) voice[i] = (short) (6000 * Math.sin(i * 2 * Math.PI * 1000 / 48000));
        assertTrue(AudioProcessing.db(dsp.process(voice, config.audioSettings())) > -25);
    }
    @Test void protectionCapsTheFinalStereoMixAndPreservesPan() {
        float[] mix = {50000, -25000, 10000, 0}; AudioProcessing.protect(mix, 75, 15);
        assertEquals(4915.05f, mix[0], .1); assertEquals(-mix[0] / 2, mix[1], .1); assertEquals(0, mix[3]);
        float[] quiet = {200, -100}; AudioProcessing.protect(quiet, 75, 15); assertArrayEquals(new float[]{200, -100}, quiet);
    }
    @Test void preferencesSurviveReloadIncludingPlayerMuteAndDevice() throws Exception {
        Path path = directory.resolve("labyvoice.json"); var config = VoiceConfig.load(path); UUID id = UUID.randomUUID();
        config.activation = VoiceConfig.Activation.VOICE; config.inputDevice = "USB microphone"; config.player(id, "Friend").muted = true;
        config.player(id, "Friend").volume = .35f; config.save();
        var loaded = VoiceConfig.load(path); assertEquals(VoiceConfig.Activation.VOICE, loaded.activation);
        assertEquals("USB microphone", loaded.inputDevice); assertTrue(loaded.existing(id).muted); assertEquals(.35f, loaded.existing(id).volume);
        assertFalse(Files.readString(path).contains("accessToken"));
    }
    @Test void corruptAndOutOfRangePreferencesAreRecoverable() throws Exception {
        Path path = directory.resolve("voice.json"); Files.writeString(path, "{broken");
        var config = VoiceConfig.load(path); assertFalse(config.enabled);
        try (var files = Files.list(directory)) { assertTrue(files.anyMatch(p -> p.getFileName().toString().contains(".invalid-"))); }
        config.microphoneVolume = Float.NaN; config.outputVolume = 999; config.releaseMillis = -1; config.players.put("bad-id", new VoiceConfig.Player()); config.save();
        var loaded = VoiceConfig.load(path); assertEquals(1, loaded.microphoneVolume); assertEquals(2, loaded.outputVolume); assertEquals(0, loaded.releaseMillis); assertTrue(loaded.players.isEmpty());
    }
    @Test void legacyDeviceSelectionMigratesAndIndependentEnginesPersist() throws Exception {
        Path path = directory.resolve("engines.json");
        Files.writeString(path, "{\"inputDevice\":\"USB microphone\",\"outputDevice\":\"Headphones\"}");
        var config = VoiceConfig.load(path);
        assertEquals(dev.lumora.voice.audio.AudioBackend.JAVAX, config.inputBackend);
        assertEquals("USB microphone", config.inputDevice);
        config.alternativeAudioInterfaces = true;
        config.inputBackend = dev.lumora.voice.audio.AudioBackend.OPENAL; config.inputDevice = "Native microphone";
        config.save(); var loaded = VoiceConfig.load(path);
        assertEquals(dev.lumora.voice.audio.AudioBackend.OPENAL, loaded.audioSettings().inputBackend());
        assertEquals(dev.lumora.voice.audio.AudioBackend.JAVAX, loaded.audioSettings().outputBackend());
        assertEquals("Native microphone", loaded.inputDevice); assertEquals("Headphones", loaded.outputDevice);
        loaded.alternativeAudioInterfaces = false; loaded.save();
        var reset = VoiceConfig.load(path);
        assertEquals(dev.lumora.voice.audio.AudioBackend.JAVAX, reset.inputBackend);
        assertEquals("", reset.inputDevice); assertEquals("Headphones", reset.outputDevice);
    }
    @Test void nativeRnnoiseSuppressesSyntheticBackgroundNoiseWithoutMicrophone() {
        Random random = new Random(4); double inputEnergy = 0, outputEnergy = 0;
        try (var filter = new NoiseSuppression()) {
            for (int frame = 0; frame < 100; frame++) {
                short[] noise = new short[960]; for (int i = 0; i < noise.length; i++) noise[i] = (short) (random.nextGaussian() * 2200);
                short[] filtered = filter.process(noise); assertEquals(noise.length, filtered.length);
                if (frame > 60) for (int i = 0; i < noise.length; i++) { inputEnergy += (double) noise[i] * noise[i]; outputEnergy += (double) filtered[i] * filtered[i]; }
            }
        }
        assertTrue(outputEnergy < inputEnergy * .5, "The bundled RNNoise model should substantially reduce stationary noise");
    }
    @Test void environmentFilterMufflesHighFrequenciesAndProducesDelayedEcho() {
        var effects = new EnvironmentalAudio(); short[] high = new short[960]; for (int i = 0; i < high.length; i++) high[i] = (short) (Math.sin(i * 2 * Math.PI * 10000 / 48000) * 8000);
        assertTrue(AudioProcessing.db(effects.process(high, true, false, false)) < AudioProcessing.db(high) - 12);
        effects = new EnvironmentalAudio(); short[] pulse = new short[960]; pulse[0] = 12000; effects.process(pulse, false, true, false);
        for (int frame = 0; frame < 9; frame++) effects.process(new short[960], false, true, false);
        assertTrue(effects.process(new short[960], false, true, false)[0] > 2000);
    }
    @Test void automaticThresholdFollowsTheNoiseButNotTheVoice() {
        var dsp = new AudioProcessing();
        float threshold = 0;
        for (int frame = 0; frame < 200; frame++) threshold = dsp.autoThreshold(-50);
        assertEquals(-40, threshold, .5, "noise floor plus margin");
        for (int frame = 0; frame < 250; frame++) threshold = dsp.autoThreshold(-15); // five seconds of speech
        assertTrue(threshold < -38, "speech hardly lifts the threshold: " + threshold);
        for (int frame = 0; frame < 20; frame++) threshold = dsp.autoThreshold(-55);
        assertTrue(threshold < -43, "a quieter room lowers it quickly: " + threshold);
        for (int frame = 0; frame < 500; frame++) threshold = dsp.autoThreshold(-48);
        assertEquals(-38, threshold, 1, "louder background noise raises it within seconds");
        for (int frame = 0; frame < 500; frame++) threshold = dsp.autoThreshold(-90);
        assertEquals(AudioProcessing.AUTO_MIN_DB, threshold, .01, "never below the minimum");
        dsp.autoThreshold(-100); // microphone closed: ignored
        dsp.reset();
        assertEquals(-40, dsp.autoThreshold(-100), "unknown after a reset");
    }
    @Test void automaticGainReplacesTheManualGain() {
        var config = new VoiceConfig(); config.highPassFilter = false; config.microphoneVolume = 4; config.automaticGain = true;
        short[] speech = new short[960]; Arrays.fill(speech, (short) 2000);
        short[] processed = new AudioProcessing().process(speech, config.audioSettings());
        assertTrue(processed[500] < 3000, "the manual 400 % does not apply while automatic: " + processed[500]);
        config.automaticGain = false;
        assertEquals(8000, new AudioProcessing().process(speech, config.audioSettings())[500]);
    }
    @Test void spatialModesAndTheirMigration() throws Exception {
        assertEquals(0, VoiceConfig.SpatialMode.OFF.pan(.8f));
        assertEquals(.25f, VoiceConfig.SpatialMode.NORMAL.pan(.25f), 1e-6);
        assertEquals(.5f, VoiceConfig.SpatialMode.STRONG.pan(.25f), 1e-6);
        assertEquals(-.5f, VoiceConfig.SpatialMode.STRONG.pan(-.25f), 1e-6);
        assertEquals(1, VoiceConfig.SpatialMode.NORMAL.rearGain(-1));
        assertEquals(.7f, VoiceConfig.SpatialMode.STRONG.rearGain(-1), 1e-6);
        assertEquals(1, VoiceConfig.SpatialMode.STRONG.rearGain(.5f));
        Path file = directory.resolve("old.json");
        Files.writeString(file, "{\"spatialAudio\": false}");
        assertEquals(VoiceConfig.SpatialMode.OFF, VoiceConfig.load(file).spatialMode, "0.8.1 without 3D audio stays off");
        Files.writeString(file, "{}");
        var fresh = VoiceConfig.load(file);
        assertEquals(VoiceConfig.SpatialMode.NORMAL, fresh.spatialMode);
        assertFalse(fresh.autoThreshold, "the manual threshold of existing users stays");
        fresh.spatialMode = VoiceConfig.SpatialMode.STRONG; fresh.autoThreshold = true; fresh.save();
        var reloaded = VoiceConfig.load(file);
        assertEquals(VoiceConfig.SpatialMode.STRONG, reloaded.spatialMode);
        assertTrue(reloaded.spatialAudio && reloaded.autoThreshold);
        assertEquals(VoiceConfig.SpatialMode.NORMAL, new VoiceConfig().spatial(), "before sanitize");
    }
}
