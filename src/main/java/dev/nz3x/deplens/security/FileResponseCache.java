package dev.nz3x.deplens.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.jetbrains.annotations.Nullable;

/** Rohantworten als Dateien: erste Zeile Zeitstempel (Epoch-Millis), danach der Wert. Best effort. */
public final class FileResponseCache implements ResponseCache {
    private final Path directory;
    private final Clock clock;

    public FileResponseCache(Path directory, Clock clock) {
        this.directory = directory;
        this.clock = clock;
    }

    @Override
    public @Nullable String get(String key, Duration maxAge) {
        Path file = file(key);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            int newline = content.indexOf('\n');
            Instant stored = Instant.ofEpochMilli(Long.parseLong(content.substring(0, newline)));
            if (Duration.between(stored, clock.instant()).compareTo(maxAge) > 0) {
                return null;
            }
            return content.substring(newline + 1);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(file);
            return null;
        }
    }

    @Override
    public void put(String key, String value) {
        try {
            Files.createDirectories(directory);
            Path temp = Files.createTempFile(directory, "entry", ".tmp");
            Files.writeString(temp, clock.millis() + "\n" + value, StandardCharsets.UTF_8);
            Files.move(temp, file(key), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Cache ist optional
        }
    }

    private Path file(String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(hash) + ".txt");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // egal
        }
    }
}
