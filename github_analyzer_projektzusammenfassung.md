# GitHub Repository Analyzer – Projektzusammenfassung und Anforderungen

**Stand:** 21. Oktober 2026  
**Status:** Anforderungen festgelegt; Implementierung noch nicht begonnen  
**Umfang:** Phase 1 und Phase 2

## 1. Projektziel

Entwicklung einer **lokal betriebenen Fullstack-Webapplikation**, die GitHub-Repositories und hochgeladene ZIP-Projekte **vor der ersten Ausführung** auf **IT-Sicherheitsrisiken und Codequalität** untersucht.

**Zentrale, verbindliche Anforderung: Der Quellcode eines untersuchten Repositories wird niemals ausgeführt.** Die Webapplikation analysiert ausschließlich vorhandene Dateien und statisch auswertbare Informationen. Es gibt **keinen dynamischen Testmodus (keine Stufe B)**.

Die Anwendung wird im **Webbrowser** bedient; sie ist keine Desktop-Anwendung und kein IntelliJ-Plugin. IntelliJ IDEA ist lediglich die empfohlene Entwicklungsumgebung.

Weitere verbindliche Rahmenbedingungen:

- **Keine KI und keine KI-API** – auch nicht für Erklärungen oder Verbesserungsvorschläge.
- **Keine GitHub REST API, keine GitHub-App und kein GitHub-API-Token** erforderlich.
- **Lokale Analyse:** Quellcode und Scanergebnisse werden von lokalen Diensten verarbeitet, ohne sie an externe KI- oder Analyseplattformen zu übertragen. Nur beim Import öffentlicher GitHub-Repositories erfolgt ein ausdrücklich begrenzter Download von GitHub.
- **Möglichst breite Sprachunterstützung** durch eine modulare, erweiterbare Scanner-Architektur.
- **Phase 1 und Phase 2** werden entwickelt; eine KI-Erweiterungsphase ist nicht vorgesehen.

## 2. Striktes Verbot der Projektausführung

### Erlaubt

- Dateien und Verzeichnisstrukturen lesen; Programmiersprachen und Framework-Hinweise erkennen.
- Quellcode, Syntaxbäume, Codemuster, Regeln, Datenflüsse und statisch ableitbare Qualitätsmetriken untersuchen.
- Manifest- und Lockdateien auf bekannte Schwachstellen in Abhängigkeiten prüfen.
- Lokal installierte, vertrauenswürdig konfigurierte **statische Scanner** auf Dateien anwenden.
- Ergebnisse als Findings mit Dateipfad, Zeile, Regel, Schweregrad und Erklärung aufbereiten.

### Verboten

- Projekte oder ihre Programme starten und irgendwelche zu prüfenden Binaries ausführen.
- Projektquellcode kompilieren oder bauen, auch nicht als Vorbereitung für Scanner.
- Tests, Build-Skripte, Postinstall-Skripte, Makefiles oder projektinterne Hilfsskripte ausführen.
- `npm install`, `pip install`, `cargo build`, `mvn package`, `gradle build`, `swift build` oder vergleichbare projektbezogene Befehle ausführen.
- Aus dem Repository stammende ausführbare Scanner-Plugins, Konfigurationsskripte, Hooks, Bootstrap-Dateien oder benutzerdefinierte Regeln unkontrolliert laden/ausführen.
- Fremde Projektdateien zur Laufzeit als ausführbaren Code interpretieren.

**Abgrenzung:** Die **Scanner-Programme selbst** müssen natürlich ausgeführt werden. Sie laufen als vom Analyzer verwaltete, vertrauenswürdige Werkzeuge; **der untersuchte Projektcode bleibt stets passiver Input**. Scanner werden so eingerichtet, dass sie nur vertrauenswürdige, von uns bereitgestellte Konfigurationen und Plugins verwenden und keine Projekt- oder Build-Skripte nachladen.

Diese Anforderung hat Vorrang vor größerer Prüftiefe: **Ein Werkzeug, das zum Analysieren einen Build oder die Ausführung von Repository-Code benötigt, wird nicht eingesetzt.**

## 3. Repository-Eingabe und sicherer Import (verbindlich)

Es gibt ausschließlich zwei Eingabearten:

