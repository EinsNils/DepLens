package dev.nz3x.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CheckableVersionTest {

    @Test
    void unresolvedIsNotCheckable() {
        assertThat(CheckableVersion.problem("1.0", true)).isEqualTo("Nicht auflösbar");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void blankVersionIsNotCheckable(String version) {
        assertThat(CheckableVersion.problem(version, false)).isEqualTo("Keine Version");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.+", "+", "[1.0,2.0)", "(,1.0]", "1.0,2.0", "latest.release", "latest.integration"})
    void dynamicOrBlankVersionsAreNotCheckable(String version) {
        assertThat(CheckableVersion.problem(version, false)).isEqualTo("Dynamische Version");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.13.0", "31.0-android", "5.2.0.Final", "1.0-SNAPSHOT", "20040616"})
    void concreteVersionsAreCheckable(String version) {
        assertThat(CheckableVersion.problem(version, false)).isNull();
    }
}
