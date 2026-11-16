package dev.nz3x.deplens.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import dev.nz3x.deplens.catalog.CatalogEntry;
import dev.nz3x.deplens.model.DependencyNode;
import dev.nz3x.deplens.security.SecurityReport;
import java.awt.Color;
import org.jetbrains.annotations.Nullable;

/** Einheitliche Darstellung von Dependencies in Liste und Baum. */
final class NodeText {
    private static final SimpleTextAttributes WARNING =
            new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.ORANGE);
    private static final SimpleTextAttributes VULNERABLE =
            new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.RED);
    private static final SimpleTextAttributes SAFE = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN,
            new JBColor(new Color(0x2E7D32), new Color(0x81C784)));

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

    /** Kompakte Zeile für die Kategorie-Liste: Artefakt, Version, Gruppe und Warnungen aller Vorkommen. */
    static void appendEntry(SimpleColoredComponent target, CatalogEntry entry) {
        DependencyNode node = entry.representative();
        target.setIcon(node.kind() == DependencyNode.Kind.MODULE ? AllIcons.Nodes.Module : AllIcons.Nodes.PpLib);
        target.append(node.name(), entry.isUnresolved()
                ? SimpleTextAttributes.ERROR_ATTRIBUTES
                : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        if (node.kind() == DependencyNode.Kind.ARTIFACT) {
            target.append("  " + node.version(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            target.append("   " + node.groupId(), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
        }
        if (entry.conflict() != null) {
            target.append("  ⚠", WARNING);
        }
        if (entry.isUnresolved()) {
            target.append("  nicht auflösbar", SimpleTextAttributes.ERROR_ATTRIBUTES);
        }
    }

    /** {@code ⛔ n} bei Lücken, {@code ✔} wenn sicher, {@code ?} wenn nicht prüfbar; nichts, solange geprüft wird. */
    static void appendSecurity(SimpleColoredComponent target, @Nullable SecurityReport report) {
        if (report == null) {
            return;
        }
        switch (report.status()) {
            case VERWUNDBAR -> target.append("  ⛔ " + report.findings().size(), VULNERABLE);
            case SICHER -> target.append("  ✔", SAFE);
            case NICHT_PRUEFBAR, FEHLER -> target.append("  ?", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }

    static void appendVulnerableMarker(SimpleColoredComponent target) {
        target.append("  ⛔", VULNERABLE);
    }

    static @Nullable String securityTooltip(@Nullable SecurityReport report) {
        if (report == null) {
            return null;
        }
        return switch (report.status()) {
            case VERWUNDBAR -> report.findings().size() == 1
                    ? "1 bekannte Sicherheitslücke"
                    : report.findings().size() + " bekannte Sicherheitslücken";
            case SICHER -> "Keine bekannten Sicherheitslücken";
            case NICHT_PRUEFBAR -> "Nicht prüfbar: " + report.problem();
            case FEHLER -> "Prüfung fehlgeschlagen: " + report.problem();
        };
    }

    static @Nullable String tooltip(CatalogEntry entry) {
        if (entry.isUnresolved()) {
            return "Gradle konnte diese Dependency nicht auflösen";
        }
        if (entry.conflict() != null) {
            return "Versionskonflikt: " + entry.conflict();
        }
        return null;
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