1. **Öffentlicher GitHub-Link:** Ein Repository wird nur von einem zuvor überprüften GitHub-Endpunkt heruntergeladen, **ohne GitHub REST API, API-Token oder GitHub-App**.
2. **ZIP-Upload:** Benutzer laden die Projektdateien direkt im Browser hoch. Beide Importwege durchlaufen dieselben strikten Archiv- und Dateiprüfungen.

### 3.1 GitHub-Link zwingend vor jedem Download prüfen

- Nur `https://github.com/<owner>/<repository>` (optional mit abschließendem `.git` und `/`) als Eingabe akzeptieren. Die Anwendung kann später kontrollierte Optionen für Branch/Commit anbieten; zusätzliche, frei wählbare URLs sind **nicht erlaubt**.
- URL mit einem robusten URL-Parser zerlegen und das **tatsächliche** Schema, den Host, Port, Userinfo und Pfad prüfen. **Nur `https` und exakt `github.com`** zulassen, nicht bloß Zeichenfolgen, die `github.com` enthalten.
- Unzulässig sind u. a. `http`, `file`, `ssh`, `git`, eigene Ports, URL-Benutzerinformationen (`@`), Query-Parameter, Fragmente, IP-Adressen, verschachtelte oder zweideutige URLs, Backslashes und nicht zum erwarteten Repo-Format passende Pfade.
- Hostnamen wie `github.com.evil.example` oder `github.com@evil.example` werden abgewiesen. Repository-Eigentümer und -Name streng auf ein gültiges, begrenztes Zeichenformat prüfen.
- Nach der Prüfung **die Downloadadresse serverseitig neu aufbauen**; niemals die ungeprüfte Benutzereingabe direkt an HTTP-Clients, Shells oder Git weiterreichen.
- **SSRF-Schutz:** DNS-Auflösung und tatsächliche Ziel-IP prüfen; private, lokale, reservierte und Cloud-Metadaten-Adressen blockieren. Verbindungen müssen an freigegebene Ziele gebunden sein, damit DNS-Rebinding die Prüfung nicht umgehen kann.
- **Redirects nicht automatisch folgen.** Notwendige GitHub-Weiterleitungen nur nach erneuter Prüfung explizit zulassen (beispielsweise zum offiziellen `codeload.github.com`); jede andere Ziel-Domain oder ein anderes Protokoll führt zum Abbruch. TLS-Zertifikate müssen gültig sein.

### 3.2 Quellcode herunterladen, niemals ausführen

- **Bevorzugter Importweg:** ein von GitHub bereitgestelltes **Quellcode-ZIP-Archiv** (Snapshot) über HTTPS, statt eines vollständigen `git clone`. GitHub stellt hierfür Archive bereit; der Download benötigt keine GitHub REST API. So werden Git-History, Checkout, Submodule und projektspezifische Git-Hooks für den Analyzer nicht benötigt.
- Branch bzw. Commit werden nur über kontrollierte, validierte Angaben aufgelöst; falls dafür ein Metadatenaufruf nötig ist, muss dieser ebenfalls dem Netzwerk- und Prozess-Sicherheitsmodell entsprechen.
- Download **streamen** und strenge Maximalgrenzen für übertragenes Datenvolumen, Dauer und Speicher setzen; HTTP-Status, Dateiheader und tatsächlichen Archivtyp prüfen.
- Dateien **zunächst ausschließlich in einem isolierten Quarantäne-/Arbeitsverzeichnis** speichern (kein automatisches Öffnen, Ausführen oder Scannen mit projektspezifischen Plugins).
- Verpflichtendes **lokales Antimalware-Signaturscanning** (z. B. ClamAV) nach dem kontrollierten Download und vor der eigentlichen Codeanalyse; bei Schadsoftwarefund Download-Inhalt sperren/quarantänisieren und den Benutzer warnen. Signaturen müssen lokal gepflegt werden.
- Keine Submodule, externen Assets oder Paketabhängigkeiten des Zielprojekts automatisch herunterladen; keinerlei Build-, Test-, Setup- oder Postinstall-Skripte ausführen.

### 3.3 Archive und Dateien sicher verarbeiten

