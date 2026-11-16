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
