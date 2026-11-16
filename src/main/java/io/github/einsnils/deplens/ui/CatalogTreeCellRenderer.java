package io.github.einsnils.deplens.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import io.github.einsnils.deplens.catalog.CatalogCategory;
import io.github.einsnils.deplens.catalog.CatalogEntry;
import io.github.einsnils.deplens.security.SecurityReport;
import java.util.function.Function;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import org.jetbrains.annotations.NotNull;

/** Renderer für die Kategorie-Liste: Hersteller mit Anzahl, darunter die Dependencies mit Sicherheitsstatus. */
final class CatalogTreeCellRenderer extends ColoredTreeCellRenderer {
    private final Function<CatalogEntry, SecurityReport> reports;

    /** {@code reports} liefert den Bericht eines Eintrags oder {@code null}, solange keiner vorliegt. */
    CatalogTreeCellRenderer(Function<CatalogEntry, SecurityReport> reports) {
        this.reports = reports;
    }

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
            if (category.entries().stream().anyMatch(this::isVulnerable)) {
                NodeText.appendVulnerableMarker(this);
            }
        } else if (treeNode.getUserObject() instanceof CatalogEntry entry) {
            SecurityReport report = reports.apply(entry);
            NodeText.appendEntry(this, entry);
            NodeText.appendSecurity(this, report);
            String securityTooltip = NodeText.securityTooltip(report);
            setToolTipText(securityTooltip != null ? securityTooltip : NodeText.tooltip(entry));
        }
    }

    private boolean isVulnerable(CatalogEntry entry) {
        SecurityReport report = reports.apply(entry);
        return report != null && report.status() == SecurityReport.Status.VERWUNDBAR;
    }
}
