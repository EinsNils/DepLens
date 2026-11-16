package io.github.einsnils.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileResponseCacheTest {
    private static final Duration DAY = Duration.ofHours(24);

    @TempDir
    Path dir;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-16T10:00:00Z"));

    @Test
    void storedValueIsReturnedWithinMaxAge() {
        FileResponseCache cache = new FileResponseCache(dir, clock);
        cache.put("osv/a:b/", "{\"vulns\":[]}");
        clock.advance(Duration.ofHours(23));

        assertThat(cache.get("osv/a:b/", DAY)).isEqualTo("{\"vulns\":[]}");
    }

    @Test
    void expiredValueIsIgnored() {
        FileResponseCache cache = new FileResponseCache(dir, clock);
        cache.put("osv/a:b/", "x");
        clock.advance(Duration.ofHours(25));

        assertThat(cache.get("osv/a:b/", DAY)).isNull();
    }

    @Test
    void missingKeyGivesNull() {
        assertThat(new FileResponseCache(dir, clock).get("nichts", DAY)).isNull();
    }

    @Test
    void corruptFileIsDiscarded() throws IOException {
        FileResponseCache cache = new FileResponseCache(dir, clock);
        cache.put("metadata/a:b", "<metadata/>");
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                Files.writeString(file, "kaputt");
            }
        }

        assertThat(cache.get("metadata/a:b", DAY)).isNull();
        try (Stream<Path> files = Files.list(dir)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void multiLineValuesAndEmptyValuesSurvive() {
        FileResponseCache cache = new FileResponseCache(dir, clock);
        cache.put("a", "zeile 1\nzeile 2\n");
        cache.put("b", "");

        assertThat(cache.get("a", DAY)).isEqualTo("zeile 1\nzeile 2\n");
        assertThat(cache.get("b", DAY)).isEmpty();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
