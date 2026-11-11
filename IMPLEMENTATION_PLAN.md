# RepoGuard Implementation Plan

Stand: 2026-10-29

## 1. Analyse des vorhandenen Repositories

- Aktueller Stand: Das Repository enthält die vier verbindlichen Referenzdokumente und IDE-Metadaten; produktiver Anwendungscode war vor Beginn dieses Auftrags noch nicht vorhanden.
- Referenzgrundlage: `github_analyzer_projektzusammenfassung.md`, `REQUIREMENTS.md`, `DESIGN.md`, `ACCEPTANCE_CRITERIA.md` wurden vollständig gelesen und als verbindlich eingestuft.
- Git-Zustand: Branch `main`; Git meldet wegen unterschiedlicher Sandbox-User eine `dubious ownership`-Warnung. Für Inspektion wird `git -c safe.directory=C:/Projekte/test-analyzer ...` verwendet, ohne globale Konfiguration zu verändern.
- Entscheidungsrelevante Widersprüche: Keine unauflösbaren fachlichen Widersprüche erkannt. Die Umsetzung ist ambitioniert, aber technisch machbar, wenn Scannerdaten lokal bereitgestellt und nicht verfügbare sichere Einzelchecks transparent als `UNSUPPORTED`/`SKIPPED` markiert werden.
- Build-Umgebung: Java 21 ist verfügbar. Maven 3.9.11 wurde am 2026-10-29 lokal unter `.tools/` bereitgestellt; Backend-Abhängigkeiten liegen im projektlokalen Cache. Backend-Kompilierung und Unit-/MockMvc-Tests sind ausführbar. Frontend-/Compose-/Scanner-/Browser-Nachweise stehen weiterhin aus.

## 2. Zielarchitektur

```text
frontend/ React + TypeScript
  -> REST API
backend/ Java 21 + Spring Boot
  -> Import validation, quarantine, malware pre-scan, extraction, scan orchestration
  -> PostgreSQL persistence via Flyway/JPA
scanner/ trusted rule inventory and adapter metadata
database/ database docs and migrations mirror
docker-compose.yml local stack: frontend, backend, postgres, clamav/scanner services
tests/ safe fixtures for RepoGuard tests only
```

### Backend-Komponenten

- `api`: REST-Controller für Health, Import, Scanstatus, Findings, History, Comparison, Reports, Updates und Settings.
- `importing`: GitHub-URL-Parser, serverseitiger Download-URL-Builder, DNS-/Redirect-Policy, ZIP-Validator, Quarantäne und Cleanup.
- `scanner`: Scanner-Orchestrator, Adapter-Interface, verwaltete Scanner-Profile, Coverage-Erfassung und Normalisierung.
- `model`: Scan-, Finding-, Coverage-, Language-, Severity- und Statusmodelle.
- `persistence`: PostgreSQL-Entities/Repositories und Flyway-Migrationen.
- `reporting`: PDF-Export aus persistierten Findings/Coverage/Snippets, ohne aktiven Fremdcode.
- `security`: API-Validierung, Origin-/Host-Schutz, Secret-Masking und sichere Fehlerantworten.

### Frontend-Komponenten

- Dashboard, Neue Analyse, Analysen, Analysebericht, Einstellungen und Hilfe gemäß `DESIGN.md`.
- Gemeinsame Design-Tokens, Light/Dark Theme, DE/EN i18n, responsive Layouts.
- Keine Demo-Buttons: Jede Aktion bindet an echte API-Endpunkte oder zeigt bewusst `UNSUPPORTED`/`NOT READY` mit Backend-Status.

### Datenmodell

