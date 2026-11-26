package dev.lumora.voice.audio;

import dev.lumora.voice.protocol.VoiceCodec;
import io.github.jaredmdobson.concentus.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AudioContinuityTest {
    private static List<byte[]> encode(int packetSamples, int count) throws Exception {
        return encode(packetSamples, count, 64000);
    }
    private static List<byte[]> encode(int packetSamples, int count, int bitrate) throws Exception {
        if (packetSamples == 5760) {
            var result = new ArrayList<byte[]>();
            for (byte[] half : encode(2880, count, 32000)) {
                var repacketizer = new OpusRepacketizer();
                assertEquals(0, repacketizer.addPacket(half, 0, half.length));
                assertEquals(0, repacketizer.addPacket(half, 0, half.length));
                byte[] combined = new byte[1024]; int length = repacketizer.createPacket(combined, 0, combined.length);
                assertTrue(length > 0); result.add(Arrays.copyOf(combined, length));
            }
            return result;
        }
        var encoder = VoiceCodec.encoder(); encoder.setBitrate(bitrate); var result = new ArrayList<byte[]>();
        for (int frame = 0; frame < count; frame++) {
            short[] pcm = new short[packetSamples];
            for (int i = 0; i < pcm.length; i++) {
                double t = (frame * packetSamples + i) / 48000.0;
                pcm[i] = (short) (4200 * Math.sin(2 * Math.PI * 173 * t) + 2200 * Math.sin(2 * Math.PI * 713 * t));
            }
            byte[] packet = new byte[1024]; int size = encoder.encode(pcm, 0, packetSamples, packet, 0, packet.length);
            result.add(Arrays.copyOf(packet, size));
        }
        return result;
    }
    @Test void delayedFivePacketBurstsAndReorderingPreserveEveryDecodedSample() throws Exception {
        var packets = encode(960, 100); var reference = new OpusDecoder(48000, 1); var expected = new ArrayList<short[]>();
        for (byte[] packet : packets) expected.add(VoiceCodec.decode(reference, packet));
        var stream = new OpusPlayout();
        for (int tick = 0; tick < 102; tick++) {
            long now = tick * 20_000_000L;
            if (tick < 100 && tick % 5 == 0) for (int offset : new int[]{2, 0, 4, 1, 3}) stream.offer(tick + offset, packets.get(tick + offset), now);
            short[] actual = stream.poll(now);
            if (tick < 2) assertNull(actual);
            else assertArrayEquals(expected.get(tick - 2), actual, "Dropped/duplicated audio at output frame " + (tick - 2));
        }
    }
    @Test void tenToOneHundredTwentyMillisecondPacketsJoinAndSplitWithoutPaddingOrLoss() throws Exception {
        for (int size : new int[]{480, 1920, 2880, 5760}) {
            int count = 11520 / size; var packets = encode(size, count); var reference = new OpusDecoder(48000, 1);
            short[] expected = new short[11520]; int offset = 0;
            var stream = new OpusPlayout();
            for (int i = 0; i < count; i++) {
                short[] decoded = VoiceCodec.decode(reference, packets.get(i)); System.arraycopy(decoded, 0, expected, offset, decoded.length); offset += decoded.length;
                stream.offer(i, packets.get(i), 0);
            }
            for (int frame = 0; frame < 12; frame++) assertArrayEquals(Arrays.copyOfRange(expected, frame * 960, (frame + 1) * 960), stream.poll(40_000_000L + frame * 20_000_000L), "Packet duration " + size / 48 + " ms");
        }
    }
    @Test void confirmedLossIsConcealedAndSpeechPausesDoNotDropTheNextWords() throws Exception {
        var packets = encode(960, 5); var stream = new OpusPlayout(); var decoder = new OpusDecoder(48000, 1);
        stream.offer(0, packets.get(0), 0); stream.offer(1, packets.get(1), 0); stream.offer(3, packets.get(3), 0);
        assertArrayEquals(VoiceCodec.decode(decoder, packets.get(0)), stream.poll(40_000_000));
        assertArrayEquals(VoiceCodec.decode(decoder, packets.get(1)), stream.poll(60_000_000));
        assertArrayEquals(VoiceCodec.conceal(decoder, 960), stream.poll(80_000_000));
        assertArrayEquals(VoiceCodec.decode(decoder, packets.get(3)), stream.poll(100_000_000));
        assertNull(stream.poll(120_000_000)); assertNull(stream.poll(300_000_000));
        stream.offer(4, packets.get(4), 500_000_000);
        assertArrayEquals(VoiceCodec.decode(decoder, packets.get(4)), stream.poll(540_000_000));
        stream.offer(0, packets.get(0), 1_000_000_000L);
        decoder.resetState(); assertArrayEquals(VoiceCodec.decode(decoder, packets.get(0)), stream.poll(1_040_000_000L));
    }
    @Test void localMonitorBuffersInputBurstsInsteadOfAlternatingAudioAndSilence() {
        var buffer = new PcmFrameBuffer();
        assertNull(buffer.monitor(0));
        short[] first = new short[960]; Arrays.fill(first, (short) 7000); buffer.offer(first, 0);
        assertNull(buffer.monitor(0)); assertNull(buffer.monitor(20_000_000));
        buffer.offer(first, 20_000_000); assertArrayEquals(first, buffer.monitor(20_000_000));
        assertArrayEquals(first, buffer.monitor(40_000_000)); assertNull(buffer.monitor(60_000_000));
        for (int tick = 0; tick < 100; tick++) {
            if (tick % 5 == 0) for (int i = 0; i < 5; i++) buffer.offer(first, tick * 20_000_000L);
            assertArrayEquals(first, buffer.monitor(tick * 20_000_000L));
        }
    }
}
