# RepoGuard: Fix und Selbstprüfung vom 11.11.2026

Stand: 2026-11-11T11:45:37.014239+00:00. Regelversion: `repoguard-source-2026.11.11.1`. **Keine vollständige Produktfreigabe; releaseReady bleibt false.**

## Ergebnis

| Eingabe / Scannerstand | Status | HIGH | MEDIUM | LOW |
|---|---|---:|---:|---:|
| Vorher: eigenes Originalarchiv, bisheriger Scanner | COMPLETED | 21 | 3 | 2061 |
| Exakt dasselbe Archiv, korrigierter Scanner | COMPLETED | 2 | 5 | 2059 |
| Aktueller Quellstand inklusive neuer Tests | COMPLETED | 2 | 11 | 2192 |

Alle drei Scans enthalten null CRITICAL. Der faire Vergleich verwendet zweimal exakt SHA-256 `f26f2fe892d286a211385f4c449548ac078c35f9d5540ec9da6854d6fcf47600`. Das aktuelle Archiv hat SHA-256 `61b743532e0af0ce38d2a06ecc479d2a58067fce9d8aec68b6adb392ceb0c6b4` und enthält 178 Dateien, darunter sämtliche 24 Dateien unter den Backend-, Frontend- und allgemeinen Testpfaden. Keine Testverzeichnisse wurden ausgeschlossen. Das Archiv erfasst den Quellstand vor Erstellung dieses Abschlussberichts; Code-Hashes wurden danach nochmals mit dem Manifest verglichen.

Scan-IDs: vorher `46ab8d23-e5ca-4a93-bdac-cb36ef0e4ce8`, gleicher Input `fb1447b2-17a5-4c1f-bf21-3fc57b41f67b`, aktueller Stand `c8820098-3b6a-497c-84a4-e3446e2c4916`. Die ZIP-Scans liegen in der lokalen Analysehistorie. Vollständige maskierte Ergebnisse und Manifeste: [Nachweisverzeichnis](test-evidence/self-review-2026-11-11/verification-summary.json).

## Einordnung der ursprünglichen 21 HIGH-Meldungen

| Gruppe | Umsetzung / verbleibender Befund |
|---|---|
| 10 SQL-Warnungen | UI-Bezeichnungen und URLs lösen keine SQL-Regel mehr aus. SQL-Konstruktionen werden im String-/Codekontext geprüft. Sichere lokale JDBC-Platzhalter bleiben erlaubt; Neuzuweisung, andere Methoden und gleichnamige Parameter verhindern eine solche Freistellung. |
| 5 JavaScript-eval-Warnungen | Inerte Strings und Kommentare werden von ausführbaren Aufrufen unterschieden. Echte Aufrufe, auch in Testdateien, mehrzeilig und in Template-Interpolation, bleiben nachgewiesen. |
| 4 Secret-Warnungen | Die Kategoriebeschriftung SECRET/Secrets wird als Anzeige-Dictionary erkannt. Direkte Credential-Zuweisungen bleiben HIGH. Eingebettete Credential-Kandidaten bleiben als MEDIUM `RG-SECRET-DATA` zur Prüfung sichtbar; sie werden nicht automatisch als harmlos bewertet. Provider-Tokens und private Schlüssel werden weiterhin auch in Strings und Kommentaren gesucht. |
| 2 Java-Prozesswarnungen | Der passive Teststring erzeugt keinen Ausführungsbefund mehr. Der echte `ProcessBuilder` bleibt als sensibler Aufruf sichtbar und ist nun zentral auf verwaltete Programme, Runtime-Profile, Konfigurationspfade und erlaubte Umgebungsvariablen beschränkt. |

Die beiden verbleibenden HIGH-Prüfhinweise sind `CoreAnalysisTest.java:44` (absichtlich synthetischer Testwert) und `TrustedProcess.java:24` (verwalteter Prozessstarter). Sie wurden geprüft und bewusst nicht anhand ihres Dateipfads ausgeblendet. Der Prozessbefund allein beweist keine Command Injection; die zentralen Sperren sind durch Negativtests belegt. Die übrigen MEDIUM-/LOW-Befunde bleiben im Bericht erhalten, einschließlich Qualitätsbefunden und Werkzeughinweisen auf den lokalen Testclient.

## Änderungen

- `SourceContext`: begrenzte lexikalische Erkennung von Literalen, Kommentaren, mehrzeiligen Strings, ausgewählten Interpolationen und JS-RegExp-Literalen; ursprüngliche Zeilenbezüge bleiben erhalten.
- `SourceRuleEngine`: kontextabhängige SQL-/Secret-Regeln, ausführbare Muster auf dem bereinigten Code und eigene sichtbare Credential-Review-Regel. Verwaltete externe AST-Scanner bleiben unabhängig aktiv.
- `ManagedCommandPolicy` / `TrustedProcess`: keine Shells, Repository-Programme, fremden Java-JARs oder Node-Skripte; kein Konfigurations-Traversal oder Runtime-Environment-Injection. Abweichende frei konfigurierte Scannerpfade werden fail-closed abgelehnt.
- `ContextRulesTest` / `TrustedProcessTest`: positive und negative Fälle einschließlich echter Muster in Testpfaden, gebundener SQL-Parameter, veränderter Platzhalter, Schattenvariablen, Kommentarzwischenräumen, String-Interpolation, Secret-Maskierung, Job-Deadline und tiefer Verschachtelung.
- `tests/run-self-review.py`: reproduzierbarer passiver ZIP-Selbstscan mit Dateihashes, Statusprüfung, Rohresultaten und Bereitschaftsprüfung. Keine Zielprojekt-Builds oder Tests werden gestartet.

