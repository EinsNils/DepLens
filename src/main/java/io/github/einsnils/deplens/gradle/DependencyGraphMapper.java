package io.github.einsnils.deplens.gradle;

import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency;
import io.github.einsnils.deplens.model.DependencyGraph;
import io.github.einsnils.deplens.model.DependencyNode;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Übersetzt die flache Analyzer-Liste (mit Parent-Zeigern) in einen {@link DependencyGraph}. */
public final class DependencyGraphMapper {
    private final String unresolvedMessage;

    public DependencyGraphMapper(String unresolvedMessage) {
        this.unresolvedMessage = unresolvedMessage;
    }

    public DependencyGraph map(String moduleName, List<? extends DependencyAnalyzerDependency> dependencies) {
        Map<DependencyAnalyzerDependency, DependencyNode> nodes = new IdentityHashMap<>();
        List<DependencyNode> roots = new ArrayList<>();
        for (DependencyAnalyzerDependency dependency : dependencies) {
            toNode(dependency, nodes, roots);
        }
        return new DependencyGraph(moduleName, roots);
    }

    private DependencyNode toNode(DependencyAnalyzerDependency dependency,
                                  Map<DependencyAnalyzerDependency, DependencyNode> nodes,
                                  List<DependencyNode> roots) {
        DependencyNode existing = nodes.get(dependency);
        if (existing != null) {
            return existing;
        }
        DependencyAnalyzerDependency parentDependency = dependency.getParent();
        DependencyNode parent = parentDependency == null ? null : toNode(parentDependency, nodes, roots);
        DependencyNode node = create(dependency, parent);
        applyStatus(dependency, node);
        nodes.put(dependency, node);
        if (parent == null) {
            roots.add(node);
        }
        return node;
    }

    private static DependencyNode create(DependencyAnalyzerDependency dependency, @Nullable DependencyNode parent) {
        String configuration = dependency.getScope().getName();
        return switch (dependency.getData()) {
            case DependencyAnalyzerDependency.Data.Artifact artifact -> DependencyNode.artifact(
                    artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), configuration, parent);
            case DependencyAnalyzerDependency.Data.Module module ->
                    DependencyNode.module(module.getName(), configuration, parent);
            case DependencyAnalyzerDependency.Data other ->
                    DependencyNode.module(String.valueOf(other), configuration, parent);
        };
    }

    private void applyStatus(DependencyAnalyzerDependency dependency, DependencyNode node) {
        for (DependencyAnalyzerDependency.Status status : dependency.getStatus()) {
            if (status instanceof DependencyAnalyzerDependency.Status.Omitted) {
                node.markOmitted();
            } else if (status instanceof DependencyAnalyzerDependency.Status.Warning warning) {
                if (unresolvedMessage.equals(warning.getMessage())) {
                    node.markUnresolved();
                } else {
                    node.markConflict(warning.getMessage());
                }
            }
        }
    }
}
