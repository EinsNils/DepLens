package io.github.einsnils.deplens.security;

import java.util.List;

/** Ein OSV-Versionsbereich vom Typ ECOSYSTEM als Folge von Ereignissen. */
public record AffectedRange(List<Event> events) {

    public enum Kind { INTRODUCED, FIXED, LAST_AFFECTED }

    public record Event(Kind kind, String version) {
    }

    public AffectedRange {
        events = List.copyOf(events);
    }
}
