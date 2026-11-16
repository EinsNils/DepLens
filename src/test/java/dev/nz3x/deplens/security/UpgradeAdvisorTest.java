package dev.nz3x.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nz3x.deplens.security.AffectedRange.Event;
import dev.nz3x.deplens.security.AffectedRange.Kind;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class UpgradeAdvisorTest {
    private static final List<MavenVersion> AVAILABLE = versions("2.12.7", "2.12.7.1", "2.13.0", "2.13.4", "2.13.4.2",
            "2.13.5", "2.14.0-rc1", "2.14.0", "2.14.1", "2.15.0", "3.0.0-rc1");
    private static final List<Vulnerability> VULNERABILITIES = List.of(
            vuln(new Event(Kind.INTRODUCED, "2.13.0"), new Event(Kind.FIXED, "2.13.4.2")),
            vuln(new Event(Kind.INTRODUCED, "0"), new Event(Kind.FIXED, "2.12.7.1"),
                    new Event(Kind.INTRODUCED, "2.13.0"), new Event(Kind.FIXED, "2.13.4")),
            vuln(new Event(Kind.INTRODUCED, "2.14.0"), new Event(Kind.LAST_AFFECTED, "2.14.1")));
    private static final MavenVersion CURRENT = MavenVersion.of("2.13.0");

    @Test
    void candidatesAreNewerStableAndUnaffected() {
        assertThat(UpgradeAdvisor.candidates(CURRENT, AVAILABLE, VULNERABILITIES))
                .extracting(MavenVersion::toString)
                .containsExactly("2.13.4.2", "2.13.5", "2.15.0");
    }

    @Test
    void choosesSmallestSameMajorAndLatest() {
        UpgradeAdvisor.Upgrade upgrade = choose(v -> false);

        assertThat(upgrade.sameMajor()).hasToString("2.13.4.2");
        assertThat(upgrade.latest()).hasToString("2.15.0");
        assertThat(upgrade.isEmpty()).isFalse();
    }

    @Test
    void rejectedCandidatesMoveToTheNextOne() {
        UpgradeAdvisor.Upgrade upgrade = choose(v -> v.toString().equals("2.13.4.2") || v.toString().equals("2.15.0"));

        assertThat(upgrade.sameMajor()).hasToString("2.13.5");
        assertThat(upgrade.latest()).hasToString("2.13.5");
    }

    @Test
    void noSameMajorCandidate() {
        UpgradeAdvisor.Upgrade upgrade = UpgradeAdvisor.choose(MavenVersion.of("1.0.0"),
                UpgradeAdvisor.candidates(MavenVersion.of("1.0.0"), versions("1.0.0", "2.0.0"), List.of()), v -> false);

        assertThat(upgrade.sameMajor()).isNull();
        assertThat(upgrade.latest()).hasToString("2.0.0");
    }

    @Test
    void noSafeVersionAtAll() {
        List<Vulnerability> everything = List.of(vuln(new Event(Kind.INTRODUCED, "0")));

        assertThat(UpgradeAdvisor.candidates(CURRENT, AVAILABLE, everything)).isEmpty();
        assertThat(UpgradeAdvisor.choose(CURRENT, List.of(), v -> false)).isEqualTo(UpgradeAdvisor.Upgrade.NONE);
        assertThat(UpgradeAdvisor.Upgrade.NONE.isEmpty()).isTrue();
    }

    @Test
    void givesUpAfterMaxTries() {
        List<MavenVersion> candidates = versions("2.0.1", "2.0.2", "2.0.3", "2.0.4", "2.0.5", "2.0.6", "2.0.7");

        UpgradeAdvisor.Upgrade upgrade = UpgradeAdvisor.choose(MavenVersion.of("2.0.0"), candidates,
                v -> !v.toString().equals("2.0.6") && !v.toString().equals("2.0.2"));

        assertThat(upgrade.sameMajor()).hasToString("2.0.2");
        assertThat(upgrade.latest()).hasToString("2.0.6");
        assertThat(UpgradeAdvisor.choose(MavenVersion.of("2.0.0"), candidates, v -> !v.toString().equals("2.0.6")
                && !v.toString().equals("2.0.1")).sameMajor()).hasToString("2.0.1");
        assertThat(UpgradeAdvisor.choose(MavenVersion.of("2.0.0"), candidates, v -> !v.toString().equals("2.0.6"))
                .sameMajor()).isNull();
    }

    @Test
    void toVerifyListsSameMajorFirstThenNewest() {
        List<MavenVersion> candidates = UpgradeAdvisor.candidates(CURRENT, AVAILABLE, VULNERABILITIES);

        assertThat(UpgradeAdvisor.toVerify(CURRENT, candidates)).extracting(MavenVersion::toString)
                .containsExactly("2.13.4.2", "2.13.5", "2.15.0");
    }

    private static UpgradeAdvisor.Upgrade choose(java.util.function.Predicate<MavenVersion> rejected) {
        return UpgradeAdvisor.choose(CURRENT, UpgradeAdvisor.candidates(CURRENT, AVAILABLE, VULNERABILITIES), rejected);
    }

    private static List<MavenVersion> versions(String... versions) {
        return Stream.of(versions).map(MavenVersion::of).toList();
    }

    private static Vulnerability vuln(Event... events) {
        return new Vulnerability("GHSA-x", List.of(), Severity.HOCH, "s", "u",
                List.of(new AffectedRange(List.of(events))), Set.of());
    }
}