- ZIP-Dateien, auch jene von GitHub, als **nicht vertrauenswürdig** behandeln. Dateianzahl, Gesamtgröße nach Entpacken, einzelne Dateigrößen, Verschachtelungstiefe und Komprimierungsverhältnis begrenzen (**ZIP-Bomb-Schutz**).
- **Zip Slip / Path Traversal** verhindern: Nur kanonisch innerhalb des zugewiesenen Arbeitsverzeichnisses liegende Pfade akzeptieren; absolute Pfade, `..`-Ausbrüche, gefährliche Symlinks und spezielle Dateitypen abweisen.
- Berechtigungen restriktiv setzen; Dateien in einem abgeschotteten Bereich (vorzugsweise `noexec`), begrenzte CPU/RAM/Plattenkontingente und definierte Löschfristen.
- Scanner nur mit unseren **vertrauenswürdigen, fest hinterlegten** Regeln und Konfigurationen starten. Projektdateien dürfen weder als ausführbare Konfiguration geladen noch über das Dashboard als aktive HTML-/Script-Inhalte gerendert werden.
- Scanner arbeiten möglichst **ohne Netzwerkzugriff**, mit schreibgeschütztem Zugriff auf das Repository und ohne Ausführungsberechtigung für fremde Projektdateien.

### 3.4 Sicherheitsgrenzen und Fehlerverhalten

**Wichtig: Ein Link auf das echte GitHub beweist nicht, dass der Repository-Inhalt harmlos ist.** Auch legitime GitHub-Repositories können Malware oder gefährliche Dateien enthalten. Vor dem Download kann deren tatsächlicher Inhalt nicht vollständig geprüft werden. Das Sicherheitsziel lautet deshalb: **Nur von offiziellen GitHub-Endpunkten herunterladen, Downloads begrenzen und isolieren, lokal auf bekannte Malware prüfen und niemals fremden Projektcode ausführen.** Eine hundertprozentige Malwarefreiheit kann kein Scanner garantieren.

- Bei ungültigen Links, unsicheren Redirects, abweichenden Inhalten, überschrittenen Limits oder Malwarefunden: **Import sicher abbrechen und verständlichen Fehler anzeigen**.
- Identische Regeln gelten sinngemäß auch für ZIP-Uploads. Keine Quellcode-Dateien an externe Scan- oder KI-Dienste übermitteln.
- Negative Sicherheitstests fest vorsehen: gefälschte Domains, `@`-URLs, alternative Protokolle und Ports, DNS-Rebinding, interne IP-Adressen, unsichere Weiterleitungen, ZIP-Slip, ZIP-Bombs, Symlinks, schädliche Uploads und defekte Archive.

**Lokal vs. offline:** Die eigentliche Analyse erfolgt lokal. Ein GitHub-Link benötigt zum Herunterladen eine Netzwerkverbindung. ZIP-Analysen können nach vorheriger Installation der Werkzeuge, Regeln, Antimalware-Signaturen und Schwachstellendatenbanken vollständig offline laufen. Diese Datenbestände müssen für aktuelle Erkennung regelmäßig gepflegt werden.

## 4. Verbindlicher Funktionsumfang der Analyse

### 4.1 IT-Sicherheit

- SQL Injection und andere unsichere Datenbankzugriffe
- Cross-Site Scripting (XSS)
- Hinweise auf fehlende Zugriffskontrollen (Broken Access Control), soweit statisch erkennbar
- Hartcodierte Secrets und Zugangsdaten
- Path Traversal und unsichere Dateioperationen
- Unsichere APIs, gefährliche Codemuster und Sicherheitsfehlkonfigurationen
- Bekannte Schwachstellen in Dependencies anhand unterstützter Manifest-/Lockdateien

**Grundwerkzeuge:** Semgrep für statische Sicherheitsregeln; Trivy für Schwachstellen, Secrets und unterstützte Konfigurationsprüfungen. Regeln/Scanner werden lokal vorgehalten und kontrolliert konfiguriert.

### 4.2 Codequalität

- Code Smells und auffällige Programmiermuster
- Übermäßige Komplexität sowie große oder schwer wartbare Funktionen
- Duplikate, wo ein geeigneter statischer Scanner dies unterstützt
- Formatierung, Stil- und Namenskonventionen
- Hinweise auf statisch erkennbare Programmierfehler
- Wartbarkeitsmetriken und Dateistruktur

**Grundprinzip:** Ergänzung der generischen Analyse durch **rein quellcodebasierte, sprachspezifische** Linter und Formatter. Qualität und Abdeckung sind sprachabhängig.

### 4.3 Verbindlich zu unterstützende Programmiersprachen

