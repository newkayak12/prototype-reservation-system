import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.kapt)
}

tasks.named("bootJar") { enabled = false }
tasks.named("jar") { enabled = true }

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

val kapt by configurations

dependencies {
    // Project modules
    implementation(project(":shared-module"))
    implementation(project(":core-module"))
    implementation(project(":application-module"))

    // Kotlin basics
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.kotlin)

    // Spring Boot starters
    implementation(libs.spring.boot.starter.web)
    implementation(libs.bundles.spring.data)
    implementation(libs.spring.retry)

    // Database dependencies
    implementation(libs.bundles.database)
    implementation(libs.bundles.flyway)

    // QueryDSL
    implementation("com.querydsl:querydsl-jpa:${libs.versions.querydsl.get()}:jakarta")
    kapt("com.querydsl:querydsl-apt:${libs.versions.querydsl.get()}:jakarta")
    kapt(libs.bundles.querydsl.kapt)

    // Additional dependencies
    implementation(libs.p6spy)
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
