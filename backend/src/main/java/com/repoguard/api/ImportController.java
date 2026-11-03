package com.repoguard.api;

import com.repoguard.importing.GitHubRepository;
import com.repoguard.importing.GitHubRepositoryUrlValidator;
import com.repoguard.importing.InvalidRepositoryUrlException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping("/api/import")
@ConditionalOnProperty(name = "repoguard.role", havingValue = "api", matchIfMissing = true)
public class ImportController {
    private final GitHubRepositoryUrlValidator validator;

    public ImportController(GitHubRepositoryUrlValidator validator) {
        this.validator = validator;
    }

    @PostMapping("/github/validate")
    public GitHubValidationResponse validateGitHubUrl(@Valid @RequestBody GitHubValidationRequest request) {
        GitHubRepository repository = validator.validate(request.url());
        return new GitHubValidationResponse(
                true,
                repository.owner(),
                repository.repository(),
                repository.canonicalUrl().toString(),
                repository.archiveUrl().toString(),
                "Repository URL accepted. Download will be rebuilt server-side from validated owner and repository."
        );
    }

    @ExceptionHandler(InvalidRepositoryUrlException.class)
    public ResponseEntity<Map<String, Object>> invalidUrl(InvalidRepositoryUrlException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "valid", false,
                "error", "INVALID_GITHUB_URL",
                "message", ex.getMessage()
        ));
    }

    public record GitHubValidationRequest(@NotBlank String url) {
    }

    public record GitHubValidationResponse(
            boolean valid,
            String owner,
            String repository,
            String canonicalUrl,
            String archiveUrl,
            String message
    ) {
    }
}
