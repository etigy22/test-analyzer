# RepoGuard Architecture Decisions

## ADR-001: Use GitHub source archives, not `git clone`

RepoGuard rebuilds allowed GitHub download URLs from validated owner/repository components. It does not pass user URLs to Git or shell commands, and it does not clone repositories, initialize submodules, or trigger hooks.

## ADR-002: Malware pre-scan is fail-closed

The malware pre-scan is a required gate before code extraction and static analysis. If the local malware scanner is unavailable, times out, or reports a finding, RepoGuard blocks the analysis and records a safe failure.

## ADR-003: Scanner coverage is explicit

Every language/tool/check records `COMPLETED`, `UNSUPPORTED`, `FAILED`, or `SKIPPED`. A scanner that did not run is never rendered as “0 findings”.

## ADR-004: Persist snippets, not full imported files

RepoGuard persists only bounded, masked finding snippets and metadata. Complete ZIP archives and extracted repository files are temporary and must be cleaned up after completion, failure, or recovery.

## ADR-005: Direct default-branch archives and DNS-bound connections

The internal downloader reconstructs the official `codeload.github.com/<owner>/<repository>/zip/HEAD` URL. `HEAD` is a reference, not a branch named `HEAD`; the previous `/zip/refs/heads/HEAD` path was incorrect. This avoids default-branch guessing and API calls. All redirects are rejected because this direct endpoint should not need them; changes in GitHub behavior must fail safely and be reviewed. Live behavior and TLS rejection tests remain outstanding.

Apache HttpClient 5 uses RepoGuard's allowlisted DNS resolver inside its connection manager. The resolver returns only validated public addresses, so validation is coupled to the addresses actually used to connect, while TLS still verifies `codeload.github.com`. The client does not enable system proxy settings or automatic retries. It aborts requests before closing entity streams to prevent draining oversized or rejected bodies. A separate wall-clock timeout also bounds DNS, connection, and streaming work from the caller's perspective.

The downloaded archive remains unverified passive input. It has no public download/scan endpoint until the mandatory malware gate and safe ZIP handling are connected. Each successful internal caller owns a closeable quarantine object; recovery after process crashes remains a future orchestration responsibility.
