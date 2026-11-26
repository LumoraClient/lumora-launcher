package dev.lumora.voice.protocol;

import io.github.jaredmdobson.concentus.*;
import java.util.Arrays;

/** Pure Java Opus: no downloaded or extracted native executables. */
public final class VoiceCodec {
    public static final int RATE=48000,SAMPLES=960;
    private VoiceCodec(){}
    public static OpusEncoder encoder()throws OpusException{var e=new OpusEncoder(RATE,1,OpusApplication.OPUS_APPLICATION_VOIP);e.setBitrate(64000);e.setComplexity(5);e.setUseInbandFEC(true);e.setPacketLossPercent(5);return e;}
    public static byte[] encode(OpusEncoder encoder,short[] pcm)throws OpusException{if(pcm.length!=SAMPLES)throw new IllegalArgumentException("Expected 20 ms PCM");byte[] out=new byte[VoiceProtocol.MAX_OPUS];return Arrays.copyOf(out,encoder.encode(pcm,0,SAMPLES,out,0,out.length));}
    public static short[] decode(OpusDecoder decoder,byte[] packet)throws OpusException{short[] out=new short[SAMPLES*6];int length=decoder.decode(packet,0,packet==null?0:packet.length,out,0,packet==null?SAMPLES:out.length,false);return Arrays.copyOf(out,length);}
    public static short[] conceal(OpusDecoder decoder,int samples)throws OpusException{short[] out=new short[samples];decoder.decode(null,0,0,out,0,samples,false);return out;}
    public static void mix(short[] pcm,float gain,float pan,float[] stereo){
        float boundedPan=Math.max(-1,Math.min(1,pan)),boundedGain=Math.max(0,Math.min(2,gain));
        float left=(float)Math.sqrt((1-boundedPan)*.5)*boundedGain,right=(float)Math.sqrt((1+boundedPan)*.5)*boundedGain;
        for(int i=0;i<Math.min(pcm.length,SAMPLES);i++){stereo[i*2]+=pcm[i]*left;stereo[i*2+1]+=pcm[i]*right;}
    }
    public static byte[] output(float[] stereo){byte[] out=new byte[stereo.length*2];for(int i=0;i<stereo.length;i++){int sample=Math.max(-32768,Math.min(32767,Math.round(stereo[i])));out[i*2]=(byte)sample;out[i*2+1]=(byte)(sample>>8);}return out;}
}

