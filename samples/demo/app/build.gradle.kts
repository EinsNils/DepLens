plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation("org.springframework.boot:spring-boot-starter-json:3.5.0")
    // ältere Version erzeugt einen Versionskonflikt
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.0")
    // nicht auflösbar, um die rote Markierung zu prüfen
    implementation("does.not:exist:1.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
}
