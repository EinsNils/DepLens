# DepLens Sicherheitsprüfung (Phase 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Für jede Dependency im DepLens-Tool-Window bekannte Sicherheitslücken anzeigen und zwei sichere Upgrade-Versionen vorschlagen (nächste mit gleicher Major, neueste).

**Architecture:** Neues Paket `dev.nz3x.deplens.security` mit reiner, netzfreier Logik (Versionen, OSV-Bereichsauswertung, Upgrade-Wahl, Parser, Orchestrierung über Schnittstellen `SecurityClient`/`ResponseCache`). Dünne IntelliJ-Schicht: `HttpSecurityClient` (IntelliJ `HttpRequests`), `FileResponseCache` (Systemordner), `SecurityService` (Projekt-Service), `DepLensSettings` + Einstellungsseite. Die UI liest Berichte aus einer Map und rendert Markierungen, einen Filter und einen Tab „Sicherheit“.

**Tech Stack:** Java 25, IntelliJ Platform 2026.2.3 (Gradle-Plugin 2.20.1), `org.apache.maven:maven-artifact:3.10.1` (`ComparableVersion`), Gson aus der Plattform, JUnit 6.1.3, AssertJ 3.27.7.

**Spec:** `docs/superpowers/specs/2026-11-16-deplens-security-check-design.md` (baut auf `docs/superpowers/specs/2026-11-16-deplens-dependency-explorer-design.md` auf)

## Global Constraints

- Ziel-IDE IntelliJ IDEA 2026.2, `sinceBuild = "262"`, Java-Toolchain 25, Quellcode UTF-8, UI-Texte deutsch.
- Paket-Wurzel `dev.nz3x.deplens`; neue Pakete `security` und `settings`.
- Tests laufen ohne Netz. Netzzugriff nur in `HttpSecurityClient`.
- Versionsquelle: `https://repo1.maven.org/maven2/<group/als/pfad>/<artifact>/maven-metadata.xml`.
- OSV: `POST https://api.osv.dev/v1/query`, Body `{"package":{"name":"<g>:<a>","ecosystem":"Maven"}}` (+ `"page_token"`), Lücken-Link `https://osv.dev/vulnerability/<id>`.
- OSS Index: `POST https://ossindex.sonatype.org/api/v3/component-report`, Basic Auth, Body `{"coordinates":[...]}`, Coordinates `pkg:maven/<g>/<a>@<v>`, max. 128 pro Anfrage; nur aktiv mit Benutzer + Token.
- mvnrepository nur als Link `https://mvnrepository.com/artifact/<g>/<a>/<v>`.
- Cache 24 h (einstellbar), 429 → einmal nach 2 s wiederholen, max. 8 parallele Abfragen, max. 5 Gegenprüf-Versuche je Vorschlag.
- Status: `SICHER`, `VERWUNDBAR`, `NICHT_PRUEFBAR`, `FEHLER`; Schweregrade KRITISCH, HOCH, MITTEL, NIEDRIG, UNBEKANNT.
- Git: Branch `feat/security-check` (von `docs/security-spec`), kleine Commits mit kurzen Nachrichten, nach jedem Task pushen; jede Commit-Nachricht endet mit
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

**Klarstellung zur Spec (Severity):** OSV liefert CVSS nur als Vektor-String, nicht als Score. Ohne `database_specific.severity` ist eine OSV-Lücke daher `UNBEKANNT`; die Ableitung aus dem CVSS-Score gilt für OSS Index (`cvssScore`).

## Review Focus

1. **Ungewöhnliche Versions-Strings** (`2.13.4.2`, `1.0.0.RELEASE`, `33.4.8-jre`, `5.0.0.Beta1`, `21-ea`) – müssen korrekt sortiert und als stabil/Vorabversion erkannt werden → `MavenVersionTest` (Task 1).
2. **OSV-Bereiche mit `introduced: "0"`, `last_affected`, mehreren Bereichen in einer Event-Liste, offenen Bereichen** – genau `fixed` ist sicher, genau `introduced` betroffen → `VulnerabilityMatcherTest` (Task 2).
3. **Netzprobleme (Fehler, 429, kaputte Antwort, 401 bei OSS Index)** – andere Dependencies laufen weiter, kaputte Antworten werden nicht gecacht → `SecurityCheckerTest` (Task 5).
4. **Artefakte, die Maven Central nicht kennt (404)** – „nicht prüfbar“ bzw. verwundbar ohne Vorschlag, kein Fehler → `SecurityCheckerTest` (Task 5).
5. **Modulwechsel während einer laufenden Prüfung** – abgebrochene Prüfung meldet nichts mehr → `SecurityCheckerTest.cancelledCheckReportsNothing` (Task 5) + eigener `RequestSequencer` im Panel (Task 7).

---

## Dateistruktur

```
build.gradle.kts                                   + maven-artifact
src/main/resources/META-INF/plugin.xml             + Einstellungsseite, Beschreibung
src/main/java/dev/nz3x/deplens/
  security/MavenVersion.java                       Versionsvergleich, Vorabversion, Major
  security/Severity.java                           Schweregrade + Ableitung
  security/AffectedRange.java                      OSV-Bereich (Events)
  security/Vulnerability.java                      Eine Lücke
  security/VulnerabilityMatcher.java               Betroffen? Behoben in?
  security/ResponseFormatException.java            Unlesbare Antwort
  security/Json.java                               Gson-Helfer (package-private)
  security/OsvParser.java                          OSV-JSON → Lücken + Seiten-Token
  security/MavenMetadataParser.java                maven-metadata.xml → Versionen
  security/OssIndexParser.java                     OSS-Index-JSON → Lücken je Coordinate
  security/UpgradeAdvisor.java                     Kandidaten, Gegenprüfliste, Wahl
  security/Artifact.java                           group:artifact:version
  security/Finding.java                            Lücke + behoben-in für die aktuelle Version
  security/SecurityReport.java                     Ergebnis je Artefakt
  security/SecurityLinks.java                      mvnrepository-/osv-Links
  security/HttpStatusException.java                HTTP-Status als IOException
  security/SecurityClient.java                     Schnittstelle der drei Dienste
  security/ResponseCache.java                      Schnittstelle des Caches
  security/SecurityChecker.java                    Orchestrierung (netzfrei testbar)
  security/FileResponseCache.java                  Cache als Dateien
  security/HttpSecurityClient.java                 HTTP über IntelliJ HttpRequests
  security/SecurityService.java                    Projekt-Service
  settings/DepLensSettings.java                    Persistente Einstellungen + Token
  settings/DepLensConfigurable.java                Settings → Tools → DepLens
  catalog/DependencyCatalog.java                   + filter(...)
  ui/NodeText.java                                 + Sicherheits-Markierung
  ui/CatalogTreeCellRenderer.java                  + Markierungen je Eintrag/Kategorie
  ui/SecurityPanel.java                            Tab „Sicherheit“
  ui/DepLensPanel.java                             Toolbar, Statuszeile, Tabs, Prüfablauf
src/test/java/dev/nz3x/deplens/
  security/*Test.java, security/Fixtures.java, security/FakeSecurityClient.java, security/InMemoryCache.java
  catalog/DependencyCatalogTest.java               + filter-Test
src/test/resources/security/
  osv-jackson-databind.json, maven-metadata-jackson-databind.xml, ossindex-jackson-databind.json
README.md                                          + Abschnitt Sicherheitsprüfung
```

---

### Task 1: Abhängigkeit und `MavenVersion`

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/main/java/dev/nz3x/deplens/security/MavenVersion.java`
- Test: `src/test/java/dev/nz3x/deplens/security/MavenVersionTest.java`

**Interfaces:**
- Produces: `MavenVersion.of(String)`, `isPreRelease()`, `long major()` (`-1` ohne führende Zahl), `compareTo`, `equals`/`hashCode` (Maven-kanonisch: `1.0` = `1.0.0`), `toString()` = Originaltext (getrimmt).

- [ ] **Step 1: Branch anlegen**

```bash
cd /p/DepLens
git switch docs/security-spec
git switch -c feat/security-check
```

- [ ] **Step 2: Abhängigkeit in `build.gradle.kts` ergänzen** (im `dependencies`-Block nach dem `intellijPlatform { ... }`-Block)

```kotlin
    // Maven-Versionsvergleich; nur ComparableVersion wird gebraucht
    implementation("org.apache.maven:maven-artifact:3.10.1") {
        isTransitive = false
    }
```

- [ ] **Step 3: Failing Test schreiben**

`src/test/java/dev/nz3x/deplens/security/MavenVersionTest.java`:

```java
package dev.nz3x.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MavenVersionTest {

    @Test
    void sortsLikeMaven() {
        List<MavenVersion> sorted = Stream.of("2.13.5", "2.13.4.2", "2.0.0", "2.0.0-RC1", "2.13.4")
                .map(MavenVersion::of)
                .sorted()
                .toList();

        assertThat(sorted).extracting(MavenVersion::toString)
                .containsExactly("2.0.0-RC1", "2.0.0", "2.13.4", "2.13.4.2", "2.13.5");
    }

    @Test
    void recognizesPreReleases() {
        assertThat(Stream.of("2.0.0-RC1", "6.0.0-M2", "5.0.0.Beta1", "1.0-SNAPSHOT", "3.0.0-alpha-1",
                        "21-ea", "1.0.0-cr2", "2.0-preview", "1.0-dev", "2.4.0-rc1")
                .map(MavenVersion::of))
                .allMatch(MavenVersion::isPreRelease);
    }

    @Test
    void releaseQualifiersAreNoPreRelease() {
        assertThat(Stream.of("2.13.4.2", "1.0.0.RELEASE", "33.4.8-jre", "5.13.4", "1.0.0.Final")
                .map(MavenVersion::of))
                .noneMatch(MavenVersion::isPreRelease);
    }

    @Test
    void majorIsFirstNumericSegment() {
        assertThat(MavenVersion.of("2.13.4").major()).isEqualTo(2);
        assertThat(MavenVersion.of("33.4.8-jre").major()).isEqualTo(33);
        assertThat(MavenVersion.of("RELEASE").major()).isEqualTo(-1);
    }

    @Test
    void equalityFollowsMavenCanonicalForm() {
        MavenVersion shortForm = MavenVersion.of("1.0");
        MavenVersion longForm = MavenVersion.of("1.0.0");

        assertThat(shortForm).isEqualTo(longForm).hasSameHashCodeAs(longForm);
        assertThat(shortForm.compareTo(longForm)).isZero();
        assertThat(MavenVersion.of(" 2.13.0 ")).hasToString("2.13.0");
    }
}
```

- [ ] **Step 4: Test ausführen, er muss fehlschlagen**

Run: `./gradlew test --tests "dev.nz3x.deplens.security.MavenVersionTest"`
Expected: FAIL – `cannot find symbol: class MavenVersion` (bzw. deutsch „Symbol nicht gefunden“).

- [ ] **Step 5: `MavenVersion` implementieren**

```java
package dev.nz3x.deplens.security;

import java.util.Locale;
import java.util.Set;
import org.apache.maven.artifact.versioning.ComparableVersion;

/** Eine Maven-Version mit Vergleich nach Maven-Regeln. */
public final class MavenVersion implements Comparable<MavenVersion> {
    private static final Set<String> PRE_RELEASE_QUALIFIERS = Set.of(
            "alpha", "beta", "milestone", "m", "rc", "cr", "snapshot", "preview", "ea", "dev");

    private final String text;
    private final ComparableVersion comparable;

    private MavenVersion(String text) {
        this.text = text;
        this.comparable = new ComparableVersion(text);
    }

    public static MavenVersion of(String text) {
        return new MavenVersion(text.strip());
    }

