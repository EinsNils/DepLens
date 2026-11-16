package io.github.einsnils.deplens.search;

import io.github.einsnils.deplens.model.DependencyGraph;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** Teilstring-Suche (ohne Groß-/Kleinschreibung) auf {@code group:artifact:version}. */
public final class DependencySearch {
    private static final Comparator<SearchHit> ORDER = Comparator
            .comparing((SearchHit hit) -> hit.node().coordinates())
            .thenComparing(hit -> hit.node().configuration())
            .thenComparing(SearchHit::location);

    private DependencySearch() {
    }

    public static List<SearchHit> search(DependencyGraph graph, @Nullable String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return List.of();
        }
        return graph.allNodes().stream()
                .filter(node -> !node.isRoot())
                .filter(node -> node.coordinates().toLowerCase(Locale.ROOT).contains(needle))
                .map(SearchHit::new)
                .sorted(ORDER)
                .toList();
    }
}
