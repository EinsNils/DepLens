package io.github.einsnils.deplens.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Antwortet aus Maps und protokolliert jeden Aufruf. */
final class FakeSecurityClient implements SecurityClient {
    /** Schlüssel: {@code packageName + "|" + pageToken} (leerer Token für die erste Seite). */
    final Map<String, String> osv = new HashMap<>();
    /** Schlüssel: {@code packageName}; fehlender Eintrag = 404. */
    final Map<String, String> metadata = new HashMap<>();
    /** Schlüssel: {@code packageName}; wird von {@link #osvQuery} geworfen. */
    final Map<String, IOException> failures = new HashMap<>();
    final List<String> calls = new ArrayList<>();
    String ossIndex = "[]";
    @Nullable IOException ossIndexFailure;
    int tooManyRequests;

    @Override
    public String osvQuery(String packageName, @Nullable String pageToken) throws IOException {
        String token = pageToken == null ? "" : pageToken;
        calls.add("osv " + packageName + " " + token);
        IOException failure = failures.get(packageName);
        if (failure != null) {
            throw failure;
        }
        if (tooManyRequests > 0) {
            tooManyRequests--;
            throw new HttpStatusException(429, "osv");
        }
        return osv.getOrDefault(packageName + "|" + token, "{}");
    }

    @Override
    public @Nullable String mavenMetadata(String groupId, String artifactId) {
        calls.add("metadata " + groupId + ":" + artifactId);
        return metadata.get(groupId + ":" + artifactId);
    }

    @Override
    public String ossIndexReport(List<String> coordinates) throws IOException {
        calls.add("oss " + coordinates.size());
        if (ossIndexFailure != null) {
            throw ossIndexFailure;
        }
        return ossIndex;
    }
}
