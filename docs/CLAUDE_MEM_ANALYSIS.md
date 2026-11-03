# Praxistest: claude-mem

**Historischer, eingeschränkter ZIP-Test.** Der spätere unveränderte GitHub-Praxistest und die aktuelle Freigabeentscheidung stehen in [REAL_REPOSITORY_ACCEPTANCE.md](REAL_REPOSITORY_ACCEPTANCE.md). Dieser frühere Bericht belegt keine Erfüllung von E2E-02 und keine produktive Freigabe.

Geprüft am 02.11.2026 mit der laufenden RepoGuard-Anwendung. Archivstand: [26ccf4d8a029d97fb5fb88a9f1ddd73729ad1b1a](https://github.com/thedotmack/claude-mem/tree/26ccf4d8a029d97fb5fb88a9f1ddd73729ad1b1a).

## Umfang und Ablauf

Der direkte GitHub-Scan `ff9ad62d-4289-4983-8099-e9f98d31d444` scheiterte am 90-Sekunden-Zeitlimit des Malware-Vorscans. Das heruntergeladene Originalarchiv enthält außerdem sieben Symlinks, die die sichere Extraktion ablehnt. Für die weiteren Prüfungen wurden diese sieben Links ausgelassen; reguläre Dateiinhalte wurden nicht verändert. Die ausgelassenen Pfade und Archivprüfsummen sind im maschinenlesbaren Nachweis aufgeführt.

Das Malware-Zeitlimit wurde für diese Tests vorübergehend auf drei Minuten erhöht. Die Malware-Prüfung blieb verpflichtend. Zielcode, Installer, Tests, Hooks und Projektkonfigurationen wurden nicht ausgeführt. Die ursprünglichen Grenzen wurden anschließend wiederhergestellt. Arbeitsverzeichnisse und Quarantäne waren nach Abschluss leer.

## Ergebnisse

| Durchlauf | Ergebnis | Befunde | Einschränkung |
|---|---|---:|---|
| Alle regulären Dateien, `e5f447a8-ed9f-43a8-aff5-1ceb3a801c73` | PARTIAL | 1.000 | Befundlimit erreicht; 971 Hinweise auf lange Zeilen. Semgrep teilweise fehlgeschlagen, ESLint fehlgeschlagen, Ruff-Ausgabelimit erreicht. Externe Befunde konnten wegen des bereits gefüllten Ergebnislimits nicht übernommen werden. |
| Fokussierte Anwendungsquellen, `17c018dd-315e-41d5-aaa4-0a61d6df7761` | PARTIAL | 1.762 | Keine Kürzung beim vorübergehenden Limit von 5.000 Befunden. Compiler-/Typprüfungen und vollständige Abhängigkeitsauflösung bleiben ausgeschlossen. |

Der fokussierte Durchlauf enthält 790 Dateien: JavaScript-/TypeScript-Anwendungsquellen unter `src/` und `services/` sowie vorhandene Paketmanifeste und Lockfiles. Testdateien, Dokumentation, Binärassets und generierte Bannerdateien sind ausgeschlossen. Die genaue Auswahl steht in [claude-mem-focus-files.txt](test-evidence/claude-mem-focus-files.txt).

ClamAV, Semgrep, ESLint und Trivy wurden im fokussierten Durchlauf erfolgreich abgeschlossen. Weitere Sprachscanner waren für diese Dateiauswahl nicht anwendbar. Semgrep meldete mit dem begrenzten verwalteten Regelbestand keine Treffer; daraus folgt keine Sicherheitsfreigabe.

## Einordnung der Hinweise

- **14 Abhängigkeitshinweise von Trivy:** 1 kritisch, 8 hoch, 4 mittel, 1 niedrig. Der als kritisch eingestufte Treffer ist `CVE-2026-102422` in `plugin/bun.lock`. Es handelt sich um Meldungen der lokalen Datenbank, nicht um bestätigte ausnutzbare Schwachstellen dieser Anwendung. Paketzuordnung, betroffene Version und Erreichbarkeit müssen vor einer Bewertung geprüft werden; der aktuelle normalisierte Befund enthält keine vollständigen Paketdetails.
- **109 ESLint-Hinweise:** 95 lose Vergleiche (`eqeqeq`), 13 leere Blöcke (`no-empty`) und 1 dynamische Funktionskonstruktion (`no-new-func`). Der letzte Treffer verlangt eine Prüfung des Datenursprungs und ist allein kein Ausnutzbarkeitsnachweis.
- **1.639 interne Quellregel-Hinweise:** überwiegend Qualitätshinweise; darunter 174 heuristische SQL-Konstruktionshinweise, 6 mögliche Eingabepfade und 4 mögliche Geheimnisse. Insbesondere SQL-Textmuster können sichere oder statische SQL-Ausdrücke treffen. Geheimniswerte werden nicht wiedergegeben.

Der Praxistest zeigt auch Grenzen der Ergebnisdarstellung: Viele längere Dateipfade werden derzeit zu stark maskiert; das erschwert die Zuordnung. Beim Gesamtdurchlauf verdrängen zahlreiche allgemeine Qualitätshinweise außerdem nachfolgende Scannerbefunde. Beide Punkte sollten vor einer belastbaren vollständigen Repositorybewertung verbessert werden.

## Nachweise

Die beiden abgeschlossenen ZIP-Analysen bleiben in der Oberfläche unter **Analysen** verfügbar. Der fokussierte Bericht ist über die ID `17c018dd-315e-41d5-aaa4-0a61d6df7761` auffindbar. [Maschinenlesbare Ergebnisse](test-evidence/claude-mem-analysis.json) enthalten Scannerstatus, Versionen, Befundzahlen, Advisory-IDs, Prüfsummen und Umfang. Die heruntergeladenen Arbeitsarchive wurden nach der Prüfung entfernt.
