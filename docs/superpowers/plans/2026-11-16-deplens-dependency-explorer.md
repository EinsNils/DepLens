# DepLens Dependency Explorer (Phase 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein IntelliJ-Tool-Window, in dem man pro Gradle-Modul nach einer Dependency sucht und für jeden Treffer Parents (Pfad bis zum Modul) und Children sieht.

**Architecture:** IntelliJs öffentliche Dependency-Analyzer-API (`DependencyAnalyzerContributor` für Gradle) liefert eine flache Liste mit Parent-Zeigern. `DependencyGraphMapper` übersetzt sie in ein eigenes, IntelliJ-freies Modell (`DependencyGraph`/`DependencyNode`). Suche (`DependencySearch`) und Baum-Aufbau (`TreeModels`) sind reine Funktionen darauf. Eine dünne Swing-UI (`DepLensPanel`) verbindet alles.

**Tech Stack:** Java 25, Gradle 9.9.1 (Kotlin DSL), IntelliJ Platform Gradle Plugin 2.20.1, IntelliJ IDEA 2026.2.3, JUnit 6.1.3, AssertJ 3.27.7.

**Spec:** `docs/superpowers/specs/2026-11-16-deplens-dependency-explorer-design.md`

## Global Constraints

- Ziel-IDE: IntelliJ IDEA 2026.2 – `sinceBuild = "262"`, kein `untilBuild`.
- Java-Toolchain 25 (die Plattform-Klassen sind Bytecode 69 = Java 25).
- Bundled Plugin `com.intellij.gradle` als einzige Plugin-Abhängigkeit.
- Nur die Analyzer-API aus `com.intellij.openapi.externalSystem.dependency.analyzer` verwenden; nur `GradleDependencySource` und `DependencyGraphMapper` dürfen sie importieren.
- `getDependencies(...)` nie auf dem EDT aufrufen.
- Paket-Wurzel: `io.github.einsnils.deplens`; Plugin-ID `io.github.einsnils.deplens`.
- Quellcode-Encoding UTF-8 (UI-Texte enthalten `·`, `⚠`, Umlaute).
- UI-Texte auf Deutsch.
- Git: ein Feature-Branch `feat/dependency-explorer`, kleine Commits mit kurzen Nachrichten, nach jedem Task pushen. Jede Commit-Nachricht endet mit
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Modulwechsel während eines laufenden Ladevorgangs** – das ältere Ergebnis darf das neuere nie überschreiben → `RequestSequencerTest` (Task 4).
2. **Suchbegriffe mit Leerzeichen, anderer Schreibweise oder Versionsfragment** (`"  JACKSON-core:2.15 "`) – müssen trotzdem treffen → `DependencySearchTest` (Task 2).
3. **Ausgelassene Vorkommen (`(*)`)** – müssen in der Trefferliste erscheinen und dürfen im Children-Baum keine Kinder zeigen → `DependencySearchTest`, `TreeModelsTest` (Tasks 2, 4).
4. **Projekt-Abhängigkeiten auf andere Module (`:core`)** – müssen auffindbar sein, die Modul-Wurzel selbst nicht → `DependencySearchTest`, `DependencyGraphMapperTest` (Tasks 2, 3).
5. **Unerwartete API-Daten** (Kind vor Parent in der Liste, unbekannter `Data`-Typ) – kein Absturz, korrekter Baum → `DependencyGraphMapperTest` (Task 3).

---

## Dateistruktur

```
settings.gradle.kts                     Projektname
build.gradle.kts                        Plugin-Build
gradle/wrapper/*, gradlew, gradlew.bat  Gradle Wrapper 9.9.1
src/main/resources/META-INF/plugin.xml  Plugin-Deskriptor + Tool-Window
src/main/java/io/github/einsnils/deplens/
  model/DependencyNode.java             Ein Vorkommen (Modul oder Artefakt) in einer Configuration
  model/DependencyGraph.java            Wurzeln (eine pro Configuration) + Traversierung
  search/SearchHit.java                 Ein Treffer + Pfad + Ortsbeschreibung
  search/DependencySearch.java          Suche im Graph
  gradle/DependencyGraphMapper.java     Analyzer-API -> DependencyGraph
  gradle/GradleDependencySource.java    Contributor-Zugriff, Module, Laden, Refresh
  ui/RequestSequencer.java              "Nur das neueste Ergebnis zählt"
  ui/TreeModels.java                    Swing-TreeModels für Parents/Children
  ui/NodeText.java                      Gemeinsame Darstellung (Farben, Icons, Tooltips)
  ui/DependencyTreeCellRenderer.java    Renderer für beide Bäume
  ui/SearchHitCellRenderer.java         Renderer für die Trefferliste
  ui/DepLensPanel.java                  Gesamtes Tool-Window-Layout + Abläufe
  ui/DepLensToolWindowFactory.java      Registriert das Panel
src/test/java/io/github/einsnils/deplens/
  model/TestGraphs.java                 Gemeinsamer Test-Graph
  model/DependencyGraphTest.java
  search/DependencySearchTest.java
  gradle/Fakes.java                     Fake-Implementierungen der Analyzer-Interfaces
  gradle/DependencyGraphMapperTest.java
  ui/RequestSequencerTest.java
  ui/TreeModelsTest.java
samples/demo/                           Eigenständiges Gradle-Beispielprojekt für die manuelle Prüfung
README.md
```

---

### Task 1: Gradle-Gerüst und Datenmodell

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, Gradle Wrapper, `src/main/resources/META-INF/plugin.xml`
- Create: `src/main/java/io/github/einsnils/deplens/model/DependencyNode.java`
- Create: `src/main/java/io/github/einsnils/deplens/model/DependencyGraph.java`
- Test: `src/test/java/io/github/einsnils/deplens/model/TestGraphs.java`, `src/test/java/io/github/einsnils/deplens/model/DependencyGraphTest.java`

**Interfaces:**
- Produces:
  - `DependencyNode.module(String name, String configuration, @Nullable DependencyNode parent)` / `DependencyNode.artifact(String groupId, String artifactId, String version, String configuration, @Nullable DependencyNode parent)` – Konstruktion hängt den Knoten automatisch an `parent.children()` an.
  - Getter: `kind()` (`DependencyNode.Kind.MODULE|ARTIFACT`), `groupId()`, `artifactId()`, `version()`, `name()` (Modulname bzw. artifactId), `configuration()`, `parent()` (nullable), `children()` (unveränderlich), `coordinates()` (`g:a:v` bzw. Modulname), `pathFromRoot()`, `isRoot()`, `isOmitted()`, `isUnresolved()`, `hasConflict()`, `conflict()` (nullable).
  - Mutatoren: `markOmitted()`, `markUnresolved()`, `markConflict(String message)`.
  - `new DependencyGraph(String moduleName, List<DependencyNode> roots)`, `DependencyGraph.empty(String moduleName)`, `moduleName()`, `roots()`, `allNodes()` (Preorder über alle Wurzeln), `isEmpty()`.
  - Test-Fixture `TestGraphs` (öffentliche Felder, siehe Step 6).

- [ ] **Step 1: Branch anlegen**

```bash
cd /p/DepLens
git switch docs/design-spec
git switch -c feat/dependency-explorer
```

- [ ] **Step 2: `settings.gradle.kts` schreiben**

```kotlin
rootProject.name = "DepLens"
```

- [ ] **Step 3: Gradle Wrapper 9.9.1 erzeugen** (mit einer lokal vorhandenen Gradle-Distribution)

```bash
cd /p/DepLens
GRADLE=$(ls -d ~/.gradle/wrapper/dists/gradle-9.8.0-bin/*/gradle-9.8.0/bin/gradle | head -1)
"$GRADLE" wrapper --gradle-version 9.9.1 --distribution-type bin
./gradlew --version
```

Expected: Ausgabe enthält `Gradle 9.9.1`.

- [ ] **Step 4: `build.gradle.kts` schreiben**

```kotlin
plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "io.github.einsnils"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.2.3")
        bundledPlugin("com.intellij.gradle")
    }

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
    // IntelliJ-Plattform erwartet JUnit 4 auf dem Test-Runtime-Classpath
    testRuntimeOnly("junit:junit:4.13.2")
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = provider { null }
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}
```

