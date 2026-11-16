package dev.nz3x.deplens.security;

import org.jetbrains.annotations.Nullable;

/** Entscheidet, ob eine Version konkret genug für eine Prüfung ist. */
public final class CheckableVersion {

    private CheckableVersion() {
    }

    /** Grund, warum die Version nicht prüfbar ist, oder {@code null}, wenn sie geprüft werden kann. */
    public static @Nullable String problem(String version, boolean unresolved) {
        if (unresolved) {
            return "Nicht auflösbar";
        }
        String text = version.strip();
        if (text.isEmpty()) {
            return "Keine Version";
        }
        if (isDynamic(text)) {
            return "Dynamische Version";
        }
        return null;
    }

    /** Gradle-Bereiche ({@code [1.0,2.0)}), Präfixe ({@code 1.+}) und {@code latest.*}. */
    private static boolean isDynamic(String version) {
        return version.contains("+") || version.contains("[") || version.contains("]") || version.contains("(")
                || version.contains(")") || version.contains(",") || version.startsWith("latest.");
    }
}
