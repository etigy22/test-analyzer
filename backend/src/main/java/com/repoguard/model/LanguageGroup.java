package com.repoguard.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum LanguageGroup {
    JAVA("Java", ".java"),
    PYTHON("Python", ".py"),
    RUST("Rust", ".rs"),
    SWIFT("Swift", ".swift"),
    JAVASCRIPT_TYPESCRIPT("JavaScript/TypeScript", ".js", ".jsx", ".ts", ".tsx", ".mjs", ".cjs"),
    C_CPP("C/C++", ".c", ".h", ".cc", ".cpp", ".cxx", ".hpp", ".hh", ".hxx"),
    CSHARP("C#", ".cs"),
    GO("Go", ".go"),
    KOTLIN("Kotlin", ".kt", ".kts"),
    PHP("PHP", ".php"),
    UNKNOWN("Unknown");

    private final String displayName;
    private final String[] extensions;

    LanguageGroup(String displayName, String... extensions) {
        this.displayName = displayName;
        this.extensions = extensions;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<LanguageGroup> fromPath(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(language -> language != UNKNOWN)
                .filter(language -> Arrays.stream(language.extensions).anyMatch(normalized::endsWith))
                .findFirst();
    }
}
