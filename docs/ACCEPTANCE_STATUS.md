# Verbindlicher Abnahmestatus

Stand: 2026-11-03T08:19:09.2266298Z

**Keine produktive Freigabe.** Die vier Projektdokumente gelten unverÃ¤ndert. Nicht vollstÃ¤ndig belegte Kriterien bleiben NOT TESTED; vorhandene Teiltests werden nicht als vollstÃ¤ndige Abnahme umgedeutet. FAIL bedeutet eine konkret festgestellte Abweichung. Die zehn Realprojekt-Proben sind getrennt von diesen MUSS-Kriterien zu bewerten.

Die gezielt nachgewiesenen Kriterien stehen unten. Insbesondere LAN-/Offline-Netzwerkmitschnitt, Prozess-/Sandbox-Audit, vollstÃ¤ndige Sprach-Einzelabnahme, Restart/Backup, PDF-SichtprÃ¼fung, LizenzprÃ¼fung und System-Schwachstellenbewertung sind dadurch nicht freigegeben.

[Praxistest mit zehn Originalarchiven](REAL_REPOSITORY_ACCEPTANCE.md) Â· [Maschinenlesbare Einzelkriterien](test-evidence/acceptance-status.json)

| Kriterium | Vorgabe | Status | Nachweis / offene Abweichung |
|---|---|---|---|
| SYS-01 | Name | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| SYS-02 | Stack | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| SYS-03 | Compose | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| SYS-04 | Health | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| SYS-05 | Persistenz | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| SYS-06 | Konfiguration | FAIL | Parallelism remains hard-coded to one; not every required runtime limit is configurable and fully represented in UI. New malware/result limits do not complete this criterion. |
| SYS-07 | Keine HostNox-Reste | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| SYS-08 | Betrieb ohne Cloud | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| SYS-09 | Containerprivilegien | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| SYS-10 | Reproduzierbarkeit | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| IMP-01 | GitHub URL | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| IMP-02 | Falsche Hosts | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| IMP-03 | URL-Sonderfälle | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| IMP-04 | Neuerzeugte Downloadadresse | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| IMP-05 | SSRF | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| IMP-06 | Redirect/TLS | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| IMP-07 | Archiv statt Checkout | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| IMP-08 | ZIP-Upload | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| IMP-09 | ZIP-Slip | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| IMP-10 | ZIP-Bomb | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| IMP-11 | Dateitypen | PASS | CoreAnalysisTest and RealRepositoryRegressionTest: dangerous/special/colliding entries rejected; safe internal file links remain passive; copied bytes bounded. Directory links remain unsupported, see REAL-03. |
| IMP-12 | Downloadlimits | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| IMP-13 | Quarantäne | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| IMP-14 | Malware | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| IMP-15 | Fail-closed | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| IMP-16 | Kein Quellcode-Cloudtransfer | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| EXE-01 | Keine Projektausführung | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| EXE-02 | Kein Build/Test | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| EXE-03 | Keine Hooks | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| EXE-04 | Vertrauenswürdige Regeln | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| EXE-05 | Scanner-Isolation | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| EXE-06 | Beobachtbarer Nachweis | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| EXE-07 | Kein dynamischer Modus | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| LANG-01 | Java | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| LANG-02 | Python | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| LANG-03 | Rust | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| LANG-04 | Swift | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| LANG-05 | JavaScript/TypeScript | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| LANG-06 | C/C++ | FAIL | Cppcheck is installed, but the specified C/C++ format-check adapter is missing. REAL-06 also has Semgrep partial failure; positive separate C and C++ acceptance remains incomplete. |
| LANG-07 | C# | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| LANG-08 | Go | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| LANG-09 | Kotlin | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| LANG-10 | PHP | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| LANG-11 | Mixed repo | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| LANG-12 | Unbekannte Sprache | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| LANG-13 | Coverage | PASS | Per-tool statuses, versions and reasons retained in all real-project results and browser coverage view. This does not accept each individual language. |
| LANG-14 | Fehlertransparenz | PASS | ExternalScannersTest plus actual REAL-06/REAL-09: unavailable/failed tools explicitly reported; never inferred successful from zero findings. |
| LANG-15 | Verfügbare Checks | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| ANA-01 | Kategorien | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| ANA-02 | Sicherheitsmuster | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| ANA-03 | Qualitätsmuster | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| ANA-04 | Dependencies | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| ANA-05 | Keine Überversprechen | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| ANA-06 | Finding-Felder | FAIL | Tool versions and paths are now normalized correctly. Several explanations/remediations remain generic; dependency package/version context is still insufficient. |
| ANA-07 | Duplikate | PASS | BoundedFindings deduplicates retained fingerprints; regression verifies duplicate input does not inflate retained findings. |
| ANA-08 | API-Verlässlichkeit | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| ANA-09 | Scanstatus | PASS | Actual real-project states PARTIAL, FAILED and BLOCKED are distinct and retained in results.json. |
| UI-01 | Branding | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| UI-02 | Design | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| UI-03 | Themes | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| UI-04 | Sprachen | FAIL | Several runtime errors, coverage reasons and report labels remain English in the German workflow. Theme/locale switching tests do not prove full translation. |
| UI-05 | Responsivität | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| UI-06 | A11y | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| UI-07 | Offline UI | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| UI-08 | Workflow | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| UI-09 | Progress | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| UI-10 | Findings | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| UI-11 | Fehler | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| UI-12 | Sicherheitshinweis | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| UI-13 | Aktive Inhalte | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DATA-01 | Löschung | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DATA-02 | Fehlerbereinigung | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DATA-03 | Persistenz | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DATA-04 | Keine Vollquellen | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DATA-05 | Snippet-Limit | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DATA-06 | Secrets | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DATA-07 | Historie | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DATA-08 | Löschen | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DATA-09 | PDF | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DATA-10 | Datenupdate | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DATA-11 | Scan-Netztrennung | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DATA-12 | Offline/Altstand | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DATA-13 | Updatefehler | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| SEC-01 | LAN ohne Login | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| SEC-02 | Keine Internetöffnung | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| SEC-03 | API-Schutz | PASS | Browser SEC-03 and BrowserRequestFilter tests: hostile origins, missing mutation header and hostile Host rejected. |
| SEC-04 | Eingaben/XSS | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| SEC-05 | Limits | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| SEC-06 | Isolation | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| SEC-07 | DB | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| SEC-08 | Logs | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| SEC-09 | Systemrisiko | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| SEC-10 | Fehlerbehandlung | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DOC-01 | Quellcode | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DOC-02 | Docker | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DOC-03 | Anleitung | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DOC-04 | Sicherheitsdoku | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DOC-05 | Datenhaltung | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DOC-06 | Scanner-Matrix | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| DOC-07 | Testdoku | PASS | Actual backend/browser tests, ten unchanged GitHub inputs and their failures are documented with evidence. No product release claimed. |
| DOC-08 | Lizenzen | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| AGENT-01 | IDE-Agent | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| AGENT-02 | Git-Anbindung | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| AGENT-03 | Prompt-gesteuert | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| AGENT-04 | Agentenimplementierung | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| AGENT-05 | Nachvollziehbarkeit | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| AGENT-06 | Dokumentkontext | PASS | All four binding documents reread; this IDE-agent task maps changes to IMP-11, ANA-06 and ANA-09 with regression tests and real inputs. |
| AGENT-07 | Sicherheitsgrenze | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |
| AGENT-08 | Übergabe | NOT TESTED | No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS. |

## Verbindliche Prompt-Dokumentation (Ergänzung 2026-11-06)

**DOC-PROMPT-01: teilweise nachgewiesen; Folgesitzung NOT TESTED.** Erstaufnahme der 18 sichtbaren Nutzernachrichten in [PROMPTS.md](../PROMPTS.md) und verbindliche Pflegepflicht in [AGENTS.md](../AGENTS.md) sowie den übrigen Projektdokumenten. Neue projektbezogene Nachrichten sind vor der Bearbeitung nachzutragen. Anhänge nur referenzieren; keine Zugangsdaten oder `.env`-Inhalte übernehmen. Fehlende frühere Historie bleibt als solche gekennzeichnet. Die langfristige Einhaltung ist mit einer späteren Nachricht zu prüfen. Bestehende Produktabnahmekriterien und releaseReady bleiben unverändert.
