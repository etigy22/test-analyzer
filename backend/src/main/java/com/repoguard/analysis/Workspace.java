package com.repoguard.analysis;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;

public final class Workspace {
    private Workspace() { }
    public static Path create(Path root, long minFreeBytes) throws IOException {
        root = root.toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
            throw new AnalysisException("STORAGE_UNSAFE", "Temporary storage is not a regular directory.");
        if (Files.getFileStore(root).getUsableSpace() < minFreeBytes)
            throw new AnalysisException("DISK_LIMIT", "Insufficient temporary disk space.");
        if (Files.getFileStore(root).supportsFileAttributeView("posix"))
            return Files.createTempDirectory(root, "scan-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        return Files.createTempDirectory(root, "scan-");
    }
    public static void delete(Path root, Path directory) throws IOException {
        Path base = root.toAbsolutePath().normalize();
        Path target = directory.toAbsolutePath().normalize();
        if (!target.startsWith(base) || target.equals(base)) throw new IOException("Cleanup outside workspace refused.");
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(target, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dir.toFile().setWritable(true, true);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.delete(dir); return FileVisitResult.CONTINUE;
            }
        });
    }
    public static void recover(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var entries = Files.list(root)) {
            for (Path entry : entries.toList()) delete(root, entry);
        }
    }
    public static void checkDeadline(long deadline) {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline)
            throw new AnalysisException("SCAN_TIMEOUT", "The analysis exceeded its time limit.");
    }
}
