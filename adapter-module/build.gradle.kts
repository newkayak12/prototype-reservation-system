import io.gitlab.arturbosch.detekt.Detekt
import org.flywaydb.gradle.FlywayExtension

plugins {
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.asciidoctor)
    alias(libs.plugins.restdocs.api.spec)
    alias(libs.plugins.swagger.generator)
    alias(libs.plugins.kotlin.kapt)
}

// Flyway's own MySQL support (flyway-mysql) and its SPI-discovery mechanism for it
// (`org.flywaydb.core.internal.plugin.PluginRegister`) both key off a single fact: whichever
// `ClassLoader` first loads `flyway-core`'s classes is ALSO the loader `PluginRegister` uses —
// permanently, via `getClass().getClassLoader()` on itself — to `ServiceLoader.load(...)` every
// database-type extension, including `flyway-mysql`'s `MySQLDatabaseType`. That lookup is NOT
// influenced by any classloader this build wires up on the side; `flyway-mysql` must be resolved
// alongside `flyway-core` as part of ONE coherent dependency graph, on the SAME classloader that
// defines the `FlywayMigrateTask`/`FlywayInfoTask` classes actually executed. Applying the plugin
// the modern way — `plugins { alias(libs.plugins.flyway) }` — resolves flyway-core via Gradle's
// own plugin-portal machinery, a classloader this project's `dependencies {}`/`buildscript {}`
// blocks cannot reach or extend; `flyway-mysql` then ends up on a different, unrelated
// classloader than the plugin's `flyway-core`, and `flywayMigrate`/`flywayInfo` fail with
// "No database found to handle jdbc:mysql://..." even though `flyway-mysql` is correctly resolved
// elsewhere on the build. The old-style application below — a `buildscript {}` classpath carrying
// the plugin artifact itself (`flyway-gradle-plugin`) together with `flyway-mysql` and the JDBC
// driver, applied via `apply(plugin = ...)` — resolves all three as ONE dependency graph on ONE
// classloader, which is exactly what `PluginRegister` needs; confirmed end-to-end against a real
// local MySQL (`flywayMigrate` applying the full V1_0..V1_24 chain). This also requires the root
// build to NOT additionally pre-register `org.flywaydb.flyway` via the `plugins {}` DSL (see
// build.gradle.kts) — once Gradle has resolved that id via the plugin-portal path for ANY project,
// it reuses that same resolution for a later old-style `apply(plugin = "org.flywaydb.flyway")`
// too, so the two application styles cannot coexist for this plugin id across the build.
// Versions are pinned explicitly in the catalog (gradle/libs.versions.toml: `flyway`, `mysql`)
// rather than left to Spring Boot's BOM, which only manages this project's own `implementation`/
// `testImplementation` configurations and never reaches a `buildscript {}` classpath.
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.flywaydb:flyway-gradle-plugin:${libs.versions.flyway.get()}")
        classpath("org.flywaydb:flyway-mysql:${libs.versions.flyway.get()}")
        classpath("com.mysql:mysql-connector-j:${libs.versions.mysql.get()}")
    }
}
apply(plugin = "org.flywaydb.flyway")

tasks.named("bootJar") { enabled = true }
tasks.named("jar") { enabled = false }

configurations.create("asciidoctorExt")

// Local defaults match what docker-entrypoint-initdb.d/init.sql provisions (the `flyway` schema
// and the `temporary` user/password) and the runtime history table/schema that Spring Flyway
// uses at startup (infrastructure-module/src/main/resources/persistence/flyway/index.yaml:
// table = flyway_version_control, schemas = flyway) — this task must write to the SAME history
// table as the application, not a second, divergent one. Every value can be overridden with a
// matching Gradle property (e.g. `-Pflyway.url=...`) without hardcoding new credentials here.
configure<FlywayExtension> {
    url = (findProperty("flyway.url") as String?) ?: "jdbc:mysql://localhost:3306/flyway"
    user = (findProperty("flyway.user") as String?) ?: "temporary"
    password = (findProperty("flyway.password") as String?) ?: "temporary"
    driver = "com.mysql.cj.jdbc.Driver"
    schemas = arrayOf("flyway")
    table = "flyway_version_control"
    // The Gradle plugin's default `locations` (filesystem:src/main/resources/db/migration) is
    // resolved relative to THIS project directory (adapter-module), where no such directory
    // exists — the migrations actually live under infrastructure-module. Left unset, the task
    // would silently apply 0 migrations and report success.
    locations = arrayOf("filesystem:${project(":infrastructure-module").projectDir}/src/main/resources/db/migration")
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
    implementation(project(":infrastructure-module"))
    compileOnly(project(":core-module"))
    testImplementation(project(":core-module"))

    // Kotlin basics
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.kotlin)

    // Spring Boot starters
    implementation(libs.bundles.spring.web)
    implementation(libs.bundles.spring.data)
    implementation(libs.spring.retry)

    // Database dependencies
    implementation(libs.bundles.database)
    // QueryDSL
    implementation("com.querydsl:querydsl-jpa:${libs.versions.querydsl.get()}:jakarta")
    kapt("com.querydsl:querydsl-apt:${libs.versions.querydsl.get()}:jakarta")
    kapt(libs.bundles.querydsl.kapt)

    // Additional dependencies
    implementation(libs.springdoc.openapi)
    implementation(libs.p6spy)
    implementation(libs.redisson.spring.boot.starter)
    
    // Kafka
    implementation(libs.bundles.kafka)

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
    // Binary-PK regression tests (TimeTableOccupancyConcurrencyTest,
    // WaitingQueueDatabaseAdapterIntegrationTest, BatchCreateDropFlywaySchemaDivergenceTest)
    // run the REAL Flyway migration chain against Testcontainers MySQL instead of a hand-picked
    // migration subset or Spring's ResourceDatabasePopulator — both of those failed to parse
    // V1_22/V1_23's stored-procedure (BEGIN...END) bodies correctly. flyway-core is already on
    // adapter-module's runtime classpath transitively (via infrastructure-module's
    // `implementation(libs.bundles.flyway)`), but `implementation` deps are not exposed to a
    // consumer's compile/test-compile classpath, so it must be declared explicitly here too.
    testImplementation(libs.bundles.flyway)
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
