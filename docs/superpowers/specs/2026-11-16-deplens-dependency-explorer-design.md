# DepLens – Dependency Explorer (Phase 1) – Design

Datum: 2026-11-16
Status: Entwurf zur Review

## Ziel

Ein IntelliJ-Plugin mit einem andockbaren Tool-Window (links/rechts), in dem man für ein
Gradle-Modul nach einer Dependency sucht und sofort sieht:

- **wo** sie vorkommt (Configuration, Pfad),
- **wer** sie reinzieht (Parents bis zum Modul),
- **was** sie selbst mitbringt (Children).

Der Suchtreffer wird farblich hervorgehoben.

Phase 2 (nicht Teil dieser Spec): Anbindung an mvnrepository bzw. eine Vulnerability-Quelle, um
neuere Versionen ohne bekannte Schwachstellen anzuzeigen. Das Datenmodell aus Phase 1 soll
dafür als Basis dienen.

## Rahmenbedingungen

| Thema | Entscheidung |
|---|---|
| Analysierte Projekte | Gradle |
| Plugin-Sprache | Java 25 (die Plattform 2026.2 ist mit Java 25 kompiliert) |
| Build | Gradle (Kotlin DSL), IntelliJ Platform Gradle Plugin 2.x |
| Ziel-IDE | IntelliJ IDEA 2026.2 (`sinceBuild = 262`, kein `untilBuild`) |
| Abhängigkeit | Bundled Plugin `com.intellij.gradle` |
| Verbreitung | Eigene Nutzung, kein Marketplace |

## Datenquelle

Wir nutzen IntelliJs eingebauten Dependency Analyzer über die öffentliche Plattform-API
(geprüft in IntelliJ IDEA 2026.2.3, keine `@ApiStatus`-Markierung):

- `DependencyAnalyzerExtension.EP_NAME` → Extension mit `isApplicable(GradleConstants.SYSTEM_ID)`
  → `createContributor(project, parentDisposable)` liefert einen `DependencyAnalyzerContributor`.
- `getProjects()` → Module (`DependencyAnalyzerProject`, mit `getModule()` / `getTitle()`).
- `getDependencyScopes(project)` → Gradle-Configurations.
- `getDependencies(project)` → flache Liste von `DependencyAnalyzerDependency` mit
  `getData()` (`Artifact`: groupId/artifactId/version, oder `Module`: name), `getScope()`,
  `getParent()` und `getStatus()`.
- `Status.Omitted` wird vom Contributor in 2026.2 nicht gesendet; ein wiederholter Unterbaum kommt als
  kinderloses Blatt an. `(*)` erkennt DepLens daher selbst: kinderloser Artefakt-Knoten, dessen
  Koordinaten in derselben Configuration an einem Knoten mit Kindern vorkommen. Ein evtl. gesendetes
  `Status.Omitted` wird weiterhin ausgewertet.
- `Status.Warning` = Versionskonflikt (angezeigt wird IntelliJs Text „conflict with <Version>“) oder
  nicht auflösbare Dependency.
- Gradle-Fehler schluckt der Contributor (nur Log) und liefert eine leere Liste; DepLens zeigt das
  als Fehlermeldung mit „Erneut versuchen“.
- `whenDataChanged(listener, disposable)` → Neuladen nach Gradle-Sync.

**Bekannte Einschränkung:** Ein Knoten trägt nur die *aufgelöste* Version. Bei Konflikten
gibt es eine Warning mit der konkurrierenden Version (z. B. „conflict with 2.13.0“),
aber nicht, welcher Parent welche Version angefordert hat. Die Anzeige ist daher
„2.15.2 ⚠ Konflikt: conflict with 2.13.0“, nicht `2.13.0 → 2.15.2` pro Pfad.
Falls das später nötig ist, kann ein eigenes Gradle-Init-Script ergänzt werden.

`getDependencies` kann einen Gradle-Lauf auslösen und wird daher nie auf dem EDT aufgerufen.

## UI

```
┌─ Tool Window "DepLens" ─────────────────────────────────────────┐
│ [Modul ▼]  [🔍 Suche                        ]  [⟳ Gradle]      │
│ Alle Dependencies (42)                                          │
│   ▸ Apache  3                                                   │
│   ▾ Jackson  3                                                  │
│       jackson-core  2.19.0   com.fasterxml.jackson.core         │
│       jackson-databind  2.19.0   com.fasterxml.jackson.core  ⚠  │
│   ▾ Spring  5                                                   │
│       spring-boot-starter-web  3.5.0   org.springframework.boot │
│       springdoc-openapi-starter-webmvc-ui  2.8.0   org.springdoc│
│   ▸ Projekt-Module  1                                           │
│ ─────────────────────────────────────────────────────────────── │
│ Abhängigkeitspfad          (erst sichtbar, wenn etwas gewählt)  │
│   ▾ :app (runtimeClasspath)                                     │
│     ▾ spring-boot-starter-web                                   │
│       ▾ spring-boot-starter-json                                │
│         ▾ ● jackson-databind 2.19.0   (hervorgehoben)           │
│             ▸ jackson-core                                      │
│   ▾ :app (testRuntimeClasspath)                                 │
│     ▾ ● jackson-databind 2.19.0                                 │
└─────────────────────────────────────────────────────────────────┘
```

