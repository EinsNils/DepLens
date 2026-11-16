# DepLens – Sicherheitsprüfung (Phase 2) – Design

Datum: 2026-11-16
Status: Entwurf zur Review
Baut auf: `2026-11-16-deplens-dependency-explorer-design.md` (Phase 1)

## Ziel

Für jede Dependency eines Moduls zeigt DepLens, ob die verwendete Version bekannte
Sicherheitslücken hat, und schlägt zwei Upgrades vor:

1. die **nächste sichere Version mit gleicher Major-Version** (kleinster Sprung),
2. die **neueste sichere Version** insgesamt.

„Sicher“ heißt: keine bekannte Lücke laut den abgefragten Quellen. Vorabversionen werden nie
vorgeschlagen.

## Rahmenbedingungen

| Thema | Entscheidung |
|---|---|
| Versionsquelle | Maven Central, `https://repo1.maven.org/maven2/<group/als/pfad>/<artifact>/maven-metadata.xml` |
| Lückenquelle 1 | OSV.dev, `POST https://api.osv.dev/v1/query` mit `{"package":{"name":"<g>:<a>","ecosystem":"Maven"}}` (ohne Version, kein Account; bei `next_page_token` weiterblättern) |
| Lückenquelle 2 (optional) | Sonatype OSS Index, `POST https://ossindex.sonatype.org/api/v3/component-report`, Basic Auth (Benutzer + API-Token; ohne Auth antwortet der Dienst mit 401), Coordinates `pkg:maven/<g>/<a>@<v>`, max. 128 pro Anfrage |
| mvnrepository.com | nur als Link `https://mvnrepository.com/artifact/<g>/<a>/<v>`, kein Abruf (keine API, Cloudflare) |
| Wann | automatisch beim Laden eines Moduls; Cache 24 h (einstellbar); Reload-Icon leert den Cache des Moduls |
| HTTP | IntelliJ `com.intellij.util.io.HttpRequests` (IDE-Proxy und Zertifikate) |
| Versionsvergleich | `org.apache.maven:maven-artifact` (`ComparableVersion`) |
| JSON | `com.google.code.gson:gson` als eigene Abhängigkeit |
| Datenschutz | Es werden nur Koordinaten (`group:artifact[:version]`) gesendet; Prüfung in den Einstellungen abschaltbar |

## Fachliche Regeln

**Betroffen (OSV):** Eine Version V ist von einer Lücke betroffen, wenn für ein
`affected`-Element des Pakets gilt:
- V steht in der expliziten Liste `versions`, **oder**
- V liegt in einem `ranges`-Eintrag vom Typ `ECOSYSTEM`: Die Events werden in Reihenfolge
  ausgewertet; `introduced: X` öffnet einen Bereich ab X (inklusive, `"0"` = von Anfang an),
  `fixed: Y` schließt ihn vor Y (Y selbst ist nicht betroffen), `last_affected: Z` schließt ihn
  nach Z (Z ist betroffen). Ein offener Bereich ohne Ende gilt bis unendlich.

**Betroffen (OSS Index):** Die Antwort nennt die Lücken je angefragter Version direkt.

**Zusammenführen:** Lücken beider Quellen werden über ihre IDs und Aliase dedupliziert
(z. B. `GHSA-…` mit Alias `CVE-2022-42003` = OSS-Index-Eintrag `CVE-2022-42003`).

**Schweregrad:** OSV `database_specific.severity` (`CRITICAL`, `HIGH`, `MODERATE`, `LOW`), sonst
aus dem CVSS-Score abgeleitet (≥ 9 kritisch, ≥ 7 hoch, ≥ 4 mittel, sonst niedrig), sonst
„unbekannt“. Anzeige deutsch: KRITISCH, HOCH, MITTEL, NIEDRIG, UNBEKANNT.

**Behoben in:** kleinste `fixed`-Version des Bereichs, der die aktuelle Version enthält; sonst leer.

**Vorabversion:** Version mit Qualifier `alpha`, `beta`, `milestone`/`M<n>`, `rc`/`cr`,
`snapshot`, `preview`, `ea`, `dev` (ohne Groß-/Kleinschreibung, auch mit `-`/`.` getrennt).

**Major:** erstes numerisches Segment der Version.

**Upgrade-Vorschläge:** Kandidaten sind alle Nicht-Vorabversionen aus `maven-metadata.xml`,
die größer als die aktuelle Version sind und von keiner OSV-Lücke betroffen sind.
- *Gleiche Major:* kleinster Kandidat mit derselben Major-Version.
- *Neueste:* größter Kandidat.
- Ist OSS Index aktiv, werden beide Vorschläge dort gegengeprüft (eine Batch-Anfrage); meldet OSS
  Index eine Lücke, rückt der jeweilige Vorschlag zum nächsten Kandidaten weiter (max. 5 Versuche,
  danach kein Vorschlag).