Falls `intellijIdea(...)` in dieser Plugin-Version nicht aufgelöst wird, stattdessen `create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaUltimate, "2026.2.3")` verwenden.

- [ ] **Step 5: `src/main/resources/META-INF/plugin.xml` schreiben** (Tool-Window kommt in Task 5)

```xml
<idea-plugin>
    <id>io.github.einsnils.deplens</id>
    <name>DepLens</name>
    <vendor>EinsNils</vendor>
    <description><![CDATA[
        Search Gradle dependencies per module and see who pulls them in (parents)
        and what they bring along (children).
    ]]></description>

    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.gradle</depends>
</idea-plugin>
```

- [ ] **Step 6: Test-Fixture `TestGraphs` schreiben**

`src/test/java/io/github/einsnils/deplens/model/TestGraphs.java`:

```java
package io.github.einsnils.deplens.model;

import java.util.List;

/**
 * Kleiner Graph für Tests.
 *
 * <pre>
 * :app (runtimeClasspath)
 *   spring-boot-starter-web
 *     spring-boot-starter-json
 *       jackson-databind 2.15.2  [Konflikt]
 *         jackson-core 2.15.2
 *   :core
 *     jackson-core 2.15.2  [(*) omitted]
 * :app (testRuntimeClasspath)
 *   jackson-databind 2.15.2
 * </pre>
 */
public final class TestGraphs {
    public static final String JACKSON = "com.fasterxml.jackson.core";
    public static final String BOOT = "org.springframework.boot";

    public final DependencyNode runtimeRoot;
    public final DependencyNode web;
    public final DependencyNode json;
    public final DependencyNode databindRuntime;
    public final DependencyNode jacksonCore;
    public final DependencyNode coreModule;
    public final DependencyNode omittedCore;
    public final DependencyNode testRoot;
    public final DependencyNode databindTest;
    public final DependencyGraph graph;

    public TestGraphs() {
        runtimeRoot = DependencyNode.module(":app", "runtimeClasspath", null);
        web = DependencyNode.artifact(BOOT, "spring-boot-starter-web", "3.5.0", "runtimeClasspath", runtimeRoot);
        json = DependencyNode.artifact(BOOT, "spring-boot-starter-json", "3.5.0", "runtimeClasspath", web);
        databindRuntime = DependencyNode.artifact(JACKSON, "jackson-databind", "2.15.2", "runtimeClasspath", json);
        databindRuntime.markConflict("conflict with 2.13.0");
        jacksonCore = DependencyNode.artifact(JACKSON, "jackson-core", "2.15.2", "runtimeClasspath", databindRuntime);
        coreModule = DependencyNode.module(":core", "runtimeClasspath", runtimeRoot);
        omittedCore = DependencyNode.artifact(JACKSON, "jackson-core", "2.15.2", "runtimeClasspath", coreModule);
        omittedCore.markOmitted();
        testRoot = DependencyNode.module(":app", "testRuntimeClasspath", null);
        databindTest = DependencyNode.artifact(JACKSON, "jackson-databind", "2.15.2", "testRuntimeClasspath", testRoot);
        graph = new DependencyGraph(":app", List.of(runtimeRoot, testRoot));
    }
}
```

- [ ] **Step 7: Failing Test `DependencyGraphTest` schreiben**

`src/test/java/io/github/einsnils/deplens/model/DependencyGraphTest.java`:

```java
package io.github.einsnils.deplens.model;

import static io.github.einsnils.deplens.model.TestGraphs.JACKSON;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DependencyGraphTest {
    private final TestGraphs g = new TestGraphs();

    @Test
    void artifactCoordinatesAreGroupArtifactVersion() {
        assertThat(g.databindRuntime.coordinates()).isEqualTo(JACKSON + ":jackson-databind:2.15.2");
        assertThat(g.databindRuntime.name()).isEqualTo("jackson-databind");
        assertThat(g.databindRuntime.kind()).isEqualTo(DependencyNode.Kind.ARTIFACT);
    }

    @Test
    void moduleCoordinatesAreModuleName() {
        assertThat(g.coreModule.coordinates()).isEqualTo(":core");
        assertThat(g.coreModule.kind()).isEqualTo(DependencyNode.Kind.MODULE);
    }

    @Test
    void creatingWithParentLinksChildren() {
        assertThat(g.runtimeRoot.children()).containsExactly(g.web, g.coreModule);
        assertThat(g.web.children()).containsExactly(g.json);
        assertThat(g.json.parent()).isSameAs(g.web);
    }

    @Test
    void onlyNodesWithoutParentAreRoots() {
        assertThat(g.runtimeRoot.isRoot()).isTrue();
        assertThat(g.web.isRoot()).isFalse();
    }

    @Test
    void pathFromRootStartsAtModuleAndEndsAtNode() {
        assertThat(g.jacksonCore.pathFromRoot())
                .containsExactly(g.runtimeRoot, g.web, g.json, g.databindRuntime, g.jacksonCore);
    }

    @Test
    void statusFlags() {
        assertThat(g.databindRuntime.hasConflict()).isTrue();
        assertThat(g.databindRuntime.conflict()).isEqualTo("conflict with 2.13.0");
        assertThat(g.omittedCore.isOmitted()).isTrue();
        assertThat(g.web.hasConflict()).isFalse();
        assertThat(g.web.conflict()).isNull();
        assertThat(g.web.isOmitted()).isFalse();
        assertThat(g.web.isUnresolved()).isFalse();

        g.web.markUnresolved();
        assertThat(g.web.isUnresolved()).isTrue();
    }

    @Test
    void allNodesIsPreorderOverAllRoots() {
        assertThat(g.graph.allNodes()).containsExactly(
                g.runtimeRoot, g.web, g.json, g.databindRuntime, g.jacksonCore,
                g.coreModule, g.omittedCore,
                g.testRoot, g.databindTest);
    }

    @Test
    void emptyGraph() {
        DependencyGraph empty = DependencyGraph.empty(":x");
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.moduleName()).isEqualTo(":x");
        assertThat(empty.allNodes()).isEmpty();
        assertThat(g.graph.isEmpty()).isFalse();
    }
}
```

- [ ] **Step 8: Test ausführen, er muss fehlschlagen**

Run: `./gradlew test --tests "io.github.einsnils.deplens.model.*"`
Expected: FAIL – Kompilierfehler `cannot find symbol: class DependencyNode`.

- [ ] **Step 9: `DependencyNode` implementieren**

`src/main/java/io/github/einsnils/deplens/model/DependencyNode.java`:

```java
package io.github.einsnils.deplens.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Ein Vorkommen einer Dependency (oder des Moduls selbst) in genau einer Gradle-Configuration. */
public final class DependencyNode {
    public enum Kind { MODULE, ARTIFACT }

    private final Kind kind;
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String name;
    private final String configuration;
    private final @Nullable DependencyNode parent;
    private final List<DependencyNode> children = new ArrayList<>();
    private boolean omitted;
    private boolean unresolved;
    private @Nullable String conflict;

    private DependencyNode(Kind kind, String groupId, String artifactId, String version, String name,
                           String configuration, @Nullable DependencyNode parent) {
        this.kind = kind;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.name = name;
        this.configuration = configuration;
        this.parent = parent;
        if (parent != null) {
            parent.children.add(this);
        }
    }

    public static DependencyNode module(String name, String configuration, @Nullable DependencyNode parent) {
        return new DependencyNode(Kind.MODULE, "", "", "", name, configuration, parent);
    }

    public static DependencyNode artifact(String groupId, String artifactId, String version,
                                          String configuration, @Nullable DependencyNode parent) {
        return new DependencyNode(Kind.ARTIFACT, groupId, artifactId, version, artifactId, configuration, parent);
    }

    public Kind kind() { return kind; }
    public String groupId() { return groupId; }
    public String artifactId() { return artifactId; }
    public String version() { return version; }
    public String name() { return name; }
    public String configuration() { return configuration; }
    public @Nullable DependencyNode parent() { return parent; }
    public List<DependencyNode> children() { return Collections.unmodifiableList(children); }
    public boolean isRoot() { return parent == null; }
    public boolean isOmitted() { return omitted; }
    public boolean isUnresolved() { return unresolved; }
    public boolean hasConflict() { return conflict != null; }
    public @Nullable String conflict() { return conflict; }

    public void markOmitted() { omitted = true; }
    public void markUnresolved() { unresolved = true; }
    public void markConflict(String message) { conflict = message; }

    /** {@code group:artifact:version} für Artefakte, sonst der Modulname. */
    public String coordinates() {
        return kind == Kind.ARTIFACT ? groupId + ":" + artifactId + ":" + version : name;
    }

    /** Pfad von der Modul-Wurzel bis einschließlich dieses Knotens. */
    public List<DependencyNode> pathFromRoot() {
        Deque<DependencyNode> path = new ArrayDeque<>();
        for (DependencyNode n = this; n != null; n = n.parent) {
            path.addFirst(n);
        }
        return List.copyOf(path);
    }

    @Override
    public String toString() {
        return coordinates();
    }
}
```

