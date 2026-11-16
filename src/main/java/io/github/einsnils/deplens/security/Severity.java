package io.github.einsnils.deplens.security;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** Schweregrad einer Lücke; die Reihenfolge ist absteigend nach Schwere. */
public enum Severity {
    KRITISCH, HOCH, MITTEL, NIEDRIG, UNBEKANNT;

    /** OSV-/GHSA-Label wie {@code CRITICAL}, {@code HIGH}, {@code MODERATE}, {@code LOW}. */
    public static Severity fromLabel(@Nullable String label) {
        if (label == null) {
            return UNBEKANNT;
        }
        return switch (label.strip().toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> KRITISCH;
            case "HIGH" -> HOCH;
            case "MODERATE", "MEDIUM" -> MITTEL;
            case "LOW" -> NIEDRIG;
            default -> UNBEKANNT;
        };
    }

    public static Severity fromCvssScore(double score) {
        if (score >= 9.0) {
            return KRITISCH;
        }
        if (score >= 7.0) {
            return HOCH;
        }
        if (score >= 4.0) {
            return MITTEL;
        }
        return score > 0.0 ? NIEDRIG : UNBEKANNT;
    }
}
