plugins {
    id("org.springframework.boot") version "3.4.5" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    kotlin("jvm") version "2.0.10"
    kotlin("plugin.spring") version "2.0.10" apply false
    kotlin("plugin.jpa") version "2.0.10" apply false
    kotlin("kapt") version "2.0.10" apply false

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

detekt {
    config.setFrom(files("$rootDir/detekt.yaml"))
    buildUponDefaultConfig = true
}

spotless {
    kotlin {
        target("**/*.kt")
        ktlint("1.2.1") // 원하는 ktlint 버전 지정
        trimTrailingWhitespace()
        indentWithSpaces(4)
        endWithNewline()
    }

    kotlinGradle {
        target("**/*.kts")
        ktlint("1.2.1")
    }
}

tasks.register("gitPreCommitHook") {
    doLast {
        println("Running spotlessKotlinGradleApply before commit...")

        // spotlessKotlinGradleApply 작업 실행 (gradlew를 통해 실행)
        exec {
            commandLine("bash", "./gradlew", "spotlessKotlinGradleApply")
        }
        exec {
            commandLine("bash", "./gradlew", "detekt")
        }

        // 변경된 파일을 git에 다시 stage
        exec {
            commandLine("git", "add", ".")
        }
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

allprojects {
    group = "com.base"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "io.gitlab.arturbosch.detekt")

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
        testImplementation("io.mockk:mockk:1.13.10")
        testImplementation("io.mockk:mockk-agent:1.13.10")
    }

    extra["snippetsDir"] = file("build/generated-snippets")

    tasks.withType<Test> {
        useJUnitPlatform()
    }
    tasks.test {
        outputs.dir(project.extra["snippetsDir"]!!)
    }
}

val queryDslVersion = "5.1.0"

project(":shared-module") {
    tasks.named("bootJar") { enabled = false }
    tasks.named("jar") { enabled = true }

    dependencies {
        implementation("org.springframework.security:spring-security-crypto")
    }
}

project(":core-module") {
    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")
    apply(plugin = "org.jetbrains.kotlin.kapt")

    tasks.named("bootJar") { enabled = false }
    tasks.named("jar") { enabled = true }

    val kapt by configurations

    dependencies {
        implementation("org.springframework.boot:spring-boot-starter-data-jpa")
        compileOnly("com.querydsl:querydsl-jpa:$queryDslVersion:jakarta")
        add("kapt", "com.querydsl:querydsl-apt:$queryDslVersion:jakarta")
        add("kapt", "jakarta.annotation:jakarta.annotation-api")
        add("kapt", "jakarta.persistence:jakarta.persistence-api")
    }
}

project(":application-module") {
    tasks.named("bootJar") { enabled = false }
    tasks.named("jar") { enabled = true }
    dependencies {
        implementation("org.springframework.security:spring-security-core:6.3.5")
    }
}

project(":adapter-module") {
    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")
    apply(plugin = "org.asciidoctor.jvm.convert")
    apply(plugin = "com.epages.restdocs-api-spec")
    apply(plugin = "org.hidetake.swagger.generator")

    tasks.named("bootJar") { enabled = true }
    tasks.named("jar") { enabled = false }

    val developmentOnly by configurations

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

        developmentOnly("org.springframework.boot:spring-boot-docker-compose")
        testImplementation("com.navercorp.fixturemonkey:fixture-monkey:1.1.8")
        testImplementation("com.navercorp.fixturemonkey:fixture-monkey-jakarta-validation:1.1.5")
        testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
        testImplementation("org.springframework.security:spring-security-test")
    }
}
