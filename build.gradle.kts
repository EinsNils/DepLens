plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "dev.nz3x"
version = "0.1.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.2.3")
        bundledPlugin("com.intellij.gradle")
    }

    // Maven-Versionsvergleich; nur ComparableVersion wird gebraucht
    implementation("org.apache.maven:maven-artifact:3.10.1") {
        isTransitive = false
    }

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
    // IntelliJ-Plattform erwartet JUnit 4 auf dem Test-Runtime-Classpath
    testRuntimeOnly("junit:junit:4.13.2")
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = provider { null }
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}
