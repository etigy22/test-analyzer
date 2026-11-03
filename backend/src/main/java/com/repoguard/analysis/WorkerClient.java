package com.repoguard.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repoguard.config.RepoGuardProperties;
import com.repoguard.config.RuntimeSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static com.repoguard.analysis.AnalysisModels.*;

@Component
@ConditionalOnProperty(name = "repoguard.role", havingValue = "api", matchIfMissing = true)
public class WorkerClient {
    private final RuntimeSettings settings;
    private final RepoGuardProperties props;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public WorkerClient(RuntimeSettings settings, RepoGuardProperties props, ObjectMapper json) { this.settings = settings; this.props = props; this.json = json; }
    public Readiness readiness() {
        try {
            var response = send(HttpRequest.newBuilder(URI.create(settings.workerUrl + "/internal/health")).GET().build(), Duration.ofSeconds(5));
            return json.readValue(response.body(), Readiness.class);
        } catch (Exception ex) { return new Readiness(false, null, null, "Isolated scanner unavailable."); }
    }
    public Result analyze(UUID id, Path file, Consumer<String> phase) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(settings.workerUrl + "/internal/analyze/" + id))
                .header("Content-Type", "application/octet-stream").POST(HttpRequest.BodyPublishers.ofFile(file)).build();
        var pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        long deadline = System.nanoTime() + props.importLimits().scannerTimeout().plusSeconds(30).toNanos();
        String previous = "";
        try {
            while (true) {
                Workspace.checkDeadline(deadline);
                try {
                    var response = pending.get(400, TimeUnit.MILLISECONDS);
                    validate(response);
                    return json.readValue(response.body(), Result.class);
                } catch (TimeoutException waiting) {
                    var response = send(HttpRequest.newBuilder(URI.create(settings.workerUrl + "/internal/status")).GET().build(), Duration.ofSeconds(3));
                    var state = json.readValue(response.body(), WorkerState.class);
                    if (id.toString().equals(state.id()) && !state.phase().equals("IDLE") && !state.phase().equals(previous)) {
                        previous = state.phase(); phase.accept(previous);
                    }
                }
            }
        } finally { if (!pending.isDone()) pending.cancel(true); }
    }
    public Readiness update() throws Exception {
        var response = send(HttpRequest.newBuilder(URI.create(settings.updaterUrl + "/internal/update"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), Duration.ofMinutes(22));
        validate(response);
        return json.readValue(response.body(), Readiness.class);
    }
    private HttpResponse<String> send(HttpRequest request, Duration timeout) throws Exception {
        var future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try { return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS); }
        finally { if (!future.isDone()) future.cancel(true); }
    }
    private void validate(HttpResponse<String> response) throws Exception {
        if (response.statusCode() == 200) return;
        var error = json.readTree(response.body());
        String code = error.path("code").asText("SCANNER_FAILED");
        String message = error.path("message").asText("The isolated scanner could not complete the analysis.");
        if (!code.matches("[A-Z_]{1,64}") || message.length() > 512) throw new AnalysisException("SCANNER_FAILED", "Invalid scanner response.");
        throw new AnalysisException(code, message);
    }
}
