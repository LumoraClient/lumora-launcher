package dev.lumora.voice.protocol;

import io.github.jaredmdobson.concentus.OpusDecoder;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.security.KeyPairGenerator;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VoiceProtocolTest {
    @Test void encryptedFramesRoundTripAndRejectTruncation()throws Exception{
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var protocol=new VoiceProtocol(generator.generateKeyPair().getPublic());
        byte[] packet=VoiceProtocol.server("example.org",25565),frame=protocol.seal(packet);assertArrayEquals(packet,protocol.open(frame));
        assertThrows(Exception.class,()->protocol.open(Arrays.copyOf(frame,frame.length-1)));byte[] clear=frame.clone();clear[0]=(byte)((clear[0]&~3)|2);assertThrows(Exception.class,()->protocol.open(clear));
        assertTrue(protocol.authenticationHash().matches("-?[0-9a-f]+"));assertTrue(protocol.handshake(UUID.randomUUID(),"LabyVoicePlayer").length>256);
    }
    @Test void transportReassemblesOutOfOrderAndAcknowledgesDuplicates()throws Exception{
        List<byte[]> sent=new ArrayList<>(),acks=new ArrayList<>();var send=new VoiceTransport(sent::add);var receive=new VoiceTransport(acks::add);
        byte[] original=new byte[1600];new Random(4).nextBytes(original);send.reliable(original,0);assertEquals(4,sent.size());
        List<byte[]> shuffled=new ArrayList<>(sent);Collections.reverse(shuffled);List<byte[]> result=new ArrayList<>();for(byte[] part:shuffled)result.addAll(receive.receive(part,10));
        assertEquals(1,result.size());assertArrayEquals(original,result.getFirst());assertTrue(receive.receive(sent.getFirst(),20).isEmpty());assertEquals(5,acks.size());
        for(byte[] ack:acks)send.receive(ack,30);sent.clear();send.maintenance(1000);assertTrue(sent.isEmpty());
    }
    @Test void reliablePacketsAreDeliveredInFrameOrder()throws Exception{
        List<byte[]> sent=new ArrayList<>();var out=new VoiceTransport(sent::add);var in=new VoiceTransport(bytes->{});out.reliable(new byte[]{1},0);out.reliable(new byte[]{2},0);
        assertTrue(in.receive(sent.get(1),1).isEmpty());var result=in.receive(sent.get(0),2);assertEquals(2,result.size());assertArrayEquals(new byte[]{1},result.get(0));assertArrayEquals(new byte[]{2},result.get(1));
        assertThrows(IOException.class,()->in.receive(new byte[]{1,0,0,0,0,0,127,0,0},0));
    }
    @Test void opusEncodesRealTwentyMillisecondFrames()throws Exception{
        var encoder=VoiceCodec.encoder();var decoder=new OpusDecoder(48000,1);short[] wave=new short[960];for(int i=0;i<wave.length;i++)wave[i]=(short)(Math.sin(i*2*Math.PI*440/48000)*7000);
        for(int n=0;n<4;n++){byte[] encoded=VoiceCodec.encode(encoder,wave);assertTrue(encoded.length>0&&encoded.length<=1024);short[] decoded=VoiceCodec.decode(decoder,encoded);assertEquals(960,decoded.length);}
        assertEquals(960,VoiceCodec.decode(decoder,null).length);
    }
    @Test void spatialMixerPansAndClipsSafely(){
        short[] mono=new short[960];Arrays.fill(mono,(short)20000);float[] mix=new float[1920];VoiceCodec.mix(mono,1,-1,mix);assertEquals(20000,mix[0],.1);assertEquals(0,mix[1],.1);
        VoiceCodec.mix(mono,1,-1,mix);byte[] out=VoiceCodec.output(mix);assertEquals(32767,(short)((out[0]&255)|(out[1]<<8)));assertEquals(0,(short)((out[2]&255)|(out[3]<<8)));
    }
}

