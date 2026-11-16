package dev.nz3x.deplens.security;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import dev.nz3x.deplens.settings.DepLensSettings;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;

/** Verbindet {@link SecurityChecker} mit Einstellungen, Cache und HTTP. */
@Service(Service.Level.PROJECT)
public final class SecurityService {
    private static final Logger LOG = Logger.getInstance(SecurityService.class);

    private final ExecutorService executor =
            AppExecutorUtil.createBoundedApplicationPoolExecutor("DepLens security", 8);
    private final ResponseCache cache =
            new FileResponseCache(PathManager.getSystemDir().resolve("deplens").resolve("cache"), Clock.systemUTC());
    private volatile boolean ossIndexRejected;

    public static SecurityService getInstance(Project project) {
        return project.getService(SecurityService.class);
    }

    /** Blockiert – nur im Hintergrund aufrufen. */
    public void check(List<Artifact> artifacts, boolean forceRefresh, BooleanSupplier cancelled,
                      SecurityChecker.Listener listener) {
        DepLensSettings settings = DepLensSettings.getInstance();
        String user = settings.ossIndexUser();
        String token = user.isEmpty() ? null : settings.ossIndexToken();
        boolean ossIndex = token != null && !token.isBlank() && !ossIndexRejected;
        SecurityChecker checker = new SecurityChecker(new HttpSecurityClient(user, token), cache,
                settings.cacheDuration(), executor, duration -> Thread.sleep(duration.toMillis()));
        Set<String> loggedProblems = ConcurrentHashMap.newKeySet();
        checker.check(artifacts, forceRefresh, ossIndex, cancelled, new SecurityChecker.Listener() {
            @Override
            public void progress(int done, int total) {
                listener.progress(done, total);
            }

            @Override
            public void reportReady(Artifact artifact, SecurityReport report) {
                if (report.status() == SecurityReport.Status.FEHLER && loggedProblems.add(report.problem())) {
                    LOG.warn("DepLens: Sicherheitsprüfung fehlgeschlagen: " + report.problem());
                }
                listener.reportReady(artifact, report);
            }

            @Override
            public void notice(SecurityChecker.Notice notice) {
                if (notice == SecurityChecker.Notice.OSS_INDEX_UNAUTHORIZED) {
                    ossIndexRejected = true;
                }
                LOG.info("DepLens: " + notice);
                listener.notice(notice);
            }
        });
    }

    public void resetOssIndexRejection() {
        ossIndexRejected = false;
    }
}
