package dev.nz3x.deplens.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerProject;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import dev.nz3x.deplens.catalog.CatalogCategory;
import dev.nz3x.deplens.catalog.CatalogEntry;
import dev.nz3x.deplens.catalog.DependencyCatalog;
import dev.nz3x.deplens.gradle.GradleDependencySource;
import dev.nz3x.deplens.model.DependencyGraph;
import dev.nz3x.deplens.model.DependencyNode;
import dev.nz3x.deplens.security.Artifact;
import dev.nz3x.deplens.security.CheckableVersion;
import dev.nz3x.deplens.security.SecurityChecker;
import dev.nz3x.deplens.security.SecurityReport;
import dev.nz3x.deplens.security.SecurityService;
import dev.nz3x.deplens.settings.DepLensSettings;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.Nullable;

/** Inhalt des DepLens-Tool-Windows. */
public final class DepLensPanel extends JPanel implements Disposable {
    private static final Logger LOG = Logger.getInstance(DepLensPanel.class);
    private static final String CARD_CONTENT = "content";
    private static final String CARD_MESSAGE = "message";
    private static final int SECURITY_TAB = 1;

    private final Project project;
    private final GradleDependencySource source;
    private final RequestSequencer sequencer = new RequestSequencer();
    private final RequestSequencer securitySequencer = new RequestSequencer();
    /** Koordinate {@code g:a:v} → Bericht; nur auf dem EDT benutzt. */
    private final Map<String, SecurityReport> reports = new HashMap<>();
    private final ComboBox<DependencyAnalyzerProject> moduleBox = new ComboBox<>();
    private final SearchTextField searchField = new SearchTextField(false);
    private final Alarm searchAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    private final JBCheckBox onlyVulnerable = new JBCheckBox("Nur verwundbare");
    private final JButton securityButton = new JButton(AllIcons.Actions.ForceRefresh);
    private final Tree catalogTree = new Tree(TreeModels.empty());
    private final Tree pathTree = new Tree(TreeModels.empty());
    private final DependencyTreeCellRenderer pathRenderer = new DependencyTreeCellRenderer();
    private final SecurityPanel securityPanel = new SecurityPanel();
    private final JBTabbedPane detailTabs = new JBTabbedPane();
    private final JBLabel listTitle = sectionTitle("");
    private final OnePixelSplitter contentSplitter = new OnePixelSplitter(true, 0.45f);
    private final JPanel toolbar = new JPanel(new BorderLayout(JBUI.scale(8), 0));
    private final JBLabel statusLabel = new JBLabel();
    private final ActionLink statusRetry = new ActionLink("erneut versuchen", (ActionListener) e -> startSecurityCheck(false));
    private final JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JBLabel messageLabel = new JBLabel("", SwingConstants.CENTER);
    private final JButton retryButton = new JButton("Gradle neu laden");
    private final JBLoadingPanel loadingPanel;
    private volatile boolean disposed;
    private boolean updatingModules;
    /** Ein automatischer Gradle-Sync pro Panel, damit ein leeres Projekt keine Endlosschleife auslöst. */
    private boolean autoSyncDone;
    private @Nullable String securityNotice;
    private int securityFailures;

    DependencyGraph graph = DependencyGraph.empty("");

    public DepLensPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        source = new GradleDependencySource(project, () -> runOnEdt(this::reloadModules));
        Disposer.register(this, source);

        moduleBox.setRenderer(new SimpleListCellRenderer<>() {
            @Override
            public void customize(JList<? extends DependencyAnalyzerProject> list,
                                  DependencyAnalyzerProject value, int index, boolean selected, boolean hasFocus) {
                setText(value == null ? "" : value.getTitle());
            }
        });
        moduleBox.addActionListener(e -> {
            if (!updatingModules) {
                loadSelectedModule();
            }
        });

