package io.github.einsnils.deplens.ui;

import io.github.einsnils.deplens.model.DependencyNode;
import io.github.einsnils.deplens.search.SearchHit;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/** Baut das Swing-Modell für den Abhängigkeitspfad eines Treffers. */
public final class TreeModels {
    private TreeModels() {
    }

    /** Kette Modul → … → Treffer; unter dem Treffer hängt sein kompletter Unterbaum. */
    public static DefaultTreeModel pathWithChildren(SearchHit hit) {
        List<DependencyNode> path = hit.path();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(path.getFirst());
        DefaultMutableTreeNode current = root;
        for (DependencyNode node : path.subList(1, path.size())) {
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
            current.add(treeNode);
            current = treeNode;
        }
        for (DependencyNode child : hit.node().children()) {
            current.add(copy(child));
        }
        return new DefaultTreeModel(root);
    }

    /** Der Knoten des Treffers in einem mit {@link #pathWithChildren} gebauten Modell. */
    public static DefaultMutableTreeNode hitNode(DefaultTreeModel model, SearchHit hit) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) model.getRoot();
        for (int depth = 1; depth < hit.path().size(); depth++) {
            node = (DefaultMutableTreeNode) node.getChildAt(0);
        }
        return node;
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
