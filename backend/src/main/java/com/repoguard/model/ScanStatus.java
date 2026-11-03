package com.repoguard.model;

public enum ScanStatus {
    QUEUED,
    VALIDATING_INPUT,
    IMPORTING,
    MALWARE_SCANNING,
    EXTRACTING,
    DETECTING_LANGUAGES,
    SCANNING,
    NORMALIZING,
    CLEANING_UP,
    COMPLETED,
    PARTIAL,
    FAILED,
    BLOCKED
}