Die zehn Gruppen aus der Projektbesprechung gehören **zum Anforderungskatalog**, nicht nur zu optionalen Erweiterungen. Innerhalb einer Gruppe können unterschiedliche Teilanalysen verfügbar sein. Weitere Sprachen sollen über Plugins/Adapter hinzukommen.

| Sprache | Statische Sicherheitsanalyse | Statische Codequalitätsanalyse ohne Projekt-Build |
|---|---|---|
| **Java** | Semgrep, Trivy für unterstützte Dependencies | Checkstyle, Semgrep-Regeln |
| **Python** | Semgrep, Bandit, Trivy | Ruff |
| **Rust** | Semgrep-Regeln (soweit verfügbar), Trivy für unterstützte Dependencies | rustfmt-Prüfungen und statische Regeln; **kein Clippy** |
| **Swift** | Semgrep-Regeln (soweit verfügbar), Trivy für unterstützte Dependencies | SwiftLint mit vertrauenswürdiger, externer Konfiguration |
| **JavaScript / TypeScript** | Semgrep, Trivy | ESLint mit ausschließlich vertrauenswürdigen Regeln/Plugins |
| **C / C++** | Semgrep-Regeln (soweit verfügbar), weitere rein statische Prüfungen | Cppcheck, clang-format; kein Build |
| **C#** | Semgrep-Regeln (soweit verfügbar), Trivy für unterstützte Dependencies | rein quellcodebasierte Roslyn-/Syntax-Regeln ohne Projekt-Build |
| **Go** | Semgrep-Regeln (soweit verfügbar), Trivy | gofmt und quellcodebasierte Regeln; kein Projekt-Build |
| **Kotlin** | Semgrep-Regeln (soweit verfügbar) | detekt mit vertrauenswürdiger Konfiguration, ohne Build |
| **PHP** | Semgrep, Trivy für unterstützte Dependencies | quellcodebasierte Style-/Syntaxregeln, beispielsweise PHP_CodeSniffer mit vertrauenswürdigem Regelwerk |

**Hinweise zur Machbarkeit:** Die genannten Werkzeuge sind eine technische Zielauswahl, die bei der Implementierung pro Sprache auf **echte Source-only-Fähigkeit** und sichere Konfiguration geprüft wird. Einträge bedeuten **nicht**, dass jedes Werkzeug jede Schwachstellenklasse in jeder Sprache findet. Projektkonfigurationen und Plugins dürfen nicht ungeprüft geladen werden.

**Explizit ausgeschlossen:** Clippy, wenn dafür Rust-Code gebaut bzw. Build-Skripte ausgeführt werden müssten; SpotBugs als Standardscanner, weil es Java-Bytecode statt reinen Quellcode analysiert; alle anderen Scanner-Modi, die fremden Code ausführen oder kompilieren.

### 4.4 Sprachübergreifende Erweiterbarkeit

- Automatische Erkennung mehrerer Sprachen in **einem** Repository anhand von Dateitypen und Projektmanifesten.
- Zentrale Scanner-Schnittstelle und pro Sprache geeignete, sichere Adapter.
- Gemeinsames Finding-Format für alle Tools und Sprachen.
- Einfache spätere Ergänzung weiterer Parser, Linter und Regelpakete.
- Für unbekannte Sprachen zumindest generische Prüfungen (z. B. Secret-Suche, Konfigurationsprüfung, Dateimetriken), **ohne vollständige Sicherheitsabdeckung vorzutäuschen**.
- Pro Prüfung anzeigen: **durchgeführt / nicht unterstützt / fehlgeschlagen / übersprungen**.

## 5. Phase 1 – Lokal lauffähiger Web-MVP

- React-/TypeScript-Weboberfläche im Browser
- Öffentliche GitHub-URL eingeben oder ZIP-Datei hochladen
- **GitHub-URL vor dem Download streng prüfen** (exakte Domain, HTTPS, sicherer Pfad, SSRF-/Redirect-Schutz)
- **Kontrollierter Quellcode-Archivdownload**, Dateiquarantäne und verpflichtender lokaler Malware-Vorscan
- Sichere Import- und Dateiverarbeitung ohne Ausführung des Repositories
- Automatische Mehrsprachenerkennung
- Lokale, rein statische Sicherheits- und Qualitätsanalyse mit passenden Scannern
- Einheitliche Findings: Typ, Kategorie, Schweregrad, Regel, Dateipfad, Zeilennummer (sofern verfügbar), Erklärung und regelbasierter Lösungshinweis
- Ergebnis-Dashboard mit zusammenfassenden Kennzahlen
- Status und Abdeckung der einzelnen Scanner nachvollziehbar anzeigen

