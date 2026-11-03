package com.repoguard.analysis;

import com.repoguard.config.RuntimeSettings;
import com.repoguard.model.LanguageGroup;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import static com.repoguard.analysis.AnalysisModels.*;
import static com.repoguard.model.LanguageGroup.*;

/** Coordinates bounded source rules and managed static scanners without executing target code or configuration. */
@Component
public class SourceRuleEngine {
    public static final String VERSION = "repoguard-source-2026.11.03.1";
    record Rule(String id, LanguageGroup language, String category, String severity, Pattern pattern,
                String title, String explanation, String remediation) { }
    private final RuntimeSettings settings;
    private final DependencyInspector dependencies;
    private ExternalScanners external;
    private final List<Rule> rules = rules();
    public SourceRuleEngine(RuntimeSettings settings, DependencyInspector dependencies) { this.settings = settings; this.dependencies = dependencies; }
    @org.springframework.beans.factory.annotation.Autowired
    public SourceRuleEngine(RuntimeSettings settings, DependencyInspector dependencies, ExternalScanners external) { this(settings,dependencies);this.external=external; }

    public Result analyze(Path root, String archiveHash, long archiveBytes, String malwareVersion, long deadline) throws IOException {
        long start = System.nanoTime();
        Path logicalRoot = root;
        try (var top = Files.list(root)) {
            List<Path> children = top.toList();
            if (children.size() == 1 && Files.isDirectory(children.getFirst())) logicalRoot = children.getFirst();
        }
        List<Path> files;
        try (var walk = Files.walk(logicalRoot)) { files = walk.filter(Files::isRegularFile).sorted().toList(); }
        Map<LanguageGroup, long[]> languages = new LinkedHashMap<>();
        Set<LanguageGroup> readable = new HashSet<>();
        BoundedFindings findings = new BoundedFindings(settings.maxFindings);
        Set<String> fingerprints = new HashSet<>();
        List<Coverage> coverage = new ArrayList<>();
        int skipped = 0;
        for (Path file : files) {
            Workspace.checkDeadline(deadline);
            String path = logicalRoot.relativize(file).toString().replace('\\', '/');
            LanguageGroup language = LanguageGroup.fromPath(path).orElse(UNKNOWN);
            long[] count = languages.computeIfAbsent(language, ignored -> new long[2]);
            count[0]++; count[1] += Files.size(file);
            if (Files.size(file) > settings.maxTextBytes) { skipped++; continue; }
            byte[] bytes = Files.readAllBytes(file);
            boolean binary = false;
            for (byte value : bytes) if (value == 0) { binary = true; break; }
            if (binary) { skipped++; continue; }
            String text;
            try { text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString(); }
            catch (java.nio.charset.CharacterCodingException ex) { skipped++; continue; }
            readable.add(language);
            String[] lines = text.split("\\R", -1);
            for (int index = 0; index < lines.length; index++) {
                Workspace.checkDeadline(deadline);
                String line = lines[index];
                String bounded = line.substring(0, Math.min(2048, line.length()));
                for (Rule rule : rules) {
                    if ((rule.language == null || rule.language == language) && rule.pattern.matcher(bounded).find())
                        add(findings, fingerprints, finding(rule.id, rule.category, rule.severity, rule.title,
                                rule.explanation, rule.remediation, language.displayName(), path, index + 1, lines));
                }
                if (line.length() > 160 && language != UNKNOWN)
                    add(findings, fingerprints, finding("RG-LONG-LINE", "QUALITY", "LOW", "Long source line",
                            "This line exceeds 160 characters. Generated/minified code may be a false positive.",
                            "Split expressions into readable steps and apply a trusted formatter.", language.displayName(), path, index + 1, lines));
            }
            dependencies.inspect(path, text, (advisory, version) -> add(findings, fingerprints,
                    finding(advisory.id(), "DEPENDENCY", "HIGH", advisory.packageName() + ": " + advisory.id(),
                            "An exact dependency version matches the bundled advisory: " + advisory.url(),
                            "Upgrade to a maintained release at or above " + advisory.fixedVersion() + ". Verify compatibility.",
                            language.displayName(), path, 0, new String[0])));
        }
        boolean truncated = findings.truncated();
        for (LanguageGroup language : languages.keySet()) {
            if (language == UNKNOWN) {
                coverage.add(new Coverage(language.displayName(), "RepoGuard", "Language-specific rules", "UNSUPPORTED", "Only generic text checks apply to unknown file types.", VERSION));
                continue;
            }
            for (String check : List.of("SECURITY", "QUALITY"))
                coverage.add(new Coverage(language.displayName(), "RepoGuard", check, truncated || !readable.contains(language) ? "SKIPPED" : "COMPLETED",
                        !readable.contains(language) ? "No readable UTF-8 source within the text limit." : truncated ? "Finding limit reached; results are incomplete." : "Bundled language-specific text rules ran. No syntax, type or interprocedural data-flow analysis.", VERSION));
            coverage.add(new Coverage(language.displayName(), "RepoGuard", "Compiler/build-dependent analysis", "UNSUPPORTED",
                    "Checks requiring target compilation, dependency installation or executable project plugins are prohibited.", null));
        }
        coverage.add(new Coverage(null, "RepoGuard", "Secrets and configuration", truncated ? "SKIPPED" : "COMPLETED",
                "Bounded text patterns; all stored literals and sensitive assignments are masked.", VERSION));
        coverage.add(new Coverage(null, "RepoGuard", "Offline dependency advisories", "COMPLETED",
                "Small bundled advisory set for exact npm, pip and Maven versions only. Other ecosystems, version ranges, transitive resolution and comprehensive CVE coverage are UNSUPPORTED.", DependencyInspector.VERSION));
        if (skipped > 0) coverage.add(new Coverage(null, "RepoGuard", "Unread source content", "SKIPPED",
                skipped + " files were binary, not UTF-8, or larger than the configured text limit.", VERSION));
        coverage.add(new Coverage(null, "ClamAV", "Mandatory malware pre-scan", "COMPLETED",
                "Signed local signatures were used. This is not a guarantee of malware freedom.", malwareVersion));
        if(external!=null) {
            var batch=external.analyze(logicalRoot,languages.keySet().stream().map(LanguageGroup::displayName).collect(java.util.stream.Collectors.toSet()),deadline,root.getParent());
            coverage.addAll(batch.coverage());
            for(Finding finding:batch.findings())add(findings,fingerprints,finding);
            if(findings.truncated() || batch.coverage().stream().anyMatch(c -> c.check().equals("Scanner result limit"))){truncated=true;coverage.add(new Coverage(null,"RepoGuard","Result limit","SKIPPED","Finding limit reached. Highest severity findings are retained; at equal severity, security/dependency/secret findings and managed scanner findings take precedence over generic quality hints.",VERSION));}
        }
        List<Language> detected = languages.entrySet().stream().map(e -> new Language(e.getKey().displayName(), (int)e.getValue()[0], e.getValue()[1])).toList();
        return new Result(findings.values(), coverage, detected, archiveHash, archiveBytes, VERSION, malwareVersion, truncated,
                (System.nanoTime() - start) / 1_000_000);
    }

