# RepoGuard – REQUIREMENTS.md
**Version:** 1.0 · **Status:** Verbindlicher Entwicklungsauftrag · **Stand:** 21.10.2026

## 1. Auftrag, Quellen und Prioritäten
Entwickle **RepoGuard** als vollständig funktionsfähige lokale, browserbasierte Fullstack-Webanwendung zur **statischen** Sicherheits- und Codequalitätsanalyse fremder Projekte **vor deren erster Ausführung**. Eingaben sind ausschließlich (a) eine öffentliche GitHub-Repository-URL und (b) ein vom Nutzer hochgeladenes ZIP-Projekt.

Diese Datei konkretisiert `github_analyzer_projektzusammenfassung.md`; `DESIGN.md` konkretisiert UX, `ACCEPTANCE_CRITERIA.md` die Abnahme. HostNox-Referenzen dienten nur als Formatvorlage; keine HostNox-spezifischen Anforderungen gelten für RepoGuard. **Priorität bei Konflikten:** (1) striktes Ausführungsverbot und übrige Sicherheitsinvarianten, (2) hier dokumentierte ausdrückliche Nutzerentscheidungen, (3) fachliche Projektzusammenfassung, (4) begründete Implementierungsdetails. Abweichungen dokumentieren und nicht stillschweigend vereinfachen.

**Fertigstellung:** Phase 1 **und** Phase 2 vollständig implementieren, testen und dokumentieren. Kein reines Mockup, keine simulierten Scannerergebnisse und keine nur nominell unterstützten Sprachen.

## 2. Festgelegte Produktentscheidungen
| Gegenstand | Bindende Entscheidung |
|---|---|
| Projektname | RepoGuard |
| Form | Webapplikation, keine Desktop-App/IDE-Erweiterung |
| Frontend | React + TypeScript |
| Backend | Java 21 + Spring Boot, REST API |
| Datenbank | PostgreSQL |
| Deployment | Docker Compose als Standardinstallation |
| Bedienung | Lokale Einzelbenutzeranwendung ohne Login |
| Erreichbarkeit | Standardmäßig im **lokalen Netzwerk** erreichbar, ausdrücklich **nicht** fürs offene Internet vorgesehen |
| UI | Modern, minimalistisch, GitHub-inspiriert; Light/Dark und DE/EN umschaltbar |
| Eingabe | Öffentliche GitHub-Repo-URL oder ZIP-Upload |
| Analyse | Statische IT-Sicherheit **und** Codequalität |
| Sprachen | Java, Python, Rust, Swift, JS/TS, C/C++, C#, Go, Kotlin, PHP; alle verpflichtend |
| Phase | 1 + 2 vollständig |
| Historie | Findings, Metadaten und **maskierte, begrenzte Fundstellenausschnitte** persistieren; komplette importierte Repos nach Analyse löschen |
| Ressourcen | Sichere konfigurierbare Standardlimits |
| Aktualisierungen | Manuell per UI auf Knopfdruck; **kein automatischer Internetzugriff während der Analyse** |
| KI/GitHub API | Keine KI/KI-API; keine GitHub REST API/App/Token |
| Projektausführung | **Immer verboten**, auch indirekt |

## 3. Nicht verhandelbares Ausführungsverbot
**Fremder Repository-Code bleibt passiver Input.** Erlaubt sind Lesen, Hashen, Parsen, Dateimetriken, statische Syntax-/Datenflussregeln, Abhängigkeits-/Manifestprüfung und die Ausführung **unserer vertrauenswürdig bereitgestellten statischen Scanner**. Verboten sind: Start von Repository-Binaries; Kompilierung/Build des Projekts; Projekttests; Hooks; Makefiles; Install-/Postinstall-Skripte; `npm install`, `pip install`, `cargo build`, `mvn package`, `gradle build`, `swift build` oder Entsprechendes; Ausführung von mitgelieferten Configskripten, Plugins, Rules, lintern, projektspezifischen Compiler-/Interpreter-Hooks. Keine dynamische „Stufe B“, keine Sandbox als Vorwand für Projektausführung.

