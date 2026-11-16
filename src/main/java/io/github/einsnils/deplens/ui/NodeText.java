package io.github.einsnils.deplens.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import io.github.einsnils.deplens.model.DependencyNode;
import org.jetbrains.annotations.Nullable;

/** Einheitliche Darstellung eines Knotens in Liste und Bäumen. */
final class NodeText {
    private static final SimpleTextAttributes WARNING =
            new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.ORANGE);

    private NodeText() {
    }

    static void append(SimpleColoredComponent target, DependencyNode node, boolean highlighted) {
        if (node.isRoot()) {
            target.setIcon(AllIcons.Nodes.Module);
            target.append(node.name(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            target.append(" (" + node.configuration() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            return;
        }
        target.setIcon(node.kind() == DependencyNode.Kind.MODULE ? AllIcons.Nodes.Module : AllIcons.Nodes.PpLib);

        SimpleTextAttributes main = node.isUnresolved() ? SimpleTextAttributes.ERROR_ATTRIBUTES
                : node.isOmitted() ? SimpleTextAttributes.GRAYED_ATTRIBUTES
                : SimpleTextAttributes.REGULAR_ATTRIBUTES;
        if (highlighted) {
            main = main.derive(SimpleTextAttributes.STYLE_BOLD | SimpleTextAttributes.STYLE_SEARCH_MATCH,
                    null, null, null);
        }

        if (node.kind() == DependencyNode.Kind.ARTIFACT) {
            target.append(node.groupId() + ":" + node.artifactId(), main);
            target.append(" " + node.version(), highlighted ? main : SimpleTextAttributes.GRAYED_ATTRIBUTES);
        } else {
            target.append(node.name(), main);
        }
        if (node.hasConflict()) {
            target.append("  ⚠ " + node.conflict(), WARNING);
        }
        if (node.isUnresolved()) {
            target.append("  nicht auflösbar", SimpleTextAttributes.ERROR_ATTRIBUTES);
        }
        if (node.isOmitted()) {
            target.append("  (*)", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }

    static @Nullable String tooltip(DependencyNode node) {
        if (node.isUnresolved()) {
            return "Gradle konnte diese Dependency nicht auflösen";
        }
        if (node.hasConflict()) {
            return "Versionskonflikt: " + node.conflict();
        }
        if (node.isOmitted()) {
            return "(*) Unterbaum wurde bereits an anderer Stelle aufgelöst – siehe anderen Treffer";
        }
        return null;
    }
}
