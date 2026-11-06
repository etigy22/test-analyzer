# RepoGuard

Lokale Webanwendung für statische Repository-Prüfungen. **Fremder Code wird niemals gebaut, installiert, getestet oder ausgeführt.**

## Start

Docker Desktop mit Linux-Containern starten. In PowerShell:

```powershell
cd C:\Projekte\test-analyzer
if (!(Test-Path .env)) { Copy-Item .env.example .env }
# Bei einer neu kopierten .env das Datenbankpasswort ändern.
docker compose up -d --build
```

Oberfläche: **http://localhost:8088**. Eine lokale `.env` wurde bei der Entwicklung mit zufälligem Datenbankpasswort angelegt; sie wird nicht versioniert. Der erste Build benötigt Internet und mehrere Minuten. Etwa 8 GB für Docker bereitstellen.

1. **Einstellungen → Sicherheitsdaten aktualisieren**: ClamAV-Signaturen und die lokalen Trivy-Datenbanken laden. Das erste vollständige Datenupdate kann mehrere Gigabyte Speicher und mehrere Minuten benötigen. Ohne verfügbare Signaturen ist kein Scan möglich. Bereits geladene Signaturen bleiben über Neustarts erhalten.
2. **Neue Analyse**: öffentliche GitHub-URL eingeben oder ZIP auswählen und starten.
3. Der Bericht zeigt reale Phasen, Findings, maskierte Ausschnitte und die Prüfabdeckung. Fehlende Prüfungen ergeben einen eingeschränkten Bericht.
4. Unter **Analysen** Berichte öffnen, zwei Ergebnisse vergleichen oder Analysen löschen. Im Bericht kann ein lokales PDF heruntergeladen werden.

```powershell
docker compose ps
docker compose logs --tail 100 backend worker updater
docker compose down
```

`down` erhält Datenbank und Signaturen. `down -v` löscht diese Daten dauerhaft.

## Was die Analyse leistet

**Nachweis vom 03.11.2026:** Fünf unveränderte GitHub-Archive erreichen jetzt tatsächlich `COMPLETED`, ohne Scannerfehler, übersprungene Quelldateien oder Ergebnis-Kürzung: [Scan-IDs und vollständige Prüfdaten](docs/FIVE_COMPLETED_SCANS.md).

`COMPLETED` bedeutet: Alle anwendbaren Prüfungen des ausdrücklich angebotenen statischen Umfangs sind abgeschlossen. Verbotene Builds/Compileranalysen und nicht angebotene allgemeine Binäranalysen bleiben als „Außerhalb Prüfumfang“ sichtbar. Echte Scannerfehler, fehlende anwendbare Prüfungen, übersprungene Quelldateien und erreichte Ergebnislimits führen weiterhin zu `PARTIAL`. Historische Berichte werden nicht nachträglich umklassifiziert.

Der enthaltene Source-only-Regelbestand prüft Java, Python, Rust, Swift, JS/TS, C/C++, C#, Go, Kotlin und PHP auf konkrete Sicherheits- und Qualitätsmuster. Hinzu kommen verwaltete Semgrep-Regeln, sprachspezifische Scanner und Trivy mit lokal aktualisierten CVE- und Java-Datenbanken. Der kleine kuratierte Offline-CVE-Bestand bleibt als ergänzender Fallback erhalten.

Das ist **keine umfassende Sicherheitsfreigabe**: Enthalten sind Semgrep, Bandit, Ruff, Cppcheck, Checkstyle, ESLint mit TypeScript-Parser, gofmt, rustfmt, SwiftLint, detekt, PHP_CodeSniffer und Trivy. Compiler-/Build-abhängige Analysen bleiben ausgeschlossen. Unterstützte Lockfiles und bestehende Archive werden offline geprüft; nicht auflösbare oder nicht unterstützte Manifeste werden nicht als vollständig abgedeckt ausgegeben. Keine Kombination statischer Werkzeuge erfasst sämtliche Schwachstellen. Die Oberfläche weist diese Grenzen aus. Beide Projektphasen und sämtliche Abnahmekriterien gelten deshalb noch nicht als vollständig abgenommen. Siehe `TEST_REPORT.md`.

**Praxistest mit zehn unveränderten GitHub-Projekten:** Nach Fehlerbehebungen und separatem Rust-Nachtest erfüllen acht Proben die definierten Import-/Scanner-Erwartungen. Bei fmt und claude-mem verbleiben dokumentierte Parser- und Ressourcengrenzen. Keine produktive Freigabe. [Aktueller Stand und offene Probleme](docs/FIX_STATUS_2026-11-03.md), [verbindlicher Abnahmestatus](docs/ACCEPTANCE_STATUS.md).

## Isolation und Daten