    /** {@code true} für Vorabversionen wie {@code 2.0.0-RC1}, {@code 6.0.0-M2}, {@code 5.0.0.Beta1}. */
    public boolean isPreRelease() {
        for (String token : tokens()) {
            if (PRE_RELEASE_QUALIFIERS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /** Erstes numerisches Segment; {@code -1}, wenn die Version nicht mit einer Zahl beginnt. */
    public long major() {
        String first = tokens()[0];
        boolean numeric = !first.isEmpty() && first.length() <= 18 && first.chars().allMatch(Character::isDigit);
        return numeric ? Long.parseLong(first) : -1;
    }

    /** Zerlegt an {@code . - _ +} und an Wechseln zwischen Ziffern und Buchstaben. */
    private String[] tokens() {
        return text.toLowerCase(Locale.ROOT).split("[.\\-_+]|(?<=\\d)(?=\\D)|(?<=\\D)(?=\\d)");
    }

    @Override
    public int compareTo(MavenVersion other) {
        return comparable.compareTo(other.comparable);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MavenVersion version && comparable.equals(version.comparable);
    }

    @Override
    public int hashCode() {
        return comparable.hashCode();
    }

    @Override
    public String toString() {
        return text;
    }
}
```

- [ ] **Step 6: Test ausführen, er muss grün sein**

Run: `./gradlew test --tests "dev.nz3x.deplens.security.MavenVersionTest"`
Expected: PASS (5 Tests).

- [ ] **Step 7: Commit und Push**

```bash
git add build.gradle.kts src
git commit -m "Add Maven version comparison

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push -u origin feat/security-check
```

---

### Task 2: Lücken-Modell und `VulnerabilityMatcher`

**Files:**
- Create: `security/Severity.java`, `security/AffectedRange.java`, `security/Vulnerability.java`, `security/VulnerabilityMatcher.java` (alle unter `src/main/java/dev/nz3x/deplens/`)
- Test: `src/test/java/dev/nz3x/deplens/security/VulnerabilityMatcherTest.java`, `src/test/java/dev/nz3x/deplens/security/VulnerabilityTest.java`

**Interfaces:**
- Consumes: `MavenVersion`.
- Produces:
  - `enum Severity { KRITISCH, HOCH, MITTEL, NIEDRIG, UNBEKANNT }` (Ordinal = absteigende Schwere), `Severity.fromLabel(@Nullable String)`, `Severity.fromCvssScore(double)`.
  - `record AffectedRange(List<Event> events)`, `enum AffectedRange.Kind { INTRODUCED, FIXED, LAST_AFFECTED }`, `record AffectedRange.Event(Kind kind, String version)`.
  - `record Vulnerability(String id, List<String> aliases, Severity severity, String summary, String url, List<AffectedRange> ranges, Set<String> versions)` mit `displayId()` und `allIds()`.
  - `static boolean VulnerabilityMatcher.affects(Vulnerability, MavenVersion)`, `static @Nullable MavenVersion VulnerabilityMatcher.fixedIn(Vulnerability, MavenVersion)`.

- [ ] **Step 1: Failing Tests schreiben**

`VulnerabilityTest.java`:

```java
package dev.nz3x.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VulnerabilityTest {

    @Test
    void displayIdPrefersCve() {
        assertThat(vulnerability("GHSA-1", "CVE-2022-1").displayId()).isEqualTo("CVE-2022-1");
        assertThat(vulnerability("CVE-2022-2").displayId()).isEqualTo("CVE-2022-2");
        assertThat(vulnerability("GHSA-3", "OTHER-3").displayId()).isEqualTo("GHSA-3");
    }

    @Test
    void allIdsContainsIdAndAliases() {
        assertThat(vulnerability("GHSA-1", "CVE-2022-1").allIds()).containsExactly("GHSA-1", "CVE-2022-1");
    }

    @Test
    void severityFromLabel() {
        assertThat(Severity.fromLabel("CRITICAL")).isEqualTo(Severity.KRITISCH);
        assertThat(Severity.fromLabel("high")).isEqualTo(Severity.HOCH);
        assertThat(Severity.fromLabel("MODERATE")).isEqualTo(Severity.MITTEL);
        assertThat(Severity.fromLabel("MEDIUM")).isEqualTo(Severity.MITTEL);
        assertThat(Severity.fromLabel("LOW")).isEqualTo(Severity.NIEDRIG);
        assertThat(Severity.fromLabel("whatever")).isEqualTo(Severity.UNBEKANNT);
        assertThat(Severity.fromLabel(null)).isEqualTo(Severity.UNBEKANNT);
    }

    @Test
    void severityFromCvssScore() {
        assertThat(Severity.fromCvssScore(9.8)).isEqualTo(Severity.KRITISCH);
        assertThat(Severity.fromCvssScore(7.0)).isEqualTo(Severity.HOCH);
        assertThat(Severity.fromCvssScore(4.0)).isEqualTo(Severity.MITTEL);
        assertThat(Severity.fromCvssScore(0.1)).isEqualTo(Severity.NIEDRIG);
        assertThat(Severity.fromCvssScore(0)).isEqualTo(Severity.UNBEKANNT);
    }

    @Test
    void severityOrderIsMostSevereFirst() {
        assertThat(Severity.KRITISCH).isLessThan(Severity.HOCH);
        assertThat(Severity.NIEDRIG).isLessThan(Severity.UNBEKANNT);
    }

    private static Vulnerability vulnerability(String id, String... aliases) {
        return new Vulnerability(id, List.of(aliases), Severity.HOCH, "s", "u", List.of(), Set.of());
    }
}
```

`VulnerabilityMatcherTest.java`:

```java
package dev.nz3x.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nz3x.deplens.security.AffectedRange.Event;
import dev.nz3x.deplens.security.AffectedRange.Kind;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VulnerabilityMatcherTest {

    @Test
    void introducedIsAffectedFixedIsNot() {
        Vulnerability v = vuln(introduced("2.13.0"), fixed("2.13.4.2"));

        assertThat(affects(v, "2.12.0")).isFalse();
        assertThat(affects(v, "2.13.0")).isTrue();
        assertThat(affects(v, "2.13.4")).isTrue();
        assertThat(affects(v, "2.13.4.2")).isFalse();
        assertThat(affects(v, "2.14.0")).isFalse();
    }

    @Test
    void introducedZeroMeansFromTheBeginning() {
        Vulnerability v = vuln(introduced("0"), fixed("2.12.7.1"));

        assertThat(affects(v, "1.0")).isTrue();
        assertThat(affects(v, "2.12.7.1")).isFalse();
    }

    @Test
    void lastAffectedIsInclusive() {
        Vulnerability v = vuln(introduced("2.14.0"), lastAffected("2.14.1"));

        assertThat(affects(v, "2.13.9")).isFalse();
        assertThat(affects(v, "2.14.1")).isTrue();
        assertThat(affects(v, "2.14.2")).isFalse();
    }

    @Test
    void severalRangesInOneEventList() {
        Vulnerability v = vuln(introduced("2.4.0"), fixed("2.12.7.1"), introduced("2.13.0"), fixed("2.13.4.2"));

        assertThat(affects(v, "2.10.0")).isTrue();
        assertThat(affects(v, "2.12.8")).isFalse();
        assertThat(affects(v, "2.13.1")).isTrue();
    }

    @Test
    void rangeWithoutEndIsOpen() {
        Vulnerability v = vuln(introduced("3.0.0"));

        assertThat(affects(v, "2.9")).isFalse();
        assertThat(affects(v, "9.9")).isTrue();
    }

    @Test
    void explicitVersionsAreAffected() {
        Vulnerability v = new Vulnerability("GHSA-1", List.of(), Severity.HOCH, "s", "u", List.of(), Set.of("2.10.0"));

        assertThat(affects(v, "2.10.0")).isTrue();
        assertThat(affects(v, "2.10.1")).isFalse();
    }

    @Test
    void fixedInIsTheEndOfTheContainingRange() {
        Vulnerability twoRanges = vuln(introduced("2.4.0"), fixed("2.12.7.1"), introduced("2.13.0"), fixed("2.13.4.2"));
        Vulnerability lastAffected = vuln(introduced("2.14.0"), lastAffected("2.14.1"));

        assertThat(VulnerabilityMatcher.fixedIn(twoRanges, MavenVersion.of("2.13.1"))).hasToString("2.13.4.2");
        assertThat(VulnerabilityMatcher.fixedIn(twoRanges, MavenVersion.of("2.10.0"))).hasToString("2.12.7.1");
        assertThat(VulnerabilityMatcher.fixedIn(twoRanges, MavenVersion.of("2.12.8"))).isNull();
        assertThat(VulnerabilityMatcher.fixedIn(lastAffected, MavenVersion.of("2.14.0"))).isNull();
    }

    private static boolean affects(Vulnerability v, String version) {
        return VulnerabilityMatcher.affects(v, MavenVersion.of(version));
    }

    private static Vulnerability vuln(Event... events) {
        return new Vulnerability("GHSA-1", List.of("CVE-1"), Severity.HOCH, "s", "u",
                List.of(new AffectedRange(List.of(events))), Set.of());
    }

    private static Event introduced(String version) {
        return new Event(Kind.INTRODUCED, version);
    }

    private static Event fixed(String version) {
        return new Event(Kind.FIXED, version);
    }

    private static Event lastAffected(String version) {
        return new Event(Kind.LAST_AFFECTED, version);
    }
}
```

- [ ] **Step 2: Tests ausführen, sie müssen fehlschlagen**

Run: `./gradlew test --tests "dev.nz3x.deplens.security.Vulnerability*"`
Expected: FAIL – `cannot find symbol: class Vulnerability`.

- [ ] **Step 3: `Severity` implementieren**

```java
package dev.nz3x.deplens.security;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** Schweregrad einer Lücke; die Reihenfolge ist absteigend nach Schwere. */
public enum Severity {
    KRITISCH, HOCH, MITTEL, NIEDRIG, UNBEKANNT;

    /** OSV-/GHSA-Label wie {@code CRITICAL}, {@code HIGH}, {@code MODERATE}, {@code LOW}. */
    public static Severity fromLabel(@Nullable String label) {
        if (label == null) {
            return UNBEKANNT;
        }
        return switch (label.strip().toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> KRITISCH;
            case "HIGH" -> HOCH;
            case "MODERATE", "MEDIUM" -> MITTEL;
            case "LOW" -> NIEDRIG;
            default -> UNBEKANNT;
        };
    }

    public static Severity fromCvssScore(double score) {
        if (score >= 9.0) {
            return KRITISCH;
        }
        if (score >= 7.0) {
            return HOCH;
        }
        if (score >= 4.0) {
            return MITTEL;
        }
        return score > 0.0 ? NIEDRIG : UNBEKANNT;
    }
}
```

- [ ] **Step 4: `AffectedRange` und `Vulnerability` implementieren**

```java
package dev.nz3x.deplens.security;

import java.util.List;

/** Ein OSV-Versionsbereich vom Typ ECOSYSTEM als Folge von Ereignissen. */
public record AffectedRange(List<Event> events) {

    public enum Kind { INTRODUCED, FIXED, LAST_AFFECTED }

    public record Event(Kind kind, String version) {
    }

    public AffectedRange {
        events = List.copyOf(events);
    }
}
```

```java
package dev.nz3x.deplens.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Eine bekannte Sicherheitslücke eines Pakets. */
public record Vulnerability(String id, List<String> aliases, Severity severity, String summary, String url,
                            List<AffectedRange> ranges, Set<String> versions) {

    public Vulnerability {
        aliases = List.copyOf(aliases);
        ranges = List.copyOf(ranges);
        versions = Set.copyOf(versions);
    }

    /** CVE-Kennung, falls vorhanden, sonst die eigene ID. */
    public String displayId() {
        if (id.startsWith("CVE-")) {
            return id;
        }
        return aliases.stream().filter(alias -> alias.startsWith("CVE-")).findFirst().orElse(id);
    }

    /** Eigene ID und alle Aliase, in dieser Reihenfolge. */
    public Set<String> allIds() {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(id);
        ids.addAll(aliases);
        return ids;
    }
}
```

- [ ] **Step 5: `VulnerabilityMatcher` implementieren**

```java
package dev.nz3x.deplens.security;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/** Wertet aus, ob eine Version von einer Lücke betroffen ist (OSV-Semantik). */
public final class VulnerabilityMatcher {

    /** Halboffenes oder geschlossenes Intervall; {@code from == null} = von Anfang an, {@code to == null} = offen. */
    private record Interval(@Nullable MavenVersion from, @Nullable MavenVersion to, boolean toInclusive) {
        boolean contains(MavenVersion version) {
            if (from != null && version.compareTo(from) < 0) {
                return false;
            }
            if (to == null) {
                return true;
            }
            int comparison = version.compareTo(to);
            return toInclusive ? comparison <= 0 : comparison < 0;
        }
    }

    private VulnerabilityMatcher() {
    }

    public static boolean affects(Vulnerability vulnerability, MavenVersion version) {
        boolean listed = vulnerability.versions().stream().anyMatch(v -> MavenVersion.of(v).equals(version));
        return listed || intervals(vulnerability).anyMatch(interval -> interval.contains(version));
    }

    /** Kleinste {@code fixed}-Version eines Bereichs, der {@code version} enthält; sonst {@code null}. */
    public static @Nullable MavenVersion fixedIn(Vulnerability vulnerability, MavenVersion version) {
        return intervals(vulnerability)
                .filter(interval -> interval.contains(version) && interval.to() != null && !interval.toInclusive())
                .map(Interval::to)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private static Stream<Interval> intervals(Vulnerability vulnerability) {
        return vulnerability.ranges().stream().flatMap(range -> intervals(range).stream());
    }

    private static List<Interval> intervals(AffectedRange range) {
        List<Interval> result = new ArrayList<>();
        MavenVersion start = null;
        boolean open = false;
        for (AffectedRange.Event event : range.events()) {
            switch (event.kind()) {
                case INTRODUCED -> {
                    start = "0".equals(event.version()) ? null : MavenVersion.of(event.version());
                    open = true;
                }
                case FIXED -> {
                    if (open) {
                        result.add(new Interval(start, MavenVersion.of(event.version()), false));
                    }
                    open = false;
                }
                case LAST_AFFECTED -> {
                    if (open) {
                        result.add(new Interval(start, MavenVersion.of(event.version()), true));
                    }
                    open = false;
                }
            }
        }
        if (open) {
            result.add(new Interval(start, null, false));
        }
        return result;
    }
}
```

- [ ] **Step 6: Tests ausführen, sie müssen grün sein**

Run: `./gradlew test --tests "dev.nz3x.deplens.security.Vulnerability*"`
Expected: PASS (12 Tests).

- [ ] **Step 7: Commit und Push**

```bash
git add src
git commit -m "Add vulnerability model and range matcher

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push
```

---

### Task 3: Parser für OSV, Maven Central und OSS Index

**Files:**
- Create: `security/ResponseFormatException.java`, `security/Json.java`, `security/OsvParser.java`, `security/MavenMetadataParser.java`, `security/OssIndexParser.java`
- Create (Test-Ressourcen): `src/test/resources/security/osv-jackson-databind.json`, `src/test/resources/security/maven-metadata-jackson-databind.xml`, `src/test/resources/security/ossindex-jackson-databind.json`
- Test: `security/Fixtures.java`, `security/OsvParserTest.java`, `security/MavenMetadataParserTest.java`, `security/OssIndexParserTest.java`

**Interfaces:**
- Consumes: `Vulnerability`, `AffectedRange`, `Severity`, `MavenVersion`.
- Produces:
  - `class ResponseFormatException extends IOException` (`(String message, @Nullable Throwable cause)`).
  - `OsvParser.parse(String json, String packageName)` → `OsvParser.Page(List<Vulnerability> vulnerabilities, @Nullable String nextPageToken)`; nur `affected`-Einträge mit `package.name == packageName` und `ecosystem == "Maven"`.
  - `MavenMetadataParser.parse(String xml)` → `List<MavenVersion>` (nur `<version>` unter `<versions>`, DOCTYPE verboten).
  - `OssIndexParser.parse(String json)` → `Map<String, List<Vulnerability>>` mit kleingeschriebener Coordinate als Schlüssel; `OssIndexParser.coordinate(String groupId, String artifactId, String version)` → `pkg:maven/<g>/<a>@<v>` kleingeschrieben.
  - Test-Helfer `Fixtures.read(String name)` liest `/security/<name>`.

Gson (`com.google.gson`) kommt aus der IntelliJ-Plattform. Falls `import com.google.gson.*` nicht kompiliert: in `build.gradle.kts` `implementation("com.google.code.gson:gson:2.15.0")` ergänzen und das im Commit erwähnen.

- [ ] **Step 1: Test-Ressourcen anlegen**

`src/test/resources/security/osv-jackson-databind.json`:

```json
{
  "vulns": [
    {
      "id": "GHSA-jjjh-jjxp-wpff",
      "summary": "Uncontrolled Resource Consumption in Jackson-databind",
      "aliases": ["CVE-2022-42003"],
      "database_specific": {"severity": "HIGH"},
      "affected": [
        {
          "package": {"ecosystem": "Maven", "name": "com.fasterxml.jackson.core:jackson-databind"},
          "ranges": [
            {"type": "ECOSYSTEM", "events": [
              {"introduced": "2.4.0-rc1"}, {"fixed": "2.12.7.1"},
              {"introduced": "2.13.0"}, {"fixed": "2.13.4.2"}
            ]}
          ]
        }
      ]
    },
    {
      "id": "GHSA-rgv9-q543-rqg4",
      "summary": "Uncontrolled Resource Consumption in FasterXML jackson-databind",
      "aliases": ["CVE-2022-42004"],
      "database_specific": {"severity": "HIGH"},
      "affected": [
        {
          "package": {"ecosystem": "Maven", "name": "com.fasterxml.jackson.core:jackson-databind"},
          "ranges": [
            {"type": "ECOSYSTEM", "events": [
              {"introduced": "0"}, {"fixed": "2.12.7.1"},
              {"introduced": "2.13.0"}, {"fixed": "2.13.4"}
            ]}
          ]
        }
      ]
    },
    {
      "id": "GHSA-test-last-affected",
      "summary": "Test entry with last_affected and explicit versions",
      "affected": [
        {
          "package": {"ecosystem": "Maven", "name": "com.fasterxml.jackson.core:jackson-databind"},
          "ranges": [
            {"type": "ECOSYSTEM", "events": [{"introduced": "2.14.0"}, {"last_affected": "2.14.1"}]},
            {"type": "GIT", "events": [{"introduced": "abc"}, {"fixed": "def"}]}
          ],
          "versions": ["2.10.0"]
        },
        {
          "package": {"ecosystem": "Maven", "name": "other.group:other-artifact"},
          "versions": ["2.15.0"]
        }
      ]
    }
  ],
  "next_page_token": "page-2"
}
```

`src/test/resources/security/maven-metadata-jackson-databind.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
  <versioning>
    <latest>3.0.0-rc1</latest>
    <release>2.15.0</release>
    <versions>
      <version>2.12.7</version>
      <version>2.12.7.1</version>
      <version>2.13.0</version>
      <version>2.13.4</version>
      <version>2.13.4.2</version>
      <version>2.13.5</version>
      <version>2.14.0-rc1</version>
      <version>2.14.0</version>
      <version>2.14.1</version>
      <version>2.15.0</version>
      <version>3.0.0-rc1</version>
    </versions>
    <lastUpdated>20260101000000</lastUpdated>
  </versioning>
</metadata>
```

`src/test/resources/security/ossindex-jackson-databind.json`:

```json
[
  {
    "coordinates": "pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.0",
    "description": "General data-binding functionality for Jackson",
    "reference": "https://ossindex.sonatype.org/component/pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.0",
    "vulnerabilities": [
      {
        "id": "CVE-2022-42003",
        "displayName": "CVE-2022-42003",
        "title": "[CVE-2022-42003] CWE-502: Deserialization of Untrusted Data",
        "cvssScore": 7.5,
        "cve": "CVE-2022-42003",
        "reference": "https://ossindex.sonatype.org/vulnerability/CVE-2022-42003"
      },
      {
        "id": "sonatype-2024-0001",
        "displayName": "sonatype-2024-0001",
        "title": "Sonatype only finding",
        "cvssScore": 9.8,
        "reference": "https://ossindex.sonatype.org/vulnerability/sonatype-2024-0001"
      }
    ]
  },
  {
    "coordinates": "pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.4.2",
    "reference": "https://ossindex.sonatype.org/component/pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.4.2",
    "vulnerabilities": [
      {
        "id": "CVE-2099-0001",
        "title": "Finding only known to OSS Index",
        "cvssScore": 5.0,
        "cve": "CVE-2099-0001",
        "reference": "https://ossindex.sonatype.org/vulnerability/CVE-2099-0001"
      }
    ]
  },
  {
    "coordinates": "pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.5",
    "reference": "https://ossindex.sonatype.org/component/pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.5",
    "vulnerabilities": []
  }
]
```

- [ ] **Step 2: Test-Helfer `Fixtures` schreiben**

```java
package dev.nz3x.deplens.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Liest Beispielantworten aus {@code src/test/resources/security}. */
final class Fixtures {
    private Fixtures() {
    }

    static String read(String name) {
        try (InputStream in = Objects.requireNonNull(Fixtures.class.getResourceAsStream("/security/" + name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

- [ ] **Step 3: Failing Tests schreiben**

`OsvParserTest.java`:

```java
package dev.nz3x.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nz3x.deplens.security.AffectedRange.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

class OsvParserTest {
    private static final String PACKAGE = "com.fasterxml.jackson.core:jackson-databind";

    @Test
    void parsesVulnerabilitiesAndPageToken() throws Exception {
        OsvParser.Page page = OsvParser.parse(Fixtures.read("osv-jackson-databind.json"), PACKAGE);

        assertThat(page.nextPageToken()).isEqualTo("page-2");
        assertThat(page.vulnerabilities()).extracting(Vulnerability::displayId)
                .containsExactly("CVE-2022-42003", "CVE-2022-42004", "GHSA-test-last-affected");
        Vulnerability first = page.vulnerabilities().getFirst();
        assertThat(first.id()).isEqualTo("GHSA-jjjh-jjxp-wpff");
        assertThat(first.severity()).isEqualTo(Severity.HOCH);
        assertThat(first.summary()).isEqualTo("Uncontrolled Resource Consumption in Jackson-databind");
        assertThat(first.url()).isEqualTo("https://osv.dev/vulnerability/GHSA-jjjh-jjxp-wpff");
        assertThat(first.ranges().getFirst().events()).hasSize(4);
        assertThat(first.ranges().getFirst().events().getFirst())
                .isEqualTo(new AffectedRange.Event(Kind.INTRODUCED, "2.4.0-rc1"));
    }

    @Test
    void keepsOnlyEcosystemRangesAndVersionsOfTheRequestedPackage() throws Exception {
        Vulnerability third = OsvParser.parse(Fixtures.read("osv-jackson-databind.json"), PACKAGE)
                .vulnerabilities().get(2);

        assertThat(third.severity()).isEqualTo(Severity.UNBEKANNT);
        assertThat(third.versions()).containsExactly("2.10.0");
        assertThat(third.ranges()).hasSize(1);
        assertThat(third.ranges().getFirst().events()).extracting(AffectedRange.Event::kind)
                .containsExactly(Kind.INTRODUCED, Kind.LAST_AFFECTED);
    }

    @Test
    void emptyResponseHasNoVulnerabilitiesAndNoToken() throws Exception {
        OsvParser.Page page = OsvParser.parse("{}", PACKAGE);

        assertThat(page.vulnerabilities()).isEqualTo(List.of());
        assertThat(page.nextPageToken()).isNull();
    }

    @Test
    void invalidResponsesAreRejected() {
        assertThatThrownBy(() -> OsvParser.parse("nope", PACKAGE)).isInstanceOf(ResponseFormatException.class);
        assertThatThrownBy(() -> OsvParser.parse("{\"vulns\":[{\"summary\":\"ohne id\"}]}", PACKAGE))
                .isInstanceOf(ResponseFormatException.class);
    }
}
```

`MavenMetadataParserTest.java`:

```java
package dev.nz3x.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MavenMetadataParserTest {

    @Test
    void readsAllVersionsInOrder() throws Exception {
        assertThat(MavenMetadataParser.parse(Fixtures.read("maven-metadata-jackson-databind.xml")))
                .extracting(MavenVersion::toString)
                .containsExactly("2.12.7", "2.12.7.1", "2.13.0", "2.13.4", "2.13.4.2", "2.13.5",
                        "2.14.0-rc1", "2.14.0", "2.14.1", "2.15.0", "3.0.0-rc1");
    }

    @Test
    void ignoresVersionElementsOutsideVersions() throws Exception {
        String xml = "<metadata><version>9.9</version><versioning><versions>"
                + "<version>1.0</version></versions></versioning></metadata>";

        assertThat(MavenMetadataParser.parse(xml)).extracting(MavenVersion::toString).containsExactly("1.0");
    }

    @Test
    void invalidXmlAndDoctypesAreRejected() {
        assertThatThrownBy(() -> MavenMetadataParser.parse("<kaputt")).isInstanceOf(ResponseFormatException.class);
        assertThatThrownBy(() -> MavenMetadataParser.parse(
                "<!DOCTYPE metadata [<!ENTITY x \"y\">]><metadata/>")).isInstanceOf(ResponseFormatException.class);
    }
}
```

`OssIndexParserTest.java`:

```java
package dev.nz3x.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OssIndexParserTest {
    private static final String CURRENT = "pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.0";

    @Test
    void parsesFindingsPerCoordinate() throws Exception {
        Map<String, List<Vulnerability>> reports = OssIndexParser.parse(Fixtures.read("ossindex-jackson-databind.json"));

        assertThat(reports).containsOnlyKeys(CURRENT,
                "pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.4.2",
                "pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.5");
        List<Vulnerability> current = reports.get(CURRENT);
        assertThat(current).extracting(Vulnerability::id).containsExactly("CVE-2022-42003", "sonatype-2024-0001");
        assertThat(current.get(0).aliases()).isEmpty();
        assertThat(current.get(0).severity()).isEqualTo(Severity.HOCH);
        assertThat(current.get(0).summary()).isEqualTo("[CVE-2022-42003] CWE-502: Deserialization of Untrusted Data");
        assertThat(current.get(0).url()).isEqualTo("https://ossindex.sonatype.org/vulnerability/CVE-2022-42003");
        assertThat(current.get(1).severity()).isEqualTo(Severity.KRITISCH);
        assertThat(reports.get("pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.5")).isEmpty();
    }

    @Test
    void cveBecomesPrimaryIdAndOwnIdAnAlias() throws Exception {
        String json = "[{\"coordinates\":\"pkg:maven/a/b@1\",\"vulnerabilities\":"
                + "[{\"id\":\"abc-123\",\"cve\":\"CVE-2020-1\",\"cvssScore\":4.0}]}]";

        Vulnerability v = OssIndexParser.parse(json).get("pkg:maven/a/b@1").getFirst();

        assertThat(v.id()).isEqualTo("CVE-2020-1");
        assertThat(v.aliases()).containsExactly("abc-123");
        assertThat(v.url()).isEqualTo("https://ossindex.sonatype.org/vulnerability/abc-123");
    }

    @Test
    void coordinateIsLowerCasePackageUrl() {
        assertThat(OssIndexParser.coordinate("Com.Example", "My-Lib", "1.0.0-RC1"))
                .isEqualTo("pkg:maven/com.example/my-lib@1.0.0-rc1");
    }

    @Test
    void invalidResponsesAreRejected() {
        assertThatThrownBy(() -> OssIndexParser.parse("{}")).isInstanceOf(ResponseFormatException.class);
        assertThatThrownBy(() -> OssIndexParser.parse("[{\"vulnerabilities\":[]}]"))
                .isInstanceOf(ResponseFormatException.class);
    }
}
```

- [ ] **Step 4: Tests ausführen, sie müssen fehlschlagen**

Run: `./gradlew test --tests "dev.nz3x.deplens.security.*ParserTest"`
Expected: FAIL – `cannot find symbol: class OsvParser`.

- [ ] **Step 5: `ResponseFormatException` und `Json` implementieren**

```java
package dev.nz3x.deplens.security;

import java.io.IOException;
import org.jetbrains.annotations.Nullable;

/** Die Antwort eines Dienstes ließ sich nicht lesen; wird wie ein Netzfehler behandelt. */
public final class ResponseFormatException extends IOException {
    public ResponseFormatException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
```

```java
package dev.nz3x.deplens.security;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Kleine, nachsichtige Lesehelfer für Gson-Objekte. */
final class Json {
    private Json() {
    }

    static JsonArray array(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    static @Nullable JsonObject object(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    static @Nullable String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    static String requiredString(JsonObject object, String key) {
        String value = string(object, key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Feld fehlt: " + key);
        }
        return value;
    }

    static double number(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsDouble()
                : 0.0;
    }

    static List<String> strings(JsonArray array) {
        List<String> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                result.add(element.getAsString());
            }
        }
        return result;
    }
}
```

- [ ] **Step 6: `OsvParser` implementieren**

```java
package dev.nz3x.deplens.security;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Liest Antworten von {@code POST https://api.osv.dev/v1/query}. */
public final class OsvParser {
    private static final String VULNERABILITY_URL = "https://osv.dev/vulnerability/";

    public record Page(List<Vulnerability> vulnerabilities, @Nullable String nextPageToken) {
        public Page {
            vulnerabilities = List.copyOf(vulnerabilities);
        }
    }

    private OsvParser() {
    }

    public static Page parse(String json, String packageName) throws ResponseFormatException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            List<Vulnerability> vulnerabilities = new ArrayList<>();
            for (JsonElement element : Json.array(root, "vulns")) {
                vulnerabilities.add(vulnerability(element.getAsJsonObject(), packageName));
            }
            String token = Json.string(root, "next_page_token");
            return new Page(vulnerabilities, token == null || token.isEmpty() ? null : token);
        } catch (RuntimeException e) {
            throw new ResponseFormatException("Ungültige OSV-Antwort", e);
        }
    }

    private static Vulnerability vulnerability(JsonObject json, String packageName) {
        String id = Json.requiredString(json, "id");
        JsonObject databaseSpecific = Json.object(json, "database_specific");
        Severity severity = Severity.fromLabel(databaseSpecific == null ? null : Json.string(databaseSpecific, "severity"));
        List<AffectedRange> ranges = new ArrayList<>();
        Set<String> versions = new LinkedHashSet<>();
        for (JsonElement element : Json.array(json, "affected")) {
            JsonObject affected = element.getAsJsonObject();
            JsonObject pkg = Json.object(affected, "package");
            if (pkg == null || !packageName.equals(Json.string(pkg, "name"))
                    || !"Maven".equals(Json.string(pkg, "ecosystem"))) {
                continue;
            }
            versions.addAll(Json.strings(Json.array(affected, "versions")));
            for (JsonElement rangeElement : Json.array(affected, "ranges")) {
                JsonObject range = rangeElement.getAsJsonObject();
                if ("ECOSYSTEM".equals(Json.string(range, "type"))) {
                    ranges.add(range(range));
                }
            }
        }
        String summary = Objects.requireNonNullElse(Json.string(json, "summary"), "");
        return new Vulnerability(id, Json.strings(Json.array(json, "aliases")), severity, summary,
                VULNERABILITY_URL + id, ranges, versions);
    }

    private static AffectedRange range(JsonObject range) {
        List<AffectedRange.Event> events = new ArrayList<>();
        for (JsonElement element : Json.array(range, "events")) {
            JsonObject event = element.getAsJsonObject();
            if (event.has("introduced")) {
                events.add(new AffectedRange.Event(AffectedRange.Kind.INTRODUCED, Json.requiredString(event, "introduced")));
            } else if (event.has("fixed")) {
                events.add(new AffectedRange.Event(AffectedRange.Kind.FIXED, Json.requiredString(event, "fixed")));
            } else if (event.has("last_affected")) {
                events.add(new AffectedRange.Event(AffectedRange.Kind.LAST_AFFECTED,
                        Json.requiredString(event, "last_affected")));
            }
        }
        return new AffectedRange(events);
    }
}
```

- [ ] **Step 7: `MavenMetadataParser` implementieren**

```java
package dev.nz3x.deplens.security;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Liest die Versionsliste aus einer {@code maven-metadata.xml} von Maven Central. */
public final class MavenMetadataParser {
    private MavenMetadataParser() {
    }

    public static List<MavenVersion> parse(String xml) throws ResponseFormatException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList nodes = document.getElementsByTagName("version");
            List<MavenVersion> versions = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                String text = node.getTextContent().strip();
                if ("versions".equals(node.getParentNode().getNodeName()) && !text.isEmpty()) {
                    versions.add(MavenVersion.of(text));
                }
            }
            return versions;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new ResponseFormatException("Ungültige maven-metadata.xml", e);
        }
    }
}
```

- [ ] **Step 8: `OssIndexParser` implementieren**

```java
package dev.nz3x.deplens.security;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Liest Antworten von {@code POST https://ossindex.sonatype.org/api/v3/component-report}. */
public final class OssIndexParser {
    private static final String VULNERABILITY_URL = "https://ossindex.sonatype.org/vulnerability/";

    private OssIndexParser() {
    }

    /** Coordinate im OSS-Index-Format, kleingeschrieben. */
    public static String coordinate(String groupId, String artifactId, String version) {
        return ("pkg:maven/" + groupId + "/" + artifactId + "@" + version).toLowerCase(Locale.ROOT);
    }

    /** Kleingeschriebene Coordinate → Lücken dieser Version. */
    public static Map<String, List<Vulnerability>> parse(String json) throws ResponseFormatException {
        try {
            Map<String, List<Vulnerability>> result = new LinkedHashMap<>();
            for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
                JsonObject report = element.getAsJsonObject();
                String coordinates = Json.requiredString(report, "coordinates").toLowerCase(Locale.ROOT);
                List<Vulnerability> vulnerabilities = new ArrayList<>();
                for (JsonElement vulnerability : Json.array(report, "vulnerabilities")) {
                    vulnerabilities.add(vulnerability(vulnerability.getAsJsonObject()));
                }
                result.put(coordinates, vulnerabilities);
            }
            return result;
        } catch (RuntimeException e) {
            throw new ResponseFormatException("Ungültige OSS-Index-Antwort", e);
        }
    }

    private static Vulnerability vulnerability(JsonObject json) {
        String id = Json.requiredString(json, "id");
        String cve = Json.string(json, "cve");
        String primary = cve != null && !cve.isEmpty() ? cve : id;
        List<String> aliases = primary.equals(id) ? List.of() : List.of(id);
        String title = Objects.requireNonNullElse(Json.string(json, "title"), primary);
        String url = Objects.requireNonNullElse(Json.string(json, "reference"), VULNERABILITY_URL + id);
        return new Vulnerability(primary, aliases, Severity.fromCvssScore(Json.number(json, "cvssScore")), title, url,
                List.of(), Set.of());
    }
}
```

- [ ] **Step 9: Tests ausführen, sie müssen grün sein**

Run: `./gradlew test --tests "dev.nz3x.deplens.security.*ParserTest"`
Expected: PASS (11 Tests).

- [ ] **Step 10: Commit und Push**

```bash
git add src
git commit -m "Add parsers for OSV, Maven Central and OSS Index

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push
```

---

### Task 4: `UpgradeAdvisor`

**Files:**
- Create: `src/main/java/dev/nz3x/deplens/security/UpgradeAdvisor.java`
- Test: `src/test/java/dev/nz3x/deplens/security/UpgradeAdvisorTest.java`

**Interfaces:**
- Consumes: `MavenVersion`, `Vulnerability`, `VulnerabilityMatcher`.
- Produces:
  - `UpgradeAdvisor.MAX_TRIES = 5`.
  - `record UpgradeAdvisor.Upgrade(@Nullable MavenVersion sameMajor, @Nullable MavenVersion latest)` mit `NONE` und `isEmpty()`.
  - `static List<MavenVersion> candidates(MavenVersion current, List<MavenVersion> available, List<Vulnerability> vulnerabilities)` – aufsteigend.
  - `static List<MavenVersion> toVerify(MavenVersion current, List<MavenVersion> candidates)`.
  - `static Upgrade choose(MavenVersion current, List<MavenVersion> candidates, Predicate<MavenVersion> rejected)`.

- [ ] **Step 1: Failing Test schreiben**

```java
package dev.nz3x.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nz3x.deplens.security.AffectedRange.Event;
import dev.nz3x.deplens.security.AffectedRange.Kind;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class UpgradeAdvisorTest {
    private static final List<MavenVersion> AVAILABLE = versions("2.12.7", "2.12.7.1", "2.13.0", "2.13.4", "2.13.4.2",
            "2.13.5", "2.14.0-rc1", "2.14.0", "2.14.1", "2.15.0", "3.0.0-rc1");
    private static final List<Vulnerability> VULNERABILITIES = List.of(
            vuln(new Event(Kind.INTRODUCED, "2.13.0"), new Event(Kind.FIXED, "2.13.4.2")),
            vuln(new Event(Kind.INTRODUCED, "0"), new Event(Kind.FIXED, "2.12.7.1"),
                    new Event(Kind.INTRODUCED, "2.13.0"), new Event(Kind.FIXED, "2.13.4")),
            vuln(new Event(Kind.INTRODUCED, "2.14.0"), new Event(Kind.LAST_AFFECTED, "2.14.1")));
    private static final MavenVersion CURRENT = MavenVersion.of("2.13.0");

    @Test
    void candidatesAreNewerStableAndUnaffected() {
        assertThat(UpgradeAdvisor.candidates(CURRENT, AVAILABLE, VULNERABILITIES))
                .extracting(MavenVersion::toString)
                .containsExactly("2.13.4.2", "2.13.5", "2.15.0");
    }

    @Test
    void choosesSmallestSameMajorAndLatest() {
        UpgradeAdvisor.Upgrade upgrade = choose(v -> false);

        assertThat(upgrade.sameMajor()).hasToString("2.13.4.2");
        assertThat(upgrade.latest()).hasToString("2.15.0");
        assertThat(upgrade.isEmpty()).isFalse();
    }

    @Test
    void rejectedCandidatesMoveToTheNextOne() {
        UpgradeAdvisor.Upgrade upgrade = choose(v -> v.toString().equals("2.13.4.2") || v.toString().equals("2.15.0"));

        assertThat(upgrade.sameMajor()).hasToString("2.13.5");
        assertThat(upgrade.latest()).hasToString("2.13.5");
    }

    @Test
    void noSameMajorCandidate() {
        UpgradeAdvisor.Upgrade upgrade = UpgradeAdvisor.choose(MavenVersion.of("1.0.0"),
                UpgradeAdvisor.candidates(MavenVersion.of("1.0.0"), versions("1.0.0", "2.0.0"), List.of()), v -> false);

        assertThat(upgrade.sameMajor()).isNull();
        assertThat(upgrade.latest()).hasToString("2.0.0");
    }

    @Test
    void noSafeVersionAtAll() {
        List<Vulnerability> everything = List.of(vuln(new Event(Kind.INTRODUCED, "0")));

        assertThat(UpgradeAdvisor.candidates(CURRENT, AVAILABLE, everything)).isEmpty();
        assertThat(UpgradeAdvisor.choose(CURRENT, List.of(), v -> false)).isEqualTo(UpgradeAdvisor.Upgrade.NONE);
        assertThat(UpgradeAdvisor.Upgrade.NONE.isEmpty()).isTrue();
    }

    @Test
    void givesUpAfterMaxTries() {
        List<MavenVersion> candidates = versions("2.0.1", "2.0.2", "2.0.3", "2.0.4", "2.0.5", "2.0.6", "2.0.7");

        UpgradeAdvisor.Upgrade upgrade = UpgradeAdvisor.choose(MavenVersion.of("2.0.0"), candidates,
                v -> !v.toString().equals("2.0.6") && !v.toString().equals("2.0.2"));

        assertThat(upgrade.sameMajor()).hasToString("2.0.2");
        assertThat(upgrade.latest()).hasToString("2.0.6");
        assertThat(UpgradeAdvisor.choose(MavenVersion.of("2.0.0"), candidates, v -> !v.toString().equals("2.0.6")
                && !v.toString().equals("2.0.1")).sameMajor()).hasToString("2.0.1");
        assertThat(UpgradeAdvisor.choose(MavenVersion.of("2.0.0"), candidates, v -> !v.toString().equals("2.0.6"))
                .sameMajor()).isNull();
    }

    @Test
    void toVerifyListsSameMajorFirstThenNewest() {
        List<MavenVersion> candidates = UpgradeAdvisor.candidates(CURRENT, AVAILABLE, VULNERABILITIES);

        assertThat(UpgradeAdvisor.toVerify(CURRENT, candidates)).extracting(MavenVersion::toString)
                .containsExactly("2.13.4.2", "2.13.5", "2.15.0");
    }

    private static UpgradeAdvisor.Upgrade choose(java.util.function.Predicate<MavenVersion> rejected) {
        return UpgradeAdvisor.choose(CURRENT, UpgradeAdvisor.candidates(CURRENT, AVAILABLE, VULNERABILITIES), rejected);
    }

    private static List<MavenVersion> versions(String... versions) {
        return Stream.of(versions).map(MavenVersion::of).toList();
    }

    private static Vulnerability vuln(Event... events) {
        return new Vulnerability("GHSA-x", List.of(), Severity.HOCH, "s", "u",
                List.of(new AffectedRange(List.of(events))), Set.of());
    }
}
```

Erklärung `givesUpAfterMaxTries`: Für „gleiche Major“ werden nur die ersten 5 Kandidaten (`2.0.1`–`2.0.5`) geprüft; ist nur `2.0.6` nicht abgelehnt, gibt es keinen Vorschlag. Für „neueste“ werden `2.0.7, 2.0.6, …` absteigend geprüft; `2.0.6` ist der zweite Versuch.

- [ ] **Step 2: Test ausführen, er muss fehlschlagen**

Run: `./gradlew test --tests "dev.nz3x.deplens.security.UpgradeAdvisorTest"`
Expected: FAIL – `cannot find symbol: class UpgradeAdvisor`.

- [ ] **Step 3: `UpgradeAdvisor` implementieren**

```java
package dev.nz3x.deplens.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/** Wählt sichere Upgrade-Versionen: kleinster Sprung in derselben Major und neueste. */
public final class UpgradeAdvisor {
    /** Höchstens so viele Kandidaten je Vorschlag werden gegengeprüft. */
    public static final int MAX_TRIES = 5;

    public record Upgrade(@Nullable MavenVersion sameMajor, @Nullable MavenVersion latest) {
        public static final Upgrade NONE = new Upgrade(null, null);

        public boolean isEmpty() {
            return sameMajor == null && latest == null;
        }
    }

    private UpgradeAdvisor() {
    }

    /** Stabile Versionen über {@code current}, die von keiner der Lücken betroffen sind, aufsteigend. */
    public static List<MavenVersion> candidates(MavenVersion current, List<MavenVersion> available,
                                                List<Vulnerability> vulnerabilities) {
        return available.stream()
                .filter(version -> !version.isPreRelease())
                .filter(version -> version.compareTo(current) > 0)
                .filter(version -> vulnerabilities.stream().noneMatch(v -> VulnerabilityMatcher.affects(v, version)))
                .distinct()
                .sorted()
                .toList();
    }

    /** Alle Kandidaten, die {@link #choose} höchstens ansieht – für eine gemeinsame Gegenprüfung. */
    public static List<MavenVersion> toVerify(MavenVersion current, List<MavenVersion> candidates) {
        Set<MavenVersion> result = new LinkedHashSet<>();
        candidates.stream().filter(version -> version.major() == current.major()).limit(MAX_TRIES).forEach(result::add);
        candidates.reversed().stream().limit(MAX_TRIES).forEach(result::add);
        return List.copyOf(result);
    }

    /** Erster nicht abgelehnter Kandidat je Vorschlag, jeweils innerhalb von {@link #MAX_TRIES} Versuchen. */
    public static Upgrade choose(MavenVersion current, List<MavenVersion> candidates, Predicate<MavenVersion> rejected) {
        MavenVersion sameMajor = candidates.stream()
                .filter(version -> version.major() == current.major())
                .limit(MAX_TRIES)
                .filter(rejected.negate())
                .findFirst()
                .orElse(null);
        MavenVersion latest = candidates.reversed().stream()
                .limit(MAX_TRIES)
                .filter(rejected.negate())
                .findFirst()
                .orElse(null);
        return new Upgrade(sameMajor, latest);
    }
}
```

- [ ] **Step 4: Test ausführen, er muss grün sein**

Run: `./gradlew test --tests "dev.nz3x.deplens.security.UpgradeAdvisorTest"`
Expected: PASS (7 Tests).

- [ ] **Step 5: Commit und Push**

```bash
git add src
git commit -m "Add upgrade advisor

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push
```

---

### Task 5: Berichte, Schnittstellen und `SecurityChecker`

**Files:**
- Create: `security/Artifact.java`, `security/Finding.java`, `security/SecurityReport.java`, `security/SecurityLinks.java`, `security/HttpStatusException.java`, `security/SecurityClient.java`, `security/ResponseCache.java`, `security/SecurityChecker.java`
- Test: `security/FakeSecurityClient.java`, `security/InMemoryCache.java`, `security/SecurityCheckerTest.java`, `security/SecurityLinksTest.java`

**Interfaces:**
- Consumes: alles aus Task 1–4.
- Produces:
  - `record Artifact(String groupId, String artifactId, String version)` mit `coordinates()` (`g:a:v`) und `packageName()` (`g:a`).
  - `record Finding(Vulnerability vulnerability, @Nullable MavenVersion fixedIn)`.
  - `record SecurityReport(Status status, List<Finding> findings, UpgradeAdvisor.Upgrade upgrade, @Nullable String problem)`, `enum SecurityReport.Status { SICHER, VERWUNDBAR, NICHT_PRUEFBAR, FEHLER }`, Fabriken `safe()`, `vulnerable(List<Finding>, Upgrade)`, `notCheckable(String)`, `error(String)`.
  - `SecurityLinks.mvnRepository(g, a, v)`, `SecurityLinks.osvPackage(g, a)`.
  - `class HttpStatusException extends IOException` mit `statusCode()`.
  - `interface SecurityClient { String osvQuery(String packageName, @Nullable String pageToken); @Nullable String mavenMetadata(String groupId, String artifactId); String ossIndexReport(List<String> coordinates); }` (alle `throws IOException`; `mavenMetadata` → `null` bei 404).
  - `interface ResponseCache { @Nullable String get(String key, Duration maxAge); void put(String key, String value); }`.
  - `new SecurityChecker(SecurityClient, ResponseCache, Duration maxAge, Executor, SecurityChecker.Sleeper)`; `void check(List<Artifact>, boolean forceRefresh, boolean ossIndexEnabled, BooleanSupplier cancelled, SecurityChecker.Listener)`.
  - `SecurityChecker.Listener { progress(int done, int total); reportReady(Artifact, SecurityReport); notice(Notice); }`, `enum SecurityChecker.Notice { OSS_INDEX_UNAUTHORIZED, OSS_INDEX_FAILED }`, `SecurityChecker.Sleeper { void sleep(Duration) throws InterruptedException; }`.
  - Cache-Schlüssel: `osv/<g:a>/<pageToken oder leer>`, `metadata/<g:a>` (Wert `""` = 404), `oss/<sortierte Coordinates mit Komma>`.

- [ ] **Step 1: Test-Doubles schreiben**

`FakeSecurityClient.java`:

```java
package dev.nz3x.deplens.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Antwortet aus Maps und protokolliert jeden Aufruf. */
final class FakeSecurityClient implements SecurityClient {
    /** Schlüssel: {@code packageName + "|" + pageToken} (leerer Token für die erste Seite). */
    final Map<String, String> osv = new HashMap<>();
    /** Schlüssel: {@code packageName}; fehlender Eintrag = 404. */
    final Map<String, String> metadata = new HashMap<>();
    /** Schlüssel: {@code packageName}; wird von {@link #osvQuery} geworfen. */
    final Map<String, IOException> failures = new HashMap<>();
    final List<String> calls = new ArrayList<>();
    String ossIndex = "[]";
    @Nullable IOException ossIndexFailure;
    int tooManyRequests;

    @Override
    public String osvQuery(String packageName, @Nullable String pageToken) throws IOException {
        String token = pageToken == null ? "" : pageToken;
        calls.add("osv " + packageName + " " + token);
        IOException failure = failures.get(packageName);
        if (failure != null) {
            throw failure;
        }
        if (tooManyRequests > 0) {
            tooManyRequests--;
            throw new HttpStatusException(429, "osv");
        }
        return osv.getOrDefault(packageName + "|" + token, "{}");
    }

    @Override
    public @Nullable String mavenMetadata(String groupId, String artifactId) {
        calls.add("metadata " + groupId + ":" + artifactId);
        return metadata.get(groupId + ":" + artifactId);
    }

    @Override
    public String ossIndexReport(List<String> coordinates) throws IOException {
        calls.add("oss " + coordinates.size());
        if (ossIndexFailure != null) {
            throw ossIndexFailure;
        }
        return ossIndex;
    }
}
```

`InMemoryCache.java`:

```java
package dev.nz3x.deplens.security;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Cache ohne Ablauf für Tests. */
final class InMemoryCache implements ResponseCache {
    final Map<String, String> values = new HashMap<>();

    @Override
    public @Nullable String get(String key, Duration maxAge) {
        return values.get(key);
    }

    @Override
    public void put(String key, String value) {
        values.put(key, value);
    }
}
```

- [ ] **Step 2: Failing Tests schreiben**

`SecurityLinksTest.java`:

```java
package dev.nz3x.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecurityLinksTest {

    @Test
    void links() {
        assertThat(SecurityLinks.mvnRepository("com.fasterxml.jackson.core", "jackson-databind", "2.13.0"))
                .isEqualTo("https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind/2.13.0");
        assertThat(SecurityLinks.osvPackage("com.fasterxml.jackson.core", "jackson-databind"))
                .isEqualTo("https://osv.dev/list?ecosystem=Maven&q=com.fasterxml.jackson.core%3Ajackson-databind");
    }
}
```

`SecurityCheckerTest.java`:

```java
package dev.nz3x.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nz3x.deplens.security.SecurityChecker.Notice;
import dev.nz3x.deplens.security.SecurityReport.Status;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecurityCheckerTest {
    private static final String GROUP = "com.fasterxml.jackson.core";
    private static final String PKG = GROUP + ":jackson-databind";

    private final FakeSecurityClient client = new FakeSecurityClient();
    private final InMemoryCache cache = new InMemoryCache();
    private final List<Duration> sleeps = new ArrayList<>();
    private final SecurityChecker checker =
            new SecurityChecker(client, cache, Duration.ofHours(24), Runnable::run, sleeps::add);
    private final RecordingListener listener = new RecordingListener();

    @BeforeEach
    void jacksonResponses() {
        client.osv.put(PKG + "|", Fixtures.read("osv-jackson-databind.json"));
        client.osv.put(PKG + "|page-2", "{}");
        client.metadata.put(PKG, Fixtures.read("maven-metadata-jackson-databind.xml"));
    }

    @Test
    void vulnerableVersionGetsSortedFindingsAndBothUpgrades() {
        check(false, false, databind("2.13.0"));

        SecurityReport report = listener.report("2.13.0");
        assertThat(report.status()).isEqualTo(Status.VERWUNDBAR);
        assertThat(report.findings()).extracting(f -> f.vulnerability().displayId())
                .containsExactly("CVE-2022-42003", "CVE-2022-42004");
        assertThat(report.findings()).extracting(f -> String.valueOf(f.fixedIn()))
                .containsExactly("2.13.4.2", "2.13.4");
        assertThat(report.upgrade().sameMajor()).hasToString("2.13.4.2");
        assertThat(report.upgrade().latest()).hasToString("2.15.0");
        assertThat(report.problem()).isNull();
    }

    @Test
    void safeVersion() {
        check(false, false, databind("2.15.0"));

        SecurityReport report = listener.report("2.15.0");
        assertThat(report.status()).isEqualTo(Status.SICHER);
        assertThat(report.findings()).isEmpty();
    }

    @Test
    void versionsOfOnePackageShareTheRequests() {
        check(false, false, databind("2.13.0"), databind("2.15.0"));

        assertThat(client.calls).containsExactly("osv " + PKG + " ", "osv " + PKG + " page-2", "metadata " + PKG);
        assertThat(listener.reports).hasSize(2);
        assertThat(listener.progress).containsExactly("1/1");
    }

    @Test
    void laterPagesAreFollowed() {
        client.osv.put(PKG + "|page-2", "{\"vulns\":[{\"id\":\"GHSA-page-two\",\"affected\":[{\"package\":"
                + "{\"ecosystem\":\"Maven\",\"name\":\"" + PKG + "\"},\"versions\":[\"2.15.0\"]}]}]}");

        check(false, false, databind("2.15.0"));

        assertThat(listener.report("2.15.0").findings()).extracting(f -> f.vulnerability().id())
                .containsExactly("GHSA-page-two");
    }

    @Test
    void unknownToMavenCentralWithoutFindingsIsNotCheckable() {
        client.metadata.remove(PKG);

        check(false, false, databind("2.15.0"));

        SecurityReport report = listener.report("2.15.0");
        assertThat(report.status()).isEqualTo(Status.NICHT_PRUEFBAR);
        assertThat(report.problem()).isEqualTo("Nicht auf Maven Central gefunden");
    }

    @Test
    void unknownToMavenCentralButVulnerableHasNoUpgrade() {
        client.metadata.remove(PKG);

        check(false, false, databind("2.13.0"));

        SecurityReport report = listener.report("2.13.0");
        assertThat(report.status()).isEqualTo(Status.VERWUNDBAR);
        assertThat(report.upgrade().isEmpty()).isTrue();
    }

    @Test
    void secondCheckIsServedFromCache() {
        check(false, false, databind("2.13.0"));
        int calls = client.calls.size();

        check(false, false, databind("2.13.0"));

        assertThat(client.calls).hasSize(calls);
        assertThat(listener.reports).hasSize(1);
    }

    @Test
    void forceRefreshBypassesTheCache() {
        check(false, false, databind("2.13.0"));
        int calls = client.calls.size();

        check(true, false, databind("2.13.0"));

        assertThat(client.calls).hasSize(2 * calls);
    }

    @Test
    void brokenCacheEntryIsReloaded() {
        cache.values.put("metadata/" + PKG, "<kaputt");

        check(false, false, databind("2.13.0"));

        assertThat(client.calls).contains("metadata " + PKG);
        assertThat(listener.report("2.13.0").upgrade().latest()).hasToString("2.15.0");
    }

    @Test
    void failedPackageIsAnErrorOthersContinue() {
        client.failures.put(PKG, new IOException("Netz weg"));
        client.metadata.put("org.slf4j:slf4j-api",
                "<metadata><versioning><versions><version>2.0.17</version></versions></versioning></metadata>");

        check(false, false, databind("2.13.0"), new Artifact("org.slf4j", "slf4j-api", "2.0.17"));

        assertThat(listener.report("2.13.0").status()).isEqualTo(Status.FEHLER);
        assertThat(listener.report("2.13.0").problem()).isEqualTo("Netz weg");
        assertThat(listener.reports.get("org.slf4j:slf4j-api:2.0.17").status()).isEqualTo(Status.SICHER);
    }

    @Test
    void tooManyRequestsIsRetriedOnceAfterTwoSeconds() {
        client.tooManyRequests = 1;

        check(false, false, databind("2.13.0"));

        assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
        assertThat(listener.report("2.13.0").status()).isEqualTo(Status.VERWUNDBAR);
    }

    @Test
    void tooManyRequestsTwiceIsAnError() {
        client.tooManyRequests = 2;

        check(false, false, databind("2.13.0"));

        assertThat(listener.report("2.13.0").status()).isEqualTo(Status.FEHLER);
        assertThat(listener.report("2.13.0").problem()).isEqualTo("HTTP 429");
    }

    @Test
    void invalidResponseIsAnErrorAndNotCached() {
        client.metadata.put(PKG, "<kaputt");

        check(false, false, databind("2.13.0"));

        assertThat(listener.report("2.13.0").status()).isEqualTo(Status.FEHLER);
        assertThat(cache.values).doesNotContainKey("metadata/" + PKG);
    }

    @Test
    void ossIndexFindingsAreMergedWithoutDuplicates() {
        client.ossIndex = Fixtures.read("ossindex-jackson-databind.json");

        check(false, true, databind("2.13.0"));

        assertThat(listener.report("2.13.0").findings()).extracting(f -> f.vulnerability().displayId())
                .containsExactly("sonatype-2024-0001", "CVE-2022-42003", "CVE-2022-42004");
    }

    @Test
    void ossIndexRejectsACandidate() {
        client.ossIndex = Fixtures.read("ossindex-jackson-databind.json");

        check(false, true, databind("2.13.0"));

        assertThat(listener.report("2.13.0").upgrade().sameMajor()).hasToString("2.13.5");
        assertThat(listener.report("2.13.0").upgrade().latest()).hasToString("2.15.0");
        assertThat(client.calls).contains("oss 1", "oss 3");
    }

    @Test
    void ossIndexUnauthorizedFallsBackToOsv() {
        client.ossIndexFailure = new HttpStatusException(401, "oss");

        check(false, true, databind("2.13.0"));

        assertThat(listener.notices).containsExactly(Notice.OSS_INDEX_UNAUTHORIZED);
        assertThat(listener.report("2.13.0").findings()).hasSize(2);
    }

    @Test
    void otherOssIndexFailureIsNoticed() {
        client.ossIndexFailure = new IOException("weg");

        check(false, true, databind("2.13.0"));

        assertThat(listener.notices).containsExactly(Notice.OSS_INDEX_FAILED);
        assertThat(listener.report("2.13.0").status()).isEqualTo(Status.VERWUNDBAR);
    }

    @Test
    void ossIndexIsNotAskedWhenDisabled() {
        check(false, false, databind("2.13.0"));

        assertThat(client.calls).noneMatch(call -> call.startsWith("oss"));
    }

    @Test
    void cancelledCheckReportsNothing() {
        checker.check(List.of(databind("2.13.0")), false, false, () -> true, listener);

        assertThat(listener.reports).isEmpty();
        assertThat(client.calls).isEmpty();
    }

    private void check(boolean forceRefresh, boolean ossIndex, Artifact... artifacts) {
        checker.check(List.of(artifacts), forceRefresh, ossIndex, () -> false, listener);
    }

    private static Artifact databind(String version) {
        return new Artifact(GROUP, "jackson-databind", version);
    }

    private static final class RecordingListener implements SecurityChecker.Listener {
        final Map<String, SecurityReport> reports = new HashMap<>();
        final List<String> progress = new ArrayList<>();
        final List<Notice> notices = new ArrayList<>();

        SecurityReport report(String version) {
            return reports.get(PKG + ":" + version);
        }

        @Override
        public void progress(int done, int total) {
            progress.add(done + "/" + total);
        }

        @Override
        public void reportReady(Artifact artifact, SecurityReport report) {
            reports.put(artifact.coordinates(), report);
        }

        @Override
        public void notice(Notice notice) {
            notices.add(notice);
        }
    }
}
```

Hinweis zu `ossIndexRejectsACandidate`: Erste OSS-Anfrage = aktuelle Version (1 Coordinate), zweite = Gegenprüfung `2.13.4.2, 2.13.5, 2.15.0` (3 Coordinates). Die Fixture meldet für `2.13.4.2` eine Lücke, deshalb rückt „gleiche Major“ auf `2.13.5`.

- [ ] **Step 3: Tests ausführen, sie müssen fehlschlagen**

Run: `./gradlew test --tests "dev.nz3x.deplens.security.Security*"`
Expected: FAIL – `cannot find symbol: class SecurityChecker`.

- [ ] **Step 4: Kleine Typen implementieren**

`Artifact.java`:

```java
package dev.nz3x.deplens.security;

/** Ein zu prüfendes Artefakt. */
public record Artifact(String groupId, String artifactId, String version) {

    public String coordinates() {
        return groupId + ":" + artifactId + ":" + version;
    }

    public String packageName() {
        return groupId + ":" + artifactId;
    }
}
```

`Finding.java`:

```java
package dev.nz3x.deplens.security;

import org.jetbrains.annotations.Nullable;

/** Eine Lücke, die die geprüfte Version betrifft, mit der Version, die sie behebt (falls bekannt). */
public record Finding(Vulnerability vulnerability, @Nullable MavenVersion fixedIn) {
}
```

`SecurityReport.java`:

```java
package dev.nz3x.deplens.security;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Ergebnis der Prüfung eines Artefakts. */
public record SecurityReport(Status status, List<Finding> findings, UpgradeAdvisor.Upgrade upgrade,
                             @Nullable String problem) {

    public enum Status { SICHER, VERWUNDBAR, NICHT_PRUEFBAR, FEHLER }

    public SecurityReport {
        findings = List.copyOf(findings);
    }

    public static SecurityReport safe() {
        return new SecurityReport(Status.SICHER, List.of(), UpgradeAdvisor.Upgrade.NONE, null);
    }

    public static SecurityReport vulnerable(List<Finding> findings, UpgradeAdvisor.Upgrade upgrade) {
        return new SecurityReport(Status.VERWUNDBAR, findings, upgrade, null);
    }

    public static SecurityReport notCheckable(String reason) {
        return new SecurityReport(Status.NICHT_PRUEFBAR, List.of(), UpgradeAdvisor.Upgrade.NONE, reason);
    }

    public static SecurityReport error(String reason) {
        return new SecurityReport(Status.FEHLER, List.of(), UpgradeAdvisor.Upgrade.NONE, reason);
    }
}
```

`SecurityLinks.java`:

```java
package dev.nz3x.deplens.security;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Links zum Nachschlagen im Browser. */
public final class SecurityLinks {
    private SecurityLinks() {
    }

    public static String mvnRepository(String groupId, String artifactId, String version) {
        return "https://mvnrepository.com/artifact/" + groupId + "/" + artifactId + "/" + version;
    }

    public static String osvPackage(String groupId, String artifactId) {
        return "https://osv.dev/list?ecosystem=Maven&q="
                + URLEncoder.encode(groupId + ":" + artifactId, StandardCharsets.UTF_8);
    }
}
```

`HttpStatusException.java`:

```java
package dev.nz3x.deplens.security;

import java.io.IOException;

/** Ein Dienst hat mit einem HTTP-Fehlerstatus geantwortet. */
public final class HttpStatusException extends IOException {
    private final int statusCode;

    public HttpStatusException(int statusCode, String url) {
        super("HTTP " + statusCode + " – " + url);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
```

`SecurityClient.java`:

```java
package dev.nz3x.deplens.security;

import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Rohzugriff auf OSV, Maven Central und OSS Index. */
public interface SecurityClient {

    /** Roh-JSON einer OSV-Anfrage für {@code group:artifact}; {@code pageToken} für Folgeseiten. */
    String osvQuery(String packageName, @Nullable String pageToken) throws IOException;

    /** Inhalt der {@code maven-metadata.xml} oder {@code null}, wenn Maven Central das Artefakt nicht kennt. */
    @Nullable String mavenMetadata(String groupId, String artifactId) throws IOException;

    /** Roh-JSON eines OSS-Index-Berichts für bis zu 128 Coordinates. */
    String ossIndexReport(List<String> coordinates) throws IOException;
}
```

`ResponseCache.java`:

```java
package dev.nz3x.deplens.security;

import java.time.Duration;
import org.jetbrains.annotations.Nullable;

/** Zwischenspeicher für Rohantworten. */
public interface ResponseCache {

    /** Wert, falls vorhanden und nicht älter als {@code maxAge}; sonst {@code null}. */
    @Nullable String get(String key, Duration maxAge);

    void put(String key, String value);
}
```

- [ ] **Step 5: `SecurityChecker` implementieren**

```java
package dev.nz3x.deplens.security;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.Nullable;

/** Prüft Artefakte gegen OSV, Maven Central und optional OSS Index. Blockiert – nur im Hintergrund aufrufen. */
public final class SecurityChecker {

    public enum Notice { OSS_INDEX_UNAUTHORIZED, OSS_INDEX_FAILED }

    public interface Listener {
        void progress(int done, int total);

        void reportReady(Artifact artifact, SecurityReport report);

        void notice(Notice notice);
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    static final Duration RETRY_DELAY = Duration.ofSeconds(2);
    private static final int OSS_INDEX_BATCH_SIZE = 128;
    private static final int MAX_OSV_PAGES = 50;
    private static final String NOT_FOUND = "";

    private record PackageData(List<Vulnerability> osv, @Nullable List<MavenVersion> versions, @Nullable String error) {
    }

    private record Evaluation(MavenVersion current, PackageData data, List<Finding> osvFindings,
                              List<MavenVersion> candidates) {
    }

    @FunctionalInterface
    private interface Load {
        String get() throws IOException;
    }

    @FunctionalInterface
    private interface Parse<T> {
        T apply(String raw) throws ResponseFormatException;
    }

    private final SecurityClient client;
    private final ResponseCache cache;
    private final Duration maxAge;
    private final Executor executor;
    private final Sleeper sleeper;

    public SecurityChecker(SecurityClient client, ResponseCache cache, Duration maxAge, Executor executor,
                           Sleeper sleeper) {
        this.client = client;
        this.cache = cache;
        this.maxAge = maxAge;
        this.executor = executor;
        this.sleeper = sleeper;
    }

    public void check(List<Artifact> artifacts, boolean forceRefresh, boolean ossIndexEnabled,
                      BooleanSupplier cancelled, Listener listener) {
        Map<String, PackageData> packages = loadPackages(artifacts, forceRefresh, cancelled, listener);
        if (packages == null || cancelled.getAsBoolean()) {
            return;
        }
        Map<Artifact, Evaluation> evaluations = new LinkedHashMap<>();
        for (Artifact artifact : artifacts) {
            evaluations.put(artifact, evaluate(artifact, packages.get(artifact.packageName())));
        }
        Map<String, List<Vulnerability>> ossIndex = Map.of();
        if (ossIndexEnabled) {
            try {
                ossIndex = loadOssIndex(evaluations, forceRefresh, cancelled);
            } catch (HttpStatusException e) {
                boolean unauthorized = e.statusCode() == 401 || e.statusCode() == 403;
                listener.notice(unauthorized ? Notice.OSS_INDEX_UNAUTHORIZED : Notice.OSS_INDEX_FAILED);
            } catch (IOException e) {
                listener.notice(Notice.OSS_INDEX_FAILED);
            } catch (CancellationException e) {
                return;
            }
        }
        for (Map.Entry<Artifact, Evaluation> entry : evaluations.entrySet()) {
            if (cancelled.getAsBoolean()) {
                return;
            }
            listener.reportReady(entry.getKey(), report(entry.getKey(), entry.getValue(), ossIndex));
        }
    }

    /** Lädt OSV-Lücken und Versionen je Paket parallel; {@code null} bei Abbruch. */
    private @Nullable Map<String, PackageData> loadPackages(List<Artifact> artifacts, boolean forceRefresh,
                                                            BooleanSupplier cancelled, Listener listener) {
        List<String> packageNames = artifacts.stream().map(Artifact::packageName).distinct().toList();
        AtomicInteger done = new AtomicInteger();
        Map<String, CompletableFuture<PackageData>> futures = new LinkedHashMap<>();
        for (String packageName : packageNames) {
            futures.put(packageName, CompletableFuture
                    .supplyAsync(() -> loadPackage(packageName, forceRefresh, cancelled), executor)
                    .whenComplete((data, error) -> {
                        if (error == null) {
                            listener.progress(done.incrementAndGet(), packageNames.size());
                        }
                    }));
        }
        Map<String, PackageData> result = new HashMap<>();
        try {
            for (Map.Entry<String, CompletableFuture<PackageData>> future : futures.entrySet()) {
                result.put(future.getKey(), future.getValue().join());
            }
        } catch (CancellationException e) {
            return null;
        } catch (CompletionException e) {
            if (e.getCause() instanceof CancellationException) {
                return null;
            }
            throw e;
        }
        return result;
    }

    private PackageData loadPackage(String packageName, boolean forceRefresh, BooleanSupplier cancelled) {
        String[] parts = packageName.split(":", 2);
        try {
            List<Vulnerability> osv = new ArrayList<>();
            String token = null;
            for (int page = 0; page < MAX_OSV_PAGES; page++) {
                String pageToken = token;
                OsvParser.Page result = cached("osv/" + packageName + "/" + (pageToken == null ? "" : pageToken),
                        forceRefresh, cancelled, () -> client.osvQuery(packageName, pageToken),
                        raw -> OsvParser.parse(raw, packageName));
                osv.addAll(result.vulnerabilities());
                token = result.nextPageToken();
                if (token == null) {
                    break;
                }
            }
            List<MavenVersion> versions = cached("metadata/" + packageName, forceRefresh, cancelled,
                    () -> Objects.requireNonNullElse(client.mavenMetadata(parts[0], parts[1]), NOT_FOUND),
                    raw -> raw.equals(NOT_FOUND) ? null : MavenMetadataParser.parse(raw));
            return new PackageData(osv, versions, null);
        } catch (IOException e) {
            return new PackageData(List.of(), null, describe(e));
        }
    }

    private static Evaluation evaluate(Artifact artifact, PackageData data) {
        MavenVersion current = MavenVersion.of(artifact.version());
        List<Finding> findings = data.osv().stream()
                .filter(vulnerability -> VulnerabilityMatcher.affects(vulnerability, current))
                .map(vulnerability -> new Finding(vulnerability, VulnerabilityMatcher.fixedIn(vulnerability, current)))
                .toList();
        List<MavenVersion> candidates = data.versions() == null
                ? List.of()
                : UpgradeAdvisor.candidates(current, data.versions(), data.osv());
        return new Evaluation(current, data, findings, candidates);
    }

    /** Erst die aktuellen Versionen, dann die Gegenprüfung der Kandidaten verwundbarer Artefakte. */
    private Map<String, List<Vulnerability>> loadOssIndex(Map<Artifact, Evaluation> evaluations, boolean forceRefresh,
                                                          BooleanSupplier cancelled) throws IOException {
        List<String> current = new ArrayList<>();
        evaluations.forEach((artifact, evaluation) -> {
            if (evaluation.data().error() == null) {
                current.add(ossCoordinate(artifact, artifact.version()));
            }
        });
        Map<String, List<Vulnerability>> result = new HashMap<>(ossIndexReports(current, forceRefresh, cancelled));
        List<String> toVerify = new ArrayList<>();
        evaluations.forEach((artifact, evaluation) -> {
            boolean vulnerable = !evaluation.osvFindings().isEmpty()
                    || !result.getOrDefault(ossCoordinate(artifact, artifact.version()), List.of()).isEmpty();
            if (vulnerable && evaluation.data().versions() != null) {
                for (MavenVersion candidate : UpgradeAdvisor.toVerify(evaluation.current(), evaluation.candidates())) {
                    toVerify.add(ossCoordinate(artifact, candidate.toString()));
                }
            }
        });
        result.putAll(ossIndexReports(toVerify, forceRefresh, cancelled));
        return result;
    }

    private Map<String, List<Vulnerability>> ossIndexReports(List<String> coordinates, boolean forceRefresh,
                                                             BooleanSupplier cancelled) throws IOException {
        Map<String, List<Vulnerability>> result = new HashMap<>();
        List<String> sorted = coordinates.stream().distinct().sorted().toList();
        for (int from = 0; from < sorted.size(); from += OSS_INDEX_BATCH_SIZE) {
            List<String> batch = sorted.subList(from, Math.min(from + OSS_INDEX_BATCH_SIZE, sorted.size()));
            result.putAll(cached("oss/" + String.join(",", batch), forceRefresh, cancelled,
                    () -> client.ossIndexReport(batch), OssIndexParser::parse));
        }
        return result;
    }

    private static SecurityReport report(Artifact artifact, Evaluation evaluation,
                                         Map<String, List<Vulnerability>> ossIndex) {
        PackageData data = evaluation.data();
        if (data.error() != null) {
            return SecurityReport.error(data.error());
        }
        List<Finding> findings = merge(evaluation.osvFindings(),
                ossIndex.getOrDefault(ossCoordinate(artifact, artifact.version()), List.of()));
        if (data.versions() == null && findings.isEmpty()) {
            return SecurityReport.notCheckable("Nicht auf Maven Central gefunden");
        }
        if (findings.isEmpty()) {
            return SecurityReport.safe();
        }
        UpgradeAdvisor.Upgrade upgrade = data.versions() == null
                ? UpgradeAdvisor.Upgrade.NONE
                : UpgradeAdvisor.choose(evaluation.current(), evaluation.candidates(),
                        candidate -> !ossIndex.getOrDefault(ossCoordinate(artifact, candidate.toString()), List.of())
                                .isEmpty());
        return SecurityReport.vulnerable(findings, upgrade);
    }

    /** OSV-Funde plus OSS-Index-Funde, deren IDs/Aliase noch nicht vorkommen; sortiert nach Schwere, dann ID. */
    private static List<Finding> merge(List<Finding> osv, List<Vulnerability> ossIndex) {
        List<Finding> result = new ArrayList<>(osv);
        Set<String> known = new HashSet<>();
        osv.forEach(finding -> known.addAll(finding.vulnerability().allIds()));
        for (Vulnerability vulnerability : ossIndex) {
            if (Collections.disjoint(known, vulnerability.allIds())) {
                result.add(new Finding(vulnerability, null));
                known.addAll(vulnerability.allIds());
            }
        }
        result.sort(Comparator.comparing((Finding finding) -> finding.vulnerability().severity())
                .thenComparing(finding -> finding.vulnerability().displayId()));
        return result;
    }

    private static String ossCoordinate(Artifact artifact, String version) {
        return OssIndexParser.coordinate(artifact.groupId(), artifact.artifactId(), version);
    }

    /** Cache lesen (kaputte Einträge ignorieren), sonst laden, parsen und erst danach speichern. */
    private <T> T cached(String key, boolean forceRefresh, BooleanSupplier cancelled, Load load, Parse<T> parse)
            throws IOException {
        if (!forceRefresh) {
            String hit = cache.get(key, maxAge);
            if (hit != null) {
                try {
                    return parse.apply(hit);
                } catch (ResponseFormatException ignored) {
                    // kaputter Eintrag: neu laden
                }
            }
        }
        if (cancelled.getAsBoolean()) {
            throw new CancellationException();
        }
        String raw = withRetry(load);
        T parsed = parse.apply(raw);
        cache.put(key, raw);
        return parsed;
    }

    private String withRetry(Load load) throws IOException {
        try {
            return load.get();
        } catch (HttpStatusException e) {
            if (e.statusCode() != 429) {
                throw e;
            }
            try {
                sleeper.sleep(RETRY_DELAY);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Unterbrochen", interrupted);
            }
            return load.get();
        }
    }

    private static String describe(IOException e) {
        if (e instanceof HttpStatusException status) {
            return "HTTP " + status.statusCode();
        }
        return Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName());
    }
}
```

- [ ] **Step 6: Tests ausführen, sie müssen grün sein**

Run: `./gradlew test --tests "dev.nz3x.deplens.security.*"`
Expected: PASS (alle Tests aus Task 1–5).

- [ ] **Step 7: Commit und Push**

```bash
git add src
git commit -m "Add security checker

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push
```

---

### Task 6: Datei-Cache, HTTP-Client, Einstellungen und Service

**Files:**
- Create: `security/FileResponseCache.java`, `security/HttpSecurityClient.java`, `security/SecurityService.java`, `settings/DepLensSettings.java`, `settings/DepLensConfigurable.java`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/java/dev/nz3x/deplens/security/FileResponseCacheTest.java`

**Interfaces:**
- Consumes: `ResponseCache`, `SecurityClient`, `HttpStatusException`, `SecurityChecker`, `Artifact`.
- Produces:
  - `new FileResponseCache(Path directory, Clock clock)`.
  - `new HttpSecurityClient(@Nullable String ossIndexUser, @Nullable String ossIndexToken)`.
  - `SecurityService.getInstance(Project)`; `void check(List<Artifact>, boolean forceRefresh, BooleanSupplier cancelled, SecurityChecker.Listener)` (blockiert); `void resetOssIndexRejection()`.
  - `DepLensSettings.getInstance()`, `isSecurityEnabled()`, `cacheDuration()`, `ossIndexUser()`, `@Nullable ossIndexToken()` (blockiert), `storeOssIndexToken(@Nullable String)` (blockiert), `notifyChanged()`, `DepLensSettings.TOPIC` mit `DepLensSettings.Listener#settingsChanged()`.

- [ ] **Step 1: Failing Test für den Datei-Cache schreiben**

```java
package dev.nz3x.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileResponseCacheTest {
    private static final Duration DAY = Duration.ofHours(24);

    @TempDir
    Path dir;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-16T10:00:00Z"));

    @Test
    void storedValueIsReturnedWithinMaxAge() {
        FileResponseCache cache = new FileResponseCache(dir, clock);
        cache.put("osv/a:b/", "{\"vulns\":[]}");
        clock.advance(Duration.ofHours(23));

        assertThat(cache.get("osv/a:b/", DAY)).isEqualTo("{\"vulns\":[]}");
    }

    @Test
    void expiredValueIsIgnored() {
        FileResponseCache cache = new FileResponseCache(dir, clock);
        cache.put("osv/a:b/", "x");
        clock.advance(Duration.ofHours(25));

        assertThat(cache.get("osv/a:b/", DAY)).isNull();
    }

    @Test
    void missingKeyGivesNull() {
        assertThat(new FileResponseCache(dir, clock).get("nichts", DAY)).isNull();
    }

    @Test
    void corruptFileIsDiscarded() throws IOException {
        FileResponseCache cache = new FileResponseCache(dir, clock);
        cache.put("metadata/a:b", "<metadata/>");
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                Files.writeString(file, "kaputt");
            }
        }

        assertThat(cache.get("metadata/a:b", DAY)).isNull();
        try (Stream<Path> files = Files.list(dir)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void multiLineValuesAndEmptyValuesSurvive() {
        FileResponseCache cache = new FileResponseCache(dir, clock);
        cache.put("a", "zeile 1\nzeile 2\n");
        cache.put("b", "");

        assertThat(cache.get("a", DAY)).isEqualTo("zeile 1\nzeile 2\n");
        assertThat(cache.get("b", DAY)).isEmpty();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
```

- [ ] **Step 2: Test ausführen, er muss fehlschlagen**

Run: `./gradlew test --tests "dev.nz3x.deplens.security.FileResponseCacheTest"`
Expected: FAIL – `cannot find symbol: class FileResponseCache`.

- [ ] **Step 3: `FileResponseCache` implementieren**

```java
package dev.nz3x.deplens.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.jetbrains.annotations.Nullable;

/** Rohantworten als Dateien: erste Zeile Zeitstempel (Epoch-Millis), danach der Wert. Best effort. */
public final class FileResponseCache implements ResponseCache {
    private final Path directory;
    private final Clock clock;

    public FileResponseCache(Path directory, Clock clock) {
        this.directory = directory;
        this.clock = clock;
    }

    @Override
    public @Nullable String get(String key, Duration maxAge) {
        Path file = file(key);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            int newline = content.indexOf('\n');
            Instant stored = Instant.ofEpochMilli(Long.parseLong(content.substring(0, newline)));
            if (Duration.between(stored, clock.instant()).compareTo(maxAge) > 0) {
                return null;
            }
            return content.substring(newline + 1);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(file);
            return null;
        }
    }

    @Override
    public void put(String key, String value) {
        try {
            Files.createDirectories(directory);
            Path temp = Files.createTempFile(directory, "entry", ".tmp");
            Files.writeString(temp, clock.millis() + "\n" + value, StandardCharsets.UTF_8);
            Files.move(temp, file(key), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Cache ist optional
        }
    }

    private Path file(String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(hash) + ".txt");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // egal
        }
    }
}
```

- [ ] **Step 4: Test ausführen, er muss grün sein**

Run: `./gradlew test --tests "dev.nz3x.deplens.security.FileResponseCacheTest"`
Expected: PASS (5 Tests).

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "Add file response cache

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 6: `HttpSecurityClient` implementieren**

```java
package dev.nz3x.deplens.security;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.util.io.HttpRequests;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Fragt die Dienste über IntelliJs {@link HttpRequests} ab (IDE-Proxy und Zertifikate gelten). */
public final class HttpSecurityClient implements SecurityClient {
    private static final String OSV_URL = "https://api.osv.dev/v1/query";
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";
    private static final String OSS_INDEX_URL = "https://ossindex.sonatype.org/api/v3/component-report";
    private static final int TIMEOUT_MS = 15_000;

    private final @Nullable String ossIndexUser;
    private final @Nullable String ossIndexToken;

    public HttpSecurityClient(@Nullable String ossIndexUser, @Nullable String ossIndexToken) {
        this.ossIndexUser = ossIndexUser;
        this.ossIndexToken = ossIndexToken;
    }

    @Override
    public String osvQuery(String packageName, @Nullable String pageToken) throws IOException {
        JsonObject pkg = new JsonObject();
        pkg.addProperty("name", packageName);
        pkg.addProperty("ecosystem", "Maven");
        JsonObject body = new JsonObject();
        body.add("package", pkg);
        if (pageToken != null) {
            body.addProperty("page_token", pageToken);
        }
        return post(OSV_URL, body.toString(), null);
    }

    @Override
    public @Nullable String mavenMetadata(String groupId, String artifactId) throws IOException {
        String url = MAVEN_CENTRAL + groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
        try {
            return HttpRequests.request(url)
                    .productNameAsUserAgent()
                    .connectTimeout(TIMEOUT_MS)
                    .readTimeout(TIMEOUT_MS)
                    .throwStatusCodeException(true)
                    .readString();
        } catch (HttpRequests.HttpStatusException e) {
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw new HttpStatusException(e.getStatusCode(), url);
        }
    }

    @Override
    public String ossIndexReport(List<String> coordinates) throws IOException {
        JsonArray array = new JsonArray();
        coordinates.forEach(array::add);
        JsonObject body = new JsonObject();
        body.add("coordinates", array);
        String credentials = ossIndexUser + ":" + ossIndexToken;
        String authorization = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return post(OSS_INDEX_URL, body.toString(), authorization);
    }

    private static String post(String url, String json, @Nullable String authorization) throws IOException {
        try {
            return HttpRequests.post(url, "application/json")
                    .productNameAsUserAgent()
                    .accept("application/json")
                    .connectTimeout(TIMEOUT_MS)
                    .readTimeout(TIMEOUT_MS)
                    .throwStatusCodeException(true)
                    .tuner(connection -> {
                        if (authorization != null) {
                            connection.setRequestProperty("Authorization", authorization);
                        }
                    })
                    .connect(request -> {
                        request.write(json);
                        return request.readString();
                    });
        } catch (HttpRequests.HttpStatusException e) {
            throw new HttpStatusException(e.getStatusCode(), url);
        }
    }
}
```

- [ ] **Step 7: `DepLensSettings` implementieren**

```java
package dev.nz3x.deplens.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.messages.Topic;
import java.time.Duration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Einstellungen von DepLens; das OSS-Index-Token liegt im IntelliJ-Passwortspeicher. */
@Service(Service.Level.APP)
@State(name = "DepLensSettings", storages = @Storage("deplens.xml"))
public final class DepLensSettings implements PersistentStateComponent<DepLensSettings.State> {

    public interface Listener {
        void settingsChanged();
    }

    public static final Topic<Listener> TOPIC = Topic.create("DepLens settings", Listener.class);

    public static final class State {
        public boolean securityEnabled = true;
        public int cacheHours = 24;
        public String ossIndexUser = "";
        public boolean ossIndexTokenStored;
    }

    private State state = new State();

    public static DepLensSettings getInstance() {
        return ApplicationManager.getApplication().getService(DepLensSettings.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State loaded) {
        state = loaded;
    }

    public boolean isSecurityEnabled() {
        return state.securityEnabled;
    }

    public Duration cacheDuration() {
        return Duration.ofHours(Math.max(1, state.cacheHours));
    }

    public String ossIndexUser() {
        return state.ossIndexUser == null ? "" : state.ossIndexUser.strip();
    }

    /** Blockiert (Passwortspeicher) – nicht auf dem EDT aufrufen. */
    public @Nullable String ossIndexToken() {
        return state.ossIndexTokenStored ? PasswordSafe.getInstance().getPassword(credentials()) : null;
    }

    /** Blockiert (Passwortspeicher) – nicht auf dem EDT aufrufen. {@code null} löscht das Token. */
    public void storeOssIndexToken(@Nullable String token) {
        PasswordSafe.getInstance().setPassword(credentials(), token);
    }

    public void notifyChanged() {
        ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).settingsChanged();
    }

    private static CredentialAttributes credentials() {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName("DepLens", "OSS Index"));
    }
}
```

- [ ] **Step 8: `DepLensConfigurable` implementieren**

```java
package dev.nz3x.deplens.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import org.jetbrains.annotations.Nullable;

/** Settings → Tools → DepLens. */
public final class DepLensConfigurable implements Configurable {
    private final JBCheckBox securityEnabled = new JBCheckBox("Sicherheitsprüfung aktivieren (OSV, Maven Central)");
    private final JSpinner cacheHours = new JSpinner(new SpinnerNumberModel(24, 1, 24 * 30, 1));
    private final JBTextField ossIndexUser = new JBTextField();
    private final JBPasswordField ossIndexToken = new JBPasswordField();
    private final JBLabel tokenHint = new JBLabel();

    @Override
    public String getDisplayName() {
        return "DepLens";
    }

    @Override
    public @Nullable JComponent createComponent() {
        tokenHint.setForeground(UIUtil.getContextHelpForeground());
        return FormBuilder.createFormBuilder()
                .addComponent(securityEnabled)
                .addLabeledComponent("Cache-Dauer (Stunden):", cacheHours)
                .addSeparator()
                .addComponent(new JBLabel("Sonatype OSS Index (optional, zusätzliche Lückenquelle)"))
                .addLabeledComponent("Benutzer (E-Mail):", ossIndexUser)
                .addLabeledComponent("API-Token:", ossIndexToken)
                .addComponent(tokenHint)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        DepLensSettings.State state = DepLensSettings.getInstance().getState();
        return securityEnabled.isSelected() != state.securityEnabled
                || (int) cacheHours.getValue() != state.cacheHours
                || !Objects.equals(ossIndexUser.getText().strip(), state.ossIndexUser)
                || ossIndexToken.getPassword().length > 0;
    }

    @Override
    public void apply() {
        DepLensSettings settings = DepLensSettings.getInstance();
        DepLensSettings.State state = settings.getState();
        state.securityEnabled = securityEnabled.isSelected();
        state.cacheHours = (int) cacheHours.getValue();
        state.ossIndexUser = ossIndexUser.getText().strip();
        char[] token = ossIndexToken.getPassword();
        ossIndexToken.setText("");
        if (token.length == 0) {
            settings.notifyChanged();
            reset();
            return;
        }
        state.ossIndexTokenStored = true;
        String value = new String(token);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            settings.storeOssIndexToken(value);
            ApplicationManager.getApplication().invokeLater(settings::notifyChanged);
        });
        reset();
    }

    @Override
    public void reset() {
        DepLensSettings.State state = DepLensSettings.getInstance().getState();
        securityEnabled.setSelected(state.securityEnabled);
        cacheHours.setValue(state.cacheHours);
        ossIndexUser.setText(state.ossIndexUser);
        ossIndexToken.setText("");
        tokenHint.setText(state.ossIndexTokenStored
                ? "Token gespeichert – leer lassen, um es zu behalten."
                : "Ohne Benutzer und Token wird nur OSV genutzt.");
    }
}
```

- [ ] **Step 9: `SecurityService` implementieren**

```java
package dev.nz3x.deplens.security;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import dev.nz3x.deplens.settings.DepLensSettings;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;

/** Verbindet {@link SecurityChecker} mit Einstellungen, Cache und HTTP. */
@Service(Service.Level.PROJECT)
public final class SecurityService {
    private static final Logger LOG = Logger.getInstance(SecurityService.class);

    private final ExecutorService executor =
            AppExecutorUtil.createBoundedApplicationPoolExecutor("DepLens security", 8);
    private final ResponseCache cache =
            new FileResponseCache(PathManager.getSystemDir().resolve("deplens").resolve("cache"), Clock.systemUTC());
    private volatile boolean ossIndexRejected;

    public static SecurityService getInstance(Project project) {
        return project.getService(SecurityService.class);
    }

    /** Blockiert – nur im Hintergrund aufrufen. */
    public void check(List<Artifact> artifacts, boolean forceRefresh, BooleanSupplier cancelled,
                      SecurityChecker.Listener listener) {
        DepLensSettings settings = DepLensSettings.getInstance();
        String user = settings.ossIndexUser();
        String token = user.isEmpty() ? null : settings.ossIndexToken();
        boolean ossIndex = token != null && !token.isBlank() && !ossIndexRejected;
        SecurityChecker checker = new SecurityChecker(new HttpSecurityClient(user, token), cache,
                settings.cacheDuration(), executor, duration -> Thread.sleep(duration.toMillis()));
        Set<String> loggedProblems = ConcurrentHashMap.newKeySet();
        checker.check(artifacts, forceRefresh, ossIndex, cancelled, new SecurityChecker.Listener() {
            @Override
            public void progress(int done, int total) {
                listener.progress(done, total);
            }

            @Override
            public void reportReady(Artifact artifact, SecurityReport report) {
                if (report.status() == SecurityReport.Status.FEHLER && loggedProblems.add(report.problem())) {
                    LOG.warn("DepLens: Sicherheitsprüfung fehlgeschlagen: " + report.problem());
                }
                listener.reportReady(artifact, report);
            }

            @Override
            public void notice(SecurityChecker.Notice notice) {
                if (notice == SecurityChecker.Notice.OSS_INDEX_UNAUTHORIZED) {
                    ossIndexRejected = true;
                }
                LOG.info("DepLens: " + notice);
                listener.notice(notice);
            }
        });
    }

    public void resetOssIndexRejection() {
        ossIndexRejected = false;
    }
}
```

- [ ] **Step 10: Einstellungsseite in `plugin.xml` registrieren** (im bestehenden `<extensions>`-Block nach `<toolWindow …/>`)

```xml
        <applicationConfigurable parentId="tools"
                                 instance="dev.nz3x.deplens.settings.DepLensConfigurable"
                                 id="dev.nz3x.deplens.settings"
                                 displayName="DepLens"/>
```

- [ ] **Step 11: Bauen**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, alle Tests grün, keine Compiler-Warnungen aus unserem Code. Falls eine IntelliJ-API anders heißt (z. B. `CredentialAttributesKt.generateServiceName`), die nächstliegende nicht-veraltete Variante nehmen und im Commit erwähnen.

- [ ] **Step 12: Commit und Push**

```bash
git add src
git commit -m "Add HTTP client, settings and security service

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push
```

---

### Task 7: UI – Markierungen, Filter, Statuszeile, Tab „Sicherheit“

**Files:**
- Modify: `src/main/java/dev/nz3x/deplens/catalog/DependencyCatalog.java` (+ `filter`)
- Modify: `src/test/java/dev/nz3x/deplens/catalog/DependencyCatalogTest.java`
- Modify: `src/main/java/dev/nz3x/deplens/ui/NodeText.java`
- Replace: `src/main/java/dev/nz3x/deplens/ui/CatalogTreeCellRenderer.java`
- Create: `src/main/java/dev/nz3x/deplens/ui/SecurityPanel.java`
- Replace: `src/main/java/dev/nz3x/deplens/ui/DepLensPanel.java`

**Interfaces:**
- Consumes: `SecurityService`, `SecurityChecker.Listener/Notice`, `SecurityReport`, `Finding`, `UpgradeAdvisor.Upgrade`, `SecurityLinks`, `Artifact`, `DepLensSettings`, bestehend: `DependencyCatalog.build/entryCount`, `CatalogEntry`, `CatalogCategory`, `TreeModels`, `GradleDependencySource`, `RequestSequencer`.
- Produces: `static List<CatalogCategory> DependencyCatalog.filter(List<CatalogCategory>, Predicate<CatalogEntry>)`; `NodeText.appendSecurity(SimpleColoredComponent, @Nullable SecurityReport)`, `NodeText.appendVulnerableMarker(SimpleColoredComponent)`, `NodeText.securityTooltip(@Nullable SecurityReport)`; `new CatalogTreeCellRenderer(Function<CatalogEntry, SecurityReport>)`; `SecurityPanel#show(CatalogEntry, @Nullable SecurityReport)`.

- [ ] **Step 1: Failing Test für `DependencyCatalog.filter` ergänzen** (in `DependencyCatalogTest`)

```java
    @Test
    void filterKeepsMatchingEntriesAndDropsEmptyCategories() {
        List<CatalogCategory> filtered = DependencyCatalog.filter(DependencyCatalog.build(g.graph, ""),
                entry -> entry.coordinates().contains("databind"));

        assertThat(filtered).extracting(CatalogCategory::name).containsExactly("Jackson");
        assertThat(filtered.getFirst().entries()).extracting(CatalogEntry::coordinates)
                .containsExactly(JACKSON + ":jackson-databind:2.15.2");
    }
```

- [ ] **Step 2: Test ausführen, er muss fehlschlagen**

Run: `./gradlew test --tests "dev.nz3x.deplens.catalog.DependencyCatalogTest"`
Expected: FAIL – `cannot find symbol: method filter`.

- [ ] **Step 3: `filter` in `DependencyCatalog` ergänzen** (nach `entryCount`; Import `java.util.function.Predicate`)

```java
    /** Behält nur Einträge, die {@code keep} erfüllen; leere Kategorien entfallen. */
    public static List<CatalogCategory> filter(List<CatalogCategory> categories, Predicate<CatalogEntry> keep) {
        return categories.stream()
                .map(category -> new CatalogCategory(category.name(),
                        category.entries().stream().filter(keep).toList()))
                .filter(category -> !category.entries().isEmpty())
                .toList();
    }
```

- [ ] **Step 4: Test ausführen, er muss grün sein**

Run: `./gradlew test --tests "dev.nz3x.deplens.catalog.DependencyCatalogTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "Add catalog filter

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 6: Sicherheits-Markierung in `NodeText` ergänzen**

Neue Imports: `dev.nz3x.deplens.security.SecurityReport`, `java.awt.Color`. Neue Konstanten nach `WARNING`:

```java
    private static final SimpleTextAttributes VULNERABLE =
            new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.RED);
    private static final SimpleTextAttributes SAFE = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN,
            new JBColor(new Color(0x2E7D32), new Color(0x81C784)));
```

Neue Methoden vor `tooltip(CatalogEntry)`:

```java
    /** {@code ⛔ n} bei Lücken, {@code ✔} wenn sicher, {@code ?} wenn nicht prüfbar; nichts, solange geprüft wird. */
    static void appendSecurity(SimpleColoredComponent target, @Nullable SecurityReport report) {
        if (report == null) {
            return;
        }
        switch (report.status()) {
            case VERWUNDBAR -> target.append("  ⛔ " + report.findings().size(), VULNERABLE);
            case SICHER -> target.append("  ✔", SAFE);
            case NICHT_PRUEFBAR, FEHLER -> target.append("  ?", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }

    static void appendVulnerableMarker(SimpleColoredComponent target) {
        target.append("  ⛔", VULNERABLE);
    }

    static @Nullable String securityTooltip(@Nullable SecurityReport report) {
        if (report == null) {
            return null;
        }
        return switch (report.status()) {
            case VERWUNDBAR -> report.findings().size() == 1
                    ? "1 bekannte Sicherheitslücke"
                    : report.findings().size() + " bekannte Sicherheitslücken";
            case SICHER -> "Keine bekannten Sicherheitslücken";
            case NICHT_PRUEFBAR -> "Nicht prüfbar: " + report.problem();
            case FEHLER -> "Prüfung fehlgeschlagen: " + report.problem();
        };
    }
```

- [ ] **Step 7: `CatalogTreeCellRenderer` ersetzen**

```java
package dev.nz3x.deplens.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import dev.nz3x.deplens.catalog.CatalogCategory;
import dev.nz3x.deplens.catalog.CatalogEntry;
import dev.nz3x.deplens.security.SecurityReport;
import java.util.function.Function;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import org.jetbrains.annotations.NotNull;

/** Renderer für die Kategorie-Liste: Hersteller mit Anzahl, darunter die Dependencies mit Sicherheitsstatus. */
final class CatalogTreeCellRenderer extends ColoredTreeCellRenderer {
    private final Function<CatalogEntry, SecurityReport> reports;

    /** {@code reports} liefert den Bericht eines Eintrags oder {@code null}, solange keiner vorliegt. */
    CatalogTreeCellRenderer(Function<CatalogEntry, SecurityReport> reports) {
        this.reports = reports;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded,
                                      boolean leaf, int row, boolean hasFocus) {
        setToolTipText(null);
        if (!(value instanceof DefaultMutableTreeNode treeNode)) {
            return;
        }
        if (treeNode.getUserObject() instanceof CatalogCategory category) {
            setIcon(AllIcons.Nodes.Folder);
            append(category.name(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            append("  " + category.entries().size(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            if (category.entries().stream().anyMatch(this::isVulnerable)) {
                NodeText.appendVulnerableMarker(this);
            }
        } else if (treeNode.getUserObject() instanceof CatalogEntry entry) {
            SecurityReport report = reports.apply(entry);
            NodeText.appendEntry(this, entry);
            NodeText.appendSecurity(this, report);
            String securityTooltip = NodeText.securityTooltip(report);
            setToolTipText(securityTooltip != null ? securityTooltip : NodeText.tooltip(entry));
        }
    }

    private boolean isVulnerable(CatalogEntry entry) {
        SecurityReport report = reports.apply(entry);
        return report != null && report.status() == SecurityReport.Status.VERWUNDBAR;
    }
}
```

- [ ] **Step 8: `SecurityPanel` schreiben**

```java
package dev.nz3x.deplens.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import dev.nz3x.deplens.catalog.CatalogEntry;
import dev.nz3x.deplens.model.DependencyNode;
import dev.nz3x.deplens.security.Finding;
import dev.nz3x.deplens.security.MavenVersion;
import dev.nz3x.deplens.security.SecurityLinks;
import dev.nz3x.deplens.security.SecurityReport;
import dev.nz3x.deplens.security.UpgradeAdvisor;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.Nullable;

/** Inhalt des Tabs „Sicherheit“ für eine gewählte Dependency. */
final class SecurityPanel extends JPanel {
    private final JBLabel header = new JBLabel();
    private final JPanel upgradeRow = row();
    private final JBLabel hint = new JBLabel();
    private final JPanel linkRow = row();
    private final FindingsModel findings = new FindingsModel();
    private final JBTable table = new JBTable(findings);

    SecurityPanel() {
        super(new BorderLayout());
        header.setFont(JBUI.Fonts.label().asBold());
        hint.setForeground(UIUtil.getContextHelpForeground());
        JPanel top = new JPanel(new VerticalLayout(JBUI.scale(6)));
        top.setBorder(JBUI.Borders.empty(8, 10));
        top.add(header);
        top.add(upgradeRow);
        top.add(hint);
        top.add(linkRow);
        add(top, BorderLayout.NORTH);

        table.setShowGrid(false);
        table.getEmptyText().setText("Keine Lücken");
        table.getColumnModel().getColumn(0).setPreferredWidth(JBUI.scale(90));
        table.getColumnModel().getColumn(1).setPreferredWidth(JBUI.scale(150));
        table.getColumnModel().getColumn(2).setPreferredWidth(JBUI.scale(400));
        table.getColumnModel().getColumn(3).setPreferredWidth(JBUI.scale(90));
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && row >= 0) {
                    BrowserUtil.browse(findings.at(table.convertRowIndexToModel(row)).vulnerability().url());
                }
            }
        });
        add(new JBScrollPane(table), BorderLayout.CENTER);
    }

    void show(CatalogEntry entry, @Nullable SecurityReport report) {
        DependencyNode node = entry.representative();
        String name = node.name() + (node.kind() == DependencyNode.Kind.ARTIFACT ? " " + node.version() : "");
        upgradeRow.removeAll();
        linkRow.removeAll();
        hint.setText("");
        hint.setVisible(false);
        upgradeRow.setVisible(false);
        findings.set(report == null ? List.of() : report.findings());

        if (report == null) {
            header.setText(name + " – wird geprüft …");
        } else {
            switch (report.status()) {
                case SICHER -> header.setText(name + " – keine bekannten Lücken");
                case NICHT_PRUEFBAR -> header.setText(name + " – nicht prüfbar: " + report.problem());
                case FEHLER -> header.setText(name + " – Prüfung fehlgeschlagen: " + report.problem());
                case VERWUNDBAR -> {
                    int count = report.findings().size();
                    header.setText(name + " – ⛔ " + count + (count == 1 ? " Lücke" : " Lücken"));
                    fillUpgrades(node, report.upgrade());
                    showTransitiveHint(entry);
                }
            }
        }
        if (node.kind() == DependencyNode.Kind.ARTIFACT) {
            linkRow.add(link("↗ auf mvnrepository öffnen",
                    SecurityLinks.mvnRepository(node.groupId(), node.artifactId(), node.version())));
            linkRow.add(link("↗ auf osv.dev öffnen", SecurityLinks.osvPackage(node.groupId(), node.artifactId())));
        }
        revalidate();
        repaint();
    }

    private void fillUpgrades(DependencyNode node, UpgradeAdvisor.Upgrade upgrade) {
        upgradeRow.setVisible(true);
        upgradeRow.add(new JBLabel("Upgrade:"));
        if (upgrade.isEmpty()) {
            upgradeRow.add(new JBLabel("Keine Version ohne bekannte Lücken verfügbar"));
            return;
        }
        if (upgrade.sameMajor() != null) {
            upgradeRow.add(versionLink(node, upgrade.sameMajor(), "gleiche Major"));
        }
        if (upgrade.latest() != null && !upgrade.latest().equals(upgrade.sameMajor())) {
            upgradeRow.add(versionLink(node, upgrade.latest(), "neueste sichere"));
        }
    }

    /** Hinweis, wenn kein Vorkommen direkt am Modul hängt. */
    private void showTransitiveHint(CatalogEntry entry) {
        boolean direct = entry.occurrences().stream()
                .anyMatch(occurrence -> occurrence.parent() != null && occurrence.parent().isRoot());
        DependencyNode parent = entry.representative().parent();
        if (!direct && parent != null) {
            hint.setText("Wird über " + parent.name() + " eingezogen – Upgrade dort oder per Gradle-Constraint.");
            hint.setVisible(true);
        }
    }

    private static ActionLink versionLink(DependencyNode node, MavenVersion version, String label) {
        return link(version + " (" + label + ")",
                SecurityLinks.mvnRepository(node.groupId(), node.artifactId(), version.toString()));
    }

    private static ActionLink link(String text, String url) {
        return new ActionLink(text, e -> BrowserUtil.browse(url));
    }

    private static JPanel row() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
    }

    private static final class FindingsModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Schweregrad", "ID", "Beschreibung", "Behoben in"};
        private List<Finding> rows = List.of();

        void set(List<Finding> findings) {
            rows = findings;
            fireTableDataChanged();
        }

        Finding at(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            Finding finding = rows.get(row);
            return switch (column) {
                case 0 -> finding.vulnerability().severity().name();
                case 1 -> finding.vulnerability().displayId();
                case 2 -> finding.vulnerability().summary();
                default -> finding.fixedIn() == null ? "–" : finding.fixedIn().toString();
            };
        }
    }
}
```

- [ ] **Step 9: `DepLensPanel` ersetzen**

Die bestehende Lade-/Thread-Logik (Module, `inBackground`, `handleMissingData`, `syncGradle`) bleibt inhaltlich gleich; neu sind Sicherheitsprüfung, Filter, Statuszeile und Tabs.

```java
package dev.nz3x.deplens.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerProject;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import dev.nz3x.deplens.catalog.CatalogCategory;
import dev.nz3x.deplens.catalog.CatalogEntry;
import dev.nz3x.deplens.catalog.DependencyCatalog;
import dev.nz3x.deplens.gradle.GradleDependencySource;
import dev.nz3x.deplens.model.DependencyGraph;
import dev.nz3x.deplens.model.DependencyNode;
import dev.nz3x.deplens.security.Artifact;
import dev.nz3x.deplens.security.SecurityChecker;
import dev.nz3x.deplens.security.SecurityReport;
import dev.nz3x.deplens.security.SecurityService;
import dev.nz3x.deplens.settings.DepLensSettings;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.Nullable;

/** Inhalt des DepLens-Tool-Windows. */
public final class DepLensPanel extends JPanel implements Disposable {
    private static final Logger LOG = Logger.getInstance(DepLensPanel.class);
    private static final String CARD_CONTENT = "content";
    private static final String CARD_MESSAGE = "message";
    private static final int SECURITY_TAB = 1;

    private final Project project;
    private final GradleDependencySource source;
    private final RequestSequencer sequencer = new RequestSequencer();
    private final RequestSequencer securitySequencer = new RequestSequencer();
    /** Koordinate {@code g:a:v} → Bericht; nur auf dem EDT benutzt. */
    private final Map<String, SecurityReport> reports = new HashMap<>();
    private final ComboBox<DependencyAnalyzerProject> moduleBox = new ComboBox<>();
    private final SearchTextField searchField = new SearchTextField(false);
    private final Alarm searchAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    private final JBCheckBox onlyVulnerable = new JBCheckBox("Nur verwundbare");
    private final JButton securityButton = new JButton(AllIcons.Actions.ForceRefresh);
    private final Tree catalogTree = new Tree(TreeModels.empty());
    private final Tree pathTree = new Tree(TreeModels.empty());
    private final DependencyTreeCellRenderer pathRenderer = new DependencyTreeCellRenderer();
    private final SecurityPanel securityPanel = new SecurityPanel();
    private final JBTabbedPane detailTabs = new JBTabbedPane();
    private final JBLabel listTitle = sectionTitle("");
    private final OnePixelSplitter contentSplitter = new OnePixelSplitter(true, 0.45f);
    private final JPanel toolbar = new JPanel(new BorderLayout(JBUI.scale(8), 0));
    private final JBLabel statusLabel = new JBLabel();
    private final ActionLink statusRetry = new ActionLink("erneut versuchen", e -> startSecurityCheck(false));
    private final JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JBLabel messageLabel = new JBLabel("", SwingConstants.CENTER);
    private final JButton retryButton = new JButton("Gradle neu laden");
    private final JBLoadingPanel loadingPanel;
    private volatile boolean disposed;
    private boolean updatingModules;
    /** Ein automatischer Gradle-Sync pro Panel, damit ein leeres Projekt keine Endlosschleife auslöst. */
    private boolean autoSyncDone;
    private @Nullable String securityNotice;
    private int securityFailures;

    DependencyGraph graph = DependencyGraph.empty("");

    public DepLensPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        source = new GradleDependencySource(project, () -> runOnEdt(this::reloadModules));
        Disposer.register(this, source);

        moduleBox.setRenderer(new SimpleListCellRenderer<>() {
            @Override
            public void customize(JList<? extends DependencyAnalyzerProject> list,
                                  DependencyAnalyzerProject value, int index, boolean selected, boolean hasFocus) {
                setText(value == null ? "" : value.getTitle());
            }
        });
        moduleBox.addActionListener(e -> {
            if (!updatingModules) {
                loadSelectedModule();
            }
        });

        JButton refreshButton = new JButton(AllIcons.Actions.Refresh);
        refreshButton.setToolTipText("Gradle neu laden");
        refreshButton.addActionListener(e -> syncGradle());
        securityButton.setToolTipText("Sicherheit neu prüfen");
        securityButton.addActionListener(e -> startSecurityCheck(true));
        onlyVulnerable.addActionListener(e -> runSearch());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0));
        actions.add(onlyVulnerable);
        actions.add(refreshButton);
        actions.add(securityButton);

        toolbar.setBorder(JBUI.Borders.empty(8));
        toolbar.add(moduleBox, BorderLayout.WEST);
        toolbar.add(searchField, BorderLayout.CENTER);
        toolbar.add(actions, BorderLayout.EAST);
        searchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                searchAlarm.cancelAllRequests();
                searchAlarm.addRequest(DepLensPanel.this::runSearch, 300);
            }
        });

        statusRow.setBorder(JBUI.Borders.empty(0, 10, 6, 10));
        statusRow.add(statusLabel);
        statusRow.add(statusRetry);
        statusRow.setVisible(false);
        JPanel north = new JPanel(new BorderLayout());
        north.add(toolbar, BorderLayout.NORTH);
        north.add(statusRow, BorderLayout.SOUTH);

        catalogTree.setRootVisible(false);
        catalogTree.setShowsRootHandles(true);
        catalogTree.setCellRenderer(new CatalogTreeCellRenderer(entry -> reports.get(entry.coordinates())));
        catalogTree.setBorder(JBUI.Borders.empty(4));
        catalogTree.setRowHeight(JBUI.scale(26));
        catalogTree.addTreeSelectionListener(e -> showEntry(selectedEntry()));
        ToolTipManager.sharedInstance().registerComponent(catalogTree);

        pathTree.setRootVisible(false);
        pathTree.setShowsRootHandles(true);
        pathTree.setCellRenderer(pathRenderer);
        pathTree.setBorder(JBUI.Borders.empty(4, 8));
        ToolTipManager.sharedInstance().registerComponent(pathTree);
        detailTabs.addTab("Abhängigkeitspfad", new JBScrollPane(pathTree));
        detailTabs.addTab("Sicherheit", securityPanel);

        retryButton.addActionListener(e -> syncGradle());
        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.add(messageLabel, BorderLayout.CENTER);
        JPanel retryRow = new JPanel();
        retryRow.add(retryButton);
        messagePanel.add(retryRow, BorderLayout.SOUTH);

        cardPanel.add(createContent(), CARD_CONTENT);
        cardPanel.add(messagePanel, CARD_MESSAGE);

        loadingPanel = new JBLoadingPanel(new BorderLayout(), this);
        loadingPanel.add(cardPanel, BorderLayout.CENTER);

        add(north, BorderLayout.NORTH);
        add(loadingPanel, BorderLayout.CENTER);

        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(DepLensSettings.TOPIC, (DepLensSettings.Listener) () -> runOnEdt(this::onSettingsChanged));
        applySecurityVisibility();
        reloadModules();
    }

    /** Kategorie-Liste oben; Details (Pfad, Sicherheit) werden erst bei einer Auswahl unten eingeblendet. */
    private JComponent createContent() {
        contentSplitter.setFirstComponent(section(listTitle, new JBScrollPane(catalogTree)));
        return contentSplitter;
    }

    private static JBLabel sectionTitle(String text) {
        JBLabel label = new JBLabel(text);
        label.setFont(JBUI.Fonts.label().asBold());
        label.setBorder(JBUI.Borders.empty(6, 10));
        return label;
    }

    private static JComponent section(JBLabel title, JComponent body) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(title, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    /** Wird auf dem EDT aufgerufen, nachdem {@link #graph} neu gesetzt wurde. */
    private void onGraphLoaded() {
        showCard(CARD_CONTENT);
        runSearch();
        startSecurityCheck(false);
    }

    private void runSearch() {
        String query = searchField.getText();
        boolean searching = !query.isBlank();
        List<CatalogCategory> categories = DependencyCatalog.build(graph, query);
        if (onlyVulnerable.isSelected()) {
            categories = DependencyCatalog.filter(categories, this::isVulnerable);
        }
        catalogTree.setModel(TreeModels.catalog(categories));
        listTitle.setText((searching ? "Treffer" : "Alle Dependencies")
                + " (" + DependencyCatalog.entryCount(categories) + ")");
        catalogTree.getEmptyText().setText(onlyVulnerable.isSelected()
                ? "Keine verwundbaren Dependencies"
                : searching ? "Keine Treffer für ‚" + query.strip() + "‘" : "Keine Dependencies");
        if ((searching || onlyVulnerable.isSelected()) && !categories.isEmpty()) {
            TreeUtil.expandAll(catalogTree);
        }
        if (searching && !categories.isEmpty()) {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) catalogTree.getModel().getRoot();
            DefaultMutableTreeNode firstEntry = (DefaultMutableTreeNode) root.getFirstChild().getChildAt(0);
            catalogTree.setSelectionPath(new TreePath(firstEntry.getPath()));
        } else {
            catalogTree.clearSelection();
            showEntry(null);
        }
    }

    private @Nullable CatalogEntry selectedEntry() {
        Object selected = catalogTree.getLastSelectedPathComponent();
        return selected instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof CatalogEntry entry
                ? entry
                : null;
    }

    private boolean isVulnerable(CatalogEntry entry) {
        SecurityReport report = reports.get(entry.coordinates());
        return report != null && report.status() == SecurityReport.Status.VERWUNDBAR;
    }

    /** Zeigt Pfad und Sicherheit der Dependency; ohne Auswahl wird der Bereich ausgeblendet. */
    private void showEntry(@Nullable CatalogEntry entry) {
        if (entry == null) {
            pathTree.setModel(TreeModels.empty());
            contentSplitter.setSecondComponent(null);
            return;
        }
        TreeModels.PathTree tree = TreeModels.allPaths(entry);
        pathRenderer.setHighlighted(entry.occurrences());
        pathTree.setModel(tree.model());
        for (DefaultMutableTreeNode hitNode : tree.hitNodes()) {
            TreePath hitPath = new TreePath(hitNode.getPath());
            pathTree.expandPath(hitPath);
            for (int i = 0; i < hitNode.getChildCount(); i++) {
                pathTree.expandPath(hitPath.pathByAddingChild(hitNode.getChildAt(i)));
            }
        }
        contentSplitter.setSecondComponent(detailTabs);
        pathTree.scrollPathToVisible(new TreePath(tree.hitNodes().getFirst().getPath()));
        updateSecurityDetails(entry);
        if (hasSecurityTab() && isVulnerable(entry)) {
            detailTabs.setSelectedIndex(SECURITY_TAB);
        }
    }

    private void updateSecurityDetails(CatalogEntry entry) {
        if (!hasSecurityTab()) {
            return;
        }
        SecurityReport report = reports.get(entry.coordinates());
        securityPanel.show(entry, report);
        detailTabs.setTitleAt(SECURITY_TAB, report != null && report.status() == SecurityReport.Status.VERWUNDBAR
                ? "Sicherheit (" + report.findings().size() + ")"
                : "Sicherheit");
    }

    private boolean hasSecurityTab() {
        return detailTabs.getTabCount() > SECURITY_TAB;
    }

    private static boolean securityEnabled() {
        return DepLensSettings.getInstance().isSecurityEnabled();
    }

    private void onSettingsChanged() {
        SecurityService.getInstance(project).resetOssIndexRejection();
        applySecurityVisibility();
        if (!graph.isEmpty()) {
            runSearch();
            startSecurityCheck(false);
        }
    }

    /** Blendet alle Sicherheits-Elemente ein oder aus, je nach Einstellung. */
    private void applySecurityVisibility() {
        boolean enabled = securityEnabled();
        onlyVulnerable.setVisible(enabled);
        securityButton.setVisible(enabled);
        if (!enabled) {
            onlyVulnerable.setSelected(false);
            securitySequencer.next();
            reports.clear();
            setStatus(null, false);
            if (hasSecurityTab()) {
                detailTabs.removeTabAt(SECURITY_TAB);
            }
        } else if (!hasSecurityTab()) {
            detailTabs.addTab("Sicherheit", securityPanel);
        }
        catalogTree.repaint();
    }

    /** Prüft alle Artefakte des Moduls; ältere Prüfungen werden dabei verworfen. */
    private void startSecurityCheck(boolean forceRefresh) {
        if (!securityEnabled() || graph.isEmpty()) {
            return;
        }
        long request = securitySequencer.next();
        reports.clear();
        securityFailures = 0;
        securityNotice = null;
        List<Artifact> artifacts = new ArrayList<>();
        for (CatalogCategory category : DependencyCatalog.build(graph, "")) {
            for (CatalogEntry entry : category.entries()) {
                DependencyNode node = entry.representative();
                if (node.kind() == DependencyNode.Kind.ARTIFACT) {
                    artifacts.add(new Artifact(node.groupId(), node.artifactId(), node.version()));
                } else {
                    reports.put(entry.coordinates(), SecurityReport.notCheckable("Projekt-Modul"));
                }
            }
        }
        catalogTree.repaint();
        refreshSelectedEntry();
        setStatus("Sicherheit wird geprüft …", false);
        BooleanSupplier cancelled = () -> disposed || !securitySequencer.isCurrent(request);
        SecurityService service = SecurityService.getInstance(project);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                service.check(artifacts, forceRefresh, cancelled, new SecurityChecker.Listener() {
                    @Override
                    public void progress(int done, int total) {
                        onSecurityEdt(request, () -> setStatus("Sicherheit wird geprüft … " + done + "/" + total, false));
                    }

                    @Override
                    public void reportReady(Artifact artifact, SecurityReport report) {
                        onSecurityEdt(request, () -> applyReport(artifact, report));
                    }

                    @Override
                    public void notice(SecurityChecker.Notice notice) {
                        onSecurityEdt(request, () -> securityNotice = noticeText(notice));
                    }
                });
                onSecurityEdt(request, this::finishSecurityCheck);
            } catch (Throwable e) {
                if (e instanceof ControlFlowException) {
                    throw e;
                }
                LOG.warn("DepLens: Sicherheitsprüfung fehlgeschlagen", e);
                onSecurityEdt(request, () -> setStatus("Sicherheitsprüfung fehlgeschlagen", true));
            }
        });
    }

    private void applyReport(Artifact artifact, SecurityReport report) {
        reports.put(artifact.coordinates(), report);
        if (report.status() == SecurityReport.Status.FEHLER) {
            securityFailures++;
        }
        catalogTree.repaint();
        refreshSelectedEntry();
    }

    private void finishSecurityCheck() {
        if (securityFailures > 0) {
            setStatus(securityFailures + (securityFailures == 1 ? " Prüfung" : " Prüfungen") + " fehlgeschlagen", true);
        } else {
            setStatus(securityNotice, false);
        }
        if (onlyVulnerable.isSelected()) {
            runSearch();
        }
    }

    private void refreshSelectedEntry() {
        CatalogEntry selected = selectedEntry();
        if (selected != null) {
            updateSecurityDetails(selected);
        }
    }

    private static String noticeText(SecurityChecker.Notice notice) {
        return switch (notice) {
            case OSS_INDEX_UNAUTHORIZED -> "OSS-Index-Token ungültig – nur OSV aktiv";
            case OSS_INDEX_FAILED -> "OSS Index nicht erreichbar – nur OSV aktiv";
        };
    }

    private void setStatus(@Nullable String text, boolean retry) {
        statusLabel.setText(text == null ? "" : text);
        statusRetry.setVisible(retry);
        statusRow.setVisible(text != null);
    }

    private void onSecurityEdt(long request, Runnable runnable) {
        runOnEdt(() -> {
            if (securitySequencer.isCurrent(request)) {
                runnable.run();
            }
        });
    }

    /** Startet einen Gradle-Sync; danach lädt {@code whenDataChanged} die Module neu. */
    private void syncGradle() {
        source.reset();
        if (source.requestSync()) {
            showMessage("Gradle wird neu geladen … DepLens aktualisiert sich danach automatisch.", false);
        } else {
            reloadModules();
        }
    }

    /**
     * Zeigt den Hinweis zu fehlenden Daten. Beim ersten Mal wird stattdessen automatisch ein Gradle-Sync
     * gestartet, damit DepLens ohne manuellen Sync alle Dependencies erkennt.
     */
    private void handleMissingData(String message) {
        if (!autoSyncDone) {
            autoSyncDone = true;
            if (source.requestSync()) {
                showMessage("Gradle-Projekt wird geladen … DepLens aktualisiert sich danach automatisch.", false);
                return;
            }
        }
        showMessage(message, true);
    }

    private void reloadModules() {
        DependencyAnalyzerProject previous = (DependencyAnalyzerProject) moduleBox.getSelectedItem();
        String previousTitle = previous == null ? null : previous.getTitle();
        long request = sequencer.next();
        loadingPanel.startLoading();
        inBackground(request, source::modules, modules -> {
            loadingPanel.stopLoading();
            updatingModules = true;
            try {
                moduleBox.removeAllItems();
                modules.forEach(moduleBox::addItem);
                modules.stream()
                        .filter(m -> Objects.equals(m.getTitle(), previousTitle))
                        .findFirst()
                        .ifPresent(moduleBox::setSelectedItem);
            } finally {
                updatingModules = false;
            }
            if (modules.isEmpty()) {
                graph = DependencyGraph.empty("");
                handleMissingData("Kein Gradle-Projekt gefunden.");
            } else {
                loadSelectedModule();
            }
        });
    }

    private void loadSelectedModule() {
        DependencyAnalyzerProject module = (DependencyAnalyzerProject) moduleBox.getSelectedItem();
        if (module == null) {
            return;
        }
        long request = sequencer.next();
        loadingPanel.startLoading();
        inBackground(request, () -> source.load(module), loaded -> {
            loadingPanel.stopLoading();
            graph = loaded;
            if (loaded.isEmpty()) {
                handleMissingData("Keine Dependencies für „" + module.getTitle()
                        + "“ – ist der Gradle-Lauf fehlgeschlagen? Details im Build-Fenster.");
            } else {
                onGraphLoaded();
            }
        });
    }

    /** Runs work off the EDT; results, cancellations and failures of superseded requests are discarded. */
    private <T> void inBackground(long request, Supplier<T> work, Consumer<T> onEdt) {
        if (disposed) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (disposed) {
                return;
            }
            try {
                T result = work.get();
                runOnEdt(() -> {
                    if (sequencer.isCurrent(request)) {
                        onEdt.accept(result);
                    }
                });
            } catch (ProcessCanceledException e) {
                stopLoadingIfCurrent(request);
            } catch (Throwable e) {
                if (e instanceof ControlFlowException) {
                    stopLoadingIfCurrent(request);
                    throw e;
                }
                LOG.warn("DepLens: loading failed", e);
                String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                runOnEdt(() -> {
                    if (sequencer.isCurrent(request)) {
                        loadingPanel.stopLoading();
                        showMessage("Laden fehlgeschlagen: " + detail, true);
                    }
                });
            }
        });
    }

    private void stopLoadingIfCurrent(long request) {
        runOnEdt(() -> {
            if (sequencer.isCurrent(request)) {
                loadingPanel.stopLoading();
            }
        });
    }

    private void runOnEdt(Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!disposed) {
                runnable.run();
            }
        }, ModalityState.any());
    }

    private void showMessage(String text, boolean retry) {
        messageLabel.setText(text);
        retryButton.setVisible(retry);
        showCard(CARD_MESSAGE);
    }

    private void showCard(String card) {
        cards.show(cardPanel, card);
    }

    @Override
    public void dispose() {
        disposed = true;
    }
}
```

- [ ] **Step 10: Bauen und alle Tests ausführen**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, alle Tests grün, keine Compiler-Warnungen aus unserem Code. Falls `VerticalLayout` oder ein anderes UI-Hilfsmittel nicht kompiliert, die nächstliegende Variante nehmen (z. B. `BoxLayout` mit `Y_AXIS`) und im Commit erwähnen.

- [ ] **Step 11: Commit und Push**

```bash
git add src
git commit -m "Show security status, filter and details tab

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push
```

---

### Task 8: Doku und Abschlussprüfung

**Files:**
- Modify: `README.md`, `src/main/resources/META-INF/plugin.xml` (Beschreibung)

- [ ] **Step 1: README um einen Abschnitt ergänzen** (vor „## Datenquelle“)

```markdown
## Sicherheitsprüfung

