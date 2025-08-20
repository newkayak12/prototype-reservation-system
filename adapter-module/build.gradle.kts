import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.asciidoctor)
    alias(libs.plugins.restdocs.api.spec)
    alias(libs.plugins.swagger.generator)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.flyway)
}

tasks.named("bootJar") { enabled = true }
tasks.named("jar") { enabled = false }

configurations.create("asciidoctorExt")

flyway {
    url = "jdbc:mysql://localhost:3306/flyway"
    user = "temporary"
    password = "temporary"
    driver = "com.mysql.cj.jdbc.Driver"
}

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

val apiSpec = file("/api-spec/openapi3.yaml")
val reservation = "reservation"
openapi3 {
    setServer("http://localhost:8080")
    title = reservation
    description = "$reservation-API"
    version = "1.0.0"
    format = "yaml"
}

swaggerSources {
    create(reservation) {
        setInputFile(apiSpec)
    }
}

tasks.register("swagger") {
    dependsOn("openapi3")
    doFirst {

        val swaggerUIFile = file(layout.buildDirectory.dir("/api-spec/openapi3.yaml"))

        val securitySchemesContent = """
            securitySchemes:
              APIKey:
                type: apiKey
                name: Authorization
                `in`: header
            security:
              - APIKey: []  # Apply the security scheme here
        """.trimIndent()

        swaggerUIFile.appendText("\n$securitySchemesContent")
        println("Append Security Settings")
    }

    doLast {
        copy {
            from("${layout.buildDirectory.get()}/api-spec/openapi3.yaml")
            into("${layout.buildDirectory.get()}/resources/main/static/docs")
        }
        println("Copy yaml")
    }
}

val developmentOnly by configurations
val kapt by configurations

dependencies {
    // Project modules
    implementation(project(":shared-module"))
    implementation(project(":application-module"))
    compileOnly(project(":core-module"))
    
    // Kotlin basics
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.kotlin)
    
    // Spring Boot starters
    implementation(libs.bundles.spring.web)
    implementation(libs.bundles.spring.data)
    
    // Database dependencies
    implementation(libs.bundles.database)
    
    // QueryDSL
    implementation("com.querydsl:querydsl-jpa:${libs.versions.querydsl.get()}:jakarta")
    kapt("com.querydsl:querydsl-apt:${libs.versions.querydsl.get()}:jakarta")
    kapt(libs.bundles.querydsl.kapt)
    
    // Additional dependencies
    implementation(libs.springdoc.openapi)
    implementation(libs.p6spy)
    
    // Development only
    developmentOnly(libs.spring.boot.docker.compose)
    
    // Documentation
    add("asciidoctorExt", libs.restdocs.asciidoctor)
    testImplementation(libs.bundles.documentation)
    testImplementation(libs.spring.security.test)
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

tasks.asciidoctor {
    inputs.dir(project.extra["snippetsDir"]!!)
    dependsOn(tasks.test)
}
