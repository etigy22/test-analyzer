package com.repoguard.importing;

import com.repoguard.config.RepoGuardProperties;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static com.repoguard.importing.ArchiveDownloadException.Code.*;

/** Download only. Deliberately not exposed as a scan endpoint before the malware gate exists. */
@Service
public class GitHubArchiveDownloader {
    private final GitHubRepositoryUrlValidator validator;
    private final Path quarantineRoot;
    private final long maxBytes;
    private final Duration timeout;
    private final Supplier<CloseableHttpClient> clients;

    @Autowired
    public GitHubArchiveDownloader(GitHubRepositoryUrlValidator validator, RepoGuardProperties properties) {
        this(validator, properties.storage().quarantineDir(), properties.importLimits().maxArchiveBytes(),
                properties.importLimits().downloadTimeout(), GitHubArchiveDownloader::newClient);
    }

    GitHubArchiveDownloader(GitHubRepositoryUrlValidator validator, Path root, long maxBytes,
                            Duration timeout, Supplier<CloseableHttpClient> clients) {
        if (maxBytes < 4 || timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Positive archive size and download timeout are required.");
        }
        this.validator = validator;
        this.quarantineRoot = root.toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        this.timeout = timeout;
        this.clients = clients;
    }

    public QuarantinedArchive download(String url) {
        GitHubRepository repository = validator.validate(url);
        Path directory = createQuarantine();
        Path archive = directory.resolve("source.zip");
        HttpGet request = new HttpGet(repository.archiveUrl());
        request.setHeader("Accept", "application/zip");
        request.setHeader("Accept-Encoding", "identity");
        request.setHeader("User-Agent", "RepoGuard/0.1");
        request.setConfig(RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(timeout.toMillis()))
                .setResponseTimeout(Timeout.ofMilliseconds(timeout.toMillis()))
                .build());

