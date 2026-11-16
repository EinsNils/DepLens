package io.github.einsnils.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MavenVersionTest {

    @Test
    void sortsLikeMaven() {
        List<MavenVersion> sorted = Stream.of("2.13.5", "2.13.4.2", "2.0.0", "2.0.0-RC1", "2.13.4")
                .map(MavenVersion::of)
                .sorted()
                .toList();

        assertThat(sorted).extracting(MavenVersion::toString)
                .containsExactly("2.0.0-RC1", "2.0.0", "2.13.4", "2.13.4.2", "2.13.5");
    }

    @Test
    void recognizesPreReleases() {
        assertThat(Stream.of("2.0.0-RC1", "6.0.0-M2", "5.0.0.Beta1", "1.0-SNAPSHOT", "3.0.0-alpha-1",
                        "21-ea", "1.0.0-cr2", "2.0-preview", "1.0-dev", "2.4.0-rc1")
                .map(MavenVersion::of))
                .allMatch(MavenVersion::isPreRelease);
    }

    @Test
    void releaseQualifiersAreNoPreRelease() {
        assertThat(Stream.of("2.13.4.2", "1.0.0.RELEASE", "33.4.8-jre", "5.13.4", "1.0.0.Final")
                .map(MavenVersion::of))
                .noneMatch(MavenVersion::isPreRelease);
    }

    @Test
    void majorIsFirstNumericSegment() {
        assertThat(MavenVersion.of("2.13.4").major()).isEqualTo(2);
        assertThat(MavenVersion.of("33.4.8-jre").major()).isEqualTo(33);
        assertThat(MavenVersion.of("RELEASE").major()).isEqualTo(-1);
    }

    @Test
    void equalityFollowsMavenCanonicalForm() {
        MavenVersion shortForm = MavenVersion.of("1.0");
        MavenVersion longForm = MavenVersion.of("1.0.0");

        assertThat(shortForm).isEqualTo(longForm).hasSameHashCodeAs(longForm);
        assertThat(shortForm.compareTo(longForm)).isZero();
        assertThat(MavenVersion.of(" 2.13.0 ")).hasToString("2.13.0");
    }
}
