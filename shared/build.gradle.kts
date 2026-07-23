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

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
repositories { mavenCentral() }

dependencies {
    implementation("org.mapstruct:mapstruct:1.6.3")
    kapt("org.mapstruct:mapstruct-processor:1.6.3")
    api("org.jetbrains.kotlin:kotlin-reflect")
    api("com.fasterxml.jackson.core:jackson-annotations:2.18.2")
    api("jakarta.validation:jakarta.validation-api:3.0.2")
    api("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
    }
}