- [ ] **Step 10: `DependencyGraph` implementieren**

`src/main/java/io/github/einsnils/deplens/model/DependencyGraph.java`:

```java
package io.github.einsnils.deplens.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Alle Dependency-Bäume eines Moduls, eine Wurzel pro Gradle-Configuration. */
public final class DependencyGraph {
    private final String moduleName;
    private final List<DependencyNode> roots;

    public DependencyGraph(String moduleName, List<DependencyNode> roots) {
        this.moduleName = moduleName;
        this.roots = List.copyOf(roots);
    }

    public static DependencyGraph empty(String moduleName) {
        return new DependencyGraph(moduleName, List.of());
    }

    public String moduleName() { return moduleName; }
    public List<DependencyNode> roots() { return roots; }
    public boolean isEmpty() { return roots.isEmpty(); }

    /** Alle Knoten in Preorder, Wurzel für Wurzel. */
    public List<DependencyNode> allNodes() {
        List<DependencyNode> result = new ArrayList<>();
        Deque<DependencyNode> stack = new ArrayDeque<>();
        for (DependencyNode root : roots.reversed()) {
            stack.push(root);
        }
        while (!stack.isEmpty()) {
            DependencyNode node = stack.pop();
            result.add(node);
            for (DependencyNode child : node.children().reversed()) {
                stack.push(child);
            }
        }
        return result;
    }
}
```

- [ ] **Step 11: Tests ausführen, sie müssen grün sein**

Run: `./gradlew test --tests "io.github.einsnils.deplens.model.*"`
Expected: PASS (8 Tests). Beim ersten Lauf lädt Gradle die IDE (~1–2 GB); das dauert.

- [ ] **Step 12: Commit und Push**

```bash
git add .
git commit -m "Add Gradle plugin scaffold and dependency model

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push -u origin feat/dependency-explorer
```

---

### Task 2: Suche

**Files:**
- Create: `src/main/java/io/github/einsnils/deplens/search/SearchHit.java`
- Create: `src/main/java/io/github/einsnils/deplens/search/DependencySearch.java`
- Test: `src/test/java/io/github/einsnils/deplens/search/DependencySearchTest.java`

**Interfaces:**
- Consumes: `DependencyGraph.allNodes()`, `DependencyNode.coordinates()/configuration()/parent()/isRoot()/name()/pathFromRoot()`, `TestGraphs`.
- Produces:
  - `record SearchHit(DependencyNode node)` mit `List<DependencyNode> path()` und `String location()` (`"<configuration> · direkt"` oder `"<configuration> · via <parent.name()>"`).
  - `static List<SearchHit> DependencySearch.search(DependencyGraph graph, @Nullable String query)` – sortiert nach Koordinate, dann Configuration, dann `location()`.

- [ ] **Step 1: Failing Test schreiben**

`src/test/java/io/github/einsnils/deplens/search/DependencySearchTest.java`:

```java
package io.github.einsnils.deplens.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.einsnils.deplens.model.TestGraphs;
import org.junit.jupiter.api.Test;

class DependencySearchTest {
    private final TestGraphs g = new TestGraphs();

    @Test
    void findsEveryOccurrenceAcrossConfigurations() {
        assertThat(DependencySearch.search(g.graph, "jackson-databind"))
                .extracting(SearchHit::node)
                .containsExactly(g.databindRuntime, g.databindTest);
    }

    @Test
    void ignoresCaseAndSurroundingWhitespaceAndMatchesVersion() {
        assertThat(DependencySearch.search(g.graph, "  JACKSON-core:2.15 "))
                .extracting(SearchHit::node)
                .containsExactly(g.omittedCore, g.jacksonCore);
    }

    @Test
    void blankQueryFindsNothing() {
        assertThat(DependencySearch.search(g.graph, "")).isEmpty();
        assertThat(DependencySearch.search(g.graph, "   ")).isEmpty();
        assertThat(DependencySearch.search(g.graph, null)).isEmpty();
    }

    @Test
    void moduleRootIsNeverAHitButProjectDependenciesAre() {
        assertThat(DependencySearch.search(g.graph, ":app")).isEmpty();
        assertThat(DependencySearch.search(g.graph, ":core"))
                .extracting(SearchHit::node)
                .containsExactly(g.coreModule);
    }

    @Test
    void omittedOccurrencesAreHits() {
        assertThat(DependencySearch.search(g.graph, "jackson-core"))
                .extracting(SearchHit::node)
                .contains(g.omittedCore);
    }

    @Test
    void noMatchGivesEmptyList() {
        assertThat(DependencySearch.search(g.graph, "guava")).isEmpty();
    }

    @Test
    void locationNamesConfigurationAndDirectParent() {
        assertThat(new SearchHit(g.databindTest).location()).isEqualTo("testRuntimeClasspath · direkt");
        assertThat(new SearchHit(g.coreModule).location()).isEqualTo("runtimeClasspath · direkt");
        assertThat(new SearchHit(g.databindRuntime).location())
                .isEqualTo("runtimeClasspath · via spring-boot-starter-json");
    }

    @Test
    void pathRunsFromModuleToHit() {
        assertThat(new SearchHit(g.databindRuntime).path())
                .containsExactly(g.runtimeRoot, g.web, g.json, g.databindRuntime);
    }
}
```

- [ ] **Step 2: Test ausführen, er muss fehlschlagen**

Run: `./gradlew test --tests "io.github.einsnils.deplens.search.*"`
Expected: FAIL – `cannot find symbol: class DependencySearch`.

- [ ] **Step 3: `SearchHit` implementieren**

```java
package io.github.einsnils.deplens.search;

import io.github.einsnils.deplens.model.DependencyNode;
import java.util.List;

/** Ein Vorkommen der gesuchten Dependency. */
public record SearchHit(DependencyNode node) {

    public List<DependencyNode> path() {
        return node.pathFromRoot();
    }

    /** z. B. {@code "runtimeClasspath · via spring-boot-starter-json"}. */
    public String location() {
        DependencyNode parent = node.parent();
        String via = parent == null || parent.isRoot() ? "direkt" : "via " + parent.name();
        return node.configuration() + " · " + via;
    }
}
```

- [ ] **Step 4: `DependencySearch` implementieren**

```java
package io.github.einsnils.deplens.search;

import io.github.einsnils.deplens.model.DependencyGraph;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** Teilstring-Suche (ohne Groß-/Kleinschreibung) auf {@code group:artifact:version}. */
public final class DependencySearch {
    private static final Comparator<SearchHit> ORDER = Comparator
            .comparing((SearchHit hit) -> hit.node().coordinates())
            .thenComparing(hit -> hit.node().configuration())
            .thenComparing(SearchHit::location);

    private DependencySearch() {
    }

    public static List<SearchHit> search(DependencyGraph graph, @Nullable String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return List.of();
        }
        return graph.allNodes().stream()
                .filter(node -> !node.isRoot())
                .filter(node -> node.coordinates().toLowerCase(Locale.ROOT).contains(needle))
                .map(SearchHit::new)
                .sorted(ORDER)
                .toList();
    }
}
```

- [ ] **Step 5: Tests ausführen, sie müssen grün sein**

Run: `./gradlew test --tests "io.github.einsnils.deplens.search.*"`
Expected: PASS (8 Tests).

- [ ] **Step 6: Commit und Push**