- `scan_jobs`: Quelle, Status, Phasen, Limits, Zeitstempel, Tool-/Datenversionen.
- `scan_sources`: sanitisiertes GitHub-/ZIP-Metadatum, Hashes, keine vollständigen Archive.
- `detected_languages`: Sprachgruppe, Dateien, Bytes, Erkennungshinweise.
- `scanner_checks`: Adapter, Sprache, Check, Status `COMPLETED|UNSUPPORTED|FAILED|SKIPPED`, Grund.
- `findings`: Kategorie, Severity, Regel, Tool, Sprache, Pfad, Zeile/Spalte, Erklärung, Abhilfe, Fingerprint.
- `finding_snippets`: begrenzte maskierte Ausschnitte mit Zeilenangaben; keine vollständigen Dateien.
- `update_runs`: manuelle Rule-/Signatur-/CVE-Update-Läufe und Datenstand.

## 3. REST-API-Plan

- `GET /api/health`: API-/DB-/Scanner-/Malware-Dienststatus.
- `POST /api/import/github/validate`: strikte GitHub-URL-Validierung ohne Download.
- `POST /api/scans/github`: validierte GitHub-Quelle importieren und Scan-Job starten.
- `POST /api/scans/zip`: ZIP hochladen, Quarantäne/Malware/Extraktion/Scan starten.
- `GET /api/scans/{id}` und `GET /api/scans/{id}/findings`: Status, Coverage und Findings.
- `GET /api/scans`: Historie mit Suche/Sortierung.
- `GET /api/scans/compare?left=&right=`: Fingerprint-basierter Vergleich.
- `GET /api/scans/{id}/report.pdf`: lokaler PDF-Export.
- `DELETE /api/scans/{id}`: persistierte Analyse und Snippets löschen.
- `POST /api/security-data/update`: manuelles Datenupdate.
- `GET /api/settings`: Limits, Datenstand, Scannerinventar.

## 4. Scanner-Adapter-Strategie

- Gemeinsames Adapter-Interface: `detectApplicability`, `buildInvocation`, `parseFindings`, `coverageStatus`.
- Jeder Adapter nutzt ausschließlich von RepoGuard verwaltete Regeln/Konfigurationen; Repository-Konfigurationsdateien werden nicht geladen.
- Adapter laufen isoliert, mit read-only Input, Arbeitszeit-/Speicher-/Outputlimits und möglichst ohne Netzwerk.
- Mindestgruppen:
  - Java: Semgrep/Trivy/Checkstyle oder sichere interne Regeln.
  - Python: Semgrep/Bandit/Trivy/Ruff.
  - Rust: Semgrep/Trivy/rustfmt-Check/sichere Regeln; kein Clippy-Build.
  - Swift: Semgrep/Trivy/SwiftLint mit verwalteter Config.
  - JS/TS: Semgrep/Trivy/ESLint mit verwalteten Regeln.
  - C/C++: Semgrep/Cppcheck/clang-format source-only.
  - C#: Semgrep/source-only Roslyn-/Syntaxregeln ohne Build.
  - Go: Semgrep/Trivy/gofmt/source-only Regeln, kein Build.
  - Kotlin: Semgrep/detekt mit externer verwalteter Config, kein Build.
  - PHP: Semgrep/Trivy/PHP_CodeSniffer mit festem Regelwerk.

## 5. Sichere Import-, Quarantäne- und Cleanup-Prozesse

1. Eingabe validieren und Quelle serverseitig aus geprüften Komponenten rekonstruieren.
2. Download/Upload nur in Quarantäne, mit Streaming-, Größen-, Timeout- und Content-Type-Limits.
3. Lokaler Malware-Vorscan fail-closed vor Extraktion.
4. Sichere ZIP-Extraktion mit Traversal-, Symlink-, Special-File-, Kollisions-, Größen- und Bombenschutz.
5. Scanner nur mit passivem Read-only-Input starten; niemals Build/Test/Install/Hook/Projektcode.
6. Findings normalisieren, Snippets maskieren, Coverage speichern.
7. Vollständige Archive und extrahierte Repository-Dateien nach Erfolg, Fehler oder Neustartbereinigung entfernen.

## 6. Implementierungsplan und Status

