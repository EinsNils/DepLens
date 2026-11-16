package dev.nz3x.deplens.security;

import java.time.Duration;
import org.jetbrains.annotations.Nullable;

/** Zwischenspeicher für Rohantworten. */
public interface ResponseCache {

    /** Wert, falls vorhanden und nicht älter als {@code maxAge}; sonst {@code null}. */
    @Nullable String get(String key, Duration maxAge);

    void put(String key, String value);
}