```bash
git add src
git commit -m "Add dependency search

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push
```

---

### Task 3: Mapping von IntelliJs Analyzer-API auf das Modell

**Files:**
- Create: `src/main/java/io/github/einsnils/deplens/gradle/DependencyGraphMapper.java`
- Test: `src/test/java/io/github/einsnils/deplens/gradle/Fakes.java`, `src/test/java/io/github/einsnils/deplens/gradle/DependencyGraphMapperTest.java`

**Interfaces:**
- Consumes: `DependencyNode.module/artifact/markOmitted/markUnresolved/markConflict`, `DependencyGraph`.
- Consumes (IntelliJ, Paket `com.intellij.openapi.externalSystem.dependency.analyzer`): `DependencyAnalyzerDependency` (`getData()`, `getScope()`, `getParent()`, `getStatus()`), `.Data.Artifact` (`getGroupId/getArtifactId/getVersion`), `.Data.Module` (`getName`), `.Scope` (`getName/getTitle/getType`), `.Status.Omitted`, `.Status.Warning` (`getMessage`). Alle erben von `com.intellij.openapi.util.UserDataHolder`.
- Produces: `new DependencyGraphMapper(String unresolvedMessage)`, `DependencyGraph map(String moduleName, List<? extends DependencyAnalyzerDependency> dependencies)`.

Hintergrund: IntelliJs Gradle-Contributor meldet nicht auflösbare Dependencies als `Warning` mit der Nachricht `ExternalSystemBundle.message("external.system.dependency.analyzer.warning.unresolved")` (englisch: `"unresolved"`) und Versionskonflikte als `Warning` mit `"conflict with <andere Version>"`. Der Mapper bekommt den Unresolved-Text deshalb von außen, damit er testbar bleibt.

- [ ] **Step 1: Fakes schreiben**

`src/test/java/io/github/einsnils/deplens/gradle/Fakes.java`:

```java
package io.github.einsnils.deplens.gradle;

import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency;
import com.intellij.openapi.util.UserDataHolderBase;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Minimal-Implementierungen der Analyzer-Interfaces für Tests. */
final class Fakes {
    private Fakes() {
    }

    static final class FakeScope extends UserDataHolderBase implements DependencyAnalyzerDependency.Scope {
        private final String name;

        FakeScope(String name) { this.name = name; }

        @Override public @NotNull String getName() { return name; }
        @Override public @NotNull String getTitle() { return name; }
        @Override public @NotNull DependencyAnalyzerDependency.Scope.Type getType() {
            return DependencyAnalyzerDependency.Scope.Type.STANDARD;
        }
    }

    static final class FakeArtifact extends UserDataHolderBase implements DependencyAnalyzerDependency.Data.Artifact {
        private final String groupId;
        private final String artifactId;
        private final String version;

        FakeArtifact(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }

        @Override public @NotNull String getGroupId() { return groupId; }
        @Override public @NotNull String getArtifactId() { return artifactId; }
        @Override public @NotNull String getVersion() { return version; }
    }

    static final class FakeModule extends UserDataHolderBase implements DependencyAnalyzerDependency.Data.Module {
        private final String name;

        FakeModule(String name) { this.name = name; }

        @Override public @NotNull String getName() { return name; }
    }

    static final class FakeOtherData extends UserDataHolderBase implements DependencyAnalyzerDependency.Data {
        @Override public String toString() { return "something-else"; }
    }

    static final class FakeOmitted extends UserDataHolderBase implements DependencyAnalyzerDependency.Status.Omitted {
        @Override public @NotNull String getTitle() { return "Omitted"; }
    }

    static final class FakeWarning extends UserDataHolderBase implements DependencyAnalyzerDependency.Status.Warning {
        private final String message;

        FakeWarning(String message) { this.message = message; }

        @Override public @NotNull String getTitle() { return message; }
        @Override public @NotNull String getMessage() { return message; }
    }

    static final class FakeDep extends UserDataHolderBase implements DependencyAnalyzerDependency {
        private final Data data;
        private final Scope scope;
        private final @Nullable FakeDep parent;
        private final List<Status> status;

        FakeDep(Data data, Scope scope, @Nullable FakeDep parent, Status... status) {
            this.data = data;
            this.scope = scope;
            this.parent = parent;
            this.status = List.of(status);
        }

        @Override public @NotNull Data getData() { return data; }
        @Override public @NotNull Scope getScope() { return scope; }
        @Override public @Nullable DependencyAnalyzerDependency getParent() { return parent; }
        @Override public @NotNull List<Status> getStatus() { return status; }
    }
}
```

- [ ] **Step 2: Failing Test schreiben**

`src/test/java/io/github/einsnils/deplens/gradle/DependencyGraphMapperTest.java`:

```java
package io.github.einsnils.deplens.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.einsnils.deplens.gradle.Fakes.FakeArtifact;
import io.github.einsnils.deplens.gradle.Fakes.FakeDep;
import io.github.einsnils.deplens.gradle.Fakes.FakeModule;
import io.github.einsnils.deplens.gradle.Fakes.FakeOmitted;
import io.github.einsnils.deplens.gradle.Fakes.FakeOtherData;
import io.github.einsnils.deplens.gradle.Fakes.FakeScope;
import io.github.einsnils.deplens.gradle.Fakes.FakeWarning;
import io.github.einsnils.deplens.model.DependencyGraph;
import io.github.einsnils.deplens.model.DependencyNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class DependencyGraphMapperTest {
    private static final String UNRESOLVED = "unresolved";
    private static final String JACKSON = "com.fasterxml.jackson.core";

    private final DependencyGraphMapper mapper = new DependencyGraphMapper(UNRESOLVED);
    private final FakeScope runtime = new FakeScope("runtimeClasspath");
    private final FakeDep root = new FakeDep(new FakeModule(":app"), runtime, null);
    private final FakeDep web = new FakeDep(
            new FakeArtifact("org.springframework.boot", "spring-boot-starter-web", "3.5.0"), runtime, root);
    private final FakeDep databind = new FakeDep(
            new FakeArtifact(JACKSON, "jackson-databind", "2.15.2"), runtime, web);

    @Test
    void mapsParentLinksIntoATree() {
        DependencyGraph graph = mapper.map(":app", List.of(root, web, databind));

        assertThat(graph.moduleName()).isEqualTo(":app");
        assertThat(graph.roots()).hasSize(1);
        DependencyNode rootNode = graph.roots().getFirst();
        assertThat(rootNode.name()).isEqualTo(":app");
        assertThat(rootNode.configuration()).isEqualTo("runtimeClasspath");
        assertThat(graph.allNodes()).extracting(DependencyNode::coordinates).containsExactly(
                ":app",
                "org.springframework.boot:spring-boot-starter-web:3.5.0",
                JACKSON + ":jackson-databind:2.15.2");
    }

    @Test
    void childListedBeforeParentIsStillLinkedOnce() {
        DependencyGraph graph = mapper.map(":app", List.of(databind, web, root));

        assertThat(graph.roots()).hasSize(1);
        assertThat(graph.allNodes()).hasSize(3);
        assertThat(graph.allNodes().getLast().pathFromRoot()).hasSize(3);
    }

    @Test
    void parentNotInListIsStillCreated() {
        DependencyGraph graph = mapper.map(":app", List.of(databind));

        assertThat(graph.allNodes()).hasSize(3);
    }

    @Test
    void oneRootPerConfiguration() {
        FakeDep testRoot = new FakeDep(new FakeModule(":app"), new FakeScope("testRuntimeClasspath"), null);

        DependencyGraph graph = mapper.map(":app", List.of(root, testRoot));

        assertThat(graph.roots()).extracting(DependencyNode::configuration)
                .containsExactly("runtimeClasspath", "testRuntimeClasspath");
    }

    @Test
    void projectDependencyBecomesModuleNode() {
        FakeDep core = new FakeDep(new FakeModule(":core"), runtime, root);

        DependencyNode node = mapper.map(":app", List.of(root, core)).allNodes().getLast();

        assertThat(node.kind()).isEqualTo(DependencyNode.Kind.MODULE);
        assertThat(node.coordinates()).isEqualTo(":core");
    }

    @Test
    void unknownDataFallsBackToItsText() {
        FakeDep other = new FakeDep(new FakeOtherData(), runtime, root);

        DependencyNode node = mapper.map(":app", List.of(root, other)).allNodes().getLast();

        assertThat(node.kind()).isEqualTo(DependencyNode.Kind.MODULE);
        assertThat(node.name()).isEqualTo("something-else");
    }

    @Test
    void mapsStatuses() {
        FakeDep omitted = new FakeDep(new FakeArtifact(JACKSON, "jackson-core", "2.15.2"), runtime, root,
                new FakeOmitted());
        FakeDep unresolved = new FakeDep(new FakeArtifact("does.not", "exist", "1.0"), runtime, root,
                new FakeWarning(UNRESOLVED));
        FakeDep conflict = new FakeDep(new FakeArtifact(JACKSON, "jackson-databind", "2.15.2"), runtime, root,
                new FakeWarning("conflict with 2.13.0"));

        List<DependencyNode> nodes = mapper.map(":app", List.of(root, omitted, unresolved, conflict)).allNodes();

        assertThat(nodes.get(1).isOmitted()).isTrue();
        assertThat(nodes.get(1).hasConflict()).isFalse();
        assertThat(nodes.get(2).isUnresolved()).isTrue();
        assertThat(nodes.get(2).hasConflict()).isFalse();
        assertThat(nodes.get(3).conflict()).isEqualTo("conflict with 2.13.0");
        assertThat(nodes.get(3).isUnresolved()).isFalse();
    }
}
```

