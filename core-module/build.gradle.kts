import io.gitlab.arturbosch.detekt.Detekt

extra["snippetsDir"] = file("build/generated-snippets")
tasks.withType<Test> {
    useJUnitPlatform()
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
}

dependencies {
    testImplementation(project(":test-module"))
}
