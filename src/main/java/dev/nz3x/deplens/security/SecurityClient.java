package dev.nz3x.deplens.security;

import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Rohzugriff auf OSV, Maven Central und OSS Index. */
public interface SecurityClient {

    /** Roh-JSON einer OSV-Anfrage für {@code group:artifact}; {@code pageToken} für Folgeseiten. */
    String osvQuery(String packageName, @Nullable String pageToken) throws IOException;

    /** Inhalt der {@code maven-metadata.xml} oder {@code null}, wenn Maven Central das Artefakt nicht kennt. */
    @Nullable String mavenMetadata(String groupId, String artifactId) throws IOException;

    /** Roh-JSON eines OSS-Index-Berichts für bis zu 128 Coordinates. */
    String ossIndexReport(List<String> coordinates) throws IOException;
}
