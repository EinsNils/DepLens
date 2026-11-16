package dev.nz3x.deplens.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import dev.nz3x.deplens.catalog.CatalogEntry;
import dev.nz3x.deplens.model.DependencyNode;
import dev.nz3x.deplens.security.Finding;
import dev.nz3x.deplens.security.MavenVersion;
import dev.nz3x.deplens.security.SecurityLinks;
import dev.nz3x.deplens.security.SecurityReport;
import dev.nz3x.deplens.security.UpgradeAdvisor;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.Nullable;

/** Inhalt des Tabs „Sicherheit“ für eine gewählte Dependency. */
final class SecurityPanel extends JPanel {
    private final JBLabel header = new JBLabel();
    private final JPanel upgradeRow = row();
    private final JBLabel hint = new JBLabel();
    private final JPanel linkRow = row();
    private final FindingsModel findings = new FindingsModel();
    private final JBTable table = new JBTable(findings);

    SecurityPanel() {
        super(new BorderLayout());
        header.setFont(JBUI.Fonts.label().asBold());
        hint.setForeground(UIUtil.getContextHelpForeground());
        JPanel top = new JPanel(new VerticalLayout(JBUI.scale(6)));
        top.setBorder(JBUI.Borders.empty(8, 10));
        top.add(header);
        top.add(upgradeRow);
        top.add(hint);
        top.add(linkRow);
        add(top, BorderLayout.NORTH);

        table.setShowGrid(false);
        table.getEmptyText().setText("Keine Lücken");
        table.getColumnModel().getColumn(0).setPreferredWidth(JBUI.scale(90));
        table.getColumnModel().getColumn(1).setPreferredWidth(JBUI.scale(150));
        table.getColumnModel().getColumn(2).setPreferredWidth(JBUI.scale(400));
        table.getColumnModel().getColumn(3).setPreferredWidth(JBUI.scale(90));
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && row >= 0) {
                    BrowserUtil.browse(findings.at(table.convertRowIndexToModel(row)).vulnerability().url());
                }
            }
        });
        add(new JBScrollPane(table), BorderLayout.CENTER);
    }

    void show(CatalogEntry entry, @Nullable SecurityReport report) {
        DependencyNode node = entry.representative();
        String name = node.name() + (node.kind() == DependencyNode.Kind.ARTIFACT ? " " + node.version() : "");
        upgradeRow.removeAll();
        linkRow.removeAll();
        hint.setText("");
        hint.setVisible(false);
        upgradeRow.setVisible(false);
        findings.set(report == null ? List.of() : report.findings());

        if (report == null) {
            header.setText(name + " – wird geprüft …");
        } else {
            switch (report.status()) {
                case SICHER -> header.setText(name + " – keine bekannten Lücken");
                case NICHT_PRUEFBAR -> header.setText(name + " – nicht prüfbar: " + report.problem());
                case FEHLER -> header.setText(name + " – Prüfung fehlgeschlagen: " + report.problem());
                case VERWUNDBAR -> {
                    int count = report.findings().size();
                    header.setText(name + " – ⛔ " + count + (count == 1 ? " Lücke" : " Lücken"));
                    fillUpgrades(node, report.upgrade());
                    showTransitiveHint(entry);
                }
            }
        }
        if (node.kind() == DependencyNode.Kind.ARTIFACT) {
            linkRow.add(link("↗ auf mvnrepository öffnen",
                    SecurityLinks.mvnRepository(node.groupId(), node.artifactId(), node.version())));
            linkRow.add(link("↗ auf osv.dev öffnen", SecurityLinks.osvPackage(node.groupId(), node.artifactId())));
        }
        revalidate();
        repaint();
    }

    private void fillUpgrades(DependencyNode node, UpgradeAdvisor.Upgrade upgrade) {
        upgradeRow.setVisible(true);
        upgradeRow.add(new JBLabel("Upgrade:"));
        if (upgrade.isEmpty()) {
            upgradeRow.add(new JBLabel("Keine Version ohne bekannte Lücken verfügbar"));
            return;
        }
        if (upgrade.sameMajor() != null) {
            upgradeRow.add(versionLink(node, upgrade.sameMajor(), "gleiche Major"));
        }
        if (upgrade.latest() != null && !upgrade.latest().equals(upgrade.sameMajor())) {
            upgradeRow.add(versionLink(node, upgrade.latest(), "neueste sichere"));
        }
    }

    /** Hinweis, wenn kein Vorkommen direkt am Modul hängt. */
    private void showTransitiveHint(CatalogEntry entry) {
        boolean direct = entry.occurrences().stream()
                .anyMatch(occurrence -> occurrence.parent() != null && occurrence.parent().isRoot());
        DependencyNode parent = entry.representative().parent();
        if (!direct && parent != null) {
            hint.setText("Wird über " + parent.name() + " eingezogen – Upgrade dort oder per Gradle-Constraint.");
            hint.setVisible(true);
        }
    }

    private static ActionLink versionLink(DependencyNode node, MavenVersion version, String label) {
        return link(version + " (" + label + ")",
                SecurityLinks.mvnRepository(node.groupId(), node.artifactId(), version.toString()));
    }

    private static ActionLink link(String text, String url) {
        return new ActionLink(text, (ActionListener) e -> BrowserUtil.browse(url));
    }

    private static JPanel row() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
    }

    private static final class FindingsModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Schweregrad", "ID", "Beschreibung", "Behoben in"};
        private List<Finding> rows = List.of();

        void set(List<Finding> findings) {
            rows = findings;
            fireTableDataChanged();
        }

        Finding at(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            Finding finding = rows.get(row);
            return switch (column) {
                case 0 -> finding.vulnerability().severity().name();
                case 1 -> finding.vulnerability().displayId();
                case 2 -> finding.vulnerability().summary();
                default -> finding.fixedIn() == null ? "–" : finding.fixedIn().toString();
            };
        }
    }
}
