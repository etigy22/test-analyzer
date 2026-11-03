package com.repoguard.analysis;

import com.repoguard.config.RepoGuardProperties;
import com.repoguard.config.RuntimeSettings;
import com.repoguard.importing.SafeZipExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import static com.repoguard.analysis.AnalysisModels.*;

@RestController
@RequestMapping("/internal")
@ConditionalOnProperty(name = "repoguard.role", havingValue = "worker")
public class WorkerController {
    private final RepoGuardProperties props;
    private final RuntimeSettings settings;
    private final MalwareScanner malware;
    private final SafeZipExtractor extractor;
    private final SourceRuleEngine rules;
    private final Semaphore permit = new Semaphore(1);
    private final AtomicReference<WorkerState> state = new AtomicReference<>(new WorkerState(null, "IDLE"));
    public WorkerController(RepoGuardProperties props, RuntimeSettings settings, MalwareScanner malware,
                            SafeZipExtractor extractor, SourceRuleEngine rules) {
        this.props = props; this.settings = settings; this.malware = malware; this.extractor = extractor; this.rules = rules;
    }
    @PostConstruct public void recover() throws IOException { Workspace.recover(props.storage().workDir()); }
    @GetMapping("/health") public Readiness health() { return malware.readiness(); }
    @GetMapping("/status") public WorkerState status() { return state.get(); }
    @PostMapping(value = "/analyze/{id}", consumes = "application/octet-stream")
    public Result analyze(@PathVariable UUID id, HttpServletRequest request) throws Exception {
        if (!permit.tryAcquire()) throw new AnalysisException("BUSY", "The isolated scanner is busy.");
        Path directory = null;
        try {
            directory = Workspace.create(props.storage().workDir(), settings.minFreeBytes);
            long deadline = System.nanoTime() + props.importLimits().scannerTimeout().toNanos();
            Path archive = directory.resolve("source.zip");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytes = 0;
            phase(id, "IMPORTING");
            try (var input = request.getInputStream(); var output = Files.newOutputStream(archive, StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    Workspace.checkDeadline(deadline);
                    if (count > props.importLimits().maxArchiveBytes() - bytes)
                        throw new AnalysisException("ARCHIVE_TOO_LARGE", "The archive exceeds its upload limit.");
                    output.write(buffer, 0, count); digest.update(buffer, 0, count); bytes += count;
                }
            }
            if (bytes < 22) throw new AnalysisException("ZIP_INVALID", "The uploaded file is not a complete ZIP archive.");
            phase(id, "MALWARE_SCANNING");
            String signatures = malware.scan(archive); // No bypass, including when configuration says otherwise.
            Workspace.checkDeadline(deadline);
            phase(id, "EXTRACTING");
            Path source = Files.createDirectory(directory.resolve("source"));
            var links=extractor.extract(archive, source, deadline);
            phase(id, "DETECTING_LANGUAGES");
            phase(id, "SCANNING");
            Result result = rules.analyze(source, HexFormat.of().formatHex(digest.digest()), bytes, signatures, deadline);
            if(!links.isEmpty()) {
                var coverage=new java.util.ArrayList<>(result.coverage());
                coverage.add(new Coverage(null,"RepoGuard","Validated internal archive links","COMPLETED",links.size()+" internal file/directory links were validated and materialized as read-only regular files/directories within all entry, depth and byte limits. No filesystem symlink was created or followed.",SourceRuleEngine.VERSION));
                result=new Result(result.findings(),coverage,result.languages(),result.archiveSha256(),result.archiveBytes(),result.rulesVersion(),result.malwareVersion(),result.truncated(),result.durationMillis());
            }
            phase(id, "NORMALIZING");
            return result;
        } finally {
            try {
                phase(id, "CLEANING_UP");
                if (directory != null) Workspace.delete(props.storage().workDir(), directory);
            } finally { state.set(new WorkerState(id.toString(), "IDLE")); permit.release(); }
        }
    }
    private void phase(UUID id, String phase) { state.set(new WorkerState(id.toString(), phase)); }
}
