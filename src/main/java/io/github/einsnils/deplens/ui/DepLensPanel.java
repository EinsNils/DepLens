package io.github.einsnils.deplens.ui;

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
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import io.github.einsnils.deplens.gradle.GradleDependencySource;
import io.github.einsnils.deplens.model.DependencyGraph;
import io.github.einsnils.deplens.search.DependencySearch;
import io.github.einsnils.deplens.search.SearchHit;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;

/** Inhalt des DepLens-Tool-Windows. */
public final class DepLensPanel extends JPanel implements Disposable {
    private static final Logger LOG = Logger.getInstance(DepLensPanel.class);
    private static final String CARD_CONTENT = "content";
    private static final String CARD_MESSAGE = "message";

    private final GradleDependencySource source;
    private final RequestSequencer sequencer = new RequestSequencer();
    private final ComboBox<DependencyAnalyzerProject> moduleBox = new ComboBox<>();
    private final SearchTextField searchField = new SearchTextField(false);
    private final Alarm searchAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    private final CollectionListModel<SearchHit> hitModel = new CollectionListModel<>();
    private final JBList<SearchHit> hitList = new JBList<>(hitModel);
    private final Tree parentsTree = new Tree(TreeModels.empty());
    private final Tree childrenTree = new Tree(TreeModels.empty());
    private final DependencyTreeCellRenderer parentsRenderer = new DependencyTreeCellRenderer();
    private final DependencyTreeCellRenderer childrenRenderer = new DependencyTreeCellRenderer();
    private final JPanel toolbar = new JPanel(new BorderLayout(JBUI.scale(4), 0));
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JBLabel messageLabel = new JBLabel("", SwingConstants.CENTER);
    private final JButton retryButton = new JButton("Erneut versuchen");
    private final JBLoadingPanel loadingPanel;
    private volatile boolean disposed;
    private boolean updatingModules;

    DependencyGraph graph = DependencyGraph.empty("");

    public DepLensPanel(Project project) {
        super(new BorderLayout());
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
        refreshButton.setToolTipText("Neu laden (für neue Daten Gradle-Sync ausführen)");
        refreshButton.addActionListener(e -> {
            source.reset();
            reloadModules();
        });

        toolbar.setBorder(JBUI.Borders.empty(4));
        toolbar.add(moduleBox, BorderLayout.WEST);
        toolbar.add(searchField, BorderLayout.CENTER);
        searchField.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                searchAlarm.cancelAllRequests();
                searchAlarm.addRequest(DepLensPanel.this::runSearch, 300);
            }
        });
        toolbar.add(refreshButton, BorderLayout.EAST);

        hitList.setCellRenderer(new SearchHitCellRenderer());
        hitList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showHit(hitList.getSelectedValue());
            }
        });
        parentsTree.setCellRenderer(parentsRenderer);
        childrenTree.setCellRenderer(childrenRenderer);
        ToolTipManager.sharedInstance().registerComponent(parentsTree);
        ToolTipManager.sharedInstance().registerComponent(childrenTree);
        hitList.setFixedCellHeight(JBUI.scale(22));
        parentsTree.setRootVisible(true);
        childrenTree.setRootVisible(true);

        retryButton.addActionListener(e -> {
            source.reset();
            reloadModules();
        });
        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.add(messageLabel, BorderLayout.CENTER);
        JPanel retryRow = new JPanel();
        retryRow.add(retryButton);
        messagePanel.add(retryRow, BorderLayout.SOUTH);

        cardPanel.add(createContent(), CARD_CONTENT);
        cardPanel.add(messagePanel, CARD_MESSAGE);

        loadingPanel = new JBLoadingPanel(new BorderLayout(), this);
        loadingPanel.add(cardPanel, BorderLayout.CENTER);

        add(toolbar, BorderLayout.NORTH);
        add(loadingPanel, BorderLayout.CENTER);

        reloadModules();
    }

    private JComponent createContent() {
        OnePixelSplitter trees = new OnePixelSplitter(false, 0.5f);
        trees.setFirstComponent(titled("Parents (bis zum Modul)", new JBScrollPane(parentsTree)));
        trees.setSecondComponent(titled("Children", new JBScrollPane(childrenTree)));

        OnePixelSplitter main = new OnePixelSplitter(true, 0.35f);
        main.setFirstComponent(titled("Treffer", new JBScrollPane(hitList)));
        main.setSecondComponent(trees);
        return main;
    }

    private static JComponent titled(String title, JComponent body) {
        JPanel panel = new JPanel(new BorderLayout());
        JBLabel label = new JBLabel(title);
        label.setBorder(JBUI.Borders.empty(2, 4));
        panel.add(label, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    /** Wird auf dem EDT aufgerufen, nachdem {@link #graph} neu gesetzt wurde. */
    private void onGraphLoaded() {
        showCard(CARD_CONTENT);
        runSearch();
    }

    private void runSearch() {
        String query = searchField.getText();
        List<SearchHit> hits = DependencySearch.search(graph, query);
        hitModel.replaceAll(hits);
        hitList.getEmptyText().setText(query.isBlank()
                ? "Suchbegriff eingeben, z. B. jackson-databind"
                : "Keine Treffer für ‚" + query.strip() + "‘");
        if (hits.isEmpty()) {
            showHit(null);
        } else {
            hitList.setSelectedIndex(0);
            // Listener feuert nicht, wenn Index 0 schon ausgewählt war
            showHit(hitList.getSelectedValue());
        }
    }

    private void showHit(SearchHit hit) {
        if (hit == null) {
            parentsTree.setModel(TreeModels.empty());
            childrenTree.setModel(TreeModels.empty());
            return;
        }
        parentsRenderer.setHighlighted(hit.node());
        childrenRenderer.setHighlighted(hit.node());
        parentsTree.setModel(TreeModels.parentPath(hit));
        TreeUtil.expandAll(parentsTree);
        childrenTree.setModel(TreeModels.children(hit.node()));
        TreeUtil.expand(childrenTree, 2);
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
                showMessage("Kein Gradle-Projekt gefunden – bitte Gradle-Sync ausführen.", false);
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
                showMessage("Keine Dependencies für „" + module.getTitle()
                        + "“ geladen – ist der Gradle-Lauf fehlgeschlagen? Details im Build-Fenster.", true);
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
                runOnEdt(() -> {
                    if (sequencer.isCurrent(request)) {
                        loadingPanel.stopLoading();
                    }
                });
            } catch (Throwable e) {
                if (e instanceof ControlFlowException) {
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
