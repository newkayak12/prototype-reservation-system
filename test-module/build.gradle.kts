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

configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-web")
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-data-jpa")
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-test")
}

dependencies {
    implementation("com.navercorp.fixturemonkey:fixture-monkey-starter-kotlin:1.1.11")
    implementation("com.navercorp.fixturemonkey:fixture-monkey-kotest:1.1.11")
}
