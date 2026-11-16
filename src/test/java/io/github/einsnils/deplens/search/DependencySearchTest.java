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
