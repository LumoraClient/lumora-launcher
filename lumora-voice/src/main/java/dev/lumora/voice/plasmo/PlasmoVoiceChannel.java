package dev.lumora.voice.plasmo;

import dev.lumora.voice.VoiceLog;
import dev.lumora.voice.platform.VoicePlatform;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * The Minecraft plugin channels of Plasmo Voice ({@code plasmo:voice/v2} and the flag channel). Received control
 * packets go to the {@link Handler} on the client thread; unknown ones are ignored.
 */
public final class PlasmoVoiceChannel {
    /** Received control packets; all on the client thread. */
    public interface Handler {
        /** The server asks for our version and RSA key (the start of a Plasmo Voice connection). */
        void playerInfoRequest();
        void connection(PlasmoVoiceProtocol.Connection connection);
        void config(PlasmoVoiceProtocol.Config config);
        void players(List<PlasmoVoiceProtocol.PlayerInfo> players);
        void player(PlasmoVoiceProtocol.PlayerInfo player);
        void playerDisconnect(UUID id);
        void source(PlasmoVoiceProtocol.Source source);
    }

    private final VoicePlatform.Channel channel;

    private PlasmoVoiceChannel(VoicePlatform platform, Handler handler) {
        channel = platform.channel(PlasmoVoiceProtocol.CHANNEL, payload -> receive(handler, payload));
        platform.channel(PlasmoVoiceProtocol.FLAG_CHANNEL, ignored -> {});
    }

    /** The real Plasmo Voice mod uses the same channels; then LumoraVoice leaves them to it. */
    public static boolean conflictingMod(VoicePlatform platform) { return platform.modLoaded("plasmovoice"); }

    /** Registers the channels; {@code null} if the Plasmo Voice mod itself is installed. */
    public static PlasmoVoiceChannel register(VoicePlatform platform, Handler handler) {
        return conflictingMod(platform) ? null : new PlasmoVoiceChannel(platform, handler);
    }

    /** The server announced Plasmo Voice (mod or plugin). */
    public boolean available() { return channel.serverSupports(); }

    public void send(byte[] payload) { if (available()) channel.send(payload); }

    static void receive(Handler handler, byte[] payload) {
        int type = PlasmoVoiceProtocol.type(payload);
        try {
            switch (type) {
                case PlasmoVoiceProtocol.PLAYER_INFO_REQUEST -> handler.playerInfoRequest();
                case PlasmoVoiceProtocol.CONNECTION -> handler.connection(PlasmoVoiceProtocol.readConnection(payload));
                case PlasmoVoiceProtocol.CONFIG -> handler.config(PlasmoVoiceProtocol.readConfig(payload));
                case PlasmoVoiceProtocol.PLAYER_LIST -> handler.players(PlasmoVoiceProtocol.readPlayerList(payload));
                case PlasmoVoiceProtocol.PLAYER_INFO_UPDATE -> handler.player(PlasmoVoiceProtocol.readPlayerInfo(payload));
                case PlasmoVoiceProtocol.PLAYER_DISCONNECT -> handler.playerDisconnect(PlasmoVoiceProtocol.readUuid(payload));
                case PlasmoVoiceProtocol.SOURCE_INFO -> handler.source(PlasmoVoiceProtocol.readSource(payload));
                default -> { } // language, activations, source lines, action bar … are not needed
            }
        } catch (IOException | RuntimeException malformed) {
            VoiceLog.warn("Ignoring malformed Plasmo Voice packet " + type, malformed);
        }
    }
}