- Fallen beide Vorschläge auf dieselbe Version, wird sie einmal angezeigt.

**Status pro Dependency (`SecurityReport`):**
- `SICHER` – geprüft, keine Lücke.
- `VERWUNDBAR` – mindestens eine Lücke.
- `NICHT_PRÜFBAR` – Projekt-Modul oder kein Artefakt auf Maven Central und keine OSV-Daten.
- `FEHLER` – Abfrage fehlgeschlagen (Netz, 5xx, 429 nach Wiederholung).
- Solange noch geprüft wird: kein Status (keine Markierung).

## Komponenten

Paket `io.github.einsnils.deplens.security`.

| Komponente | Verantwortung | Kennt IntelliJ? |
|---|---|---|
| `MavenVersion` | Vergleich (`ComparableVersion`), Vorabversion, Major | nein |
| `Vulnerability` / `AffectedRange` | Lücke: ID, Aliase, Schweregrad, Kurztext, Bereiche, explizite Versionen, Link | nein |
| `OsvParser` | OSV-JSON → `List<Vulnerability>` + `nextPageToken` | nein |
| `OssIndexParser` | OSS-Index-JSON → Lücken je Koordinate | nein |
| `MavenMetadataParser` | `maven-metadata.xml` → Versionsliste | nein |
| `VulnerabilityMatcher` | Ist Version V von Lücke L betroffen? Behoben-in-Version | nein |
| `UpgradeAdvisor` | Kandidaten → zwei Vorschläge (mit optionaler Gegenprüfung) | nein |
| `SecurityReport` | Ergebnis je `group:artifact:version` | nein |
| `SecurityClient` (Interface) + `HttpSecurityClient` | Rohantworten der drei Dienste holen | Interface nein, Implementierung ja |
| `SecurityCache` | Rohantworten als Dateien unter `PathManager.getSystemPath()/deplens/cache`, mit Zeitstempel; abgelaufen/kaputt → verwerfen | ja (Pfad) |
| `SecurityChecker` | Orchestrierung für eine Liste von Artefakten: Cache → Client → Parser → Matcher → Advisor; parallel (max. 8), meldet Teilergebnisse über Callback, abbrechbar | nein (bekommt Client, Cache-Schnittstelle, Executor) |
| `SecurityService` | Projekt-Service; verbindet `SecurityChecker` mit Einstellungen, Hintergrund-Threads und UI | ja |
| `DepLensSettings` + `DepLensConfigurable` | Prüfung an/aus, Cache-Dauer (h), OSS-Index-Benutzer; Token in `PasswordSafe` | ja |

UI-Erweiterungen in `io.github.einsnils.deplens.ui`:
- `CatalogTreeCellRenderer` / `NodeText`: Status-Markierung je Eintrag und Kategorie.
- `SecurityPanel`: Inhalt des Tabs „Sicherheit“.
- `DepLensPanel`: Reload-Icon, Statuszeile, Filter „Nur verwundbare“, Tabs unten.

`DependencyCatalog`, `DependencyGraph` und die Gradle-Anbindung bleiben unverändert.

## Datenfluss

1. Modul geladen → `DependencyCatalog` wie bisher.
2. `SecurityService.check(einträge, request)` im Hintergrund; Projekt-Module sofort `NICHT_PRÜFBAR`.
3. Je eindeutigem `group:artifact`: OSV (alle Seiten) und `maven-metadata.xml` aus Cache oder Netz.
4. Optional: eine OSS-Index-Batch-Anfrage für alle aktuellen Versionen (in 128er-Blöcken), danach
   eine für die Vorschlags-Kandidaten.
5. Je Eintrag `SecurityReport`; Callback auf dem EDT, nur wenn `RequestSequencer.isCurrent(request)`.
6. Die UI hält `Map<Koordinate, SecurityReport>` und rendert neu.

## UI

**Toolbar:** `[Modul ▼] [Suche] [☐ Nur verwundbare] [⟳ Gradle] [🛡 Sicherheit neu prüfen]`
- „Sicherheit neu prüfen“ leert den Cache für alle Artefakte des Moduls und prüft neu.
- Unter der Toolbar eine Statuszeile, nur während/nach Problemen sichtbar:
  „Sicherheit wird geprüft … 34/120“, danach z. B. „5 Prüfungen fehlgeschlagen – erneut versuchen“
  (Link) oder „OSS-Index-Token ungültig – nur OSV aktiv“.

**Kategorie-Liste:**
- Eintrag: `⛔ 3` (rot) bei `VERWUNDBAR`, `✔` (grün, dezent) bei `SICHER`, `?` (grau) bei
  `NICHT_PRÜFBAR`/`FEHLER` mit Tooltip-Grund.
- Kategorie: `⛔` wenn mindestens ein Eintrag verwundbar ist.
- „Nur verwundbare“ blendet alle Einträge ohne `VERWUNDBAR` und leere Kategorien aus.

