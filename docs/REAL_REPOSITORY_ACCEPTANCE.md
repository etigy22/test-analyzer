# RepoGuard: zehn unveränderte GitHub-Projekte

**Historische Erstserie vom 02.11.2026.** Den aktuellen Fehlerbehebungsstand mit acht bestandenen Proben, den verbleibenden zwei Scannergrenzen und dem Rust-Nachtest beschreibt [FIX_STATUS_2026-11-03.md](FIX_STATUS_2026-11-03.md). Die Originalergebnisse unten werden nicht rückwirkend überschrieben.

## Ergebnis und Freigabe

**Keine produktive Freigabe.** Die Projektdokumente sind verbindlich: `ACCEPTANCE_CRITERIA.md` fordert alle MUSS-Kriterien mit PASS und Nachweis; ein Build oder eine Demo reichen ausdrücklich nicht. Die folgende Serie ist ein realer Import-/Scanner-Praxistest und ersetzt diese Gesamtabnahme nicht.

Am 02.11.2026 wurden zehn bekannte Projekte aus den zehn Pflichtsprachgruppen mit `repoguard-source-2026.11.02.2` geprüft. Auswahl nach Sprachabdeckung, keine behauptete Popularitätsrangliste. Der Nutzerauftrag war die Prüfung des Tools; die Projekte selbst wurden weder gebaut, getestet, installiert noch ausgeführt.

**Alle zehn Eingaben gingen als GitHub-URL über `/api/scans/github` durch den normalen Anwendungspfad. Keine bereinigten ZIPs, keine weggelassenen Dateien, keine injizierten Muster und keine besonderen Test-Overrides.** Die Anwendung hat ihre konfigurierten Sicherheits- und Ressourcenlimits beibehalten. Laufzeit: 14:45:03 bis 14:56:25 UTC.

## Vorher definierte Probe

PASS bedeutet hier ausschließlich: Originalimport erreicht die statische Analyse, erwartete Sprache wird erkannt, erwartete anwendbare Scanner werden erfolgreich abgeschlossen, kein Scanner meldet FAILED und das Ergebnis wird nicht gekürzt. Regulär ausgeschlossene Compiler-/Buildprüfungen sind weiterhin UNSUPPORTED und machen den Anwendungsstatus PARTIAL. Daher ist ein Probe-PASS kein vollständiger Scan und keine Sicherheitsfreigabe des untersuchten Projekts.

## Tatsächliche Ergebnisse

