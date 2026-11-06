# Fünf vollständig ausgeführte statische Analysen

Nachweis erzeugt: 2026-11-03T10:40:01.9011515Z

Fünf neue, unveränderte GitHub-Archive wurden über den normalen Import verarbeitet. Alle gespeicherten Jobs haben den echten Status COMPLETED, keine Kürzung und keine fehlgeschlagene oder übersprungene anwendbare Prüfung. Die Archive wurden weder bereinigt noch durch Fixtures ersetzt.

| Repository | Scan-ID | Befunde | Status | Anwendbare Lücken |
|---|---|---:|---|---:|
| https://github.com/spring-projects/spring-petclinic | 0da81864-17be-49b1-9383-6db22bede28a | 1069 | COMPLETED | 0 |
| https://github.com/pallets/flask | 23bed2d0-614d-4ef1-8b0d-225288c7f50c | 6183 | COMPLETED | 0 |
| https://github.com/DapperLib/Dapper | 144792ae-031b-4aed-b07a-98de82d7656c | 354 | COMPLETED | 0 |
| https://github.com/go-chi/chi | e65080a8-a400-4995-ab66-9250d35e189a | 34 | COMPLETED | 0 |
| https://github.com/slimphp/Slim | c33c3526-4d80-44f8-be4f-0414617c8d0b | 0 | COMPLETED | 0 |

## Was korrigiert wurde

Der alte Gesamtstatus wurde bei jedem UNSUPPORTED-Eintrag auf PARTIAL gesetzt. Dazu gehörten pauschal auch verbotene Compiler-/Buildprüfungen und nicht angebotene Zusatzanalysen. Dadurch konnte selbst ein vollständig ausgeführter statischer Auftrag nie erfolgreich enden.

Coverage enthält nun ein explizites Feld applicable. Nur ausdrücklich nicht zum statischen Auftrag gehörende UNSUPPORTED-Einträge können den Gesamtstatus unberührt lassen. FAILED und SKIPPED bleiben immer Einschränkungen, auch bei einer fehlerhaften Bereichsmarkierung. Fehlende Scanner, Quelldatei-/Zeit-/Ergebnislimits, Parserfehler und fehlende Daten werden nicht ausgeblendet. Alte gespeicherte Berichte werden nicht umklassifiziert; fehlende Bereichsinformation wird konservativ als anwendbar behandelt.

Binäre Nicht-Quelldateien erhalten keine vorgetäuschte UTF-8-Quellprüfung. Sie bleiben im Originalarchiv für den Pflicht-Malwarevorscan und unterstützte Trivy-Prüfungen enthalten; der fehlende allgemeine Binäranalysemodus wird sichtbar außerhalb des Quellprüfumfangs ausgewiesen. Nicht lesbare oder zu große unterstützte Quelldateien führen weiterhin zu PARTIAL.

## Was COMPLETED ausdrücklich nicht bedeutet

Alle vereinbarten anwendbaren statischen Prüfungen sind ausgeführt. Das ist kein Nachweis von Sicherheit oder Malwarefreiheit und keine universelle Prüfung jeder Datei mit jedem denkbaren Verfahren. Compiler-, Build- und dynamische Analysen bleiben laut REQUIREMENTS verboten. Die vollständigen Ausschlüsse sind im UI, im PDF und in den gespeicherten Coverage-Daten sichtbar. Die vollständige Produktabnahme bleibt separat offen.

## Reproduktion und Rohdaten

`tests/Invoke-CompleteScans.ps1` prüft den Gesamtstatus und jede einzelne anwendbare Coverage-Zeile, die Ergebnisbegrenzung sowie das tatsächliche Jobende. Vollständige Metadaten mit Zeiten, Version, Archiv-SHA-256 und Coverage: [results.json](test-evidence/completed-static-scans/results.json).
