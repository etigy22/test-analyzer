package com.repoguard.importing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubRepositoryUrlValidatorTest {
    private final GitHubRepositoryUrlValidator validator = new GitHubRepositoryUrlValidator();

    @Test
    void acceptsCanonicalGithubRepositoryUrl() {
        GitHubRepository repository = validator.validate("https://github.com/openai/codex");

        assertThat(repository.owner()).isEqualTo("openai");
        assertThat(repository.repository()).isEqualTo("codex");
        assertThat(repository.canonicalUrl().toString()).isEqualTo("https://github.com/openai/codex");
        assertThat(repository.archiveUrl().toString()).isEqualTo("https://codeload.github.com/openai/codex/zip/HEAD");
    }

    @Test
    void stripsOptionalGitSuffix() {
        GitHubRepository repository = validator.validate("https://github.com/example/repo.git");

        assertThat(repository.repository()).isEqualTo("repo");
        assertThat(repository.canonicalUrl().toString()).isEqualTo("https://github.com/example/repo");
    }

    @Test
    void acceptsExactlyOneTrailingSlashWithOrWithoutGitSuffix() {
        for (String suffix : new String[]{"/", ".git/"}) {
            assertThat(validator.validate("https://github.com/example/repo" + suffix).canonicalUrl().toString())
                    .isEqualTo("https://github.com/example/repo");
        }
    }

    @Test
    void rejectsAmbiguousEncodedAndOversizedPaths() {
        String[] invalid = {
                "https://github.com/example/repo//", "https://github.com//example/repo",
                "https://github.com/example/%72epo", "https://github.com/example/repo%2Fextra",
                "https://github.com/example/repo%2fextra", "https://github.com/example/repo%5Cextra",
                "https://github.com/example/..", "https://github.com/example/.git",
                "https://github.com/-example/repo", "https://github.com/example-/repo",
                "https://github.com/ex--ample/repo", "https://github.com/example/" + "x".repeat(101),
                "https://github.com/" + "x".repeat(40) + "/repo"
        };
        for (String url : invalid) {
            assertThatThrownBy(() -> validator.validate(url)).as(url)
                    .isInstanceOf(InvalidRepositoryUrlException.class);
        }
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(InvalidRepositoryUrlException.class);
        assertThatThrownBy(() -> validator.validate(" ")).isInstanceOf(InvalidRepositoryUrlException.class);
        assertThatThrownBy(() -> validator.validate("x".repeat(301))).isInstanceOf(InvalidRepositoryUrlException.class);
    }

    @Test
    void rejectsHostConfusionAndUserInfo() {
        assertThatThrownBy(() -> validator.validate("https://github.com.evil.example/openai/codex"))
                .isInstanceOf(InvalidRepositoryUrlException.class);
        assertThatThrownBy(() -> validator.validate("https://github.com@evil.example/openai/codex"))
                .isInstanceOf(InvalidRepositoryUrlException.class);
    }

    @Test
    void rejectsUnsafeUrlComponents() {
        String[] invalidUrls = {
                "http://github.com/openai/codex",
                "ssh://github.com/openai/codex",
                "https://github.com:8443/openai/codex",
                "https://github.com/openai/codex?tab=readme",
                "https://github.com/openai/codex#readme",
                "https://github.com/openai/codex/issues",
                "https://github.com/openai\\codex",
                "https://127.0.0.1/openai/codex"
        };

        for (String invalidUrl : invalidUrls) {
            assertThatThrownBy(() -> validator.validate(invalidUrl))
                    .as(invalidUrl)
                    .isInstanceOf(InvalidRepositoryUrlException.class);
        }
    }
}
