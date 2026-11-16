package dev.nz3x.deplens.catalog;

import java.util.List;

/** Ein Hersteller mit seinen Dependencies. */
public record CatalogCategory(String name, List<CatalogEntry> entries) {

    public CatalogCategory {
        entries = List.copyOf(entries);
    }
}
