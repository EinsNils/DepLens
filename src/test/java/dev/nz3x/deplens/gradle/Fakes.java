package dev.nz3x.deplens.gradle;

import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency;
import com.intellij.openapi.util.UserDataHolderBase;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Minimal-Implementierungen der Analyzer-Interfaces für Tests. */
final class Fakes {
    private Fakes() {
    }

    static final class FakeScope extends UserDataHolderBase implements DependencyAnalyzerDependency.Scope {
        private final String name;

        FakeScope(String name) { this.name = name; }

        @Override public @NotNull String getName() { return name; }
        @Override public @NotNull String getTitle() { return name; }
        @Override public @NotNull DependencyAnalyzerDependency.Scope.Type getType() {
            return DependencyAnalyzerDependency.Scope.Type.STANDARD;
        }
    }

    static final class FakeArtifact extends UserDataHolderBase implements DependencyAnalyzerDependency.Data.Artifact {
        private final String groupId;
        private final String artifactId;
        private final String version;

        FakeArtifact(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }

        @Override public @NotNull String getGroupId() { return groupId; }
        @Override public @NotNull String getArtifactId() { return artifactId; }
        @Override public @NotNull String getVersion() { return version; }
    }

    static final class FakeModule extends UserDataHolderBase implements DependencyAnalyzerDependency.Data.Module {
        private final String name;

        FakeModule(String name) { this.name = name; }

        @Override public @NotNull String getName() { return name; }
    }

    static final class FakeOmitted extends UserDataHolderBase implements DependencyAnalyzerDependency.Status.Omitted {
        @Override public @NotNull String getTitle() { return "Omitted"; }
    }

    static final class FakeWarning extends UserDataHolderBase implements DependencyAnalyzerDependency.Status.Warning {
        private final String message;

        FakeWarning(String message) { this.message = message; }

        @Override public @NotNull String getTitle() { return message; }
        @Override public @NotNull String getMessage() { return message; }
    }

    static final class FakeDep extends UserDataHolderBase implements DependencyAnalyzerDependency {
        private final Data data;
        private final Scope scope;
        private final @Nullable FakeDep parent;
        private final List<Status> status;

        FakeDep(Data data, Scope scope, @Nullable FakeDep parent, Status... status) {
            this.data = data;
            this.scope = scope;
            this.parent = parent;
            this.status = List.of(status);
        }

        @Override public @NotNull Data getData() { return data; }
        @Override public @NotNull Scope getScope() { return scope; }
        @Override public @Nullable DependencyAnalyzerDependency getParent() { return parent; }
        @Override public @NotNull List<Status> getStatus() { return status; }
    }
}