DepLens prüft jede Dependency automatisch gegen [OSV.dev](https://osv.dev) und liest die verfügbaren
Versionen von Maven Central. In der Liste steht ⛔ (mit Anzahl der Lücken), ✔ (keine bekannten Lücken)
oder `?` (nicht prüfbar). Im Tab „Sicherheit“ stehen die Lücken mit Schweregrad, Link und
„behoben in“ sowie zwei Upgrade-Vorschläge: die nächste sichere Version mit gleicher Major und die
neueste sichere Version.

- Ergebnisse werden 24 h zwischengespeichert; das Schild-Icon prüft neu.
- „Nur verwundbare“ blendet alle sicheren Dependencies aus.
- Gesendet werden nur Koordinaten (`group:artifact[:version]`).
- *Settings → Tools → DepLens*: Prüfung an/aus, Cache-Dauer, optional Sonatype OSS Index
  (Benutzer + API-Token, Token im IntelliJ-Passwortspeicher).
```

- [ ] **Step 2: Plugin-Beschreibung in `plugin.xml` ersetzen**

```xml
    <description><![CDATA[
        Browse Gradle dependencies per module grouped by vendor, see every path that pulls a dependency in,
        and check them for known vulnerabilities (OSV.dev, optional Sonatype OSS Index) with safe upgrade suggestions.
    ]]></description>
```

- [ ] **Step 3: Komplett bauen**

Run: `./gradlew build buildPlugin`
Expected: BUILD SUCCESSFUL, `build/distributions/DepLens-0.1.0.zip` existiert.

- [ ] **Step 4: Commit und Push**

```bash
git add README.md src/main/resources/META-INF/plugin.xml
git commit -m "Docs: security check

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push
```

- [ ] **Step 5: Manuelle Prüfung (Nutzer, `./gradlew runIde`, Projekt `samples/demo`, Modul `app`)**

Expected:
- Kurz nach dem Laden erscheint „Sicherheit wird geprüft … n/m“, danach Markierungen in der Liste.
- `jackson-databind` (2.13.0 angefordert, aufgelöst evtl. höher) – falls verwundbar: ⛔ mit Anzahl, Kategorie „Jackson“ mit ⛔; Tab „Sicherheit (n)“ öffnet sich automatisch, Tabelle mit Schweregrad/ID/„Behoben in“, Doppelklick öffnet osv.dev, Upgrade-Links öffnen mvnrepository.
- Eine transitive verwundbare Dependency zeigt „Wird über … eingezogen“.
- `does.not:exist` zeigt `?` mit Tooltip.
- „Nur verwundbare“ filtert die Liste.
- Schild-Icon prüft neu (Statuszeile erscheint wieder).
- Netzwerk trennen + Schild-Icon: `?`-Markierungen und „n Prüfungen fehlgeschlagen – erneut versuchen“.
- *Settings → Tools → DepLens*: Prüfung aus → alle Sicherheits-Elemente verschwinden; wieder an → Prüfung startet.
- Mit ungültigem OSS-Index-Token: Statuszeile „OSS-Index-Token ungültig – nur OSV aktiv“.
