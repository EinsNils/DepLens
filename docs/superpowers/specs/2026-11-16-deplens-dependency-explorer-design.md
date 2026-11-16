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
| Plugin-Sprache | Java 21 |
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
- `Status.Omitted` = Unterbaum wurde bereits an anderer Stelle aufgelöst (Gradles `(*)`).
- `Status.Warning` = Versionskonflikt (Gradle-Selection-Reason „between versions …“) oder
  nicht auflösbare Dependency.
- `whenDataChanged(listener, disposable)` → Neuladen nach Gradle-Sync.

**Bekannte Einschränkung:** Ein Knoten trägt nur die *aufgelöste* Version. Bei Konflikten
gibt es eine Warning mit Gradles Begründung (z. B. „between versions 2.15.2 and 2.13.0“),
aber nicht, welcher Parent welche Version angefordert hat. Die Anzeige ist daher
„2.15.2 ⚠ Konflikt: between versions 2.15.2 and 2.13.0“, nicht `2.13.0 → 2.15.2` pro Pfad.
Falls das später nötig ist, kann ein eigenes Gradle-Init-Script ergänzt werden.

`getDependencies` kann einen Gradle-Lauf auslösen und wird daher nie auf dem EDT aufgerufen.

## UI

```
┌─ Tool Window "DepLens" ─────────────────────────────────────────┐
│ [Modul ▼]  [🔍 Suche: jackson-databind      ]  [⟳ Refresh]     │
│ ┌ Trefferliste ─────────────────────────────────────────────┐ │
│ │ com.fasterxml.jackson.core:jackson-databind:2.15.2         │ │
│ │   runtimeClasspath · via spring-boot-starter-json  ⚠       │ │
│ │   testRuntimeClasspath · direkt                            │ │
│ └────────────────────────────────────────────────────────────┘ │
│ ┌ Parents (bis zum Modul) ───┐ ┌ Children ──────────────────┐ │
│ │ ▾ :app (runtimeClasspath)  │ │ ▾ jackson-databind 2.15.2  │ │
│ │   ▾ spring-boot-starter-web│ │   ├ jackson-annotations    │ │
│ │     ▾ …-starter-json       │ │   └ jackson-core           │ │
│ │       ● jackson-databind   │ │                            │ │
│ └────────────────────────────┘ └────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

- **Modulauswahl:** eine ComboBox, genau ein Modul.
- **Configurations:** alle auflösbaren Configurations des Moduls; Treffer und Pfade sind
  immer nach Configuration gruppiert bzw. beschriftet.
- **Suche:** Teilstring (case-insensitive) auf `group:artifact:version`, mit kurzer
  Verzögerung beim Tippen (Debounce ~300 ms). Leere Suche → keine Treffer, Bäume leer.
- **Trefferliste:** ein Eintrag pro Vorkommen (Knoten), gruppiert nach Koordinate.
  Zeigt Configuration, direkten Parent (oder „direkt“) und ⚠ bei Warnings.
- **Geteilte Ansicht** (Klick auf einen Treffer):
  - links: Pfad Modul/Configuration → … → Treffer (aufgeklappt),
  - rechts: Unterbaum des Treffers (Children, rekursiv).
- **Darstellung:** Treffer farbig hervorgehoben; Konflikte mit ⚠ und Tooltip mit der
  Gradle-Begründung; nicht auflösbare Knoten rot; ausgelassene Knoten (`Omitted`) grau mit
  Hinweis „(*) siehe andere Stelle“.

## Komponenten

| Komponente | Verantwortung | Kennt IntelliJ-API? |
|---|---|---|
| `GradleDependencySource` | Contributor holen, Module listen, Dependencies eines Moduls laden, Sync-Listener | ja (einzige Stelle für die Analyzer-API) |
| `DependencyGraphMapper` | `List<DependencyAnalyzerDependency>` → `DependencyGraph` | nur die Analyzer-Interfaces |
| `DependencyGraph` | Knoten (Koordinate, Version, Configuration, Parent, Children, Status) | nein |
| `DependencySearch` | Suche → Treffer mit Pfad zur Wurzel | nein |
| `DepLensToolWindowFactory` | Registriert das Tool-Window | ja |
| `DepLensPanel` | Layout, ComboBox, Suchfeld, Liste, `Splitter` mit zwei `Tree`s, Lade-/Fehlerzustände | ja |
| `DependencyNodeRenderer` | Farben, Icons, Tooltips | ja |

Datenfluss: Modul wählen → `GradleDependencySource` lädt im Hintergrund
(`ReadAction.nonBlocking` bzw. Background-Task) → `DependencyGraphMapper` → `DependencyGraph`
→ Suche → Trefferliste → Auswahl → Parents-/Children-Baum.

## Fehlerbehandlung

- Kein Gradle-Projekt / keine Module → Leerzustand mit Hinweis auf Gradle-Sync.
- Laden läuft → Ladeindikator; Modulwechsel verwirft das veraltete Ergebnis.
- Exception aus API/Gradle → Meldung im Tool-Window mit „Erneut versuchen“, Logging über
  `Logger`; kein Absturz.
- Nicht auflösbare Dependencies → rot, aber durchsuchbar.
- Keine Treffer → „Keine Treffer für ‚…‘“.

## Tests

- `DependencyGraph` und `DependencySearch`: JUnit 5, test-first. Fälle: Treffer in mehreren
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
