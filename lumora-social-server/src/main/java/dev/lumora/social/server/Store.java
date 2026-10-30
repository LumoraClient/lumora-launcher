package dev.lumora.social.server;

import dev.lumora.social.Js;
import dev.lumora.social.Rules;
import dev.lumora.social.model.ChatMessage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.List;

/**
 * Files of a server instance in the data directory:
 * <ul>
 *   <li>{@code state.json}: players, friendships, requests, groups, read positions (written atomically),</li>
 *   <li>{@code messages/<conversation>.jsonl}: one message per line, appended; on start the newest
 *       {@link Rules#HISTORY_KEPT} are loaded and longer files are shortened.</li>
 * </ul>
 * Message texts are stored as plain text: whoever runs the server can read them (documented in SOCIAL.md).
 */
final class Store {
    private final Path dir;
    private final Path messagesDir;

    Store(Path dir) throws IOException {
        this.dir = dir;
        this.messagesDir = dir.resolve("messages");
        Files.createDirectories(messagesDir);
    }

    State load() throws IOException {
        Path f = dir.resolve("state.json");
        State s = Files.isRegularFile(f) ? State.read(Js.object(Files.readString(f, StandardCharsets.UTF_8))) : new State();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(messagesDir, "*.jsonl")) {
            for (Path p : ds) {
                String conv = fileToConv(p.getFileName().toString());
                if (conv == null) {
                    continue;
                }
                List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                ArrayDeque<ChatMessage> q = new ArrayDeque<>();
                for (String line : lines) {
                    try {
                        ChatMessage m = ChatMessage.read(Js.object(line));
                        if (m != null && m.conv.equals(conv)) {
                            q.addLast(m);
                            s.nextMessageId = Math.max(s.nextMessageId, m.id + 1);
                            if (q.size() > Rules.HISTORY_KEPT) {
                                q.removeFirst();
                            }
                        }
                    } catch (RuntimeException ignored) {
                        // a torn last line after a crash
                    }
                }
                if (!q.isEmpty()) {
                    s.messages.put(conv, q);
                }
                if (lines.size() > Rules.HISTORY_KEPT + Rules.HISTORY_KEPT / 2) {
                    rewrite(conv, q);
                }
            }
        }
        return s;
    }

    void save(State s) throws IOException {
        writeAtomically(dir.resolve("state.json"), s.json().toString());
    }

    void append(ChatMessage m) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file(m.conv), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
            w.write(m.json().toString());
            w.write('\n');
        }
    }

    void deleteConversation(String conv) throws IOException {
        Files.deleteIfExists(file(conv));
    }

    private void rewrite(String conv, ArrayDeque<ChatMessage> q) throws IOException {
        StringBuilder b = new StringBuilder();
        for (ChatMessage m : q) {
            b.append(m.json()).append('\n');
        }
        writeAtomically(file(conv), b.toString());
    }

    private Path file(String conv) {
        return messagesDir.resolve(conv.replace(':', '_') + ".jsonl");
    }

    private static String fileToConv(String name) {
        String base = name.substring(0, name.length() - ".jsonl".length());
        if (base.startsWith("dm_") && base.length() == 3 + 32 + 1 + 32) {
            return "dm:" + base.substring(3, 35) + ":" + base.substring(36);
        }
        if (base.startsWith("g_") && base.substring(2).matches("[a-z0-9]{1,24}")) {
            return "g:" + base.substring(2);
        }
        return null;
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
