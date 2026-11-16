package dev.nz3x.deplens.security;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Cache ohne Ablauf für Tests. */
final class InMemoryCache implements ResponseCache {
    final Map<String, String> values = new HashMap<>();

    @Override
    public @Nullable String get(String key, Duration maxAge) {
        return values.get(key);
    }

    @Override
    public void put(String key, String value) {
        values.put(key, value);
    }
}
