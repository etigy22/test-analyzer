package com.repoguard.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "repoguard")
public record RepoGuardProperties(
        @NotBlank String publicBaseUrl,
        Storage storage,
        ImportLimits importLimits,
        Security security,
        List<String> allowedOrigins
) {
    public record Storage(
            Path quarantineDir,
            Path workDir,
            Path reportDir
    ) {
    }

    public record ImportLimits(
            @Min(1) long maxArchiveBytes,
            @Min(1) long maxExpandedBytes,
            @Min(1) long maxSingleFileBytes,
            @Min(1) int maxFiles,
            @Min(1) int maxZipDepth,
            @Min(1) int maxCompressionRatio,
            Duration downloadTimeout,
            Duration scannerTimeout
    ) {
    }

    public record Security(
            boolean malwareScanRequired,
            boolean blockPrivateNetworks,
            boolean allowScannerNetwork
    ) {
    }
}
