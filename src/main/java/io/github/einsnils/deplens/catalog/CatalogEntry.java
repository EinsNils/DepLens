package io.github.einsnils.deplens.catalog;

import io.github.einsnils.deplens.model.DependencyNode;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Eine Dependency ({@code group:artifact:version}) mit all ihren Vorkommen im Modul. */
public record CatalogEntry(List<DependencyNode> occurrences) {

    public CatalogEntry {
        occurrences = List.copyOf(occurrences);
    }

    /** Erstes Vorkommen in Graph-Reihenfolge; trägt Name, Version und Art. */
    public DependencyNode representative() {
        return occurrences.getFirst();
    }

    public String coordinates() {
        return representative().coordinates();
    }

    /** Erste Konflikt-Meldung eines Vorkommens, sonst {@code null}. */
    public @Nullable String conflict() {
        return occurrences.stream()
                .map(DependencyNode::conflict)
                .filter(message -> message != null)
                .findFirst()
                .orElse(null);
    }

    public boolean isUnresolved() {
        return occurrences.stream().anyMatch(DependencyNode::isUnresolved);
    }
}
