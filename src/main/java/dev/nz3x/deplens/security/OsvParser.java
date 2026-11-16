package dev.nz3x.deplens.security;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Liest Antworten von {@code POST https://api.osv.dev/v1/query}. */
public final class OsvParser {
    private static final String VULNERABILITY_URL = "https://osv.dev/vulnerability/";

    public record Page(List<Vulnerability> vulnerabilities, @Nullable String nextPageToken) {
        public Page {
            vulnerabilities = List.copyOf(vulnerabilities);
        }
    }

    private OsvParser() {
    }

    public static Page parse(String json, String packageName) throws ResponseFormatException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            List<Vulnerability> vulnerabilities = new ArrayList<>();
            for (JsonElement element : Json.array(root, "vulns")) {
                vulnerabilities.add(vulnerability(element.getAsJsonObject(), packageName));
            }
            String token = Json.string(root, "next_page_token");
            return new Page(vulnerabilities, token == null || token.isEmpty() ? null : token);
        } catch (RuntimeException e) {
            throw new ResponseFormatException("Ungültige OSV-Antwort", e);
        }
    }

    private static Vulnerability vulnerability(JsonObject json, String packageName) {
        String id = Json.requiredString(json, "id");
        JsonObject databaseSpecific = Json.object(json, "database_specific");
        Severity severity = Severity.fromLabel(databaseSpecific == null ? null : Json.string(databaseSpecific, "severity"));
        List<AffectedRange> ranges = new ArrayList<>();
        Set<String> versions = new LinkedHashSet<>();
        for (JsonElement element : Json.array(json, "affected")) {
            JsonObject affected = element.getAsJsonObject();
            JsonObject pkg = Json.object(affected, "package");
            if (pkg == null || !packageName.equals(Json.string(pkg, "name"))
                    || !"Maven".equals(Json.string(pkg, "ecosystem"))) {
                continue;
            }
            versions.addAll(Json.strings(Json.array(affected, "versions")));
            for (JsonElement rangeElement : Json.array(affected, "ranges")) {
                JsonObject range = rangeElement.getAsJsonObject();
                if ("ECOSYSTEM".equals(Json.string(range, "type"))) {
                    ranges.add(range(range));
                }
            }
        }
        String summary = Objects.requireNonNullElse(Json.string(json, "summary"), "");
        return new Vulnerability(id, Json.strings(Json.array(json, "aliases")), severity, summary,
                VULNERABILITY_URL + id, ranges, versions);
    }

    private static AffectedRange range(JsonObject range) {
        List<AffectedRange.Event> events = new ArrayList<>();
        for (JsonElement element : Json.array(range, "events")) {
            JsonObject event = element.getAsJsonObject();
            if (event.has("introduced")) {
                events.add(new AffectedRange.Event(AffectedRange.Kind.INTRODUCED, Json.requiredString(event, "introduced")));
            } else if (event.has("fixed")) {
                events.add(new AffectedRange.Event(AffectedRange.Kind.FIXED, Json.requiredString(event, "fixed")));
            } else if (event.has("last_affected")) {
                events.add(new AffectedRange.Event(AffectedRange.Kind.LAST_AFFECTED,
                        Json.requiredString(event, "last_affected")));
            }
        }
        return new AffectedRange(events);
    }
}
