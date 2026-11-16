package io.github.einsnils.deplens.security;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.Nullable;

/** Prüft Artefakte gegen OSV, Maven Central und optional OSS Index. Blockiert – nur im Hintergrund aufrufen. */
public final class SecurityChecker {

    public enum Notice { OSS_INDEX_UNAUTHORIZED, OSS_INDEX_FAILED }

    public interface Listener {
        void progress(int done, int total);

        void reportReady(Artifact artifact, SecurityReport report);

        void notice(Notice notice);
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    static final Duration RETRY_DELAY = Duration.ofSeconds(2);
    private static final int OSS_INDEX_BATCH_SIZE = 128;
    private static final int MAX_OSV_PAGES = 50;
    private static final String NOT_FOUND = "";

    private record PackageData(List<Vulnerability> osv, @Nullable List<MavenVersion> versions, @Nullable String error) {
    }

    private record Evaluation(MavenVersion current, PackageData data, List<Finding> osvFindings,
                              List<MavenVersion> candidates) {
    }

    @FunctionalInterface
    private interface Load {
        String get() throws IOException;
    }

    @FunctionalInterface
    private interface Parse<T> {
        T apply(String raw) throws ResponseFormatException;
    }

    private final SecurityClient client;
    private final ResponseCache cache;
    private final Duration maxAge;
    private final Executor executor;
    private final Sleeper sleeper;

    public SecurityChecker(SecurityClient client, ResponseCache cache, Duration maxAge, Executor executor,
                           Sleeper sleeper) {
        this.client = client;
        this.cache = cache;
        this.maxAge = maxAge;
        this.executor = executor;
        this.sleeper = sleeper;
    }

    public void check(List<Artifact> artifacts, boolean forceRefresh, boolean ossIndexEnabled,
                      BooleanSupplier cancelled, Listener listener) {
        Map<String, PackageData> packages = loadPackages(artifacts, forceRefresh, cancelled, listener);
        if (packages == null || cancelled.getAsBoolean()) {
            return;
        }
        Map<Artifact, Evaluation> evaluations = new LinkedHashMap<>();
        for (Artifact artifact : artifacts) {
            evaluations.put(artifact, evaluate(artifact, packages.get(artifact.packageName())));
        }
        Map<String, List<Vulnerability>> ossIndex = Map.of();
        if (ossIndexEnabled) {
            try {
                ossIndex = loadOssIndex(evaluations, forceRefresh, cancelled);
            } catch (HttpStatusException e) {
                boolean unauthorized = e.statusCode() == 401 || e.statusCode() == 403;
                listener.notice(unauthorized ? Notice.OSS_INDEX_UNAUTHORIZED : Notice.OSS_INDEX_FAILED);
            } catch (IOException e) {
                listener.notice(Notice.OSS_INDEX_FAILED);
            } catch (CancellationException e) {
                return;
            }
        }
        for (Map.Entry<Artifact, Evaluation> entry : evaluations.entrySet()) {
            if (cancelled.getAsBoolean()) {
                return;
            }
            listener.reportReady(entry.getKey(), report(entry.getKey(), entry.getValue(), ossIndex));
        }
    }

    /** Lädt OSV-Lücken und Versionen je Paket parallel; {@code null} bei Abbruch. */
    private @Nullable Map<String, PackageData> loadPackages(List<Artifact> artifacts, boolean forceRefresh,
                                                            BooleanSupplier cancelled, Listener listener) {
        List<String> packageNames = artifacts.stream().map(Artifact::packageName).distinct().toList();
        AtomicInteger done = new AtomicInteger();
        Map<String, CompletableFuture<PackageData>> futures = new LinkedHashMap<>();
        for (String packageName : packageNames) {
            futures.put(packageName, CompletableFuture
                    .supplyAsync(() -> loadPackage(packageName, forceRefresh, cancelled), executor)
                    .whenComplete((data, error) -> {
                        if (error == null) {
                            listener.progress(done.incrementAndGet(), packageNames.size());
                        }
                    }));
        }
        Map<String, PackageData> result = new HashMap<>();
        try {
            for (Map.Entry<String, CompletableFuture<PackageData>> future : futures.entrySet()) {
                result.put(future.getKey(), future.getValue().join());
            }
        } catch (CancellationException e) {
            return null;
        } catch (CompletionException e) {
            if (e.getCause() instanceof CancellationException) {
                return null;
            }
            throw e;
        }
        return result;
    }

