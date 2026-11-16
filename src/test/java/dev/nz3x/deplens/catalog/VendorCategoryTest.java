package dev.nz3x.deplens.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nz3x.deplens.model.DependencyNode;
import org.junit.jupiter.api.Test;

class VendorCategoryTest {

    @Test
    void knownVendorsAreMergedAcrossGroups() {
        assertThat(VendorCategory.ofGroup("org.springframework.boot")).isEqualTo("Spring");
        assertThat(VendorCategory.ofGroup("org.springframework")).isEqualTo("Spring");
        assertThat(VendorCategory.ofGroup("org.springdoc")).isEqualTo("Spring");
        assertThat(VendorCategory.ofGroup("io.spring.gradle")).isEqualTo("Spring");
        assertThat(VendorCategory.ofGroup("com.fasterxml.jackson.core")).isEqualTo("Jackson");
        assertThat(VendorCategory.ofGroup("com.fasterxml.jackson.datatype")).isEqualTo("Jackson");
        assertThat(VendorCategory.ofGroup("org.junit.jupiter")).isEqualTo("JUnit");
        assertThat(VendorCategory.ofGroup("junit")).isEqualTo("JUnit");
        assertThat(VendorCategory.ofGroup("org.opentest4j")).isEqualTo("JUnit");
        assertThat(VendorCategory.ofGroup("org.jetbrains.kotlin")).isEqualTo("Kotlin");
        assertThat(VendorCategory.ofGroup("org.jetbrains.kotlinx")).isEqualTo("Kotlin");
    }

    @Test
    void prefixMustEndAtASegmentBoundary() {
        assertThat(VendorCategory.ofGroup("org.springframeworkx")).isEqualTo("Springframeworkx");
    }

    @Test
    void otherGroupsUseTheSegmentAfterTheDomainPrefix() {
        assertThat(VendorCategory.ofGroup("org.slf4j")).isEqualTo("Slf4j");
        assertThat(VendorCategory.ofGroup("io.micrometer")).isEqualTo("Micrometer");
        assertThat(VendorCategory.ofGroup("com.google.guava")).isEqualTo("Google");
        assertThat(VendorCategory.ofGroup("org.apache.commons")).isEqualTo("Apache");
    }

    @Test
    void groupsWithoutDomainPrefixUseTheirFirstSegment() {
        assertThat(VendorCategory.ofGroup("jakarta.servlet")).isEqualTo("Jakarta");
        assertThat(VendorCategory.ofGroup("commons-io")).isEqualTo("Commons-io");
    }

    @Test
    void blankGroupFallsBackToOthers() {
        assertThat(VendorCategory.ofGroup("")).isEqualTo(VendorCategory.OTHERS);
        assertThat(VendorCategory.ofGroup("org")).isEqualTo("Org");
    }

    @Test
    void projectModulesHaveTheirOwnCategory() {
        DependencyNode root = DependencyNode.module(":app", "runtimeClasspath", null);
        DependencyNode core = DependencyNode.module(":core", "runtimeClasspath", root);
        DependencyNode web = DependencyNode.artifact("org.springframework.boot", "spring-boot-starter-web",
                "3.5.0", "runtimeClasspath", root);

        assertThat(VendorCategory.of(core)).isEqualTo(VendorCategory.PROJECT_MODULES);
        assertThat(VendorCategory.of(web)).isEqualTo("Spring");
    }
}
