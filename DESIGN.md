# RepoGuard – DESIGN.md
**Version:** 1.0 · **Status:** Verbindliche Design- und UX-Spezifikation · **Stand:** 21.10.2026

## 1. Zweck und Geltungsbereich
RepoGuard ist eine lokal betriebene, browserbasierte Anwendung zur **rein statischen** Sicherheits- und Qualitätsanalyse öffentlicher GitHub-Repositories und hochgeladener ZIP-Projekte. Diese Datei legt Produktidentität, Nutzerführung, visuelle Gestaltung und die Darstellung fachlicher Ergebnisse fest. Funktionale und sicherheitstechnische Vorgaben stehen in `REQUIREMENTS.md`, die überprüfbaren Kriterien in `ACCEPTANCE_CRITERIA.md`. Die ursprüngliche `github_analyzer_projektzusammenfassung.md` ist die fachliche Ausgangsbasis; bei abweichenden älteren HostNox-Vorlagen gelten ausschließlich RepoGuard-Vorgaben.

**Leitprinzip:** „Repository verstehen, bevor es ausgeführt wird.“ Die Anwendung darf **niemals** suggerieren, dass sie Repository-Code zur Untersuchung startet oder Malwarefreiheit garantiert.

## 2. Produktidentität
- **Name:** RepoGuard; konsistent in Browser-Titel, Dashboard, Berichten, Fehlermeldungen, Dokumentation und Compose-Projektnamen.
- **Produktbeschreibung:** Lokaler GitHub Repository Security & Code Quality Analyzer.
- **Kurzer UI-Claim:** „Prüfe Code, bevor du ihn ausführst.“ / “Inspect code before you run it.”
- **Logo:** Eigenständiges, reduziertes Schild-/Repository-Motiv als lokales SVG; monochrome Version für Light/Dark; eigenes Favicon; keine Übernahme des GitHub-Logos oder irreführende GitHub-Zugehörigkeit.
- **Branding:** „GitHub-inspiriert“ bedeutet zurückhaltende Entwicklerwerkzeug-Ästhetik, klare Tabellen, Code-Typografie und vertraute Interaktionsmuster, **keine** Kopie des GitHub-Designs oder seiner Markenzeichen.

## 3. Designsystem
### 3.1 Gestaltungsprinzipien
Modern, minimalistisch, professionell und informationszentriert. Großzügige Abstände, eindeutige Hierarchie, reduzierte visuelle Effekte und hohe Datendichte, wo sinnvoll. Bevorzugt neutrale Oberflächen mit sparsamen Blau-/Türkis-Akzenten; Schweregrade erhalten separate semantische Farben und Textlabels. Keine dekorativen Hacker-/Matrix-Effekte, Animationen, die Scanprozesse vortäuschen, oder unleserlich kleine Tabellen.

### 3.2 Farb-Tokens als Ausgangspunkt
Die Werte sind **Implementierungsvorgaben als Startpalette**, nicht aus der fachlichen Projektzusammenfassung abgeleitete Markenfarben. Bei nachgewiesenen Kontrastproblemen müssen sie angepasst und dokumentiert werden.

| Token | Light | Dark | Einsatz |
|---|---|---|---|
| `bg.canvas` | `#F6F8FA` | `#0D1117` | Seitenhintergrund |
| `bg.surface` | `#FFFFFF` | `#161B22` | Karten, Tabellen, Dialoge |
| `bg.subtle` | `#F0F3F6` | `#21262D` | Hover, Sekundärflächen |
| `text.primary` | `#1F2328` | `#E6EDF3` | Haupttext |
| `text.secondary` | `#59636E` | `#9DA7B3` | Metadaten |
| `border.default` | `#D0D7DE` | `#30363D` | Grenzen |
| `accent` | `#0969DA` | `#58A6FF` | Primäraktionen, Links |
| `accent.surface` | `#DDF4FF` | `#122B45` | Markierungen |
| `success` | `#1A7F37` | `#3FB950` | Erfolgreich |
| `warning` | `#9A6700` | `#D29922` | Warnung |
| `danger` | `#CF222E` | `#F85149` | Fehler/Kritisch |

**Schweregrade:** `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFO` jeweils mit farbunabhängigem Label und Symbol. `UNKNOWN` für nicht klassifizierbare Ergebnisse. **Prüfstatus:** `COMPLETED`, `UNSUPPORTED`, `FAILED`, `SKIPPED` als eigene Dimension; ein nicht ausgeführter Scanner darf **nicht** als „0 Findings“ ausgegeben werden.

### 3.3 Typografie und Komponenten
- UI: lokal ausgelieferte System-Sans-Serif-Schrift bzw. lizenzgerecht lokal gebündelte Schrift. Code, Regeln, Hashes, Pfade und Zeilennummern: Monospace.
- Einheitliche Komponenten: Button, Textfeld, Dropzone, Schrittanzeige, Progressbar, Badge/Statuslabel, Alert, Tabellen, Filter, Tabs, Dialoge, Toasts, Tooltip, Codeansicht und Download-Aktion.
- Definierte Zustände für Laden, Leer, Fehler, teilweise abgeschlossen, Erfolg; keine funktionslosen Demo-Schaltflächen.
- Icons stets mit verständlichem Text/Accessible-Name; Icons dürfen Semantik nicht allein tragen.