| Prio | Status | Abschnitt | Abhängigkeiten | Abnahmekriterien |
|---|---|---|---|---|
| P0 | IN PROGRESS | Projektgerüst, Compose, Backend-/Frontend-Basis, Health API | keine | SYS-01..SYS-04, SYS-09, SYS-10 |
| P0 | IN PROGRESS | GitHub-URL-Validator und begrenzter Quarantäne-Downloader implementiert; DNS/IP-Policy an Connection Manager gekoppelt, Redirects gesperrt; Live-TLS-/GitHub-/E2E-Abnahme noch offen | Backend-Basis | IMP-01..IMP-07, IMP-12, IMP-16 |
| P0 | TODO | ZIP-Upload, Quarantäne, Malware-Fail-Closed, sichere Extraktion | Storage/Limits | IMP-08..IMP-15, SEC-05 |
| P0 | TODO | Scan-State-Machine und PostgreSQL-Migrationen | DB/Backend | SYS-05, ANA-08, ANA-09, DATA-03 |
| P0 | TODO | Scanner-Orchestrator, Finding-Modell, Coverage-Status | Import/DB | LANG-13..LANG-15, ANA-01, ANA-05..ANA-07 |
| P0 | TODO | Adapter für zehn Sprachgruppen, sichere interne Fallback-Regeln | Scanner-Orchestrator | LANG-01..LANG-12, ANA-02, ANA-03 |
| P1 | TODO | Dependency-/Secret-/Config-Prüfungen mit lokalem Datenbestand | Scannerdaten | ANA-04, DATA-10..DATA-13 |
| P1 | TODO | UI-Kernworkflow mit DE/EN, Light/Dark, responsiv | API-Basis | UI-01..UI-13 |
| P1 | TODO | Historie, Filter, Vergleich, maskierte Snippets | DB/Finding-Modell | DATA-01..DATA-08 |
| P1 | TODO | PDF-Bericht | Persistierte Ergebnisse | DATA-06, DATA-09 |
| P1 | TODO | Sicherheitshärtung: CORS/Host/Origin, Limits, Container, Cleanup Recovery | alle Kernmodule | SEC-01..SEC-09, EXE-01..EXE-07 |
| P2 | TODO | Tests: Unit, Integration, Security, Browser-E2E, Fixtures | Implementierung | alle Kriterien, E2E-01..E2E-10 |
| P2 | TODO | Dokumentation, `TEST_REPORT.md`, `test-results.json`, Agent Workflow | Tests/Builds | DOC-01..DOC-10, AGENT-01..AGENT-08 |

## 7. Architekturentscheidungen

- GitHub-Import nutzt Archivdownload über serverseitig gebaute URLs, keinen `git clone`.
- Malware-Vorscan ist verpflichtend und fail-closed; falls ClamAV nicht bereit ist, startet keine Codeanalyse.
- Für den Entwicklungsbeginn wird ein interner source-only Regeladapter als sicherer Mindest-Fallback geplant, damit die zehn Sprachgruppen echte quelltextbasierte Befunde liefern können, auch wenn externe Scanner lokal fehlen. Externe Scanner bleiben als Adapter integrierbar und werden transparent als `UNSUPPORTED`/`FAILED` ausgewiesen, wenn sie nicht verfügbar sind.
- Historische Codeansicht speichert nur maskierte Snippets, nicht komplette Fremddateien.
- Tests von RepoGuard dürfen laufen; Tests/Builds importierter Zielprojekte bleiben verboten.

## 8. Offene Risiken

- Scanner-Verfügbarkeit: Externe Tools wie Semgrep, Trivy, Bandit, Ruff, SwiftLint, Cppcheck, detekt oder PHP_CodeSniffer müssen in Images reproduzierbar bereitstehen oder transparent als nicht verfügbar markiert werden.
- Standard-Branch ohne GitHub REST API: Downloader nutzt den direkten offiziellen Archivpfad `/zip/HEAD` statt des falschen Branchpfads `/zip/refs/heads/HEAD`; echter GitHub-Netzwerktest steht aus.
- Docker-/Frontend-Umgebung: Backend-Tests sind inzwischen ausführbar. Frontend-/Containerbuild und vollständige Stack-Abnahme sind noch nicht nachgewiesen.
- Vollständige DoD erfordert reale Scanner- und Browser-E2E-Nachweise; diese dürfen nicht simuliert werden.

