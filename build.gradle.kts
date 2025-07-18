plugins {
    id("org.springframework.boot") version "3.4.5" apply false
    id("io.spring.dependency-management") version "1.1.7"
    id("jacoco")
    kotlin("jvm") version "2.0.10"
    kotlin("plugin.spring") version "2.0.10" apply false
    kotlin("plugin.jpa") version "2.0.10" apply false
    kotlin("kapt") version "2.0.10" apply false
    id("org.flywaydb.flyway") version "10.20.1" apply false
//    id("org.sonarqube") version "4.4.1.3373"
    id("org.asciidoctor.jvm.convert") version "3.3.2" apply false
    id("com.epages.restdocs-api-spec") version "0.19.4" apply false
    id("org.hidetake.swagger.generator") version "2.18.2" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
    id("com.diffplug.spotless") version "6.25.0"
    id("com.github.ben-manes.versions") version "0.51.0"
}


java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

spotless {
    kotlin {
        target("**/*.kt")



        ktlint("1.2.1")
        trimTrailingWhitespace()
        indentWithSpaces(4)
        endWithNewline()
    }

//    kotlinGradle {
//        target("**/*.kts")
//        ktlint("1.2.1")
//    }
}

jacoco {
    toolVersion = "0.8.11" // 원하는 버전 명시
}

fun String.runCommand(): String =
    ProcessBuilder(*split(" ").toTypedArray())
        .redirectErrorStream(true)
        .start()
        .inputStream
        .bufferedReader()
        .readText()

tasks.register<Exec>("preCommitApplySpotless") {
    description = "Pre-commit code applying spotless."
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    commandLine("bash", "./gradlew", "spotlessKotlinApply")
}

tasks.register<Exec>("preCommitDetekt") {
    description = "Pre-commit code applying detekt."
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    commandLine("bash", "./gradlew", "detekt")
    mustRunAfter("preCommitApplySpotless")
}

tasks.register<Exec>("preCommitAddCommit") {
    description = "Pre-commit code adding commit."
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    val stagedFiles = "git diff --cached --name-only"
        .runCommand()
        .lines()
        .filter { it.isNotBlank() && File(project.rootDir, it).exists() }
    if (stagedFiles.isNotEmpty()) {
        commandLine(listOf("git", "add") + stagedFiles)
    } else {
        commandLine("echo", "No staged files to add.")
    }
    mustRunAfter("preCommitDetekt")
}

tasks.register("gitPreCommitHook") {
    description = "Git hook commit."
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    dependsOn("preCommitApplySpotless", "preCommitDetekt", "preCommitAddCommit")
    doLast {
        println("Running spotlessKotlinGradleApply and detekt before commit...")
    }
}

// pre-commit 후크 설정
tasks.named("gitPreCommitHook") {
    doLast {
        val hookFile = file(".git/hooks/pre-commit")
        if (!hookFile.exists()) {
            hookFile.writeText(
                """
                #!/bin/sh
                ./gradlew gitPreCommitHook
                """.trimIndent(),
            )
            hookFile.setExecutable(true)
            println("Git pre-commit hook set up successfully!")
        }
    }
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.21.0")
    }
}

allprojects {
    group = "com.reservation"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "jacoco")




    detekt {
        config.setFrom(files("$rootDir/detekt.yaml"))
        buildUponDefaultConfig = true
    }

    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.springframework.boot:spring-boot-starter-web")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    }

    dependencies {
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        testImplementation("io.kotest:kotest-runner-junit5:5.9.0")
        testImplementation("io.kotest:kotest-assertions-core:5.9.0")
        testImplementation("io.kotest:kotest-framework-engine:5.9.0")
        testImplementation("io.kotest:kotest-assertions-core:5.9.0")
        testImplementation("io.kotest:kotest-property:5.9.0")
        testImplementation("io.kotest:kotest-runner-junit5:5.9.0")
        testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
        testImplementation("com.ninja-squad:springmockk:4.0.2")
        testImplementation("com.navercorp.fixturemonkey:fixture-monkey-starter-kotlin:1.1.11")
        testImplementation("com.navercorp.fixturemonkey:fixture-monkey-kotest:1.1.11")
        testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
        testImplementation("io.mockk:mockk:1.13.10")
        testImplementation("io.mockk:mockk-agent:1.13.10")
        testImplementation("org.assertj:assertj-core:3.24.2")
    }





    extra["snippetsDir"] = file("build/generated-snippets")
    tasks.withType<Test> {
        useJUnitPlatform()
    }
    tasks.test {
        outputs.dir(project.extra["snippetsDir"]!!)
    }
    tasks.test {
        useJUnitPlatform()
        finalizedBy(tasks.jacocoTestReport)
    }

    tasks.withType<JacocoReport>().configureEach {
        dependsOn(tasks.test)

        reports {
            xml.required.set(true)
            html.required.set(true)
        }

        classDirectories.setFrom(
            fileTree(layout.buildDirectory.dir("classes")) {
                exclude("**/*\$*") // object/class 중복 방지
            }
        )

        sourceDirectories.setFrom(files("src/main/kotlin"))
        executionData.setFrom(
            fileTree(layout.buildDirectory.get().asFile) {
                include("jacoco/test.exec")
            }
        )
    }
}

val queryDslVersion = "5.1.0"

project(":test-module") {
    tasks.named("bootJar") { enabled = false }
    tasks.named("jar") { enabled = true }

}

project(":shared-module") {
    tasks.named("bootJar") { enabled = false }
    tasks.named("jar") { enabled = true }

    dependencies {
        implementation("org.springframework.security:spring-security-crypto")
    }
}

project(":core-module") {
    tasks.named("bootJar") { enabled = false }
    tasks.named("jar") { enabled = true }
}

project(":application-module") {
    tasks.named("bootJar") { enabled = false }
    tasks.named("jar") { enabled = true }
    dependencies {
//        implementation("org.springframework.security:spring-security-core:6.3.5")
        implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    }
}

project(":adapter-module") {
    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")
    apply(plugin = "org.asciidoctor.jvm.convert")
    apply(plugin = "com.epages.restdocs-api-spec")
    apply(plugin = "org.hidetake.swagger.generator")
    apply(plugin = "org.jetbrains.kotlin.kapt")
    apply(plugin = "org.flywaydb.flyway")





    tasks.named("bootJar") { enabled = true }
    tasks.named("jar") { enabled = false }

    val developmentOnly by configurations
    val kapt by configurations


    dependencies {
        implementation("org.springframework.boot:spring-boot-starter-actuator")
        implementation("org.springframework.boot:spring-boot-starter-validation")
        implementation("org.springframework.boot:spring-boot-starter-security")
        implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
        implementation("org.springframework.boot:spring-boot-starter-data-jpa")
        implementation("org.flywaydb:flyway-core")
        implementation("org.flywaydb:flyway-mysql")
        runtimeOnly("com.mysql:mysql-connector-j")
        implementation("com.querydsl:querydsl-jpa:$queryDslVersion:jakarta")
        add("kapt", "com.querydsl:querydsl-apt:$queryDslVersion:jakarta")
        add("kapt", "jakarta.annotation:jakarta.annotation-api")
        add("kapt", "jakarta.persistence:jakarta.persistence-api")
    }

    dependencies {
        developmentOnly("org.springframework.boot:spring-boot-docker-compose")
        testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
        testImplementation("org.springframework.security:spring-security-test")
        testImplementation("org.testcontainers:mysql")
        testImplementation("org.springframework.boot:spring-boot-testcontainers")
        testImplementation("org.testcontainers:junit-jupiter")
    }
}
