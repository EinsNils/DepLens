package dev.nz3x.deplens.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Alle Dependency-Bäume eines Moduls, eine Wurzel pro Gradle-Configuration. */
public final class DependencyGraph {
    private final String moduleName;
    private final List<DependencyNode> roots;

    public DependencyGraph(String moduleName, List<DependencyNode> roots) {
        this.moduleName = moduleName;
        this.roots = List.copyOf(roots);
    }

    public static DependencyGraph empty(String moduleName) {
        return new DependencyGraph(moduleName, List.of());
    }

    public String moduleName() { return moduleName; }
    public List<DependencyNode> roots() { return roots; }
    public boolean isEmpty() { return roots.isEmpty(); }

    /** Alle Knoten in Preorder, Wurzel für Wurzel. */
    public List<DependencyNode> allNodes() {
        List<DependencyNode> result = new ArrayList<>();
        Deque<DependencyNode> stack = new ArrayDeque<>();
        for (DependencyNode root : roots.reversed()) {
            stack.push(root);
        }
        while (!stack.isEmpty()) {
            DependencyNode node = stack.pop();
            result.add(node);
            for (DependencyNode child : node.children().reversed()) {
                stack.push(child);
            }
        }
        return result;
    }
}