## 9. Fortsetzung am 2026-10-29

- Nutzerauftrag: „contiune“; anhand des vorhandenen Checkouts und Plans als Fortsetzung des nächsten P0-Importbausteins umgesetzt.
- URL-Parser akzeptiert einen optionalen abschließenden Slash, verwirft Prozentkodierungen, mehrdeutige Segmente, ungültige Owner und zu lange Eingaben.
- Download-Service rekonstruiert die Ziel-URL intern, verwendet ausschließlich geprüfte öffentliche DNS-Adressen für die tatsächliche Verbindung und behält TLS-Hostnameprüfung bei. Kein Proxy aus System-/Umgebungseinstellungen, keine automatischen Redirects, Retries, Cookies oder Dekompression.
- Download prüft Status, Medientyp, ZIP-Signatur, deklarierte und tatsächliche Bytes, Gesamtdauer und SHA-256. Fehler und geschlossene Quarantäneobjekte entfernen temporäre Dateien. Verbindungen werden vor dem Schließen ungültiger Streams abgebrochen, damit der HTTP-Client keine überschüssigen Antwortdaten nachliest.
- Der Dienst ist bewusst noch kein Scan-Endpunkt. Nächster fachlicher Schritt: gemeinsamer ZIP-Upload/Quarantänepfad, obligatorischer Malware-Vorscan und sichere Archivvalidierung/-extraktion. Danach Job-Zustandsmaschine und Scannerintegration.
- Nachweise: `TEST_REPORT.md`, `test-results.json`, `docs/test-evidence/github-import-tests.txt`. Keine vollständige IMP-/E2E-Abnahme und keine Releasefreigabe aus diesen isolierten Backend-Tests ableiten.

## Fortsetzung am 2026-11-01

Implementiert: TypeScript/Vite-Build, responsive React-Oberflaeche mit DE/EN und Light/Dark, GitHub-/ZIP-Jobstart, verpflichtendes ClamAV-Gate, sichere ZIP-Extraktion, isolierter Source-only-Scanner fuer zehn Sprachgruppen, maskierte Findings, PostgreSQL-Historie, Filter, Fingerprint-Vergleich, Loeschen, PDF und manueller signierter Malware-Datenupdatepfad. Der Compose-Stack wurde real gestartet und das erste Signaturupdate erfolgreich ausgefuehrt. API, Scanner und Updatedienst sind getrennt; nur die Weboberflaeche veroeffentlicht einen Port.

Die bisherige TODO-Tabelle beschreibt den urspruenglichen Plan und ist keine aktuelle Abnahmeliste. Aktuelle Nachweise stehen in TEST_REPORT.md und test-results.json. Weiter offen: umfassende externe Scanner-/CVE-Abdeckung, vollstaendige Abnahme inklusive LAN, Netzwerknachweisen, Neustart/Backup und PDF-Sichtpruefung. Die enthaltenen Textregeln und der kleine kuratierte CVE-Bestand werden bewusst als eingeschraenkt ausgewiesen. Keine vollstaendige Produktionsfreigabe behaupten.

## Analyseabdeckung am 2026-11-02

Die zuvor offene externe Scannerintegration ist umgesetzt: Semgrep, SwiftLint, rustfmt, ESLint, detekt, Checkstyle, gofmt, PHP_CodeSniffer, Bandit, Ruff, Cppcheck und Trivy laufen mit verwalteten Konfigurationen. Ein echter passiver Zehn-Sprachen-Test lieferte aus jedem der zwoelf Scanner mindestens einen normalisierten Befund; Trivy meldete echte Offline-CVEs. ClamAV- und Trivy-Daten wurden erfolgreich aktualisiert. Scannerfehler, fehlende Daten, Zeitlimits und nicht unterstuetzte Pruefungen bleiben sichtbar.

