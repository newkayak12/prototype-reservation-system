import io.gitlab.arturbosch.detekt.Detekt

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

dependencies {
    implementation(project(":shared-module"))
    implementation(project(":core-module"))
    implementation(libs.kotlin.reflect)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.web)  // For MultipartFile
    compileOnly(libs.spring.retry)
    testImplementation(project(":test-module"))
    testImplementation(libs.spring.boot.starter.test)  // For MockMultipartFile and Spring test support
    testImplementation(libs.bundles.testing.kotest)
    testImplementation(libs.bundles.testing.mock)
    testImplementation(libs.bundles.testing.fixtures)  // FixtureMonkey includes jqwik
    testImplementation(libs.assertj)
}
