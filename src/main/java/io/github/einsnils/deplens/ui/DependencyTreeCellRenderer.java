package io.github.einsnils.deplens.ui;

import com.intellij.ui.ColoredTreeCellRenderer;
import io.github.einsnils.deplens.model.DependencyNode;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DependencyTreeCellRenderer extends ColoredTreeCellRenderer {
    private @Nullable DependencyNode highlighted;

    void setHighlighted(@Nullable DependencyNode node) {
        highlighted = node;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded,
                                      boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode treeNode
                && treeNode.getUserObject() instanceof DependencyNode node) {
            NodeText.append(this, node, node == highlighted);
            setToolTipText(NodeText.tooltip(node));
        }
    }
}
