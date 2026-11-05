package dev.lumora.launcher.core.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One launcher per Lumora folder. The first start holds {@code launcher.lock} and listens on a loopback port
 * (written with a random per-run token to {@code launcher.port}); a second start sends that instance "show" and
 * ends, so the window that is waiting in the tray comes back instead of a second launcher.
 */
public final class SingleInstance implements AutoCloseable {
    public enum Request { SHOW, PING }

    static final String LOCK_FILE = "launcher.lock";
    static final String PORT_FILE = "launcher.port";
    private static final int MAX_LINE = 200;

    private final FileChannel channel;
    private final FileLock lock;
    private final ServerSocket server;
    private final Path portFile;
    private final String token;
    private final Object handlerLock = new Object();
    private Consumer<Request> handler;
    private boolean pendingShow;
    private volatile boolean closed;

    private SingleInstance(FileChannel channel, FileLock lock, ServerSocket server, Path portFile, String token) {
        this.channel = channel;
        this.lock = lock;
        this.server = server;
        this.portFile = portFile;
        this.token = token;
    }

    /** Takes the lock of {@code dir}; empty while another launcher holds it. */
    public static Optional<SingleInstance> claim(Path dir) throws IOException {
        Files.createDirectories(dir);
        FileChannel ch = FileChannel.open(dir.resolve(LOCK_FILE), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock l;
        try {
            l = ch.tryLock();
        } catch (OverlappingFileLockException e) {
            l = null; // held by this JVM
        } catch (IOException e) {
            ch.close();
            throw e;
        }
        if (l == null) {
            ch.close();
            return Optional.empty();
        }
        ServerSocket ss = null;
        try {
            ss = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
            byte[] raw = new byte[16];
            new SecureRandom().nextBytes(raw);
            String token = HexFormat.of().formatHex(raw);
            Path portFile = dir.resolve(PORT_FILE);
            Path tmp = dir.resolve(PORT_FILE + ".tmp");
            Files.writeString(tmp, ss.getLocalPort() + " " + token, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, portFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, portFile, StandardCopyOption.REPLACE_EXISTING);
            }
            SingleInstance si = new SingleInstance(ch, l, ss, portFile, token);
            Thread t = new Thread(si::serve, "Lumora single instance");
            t.setDaemon(true);
            t.start();
            return Optional.of(si);
        } catch (IOException | RuntimeException e) {
            if (ss != null) {
                ss.close();
            }
            l.release();
            ch.close();
            throw e;
        }
    }

    /**
     * Asks the launcher that holds the lock of {@code dir} to handle {@code request}. Retries until {@code wait} is over
     * (that launcher may just be starting). False if nobody answered or the lock became free in the meantime.
     */
    public static boolean signal(Path dir, Request request, Duration wait) {
        long end = System.nanoTime() + wait.toNanos();
        do {
            if (send(dir, request)) {
                return true;
            }
            if (lockFree(dir)) {
                return false;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.nanoTime() < end);
        return false;
    }

    /**
     * Receives the requests of later starts (on a background thread). A "show" that arrived before is delivered
     * right away.
     */
    public void onRequest(Consumer<Request> h) {
        boolean deliver;
        synchronized (handlerLock) {
            handler = h;
            deliver = pendingShow;
            pendingShow = false;
        }
        if (deliver && h != null) {
            h.accept(Request.SHOW);
        }
    }

    /** Releases the lock (a self-update starts the new launcher right after this). */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            server.close();
        } catch (IOException ignored) {
            // nothing to do
        }
        try {
            if (Files.isRegularFile(portFile) && Files.readString(portFile, StandardCharsets.UTF_8).endsWith(" " + token)) {
                Files.delete(portFile);
            }
        } catch (IOException ignored) {
            // the next launcher overwrites it
        }
        try {
            lock.release();
            channel.close();
        } catch (IOException ignored) {
            // the OS frees it when the process ends
        }
    }

    private void serve() {
        while (!closed) {
            try (Socket s = server.accept()) {
                s.setSoTimeout(2000);
                Request r = parse(readLine(s));
                OutputStream out = s.getOutputStream();
                out.write((r == null ? "NO\n" : "OK\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                if (r != null) {
                    dispatch(r);
                }
            } catch (SocketException e) {
                if (closed) {
                    return;
                }
            } catch (IOException | RuntimeException e) {
                Log.warn("Single instance request failed: " + e.getMessage());
            }
        }
    }

    private Request parse(String line) {
        if (line == null) {
            return null;
        }
        String[] parts = line.trim().split(" ");
        if (parts.length != 2 || !java.security.MessageDigest.isEqual(parts[0].getBytes(StandardCharsets.US_ASCII),
                token.getBytes(StandardCharsets.US_ASCII))) {
            return null;
        }
        try {
            return Request.valueOf(parts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void dispatch(Request r) {
        Consumer<Request> h;
        synchronized (handlerLock) {
            h = handler;
            if (h == null && r == Request.SHOW) {
                pendingShow = true;
            }
        }
        if (h != null) {
            h.accept(r);
        }
    }

    private static boolean send(Path dir, Request request) {
        String[] port;
        try {
            Path f = dir.resolve(PORT_FILE);
            if (!Files.isRegularFile(f) || Files.size(f) > MAX_LINE) {
                return false;
            }
            port = Files.readString(f, StandardCharsets.UTF_8).trim().split(" ");
        } catch (IOException e) {
            return false;
        }
        if (port.length != 2) {
            return false;
        }
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), Integer.parseInt(port[0])), 1000);
            s.setSoTimeout(3000);
            OutputStream out = s.getOutputStream();
            out.write((port[1] + " " + request.name() + "\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return "OK".equals(readLine(s));
        } catch (IOException | NumberFormatException e) {
            return false;
        }
    }

    private static boolean lockFree(Path dir) {
        try (FileChannel ch = FileChannel.open(dir.resolve(LOCK_FILE), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock l = ch.tryLock();
            if (l != null) {
                l.release();
                return true;
            }
            return false;
        } catch (OverlappingFileLockException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static String readLine(Socket s) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII), 64);
        StringBuilder b = new StringBuilder();
        int c;
        while ((c = in.read()) != -1 && c != '\n') {
            if (b.length() >= MAX_LINE) {
                return null;
            }
            b.append((char) c);
        }
        return c == -1 && b.length() == 0 ? null : b.toString();
    }
}