    private PackageData loadPackage(String packageName, boolean forceRefresh, BooleanSupplier cancelled) {
        String[] parts = packageName.split(":", 2);
        try {
            List<Vulnerability> osv = new ArrayList<>();
            String token = null;
            for (int page = 0; page < MAX_OSV_PAGES; page++) {
                String pageToken = token;
                OsvParser.Page result = cached("osv/" + packageName + "/" + (pageToken == null ? "" : pageToken),
                        forceRefresh, cancelled, () -> client.osvQuery(packageName, pageToken),
                        raw -> OsvParser.parse(raw, packageName));
                osv.addAll(result.vulnerabilities());
                token = result.nextPageToken();
                if (token == null) {
                    break;
                }
            }
            List<MavenVersion> versions = cached("metadata/" + packageName, forceRefresh, cancelled,
                    () -> Objects.requireNonNullElse(client.mavenMetadata(parts[0], parts[1]), NOT_FOUND),
                    raw -> raw.equals(NOT_FOUND) ? null : MavenMetadataParser.parse(raw));
            return new PackageData(osv, versions, null);
        } catch (IOException e) {
            return new PackageData(List.of(), null, describe(e));
        }
    }

    private static Evaluation evaluate(Artifact artifact, PackageData data) {
        MavenVersion current = MavenVersion.of(artifact.version());
        List<Finding> findings = data.osv().stream()
                .filter(vulnerability -> VulnerabilityMatcher.affects(vulnerability, current))
                .map(vulnerability -> new Finding(vulnerability, VulnerabilityMatcher.fixedIn(vulnerability, current)))
                .toList();
        List<MavenVersion> candidates = data.versions() == null
                ? List.of()
                : UpgradeAdvisor.candidates(current, data.versions(), data.osv());
        return new Evaluation(current, data, findings, candidates);
    }

    /** Erst die aktuellen Versionen, dann die Gegenprüfung der Kandidaten verwundbarer Artefakte. */
    private Map<String, List<Vulnerability>> loadOssIndex(Map<Artifact, Evaluation> evaluations, boolean forceRefresh,
                                                          BooleanSupplier cancelled) throws IOException {
        List<String> current = new ArrayList<>();
        evaluations.forEach((artifact, evaluation) -> {
            if (evaluation.data().error() == null) {
                current.add(ossCoordinate(artifact, artifact.version()));
            }
        });
        Map<String, List<Vulnerability>> result = new HashMap<>(ossIndexReports(current, forceRefresh, cancelled));
        List<String> toVerify = new ArrayList<>();
        evaluations.forEach((artifact, evaluation) -> {
            boolean vulnerable = !evaluation.osvFindings().isEmpty()
                    || !result.getOrDefault(ossCoordinate(artifact, artifact.version()), List.of()).isEmpty();
            if (vulnerable && evaluation.data().versions() != null) {
                for (MavenVersion candidate : UpgradeAdvisor.toVerify(evaluation.current(), evaluation.candidates())) {
                    toVerify.add(ossCoordinate(artifact, candidate.toString()));
                }
            }
        });
        result.putAll(ossIndexReports(toVerify, forceRefresh, cancelled));
        return result;
    }

