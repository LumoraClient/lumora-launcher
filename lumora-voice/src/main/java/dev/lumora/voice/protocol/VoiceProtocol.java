package dev.lumora.voice.protocol;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.*;

/** LumoraVoice's independent implementation of the publicly documented LM4 wire format.
 * Protocol references and third-party notices: docs/VOICECHAT.md. */
public final class VoiceProtocol {
    public static final int VERSION=6,MAX_FRAME=16384,MAX_OPUS=1024;
    private static final byte[] IDENTIFIER={0,(byte)128,26,(byte)177,(byte)177,31,(byte)220,78,(byte)128};
    private final PublicKey publicKey;
    private final SecretKey key;
    public VoiceProtocol(PublicKey publicKey)throws Exception{
        this.publicKey=publicKey;var generator=KeyGenerator.getInstance("AES");generator.init(128);key=generator.generateKey();
    }
    public String authenticationHash()throws Exception{
        var digest=MessageDigest.getInstance("SHA-1");digest.update("laby".getBytes(StandardCharsets.UTF_8));digest.update(key.getEncoded());digest.update(publicKey.getEncoded());return new BigInteger(digest.digest()).toString(16);
    }
    public byte[] handshake(UUID id,String name)throws Exception{
        byte[] plain=packet(0,out->{out.writeInt(VERSION);out.write(key.getEncoded());uuid(out,id);out.writeByte(1);string(out,name);});
        var rsa=Cipher.getInstance("RSA/ECB/PKCS1Padding");rsa.init(Cipher.ENCRYPT_MODE,publicKey);
        var encrypted=new ByteArrayOutputStream();
        for(int offset=0;offset<plain.length;offset+=200){byte[] block=rsa.doFinal(plain,offset,Math.min(200,plain.length-offset));if(block.length<128||block.length>383)throw new IOException("Nicht unterstützter Laby-RSA-Schlüssel");encrypted.write(block.length-128);encrypted.write(block);}
        return frame(0,IDENTIFIER,encrypted.toByteArray());
    }
    public byte[] seal(byte[] payload)throws Exception{
        if(payload.length>MAX_FRAME-64)throw new IOException("Voice-Paket zu groß");
        var cipher=Cipher.getInstance("AES/ECB/PKCS5Padding");cipher.init(Cipher.ENCRYPT_MODE,key);return frame(1,null,cipher.doFinal(payload));
    }
    public byte[] open(byte[] frame)throws Exception{
        if(frame.length<4||frame.length>MAX_FRAME)throw new IOException("Ungültige Voice-Frame-Länge");
        var in=new DataInputStream(new ByteArrayInputStream(frame));int flags=in.readUnsignedByte(),type=flags&3;
        if((flags&4)!=0){int n=varInt(in);if(n<0||n>64||n>in.available())throw new IOException("Ungültiger Voice-Identifier");in.skipNBytes(n);}
        if((flags&8)==0||in.readUnsignedByte()!=3)throw new IOException("Laby-Netzwerkversion nicht unterstützt");
        int length=varInt(in);if(length<=0||length>MAX_FRAME||length!=in.available())throw new IOException("Ungültiger Voice-Frame");
        byte[] body=in.readNBytes(length);
        // Once a session key exists, never trust unencrypted audio, kicks or moderation packets.
        if(type!=1)throw new IOException("Unverschlüsseltes Voice-Paket verworfen");
        var cipher=Cipher.getInstance("AES/ECB/PKCS5Padding");cipher.init(Cipher.DECRYPT_MODE,key);return cipher.doFinal(body);
    }
    private static byte[] frame(int type,byte[] identifier,byte[] body)throws IOException{
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(type|8|(identifier==null?0:4));
        if(identifier!=null){varInt(out,identifier.length);out.write(identifier);}out.writeByte(3);varInt(out,body.length);out.write(body);return bytes.toByteArray();
    }
    @FunctionalInterface public interface Writer{void write(DataOutputStream out)throws IOException;}
    public static byte[] packet(int id,Writer writer)throws IOException{var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(id);writer.write(out);return bytes.toByteArray();}
    public static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    public static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    public static void string(DataOutputStream out,String value)throws IOException{byte[] utf=value.getBytes(StandardCharsets.UTF_8);if(utf.length>1024)throw new IOException("Voice-Text zu lang");out.writeInt(utf.length);out.write(utf);}
    public static String string(DataInputStream in)throws IOException{int n=in.readInt();if(n<0||n>1024||n>in.available())throw new IOException("Ungültiger Voice-Text");return new String(in.readNBytes(n),StandardCharsets.UTF_8).replaceAll("[\\p{Cntrl}]"," ");}
    public static byte[] server(String host,int port)throws IOException{return packet(12,out->{string(out,host);out.writeInt(port);});}
    public static byte[] visible(int action,Collection<UUID> ids)throws IOException{if(ids.size()>128)throw new IOException("Zu viele Voice-Spieler");return packet(7,out->{out.writeByte(action);out.writeShort(ids.size());for(UUID id:ids)uuid(out,id);});}
    public static byte[] audio(long sequence,byte[] opus)throws IOException{if(opus.length<1||opus.length>MAX_OPUS)throw new IOException("Ungültiges Opus-Paket");return packet(8,out->{out.writeInt(opus.length+8);out.writeLong(sequence);out.write(opus);});}
    private static void varInt(DataOutputStream out,int n)throws IOException{do{int b=n&127;n>>>=7;out.writeByte(b|(n==0?0:128));}while(n!=0);}
    private static int varInt(DataInputStream in)throws IOException{int n=0;for(int i=0;i<5;i++){int b=in.readUnsignedByte();n|=(b&127)<<(i*7);if((b&128)==0){if(n<0)throw new IOException("Voice-Varint zu groß");return n;}}throw new IOException("Voice-Varint zu lang");}
}

