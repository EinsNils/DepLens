package io.github.einsnils.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OssIndexParserTest {
    private static final String CURRENT = "pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.0";

    @Test
    void parsesFindingsPerCoordinate() throws Exception {
        Map<String, List<Vulnerability>> reports = OssIndexParser.parse(Fixtures.read("ossindex-jackson-databind.json"));

        assertThat(reports).containsOnlyKeys(CURRENT,
                "pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.4.2",
                "pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.5");
        List<Vulnerability> current = reports.get(CURRENT);
        assertThat(current).extracting(Vulnerability::id).containsExactly("CVE-2022-42003", "sonatype-2024-0001");
        assertThat(current.get(0).aliases()).isEmpty();
        assertThat(current.get(0).severity()).isEqualTo(Severity.HOCH);
        assertThat(current.get(0).summary()).isEqualTo("[CVE-2022-42003] CWE-502: Deserialization of Untrusted Data");
        assertThat(current.get(0).url()).isEqualTo("https://ossindex.sonatype.org/vulnerability/CVE-2022-42003");
        assertThat(current.get(1).severity()).isEqualTo(Severity.KRITISCH);
        assertThat(reports.get("pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.5")).isEmpty();
    }

    @Test
    void cveBecomesPrimaryIdAndOwnIdAnAlias() throws Exception {
        String json = "[{\"coordinates\":\"pkg:maven/a/b@1\",\"vulnerabilities\":"
                + "[{\"id\":\"abc-123\",\"cve\":\"CVE-2020-1\",\"cvssScore\":4.0}]}]";

        Vulnerability v = OssIndexParser.parse(json).get("pkg:maven/a/b@1").getFirst();

        assertThat(v.id()).isEqualTo("CVE-2020-1");
        assertThat(v.aliases()).containsExactly("abc-123");
        assertThat(v.url()).isEqualTo("https://ossindex.sonatype.org/vulnerability/abc-123");
    }

    @Test
    void coordinateIsLowerCasePackageUrl() {
        assertThat(OssIndexParser.coordinate("Com.Example", "My-Lib", "1.0.0-RC1"))
                .isEqualTo("pkg:maven/com.example/my-lib@1.0.0-rc1");
    }

    @Test
    void invalidResponsesAreRejected() {
        assertThatThrownBy(() -> OssIndexParser.parse("{}")).isInstanceOf(ResponseFormatException.class);
        assertThatThrownBy(() -> OssIndexParser.parse("[{\"vulnerabilities\":[]}]"))
                .isInstanceOf(ResponseFormatException.class);
    }
}