## 4. Layout und Navigation
### 4.1 Desktop
Kompakte linke Navigation und Kopfzeile mit Produktname, aktueller Ansicht, Sprachumschaltung (DE/EN), Theme-Umschaltung und Anzeige „Lokale Analyse“. Inhalt zentriert, breite Ergebnis-/Codetabellen dürfen den Platz nutzen.

**Hauptansichten:**
1. **Dashboard** – Scan starten, letzte Scans, Kennzahlen und Warnhinweise.
2. **Neue Analyse** – GitHub-URL **oder** ZIP-Upload, sichere Eingabehinweise, Start.
3. **Analysen** – Historie mit Suche, Sortierung, Datum, Status und Vergleich.
4. **Analysebericht** – Gesamtüberblick, Findings, Coverage, betroffene Dateien, Codeausschnitte und PDF.
5. **Einstellungen** – konfigurierte Limits, Update-Status, manuelle Datenupdates, Sprache und Theme.
6. **Über / Hilfe** – Grenzen statischer Analyse, Datenaufbewahrung und System-/Scanner-Versionen.

### 4.2 Mobil und Tablet
Responsive nutzbar bei 375, 768 und 1440 CSS-Pixeln; bei schmalen Ansichten Seitenleiste als Menü, Tabellen als gut zugängliche gestapelte Einträge oder horizontal scrollbarer Datenteil **ohne** abgeschnittene Aktionen. Kernfunktionen tastaturbedienbar. Die Codeansicht darf horizontal scrollen; globale Seitenüberläufe sind zu vermeiden.

## 5. End-to-End-Nutzerführung
### 5.1 Import
Zwei eindeutig getrennte Eingabewege: **„GitHub-Repository“** und **„ZIP-Datei“**. Bei GitHub nur öffentliche HTTPS-Repository-Links im erlaubten Format; keine frei wählbaren Download-URLs oder Token-Eingaben. Vor dem Start muss angezeigt werden, dass GitHub-Import eine Netzwerkverbindung benötigt, während die eigentliche Analyse lokal erfolgt.

Beim ZIP-Upload Dateiname und erlaubtes Format sichtbar. Upload-Fortschritt, maximale konfigurierte Größe und eindeutige Servervalidierungsfehler zeigen. Keinerlei visuelle Option für Build, Test, Ausführen, Deployment oder dynamische Analyse anbieten.

### 5.2 Scan-Verlauf
Deterministische Phasenanzeige: `Eingabe prüfen → Import/Quarantäne → Malware-Vorscan → Sichere Extraktion → Sprachen erkennen → Statische Scanner → Ergebnisse aufbereiten → Temporäre Dateien löschen → Bericht bereit`. Jede Phase hat Status, verständliche Fehlerursache und (wo messbar) Fortschritt; Prozentangaben nicht erfinden. Während Scanner laufen keine automatischen Update-Downloads.

Malwarefund, ungültige URL, Redirect-Verletzung, Archivbombe, Scannerfehler und Ressourcenlimit-Überschreitung benötigen **unterschiedliche** Meldungen. Bei gesperrten Imports dürfen keine Findings als vollständige Codeanalyse dargestellt werden.

### 5.3 Scan-Dashboard
Oberste Ebene: Scanquelle (sanitisiert), Zeit, Scanstatus, erkannte Sprachen, Anzahl der Findings nach Schweregrad, Scannerabdeckung und klarer Hinweis „Statische Analyse – kein Nachweis von Sicherheit oder Malwarefreiheit“. Zeige die Anzahl erfolgreich, fehlgeschlagen, nicht unterstützt und übersprungen ausgeführter Prüfungen getrennt an. Ein sicherer Fehlerzustand kann als „Analyse unvollständig“ erscheinen.

### 5.4 Findings
Tabelle mit mindestens: Schweregrad, Kategorie (Security/Quality/Dependency/Secret/Configuration), Regel-ID, Kurztext, Sprache, Tool, Dateipfad, Zeile (falls vorhanden), Status. Filter nach Schweregrad, Sprache, Tool, Kategorie, Datei; Suche und Sortierung. Detailansicht: Erklärung, technische Einordnung, regelbasierter Abhilfehinweis, Dateipfad/Zeilenbezug, Quelltool/Regel und ggf. Grenzen/Falschpositiv-Hinweis. Doppelte Scannerfunde können gruppiert werden, ohne Herkunft zu verbergen.