- API, PostgreSQL, Scanner und Updatedienst sind getrennte Container. Nur die Oberfläche veröffentlicht Port 8088. Der Scanner hängt ausschließlich an einem internen Netzwerk, hat keinen Internetzugang und verarbeitet keine ausführbaren Projektkonfigurationen.
- Archive durchlaufen Quarantäne, obligatorisches lokales ClamAV und sichere ZIP-Extraktion. Pfade, Sonderdateien, Kollisionen, CRC, Dateianzahl, Tiefe, Größen und Kompressionsverhältnis werden geprüft.
- Interne Datei- und Verzeichnis-Symlinks werden vor dem Schreiben vollständig geprüft und als schreibgeschützte reguläre Kopien ihres Archivziels analysiert. Es entstehen keine Betriebssystem-Symlinks. Fehlende, externe und zyklische Verweise werden abgelehnt; kopierte Bytes zählen gegen das Entpacklimit. Die Anzahl verarbeiteter Links steht in der Prüfabdeckung.
- Das Befundlimit begrenzt die gespeicherten Ergebnisse. Höhere Schweregrade verdrängen niedrigere; bei gleichem Schweregrad haben Security-/Dependency-/Secret-Befunde und Sprachscanner Vorrang vor allgemeinen Qualitätshinweisen. Kürzung bleibt ausdrücklich sichtbar.
- Vollständige Quellen und Archive werden nach Abschluss oder Fehler entfernt. PostgreSQL speichert Metadaten, Ergebnisse und begrenzte maskierte Ausschnitte. Temporäre Daten liegen in `noexec`-tmpfs; verwaiste Dateien werden beim Dienststart bereinigt.
- Signaturupdates laufen ausschließlich auf Anforderung und sperren neue Scans bis zum Abschluss. Ein neues Snapshot wird vor atomarer Aktivierung geprüft. Ein fehlgeschlagenes Update ersetzt den aktiven Datenstand nicht. Quellregeln und kuratierte Advisories werden mit Anwendungsversionen aktualisiert.
- Historie wird beim API-Neustart nach 90 Tagen bereinigt. Datenbank-Backups haben eigene Aufbewahrungsfristen.

## Konfiguration / LAN

Für LAN-Zugriff `REPOGUARD_PUBLIC_BASE_URL` und `REPOGUARD_FRONTEND_ORIGIN` in `.env` auf die tatsächliche Serveradresse setzen, z. B. `http://192.168.1.20:8088`, anschließend `docker compose up -d`.

**Keine Anmeldung:** Jeder mit Netzwerkzugriff kann Analysen und Berichte sehen. Zugriff per Firewall auf vertrauenswürdige Geräte begrenzen; nicht öffentlich ins Internet stellen. Origin-, Host- und Mutation-Header-Prüfungen schützen Browserzugriffe.

Standardlimits: 200 MB Archiv, 500 MB entpackt, 10 MB pro Datei, 25.000 Einträge, Tiefe 16, Verhältnis 100:1, Download 2 Minuten, Scan 10 Minuten, eine gleichzeitige Analyse, 10.000 Findings. Gelesene Texte sind auf 10 MB je Datei begrenzt; Ausschnitte auf fünf Zeilen zu je 160 Zeichen. Übersprungene Inhalte werden in der Abdeckung ausgewiesen. Compose enthält CPU-, RAM-, Prozess- und tmpfs-Limits.

Der verpflichtende Malware-Vorscan ist auf drei Minuten begrenzt (`REPOGUARD_MALWARE_TIMEOUT`). Die gesamte Scanfrist (`REPOGUARD_SCANNER_TIMEOUT`) und das Ergebnislimit (`REPOGUARD_MAX_FINDINGS`) stehen ebenfalls in `.env.example` und werden beim Containerstart übernommen. Ein Timeout blockiert die Codeanalyse weiterhin.

## Entwicklung und Tests

Java 21, Maven und Node.js erforderlich:

```powershell
mvn -f backend/pom.xml test
cd frontend
npm ci
npm run build
npx playwright install chromium
npm run test:e2e
```

Browserprüfungen benötigen den laufenden Stack mit Signaturen und legen ausschließlich passive Testprojekte an. Sie verwenden auch die harmlose EICAR-Testsignatur. Keine Testfixture wird ausgeführt. Testergebnisse befinden sich unter `backend/target/surefire-reports` und `frontend/test-results`; `tests/Write-TestReport.ps1` erzeugt die Zusammenfassung.

## Historie gesammelt löschen

Unter **Analysen** können auch fehlgeschlagene und blockierte abgeschlossene Jobs markiert werden. **Alle sichtbaren auswählen** berücksichtigt den aktuellen Suchfilter. **Auswahl löschen** löscht nach einer gemeinsamen Bestätigung die gesamte Auswahl samt Ergebnissen und Snippets. Laufende Jobs sind geschützt; enthält eine API-Auswahl einen laufenden oder fehlenden Job, wird nichts gelöscht. Nur der Vergleich bleibt auf genau zwei Analysen mit Ergebnissen begrenzt.
