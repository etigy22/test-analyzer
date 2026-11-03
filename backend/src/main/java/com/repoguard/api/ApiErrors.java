package com.repoguard.api;

import com.repoguard.analysis.AnalysisException;
import com.repoguard.importing.InvalidRepositoryUrlException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(AnalysisException.class) public ResponseEntity<?> analysis(AnalysisException failure) {
        int status = switch (failure.code()) { case "NOT_FOUND" -> 404; case "BUSY" -> 409; case "ARCHIVE_TOO_LARGE" -> 413; default -> 422; };
        return ResponseEntity.status(status).body(Map.of("code", failure.code(), "message", failure.getMessage()));
    }
    @ExceptionHandler(InvalidRepositoryUrlException.class) public ResponseEntity<?> invalidUrl(InvalidRepositoryUrlException failure) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_GITHUB_URL", "message", failure.getMessage()));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class) public ResponseEntity<?> upload() {
        return ResponseEntity.status(413).body(Map.of("code", "ARCHIVE_TOO_LARGE", "message", "The upload exceeds the configured size limit."));
    }
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class, org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class, org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<?> input() { return ResponseEntity.badRequest().body(Map.of("code", "INVALID_INPUT", "message", "Request parameters are missing or invalid.")); }
    @ExceptionHandler(Exception.class) public ResponseEntity<?> unexpected(Exception failure) {
        return ResponseEntity.status(500).body(Map.of("code", "INTERNAL_ERROR", "message", "The request failed. Check service readiness."));
    }
}
