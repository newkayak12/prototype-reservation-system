import io.gitlab.arturbosch.detekt.Detekt

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
    testImplementation(project(":test-module"))

}