## 6. Phase 2 – Erweiterte Funktionen

- Bekannte Schwachstellen in Dependencies prüfen, soweit per Manifest-/Lockdateien möglich
- Findings nach Sprache, Tool, Kategorie, Schweregrad und Datei filtern
- Quellcodeansicht mit Zeilennummern und markierten Fundstellen
- Historie früherer Analysen in PostgreSQL speichern und vergleichen bzw. anzeigen
- PDF-Export eines Analyseberichts einschließlich Scanner-Abdeckung und Einschränkungen
- Verbesserte Fehlerbehandlung, Berichtsqualität und Übersichtlichkeit

**Nicht geplant:** Ausführung, Testen, Kompilierung oder Deployment der untersuchten Repositories; dynamische Laufzeitanalyse; KI-gestützte Auswertung; GitHub API/Logins/automatische Pull-Request-Scans.

## 7. Architektur und Technologie-Stack

| Bereich | Technologie / Rolle |
|---|---|
| Entwicklung | IntelliJ IDEA als IDE |
| Web-Frontend | React + TypeScript |
| Backend / REST API | Java 21 + Spring Boot |
| Datenbank | PostgreSQL |
| GitHub-Import | Streng validierte GitHub-URL, kontrollierter HTTPS-Download offizieller GitHub-Quellcode-ZIP-Archive, ohne REST API |
| ZIP-Verarbeitung | Java ZIP API mit ZIP-Slip-/ZIP-Bomb-Schutz, Datei-/Größen-/Ressourcenlimits und isolierter Quarantäne |
| Malware-Vorscan | **Verpflichtend:** lokal installierter Signaturscanner, z. B. ClamAV, ohne Cloud-Übertragung |
| Security | Semgrep, Trivy, Bandit und vertrauenswürdige statische Regeln |
| Qualität | Sprachspezifische Source-only-Linter und Formatter, z. B. Ruff, ESLint, Checkstyle, SwiftLint, Cppcheck, detekt |
| Bereitstellung | Docker Compose für die **eigene Webapplikation und Scanner** |
| Berichtserstellung | Lokaler PDF-Generator im Backend |

**Wichtige Unterscheidung:** `docker compose up --build` darf verwendet werden, um **unseren Analyzer** zu bauen und zu starten. Es darf **niemals** dazu verwendet werden, hochgeladene oder geklonte fremde Repositories zu bauen oder auszuführen.

### Verarbeitungsablauf

```text
Browser: GitHub-URL oder ZIP
           |
           v
Spring Boot: GitHub-Link validieren + sicheren Download / ZIP prüfen
           |
           v
Quarantäne + Archivgrenzen + lokaler Malware-Vorscan
           |
           v
Dateien erkennen + Sprachen/Dependencies bestimmen
           |
           v
Lokale Scanner mit vertrauenswürdigen Regeln
(nur Lesen/Parsen, kein Build, keine Projektausführung)
           |
           v
Ergebnisse normalisieren, Duplikate bewerten,
Scanner-Abdeckung dokumentieren
           |
           v
PostgreSQL: Analysen und Findings
           |
           v
React-Dashboard: Filter / Codeansicht / Historie
           |
           v
PDF-Bericht
```

## 8. Geplante Projektstruktur

```text
github-analyzer/
├── backend/             # Spring Boot, API, Analyse-Orchestrierung, Persistence
├── frontend/            # React/TypeScript, Upload und Dashboard
├── scanner/             # Vertrauenswürdige Regeln, Scanner-Adapter, Konfigurationen
├── database/            # Migrationen
├── tests/               # Tests der eigenen Anwendung, niemals des Ziel-Repositories
├── docker-compose.yml
├── .env.example
└── README.md
```

## 9. Sicherheitsanforderungen an die Webapplikation

