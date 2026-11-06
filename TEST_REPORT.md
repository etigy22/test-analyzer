# RepoGuard test report

Commit preparation, 2026-11-06: the expanded scanner adapters use Semgrep 1.183.0, bounded ESLint input batches, concise Ruff diagnostics and a 10 MB text limit. The backend regression suite has 112 passing tests; frontend TypeScript checking and the production build pass. The nine browser passes below predate these latest adapter changes. The focused fmt retry still reports a Semgrep parser error (docs/test-evidence/expanded-fmt); no claim that all ten repositories complete successfully is made. Expanded claude-mem and a fresh strict ten-repository run remain outstanding.

Generated: 2026-11-03T10:50:14.6600006Z

Backend: 111 PASS, 0 FAIL. Browser: 9 passed, 0 failed, 0 skipped.

Real original GitHub scans with persisted COMPLETED and zero applicable gaps: 5. Evidence and scope explanation: docs/FIVE_COMPLETED_SCANS.md.

Evidence: backend/target/surefire-reports and frontend/test-results (JSON, screenshots and traces). Detailed outcomes: test-results.json. All target fixtures remain passive.

Executed scanner versions, finding counts and explicit coverage limits: docs/test-evidence/scanner-coverage.json. Post-run checks found empty worker/backend workspaces, no executed target-configuration marker, no worker default route and zero stored occurrences of the unmasked fixture secrets.

Ten unmodified real GitHub projects: docs/REAL_REPOSITORY_ACCEPTANCE.md and docs/test-evidence/real-repositories/results.json. A real-project probe PASS is scoped to its declared expectations and does not imply product acceptance. Remaining mandatory criteria and confirmed failures: docs/ACCEPTANCE_STATUS.md. releaseReady remains false.

Backend checks include ten-language source rules, scanner normalization and failures, bounded separate output streams, masking, fingerprints, curated CVEs, ZIP attacks, cleanup, malware gating and origin/host checks. Browser cases exercise all twelve installed scanners on passive ten-language input, real offline Trivy CVE findings, hostile project configuration, ZIP scans, filtering, masked details, PDF download, EICAR and traversal blocking, history/comparison/deletion, German/English and light/dark at three widths. Failed/skipped cases are not accepted.

Docker startup and the ClamAV/Trivy security data update succeeded. Installed tools and their limits are documented in scanner/SCANNER_INVENTORY.md. Compiler/type/build-dependent analysis, unsupported dependency formats and missing transitive metadata remain outside coverage. Full operational acceptance remains incomplete: live GitHub/TLS/network tracing, restart/backup acceptance, independent LAN-device access and PDF visual inspection are outstanding. No production/security release approval is claimed.

## Verbindliche Prompt-Dokumentation (Ergänzung 2026-11-06)

Zusätzliches verbindliches Kriterium DOC-PROMPT-01: [PROMPTS.md](PROMPTS.md) enthält die 18 aktuell verfügbaren Nutzernachrichten und die Pflegepflicht für neue Nachrichten. [AGENTS.md](AGENTS.md) sowie Anforderungen, Abnahmekriterien, Design, Projektzusammenfassung und Umsetzungsplan verweisen auf diese Regel. Die Erstaufnahme wird auf fortlaufende IDs, Vollständigkeit des sichtbaren Verlaufs und fehlende vertrauliche Dateiinhalte geprüft. Die fortlaufende Einhaltung in einer späteren Sitzung ist noch nicht nachgewiesen; dadurch entsteht keine vollständige Produktabnahme.