**Wichtig:** Ein statischer Scanner darf laufen, aber seine Konfiguration/Regeln/Plugins müssen aus einem **vertrauenswürdigen, mit RepoGuard verwalteten Bestand** stammen. Scannermodi, die fremde Konfigurationsdateien ausführen, Dependencies installieren oder einen Build erzwingen, werden deaktiviert oder ausgeschlossen. Keine stillschweigenden Fallbacks auf riskante Tools.

## 4. Architektur
Komponenten und Datenfluss:
```text
Browser (React/TS)
  -> Spring Boot REST API / Job-Orchestrierung
  -> Eingabevalidierung / kontrollierter GitHub-Archivdownload ODER ZIP-Upload
  -> isoliertes Quarantäne-Verzeichnis + lokaler Malware-Vorscan
  -> ZIP-Validierung und sichere Extraktion
  -> Sprach-/Manifest-Erkennung
  -> isolierte lokale statische Scanner-Adapter (ohne Repository-Ausführung)
  -> Normalisierung/Deduplizierung/Abdeckungsstatus
  -> PostgreSQL (Findings, Metadaten, maskierte Code-Snippets)
  -> kontrollierte Löschung aller vollständigen Repository-Dateien
  -> Dashboard / Filter / Historie / PDF
```
Download-/Update-Komponenten sind von Scanner-Laufzeitnetzwerken logisch getrennt. Scanner-Container/Prozesse dürfen keine unkontrollierte Verbindung nach außen haben. PostgreSQL ist nicht öffentlich zu exponieren. Lokale Daten sind durch persistente Volumes zu sichern, temporäre Projektdateien **nicht** persistent aufzubewahren.

## 5. Sichere Eingaben
### 5.1 GitHub-URL
- Zulässiges Eingabeformat ausschließlich `https://github.com/<owner>/<repository>` mit optionalem abschließendem `.git` und `/`; Query, Fragment, Userinfo, alternativer Port, weitere Pfadsegmente, Backslashes und verschachtelte URLs ablehnen.
- Mit robustem URL-Parser Schema, **exakten** Host `github.com`, Port, Userinfo, Pfad und Komponenten validieren. `github.com.evil.example`, `github.com@evil.example`, IPs, `http:`, `file:`, `ssh:` etc. abweisen.
- Owner/Repo syntaktisch begrenzen und validieren; keine ungebundenen URLs, Branch-Parameter oder URL-Fragmente übernehmen.
- Download-URL **serverseitig aus geprüften Komponenten neu erstellen**. Bevorzugt offizielles GitHub-**Quellcode-ZIP**, **kein Git-Checkout**, keine Submodule und keine fremden Hooks.
- DNS-Auflösung und tatsächlich verwendete Ziel-IP kontrollieren; private, Loopback-, Link-local-, reservierte und Cloud-Metadaten-Ziele verweigern. DNS-Rebinding verhindern, indem validierte Ziele mit tatsächlicher Verbindung gekoppelt sind.
- Redirects standardmäßig aus; unvermeidbare Redirects einzeln prüfen und nur offizielle festgelegte GitHub-Archivhosts (z. B. `codeload.github.com`) nach erneuter Schema-/Host-/IP-/TLS-Prüfung zulassen.
- Timeouts, Antwortgrößenlimits, Streaming, TLS-Prüfung, HTTP-Status und tatsächlichen Archivtyp prüfen. Branch-/Default-Branch-Auflösung ohne REST API nur über explizit abgesicherte Metadaten-/Archivmechanismen; **nicht** stillschweigend annehmen, dass jeder Default-Branch `main` heißt. Falls dies nicht sicher und eindeutig machbar ist, Fehlermeldung oder dokumentierter, sicherer Fallback.

