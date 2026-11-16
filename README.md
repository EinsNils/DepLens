# DepLens

IntelliJ-Plugin zum Durchsuchen von Gradle-Dependencies pro Modul.
Für jeden Treffer zeigt DepLens, wer die Dependency reinzieht (Parents) und was sie mitbringt (Children).

## Voraussetzungen

- IntelliJ IDEA 2026.2+
- JDK 25

## Entwickeln

| Befehl | Zweck |
|---|---|
| `./gradlew test` | Unit-Tests |
| `./gradlew runIde` | Sandbox-IDE mit Plugin starten (zum Testen `samples/demo` öffnen) |
| `./gradlew buildPlugin` | Installierbares ZIP unter `build/distributions/` |

Installation: *Settings → Plugins → ⚙ → Install Plugin from Disk…* und das ZIP wählen.

## Datenquelle

DepLens nutzt IntelliJs eingebauten Gradle-Dependency-Analyzer
(`DependencyAnalyzerContributor`). Pro Knoten ist nur die aufgelöste Version bekannt;
Versionskonflikte erscheinen als ⚠ mit Gradles Begründung.
