package dev.nz3x.deplens.security;

/** Ein zu prüfendes Artefakt. */
public record Artifact(String groupId, String artifactId, String version) {

    public String coordinates() {
        return groupId + ":" + artifactId + ":" + version;
    }

    public String packageName() {
        return groupId + ":" + artifactId;
    }
}
