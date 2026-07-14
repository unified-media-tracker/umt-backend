plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
    `java-library`
}

group = "com.umt"
version = "0.0.1-SNAPSHOT"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
repositories { mavenCentral() }

dependencies {
    api("org.jetbrains.kotlin:kotlin-reflect")
    api("com.fasterxml.jackson.core:jackson-annotations:2.18.2")
    api("jakarta.validation:jakarta.validation-api:3.0.2")
}