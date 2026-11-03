# Fehlerbehebungen und verbleibende Grenzen

**Letzter Nachtest:** ripgrep besteht mit `repoguard-source-2026.11.03.1` ohne Kürzung, 1.273 Befunde und ohne Scannerfehler. Nachweise: `test-evidence/real-repositories-v3/REAL-03-followup.json`. Zusammen mit der vollständigen Wiederholungsserie ergibt das **acht bestandene Proben und zwei weiterhin eingeschränkte Projekte (fmt und claude-mem)**. Die ursprünglichen Einzelresultate bleiben unverändert erhalten; der Rust-Nachtest ist separat versioniert.

## Behobene Fehler

- Die Historien-Checkboxen dienen jetzt einer allgemeinen Auswahl. Auch fehlgeschlagene und blockierte abgeschlossene Jobs sind auswählbar; die bisherige Grenze von zwei Einträgen gilt nur noch für den Vergleich.
- „Alle sichtbaren auswählen“, Auswahlzähler, Abbrechen und eine gemeinsame Löschbestätigung sind implementiert. Der neue transaktionale API-Endpunkt löscht die gesamte Auswahl oder gar nichts. Laufende oder nicht mehr vorhandene Jobs verhindern eine Teillöschung. Tests löschen ausschließlich eigene Testjobs.
- Sichere interne Datei- und Verzeichnislinks werden innerhalb der Archivgrenzen als schreibgeschützte reguläre Kopien materialisiert. Es werden keine Betriebssystem-Symlinks angelegt oder verfolgt. Externe, fehlende und zyklische Ziele sowie Größen-/Tiefenüberschreitungen bleiben gesperrt.
- Das Standard-Befundlimit beträgt dokumentiert 10.000, die Gesamtfrist zehn Minuten. Höhere Schweregrade verdrängen allgemeine Qualitätshinweise; jede Kürzung bleibt sichtbar.
- Malwarefund, Malware-Scanlimit und Malware-Timeout sind getrennte Fehlercodes. Kein fehlgeschlagener Pflichtvorscan wird umgangen.
- Cppcheck erhält explizit auch Header als Eingaben. Rustfmt verwendet bounded statisch gelesene Paket-/Workspace-Editionen. Normale Textstellen `error:` innerhalb eines Formatierungsdiffs werden nicht mehr als Parserfehler fehlklassifiziert.
- Semgrep-Diagnosen erfassen auch das getaggte JSON-Format für PartialParsing, mit begrenzten Pfad-/Zeilenangaben statt rohem Quelltext. Normalisierte Findings tragen die tatsächliche erfasste Toolversion.

## Grenzen, die nicht als Erfolg umetikettiert werden

Die Originalarchive werden weiterhin vollständig importiert. Der aktuelle Stand garantiert keine vollständige Sicherheitsanalyse jedes beliebigen Repositorys. `fmt` enthält einen von Semgrep als Syntaxfehler gemeldeten Header `test/gtest/gtest/gtest.h`. Bei `claude-mem` melden die integrierten Scanner PartialParsing/Timeout in gebündeltem JavaScript, ein ESLint-Speicherlimit und ein Ruff-Ausgabelimit. Diese Ergebnisse sind Teilergebnisse, keine erfolgreichen vollständigen Scans und kein Nachweis einer Schwachstelle in diesen Projekten.

Diese Grenzen sind grundsätzlich durch Parser-/Toolchain-Upgrades, inkrementelle Verarbeitung und weitere Adapterarbeit verbesserbar. Ein vollständiges Ergebnis mit dem aktuell eingebundenen Regel-/Parserbestand lässt sich jedoch nicht durch Abschalten der Limits, Ignorieren von Fehlern oder Ausführung von Projekt-Builds seriös erzwingen. Insbesondere eine Syntax, die ein Parser nicht versteht, ist nicht mit „keine Befunde“ gleichzusetzen.

## Nachweise

Aktueller eigener Backend-Testlauf: **109 Tests bestanden**. Der Browserlauf nach den UI-/API-Korrekturen: **8 Tests bestanden**, einschließlich einer 27er-Auswahl und atomarer Löschung echter eigener Testjobs. Der vorherige unterbrochene Browserlauf bleibt als `browser-interrupted-attempt.*` erhalten.

Die erste unveränderte Zehner-Serie steht unter `test-evidence/real-repositories`, die unterbrochene Folgeserie unter `real-repositories-v2`, die Wiederholung vom 03.11.2026 unter `real-repositories-v3`. Jeder Lauf enthält seinen tatsächlichen Versionsstand, Zeiten, Scan-IDs, Abdeckungsstatus und Fehler. Frühere Berichte werden nicht rückwirkend zu Erfolgen geändert. Die vollständige Produktabnahme bleibt offen; `releaseReady` bleibt `false`.