    private void add(BoundedFindings list, Set<String> seen, Finding finding) {
        list.add(finding);
    }
    private Finding finding(String rule, String category, String severity, String title, String explanation,
                            String remediation, String language, String path, int line, String[] lines) {
        String safePath = SecretMasker.safePath(path);
        Snippet snippet = null;
        String matched = line > 0 ? SecretMasker.mask(lines[line - 1].substring(0, Math.min(lines[line - 1].length(), 2048))).trim() : "";
        if (line > 0) {
            int start = Math.max(0, line - 1 - settings.snippetLines / 2);
            int end = Math.min(lines.length, start + settings.snippetLines);
            StringBuilder text = new StringBuilder();
            boolean shortened = false;
            for (int i = start; i < end; i++) {
                String masked = SecretMasker.mask(lines[i].substring(0, Math.min(2048, lines[i].length())));
                if (masked.length() > settings.snippetLineLength) { masked = masked.substring(0, settings.snippetLineLength) + "…"; shortened = true; }
                if (!text.isEmpty()) text.append('\n');
                text.append(masked);
            }
            snippet = new Snippet(start + 1, end, text.toString(), shortened);
        }
        String fingerprint = hash(rule + "\n" + path + "\n" + matched);
        return new Finding(UUID.randomUUID().toString(), category, severity, rule, title, explanation, remediation,
                "RepoGuard", VERSION, language, safePath, line, fingerprint, snippet);
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static Rule rule(String id, LanguageGroup language, String category, String severity, String regex, String title, String remediation) {
        return new Rule(id, language, category, severity, Pattern.compile(regex), title,
                "A static source pattern matched. Review the context; this rule does not prove exploitability.", remediation);
    }
    private static List<Rule> rules() {
        String shell = "Avoid shell execution with untrusted input; use fixed arguments and validate all input.";
        String quality = "Use explicit, maintainable error handling and the language's safer idioms.";
        List<Rule> r = new ArrayList<>();
        r.add(rule("RG-JAVA-EXEC", JAVA, "SECURITY", "HIGH", "Runtime\\.getRuntime\\(\\)\\.exec\\s*\\(|new\\s+ProcessBuilder\\s*\\(", "Process execution sink", shell));
        r.add(rule("RG-JAVA-EMPTY-CATCH", JAVA, "QUALITY", "MEDIUM", "catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}", "Empty exception handler", quality));
        r.add(rule("RG-PY-EVAL", PYTHON, "SECURITY", "HIGH", "\\b(eval|exec)\\s*\\(|shell\\s*=\\s*True", "Dynamic execution sink", shell));
        r.add(rule("RG-PY-BARE-EXCEPT", PYTHON, "QUALITY", "MEDIUM", "^\\s*except\\s*:", "Bare exception handler", quality));
        r.add(rule("RG-RUST-PROCESS", RUST, "SECURITY", "HIGH", "Command::new\\s*\\(", "Process execution sink", shell));
        r.add(rule("RG-RUST-UNWRAP", RUST, "QUALITY", "MEDIUM", "\\.unwrap\\s*\\(\\s*\\)", "Unchecked unwrap may panic", quality));
        r.add(rule("RG-SWIFT-PROCESS", SWIFT, "SECURITY", "HIGH", "\\bProcess\\s*\\(|\\bsystem\\s*\\(", "Process execution sink", shell));
        r.add(rule("RG-SWIFT-TRY", SWIFT, "QUALITY", "MEDIUM", "\\btry!", "Forced error handling may crash", quality));
        r.add(rule("RG-JS-EVAL", JAVASCRIPT_TYPESCRIPT, "SECURITY", "HIGH", "\\beval\\s*\\(|\\bnew\\s+Function\\s*\\(", "Dynamic code execution sink", "Replace dynamic code evaluation with safe data parsing."));
        r.add(rule("RG-JS-DOM", JAVASCRIPT_TYPESCRIPT, "SECURITY", "HIGH", "\\.innerHTML\\s*=|dangerouslySetInnerHTML", "HTML injection sink", "Render text nodes or apply a maintained, trusted HTML sanitizer."));
        r.add(rule("RG-JS-EQUALITY", JAVASCRIPT_TYPESCRIPT, "QUALITY", "LOW", "(?<![=!])==(?!=)|(?<![!])!=(?!=)", "Loose equality comparison", "Use strict equality and explicit type conversions."));
        r.add(rule("RG-CPP-UNSAFE", C_CPP, "SECURITY", "HIGH", "\\b(strcpy|strcat|gets|sprintf|system)\\s*\\(", "Unsafe C/C++ API", "Use bounded memory operations and avoid shell commands."));
        r.add(rule("RG-CPP-NAMESPACE", C_CPP, "QUALITY", "LOW", "using\\s+namespace\\s+std\\s*;", "Global namespace import", "Prefer explicit std:: names, especially in headers."));
        r.add(rule("RG-CS-PROCESS", CSHARP, "SECURITY", "HIGH", "Process\\.Start\\s*\\(", "Process execution sink", shell));
        r.add(rule("RG-CS-CATCH", CSHARP, "QUALITY", "MEDIUM", "catch\\s*\\(\\s*Exception\\b", "Overly broad exception handler", quality));
        r.add(rule("RG-GO-PROCESS", GO, "SECURITY", "HIGH", "exec\\.Command(?:Context)?\\s*\\(", "Process execution sink", shell));
        r.add(rule("RG-GO-ERROR", GO, "QUALITY", "MEDIUM", "_,\\s*_\\s*[:=]=|_\\s*=\\s*[^=]", "Ignored result or error", quality));
        r.add(rule("RG-KT-EXEC", KOTLIN, "SECURITY", "HIGH", "Runtime\\.getRuntime\\(\\)\\.exec\\s*\\(|ProcessBuilder\\s*\\(", "Process execution sink", shell));
        r.add(rule("RG-KT-NONNULL", KOTLIN, "QUALITY", "MEDIUM", "!!", "Forced non-null assertion", "Use safe calls and explicit null handling."));
        r.add(rule("RG-PHP-EXEC", PHP, "SECURITY", "HIGH", "\\b(eval|shell_exec|system|passthru)\\s*\\(", "Dynamic execution sink", shell));
        r.add(rule("RG-PHP-EQUALITY", PHP, "QUALITY", "LOW", "(?<![=!])==(?!=)|(?<![!])!=(?!=)", "Loose equality comparison", "Use strict comparisons and validate types."));
        r.add(rule("RG-SQL-CONCAT", null, "SECURITY", "HIGH", "(?i)(SELECT|INSERT|UPDATE|DELETE)[^;]{0,300}(\\+|\\$\\{|%s)", "Potential dynamically constructed SQL", "Use parameterized queries and bound parameters."));
        r.add(rule("RG-PATH-INPUT", null, "SECURITY", "MEDIUM", "(?i)(readFile|open|readString|readAllBytes)\\s*\\([^)]{0,200}(request|params|input)", "Potential untrusted file path", "Resolve against a fixed base directory and reject paths outside it."));
        r.add(rule("RG-SECRET", null, "SECRET", "HIGH", "(?i)(password|passwd|secret|token|api[_-]?key)\\s*[:=]\\s*['\"][^'\"]{6,}|AKIA[A-Z0-9]{16}|gh[pousr]_[A-Za-z0-9]{20,}|BEGIN[^-]{0,30}PRIVATE KEY", "Possible embedded secret", "Remove credentials, rotate exposed secrets and load them from protected runtime configuration."));
        r.add(rule("RG-CONFIG-DEBUG", null, "CONFIGURATION", "MEDIUM", "(?i)\\b(debug|allow_debug)\\s*[:=]\\s*(true|1)|NODE_TLS_REJECT_UNAUTHORIZED\\s*=\\s*['\"]?0|verify\\s*=\\s*False|rejectUnauthorized\\s*:\\s*false", "Unsafe debug or TLS configuration", "Disable production debug output and keep TLS certificate verification enabled."));
        return List.copyOf(r);
    }
}
