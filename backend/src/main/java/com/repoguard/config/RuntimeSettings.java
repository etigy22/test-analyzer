package com.repoguard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.time.Duration;

@Component
public class RuntimeSettings {
    @Value("${repoguard.worker-url:http://worker:8080}") public String workerUrl;
    @Value("${repoguard.updater-url:http://updater:8080}") public String updaterUrl;
    @Value("${repoguard.signature-dir:/signatures}") public Path signatureDir;
    @Value("${repoguard.scanner-cache:/scanner-cache}") public Path scannerCache;
    @Value("${repoguard.clamscan:/usr/bin/clamscan}") public String clamscan;
    @Value("${repoguard.freshclam:/usr/bin/freshclam}") public String freshclam;
    @Value("${repoguard.max-findings:10000}") public int maxFindings;
    @Value("${repoguard.max-text-bytes:10485760}") public long maxTextBytes;
    @Value("${repoguard.snippet-lines:5}") public int snippetLines;
    @Value("${repoguard.snippet-line-length:160}") public int snippetLineLength;
    @Value("${repoguard.history-days:90}") public int historyDays;
    @Value("${repoguard.min-free-bytes:134217728}") public long minFreeBytes;
    @Value("${repoguard.malware-timeout:PT3M}") public Duration malwareTimeout;
}