**Unterer Bereich (bei Auswahl):** Tabs „Abhängigkeitspfad“ und „Sicherheit (N)“; bei
`VERWUNDBAR` wird „Sicherheit“ automatisch gewählt.

Tab „Sicherheit“:
```
jackson-databind 2.13.0   ⛔ 3 Lücken
Upgrade:  2.13.5 (gleiche Major)  ·  2.19.0 (neueste sichere)
Wird über spring-boot-starter-json eingezogen – Upgrade dort oder per Gradle-Constraint.
↗ auf mvnrepository öffnen   ↗ auf osv.dev öffnen

Schweregrad  ID               Beschreibung                 Behoben in
KRITISCH     CVE-2022-42003   Deep wrapper array …         2.13.4.2
HOCH         GHSA-…           …                            2.13.5
```
- Tabelle sortiert nach Schweregrad (absteigend), dann ID. Angezeigt wird die CVE-ID, falls
  vorhanden, sonst die eigene ID. Doppelklick bzw. Klick auf die ID öffnet die Lücke im Browser
  (OSV: `https://osv.dev/vulnerability/<id>`, OSS Index: deren Referenz-URL).
- Hinweis „Wird über … eingezogen“ nur, wenn kein Vorkommen direkt am Modul hängt; genannt wird
  der direkte Parent des ersten Vorkommens.
- Keine Vorschläge möglich → „Keine Version ohne bekannte Lücken verfügbar“.
- `SICHER` → „Keine bekannten Lücken“ plus mvnrepository-Link.

**Einstellungen** (*Settings → Tools → DepLens*): Sicherheitsprüfung an/aus (Standard an),
Cache-Dauer in Stunden (Standard 24), OSS-Index-Benutzer, OSS-Index-Token (PasswordSafe).
Ist die Prüfung aus, verschwinden Markierungen, Filter, Reload-Icon und Tab „Sicherheit“.

## Fehlerbehandlung

| Fall | Verhalten |
|---|---|
| Netz weg / Timeout / 5xx | Eintrag `FEHLER` (`?`, Tooltip mit Grund); andere laufen weiter; Statuszeile mit „erneut versuchen“ |
| 429 | einmal nach 2 s wiederholen, sonst `FEHLER` |
| `maven-metadata.xml` 404 | OSV-Lücken trotzdem anzeigen, keine Vorschläge; ohne OSV-Daten → `NICHT_PRÜFBAR` |
| OSS Index 401/403 | einmal Hinweis in der Statuszeile, für den Rest der Sitzung nur OSV |
| Ungültiges JSON/XML | wie Netzfehler; Cache-Eintrag wird nicht geschrieben |
| Cache-Datei kaputt/abgelaufen | verwerfen, neu laden |
| Modulwechsel/Reload während Prüfung | laufende Prüfung abbrechen, Ergebnisse verwerfen (`RequestSequencer`) |
| Prüfung in Einstellungen aus | keine Netzanfragen |

Keine Dialoge; Logging über `Logger` (Warnungen ohne Stacktrace-Flut: je Dienst und Fehlerart
einmal pro Prüfung).

## Tests

JUnit 5 + AssertJ, test-first, ohne Netz.
- `MavenVersion`: `2.13.4 < 2.13.4.2 < 2.13.5`, `2.0.0-RC1 < 2.0.0`, Vorabversionen
  (`-RC1`, `-M2`, `.Beta1`, `-SNAPSHOT`, `-alpha`), Major.
- `VulnerabilityMatcher`: `introduced`/`fixed`, `introduced: "0"`, `last_affected`, mehrere
  Bereiche, offener Bereich, explizite Versionsliste, Grenzen (genau `fixed` sicher, genau
  `introduced` betroffen), Behoben-in.
- `UpgradeAdvisor`: gleiche Major vorhanden / nicht vorhanden, Vorabversionen übersprungen,
  keine sichere Version, aktuelle Version schon sicher, beide Vorschläge gleich, Gegenprüfung lehnt
  Kandidaten ab → nächster, max. 5 Versuche.
- Parser: echte gespeicherte Antworten als Test-Ressourcen (OSV jackson-databind, maven-metadata.xml
  jackson-databind, OSS-Index-Beispielantwort), Paging-Token, Severity-Ableitung aus CVSS.
- `SecurityChecker` mit Fake-Client und In-Memory-Cache: Cache-Treffer ohne Netz, Teilfehler,
  429-Wiederholung, Abbruch, Dedupe OSV + OSS Index.
- `SecurityCache`: Ablauf, kaputte Datei.
- UI: manuelle Checkliste mit `runIde` gegen `samples/demo` (enthält bereits
  `jackson-databind:2.13.0`).

## Nicht im Umfang

- Build-Skripte automatisch ändern (Upgrade anwenden)
- andere Repositories als Maven Central, private Registries
- Benachrichtigungen/Inspections außerhalb des Tool-Windows
- Lizenzprüfung
