package com.repoguard.importing;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.repoguard.importing.ArchiveDownloadException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GitHubArchiveDownloaderTest {
    @TempDir Path root;

    @Test
    void streamsPassiveArchiveHashesItAndDeletesItWhenClosed() throws Exception {
        byte[] zip = zip();
        var response = response(200, zip, ContentType.create("application/zip"));
        CloseableHttpClient client = client(response);
        try (QuarantinedArchive archive = downloader(client, 1024).download("https://github.com/example/repo.git/")) {
            assertThat(archive.bytes()).isEqualTo(zip.length);
            assertThat(Files.readAllBytes(archive.path())).isEqualTo(zip);
            assertThat(archive.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip)));
        }
        assertEmptyQuarantine();
        verify(client).close();
    }

    @Test
    void validatesInputBeforeCreatingQuarantineOrConnecting() {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        assertThatThrownBy(() -> downloader(client, 1024).download("https://evil.example/example/repo"))
                .isInstanceOf(InvalidRepositoryUrlException.class);
        verifyNoInteractions(client);
        assertEmptyQuarantine();
    }

    @Test
    void refusesRedirectWithoutRequestingItsLocation() throws Exception {
        var response = response(302, new byte[0], ContentType.TEXT_HTML);
        response.setHeader("Location", "http://169.254.169.254/latest/meta-data/");
        expectFailure(client(response), 1024, REDIRECT_BLOCKED);
    }

    @Test
    void rejectsHttpErrorsAndHtmlAndFakeZipBodies() throws Exception {
        expectFailure(client(response(404, new byte[0], ContentType.TEXT_HTML)), 1024, HTTP_STATUS);
        expectFailure(client(response(200, "<html>bad</html>".getBytes(), ContentType.TEXT_HTML)), 1024, INVALID_ARCHIVE);
        expectFailure(client(response(200, "not a ZIP".getBytes(), ContentType.APPLICATION_OCTET_STREAM)), 1024, INVALID_ARCHIVE);
    }

    @Test
    void rejectsDeclaredOversizeBeforeReadingBody() throws Exception {
        var response = new BasicClassicHttpResponse(200);
        HttpEntity entity = mock(HttpEntity.class);
        when(entity.getContentType()).thenReturn("application/zip");
        when(entity.getContentLength()).thenReturn(1025L);
        response.setEntity(entity);
        expectFailure(client(response), 1024, ARCHIVE_TOO_LARGE);
        verify(entity, never()).getContent();
    }

    @Test
    void acceptsArchiveExactlyAtConfiguredSizeLimit() throws Exception {
        byte[] zip = zip();
        try (QuarantinedArchive archive = downloader(client(response(200, zip, ContentType.create("application/zip"))),
                zip.length).download("https://github.com/example/repo")) {
            assertThat(archive.bytes()).isEqualTo(zip.length);
        }
        assertEmptyQuarantine();
    }

    @Test
    void countsActualBytesWhenLengthIsUnknownOrUnderstated() throws Exception {
        byte[] zip = zip();
        for (long declared : new long[]{-1, 4}) {
            var response = new BasicClassicHttpResponse(200);
            HttpEntity entity = mock(HttpEntity.class);
            when(entity.getContentType()).thenReturn("application/zip");
            when(entity.getContentLength()).thenReturn(declared);
            when(entity.getContent()).thenReturn(new ByteArrayInputStream(zip));
            response.setEntity(entity);
            expectFailure(client(response), zip.length - 1, ARCHIVE_TOO_LARGE);
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void abortsConnectionBeforeClosingRejectedStreamSoItCannotDrainBeyondLimit() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(HttpGet.class), any(HttpClientResponseHandler.class))).thenAnswer(call -> {
            HttpGet request = call.getArgument(0);
            var response = new BasicClassicHttpResponse(200);
            HttpEntity entity = mock(HttpEntity.class);
            when(entity.getContentType()).thenReturn("application/zip");
            when(entity.getContentLength()).thenReturn(-1L);
            when(entity.getContent()).thenReturn(new ByteArrayInputStream(zip()) {
                @Override
                public void close() {
                    assertThat(request.isCancelled()).as("abort before entity close/drain").isTrue();
                }
            });
            response.setEntity(entity);
            try {
                return ((HttpClientResponseHandler<?>) call.getArgument(1)).handleResponse(response);
            } finally {
                assertThat(request.isCancelled()).isTrue();
            }
        });
        expectFailure(client, 4, ARCHIVE_TOO_LARGE);
    }

    @Test
    void rejectsTruncatedAndUnexpectedlyEncodedResponse() throws Exception {
        var response = new BasicClassicHttpResponse(200);
        HttpEntity entity = mock(HttpEntity.class);
        when(entity.getContentType()).thenReturn("application/zip");
        when(entity.getContentLength()).thenReturn(1000L);
        when(entity.getContent()).thenReturn(new ByteArrayInputStream(zip()));
        response.setEntity(entity);
        expectFailure(client(response), 1024, INVALID_ARCHIVE);
        response = response(200, zip(), ContentType.create("application/zip"));
        response.setHeader("Content-Encoding", "gzip");
        expectFailure(client(response), 1024, INVALID_ARCHIVE);
    }

    @Test
    void cleansPartialDownloadAfterStreamFailureWithoutLeakingDetails() throws Exception {
        var response = new BasicClassicHttpResponse(200);
        HttpEntity entity = mock(HttpEntity.class);
        when(entity.getContentType()).thenReturn("application/zip");
        when(entity.getContentLength()).thenReturn(-1L);
        when(entity.getContent()).thenThrow(new IOException("sensitive/path/token"));
        response.setEntity(entity);
        expectFailure(client(response), 1024, DOWNLOAD_FAILED);
    }

    @Test
    void absoluteTimeoutIncludesBlockedNetworkWorkAndCleansQuarantine() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CountDownLatch exited = new CountDownLatch(1);
        when(client.execute(any(HttpGet.class), any(HttpClientResponseHandler.class))).thenAnswer(call -> {
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("Should be interrupted");
            } finally {
                exited.countDown();
            }
        });
        var downloader = new GitHubArchiveDownloader(new GitHubRepositoryUrlValidator(), root, 1024,
                Duration.ofMillis(100), () -> client);
        assertThatThrownBy(() -> downloader.download("https://github.com/example/repo"))
                .isInstanceOfSatisfying(ArchiveDownloadException.class, error -> assertThat(error.code()).isEqualTo(DOWNLOAD_TIMEOUT));
        assertThat(exited.await(5, TimeUnit.SECONDS)).isTrue();
        assertEmptyQuarantine();
    }

    private void expectFailure(CloseableHttpClient client, long limit, ArchiveDownloadException.Code code) {
        assertThatThrownBy(() -> downloader(client, limit).download("https://github.com/example/repo"))
                .isInstanceOfSatisfying(ArchiveDownloadException.class, error -> {
                    assertThat(error.code()).isEqualTo(code);
                    assertThat(error.getMessage()).doesNotContain("sensitive", root.toString());
                });
        assertEmptyQuarantine();
    }

    private GitHubArchiveDownloader downloader(CloseableHttpClient client, long limit) {
        return new GitHubArchiveDownloader(new GitHubRepositoryUrlValidator(), root, limit, Duration.ofSeconds(5), () -> client);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CloseableHttpClient client(BasicClassicHttpResponse response) throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(HttpGet.class), any(HttpClientResponseHandler.class))).thenAnswer(call -> {
            HttpGet request = call.getArgument(0);
            assertThat(request.getUri().toString()).isEqualTo("https://codeload.github.com/example/repo/zip/HEAD");
            assertThat(request.getConfig().isRedirectsEnabled()).isFalse();
            return ((HttpClientResponseHandler<?>) call.getArgument(1)).handleResponse(response);
        });
        return client;
    }

    private BasicClassicHttpResponse response(int status, byte[] body, ContentType type) {
        var response = new BasicClassicHttpResponse(status);
        response.setEntity(new ByteArrayEntity(body, type));
        return response;
    }

    private void assertEmptyQuarantine() {
        try (var entries = Files.list(root)) {
            assertThat(entries.toList()).isEmpty();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private byte[] zip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("repo/passive.txt"));
            zip.write("This is inert RepoGuard test data.".getBytes());
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
