package dev.lumora.core.connection;

import dev.lumora.api.connection.DisconnectKind;

import java.util.List;
import java.util.Locale;

/**
 * Sorts a disconnect message into a {@link DisconnectKind}. Minecraft's own reasons are recognised by their
 * translation keys (language independent); kick messages of server software and the exception texts old versions show
 * ("Connection refused", "UnknownHostException", "Connection throttled") by their wording.
 */
public final class DisconnectClassifier {
    private DisconnectClassifier() {
    }

    /**
     * @param keys translation keys found in the message (the message itself and its arguments), may be empty
     * @param text the message as plain text
     */
    public static DisconnectKind classify(List<String> keys, String text) {
        for (String k : keys) {
            DisconnectKind kind = byKey(k);
            if (kind != null) {
                return kind;
            }
        }
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (t.isBlank()) {
            return DisconnectKind.OTHER;
        }
        if (t.contains("throttled") || t.contains("please wait before reconnecting") || t.contains("zu schnell")) {
            return DisconnectKind.THROTTLED;
        }
        if (t.contains("unknownhost") || t.contains("unknown host") || t.contains("unbekannter host")
                || t.contains("nodename nor servname") || t.contains("name or service not known")) {
            return DisconnectKind.UNKNOWN_HOST;
        }
        if (t.contains("timed out") || t.contains("timeout") || t.contains("zeitüberschreitung")) {
            return DisconnectKind.TIMEOUT;
        }
        if (t.contains("connection refused") || t.contains("connectexception") || t.contains("no route to host")
                || t.contains("network is unreachable") || t.contains("verbindung abgelehnt")) {
            return DisconnectKind.REFUSED;
        }
        if (t.contains("whitelist") || t.contains("white-list") || t.contains("white list")) {
            return DisconnectKind.WHITELIST;
        }
        if (t.contains("banned") || t.contains("gebannt") || t.contains("gesperrt") || t.contains("blacklisted")) {
            return DisconnectKind.BANNED;
        }
        if (t.contains("server is full") || t.contains("server full") || t.contains("server ist voll")) {
            return DisconnectKind.FULL;
        }
        if (t.contains("outdated") || t.contains("incompatible client") || t.contains("please use") || t.contains("veraltet")) {
            return DisconnectKind.OUTDATED;
        }
        if (t.contains("restart") || t.contains("server closed") || t.contains("shutting down") || t.contains("neustart")) {
            return DisconnectKind.SHUTDOWN;
        }
        if (t.contains("invalid session") || t.contains("failed to verify username") || t.contains("not authenticated")
                || t.contains("bad login") || t.contains("authentication servers")) {
            return DisconnectKind.AUTH;
        }
        if (t.contains("logged in from another location")) {
            return DisconnectKind.DUPLICATE_LOGIN;
        }
        if (t.contains("end of stream") || t.contains("connection reset") || t.contains("forcibly closed")
                || t.contains("connection lost") || t.contains("verbindung verloren")) {
            return DisconnectKind.LOST;
        }
        if (t.contains("kicked") || t.contains("gekickt")) {
            return DisconnectKind.KICKED;
        }
        return DisconnectKind.OTHER;
    }

    private static DisconnectKind byKey(String k) {
        if (k == null) {
            return null;
        }
        return switch (k) {
            case "connect.aborted", "disconnect.quitting" -> DisconnectKind.USER;
            case "disconnect.timeout", "connect.p2p.failed.timeout", "multiplayer.disconnect.slow_login" -> DisconnectKind.TIMEOUT;
            case "disconnect.unknownHost" -> DisconnectKind.UNKNOWN_HOST;
            case "disconnect.endOfStream", "disconnect.lost" -> null; // titles or too generic: keep looking at the arguments
            case "disconnect.loginFailedInfo.invalidSession", "disconnect.loginFailedInfo.serversUnavailable",
                 "disconnect.loginFailedInfo.insufficientPrivileges", "multiplayer.disconnect.unverified_username",
                 "multiplayer.disconnect.authservers_down", "multiplayer.disconnect.expired_public_key",
                 "multiplayer.disconnect.invalid_public_key_signature", "multiplayer.disconnect.invalid_public_key_signature.new",
                 "multiplayer.disconnect.missing_public_key", "multiplayer.disconnect.invalid_public_key" -> DisconnectKind.AUTH;
            case "disconnect.loginFailedInfo.userBanned", "multiplayer.disconnect.banned", "multiplayer.disconnect.banned.reason",
                 "multiplayer.disconnect.banned_ip.reason", "multiplayer.disconnect.ip_banned" -> DisconnectKind.BANNED;
            case "multiplayer.disconnect.not_whitelisted" -> DisconnectKind.WHITELIST;
            case "multiplayer.disconnect.server_full" -> DisconnectKind.FULL;
            case "multiplayer.disconnect.outdated_client", "multiplayer.disconnect.outdated_server",
                 "multiplayer.disconnect.incompatible" -> DisconnectKind.OUTDATED;
            case "multiplayer.disconnect.server_shutdown" -> DisconnectKind.SHUTDOWN;
            case "multiplayer.disconnect.duplicate_login", "multiplayer.disconnect.name_taken" -> DisconnectKind.DUPLICATE_LOGIN;
            case "multiplayer.disconnect.kicked", "multiplayer.disconnect.idling", "multiplayer.disconnect.flying",
                 "disconnect.spam", "disconnect.exceeded_packet_rate", "multiplayer.disconnect.illegal_characters",
                 "multiplayer.disconnect.code_of_conduct" -> DisconnectKind.KICKED;
            case "disconnect.packetError", "multiplayer.disconnect.invalid_packet", "multiplayer.disconnect.configuration_error",
                 "multiplayer.disconnect.missing_tags", "multiplayer.disconnect.unexpected_query_response",
                 "multiplayer.disconnect.invalid_player_data" -> DisconnectKind.PROTOCOL;
            default -> null;
        };
    }
}