- [ ] **Step 3: Test ausführen, er muss fehlschlagen**

Run: `./gradlew test --tests "io.github.einsnils.deplens.gradle.*"`
Expected: FAIL – `cannot find symbol: class DependencyGraphMapper`.

- [ ] **Step 4: `DependencyGraphMapper` implementieren**

```java
package io.github.einsnils.deplens.gradle;

import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency;
import io.github.einsnils.deplens.model.DependencyGraph;
import io.github.einsnils.deplens.model.DependencyNode;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Übersetzt die flache Analyzer-Liste (mit Parent-Zeigern) in einen {@link DependencyGraph}. */
public final class DependencyGraphMapper {
    private final String unresolvedMessage;

    public DependencyGraphMapper(String unresolvedMessage) {
        this.unresolvedMessage = unresolvedMessage;
    }

    public DependencyGraph map(String moduleName, List<? extends DependencyAnalyzerDependency> dependencies) {
        Map<DependencyAnalyzerDependency, DependencyNode> nodes = new IdentityHashMap<>();
        List<DependencyNode> roots = new ArrayList<>();
        for (DependencyAnalyzerDependency dependency : dependencies) {
            toNode(dependency, nodes, roots);
        }
        return new DependencyGraph(moduleName, roots);
    }

    private DependencyNode toNode(DependencyAnalyzerDependency dependency,
                                  Map<DependencyAnalyzerDependency, DependencyNode> nodes,
                                  List<DependencyNode> roots) {
        DependencyNode existing = nodes.get(dependency);
        if (existing != null) {
            return existing;
        }
        DependencyAnalyzerDependency parentDependency = dependency.getParent();
        DependencyNode parent = parentDependency == null ? null : toNode(parentDependency, nodes, roots);
        DependencyNode node = create(dependency, parent);
        applyStatus(dependency, node);
        nodes.put(dependency, node);
        if (parent == null) {
            roots.add(node);
        }
        return node;
    }

    private static DependencyNode create(DependencyAnalyzerDependency dependency, @Nullable DependencyNode parent) {
        String configuration = dependency.getScope().getName();
        return switch (dependency.getData()) {
            case DependencyAnalyzerDependency.Data.Artifact artifact -> DependencyNode.artifact(
                    artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), configuration, parent);
            case DependencyAnalyzerDependency.Data.Module module ->
                    DependencyNode.module(module.getName(), configuration, parent);
            case DependencyAnalyzerDependency.Data other ->
                    DependencyNode.module(String.valueOf(other), configuration, parent);
        };
    }

    private void applyStatus(DependencyAnalyzerDependency dependency, DependencyNode node) {
        for (DependencyAnalyzerDependency.Status status : dependency.getStatus()) {
            if (status instanceof DependencyAnalyzerDependency.Status.Omitted) {
                node.markOmitted();
            } else if (status instanceof DependencyAnalyzerDependency.Status.Warning warning) {
                if (unresolvedMessage.equals(warning.getMessage())) {
                    node.markUnresolved();
                } else {
                    node.markConflict(warning.getMessage());
                }
            }
        }
    }
}
```

- [ ] **Step 5: Tests ausführen, sie müssen grün sein**

Run: `./gradlew test --tests "io.github.einsnils.deplens.gradle.*"`
Expected: PASS (7 Tests).

- [ ] **Step 6: Commit und Push**

```bash
git add src
git commit -m "Map IntelliJ dependency analyzer data to model

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push
```

---

### Task 4: UI-Logik ohne IDE (TreeModels, RequestSequencer)

**Files:**
- Create: `src/main/java/io/github/einsnils/deplens/ui/TreeModels.java`
- Create: `src/main/java/io/github/einsnils/deplens/ui/RequestSequencer.java`
- Test: `src/test/java/io/github/einsnils/deplens/ui/TreeModelsTest.java`, `src/test/java/io/github/einsnils/deplens/ui/RequestSequencerTest.java`

**Interfaces:**
- Consumes: `SearchHit.path()`, `DependencyNode.children()`, `TestGraphs`.
- Produces:
  - `static DefaultTreeModel TreeModels.parentPath(SearchHit hit)` – lineare Kette Modul → … → Treffer; `userObject` jedes `DefaultMutableTreeNode` ist ein `DependencyNode`.
  - `static DefaultTreeModel TreeModels.children(DependencyNode node)` – rekursive Kopie ab `node`.
  - `static DefaultTreeModel TreeModels.empty()`.
  - `RequestSequencer`: `long next()`, `boolean isCurrent(long id)`.

- [ ] **Step 1: Failing Tests schreiben**

`src/test/java/io/github/einsnils/deplens/ui/TreeModelsTest.java`:

```java
package io.github.einsnils.deplens.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.einsnils.deplens.model.TestGraphs;
import io.github.einsnils.deplens.search.SearchHit;
import java.util.Collections;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;

class TreeModelsTest {
    private final TestGraphs g = new TestGraphs();

    @Test
    void parentPathIsAChainFromModuleToHit() {
        DefaultTreeModel model = TreeModels.parentPath(new SearchHit(g.databindRuntime));

        assertThat(userObjects(model)).containsExactly(g.runtimeRoot, g.web, g.json, g.databindRuntime);
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        assertThat(root.getDepth()).isEqualTo(3);
    }

    @Test
    void childrenCopiesTheWholeSubtree() {
        DefaultTreeModel model = TreeModels.children(g.runtimeRoot);

        assertThat(userObjects(model)).containsExactly(
                g.runtimeRoot, g.web, g.json, g.databindRuntime, g.jacksonCore, g.coreModule, g.omittedCore);
    }

    @Test
    void omittedNodeHasNoChildren() {
        DefaultTreeModel model = TreeModels.children(g.omittedCore);

        assertThat(userObjects(model)).containsExactly(g.omittedCore);
    }

    @Test
    void emptyModelHasNoRoot() {
        assertThat(TreeModels.empty().getRoot()).isNull();
    }

    private static List<Object> userObjects(DefaultTreeModel model) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        return Collections.list(root.preorderEnumeration()).stream()
                .map(node -> ((DefaultMutableTreeNode) node).getUserObject())
                .toList();
    }
}
```

`src/test/java/io/github/einsnils/deplens/ui/RequestSequencerTest.java`:

```java
package io.github.einsnils.deplens.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestSequencerTest {

    @Test
    void onlyTheNewestRequestIsCurrent() {
        RequestSequencer sequencer = new RequestSequencer();

        long first = sequencer.next();
        assertThat(sequencer.isCurrent(first)).isTrue();

        long second = sequencer.next();
        assertThat(sequencer.isCurrent(first)).isFalse();
        assertThat(sequencer.isCurrent(second)).isTrue();
    }
}
```

