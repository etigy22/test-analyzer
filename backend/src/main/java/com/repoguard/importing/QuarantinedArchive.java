package com.repoguard.importing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Passive, unverified input. Must pass malware and ZIP validation before analysis. */
public record QuarantinedArchive(Path path, long bytes, String sha256) implements AutoCloseable {
    @Override
    public void close() throws IOException {
        Files.deleteIfExists(path);
        Files.deleteIfExists(path.getParent());
    }
}
