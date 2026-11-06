package com.repoguard.analysis;

import java.util.List;
import java.util.UUID;

public final class AnalysisModels {
    private AnalysisModels() { }
    public record Finding(String id, String category, String severity, String ruleId, String title,
                          String explanation, String remediation, String tool, String toolVersion,
                          String language, String path, int line, String fingerprint, Snippet snippet) { }
    public record Snippet(int startLine, int endLine, String text, boolean truncated) { }
    public record Coverage(String language, String tool, String check, String status, String reason, String version, Boolean applicable) {
        public Coverage(String language,String tool,String check,String status,String reason,String version){this(language,tool,check,status,reason,version,true);}
        public boolean incomplete(){return !status.equals("COMPLETED") && !(Boolean.FALSE.equals(applicable) && status.equals("UNSUPPORTED"));}
    }
    public record Language(String name, int files, long bytes) { }
    public record Result(List<Finding> findings, List<Coverage> coverage, List<Language> languages,
                         String archiveSha256, long archiveBytes, String rulesVersion, String malwareVersion,
                         boolean truncated, long durationMillis) { }
    public record Scan(UUID id, String sourceType, String source, String status, String createdAt,
                       String updatedAt, String completedAt, String failureCode, String failureMessage,
                       Result result) { }
    public record WorkerState(String id, String phase) { }
    public record Readiness(boolean ready, String version, String updatedAt, String message) { }
}
