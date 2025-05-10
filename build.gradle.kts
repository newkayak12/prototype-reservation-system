plugins {
    kotlin("jvm") version "1.9.25"
    id("org.springframework.boot") version "3.4.5" apply false
    kotlin("plugin.spring") version "1.9.25" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    kotlin("plugin.jpa") version "1.9.25" apply false
    kotlin("kapt") version "1.9.25" apply false

    id("org.asciidoctor.jvm.convert") version "3.3.2" apply false
    id("com.epages.restdocs-api-spec") version "0.19.4" apply false
    id("org.hidetake.swagger.generator") version "2.18.2" apply false
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



    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.springframework.boot:spring-boot-starter-web")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
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
        compileOnly("com.querydsl:querydsl-jpa:${queryDslVersion}:jakarta")
        add("kapt", "com.querydsl:querydsl-apt:${queryDslVersion}:jakarta")
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
        implementation("com.querydsl:querydsl-jpa:${queryDslVersion}:jakarta")

        developmentOnly("org.springframework.boot:spring-boot-docker-compose")
        testImplementation("com.navercorp.fixturemonkey:fixture-monkey:1.1.8")
        testImplementation("com.navercorp.fixturemonkey:fixture-monkey-jakarta-validation:1.1.5")
        testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
        testImplementation("org.springframework.security:spring-security-test")
    }


}

