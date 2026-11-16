package dev.nz3x.deplens.catalog;

import dev.nz3x.deplens.model.DependencyNode;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Leitet aus der groupId einen Hersteller-Namen als Kategorie ab. */
public final class VendorCategory {
    public static final String PROJECT_MODULES = "Projekt-Module";
    public static final String OTHERS = "Sonstige";

    private record Rule(String groupPrefix, String category) {
        boolean matches(String groupId) {
            return groupId.equals(groupPrefix) || groupId.startsWith(groupPrefix + ".");
        }
    }

    /** Hersteller, deren Artefakte über mehrere groupIds verteilt sind. */
    private static final List<Rule> RULES = List.of(
            new Rule("org.springframework", "Spring"),
            new Rule("org.springdoc", "Spring"),
            new Rule("io.spring", "Spring"),
            new Rule("com.fasterxml.jackson", "Jackson"),
            new Rule("org.junit", "JUnit"),
            new Rule("junit", "JUnit"),
            new Rule("org.opentest4j", "JUnit"),
            new Rule("org.jetbrains.kotlin", "Kotlin"),
            new Rule("org.jetbrains.kotlinx", "Kotlin"));

    /** Erste Segmente, nach denen erst der eigentliche Herstellername kommt. */
    private static final Set<String> DOMAIN_PREFIXES = Set.of(
            "org", "com", "io", "net", "de", "dev", "me", "ch", "at", "eu", "info", "edu", "uk", "fr", "nl");

    private VendorCategory() {
    }

    public static String of(DependencyNode node) {
        return node.kind() == DependencyNode.Kind.MODULE ? PROJECT_MODULES : ofGroup(node.groupId());
    }

    public static String ofGroup(String groupId) {
        for (Rule rule : RULES) {
            if (rule.matches(groupId)) {
                return rule.category();
            }
        }
        String[] segments = groupId.split("\\.");
        boolean hasDomainPrefix = segments.length > 1 && DOMAIN_PREFIXES.contains(segments[0]);
        String vendor = hasDomainPrefix ? segments[1] : segments[0];
        return vendor.isEmpty() ? OTHERS : capitalize(vendor);
    }

    private static String capitalize(String text) {
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
