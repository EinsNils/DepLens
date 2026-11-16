package dev.nz3x.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nz3x.deplens.security.SecurityChecker.Notice;
import dev.nz3x.deplens.security.SecurityReport.Status;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecurityCheckerTest {
    private static final String GROUP = "com.fasterxml.jackson.core";
    private static final String PKG = GROUP + ":jackson-databind";

    private final FakeSecurityClient client = new FakeSecurityClient();
    private final InMemoryCache cache = new InMemoryCache();
    private final List<Duration> sleeps = new ArrayList<>();
    private final SecurityChecker checker =
            new SecurityChecker(client, cache, Duration.ofHours(24), Runnable::run, sleeps::add);
    private final RecordingListener listener = new RecordingListener();

    @BeforeEach
    void jacksonResponses() {
        client.osv.put(PKG + "|", Fixtures.read("osv-jackson-databind.json"));
        client.osv.put(PKG + "|page-2", "{}");
        client.metadata.put(PKG, Fixtures.read("maven-metadata-jackson-databind.xml"));
    }

    @Test
    void vulnerableVersionGetsSortedFindingsAndBothUpgrades() {
        check(false, false, databind("2.13.0"));

        SecurityReport report = listener.report("2.13.0");
        assertThat(report.status()).isEqualTo(Status.VERWUNDBAR);
        assertThat(report.findings()).extracting(f -> f.vulnerability().displayId())
                .containsExactly("CVE-2022-42003", "CVE-2022-42004");
        assertThat(report.findings()).extracting(f -> String.valueOf(f.fixedIn()))
                .containsExactly("2.13.4.2", "2.13.4");
        assertThat(report.upgrade().sameMajor()).hasToString("2.13.4.2");
        assertThat(report.upgrade().latest()).hasToString("2.15.0");
        assertThat(report.problem()).isNull();
    }

    @Test
    void safeVersion() {
        check(false, false, databind("2.15.0"));

        SecurityReport report = listener.report("2.15.0");
        assertThat(report.status()).isEqualTo(Status.SICHER);
        assertThat(report.findings()).isEmpty();
    }

    @Test
    void versionsOfOnePackageShareTheRequests() {
        check(false, false, databind("2.13.0"), databind("2.15.0"));

        assertThat(client.calls).containsExactly("osv " + PKG + " ", "osv " + PKG + " page-2", "metadata " + PKG);
        assertThat(listener.reports).hasSize(2);
        assertThat(listener.progress).containsExactly("1/1");
    }

    @Test
    void laterPagesAreFollowed() {
        client.osv.put(PKG + "|page-2", "{\"vulns\":[{\"id\":\"GHSA-page-two\",\"affected\":[{\"package\":"
                + "{\"ecosystem\":\"Maven\",\"name\":\"" + PKG + "\"},\"versions\":[\"2.15.0\"]}]}]}");

        check(false, false, databind("2.15.0"));

        assertThat(listener.report("2.15.0").findings()).extracting(f -> f.vulnerability().id())
                .containsExactly("GHSA-page-two");
    }

    @Test
    void unknownToMavenCentralWithoutFindingsIsNotCheckable() {
        client.metadata.remove(PKG);

        check(false, false, databind("2.15.0"));

        SecurityReport report = listener.report("2.15.0");
        assertThat(report.status()).isEqualTo(Status.NICHT_PRUEFBAR);
        assertThat(report.problem()).isEqualTo("Nicht auf Maven Central gefunden");
    }

    @Test
    void unknownToMavenCentralButVulnerableHasNoUpgrade() {
        client.metadata.remove(PKG);

        check(false, false, databind("2.13.0"));

        SecurityReport report = listener.report("2.13.0");
        assertThat(report.status()).isEqualTo(Status.VERWUNDBAR);
        assertThat(report.upgrade().isEmpty()).isTrue();
    }

    @Test
    void secondCheckIsServedFromCache() {
        check(false, false, databind("2.13.0"));
        int calls = client.calls.size();

        check(false, false, databind("2.13.0"));

        assertThat(client.calls).hasSize(calls);
        assertThat(listener.reports).hasSize(1);
    }

    @Test
    void forceRefreshBypassesTheCache() {
        check(false, false, databind("2.13.0"));
        int calls = client.calls.size();

        check(true, false, databind("2.13.0"));

        assertThat(client.calls).hasSize(2 * calls);
    }

    @Test
    void brokenCacheEntryIsReloaded() {
        cache.values.put("metadata/" + PKG, "<kaputt");

        check(false, false, databind("2.13.0"));

        assertThat(client.calls).contains("metadata " + PKG);
        assertThat(listener.report("2.13.0").upgrade().latest()).hasToString("2.15.0");
    }

    @Test
    void failedPackageIsAnErrorOthersContinue() {
        client.failures.put(PKG, new IOException("Netz weg"));
        client.metadata.put("org.slf4j:slf4j-api",
                "<metadata><versioning><versions><version>2.0.17</version></versions></versioning></metadata>");

        check(false, false, databind("2.13.0"), new Artifact("org.slf4j", "slf4j-api", "2.0.17"));

        assertThat(listener.report("2.13.0").status()).isEqualTo(Status.FEHLER);
        assertThat(listener.report("2.13.0").problem()).isEqualTo("Netz weg");
        assertThat(listener.reports.get("org.slf4j:slf4j-api:2.0.17").status()).isEqualTo(Status.SICHER);
    }

    @Test
    void tooManyRequestsIsRetriedOnceAfterTwoSeconds() {
        client.tooManyRequests = 1;

        check(false, false, databind("2.13.0"));

        assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
        assertThat(listener.report("2.13.0").status()).isEqualTo(Status.VERWUNDBAR);
    }

    @Test
    void tooManyRequestsTwiceIsAnError() {
        client.tooManyRequests = 2;

        check(false, false, databind("2.13.0"));

        assertThat(listener.report("2.13.0").status()).isEqualTo(Status.FEHLER);
        assertThat(listener.report("2.13.0").problem()).isEqualTo("HTTP 429");
    }

    @Test
    void invalidResponseIsAnErrorAndNotCached() {
        client.metadata.put(PKG, "<kaputt");

        check(false, false, databind("2.13.0"));

        assertThat(listener.report("2.13.0").status()).isEqualTo(Status.FEHLER);
        assertThat(cache.values).doesNotContainKey("metadata/" + PKG);
    }

    @Test
    void ossIndexFindingsAreMergedWithoutDuplicates() {
        client.ossIndex = Fixtures.read("ossindex-jackson-databind.json");

        check(false, true, databind("2.13.0"));

        assertThat(listener.report("2.13.0").findings()).extracting(f -> f.vulnerability().displayId())
                .containsExactly("sonatype-2024-0001", "CVE-2022-42003", "CVE-2022-42004");
    }

    @Test
    void ossIndexRejectsACandidate() {
        client.ossIndex = Fixtures.read("ossindex-jackson-databind.json");

        check(false, true, databind("2.13.0"));

        assertThat(listener.report("2.13.0").upgrade().sameMajor()).hasToString("2.13.5");
        assertThat(listener.report("2.13.0").upgrade().latest()).hasToString("2.15.0");
        assertThat(client.calls).contains("oss 1", "oss 3");
    }

    @Test
    void ossIndexUnauthorizedFallsBackToOsv() {
        client.ossIndexFailure = new HttpStatusException(401, "oss");

        check(false, true, databind("2.13.0"));

        assertThat(listener.notices).containsExactly(Notice.OSS_INDEX_UNAUTHORIZED);
        assertThat(listener.report("2.13.0").findings()).hasSize(2);
    }

    @Test
    void otherOssIndexFailureIsNoticed() {
        client.ossIndexFailure = new IOException("weg");

        check(false, true, databind("2.13.0"));

        assertThat(listener.notices).containsExactly(Notice.OSS_INDEX_FAILED);
        assertThat(listener.report("2.13.0").status()).isEqualTo(Status.VERWUNDBAR);
    }

    @Test
    void ossIndexIsNotAskedWhenDisabled() {
        check(false, false, databind("2.13.0"));

        assertThat(client.calls).noneMatch(call -> call.startsWith("oss"));
    }

    @Test
    void cancelledCheckReportsNothing() {
        checker.check(List.of(databind("2.13.0")), false, false, () -> true, listener);

        assertThat(listener.reports).isEmpty();
        assertThat(client.calls).isEmpty();
    }

    private void check(boolean forceRefresh, boolean ossIndex, Artifact... artifacts) {
        checker.check(List.of(artifacts), forceRefresh, ossIndex, () -> false, listener);
    }

    private static Artifact databind(String version) {
        return new Artifact(GROUP, "jackson-databind", version);
    }

    private static final class RecordingListener implements SecurityChecker.Listener {
        final Map<String, SecurityReport> reports = new HashMap<>();
        final List<String> progress = new ArrayList<>();
        final List<Notice> notices = new ArrayList<>();

        SecurityReport report(String version) {
            return reports.get(PKG + ":" + version);
        }

        @Override
        public void progress(int done, int total) {
            progress.add(done + "/" + total);
        }

        @Override
        public void reportReady(Artifact artifact, SecurityReport report) {
            reports.put(artifact.coordinates(), report);
        }

        @Override
        public void notice(Notice notice) {
            notices.add(notice);
        }
    }
}
