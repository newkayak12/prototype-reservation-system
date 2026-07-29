import io.gitlab.arturbosch.detekt.Detekt

// command-infrastructure: ES 엔진·Outbox relay·Kafka producer 등 횡단 기술 배관.
// 허용 의존 = contract, shared. command-core 금지 — event_store 경로는 타입-불가지의
// StoredEvent(바이트)만 다룬다. (00-module-index §2, DESIGN-019)
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
    implementation(project(":contract-module"))
    implementation(project(":shared-module"))

    testImplementation(project(":test-module"))
    testImplementation(libs.bundles.testing.kotest)
    testImplementation(libs.bundles.testing.mock)
    testImplementation(libs.bundles.testing.fixtures)
}