        JButton refreshButton = new JButton(AllIcons.Actions.Refresh);
        refreshButton.setToolTipText("Gradle neu laden");
        refreshButton.addActionListener(e -> syncGradle());
        securityButton.setToolTipText("Sicherheit neu prüfen");
        securityButton.addActionListener(e -> startSecurityCheck(true));
        onlyVulnerable.addActionListener(e -> runSearch());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0));
        actions.add(onlyVulnerable);
        actions.add(refreshButton);
        actions.add(securityButton);

        toolbar.setBorder(JBUI.Borders.empty(8));
        toolbar.add(moduleBox, BorderLayout.WEST);
        toolbar.add(searchField, BorderLayout.CENTER);
        toolbar.add(actions, BorderLayout.EAST);
        searchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                searchAlarm.cancelAllRequests();
                searchAlarm.addRequest(DepLensPanel.this::runSearch, 300);
            }
        });

        statusRow.setBorder(JBUI.Borders.empty(0, 10, 6, 10));
        statusRow.add(statusLabel);
        statusRow.add(statusRetry);
        statusRow.setVisible(false);
        JPanel north = new JPanel(new BorderLayout());
        north.add(toolbar, BorderLayout.NORTH);
        north.add(statusRow, BorderLayout.SOUTH);

        catalogTree.setRootVisible(false);
        catalogTree.setShowsRootHandles(true);
        catalogTree.setCellRenderer(new CatalogTreeCellRenderer(entry -> reports.get(entry.coordinates())));
        catalogTree.setBorder(JBUI.Borders.empty(4));
        catalogTree.setRowHeight(JBUI.scale(26));
        catalogTree.addTreeSelectionListener(e -> showEntry(selectedEntry()));
        ToolTipManager.sharedInstance().registerComponent(catalogTree);

        pathTree.setRootVisible(false);
        pathTree.setShowsRootHandles(true);
        pathTree.setCellRenderer(pathRenderer);
        pathTree.setBorder(JBUI.Borders.empty(4, 8));
        ToolTipManager.sharedInstance().registerComponent(pathTree);
        detailTabs.addTab("Abhängigkeitspfad", new JBScrollPane(pathTree));
        detailTabs.addTab("Sicherheit", securityPanel);

        retryButton.addActionListener(e -> syncGradle());
        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.add(messageLabel, BorderLayout.CENTER);
        JPanel retryRow = new JPanel();
        retryRow.add(retryButton);
        messagePanel.add(retryRow, BorderLayout.SOUTH);

        cardPanel.add(createContent(), CARD_CONTENT);
        cardPanel.add(messagePanel, CARD_MESSAGE);

        loadingPanel = new JBLoadingPanel(new BorderLayout(), this);
        loadingPanel.add(cardPanel, BorderLayout.CENTER);

        add(north, BorderLayout.NORTH);
        add(loadingPanel, BorderLayout.CENTER);

        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(DepLensSettings.TOPIC, (DepLensSettings.Listener) () -> runOnEdt(this::onSettingsChanged));
        applySecurityVisibility();
        reloadModules();
    }

    /** Kategorie-Liste oben; Details (Pfad, Sicherheit) werden erst bei einer Auswahl unten eingeblendet. */
    private JComponent createContent() {
        contentSplitter.setFirstComponent(section(listTitle, new JBScrollPane(catalogTree)));
        return contentSplitter;
    }

    private static JBLabel sectionTitle(String text) {
        JBLabel label = new JBLabel(text);
        label.setFont(JBUI.Fonts.label().asBold());
        label.setBorder(JBUI.Borders.empty(6, 10));
        return label;
    }

    private static JComponent section(JBLabel title, JComponent body) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(title, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    /** Wird auf dem EDT aufgerufen, nachdem {@link #graph} neu gesetzt wurde. */
    private void onGraphLoaded() {
        showCard(CARD_CONTENT);
        runSearch();
        startSecurityCheck(false);
    }

    private void runSearch() {
        String query = searchField.getText();
        boolean searching = !query.isBlank();
        List<CatalogCategory> categories = DependencyCatalog.build(graph, query);
        if (onlyVulnerable.isSelected()) {
            categories = DependencyCatalog.filter(categories, this::isVulnerable);
        }
        catalogTree.setModel(TreeModels.catalog(categories));
        listTitle.setText((searching ? "Treffer" : "Alle Dependencies")
                + " (" + DependencyCatalog.entryCount(categories) + ")");
        catalogTree.getEmptyText().setText(onlyVulnerable.isSelected()
                ? "Keine verwundbaren Dependencies"
                : searching ? "Keine Treffer für ‚" + query.strip() + "‘" : "Keine Dependencies");
        if ((searching || onlyVulnerable.isSelected()) && !categories.isEmpty()) {
            TreeUtil.expandAll(catalogTree);
        }
        if (searching && !categories.isEmpty()) {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) catalogTree.getModel().getRoot();
            DefaultMutableTreeNode firstEntry = (DefaultMutableTreeNode) root.getFirstChild().getChildAt(0);
            catalogTree.setSelectionPath(new TreePath(firstEntry.getPath()));
        } else {
            catalogTree.clearSelection();
            showEntry(null);
        }
    }

    private @Nullable CatalogEntry selectedEntry() {
        Object selected = catalogTree.getLastSelectedPathComponent();
        return selected instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof CatalogEntry entry
                ? entry
                : null;
    }

    private boolean isVulnerable(CatalogEntry entry) {
        SecurityReport report = reports.get(entry.coordinates());
        return report != null && report.status() == SecurityReport.Status.VERWUNDBAR;
    }

    /** Zeigt Pfad und Sicherheit der Dependency; ohne Auswahl wird der Bereich ausgeblendet. */
    private void showEntry(@Nullable CatalogEntry entry) {
        if (entry == null) {
            pathTree.setModel(TreeModels.empty());
            contentSplitter.setSecondComponent(null);
            return;
        }
        TreeModels.PathTree tree = TreeModels.allPaths(entry);
        pathRenderer.setHighlighted(entry.occurrences());
        pathTree.setModel(tree.model());
        for (DefaultMutableTreeNode hitNode : tree.hitNodes()) {
            TreePath hitPath = new TreePath(hitNode.getPath());
            pathTree.expandPath(hitPath);
            for (int i = 0; i < hitNode.getChildCount(); i++) {
                pathTree.expandPath(hitPath.pathByAddingChild(hitNode.getChildAt(i)));
            }
        }
        contentSplitter.setSecondComponent(detailTabs);
        pathTree.scrollPathToVisible(new TreePath(tree.hitNodes().getFirst().getPath()));
        updateSecurityDetails(entry);
        if (hasSecurityTab() && isVulnerable(entry)) {
            detailTabs.setSelectedIndex(SECURITY_TAB);
        }
    }

    private void updateSecurityDetails(CatalogEntry entry) {
        if (!hasSecurityTab()) {
            return;
        }
        SecurityReport report = reports.get(entry.coordinates());
        securityPanel.show(entry, report);
        detailTabs.setTitleAt(SECURITY_TAB, report != null && report.status() == SecurityReport.Status.VERWUNDBAR
                ? "Sicherheit (" + report.findings().size() + ")"
                : "Sicherheit");
    }

    private boolean hasSecurityTab() {
        return detailTabs.getTabCount() > SECURITY_TAB;
    }

    private static boolean securityEnabled() {
        return DepLensSettings.getInstance().isSecurityEnabled();
    }

    private void onSettingsChanged() {
        SecurityService.getInstance(project).resetOssIndexRejection();
        applySecurityVisibility();
        if (!graph.isEmpty()) {
            runSearch();
            startSecurityCheck(false);
        }
    }

    /** Blendet alle Sicherheits-Elemente ein oder aus, je nach Einstellung. */
    private void applySecurityVisibility() {
        boolean enabled = securityEnabled();
        onlyVulnerable.setVisible(enabled);
        securityButton.setVisible(enabled);
        if (!enabled) {
            onlyVulnerable.setSelected(false);
            securitySequencer.next();
            reports.clear();
            setStatus(null, false);
            if (hasSecurityTab()) {
                detailTabs.removeTabAt(SECURITY_TAB);
            }
        } else if (!hasSecurityTab()) {
            detailTabs.addTab("Sicherheit", securityPanel);
        }
        catalogTree.repaint();
    }

    /** Prüft alle Artefakte des Moduls; ältere Prüfungen werden dabei verworfen. */
    private void startSecurityCheck(boolean forceRefresh) {
        if (!securityEnabled() || graph.isEmpty()) {
            return;
        }
        long request = securitySequencer.next();
        reports.clear();
        securityFailures = 0;
        securityNotice = null;
        List<Artifact> artifacts = new ArrayList<>();
        for (CatalogCategory category : DependencyCatalog.build(graph, "")) {
            for (CatalogEntry entry : category.entries()) {
                DependencyNode node = entry.representative();
                String problem = node.kind() == DependencyNode.Kind.ARTIFACT
                        ? CheckableVersion.problem(node.version(), entry.isUnresolved())
                        : "Projekt-Modul";
                if (problem == null) {
                    artifacts.add(new Artifact(node.groupId(), node.artifactId(), node.version()));
                } else {
                    reports.put(entry.coordinates(), SecurityReport.notCheckable(problem));
                }
            }
        }
        catalogTree.repaint();
        refreshSelectedEntry();
        setStatus("Sicherheit wird geprüft …", false);
        BooleanSupplier cancelled = () -> disposed || !securitySequencer.isCurrent(request);
        SecurityService service = SecurityService.getInstance(project);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                service.check(artifacts, forceRefresh, cancelled, new SecurityChecker.Listener() {
                    @Override
                    public void progress(int done, int total) {
                        onSecurityEdt(request, () -> setStatus("Sicherheit wird geprüft … " + done + "/" + total, false));
                    }

                    @Override
                    public void reportReady(Artifact artifact, SecurityReport report) {
                        onSecurityEdt(request, () -> applyReport(artifact, report));
                    }

                    @Override
                    public void notice(SecurityChecker.Notice notice) {
                        onSecurityEdt(request, () -> securityNotice = noticeText(notice));
                    }
                });
                onSecurityEdt(request, this::finishSecurityCheck);
            } catch (Throwable e) {
                if (e instanceof ControlFlowException) {
                    throw e;
                }
                LOG.warn("DepLens: Sicherheitsprüfung fehlgeschlagen", e);
                onSecurityEdt(request, () -> setStatus("Sicherheitsprüfung fehlgeschlagen", true));
            }
        });
    }

    private void applyReport(Artifact artifact, SecurityReport report) {
        reports.put(artifact.coordinates(), report);
        if (report.status() == SecurityReport.Status.FEHLER) {
            securityFailures++;
        }
        catalogTree.repaint();
        refreshSelectedEntry();
    }

    private void finishSecurityCheck() {
        if (securityFailures > 0) {
            setStatus(securityFailures + (securityFailures == 1 ? " Prüfung" : " Prüfungen") + " fehlgeschlagen", true);
        } else {
            setStatus(securityNotice, false);
        }
        if (onlyVulnerable.isSelected()) {
            runSearch();
        }
    }

    private void refreshSelectedEntry() {
        CatalogEntry selected = selectedEntry();
        if (selected != null) {
            updateSecurityDetails(selected);
        }
    }

    private static String noticeText(SecurityChecker.Notice notice) {
        return switch (notice) {
            case OSS_INDEX_UNAUTHORIZED -> "OSS-Index-Token ungültig – nur OSV aktiv";
            case OSS_INDEX_FAILED -> "OSS Index nicht erreichbar – nur OSV aktiv";
        };
    }

    private void setStatus(@Nullable String text, boolean retry) {
        statusLabel.setText(text == null ? "" : text);
        statusRetry.setVisible(retry);
        statusRow.setVisible(text != null);
    }

    private void onSecurityEdt(long request, Runnable runnable) {
        runOnEdt(() -> {
            if (securitySequencer.isCurrent(request)) {
                runnable.run();
            }
        });
    }

    /** Startet einen Gradle-Sync; danach lädt {@code whenDataChanged} die Module neu. */
    private void syncGradle() {
        source.reset();
        if (source.requestSync()) {
            showMessage("Gradle wird neu geladen … DepLens aktualisiert sich danach automatisch.", false);
        } else {
            reloadModules();
        }
    }

    /**
     * Zeigt den Hinweis zu fehlenden Daten. Beim ersten Mal wird stattdessen automatisch ein Gradle-Sync
     * gestartet, damit DepLens ohne manuellen Sync alle Dependencies erkennt.
     */
    private void handleMissingData(String message) {
        if (!autoSyncDone) {
            autoSyncDone = true;
            if (source.requestSync()) {
                showMessage("Gradle-Projekt wird geladen … DepLens aktualisiert sich danach automatisch.", false);
                return;
            }
        }
        showMessage(message, true);
    }

    private void reloadModules() {
        DependencyAnalyzerProject previous = (DependencyAnalyzerProject) moduleBox.getSelectedItem();
        String previousTitle = previous == null ? null : previous.getTitle();
        long request = sequencer.next();
        loadingPanel.startLoading();
        inBackground(request, source::modules, modules -> {
            loadingPanel.stopLoading();
            updatingModules = true;
            try {
                moduleBox.removeAllItems();
                modules.forEach(moduleBox::addItem);
                modules.stream()
                        .filter(m -> Objects.equals(m.getTitle(), previousTitle))
                        .findFirst()
                        .ifPresent(moduleBox::setSelectedItem);
            } finally {
                updatingModules = false;
            }
            if (modules.isEmpty()) {
                graph = DependencyGraph.empty("");
                handleMissingData("Kein Gradle-Projekt gefunden.");
            } else {
                loadSelectedModule();
            }
        });
    }

    private void loadSelectedModule() {
        DependencyAnalyzerProject module = (DependencyAnalyzerProject) moduleBox.getSelectedItem();
        if (module == null) {
            return;
        }
        long request = sequencer.next();
        loadingPanel.startLoading();
        inBackground(request, () -> source.load(module), loaded -> {
            loadingPanel.stopLoading();
            graph = loaded;
            if (loaded.isEmpty()) {
                handleMissingData("Keine Dependencies für „" + module.getTitle()
                        + "“ – ist der Gradle-Lauf fehlgeschlagen? Details im Build-Fenster.");
            } else {
                onGraphLoaded();
            }
        });
    }

    /** Runs work off the EDT; results, cancellations and failures of superseded requests are discarded. */
    private <T> void inBackground(long request, Supplier<T> work, Consumer<T> onEdt) {
        if (disposed) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (disposed) {
                return;
            }
            try {
                T result = work.get();
                runOnEdt(() -> {
                    if (sequencer.isCurrent(request)) {
                        onEdt.accept(result);
                    }
                });
            } catch (ProcessCanceledException e) {
                stopLoadingIfCurrent(request);
            } catch (Throwable e) {
                if (e instanceof ControlFlowException) {
                    stopLoadingIfCurrent(request);
                    throw e;
                }
                LOG.warn("DepLens: loading failed", e);
                String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                runOnEdt(() -> {
                    if (sequencer.isCurrent(request)) {
                        loadingPanel.stopLoading();
                        showMessage("Laden fehlgeschlagen: " + detail, true);
                    }
                });
            }
        });
    }

    private void stopLoadingIfCurrent(long request) {
        runOnEdt(() -> {
            if (sequencer.isCurrent(request)) {
                loadingPanel.stopLoading();
            }
        });
    }

    private void runOnEdt(Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!disposed) {
                runnable.run();
            }
        }, ModalityState.any());
    }

    private void showMessage(String text, boolean retry) {
        messageLabel.setText(text);
        retryButton.setVisible(retry);
        showCard(CARD_MESSAGE);
    }

    private void showCard(String card) {
        cards.show(cardPanel, card);
    }

    @Override
    public void dispose() {
        disposed = true;
    }
}
