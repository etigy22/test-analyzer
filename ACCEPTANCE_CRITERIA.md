# RepoGuard – ACCEPTANCE_CRITERIA.md
**Version:** 1.0 · **Status:** Verbindliche messbare Abnahmespezifikation · **Stand:** 21.10.2026

## 1. Allgemeine Abnahmeregeln
Alle hier aufgeführten Kriterien sind **MUSS**, soweit nicht ausdrücklich als optional gekennzeichnet. Für jedes Kriterium gilt:
- **PASS:** Erwartete Vorgabe in real ausgeführtem Test nachgewiesen.
- **FAIL:** Tatsächliches Ergebnis widerspricht Vorgabe.
- **BLOCKED:** Wegen dokumentierter externer Abhängigkeit nicht testbar; **kein** PASS.
- **NOT TESTED:** Noch nicht ausgeführt; **kein** PASS.

Ein Build, ein Mockup, eine positive Demo mit nur einer Programmiersprache oder behauptete Testläufe sind **keine Abnahme**. Keine bekannten ungeklärten kritischen/hohen Sicherheitslücken. Scanner-/Sprach-/Datenbankstände, Testumgebung, Eingaben und Nachweise festhalten. Alle E2E-Tests sind verbindlich.

## 2. Produkt, Installation und Betrieb
| ID | Kriterium | Messbare Erfolgsvorgabe |
|---|---|---|
| SYS-01 | Name | Oberfläche, Seitentitel, Compose-Projekt und Berichte heißen RepoGuard |
| SYS-02 | Stack | Java 21/Spring Boot, React/TypeScript und PostgreSQL sind tatsächlich integriert |
| SYS-03 | Compose | Dokumentierter `docker compose up -d --build` bzw. Release-Start führt zu funktionsfähigem Stack |
| SYS-04 | Health | Frontend, API, DB und nötige Scan-/Malware-Dienste melden realen bereiten Zustand |
| SYS-05 | Persistenz | Scanmetadaten/Findings/Snippets über Neustart und reguläre Neuanlage der Container erhalten |
| SYS-06 | Konfiguration | Port, Ressourcen-/Uploadlimits, Speicherfristen und Scanparallelität über dokumentierte Konfiguration änderbar; Änderungen wirksam |
| SYS-07 | Keine HostNox-Reste | Keine irreführenden HostNox-Brandings oder Pi-4-/ARM64-Pflichtvorgaben |
| SYS-08 | Betrieb ohne Cloud | ZIP-Scan mit lokal vorbereiteten Tools/Daten ohne externe Netzverbindung möglich |
| SYS-09 | Containerprivilegien | Keine `privileged`-Container, kein Docker-Socket-Mount, kein Host-Network; interne Dienste nicht unaufgefordert veröffentlicht |
| SYS-10 | Reproduzierbarkeit | Versionsfixierte Abhängigkeiten/Images und dokumentierte lokale Build- und Startanleitung vorhanden |

