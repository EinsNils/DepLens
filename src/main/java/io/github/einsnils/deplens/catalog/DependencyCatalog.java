package io.github.einsnils.deplens.catalog;

import io.github.einsnils.deplens.model.DependencyGraph;
import io.github.einsnils.deplens.model.DependencyNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.Nullable;

/**
 * Alle Dependencies eines Moduls, jede genau einmal, nach Hersteller gruppiert.
 * Ein Suchbegriff filtert per Teilstring (ohne Groß-/Kleinschreibung) auf {@code group:artifact:version}.
 */
public final class DependencyCatalog {
    private static final Comparator<String> CATEGORY_ORDER = Comparator
            .comparing((String name) -> name.equals(VendorCategory.PROJECT_MODULES))
            .thenComparing(String.CASE_INSENSITIVE_ORDER);
    private static final Comparator<CatalogEntry> ENTRY_ORDER = Comparator
            .comparing((CatalogEntry entry) -> entry.representative().name(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(CatalogEntry::coordinates);

    private DependencyCatalog() {
    }

    public static List<CatalogCategory> build(DependencyGraph graph, @Nullable String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        Map<String, List<DependencyNode>> occurrencesByCoordinates = new LinkedHashMap<>();
        for (DependencyNode node : graph.allNodes()) {
            if (!node.isRoot() && node.coordinates().toLowerCase(Locale.ROOT).contains(needle)) {
                occurrencesByCoordinates.computeIfAbsent(node.coordinates(), key -> new ArrayList<>()).add(node);
            }
        }

        Map<String, List<CatalogEntry>> entriesByCategory = new TreeMap<>(CATEGORY_ORDER);
        for (List<DependencyNode> occurrences : occurrencesByCoordinates.values()) {
            CatalogEntry entry = new CatalogEntry(occurrences);
            entriesByCategory.computeIfAbsent(VendorCategory.of(entry.representative()), key -> new ArrayList<>())
                    .add(entry);
        }
        return entriesByCategory.entrySet().stream()
                .map(category -> new CatalogCategory(
                        category.getKey(), category.getValue().stream().sorted(ENTRY_ORDER).toList()))
                .toList();
    }

    public static int entryCount(List<CatalogCategory> categories) {
        return categories.stream().mapToInt(category -> category.entries().size()).sum();
    }
}