### 5.5 Quellcodeansicht und Historie
**Vollständige fremde Repository-Dateien werden nach Abschluss gelöscht.** In Historienberichten dürfen ausschließlich zu Findings gehörende **begrenzte, sicher maskierte Codeausschnitte** plus Zeilennummern und Metadaten erscheinen. Kein vollständiger Dateibrowser für alte Scans. Bei Findings ohne speicherbaren Ausschnitt wird dies sichtbar erklärt. Secrets, Tokens und Zugangsdaten sind in Vorschau, Logs, Exporten und Datenbank-Snippets zu maskieren; niemals durch „Kopieren“ oder PDFs rekonstruierbar machen. Repository-Dateipfade sind nur Text und dürfen keine Navigations-/Ausführungsaktion auslösen.

### 5.6 PDF
PDF lokal im Backend erzeugen. Bericht mit Quelle, Datum, Versionen, Findings, Abdeckung, Einschränkungen, Risikoeinstufung, maskierten Fundstellenausschnitten, Filter-/Scanmetadaten und Angabe „Kein Build, keine Ausführung“. Keine Rohgeheimnisse, kein aktiver Inhalt aus dem Repository im PDF. Bei Teilfehlern klare Warnung auf Titelseite/Zusammenfassung.

## 6. Sprache, Theme und Barrierefreiheit
- Oberfläche **Deutsch und Englisch**, manuell umschaltbar, inklusive aller Kernaktionen, Fehler, Prüfergebnisse, PDF-Beschriftungen und Hilfe. Technische Regel-IDs/Originalregeln bleiben erhalten; Erklärungen werden nur übersetzt, wenn fachlich korrekt.
- **Light und Dark Mode** manuell umschaltbar, optional „Systemeinstellung“; Auswahl lokal speichern, ohne Cloudkonto. Keine Bildschirmbereiche mit inkonsistentem Theme.
- WCAG 2.2 AA als Ziel: passende Kontraste, sichtbare Fokusmarkierung, logische Tastaturreihenfolge, Labels, Screenreader-Statusmeldungen, keine reine Farbcodierung, `prefers-reduced-motion`.
- Keine externen CDN-, Font-, Tracking- oder Designservices; sämtliche UI-Ressourcen lokal bereitstellen.

## 7. Vertrauensgrenze und Nutzerhinweise
Da die Oberfläche **ohne Login standardmäßig im LAN erreichbar** sein soll, muss in der UI und Installationshilfe deutlich stehen: Jeder mit Netzwerkzugriff kann Scans starten und gespeicherte Berichte einschließlich maskierter Ausschnitte einsehen. Standardinstallation darf keine Router-Portfreigaben, öffentlich erreichbaren Bindings außerhalb des konfigurierten privaten Netzbereichs oder automatisches Internet-Publishing einrichten. UI-Hinweis ist kein Ersatz für Server-Sicherheitskontrollen: Limits, Uploadprüfungen, CORS/CSRF-nahe Schutzmaßnahmen, restriktive API und Ausführungsisolierung sind Pflicht.

## 8. Design-Deliverables
- Implementierte, tatsächlich mit der REST-API verbundene responsive React-/TypeScript-Oberfläche.
- Wiederverwendbare und dokumentierte Komponenten/Tokens; lokales RepoGuard-Logo und Favicon (SVG).
- Vollständiger Light-/Dark- und DE-/EN-Modus.
- Realistische Empty/Error/Partial-Coverage-Zustände; keine Mockups als Endergebnis.
- Dokumentierte Ansichten und Tests gemäß `ACCEPTANCE_CRITERIA.md`.

## 9. Designentwicklung über IDE-Agenten und Git
Die Gestaltung wird **ausschließlich über Entwicklungsagenten in Visual Studio Code oder IntelliJ IDEA** umgesetzt. Der Entwickler erteilt Gestaltungs- und Umsetzungsaufträge als Prompts direkt im IDE-Agenten, während das RepoGuard-Projekt über einen Git-Checkout geöffnet ist. Der Agent nutzt diese `DESIGN.md` sowie `REQUIREMENTS.md` und `ACCEPTANCE_CRITERIA.md` als verbindlichen Kontext, setzt UI-Komponenten im versionierten Projekt um und stellt seine Änderungen als prüfbare Git-Diffs bereit. Ein unveränderter Screenshot, Design-Mockup oder Chat-Entwurf ersetzt keine tatsächliche Integration in Frontend und Backend.

Für UI-bezogene Agentenaufträge ist mindestens festzuhalten: gewünschte Ansicht/Interaktion, betroffene Anforderungen, Änderungen an Design-Tokens oder Komponenten, nachprüfbarer Build-/Teststatus und ggf. verbleibende Abweichungen. Die Auswahl des konkreten Agenten-Plugins bleibt offen.

## 10. Verbindliche Designabnahme
Gestaltung und Verhalten gelten erst als erfüllt, wenn der gesamte Import-Scan-Bericht-Export-Workflow ohne Terminal im Browser funktioniert, alle genannten Zustände auf echten Backend-Daten beruhen, alte Historie **keinen Vollquelltext** zeigt und keine Secrets offenlegt. Eine reine grafische Vorschau gilt nicht als Umsetzung.
