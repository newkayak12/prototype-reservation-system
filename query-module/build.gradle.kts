import io.gitlab.arturbosch.detekt.Detekt

// query-module: projection(이벤트 구독→read model 갱신) + read model(조회 API).
// 허용 의존 = contract, shared. command-* 전체 금지 (CQRS 경계). (00-module-index §2)
// 부팅 가능한 실행 모듈이지만 @SpringBootApplication 진입점이 아직 없다.
// 진입점 구현 시 bootJar=true / jar=false 로 되돌린다.
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
