plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management)
    jacoco
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.flyway) apply false
    alias(libs.plugins.asciidoctor) apply false
    alias(libs.plugins.restdocs.api.spec) apply false
    alias(libs.plugins.swagger.generator) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
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
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        indentWithSpaces(4)
        endWithNewline()
    }

}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

fun String.runCommand(): String =
    ProcessBuilder(*split(" ").toTypedArray())
        .redirectErrorStream(true)
        .start()
        .inputStream
        .bufferedReader()
        .readText()

tasks.register("preCommitApplySpotless") {
    description = "Pre-commit code applying spotless."
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    outputs.cacheIf { false }
    outputs.upToDateWhen { false }

    dependsOn("spotlessKotlinApply")
}

tasks.register("preCommitDetekt") {
    description = "Pre-commit code applying detekt."
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    outputs.cacheIf { false }
    outputs.upToDateWhen { false }

    dependsOn("detekt")
    mustRunAfter("preCommitApplySpotless")
}

tasks.register<Exec>("preCommitAddCommit") {
    description = "Pre-commit code adding commit."
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    outputs.cacheIf { false }
    outputs.upToDateWhen { false }

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
    outputs.cacheIf { false }
    outputs.upToDateWhen { false }
    doLast {
        val hookFile = File(project.rootDir, ".git/hooks/pre-commit")
        if (!hookFile.exists()) {
            hookFile.parentFile.mkdirs()
            hookFile.writeText(
                """
                #!/bin/sh
                ./gradlew gitPreCommitHook --rerun-tasks --no-build-cache
                """.trimIndent(),
            )
            hookFile.setExecutable(true)
            println("Git pre-commit hook set up successfully!")
        }
    }
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:${libs.versions.testcontainers.get()}")
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

    configurations.named("detekt").configure {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion("2.0.10")
            }
        }
    }

    // 공통 dependencies는 각 모듈에서 개별적으로 정의
    // 루트에서는 version catalog를 활용한 설정만 관리





    extra["snippetsDir"] = file("build/generated-snippets")
    tasks.withType<Test> {
        useJUnitPlatform()

        failFast = true
        outputs.cacheIf { false }
        outputs.upToDateWhen { false }

        jvmArgs(
            "-Xmx2g", "-XX:+UseG1GC", "-XX:+UseStringDeduplication",
            "-Djava.awt.headless=true"
        )

        systemProperties(
            "spring.test.context.cache.maxSize" to "30",
            "spring.main.lazy-initialization" to "true",
            "spring.main.banner-mode" to "off",
            "spring.jmx.enabled" to "false",
            "junit.jupiter.execution.parallel.enabled" to "false",
            "logging.level.root" to "WARN",
            "java.security.egd" to "file:/dev/./urandom"
        )

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

