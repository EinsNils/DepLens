package io.github.einsnils.deplens.ui;

import io.github.einsnils.deplens.catalog.CatalogCategory;
import io.github.einsnils.deplens.catalog.CatalogEntry;
import io.github.einsnils.deplens.model.DependencyNode;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/** Baut die Swing-Modelle für die Kategorie-Liste und den Abhängigkeitspfad. */
public final class TreeModels {

    /** Pfad-Baum plus die Baumknoten der gewählten Dependency (zum Aufklappen und Hervorheben). */
    public record PathTree(DefaultTreeModel model, List<DefaultMutableTreeNode> hitNodes) {
    }

    private TreeModels() {
    }

    /** Unsichtbare Wurzel → Kategorien → Einträge. */
    public static DefaultTreeModel catalog(List<CatalogCategory> categories) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        for (CatalogCategory category : categories) {
            DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(category);
            for (CatalogEntry entry : category.entries()) {
                categoryNode.add(new DefaultMutableTreeNode(entry, false));
            }
            root.add(categoryNode);
        }
        return new DefaultTreeModel(root);
    }

    /**
     * Alle Wege Modul → … → Dependency, zusammengeführt (gemeinsame Abschnitte nur einmal), mit den Children
     * jedes Vorkommens. Unter der unsichtbaren Wurzel steht eine Modul-Wurzel pro Configuration.
     */
    public static PathTree allPaths(CatalogEntry entry) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        Map<DependencyNode, DefaultMutableTreeNode> treeNodes = new IdentityHashMap<>();
        List<DefaultMutableTreeNode> hitNodes = new ArrayList<>();
        for (DependencyNode occurrence : entry.occurrences()) {
            DefaultMutableTreeNode parent = root;
            for (DependencyNode node : occurrence.pathFromRoot()) {
                parent = attach(parent, node, treeNodes);
            }
            hitNodes.add(parent);
            for (DependencyNode child : occurrence.children()) {
                attachSubtree(parent, child, treeNodes);
            }
        }
        return new PathTree(new DefaultTreeModel(root), List.copyOf(hitNodes));
    }

    public static DefaultTreeModel empty() {
        return new DefaultTreeModel(null);
    }

    private static DefaultMutableTreeNode attach(DefaultMutableTreeNode parent, DependencyNode node,
                                                 Map<DependencyNode, DefaultMutableTreeNode> treeNodes) {
        return treeNodes.computeIfAbsent(node, key -> {
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(key);
            parent.add(treeNode);
            return treeNode;
        });
    }

    private static void attachSubtree(DefaultMutableTreeNode parent, DependencyNode node,
                                      Map<DependencyNode, DefaultMutableTreeNode> treeNodes) {
        DefaultMutableTreeNode treeNode = attach(parent, node, treeNodes);
        for (DependencyNode child : node.children()) {
            attachSubtree(treeNode, child, treeNodes);
        }
    }
}
