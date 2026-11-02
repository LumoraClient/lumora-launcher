package dev.lumora.launcher.core.download;

import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Hashing;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Verified HTTP downloads.
 * <ul>
 *   <li>Every download has a mandatory expected hash; the file only becomes visible at its final path
 *       after the hash matched (temp file + atomic move).</li>
 *   <li>Existing files with a matching hash are not downloaded again (repairs only what is broken).</li>
 *   <li>Transient failures (I/O, 5xx, 429) are retried with exponential backoff; hash mismatches are
 *       retried once more with a fresh download and then reported.</li>
 *   <li>Only https URLs on an allow-list of official hosts are accepted.</li>
 * </ul>
 */
public final class Downloader {
    public static final Set<String> DEFAULT_TRUSTED_HOSTS = Set.of(
            "piston-meta.mojang.com", "piston-data.mojang.com", "launchermeta.mojang.com", "launcher.mojang.com",
            "libraries.minecraft.net", "resources.download.minecraft.net",
            "meta.fabricmc.net", "maven.fabricmc.net",
            // Legacy Fabric: loader profiles, intermediary mappings and patched LWJGL 2 for 1.8.9 / 1.12.2
            "meta.legacyfabric.net", "maven.legacyfabric.net",
            // mod loaders: Quilt meta and Maven, Forge and NeoForge Maven (installers, libraries, version lists)
            "meta.quiltmc.org", "maven.quiltmc.org", "maven.minecraftforge.net", "files.minecraftforge.net", "maven.neoforged.net",
            "api.modrinth.com", "cdn.modrinth.com",
            // skins: official Mojang profile/texture services (read-only, public data)
            "sessionserver.mojang.com", "api.mojang.com", "api.minecraftservices.com", "textures.minecraft.net");

    public record Task(URI uri, Path target, Hashing.Expected expected, long size) {
        public Task {
            if (uri == null || target == null || expected == null) {
                throw new IllegalArgumentException("uri, target and expected hash are required");
            }
        }
    }

    public interface Progress {
        /** Called from worker threads. */
        void update(long bytesDone, long bytesTotal, int filesDone, int filesTotal);
    }

    public static final class DownloadException extends IOException {
        private static final long serialVersionUID = 1L;
        private final transient List<Task> failed;

        public DownloadException(String message, List<Task> failed, Throwable cause) {
            super(message, cause);
            this.failed = List.copyOf(failed);
        }

        public List<Task> failed() {
            return failed;
        }
    }

    private final HttpClient http;
    private final Set<String> trustedHosts;
    private final boolean allowInsecureLocalhost;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final int parallelism;

    public Downloader() {
        this(8);
    }