## 3. Eingabe, GitHub und sichere Archive
| ID | Kriterium | Messbare Erfolgsvorgabe |
|---|---|---|
| IMP-01 | GitHub URL | Gültiger öffentlicher `https://github.com/owner/repo`-Link wird ohne GitHub REST API/App/Token verarbeitet |
| IMP-02 | Falsche Hosts | `github.com.evil.example`, `github.com@evil.example` und IP-Literale werden vor Netzwerkdownload abgelehnt |
| IMP-03 | URL-Sonderfälle | `http`, `file`, `ssh`, andere Ports, Query/Fragment, Backslash, Userinfo und zusätzliche Pfade abgelehnt |
| IMP-04 | Neuerzeugte Downloadadresse | Verbindung erfolgt nur an serverseitig aus validierten Feldern erzeugte URL; kein User-URL-Passthrough |
| IMP-05 | SSRF | Interne/Loopback/Link-local/Cloud-Metadaten-IP und simuliertes DNS-Rebinding blockiert |
| IMP-06 | Redirect/TLS | Unsichere Redirect-Ziele, unzulässige Protokolle und ungültiges TLS abgelehnt; legitimer GitHub-Archivredirect kontrolliert erlaubt |
| IMP-07 | Archiv statt Checkout | Kein `git clone`, keine Submodule, fremde Hooks oder Projektsetup-Befehle |
| IMP-08 | ZIP-Upload | Gültige ZIP-Projekte werden über Browser hochgeladen und nach denselben Regeln wie GitHub-Archive verarbeitet |
| IMP-09 | ZIP-Slip | `../`, absolute, Windows-/Unicode-kritische Pfade führen nicht zu Zugriff außerhalb des Arbeitsbereichs |
| IMP-10 | ZIP-Bomb | Dateianzahl, Einzel-/Entpackgröße, Tiefe, Kompressionsverhältnis und Datenträgerlimit werden während Extraktion durchgesetzt |
| IMP-11 | Dateitypen | Schadhafte Symlinks/Special Files, kaputte Archive und kollidierende Einträge abgelehnt |
| IMP-12 | Downloadlimits | Übergröße, Timeout, HTTP-Fehler, falsches Format führen zu kontrolliertem Abbruch mit aussagekräftigem UI-Status |
| IMP-13 | Quarantäne | Ungeprüfte Archive nicht in öffentliches Webroot und nicht ohne Prüfung an Scanner übergeben |
| IMP-14 | Malware | Lokaler Signaturvorscan obligatorisch **vor** Codeanalyse; Testsignatur wird erkannt, Import blockiert |
| IMP-15 | Fail-closed | Scanner/Signaturdienst des Malware-Vorscans fällt aus → kein Codeanalyse-Start und kein falsches „sicher“-Resultat |
| IMP-16 | Kein Quellcode-Cloudtransfer | Netzwerkbeobachtung zeigt keine Repository-Übertragung an externe Scan-/KI-Dienste |

## 4. Absolutes Verbot der Projektausführung
| ID | Kriterium | Messbare Erfolgsvorgabe |
|---|---|---|
| EXE-01 | Keine Projektausführung | Test-Fixtures mit Projekt-Startskripten/Executable-Markern: kein einziges Skript/Binary gestartet |
| EXE-02 | Kein Build/Test | Projektspezifische `npm install`, `pip install`, `cargo build`, `mvn package`, `gradle build`, `swift build`, Makefiles und Tests werden nie aufgerufen |
| EXE-03 | Keine Hooks | Git-/Build-/Postinstall-Hooks und ausführbare Projektkonfiguration bleiben inaktiv |
| EXE-04 | Vertrauenswürdige Regeln | Vom Repository angebotene eigene Plugins/Rule-Dateien/Config-Skripte werden nicht geladen/ausgeführt |
| EXE-05 | Scanner-Isolation | Scanner arbeiten nur mit verwalteten Werkzeugen/Regeln, ohne Schreibzugriff auf Input und ohne unnötigen Netzverkehr |
| EXE-06 | Beobachtbarer Nachweis | Prozess-, Audit- und/oder Sandbox-Test belegt, dass keine Repository-Ausführungsaktion ausgelöst wurde |
| EXE-07 | Kein dynamischer Modus | Weder UI noch API erlauben Laufzeit-/Build-/Test-Scan fremder Projekte |

## 5. Sprachunterstützung und Scannerabdeckung
**Abnahmeregel:** Jede Sprachgruppe ist Pflicht. Für jede Gruppe ein eigenes, geeignetes Testrepository mit mindestens einem nachweisbar erkennbaren Sicherheitsmuster und einem Qualitäts-/Stylemuster verwenden. Die eingesetzten Tools müssen wirklich laufen, nicht nur in der Dokumentation stehen. „Alle verfügbaren Prüfungen“ bedeutet: sämtliche im freigegebenen, lokal installierten, quellcodebasierten Regel-/Adapterbestand auf das Testprojekt anwendbaren und sicher ausführbaren Prüfungen. Nicht einsetzbare Prüfungen werden mit nachvollziehbarem Grund markiert; es wird **keine** universelle Abdeckung sämtlicher Schwachstellenklassen behauptet.

