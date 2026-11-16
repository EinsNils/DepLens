package dev.nz3x.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecurityLinksTest {

    @Test
    void links() {
        assertThat(SecurityLinks.mvnRepository("com.fasterxml.jackson.core", "jackson-databind", "2.13.0"))
                .isEqualTo("https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind/2.13.0");
        assertThat(SecurityLinks.osvPackage("com.fasterxml.jackson.core", "jackson-databind"))
                .isEqualTo("https://osv.dev/list?ecosystem=Maven&q=com.fasterxml.jackson.core%3Ajackson-databind");
    }
}
