package io.github.einsnils.deplens.security;

import java.io.IOException;
import org.jetbrains.annotations.Nullable;

/** Die Antwort eines Dienstes ließ sich nicht lesen; wird wie ein Netzfehler behandelt. */
public final class ResponseFormatException extends IOException {
    public ResponseFormatException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