- [ ] **Step 2: Tests ausführen, sie müssen fehlschlagen**

Run: `./gradlew test --tests "io.github.einsnils.deplens.ui.*"`
Expected: FAIL – `cannot find symbol: class TreeModels`.

- [ ] **Step 3: `TreeModels` implementieren**

```java
package io.github.einsnils.deplens.ui;

import io.github.einsnils.deplens.model.DependencyNode;
import io.github.einsnils.deplens.search.SearchHit;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/** Baut die Swing-Modelle für den Parents- und den Children-Baum. */
public final class TreeModels {
    private TreeModels() {
    }

    public static DefaultTreeModel parentPath(SearchHit hit) {
        DefaultMutableTreeNode root = null;
        DefaultMutableTreeNode current = null;
        for (DependencyNode node : hit.path()) {
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
            if (current == null) {
                root = treeNode;
            } else {
                current.add(treeNode);
            }
            current = treeNode;
        }
        return new DefaultTreeModel(root);
    }

    public static DefaultTreeModel children(DependencyNode node) {
        return new DefaultTreeModel(copy(node));
    }

    public static DefaultTreeModel empty() {
        return new DefaultTreeModel(null);
    }

    private static DefaultMutableTreeNode copy(DependencyNode node) {
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
        for (DependencyNode child : node.children()) {
            treeNode.add(copy(child));
        }
        return treeNode;
    }
}
```

- [ ] **Step 4: `RequestSequencer` implementieren**

```java
package io.github.einsnils.deplens.ui;

import java.util.concurrent.atomic.AtomicLong;

/** Merkt sich die zuletzt gestartete Anfrage, damit veraltete Ergebnisse verworfen werden. */
public final class RequestSequencer {
    private final AtomicLong current = new AtomicLong();

    public long next() {
        return current.incrementAndGet();
    }

    public boolean isCurrent(long id) {
        return current.get() == id;
    }
}
```

- [ ] **Step 5: Alle Tests ausführen, sie müssen grün sein**

Run: `./gradlew test`
Expected: PASS (alle Tests aus Task 1–4).

- [ ] **Step 6: Commit und Push**

```bash
git add src
git commit -m "Add tree models and request sequencer

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push
```

---

### Task 5: Gradle-Anbindung und Tool-Window mit Modulauswahl

Ziel dieses Tasks: Das Tool-Window erscheint, listet die Gradle-Module, lädt das gewählte Modul im Hintergrund und zeigt Lade-, Leer- und Fehlerzustände. Suche, Trefferliste und Bäume kommen in Task 6.

**Files:**
- Create: `src/main/java/io/github/einsnils/deplens/gradle/GradleDependencySource.java`
- Create: `src/main/java/io/github/einsnils/deplens/ui/DepLensPanel.java`
- Create: `src/main/java/io/github/einsnils/deplens/ui/DepLensToolWindowFactory.java`
- Modify: `src/main/resources/META-INF/plugin.xml`

**Interfaces:**
- Consumes: `DependencyGraphMapper`, `DependencyGraph`, `RequestSequencer`.
- Consumes (IntelliJ): `DependencyAnalyzerExtension.EP_NAME`, `isApplicable(ProjectSystemId)`, `createContributor(Project, Disposable)`; `DependencyAnalyzerContributor.getProjects()/getDependencies(project)/whenDataChanged(Function0<Unit>, Disposable)`; `DependencyAnalyzerProject.getTitle()`; `GradleConstants.SYSTEM_ID`; `ExternalSystemBundle.message(String)`.
- Produces:
  - `new GradleDependencySource(Project project, Runnable onDataChanged)` (ist `Disposable`), `List<DependencyAnalyzerProject> modules()`, `DependencyGraph load(DependencyAnalyzerProject module)`, `void reset()`. `modules()` und `load()` blockieren und laufen nur im Hintergrund.
  - `DepLensPanel(Project project)` (`JPanel`, `Disposable`) mit den Feldern `graph` (aktueller `DependencyGraph`) und der Methode `onGraphLoaded()`, die Task 6 erweitert.

- [ ] **Step 1: `GradleDependencySource` schreiben**

```java
package io.github.einsnils.deplens.gradle;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerContributor;
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerExtension;
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerProject;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import io.github.einsnils.deplens.model.DependencyGraph;
import java.util.List;
import kotlin.Unit;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/** Einziger Zugang zu IntelliJs Gradle-Dependency-Analyzer. Methoden blockieren – nie auf dem EDT aufrufen. */
public final class GradleDependencySource implements Disposable {
    private final Project project;
    private final Runnable onDataChanged;
    private final DependencyGraphMapper mapper = new DependencyGraphMapper(
            ExternalSystemBundle.message("external.system.dependency.analyzer.warning.unresolved"));
    private @Nullable Disposable contributorDisposable;
    private @Nullable DependencyAnalyzerContributor contributor;

    public GradleDependencySource(Project project, Runnable onDataChanged) {
        this.project = project;
        this.onDataChanged = onDataChanged;
    }

    public List<DependencyAnalyzerProject> modules() {
        DependencyAnalyzerContributor current = contributor();
        return current == null ? List.of() : current.getProjects();
    }

    public DependencyGraph load(DependencyAnalyzerProject module) {
        DependencyAnalyzerContributor current = contributor();
        if (current == null) {
            return DependencyGraph.empty(module.getTitle());
        }
        return mapper.map(module.getTitle(), current.getDependencies(module));
    }

    /** Verwirft den Contributor samt Cache; der nächste Aufruf fragt Gradle neu. */
    public synchronized void reset() {
        if (contributorDisposable != null) {
            Disposer.dispose(contributorDisposable);
        }
        contributorDisposable = null;
        contributor = null;
    }

    private synchronized @Nullable DependencyAnalyzerContributor contributor() {
        if (contributor == null) {
            for (DependencyAnalyzerExtension extension : DependencyAnalyzerExtension.EP_NAME.getExtensionList()) {
                if (extension.isApplicable(GradleConstants.SYSTEM_ID)) {
                    contributorDisposable = Disposer.newDisposable(this, "DepLens contributor");
                    contributor = extension.createContributor(project, contributorDisposable);
                    contributor.whenDataChanged(() -> {
                        onDataChanged.run();
                        return Unit.INSTANCE;
                    }, contributorDisposable);
                    break;
                }
            }
        }
        return contributor;
    }

    @Override
    public void dispose() {
        // contributorDisposable ist Kind von this und wird von Disposer mit entsorgt.
    }
}
```

- [ ] **Step 2: Kompilieren**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. Falls `ExternalSystemBundle.message` nicht sichtbar ist, den Text `"unresolved"` als Konstante verwenden und das im Commit erwähnen.

- [ ] **Step 3: `DepLensPanel` (Grundgerüst) schreiben**

