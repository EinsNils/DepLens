package dev.nz3x.deplens.security;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.util.io.HttpRequests;
import java.io.IOException;
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

    private final @Nullable String ossIndexUser;
    private final @Nullable String ossIndexToken;

    public HttpSecurityClient(@Nullable String ossIndexUser, @Nullable String ossIndexToken) {
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
        return post(OSV_URL, body.toString(), null);
    }

    @Override
    public @Nullable String mavenMetadata(String groupId, String artifactId) throws IOException {
        String url = MAVEN_CENTRAL + groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
        try {
            return HttpRequests.request(url)
                    .productNameAsUserAgent()
                    .connectTimeout(TIMEOUT_MS)
                    .readTimeout(TIMEOUT_MS)
                    .throwStatusCodeException(true)
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
        return post(OSS_INDEX_URL, body.toString(), authorization);
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
                        if (authorization != null) {
                            connection.setRequestProperty("Authorization", authorization);
                        }
                    })
                    .connect(request -> {
                        request.write(json);
                        return request.readString();
                    });
        } catch (HttpRequests.HttpStatusException e) {
            throw new HttpStatusException(e.getStatusCode(), url);
        }
    }
}
