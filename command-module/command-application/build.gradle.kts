import io.gitlab.arturbosch.detekt.Detekt

// command-application: 유스케이스·포트. 허용 의존 = command-core, contract, shared. (00-module-index §2)
// 핵심 불변식(DESIGN-019): core 이벤트 타입을 아는 유일한 계층.
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
    implementation(project(":command-module:command-core"))
    implementation(project(":contract-module"))
    implementation(project(":shared-module"))

    testImplementation(project(":test-module"))
    testImplementation(libs.bundles.testing.kotest)
    testImplementation(libs.bundles.testing.mock)
    testImplementation(libs.bundles.testing.fixtures)
    testImplementation(libs.assertj)
}
