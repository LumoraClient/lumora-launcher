package dev.lumora.voice.protocol;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoiceSendQueueTest {
    @Test void hundredMillisecondCaptureBurstsAreSentAtTwentyMillisecondIntervals() {
        var queue = new VoiceSendQueue();
        for (int i = 0; i < 5; i++) queue.audio(new byte[]{(byte) i}, 0);
        for (int i = 0; i < 5; i++) {
            long now = i * 20_000_000L;
            assertArrayEquals(new byte[]{(byte) i}, queue.audio(now));
            assertNull(queue.audio(now)); assertNull(queue.audio(now + 19_000_000L));
        }
        assertNull(queue.audio(100_000_000L));
        queue.audio(new byte[]{9}, 1_000_000_000L);
        assertArrayEquals(new byte[]{9}, queue.audio(1_000_000_000L));
    }
    @Test void controlTrafficDoesNotConsumeAudioAndMuteDiscardsQueuedSpeech() {
        var queue = new VoiceSendQueue(); queue.audio(new byte[]{8}, 0);
        for (int i = 0; i < 24; i++) queue.control(new byte[]{(byte) i});
        for (int i = 0; i < 24; i++) assertArrayEquals(new byte[]{(byte) i}, queue.control());
        assertArrayEquals(new byte[]{8}, queue.audio(0));
        queue.audio(new byte[]{9}, 0); queue.silence(); assertNull(queue.audio(20_000_000));
        queue.audio(new byte[]{10}, 0); assertNull(queue.audio(200_000_000));
    }
}
