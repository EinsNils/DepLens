package io.github.einsnils.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.einsnils.deplens.security.AffectedRange.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

class OsvParserTest {
    private static final String PACKAGE = "com.fasterxml.jackson.core:jackson-databind";

    @Test
    void parsesVulnerabilitiesAndPageToken() throws Exception {
        OsvParser.Page page = OsvParser.parse(Fixtures.read("osv-jackson-databind.json"), PACKAGE);

        assertThat(page.nextPageToken()).isEqualTo("page-2");
        assertThat(page.vulnerabilities()).extracting(Vulnerability::displayId)
                .containsExactly("CVE-2022-42003", "CVE-2022-42004", "GHSA-test-last-affected");
        Vulnerability first = page.vulnerabilities().getFirst();
        assertThat(first.id()).isEqualTo("GHSA-jjjh-jjxp-wpff");
        assertThat(first.severity()).isEqualTo(Severity.HOCH);
        assertThat(first.summary()).isEqualTo("Uncontrolled Resource Consumption in Jackson-databind");
        assertThat(first.url()).isEqualTo("https://osv.dev/vulnerability/GHSA-jjjh-jjxp-wpff");
        assertThat(first.ranges().getFirst().events()).hasSize(4);
        assertThat(first.ranges().getFirst().events().getFirst())
                .isEqualTo(new AffectedRange.Event(Kind.INTRODUCED, "2.4.0-rc1"));
    }

    @Test
    void keepsOnlyEcosystemRangesAndVersionsOfTheRequestedPackage() throws Exception {
        Vulnerability third = OsvParser.parse(Fixtures.read("osv-jackson-databind.json"), PACKAGE)
                .vulnerabilities().get(2);

        assertThat(third.severity()).isEqualTo(Severity.UNBEKANNT);
        assertThat(third.versions()).containsExactly("2.10.0");
        assertThat(third.ranges()).hasSize(1);
        assertThat(third.ranges().getFirst().events()).extracting(AffectedRange.Event::kind)
                .containsExactly(Kind.INTRODUCED, Kind.LAST_AFFECTED);
    }

    @Test
    void emptyResponseHasNoVulnerabilitiesAndNoToken() throws Exception {
        OsvParser.Page page = OsvParser.parse("{}", PACKAGE);

        assertThat(page.vulnerabilities()).isEqualTo(List.of());
        assertThat(page.nextPageToken()).isNull();
    }

    @Test
    void invalidResponsesAreRejected() {
        assertThatThrownBy(() -> OsvParser.parse("nope", PACKAGE)).isInstanceOf(ResponseFormatException.class);
        assertThatThrownBy(() -> OsvParser.parse("{\"vulns\":[{\"summary\":\"ohne id\"}]}", PACKAGE))
                .isInstanceOf(ResponseFormatException.class);
    }
}