| ID | Sprachgruppe | Erfolgsvorgabe |
|---|---|---|
| LANG-01 | Java | Sprache erkannt; source-only Security- und Qualitätscheck ausgeführt; erwartete Fixtures erkannt |
| LANG-02 | Python | Sprache erkannt; source-only Security- und Qualitätscheck ausgeführt; erwartete Fixtures erkannt |
| LANG-03 | Rust | Sprache erkannt; source-only Security- und Qualitätscheck ausgeführt; kein Build/Clippy mit Buildbedarf |
| LANG-04 | Swift | Sprache erkannt; source-only Security- und Qualitätscheck ausgeführt; keine repo-geladene SwiftLint-Config |
| LANG-05 | JavaScript/TypeScript | JS und TS erkannt; Security- und Qualitätstools mit verwalteten Regeln ausgeführt |
| LANG-06 | C/C++ | C und C++ erkannt; source-only Security-/Qualitätschecks einschließlich Cppcheck/Formatcheck, ohne Build |
| LANG-07 | C# | Sprache erkannt; source-only Security-/Qualitätschecks, kein Projekt-Build erforderlich |
| LANG-08 | Go | Sprache erkannt; source-only Security-/Qualitätschecks, kein Projekt-Build |
| LANG-09 | Kotlin | Sprache erkannt; source-only Security-/Qualitätschecks, keine projektspezifische Konfiguration ausgeführt |
| LANG-10 | PHP | Sprache erkannt; source-only Security-/Qualitätschecks mit festem Regelbestand |
| LANG-11 | Mixed repo | Repository mit mindestens drei unterstützten Sprachen: alle erkannt; jeweilige passende Adapter gestartet |
| LANG-12 | Unbekannte Sprache | Generische Prüfungen möglich, aber keine falsche Darstellung vollständiger Sprachunterstützung |
| LANG-13 | Coverage | Pro Sprache/Scanner/Prüfung `COMPLETED`, `UNSUPPORTED`, `FAILED` oder `SKIPPED` einschließlich Grund sichtbar |
| LANG-14 | Fehlertransparenz | Absichtlich fehlender/defekter Scanner wird nicht als „0 Findings“ oder erfolgreicher Scan ausgewiesen |
| LANG-15 | Verfügbare Checks | Inventar aller freigegebenen anwendbaren Source-only-Regeln/Tools stimmt mit tatsächlich gestarteten Checks oder begründeten Ausnahmen überein |

## 6. Findings, Qualität, Dependencies und API
| ID | Kriterium | Messbare Erfolgsvorgabe |
|---|---|---|
| ANA-01 | Kategorien | Security, Quality, Dependency, Secret und Configuration werden einheitlich normalisiert |
| ANA-02 | Sicherheitsmuster | Vorbereitete statisch erkennbare Beispiele für SQLi, XSS, Secrets, Traversal, unsichere APIs und Fehlkonfigurationen erzeugen bei unterstützter Sprache erwartete Ergebnisse |
| ANA-03 | Qualitätsmuster | Vorbereitete Komplexitäts-/Stil-/Format-/Wartbarkeitsmuster werden durch geeignete Quellenregeln erkannt |
| ANA-04 | Dependencies | Unterstützte Lock-/Manifestdatei mit bekannter CVE und lokalem DB-Bestand ergibt ein referenziertes Finding |
| ANA-05 | Keine Überversprechen | Unsupported-Sprach-/Regel-/Dependency-Prüfungen erscheinen ausdrücklich als eingeschränkt |
| ANA-06 | Finding-Felder | Kategorie, Severity, Regel, Tool, Sprache, Dateipfad, Zeile falls vorhanden, Erklärung und regelbasierter Hinweis vorhanden |
| ANA-07 | Duplikate | Identischer Befund derselben Quelle wird nicht unkontrolliert doppelt gezählt; Toolherkunft bleibt erhalten |
| ANA-08 | API-Verlässlichkeit | Jobs/status/Ergebnisse/History/Exports liefern dokumentierte validierte Daten und sinnvolle HTTP-Fehler |
| ANA-09 | Scanstatus | Scan kann Erfolg, Teilergebnis und Fehler unterscheiden; Fehler werden nicht still verborgen |