| Test | Originalrepository | Sprache | Befunde | Probe | Tatsächliches Ergebnis |
|---|---|---|---:|---|---|
| REAL-01 | [spring-projects/spring-petclinic](https://github.com/spring-projects/spring-petclinic) | Java | 1.000 | FAIL | Statische Analyse erreicht, erwartete Scanner erfolgreich; Ergebnislimit erreicht. |
| REAL-02 | [pallets/flask](https://github.com/pallets/flask) | Python | 1.000 | FAIL | Statische Analyse erreicht, erwartete Scanner erfolgreich; Ergebnislimit erreicht. |
| REAL-03 | [BurntSushi/ripgrep](https://github.com/BurntSushi/ripgrep) | Rust | – | FAIL | ZIP_SPECIAL_FILE: Verweis `HomebrewFormula -> pkg/brew` kann vom auf reguläre Dateien begrenzten Linkpfad nicht verarbeitet werden. Keine Codeanalyse. |
| REAL-04 | [Alamofire/Alamofire](https://github.com/Alamofire/Alamofire) | Swift | 230 | PASS | Erwartete Sprache/Scanner erkannt bzw. erfolgreich, keine Kürzung; reguläre Abdeckungsgrenzen bleiben bestehen. |
| REAL-05 | [thedotmack/claude-mem](https://github.com/thedotmack/claude-mem) | JS/TS | – | FAIL | Pflicht-Malwarevorscan blockiert mit MALWARE_DETECTED. Der aktuelle Fehlertext vermischt Fund und Scanlimit; Ursache damit nicht eindeutig nachgewiesen. Keine Codeanalyse, keine ausgelassenen Symlinks als Ersatzlösung. |
| REAL-06 | [fmtlib/fmt](https://github.com/fmtlib/fmt) | C/C++ | 753 | FAIL | Teilergebnis vorhanden; Semgrep meldet SEMGREP_PARTIAL. |
| REAL-07 | [DapperLib/Dapper](https://github.com/DapperLib/Dapper) | C# | 354 | PASS | Erwartete Sprache/Scanner erkannt bzw. erfolgreich, keine Kürzung; reguläre Abdeckungsgrenzen bleiben bestehen. |
| REAL-08 | [go-chi/chi](https://github.com/go-chi/chi) | Go | 34 | PASS | Erwartete Sprache/Scanner erkannt bzw. erfolgreich, keine Kürzung; reguläre Abdeckungsgrenzen bleiben bestehen. |
| REAL-09 | [square/okio](https://github.com/square/okio) | Kotlin, gemischt | 1.000 | FAIL | Ergebnislimit erreicht; zusätzlich Cppcheck SCANNER_FAILED im gemischten Repository. |
| REAL-10 | [slimphp/Slim](https://github.com/slimphp/Slim) | PHP | 0 | PASS | Erwartete Sprache/Scanner erkannt bzw. erfolgreich, keine Kürzung. Null Treffer gelten nur für die konfigurierten Regeln, nicht als Sicherheitsnachweis. |

Damit erreichen acht Originalarchive die Codeanalyse; zwei werden vorher gesperrt. Vier der acht Auswertungen erfüllen die Probe, vier sind durch Scannerfehler oder Kürzung eingeschränkt. Alle gespeicherten Anwendungsberichte haben ausdrücklich eingeschränkte Abdeckung. Die Testserie wurde nicht nachträglich auf erfolgreiche Projekte reduziert.

## Umgesetzte Korrekturen

- **IMP-11 / REQUIREMENTS 5.2:** Interne Datei-Symlinks werden vor dem Schreiben auf existierende reguläre Archivziele, Zyklen, Ausbrüche, Encoding, CRC und Grenzen geprüft und als schreibgeschützte reguläre Kopien analysiert. Keine Betriebssystem-Symlinks entstehen oder werden verfolgt. Kopierte Bytes zählen gegen das Entpacklimit. Verzeichnis-/fehlende/externe Verweise bleiben abgelehnt. Dies löst nicht jeden realen Symlink-Fall; REAL-03 belegt die verbleibende Grenze. Die sieben zuvor entfernten claude-mem-Links sind kein akzeptierter Ersatz für den Originalimport.
- **ANA-06 / DESIGN 5.4:** Normale längere Pfade bleiben lesbar; erkennbare Credential-Muster werden weiterhin maskiert. Alte historische Ergebnisse werden dadurch nicht nachträglich verändert.
- **ANA-05 / ANA-09 / DESIGN 5.3:** Begrenzte Ergebnisspeicherung priorisiert Schweregrad, dann Kategorie und Sprachscanner, statt zuerst gefundene lange Zeilen zu behalten. Kürzung bleibt sichtbar und zählt in dieser Probe als FAIL. Alle Scanner laufen trotz gefülltem Ergebnislimit weiter.
- Das Malware-Zeitlimit beträgt nun dokumentiert drei Minuten, innerhalb einer weiterhin begrenzten Gesamtanalyse. Kein Malware-Bypass wurde eingeführt.

Die neuen Regressionstests prüfen sichere und gefährliche Links, fehlende/zyklische Ziele, Kopiergrößenlimits, späte Security-/Dependency-Befunde und Pfadmaskierung. Insgesamt 98 Backend-Tests bestanden. Browserergebnisse und laufende Gesamtbewertung stehen in `TEST_REPORT.md` und `test-results.json`.

## Noch offene Freigabehindernisse

1. Sichere Behandlung legitimer Verzeichnislinks und verständliche Anzeige nicht unterstützter Linkfälle, ohne fremde Links im Betriebssystem zu verfolgen.
2. Malwarefund und Ressourcen-/Scanlimit müssen zuverlässig unterschieden werden; die Blockade von REAL-05 ist zu diagnostizieren. Es gibt dafür aktuell keinen Nachweis eines tatsächlichen Malwarefunds.
3. Nachvollziehbare Scannerdiagnosen für Semgrep-Teilfehler und Cppcheck-Fehler. Generische Fehlercodes reichen zur Ursachenbehebung nicht aus.
4. Ergebnisvolumen bei realen Projekten: Standardlimit, Regelrauschen und transparente Angaben zu verworfenen Befunden verbessern. Eine erhöhte Grenze allein ersetzt keine Qualitätsbewertung.
5. Vollständige normalisierte Toolversionen und regelbezogene Erklärungen; Dependency-Befunde brauchen Paket-/Versionskontext. Die aktuellen generischen Adaptertexte erfüllen die fachliche Nutzbarkeit noch nicht vollständig.
6. Alle weiteren MUSS-Kriterien einschließlich separater positiver Sprachfixtures, C/C++-Formatprüfung, Prozess-/Netzwerknachweisen, LAN, Backup/Restore, vollständiger DE/EN-Fehlerdarstellung, PDF-Sichtprüfung und Lizenzdokumentation müssen separat abgenommen werden. Siehe [Abnahmestatus](ACCEPTANCE_STATUS.md).

## Reproduzierbarkeit und Nachweise

- Testharness: [Invoke-RealRepositories.ps1](../tests/Invoke-RealRepositories.ps1). Er läuft auf RepoGuard, führt keine Befehle aus Zielprojekten aus und erstellt keine Ersatzarchive.
- [Gesamtergebnis](test-evidence/real-repositories/results.json) sowie REAL-01.json bis REAL-10.json: Zeitstempel, Version, Scan-ID, Original-URL, erwartetes/tatsächliches Ergebnis, Toolstände, Coverage, Befundzahlen und Hash der Originalarchive bei erfolgreichen Analysen. Bei vorzeitig gesperrten Jobs stellt die aktuelle API keinen Archivhash bereit; das ist eine Nachweislücke und wird nicht durch erfundene Daten ersetzt.
- Native Ausgabe: [real-repositories.log](test-evidence/real-repositories/run.log). Die Ergebnisse bleiben unter ihren Scan-IDs in der Anwendungshistorie.
- Der frühere [claude-mem-Bericht](CLAUDE_MEM_ANALYSIS.md) beschreibt andere, eingeschränkte ZIP-Testeingaben und einen älteren Anwendungsstand. Er ist kein Nachweis für den unveränderten GitHub-Import oder eine produktive Freigabe.
