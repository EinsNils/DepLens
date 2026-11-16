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