### 5.2 ZIP-Upload und Archivverarbeitung
- Beide Importwege benutzen dieselbe Quarantäne und dieselbe Archiv-Validierung.
- Vor Extraktion: Datei-/Archivtyp und Größen-/Dateianzahl-/Tiefen-/Kompressionslimits prüfen. Beim Entpacken fortlaufend **tatsächlich** ausgegebene Bytes zählen.
- Gegen ZIP-Slip/Traversal, absolute Pfade, `..`, Windows-Pfadvarianten, Symlinks, Special Files und Überschreiben außerhalb des Arbeitsverzeichnisses absichern. Keine verfolgten Symlinks. Doppelte/kollidierende Pfade deterministisch und sicher behandeln.
- Archive zunächst in isoliertem Quarantänebereich halten; Dateirechte restriktiv, vorzugsweise `noexec`; niemals im statischen Webroot bereitstellen.
- **Verpflichtender lokaler Antimalware-Signaturvorscan** (z. B. ClamAV) nach Upload/Download und vor der eigentlichen Codeanalyse. Ein Fund oder fehlgeschlagener/unverfügbarer verpflichtender Vorscan blockiert die Analyse **fail-closed**. Ein Negativbefund ist keine Malwarefreiheitsgarantie.
- Kein Projektbestandteil wird zum Browser als aktives HTML/JS gerendert; Datei- und Codeansichten ausschließlich escaped/plain text.

## 6. Scanner-Matrix und Mindestabdeckung
**Alle zehn Sprachgruppen sind MUSS.** Pro erkannter Sprachgruppe muss RepoGuard mindestens einen tatsächlich ausgeführten, sicheren quellcodebasierten Sicherheits-/Regelcheck und mindestens einen tatsächlich ausgeführten sicheren Qualitäts-/Syntax-/Style-Check nachweisen, soweit die betreffende Gruppe in einem geeigneten Testprojekt enthalten ist; generische Secrets-/Dateimetrikchecks allein dürfen nicht als vollständige sprachspezifische Unterstützung ausgegeben werden. Bestehende **technisch mögliche, sicher source-only** Prüfungen der integrierten Adapter sollen ausgeführt werden; jede unterlassene Prüfung wird begründet. **Unmögliche/unsichere/nicht vorhandene Prüfungen** erhalten den Status `UNSUPPORTED` oder `SKIPPED` und dürfen nicht als bestanden zählen. Sprach-/Werkzeugkompatibilität ist vor Auslieferung real zu validieren.

| Sprache | Vorgesehene Sicherheitsanalyse | Vorgesehene Qualitätsanalyse |
|---|---|---|
| Java | Semgrep, unterstützte Trivy-Dependencyprüfungen | Checkstyle, sichere statische Regeln |
| Python | Semgrep, Bandit, Trivy | Ruff |
| Rust | Semgrep/Regeln, unterstützte Trivy-Dependencies | rustfmt-Check, sichere Regeln; **kein Clippy bei Buildbedarf** |
| Swift | Semgrep/Regeln, unterstützte Trivy-Dependencies | SwiftLint mit ausschließlich vertrauenswürdiger Konfiguration |
| JavaScript/TypeScript | Semgrep, Trivy | ESLint mit fest verwalteten Regeln/Plugins |
| C/C++ | Semgrep/Regeln, sichere statische Prüfungen | Cppcheck, clang-format ohne Projekt-Build |
| C# | Semgrep/Regeln, unterstützte Trivy-Dependencies | source-only Roslyn-/Syntaxregeln ohne Build |
| Go | Semgrep/Regeln, Trivy | gofmt und sichere source-only Regeln, kein Projekt-Build |
| Kotlin | Semgrep/Regeln | detekt mit extern verwalteter Konfiguration, ohne Projekt-Build |
| PHP | Semgrep, unterstützte Trivy-Dependencies | PHP_CodeSniffer mit festem Regelwerk/geeignete source-only Regeln |

**Kein SpotBugs als Source-only-Java-Scanner** (Bytecode nötig). Keine pauschale Aussage, dass jede Schwachstellenklasse in jeder Sprache erfasst wird. Bei einer Toolversion mit nicht erfüllbaren Sicherheitsbedingungen ist ein sicherer Adapterersatz zu wählen oder die betreffende Prüfung transparent als nicht unterstützt zu kennzeichnen; die gesamte Sprache darf dadurch nicht aus dem Pflichtumfang verschwinden.

