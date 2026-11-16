package io.github.einsnils.deplens.security;

import java.io.IOException;

/** Ein Dienst hat mit einem HTTP-Fehlerstatus geantwortet. */
public final class HttpStatusException extends IOException {
    private final int statusCode;

    public HttpStatusException(int statusCode, String url) {
        super("HTTP " + statusCode + " – " + url);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
