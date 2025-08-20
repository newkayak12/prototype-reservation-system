import io.gitlab.arturbosch.detekt.Detekt

tasks.named("bootJar") { enabled = false }
tasks.named("jar") { enabled = true }

extra["snippetsDir"] = file("build/generated-snippets")
tasks.withType<Test> {
    // Kotest를 사용하므로 useJUnitPlatform() 제거
}
tasks.test {
    outputs.dir(project.extra["snippetsDir"]!!)
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

dependencies {
    implementation(project(":shared-module"))
    implementation(libs.kotlin.reflect)
    implementation(libs.spring.boot.starter)  // For @ConfigurationProperties
    
    testImplementation(project(":test-module"))
    testImplementation(libs.bundles.testing.kotest)
    testImplementation(libs.bundles.testing.mock)
    testImplementation(libs.bundles.testing.fixtures)  // FixtureMonkey includes jqwik
}
