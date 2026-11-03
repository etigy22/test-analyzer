package com.repoguard.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.repoguard.analysis.WorkerClient;
import com.repoguard.persistence.ScanRepository;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@ConditionalOnProperty(name = "repoguard.role", havingValue = "api", matchIfMissing = true)
public class HealthController {
    private final ScanRepository repository;
    private final WorkerClient worker;
    public HealthController(ScanRepository repository, WorkerClient worker) { this.repository = repository; this.worker = worker; }
    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
                "product", "RepoGuard",
                "status", "UP",
                "timestamp", Instant.now().toString(),
                "database", repository.ready() ? "UP" : "DOWN",
                "malware", worker.readiness()
        );
    }
}