### 6.1 Sicherheitschecks
SQL-Injection, XSS, statisch erkennbare Zugriffskontrollprobleme, hartcodierte Secrets, Path Traversal, unsichere APIs und Codemuster, Sicherheitsfehlkonfigurationen, bekannte Dependency-Schwachstellen über unterstützte Manifeste/Lockfiles. Grenzen statischer Erkennbarkeit deutlich machen.

### 6.2 Qualitätschecks
Code Smells, komplexe/lange Funktionen, Duplikate wo verfügbar, Formatierung, Naming/Stil, statisch erkennbare Bugs, Wartbarkeits- und Dateimetriken. Scanner-Adapter einheitlich und modular; Mehrsprachenerkennung in einer Analyse.

### 6.3 Gemeinsames Finding-Modell
ID; Job/Scan-ID; Quelle; Zeit; Kategorie; Severity; Regel-ID; Titel; Erklärung; konkrete regelbasierte Abhilfe; Scanner und Version; Sprache; relativer kanonisierter Dateipfad; Zeilen-/Spaltenbereich, soweit vorhanden; Fingerprint für Deduplizierung; optional maskiertes begrenztes Snippet; Hinweis auf Fehlalarm-/Abdeckungsgrenzen. Tool-Rohoutput nur geprüft und sanitisiert übernehmen. Separate Statusmodelle pro Scan, Sprache, Scanner und Prüfung: `COMPLETED`, `UNSUPPORTED`, `FAILED`, `SKIPPED` mit Fehlergrund. Kein „0 Findings“, wenn ein Scanner nicht gelaufen ist.

## 7. Phasen
### Phase 1 – Vollständiger Web-MVP
React/TS-UI, GitHub-/ZIP-Eingabe, sicherer Import/Quarantäne, Antimalware-Vorscan, sichere Extraktion, automatische Spracherkennung, lokale statische Sicherheits- und Qualitätschecks für die zehn Gruppen, Normalisierung/Severity/regelbasierte Hinweise, Ergebnisübersicht und Scannerabdeckung.

### Phase 2 – Erweiterungen
Dependency-CVE-Erkennung über lokal verfügbare Daten; Filter nach Sprache/Tool/Kategorie/Severity/Datei; Codeansicht der **maskierten Fundstellenausschnitte** statt dauerhaft gespeicherter kompletter Dateien; PostgreSQL-Analysehistorie und Vergleiche anhand gespeicherter Metadaten/Fingerprints; lokaler PDF-Export; verbesserte Status-/Fehlerdarstellung. Beide Phasen sind für die **Gesamtabnahme Pflicht**.

## 8. Datenhaltung und Datenschutz
- **Vollständige Repos und hochgeladene ZIPs** ausschließlich temporär verwenden und nach Abschluss, sicherem Abbruch oder Fehler entfernen; verwaiste Arbeitsverzeichnisse beim Neustart/periodisch bereinigen. „Löschen“ ist logische Dateientfernung; physische forensische Unwiederbringlichkeit wird nicht versprochen.
- PostgreSQL persistiert Scans, Quellenmetadaten, erkannte Sprachen, Toolstände, Prüfabdeckung, Findings, Fingerprints und **begrenzte maskierte Fundstellenausschnitte**. Keine kompletten Projektdateien/Archive oder rohe Secrets. Codeansicht historischer Scans zeigt nur gespeicherte Ausschnitte.
- Konfigurierbares Aufbewahrungs-/Löschkonzept für Historie und Berichte. Klare UI-Aktion „Analyse löschen“ einschließlich Findings/Snippets; Datenbank-Backups können nach deren separaten Fristen Daten weiterhin enthalten.
- API-, UI-, Log- und PDF-Ausgaben maskieren Secrets und begrenzen Freitextmengen; niemals Original-Zugangsdaten aus Erkennungsfunden ausgeben.
- Ohne Login **können alle LAN-Teilnehmer mit Zugriff** Scans und Historie sehen: als bewusste Betriebsentscheidung und Risiko in UI/README dokumentieren; sensible private Repos nicht als vermeintlich vertraulich darstellen.

