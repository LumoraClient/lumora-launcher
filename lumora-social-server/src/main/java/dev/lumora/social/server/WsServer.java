package dev.lumora.social.server;

import javax.net.ssl.SSLContext;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal WebSocket server (RFC 6455, text messages only) on plain or TLS sockets, one virtual thread per connection
 * plus one writer thread each. Limits: 8 KiB request header, 64 KiB per frame, 256 KiB per message, 1000 queued
 * outgoing frames (a client that does not read is dropped), {@value #MAX_PER_IP} connections per address. Every
 * connection is pinged every 30 s and closed after 90 s without any frame. {@code GET /health} answers "ok" for
 * reverse-proxy checks.
 */
final class WsServer implements Closeable {
    static final int MAX_PER_IP = 16;
    private static final int MAX_HEADER = 8192;
    private static final int MAX_FRAME = 64 * 1024;
    private static final int MAX_MESSAGE = 256 * 1024;
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /** Callbacks of one connection; called on the connection's reader thread, one at a time. */
    interface Session {
        void onText(String text);

        void onClose();
    }

    interface Handler {
        Session open(Conn conn);
    }

    private final ServerSocket server;
    private final Handler handler;
    private final Set<Conn> conns = ConcurrentHashMap.newKeySet();
    private final Map<InetAddress, AtomicInteger> perIp = new ConcurrentHashMap<>();
    private final ScheduledExecutorService pinger = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-ping");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean closed;

    WsServer(InetSocketAddress bind, SSLContext tls, Handler handler) throws IOException {
        this.handler = handler;
        this.server = tls == null ? new ServerSocket() : tls.getServerSocketFactory().createServerSocket();
        server.setReuseAddress(true);
        server.bind(bind, 128);
        Thread accept = new Thread(this::acceptLoop, "ws-accept");
        accept.setDaemon(true);
        accept.start();
        pinger.scheduleAtFixedRate(this::pingAll, 30, 30, TimeUnit.SECONDS);
    }

    int port() {
        return server.getLocalPort();
    }

    private void acceptLoop() {
        while (!closed) {
            Socket s;
            try {
                s = server.accept();
            } catch (IOException e) {
                if (closed) {
                    return;
                }
                continue;
            }
            InetAddress ip = s.getInetAddress();
            AtomicInteger n = perIp.computeIfAbsent(ip, k -> new AtomicInteger());
            if (n.incrementAndGet() > MAX_PER_IP) {
                n.decrementAndGet();
                closeQuietly(s);
                continue;
            }
            Thread.ofVirtual().name("ws-conn").start(() -> {
                try {
                    serve(s);
                } finally {
                    if (n.decrementAndGet() <= 0) {
                        perIp.remove(ip, n);
                    }
                }
            });
        }
    }

    private void pingAll() {
        long now = System.currentTimeMillis();
        for (Conn c : conns) {
            if (now - c.lastFrame > 90_000) {
                c.close(1001, "timeout");
            } else {
                c.queue(frame(0x9, new byte[0]));
            }
        }
    }

    private void serve(Socket s) {
        Conn conn = null;
        Session session = null;
        try {
            s.setSoTimeout(10_000);
            s.setTcpNoDelay(true);
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            Map<String, String> headers = new HashMap<>();
            String requestLine = readHead(in, headers);
            if (requestLine == null) {
                return;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length != 3 || !parts[0].equals("GET")) {
                respond(out, "405 Method Not Allowed", "");
                return;
            }
            String path = parts[1];
            if (path.equals("/health")) {
                respond(out, "200 OK", "ok");
                return;
            }
            String key = headers.get("sec-websocket-key");
            if (!"websocket".equalsIgnoreCase(headers.getOrDefault("upgrade", ""))
                    || !headers.getOrDefault("connection", "").toLowerCase(Locale.ROOT).contains("upgrade")
                    || !"13".equals(headers.get("sec-websocket-version")) || key == null || key.length() > 64) {
                respond(out, "426 Upgrade Required", "Lumora Social: WebSocket expected");
                return;
            }
            String accept = Base64.getEncoder().encodeToString(sha1((key.strip() + GUID).getBytes(StandardCharsets.US_ASCII)));
            out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            s.setSoTimeout(120_000);
            conn = new Conn(s, out);
            conns.add(conn);
            session = handler.open(conn);
            readLoop(in, conn, session);
        } catch (IOException e) {
            // connection ended
        } finally {
            if (conn != null) {
                conns.remove(conn);
                conn.close(1000, "");
            } else {
                closeQuietly(s);
            }
            if (session != null) {
                session.onClose();
            }
        }
    }

    private void readLoop(InputStream in, Conn conn, Session session) throws IOException {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        boolean inMessage = false;
        while (!conn.closed) {
            int b0;
            try {
                b0 = in.read();
            } catch (SocketTimeoutException e) {
                return;
            }
            if (b0 < 0) {
                return;
            }
            int b1 = readByte(in);
            boolean fin = (b0 & 0x80) != 0;
            int opcode = b0 & 0x0F;
            if ((b0 & 0x70) != 0 || (b1 & 0x80) == 0) {
                conn.close(1002, "protocol error"); // reserved bits / unmasked client frame
                return;
            }
            long len = b1 & 0x7F;
            if (len == 126) {
                len = (readByte(in) << 8) | readByte(in);
            } else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) {
                    len = (len << 8) | readByte(in);
                }
            }
            if (len > MAX_FRAME) {
                conn.close(1009, "frame too large");
                return;
            }
            byte[] mask = in.readNBytes(4);
            byte[] payload = in.readNBytes((int) len);
            if (mask.length != 4 || payload.length != len) {
                return;
            }
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= mask[i & 3];
            }
            conn.lastFrame = System.currentTimeMillis();
            switch (opcode) {
                case 0x1, 0x0 -> {
                    if (opcode == 0x1 && inMessage || opcode == 0x0 && !inMessage) {
                        conn.close(1002, "protocol error");
                        return;
                    }
                    inMessage = true;
                    if (message.size() + payload.length > MAX_MESSAGE) {
                        conn.close(1009, "message too large");
                        return;
                    }
                    message.write(payload);
                    if (fin) {
                        String text;
                        try {
                            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                                    .decode(ByteBuffer.wrap(message.toByteArray())).toString();
                        } catch (CharacterCodingException e) {
                            conn.close(1007, "invalid utf-8");
                            return;
                        }
                        message.reset();
                        inMessage = false;
                        if (!conn.allow()) {
                            conn.close(1008, "rate limit");
                            return;
                        }
                        session.onText(text);
                    }
                }
                case 0x8 -> {
                    conn.close(1000, "");
                    return;
                }
                case 0x9 -> conn.queue(frame(0xA, payload.length <= 125 ? payload : new byte[0]));
                case 0xA -> {
                    // pong: lastFrame already updated
                }
                default -> {
                    conn.close(1003, "text only");
                    return;
                }
            }
        }
    }

    private static int readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new EOFException();
        }
        return b;
    }

    private static String readHead(InputStream in, Map<String, String> headers) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        String requestLine = null;
        int total = 0;
        while (true) {
            int b = in.read();
            if (b < 0 || ++total > MAX_HEADER) {
                return null;
            }
            if (b == '\n') {
                String l = line.toString(StandardCharsets.ISO_8859_1).replace("\r", "");
                line.reset();
                if (l.isEmpty()) {
                    return requestLine;
                }
                if (requestLine == null) {
                    requestLine = l;
                } else {
                    int c = l.indexOf(':');
                    if (c > 0) {
                        headers.put(l.substring(0, c).strip().toLowerCase(Locale.ROOT), l.substring(c + 1).strip());
                    }
                }
            } else {
                line.write(b);
            }
        }
    }

    private static void respond(OutputStream out, String status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 " + status + "\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: " + b.length
                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(b);
        out.flush();
    }

    static byte[] frame(int opcode, byte[] payload) {
        int len = payload.length;
        int head = len <= 125 ? 2 : len <= 0xFFFF ? 4 : 10;
        byte[] f = new byte[head + len];
        f[0] = (byte) (0x80 | opcode);
        if (len <= 125) {
            f[1] = (byte) len;
        } else if (len <= 0xFFFF) {
            f[1] = 126;
            f[2] = (byte) (len >>> 8);
            f[3] = (byte) len;
        } else {
            f[1] = 127;
            for (int i = 0; i < 8; i++) {
                f[2 + i] = (byte) ((long) len >>> (56 - 8 * i));
            }
        }
        System.arraycopy(payload, 0, f, head, len);
        return f;
    }

    private static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    @Override
    public void close() {
        closed = true;
        pinger.shutdownNow();
        try {
            server.close();
        } catch (IOException ignored) {
            // closing
        }
        for (Conn c : conns) {
            c.close(1001, "server stopping");
        }
    }

    /** One open WebSocket: thread-safe {@link #send}, own writer thread. */
    static final class Conn {
        private final Socket socket;
        private final OutputStream out;
        private final BlockingQueue<byte[]> outbox = new ArrayBlockingQueue<>(1000);
        private volatile boolean closed;
        volatile long lastFrame = System.currentTimeMillis();
        // token bucket: 20 messages per second, burst 60
        private double tokens = 60;
        private long refill = System.nanoTime();

        Conn(Socket socket, OutputStream out) {
            this.socket = socket;
            this.out = out;
            Thread.ofVirtual().name("ws-writer").start(this::writeLoop);
        }

        String address() {
            return socket.getInetAddress().getHostAddress();
        }

        void send(String text) {
            queue(frame(0x1, text.getBytes(StandardCharsets.UTF_8)));
        }

        void queue(byte[] frame) {
            if (closed) {
                return;
            }
            if (!outbox.offer(frame)) {
                close(1008, "too slow"); // client does not read
            }
        }

        synchronized boolean allow() {
            long now = System.nanoTime();
            tokens = Math.min(60, tokens + (now - refill) / 1e9 * 20);
            refill = now;
            if (tokens < 1) {
                return false;
            }
            tokens--;
            return true;
        }

        private void writeLoop() {
            try {
                while (!closed || !outbox.isEmpty()) {
                    byte[] f = outbox.poll(1, TimeUnit.SECONDS);
                    if (f != null) {
                        out.write(f);
                        out.flush();
                        if ((f[0] & 0x0F) == 0x8) {
                            break;
                        }
                    }
                }
            } catch (IOException | InterruptedException e) {
                // socket gone
            } finally {
                closed = true;
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // already closed
                }
            }
        }

        void close(int code, String reason) {
            if (closed) {
                return;
            }
            byte[] r = reason.getBytes(StandardCharsets.UTF_8);
            byte[] p = new byte[2 + Math.min(r.length, 120)];
            p[0] = (byte) (code >>> 8);
            p[1] = (byte) code;
            System.arraycopy(r, 0, p, 2, p.length - 2);
            outbox.offer(frame(0x8, p));
            closed = true;
            try {
                socket.shutdownInput();
            } catch (IOException | UnsupportedOperationException ignored) {
                // TLS sockets cannot half-close; the writer closes the socket
            }
        }

        boolean isClosed() {
            return closed;
        }
    }
}
