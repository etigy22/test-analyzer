package com.repoguard.importing;

public class InvalidRepositoryUrlException extends RuntimeException {
    public InvalidRepositoryUrlException(String message) {
        super(message);
    }

    public InvalidRepositoryUrlException(String message, Throwable cause) {
        super(message, cause);
    }
}
