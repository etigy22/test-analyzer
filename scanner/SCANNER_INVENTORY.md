# RepoGuard scanner coverage

All target projects remain passive input. No target builds, tests, dependency installation, hooks, project plugins or executable configuration are allowed.

| Language | Managed security checks | Managed quality checks |
|---|---|---|
| Java | Semgrep AST rules, internal source rules | Checkstyle 11.1.0 |
| Python | Semgrep, Bandit 1.8.6, internal rules | Ruff 0.14.3 |
| Rust | Semgrep, internal rules | rustfmt (installed distribution version) |
| Swift | Internal source security rules | SwiftLint 0.62.2 with fixed selected rules |
| JavaScript/TypeScript | Semgrep and ESLint security rules | ESLint 9.39.1 with managed TypeScript parser |
| C/C++ | Semgrep, Cppcheck, internal rules | Cppcheck (installed distribution version) |
| C# | Semgrep, internal rules | Internal source-only rules; no project build |
| Go | Semgrep, internal rules | gofmt (installed distribution version) |
| Kotlin | Semgrep, internal rules | detekt 1.23.8 without project classpath/build |
| PHP | Semgrep, internal rules | PHP_CodeSniffer 4.0.1 with fixed PSR12 rules |

Semgrep 1.140.0 runs only locally supplied rules, not a downloaded registry pack. Its parser/rule limits remain visible as failed/unsupported checks. Tool versions are captured in each report. Compiler-dependent, interprocedural and type-dependent coverage is not implied by this table.

## Vulnerabilities and secrets

Trivy 0.67.2 uses the manually updated vulnerability and Java databases. It scans supported existing manifests, lockfiles and archives offline and performs secret detection. It never installs or resolves project dependencies through the network. Exact support is determined by Trivy's installed analyzers and available input metadata; unsupported formats and missing transitive metadata cannot be treated as complete coverage. A small curated advisory set remains an additional fallback, not the primary CVE database.

Updates download into a private staging snapshot, verify that the managed scanner can open the data, and atomically activate the snapshot. Failure preserves the previous active snapshot. Large download intermediates use snapshot storage rather than the small runtime tmpfs. The scanner mounts published snapshots read-only and uses a separate private copy for Trivy's writable database handles. Only security databases persist there; target artifacts and raw scanner reports are removed with the job workspace.

## Isolation and configuration

The worker has no internet route. Managed configuration is in backend/scanners and is outside the target tree. ESLint disables project config lookup and inline config; Ruff is isolated and ignores project gitignore rules; Bandit uses explicit YAML/INI and ignores nosec comments; Semgrep ignores target suppression files/comments. No target-supplied scanner plugins are loaded. Some language tools retain their own built-in source-suppression semantics; such behavior is not proof of exhaustive coverage.

Both scanner output streams are bounded and captured separately; diagnostic text cannot corrupt JSON parsing. Cppcheck XML is read from stderr with external entities disabled. Raw tool messages, matched credentials and full source files are not persisted. Findings outside the canonical source root are rejected; stored snippets are bounded and masked, including unterminated string literals.

## Evidence

Backend tests cover missing tools/data, adapter failure reporting, path confinement, secret-safe normalization, independent output bounds and masking. frontend/tests/coverage.spec.ts performs a real ten-language job, checks every installed scanner, verifies a live offline CVE finding, and supplies deliberately hostile project configuration. See TEST_REPORT.md and test-results.json for executed results. An installed tool alone is not evidence that a check passed.