## 7. UI, Design und Internationalisierung
| ID | Kriterium | Messbare Erfolgsvorgabe |
|---|---|---|
| UI-01 | Branding | RepoGuard und passendes eigenes Logo/Favicon an relevanten Stellen |
| UI-02 | Design | Modernes schlichtes Entwickler-Dashboard nach `DESIGN.md`, nicht HostNox-Matrix-Stil |
| UI-03 | Themes | Light und Dark manuell wechselbar; alle Kernansichten und Dialoge korrekt |
| UI-04 | Sprachen | Deutsch und Englisch vollständig für Kernworkflow, Einstellungen, Status und Berichtsbeschriftung |
| UI-05 | Responsivität | Bei 375, 768 und 1440 px keine abgeschnittenen Kernaktionen oder globalen Horizontalüberläufe |
| UI-06 | A11y | Kernfunktionen per Tastatur; sichtbarer Fokus, Statuslabels, WCAG-2.2-AA-konforme Kontraste |
| UI-07 | Offline UI | Keine externen Fonts/CDNs/Tracking-/UI-Abhängigkeiten beim Laden |
| UI-08 | Workflow | GitHub-URL-Eingabe und ZIP-Dropzone starten echte API-Jobs ohne Terminal |
| UI-09 | Progress | Reale, nachvollziehbare Import-/Malware-/Scan-/Bereinigungsstatus; kein fingierter Prozentfortschritt |
| UI-10 | Findings | Filter nach Severity, Sprache, Tool, Kategorie und Datei ändern Ergebnismenge korrekt |
| UI-11 | Fehler | Ungültiger Link, Malwarefund, Scanner-Teilversagen und Grenzverletzung jeweils unterscheidbar |
| UI-12 | Sicherheitshinweis | UI erklärt statische Grenzen und LAN-Betrieb ohne Login klar |
| UI-13 | Aktive Inhalte | HTML/JS im Fremdcode wird in Vorschau nicht ausgeführt oder als aktiver DOM-Code gerendert |

## 8. Historie, Aufbewahrung, PDF und Updates
| ID | Kriterium | Messbare Erfolgsvorgabe |
|---|---|---|
| DATA-01 | Löschung | Original-ZIP, entpackte Dateien und GitHub-Downloadreste nach erfolgreichem Job nicht mehr in Arbeitsverzeichnissen |
| DATA-02 | Fehlerbereinigung | Bei Abbruch, Timeouts und Neustart verwaiste vollständige Repository-Dateien nach definierter Bereinigung entfernt |
| DATA-03 | Persistenz | Findings, Metadaten, Toolstände, Coverage, Snippets bleiben in PostgreSQL nach Neustart erhalten |
| DATA-04 | Keine Vollquellen | Historische API/DB/UI enthält keine vollständigen importierten Repository-Dateien |
| DATA-05 | Snippet-Limit | Nur fest begrenzte Fundstellenbereiche und Zeilenangaben gespeichert; Limits konfigurierbar und wirksam |
| DATA-06 | Secrets | Test-Secret erscheint weder unmaskiert in UI, DB-Snippets, Logs, API noch PDF |
| DATA-07 | Historie | Mehrere Analysen abrufbar, sortierbar und anhand gespeicherter Metadaten/Fingerprints vergleichbar |
| DATA-08 | Löschen | Analyse und ihre Snippets über UI löschbar; API/DB-Einträge danach nicht mehr abrufbar |
| DATA-09 | PDF | PDF aus echtem Job lokal erstellt; Findings, Coverage, Grenzen und maskierte Snippets vorhanden |
| DATA-10 | Datenupdate | Nutzer kann Update per Button anstoßen; Erfolg/Fehler, letzte Aktualisierung und Datenversion sichtbar |
| DATA-11 | Scan-Netztrennung | Während des Scans keine automatischen Signatur-/CVE-/Rule-Downloads; Netzwerkprotokoll belegt dies |
| DATA-12 | Offline/Altstand | Mit vorhandenem Datenbestand offline ZIP-Scan erfolgreich; veraltete Datenbasis wird angezeigt |
| DATA-13 | Updatefehler | Beschädigtes/fehlgeschlagenes Update übernimmt keinen defekten Datenbestand; letzter brauchbarer Stand bleibt soweit sicher erhalten |