## 9. LAN-Sicherheit ohne Authentifizierung
- Standardmäßig auf dem LAN erreichbar (konfigurierbares Port-Binding), **keine** automatischen Router-/Firewall-/DNS-Änderungen, Reverse-Proxies oder öffentliche Portfreigaben.
- Keine Login- oder Accountoberfläche implementieren. Netzwerksegmentierung/Host-Firewall sind Administratoraufgaben und in der Anleitung zu beschreiben.
- API-Sicherheit ist weiterhin Pflicht: strikte Validierung; Upload-/Request-/Rate-Limits; CORS restriktiv; CSRF-/Cross-Origin-Missbrauch bei zustandsändernden Endpunkten berücksichtigen (insbesondere DNS-Rebinding/Host-Header/Origin); kein unkontrollierter Proxy/URL-Fetch; sichere Datei-/PDF-Ausgabe; klare Fehlercodes ohne interne Pfade/Secrets.
- Backend/PostgreSQL/Scanner-Netze nicht unnötig hostseitig veröffentlichen; Container ohne `privileged`, Docker-Socket oder Host-Network; nach Möglichkeit Nicht-root, read-only/`noexec`-Arbeitsbereiche, Capabilities droppen, CPU/RAM/PID-/Datenträger-/Timeout-Limits.
- Untrusted Parser/Scanner als Angriffsfläche behandeln; Scanner isoliert und möglichst ohne Netzwerk, mit schreibgeschütztem Repository-Mount, vertrauenswürdigen Versionen/Rules. System-/Rule-Updates getrennt durchführen.

## 10. Ressourcenlimits und Konfiguration
Konfigurierbare, **sichere Defaults** für mindestens: ZIP-Uploadgröße, GitHub-Downloadbytes, maximale entpackte Gesamtbytes, einzelne Datei, Dateianzahl, Archivpfadtiefe, Kompressionsverhältnis, Download-/Upload-/Jobtimeout, parallele Scans, CPU, RAM, Prozesse, freier Datenträgerspeicher, Snippet-Zeilen-/Zeichenlänge, Historie-Aufbewahrung. Konkrete Standardwerte in `.env.example`, `README.md` und UI anzeigen und durch Negativtests validieren. Keine unrealistischen Grenzwerte als bereits erprobt darstellen; bei Grenzübertritt **kontrollierter Abbruch**, keine Teil-Ergebnisse als vollständig kennzeichnen.

## 11. Updates
- UI-Schaltfläche „Sicherheitsdaten aktualisieren“, getrennt von Job-Scansteuerung; Versions-/Zeitstempel und Erfolg/Fehler für ClamAV-Signaturen, Trivy-Vulnerability-Daten, Semgrep-/sonstige vertrauenswürdige Regelbestände.
- Updatevorgang explizit vom Nutzer ausgelöst; Downloads nur über fest erlaubte, gesicherte Quellen; Integrität, Versionierung und atomare Übernahme prüfen. Scanner während Updates entweder mit fixiertem Snapshot arbeiten lassen oder Update/Scan gegenseitig ausschließen.
- **Kein automatischer Daten- oder Regel-Download während Analysen.** Analyse nutzt vorhandene lokale Daten; offline ZIP-Analyse möglich, wenn Tools/DBs vorher installiert. Veraltete/fehlende Daten offen markieren; fehlender verpflichtender Malware-Vorscan **blockiert** die Analyse.
- Keine Übertragung des untersuchten Quellcodes an externe Security-/KI-/Cloud-Plattformen.

## 12. Bereitstellung und Projektstruktur
Standard: Docker Compose, dokumentierter einzelner Einstiegspunkt. Java-Backend, React-Frontend, PostgreSQL, Scanner-Laufzeiten und Malware-Engine/DB sicher integrieren; erforderliche Mehrcontainerarchitektur ist wegen Isolation begründet. Reproduzierbare/versionsfixierte Images, `.env.example`, Dockerfile(s), `compose.yaml`, Healthchecks, persistente PostgreSQL-/Signatur-/Regeldaten, temporäres scanbezogenes Storage, Logs und kontrollierte Restart-Policies. **Docker baut RepoGuard, niemals ein importiertes Fremdprojekt.**

