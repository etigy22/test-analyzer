package com.repoguard.importing;

import java.net.URI;

public record GitHubRepository(
        String owner,
        String repository,
        URI canonicalUrl,
        URI archiveUrl
) {
}