- **Modulauswahl:** eine ComboBox, genau ein Modul.
- **Liste nach Hersteller:** jede Dependency (`group:artifact:version`) genau einmal, gruppiert nach
  Hersteller (`VendorCategory`: Tabelle für Spring, Jackson, JUnit, Kotlin; sonst das Segment nach
  `org.`/`com.`/`io.` … der groupId). Kategorien alphabetisch, „Projekt-Module“ zuletzt, anfangs
  zugeklappt. Überschrift „Alle Dependencies (N)“ bzw. „Treffer (N)“.
- **Suche:** Teilstring (case-insensitive) auf `group:artifact:version`, Debounce ~300 ms. Leere
  Kategorien verschwinden, die übrigen klappen auf, der erste Treffer wird gewählt.
- **Abhängigkeitspfad:** ein Baum, nur sichtbar, wenn eine Dependency gewählt ist. Alle Wege
  Modul/Configuration → … → Dependency, zusammengeführt, darunter jeweils ihre Children.
- **Gradle-Sync:** Findet DepLens keine Module oder keine Dependencies, startet es einmal automatisch
  einen Gradle-Sync und lädt danach neu. Der Refresh-Button und „Gradle neu laden“ starten ebenfalls
  einen Sync.
- **Darstellung:** Vorkommen der gewählten Dependency farbig hervorgehoben; Konflikte mit ⚠ und
  Tooltip; nicht auflösbare Knoten rot; ausgelassene Knoten (`Omitted`) grau mit „(*)“.
  Großzügige Abstände (Toolbar 8 px, Überschriften fett, Zeilenhöhe 26 px).

## Komponenten

| Komponente | Verantwortung | Kennt IntelliJ-API? |
|---|---|---|
| `GradleDependencySource` | Contributor holen, Module listen, Dependencies eines Moduls laden, Sync-Listener | ja (einzige Stelle für die Analyzer-API) |
| `DependencyGraphMapper` | `List<DependencyAnalyzerDependency>` → `DependencyGraph` | nur die Analyzer-Interfaces |
| `DependencyGraph` | Knoten (Koordinate, Version, Configuration, Parent, Children, Status) | nein |
| `VendorCategory` / `DependencyCatalog` | Hersteller-Kategorie; Dependencies einmalig, gruppiert, gefiltert | nein |
| `DepLensToolWindowFactory` | Registriert das Tool-Window | ja |
| `DepLensPanel` | Layout, ComboBox, Suchfeld, Liste, ein-/ausblendbarer Pfad-`Tree`, Lade-/Fehlerzustände | ja |
| `DependencyNodeRenderer` | Farben, Icons, Tooltips | ja |

Datenfluss: Modul wählen → `GradleDependencySource` lädt im Hintergrund
(`ReadAction.nonBlocking` bzw. Background-Task) → `DependencyGraphMapper` → `DependencyGraph`
→ `DependencyCatalog` (Suche, Kategorien) → Auswahl → Abhängigkeitspfad.

## Fehlerbehandlung

- Kein Gradle-Projekt / keine Module → Leerzustand mit Hinweis auf Gradle-Sync.
- Laden läuft → Ladeindikator; Modulwechsel verwirft das veraltete Ergebnis.
- Exception aus API/Gradle → Meldung im Tool-Window mit „Erneut versuchen“, Logging über
  `Logger`; kein Absturz.
- Nicht auflösbare Dependencies → rot, aber durchsuchbar.
- Keine Treffer → „Keine Treffer für ‚…‘“.

## Tests

- `DependencyGraph`, `VendorCategory` und `DependencyCatalog`: JUnit 5, test-first. Fälle: Treffer in mehreren
  Configurations, mehrere Pfade zum selben Artefakt, Konflikt-Status, `Omitted`-Knoten,
  Groß-/Kleinschreibung, leere Suche.
- `DependencyGraphMapper`: JUnit 5 mit Fake-Implementierungen der Analyzer-Interfaces.
- UI: manuelle Checkliste mit `runIde` gegen ein Beispiel-Gradle-Multi-Modul-Projekt.

## Nicht im Umfang (Phase 1)

- mvnrepository / Vulnerability-Prüfung (Phase 2)
- Mehrfachauswahl von Modulen
- Navigation zur Deklaration in `build.gradle(.kts)`
- Pro-Pfad-Anzeige der angeforderten Version
- Marketplace-Veröffentlichung
