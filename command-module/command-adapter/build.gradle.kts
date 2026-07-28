import io.gitlab.arturbosch.detekt.Detekt

// command-adapter: 인바운드(REST) · 아웃바운드 어댑터. 부팅 가능한 실행 모듈.
// 허용 의존 = command-application, command-infrastructure, contract, shared. query 금지.
// (00-module-index §2)
tasks.named("bootJar") { enabled = true }
tasks.named("jar") { enabled = false }

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
    implementation(project(":command-module:command-application"))
    implementation(project(":command-module:command-infrastructure"))
    implementation(project(":contract-module"))
    implementation(project(":shared-module"))

    testImplementation(project(":test-module"))
    testImplementation(libs.bundles.testing.kotest)
    testImplementation(libs.bundles.testing.mock)
    testImplementation(libs.bundles.testing.fixtures)
}
