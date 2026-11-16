package io.github.einsnils.deplens.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import io.github.einsnils.deplens.catalog.CatalogCategory;
import io.github.einsnils.deplens.catalog.CatalogEntry;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import org.jetbrains.annotations.NotNull;

/** Renderer für die Kategorie-Liste: Hersteller mit Anzahl, darunter die Dependencies. */
final class CatalogTreeCellRenderer extends ColoredTreeCellRenderer {

    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded,
                                      boolean leaf, int row, boolean hasFocus) {
        setToolTipText(null);
        if (!(value instanceof DefaultMutableTreeNode treeNode)) {
            return;
        }
        if (treeNode.getUserObject() instanceof CatalogCategory category) {
            setIcon(AllIcons.Nodes.Folder);
            append(category.name(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            append("  " + category.entries().size(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        } else if (treeNode.getUserObject() instanceof CatalogEntry entry) {
            NodeText.appendEntry(this, entry);
            setToolTipText(NodeText.tooltip(entry));
        }
    }
}
