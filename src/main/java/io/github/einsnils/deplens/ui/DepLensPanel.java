package io.github.einsnils.deplens.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerProject;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.ui.JBUI;
import io.github.einsnils.deplens.gradle.GradleDependencySource;
import io.github.einsnils.deplens.model.DependencyGraph;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Inhalt des DepLens-Tool-Windows. */
public final class DepLensPanel extends JPanel implements Disposable {
    private static final Logger LOG = Logger.getInstance(DepLensPanel.class);
    private static final String CARD_CONTENT = "content";
    private static final String CARD_MESSAGE = "message";

    private final GradleDependencySource source;
    private final RequestSequencer sequencer = new RequestSequencer();
    private final ComboBox<DependencyAnalyzerProject> moduleBox = new ComboBox<>();
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
        refreshButton.setToolTipText("Neu laden");
        refreshButton.addActionListener(e -> {
            source.reset();
            reloadModules();
        });

        toolbar.setBorder(JBUI.Borders.empty(4));
        toolbar.add(moduleBox, BorderLayout.WEST);
        toolbar.add(refreshButton, BorderLayout.EAST);

        retryButton.addActionListener(e -> reloadModules());
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

    /** Hauptbereich; Task 6 ersetzt das durch Trefferliste und Bäume. */
    private JComponent createContent() {
        return new JBLabel("Modul geladen", SwingConstants.CENTER);
    }

    /** Wird auf dem EDT aufgerufen, nachdem {@link #graph} neu gesetzt wurde. */
    private void onGraphLoaded() {
        showCard(CARD_CONTENT);
    }

    private void reloadModules() {
        DependencyAnalyzerProject previous = (DependencyAnalyzerProject) moduleBox.getSelectedItem();
        String previousTitle = previous == null ? null : previous.getTitle();
        loadingPanel.startLoading();
        inBackground(source::modules, modules -> {
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
        inBackground(() -> source.load(module), loaded -> {
            if (!sequencer.isCurrent(request)) {
                return;
            }
            loadingPanel.stopLoading();
            graph = loaded;
            onGraphLoaded();
        });
    }

    private <T> void inBackground(Supplier<T> work, Consumer<T> onEdt) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                T result = work.get();
                runOnEdt(() -> onEdt.accept(result));
            } catch (ProcessCanceledException e) {
                runOnEdt(loadingPanel::stopLoading);
            } catch (RuntimeException e) {
                LOG.warn("DepLens: loading failed", e);
                runOnEdt(() -> {
                    loadingPanel.stopLoading();
                    showMessage("Laden fehlgeschlagen: " + e.getMessage(), true);
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
