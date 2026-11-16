package io.github.einsnils.deplens.search;

import io.github.einsnils.deplens.model.DependencyNode;
import java.util.List;

/** Ein Vorkommen der gesuchten Dependency. */
public record SearchHit(DependencyNode node) {

    public List<DependencyNode> path() {
        return node.pathFromRoot();
    }

    /** z. B. {@code "runtimeClasspath · via spring-boot-starter-json"}. */
    public String location() {
        DependencyNode parent = node.parent();
        String via = parent == null || parent.isRoot() ? "direkt" : "via " + parent.name();
        return node.configuration() + " · " + via;
    }
}
