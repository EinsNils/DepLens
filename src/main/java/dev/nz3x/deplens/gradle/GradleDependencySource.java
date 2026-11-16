package dev.nz3x.deplens.gradle;

import com.intellij.DynamicBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerContributor;
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerExtension;
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerProject;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import dev.nz3x.deplens.model.DependencyGraph;
import java.util.List;
import kotlin.Unit;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/** Einziger Zugang zu IntelliJs Gradle-Dependency-Analyzer. Methoden blockieren – nie auf dem EDT aufrufen. */
public final class GradleDependencySource implements Disposable {
    private final Project project;
    private final Runnable onDataChanged;
    // ExternalSystemBundle ist interne API; dasselbe Bundle über die öffentliche DynamicBundle-API laden.
    private static final DynamicBundle EXTERNAL_SYSTEM_MESSAGES =
            new DynamicBundle(ExternalSystemUtil.class, "messages.ExternalSystemBundle");
    private final DependencyGraphMapper mapper = new DependencyGraphMapper(EXTERNAL_SYSTEM_MESSAGES.messageOrDefault(
            "external.system.dependency.analyzer.warning.unresolved", "unresolved"));
    private @Nullable Disposable contributorDisposable;
    private @Nullable DependencyAnalyzerContributor contributor;

    public GradleDependencySource(Project project, Runnable onDataChanged) {
        this.project = project;
        this.onDataChanged = onDataChanged;
    }

    public List<DependencyAnalyzerProject> modules() {
        DependencyAnalyzerContributor current = contributor();
        return current == null ? List.of() : current.getProjects();
    }

    public DependencyGraph load(DependencyAnalyzerProject module) {
        DependencyAnalyzerContributor current = contributor();
        if (current == null) {
            return DependencyGraph.empty(module.getTitle());
        }
        return mapper.map(module.getTitle(), current.getDependencies(module));
    }

    /**
     * Startet einen Gradle-Sync für alle verknüpften Gradle-Projekte. Nach dessen Ende meldet der Contributor
     * neue Daten ({@code onDataChanged}).
     *
     * @return {@code false}, wenn das Projekt kein verknüpftes Gradle-Projekt hat
     */
    public boolean requestSync() {
        if (GradleSettings.getInstance(project).getLinkedProjectsSettings().isEmpty()) {
            return false;
        }
        ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, GradleConstants.SYSTEM_ID));
        return true;
    }

    /**
     * Verwirft den Contributor und seine Listener. Die Daten liegen im projektweiten Index von IntelliJ und
     * werden erst nach einem Gradle-Sync neu geladen; ein neuer Contributor liest zunächst nur diesen Index.
     */
    public synchronized void reset() {
        if (contributorDisposable != null) {
            Disposer.dispose(contributorDisposable);
        }
        contributorDisposable = null;
        contributor = null;
    }

    private synchronized @Nullable DependencyAnalyzerContributor contributor() {
        if (contributor == null) {
            for (DependencyAnalyzerExtension extension : DependencyAnalyzerExtension.EP_NAME.getExtensionList()) {
                if (extension.isApplicable(GradleConstants.SYSTEM_ID)) {
                    contributorDisposable = Disposer.newDisposable(this, "DepLens contributor");
                    contributor = extension.createContributor(project, contributorDisposable);
                    contributor.whenDataChanged(() -> {
                        onDataChanged.run();
                        return Unit.INSTANCE;
                    }, contributorDisposable);
                    break;
                }
            }
        }
        return contributor;
    }

    @Override
    public void dispose() {
        // contributorDisposable ist Kind von this und wird von Disposer mit entsorgt.
    }
}