- Fremde Repositories **nur als Daten** behandeln und niemals ausführen.
- Scanner in isolierten, ressourcenbeschränkten Prozessen/Containern mit möglichst schreibgeschützter Sicht auf den Quellcode betreiben.
- Ohne Netzwerkzugriff während der eigentlichen Analyse, soweit möglich; benötigte Regeln und Scanner-Daten vorher lokal bereitstellen.
- Ausschließlich **vertrauenswürdige Scanner-Versionen, Regeln, Parser und Plugins** nutzen; Konfigurationen aus dem Ziel-Repository nicht automatisch laden.
- Symlinks, Pfad-Traversal, ZIP-Bombs und zu große Dateien/Archive absichern.
- GitHub-URL **vor dem Download** nach strenger Allowlist verifizieren; nur offiziell erlaubte GitHub-Hosts, TLS, SSRF-/DNS-/Redirect-Prüfung; Archivdownload bevorzugen statt Git-Checkout.
- Heruntergeladene und hochgeladene Archive vor Verwendung isolieren und kontrollieren; verpflichtender lokaler Malware-Vorscan vor der Codeanalyse.
- CPU-, RAM-, Speicher- und Laufzeitlimits für Scanner; kontrollierte Fehlerbehandlung.
- Eingaben, Ergebnisse und gegebenenfalls Secrets geschützt speichern und anzeigen.
- Fehlende Scanner-Abdeckung und Fehlalarme klar kommunizieren.

## 10. Entwicklung und Übergabe

Geplant ist ein **vollständiges Fullstack-Projekt**, einschließlich Frontend, Backend, Datenbankschema, Scanner-Integration, Docker-Konfiguration, automatisierter Tests der **Analyzer-Anwendung selbst** und Projektdokumentation. Das Projekt soll als ZIP übergeben und lokal ausgeführt werden können. Die Endbenutzer brauchen lediglich einen Browser.

Die eigentliche Implementierung und Verifikation auf dem Zielrechner stehen noch aus. Nach Installation der erforderlichen Images/Scanner-Daten können ZIP-Repositories lokal und bei entsprechendem Datenbestand offline analysiert werden. GitHub-URLs benötigen einen Downloadzugriff.

## 11. Zusammenfassung der beschlossenen Anforderungen

| Entscheidung | Vorgabe |
|---|---|
| Anwendung | **Browserbasierte Webapplikation** |
| Funktionsumfang | **Phase 1 und Phase 2** |
| Analysen | **IT-Sicherheit und Codequalität** |
| Eingaben | **Öffentliche GitHub-URL oder ZIP-Datei** |
| GitHub-Link-Sicherheit | **Vor Download verifizieren: exakter Host, HTTPS, SSRF- und Redirect-Schutz** |
| Download-Sicherheit | **GitHub-Quellcode-ZIP, Quarantäne, Größenlimits, sichere Extraktion, verpflichtender lokaler Malware-Vorscan** |
| GitHub REST API | **Nicht verwenden** |
| KI / KI-API | **Nicht verwenden** |
| Verarbeitung | **Lokal, mit lokalen Scanwerkzeugen** |
| Projektcode ausführen | **Strikt verboten** |
| Projektcode kompilieren / Tests / Skripte starten | **Strikt verboten** |
| Dynamische Analyse / Stufe B | **Nicht vorgesehen** |
| Sprachen | **Java, Python, Rust, Swift, JS/TS, C/C++, C#, Go, Kotlin, PHP** sowie erweiterbar |
| Ergebnisse | **Findings, Filter, Codeansicht, Historie, PDF** |
| Stack | **Java 21, Spring Boot, React, TypeScript, PostgreSQL, Docker Compose** |
| Umsetzungsstatus | **Noch nicht programmiert** |

**Leitprinzip:** Der Analyzer prüft ausschließlich Quellcode und statisch zugängliche Metadaten, **bevor** Entwickler ein Projekt starten. Keinerlei vom untersuchten Repository stammender Code wird ausgeführt – auch nicht indirekt über Build-Tools, Plugins oder Konfigurationsskripte. **Jeder GitHub-Link wird vor dem Download validiert und jeder Download bleibt nicht vertrauenswürdiger, isolierter Input.**

## Verbindliche Prompt-Dokumentation (Ergänzung 2026-11-06)

Alle neuen projektbezogenen Nutzernachrichten einschließlich Rückfragen, Korrekturen und Freigaben werden vor ihrer Bearbeitung gemäß der verbindlichen Regel in [PROMPTS.md](PROMPTS.md) fortlaufend protokolliert. Nicht verfügbare frühere Nachrichten werden nicht erfunden; Anhänge werden nur referenziert und Zugangsdaten ausgeschlossen. Keine `.env`-Inhalte übernehmen. Für künftige Projektarbeit gilt zusätzlich [AGENTS.md](AGENTS.md).
