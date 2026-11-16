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
