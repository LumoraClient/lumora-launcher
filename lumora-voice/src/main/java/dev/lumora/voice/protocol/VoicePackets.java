package dev.lumora.voice.protocol;

/** Packet IDs verified against the VoiceChat 2.1.1 protocol registry (protocol version 6). */
public final class VoicePackets {
    private VoicePackets() {}
    public static final int CREATE_CHANNEL = 29, UPDATE_CHANNEL = 30, DELETE_CHANNEL = 31, MOVE_USER = 32, USER_PROPERTIES = 33;
    public static final int CHANNEL_SHOW = 34, CHANNEL_UPDATE = 35, CHANNEL_HIDE = 36;
    public static final int USER_SHOW = 37, USER_UPDATE = 38, USER_SWITCH = 39, USER_HIDE = 40, CHANNEL_ALERT = 41;
    public static final int DISCONNECT = 42, SUBSCRIBE_CHANNELS = 43, RESET_CHANNEL_REQUEST = 44, CHANNEL_RESET = 45;
}
