package dev.nz3x.deplens.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Ein Vorkommen einer Dependency (oder des Moduls selbst) in genau einer Gradle-Configuration. */
public final class DependencyNode {
    public enum Kind { MODULE, ARTIFACT }

    private final Kind kind;
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String name;
    private final String configuration;
    private final @Nullable DependencyNode parent;
    private final List<DependencyNode> children = new ArrayList<>();
    private boolean omitted;
    private boolean unresolved;
    private @Nullable String conflict;

    private DependencyNode(Kind kind, String groupId, String artifactId, String version, String name,
                           String configuration, @Nullable DependencyNode parent) {
        this.kind = kind;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.name = name;
        this.configuration = configuration;
        this.parent = parent;
        if (parent != null) {
            parent.children.add(this);
        }
    }

    public static DependencyNode module(String name, String configuration, @Nullable DependencyNode parent) {
        return new DependencyNode(Kind.MODULE, "", "", "", name, configuration, parent);
    }

    public static DependencyNode artifact(String groupId, String artifactId, String version,
                                          String configuration, @Nullable DependencyNode parent) {
        return new DependencyNode(Kind.ARTIFACT, groupId, artifactId, version, artifactId, configuration, parent);
    }

    public Kind kind() { return kind; }
    public String groupId() { return groupId; }
    public String artifactId() { return artifactId; }
    public String version() { return version; }
    public String name() { return name; }
    public String configuration() { return configuration; }
    public @Nullable DependencyNode parent() { return parent; }
    public List<DependencyNode> children() { return Collections.unmodifiableList(children); }
    public boolean isRoot() { return parent == null; }
    public boolean isOmitted() { return omitted; }
    public boolean isUnresolved() { return unresolved; }
    public boolean hasConflict() { return conflict != null; }
    public @Nullable String conflict() { return conflict; }

    public void markOmitted() { omitted = true; }
    public void markUnresolved() { unresolved = true; }
    public void markConflict(String message) { conflict = message; }

    /** {@code group:artifact:version} für Artefakte, sonst der Modulname. */
    public String coordinates() {
        return kind == Kind.ARTIFACT ? groupId + ":" + artifactId + ":" + version : name;
    }

    /** Pfad von der Modul-Wurzel bis einschließlich dieses Knotens. */
    public List<DependencyNode> pathFromRoot() {
        Deque<DependencyNode> path = new ArrayDeque<>();
        for (DependencyNode n = this; n != null; n = n.parent) {
            path.addFirst(n);
        }
        return List.copyOf(path);
    }

    @Override
    public String toString() {
        return coordinates();
    }
}
