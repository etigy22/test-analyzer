package com.repoguard.api;

import com.repoguard.importing.GitHubRepositoryUrlValidator;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ImportControllerTest {
    private final MockMvc api = MockMvcBuilders.standaloneSetup(
            new ImportController(new GitHubRepositoryUrlValidator())).build();

    @Test
    void returnsCanonicalUrlAndDefaultBranchArchiveWithoutDownloading() throws Exception {
        api.perform(post("/api/import/github/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://github.com/example/repo.git/\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.canonicalUrl").value("https://github.com/example/repo"))
                .andExpect(jsonPath("$.archiveUrl").value("https://codeload.github.com/example/repo/zip/HEAD"));
    }

    @Test
    void rejectsHostSpoofingWithSafeStructuredError() throws Exception {
        api.perform(post("/api/import/github/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://github.com.evil.example/example/repo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.error").value("INVALID_GITHUB_URL"));
    }

    @Test
    void rejectsMissingUrlAndMalformedJson() throws Exception {
        api.perform(post("/api/import/github/validate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        api.perform(post("/api/import/github/validate").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }
}