## 9. Sicherheits- und Ressourcenprüfungen
| ID | Kriterium | Messbare Erfolgsvorgabe |
|---|---|---|
| SEC-01 | LAN ohne Login | Aus zweitem LAN-Gerät erreichbar, keine Anmeldung; dies wird als Risiko erklärt |
| SEC-02 | Keine Internetöffnung | Compose richtet weder Portweiterleitung noch öffentliche Domain ein; Anleitung warnt ausdrücklich |
| SEC-03 | API-Schutz | Bösartige Origin-/Host-/DNS-Rebinding-ähnliche Browserzugriffe und fremdseitig initiierte Zustandsänderungen abgewehrt |
| SEC-04 | Eingaben/XSS | Benutzereingaben, Scanneroutput und Snippets können kein Skript im Dashboard ausführen |
| SEC-05 | Limits | Je ein Test überschreitet Upload-, Expand-, Datei-, Zeit-, Parallelitäts- und Scanner-Ressourcenlimit; kontrollierter Fehler |
| SEC-06 | Isolation | Scanner kann Container-/Host-Privilegien nicht erweitern, fremden Code nicht ausführen und Daten nicht extern senden |
| SEC-07 | DB | PostgreSQL nicht vom LAN direkt erreichbar; Zugangsdaten nicht im Repository/Frontend |
| SEC-08 | Logs | Keine Secrets, vollständigen fremden Repository-Dateien oder privaten Rohdaten im Klartext-Logging |
| SEC-09 | Systemrisiko | Keine bekannten ungeklärten kritischen oder hohen Schwachstellen in ausgelieferten Komponenten |
| SEC-10 | Fehlerbehandlung | Nach Sicherheitsfehler weder unsichere Teilfreigabe noch Voll-Erfolgsmeldung |

## 10. Dokumentation und Lieferung
| ID | Kriterium | Messbare Erfolgsvorgabe |
|---|---|---|
| DOC-01 | Quellcode | Vollständiges Repository mit Frontend, Backend, Scanner-Adaptern, Regeln, Tests, Migrationen |
| DOC-02 | Docker | `compose.yaml`, Dockerfile(s), `.env.example`, Healthchecks und persistente Volume-Konfiguration |
| DOC-03 | Anleitung | Installation, Start, erster Scan, GitHub-Import, ZIP, PDF, Historie, Updates und Fehlerbehebung nachvollziehbar |
| DOC-04 | Sicherheitsdoku | SSRF/Redirect/ZIP/Malware/Isolation, Netzrisiko ohne Login und statische Grenzen dokumentiert |
| DOC-05 | Datenhaltung | Historie, maskierte Snippets, Löschung, Backup/Restore und Datenfristen beschrieben |
| DOC-06 | Scanner-Matrix | Je Sprache echte Tools/Versionen, verfügbare Checks und Einschränkungen dokumentiert |
| DOC-07 | Testdoku | Automatisierte Tests, reale Läufe, Abnahmeprotokoll und belegte Fehlschläge |
| DOC-08 | Lizenzen | Lizenzentscheidung und Lizenzen der Scanner/Rules/Dependencies nachvollziehbar |

