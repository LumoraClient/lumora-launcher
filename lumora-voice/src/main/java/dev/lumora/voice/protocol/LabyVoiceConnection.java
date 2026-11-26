package dev.lumora.voice.protocol;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/** Connects with the player's real Mojang session; no borrowed identities or client branding. */
public final class LabyVoiceConnection implements VoiceService {
    @FunctionalInterface public interface Authenticator{void join(String hash)throws Exception;}
    @FunctionalInterface public interface AudioReceiver{void receive(UUID id,long sequence,byte[] data);}
    private final VoiceSendQueue queue = new VoiceSendQueue();
    private final Consumer<String> status;
    private final AudioReceiver audio;
    private volatile boolean running=true,connected;
    private volatile DatagramSocket socket;
    private volatile Set<UUID> visible=Set.of();
    private long sequence;
    private final String relayHost;
    private final int relayPort;
    private final VoiceChannels channels;
    private final VoicePresence presence = new VoicePresence();
    private volatile boolean staff;
    public LabyVoiceConnection(UUID uuid,String name,String host,int port,Authenticator authenticator,Consumer<String> status,AudioReceiver audio){this(uuid,name,host,port,false,false,authenticator,status,audio);}
    public LabyVoiceConnection(UUID uuid,String name,String host,int port,boolean backup,boolean test,Authenticator authenticator,Consumer<String> status,AudioReceiver audio){
        this.status=status;this.audio=audio;relayHost=backup?"voice2.labymod.net":"voice.labymod.net";relayPort=test?8067:8066;channels=new VoiceChannels(uuid);
        Thread thread=new Thread(()->run(uuid,name,host,port,authenticator),"LumoraVoice Network");thread.setDaemon(true);thread.start();
    }
    @Override public String name(){return "Laby Voice";}
    public VoiceChannels channels(){return channels;}
    public boolean staff(){return staff;}
    public boolean participant(UUID id){return connected()&&(presence.contains(id)||channels.user(id)!=null);}
    @Override public boolean otherGroup(UUID id){return connected()&&channels.otherGroup(id);}
    public void control(byte[] payload){if(connected())queue.control(payload);}
    public boolean connected(){return running&&connected;}
    public void visible(Set<UUID> ids){visible=Set.copyOf(ids);}
    public void audio(byte[] opus){if(!connected()||serverMuted)return;try{queue.audio(VoiceProtocol.audio(sequence++,opus),System.nanoTime());}catch(IOException ignored){}}
    public void silenceAudio(){queue.silence();}
    private void run(UUID uuid,String name,String host,int port,Authenticator authenticator){
        try{
            status.accept("Laby-Sprachdienst wird verbunden …");
            byte[] rawKey;
            try(var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()){
                var response=http.send(HttpRequest.newBuilder(URI.create("https://dl.labymod.net/labyconnect/voice/key.pub")).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
                if(response.statusCode()!=200||response.body().length>8192)throw new IOException("Laby-Schlüssel ist momentan nicht verfügbar");rawKey=response.body();
            }
            if(!running)return;
            String base64=new String(rawKey,java.nio.charset.StandardCharsets.US_ASCII).replaceAll("-----[^-]+-----","").replaceAll("\\s","");
            var key=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));var protocol=new VoiceProtocol(key);
            status.accept("Minecraft-Identität wird bestätigt …");try{authenticator.join(protocol.authenticationHash());}catch(Exception rejected){throw new IOException("Die Minecraft-Sitzung konnte nicht bestätigt werden. Bitte im Launcher neu anmelden.");}if(!running)return;
            try(DatagramSocket datagram=new DatagramSocket()){
                socket=datagram;datagram.connect(new InetSocketAddress(relayHost,relayPort));datagram.setSoTimeout(5);
                var transport=new VoiceTransport(bytes->datagram.send(new DatagramPacket(bytes,bytes.length)));
                long started=System.currentTimeMillis(),lastReceived=started,lastVisible=0;Set<UUID> previous=Set.of();
                transport.reliable(protocol.handshake(uuid,name),started);
                byte[] buffer=new byte[VoiceProtocol.MAX_FRAME+10];
                while(running){
                    long now=System.currentTimeMillis();
                    if(!connected&&now-started>12000)throw new IOException("Laby-Sprachdienst nicht erreichbar. UDP-Port 8066 prüfen.");
                    if(connected&&now-lastReceived>35000)throw new IOException("Verbindung zum Laby-Sprachdienst unterbrochen.");
                    if(connected&&now-lastVisible>=1000){
                        Set<UUID> current=visible,added=new HashSet<>(current),removed=new HashSet<>(previous);added.removeAll(previous);removed.removeAll(current);
                        if(!removed.isEmpty())transport.reliable(protocol.seal(VoiceProtocol.visible(1,removed)),now);
                        if(!added.isEmpty())transport.reliable(protocol.seal(VoiceProtocol.visible(0,added)),now);
                        if(added.isEmpty()&&removed.isEmpty())transport.reliable(protocol.seal(VoiceProtocol.visible(2,current)),now);
                        previous=current;lastVisible=now;
                    }
                    for(int sent=0;sent<20;sent++){byte[] control=queue.control();if(control==null)break;transport.reliable(protocol.seal(control),now);}
                    byte[] voice=queue.audio(System.nanoTime());if(voice!=null)transport.unreliable(protocol.seal(voice));
                    transport.maintenance(now);
                    try{
                        var packet=new DatagramPacket(buffer,buffer.length);datagram.receive(packet);
                        for(byte[] frame:transport.receive(Arrays.copyOfRange(packet.getData(),packet.getOffset(),packet.getOffset()+packet.getLength()),now)){
                            byte[] payload;try{payload=protocol.open(frame);}catch(Exception malformed){continue;}
                            var in=new DataInputStream(new ByteArrayInputStream(payload));int id=in.readUnsignedByte();
                            lastReceived=now;
                            if(id==1){int version=in.readInt(),result=in.readUnsignedByte();if(version!=VoiceProtocol.VERSION)throw new IOException("Laby-Protokoll geändert (Version "+version+"). Adapter-Update erforderlich.");if(result==2)throw new IOException("Dein Konto ist bereits im Laby-VoiceChat verbunden. Andere Sitzung beenden und später erneut verbinden.");if(result!=0)throw new IOException("Laby hat die Minecraft-Anmeldung abgelehnt. Bitte mit einem gültigen Java-Konto anmelden.");staff=in.available()>0&&in.readBoolean();connected=true;transport.reliable(protocol.seal(VoiceProtocol.server(host,port)),now);transport.reliable(protocol.seal(VoiceProtocol.packet(43,out->{})),now);status.accept("Mit Laby Voice verbunden");}
                            else if(id==2)throw new IOException("Laby hat den Sitzungsschlüssel abgelehnt. Bitte erneut verbinden.");
                            else if(id==4){String reason=in.available()>=4?VoiceProtocol.string(in):"Verbindung beendet";throw new IOException("Laby: "+reason.substring(0,Math.min(160,reason.length())));}
                            else if(id==5){ // Respect server-side mute information. Never transmit until explicitly unmuted.
                                if(in.available()>=17){UUID target=VoiceProtocol.uuid(in);boolean muted=in.readBoolean();if(!muted)mutedUsers.remove(target);else if(mutedUsers.size()<1024)mutedUsers.add(target);if(target.equals(uuid)){serverMuted=muted;status.accept(muted?"Vom Laby-Sprachdienst stummgeschaltet":"Mit Laby Voice verbunden");}}
                            }
                            else if(id==9&&connected){UUID sender=VoiceProtocol.uuid(in);int length=in.readInt();if(length<9||length>VoiceProtocol.MAX_OPUS+8||length!=in.available())continue;long serial=in.readLong();if(serial<0||sender.equals(uuid)||(!visible.contains(sender)&&!channels.remoteAllowed(sender)))continue;audio.receive(sender,serial,in.readNBytes(length-8));}
                            else if(connected){try{if(!presence.receive(id,in))channels.receive(id,in);}catch(IOException malformed){/* Reject malformed presence/channel updates. */}}
                            // KeepAlive (3) needs no acknowledgement beyond transport ACKs.
                        }
                    }catch(SocketTimeoutException ignored){}
                }
                if(connected){try{transport.reliable(protocol.seal(VoiceProtocol.packet(42,out->VoiceProtocol.string(out,""))),System.currentTimeMillis());}catch(Exception ignored){}}
            }
        }catch(Exception e){if(running)status.accept("Fehler: "+friendly(e));}
        finally{connected=false;running=false;socket=null;queue.clear();presence.clear();mutedUsers.clear();}
    }
    private volatile boolean serverMuted;
    /** Players the Laby voice service muted (packet 5 names the target; you yourself included). */
    private final Set<UUID> mutedUsers=java.util.concurrent.ConcurrentHashMap.newKeySet();
    @Override public boolean serverMuted(UUID id){return connected()&&mutedUsers.contains(id);}
    public boolean serverMuted(){return serverMuted;}
    private static String friendly(Exception e){String message=e.getMessage();if(message==null||message.length()>220)return "Sprachverbindung fehlgeschlagen. Bitte erneut versuchen.";return message.replaceAll("[\\p{Cntrl}]"," ");}
    @Override public void close(){running=false;queue.clear();}
}

