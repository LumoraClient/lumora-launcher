package dev.lumora.voice.protocol;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Bounded, ordered, retransmitted datagrams; all methods run on one network thread. */
public final class VoiceTransport {
    @FunctionalInterface public interface Sender{void send(byte[] bytes)throws IOException;}
    private record Pending(byte[] data,long lastSent,int attempts){}
    private static final class Assembly{final byte[][] segments;final long created;int received;Assembly(int count,long time){segments=new byte[count][];created=time;}}
    private final Sender sender;
    private final Map<Integer,Pending> pending=new HashMap<>();
    private final Map<Integer,Assembly> incoming=new HashMap<>();
    private final Map<Integer,byte[]> complete=new HashMap<>();
    private int nextOutgoing,nextIncoming;
    public VoiceTransport(Sender sender){this.sender=sender;}
    public void unreliable(byte[] frame)throws IOException{if(frame.length>1400)throw new IOException("UDP-Voice-Paket zu groß");byte[] packet=new byte[frame.length+1];System.arraycopy(frame,0,packet,1,frame.length);sender.send(packet);}
    public void reliable(byte[] frame,long now)throws IOException{
        if(frame.length<1||frame.length>VoiceProtocol.MAX_FRAME||pending.size()>128)throw new IOException("Voice-Sendepuffer voll");
        int id=nextOutgoing++&65535,count=(frame.length+511)/512;
        for(int segment=0;segment<count;segment++){int length=Math.min(512,frame.length-segment*512);var buffer=ByteBuffer.allocate(9+length);buffer.put((byte)1).putShort((short)id).putShort((short)segment).putShort((short)count).putShort((short)length).put(frame,segment*512,length);byte[] bytes=buffer.array();pending.put((id<<16)|segment,new Pending(bytes,now,1));sender.send(bytes);}
    }
    public List<byte[]> receive(byte[] packet,long now)throws IOException{
        if(packet.length<1||packet.length>VoiceProtocol.MAX_FRAME+9)throw new IOException("Ungültiges Voice-Datagramm");
        var in=ByteBuffer.wrap(packet);int type=in.get()&255;
        if(type==0){if(in.remaining()<1||in.remaining()>1400)throw new IOException("Ungültiges Audio-Datagramm");return List.of(Arrays.copyOfRange(packet,1,packet.length));}
        if(type==2){if(in.remaining()!=4)throw new IOException("Ungültiges ACK");int id=in.getShort()&65535,segment=in.getShort()&65535;pending.remove((id<<16)|segment);return List.of();}
        if(type!=1||in.remaining()<8)throw new IOException("Unbekannter Voice-Transport");
        int id=in.getShort()&65535,segment=in.getShort()&65535,count=in.getShort()&65535,length=in.getShort()&65535;
        if(count<1||count>32||segment>=count||length>512||length!=in.remaining())throw new IOException("Ungültiges Voice-Segment");
        sender.send(ByteBuffer.allocate(5).put((byte)2).putShort((short)id).putShort((short)segment).array());
        if(count==1&&length==0){nextIncoming=0;incoming.clear();complete.clear();return List.of();}
        if(((id-nextIncoming)&65535)>=32768)return List.of();
        if(incoming.size()+complete.size()>=64&&!incoming.containsKey(id))throw new IOException("Voice-Empfangspuffer voll");
        if(complete.containsKey(id))return drain();
        Assembly assembly=incoming.computeIfAbsent(id,k->new Assembly(count,now));if(assembly.segments.length!=count)throw new IOException("Widersprüchliche Voice-Fragmente");
        if(assembly.segments[segment]==null){byte[] data=new byte[length];in.get(data);assembly.segments[segment]=data;assembly.received++;}
        if(assembly.received==count){var bytes=new ByteArrayOutputStream();for(byte[] data:assembly.segments)bytes.write(data);incoming.remove(id);complete.put(id,bytes.toByteArray());}
        return drain();
    }
    private List<byte[]> drain(){List<byte[]> result=new ArrayList<>();while(complete.containsKey(nextIncoming)){result.add(complete.remove(nextIncoming));nextIncoming=(nextIncoming+1)&65535;}return result;}
    public void maintenance(long now)throws IOException{
        for(var entry:pending.entrySet()){Pending p=entry.getValue();if(now-p.lastSent<500)continue;if(p.attempts>=8)throw new IOException("Laby-Sprachdienst antwortet nicht (UDP 8066)");sender.send(p.data);entry.setValue(new Pending(p.data,now,p.attempts+1));}
        if(incoming.values().stream().anyMatch(a->now-a.created>10000))throw new IOException("Unvollständige Voice-Übertragung");
    }
}

