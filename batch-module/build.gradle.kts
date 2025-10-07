import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.kapt)
}

tasks.named("bootJar") { enabled = true }
tasks.named("jar") { enabled = false }




kapt {
    arguments {
        arg("querydsl.generatedAnnotation", "jakarta.annotation.Generated")
    }
    correctErrorTypes = true
}

sourceSets {
    main {
        java {
            srcDirs("build/generated/kapt/main")
        }
    }
}

tasks.named<Detekt>("detekt") {
    reports {
        html.required.set(true)
        html.outputLocation.set(file("$rootDir/build/reports/${project.name}detekt/detekt.html"))
        sarif.required.set(false)
        xml.required.set(false)
        txt.required.set(false)
    }
}

val developmentOnly by configurations
val kapt by configurations

dependencies {
    // Project modules
    implementation(project(":shared-module"))
    implementation(project(":application-module"))
    compileOnly(project(":core-module"))
    testImplementation(project(":core-module"))

    // Kotlin basics
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.kotlin)

    // Spring Boot starters
    implementation(libs.bundles.spring.web)
    implementation(libs.bundles.spring.data)

    // Spring Batch for long-running jobs
    implementation(libs.spring.boot.starter.batch)
    implementation(libs.spring.boot.starter.batch.test)

    // Database dependencies
    implementation(libs.bundles.database)

    // QueryDSL
    implementation("com.querydsl:querydsl-jpa:${libs.versions.querydsl.get()}:jakarta")
    kapt("com.querydsl:querydsl-apt:${libs.versions.querydsl.get()}:jakarta")
    kapt(libs.bundles.querydsl.kapt)

    // Additional dependencies
    implementation(libs.p6spy)

    // Development only
    developmentOnly(libs.spring.boot.docker.compose)

    // Documentation
    testImplementation(libs.bundles.testing.containers)
    testImplementation(libs.bundles.testing.kotest)
    testImplementation(libs.bundles.testing.mock)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.bundles.testing.fixtures)  // FixtureMonkey includes jqwik
    testImplementation(project(":test-module"))
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
