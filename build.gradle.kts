plugins {
    // versions come from gradle.properties via settings.gradle.kts pluginManagement
    kotlin("jvm") apply false
    kotlin("plugin.spring") apply false
    kotlin("plugin.jpa") apply false
    kotlin("kapt") apply false
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management") apply false
    // applied to the root project only — it walks the subprojects itself
    id("org.sonarqube")
}

// ---------------------------------------------------------------------------
//  Coverage
// ---------------------------------------------------------------------------
// Every Kotlin module gets JaCoCo, and `test` always produces the XML report Sonar reads.
// The scanner picks those reports up on its own, so there is no report path to keep in sync.
subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "jacoco")

        the<JacocoPluginExtension>().toolVersion =
            rootProject.property("jacocoVersion") as String

        tasks.withType<Test>().configureEach {
            finalizedBy(tasks.withType<JacocoReport>())
        }

        tasks.withType<JacocoReport>().configureEach {
            dependsOn(tasks.withType<Test>())
            reports {
                xml.required.set(true)
                // HTML is noise in CI; generate it locally with `./gradlew jacocoTestReport`
                html.required.set(false)
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Static analysis
// ---------------------------------------------------------------------------
sonar {
    properties {
        property("sonar.host.url", "https://sonarcloud.io")
        // override from CI if the keys on SonarQube Cloud end up named differently
        property(
            "sonar.projectKey",
            System.getenv("SONAR_PROJECT_KEY") ?: "unified-media-tracker-backend",
        )
        property(
            "sonar.organization",
            System.getenv("SONAR_ORGANIZATION") ?: "unified-media-tracker",
        )

        // ai-analyser is not a Gradle module, so it is attached to the root project by hand.
        // coverage.xml comes from `pytest --cov --cov-report=xml`.
        property("sonar.sources", "ai-analyser/app")
        property("sonar.tests", "ai-analyser/tests")
        property("sonar.python.version", "3.11")
        property("sonar.python.coverage.reportPaths", "ai-analyser/coverage.xml")

        // build output and OpenAPI-generated Kotlin are not handwritten code
        property("sonar.exclusions", "**/build/**,**/generated/**,**/*MapperImpl*")
        property("sonar.coverage.exclusions", "**/build/**,**/generated/**,**/*Application.kt")
    }
}