Empfohlene Struktur:
```text
repoguard/
├── backend/                 # Java 21 / Spring Boot / API / Orchestrierung
├── frontend/                # React / TypeScript / Designsystem
├── scanner/                 # Nur vertrauenswürdige Adapter/Regeln/Konfigurationen
├── database/                # Schema und Migrationen
├── tests/                   # Ausschließlich Tests von RepoGuard, Test-Repositories nur passiv
├── docs/
├── compose.yaml
├── .env.example
├── README.md
├── SECURITY.md
├── DESIGN.md
├── REQUIREMENTS.md
└── ACCEPTANCE_CRITERIA.md
```
Ports, Volumes und Limits konfigurierbar. Standardports in Compose/README konsistent festlegen; keine fest erfundenen externen Zertifikate/Domains. Deployment auf unterstützter Linux-Docker-Umgebung; **keine** ungeprüfte Raspberry-Pi-/ARM64-Verpflichtung aus den HostNox-Referenzen übertragen.

## 13. Verbindlicher Agenten-basierter Entwicklungsworkflow

**Die gesamte Implementierung von RepoGuard erfolgt ausschließlich durch KI-gestützte Entwicklungsagenten, die innerhalb von Visual Studio Code oder IntelliJ IDEA gesteuert werden.** Der Mensch definiert Anforderungen, formuliert Prompts, überprüft Ergebnisse und erteilt Freigaben; er muss keinen Projektcode manuell entwickeln. Die Agenten sind für Erstellung, Änderung, Refactoring, Tests und technische Dokumentation des Anwendungscodes verantwortlich.

### 13.1 Entwicklungsumgebung und Git
- **Zugelassene Entwicklungsumgebungen:** Visual Studio Code und IntelliJ IDEA. Mindestens eine davon muss als vollständiger Arbeitsweg demonstriert sein; die Wahl des konkreten Agenten-/IDE-Plugins bleibt offen und muss dokumentiert werden.
- **Git als verbindliche Arbeitsgrundlage:** Der komplette RepoGuard-Projektstand liegt in einem versionierten Git-Repository. Die IDE öffnet einen lokalen Checkout dieses Repositories; der Entwicklungsagent arbeitet an genau diesem Projektverzeichnis. Optional verwendete Remote-Repositories dienen zum Synchronisieren und Nachvollziehen der Änderungen.
- Die verbindlichen Dateien `github_analyzer_projektzusammenfassung.md`, `DESIGN.md`, `REQUIREMENTS.md` und `ACCEPTANCE_CRITERIA.md` müssen dem Agenten im Repository zugänglich sein und als Projektkontext berücksichtigt werden. Sie dürfen nicht durch spontane Prompt-Entscheidungen stillschweigend außer Kraft gesetzt werden.
- Änderungen des Agenten müssen als nachvollziehbarer Git-Diff prüfbar sein. Commit-Nachrichten beschreiben die Änderungen; Commits/Pushes erfolgen nach den für das Projekt vereinbarten Freigaben. Kein direktes, undokumentiertes Ändern einer produktiven Installation.

### 13.2 Bedienablauf für die Entwicklung
1. Entwickler öffnet das mit Git verbundene RepoGuard-Projekt in **VS Code oder IntelliJ IDEA**.
2. Entwickler gibt einen natürlichsprachlichen **Prompt direkt an den in die IDE eingebundenen Entwicklungsagenten** (z. B. Implementierung eines abgegrenzten Features mit Verweis auf die vier Referenzdokumente).
3. Agent prüft den Repository-Kontext, plant die Änderung und bearbeitet Dateien im lokalen Git-Arbeitsverzeichnis.
4. Agent führt zulässige Builds und Tests **der RepoGuard-Anwendung selbst** aus; das absolute Verbot, zu analysierende fremde Repositories auszuführen oder zu bauen, gilt weiterhin uneingeschränkt.
5. Entwickler überprüft Git-Diff, Testergebnisse, offene Einschränkungen und Erfüllung der Abnahmekriterien und gibt die Übernahme frei.
6. Änderungen werden nachvollziehbar committet und bei Verwendung eines Remote-Repositories synchronisiert; der nächste Prompt baut auf dem versionierten Projektstand auf.