Abschlussnachweis: 93 Backend-Tests und sechs Browser-/End-to-End-Tests bestanden. Versionen, Befundzahlen und Grenzen stehen in docs/test-evidence/scanner-coverage.json. Nach den Tests waren die Arbeitsverzeichnisse leer, die Projektkonfiguration hatte keinen Ausfuehrungsmarker erzeugt, der Worker hatte keine Standardroute und die Datenbank enthielt keine unmaskierten Test-Geheimnisse. Build-/Compiler-/Typabhaengige Pruefungen und nicht unterstuetzte Abhaengigkeitsformate bleiben ausserhalb der erlaubten statischen Analyse. Die weiteren oben genannten betrieblichen Abnahmepunkte bleiben offen.

## Verbindliche Dokumente und zehn Originalarchive am 2026-11-02

Der ausdrueckliche Folgeauftrag verlangt einen Abgleich mit den Referenzdokumenten und zehn echte GitHub-Projekte. Alle vier verbindlichen Dokumente wurden erneut beruecksichtigt. Sichere interne Dateilinks werden jetzt ohne Betriebssystem-Symlinks als begrenzte schreibgeschuetzte Zielkopien analysiert; schadhafte, fehlende, zyklische und Verzeichnislinks bleiben gesperrt. Lesbare Dateipfade und priorisierte begrenzte Findings wurden korrigiert. Neue Regressionen und vorhandene Suite: 98 Backend-Tests und sechs Browser-Tests bestanden.

Die zehn unveraenderten Originalarchive wurden direkt ueber den regulaeren GitHub-Import geprueft, ohne Ersatz-ZIPs und ohne fremde Codeausfuehrung. Ergebnis: vier eingegrenzte Probe-PASS, sechs FAIL. Der claude-mem-Originalimport bleibt durch den Malware-Vorscan blockiert; Fund und Scanlimit sind dort noch nicht unterscheidbar. Die fruehere Entfernung von sieben Links ist keine akzeptierte Loesung oder Abnahme. Verzeichnislinks, Parserfehler, Befundrauschen/-limits, normalisierte Fachinformationen und weitere MUSS-Kriterien bleiben offen. docs/REAL_REPOSITORY_ACCEPTANCE.md und docs/ACCEPTANCE_STATUS.md ersetzen fruehere Teilnachweise nicht, sondern ordnen sie ein. Keine produktive Freigabe; kein Commit/Push.

## Verbindliche Prompt-Dokumentation (Ergänzung 2026-11-06)

Vor jeder inhaltlichen Bearbeitung einer neuen projektbezogenen Nutzernachricht zuerst den Eintrag in [PROMPTS.md](PROMPTS.md) ergänzen; bei Sitzungsbeginn [AGENTS.md](AGENTS.md) und die verbindlichen Dokumente lesen. Fortlaufende IDs, bekannte Daten und Quellen pflegen, Duplikate vermeiden, Anhänge nur referenzieren und Zugangsdaten ausschließen. Keine `.env`-Inhalte übernehmen. Diese Pflegepflicht ist Bestandteil aller weiteren Umsetzungsschritte.


## Umsetzung des Fixplans und Selbstprüfung, 2026-11-11

Umgesetzt: SQL-/Secret-Kontext, Trennung inerter Daten von ausführbaren Mustern, zentrale Scanner-Startpolicy, positive/negative Umgehungs-Gegenproben sowie Selbstscan desselben und des aktuellen Inputs. 168 Backend- und 8 Browser-/API-Tests PASS. Übrige Produktabnahme bleibt offen. [Selbstprüfungsbericht](docs/SELF_REVIEW_2026-11-11.md)
