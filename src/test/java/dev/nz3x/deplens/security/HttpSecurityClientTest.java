package dev.nz3x.deplens.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Statuscodes echter HTTP-Antworten werden auf {@link HttpStatusException} abgebildet. */
class HttpSecurityClientTest {
    private final AtomicInteger authenticatorCalls = new AtomicInteger();
    private HttpServer server;
    private HttpSecurityClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        respond("/osv-429", 429, "{}", false);
        respond("/osv-200", 200, "{\"vulns\":[]}", false);
        respond("/oss-401", 401, "{}", true);
        respond("/maven/org/missing/lib/maven-metadata.xml", 404, "", false);
        respond("/maven/org/broken/lib/maven-metadata.xml", 500, "", false);
        respond("/maven/org/secret/lib/maven-metadata.xml", 401, "", true);
        server.start();
        // Ein globaler Authenticator wie IntelliJs IdeaWideAuthenticator: darf nie gefragt werden.
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                authenticatorCalls.incrementAndGet();
                return new PasswordAuthentication("x", "y".toCharArray());
            }
        });
    }

    @AfterEach
    void stopServer() {
        Authenticator.setDefault(null);
        server.stop(0);
    }

    @Test
    void postErrorBecomesHttpStatusException() {
        client = client("/osv-429", "/oss-401");

        assertThatThrownBy(() -> client.osvQuery("g:a", null))
                .isInstanceOfSatisfying(HttpStatusException.class, e -> assertThat(e.statusCode()).isEqualTo(429));
    }

    @Test
    void unauthorizedPostDoesNotAskTheAuthenticator() {
        client = client("/osv-429", "/oss-401");

        assertThatThrownBy(() -> client.ossIndexReport(List.of("pkg:maven/g/a@1")))
                .isInstanceOfSatisfying(HttpStatusException.class, e -> assertThat(e.statusCode()).isEqualTo(401));
        assertThat(authenticatorCalls).hasValue(0);
    }

    @Test
    void successfulPostReturnsBody() throws IOException {
        client = client("/osv-200", "/oss-401");

        assertThat(client.osvQuery("g:a", null)).isEqualTo("{\"vulns\":[]}");
    }

    @Test
    void missingMetadataIsNull() throws IOException {
        client = client("/osv-200", "/oss-401");

        assertThat(client.mavenMetadata("org.missing", "lib")).isNull();
    }

    @Test
    void metadataServerErrorBecomesHttpStatusException() {
        client = client("/osv-200", "/oss-401");

        assertThatThrownBy(() -> client.mavenMetadata("org.broken", "lib"))
                .isInstanceOfSatisfying(HttpStatusException.class, e -> assertThat(e.statusCode()).isEqualTo(500));
    }

    @Test
    void unauthorizedGetDoesNotAskTheAuthenticator() {
        client = client("/osv-200", "/oss-401");

        assertThatThrownBy(() -> client.mavenMetadata("org.secret", "lib"))
                .isInstanceOfSatisfying(HttpStatusException.class, e -> assertThat(e.statusCode()).isEqualTo(401));
        assertThat(authenticatorCalls).hasValue(0);
    }

    private HttpSecurityClient client(String osvPath, String ossPath) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new HttpSecurityClient(base + osvPath, base + "/maven/", base + ossPath, "user", "token");
    }

    private void respond(String path, int status, String body, boolean basicChallenge) {
        server.createContext(path, exchange -> {
            exchange.getRequestBody().readAllBytes();
            if (basicChallenge) {
                exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"test\"");
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }
}
