package io.github.einsnils.deplens.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.einsnils.deplens.gradle.Fakes.FakeArtifact;
import io.github.einsnils.deplens.gradle.Fakes.FakeDep;
import io.github.einsnils.deplens.gradle.Fakes.FakeModule;
import io.github.einsnils.deplens.gradle.Fakes.FakeOmitted;
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

    private FakeDep jackson(FakeScope scope, FakeDep parent) {
        return new FakeDep(new FakeArtifact(JACKSON, "jackson-core", "2.15.2"), scope, parent);
    }

    @Test
    void childlessDuplicateOfExpandedNodeInSameConfigurationIsOmitted() {
        FakeDep expanded = jackson(runtime, web);
        FakeDep child = new FakeDep(new FakeArtifact("a", "b", "1"), runtime, expanded);
        FakeDep repeated = jackson(runtime, root);

        DependencyGraph graph = mapper.map(":app", List.of(root, web, expanded, child, repeated));

        List<DependencyNode> core = graph.allNodes().stream()
                .filter(n -> n.artifactId().equals("jackson-core")).toList();
        assertThat(core).hasSize(2);
        assertThat(core.stream().filter(n -> !n.children().isEmpty()).allMatch(n -> !n.isOmitted())).isTrue();
        assertThat(core.stream().filter(n -> n.children().isEmpty()).allMatch(DependencyNode::isOmitted)).isTrue();
    }

    @Test
    void duplicateInDifferentConfigurationIsNotOmitted() {
        FakeScope test = new FakeScope("testRuntimeClasspath");
        FakeDep testRoot = new FakeDep(new FakeModule(":app"), test, null);
        FakeDep expanded = jackson(runtime, root);
        FakeDep child = new FakeDep(new FakeArtifact("a", "b", "1"), runtime, expanded);
        FakeDep otherConfig = jackson(test, testRoot);

        DependencyGraph graph = mapper.map(":app", List.of(root, expanded, child, testRoot, otherConfig));

        assertThat(graph.allNodes()).noneMatch(DependencyNode::isOmitted);
    }

    @Test
    void childlessDuplicatesAreNotOmitted() {
        DependencyGraph graph = mapper.map(":app", List.of(root, jackson(runtime, root), jackson(runtime, web)));

        assertThat(graph.allNodes()).noneMatch(DependencyNode::isOmitted);
    }
}
