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
    void pathRunsFromModuleToHitAndHitKeepsItsChildren() {
        DefaultTreeModel model = TreeModels.pathWithChildren(new SearchHit(g.databindRuntime));

        assertThat(userObjects(model)).containsExactly(
                g.runtimeRoot, g.web, g.json, g.databindRuntime, g.jacksonCore);
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        assertThat(root.getChildCount()).isEqualTo(1);
    }

    @Test
    void siblingsOfThePathAreLeftOut() {
        DefaultTreeModel model = TreeModels.pathWithChildren(new SearchHit(g.web));

        assertThat(userObjects(model)).containsExactly(
                g.runtimeRoot, g.web, g.json, g.databindRuntime, g.jacksonCore);
        assertThat(userObjects(model)).doesNotContain(g.coreModule);
    }

    @Test
    void omittedHitEndsThePath() {
        DefaultTreeModel model = TreeModels.pathWithChildren(new SearchHit(g.omittedCore));

        assertThat(userObjects(model)).containsExactly(g.runtimeRoot, g.coreModule, g.omittedCore);
    }

    @Test
    void hitTreeNodeIsTheDeepestPathNode() {
        SearchHit hit = new SearchHit(g.runtimeRoot.children().getFirst());
        DefaultMutableTreeNode hitNode = TreeModels.hitNode(TreeModels.pathWithChildren(hit), hit);

        assertThat(hitNode.getUserObject()).isSameAs(g.web);
        assertThat(hitNode.getChildCount()).isEqualTo(1);

        SearchHit json = new SearchHit(g.json);
        assertThat(TreeModels.hitNode(TreeModels.pathWithChildren(json), json).getUserObject()).isSameAs(g.json);
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
