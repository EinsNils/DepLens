package io.github.einsnils.deplens.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.einsnils.deplens.catalog.CatalogCategory;
import io.github.einsnils.deplens.catalog.CatalogEntry;
import io.github.einsnils.deplens.catalog.DependencyCatalog;
import io.github.einsnils.deplens.model.TestGraphs;
import java.util.Collections;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;

class TreeModelsTest {
    private final TestGraphs g = new TestGraphs();

    @Test
    void catalogHasCategoriesWithTheirEntriesUnderAHiddenRoot() {
        List<CatalogCategory> categories = DependencyCatalog.build(g.graph, "");

        DefaultTreeModel model = TreeModels.catalog(categories);

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        assertThat(root.getChildCount()).isEqualTo(3);
        DefaultMutableTreeNode jackson = (DefaultMutableTreeNode) root.getChildAt(0);
        assertThat(jackson.getUserObject()).isEqualTo(categories.getFirst());
        assertThat(((DefaultMutableTreeNode) jackson.getChildAt(1)).getUserObject())
                .isEqualTo(categories.getFirst().entries().get(1));
    }

    @Test
    void allPathsShowsEveryOccurrencePerConfigurationWithItsChildren() {
        TreeModels.PathTree tree = TreeModels.allPaths(entry("jackson-databind"));

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.model().getRoot();
        assertThat(root.getUserObject()).isNull();
        assertThat(userObjects(tree.model())).containsExactly(
                g.runtimeRoot, g.web, g.json, g.databindRuntime, g.jacksonCore,
                g.testRoot, g.databindTest);
        assertThat(tree.hitNodes()).extracting(DefaultMutableTreeNode::getUserObject)
                .containsExactly(g.databindRuntime, g.databindTest);
    }

    @Test
    void sharedPathPrefixesAreMerged() {
        TreeModels.PathTree tree = TreeModels.allPaths(entry("jackson-core"));

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.model().getRoot();
        assertThat(root.getChildCount()).isEqualTo(1);
        assertThat(userObjects(tree.model())).containsExactly(
                g.runtimeRoot, g.web, g.json, g.databindRuntime, g.jacksonCore, g.coreModule, g.omittedCore);
        assertThat(tree.hitNodes()).extracting(DefaultMutableTreeNode::getUserObject)
                .containsExactly(g.jacksonCore, g.omittedCore);
    }

    @Test
    void siblingsOfThePathsAreLeftOut() {
        TreeModels.PathTree tree = TreeModels.allPaths(entry("spring-boot-starter-json"));

        assertThat(userObjects(tree.model())).containsExactly(
                g.runtimeRoot, g.web, g.json, g.databindRuntime, g.jacksonCore);
    }

    @Test
    void emptyModelHasNoRoot() {
        assertThat(TreeModels.empty().getRoot()).isNull();
    }

    private CatalogEntry entry(String query) {
        return DependencyCatalog.build(g.graph, query).getFirst().entries().getFirst();
    }

    /** Preorder ohne die unsichtbare Wurzel. */
    private static List<Object> userObjects(DefaultTreeModel model) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        return Collections.list(root.preorderEnumeration()).stream()
                .skip(1)
                .map(node -> ((DefaultMutableTreeNode) node).getUserObject())
                .toList();
    }
}