    /** @param parallelism files downloaded at the same time (setting "Gleichzeitige Downloads") */
    public Downloader(int parallelism) {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(15)).build(),
                DEFAULT_TRUSTED_HOSTS, false, 4, Duration.ofMillis(500), parallelism);
    }

    /** Full constructor; {@code allowInsecureLocalhost} exists for tests against a local HTTP server. */
    public Downloader(HttpClient http, Set<String> trustedHosts, boolean allowInsecureLocalhost,
                      int maxAttempts, Duration baseBackoff, int parallelism) {
        this.http = http;
        this.trustedHosts = Set.copyOf(trustedHosts);
        this.allowInsecureLocalhost = allowInsecureLocalhost;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseBackoff = baseBackoff;
        this.parallelism = Math.max(1, parallelism);
    }

    public void checkTrusted(URI uri) throws IOException {
        String host = uri.getHost();
        boolean local = allowInsecureLocalhost && "http".equals(uri.getScheme())
                && ("localhost".equals(host) || "127.0.0.1".equals(host));
        if (!local && (!"https".equals(uri.getScheme()) || host == null || !trustedHosts.contains(host))) {
            throw new IOException("Untrusted download source refused: " + uri);
        }
    }

    /** Fetches a small text document (metadata). Hash verification is done by the caller where available. */
    public String fetchString(URI uri) throws IOException {
        checkTrusted(uri);
        IOException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> resp = http.send(request(uri), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    return resp.body();
                }
                last = new IOException("HTTP " + resp.statusCode() + " for " + uri);
                if (!isRetryable(resp.statusCode())) {
                    throw last;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while fetching " + uri);
            } catch (IOException e) {
                last = e;
            }
            sleepBackoff(attempt);
        }
        throw last;
    }

    /** Small binary download (icons, skin textures) from a trusted host, capped at {@code maxBytes}. */
    public byte[] fetchBytes(URI uri, int maxBytes) throws IOException {
        checkTrusted(uri);
        try {
            HttpResponse<java.io.InputStream> resp = http.send(request(uri), HttpResponse.BodyHandlers.ofInputStream());
            try (java.io.InputStream in = resp.body()) {
                if (resp.statusCode() != 200) {
                    throw new IOException("HTTP " + resp.statusCode() + " for " + uri);
                }
                byte[] data = in.readNBytes(maxBytes + 1);
                if (data.length > maxBytes) {
                    throw new IOException("Response larger than " + maxBytes + " bytes: " + uri);
                }
                return data;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while fetching " + uri);
        }
    }

    /** Fetches a metadata document and verifies it against a known SHA-1 (as provided by Mojang manifests). */
    public String fetchVerified(URI uri, Hashing.Expected expected) throws IOException {
        String body = fetchString(uri);
        String actual = Hashing.hash(body.getBytes(java.nio.charset.StandardCharsets.UTF_8), expected.algorithm());
        if (!actual.equals(expected.hex())) {
            throw new IOException("Checksum mismatch for " + uri + ": expected " + expected.hex() + " got " + actual);
        }
        return body;
    }

    public enum Verify {
        /** Re-hash existing files (first install, repair). */
        FULL,
        /** Accept an existing file whose size matches; used for routine starts after a verified install. */
        QUICK
    }

    public boolean download(Task task, AtomicLong bytesCounter) throws IOException {
        return download(task, bytesCounter, Verify.FULL);
    }

    /** Downloads a single file (or verifies the existing one). Returns true if bytes were transferred. */
    public boolean download(Task task, AtomicLong bytesCounter, Verify verify) throws IOException {
        checkTrusted(task.uri());
        boolean present = verify == Verify.QUICK && task.size() > 0 && Files.isRegularFile(task.target())
                && Files.size(task.target()) == task.size();
        if (present || Hashing.matches(task.target(), task.expected())) {
            if (bytesCounter != null && task.size() > 0) {
                bytesCounter.addAndGet(task.size());
            }
            return false;
        }
        IOException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long counted = 0;
            Path tmp = null;
            try {
                Files.createDirectories(task.target().toAbsolutePath().getParent());
                tmp = Files.createTempFile(task.target().toAbsolutePath().getParent(),
                        task.target().getFileName().toString(), ".part");
                HttpResponse<InputStream> resp = http.send(request(task.uri()), HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() != 200) {
                    resp.body().close();
                    last = new IOException("HTTP " + resp.statusCode() + " for " + task.uri());
                    if (!isRetryable(resp.statusCode())) {
                        break;
                    }
                } else {
                    MessageDigest digest = task.expected().algorithm().newDigest();
                    try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(tmp)) {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            digest.update(buf, 0, n);
                            out.write(buf, 0, n);
                            counted += n;
                            if (bytesCounter != null) {
                                bytesCounter.addAndGet(n);
                            }
                        }
                    }
                    String actual = HexFormat.of().formatHex(digest.digest());
                    if (actual.equals(task.expected().hex())) {
                        FileUtil.moveReplacing(tmp, task.target());
                        return true;
                    }
                    last = new IOException("Checksum mismatch for " + task.uri() + " (expected "
                            + task.expected() + ", got " + actual + ")");
                    Log.warn(last.getMessage() + " - attempt " + attempt + "/" + maxAttempts);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while downloading " + task.uri());
            } catch (IOException e) {
                last = e;
                Log.warn("Download failed (" + attempt + "/" + maxAttempts + "): " + task.uri() + ": " + e);
            } finally {
                if (tmp != null) {
                    Files.deleteIfExists(tmp);
                }
            }
            if (bytesCounter != null && counted > 0) {
                bytesCounter.addAndGet(-counted);
            }
            sleepBackoff(attempt);
        }
        throw last != null ? last : new IOException("Download failed: " + task.uri());
    }

    /** Downloads all tasks in parallel. All failures are collected and reported together. */
    public void downloadAll(List<Task> tasks, Progress progress) throws IOException {
        downloadAll(tasks, progress, Verify.FULL);
    }

    public void downloadAll(List<Task> tasks, Progress progress, Verify verify) throws IOException {
        if (tasks.isEmpty()) {
            return;
        }
        long total = tasks.stream().mapToLong(t -> Math.max(0, t.size())).sum();
        AtomicLong bytes = new AtomicLong();
        java.util.concurrent.atomic.AtomicInteger files = new java.util.concurrent.atomic.AtomicInteger();
        List<Task> failed = java.util.Collections.synchronizedList(new ArrayList<>());
        List<IOException> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        try (ExecutorService pool = Executors.newFixedThreadPool(parallelism, r -> {
            Thread t = new Thread(r, "download-worker");
            t.setDaemon(true);
            return t;
        })) {
            List<Future<?>> futures = new ArrayList<>();
            for (Task task : tasks) {
                futures.add(pool.submit(() -> {
                    try {
                        download(task, bytes, verify);
                    } catch (IOException e) {
                        failed.add(task);
                        errors.add(e);
                    }
                    int done = files.incrementAndGet();
                    if (progress != null) {
                        progress.update(bytes.get(), total, done, tasks.size());
                    }
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    pool.shutdownNow();
                    throw new InterruptedIOException("Download interrupted");
                } catch (ExecutionException e) {
                    errors.add(new IOException(e.getCause()));
                }
            }
        }
        if (!failed.isEmpty() || !errors.isEmpty()) {
            IOException first = errors.isEmpty() ? null : errors.getFirst();
            throw new DownloadException(failed.size() + " of " + tasks.size() + " downloads failed"
                    + (first != null ? " (first error: " + first.getMessage() + ")" : ""), failed, first);
        }
    }

    private HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", UserAgent.VALUE)
                .GET().build();
    }

    private static boolean isRetryable(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    private void sleepBackoff(int attempt) throws InterruptedIOException {
        if (attempt >= maxAttempts) {
            return;
        }
        try {
            Thread.sleep(baseBackoff.toMillis() * (1L << (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted during retry backoff");
        }
    }
}
