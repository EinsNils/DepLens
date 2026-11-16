package io.github.einsnils.deplens.security;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Links zum Nachschlagen im Browser. */
public final class SecurityLinks {
    private SecurityLinks() {
    }

    public static String mvnRepository(String groupId, String artifactId, String version) {
        return "https://mvnrepository.com/artifact/" + groupId + "/" + artifactId + "/" + version;
    }

    public static String osvPackage(String groupId, String artifactId) {
        return "https://osv.dev/list?ecosystem=Maven&q="
                + URLEncoder.encode(groupId + ":" + artifactId, StandardCharsets.UTF_8);
    }
}
