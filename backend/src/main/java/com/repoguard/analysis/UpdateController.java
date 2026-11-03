package com.repoguard.analysis;

import com.repoguard.config.RuntimeSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import static com.repoguard.analysis.AnalysisModels.*;

@RestController
@RequestMapping("/internal")
@ConditionalOnProperty(name = "repoguard.role", havingValue = "updater")
public class UpdateController {
    private final RuntimeSettings settings;
    private final Semaphore permit = new Semaphore(1);
    public UpdateController(RuntimeSettings settings) { this.settings = settings; }
    @GetMapping("/health") public Readiness health() { return new Readiness(true, SourceRuleEngine.VERSION, null, "Manual updater available."); }
    @PostMapping("/update") public Readiness update() throws IOException {
        if (!permit.tryAcquire()) throw new AnalysisException("BUSY", "A security data update is already running.");
        Path root = settings.signatureDir.toAbsolutePath().normalize();
        Path snapshot = root.resolve(UUID.randomUUID().toString());
        Path pointer = root.resolve("current.new");
        boolean installed = false;
        try {
            Files.createDirectories(snapshot);
            var update = TrustedProcess.run(List.of(settings.freshclam, "--config-file=/etc/repoguard/freshclam.conf",
                    "--datadir=" + snapshot, "--stdout"), root, Duration.ofMinutes(10));
            if (update.exit() != 0)
                throw new AnalysisException("UPDATE_FAILED", "ClamAV signature download failed. The previous snapshot remains active; retry later (the upstream CDN may rate-limit downloads).");
            var check = TrustedProcess.run(List.of(settings.clamscan, "--database=" + snapshot, "--no-summary", "/etc/repoguard/empty.txt"),
                    root, Duration.ofSeconds(90));
            if (check.exit() != 0) throw new AnalysisException("UPDATE_INVALID", "Downloaded security data failed verification. The previous snapshot remains active.");
            Path cve = Files.createDirectory(snapshot.resolve("trivy"));
            Path downloadTemp=Files.createDirectory(snapshot.resolve("download-tmp"));
            for (String database : List.of("--download-db-only", "--download-java-db-only")) {
                var download = TrustedProcess.run(List.of("/usr/local/bin/trivy", "--cache-dir", cve.toString(),
                        "image", database, "--no-progress"), root, Duration.ofMinutes(5),65536,java.util.Map.of("TMPDIR",downloadTemp.toString()));
                if (download.exit() != 0) throw new AnalysisException("CVE_UPDATE_FAILED", "Vulnerability database update failed. The previous complete security snapshot remains active.");
            }
            if (!Files.isRegularFile(cve.resolve("db/trivy.db")) || !Files.isRegularFile(cve.resolve("java-db/trivy-java.db")))
                throw new AnalysisException("CVE_UPDATE_INVALID", "Downloaded vulnerability database files are missing.");
            Path verificationSource=Files.createDirectory(snapshot.resolve("verification-source"));
            var verifyDatabase=TrustedProcess.run(List.of("/usr/local/bin/trivy","--cache-dir",cve.toString(),"fs",
                    "--offline-scan","--skip-db-update","--skip-java-db-update","--scanners","vuln","--format","json","--quiet",verificationSource.toString()),
                    root,Duration.ofMinutes(2),65536,java.util.Map.of("TMPDIR",downloadTemp.toString()));
            if(verifyDatabase.exit()!=0)throw new AnalysisException("CVE_UPDATE_INVALID","Downloaded vulnerability database could not be opened by the managed scanner.");
            Workspace.delete(snapshot,verificationSource);
            Workspace.delete(snapshot,downloadTemp);
            Files.writeString(pointer, snapshot.getFileName().toString());
            Files.move(pointer, root.resolve("current"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            installed = true;
            return new Readiness(true, snapshot.getFileName().toString(), Instant.now().toString(), "ClamAV signatures and local Trivy vulnerability databases updated. Source rules ship with application releases.");
        } finally {
            try {
                Files.deleteIfExists(pointer);
                if (!installed && Files.exists(snapshot)) Workspace.delete(root, snapshot);
            } finally { permit.release(); }
        }
    }
}
