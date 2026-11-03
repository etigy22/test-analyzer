package com.repoguard.analysis;

import com.repoguard.config.RepoGuardProperties;
import com.repoguard.config.RuntimeSettings;
import com.repoguard.importing.*;
import com.repoguard.persistence.ScanRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import static com.repoguard.analysis.AnalysisModels.*;

@Service
@ConditionalOnProperty(name = "repoguard.role", havingValue = "api", matchIfMissing = true)
public class ScanService {
    private final ScanRepository repository;
    private final GitHubArchiveDownloader github;
    private final GitHubRepositoryUrlValidator validator;
    private final WorkerClient worker;
    private final RepoGuardProperties props;
    private final RuntimeSettings settings;
    private final Semaphore gate = new Semaphore(1);
    public ScanService(ScanRepository repository, GitHubArchiveDownloader github, GitHubRepositoryUrlValidator validator,
                        WorkerClient worker, RepoGuardProperties props, RuntimeSettings settings) {
        this.repository = repository; this.github = github; this.validator = validator; this.worker = worker; this.props = props; this.settings = settings;
    }
    @PostConstruct public void recover() throws Exception {
        Workspace.recover(props.storage().quarantineDir());
        Workspace.recover(props.storage().workDir());
        repository.recover(settings.historyDays);
    }
    public Scan github(String url) {
        String source = validator.validate(url).canonicalUrl().toString();
        acquire();
        UUID id;
        try { id = repository.create("GITHUB", source); }
        catch (RuntimeException failure) { gate.release(); throw failure; }
        Thread.ofVirtual().name("scan-" + id).start(() -> run(id, () -> github.download(source)));
        return repository.get(id);
    }
    public Scan upload(MultipartFile file) throws Exception {
        if (file.isEmpty() || file.getSize() > props.importLimits().maxArchiveBytes())
            throw new AnalysisException("ARCHIVE_TOO_LARGE", "Select a non-empty ZIP within the upload limit.");
        acquire();
        Path directory = null;
        UUID id = null;
        boolean submitted = false;
        try {
            id = repository.create("ZIP", "ZIP archive");
            repository.phase(id, "IMPORTING");
            directory = Workspace.create(props.storage().quarantineDir(), settings.minFreeBytes);
            Path archive = directory.resolve("source.zip");
            long total = 0;
            long deadline = System.nanoTime() + props.importLimits().downloadTimeout().toNanos();
            try (var input = file.getInputStream(); var output = Files.newOutputStream(archive, StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    Workspace.checkDeadline(deadline);
                    if (count > props.importLimits().maxArchiveBytes() - total)
                        throw new AnalysisException("ARCHIVE_TOO_LARGE", "The uploaded archive exceeds its size limit.");
                    output.write(buffer, 0, count); total += count;
                }
            }
            QuarantinedArchive quarantined = new QuarantinedArchive(archive, total, "");
            UUID scanId = id;
            Thread.ofVirtual().name("scan-" + id).start(() -> run(scanId, () -> quarantined));
            submitted = true;
            return repository.get(id);
        } finally {
            if (!submitted) {
                try {
                    if (directory != null) Workspace.delete(props.storage().quarantineDir(), directory);
                    if (id != null) repository.fail(id, "UPLOAD_FAILED", "The upload could not be safely stored.");
                } finally { gate.release(); }
            }
        }
    }
    private void run(UUID id, Supplier<QuarantinedArchive> input) {
        try {
            repository.phase(id, "IMPORTING");
            Result result;
            try (QuarantinedArchive archive = input.get()) {
                result = worker.analyze(id, archive.path(), phase -> repository.phase(id, phase));
                repository.phase(id, "CLEANING_UP");
            }
            repository.finish(id, result);
        } catch (AnalysisException ex) { repository.fail(id, ex.code(), ex.getMessage()); }
        catch (ArchiveDownloadException ex) { repository.fail(id, ex.code().name(), ex.getMessage()); }
        catch (Exception ex) { repository.fail(id, "SCAN_FAILED", "Analysis failed. Check scanner readiness and configured limits."); }
        finally { gate.release(); }
    }
    public UUID update() {
        acquire();
        UUID id;
        try { id = repository.startUpdate(); }
        catch (RuntimeException failure) { gate.release(); throw failure; }
        Thread.ofVirtual().name("security-data-update").start(() -> {
            try {
                Readiness result = worker.update();
                repository.finishUpdate(id, true, result.message(), result.version());
            } catch (AnalysisException ex) { repository.finishUpdate(id, false, ex.getMessage(), null); }
            catch (Exception ex) { repository.finishUpdate(id, false, "Security data updater unavailable or timed out. Previous data remains active.", null); }
            finally { gate.release(); }
        });
        return id;
    }
    private void acquire() {
        if (!gate.tryAcquire()) throw new AnalysisException("BUSY", "A scan or security data update is already running. Try again when it finishes.");
    }
}