        AtomicBoolean cancelled = new AtomicBoolean();
        FutureTask<QuarantinedArchive> task = new FutureTask<>(() -> {
            boolean completed = false;
            try (CloseableHttpClient client = clients.get()) {
                QuarantinedArchive result = client.execute(request, response -> {
                    try {
                        return copy(response, archive, cancelled, request);
                    } finally {
                        // Never let HttpClient drain rejected or oversized response bodies.
                        request.cancel();
                    }
                });
                completed = true;
                return result;
            } finally {
                if (!completed || cancelled.get()) {
                    cleanup(archive, directory);
                }
            }
        });
        Thread worker = Thread.ofVirtual().name("github-archive-download").start(task);
        boolean handedOff = false;
        try {
            QuarantinedArchive result = task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            handedOff = true;
            return result;
        } catch (TimeoutException ex) {
            throw new ArchiveDownloadException(DOWNLOAD_TIMEOUT, "GitHub archive download exceeded its time limit.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ArchiveDownloadException(DOWNLOAD_FAILED, "GitHub archive download was interrupted.");
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof ArchiveDownloadException failure) {
                throw failure;
            }
            throw new ArchiveDownloadException(DOWNLOAD_FAILED, "GitHub archive download failed; check connectivity and TLS.");
        } finally {
            if (!handedOff) {
                cancelled.set(true);
                request.cancel();
                worker.interrupt();
                task.cancel(true);
                try {
                    cleanup(archive, directory);
                } catch (IOException ex) {
                    throw new ArchiveDownloadException(QUARANTINE_FAILED, "Failed to remove an incomplete quarantined archive.");
                }
            }
        }
    }

    private QuarantinedArchive copy(ClassicHttpResponse response, Path archive, AtomicBoolean cancelled,
                                    HttpGet request) throws IOException {
        int status = response.getCode();
        if (status >= 300 && status < 400) {
            throw new ArchiveDownloadException(REDIRECT_BLOCKED, "GitHub archive redirects are not allowed.");
        }
        if (status != 200) {
            throw new ArchiveDownloadException(HTTP_STATUS, "GitHub did not return a public repository archive.");
        }
        if (response.getEntity() == null) {
            throw new ArchiveDownloadException(INVALID_ARCHIVE, "GitHub returned an empty archive response.");
        }
        String type = response.getEntity().getContentType();
        String mediaType = type == null ? "" : type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!mediaType.equals("application/zip") && !mediaType.equals("application/x-zip-compressed")
                && !mediaType.equals("application/octet-stream")) {
            throw new ArchiveDownloadException(INVALID_ARCHIVE, "GitHub did not return ZIP content.");
        }
        if (response.containsHeader("Content-Encoding")
                && !"identity".equalsIgnoreCase(response.getFirstHeader("Content-Encoding").getValue())) {
            throw new ArchiveDownloadException(INVALID_ARCHIVE, "Encoded archive responses are not allowed.");
        }
        if (response.getEntity().getContentLength() > maxBytes) {
            throw new ArchiveDownloadException(ARCHIVE_TOO_LARGE, "GitHub archive exceeds the configured size limit.");
        }
        MessageDigest digest = sha256();
        long total = 0;
        try (InputStream input = abortBeforeClose(response.getEntity().getContent(), request);
             OutputStream output = Files.newOutputStream(archive, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] magic = input.readNBytes(4);
            if (magic.length != 4 || magic[0] != 'P' || magic[1] != 'K'
                    || !(magic[2] == 3 && magic[3] == 4 || magic[2] == 5 && magic[3] == 6)) {
                throw new ArchiveDownloadException(INVALID_ARCHIVE, "Response does not have a ZIP signature.");
            }
            output.write(magic);
            digest.update(magic);
            total = magic.length;
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer, 0, (int) Math.min(buffer.length, maxBytes - total + 1))) != -1) {
                if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                    throw new ArchiveDownloadException(DOWNLOAD_TIMEOUT, "GitHub archive download was cancelled.");
                }
                if (count > maxBytes - total) {
                    throw new ArchiveDownloadException(ARCHIVE_TOO_LARGE, "GitHub archive exceeds the configured size limit.");
                }
                output.write(buffer, 0, count);
                digest.update(buffer, 0, count);
                total += count;
            }
        }
        if (response.getEntity().getContentLength() >= 0 && total != response.getEntity().getContentLength()) {
            throw new ArchiveDownloadException(INVALID_ARCHIVE, "GitHub archive response was truncated.");
        }
        return new QuarantinedArchive(archive, total, HexFormat.of().formatHex(digest.digest()));
    }

    private static InputStream abortBeforeClose(InputStream input, HttpGet request) {
        return new FilterInputStream(input) {
            @Override
            public void close() throws IOException {
                // Abort before the underlying entity stream's close can drain unread data.
                request.cancel();
                super.close();
            }
        };
    }

    private Path createQuarantine() {
        try {
            Files.createDirectories(quarantineRoot);
            if (Files.isSymbolicLink(quarantineRoot) || !Files.isDirectory(quarantineRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Invalid quarantine directory.");
            }
            if (Files.getFileStore(quarantineRoot).supportsFileAttributeView("posix")) {
                return Files.createTempDirectory(quarantineRoot, "github-",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            }
            // Windows inherits access controls from the operator-managed quarantine root.
            return Files.createTempDirectory(quarantineRoot, "github-");
        } catch (IOException ex) {
            throw new ArchiveDownloadException(QUARANTINE_FAILED, "Cannot create the private quarantine directory.");
        }
    }

    private static CloseableHttpClient newClient() {
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new GitHubDnsResolver()).build())
                .disableRedirectHandling().disableAutomaticRetries().disableContentCompression()
                .disableCookieManagement().build();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by Java.", ex);
        }
    }

    private static void cleanup(Path archive, Path directory) throws IOException {
        Files.deleteIfExists(archive);
        Files.deleteIfExists(directory);
    }
}