## Tatsächlich ausgeführte Prüfungen

- **168 Backend-Tests PASS**, null Fehler/übersprungene Tests; ausführbares JAR gebaut. `mvn -B -o -f backend/pom.xml package` nach initialer Bereitstellung der eigenen Build-Abhängigkeiten.
- **8 Browser-/API-Tests PASS** mit Playwright und lokal installiertem Chrome: alle zwölf verwalteten Scanner auf passiven Zehn-Sprachen-Fixtures, echte Offline-Trivy-CVEs, Maskierung, Historie und atomare Löschung, Malware-/Traversal-Sperren, Origin-/Host-Schutz, PDF-Download sowie DE/EN und Light/Dark bei drei Breiten.
- **TypeScript-Prüfung und Frontend-Produktionsbuild PASS** (`npm run build`). Frontend-Produktcode wurde nicht geändert.
- Zwei korrigierte Selbstscans **COMPLETED**, keine fehlgeschlagene oder übersprungene anwendbare Prüfung und keine Befundkürzung.
- Worker-Arbeitsbereich und API-Quarantäne nach den Tests leer; Ausführungsmarker fehlt; Scanner-Netz intern und keine IPv4-Standardroute. Null Vorkommen der geprüften unmaskierten Testwerte in gespeicherten Ergebnissen.
- Alle drei gestarteten Java-Dienste enthalten exakt das gebaute JAR `840b675e97be667e0421b90d7952030c52379c33dddcc19a3c76d3e728396f5c`. Für diesen lokalen Test wurde das JAR auf die vorhandenen Laufzeit-/Scannerimages gelegt; kein vollständiger frischer Scanner-Image-Build wird behauptet. Image-IDs stehen in `deployment.json`.

Die vollständigen Browsernachweise stehen in `browser-results.json`, Scannerbefunde in `ten-language-coverage.json`, Backend-Einzelfälle in `backend-cases.json`, Laufzeitprüfungen in `runtime-checks.json`. Zwischenstände und fehlgeschlagene bzw. unterbrochene Vorläufe bleiben getrennt erhalten und zählen nicht als PASS. Temporäre lokale ZIP-Exporte werden nach Abschluss entfernt; die Ergebnisdateien und Hash-Manifeste bleiben erhalten.

## Grenzen und Abnahme

Die neue Kontextanalyse ist ein begrenztes lexikalisches Hilfsmittel, kein vollständiger Parser, keine Typ- oder interprozedurale Taint-Analyse. Komplexe Sprachkonstrukte, indirekte Datenflüsse, beliebige Secret-Formate und sämtliche Schwachstellen sind damit nicht vollständig abgedeckt. Die positiven Gegenproben belegen konkrete Fälle, keine allgemeine Sicherheitsgarantie. Echte Provider- oder Credential-Kandidaten werden nicht aufgrund eines Testpfads ignoriert; unklare Datenbefunde erfordern weiterhin Prüfung.

Die Änderungen liefern gezielte Nachweise zu ANA-02/05/06/09, EXE-03/04, LANG-13, DATA-06 und SEC-03. Sie schließen die übrige Produktabnahme, einen vollständigen Prozess-/Netzwerkaudit, alle Sprach-Einzelabnahmen oder eine System-CVE-Prüfung nicht ab. Bestehende FAIL/NOT TESTED-Kriterien und frühere Berichte werden nicht rückwirkend zu PASS geändert. Keine Commit-/Push-Aktion wurde vorgenommen.


## Platzsparende Nachweisablage, 2026-11-11

Die vollständigen Vorher-/Nachher-Rohresultate liegen jetzt verlustfrei als `before-result.json.gz`, `same-input-result.json.gz` und `after-result.json.gz` vor. Zwischenstände und ältere Versuchslogs wurden bytegetreu in `intermediate-runs.tar.gz` archiviert; `archive-index.json` enthält Originalpfade und SHA-256-Nachweise. Die abschließenden Zusammenfassungen bleiben direkt lesbar. Keine Befunde oder Status wurden entfernt oder geändert. Der Testclient schreibt künftig ebenfalls komprimierte Rohresultate; seine Archivierungsänderung erfolgte nach dem oben nachgewiesenen Selbstscan. Anwendungscode und laufender Stack bleiben unverändert. [Ablageübersicht](test-evidence/self-review-2026-11-11/README.md).