```java
package io.github.einsnils.deplens.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerProject;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.ui.JBUI;
import io.github.einsnils.deplens.gradle.GradleDependencySource;
import io.github.einsnils.deplens.model.DependencyGraph;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Inhalt des DepLens-Tool-Windows. */
public final class DepLensPanel extends JPanel implements Disposable {
    private static final Logger LOG = Logger.getInstance(DepLensPanel.class);
    private static final String CARD_CONTENT = "content";
    private static final String CARD_MESSAGE = "message";

    private final GradleDependencySource source;
    private final RequestSequencer sequencer = new RequestSequencer();
    private final ComboBox<DependencyAnalyzerProject> moduleBox = new ComboBox<>();
    private final JPanel toolbar = new JPanel(new BorderLayout(JBUI.scale(4), 0));
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JBLabel messageLabel = new JBLabel("", SwingConstants.CENTER);
    private final JButton retryButton = new JButton("Erneut versuchen");
    private final JBLoadingPanel loadingPanel;
    private volatile boolean disposed;
    private boolean updatingModules;

    DependencyGraph graph = DependencyGraph.empty("");

    public DepLensPanel(Project project) {
        super(new BorderLayout());
        source = new GradleDependencySource(project, () -> runOnEdt(this::reloadModules));
        Disposer.register(this, source);

        moduleBox.setRenderer(SimpleListCellRenderer.create("", DependencyAnalyzerProject::getTitle));
        moduleBox.addActionListener(e -> {
            if (!updatingModules) {
                loadSelectedModule();
            }
        });

        JButton refreshButton = new JButton(AllIcons.Actions.Refresh);
        refreshButton.setToolTipText("Neu laden");
        refreshButton.addActionListener(e -> {
            source.reset();
            reloadModules();
        });

        toolbar.setBorder(JBUI.Borders.empty(4));
        toolbar.add(moduleBox, BorderLayout.WEST);
        toolbar.add(refreshButton, BorderLayout.EAST);

        retryButton.addActionListener(e -> reloadModules());
        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.add(messageLabel, BorderLayout.CENTER);
        JPanel retryRow = new JPanel();
        retryRow.add(retryButton);
        messagePanel.add(retryRow, BorderLayout.SOUTH);

        cardPanel.add(createContent(), CARD_CONTENT);
        cardPanel.add(messagePanel, CARD_MESSAGE);

        loadingPanel = new JBLoadingPanel(new BorderLayout(), this);
        loadingPanel.add(cardPanel, BorderLayout.CENTER);

        add(toolbar, BorderLayout.NORTH);
        add(loadingPanel, BorderLayout.CENTER);

        reloadModules();
    }

    /** Hauptbereich; Task 6 ersetzt das durch Trefferliste und Bäume. */
    private JComponent createContent() {
        return new JBLabel("Modul geladen", SwingConstants.CENTER);
    }

    /** Wird auf dem EDT aufgerufen, nachdem {@link #graph} neu gesetzt wurde. */
    private void onGraphLoaded() {
        showCard(CARD_CONTENT);
    }

    private void reloadModules() {
        DependencyAnalyzerProject previous = (DependencyAnalyzerProject) moduleBox.getSelectedItem();
        String previousTitle = previous == null ? null : previous.getTitle();
        loadingPanel.startLoading();
        inBackground(source::modules, modules -> {
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
                showMessage("Kein Gradle-Projekt gefunden – bitte Gradle-Sync ausführen.", false);
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
        inBackground(() -> source.load(module), loaded -> {
            if (!sequencer.isCurrent(request)) {
                return;
            }
            loadingPanel.stopLoading();
            graph = loaded;
            onGraphLoaded();
        });
    }

    private <T> void inBackground(Supplier<T> work, Consumer<T> onEdt) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                T result = work.get();
                runOnEdt(() -> onEdt.accept(result));
            } catch (ProcessCanceledException e) {
                runOnEdt(loadingPanel::stopLoading);
            } catch (RuntimeException e) {
                LOG.warn("DepLens: loading failed", e);
                runOnEdt(() -> {
                    loadingPanel.stopLoading();
                    showMessage("Laden fehlgeschlagen: " + e.getMessage(), true);
                });
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

Hinweis: `ProcessCanceledException` darf nicht geloggt werden (IntelliJ meldet das sonst als Fehler); deshalb der eigene `catch`-Zweig.

- [ ] **Step 4: `DepLensToolWindowFactory` schreiben**

```java
package io.github.einsnils.deplens.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class DepLensToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        DepLensPanel panel = new DepLensPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
    }
}
```

- [ ] **Step 5: Tool-Window in `plugin.xml` registrieren** (vor `</idea-plugin>` einfügen)

```xml
    <extensions defaultExtensionNs="com.intellij">
        <toolWindow id="DepLens"
                    anchor="right"
                    icon="AllIcons.Nodes.PpLib"
                    factoryClass="io.github.einsnils.deplens.ui.DepLensToolWindowFactory"/>
    </extensions>
```

- [ ] **Step 6: Bauen und Tests ausführen**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 7: Beispielprojekt für die manuelle Prüfung anlegen**

`samples/demo/settings.gradle.kts`:

```kotlin
rootProject.name = "demo"
include("app", "core")
```

`samples/demo/core/build.gradle.kts`:

```kotlin
plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-core:2.15.2")
}
```

`samples/demo/app/build.gradle.kts`:

```kotlin
plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation("org.springframework.boot:spring-boot-starter-json:3.5.0")
    // ältere Version erzeugt einen Versionskonflikt
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.0")
    // nicht auflösbar, um die rote Markierung zu prüfen
    implementation("does.not:exist:1.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
}
```

Wrapper übernehmen:

```bash
cp -r gradle gradlew gradlew.bat samples/demo/
```

- [ ] **Step 8: Manuell prüfen**

Run: `./gradlew runIde` → in der Sandbox-IDE `samples/demo` öffnen, Gradle-Sync abwarten, Tool-Window „DepLens“ rechts öffnen.

Expected:
- Dropdown listet die Module (z. B. `demo`, `app`, `core`).
- Beim Wählen von `app` erscheint kurz der Ladeindikator, danach „Modul geladen“.
- Refresh-Button lädt neu ohne Fehler.
- `idea.log` der Sandbox (`build/idea-sandbox/.../log/idea.log`) enthält keine `DepLens`-Exceptions. Erscheint dort „Read access is allowed from inside read-action only“, die Aufrufe in `GradleDependencySource.modules()`/`load()` in `ReadAction.compute(...)` einwickeln und erneut prüfen.

- [ ] **Step 9: Commit und Push**

```bash
git add src samples
git commit -m "Add tool window with module selection

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push
```

---

### Task 6: Suche, Trefferliste und geteilte Baumansicht

**Files:**
- Create: `src/main/java/io/github/einsnils/deplens/ui/NodeText.java`
- Create: `src/main/java/io/github/einsnils/deplens/ui/DependencyTreeCellRenderer.java`
- Create: `src/main/java/io/github/einsnils/deplens/ui/SearchHitCellRenderer.java`
- Modify: `src/main/java/io/github/einsnils/deplens/ui/DepLensPanel.java` (`createContent()`, `onGraphLoaded()`, Toolbar)

**Interfaces:**
- Consumes: `DependencySearch.search`, `SearchHit`, `TreeModels.parentPath/children/empty`, `DependencyNode`-Getter, `DepLensPanel.graph`.
- Produces: `NodeText.append(SimpleColoredComponent target, DependencyNode node, boolean highlighted)`, `NodeText.tooltip(DependencyNode node)`, `DependencyTreeCellRenderer.setHighlighted(@Nullable DependencyNode node)`.

- [ ] **Step 1: `NodeText` schreiben**

```java
package io.github.einsnils.deplens.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import io.github.einsnils.deplens.model.DependencyNode;
import org.jetbrains.annotations.Nullable;

