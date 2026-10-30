package dev.lumora.launcher.core.integrations;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.util.Log;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Discord Rich Presence over the local Discord app's IPC socket (named pipe {@code \\.\pipe\discord-ipc-N} on Windows,
 * Unix socket {@code discord-ipc-N} elsewhere), the protocol Discord documents for its Game SDK / RPC:
 * frames of {@code [opcode int32 LE][length int32 LE][JSON]}, opcode 0 = handshake, 1 = command.
 * Needs the user's own Discord application ID; nothing leaves the computer except to the local Discord app. Errors
 * (Discord not running) are logged once and never reach the UI.
 */
public final class DiscordPresence implements Closeable {
    static final int OP_HANDSHAKE = 0;
    static final int OP_FRAME = 1;
    static final int OP_CLOSE = 2;

    /** One connection to the Discord app. */
    interface Transport extends Closeable {
        void write(byte[] data) throws IOException;

        /** Reads exactly n bytes. */
        byte[] read(int n) throws IOException;
    }

    /** What the presence shows. */
    public record Activity(String details, String state, long startEpochSeconds) {
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "discord-presence");
        t.setDaemon(true);
        return t;
    });
    private final java.util.function.Supplier<Transport> connector;
    private Transport transport;
    private String connectedId;
    private boolean warned;

    public DiscordPresence() {
        this(DiscordPresence::openLocal);
    }

    DiscordPresence(java.util.function.Supplier<Transport> connector) {
        this.connector = connector;
    }

    /** Shows the activity if Rich Presence is on and configured, else clears it. Asynchronous. */
    public void update(LauncherConfig config, Activity activity) {
        boolean on = config.discordRichPresence && LauncherConfig.isValidDiscordAppId(config.discordAppId);
        String appId = on ? config.discordAppId.strip() : null;
        io.submit(() -> apply(appId, on ? activity : null));
    }

    private void apply(String appId, Activity activity) {
        try {
            if (appId == null) {
                if (transport != null) {
                    send(OP_FRAME, activityCommand(null));
                    disconnect();
                }
                return;
            }
            if (transport == null || !appId.equals(connectedId)) {
                disconnect();
                Transport t = connector.get();
                if (t == null) {
                    warnOnce("Discord läuft nicht (kein IPC-Kanal gefunden)");
                    return;
                }
                transport = t;
                connectedId = appId;
                JsonObject hello = new JsonObject();
                hello.addProperty("v", 1);
                hello.addProperty("client_id", appId);
                send(OP_HANDSHAKE, hello.toString());
                readFrame(); // READY or an error
            }
            send(OP_FRAME, activityCommand(activity));
            readFrame();
            warned = false;
        } catch (IOException | RuntimeException e) {
            warnOnce("Discord Rich Presence: " + e.getMessage());
            disconnect();
        }
    }

    private void warnOnce(String message) {
        if (!warned) {
            warned = true;
            Log.info(message);
        }
    }

    private void send(int op, String json) throws IOException {
        transport.write(encode(op, json));
    }

    private JsonObject readFrame() throws IOException {
        ByteBuffer head = ByteBuffer.wrap(transport.read(8)).order(ByteOrder.LITTLE_ENDIAN);
        int op = head.getInt();
        int len = head.getInt();
        if (len < 0 || len > 1 << 20) {
            throw new IOException("ungültige Antwort von Discord");
        }
        String body = new String(transport.read(len), StandardCharsets.UTF_8);
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        if (op == OP_CLOSE || (o.has("evt") && "ERROR".equals(o.get("evt").getAsString()))) {
            String msg = o.has("message") ? o.get("message").getAsString()
                    : o.has("data") && o.getAsJsonObject("data").has("message") ? o.getAsJsonObject("data").get("message").getAsString() : body;
            throw new IOException("Discord hat abgelehnt: " + msg);
        }
        return o;
    }

    private void disconnect() {
        if (transport != null) {
            try {
                transport.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
        transport = null;
        connectedId = null;
    }

    /** Waits until all queued updates ran (tests). */
    void awaitIdle() throws Exception {
        io.submit(() -> { }).get(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        io.submit(this::disconnect);
        io.shutdown();
    }

    /** SET_ACTIVITY command; null activity clears the presence. */
    static String activityCommand(Activity a) {
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        if (a != null) {
            JsonObject act = new JsonObject();
            if (a.details() != null && !a.details().isBlank()) {
                act.addProperty("details", limit(a.details()));
            }
            if (a.state() != null && !a.state().isBlank()) {
                act.addProperty("state", limit(a.state()));
            }
            if (a.startEpochSeconds() > 0) {
                JsonObject ts = new JsonObject();
                ts.addProperty("start", a.startEpochSeconds());
                act.add("timestamps", ts);
            }
            // shown if the Discord application has an art asset named "lumora"
            JsonObject assets = new JsonObject();
            assets.addProperty("large_image", "lumora");
            assets.addProperty("large_text", "Lumora Client");
            act.add("assets", assets);
            args.add("activity", act);
        }
        JsonObject cmd = new JsonObject();
        cmd.addProperty("cmd", "SET_ACTIVITY");
        cmd.add("args", args);
        cmd.addProperty("nonce", UUID.randomUUID().toString());
        return cmd.toString();
    }

    /** Discord allows 2..128 characters per field. */
    private static String limit(String s) {
        String t = s.strip();
        if (t.length() < 2) {
            t = t + "  ";
        }
        return t.length() > 128 ? t.substring(0, 127) + "…" : t;
    }

    static byte[] encode(int op, String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer b = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(op).putInt(body.length).put(body);
        return b.array();
    }

    /** First reachable discord-ipc-0..9 of the local Discord app, null if Discord does not run. */
    private static Transport openLocal() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        for (int i = 0; i < 10; i++) {
            try {
                if (windows) {
                    return new PipeTransport(new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw"));
                }
                for (String env : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
                    String dir = System.getenv(env);
                    if (dir != null && Files.exists(Path.of(dir, "discord-ipc-" + i))) {
                        SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                        ch.connect(UnixDomainSocketAddress.of(Path.of(dir, "discord-ipc-" + i)));
                        return new SocketTransport(ch);
                    }
                }
                if (Files.exists(Path.of("/tmp/discord-ipc-" + i))) {
                    SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                    ch.connect(UnixDomainSocketAddress.of(Path.of("/tmp/discord-ipc-" + i)));
                    return new SocketTransport(ch);
                }
            } catch (IOException | RuntimeException e) {
                // try the next one
            }
        }
        return null;
    }

    private record PipeTransport(RandomAccessFile file) implements Transport {
        @Override
        public void write(byte[] data) throws IOException {
            file.write(data);
        }

        @Override
        public byte[] read(int n) throws IOException {
            byte[] b = new byte[n];
            file.readFully(b);
            return b;
        }

        @Override
        public void close() throws IOException {
            file.close();
        }
    }

    private record SocketTransport(SocketChannel channel) implements Transport {
        @Override
        public void write(byte[] data) throws IOException {
            ByteBuffer b = ByteBuffer.wrap(data);
            while (b.hasRemaining()) {
                channel.write(b);
            }
        }

        @Override
        public byte[] read(int n) throws IOException {
            ByteBuffer b = ByteBuffer.allocate(n);
            while (b.hasRemaining()) {
                if (channel.read(b) < 0) {
                    throw new EOFException("Discord hat die Verbindung geschlossen");
                }
            }
            return b.array();
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }
}
