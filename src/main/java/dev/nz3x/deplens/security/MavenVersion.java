package dev.nz3x.deplens.security;

import java.util.Locale;
import java.util.Set;
import org.apache.maven.artifact.versioning.ComparableVersion;

/** Eine Maven-Version mit Vergleich nach Maven-Regeln. */
public final class MavenVersion implements Comparable<MavenVersion> {
    private static final Set<String> PRE_RELEASE_QUALIFIERS = Set.of(
            "alpha", "beta", "milestone", "m", "rc", "cr", "snapshot", "preview", "ea", "dev");
    private static final Set<String> RELEASE_QUALIFIERS = Set.of("final", "release", "ga");

    private final String text;
    private final ComparableVersion comparable;

    private MavenVersion(String text) {
        this.text = text;
        this.comparable = new ComparableVersion(text);
    }

    public static MavenVersion of(String text) {
        return new MavenVersion(text.strip());
    }

    /** {@code true} für Vorabversionen wie {@code 2.0.0-RC1}, {@code 6.0.0-M2}, {@code 5.0.0.Beta1}. */
    public boolean isPreRelease() {
        for (String token : tokens()) {
            if (PRE_RELEASE_QUALIFIERS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /** Erstes numerisches Segment; {@code -1}, wenn die Version nicht mit einer Zahl beginnt. */
    public long major() {
        String first = tokens()[0];
        boolean numeric = !first.isEmpty() && first.length() <= 18 && first.chars().allMatch(Character::isDigit);
        return numeric ? Long.parseLong(first) : -1;
    }

    /**
     * Variante wie {@code jre} oder {@code android}: Buchstaben-Segmente nach dem letzten numerischen Segment,
     * ohne Release- und Vorab-Qualifier; {@code ""}, wenn es keine gibt.
     */
    public String variant() {
        String[] tokens = tokens();
        int end = tokens.length;
        while (end > 0 && !isNumeric(tokens[end - 1])) {
            end--;
        }
        StringBuilder variant = new StringBuilder();
        for (int i = end; i < tokens.length; i++) {
            String token = tokens[i];
            if (!token.isEmpty() && !RELEASE_QUALIFIERS.contains(token) && !PRE_RELEASE_QUALIFIERS.contains(token)) {
                variant.append(variant.isEmpty() ? "" : "-").append(token);
            }
        }
        return variant.toString();
    }

    private static boolean isNumeric(String token) {
        return !token.isEmpty() && token.chars().allMatch(Character::isDigit);
    }

    /** Zerlegt an {@code . - _ +} und an Wechseln zwischen Ziffern und Buchstaben. */
    private String[] tokens() {
        return text.toLowerCase(Locale.ROOT).split("[.\\-_+]|(?<=\\d)(?=\\D)|(?<=\\D)(?=\\d)");
    }

    @Override
    public int compareTo(MavenVersion other) {
        return comparable.compareTo(other.comparable);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MavenVersion version && comparable.equals(version.comparable);
    }

    @Override
    public int hashCode() {
        return comparable.hashCode();
    }

    @Override
    public String toString() {
        return text;
    }
}
