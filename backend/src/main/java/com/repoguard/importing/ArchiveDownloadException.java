package com.repoguard.importing;

/** Safe, bounded error messages. Never include response bodies or local paths. */
public class ArchiveDownloadException extends RuntimeException {
    public enum Code {
        NETWORK_POLICY, HTTP_STATUS, REDIRECT_BLOCKED, INVALID_ARCHIVE,
        ARCHIVE_TOO_LARGE, DOWNLOAD_TIMEOUT, DOWNLOAD_FAILED, QUARANTINE_FAILED
    }

    private final Code code;

    public ArchiveDownloadException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
