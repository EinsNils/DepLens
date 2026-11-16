package dev.nz3x.deplens.security;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.util.io.HttpRequests;
import java.io.IOException;
import java.net.Authenticator;
import java.net.HttpRetryException;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Fragt die Dienste über IntelliJs {@link HttpRequests} ab (IDE-Proxy und Zertifikate gelten). */
public final class HttpSecurityClient implements SecurityClient {
    private static final String OSV_URL = "https://api.osv.dev/v1/query";
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";
    private static final String OSS_INDEX_URL = "https://ossindex.sonatype.org/api/v3/component-report";
    private static final int TIMEOUT_MS = 15_000;
    /** Liefert nie Zugangsdaten. */
    private static final Authenticator NO_PROMPT = new Authenticator() {
    };

    private final String osvUrl;
    private final String mavenCentral;
    private final String ossIndexUrl;
    private final @Nullable String ossIndexUser;
    private final @Nullable String ossIndexToken;

    public HttpSecurityClient(@Nullable String ossIndexUser, @Nullable String ossIndexToken) {
        this(OSV_URL, MAVEN_CENTRAL, OSS_INDEX_URL, ossIndexUser, ossIndexToken);
    }

    /** Für Tests gegen einen lokalen Server; {@code mavenCentral} endet mit {@code /}. */
    HttpSecurityClient(String osvUrl, String mavenCentral, String ossIndexUrl,
                       @Nullable String ossIndexUser, @Nullable String ossIndexToken) {
        this.osvUrl = osvUrl;
        this.mavenCentral = mavenCentral;
        this.ossIndexUrl = ossIndexUrl;
        this.ossIndexUser = ossIndexUser;
        this.ossIndexToken = ossIndexToken;
    }

    @Override
    public String osvQuery(String packageName, @Nullable String pageToken) throws IOException {
        JsonObject pkg = new JsonObject();
        pkg.addProperty("name", packageName);
        pkg.addProperty("ecosystem", "Maven");
        JsonObject body = new JsonObject();
        body.add("package", pkg);
        if (pageToken != null) {
            body.addProperty("page_token", pageToken);
        }
        return post(osvUrl, body.toString(), null);
    }

    @Override
    public @Nullable String mavenMetadata(String groupId, String artifactId) throws IOException {
        String url = mavenCentral + groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
        try {
            return HttpRequests.request(url)
                    .productNameAsUserAgent()
                    .connectTimeout(TIMEOUT_MS)
                    .readTimeout(TIMEOUT_MS)
                    .throwStatusCodeException(true)
                    .tuner(HttpSecurityClient::withoutAuthPrompt)
                    .readString();
        } catch (HttpRequests.HttpStatusException e) {
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw new HttpStatusException(e.getStatusCode(), url);
        }
    }

    @Override
    public String ossIndexReport(List<String> coordinates) throws IOException {
        JsonArray array = new JsonArray();
        coordinates.forEach(array::add);
        JsonObject body = new JsonObject();
        body.add("coordinates", array);
        String credentials = ossIndexUser + ":" + ossIndexToken;
        String authorization = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return post(ossIndexUrl, body.toString(), authorization);
    }

    private static String post(String url, String json, @Nullable String authorization) throws IOException {
        try {
            return HttpRequests.post(url, "application/json")
                    .productNameAsUserAgent()
                    .accept("application/json")
                    .connectTimeout(TIMEOUT_MS)
                    .readTimeout(TIMEOUT_MS)
                    .throwStatusCodeException(true)
                    .tuner(connection -> {
                        withoutAuthPrompt(connection);
                        if (authorization != null) {
                            connection.setRequestProperty("Authorization", authorization);
                        }
                    })
                    .connect(request -> {
                        request.write(json);
                        if (request.getConnection() instanceof HttpURLConnection connection) {
                            checkStatus(connection, url);
                        }
                        return request.readString();
                    });
        } catch (HttpRequests.HttpStatusException e) {
            throw new HttpStatusException(e.getStatusCode(), url);
        }
    }

    /**
     * Nach dem Senden eines Bodys prüft {@link HttpRequests} den Status erst nach dem Lesen; das Lesen wirft bei
     * Fehlercodes aber eine allgemeine {@link IOException}. Darum hier vorher prüfen.
     */
    static void checkStatus(HttpURLConnection connection, String url) throws IOException {
        int code;
        try {
            code = connection.getResponseCode();
        } catch (HttpRetryException e) {
            // 401 mit WWW-Authenticate im Streaming-Modus
            code = e.responseCode();
        }
        if (code >= 400) {
            throw new HttpStatusException(code, url);
        }
    }

    /** Verhindert, dass der globale Authenticator der IDE bei 401 einen Login-Dialog öffnet. */
    private static void withoutAuthPrompt(URLConnection connection) {
        if (connection instanceof HttpURLConnection http) {
            http.setAuthenticator(NO_PROMPT);
        }
    }
}