/** Einheitliche Darstellung eines Knotens in Liste und Bäumen. */
final class NodeText {
    private static final SimpleTextAttributes WARNING =
            new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.ORANGE);

    private NodeText() {
    }

    static void append(SimpleColoredComponent target, DependencyNode node, boolean highlighted) {
        if (node.isRoot()) {
            target.setIcon(AllIcons.Nodes.Module);
            target.append(node.name(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            target.append(" (" + node.configuration() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            return;
        }
        target.setIcon(node.kind() == DependencyNode.Kind.MODULE ? AllIcons.Nodes.Module : AllIcons.Nodes.PpLib);

        SimpleTextAttributes main = node.isUnresolved() ? SimpleTextAttributes.ERROR_ATTRIBUTES
                : node.isOmitted() ? SimpleTextAttributes.GRAYED_ATTRIBUTES
                : SimpleTextAttributes.REGULAR_ATTRIBUTES;
        if (highlighted) {
            main = main.derive(SimpleTextAttributes.STYLE_BOLD | SimpleTextAttributes.STYLE_SEARCH_MATCH,
                    null, null, null);
        }

        if (node.kind() == DependencyNode.Kind.ARTIFACT) {
            target.append(node.groupId() + ":" + node.artifactId(), main);
            target.append(" " + node.version(), highlighted ? main : SimpleTextAttributes.GRAYED_ATTRIBUTES);
        } else {
            target.append(node.name(), main);
        }
        if (node.hasConflict()) {
            target.append("  ⚠ " + node.conflict(), WARNING);
        }
        if (node.isUnresolved()) {
            target.append("  nicht auflösbar", SimpleTextAttributes.ERROR_ATTRIBUTES);
        }
        if (node.isOmitted()) {
            target.append("  (*)", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }

    static @Nullable String tooltip(DependencyNode node) {
        if (node.isUnresolved()) {
            return "Gradle konnte diese Dependency nicht auflösen";
        }
        if (node.hasConflict()) {
            return "Versionskonflikt: " + node.conflict();
        }
        if (node.isOmitted()) {
            return "(*) Unterbaum wurde bereits an anderer Stelle aufgelöst – siehe anderen Treffer";
        }
        return null;
    }
}
```

- [ ] **Step 2: Renderer schreiben**

`DependencyTreeCellRenderer.java`:

```java
package io.github.einsnils.deplens.ui;

import com.intellij.ui.ColoredTreeCellRenderer;
import io.github.einsnils.deplens.model.DependencyNode;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DependencyTreeCellRenderer extends ColoredTreeCellRenderer {
    private @Nullable DependencyNode highlighted;

    void setHighlighted(@Nullable DependencyNode node) {
        highlighted = node;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded,
                                      boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode treeNode
                && treeNode.getUserObject() instanceof DependencyNode node) {
            NodeText.append(this, node, node == highlighted);
            setToolTipText(NodeText.tooltip(node));
        }
    }
}
```

`SearchHitCellRenderer.java`:

```java
package io.github.einsnils.deplens.ui;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import io.github.einsnils.deplens.search.SearchHit;
import javax.swing.JList;
import org.jetbrains.annotations.NotNull;

final class SearchHitCellRenderer extends ColoredListCellRenderer<SearchHit> {

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends SearchHit> list, SearchHit hit, int index,
                                         boolean selected, boolean hasFocus) {
        NodeText.append(this, hit.node(), false);
        append("   " + hit.location(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        setToolTipText(NodeText.tooltip(hit.node()));
    }
}
```

- [ ] **Step 3: `DepLensPanel` erweitern**

Neue Imports:

```java
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.ui.tree.TreeUtil;
import io.github.einsnils.deplens.search.DependencySearch;
import io.github.einsnils.deplens.search.SearchHit;
import java.util.List;
import javax.swing.event.DocumentEvent;
```

Neue Felder (nach `moduleBox`):

```java
    private final SearchTextField searchField = new SearchTextField(false);
    private final Alarm searchAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    private final CollectionListModel<SearchHit> hitModel = new CollectionListModel<>();
    private final JBList<SearchHit> hitList = new JBList<>(hitModel);
    private final Tree parentsTree = new Tree(TreeModels.empty());
    private final Tree childrenTree = new Tree(TreeModels.empty());
    private final DependencyTreeCellRenderer parentsRenderer = new DependencyTreeCellRenderer();
    private final DependencyTreeCellRenderer childrenRenderer = new DependencyTreeCellRenderer();
```

Im Konstruktor nach `toolbar.add(moduleBox, BorderLayout.WEST);` einfügen:

```java
        toolbar.add(searchField, BorderLayout.CENTER);
        searchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                searchAlarm.cancelAllRequests();
                searchAlarm.addRequest(DepLensPanel.this::runSearch, 300);
            }
        });

        hitList.setCellRenderer(new SearchHitCellRenderer());
        hitList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showHit(hitList.getSelectedValue());
            }
        });
        parentsTree.setCellRenderer(parentsRenderer);
        childrenTree.setCellRenderer(childrenRenderer);
        parentsTree.setRootVisible(true);
        childrenTree.setRootVisible(true);
```

`createContent()` ersetzen:

```java
    private JComponent createContent() {
        OnePixelSplitter trees = new OnePixelSplitter(false, 0.5f);
        trees.setFirstComponent(titled("Parents (bis zum Modul)", new JBScrollPane(parentsTree)));
        trees.setSecondComponent(titled("Children", new JBScrollPane(childrenTree)));

        OnePixelSplitter main = new OnePixelSplitter(true, 0.35f);
        main.setFirstComponent(titled("Treffer", new JBScrollPane(hitList)));
        main.setSecondComponent(trees);
        return main;
    }

    private static JComponent titled(String title, JComponent body) {
        JPanel panel = new JPanel(new BorderLayout());
        JBLabel label = new JBLabel(title);
        label.setBorder(JBUI.Borders.empty(2, 4));
        panel.add(label, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }
```

`onGraphLoaded()` ersetzen und neue Methoden ergänzen:

```java
    private void onGraphLoaded() {
        showCard(CARD_CONTENT);
        runSearch();
    }

    private void runSearch() {
        String query = searchField.getText();
        List<SearchHit> hits = DependencySearch.search(graph, query);
        hitModel.replaceAll(hits);
        hitList.getEmptyText().setText(query.isBlank()
                ? "Suchbegriff eingeben, z. B. jackson-databind"
                : "Keine Treffer für ‚" + query.strip() + "‘");
        if (hits.isEmpty()) {
            showHit(null);
        } else {
            hitList.setSelectedIndex(0);
            // Listener feuert nicht, wenn Index 0 schon ausgewählt war
            showHit(hitList.getSelectedValue());
        }
    }

    private void showHit(SearchHit hit) {
        if (hit == null) {
            parentsTree.setModel(TreeModels.empty());
            childrenTree.setModel(TreeModels.empty());
            return;
        }
        parentsRenderer.setHighlighted(hit.node());
        childrenRenderer.setHighlighted(hit.node());
        parentsTree.setModel(TreeModels.parentPath(hit));
        TreeUtil.expandAll(parentsTree);
        childrenTree.setModel(TreeModels.children(hit.node()));
        TreeUtil.expand(childrenTree, 2);
    }
```

- [ ] **Step 4: Bauen und Tests ausführen**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, alle Tests grün. Falls `SimpleTextAttributes.STYLE_SEARCH_MATCH` nicht existiert, statt `derive(...)` `new SimpleTextAttributes(JBColor.YELLOW, null, null, SimpleTextAttributes.STYLE_BOLD | SimpleTextAttributes.STYLE_OPAQUE)` verwenden.

- [ ] **Step 5: Manuell prüfen**

Run: `./gradlew runIde` → `samples/demo` öffnen, Modul `app` wählen.

Expected:
- Leere Suche: Liste zeigt „Suchbegriff eingeben, z. B. jackson-databind“, Bäume leer.
- `jackson-databind` eingeben: Treffer in mehreren Configurations (z. B. `compileClasspath`, `runtimeClasspath`, `testRuntimeClasspath`), jeweils mit „direkt“ bzw. „via …“; Konflikt-Treffer mit orangem `⚠ conflict with …` und Tooltip.
- Treffer anklicken: links Kette `app (runtimeClasspath)` → … → Treffer (farbig hervorgehoben), rechts Treffer mit `jackson-core`, `jackson-annotations`.
- `jackson-core` eingeben: auch `(*)`-Vorkommen erscheinen grau; rechts ohne Kinder, Tooltip erklärt `(*)`.
- `exist` eingeben: `does.not:exist` rot mit „nicht auflösbar“.
- `:core` eingeben: Projekt-Abhängigkeit wird gefunden.
- `xyz` eingeben: „Keine Treffer für ‚xyz‘“.
- Tool-Window nach links ziehen funktioniert.

- [ ] **Step 6: Commit und Push**

```bash
git add src
git commit -m "Add search, hit list and parent/child trees

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push
```

---

### Task 7: README und Abschlussprüfung

**Files:**
- Create: `README.md`

- [ ] **Step 1: `README.md` schreiben**

```markdown
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
```

- [ ] **Step 2: Fehlerfall manuell prüfen**

Run: `./gradlew runIde` → in `samples/demo/app/build.gradle.kts` einen Syntaxfehler einbauen (z. B. `implementation(` ohne Klammer zu), Gradle-Sync ausführen, DepLens-Refresh klicken.

Expected: Kein Absturz; entweder Meldung „Laden fehlgeschlagen: …“ mit „Erneut versuchen“ oder „Kein Gradle-Projekt gefunden – bitte Gradle-Sync ausführen.“. Syntaxfehler danach wieder entfernen.

- [ ] **Step 3: Plugin-ZIP bauen**

Run: `./gradlew buildPlugin`
Expected: `build/distributions/DepLens-0.1.0.zip` existiert.

- [ ] **Step 4: Commit und Push**

```bash
git add README.md
git commit -m "Add README

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push
```
