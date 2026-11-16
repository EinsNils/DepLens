package io.github.einsnils.deplens.ui;

import com.intellij.ui.ColoredTreeCellRenderer;
import io.github.einsnils.deplens.model.DependencyNode;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import org.jetbrains.annotations.NotNull;

/** Renderer für den Abhängigkeitspfad; die Vorkommen der gewählten Dependency werden hervorgehoben. */
final class DependencyTreeCellRenderer extends ColoredTreeCellRenderer {
    private final Set<DependencyNode> highlighted = Collections.newSetFromMap(new IdentityHashMap<>());

    void setHighlighted(Collection<DependencyNode> nodes) {
        highlighted.clear();
        highlighted.addAll(nodes);
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded,
                                      boolean leaf, int row, boolean hasFocus) {
        setToolTipText(null);
        if (value instanceof DefaultMutableTreeNode treeNode
                && treeNode.getUserObject() instanceof DependencyNode node) {
            NodeText.append(this, node, highlighted.contains(node));
            setToolTipText(NodeText.tooltip(node));
        }
    }
}
