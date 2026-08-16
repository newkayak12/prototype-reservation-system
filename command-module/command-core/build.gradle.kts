import io.gitlab.arturbosch.detekt.Detekt

// command-core: 순수 도메인 (JPA/Spring 금지). 허용 의존 = shared 뿐. (00-module-index §2)
tasks.named("bootJar") { enabled = false }
tasks.named("jar") { enabled = true }

tasks.named<Detekt>("detekt") {
    reports {
        html.required.set(true)
        html.outputLocation.set(file("$rootDir/build/reports/${project.name}detekt/detekt.html"))
        sarif.required.set(false)
        xml.required.set(false)
        txt.required.set(false)
    }
}

configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-web")
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-data-jpa")
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-test")
}

dependencies {
    implementation(project(":shared-module"))

    testImplementation(project(":test-module"))
    testImplementation(libs.bundles.testing.kotest)
    testImplementation(libs.bundles.testing.mock)
    testImplementation(libs.bundles.testing.fixtures)
    testImplementation(libs.bundles.testing.konsist)
    testImplementation(libs.kotest.property)
}
