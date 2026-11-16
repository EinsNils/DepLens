package dev.nz3x.deplens.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/** Wählt sichere Upgrade-Versionen: kleinster Sprung in derselben Major und neueste. */
public final class UpgradeAdvisor {
    /** Höchstens so viele Kandidaten je Vorschlag werden gegengeprüft. */
    public static final int MAX_TRIES = 5;

    public record Upgrade(@Nullable MavenVersion sameMajor, @Nullable MavenVersion latest) {
        public static final Upgrade NONE = new Upgrade(null, null);

        public boolean isEmpty() {
            return sameMajor == null && latest == null;
        }
    }

    private UpgradeAdvisor() {
    }

    /** Stabile Versionen über {@code current}, die von keiner der Lücken betroffen sind, aufsteigend. */
    public static List<MavenVersion> candidates(MavenVersion current, List<MavenVersion> available,
                                                List<Vulnerability> vulnerabilities) {
        return available.stream()
                .filter(version -> !version.isPreRelease())
                .filter(version -> version.compareTo(current) > 0)
                .filter(version -> vulnerabilities.stream().noneMatch(v -> VulnerabilityMatcher.affects(v, version)))
                .distinct()
                .sorted()
                .toList();
    }

    /** Alle Kandidaten, die {@link #choose} höchstens ansieht – für eine gemeinsame Gegenprüfung. */
    public static List<MavenVersion> toVerify(MavenVersion current, List<MavenVersion> candidates) {
        Set<MavenVersion> result = new LinkedHashSet<>();
        candidates.stream().filter(version -> version.major() == current.major()).limit(MAX_TRIES).forEach(result::add);
        candidates.reversed().stream().limit(MAX_TRIES).forEach(result::add);
        return List.copyOf(result);
    }

    /** Erster nicht abgelehnter Kandidat je Vorschlag, jeweils innerhalb von {@link #MAX_TRIES} Versuchen. */
    public static Upgrade choose(MavenVersion current, List<MavenVersion> candidates, Predicate<MavenVersion> rejected) {
        MavenVersion sameMajor = candidates.stream()
                .filter(version -> version.major() == current.major())
                .limit(MAX_TRIES)
                .filter(rejected.negate())
                .findFirst()
                .orElse(null);
        MavenVersion latest = candidates.reversed().stream()
                .limit(MAX_TRIES)
                .filter(rejected.negate())
                .findFirst()
                .orElse(null);
        return new Upgrade(sameMajor, latest);
    }
}
