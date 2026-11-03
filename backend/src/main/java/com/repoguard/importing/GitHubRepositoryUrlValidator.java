package com.repoguard.importing;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

@Component
public class GitHubRepositoryUrlValidator {
    private static final Pattern OWNER_PATTERN = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?");
    private static final Pattern REPOSITORY_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    public GitHubRepository validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank() || rawUrl.length() > 300) {
            throw new InvalidRepositoryUrlException("Repository URL is required.");
        }
        if (rawUrl.contains("\\")) {
            throw new InvalidRepositoryUrlException("Backslashes are not allowed in repository URLs.");
        }

        URI uri = parse(rawUrl.trim());
        validateUri(uri);

        String path = uri.getRawPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String[] segments = path.split("/", -1);
        if (segments.length != 3 || segments[1].isBlank() || segments[2].isBlank()) {
            throw new InvalidRepositoryUrlException("Only https://github.com/<owner>/<repository> URLs are allowed.");
        }

        String owner = segments[1];
        String repository = stripOptionalGitSuffix(segments[2]);
        validateOwnerAndRepo(owner, repository);

        URI canonical = URI.create("https://github.com/%s/%s".formatted(owner, repository));
        URI archive = URI.create("https://codeload.github.com/%s/%s/zip/HEAD".formatted(owner, repository));
        return new GitHubRepository(owner, repository, canonical, archive);
    }

    private URI parse(String rawUrl) {
        try {
            return new URI(rawUrl);
        } catch (URISyntaxException ex) {
            throw new InvalidRepositoryUrlException("Repository URL is syntactically invalid.", ex);
        }
    }

    private void validateUri(URI uri) {
        if (!"https".equals(uri.getScheme())) {
            throw new InvalidRepositoryUrlException("Only HTTPS GitHub URLs are allowed.");
        }
        if (!"github.com".equals(uri.getHost())) {
            throw new InvalidRepositoryUrlException("Host must be exactly github.com.");
        }
        if (uri.getPort() != -1) {
            throw new InvalidRepositoryUrlException("Custom ports are not allowed.");
        }
        if (uri.getRawUserInfo() != null) {
            throw new InvalidRepositoryUrlException("User info is not allowed in repository URLs.");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new InvalidRepositoryUrlException("Query parameters and fragments are not allowed.");
        }
        String path = uri.getRawPath();
        if (path == null || path.isBlank() || path.contains("//") || path.contains("%")) {
            throw new InvalidRepositoryUrlException("Repository path is invalid.");
        }
    }

    private String stripOptionalGitSuffix(String repository) {
        if (repository.endsWith(".git")) {
            return repository.substring(0, repository.length() - 4);
        }
        return repository;
    }

    private void validateOwnerAndRepo(String owner, String repository) {
        if (!OWNER_PATTERN.matcher(owner).matches() || owner.contains("--")) {
            throw new InvalidRepositoryUrlException("Repository owner has an invalid format.");
        }
        if (!REPOSITORY_PATTERN.matcher(repository).matches() || repository.equals(".") || repository.equals("..")) {
            throw new InvalidRepositoryUrlException("Repository name has an invalid format.");
        }
    }
}
