package io.github.einsnils.deplens.security;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Ergebnis der Prüfung eines Artefakts. */
public record SecurityReport(Status status, List<Finding> findings, UpgradeAdvisor.Upgrade upgrade,
                             @Nullable String problem) {

    public enum Status { SICHER, VERWUNDBAR, NICHT_PRUEFBAR, FEHLER }

    public SecurityReport {
        findings = List.copyOf(findings);
    }

    public static SecurityReport safe() {
        return new SecurityReport(Status.SICHER, List.of(), UpgradeAdvisor.Upgrade.NONE, null);
    }

    public static SecurityReport vulnerable(List<Finding> findings, UpgradeAdvisor.Upgrade upgrade) {
        return new SecurityReport(Status.VERWUNDBAR, findings, upgrade, null);
    }

    public static SecurityReport notCheckable(String reason) {
        return new SecurityReport(Status.NICHT_PRUEFBAR, List.of(), UpgradeAdvisor.Upgrade.NONE, reason);
    }

    public static SecurityReport error(String reason) {
        return new SecurityReport(Status.FEHLER, List.of(), UpgradeAdvisor.Upgrade.NONE, reason);
    }
}
