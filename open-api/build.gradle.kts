plugins {
    kotlin("jvm")
    id("org.openapi.generator") version "7.8.0"
}

repositories {
    mavenCentral()
}

openApiGenerate {
    generatorName.set("kotlin-spring")
    inputSpec.set("$projectDir/src/main/resources/umt-api.yaml")
    outputDir.set("${layout.buildDirectory}/generated")
    apiPackage.set("com.umt")
    modelPackage.set("com.umt.model")
    library.set("spring-boot")
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useTags" to "true",
            "documentationProvider" to "springdoc",
            "useSpringBoot3" to "true",
            "serializationLibrary" to "jackson",
            "useBeanValidation" to "true",
            "enumPropertyNaming" to "UPPERCASE"
        )
    )
}

sourceSets {
    main {
        kotlin.srcDir("${layout.buildDirectory}/generated/src/main/kotlin")
    }
}

tasks.named("compileKotlin") {
    dependsOn("openApiGenerate")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.4.1"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springdoc:springdoc-openapi-starter-common:2.6.0")
}