package dev.nz3x.deplens.catalog;

import static dev.nz3x.deplens.model.TestGraphs.JACKSON;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nz3x.deplens.model.DependencyGraph;
import dev.nz3x.deplens.model.DependencyNode;
import dev.nz3x.deplens.model.TestGraphs;
import java.util.List;
import org.junit.jupiter.api.Test;

class DependencyCatalogTest {
    private final TestGraphs g = new TestGraphs();

    @Test
    void blankQueryGroupsEveryDependencyOnceByVendor() {
        List<CatalogCategory> categories = DependencyCatalog.build(g.graph, "  ");

        assertThat(categories).extracting(CatalogCategory::name)
                .containsExactly("Jackson", "Spring", VendorCategory.PROJECT_MODULES);
        assertThat(categories.get(0).entries()).extracting(CatalogEntry::coordinates).containsExactly(
                JACKSON + ":jackson-core:2.15.2", JACKSON + ":jackson-databind:2.15.2");
        assertThat(categories.get(1).entries()).extracting(CatalogEntry::coordinates).containsExactly(
                "org.springframework.boot:spring-boot-starter-json:3.5.0",
                "org.springframework.boot:spring-boot-starter-web:3.5.0");
        assertThat(categories.get(2).entries()).extracting(CatalogEntry::coordinates).containsExactly(":core");
    }

    @Test
    void entryCollectsAllOccurrencesInGraphOrder() {
        CatalogEntry databind = DependencyCatalog.build(g.graph, null).get(0).entries().get(1);
        CatalogEntry core = DependencyCatalog.build(g.graph, null).get(0).entries().get(0);

        assertThat(databind.occurrences()).containsExactly(g.databindRuntime, g.databindTest);
        assertThat(core.occurrences()).containsExactly(g.jacksonCore, g.omittedCore);
        assertThat(databind.representative()).isSameAs(g.databindRuntime);
    }

    @Test
    void entryReportsConflictAndUnresolvedOfAnyOccurrence() {
        g.databindTest.markUnresolved();
        CatalogEntry databind = DependencyCatalog.build(g.graph, "databind").getFirst().entries().getFirst();
        CatalogEntry web = DependencyCatalog.build(g.graph, "starter-web").getFirst().entries().getFirst();

        assertThat(databind.conflict()).isEqualTo("conflict with 2.13.0");
        assertThat(databind.isUnresolved()).isTrue();
        assertThat(web.conflict()).isNull();
        assertThat(web.isUnresolved()).isFalse();
    }

    @Test
    void queryFiltersEntriesAndDropsEmptyCategories() {
        List<CatalogCategory> categories = DependencyCatalog.build(g.graph, "DATABIND");

        assertThat(categories).extracting(CatalogCategory::name).containsExactly("Jackson");
        assertThat(categories.getFirst().entries()).extracting(CatalogEntry::coordinates)
                .containsExactly(JACKSON + ":jackson-databind:2.15.2");
    }

    @Test
    void differentVersionsAreSeparateEntries() {
        DependencyNode root = DependencyNode.module(":app", "runtimeClasspath", null);
        DependencyNode testRoot = DependencyNode.module(":app", "testRuntimeClasspath", null);
        DependencyNode.artifact(JACKSON, "jackson-core", "2.19.0", "runtimeClasspath", root);
        DependencyNode.artifact(JACKSON, "jackson-core", "2.15.2", "testRuntimeClasspath", testRoot);
        DependencyGraph graph = new DependencyGraph(":app", List.of(root, testRoot));

        assertThat(DependencyCatalog.build(graph, "").getFirst().entries()).extracting(CatalogEntry::coordinates)
                .containsExactly(JACKSON + ":jackson-core:2.15.2", JACKSON + ":jackson-core:2.19.0");
    }

    @Test
    void noMatchGivesNoCategories() {
        assertThat(DependencyCatalog.build(g.graph, "guava")).isEmpty();
        assertThat(DependencyCatalog.build(DependencyGraph.empty(":x"), "")).isEmpty();
    }

    @Test
    void entryCountSumsAllCategories() {
        assertThat(DependencyCatalog.entryCount(DependencyCatalog.build(g.graph, ""))).isEqualTo(5);
    }

    @Test
    void filterKeepsMatchingEntriesAndDropsEmptyCategories() {
        List<CatalogCategory> filtered = DependencyCatalog.filter(DependencyCatalog.build(g.graph, ""),
                entry -> entry.coordinates().contains("databind"));

        assertThat(filtered).extracting(CatalogCategory::name).containsExactly("Jackson");
        assertThat(filtered.getFirst().entries()).extracting(CatalogEntry::coordinates)
                .containsExactly(JACKSON + ":jackson-databind:2.15.2");
    }
}
