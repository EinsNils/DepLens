package io.github.einsnils.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MavenMetadataParserTest {

    @Test
    void readsAllVersionsInOrder() throws Exception {
        assertThat(MavenMetadataParser.parse(Fixtures.read("maven-metadata-jackson-databind.xml")))
                .extracting(MavenVersion::toString)
                .containsExactly("2.12.7", "2.12.7.1", "2.13.0", "2.13.4", "2.13.4.2", "2.13.5",
                        "2.14.0-rc1", "2.14.0", "2.14.1", "2.15.0", "3.0.0-rc1");
    }

    @Test
    void ignoresVersionElementsOutsideVersions() throws Exception {
        String xml = "<metadata><version>9.9</version><versioning><versions>"
                + "<version>1.0</version></versions></versioning></metadata>";

        assertThat(MavenMetadataParser.parse(xml)).extracting(MavenVersion::toString).containsExactly("1.0");
    }

    @Test
    void invalidXmlAndDoctypesAreRejected() {
        assertThatThrownBy(() -> MavenMetadataParser.parse("<kaputt")).isInstanceOf(ResponseFormatException.class);
        assertThatThrownBy(() -> MavenMetadataParser.parse(
                "<!DOCTYPE metadata [<!ENTITY x \"y\">]><metadata/>")).isInstanceOf(ResponseFormatException.class);
    }
}