## 11. Agentenbasierte Entwicklung und Git-Nachweis
| ID | Kriterium | Messbare Erfolgsvorgabe |
|---|---|---|
| AGENT-01 | IDE-Agent | Mindestens ein realer, erfolgreicher Feature-Workflow mit einem Entwicklungsagenten **innerhalb von VS Code oder IntelliJ IDEA** dokumentiert |
| AGENT-02 | Git-Anbindung | Projekt in der IDE als lokaler Git-Checkout geöffnet; Agent ändert Dateien des Checkout; `git diff` zeigt die Änderung |
| AGENT-03 | Prompt-gesteuert | Ein dokumentierter natürlicher Prompt mit Verweis auf die RepoGuard-Referenzdateien führt zu einer überprüfbaren Codeänderung |
| AGENT-04 | Agentenimplementierung | Implementierung, Refactoring, Tests und technische Dokumentationsänderungen erfolgen durch IDE-Agenten, nicht durch manuelle Codeentwicklung; Arbeitsverfahren dokumentiert |
| AGENT-05 | Nachvollziehbarkeit | Betroffene Dateien, Git-Commit(s), Testresultate und Freigabeentscheidung einer Beispieländerung lassen sich zuordnen |
| AGENT-06 | Dokumentkontext | Die vier verbindlichen Projektdokumente liegen im Repository und sind für den Agenten zugänglich; ein Testauftrag berücksichtigt mindestens eine daraus abgeleitete MUSS-Vorgabe |
| AGENT-07 | Sicherheitsgrenze | Vom Agenten ausgelöste Build-/Testschritte betreffen ausschließlich RepoGuard und **niemals** untersuchte Fremdrepositories |
| AGENT-08 | Übergabe | `docs/AGENT_WORKFLOW.md` erklärt IDE-Einrichtung, Git-Checkout, Prompt, Review, Test, Commit und optionalen Push ohne Anbieterzwang |

## 12. Verbindliche Ende-zu-Ende-Tests
### E2E-01 – Installation
**Gegeben:** unterstützter Docker-Host mit verfügbaren Images/Daten und freiem Port. **Wenn:** RepoGuard per dokumentiertem Compose-Start gestartet wird. **Dann:** Frontend aus zweitem LAN-Gerät erreichbar, API/DB/Scanner bereit, keine Anmeldung nötig und PostgreSQL nicht von außen direkt erreichbar.

### E2E-02 – Gültiges GitHub-Repository
**Wenn:** öffentliche gültige GitHub-URL eingegeben wird. **Dann:** sichere Validierung und offizieller Archivdownload, lokaler Malware-Vorscan, statische Scanner, vollständige Findings-/Coverage-Anzeige; keinerlei Codeausführung; danach komplette Download-/Repository-Dateien gelöscht.

### E2E-03 – ZIP mit mehreren Sprachen
**Wenn:** ZIP mit mindestens drei der unterstützten Sprachen hochgeladen wird. **Dann:** alle drei Sprachen erkannt, geeignete sichere Adapter laufen, Security- und Quality-Findings erscheinen, fehlende Prüfungen sind markiert und Restdateien gelöscht.

### E2E-04 – Einzeltests aller zehn Sprachen
Für **jede** Sprachgruppe eigenständige Security- und Quality-Testfixture durchlaufen. Erwartet: Sprachdetektion und echte, nachweisbare positive statische Scannerbefunde; keine Builds/Skripte; alle nicht anwendbaren Checks explizit dokumentiert.

### E2E-05 – Sicherheitsblockade
**Wenn:** gefälschte URL, interne Redirect-Adresse, ZIP-Slip/ZIP-Bomb-Testarchiv oder lokale Malware-Testsignatur übergeben wird. **Dann:** sicherer Abbruch, verständliche Meldung, keine eigentliche Codeanalyse und bereinigtes Arbeitsverzeichnis.

### E2E-06 – Historie und PDF
**Wenn:** zwei Scans mit wiedererkennbaren Findings abgeschlossen sind. **Dann:** Unterschiede/Fingerprints sichtbar, historische Snippets maskiert, komplette Fremddateien fehlen; PDF enthält echte Ergebnisse, Coverage und Grenzen ohne offengelegte Secrets.

