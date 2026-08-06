plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("kapt")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
    `java-library`
}

group = "com.umt"
version = "0.0.1-SNAPSHOT"

java { toolchain { languageVersion = JavaLanguageVersion.of(22) } }
repositories { mavenCentral() }

dependencies {
    implementation("org.mapstruct:mapstruct:1.6.3")
    kapt("org.mapstruct:mapstruct-processor:1.6.3")
    api("org.jetbrains.kotlin:kotlin-reflect")
    api("com.fasterxml.jackson.core:jackson-annotations:2.18.2")
    api("jakarta.validation:jakarta.validation-api:3.0.2")
    api("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.13")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// The Spring Boot BOM manages Kotlin itself (3.4.1 pins 1.9.25). The other modules never notice
// because the Spring Boot Gradle plugin realigns kotlin.version to the applied Kotlin plugin —
// here the plugin is `apply false`, so that never happens and kotlin-reflect would be dragged
// back to 1.9.25 while the compiler is 2.2.0. Override the BOM property explicitly.
extra["kotlin.version"] = property("kotlinVersion") as String

dependencyManagement {
    imports {
        // must match the version the consuming modules resolve, otherwise this module compiles
        // against one Spring Security and runs on another
        mavenBom("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}")
    }
}