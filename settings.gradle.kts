pluginManagement {
    // Plugin versions live in gradle.properties, so modules cannot drift apart. Two real bugs
    // came from that drift: shared imported the Boot 4.1.0 BOM while everything else was on
    // 3.4.1, and kapt was pinned to a different Kotlin version than the rest of the toolchain.
    val kotlinVersion: String by settings
    val springBootVersion: String by settings
    val springDependencyManagementVersion: String by settings
    val sonarqubeVersion: String by settings

    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.spring") version kotlinVersion
        kotlin("plugin.jpa") version kotlinVersion
        kotlin("kapt") version kotlinVersion
        id("org.springframework.boot") version springBootVersion
        id("io.spring.dependency-management") version springDependencyManagementVersion
        id("org.sonarqube") version sonarqubeVersion
    }
}

rootProject.name = "umt-backend"

include("api-gateway")
include("core-service")
include("shared")
include("open-api")
