package dev.lumora.client.compat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;

/**
 * Minecraft 1.12.2 reads its built-in advancements and recipes by opening its own jar with
 * {@code FileSystems.newFileSystem(jar URI)} and closes that file system afterwards. Under Fabric Loader the game jar
 * is already open as a zip file system, so the call fails with {@link FileSystemAlreadyExistsException} (crash
 * "Exception in server tick loop" when a world is created). Then the existing file system is used through a view whose
 * {@code close()} does nothing - closing it would break the loader's own access to the jar.
 */
public final class SharedJarFileSystem {
    private SharedJarFileSystem() {
    }

    /** Like {@code FileSystems.newFileSystem(uri, env)}; an already open file system is shared instead. */
    public static FileSystem open(URI uri, Map<String, ?> env) throws IOException {
        try {
            return FileSystems.newFileSystem(uri, env);
        } catch (FileSystemAlreadyExistsException e) {
            return new NonClosing(FileSystems.getFileSystem(uri));
        }
    }

    /** Delegating view of a shared file system; only {@link #close()} differs (no-op). */
    private static final class NonClosing extends FileSystem {
        private final FileSystem fs;

        NonClosing(FileSystem fs) {
            this.fs = fs;
        }

        @Override
        public FileSystemProvider provider() {
            return fs.provider();
        }

        @Override
        public void close() {
            // shared with Fabric Loader: stays open
        }

        @Override
        public boolean isOpen() {
            return fs.isOpen();
        }

        @Override
        public boolean isReadOnly() {
            return fs.isReadOnly();
        }

        @Override
        public String getSeparator() {
            return fs.getSeparator();
        }

        @Override
        public Iterable<Path> getRootDirectories() {
            return fs.getRootDirectories();
        }

        @Override
        public Iterable<FileStore> getFileStores() {
            return fs.getFileStores();
        }

        @Override
        public Set<String> supportedFileAttributeViews() {
            return fs.supportedFileAttributeViews();
        }

        @Override
        public Path getPath(String first, String... more) {
            return fs.getPath(first, more);
        }

        @Override
        public PathMatcher getPathMatcher(String syntaxAndPattern) {
            return fs.getPathMatcher(syntaxAndPattern);
        }

        @Override
        public UserPrincipalLookupService getUserPrincipalLookupService() {
            return fs.getUserPrincipalLookupService();
        }

        @Override
        public WatchService newWatchService() throws IOException {
            return fs.newWatchService();
        }
    }
}
