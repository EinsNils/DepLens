package io.github.einsnils.deplens.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Liest Beispielantworten aus {@code src/test/resources/security}. */
final class Fixtures {
    private Fixtures() {
    }

    static String read(String name) {
        try (InputStream in = Objects.requireNonNull(Fixtures.class.getResourceAsStream("/security/" + name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
