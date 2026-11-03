package com.repoguard.api;

import com.repoguard.analysis.*;
import com.repoguard.analysis.AnalysisModels.*;
import com.repoguard.config.RepoGuardProperties;
import com.repoguard.config.RuntimeSettings;
import com.repoguard.persistence.ScanRepository;
import com.repoguard.reporting.PdfReport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@ConditionalOnProperty(name = "repoguard.role", havingValue = "api", matchIfMissing = true)
public class ScanController {
    private final ScanService service;
    private final ScanRepository repository;
    private final WorkerClient worker;
    private final RepoGuardProperties props;
    private final RuntimeSettings settings;
    private final PdfReport pdf;
    public ScanController(ScanService service, ScanRepository repository, WorkerClient worker,
                          RepoGuardProperties props, RuntimeSettings settings, PdfReport pdf) {
        this.service = service; this.repository = repository; this.worker = worker; this.props = props; this.settings = settings; this.pdf = pdf;
    }
    public record GitHubRequest(@NotBlank String url) { }
    @PostMapping("/scans/github") @ResponseStatus(HttpStatus.ACCEPTED)
    public Scan github(@Valid @RequestBody GitHubRequest input) { return service.github(input.url()); }
    @PostMapping(value = "/scans/zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.ACCEPTED)
    public Scan zip(@RequestPart("file") MultipartFile file) throws Exception { return service.upload(file); }
    @GetMapping("/scans") public List<Scan> list() { return repository.list(); }
    @GetMapping("/scans/{id}") public Scan get(@PathVariable UUID id) { return repository.get(id); }
    @DeleteMapping("/scans/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { repository.delete(id); }
    public record DeleteRequest(@jakarta.validation.constraints.NotEmpty @jakarta.validation.constraints.Size(max=200) List<@jakarta.validation.constraints.NotNull UUID> ids) { }
    @PostMapping("/scans/delete") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSelected(@Valid @RequestBody DeleteRequest input) { repository.deleteAll(input.ids()); }
    @GetMapping("/scans/{id}/findings") public List<Finding> findings(@PathVariable UUID id,
            @RequestParam(required = false) String severity, @RequestParam(required = false) String language,
            @RequestParam(required = false) String tool, @RequestParam(required = false) String category,
            @RequestParam(required = false) String path) {
        Result result = repository.get(id).result(); if (result == null) return List.of();
        return result.findings().stream().filter(f -> severity == null || f.severity().equals(severity))
                .filter(f -> language == null || f.language().equals(language)).filter(f -> tool == null || f.tool().equals(tool))
                .filter(f -> category == null || f.category().equals(category)).filter(f -> path == null || f.path().contains(path)).toList();
    }
    @GetMapping("/scans/compare") public Map<String,Object> compare(@RequestParam UUID left, @RequestParam UUID right) {
        Result a = repository.get(left).result(), b = repository.get(right).result();
        if (a == null || b == null) throw new AnalysisException("NO_RESULTS", "Both analyses need stored results before comparison.");
        Map<String,Finding> before = a.findings().stream().collect(Collectors.toMap(Finding::fingerprint, Function.identity(), (x,y) -> x));
        Map<String,Finding> after = b.findings().stream().collect(Collectors.toMap(Finding::fingerprint, Function.identity(), (x,y) -> x));
        return Map.of("left", left, "right", right, "added", after.values().stream().filter(f -> !before.containsKey(f.fingerprint())).toList(),
                "resolved", before.values().stream().filter(f -> !after.containsKey(f.fingerprint())).toList(),
                "unchanged", after.values().stream().filter(f -> before.containsKey(f.fingerprint())).toList(), "limited", true);
    }
    @GetMapping("/scans/{id}/report.pdf") public ResponseEntity<byte[]> report(@PathVariable UUID id, @RequestParam(defaultValue = "de") String locale) {
        if (!Set.of("de", "en").contains(locale)) throw new AnalysisException("INVALID_LOCALE", "Locale must be de or en.");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=repoguard-" + id + ".pdf").body(pdf.render(repository.get(id), locale));
    }
    @GetMapping("/settings") public Map<String,Object> settings() {
        return Map.of("limits", props.importLimits(), "maxParallelScans", 1, "maxFindings", settings.maxFindings,
                "snippetLines", settings.snippetLines, "maxTextBytes", settings.maxTextBytes, "historyDays", settings.historyDays,
                "malware", worker.readiness(), "lastUpdate", repository.lastUpdate(), "rulesVersion", SourceRuleEngine.VERSION,
                "advisoriesVersion", DependencyInspector.VERSION);
    }
    @PostMapping("/security-data/update") @ResponseStatus(HttpStatus.ACCEPTED) public Map<String,Object> update() { return Map.of("id", service.update(), "status", "RUNNING"); }
}