    private Map<String, List<Vulnerability>> ossIndexReports(List<String> coordinates, boolean forceRefresh,
                                                             BooleanSupplier cancelled) throws IOException {
        Map<String, List<Vulnerability>> result = new HashMap<>();
        List<String> sorted = coordinates.stream().distinct().sorted().toList();
        for (int from = 0; from < sorted.size(); from += OSS_INDEX_BATCH_SIZE) {
            List<String> batch = sorted.subList(from, Math.min(from + OSS_INDEX_BATCH_SIZE, sorted.size()));
            result.putAll(cached("oss/" + String.join(",", batch), forceRefresh, cancelled,
                    () -> client.ossIndexReport(batch), OssIndexParser::parse));
        }
        return result;
    }

    private static SecurityReport report(Artifact artifact, Evaluation evaluation,
                                         Map<String, List<Vulnerability>> ossIndex) {
        PackageData data = evaluation.data();
        if (data.error() != null) {
            return SecurityReport.error(data.error());
        }
        List<Finding> findings = merge(evaluation.osvFindings(),
                ossIndex.getOrDefault(ossCoordinate(artifact, artifact.version()), List.of()));
        if (data.versions() == null && findings.isEmpty()) {
            return SecurityReport.notCheckable("Nicht auf Maven Central gefunden");
        }
        if (findings.isEmpty()) {
            return SecurityReport.safe();
        }
        UpgradeAdvisor.Upgrade upgrade = data.versions() == null
                ? UpgradeAdvisor.Upgrade.NONE
                : UpgradeAdvisor.choose(evaluation.current(), evaluation.candidates(),
                        candidate -> !ossIndex.getOrDefault(ossCoordinate(artifact, candidate.toString()), List.of())
                                .isEmpty());
        return SecurityReport.vulnerable(findings, upgrade);
    }

    /** OSV-Funde plus OSS-Index-Funde, deren IDs/Aliase noch nicht vorkommen; sortiert nach Schwere, dann ID. */
    private static List<Finding> merge(List<Finding> osv, List<Vulnerability> ossIndex) {
        List<Finding> result = new ArrayList<>(osv);
        Set<String> known = new HashSet<>();
        osv.forEach(finding -> known.addAll(finding.vulnerability().allIds()));
        for (Vulnerability vulnerability : ossIndex) {
            if (Collections.disjoint(known, vulnerability.allIds())) {
                result.add(new Finding(vulnerability, null));
                known.addAll(vulnerability.allIds());
            }
        }
        result.sort(Comparator.comparing((Finding finding) -> finding.vulnerability().severity())
                .thenComparing(finding -> finding.vulnerability().displayId()));
        return result;
    }

    private static String ossCoordinate(Artifact artifact, String version) {
        return OssIndexParser.coordinate(artifact.groupId(), artifact.artifactId(), version);
    }

    /** Cache lesen (kaputte Einträge ignorieren), sonst laden, parsen und erst danach speichern. */
    private <T> T cached(String key, boolean forceRefresh, BooleanSupplier cancelled, Load load, Parse<T> parse)
            throws IOException {
        if (!forceRefresh) {
            String hit = cache.get(key, maxAge);
            if (hit != null) {
                try {
                    return parse.apply(hit);
                } catch (ResponseFormatException ignored) {
                    // kaputter Eintrag: neu laden
                }
            }
        }
        if (cancelled.getAsBoolean()) {
            throw new CancellationException();
        }
        String raw = withRetry(load);
        T parsed = parse.apply(raw);
        cache.put(key, raw);
        return parsed;
    }

    private String withRetry(Load load) throws IOException {
        try {
            return load.get();
        } catch (HttpStatusException e) {
            if (e.statusCode() != 429) {
                throw e;
            }
            try {
                sleeper.sleep(RETRY_DELAY);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Unterbrochen", interrupted);
            }
            return load.get();
        }
    }

    private static String describe(IOException e) {
        if (e instanceof HttpStatusException status) {
            return "HTTP " + status.statusCode();
        }
        return Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName());
    }
}