### E2E-07 – Manuelle Sicherheitsupdates
**Wenn:** Update über UI angestoßen wird. **Dann:** nur geprüfte Quellen werden benutzt, Aktualisierungsstatus transparent; bei anschließendem Offline-Scan keine Updateverbindung.

### E2E-08 – Neustart und Datenbereinigung
**Wenn:** Scan abgeschlossen ist und Stack neu gestartet wird. **Dann:** Historie/Findings noch vorhanden, volle Quellarchive nicht; absichtlich unterbrochene Jobs hinterlassen nach Recovery keine unbereinigten Projektdateien.

### E2E-09 – Design, DE/EN und Light/Dark
**Wenn:** beide Sprach-/Themevarianten auf Desktop/Tablet/Smartphone getestet werden. **Dann:** vollständiger Kernworkflow lesbar, bedienbar und backendgebunden; keine aktiven Fremdcodeinhalte.

### E2E-10 – Promptgesteuerte Agentenentwicklung im Git-Repository
**Gegeben:** RepoGuard ist als Git-Repository in VS Code oder IntelliJ IDEA geöffnet und ein Entwicklungsagent eingebunden. **Wenn:** der Nutzer einen Prompt zur Implementierung bzw. Korrektur einer klar abgegrenzten Funktion eingibt und auf die verbindlichen Projektdateien verweist. **Dann:** der Agent ändert die entsprechenden Dateien im Checkout, führt passende Tests der **RepoGuard-Anwendung** aus, zeigt den Git-Diff und dokumentiert das Ergebnis einschließlich Review und Commit. Dabei wird kein untersuchtes Fremdrepository gebaut oder ausgeführt.

## 13. Definition of Done
RepoGuard Version 1.0 ist erst freigabefähig, wenn:
1. **Alle MUSS-Kriterien PASS** sind; BLOCKED/NOT TESTED sind keine Freigabe.
2. **Alle zehn E2E-Tests** real und mit Nachweisen bestanden sind.
3. **Alle zehn Sprachgruppen** mit tatsächlichen quellcodebasierten Security- und Quality-Prüfungen nachgewiesen sind.
4. **Kein fremder Repository-Code** ausgeführt, gebaut oder getestet wurde; Negativtests bestanden.
5. **Quarantäne, lokaler Malware-Vorscan, SSRF-/Archivschutz und Cleanup** erfolgreich geprüft wurden.
6. **Phase 1 und Phase 2** einschließlich Filter, maskierter historischer Codeausschnitte, PostgreSQL und PDF umgesetzt sind.
7. **Deutsch/Englisch, Light/Dark, LAN-Betrieb** und Sicherheitswarnungen funktionieren.
8. **Docker Compose**, Quelldateien, Dokumentation und echte Testprotokolle vorliegen.
9. Der **Agenten-/IDE-/Git-Workflow** gemäß AGENT-01 bis AGENT-08 mit realem Git-Diff und Testnachweis erfüllt ist.
9. Keine bekannten ungeklärten kritischen oder hohen Sicherheitsprobleme bestehen.

## 14. Abnahmeberichte
Verpflichtend: `TEST_REPORT.md` (menschenlesbar) und `test-results.json` (maschinenlesbar). **Jeder Testeintrag** enthält Test-ID, Requirement-Verweis, Beschreibung, Status, Datum/Uhrzeit, getestete Version, Testumgebung, Testdaten/Fixture, erwartetes Ergebnis, tatsächliches Ergebnis, Nachweis (Log/Screenshot/Automationsausgabe/Hash), bei Fehlschlag Ursache und Behebungsstatus. **Keine fingierten Ergebnisse**; nicht ausgeführte Tests als NOT TESTED markieren.

## 15. Traceability und Vorrang
Die IDs dieses Dokuments sind verbindlich und werden in automatisierten Tests/Reports verwendet. Bei Sicherheitskonflikten gelten stets die strikten Verbote aus `REQUIREMENTS.md`; nicht realisierbare Scannerprüfungen werden ehrlich als `UNSUPPORTED`/`SKIPPED` mit Grund ausgewiesen, **niemals** durch Ausführung von Fremdcode kompensiert.
