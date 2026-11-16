package dev.nz3x.deplens.model;

import java.util.List;

/**
 * Kleiner Graph für Tests.
 *
 * <pre>
 * :app (runtimeClasspath)
 *   spring-boot-starter-web
 *     spring-boot-starter-json
 *       jackson-databind 2.15.2  [Konflikt]
 *         jackson-core 2.15.2
 *   :core
 *     jackson-core 2.15.2  [(*) omitted]
 * :app (testRuntimeClasspath)
 *   jackson-databind 2.15.2
 * </pre>
 */
public final class TestGraphs {
    public static final String JACKSON = "com.fasterxml.jackson.core";
    public static final String BOOT = "org.springframework.boot";

    public final DependencyNode runtimeRoot;
    public final DependencyNode web;
    public final DependencyNode json;
    public final DependencyNode databindRuntime;
    public final DependencyNode jacksonCore;
    public final DependencyNode coreModule;
    public final DependencyNode omittedCore;
    public final DependencyNode testRoot;
    public final DependencyNode databindTest;
    public final DependencyGraph graph;

    public TestGraphs() {
        runtimeRoot = DependencyNode.module(":app", "runtimeClasspath", null);
        web = DependencyNode.artifact(BOOT, "spring-boot-starter-web", "3.5.0", "runtimeClasspath", runtimeRoot);
        json = DependencyNode.artifact(BOOT, "spring-boot-starter-json", "3.5.0", "runtimeClasspath", web);
        databindRuntime = DependencyNode.artifact(JACKSON, "jackson-databind", "2.15.2", "runtimeClasspath", json);
        databindRuntime.markConflict("conflict with 2.13.0");
        jacksonCore = DependencyNode.artifact(JACKSON, "jackson-core", "2.15.2", "runtimeClasspath", databindRuntime);
        coreModule = DependencyNode.module(":core", "runtimeClasspath", runtimeRoot);
        omittedCore = DependencyNode.artifact(JACKSON, "jackson-core", "2.15.2", "runtimeClasspath", coreModule);
        omittedCore.markOmitted();
        testRoot = DependencyNode.module(":app", "testRuntimeClasspath", null);
        databindTest = DependencyNode.artifact(JACKSON, "jackson-databind", "2.15.2", "testRuntimeClasspath", testRoot);
        graph = new DependencyGraph(":app", List.of(runtimeRoot, testRoot));
    }
}
