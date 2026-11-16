package dev.nz3x.deplens.security;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Liest Antworten von {@code POST https://ossindex.sonatype.org/api/v3/component-report}. */
public final class OssIndexParser {
    private static final String VULNERABILITY_URL = "https://ossindex.sonatype.org/vulnerability/";

    private OssIndexParser() {
    }

    /** Coordinate im OSS-Index-Format, kleingeschrieben. */
    public static String coordinate(String groupId, String artifactId, String version) {
        return ("pkg:maven/" + groupId + "/" + artifactId + "@" + version).toLowerCase(Locale.ROOT);
    }

    /** Kleingeschriebene Coordinate → Lücken dieser Version. */
    public static Map<String, List<Vulnerability>> parse(String json) throws ResponseFormatException {
        try {
            Map<String, List<Vulnerability>> result = new LinkedHashMap<>();
            for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
                JsonObject report = element.getAsJsonObject();
                String coordinates = Json.requiredString(report, "coordinates").toLowerCase(Locale.ROOT);
                List<Vulnerability> vulnerabilities = new ArrayList<>();
                for (JsonElement vulnerability : Json.array(report, "vulnerabilities")) {
                    vulnerabilities.add(vulnerability(vulnerability.getAsJsonObject()));
                }
                result.put(coordinates, vulnerabilities);
            }
            return result;
        } catch (RuntimeException e) {
            throw new ResponseFormatException("Ungültige OSS-Index-Antwort", e);
        }
    }

    private static Vulnerability vulnerability(JsonObject json) {
        String id = Json.requiredString(json, "id");
        String cve = Json.string(json, "cve");
        String primary = cve != null && !cve.isEmpty() ? cve : id;
        List<String> aliases = primary.equals(id) ? List.of() : List.of(id);
        String title = Objects.requireNonNullElse(Json.string(json, "title"), primary);
        String url = Objects.requireNonNullElse(Json.string(json, "reference"), VULNERABILITY_URL + id);
        return new Vulnerability(primary, aliases, Severity.fromCvssScore(Json.number(json, "cvssScore")), title, url,
                List.of(), Set.of());
    }
}
