# RepoGuard Architecture Decisions

## Nutzerentscheidung: verbindliches Promptprotokoll (2026-11-06)

Neue projektbezogene Nutzernachrichten werden vor der Bearbeitung gemäß [PROMPTS.md](../PROMPTS.md) mit fortlaufender ID, bekanntem Datum, Quelle und ursprünglichem Wortlaut dokumentiert. [AGENTS.md](../AGENTS.md) und die Pflichtdokumente verankern diese Regel. Rückfragen, Korrekturen, Freigaben und Fortsetzungen gehören dazu. Anhänge nur referenzieren, Zugangsdaten maskieren, keine `.env`-Inhalte übernehmen. Nicht verfügbare Historie bleibt ausdrücklich unvollständig. Es wird kein automatischer Hintergrundexport eingerichtet.

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

## 2026-11-11: Generierte Nachweise lokal halten

Auf ausdrücklichen Nutzerwunsch werden rohe Reports, Scanresultate, Logs und Nachweisarchive unter `docs/test-evidence/` nicht mehr über Git synchronisiert. Vorhandene lokale Dateien bleiben erhalten; bereits getrackte bzw. vorgemerkte Artefakte werden ausschließlich aus dem Git-Index entfernt. Der Ablage-Leitfaden sowie knappe Test-, Abnahme- und Review-Zusammenfassungen bleiben versionierbar. Deren Detailpfade verweisen auf lokale Nachweise; ein neuer Checkout enthält diese möglicherweise nicht. Frühere Commits werden nicht umgeschrieben. Die Pflicht zu echten Nachweisen und ehrlicher Abnahmekennzeichnung bleibt bestehen.