### 13.3 Anforderungen an Agentenarbeit
- Keine parallel entstehenden, nicht integrierten Projektvarianten als vermeintliches Endergebnis: Das Git-Repository ist **Single Source of Truth** für Implementierung und Referenzdokumente.
- Agenten dürfen keine MUSS-Anforderungen eigenmächtig streichen, fälschlich als erledigt markieren oder Tests erfinden. Bei blockierten Anforderungen: Grund, betroffene Kriterien und vorgeschlagene sichere Lösung dokumentieren.
- Prompts und Umsetzungsentscheidungen sollen für spätere Nachvollziehbarkeit in einer kurzen Entwicklungsdokumentation (z. B. `docs/AGENT_WORKFLOW.md` und `docs/DECISIONS.md`) festgehalten werden, ohne vertrauliche Tokens oder Zugangsdaten zu speichern.
- Der Agent kann Quellcode generieren und die **eigene** Anwendung bauen/testen. Er darf weder für Scanner noch im Entwicklungsprozess Code aus **importierten Ziel-Repositories** ausführen.
- Keine bestimmte kostenpflichtige Agentenplattform, Cloud-Anbindung oder IDE-Erweiterung ist vorgeschrieben; die agentenbasierte Arbeitsweise ist verbindlich, nicht ein konkreter Anbieter.

## 14. Entwicklungsqualität, Tests und Dokumentation
- API-Design, Datenmodell/Migrationen, Job-/Scanner-State-Machine und Löschkonzept dokumentieren.
- Automatisierte Unit-, Integration-, API-, Security- und Browser-E2E-Tests der **Analyzer-Software**, mit sicheren nicht ausführbaren Test-Fixtures; keine Tests/Builds der importierten Projekte.
- Negativfälle: falsch geformte GitHub-Hosts/URLs, alternative Protokolle/Ports/Userinfo, DNS-Rebinding/interne Ziele, unsichere Redirects, HTTP-Abbrüche, manipulierte ZIPs/Traversal/Symlinks/Bombs, Malware-Vorscan-Ausfall, Scannerabsturz/-Timeout/-Netzwerkblock, gefährliche projektlokale Scannerkonfiguration, Secrets in UI/DB/PDF, Datenbereinigung, Cross-Origin-API-Aufruf.
- README deutsch und englisch oder deutsch mit vollständigen englischen UI-Hilfen; Installations-, Update-, Backup-/Restore-, Offline-, Betriebs-, Troubleshooting- und Sicherheitshinweise, Nutzungs- und Lizenzentscheidungen.
- `TEST_REPORT.md` + `test-results.json`; Dokumentation des Agenten-/Git-Workflows, echte Messdaten und Ausführung/Umgebung kennzeichnen; keinen PASS ohne Beleg behaupten.
- Quellcode, Compose, Agenten-/Git-Workflow-Dokumentation und echte Testergebnisse als übergabefähiges Projekt; keine bloße theoretische Architektur.

## 15. Definition of Done
RepoGuard ist erst fertig, wenn alle **MUSS**-Kriterien in `ACCEPTANCE_CRITERIA.md` mit **PASS** und Nachweis abgeschlossen sind; beide Phasen und zehn Sprachen real funktionieren; keine fremde Codeausführung möglich ist; das Import-/Malware-/Scanner-Isolationsmodell nach Negativtests funktioniert; vollständige Fremddateien bereinigt werden; UI/DB/Export konsistent und secretsicher sind; Compose reproduzierbar startet, der Agenten-Workflow in VS Code oder IntelliJ IDEA über ein Git-Repository praktisch nachgewiesen ist und Dokumentation vollständig ist. Bekannte kritische/hohe ungeklärte Sicherheitslücken verhindern eine Freigabe.
